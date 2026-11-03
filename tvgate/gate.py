#!/usr/bin/env python3
import datetime
import argparse
import re
import socket
import sqlite3
import hmac
import json
import math
import os
import signal
import subprocess
import sys
import tempfile
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, urlsplit

HOME = Path(os.environ.get('TVGATE_HOME', Path.home() / 'Library/Application Support/tvgate'))
TOKEN = Path(os.environ.get('TVGATE_TOKEN', Path.home() / '.secrets/tvgate.tokens.json'))
ADB = os.environ.get('ADB', 'adb')
HOOK = os.environ.get('TVGATE_HOOK', str(Path(__file__).with_name('sleephook')))
NOTIFY = os.environ.get('TVGATE_NOTIFY', 'osascript')
INTERVAL = float(os.environ.get('TVGATE_INTERVAL', '30'))
DEVICE = os.environ.get('TVGATE_DEVICE', '192.0.2.69:5555')
PACKAGES = ('com.google.android.youtube.tv', 'com.netflix.ninja')
STATE = HOME / 'state.json'
LOG = Path(os.environ.get('TVGATE_LOG', Path.home() / 'Library/Logs/tvgate.log'))
state_lock = threading.RLock()
adb_lock = threading.Lock()
children_lock = threading.Lock()
children = set()
stop = threading.Event()
until = None
tv = 'unknown'
disabled = set()
checked_at = None
grace = False
sleeping = False
sleep_ended = 0
failed_since = None
last_apply = 0
hook = None
last_check = None
db = None
db_lock = threading.RLock()
fetch_lock = threading.Lock()
fetch_now = threading.Event()
APPS = {'com.google.android.youtube.tv': 'YouTube', 'com.netflix.ninja': 'Netflix', 'tv.fourgtv.video': '四季', 'com.spotify.tv.android': 'Spotify', 'com.disney.disneyplus': 'Disney+', 'com.wbd.stream': 'HBO Max'}


def log(action, result, device=None, minutes=None, end=None, detail=None):
    try:
        LOG.parent.mkdir(parents=True, exist_ok=True)
        with LOG.open('a') as f:
            f.write(f'{datetime.datetime.now().isoformat(timespec="seconds")} {action} {result}\n')
    except OSError:
        pass
    if action in ('open', 'lock', 'expire', 'schedule', 'schedule-skipped', 'relock', 'away', 'grace', 'schedule-add', 'schedule-delete'):
        database('INSERT INTO log VALUES (?, ?, ?, ?, ?, ?)', (time.time(), device or '系統', action, minutes, end, detail), write=True)


def authorized():
    return until is not None and until > time.time()


def display(timestamp, fmt='%H:%M'):
    return datetime.datetime.fromtimestamp(timestamp).strftime(fmt) if timestamp is not None else None


def load():
    global until, last_check
    until = last_check = None
    try:
        saved = json.loads(STATE.read_text())
        if not isinstance(saved, dict):
            raise ValueError('invalid state')
        now = time.time()
        for field in ('until', 'last_check'):
            value = saved.get(field)
            if value is not None:
                try:
                    valid = (isinstance(value, (int, float)) and not isinstance(value, bool)
                             and math.isfinite(value) and abs(value - now) <= 400 * 86400)
                except OverflowError:
                    valid = False
                if not valid:
                    log('state', 'invalid ' + field)
                    value = None
            if field == 'until': until = value
            else: last_check = value
    except FileNotFoundError:
        pass
    except (OSError, ValueError, TypeError):
        log('state', 'invalid')



def save(value):
    HOME.mkdir(parents=True, exist_ok=True)
    name = None
    try:
        with tempfile.NamedTemporaryFile(mode='w', dir=HOME, delete=False) as f:
            name = f.name
            json.dump({'until': value, 'last_check': last_check}, f)
        os.replace(name, STATE)
    finally:
        if name and os.path.exists(name):
            os.unlink(name)


