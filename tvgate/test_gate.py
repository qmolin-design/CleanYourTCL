import concurrent.futures
import contextlib
import datetime
import importlib.util
import sqlite3
import threading
import json
import os
from pathlib import Path
import signal
import socket
import subprocess
import sys
sys.dont_write_bytecode = True
import tempfile
import time
import unittest
from unittest import mock
import urllib.error
import urllib.request

HERE = Path(__file__).resolve().parent
DEVICE = '192.0.2.69:5555'
PACKAGE = 'com.google.android.youtube.tv'
NETFLIX = 'com.netflix.ninja'
PACKAGES = (PACKAGE, NETFLIX)
ENABLE = ['-s', DEVICE, 'shell', '; '.join('pm enable --user 0 ' + p for p in PACKAGES)]
FORCE_STOP = ['-s', DEVICE, 'shell', '; '.join('am force-stop ' + p for p in PACKAGES)]
DISABLE = ['-s', DEVICE, 'shell', '; '.join('pm disable-user --user 0 ' + p for p in PACKAGES)]
DUMPSYS = ['-s', DEVICE, 'shell', 'dumpsys', 'activity', 'activities']
ADB_SCRIPT = '''#!/usr/bin/env python3
import json, os, sys, time
from pathlib import Path
p = Path(os.environ['TVGATE_HOME'])
a = sys.argv[1:]
with (p/'calls').open('a') as f: f.write(json.dumps(a)+'\\n')
mode = (p/'mode').read_text().strip() if (p/'mode').exists() else ''
cmd = ' '.join(a[a.index('shell')+1:]) if 'shell' in a else ''
PK = ['com.google.android.youtube.tv', 'com.netflix.ninja']
# locked 檔：'1' 兩個都停用、'0' 都啟用，其他為停用套件清單（逗號分隔）
raw = (p/'locked').read_text().strip() if (p/'locked').exists() else '0'
off = set(PK) if raw == '1' else set() if raw == '0' else set(x for x in raw.split(',') if x)
def save(): (p/'locked').write_text('1' if off == set(PK) else '0' if not off else ','.join(sorted(off)))
if mode == 'hang' and 'list' in a: time.sleep(30)
if mode == 'slow' and 'pm enable' in cmd: time.sleep(1.2)
if mode == 'fail' or (mode == 'foreground_fail' and 'dumpsys' in a): sys.exit(1)
if 'list' in a:
    if mode == 'empty': sys.exit(0)
    print('package:com.example.disabled')
    print('package:com.google.android.youtube.tvmusic')
    for x in sorted(off): print('package:'+x)
elif 'pm enable' in cmd or 'pm disable-user' in cmd:
    refuse = (p/'refuse').read_text().strip() if (p/'refuse').exists() else ''
    for part in cmd.split(';'):
        w = part.split()
        if w[1] == 'enable': off.discard(w[-1])
        elif w[-1] != refuse: off.add(w[-1])
    save()
elif 'usagestats' in a:
    if mode == 'usage_hang': time.sleep(20)
    if mode == 'usage_fail': sys.exit(1)
    print((p/'usage').read_text() if (p/'usage').exists() else 'Last 24 hour events\\nIn-memory daily stats')
elif 'dumpsys' in a:
    if (p/'arrival_open').exists():
        (p/'locked').write_text('0')
        (p/'arrival_open').unlink()
    print('mResumedActivity: '+{'1': PK[0], 'nf': PK[1]}.get((p/'front').read_text(), 'other'))
'''
HOOK_SCRIPT = '''#!/usr/bin/env python3
import os, sys, time
from pathlib import Path
p=Path(os.environ['TVGATE_HOME'])
with (p/'hookstarts').open('a') as f: f.write('start\\n')
while True:
    event=p/'event'
    if event.exists():
        msg=event.read_text(); event.unlink()
        if msg == 'exit': sys.exit(0)
        print(msg, flush=True)
        if msg == 'sleep': (p/'answer').write_text(sys.stdin.readline().strip())
    time.sleep(.03)
'''
NOTIFY_SCRIPT = '''#!/usr/bin/env python3
import os, sys
from pathlib import Path
(Path(os.environ['TVGATE_HOME'])/'notified').write_text(' '.join(sys.argv[1:]))
'''


def wait_for(predicate, timeout=5):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if predicate(): return
        time.sleep(.05)
    raise AssertionError('等待逾時')


class GateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.p = Path(self.temp.name)
        (self.p/'locked').write_text('1')
        (self.p/'front').write_text('0')
        (self.p/'token').write_text(json.dumps({'爸爸': 'test-secret', '媽媽': 'other-secret'}))
        for name, script in [('adb', ADB_SCRIPT), ('hook', HOOK_SCRIPT), ('notify', NOTIFY_SCRIPT)]:
            (self.p/name).write_text(script)
            (self.p/name).chmod(0o755)
        with socket.socket() as s:
            s.bind(('127.0.0.1', 0))
            self.port = s.getsockname()[1]
        self.env = dict(os.environ, HOME=str(self.p), TVGATE_HOME=str(self.p), TVGATE_TOKEN=str(self.p/'token'), ADB=str(self.p/'adb'), TVGATE_DEVICE=DEVICE, TVGATE_HOOK=str(self.p/'hook'), TVGATE_NOTIFY=str(self.p/'notify'), TVGATE_PORT=str(self.port), TVGATE_INTERVAL='0.4', TVGATE_LOG=str(self.p/'Library/Logs/tvgate.log'), TVGATE_HOME_NET=str(self.p/'home_net'), PYTHONDONTWRITEBYTECODE='1')
        (self.p/'home_net').write_text('1')
        self.proc = None
        self.start()

    def start(self):
        self.proc = subprocess.Popen([sys.executable, str(HERE/'gate.py')], env=self.env, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, start_new_session=True)
        wait_for(lambda: self.ready())
        wait_for(lambda: (self.p/'hookstarts').exists())

    def test_device_override(self):
        self.stop()
        (self.p/'calls').unlink(missing_ok=True)
        self.env['TVGATE_DEVICE'] = '192.0.2.123:5555'
        self.start()
        wait_for(lambda: any(a[:2] == ['-s', '192.0.2.123:5555'] for a in self.calls()))
        calls = self.calls()
        self.assertTrue(any(a[:2] == ['-s', '192.0.2.123:5555'] for a in calls))
        self.assertFalse(any(DEVICE in a for a in calls))

    def ready(self):
        try: return self.request('/status')[0] == 200
        except (OSError, urllib.error.URLError): return False

    def stop(self):
        if self.proc and self.proc.poll() is None:
            self.proc.terminate()
            try: self.proc.wait(timeout=9)
            except subprocess.TimeoutExpired:
                os.killpg(self.proc.pid, signal.SIGKILL)
                self.proc.wait()
                self.fail('gate did not stop')
        if self.proc:
            try: os.killpg(self.proc.pid, 0)
            except ProcessLookupError: pass
            else:
                os.killpg(self.proc.pid, signal.SIGKILL)
                self.fail('child process left behind')

    def tearDown(self):
        try: self.stop()
        finally: self.temp.cleanup()

    def request(self, path, token='test-secret', method='POST', timeout=25, body=None):
        headers = {} if token is None else {'X-Token': token}
        req = urllib.request.Request('http://127.0.0.1:%d%s' % (self.port, path), data=(json.dumps(body).encode() if body is not None else b'') if method == 'POST' else None, headers=headers, method=method)
        try: response = urllib.request.urlopen(req, timeout=timeout)
        except urllib.error.HTTPError as e: response = e
        with response: return response.status, json.load(response)

    def calls(self):
        try: return [json.loads(x) for x in (self.p/'calls').read_text().splitlines()]
        except FileNotFoundError: return []

    def event(self, event):
        (self.p/'event').write_text(event)
        wait_for(lambda: not (self.p/'event').exists())

    def state(self): return self.request('/status')[1]
    def locked(self): return (self.p/'locked').read_text() == '1'
    def lock_events(self):
        with contextlib.closing(sqlite3.connect(self.p/'tvgate.db')) as db, db:
            return db.execute("SELECT count(*) FROM events WHERE type='GATE_LOCK'").fetchone()[0]
    def lock_events_of(self, package):
        with contextlib.closing(sqlite3.connect(self.p/'tvgate.db')) as db, db:
            return db.execute("SELECT count(*) FROM events WHERE type='GATE_LOCK' AND package=?", (package,)).fetchone()[0]
    def two_cycles(self):
        before = len(self.calls())
        wait_for(lambda: sum('list' in a for a in self.calls()[before:]) >= 2, 8)
    def sleeping_cycles(self):
        target = time.time() + 3 * float(self.env['TVGATE_INTERVAL'])
        wait_for(lambda: (self.p/'state.json').exists() and json.loads((self.p/'state.json').read_text()).get('last_check', 0) >= target)
    def assert_lock_sequence(self, before):
        commands = [a for a in self.calls()[before:] if a in (FORCE_STOP, DISABLE)]
        self.assertTrue(any(commands[i:i+2] == [FORCE_STOP, DISABLE] for i in range(len(commands)-1)), commands)

    def test_auth_and_validation(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        self.assertTrue(self.request('/open?min=15')[1]['ok'])
        initial = self.state()
        before = len(self.calls())
        for path in ('/open?min=15', '/lock', '/status'):
            for token in (None, 'wrong'):
                self.assertEqual(self.request(path, token)[0], 401)
                current = self.state()
                self.assertEqual((current['authorized'], current['until']), (initial['authorized'], initial['until']))
        for path in ('/status', '/open', '/lock'):
            self.assertEqual(self.request(path, method='GET')[0], 405)
        self.assertEqual(self.request('/open?min=7')[0], 400)
        self.assertFalse(any(w in x for a in self.calls()[before:] for x in a for w in ('pm enable', 'pm disable-user', 'am force-stop')))

    def test_open_and_lock(self):
        before = len(self.calls())
        self.assertTrue(self.request('/open?min=15')[1]['ok'])
        self.assertIn(ENABLE, self.calls()[before:])
        self.assertEqual((self.state()['authorized'], self.state()['tv']), (True, 'open'))
        before = len(self.calls())
        events = self.lock_events()
        self.assertTrue(self.request('/lock')[1]['ok'])
        self.assertEqual(self.lock_events(), events + len(PACKAGES))
        self.assertEqual((self.state()['authorized'], self.state()['tv']), (False, 'locked'))
        self.assert_lock_sequence(before)
        self.two_cycles()
        self.assertNotIn('expire', [x['action'] for x in self.request('/log')[1]['items']])

    def test_expiry(self):
        self.stop()
        (self.p/'state.json').write_text(json.dumps({'until': time.time()+1.5}))
        (self.p/'locked').write_text('0')
        before = len(self.calls())
        events = self.lock_events()
        self.start()
        wait_for(lambda: self.locked(), 10)
        wait_for(lambda: self.lock_events() == events + len(PACKAGES))
        self.assert_lock_sequence(before)
        wait_for(lambda: self.state()['tv'] == 'locked')
        self.two_cycles()
        self.assertEqual(sum(x['action'] == 'expire' for x in self.request('/log')[1]['items']), 1)

    def test_reconcile_and_music(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        before = len(self.calls())
        start = time.monotonic()
        (self.p/'locked').write_text('0')
        limit = 3 * float(self.env['TVGATE_INTERVAL'])
        wait_for(self.locked, limit)
        self.assertLess(time.monotonic() - start, limit)
        self.assert_lock_sequence(before)
        before = len(self.calls())
        self.assertTrue(self.request('/open?min=15')[1]['ok'])
        self.assertIn(ENABLE, self.calls()[before:])
        self.assertEqual(self.state()['tv'], 'open')

    def test_read_failure(self):
        for mode in ('empty', 'fail'):
            (self.p/'mode').write_text('')
            wait_for(lambda: self.state()['tv'] in ('open', 'locked'))
            before = len(self.calls())
            (self.p/'mode').write_text(mode)
            wait_for(lambda: self.state()['tv'] == 'unknown')
            result = self.request('/open?min=15')[1]
            self.assertFalse(result['ok'])
            self.assertIn('until', result)
            self.assertIn(['connect', DEVICE], self.calls()[before:])

    def test_hung_adb_status_responsive(self):
        (self.p/'mode').write_text('hang')
        with concurrent.futures.ThreadPoolExecutor() as pool:
            start = time.monotonic()
            future = pool.submit(self.request, '/open?min=15')
            wait_for(lambda: any('list' in a for a in self.calls()))
            self.assertEqual(self.request('/status', timeout=2)[0], 200)
            self.assertFalse(future.result(timeout=20)[1]['ok'])
            self.assertLess(time.monotonic()-start, 20)
        (self.p/'mode').write_text('')

    def test_concurrent_lock(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        (self.p/'mode').write_text('slow')
        before = len(self.calls())
        with concurrent.futures.ThreadPoolExecutor() as pool:
            f = pool.submit(self.request, '/open?min=15')
            wait_for(lambda: ENABLE in self.calls()[before:])
            before_lock = len(self.calls())
            lock = pool.submit(self.request, '/lock')
            f.result(timeout=10); lock.result(timeout=10)
        self.assertEqual((self.state()['authorized'], self.state()['tv']), (False, 'locked'))
        self.assertTrue(self.locked())
        self.assert_lock_sequence(before_lock)

    def test_hook_sleep_wake_and_grace(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        before = len(self.calls())
        self.event('sleep')
        wait_for(lambda: (self.p/'answer').exists())
        self.assertEqual((self.p/'answer').read_text(), 'ok')
        self.assertIn(ENABLE, self.calls()[before:])
        self.assertFalse(self.locked())
        before = len(self.calls())
        self.event('wake')
        wait_for(self.locked)
        self.assert_lock_sequence(before)
        (self.p/'answer').unlink()
        self.event('sleep')
        wait_for(lambda: (self.p/'answer').exists())
        self.assertEqual((self.p/'answer').read_text(), 'ok')
        (self.p/'locked').write_text('0')
        (self.p/'front').write_text('1')
        before = len(self.calls())
        relocks = sum(x['action']=='relock' for x in self.request('/log')[1]['items'])
        self.sleeping_cycles()
        self.assertFalse(self.locked())
        self.assertNotIn(FORCE_STOP, self.calls()[before:])
        self.assertEqual(sum(x['action']=='relock' for x in self.request('/log')[1]['items']), relocks)
        self.event('wake')
        wait_for(lambda: (self.p/'notified').exists())
        self.assertFalse(self.locked())
        self.assertIn('grace', [x['action'] for x in self.request('/log')[1]['items']])
        (self.p/'notified').unlink()
        before = len(self.calls())
        (self.p/'front').write_text('0')
        wait_for(self.locked, float(self.env['TVGATE_INTERVAL']) + .3)
        self.assert_lock_sequence(before)
        (self.p/'answer').unlink()
        (self.p/'mode').write_text('fail')
        self.event('sleep')
        wait_for(lambda: (self.p/'answer').exists())
        self.assertEqual((self.p/'answer').read_text(), 'fail')
        (self.p/'mode').write_text('')
        (self.p/'front').write_text('1')
        (self.p/'locked').write_text('0')
        before = len(self.calls())
        self.sleeping_cycles()
        self.assertFalse(self.locked())
        self.assertNotIn(FORCE_STOP, self.calls()[before:])
        self.event('wake')
        wait_for(lambda: (self.p/'notified').exists())
        count = len(self.calls())
        wait_for(lambda: sum('list' in a for a in self.calls()[count:]) >= 2)
        self.assertFalse(self.locked())
        self.assertIn('grace', (self.p/'Library/Logs/tvgate.log').read_text())
        before = len(self.calls())
        (self.p/'mode').write_text('foreground_fail')
        wait_for(lambda: DUMPSYS in self.calls()[before:])
        count = len(self.calls())
        wait_for(lambda: sum('list' in a for a in self.calls()[count:]) >= 2)
        self.assertFalse(self.locked())
        self.assertNotIn(FORCE_STOP, self.calls()[before:])
        (self.p/'mode').write_text('')
        before = len(self.calls())
        (self.p/'front').write_text('0')
        wait_for(self.locked)
        self.assert_lock_sequence(before)
        (self.p/'notified').unlink()
        (self.p/'front').write_text('1')
        (self.p/'locked').write_text('0')
        self.event('wake')
        wait_for(lambda: (self.p/'notified').exists())
        count = len(self.calls())
        wait_for(lambda: sum('list' in a for a in self.calls()[count:]) >= 2)
        self.assertFalse(self.locked())
        before = len(self.calls())
        start = time.monotonic()
        self.assertTrue(self.request('/lock')[1]['ok'])
        self.assertLess(time.monotonic() - start, 1)
        self.assertEqual((self.state()['tv'], self.state()['authorized']), ('locked', False))
        self.assert_lock_sequence(before)

    def test_sleep_expiry_waits_for_wake(self):
        self.stop()
        end = time.time() + 3
        (self.p/'state.json').write_text(json.dumps({'until': end}))
        self.start()
        self.event('sleep')
        wait_for(lambda: (self.p/'answer').exists())
        self.assertEqual((self.p/'answer').read_text(), 'ok')
        wait_for(lambda: json.loads((self.p/'state.json').read_text()).get('last_check', 0) > end + 1.2, 8)
        self.assertFalse(self.locked())
        self.assertNotIn('expire', [x['action'] for x in self.request('/log')[1]['items']])
        self.assertEqual(json.loads((self.p/'state.json').read_text())['until'], end)
        self.event('wake')
        wait_for(self.locked)
        wait_for(lambda: sum(x['action']=='expire' for x in self.request('/log')[1]['items']) == 1)
        self.two_cycles()
        self.assertEqual(sum(x['action']=='expire' for x in self.request('/log')[1]['items']), 1)

    def test_sleep_schedule_is_skipped(self):
        self.stop()
        fixed = datetime.datetime(2026, 10, 26, 12, 0)
        clock = self.p/'clock'
        clock.write_text(str(fixed.timestamp()))
        self.env['TVGATE_CLOCK'] = str(clock)
        self.start()
        wait_for(lambda: self.state()['tv'] == 'locked')
        wait_for(lambda: (self.p/'state.json').exists() and json.loads((self.p/'state.json').read_text()).get('last_check') == fixed.timestamp())
        target = fixed + datetime.timedelta(minutes=1)
        self.request('/schedule/add', body={'days': [target.isoweekday()], 'time': target.strftime('%H:%M')})
        self.event('sleep')
        wait_for(lambda: (self.p/'answer').exists())
        self.assertEqual((self.p/'answer').read_text(), 'ok')
        before = len(self.calls())
        clock.write_text(str(target.timestamp()+1))
        wait_for(lambda: any(x['action']=='schedule-skipped' for x in self.request('/log')[1]['items']))
        items = self.request('/log')[1]['items']
        self.assertFalse(self.locked())
        self.assertNotIn(FORCE_STOP, self.calls()[before:])
        self.assertNotIn('schedule', [x['action'] for x in items])
        skipped = [x for x in items if x['action']=='schedule-skipped']
        self.assertEqual(len(skipped), 1)
        self.assertIn('Mac 休眠', skipped[0]['detail'])

    def test_sleep_pauses_usage_fetch(self):
        self.stop()
        self.env['TVGATE_FETCH_INTERVAL'] = '0.1'
        self.start()
        wait_for(lambda: any('usagestats' in a for a in self.calls()))
        self.event('sleep')
        wait_for(lambda: (self.p/'answer').exists())
        before = sum('usagestats' in a for a in self.calls())
        self.sleeping_cycles()
        self.assertEqual(sum('usagestats' in a for a in self.calls()), before)
        self.event('wake')
        wait_for(lambda: sum('usagestats' in a for a in self.calls()) > before)

    def test_wake_foreground_failure(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        (self.p/'mode').write_text('foreground_fail')
        (self.p/'locked').write_text('0')
        before = len(self.calls())
        self.event('wake')
        wait_for(lambda: DUMPSYS in self.calls()[before:])
        count = len(self.calls())
        wait_for(lambda: sum('list' in a for a in self.calls()[count:]) >= 2)
        self.assertFalse(self.locked())
        self.assertFalse((self.p/'notified').exists())
        self.assertNotIn(FORCE_STOP, self.calls()[before:])
        before = len(self.calls())
        (self.p/'mode').write_text('')
        wait_for(self.locked)
        self.assert_lock_sequence(before)
        self.assertFalse((self.p/'notified').exists())

    def test_no_grace_without_wake(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        (self.p/'front').write_text('1')
        (self.p/'locked').write_text('0')
        wait_for(self.locked)
        self.assertFalse((self.p/'notified').exists())

    def test_hook_restart(self):
        self.event('exit')
        wait_for(lambda: (self.p/'hookstarts').read_text().count('start') >= 2)
        self.assertIn('restarting', (self.p/'Library/Logs/tvgate.log').read_text())

    def test_restart_and_corrupt_state(self):
        self.request('/open?min=15')
        until = self.state()['until']
        self.stop(); self.start()
        self.assertTrue(self.state()['authorized'])
        self.assertEqual(self.state()['until'], until)
        self.stop()
        (self.p/'state.json').write_text('bad')
        self.start()
        self.assertFalse(self.state()['authorized'])

    def test_invalid_state_and_schedule(self):
        for content in ('[]', '"x"', '5', 'null', '{"until":1e300}',
                        '{"until":"abc"}', '{"last_check":1e18}'):
            with self.subTest(content=content):
                self.stop()
                (self.p/'state.json').write_text(content)
                log_path = self.p/'Library/Logs/tvgate.log'
                before_log = log_path.read_text()
                self.start()
                status, state = self.request('/status')
                self.assertEqual(status, 200)
                self.assertFalse(state['authorized'])
                self.assertIn('state invalid', log_path.read_text()[len(before_log):])
                self.assertTrue(self.request('/open?min=15')[1]['ok'])
        clock = self.p/'clock'
        now = datetime.datetime.now().replace(second=0, microsecond=0)
        clock.write_text(str(now.timestamp()))
        self.stop()
        (self.p/'state.json').write_text('{"last_check":1e18}')
        self.env['TVGATE_CLOCK'] = str(clock)
        self.start()
        wait_for(lambda: json.loads((self.p/'state.json').read_text()).get('last_check') == now.timestamp())
        self.assertTrue(self.request('/open?min=15')[1]['ok'])
        target = now + datetime.timedelta(minutes=1)
        self.request('/schedule/add', body={'days':[target.isoweekday()], 'time':target.strftime('%H:%M')})
        clock.write_text(str(target.timestamp()+1))
        wait_for(lambda: any(x['action'] == 'schedule' for x in self.request('/log')[1]['items']))
        wait_for(self.locked)
        self.two_cycles()
        self.assertNotIn('expire', [x['action'] for x in self.request('/log')[1]['items']])

    def test_open_overwrites_expired_once(self):
        self.stop()
        self.env['TVGATE_INTERVAL'] = '3'
        (self.p/'state.json').write_text(json.dumps({'until': time.time()-1}))
        self.start()
        self.assertTrue(self.request('/open?min=15')[1]['ok'])
        wait_for(lambda: sum(x['action'] == 'expire' for x in self.request('/log')[1]['items']) == 1)
        self.two_cycles()
        self.assertEqual(sum(x['action'] == 'expire' for x in self.request('/log')[1]['items']), 1)

    def test_logs_do_not_contain_keys(self):
        for key in ('test-secret', 'other-secret'):
            self.assertTrue(self.request('/open?min=15', key)[1]['ok'])
            self.assertTrue(self.request('/lock', key)[1]['ok'])
        self.assertEqual(self.request('/status', 'unknown-secret')[0], 401)
        text_log = (self.p/'Library/Logs/tvgate.log').read_text()
        with contextlib.closing(sqlite3.connect(self.p/'tvgate.db')) as db, db:
            db_log = str(db.execute('SELECT * FROM log').fetchall())
        for key in ('test-secret', 'other-secret', 'unknown-secret'):
            self.assertNotIn(key, text_log)
            self.assertNotIn(key, db_log)

    def test_unwritable_open(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        before = len(self.calls())
        self.p.chmod(0o500)
        try:
            self.assertFalse(self.request('/open?min=15')[1]['ok'])
            self.assertFalse(any('pm enable' in x for a in self.calls()[before:] for x in a))
        finally:
            self.p.chmod(0o700)

    def test_sigterm_opens(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        before = len(self.calls())
        self.stop()
        self.assertFalse(self.locked())
        self.assertIn(ENABLE, self.calls()[before:])


    def test_devices_and_revocation(self):
        for token, name in [('test-secret', '爸爸'), ('other-secret', '媽媽')]:
            self.assertTrue(self.request('/open?min=15', token)[1]['ok'])
            self.assertTrue(self.request('/lock', token)[1]['ok'])
            rows = self.request('/log')[1]['items']
            self.assertEqual([(r['action'], r['device']) for r in rows if r['action'] in ('open', 'lock')][:2], [('lock', name), ('open', name)])
        before = self.request('/log')[1]['items']
        for content in ('bad', json.dumps({'爸爸': 4}), None):
            if content is None: (self.p/'token').unlink()
            else: (self.p/'token').write_text(content)
            self.assertEqual(self.request('/open?min=15')[0], 401)
        (self.p/'token').write_text(json.dumps({'爸爸': 'test-secret', '媽媽': 'other-secret'}))
        self.assertEqual(self.request('/lock', 'wrong')[0], 401)
        self.assertEqual(self.request('/log')[1]['items'], before)
        (self.p/'token').write_text(json.dumps({'爸爸': 'test-secret', '媽媽': ''}))
        self.assertEqual(self.request('/status')[0], 200)
        self.assertEqual(self.request('/status', '')[0], 401)
        (self.p/'token').write_text(json.dumps({'爸爸': 'test-secret'}))
        self.assertEqual(self.request('/status', 'other-secret')[0], 401)
        self.assertEqual(self.request('/status')[0], 200)

    def test_schedule_validation_and_logs(self):
        for data in ({'days': [], 'time': '07:40'}, {'days': [0], 'time': '07:40'}, {'days': [1,1], 'time': '07:40'}, {'days': [1], 'time': '25:00'}, {'days': [1], 'time': '7:40'}):
            self.assertEqual(self.request('/schedule/add', body=data)[0], 400)
        self.assertEqual(self.request('/schedule/add')[0], 400)
        req = urllib.request.Request('http://127.0.0.1:%d/schedule/add' % self.port, data=b'not json', headers={'X-Token':'test-secret'}, method='POST')
        with self.assertRaises(urllib.error.HTTPError) as error: urllib.request.urlopen(req)
        error.exception.close()
        self.assertEqual(error.exception.code, 400)
        rule = {'days': [1], 'time': '07:40'}
        ident = self.request('/schedule/add', body=rule)[1]['id']
        self.assertEqual(self.request('/schedule/add', body=rule)[1]['id'], ident)
        for n in range(19):
            self.assertEqual(self.request('/schedule/add', body={'days': [2], 'time': '08:%02d' % n})[0], 200)
        self.assertEqual(self.request('/schedule/add', body={'days': [3], 'time': '08:30'})[0], 400)
        self.assertEqual(self.request('/schedule/delete', body={'id': 999})[0], 404)
        self.assertEqual(self.request('/schedule/delete', body={'id': ident})[0], 200)
        actions = [x['action'] for x in self.request('/log')[1]['items']]
        self.assertIn('schedule-add', actions); self.assertIn('schedule-delete', actions)
        self.assertEqual(self.request('/usage?range=year')[0], 400)

    def test_db_corrupt(self):
        self.stop()
        for suffix in ('-wal', '-shm'):
            (self.p/('tvgate.db'+suffix)).unlink(missing_ok=True)
        (self.p/'tvgate.db').write_text('garbage')
        self.start()
        self.assertTrue(self.request('/open?min=15')[1]['ok'])
        self.assertTrue(self.request('/lock')[1]['ok'])
        self.assertEqual(self.request('/usage?range=day'), (503, {'error': 'db'}))
        self.assertEqual(self.request('/log')[0], 503)
        self.assertEqual(self.request('/schedule')[0], 503)

    def test_web_page(self):
        url = 'http://127.0.0.1:%d/' % self.port
        with urllib.request.urlopen(url) as response:
            page = response.read().decode()
            self.assertEqual(response.status, 200)
            self.assertEqual(response.headers['Cache-Control'], 'no-store')
        for word in ('前景時間', '紀錄', '排程', 'box-sizing', 'flex-wrap'): self.assertIn(word, page)
        for secret in ('test-secret', 'other-secret'): self.assertNotIn(secret, page)
        with urllib.request.urlopen(urllib.request.Request(url, method='HEAD')) as response:
            self.assertEqual(response.read(), b'')
        self.assertEqual(self.request('/status', method='GET')[0], 405)

    def test_page_disables_buttons_during_action(self):
        with urllib.request.urlopen('http://127.0.0.1:%d/' % self.port) as response:
            page = response.read().decode()
        for word in ('.buttons button', 'disabled=true', 'disabled=false', 'finally', 'button:disabled', '部分鎖定'): self.assertIn(word, page)
        self.assertLess(page.index('disabled=true'), page.index('await api(path)'))

    def test_both_packages_locked_and_enabled(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        before = len(self.calls())
        self.assertTrue(self.request('/open?min=15')[1]['ok'])
        calls = self.calls()[before:]
        self.assertEqual(calls.count(ENABLE), 1)
        self.assertEqual((self.p/'locked').read_text(), '0')
        for p in PACKAGES: self.assertIn('pm enable --user 0 ' + p, ENABLE[-1])
        before = len(self.calls())
        self.assertTrue(self.request('/lock')[1]['ok'])
        calls = self.calls()[before:]
        self.assertEqual((calls.count(FORCE_STOP), calls.count(DISABLE)), (1, 1))
        self.assertLess(calls.index(FORCE_STOP), calls.index(DISABLE))
        for p in PACKAGES: self.assertIn('pm disable-user --user 0 ' + p, DISABLE[-1])
        self.assertEqual((self.p/'locked').read_text(), '1')
        self.assertEqual([self.lock_events_of(p) for p in PACKAGES], [1, 1])

    def test_partial_lock_is_mixed_and_fixed_on_retry(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        (self.p/'refuse').write_text(NETFLIX)
        (self.p/'locked').write_text('0')
        wait_for(lambda: self.state()['tv'] == 'mixed')
        self.assertEqual((self.p/'locked').read_text(), PACKAGE)
        self.assertEqual(self.state()['authorized'], False)
        before = len(self.calls())
        wait_for(lambda: self.calls()[before:].count(DISABLE) >= 2, 8)
        self.assertEqual(self.state()['tv'], 'mixed')
        wait_for(lambda: self.lock_events_of(PACKAGE) >= 1)
        self.assertEqual(self.lock_events_of(NETFLIX), 0)
        (self.p/'refuse').unlink()
        wait_for(lambda: self.state()['tv'] == 'locked', 8)
        self.assertEqual((self.p/'locked').read_text(), '1')
        self.assertEqual(self.lock_events_of(NETFLIX), 1)

    def test_partial_state_is_corrected_when_open(self):
        self.assertTrue(self.request('/open?min=15')[1]['ok'])
        (self.p/'locked').write_text(NETFLIX)
        wait_for(lambda: (self.p/'locked').read_text() == '0', 8)
        wait_for(lambda: self.state()['tv'] == 'open')

    def test_netflix_foreground_gets_grace(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        self.event('sleep')
        wait_for(lambda: (self.p/'answer').exists())
        self.assertEqual((self.p/'answer').read_text(), 'ok')
        (self.p/'locked').write_text('0')
        (self.p/'front').write_text('nf')
        before = len(self.calls())
        self.event('wake')
        wait_for(lambda: (self.p/'notified').exists())
        self.assertIn('Netflix', (self.p/'notified').read_text())
        self.assertFalse(self.locked())
        self.assertIn('grace', [x['action'] for x in self.request('/log')[1]['items']])
        self.assertNotIn(FORCE_STOP, self.calls()[before:])
        (self.p/'front').write_text('0')
        wait_for(self.locked, 5)
        self.assert_lock_sequence(before)

    def test_concurrent_endpoints(self):
        def work(i):
            for _ in range(5):
                for path in ('/usage?range=day', '/log', '/open?min=15', '/status'):
                    status, _ = self.request(path)
                    self.assertEqual(status, 200)
        with concurrent.futures.ThreadPoolExecutor(max_workers=20) as pool:
            list(pool.map(work, range(20)))

    def test_usage_fetch_dedup_and_failure_isolated(self):
        self.stop()
        now = datetime.datetime(2099, 10, 26, 12, 0)
        earlier = now - datetime.timedelta(minutes=20)
        stamp = lambda x: x.strftime('%Y-%m-%d %H:%M:%S')
        (self.p/'usage').write_text('Last 24 hour events\n time="%s" type=ACTIVITY_RESUMED package=%s class=X\n time="%s" type=SCREEN_NON_INTERACTIVE package=android\n time="%s" type=UNKNOWN_TYPE_33 package=x\nIn-memory daily stats\n time="%s" type=ACTIVITY_RESUMED package=com.netflix.ninja class=X\n' % (stamp(earlier), PACKAGE, stamp(now), stamp(now), stamp(now)))
        self.env['TVGATE_FETCH_INTERVAL'] = '1'
        (self.p/'locked').write_text('1')
        with contextlib.closing(sqlite3.connect(self.p/'tvgate.db')) as c, c:
            c.execute('delete from events')
            c.execute('delete from meta')
        self.start()
        def meta():
            with contextlib.closing(sqlite3.connect(self.p/'tvgate.db')) as c:
                return dict(c.execute('select key,value from meta'))
        wait_for(lambda: 'last_fetch' in meta())
        wait_for(lambda: self.state()['tv'] == 'locked')
        first_fetch = float(meta()['last_fetch'])
        with contextlib.closing(sqlite3.connect(self.p/'tvgate.db')) as c, c:
            count = c.execute('SELECT count(*) FROM events').fetchone()[0]
            self.assertEqual(count, 2)
            self.assertEqual(c.execute("select count(*) from events where type='ACTIVITY_RESUMED' and package='com.netflix.ninja'").fetchone()[0], 0)
            self.assertEqual(c.execute("select count(*) from events where type like 'UNKNOWN%'").fetchone()[0], 0)
        wait_for(lambda: sum('usagestats' in a for a in self.calls()) >= 2)
        wait_for(lambda: float(meta()['last_fetch']) > first_fetch)
        with contextlib.closing(sqlite3.connect(self.p/'tvgate.db')) as c, c:
            self.assertEqual(c.execute('SELECT count(*) FROM events').fetchone()[0], count)
        self.assertEqual(self.request('/usage?range=day')[1]['data_since'], stamp(earlier)[:16])
        (self.p/'front').write_text('1')
        wait_for(lambda: self.state()['tv'] == 'locked')
        self.assertFalse(self.state()['authorized'])
        (self.p/'mode').write_text('usage_fail')
        before = sum('usagestats' in a for a in self.calls())
        wait_for(lambda: sum('usagestats' in a for a in self.calls()) > before)
        wait_for(lambda: 'usage fetch failed' in (self.p/'Library/Logs/tvgate.log').read_text())
        self.assertEqual(self.state()['tv'], 'locked')
        self.assertFalse(self.state()['authorized'])
        self.assertFalse((self.p/'notified').exists())
        self.assertNotIn('grace', [x['action'] for x in self.request('/log')[1]['items']])

    def test_usage_hang_does_not_block(self):
        self.stop(); (self.p/'mode').write_text('usage_hang'); self.start()
        wait_for(lambda: any('usagestats' in a for a in self.calls()))
        start = time.monotonic()
        self.assertTrue(self.request('/open?min=15', timeout=19)[1]['ok'])
        self.assertLess(time.monotonic()-start, 20)
        start = time.monotonic(); self.assertEqual(self.request('/status', timeout=2)[0], 200)
        self.assertLess(time.monotonic()-start, 2)


    def test_empty_database_and_links(self):
        self.stop()
        (self.p/'mode').write_text('usage_fail')
        for suffix in ('', '-wal', '-shm'):
            (self.p/('tvgate.db'+suffix)).unlink(missing_ok=True)
        self.start()
        data = self.request('/usage?range=day')[1]
        self.assertTrue(all(app['minutes'] == 0 for app in data['apps']))
        self.assertIsNone(data['data_since']); self.assertIsNone(data['last_fetch'])
        self.stop()
        for name, output in [('scutil', 'Example'), ('ipconfig', '192.0.2.10')]:
            path = self.p/name
            path.write_text('#!/bin/sh\nprintf "%s\\n" '+output+'\n'); path.chmod(0o755)
        (self.p/'token').write_text(json.dumps({'爸爸':'test-secret', '媽媽':'other-secret', '孩子':'third-secret'}))
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            port = sock.getsockname()[1]
        env = dict(self.env, TVGATE_PORT=str(port), PATH=str(self.p)+os.pathsep+os.environ['PATH'])
        run = subprocess.run([sys.executable, str(HERE/'gate.py'), '--links'], env=env, text=True, capture_output=True, timeout=5)
        self.assertEqual(run.returncode, 0)
        lines = run.stdout.splitlines()
        self.assertEqual(len(lines), 6)
        for device, key in [('爸爸','test-secret'), ('媽媽','other-secret'), ('孩子','third-secret')]:
            self.assertIn(f'{device}: http://Example.local:{port}/#t={key}', lines)
            self.assertIn(f'{device}: http://192.0.2.10:{port}/#t={key}', lines)
        with self.assertRaises(OSError):
            socket.create_connection(('127.0.0.1', port), timeout=.2)

    def test_event_log_expiry_relock_sleep_grace(self):
        wait_for(lambda: self.state()['tv'] == 'locked')
        (self.p/'locked').write_text('0')
        wait_for(self.locked)
        self.event('sleep'); wait_for(lambda: (self.p/'answer').exists())
        (self.p/'front').write_text('1')
        self.event('wake'); wait_for(lambda: (self.p/'notified').exists())
        actions = [x['action'] for x in self.request('/log')[1]['items']]
        for name in ('relock','away','grace'): self.assertIn(name, actions)
        self.request('/open?min=15')
        self.stop()
        (self.p/'state.json').write_text(json.dumps({'until': time.time()+1.5}))
        self.start()
        wait_for(lambda: 'expire' in [x['action'] for x in self.request('/log')[1]['items']], 8)

    def test_network_return_and_tv_reconnect(self):
        self.stop(); self.env['TVGATE_AWAY_SECONDS'] = '0.5'; self.start()
        wait_for(lambda: self.state()['tv'] == 'locked')
        (self.p/'front').write_text('1')
        (self.p/'mode').write_text('fail')
        wait_for(lambda: self.state()['tv'] == 'unknown')
        (self.p/'locked').write_text('0')
        (self.p/'mode').write_text('')
        wait_for(self.locked)
        self.assertFalse((self.p/'notified').exists())
        away_start = time.time()
        (self.p/'home_net').write_text('0')
        wait_for(lambda: json.loads((self.p/'state.json').read_text()).get('last_check', 0) > away_start)
        away_seen = json.loads((self.p/'state.json').read_text())['last_check']
        wait_for(lambda: json.loads((self.p/'state.json').read_text()).get('last_check', 0) >= away_seen + .7)
        (self.p/'arrival_open').write_text('1')
        (self.p/'home_net').write_text('1')
        wait_for(lambda: (self.p/'notified').exists())
        self.assertFalse(self.locked())

    def test_schedule_locks_during_grace(self):
        self.stop()
        clock = self.p/'clock'
        fixed = datetime.datetime(2026, 10, 26, 12, 0)
        clock.write_text(str(fixed.timestamp()))
        self.env['TVGATE_CLOCK'] = str(clock)
        self.env['TVGATE_INTERVAL'] = '2'
        self.start()
        wait_for(lambda: (self.p/'state.json').exists() and json.loads((self.p/'state.json').read_text()).get('last_check') is not None)
        wait_for(lambda: self.state()['tv'] == 'locked')
        (self.p/'front').write_text('1')
        (self.p/'locked').write_text('0')
        self.event('wake')
        wait_for(lambda: (self.p/'notified').exists())
        self.assertFalse(self.locked())
        target = fixed + datetime.timedelta(minutes=1)
        rule = {'days':[target.isoweekday()], 'time':target.strftime('%H:%M')}
        self.assertEqual(self.request('/schedule/add', body=rule)[0], 200)
        clock.write_text(str(target.timestamp()+1))
        wait_for(self.locked)
        self.assertIn('schedule', [x['action'] for x in self.request('/log')[1]['items']])
        self.assertFalse(self.state()['authorized'])

    def test_schedule_end_to_end(self):
        self.stop()
        now = datetime.datetime(2026, 10, 26, 12, 0)
        target = now.timestamp()
        clock = self.p/'clock'
        clock.write_text(str(target + 1))
        self.env['TVGATE_CLOCK'] = str(clock)
        with contextlib.closing(sqlite3.connect(self.p/'tvgate.db')) as db, db:
            db.execute('insert into schedule(days,time) values (?,?)', (json.dumps([now.isoweekday()]),now.strftime('%H:%M')))
        (self.p/'state.json').write_text(json.dumps({'until': time.time()+3600,'last_check':target-1}))
        (self.p/'locked').write_text('0')
        events = self.lock_events()
        self.start()
        wait_for(lambda: any(x['action']=='schedule' for x in self.request('/log')[1]['items']))
        wait_for(self.locked)
        wait_for(lambda: self.lock_events() == events + len(PACKAGES))
        self.assertFalse(self.state()['authorized'])
        self.two_cycles()
        self.assertNotIn('expire', [x['action'] for x in self.request('/log')[1]['items']])
        self.stop()
        with contextlib.closing(sqlite3.connect(self.p/'tvgate.db')) as db, db: db.execute('delete from log')
        (self.p/'state.json').write_text(json.dumps({'until':time.time()+3600,'last_check':target-1}))
        clock.write_text(str(target + 601))
        self.start()
        wait_for(lambda: any(x['action']=='schedule-skipped' for x in self.request('/log')[1]['items']))
        self.assertTrue(self.state()['authorized'])
        self.assertEqual(sum(x['action']=='schedule-skipped' for x in self.request('/log')[1]['items']), 1)
        self.stop()
        clock.write_text(str(target + 602))
        self.start()
        wait_for(lambda: json.loads((self.p/'state.json').read_text()).get('last_check') == target + 602)
        self.assertEqual(sum(x['action']=='schedule-skipped' for x in self.request('/log')[1]['items']), 1)



spec = importlib.util.spec_from_file_location('gate', HERE/'gate.py')
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class PureTest(unittest.TestCase):
    def test_calc_exact(self):
        yt, netflix = PACKAGE, 'com.netflix.ninja'
        e = lambda t,k,p='',c='': (t,k,p,c)
        resume = 'ACTIVITY_RESUMED'
        def calc(events, now=100000, last=100000, lo=0, hi=86400):
            return gate.calc(events, now, last, lo, hi)
        x = calc([e(36000,resume,yt),e(37800,resume,netflix),e(39000,'SCREEN_NON_INTERACTIVE')])
        self.assertEqual((x[yt], x[netflix]), (1800,1200))
        events = [e(85800,resume,yt),e(87000,resume,'com.gathin.tvhome')]
        self.assertEqual(calc(events,lo=0,hi=86400)[yt],600)
        self.assertEqual(calc(events,lo=86400,hi=172800)[yt],600)
        self.assertEqual(calc([e(36000,resume,yt,'Shell'),e(36000,resume,yt,'Main'),e(37200,'GATE_LOCK',yt)])[yt],1200)
        self.assertEqual(calc([e(36000,resume,yt)],now=43200,last=38400)[yt],2400)
        self.assertEqual(calc([e(36000,resume,yt)],now=43200,last=None).get(yt,0),0)
        self.assertEqual(calc([e(36000,resume,yt)],now=57600,last=57600)[yt],14400)
        self.assertEqual(calc([e(36000,resume,yt),e(37800,'DEVICE_STARTUP')])[yt],1800)

    def test_synthetic_sample(self):
        rows = gate.parse_events((HERE/'testdata/usagestats_events_sample.txt').read_text())
        self.assertEqual(sum(r[1]=='ACTIVITY_RESUMED' for r in rows),3)
        self.assertNotIn('UNKNOWN_TYPE_33', [r[1] for r in rows])
        self.assertNotIn('com.example.ignored', [r[2] for r in rows])
        lo, hi = min(x[0] for x in rows), max(x[0] for x in rows)
        result = gate.calc(rows,hi,hi,lo,hi)
        self.assertEqual(result, {PACKAGE:300,'com.netflix.ninja':180})
        self.assertEqual(sum(result.values()),480)
        self.assertLessEqual(sum(result.values()),hi-lo)

    def test_schedule_cancels_grace_and_skips_old(self):
        for age in (10, 600, 601):
            fake_stop = mock.Mock()
            fake_stop.wait.side_effect = [False, True]
            rule = {'days': [1], 'time': '07:40'}
            with mock.patch.multiple(gate, stop=fake_stop, last_check=1, until=10000, grace='watching', last_apply=time.monotonic(), failed_since=None), \
                 mock.patch.object(gate.time, 'time', return_value=1000), \
                 mock.patch.object(gate, 'home_network', return_value=True), \
                 mock.patch.object(gate, 'get_rules', return_value=[rule]), \
                 mock.patch.object(gate, 'schedule_due', return_value=[(rule,1000-age)]), \
                 mock.patch.object(gate, 'save'), \
                 mock.patch.object(gate, 'log') as logs, \
                 mock.patch.object(gate, 'apply') as apply:
                gate.monitor()
                if age <= 600:
                    self.assertIsNone(gate.until)
                    self.assertFalse(gate.grace)
                    apply.assert_called_with(force_lock=True, reconcile=False)
                    self.assertIn('schedule', [call.args[0] for call in logs.call_args_list])
                else:
                    self.assertEqual(gate.until,10000)
                    self.assertEqual(gate.grace,'watching')
                    apply.assert_not_called()
                    self.assertIn('schedule-skipped', [call.args[0] for call in logs.call_args_list])

    def test_schedule_away_is_logged(self):
        fake_stop = mock.Mock(); fake_stop.wait.side_effect = [False, True]
        rule = {'days': [1], 'time': '07:40'}
        with mock.patch.multiple(gate, stop=fake_stop, last_check=1, until=10000, grace=False, last_apply=time.monotonic(), failed_since=None), \
             mock.patch.object(gate.time, 'time', return_value=1000), \
             mock.patch.object(gate, 'home_network', return_value=False), \
             mock.patch.object(gate, 'get_rules', return_value=[rule]), \
             mock.patch.object(gate, 'schedule_due', return_value=[(rule, 999)]), \
             mock.patch.object(gate, 'save'), \
             mock.patch.object(gate, 'log') as logs, \
             mock.patch.object(gate, 'apply') as apply:
            gate.monitor()
            apply.assert_not_called()
            self.assertEqual(gate.until, 10000)
            logs.assert_any_call('schedule-skipped', 'Mac 不在家', detail='Mac 不在家')

    def test_wake_skips_schedule_crossed_while_asleep(self):
        fake_stop = mock.Mock(); fake_stop.wait.side_effect = [False, True]
        rule = {'days': [1], 'time': '07:40'}
        with mock.patch.multiple(gate, stop=fake_stop, sleeping=False, sleep_ended=1000,
                                 last_check=1, until=10000, grace=False,
                                 last_apply=time.monotonic(), failed_since=None), \
             mock.patch.object(gate.time, 'time', return_value=1001), \
             mock.patch.object(gate, 'home_network', return_value=True), \
             mock.patch.object(gate, 'get_rules', return_value=[rule]), \
             mock.patch.object(gate, 'schedule_due', return_value=[(rule, 999)]), \
             mock.patch.object(gate, 'save'), \
             mock.patch.object(gate, 'log') as logs, \
             mock.patch.object(gate, 'apply') as apply:
            gate.monitor()
            apply.assert_not_called()
            self.assertEqual(gate.until, 10000)
            logs.assert_any_call('schedule-skipped', 'Mac 休眠', detail='Mac 休眠')

    def test_expiry_preserves_wake_grace(self):
        fake_stop = mock.Mock(); fake_stop.wait.side_effect = [False, True]
        with mock.patch.multiple(gate, stop=fake_stop, sleeping=False, last_check=1,
                                 until=999, grace='watching', failed_since=None), \
             mock.patch.object(gate.time, 'time', return_value=1000), \
             mock.patch.object(gate, 'home_network', return_value=True), \
             mock.patch.object(gate, 'get_rules', return_value=[]), \
             mock.patch.object(gate, 'save'), \
             mock.patch.object(gate, 'log') as logs, \
             mock.patch.object(gate, 'read_tv', return_value=True), \
             mock.patch.object(gate, 'apply') as apply:
            gate.monitor()
            self.assertIsNone(gate.until)
            self.assertEqual(gate.grace, 'watching')
            logs.assert_called_once_with('expire', 'expired')
            apply.assert_called_once_with()

    def test_lock_event_requires_readback(self):
        def read(deadline=None):
            gate.tv = 'open'
            return True
        with mock.patch.multiple(gate, until=None, grace=False, tv='open'), \
             mock.patch.object(gate, 'read_tv', side_effect=read), \
             mock.patch.object(gate, 'command', return_value=mock.Mock(returncode=0)), \
             mock.patch.object(gate, 'database') as db, \
             mock.patch.object(gate, 'log'):
            self.assertFalse(gate.apply())
            db.assert_not_called()

    def test_state_fields_are_validated_independently(self):
        now = time.time()
        with tempfile.TemporaryDirectory() as directory, \
             mock.patch.object(gate, 'STATE', Path(directory)/'state.json'), \
             mock.patch.object(gate, 'log') as logs:
            gate.STATE.write_text(json.dumps({'until': now + 60, 'last_check': 1e18}))
            gate.load()
            self.assertEqual(gate.until, now + 60)
            self.assertIsNone(gate.last_check)
            logs.assert_called_once_with('state', 'invalid last_check')
            logs.reset_mock()
            gate.STATE.write_text(json.dumps({'until': True, 'last_check': now - 60}))
            gate.load()
            self.assertIsNone(gate.until)
            self.assertEqual(gate.last_check, now - 60)
            logs.assert_called_once_with('state', 'invalid until')

    def test_monitor_recovers_after_iteration_exception(self):
        fake_stop = mock.Mock(); fake_stop.wait.side_effect = [False, False, True]
        with mock.patch.multiple(gate, stop=fake_stop, last_check=None, until=None, grace=False,
                                 last_apply=time.monotonic(), failed_since=None), \
             mock.patch.object(gate, 'home_network', side_effect=[RuntimeError('failure'), True]), \
             mock.patch.object(gate, 'get_rules', return_value=[]) as rules, \
             mock.patch.object(gate, 'save'), mock.patch.object(gate, 'log') as logs:
            gate.monitor()
            rules.assert_called_once()
            logs.assert_any_call('monitor', 'iteration failed: RuntimeError')

    def test_usage_bad_meta(self):
        with mock.patch.object(gate, 'database', side_effect=[[], [('last_fetch', 'bad'), ('data_since', 'bad')]]):
            result = gate.usage('day')
        self.assertIsNone(result['data_since'])
        self.assertIsNone(result['last_fetch'])

    def test_three_minute_tv_failure_does_not_trigger_arrival(self):
        fake_stop = mock.Mock(); fake_stop.wait.side_effect = [False, True]
        with mock.patch.multiple(gate, stop=fake_stop, last_check=1, until=None, grace=False, last_apply=time.monotonic(), failed_since=time.monotonic()-180), \
             mock.patch.object(gate, 'home_network', return_value=True), \
             mock.patch.object(gate, 'get_rules', return_value=[]), \
             mock.patch.object(gate, 'save'), \
             mock.patch.object(gate, 'read_tv', return_value=True), \
             mock.patch.object(gate, 'arrival') as arrival, \
             mock.patch.object(gate, 'apply') as apply:
            gate.monitor()
            arrival.assert_not_called(); apply.assert_called_once()

    def test_schedule_due(self):
        ts = lambda s: datetime.datetime.strptime(s,'%Y-%m-%d %H:%M:%S').timestamp()
        r = {'days':[1], 'time':'07:40'}
        self.assertEqual(len(gate.schedule_due([r],ts('2026-10-26 07:39:58'),ts('2026-10-26 07:40:03'))),1)
        self.assertEqual(gate.schedule_due([r],ts('2026-10-26 07:40:03'),ts('2026-10-26 07:40:08')),[])
        self.assertEqual(gate.schedule_due([r],ts('2026-10-31 07:39:58'),ts('2026-10-31 07:40:03')),[])
        sunday = {'days':[7], 'time':'23:59'}
        self.assertEqual(len(gate.schedule_due([sunday],ts('2026-11-01 23:58:58'),ts('2026-11-02 00:00:02'))),1)
        every = {'days':[1,2,3,4,5,6,7], 'time':'07:40'}
        self.assertEqual(len(gate.schedule_due([every],ts('2026-10-26 07:39:58'),ts('2026-10-28 07:40:03'))),3)


if __name__ == '__main__':
    unittest.main(verbosity=2)
