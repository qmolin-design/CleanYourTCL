import { page } from './page.mjs';
const certCache = new Map();
const decode = s => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
const jsonPart = s => JSON.parse(new TextDecoder().decode(decode(s)));

async function identity(req, env, now) {
  try {
    const [h, p, s, ...extra] = (req.headers.get('Cf-Access-Jwt-Assertion') || '').split('.');
    if (!h || !p || !s || extra.length) return null;
    const header = jsonPart(h), claims = jsonPart(p);
    if (header.alg !== 'RS256' || typeof header.kid !== 'string') return null;
    const issuer = `https://${env.ACCESS_TEAM}.cloudflareaccess.com`;
    if (claims.iss !== issuer || !Number.isFinite(claims.exp) || claims.exp <= now / 1000 || !(Array.isArray(claims.aud) ? claims.aud : [claims.aud]).includes(env.ACCESS_AUD)) return null;
    let cache = certCache.get(issuer);
    if (!cache || now - cache.at >= 3600000) {
      const response = await fetch(`${issuer}/cdn-cgi/access/certs`);
      if (!response.ok) return null;
      cache = { keys: (await response.json()).keys, at: now, unknownAt: -Infinity };
      certCache.set(issuer, cache);
    }
    let jwk = cache.keys.find(k => k.kid === header.kid);
    if (!jwk && now - cache.unknownAt >= 60000) {
      cache.unknownAt = now;
      const response = await fetch(`${issuer}/cdn-cgi/access/certs`);
      if (!response.ok) return null;
      cache.keys = (await response.json()).keys;
      cache.at = now;
      jwk = cache.keys.find(k => k.kid === header.kid);
    }
    if (!jwk) return null;
    const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    if (!await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, decode(s), new TextEncoder().encode(`${h}.${p}`))) return null;
    const parents = JSON.parse(env.PARENTS);
    if (typeof claims.email === 'string' && Object.hasOwn(parents, claims.email)) return { role: 'parent', name: parents[claims.email] };
    if (typeof claims.common_name === 'string' && claims.common_name === env.TV_CLIENT_ID) return { role: 'tv', name: '電視' };
    return { role: 'other' };
  } catch { return null; }
}