def command(args, deadline=None, retry=True):
    def run(cmd):
        timeout = min(8, max(0.01, deadline - time.monotonic())) if deadline else 8
        try:
            with children_lock:
                if stop.is_set() and deadline is None:
                    return None
                process = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                children.add(process)
            try:
                stdout, stderr = process.communicate(timeout=timeout)
                return subprocess.CompletedProcess(cmd, process.returncode, stdout, stderr)
            except subprocess.TimeoutExpired:
                process.kill()
                process.communicate()
                return None
            finally:
                with children_lock:
                    children.discard(process)
        except OSError:
            return None
    cmd = [ADB] + (args if args[0] == 'connect' else ['-s', DEVICE] + args)
    result = run(cmd)
    if retry and (result is None or result.returncode != 0) and (deadline is None or time.monotonic() < deadline):
        run([ADB, 'connect', DEVICE])
        if deadline is None or time.monotonic() < deadline:
            result = run(cmd)
    return result


def read_tv(deadline=None):
    global tv, disabled, checked_at, failed_since
    args = ['shell', 'pm', 'list', 'packages', '-d', '--user', '0']
    result = command(args, deadline)
    if result is not None and result.returncode == 0 and not result.stdout.strip() and (deadline is None or time.monotonic() < deadline):
        command(['connect', DEVICE], deadline, retry=False)
        result = command(args, deadline)
    with state_lock:
        if result is None or result.returncode or not result.stdout.strip():
            tv = 'unknown'
            if failed_since is None:
                failed_since = time.monotonic()
            return False
        lines = result.stdout.splitlines()
        disabled = {p for p in PACKAGES if 'package:' + p in lines}
        tv = 'locked' if len(disabled) == len(PACKAGES) else 'open' if not disabled else 'mixed'
        checked_at = display(time.time(), '%H:%M:%S')
        return True


def foreground(deadline=None):
    result = command(['shell', 'dumpsys', 'activity', 'activities'], deadline)
    if result is None or result.returncode != 0 or not result.stdout.strip():
        return None
    return any('mResumedActivity' in line and any(p in line for p in PACKAGES) for line in result.stdout.splitlines())


def apply(forced_open=False, deadline=None, force_lock=False, reconcile=True):
    global last_apply, grace
    timeout = max(0, deadline - time.monotonic()) if deadline else -1
    if not adb_lock.acquire(timeout=timeout):
        return False
    try:
        if sleeping and not forced_open:
            return False
        if not read_tv(deadline):
            return False
        with state_lock:
            desired = 'open' if forced_open or authorized() else 'locked'
            in_grace = grace and not forced_open and desired == 'locked'
        if in_grace:
            watching = foreground(deadline)
            with state_lock:
                if grace and not authorized():
                    if watching is None:
                        return False
                    notify = watching and grace == 'pending'
                    grace = 'watching' if watching else False
                    if watching and not notify:
                        return True
                else:
                    notify = False
            if notify:
                log('arrival', 'YouTube playing; grace')
                log('grace', 'YouTube playing')
                try:
                    subprocess.run([NOTIFY, '-e', 'display notification "有人正在看 YouTube 或 Netflix，要停請按立即鎖" with title "tvgate"'], timeout=8, capture_output=True)
                except (OSError, subprocess.TimeoutExpired):
                    log('notification', 'failed')
                return True
        with state_lock:
            if sleeping and not forced_open:
                return False
            desired = 'open' if forced_open or authorized() else 'locked'
            current = tv
        if current != desired or (force_lock and desired == 'locked'):
            log('apply', desired)
            if desired == 'locked':
                command(['shell', '; '.join('am force-stop ' + p for p in PACKAGES)], deadline)
                command(['shell', '; '.join('pm disable-user --user 0 ' + p for p in PACKAGES)], deadline)
            else:
                command(['shell', '; '.join('pm enable --user 0 ' + p for p in PACKAGES)], deadline)
            if not read_tv(deadline):
                return False
            if desired == 'locked':
                # 讀回確認哪個套件已停用就寫哪個（mixed 也寫），calc 才會在鎖定時截斷該 App 區段
                for p in PACKAGES:
                    if p in disabled:
                        database('INSERT OR IGNORE INTO events VALUES (?, ?, ?, ?)', (time.time(), 'GATE_LOCK', p, ''), write=True)
                if tv == 'locked' and reconcile: log('relock', 'locked')
        with state_lock:
            last_apply = time.monotonic()
            return tv == desired
    finally:
        adb_lock.release()


