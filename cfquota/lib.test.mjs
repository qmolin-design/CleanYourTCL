import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { DatabaseSync } from 'node:sqlite';
import { mkdtempSync, readFileSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { handle, eventText, adultWindow } from './lib.mjs';
import { page } from './page.mjs';

const schema = readFileSync(new URL('./schema.sql', import.meta.url), 'utf8');
const keys = await Promise.all([1,2,3].map(async n => {
  const pair = await crypto.subtle.generateKey({name:'RSASSA-PKCS1-v1_5',modulusLength:2048,publicExponent:new Uint8Array([1,0,1]),hash:'SHA-256'}, true, ['sign','verify']);
  return { ...pair, jwk:{...await crypto.subtle.exportKey('jwk', pair.publicKey),kid:`key${n}`} };
}));
const enc = v => Buffer.from(typeof v === 'string' ? v : JSON.stringify(v)).toString('base64url');
let serial = 0;
function setup() {
  const sql = new DatabaseSync(':memory:'); sql.exec(schema);
  const DB = { prepare(query) { return { bind(...args) { const s=sql.prepare(query); return { async run(){return s.run(...args)}, async first(){return s.get(...args) ?? null}, async all(){return {results:s.all(...args)}} }; } }; }, async batch(stmts) { sql.exec('BEGIN'); try { const results=[]; for(const s of stmts) results.push(await s.run()); sql.exec('COMMIT'); return results; } catch(e) { sql.exec('ROLLBACK'); throw e; } } };
  let now=1800000000000, available=[keys[0].jwk], calls=0;
  const env={DB,ACCESS_TEAM:`test${++serial}`,ACCESS_AUD:'audience',PARENTS:JSON.stringify({'parent@example.test':'爸爸'}),TV_CLIENT_ID:'tv-client',OFFLINE_MIN:'30'};
  globalThis.fetch=async url=>{ assert.equal(url,`https://${env.ACCESS_TEAM}.cloudflareaccess.com/cdn-cgi/access/certs`); calls++; return Response.json({keys:available}); };
  async function token(role='parent', options={}) {
    const key=options.key ?? keys[0], header={alg:options.alg ?? 'RS256',kid:options.kid ?? key.jwk.kid};
    const claims={iss:`https://${env.ACCESS_TEAM}.cloudflareaccess.com`,aud:'audience',exp:Math.floor(now/1000)+3600,...(role==='parent'?{email:'parent@example.test'}:role==='tv'?{common_name:'tv-client'}:{email:'other@example.test'}),...options.claims};
    const input=enc(header)+'.'+enc(claims), signature=await crypto.subtle.sign('RSASSA-PKCS1-v1_5',key.privateKey,new TextEncoder().encode(input));
    return input+'.'+Buffer.from(signature).toString('base64url');
  }
  async function call(path, {method='GET',role='parent',jwt,body,origin='https://tv-demo.example.net'}={}) {
    const headers={}; if(jwt !== null) headers['Cf-Access-Jwt-Assertion']=jwt ?? await token(role); if(origin !== null) headers.Origin=origin;
    return handle(new Request(`https://tv-demo.example.net${path}`,{method,headers,...(body !== undefined?{body:typeof body==='string'?body:JSON.stringify(body)}:{})}),env,now);
  }
  return {sql,env,token,call,get calls(){return calls},set available(v){available=v},advance(ms){now+=ms},get now(){return now}};
}
const report = overrides => ({v:1,last_seq:0,db_id:null,balance_s:0,capped:false,used_today_s:0,used_total_s:0,yt:'enabled',fg_yt:true,screen:'on',adb:'ok',enforce:false,boot_count:1,uptime_s:100,gap_s:0,wl:'keep',app_ver:2,...overrides});
const sync = (c, r) => c.call('/tv/api/sync',{method:'POST',role:'tv',body:r});
const grant = (c, body) => c.call('/tv/api/grant',{method:'POST',body});
const state = async c => (await c.call('/tv/api/state')).json();

test('1 驗證、憑證快取與身分', async () => {
  const c=setup();
  assert.equal((await c.call('/tv/',{jwt:null})).status,401);
  for(const options of [{key:keys[1],kid:'key1'},{alg:'HS256'},{claims:{aud:'wrong'}},{claims:{iss:'wrong'}},{claims:{exp:1}}]) assert.equal((await c.call('/tv/',{jwt:await c.token('parent',options)})).status,401);
  assert.equal((await c.call('/tv/')).status,200); assert.equal((await c.call('/tv/')).status,200); assert.equal(c.calls,1);
  c.available=[keys[0].jwk,keys[1].jwk]; assert.equal((await c.call('/tv/',{jwt:await c.token('parent',{key:keys[1]})})).status,200); assert.equal(c.calls,2);
  assert.equal((await c.call('/tv/',{jwt:await c.token('parent',{key:keys[2]})})).status,401); assert.equal(c.calls,2);
  c.advance(60000); assert.equal((await c.call('/tv/',{jwt:await c.token('parent',{key:keys[2]})})).status,401); assert.equal(c.calls,3);
  assert.equal((await c.call('/tv/api/sync',{method:'POST',role:'parent'})).status,403);
  assert.equal((await c.call('/tv/api/state',{role:'tv'})).status,403); assert.equal((await c.call('/tv/',{role:'other'})).status,403);
  for(const p of ['/','/foo','/tvx']) assert.equal((await c.call(p,{jwt:null})).status,404);
});

test('2 加值、冪等、驗證與上限', async () => {
  const c=setup(), body={kind:'add',minutes:30,req_id:'a'};
  assert.equal((await (await grant(c,body)).json()).seq,1);
  assert.deepEqual({...c.sql.prepare('SELECT seconds,by FROM grants').get()},{seconds:1800,by:'爸爸'});
  assert.equal((await (await grant(c,body)).json()).seq,1); assert.equal(c.sql.prepare('SELECT count(*) n FROM grants').get().n,1);
  for(const bad of [{kind:'add',minutes:7,req_id:'b'},'{',{kind:'add',minutes:15}]) assert.equal((await grant(c,bad)).status,400);
  for(const origin of [null,'https://evil.test']) assert.equal((await c.call('/tv/api/grant',{method:'POST',origin,body})).status,403);
  await grant(c,{kind:'zero',req_id:'zero'}); assert.deepEqual({...c.sql.prepare('SELECT kind,seconds FROM grants WHERE req_id=?').get('zero')},{kind:'set',seconds:0});
  await sync(c,report({balance_s:3000,last_seq:2})); assert.equal((await (await grant(c,{kind:'add',minutes:15,req_id:'c'})).json()).would_cap,true);
});

test('電視同步不需要 Origin', async () => {
  const c=setup();
  assert.equal((await c.call('/tv/api/sync',{method:'POST',role:'tv',origin:null,body:report()})).status,200);
});

test('3 同步、重新安裝、資料庫重建與輸入驗證', async () => {
  const c=setup(); await grant(c,{kind:'add',minutes:15,req_id:'a'}); await grant(c,{kind:'add',minutes:30,req_id:'b'});
  assert.deepEqual((await (await sync(c,report({last_seq:null}))).json()).grants,[]);
  let result=await (await sync(c,report())).json(); assert.deepEqual(result.grants.map(x=>x.seq),[1,2]); assert.equal(result.cap_s,3600); assert.equal(result.enforce,false); assert.ok(result.db_id);
  result=await (await sync(c,report({last_seq:1,db_id:result.db_id}))).json(); assert.deepEqual(result.grants.map(x=>x.seq),[2]); assert.deepEqual((await state(c)).pending.map(x=>x.seq),[2]);
  await sync(c,report({last_seq:0})); assert.equal(c.sql.prepare('SELECT acked_seq FROM tv').get().acked_seq,1);
  await sync(c,report({last_seq:2})); await grant(c,{kind:'add',minutes:60,req_id:'c'}); result=await (await sync(c,report({last_seq:null}))).json(); assert.deepEqual(result.grants.map(x=>x.seq),[3]); assert.equal(c.sql.prepare("SELECT count(*) n FROM events WHERE kind='reinstall'").get().n,1);
  result=await (await sync(c,report({db_id:'wrong'}))).json(); assert.deepEqual(result.grants,[]); assert.equal(c.sql.prepare("SELECT count(*) n FROM events WHERE kind='db-reset'").get().n,1);
  c.sql.exec("UPDATE meta SET v='7200' WHERE k='cap_s'; UPDATE meta SET v='true' WHERE k='enforce'"); result=await (await sync(c,report())).json(); assert.equal(result.cap_s,7200); assert.equal(result.enforce,true);
  const before=JSON.stringify(c.sql.prepare('SELECT * FROM tv').get());
  for(const bad of [{...report(),v:undefined},report({fg_yt:'yes'}),report({used_total_s:-1}),report({balance_s:86401}),report({adb:'a'.repeat(65)})]) { assert.equal((await sync(c,bad)).status,400); assert.equal(JSON.stringify(c.sql.prepare('SELECT * FROM tv').get()),before); }
});

test('4 異常、事件與恢復', async () => {
  const c=setup(); await sync(c,report()); c.advance(31*60000);
  assert.ok((await state(c)).alerts.some(a=>a.level==='red' && a.text.includes('31')));
  c.sql.prepare('UPDATE tv SET report=?').run(JSON.stringify(report({screen:'off'})));
  let alerts=(await state(c)).alerts; assert.ok(alerts.some(a=>a.level==='yellow' && a.text.includes('31'))); assert.ok(!alerts.some(a=>a.level==='red')); assert.ok(alerts.some(a=>a.text.includes('影子模式')));
  await sync(c,report()); assert.ok(!(await state(c)).alerts.some(a=>a.level==='red')); assert.match(c.sql.prepare("SELECT detail FROM events WHERE kind='online'").get().detail,/31/);
  for(let i=0;i<4;i++) await sync(c,report({adb:'io'})); assert.ok((await state(c)).alerts.some(a=>a.level==='red' && a.text.includes('adb'))); assert.equal(c.sql.prepare("SELECT count(*) n FROM events WHERE kind='adb-bad'").get().n,1);
  await sync(c,report({yt:'disabled',gap_s:400})); await sync(c,report({yt:'enabled'})); alerts=(await state(c)).alerts; assert.ok(!alerts.some(a=>a.level==='red'));
  for(const kind of ['adb-ok','lock','unlock','gap']) assert.equal(c.sql.prepare('SELECT count(*) n FROM events WHERE kind=?').get(kind).n,1);
});

test('5 家長頁', async () => {
  const c=setup(), res=await c.call('/tv/'), html=await res.text(); assert.equal(res.status,200); assert.match(res.headers.get('content-type'),/text\/html/); assert.equal(res.headers.get('cache-control'),'no-store');
  for(const word of ['+15','+30','+60','歸零']) assert.ok(html.includes(word)); assert.ok(!html.includes(c.env.TV_CLIENT_ID));
});

test('U2-① 舊回報（無新欄位）sync 照舊 200 且不寫 usage_daily', async () => {
  const c = setup();
  await grant(c, { kind: 'add', minutes: 15, req_id: 'u2old' });
  const res = await sync(c, report());
  assert.equal(res.status, 200);
  assert.deepEqual((await res.json()).grants.map(x => x.seq), [1]);
  assert.equal(c.sql.prepare('SELECT count(*) n FROM usage_daily').get().n, 0);
});

test('U2-①b usage_daily 不存在時新回報 sync 仍 200 且 grants 正常', async () => {
  const c = setup();
  await grant(c, { kind: 'add', minutes: 30, req_id: 'u2new' });
  c.sql.exec('DROP TABLE usage_daily');
  let logged = '';
  const orig = console.error; console.error = (...a) => { logged += a.join(' '); };
  try {
    const res = await sync(c, report({ today: '2028-01-15', used_today_yt_s: 60, used_today_nf_s: 30 }));
    assert.equal(res.status, 200);
    assert.deepEqual((await res.json()).grants.map(x => x.seq), [1]);
    assert.deepEqual((await state(c)).usage.today, { youtube_s: 0, netflix_s: 0 });
  } finally { console.error = orig; }
  assert.match(logged, /usage_daily/);
});

test('U2-② 新回報 upsert：較小秒數不覆蓋較大者', async () => {
  const c = setup();
  const sec = Math.floor(c.now / 1000);
  const today = new Date((sec + 8 * 3600) * 1000).toISOString().slice(0, 10);
  assert.equal((await sync(c, report({ today, used_today_yt_s: 300, used_today_nf_s: 50 }))).status, 200);
  assert.equal((await sync(c, report({ today, used_today_yt_s: 100, used_today_nf_s: 200 }))).status, 200);
  const seconds = app => c.sql.prepare('SELECT seconds FROM usage_daily WHERE date=? AND app=?').get(today, app).seconds;
  assert.equal(seconds('youtube'), 300);
  assert.equal(seconds('netflix'), 200);
});

test('U2-③ 只給部分新欄位 → 400', async () => {
  const c = setup();
  const full = { today: '2028-01-15', used_today_yt_s: 60, used_today_nf_s: 30 };
  const bads = [
    { today: full.today },
    { today: full.today, used_today_yt_s: 60 },
    { used_today_yt_s: 60, used_today_nf_s: 30 },
    { ...full, today: '15/01/2028' },
    { ...full, today: '2028-1-5' },
    { ...full, used_today_yt_s: 86401 },
    { ...full, used_today_nf_s: -1 },
    { ...full, used_today_yt_s: 1.5 },
    { ...full, used_today_yt_s: '60' },
  ];
  for (const b of bads) assert.equal((await sync(c, report(b))).status, 400, JSON.stringify(b));
  assert.equal((await sync(c, report(full))).status, 200);
});

test('U2-④ state usage today/week/month 跨週跨月加總', async () => {
  const c = setup();
  const sec = Math.floor(c.now / 1000);
  const today = new Date((sec + 8 * 3600) * 1000).toISOString().slice(0, 10);
  const add = (d, n) => new Date(Date.parse(d + 'T00:00:00Z') + n * 86400000).toISOString().slice(0, 10);
  const sinceMonday = (new Date((sec + 8 * 3600) * 1000).getUTCDay() + 6) % 7;
  const weekStart = add(today, -sinceMonday), monthStart = today.slice(0, 8) + '01';
  const plan = new Map();
  plan.set(add(today, -8), [120, 120]);
  plan.set(add(monthStart, -1), [6000, 6000]);
  plan.set(add(today, -1), [60, 30]);
  if (!plan.has(monthStart) && monthStart !== today) plan.set(monthStart, [30, 30]);
  const inserted = [];
  for (const [date, [yt, nf]] of plan) {
    c.sql.prepare(`INSERT INTO usage_daily(date,app,seconds) VALUES(?,'youtube',?)`).run(date, yt);
    c.sql.prepare(`INSERT INTO usage_daily(date,app,seconds) VALUES(?,'netflix',?)`).run(date, nf);
    inserted.push({ date, youtube_s: yt, netflix_s: nf });
  }
  assert.equal((await sync(c, report({ today, used_today_yt_s: 600, used_today_nf_s: 300 }))).status, 200);
  inserted.push({ date: today, youtube_s: 600, netflix_s: 300 });
  const u = (await state(c)).usage;
  assert.deepEqual(u.today, { youtube_s: 600, netflix_s: 300 });
  const inRange = (d, from) => d >= from && d <= today;
  const sum = from => inserted.filter(r => inRange(r.date, from))
    .reduce((a, r) => ({ youtube_s: a.youtube_s + r.youtube_s, netflix_s: a.netflix_s + r.netflix_s }), { youtube_s: 0, netflix_s: 0 });
  assert.deepEqual(u.week, sum(weekStart));
  assert.deepEqual(u.month, sum(monthStart));
  assert.deepEqual(u.days, inserted.filter(r => inRange(r.date, monthStart)).sort((a, b) => (a.date < b.date ? 1 : -1)));
  assert.ok(u.week.youtube_s >= 600 && u.week.netflix_s >= 300);
  assert.ok(u.month.youtube_s >= 600 && u.month.netflix_s >= 300);
  assert.ok(!u.days.some(d => d.date === add(monthStart, -1)));
});

test('U2-④b 跨月週：week 含上月紀錄，month/days 不含', async () => {
  const c = setup();
  c.advance(Date.parse('2026-11-01T04:00:00Z') - c.now);
  const sec = Math.floor(c.now / 1000);
  const today = new Date((sec + 8 * 3600) * 1000).toISOString().slice(0, 10);
  assert.equal(today, '2026-11-01');
  const put = (date, app, seconds) => c.sql.prepare(`INSERT INTO usage_daily(date,app,seconds) VALUES(?,?,?)`).run(date, app, seconds);
  put('2026-10-26', 'youtube', 100); put('2026-10-26', 'netflix', 10);
  put('2026-10-28', 'youtube', 200); put('2026-10-28', 'netflix', 20);
  assert.equal((await sync(c, report({ today: '2026-11-01', used_today_yt_s: 300, used_today_nf_s: 30 }))).status, 200);
  const u = (await state(c)).usage;
  assert.deepEqual(u.today, { youtube_s: 300, netflix_s: 30 });
  assert.deepEqual(u.week, { youtube_s: 600, netflix_s: 60 });
  assert.deepEqual(u.month, { youtube_s: 300, netflix_s: 30 });
  assert.deepEqual(u.days, [{ date: '2026-11-01', youtube_s: 300, netflix_s: 30 }]);
});

test('U2-⑤ 紀錄白話化', async () => {
  assert.equal(eventText('grant', '1800'), '加 30 分');
  assert.deepEqual([
    [eventText('grant', '1800'), '加 30 分'],
    [eventText('zero', null), '歸零'],
    [eventText('received', '2'), '電視收到加值'],
    [eventText('lock', null), '已鎖 YouTube／Netflix'],
    [eventText('unlock', null), '已開放 YouTube／Netflix'],
    [eventText('online', '中斷 31 分'), '恢復連線（中斷 31 分）'],
    [eventText('gap', '400'), '回報中斷 6 分'],
    [eventText('adb-bad', 'io'), '無法控制（io）'],
    [eventText('adb-ok', null), '恢復控制'],
    [eventText('reinstall', null), '電視 App 重新安裝'],
    [eventText('db-reset', null), '資料庫重建'],
    [eventText('mystery', 'x'), 'mystery x'],
  ].map(([a, b]) => a === b), new Array(12).fill(true));
  const c = setup();
  await grant(c, { kind: 'add', minutes: 30, req_id: 'u2e2e' });
  await sync(c, report());
  await sync(c, report({ yt: 'disabled' }));
  const events = (await state(c)).events;
  const byKind = Object.fromEntries(events.map(e => [e.kind, e]));
  assert.equal(byKind.grant.text, '加 30 分');
  assert.equal(byKind.lock.text, '已鎖 YouTube／Netflix');
  for (const e of events) assert.equal(e.text, eventText(e.kind, e.detail));
});

test('6 每次同步清理 90 天以前事件', async () => {
  const c=setup(), now=Math.floor(c.now/1000); c.sql.prepare('INSERT INTO events(ts,who,kind) VALUES(?,?,?)').run(now-90*86400-1,'電視','gap'); c.sql.prepare('INSERT INTO events(ts,who,kind) VALUES(?,?,?)').run(now-90*86400,'電視','gap');
  await sync(c,report()); assert.equal(c.sql.prepare("SELECT count(*) n FROM events WHERE kind='gap'").get().n,1); assert.equal(c.sql.prepare("SELECT ts FROM events WHERE kind='gap'").get().ts,now-90*86400);
});

const SCHED = [
  // Fixed synthetic schedule for cross-midnight and weekday-boundary regression cases.
  { days: '0123456', from: '00:00', to: '06:00' },
  { days: '12345', from: '10:00', to: '16:00' },
  { days: '0123456', from: '22:00', to: '24:00' },
];
const T = (mo, d, h, mi) => Date.parse(`2026-${String(mo).padStart(2,'0')}-${String(d).padStart(2,'0')}T${String(h).padStart(2,'0')}:${String(mi).padStart(2,'0')}:00+08:00`) / 1000;
const go = (c, mo, d, h, mi) => c.advance(T(mo, d, h, mi) * 1000 - c.now);
const adultCall = (c, body, opts = {}) => c.call('/tv/api/adult', { method: 'POST', body, ...opts });

test('adult_sched 的 schema 預設只含合成排程範例', () => {
  assert.match(schema, /"from":"00:00","to":"06:00".*"from":"10:00","to":"16:00".*"from":"22:00","to":"24:00"/);
});

test('A1-① 合成排程：平日 11:00 在時段內、until=16:00', () => {
  const w = adultWindow(T(11, 2, 11, 0), SCHED, null);
  assert.equal(w.active, true);
  assert.equal(w.until, T(11, 2, 16, 0));
  assert.equal(w.from, T(11, 2, 10, 0));
});

test('A1-② 合成排程：週六 10:00 不在時段、next=當天 22:00、next_until=週日 06:00', () => {
  const w = adultWindow(T(11, 7, 10, 0), SCHED, null);
  assert.equal(w.active, false);
  assert.equal(w.until, 0);
  assert.equal(w.next_from, T(11, 7, 22, 0));
  assert.equal(w.next_until, T(11, 8, 6, 0));
});

test('A1-③ 合成排程：週一 23:00 跨日合併、until=週二 06:00、next=週二 10:00–16:00', () => {
  const w = adultWindow(T(11, 2, 23, 0), SCHED, null);
  assert.equal(w.active, true);
  assert.equal(w.until, T(11, 3, 6, 0));
  assert.equal(w.next_from, T(11, 3, 10, 0));
  assert.equal(w.next_until, T(11, 3, 16, 0));
});

test('A1-④ 合成排程：週一 05:00 until=06:00、next=10:00', () => {
  const w = adultWindow(T(11, 2, 5, 0), SCHED, null);
  assert.equal(w.active, true);
  assert.equal(w.until, T(11, 2, 6, 0));
  assert.equal(w.next_from, T(11, 2, 10, 0));
  assert.equal(w.next_until, T(11, 2, 16, 0));
});

test('A1-⑤ 合成排程：週五 22:00 until=週六 06:00、next=週六 22:00–週日 06:00（週末白天沒有）', () => {
  const w = adultWindow(T(11, 6, 22, 0), SCHED, null);
  assert.equal(w.active, true);
  assert.equal(w.until, T(11, 7, 6, 0));
  assert.equal(w.next_from, T(11, 7, 22, 0));
  assert.equal(w.next_until, T(11, 8, 6, 0));
});

test('A1-⑥ 合成排程：11:00 關閉 → active=false、off_until=16:00，22:00 恢復', async () => {
  const c = setup(); go(c, 11, 2, 11, 0);
  const res = await adultCall(c, { action: 'off' });
  assert.equal(res.status, 200);
  assert.deepEqual(await res.json(), { active: false, from: 0, until: 0, next_from: T(11, 2, 22, 0), next_until: T(11, 3, 6, 0), off_until: T(11, 2, 16, 0), manual: null, source: null, choices: [{ key: '60', until: T(11, 2, 12, 0) }, { key: '120', until: T(11, 2, 13, 0) }, { key: 'next', until: T(11, 2, 22, 0) }] });
  assert.equal(eventText('adult-off', String(T(11, 2, 16, 0))), '關閉爸媽時段到 11/2（週一）16:00');
  go(c, 11, 2, 22, 0);
  const late = (await state(c)).adult;
  assert.equal(late.active, true);
  assert.equal(late.until, T(11, 3, 6, 0));
});

test('A1-⑦ 合成排程：08:00 按下一段不要 → 10:00–16:00 不算、22:00 照常', async () => {
  const c = setup(); go(c, 11, 2, 8, 0);
  assert.equal((await adultCall(c, { action: 'off' })).status, 200);
  assert.equal((await state(c)).adult.off_until, T(11, 2, 16, 0));
  go(c, 11, 2, 9, 0);
  assert.equal((await state(c)).adult.active, false);
  go(c, 11, 2, 22, 0);
  const late = (await state(c)).adult;
  assert.equal(late.active, true);
  assert.equal(late.until, T(11, 3, 6, 0));
});

test('A1-⑧ off 連送兩次事件只 1 筆；resume 後 off_until 消失', async () => {
  const c = setup(); go(c, 11, 2, 10, 0);
  assert.equal((await adultCall(c, { action: 'off' })).status, 200);
  assert.equal((await adultCall(c, { action: 'off' })).status, 200);
  assert.equal(c.sql.prepare("SELECT count(*) n FROM events WHERE kind='adult-off'").get().n, 1);
  const resumed = await (await adultCall(c, { action: 'resume' })).json();
  assert.equal(resumed.off_until, null);
  assert.equal((await state(c)).adult.off_until, null);
  assert.equal(c.sql.prepare("SELECT count(*) n FROM events WHERE kind='adult-resume'").get().n, 1);
  assert.equal(eventText('adult-resume', null), '恢復爸媽時段');
  assert.equal((await adultCall(c, { action: 'resume' })).status, 200);
  assert.equal(c.sql.prepare("SELECT count(*) n FROM events WHERE kind='adult-resume'").get().n, 1);
});

test('A1-⑨ adult_sched 壞掉 → sync 200、adult_left_s=0', async () => {
  const c = setup(); go(c, 11, 2, 10, 0);
  c.sql.prepare("UPDATE meta SET v='壞掉' WHERE k='adult_sched'").run();
  const res = await sync(c, report());
  assert.equal(res.status, 200);
  assert.equal((await res.json()).adult_left_s, 0);
  assert.equal((await state(c)).adult.active, false);
});

test('A1-⑩ /tv/api/adult 非 parent 403、錯 Origin 403、錯 action 400、無段可關 400', async () => {
  const c = setup(); go(c, 11, 2, 10, 0);
  assert.equal((await adultCall(c, { action: 'off' }, { role: 'tv' })).status, 403);
  assert.equal((await adultCall(c, { action: 'off' }, { origin: 'https://evil.test' })).status, 403);
  assert.equal((await adultCall(c, { action: 'xxx' })).status, 400);
  assert.equal((await adultCall(c, {})).status, 400);
  c.sql.prepare("UPDATE meta SET v='[]' WHERE k='adult_sched'").run();
  assert.equal((await adultCall(c, { action: 'off' })).status, 400);
});

test('A1-⑪ 合成排程：sync 回應平日 11:00 剩 18000 秒', async () => {
  const c = setup(); go(c, 11, 2, 11, 0);
  const res = await sync(c, report());
  assert.equal(res.status, 200);
  assert.equal((await res.json()).adult_left_s, 18000);
});

test('A1-家長頁有模式區（舊爸媽卡已拿掉）', async () => {
  const c = setup();
  const html = await (await c.call('/tv/')).text();
  assert.ok(html.includes('id="modeBox"'));
  assert.ok(!html.includes('id="adultButtons"'));
  assert.ok(html.includes('/tv/api/adult'));
});

test('U4-① 家長頁新版面（c-sheet、row3、modeBox、這 7 天）', async () => {
  const c = setup(), res = await c.call('/tv/'), html = await res.text();
  assert.equal(res.status, 200);
  assert.match(res.headers.get('content-type'), /text\/html/);
  for (const word of ['c-sheet', 'row3', 'modeBox', '這 7 天']) assert.ok(html.includes(word), word);
});

test('U4-② 頁面 script 無反引號與 ${ 且可通過 node --check', () => {
  const m = page.match(/<script>([\s\S]*?)<\/script>/);
  assert.ok(m, 'no script block');
  assert.ok(!m[1].includes('`'), 'script has backtick');
  assert.ok(!m[1].includes('${'), 'script has ${');
  const f = path.join(mkdtempSync(path.join(os.tmpdir(), 'u4-')), 'page-script.mjs');
  writeFileSync(f, m[1]);
  execFileSync(process.execPath, ['--check', f]);
});

test('M1-① adultWindow 手動：單獨／相接合併／off 不影響／過期／壞排程／省參數', () => {
  const now = T(11, 2, 20, 0);
  let w = adultWindow(now, '[]', null, now + 3600);
  assert.equal(w.active, true); assert.equal(w.from, now); assert.equal(w.until, now + 3600);
  w = adultWindow(T(11, 2, 21, 30), SCHED, null, T(11, 2, 22, 0));
  assert.equal(w.active, true); assert.equal(w.until, T(11, 3, 6, 0));
  w = adultWindow(T(11, 2, 10, 0), SCHED, T(11, 2, 16, 0), T(11, 2, 11, 0));
  assert.equal(w.active, true); assert.equal(w.until, T(11, 2, 11, 0));
  w = adultWindow(now, SCHED, null, now - 1);
  assert.deepEqual(w, adultWindow(now, SCHED, null));
  assert.equal(w.active, false);
  w = adultWindow(now, '壞掉', null, now + 3600);
  assert.equal(w.active, true); assert.equal(w.until, now + 3600);
  assert.deepEqual(adultWindow(now, SCHED, null), adultWindow(now, SCHED, null, undefined));
});

test('M1-② on：60／120／next、無效 next、覆蓋、事件、sync', async () => {
  const c = setup(); go(c, 11, 2, 18, 0);
  const now = Math.floor(c.now / 1000);
  let r = await adultCall(c, { action: 'on', choice: '60' });
  assert.equal(r.status, 200);
  let a = await r.json();
  assert.equal(a.active, true); assert.equal(a.from, now); assert.equal(a.until, now + 3600);
  assert.equal(a.source, 'manual');
  assert.deepEqual(a.manual, { until: now + 3600, by: '爸爸', at: now });
  assert.deepEqual(a.choices, [{ key: '60', until: now + 3600 }, { key: '120', until: now + 7200 }, { key: 'next', until: T(11, 2, 22, 0) }]);
  assert.equal(c.sql.prepare("SELECT detail FROM events WHERE kind='adult-on'").get().detail, String(now + 3600));
  assert.equal(eventText('adult-on', String(now + 3600)), '開爸媽模式到 11/2（週一）19:00');
  assert.equal((await (await sync(c, report())).json()).adult_left_s, 3600);
  r = await adultCall(c, { action: 'on', choice: '120' });
  assert.equal(r.status, 200);
  a = await r.json();
  assert.equal(a.until, now + 7200);
  assert.deepEqual(a.manual, { until: now + 7200, by: '爸爸', at: now });
  assert.equal(c.sql.prepare("SELECT count(*) n FROM events WHERE kind='adult-on'").get().n, 2);
  assert.equal((await (await sync(c, report())).json()).adult_left_s, 7200);
  go(c, 11, 2, 20, 0);
  r = await adultCall(c, { action: 'on', choice: 'next' });
  assert.equal(r.status, 200);
  a = await r.json();
  assert.equal(a.source, 'manual');
  assert.deepEqual(a.manual, { until: T(11, 2, 22, 0), by: '爸爸', at: T(11, 2, 20, 0) });
  assert.equal(a.until, T(11, 3, 6, 0));
  c.sql.prepare("UPDATE meta SET v='[]' WHERE k='adult_sched'").run();
  assert.equal((await adultCall(c, { action: 'on', choice: 'next' })).status, 400);
});

test('M1-③ kid：只手動／未開始相接／已開始重疊／只排程／都沒有', async () => {
  let c = setup(); go(c, 11, 2, 18, 0);
  await adultCall(c, { action: 'on', choice: '60' });
  let r = await adultCall(c, { action: 'kid' });
  assert.equal(r.status, 200);
  let a = await r.json();
  assert.equal(a.active, false); assert.equal(a.manual, null); assert.equal(a.source, null);
  assert.equal(c.sql.prepare("SELECT v FROM meta WHERE k='adult_manual'").get(), undefined);
  assert.equal(c.sql.prepare("SELECT v FROM meta WHERE k='adult_off_until'").get(), undefined);
  assert.equal(c.sql.prepare("SELECT count(*) n FROM events WHERE kind='adult-kid'").get().n, 1);
  assert.equal(eventText('adult-kid', null), '切回兒童模式');
  c = setup(); go(c, 11, 2, 20, 0);
  await adultCall(c, { action: 'on', choice: 'next' });
  r = await adultCall(c, { action: 'kid' });
  assert.equal(r.status, 200);
  a = await r.json();
  assert.equal(a.manual, null); assert.equal(a.active, false);
  assert.equal(c.sql.prepare("SELECT v FROM meta WHERE k='adult_off_until'").get(), undefined);
  go(c, 11, 2, 22, 0);
  assert.equal((await state(c)).adult.active, true);
  assert.equal((await state(c)).adult.until, T(11, 3, 6, 0));
  c = setup(); go(c, 11, 2, 10, 0);
  await adultCall(c, { action: 'on', choice: '60' });
  r = await adultCall(c, { action: 'kid' });
  assert.equal(r.status, 200);
  a = await r.json();
  assert.equal(a.active, false); assert.equal(a.manual, null);
  assert.equal(a.off_until, T(11, 2, 16, 0));
  assert.equal((await (await sync(c, report())).json()).adult_left_s, 0);
  c = setup(); go(c, 11, 2, 10, 0);
  r = await adultCall(c, { action: 'kid' });
  assert.equal(r.status, 200);
  assert.equal((await r.json()).off_until, T(11, 2, 16, 0));
  assert.equal(c.sql.prepare("SELECT count(*) n FROM events WHERE kind='adult-kid'").get().n, 1);
  c = setup(); go(c, 11, 2, 18, 0);
  r = await adultCall(c, { action: 'kid' });
  assert.equal(r.status, 200);
  a = await r.json();
  assert.equal(a.active, false); assert.equal(a.manual, null);
  assert.equal(c.sql.prepare("SELECT count(*) n FROM events WHERE kind='adult-kid'").get().n, 0);
});

test('M1-④ state.adult：manual／source／choices（合併、跨日、逾 24h 不給）', async () => {
  const c = setup(); go(c, 11, 2, 20, 0);
  await adultCall(c, { action: 'on', choice: 'next' });
  const a = (await state(c)).adult;
  assert.equal(a.active, true);
  assert.deepEqual(a.manual, { until: T(11, 2, 22, 0), by: '爸爸', at: T(11, 2, 20, 0) });
  assert.equal(a.source, 'manual');
  assert.equal(a.until, T(11, 3, 6, 0));
  assert.deepEqual(a.choices, [
    { key: '60', until: T(11, 2, 21, 0) },
    { key: '120', until: T(11, 2, 22, 0) },
    { key: 'next', until: T(11, 2, 22, 0) },
  ]);
  const d = setup(); go(d, 11, 2, 10, 0);
  const b = (await state(d)).adult;
  assert.equal(b.source, 'sched'); assert.equal(b.manual, null);
  const e = setup();
  e.sql.prepare("UPDATE meta SET v=? WHERE k='adult_sched'").run(JSON.stringify([{ days: '2', from: '10:00', to: '16:00' }]));
  go(e, 11, 2, 10, 0);
  assert.deepEqual((await state(e)).adult.choices, [
    { key: '60', until: T(11, 2, 11, 0) },
    { key: '120', until: T(11, 2, 12, 0) },
    { key: 'next', until: T(11, 3, 10, 0) },
  ]);
  go(e, 11, 2, 8, 0);
  assert.deepEqual((await state(e)).adult.choices, [
    { key: '60', until: T(11, 2, 9, 0) },
    { key: '120', until: T(11, 2, 10, 0) },
  ]);
});

test('M1-④b 壞資料與授權', async () => {
  const c = setup(); go(c, 11, 2, 18, 0);
  const now = Math.floor(c.now / 1000);
  const put = v => c.sql.prepare("INSERT INTO meta(k,v) VALUES('adult_manual',?) ON CONFLICT(k) DO UPDATE SET v=excluded.v").run(v);
  for (const bad of ['壞掉', JSON.stringify({ until: 'x' }), JSON.stringify({ until: -5 }), JSON.stringify({ until: now - 10 }), JSON.stringify({ until: now + 86401 })]) {
    put(bad);
    const a = (await state(c)).adult;
    assert.equal(a.manual, null); assert.equal(a.source, null); assert.equal(a.active, false);
    assert.equal((await (await sync(c, report())).json()).adult_left_s, 0);
  }
  assert.equal((await adultCall(c, { action: 'on', choice: '60' }, { role: 'tv' })).status, 403);
  assert.equal((await adultCall(c, { action: 'kid' }, { role: 'tv' })).status, 403);
  assert.equal((await adultCall(c, { action: 'on', choice: '60' }, { origin: 'https://evil.test' })).status, 403);
  assert.equal((await adultCall(c, { action: 'kid' }, { origin: 'https://evil.test' })).status, 403);
  for (const bad of [{ action: 'on' }, { action: 'on', choice: '30' }, { action: 'kiddo' }]) assert.equal((await adultCall(c, bad)).status, 400);
  c.sql.prepare("UPDATE meta SET v='[]' WHERE k='adult_sched'").run();
  assert.equal((await adultCall(c, { action: 'on', choice: 'next' })).status, 400);
});

test('M1-⑤ 頁面：模式區 id、有 seg／dur、無爸媽卡', async () => {
  const c = setup(), res = await c.call('/tv/'), html = await res.text();
  assert.equal(res.status, 200);
  for (const word of ['id="modeBox"', '模式', '/tv/api/adult']) assert.ok(html.includes(word), word);
  for (const gone of ['adultButtons', 'id="adult"', 'adultTag']) assert.ok(!html.includes(gone), gone);
});

test('U4-③ usage.recent 跨月：今天 11-02、資料在 10-28 與 11-01', async () => {
  const c = setup();
  c.advance(Date.parse('2026-11-02T04:00:00Z') - c.now);
  const put = (date, app, seconds) => c.sql.prepare('INSERT INTO usage_daily(date,app,seconds) VALUES(?,?,?)').run(date, app, seconds);
  put('2026-10-28', 'youtube', 200); put('2026-10-28', 'netflix', 20);
  put('2026-11-01', 'youtube', 300); put('2026-11-01', 'netflix', 30);
  const u = (await state(c)).usage;
  assert.equal(u.recent.length, 7);
  assert.deepEqual(u.recent.map(r => r.date), ['2026-10-27', '2026-10-28', '2026-10-29', '2026-10-30', '2026-10-31', '2026-11-01', '2026-11-02']);
  assert.deepEqual(u.recent[1], { date: '2026-10-28', youtube_s: 200, netflix_s: 20 });
  assert.deepEqual(u.recent[5], { date: '2026-11-01', youtube_s: 300, netflix_s: 30 });
  for (const i of [0, 2, 3, 4, 6]) assert.deepEqual(u.recent[i], { date: u.recent[i].date, youtube_s: 0, netflix_s: 0 });
});