const response = (value, status = 200) => Response.json(value, { status, headers: { 'Cache-Control': 'no-store' } });
const error = status => response({ error: status }, status);
const stmt = (db, sql, ...args) => db.prepare(sql).bind(...args);
const all = async (db, sql, ...args) => (await stmt(db, sql, ...args).all()).results;
const first = (db, sql, ...args) => stmt(db, sql, ...args).first();
const event = (db, ts, who, kind, detail = null) => stmt(db, 'INSERT INTO events(ts,who,kind,detail) VALUES(?,?,?,?)', ts, who, kind, detail);
const integer = n => Number.isSafeInteger(n) && n >= 0;
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
function validUsageFields(r) {
  const keys = ['today', 'used_today_yt_s', 'used_today_nf_s'].filter(k => r[k] !== undefined);
  if (!keys.length) return true;
  if (keys.length !== 3) return false;
  if (typeof r.today !== 'string' || !DATE_RE.test(r.today)) return false;
  return ['used_today_yt_s', 'used_today_nf_s'].every(k => integer(r[k]) && r[k] <= 86400);
}
function validReport(r) {
  return r && typeof r === 'object' && !Array.isArray(r) && r.v === 1 &&
    (r.last_seq === null || integer(r.last_seq)) && (r.db_id === null || typeof r.db_id === 'string') &&
    integer(r.balance_s) && r.balance_s <= 86400 && typeof r.capped === 'boolean' &&
    integer(r.used_today_s) && integer(r.used_total_s) && ['enabled','disabled','unknown'].includes(r.yt) &&
    (r.fg_yt === null || typeof r.fg_yt === 'boolean') && ['on','off','unknown'].includes(r.screen) &&
    typeof r.adb === 'string' && r.adb.length <= 64 && typeof r.enforce === 'boolean' &&
    integer(r.boot_count) && integer(r.uptime_s) && integer(r.gap_s) &&
    typeof r.wl === 'string' && r.wl.length <= 64 && integer(r.app_ver) && validUsageFields(r);
}
async function meta(db) {
  return Object.fromEntries((await all(db, 'SELECT k,v FROM meta')).map(x => [x.k, x.v]));
}
const TAIPEI_OFFSET = 8 * 3600;
const ADULT_WEEK = ['日','一','二','三','四','五','六'];
const adultHM = s => {
  if (s === '24:00') return 1440;
  const m = /^([01]\d|2[0-3]):([0-5]\d)$/.exec(s);
  return m ? Number(m[1]) * 60 + Number(m[2]) : -1;
};
function parseManual(raw, nowS) {
  try {
    if (raw === undefined || raw === null) return null;
    const o = typeof raw === 'string' ? JSON.parse(raw) : raw;
    if (!o || typeof o !== 'object' || Array.isArray(o)) return null;
    const until = Math.floor(Number(o.until));
    if (!Number.isFinite(until) || !(until > nowS) || until > nowS + 86400) return null;
    return { until, by: typeof o.by === 'string' ? o.by : '', at: Number.isFinite(Number(o.at)) ? Math.floor(Number(o.at)) : until };
  } catch { return null; }
}
function adultManual(m, nowS) {
  return parseManual(m.adult_manual, nowS);
}
export function adultWindow(nowS, sched, offUntil, manualUntil) {
  const none = { active: false, from: 0, until: 0, next_from: 0, next_until: 0 };
  try {
    if (!Number.isFinite(nowS)) return none;
    let list = sched;
    if (typeof list === 'string') {
      try { list = JSON.parse(list); } catch { list = []; }
    }
    let schedOk = Array.isArray(list) && list.length > 0;
    if (schedOk) for (const e of list) {
      if (!e || typeof e !== 'object' || Array.isArray(e)) { schedOk = false; break; }
      if (typeof e.days !== 'string' || !e.days.length || [...e.days].some(c => c < '0' || c > '6')) { schedOk = false; break; }
      if (typeof e.from !== 'string' || typeof e.to !== 'string') { schedOk = false; break; }
      const f = adultHM(e.from), t = adultHM(e.to);
      if (f < 0 || t < 0 || !(f < t)) { schedOk = false; break; }
    }
    if (!schedOk) list = [];
    const midnight = Math.floor((nowS + TAIPEI_OFFSET) / 86400) * 86400 - TAIPEI_OFFSET;
    const ivs = [];
    for (let d = -1; d <= 3; d++) {
      const dayStart = midnight + d * 86400;
      const wd = new Date((dayStart + TAIPEI_OFFSET) * 1000).getUTCDay();
      for (const e of list) {
        if (!e.days.includes(String(wd))) continue;
        ivs.push([dayStart + adultHM(e.from) * 60, dayStart + adultHM(e.to) * 60]);
      }
    }
    ivs.sort((a, b) => a[0] - b[0]);
    const merged = [];
    for (const iv of ivs) {
      const last = merged[merged.length - 1];
      if (last && iv[0] <= last[1]) last[1] = Math.max(last[1], iv[1]);
      else merged.push([iv[0], iv[1]]);
    }
    const off = Number(offUntil);
    const live = Number.isFinite(off) && nowS < off ? merged.filter(iv => iv[1] > off) : merged;
    const all = live.slice();
    const mu = Number(manualUntil);
    if (Number.isFinite(mu) && nowS < mu) all.push([nowS, mu]);
    all.sort((a, b) => a[0] - b[0]);
    const final = [];
    for (const iv of all) {
      const last = final[final.length - 1];
      if (last && iv[0] <= last[1]) last[1] = Math.max(last[1], iv[1]);
      else final.push([iv[0], iv[1]]);
    }
    let cur = null, next = null;
    for (const iv of final) {
      if (iv[0] <= nowS && nowS < iv[1]) cur = iv;
      else if (iv[0] > nowS && !next) next = iv;
    }
    return { active: !!cur, from: cur ? cur[0] : 0, until: cur ? cur[1] : 0, next_from: next ? next[0] : 0, next_until: next ? next[1] : 0 };
  } catch { return none; }
}
function adultFmt(ts) {
  const d = new Date((Number(ts) + TAIPEI_OFFSET) * 1000), p = n => String(n).padStart(2, '0');
  return `${d.getUTCMonth() + 1}/${d.getUTCDate()}（週${ADULT_WEEK[d.getUTCDay()]}）${p(d.getUTCHours())}:${p(d.getUTCMinutes())}`;
}
function adultLeft(m, seconds) {
  try {
    const man = adultManual(m, seconds);
    const w = adultWindow(seconds, m.adult_sched, m.adult_off_until, man ? man.until : undefined);
    return w.active ? Math.min(Math.max(w.until - seconds, 0), 86400) : 0;
  } catch (e) { console.error('adult calc failed', e); return 0; }
}
function adultInfo(nowS, schedRaw, offRaw, manualRaw) {
  let manual = null;
  try { manual = parseManual(manualRaw, nowS); } catch (e) { console.error('adult calc failed', e); manual = null; }
  const mu = manual ? manual.until : undefined;
  let w, base;
  try {
    w = adultWindow(nowS, schedRaw, offRaw, mu);
    base = adultWindow(nowS, schedRaw, offRaw);
  } catch (e) { console.error('adult calc failed', e); w = { active: false, from: 0, until: 0, next_from: 0, next_until: 0 }; base = w; }
  const n = Number(offRaw);
  const choices = [{ key: '60', until: nowS + 3600 }, { key: '120', until: nowS + 7200 }];
  if (base.next_from > nowS && base.next_from <= nowS + 86400) choices.push({ key: 'next', until: base.next_from });
  return { active: w.active, from: w.from, until: w.until, next_from: w.next_from, next_until: w.next_until, off_until: Number.isFinite(n) && n > 0 ? Math.floor(n) : null, manual, source: !w.active ? null : (manual ? 'manual' : 'sched'), choices };
}
async function sync(db, r, now, offline) {
  const m = await meta(db), old = await first(db, 'SELECT * FROM tv WHERE id=1');
  const prev = old && JSON.parse(old.report), ack = old?.acked_seq ?? 0;
  const max = (await first(db, 'SELECT COALESCE(MAX(seq),0) AS n FROM grants')).n;
  const reset = r.db_id !== null && r.db_id !== m.db_id;
  const grants = reset || (r.last_seq === null && !old) ? [] : await all(db, 'SELECT seq,kind,seconds FROM grants WHERE seq>? ORDER BY seq', r.last_seq === null ? ack : r.last_seq);
  const actions = [];
  if (reset) actions.push(event(db, now, '電視', 'db-reset'));
  else if (r.last_seq === null && old) actions.push(event(db, now, '電視', 'reinstall'));
  if (r.last_seq !== null && r.last_seq > ack) actions.push(event(db, now, '電視', 'received', String(r.last_seq)));
  if (prev && r.yt !== prev.yt && (r.yt === 'disabled' || r.yt === 'enabled')) actions.push(event(db, now, '電視', r.yt === 'disabled' ? 'lock' : 'unlock'));
  if (old && now - old.last_seen >= offline * 60) actions.push(event(db, now, '電視', 'online', `中斷 ${Math.floor((now - old.last_seen) / 60)} 分`));
  if (r.gap_s > 300) actions.push(event(db, now, '電視', 'gap', String(r.gap_s)));
  if (r.adb !== 'ok' && (old?.adb_bad ?? 0) === 2) actions.push(event(db, now, '電視', 'adb-bad', r.adb));
  if (r.adb === 'ok' && (old?.adb_bad ?? 0) >= 3) actions.push(event(db, now, '電視', 'adb-ok'));
  actions.push(stmt(db, `INSERT INTO tv(id,last_seen,report,acked_seq,adb_bad) VALUES(1,?,?,?,CASE WHEN ?='ok' THEN 0 ELSE 1 END)
    ON CONFLICT(id) DO UPDATE SET last_seen=excluded.last_seen, report=excluded.report,
    acked_seq=MAX(COALESCE(tv.acked_seq,0),COALESCE(excluded.acked_seq,0)), adb_bad=CASE WHEN ?='ok' THEN 0 ELSE tv.adb_bad+1 END`, now, JSON.stringify(r), r.last_seq, r.adb, r.adb));
  actions.push(stmt(db, 'DELETE FROM events WHERE ts < ?', now - 90 * 86400));
  await db.batch(actions);
  if (r.today !== undefined) {
    try {
      await db.batch([
        stmt(db, `INSERT INTO usage_daily(date,app,seconds) VALUES(?,?,?) ON CONFLICT(date,app) DO UPDATE SET seconds=MAX(seconds,excluded.seconds)`, r.today, 'youtube', r.used_today_yt_s),
        stmt(db, `INSERT INTO usage_daily(date,app,seconds) VALUES(?,?,?) ON CONFLICT(date,app) DO UPDATE SET seconds=MAX(seconds,excluded.seconds)`, r.today, 'netflix', r.used_today_nf_s),
      ]);
    } catch (e) { console.error('usage_daily upsert failed', e); }
  }
  return { grants, max_seq: max, db_id: m.db_id, cap_s: Number(m.cap_s), enforce: m.enforce === 'true', adult_left_s: adultLeft(m, now) };
}
export function eventText(kind, detail = null) {
  if (kind && typeof kind === 'object' && !Array.isArray(kind)) { detail = kind.detail ?? null; kind = kind.kind; }
  const mins = s => Math.floor(Number(s) / 60);
  switch (kind) {
    case 'grant': { const n = mins(detail); return Number.isFinite(n) ? `加 ${n} 分` : '加值'; }
    case 'zero': return '歸零';
    case 'received': return '電視收到加值';
    case 'lock': return '已鎖 YouTube／Netflix';
    case 'unlock': return '已開放 YouTube／Netflix';
    case 'online': return detail == null || detail === '' ? '恢復連線' : `恢復連線（${detail}）`;
    case 'gap': { const n = mins(detail); return Number.isFinite(n) ? `回報中斷 ${n} 分` : '回報中斷'; }
    case 'adb-bad': return detail == null || detail === '' ? '無法控制' : `無法控制（${detail}）`;
    case 'adb-ok': return '恢復控制';
    case 'reinstall': return '電視 App 重新安裝';
    case 'db-reset': return '資料庫重建';
    case 'adult-off': { const n = Number(detail); return Number.isFinite(n) && n > 0 ? `關閉爸媽時段到 ${adultFmt(n)}` : '關閉爸媽時段'; }
    case 'adult-resume': return '恢復爸媽時段';
    case 'adult-on': { const n = Number(detail); return Number.isFinite(n) && n > 0 ? `開爸媽模式到 ${adultFmt(n)}` : '開爸媽模式'; }
    case 'adult-kid': return '切回兒童模式';
    default: return detail == null || detail === '' ? String(kind) : `${kind} ${detail}`;
  }
}
const taipeiToday = seconds => new Date((seconds + 8 * 3600) * 1000).toISOString().slice(0, 10);
const addDays = (date, n) => new Date(Date.parse(date + 'T00:00:00Z') + n * 86400000).toISOString().slice(0, 10);
const emptyUsage = () => ({ today: { youtube_s: 0, netflix_s: 0 }, week: { youtube_s: 0, netflix_s: 0 }, month: { youtube_s: 0, netflix_s: 0 }, days: [], recent: [] });
async function usage(db, seconds) {
  try {
    const today = taipeiToday(seconds);
    const sinceMonday = (new Date((seconds + 8 * 3600) * 1000).getUTCDay() + 6) % 7;
    const weekStart = addDays(today, -sinceMonday), monthStart = today.slice(0, 8) + '01', recentStart = addDays(today, -6);
    const queryStart = [weekStart, monthStart, recentStart].reduce((a, b) => (a < b ? a : b));
    const rows = await all(db, 'SELECT date,app,seconds FROM usage_daily WHERE date>=? AND date<=?', queryStart, today);
    const byDate = new Map();
    for (const r of rows) {
      if (!byDate.has(r.date)) byDate.set(r.date, { date: r.date, youtube_s: 0, netflix_s: 0 });
      const d = byDate.get(r.date);
      if (r.app === 'youtube') d.youtube_s = r.seconds; else if (r.app === 'netflix') d.netflix_s = r.seconds;
    }
    const days = [...byDate.values()].filter(d => d.date >= monthStart).sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));
    const sum = from => { let youtube_s = 0, netflix_s = 0; for (const d of byDate.values()) if (d.date >= from && d.date <= today) { youtube_s += d.youtube_s; netflix_s += d.netflix_s; } return { youtube_s, netflix_s }; };
    const at = date => { const d = byDate.get(date); return d ? { youtube_s: d.youtube_s, netflix_s: d.netflix_s } : { youtube_s: 0, netflix_s: 0 }; };
    const recent = [];
    for (let i = 6; i >= 0; i--) { const date = addDays(today, -i), d = byDate.get(date); recent.push({ date, youtube_s: d ? d.youtube_s : 0, netflix_s: d ? d.netflix_s : 0 }); }
    return { today: at(today), week: sum(weekStart), month: sum(monthStart), days, recent };
  } catch { return emptyUsage(); }
}
async function state(db, name, now, offline) {
  const m = await meta(db), row = await first(db, 'SELECT * FROM tv WHERE id=1');
  const report = row && JSON.parse(row.report), alerts = [];
  if (row && now - row.last_seen >= offline * 60) {
    const min = Math.floor((now - row.last_seen) / 60);
    alerts.push(report.screen === 'on' ? { level:'red', text:`電視 App 已 ${min} 分沒回報（最後螢幕開著）` } : { level:'yellow', text:`電視 ${min} 分前回報（之後待機或關機）` });
  }
  if (row?.adb_bad >= 3) alerts.push({ level:'red', text:`電視 App 無法控制 YouTube／Netflix（adb：${report.adb}）` });
  if (await first(db, "SELECT id FROM events WHERE ts>=? AND kind IN ('reinstall','db-reset') LIMIT 1", now - 86400)) alerts.push({ level:'yellow', text:'近期電視重新安裝或資料庫重建' });
  if (m.enforce !== 'true') alerts.push({ level:'yellow', text:'影子模式：此頁不控制電視' });
  const tv = row ? { last_seen:row.last_seen, balance_s:report.balance_s, capped:report.capped, used_today_s:report.used_today_s, yt:report.yt, fg_yt:report.fg_yt, screen:report.screen, adb:report.adb, adb_bad:row.adb_bad, wl:report.wl, app_ver:report.app_ver } : null;
  const eventRows = await all(db, 'SELECT ts,who,kind,detail FROM events ORDER BY id DESC LIMIT 30');
  const events = eventRows.map(e => ({ ...e, text: eventText(e.kind, e.detail) }));
  return { me:name, cap_s:Number(m.cap_s), enforce:m.enforce === 'true', tv, alerts,
    adult: adultInfo(now, m.adult_sched, m.adult_off_until, m.adult_manual),
    usage:await usage(db, now),
    pending:await all(db, 'SELECT seq,kind,seconds,by,created_at FROM grants WHERE seq>? ORDER BY seq', row?.acked_seq ?? 0),
    events };
}
async function grant(db, data, who, now) {
  if (!data || typeof data !== 'object' || Array.isArray(data) || typeof data.req_id !== 'string' || !data.req_id.length || data.req_id.length > 64 || !((data.kind === 'add' && [15,30,60].includes(data.minutes)) || (data.kind === 'zero' && !Object.hasOwn(data, 'minutes')))) return null;
  const kind = data.kind === 'zero' ? 'set' : 'add', seconds = kind === 'set' ? 0 : data.minutes * 60;
  await db.batch([
    stmt(db, 'INSERT OR IGNORE INTO grants(kind,seconds,by,req_id,created_at) VALUES(?,?,?,?,?)', kind, seconds, who, data.req_id, now),
    stmt(db, 'INSERT INTO events(ts,who,kind,detail) SELECT ?,?,?,? WHERE changes()>0', now, who, data.kind === 'zero' ? 'zero' : 'grant', String(seconds))
  ]);
  const saved = await first(db, 'SELECT seq FROM grants WHERE req_id=?', data.req_id);
  const m = await meta(db), row = await first(db, 'SELECT * FROM tv WHERE id=1');
  const pending = await first(db, "SELECT COALESCE(SUM(seconds),0) AS n FROM grants WHERE seq>? AND kind='add'", row?.acked_seq ?? 0);
  return { seq:saved.seq, would_cap:(row ? JSON.parse(row.report).balance_s : 0) + pending.n > Number(m.cap_s) };
}
async function adultApi(db, action, choice, who, now) {
  const m = await meta(db);
  if (action === 'off') {
    let base;
    try { base = adultWindow(now, m.adult_sched, null); } catch (e) { console.error('adult calc failed', e); return null; }
    const target = base.active ? base.until : (base.next_from ? base.next_until : 0);
    if (!target) return null;
    const cur = m.adult_off_until;
    if (cur === String(target)) return adultInfo(now, m.adult_sched, cur, m.adult_manual);
    await db.batch([
      stmt(db, "INSERT INTO meta(k,v) VALUES('adult_off_until',?) ON CONFLICT(k) DO UPDATE SET v=excluded.v", String(target)),
      stmt(db, 'INSERT INTO events(ts,who,kind,detail) SELECT ?,?,?,? WHERE changes()>0', now, who, 'adult-off', String(target))
    ]);
    return adultInfo(now, m.adult_sched, String(target), m.adult_manual);
  }
  if (action === 'resume') {
    if (m.adult_off_until === undefined) return adultInfo(now, m.adult_sched, null, m.adult_manual);
    await db.batch([
      stmt(db, "DELETE FROM meta WHERE k='adult_off_until'"),
      stmt(db, 'INSERT INTO events(ts,who,kind,detail) SELECT ?,?,?,? WHERE changes()>0', now, who, 'adult-resume', null)
    ]);
    return adultInfo(now, m.adult_sched, null, m.adult_manual);
  }
  if (action === 'on') {
    let until = 0;
    if (choice === '60') until = now + 3600;
    else if (choice === '120') until = now + 7200;
    else if (choice === 'next') {
      let base;
      try { base = adultWindow(now, m.adult_sched, m.adult_off_until); } catch (e) { console.error('adult calc failed', e); return null; }
      if (!(base.next_from > now && base.next_from <= now + 86400)) return null;
      until = base.next_from;
    } else return null;
    const raw = JSON.stringify({ until, by: who, at: now });
    await db.batch([
      stmt(db, "INSERT INTO meta(k,v) VALUES('adult_manual',?) ON CONFLICT(k) DO UPDATE SET v=excluded.v", raw),
      event(db, now, who, 'adult-on', String(until))
    ]);
    return adultInfo(now, m.adult_sched, m.adult_off_until, raw);
  }
  if (action === 'kid') {
    const manual = adultManual(m, now);
    let base;
    try { base = adultWindow(now, m.adult_sched, m.adult_off_until); } catch (e) { console.error('adult calc failed', e); return null; }
    const target = base.active ? base.until : 0;
    const offChanged = target > 0 && m.adult_off_until !== String(target);
    if (!manual && !offChanged) return adultInfo(now, m.adult_sched, m.adult_off_until, m.adult_manual);
    const stmts = [stmt(db, "DELETE FROM meta WHERE k='adult_manual'")];
    if (offChanged) stmts.push(stmt(db, "INSERT INTO meta(k,v) VALUES('adult_off_until',?) ON CONFLICT(k) DO UPDATE SET v=excluded.v", String(target)));
    stmts.push(event(db, now, who, 'adult-kid'));
    await db.batch(stmts);
    const nm = await meta(db);
    return adultInfo(now, nm.adult_sched, nm.adult_off_until, nm.adult_manual);
  }
  return null;
}