def arrival(waking=False):
    global grace, sleeping, sleep_ended
    with adb_lock:
        with state_lock:
            if waking:
                if sleeping:
                    # 沒有暗喚醒時，醒來才發現的休眠排程也要略過。
                    sleep_ended = schedule_clock()
                sleeping = False
            elif sleeping:
                return
            if not authorized():
                grace = 'pending'
    fetch_now.set()
    apply()


def home_network():
    override = os.environ.get('TVGATE_HOME_NET')
    if override is not None:
        try:
            return Path(override).read_text().strip() == '1'
        except OSError:
            return False
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as sock:
            sock.connect(('192.0.2.69', 9))
            return sock.getsockname()[0].startswith('192.0.2.')
    except OSError:
        return False


def schedule_clock():
    try:
        return float(Path(os.environ['TVGATE_CLOCK']).read_text())
    except (KeyError, OSError, ValueError):
        return time.time()


def monitor():
    global failed_since, last_check, until, grace
    away_since = None
    away_threshold = float(os.environ.get('TVGATE_AWAY_SECONDS', '120'))
    while not stop.wait(min(5, INTERVAL)):
        try:
            now = schedule_clock()
            at_home = home_network()
            if not at_home and away_since is None:
                away_since = time.monotonic()
            if at_home and away_since is not None:
                if time.monotonic() - away_since >= away_threshold:
                    arrival()
                away_since = None
            rules = get_rules()
            with state_lock:
                due_rules = schedule_due(rules or [], last_check if last_check is not None else now, now)
                last_check = now
                try:
                    save(until)
                except OSError:
                    log('state', 'save failed')
                    due_rules = []
                expired = not sleeping and until is not None and until <= time.time()
                if expired:
                    until = None
                    try: save(None)
                    except OSError: log('state', 'save failed')
                    log('expire', 'expired')
                due = time.monotonic() - last_apply >= INTERVAL
                had_failure = failed_since
            for rule, target in due_rules:
                if sleeping or target <= sleep_ended:
                    log('schedule-skipped', 'Mac 休眠', detail='Mac 休眠')
                elif now - target > 600:
                    log('schedule-skipped', display(target, '%Y-%m-%d %H:%M'), detail=display(target, '%Y-%m-%d %H:%M'))
                elif at_home:
                    with state_lock:
                        until = None
                        grace = False
                        try: save(None)
                        except OSError: log('state', 'save failed')
                    log('schedule', 'locked')
                    apply(force_lock=True, reconcile=False)
                else:
                    log('schedule-skipped', 'Mac 不在家', detail='Mac 不在家')
            if not sleeping and (expired or due or had_failure is not None):
                with adb_lock:
                    success = read_tv()
                if success:
                    with state_lock:
                        failed_since = None
                    apply()
        except Exception as e:
            log('monitor', f'iteration failed: {type(e).__name__}')


def hooks():
    global hook, sleeping
    while not stop.is_set():
        try:
            hook = subprocess.Popen([HOOK], stdin=subprocess.PIPE, stdout=subprocess.PIPE, text=True, bufsize=1)
            for line in hook.stdout:
                if stop.is_set():
                    break
                if line.strip() == 'sleep':
                    with state_lock:
                        sleeping = True
                    log('away', 'sleep')
                    ok = apply(forced_open=True, deadline=time.monotonic() + 7.8)
                    try:
                        hook.stdin.write(('ok' if ok else 'fail') + '\n')
                        hook.stdin.flush()
                    except (BrokenPipeError, OSError):
                        pass
                elif line.strip() == 'wake':
                    arrival(waking=True)
            hook.wait()
            if not stop.is_set():
                log('hook', 'exited; restarting')
        except OSError as e:
            log('hook', str(e))
        stop.wait(1)


def init_db():
    global db
    try:
        HOME.mkdir(parents=True, exist_ok=True)
        db = sqlite3.connect(HOME / 'tvgate.db', check_same_thread=False)
        with db_lock:
            db.execute('PRAGMA busy_timeout=5000')
            db.execute('PRAGMA journal_mode=WAL')
            db.executescript('''CREATE TABLE IF NOT EXISTS log(ts_epoch REAL, device TEXT, action TEXT, minutes INTEGER, until TEXT, detail TEXT);
                CREATE TABLE IF NOT EXISTS events(ts_epoch REAL, type TEXT, package TEXT, class TEXT, UNIQUE(ts_epoch,type,package,class));
                CREATE TABLE IF NOT EXISTS schedule(id INTEGER PRIMARY KEY, days TEXT, time TEXT);
                CREATE TABLE IF NOT EXISTS meta(key TEXT PRIMARY KEY, value TEXT);''')
            db.commit()
    except (OSError, sqlite3.Error):
        if db is not None:
            db.close()
        db = None


def database(sql, args=(), write=False):
    if db is None:
        return None
    try:
        with db_lock:
            result = db.execute(sql, args)
            if write:
                db.commit()
                return result.lastrowid
            return result.fetchall()
    except sqlite3.Error:
        return None


def parse_events(text):
    result = []
    active = False
    for line in text.splitlines():
        if 'Last 24 hour events' in line:
            active = True
            continue
        if active and 'In-memory' in line:
            break
        if not active:
            continue
        fields = dict(re.findall(r'(time|type|package|class)=("[^"]*"|\S+)', line))
        kind = fields.get('type')
        if kind not in ('ACTIVITY_RESUMED', 'SCREEN_NON_INTERACTIVE', 'DEVICE_SHUTDOWN', 'DEVICE_STARTUP'):
            continue
        try:
            ts = datetime.datetime.strptime(fields['time'].strip('"'), '%Y-%m-%d %H:%M:%S').timestamp()
        except (KeyError, ValueError):
            continue
        result.append((ts, kind, fields.get('package', ''), fields.get('class', '')))
    return result


def calc(events, now, last_fetch, day_start, day_end):
    totals = {}
    current = None
    start = None
    def close(end):
        if current is not None:
            seconds = max(0, min(end, start + 14400, day_end) - max(start, day_start))
            totals[current] = totals.get(current, 0) + seconds
    for ts, kind, package, cls in sorted(events, key=lambda e: (e[0], e[1] == 'ACTIVITY_RESUMED')):
        if kind == 'ACTIVITY_RESUMED':
            if package != current:
                close(ts)
                current, start = package, ts
        elif kind in ('SCREEN_NON_INTERACTIVE', 'DEVICE_SHUTDOWN', 'DEVICE_STARTUP') or (kind == 'GATE_LOCK' and package == current):
            close(ts)
            current, start = None, None
    if last_fetch is not None:
        close(min(now, last_fetch))
    return totals