export async function handle(req, env, now) {
  const path = new URL(req.url).pathname;
  if (!path.startsWith('/tv/')) return error(404);
  const user = await identity(req, env, now);
  if (!user) return error(401);
  if (req.method === 'POST' && (path === '/tv/api/grant' || path === '/tv/api/adult') && req.headers.get('Origin') !== 'https://tv-demo.example.net') return error(403);
  const role = path === '/tv/api/sync' ? 'tv' : 'parent';
  if (user.role !== role) return error(403);
  if (req.method === 'GET' && path === '/tv/') return new Response(page, { headers: { 'Content-Type':'text/html; charset=utf-8', 'Cache-Control':'no-store' } });
  const offline = Number(env.OFFLINE_MIN) > 0 ? Number(env.OFFLINE_MIN) : 30, seconds = Math.floor(now / 1000);
  if (req.method === 'GET' && path === '/tv/api/state') return response(await state(env.DB, user.name, seconds, offline));
  if (req.method === 'POST' && (path === '/tv/api/sync' || path === '/tv/api/grant' || path === '/tv/api/adult')) {
    let body; try { body = await req.json(); } catch { return error(400); }
    if (path === '/tv/api/sync') return validReport(body) ? response(await sync(env.DB, body, seconds, offline)) : error(400);
    if (path === '/tv/api/adult') {
      const result = body && typeof body === 'object' ? await adultApi(env.DB, body.action, body.choice, user.name, seconds) : null;
      return result ? response(result) : error(400);
    }
    const result = await grant(env.DB, body, user.name, seconds);
    return result ? response(result) : error(400);
  }
  return error(404);
}