def fetch_usage():
    with fetch_lock:
        if sleeping:
            return
        try:
            with children_lock:
                if stop.is_set(): return
                process = subprocess.Popen([ADB, '-s', DEVICE, 'shell', 'dumpsys', 'usagestats'], stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
                children.add(process)
            try:
                output, _ = process.communicate(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.communicate()
                log('usage', 'fetch failed')
                return
            finally:
                with children_lock: children.discard(process)
            if process.returncode or 'Last 24 hour events' not in output:
                log('usage', 'fetch failed')
                return
            if db is not None:
                try:
                    with db_lock:
                        db.executemany('INSERT OR IGNORE INTO events VALUES (?, ?, ?, ?)', parse_events(output))
                        now = time.time()
                        db.execute('INSERT OR REPLACE INTO meta VALUES (?, ?)', ('last_fetch', str(now)))
                        db.execute('DELETE FROM events WHERE ts_epoch < ?', (now - 40 * 86400,))
                        db.commit()
                except sqlite3.Error:
                    log('usage', 'db failed')
        except OSError:
            log('usage', 'fetch failed')


def fetch_loop():
    while not stop.is_set():
        fetch_usage()
        fetch_now.wait(float(os.environ.get('TVGATE_FETCH_INTERVAL', '600')))
        fetch_now.clear()


def get_rules():
    rows = database('SELECT id, days, time FROM schedule ORDER BY id')
    return None if rows is None else [{'id': row[0], 'days': json.loads(row[1]), 'time': row[2]} for row in rows]


def schedule_due(rules, prev_epoch, now_epoch):
    if now_epoch <= prev_epoch: return []
    date = datetime.datetime.fromtimestamp(prev_epoch).date()
    end = datetime.datetime.fromtimestamp(now_epoch).date()
    result = []
    while date <= end:
        for rule in rules:
            if date.isoweekday() in rule['days']:
                hour, minute = map(int, rule['time'].split(':'))
                target = datetime.datetime.combine(date, datetime.time(hour, minute)).timestamp()
                if prev_epoch < target <= now_epoch:
                    result.append((rule, target))
        date += datetime.timedelta(days=1)
    return sorted(result, key=lambda item: item[1])


def usage(period):
    rows = database('SELECT ts_epoch,type,package,class FROM events')
    meta = database('SELECT key,value FROM meta')
    if rows is None or meta is None: return None
    meta = dict(meta)
    try:
        last = float(meta['last_fetch'])
        if not math.isfinite(last): last = None
    except (KeyError, ValueError, TypeError):
        last = None
    try:
        last_display = display(last, '%Y-%m-%d %H:%M')
    except (ValueError, OverflowError, OSError):
        last = last_display = None
    now = time.time()
    today = datetime.datetime.fromtimestamp(now).date()
    start = today if period == 'day' else today - datetime.timedelta(days=today.weekday()) if period == 'week' else today.replace(day=1)
    beginning = datetime.datetime.combine(start, datetime.time()).timestamp()
    end = datetime.datetime.combine(today + datetime.timedelta(days=1), datetime.time()).timestamp()
    totals = calc(rows, now, last, beginning, end)
    days = []
    if period != 'month':
        date = start
        while date <= today:
            lo = datetime.datetime.combine(date, datetime.time()).timestamp()
            hi = datetime.datetime.combine(date + datetime.timedelta(days=1), datetime.time()).timestamp()
            values = calc(rows, now, last, lo, hi)
            days.append({'date': str(date), 'minutes': {name: int(values.get(pkg, 0) // 60) for pkg, name in APPS.items()}})
            date += datetime.timedelta(days=1)
    return {'range': period, 'from': str(start), 'apps': [{'name': name, 'minutes': int(totals.get(pkg, 0) // 60)} for pkg, name in APPS.items()], 'days': days, 'data_since': display(min(row[0] for row in rows), '%Y-%m-%d %H:%M') if rows else None, 'last_fetch': last_display}


def tokens():
    try:
        data = json.loads(TOKEN.read_text())
        if not isinstance(data, dict) or not all(isinstance(k, str) and isinstance(v, str) for k, v in data.items()):
            raise ValueError('invalid token file')
        return {k: v for k, v in data.items() if v}
    except (OSError, ValueError, UnicodeError):
        log('token', 'invalid file')
        return {}


def links(port):
    data = tokens()
    try:
        name = subprocess.check_output(['scutil', '--get', 'LocalHostName'], text=True, timeout=3).strip()
        ip = subprocess.check_output(['ipconfig', 'getifaddr', 'en0'], text=True, timeout=3).strip()
    except (OSError, subprocess.SubprocessError):
        print('無法取得本機名稱或 en0 IP', file=sys.stderr)
        return 1
    for device, token in data.items():
        print(f'{device}: http://{name}.local:{port}/#t={token}')
        print(f'{device}: http://{ip}:{port}/#t={token}')
    return 0


PAGE = r'''<!doctype html><html lang="zh-Hant"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>電視時間管理</title><style>
*{box-sizing:border-box;overflow-wrap:anywhere}body{font:17px system-ui;margin:auto;max-width:650px;padding:16px;background:#f6f7fb;color:#18243a}section{background:white;border-radius:12px;padding:16px;margin:12px 0}button,input{font:inherit;padding:10px;margin:4px}button{background:#2458a6;color:white;border:0;border-radius:8px}button:disabled{opacity:.45}section{max-width:100%}.buttons,#days{display:flex;flex-wrap:wrap}.usage-row{display:grid;grid-template-columns:5em minmax(0,1fr) auto;gap:8px;align-items:center}progress{width:100%}small{color:#49566a}li{margin:8px 0}
</style><h1>電視時間管理</h1><p id="message"></p><section><h2>狀態</h2><p id="status"></p><div class="buttons"><button onclick="action('/open?min=15')">開放 15 分</button><button onclick="action('/open?min=30')">開放 30 分</button><button onclick="action('/open?min=60')">開放 60 分</button><button onclick="action('/lock')">立即鎖</button></div><p id="result"></p></section><section><h2>前景時間</h2><button onclick="loadUsage('day')">今天</button><button onclick="loadUsage('week')">本週</button><button onclick="loadUsage('month')">本月</button><div id="usage"></div><small>前景時間不等於實際觀看；Mac 離家超過 24 小時可能少算，HDMI 或無事件時最多多算 4 小時；資料從服務上線開始。</small></section><section><h2>紀錄</h2><ul id="logs"></ul></section><section><h2>排程</h2><div id="rules"></div><form id="schedule"><div id="days"></div><input type="time" id="clock" required><button>新增</button></form></section><footer id="device"></footer><script>
const hash=new URLSearchParams(location.hash.slice(1)).get('t');let token=hash||localStorage.getItem('t');if(hash)localStorage.setItem('t',hash);if(!token)document.getElementById('message').textContent='請用設備連結開啟';let range='day';
async function api(path,body){let r=await fetch(path,{method:'POST',headers:{'X-Token':token||'','Content-Type':'application/json'},body:body?JSON.stringify(body):''});let data=await r.json();if(!r.ok)throw Error(r.status===401?'設備鑰匙無效':(data.error||'請求失敗'));return data}
const el=id=>document.getElementById(id);function text(tag,value){let e=document.createElement(tag);e.textContent=value;return e}
async function refresh(){if(!token)return;try{let s=await api('/status');el('message').textContent='';el('status').textContent=(s.authorized?'已授權到 '+s.until:'未授權')+'；電視：'+({open:'已開放',locked:'已鎖',mixed:'部分鎖定',unknown:'無法確認'}[s.tv])+'；最後確認：'+(s.tv_checked_at||'無');el('device').textContent='目前設備：'+s.device;await Promise.all([loadUsage(range),loadLogs(),loadRules()])}catch(e){el('message').textContent=e.message}}
async function action(path){let bs=document.querySelectorAll('.buttons button');bs.forEach(b=>b.disabled=true);try{let r=await api(path);el('result').textContent=r.ok?'成功'+(r.until?'，已授權到 '+r.until:''):'電視沒回應：'+(r.note||r.error||'');refresh()}catch(e){el('result').textContent=e.message}finally{bs.forEach(b=>b.disabled=false)}}
async function loadUsage(r){range=r;let d=await api('/usage?range='+r);let root=el('usage');root.replaceChildren();for(let a of d.apps){let p=document.createElement('p'),bar=document.createElement('progress');p.className='usage-row';bar.max=Math.max(60,...d.apps.map(x=>x.minutes));bar.value=a.minutes;p.append(text('span',a.name),bar,text('span',a.minutes+' 分鐘'));root.append(p)}for(let day of d.days)root.append(text('p',day.date+'：'+Object.values(day.minutes).reduce((a,b)=>a+b,0)+' 分鐘'));root.append(text('p','資料起點：'+(d.data_since||'無')+'；最後抓取時間：'+(d.last_fetch||'無')))}
const labels={open:'開放',lock:'立即鎖',expire:'到期',schedule:'排程切斷','schedule-skipped':'排程錯過',relock:'對帳鎖回',away:'離開時開放',grace:'回家寬限','schedule-add':'新增排程','schedule-delete':'刪除排程'};
async function loadLogs(){let d=await api('/log');el('logs').replaceChildren();for(let x of d.items)el('logs').append(text('li',[x.time.slice(5,16),x.device,labels[x.action]||x.action,x.minutes?x.minutes+' 分鐘':'',x.until||''].join(' ')))}
async function loadRules(){let d=await api('/schedule');el('rules').replaceChildren();for(let r of d.items){let days=[...r.days].sort((a,b)=>a-b),names=['一','二','三','四','五','六','日'],label=days.length>1&&days.every((n,i)=>n===days[0]+i)?'週'+names[days[0]-1]+'～週'+names[days.at(-1)-1]:'週'+days.map(n=>names[n-1]).join('、');let p=text('p',label+' '+r.time),b=text('button','刪除');b.onclick=async()=>{try{await api('/schedule/delete',{id:r.id});refresh()}catch(e){el('message').textContent=e.message}};p.append(b);el('rules').append(p)}}
for(let n=1;n<=7;n++){let label=document.createElement('label'),c=document.createElement('input');c.type='checkbox';c.value=n;label.append(c,document.createTextNode('週'+['一','二','三','四','五','六','日'][n-1]));el('days').append(label)}el('schedule').onsubmit=async e=>{e.preventDefault();try{await api('/schedule/add',{days:[...el('days').querySelectorAll(':checked')].map(x=>+x.value),time:el('clock').value});refresh()}catch(err){el('message').textContent=err.message}};refresh();setInterval(refresh,15000);
</script></html>'''


class Handler(BaseHTTPRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(15)

    def log_message(self, fmt, *args):
        log('http', fmt % args)

    def reply(self, code, obj):
        data = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(data)))
        self.send_header('Cache-Control', 'no-store')
        self.end_headers()
        if self.command != 'HEAD': self.wfile.write(data)

    def send_error(self, code, message=None, explain=None):
        if code == 501:
            return self.reply(405, {'error': 'method not allowed'})
        return super().send_error(code, message, explain)

    def do_GET(self):
        if urlsplit(self.path).path != '/':
            return self.reply(405, {'error': 'method not allowed'})
        data = PAGE.encode()
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Cache-Control', 'no-store')
        self.send_header('Content-Length', str(len(data)))
        self.end_headers()
        if self.command != 'HEAD': self.wfile.write(data)
    do_HEAD = do_GET
    def other_method(self):
        self.reply(405, {'error': 'method not allowed'})
    do_PUT = do_DELETE = do_PATCH = do_OPTIONS = other_method

    def body(self):
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if length < 0 or length > 65536: return None
            return json.loads(self.rfile.read(length))
        except (ValueError, UnicodeError):
            return None

    def do_POST(self):
        global until, grace
        given = self.headers.get('X-Token', '').encode('utf-8')
        device = next((name for name, value in tokens().items() if hmac.compare_digest(given, value.encode('utf-8'))), None)
        if device is None:
            log('token', 'unauthorized')
            return self.reply(401, {'error': 'unauthorized'})
        url = urlsplit(self.path)
        if url.path == '/status':
            with state_lock:
                return self.reply(200, {'authorized': authorized(), 'until': display(until) if authorized() else None, 'tv': tv, 'tv_checked_at': checked_at, 'device': device})
        if url.path == '/usage':
            period = parse_qs(url.query).get('range', [''])[0]
            if period not in ('day', 'week', 'month'): return self.reply(400, {'error': 'invalid range'})
            result = usage(period)
            return self.reply(200, result) if result is not None else self.reply(503, {'error': 'db'})
        if url.path == '/log':
            rows = database('SELECT ts_epoch,device,action,minutes,until,detail FROM log ORDER BY rowid DESC LIMIT 30')
            if rows is None: return self.reply(503, {'error': 'db'})
            return self.reply(200, {'items': [{'time': display(r[0], '%Y-%m-%d %H:%M:%S'), 'device': r[1], 'action': r[2], 'minutes': r[3], 'until': r[4], 'detail': r[5]} for r in rows]})
        if url.path == '/schedule':
            rules = get_rules()
            return self.reply(200, {'items': rules}) if rules is not None else self.reply(503, {'error': 'db'})
        if url.path == '/schedule/add':
            value = self.body()
            if not isinstance(value, dict): return self.reply(400, {'error': 'invalid rule'})
            days, clock = value.get('days'), value.get('time')
            if not isinstance(days, list) or not days or any(type(x) is not int or x < 1 or x > 7 for x in days) or len(set(days)) != len(days) or not isinstance(clock, str) or not re.fullmatch(r'(?:[01][0-9]|2[0-3]):[0-5][0-9]', clock):
                return self.reply(400, {'error': 'invalid rule'})
            days = sorted(days)
            with db_lock:
                rules = get_rules()
                if rules is None: return self.reply(503, {'error': 'db'})
                old = next((r for r in rules if r['days'] == days and r['time'] == clock), None)
                if old: return self.reply(200, {'id': old['id']})
                if len(rules) >= 20: return self.reply(400, {'error': 'too many rules'})
                ident = database('INSERT INTO schedule(days,time) VALUES (?,?)', (json.dumps(days), clock), write=True)
            if ident is None: return self.reply(503, {'error': 'db'})
            log('schedule-add', clock, device=device, detail=clock)
            return self.reply(200, {'id': ident})
        if url.path == '/schedule/delete':
            value = self.body()
            if not isinstance(value, dict) or type(value.get('id')) is not int: return self.reply(400, {'error': 'invalid id'})
            with db_lock:
                rules = get_rules()
                if rules is None: return self.reply(503, {'error': 'db'})
                if not any(r['id'] == value['id'] for r in rules): return self.reply(404, {'error': 'not found'})
                result = database('DELETE FROM schedule WHERE id=?', (value['id'],), write=True)
            if result is None: return self.reply(503, {'error': 'db'})
            log('schedule-delete', str(value['id']), device=device, detail=str(value['id']))
            return self.reply(200, {'ok': True})
        if url.path not in ('/open', '/lock'):
            return self.reply(404, {'error': 'not found'})
        if url.path == '/open':
            mins = parse_qs(url.query, keep_blank_values=True).get('min', [])
            if len(mins) != 1 or mins[0] not in ('15', '30', '60'):
                return self.reply(400, {'error': 'invalid min'})
            value = time.time() + int(mins[0]) * 60
        else:
            value = None
        error = None
        with state_lock:
            grace = False
            try:
                save(value)
            except OSError as e:
                error = str(e)
            if error is None or value is None:
                if value is not None and until is not None and until <= time.time():
                    log('expire', 'expired')
                until = value
        if error and value is not None:
            return self.reply(500, {'ok': False, 'error': error})
        log('open' if value else 'lock', 'requested', device=device, minutes=int(mins[0]) if value else None, end=display(value))
        success = apply(deadline=time.monotonic() + 19, reconcile=False)
        if value is not None:
            with state_lock:
                success = success and authorized() and tv == 'open' and until == value
            if success:
                return self.reply(200, {'ok': True, 'until': display(value)})
            with state_lock:
                unchanged = until == value and authorized()
            note = '授權仍有效，電視恢復連線後會自動開放；要取消請按立即鎖' if unchanged else '授權已變更或取消'
            return self.reply(200, {'ok': False, 'error': '電視未確認開放', 'until': display(value), 'note': note})
        result = {'ok': success and not error}
        if error or not success:
            result['error'] = error or '電視未確認鎖定'
        self.reply(200, result)


def main():
    init_db()
    load()
    server = ThreadingHTTPServer(('0.0.0.0', int(os.environ.get('TVGATE_PORT', '8790'))), Handler)
    server.daemon_threads = True
    def terminate(signum, frame):
        stop.set()
        threading.Thread(target=server.shutdown, daemon=True).start()
    signal.signal(signal.SIGTERM, terminate)
    threading.Thread(target=hooks, daemon=True).start()
    threading.Thread(target=fetch_loop, daemon=True).start()
    threading.Thread(target=monitor, daemon=True).start()
    threading.Thread(target=apply, daemon=True).start()
    server.serve_forever(poll_interval=0.1)
    deadline = time.monotonic() + 7.8
    log('away', 'stop')
    apply(forced_open=True, deadline=deadline)
    with children_lock:
        for child in children:
            if child.poll() is None:
                child.kill()
    if hook and hook.poll() is None:
        hook.terminate()
        try:
            hook.wait(timeout=max(0.1, deadline - time.monotonic()))
        except subprocess.TimeoutExpired:
            hook.kill()
            hook.wait()
    server.server_close()


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--links', action='store_true')
    parser.add_argument('--port', type=int, default=int(os.environ.get('TVGATE_PORT', '8790')))
    args = parser.parse_args()
    if args.links:
        sys.exit(links(args.port))
    os.environ['TVGATE_PORT'] = str(args.port)
    main()
