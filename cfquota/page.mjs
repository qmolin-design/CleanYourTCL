export const page = `<!doctype html>
<html lang="zh-Hant">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>看電視時間</title>
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Geist+Mono:wght@400&family=Hanken+Grotesk:wght@500;600&family=Noto+Sans+TC:wght@400;500;700&display=swap">
<style>
:root {
  --c-paper: oklch(97% 0.006 55); --c-paper-2: oklch(94.5% 0.007 55); --c-paper-3: oklch(91.5% 0.009 55);
  --c-ink: oklch(21% 0.012 38); --c-ink-2: oklch(29% 0.013 40); --c-muted: oklch(47% 0.013 45);
  --c-rule: oklch(87% 0.009 55); --c-cotton: oklch(98.8% 0.0045 80); --c-desk: oklch(94.3% 0.008 64);
  --c-board: oklch(22% 0.011 40); --c-board-type: oklch(96.5% 0.006 70);
  --c-accent: oklch(74% 0.125 30); --c-accent-2: oklch(54% 0.165 29); --c-paid: oklch(55.5% 0.15 33);
  --c-ok: oklch(56% 0.11 150); --c-warn: oklch(55% 0.13 65);
  --c-yt: oklch(60% 0.06 50); --c-yt-soft: oklch(72% 0.045 55); --c-nf: oklch(74% 0.11 35); --c-nf-soft: oklch(85% 0.06 40); --c-today-band: oklch(92% 0.02 50);
  --c-edge: inset 0 1px 0 oklch(100% 0.005 80 / 0.9), inset 0 -1px 0 oklch(32% 0.03 50 / 0.07), 0 0 0 0.5px oklch(32% 0.03 50 / 0.22), 0 1px 0 oklch(32% 0.03 50 / 0.12);
  --c-cast: 6px 5px 10px -6px oklch(32% 0.03 50 / 0.3); --c-cast-lift: 12px 10px 18px -8px oklch(32% 0.03 50 / 0.3);
  --c-soft: 0 1px 2px oklch(32% 0.03 50 / 0.06), 0 10px 22px -14px oklch(32% 0.03 50 / 0.28);
  --c-frame: 0 1px 2px oklch(32% 0.03 50 / 0.06), 0 6px 16px -8px oklch(32% 0.03 50 / 0.16);
  --c-groove: inset 1px 1.5px 2px oklch(32% 0.03 50 / 0.16), inset -1px -1px 0 oklch(100% 0.005 80 / 0.9), 0 0 0 0.5px oklch(32% 0.03 50 / 0.2);
  --c-font: "Hanken Grotesk", "Noto Sans TC", "PingFang TC", "Heiti TC", sans-serif; --c-fig: "Geist Mono", ui-monospace, monospace;
  --c-tex: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='240' height='240'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='.85' numOctaves='2' stitchTiles='stitch'/%3E%3CfeColorMatrix values='0 0 0 0 .45 0 0 0 0 .35 0 0 0 0 .25 0 0 0 .06 0'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  --dur-short: 200ms; --ease: cubic-bezier(0.22, 1, 0.36, 1);
}
*, *::before, *::after { box-sizing: border-box; }
html, body { overflow-x: clip; }
body { margin: 0; background: var(--c-desk); color: var(--c-ink); font: 400 16px/1.5 "Noto Sans TC", "PingFang TC", "Heiti TC", sans-serif; }
button { font: inherit; color: inherit; cursor: pointer; }
:focus-visible { outline: 3px solid oklch(54% 0.17 30); outline-offset: 2px; }
.wrap { max-width: 420px; margin: 0 auto; padding: 16px 16px 48px; }
@media (prefers-reduced-motion: reduce) { *, *::before, *::after { transition-duration: 0ms !important; animation: none !important; } }
.screen { width: 100%; border-radius: 22px; overflow: clip; border: 1px solid var(--c-rule); box-shadow: var(--c-frame); background: var(--c-paper); color: var(--c-ink); font-family: var(--c-font); }
.c-desk { background-color: var(--c-desk); background-image: radial-gradient(90% 120% at 0% 30%, var(--c-cotton), transparent 62%), var(--c-tex); padding: 16px 18px 22px; display: grid; gap: 14px; }
.c-desk > *, .c-body > * { min-width: 0; }
.c-sheet, .c-confirm { min-width: 0; }
.c-top { display: flex; justify-content: space-between; align-items: center; min-height: 36px; }
.c-top b { font-weight: 600; font-size: 1.0625rem; }
.tv { display: inline-flex; width: 36px; height: 36px; align-items: center; justify-content: center; border-radius: 10px; background: var(--c-cotton); box-shadow: var(--c-edge); }
.tv svg { width: 24px; height: 24px; display: block; }
.tv .frame { fill: none; stroke: var(--c-ink-2); stroke-width: 1.6; stroke-linejoin: round; stroke-linecap: round; }
.tv .screen { fill: var(--c-ok); opacity: 0.85; }
.tv.lost .screen { fill: var(--c-paper-3); opacity: 1; }
.tv .cut { stroke: var(--c-warn); stroke-width: 2; stroke-linecap: round; }
.c-sheet { position: relative; background-color: var(--c-cotton); background-image: linear-gradient(100deg, var(--c-cotton), transparent 45%), var(--c-tex); box-shadow: var(--c-edge), var(--c-cast); border-radius: 8px; padding: 14px 18px 18px; display: grid; gap: 4px; }
.c-sheet-top { display: flex; justify-content: space-between; align-items: baseline; gap: 12px; }
.c-lab { margin: 0; color: var(--c-muted); font-weight: 500; font-size: 0.9375rem; }
.c-upd { margin: 0; color: var(--c-muted); font-size: 0.8125rem; font-variant-numeric: tabular-nums; }
.c-upd.late { color: var(--c-warn); font-weight: 500; }
.c-big { margin: 0; display: flex; align-items: baseline; gap: 8px; line-height: 0.9; }
.c-big .n { font-weight: 600; font-size: 5.5rem; letter-spacing: -0.05em; font-variant-numeric: tabular-nums; }
.c-big .u { font-size: 1.5rem; font-weight: 500; }
.c-line { margin: 6px 0 0; color: var(--c-ink-2); font-size: 0.9375rem; }
.c-line + .c-line { margin-top: 0; }
.c-stamp { position: absolute; right: 18px; bottom: 18px; margin: 0; font-weight: 700; font-size: 1.625rem; color: var(--c-paid); border: 3px solid var(--c-paid); border-radius: 6px; padding: 2px 10px; rotate: -8deg; opacity: 0.85; }
.c-alert { margin: 0; padding: 10px 12px; border-radius: 8px; border: 1px solid var(--c-warn); background: var(--c-cotton); font-size: 0.9375rem; overflow-wrap: anywhere; }
.c-alert.red { border-color: var(--c-accent-2); }
.c-acts { display: grid; gap: 10px; }
.c-acts.row3 { grid-template-columns: repeat(3, minmax(0, 1fr)); }
.c-acts.row3 .zero { grid-column: 1 / -1; min-height: 44px; }
.c-btn { min-height: 52px; border: 0; border-radius: 8px; font-weight: 600; font-size: 1.0625rem; background-color: var(--c-cotton); background-image: var(--c-tex); color: var(--c-ink); box-shadow: var(--c-edge), var(--c-cast); transition: transform var(--dur-short) var(--ease), box-shadow var(--dur-short) var(--ease); }
.c-btn:hover { transform: translateY(-1px); box-shadow: var(--c-edge), var(--c-cast-lift); }
.c-btn:active { transform: translateY(1px); }
.c-btn:disabled { opacity: 0.5; cursor: default; transform: none; }
.c-btn.zero { color: var(--c-accent-2); background-color: var(--c-paper-2); }
.c-confirm { display: grid; gap: 10px; padding: 12px 14px; border-radius: 8px; background: var(--c-cotton); box-shadow: var(--c-edge); }
.c-confirm p { margin: 0; }
.c-confirm div { display: flex; gap: 10px; }
.c-ink-btn { border: 0; border-radius: 8px; min-height: 44px; padding: 0 18px; background: var(--c-board); color: var(--c-board-type); font-weight: 600; box-shadow: var(--c-cast); }
.c-ink-btn:disabled { opacity: 0.5; cursor: default; }
.c-plain { border: 0; background: none; min-height: 44px; padding: 0 12px; text-decoration: underline; text-underline-offset: 3px; text-decoration-color: var(--c-accent); }
.c-plain:disabled { opacity: 0.5; cursor: default; }
.mode { display: grid; gap: 10px; padding: 14px; border-radius: 8px; background: var(--c-cotton); box-shadow: var(--c-edge); }
.mode > * { min-width: 0; }
.seg { display: grid; grid-template-columns: 1fr 1fr; background: var(--c-desk); border-radius: 999px; padding: 4px; gap: 4px; }
.seg button { border: 0; border-radius: 999px; min-height: 48px; font: inherit; font-weight: 600; background: transparent; color: var(--c-ink); cursor: pointer; min-width: 0; }
.seg button[aria-pressed="true"] { background: var(--c-board); color: var(--c-board-type); box-shadow: var(--c-cast); cursor: default; }
.seg button.adultOn[aria-pressed="true"] { background: var(--c-accent-2); }
.mode p { margin: 0; font-size: 0.9375rem; overflow-wrap: anywhere; }
.mode .sub { font-size: 0.8125rem; opacity: .75; }
.dur { display: flex; flex-wrap: wrap; gap: 8px; }
.dur button { border: 1px solid var(--c-rule); background: var(--c-cotton); border-radius: 8px; min-height: 44px; padding: 0 14px; font: inherit; min-width: 0; }
.dur button.rec { border-color: var(--c-board); font-weight: 600; }
.note { margin: 0; font-size: 0.875rem; color: var(--c-accent-2); overflow-wrap: anywhere; }
.c-body { padding: 20px 18px 24px; display: grid; gap: 24px; }
.c-h { margin: 0 0 10px; font-weight: 600; font-size: 1.125rem; }
.u-meter { display: grid; gap: 8px; }
.u-track { height: 16px; border-radius: 999px; background: var(--c-paper-2); box-shadow: var(--c-groove); overflow: hidden; display: flex; }
.u-track i { display: block; height: 100%; }
.u-track .yt { background: var(--c-yt); }
.u-track .nf { background: var(--c-nf); }
.u-meter p { margin: 0; font-size: 0.9375rem; color: var(--c-ink-2); }
.u-meter .c-sub { font-size: 0.8125rem; color: var(--c-muted); }
.u-pills { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 10px; margin-top: 4px; }
.u-pill { background-color: var(--c-cotton); background-image: var(--c-tex); border-radius: 12px; padding: 10px 12px; box-shadow: var(--c-edge), var(--c-soft); display: grid; gap: 2px; min-width: 0; }
.u-pill span { font-size: 0.875rem; color: var(--c-muted); }
.u-pill b { font-weight: 600; font-size: 1.625rem; line-height: 1.1; letter-spacing: -0.02em; font-variant-numeric: tabular-nums; }
.u-pill small { font-size: 0.8125rem; color: var(--c-muted); }
.chart { display: grid; grid-template-columns: 2rem minmax(0, 1fr); gap: 6px; }
.chart .axis { position: relative; height: 150px; font-size: 0.75rem; color: var(--c-muted); }
.chart .axis span { position: absolute; right: 0; translate: 0 50%; font-variant-numeric: tabular-nums; }
.chart .plot { position: relative; height: 150px; display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 8px; align-items: end; }
.chart .grid { position: absolute; left: 0; right: 0; height: 0; border-top: 1px dashed var(--c-rule); }
.chart .band { position: absolute; top: -4px; bottom: 0; right: 0; width: calc((100% - 48px) / 7 + 6px); translate: 3px 0; border-radius: 8px 8px 0 0; background: var(--c-today-band); }
.chart .bar { position: relative; display: flex; flex-direction: column-reverse; height: 100%; }
.chart .bar i { display: block; width: 100%; }
.chart .bar i.nf { border-radius: 4px 4px 0 0; }
.chart .bar i.yt:last-of-type { border-radius: 4px 4px 0 0; }
.chart .bar .yt { background: var(--c-yt-soft); }
.chart .bar .nf { background: var(--c-nf-soft); }
.chart .bar.today .yt { background: var(--c-yt); }
.chart .bar.today .nf { background: var(--c-nf); }
.chart .bar em { font-style: normal; font-size: 0.6875rem; color: var(--c-muted); text-align: center; font-family: var(--c-fig); padding-bottom: 2px; }
.chart .bar.today em { color: var(--c-ink); }
.chart .days { grid-column: 2; display: grid; grid-template-columns: repeat(7, minmax(0, 1fr)); gap: 8px; font-size: 0.8125rem; text-align: center; color: var(--c-muted); }
.chart .days .today { color: var(--c-ink); font-weight: 600; }
.legend { display: flex; flex-wrap: wrap; gap: 6px 16px; font-size: 0.875rem; margin-top: 10px; color: var(--c-muted); }
.legend span { display: inline-flex; align-items: center; gap: 6px; }
.legend i { width: 12px; height: 12px; border-radius: 3px; display: inline-block; }
.legend .yt { background: var(--c-yt); }
.legend .nf { background: var(--c-nf); }
.log { list-style: none; margin: 0; padding: 0; display: grid; gap: 8px; }
.log li { display: grid; grid-template-columns: 4.75rem minmax(0, 1fr); gap: 10px; padding: 11px 14px; background-color: var(--c-cotton); background-image: var(--c-tex); border-radius: 12px; box-shadow: var(--c-edge), var(--c-soft); font-size: 0.9375rem; overflow-wrap: anywhere; }
.log li > * { min-width: 0; }
.log time { color: var(--c-muted); font-size: 0.875rem; }
.log b { font-weight: 600; }
.more summary { cursor: pointer; padding: 10px 0; min-height: 44px; text-decoration: underline; text-decoration-color: var(--c-accent); text-underline-offset: 3px; list-style: none; }
.more .log { margin-top: 4px; }
.c-me { margin: 0; color: var(--c-muted); font-size: 0.875rem; }
</style>
</head>
<body>
<div class="wrap"><div class="screen">
<div class="c-desk">
<div class="c-top"><b>看電視時間</b><span id="tvIcon"></span></div>
<div id="sheet"></div>
<div id="alerts"></div>
<div id="acts"></div>
<p class="note" id="note" hidden></p>
</div>
<div class="c-body">
<section aria-label="使用時間"><h2 class="c-h" id="useTitle">今天看了 0 分</h2><div id="meter"></div><div class="u-pills" id="pills"></div></section>
<section><h2 class="c-h">這 7 天</h2><div id="chart"></div></section>
<section><h2 class="c-h">紀錄</h2><div id="log"></div></section>
<section><h2 class="c-h">模式</h2><div class="mode" id="modeBox"></div></section>
<p class="c-me" id="me"></p>
</div>
</div></div>
<script>
var $ = function(id){ return document.getElementById(id); };
var ui = { confirm: false, sending: false, note: '', modeAsk: false, modeBusy: false };
var curS = null;
var capMin = 60;
var WK = ['日','一','二','三','四','五','六'];
function esc(s){ return String(s).replace(/[&<>"']/g, function(c){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]; }); }
function p2(n){ return String(n).padStart(2, '0'); }
function tp(ts){ var d = new Date((Number(ts) + 8 * 3600) * 1000); return { y: d.getUTCFullYear(), m: d.getUTCMonth() + 1, d: d.getUTCDate(), h: d.getUTCHours(), mi: d.getUTCMinutes(), wd: d.getUTCDay() }; }
function hm(ts){ var t = tp(ts); return p2(t.h) + ':' + p2(t.mi); }
function untilShort(ts, nowS){ var t = tp(ts), dn = dayNum(ts), today = dayNum(nowS); if (t.h === 0 && t.mi === 0 && dn === today + 1) return '24:00'; if (dn === today) return hm(ts); if (dn === today + 1) return '明天 ' + hm(ts); return t.m + '/' + t.d + ' ' + hm(ts); }
function startLabel(ts, nowS){ var t = tp(ts), dn = dayNum(ts), today = dayNum(nowS); if (dn === today) return (t.h >= 18 ? '到今晚 ' : '到 ') + hm(ts); if (dn === today + 1) return '到明天 ' + hm(ts); return '到' + t.m + '/' + t.d + ' ' + hm(ts); }
function openAt(ts, nowS){ var t = tp(ts), dn = dayNum(ts), today = dayNum(nowS); if (dn === today) return hm(ts); if (dn === today + 1) return '明天 ' + hm(ts); return t.m + '/' + t.d + ' ' + hm(ts); }
function choiceLabel(key, until, nowS){ if (key === '60') return '1 小時'; if (key === '120') return '2 小時'; return startLabel(until, nowS); }
function segHtml(kidPressed, dis){ return '<div class="seg" role="group" aria-label="模式"><button type="button" aria-pressed="' + (kidPressed ? 'true' : 'false') + '"' + dis + ' id="modeKidBtn">兒童模式</button><button type="button" class="adultOn" aria-pressed="' + (kidPressed ? 'false' : 'true') + '"' + dis + ' id="modeAdultBtn">爸媽模式</button></div>'; }
function renderMode(s, nowS){
  var box = $('modeBox');
  if (!box) return;
  var ad = s ? s.adult : null;
  var dis = ui.modeBusy ? ' disabled' : '';
  if (ad && ad.active){
    if (ad.source === 'manual' && ad.manual){
      box.innerHTML = segHtml(false, dis)
        + '<p>爸媽模式中：不鎖、不扣時間，<b>到 ' + esc(untilShort(ad.until, nowS)) + '</b>' + (ad.until > ad.manual.until ? '（接上排程時段）' : '') + '自動切回兒童模式。</p>'
        + '<p class="sub">由 ' + esc(ad.manual.by) + ' 在 ' + esc(openAt(ad.manual.at, nowS)) + ' 開啟。按「兒童模式」立刻切回，電視約 1 分鐘內收到。</p>';
    } else {
      box.innerHTML = segHtml(false, dis)
        + '<p>爸媽模式中（排程時段）：到 <b>' + esc(untilShort(ad.until, nowS)) + '</b> 自動切回兒童模式。</p>'
        + '<p class="sub">按「兒童模式」＝關閉這一段排程，下一段照常。</p>';
    }
    return;
  }
  if (ui.modeAsk && ad && ad.choices){
    var i, c, html = segHtml(true, dis) + '<p><b>切到爸媽模式多久？</b>期間電視不鎖、不扣時間，時間到自動切回兒童模式。</p><div class="dur">';
    var nextLabel = '';
    for (i = 0; i < ad.choices.length; i++){
      c = ad.choices[i];
      if (c.key === 'next') nextLabel = choiceLabel(c.key, c.until, nowS);
      html += '<button type="button"' + (c.key === 'next' ? ' class="rec"' : '') + ' data-choice="' + esc(c.key) + '"' + dis + '>' + esc(choiceLabel(c.key, c.until, nowS)) + '</button>';
    }
    html += '<button type="button" class="c-plain" id="modeCancel"' + dis + ' style="border:0">取消</button></div>';
    if (nextLabel) html += '<p class="sub">「' + esc(nextLabel) + '」＝接上爸媽時段。</p>';
    box.innerHTML = html;
    return;
  }
  box.innerHTML = segHtml(true, dis) + '<p>現在是兒童模式：看 YouTube／Netflix 會扣時間，用完就鎖。</p>';
}
function setModeDisabled(on){
  var box = $('modeBox');
  if (!box) return;
  var btns = box.getElementsByTagName('button');
  for (var i = 0; i < btns.length; i++) btns[i].disabled = on;
}
async function doModeOn(choice){
  if (ui.modeBusy) return;
  ui.modeBusy = true; setModeDisabled(true);
  var ok = false;
  try {
    var r = await fetch('/tv/api/adult', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ action: 'on', choice: choice }) });
    if (!r.ok) throw new Error('adult on failed');
    ok = true;
  } catch (e) { ui.note = '送出失敗，請重新整理確認'; }
  ui.modeBusy = false;
  if (ok) ui.modeAsk = false;
  setModeDisabled(false);
  await refresh();
  if (ok) ui.note = '已切到爸媽模式，電視約 10 秒內開放';
  renderNote();
}
async function doModeKid(){
  if (ui.modeBusy) return;
  ui.modeBusy = true; setModeDisabled(true);
  var ok = false;
  try {
    var r = await fetch('/tv/api/adult', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ action: 'kid' }) });
    if (!r.ok) throw new Error('adult kid failed');
    ok = true;
  } catch (e) { ui.note = '送出失敗，請重新整理確認'; }
  ui.modeBusy = false; ui.modeAsk = false;
  setModeDisabled(false);
  await refresh();
  if (ok) ui.note = '已切回兒童模式，電視約 1 分鐘內收到';
  renderNote();
}
function fl(s){ return Math.floor(Number(s) / 60); }
function dayNum(ts){ return Math.floor((Number(ts) + 8 * 3600) / 86400); }
function logTime(ts, nowS){ var t = tp(ts), hm = p2(t.h) + ':' + p2(t.mi), dn = dayNum(ts), today = dayNum(nowS); if (dn === today) return '今天 ' + hm; if (dn === today - 1) return '昨天 ' + hm; return t.m + '/' + t.d + ' ' + hm; }
function uuid(){ if (window.crypto && window.crypto.randomUUID) return window.crypto.randomUUID(); return String(Date.now()) + '-' + Math.floor(Math.random() * 1000000); }
function tvIcon(lost){
  var label = lost ? '電視失聯' : '電視在線';
  return '<span class="tv' + (lost ? ' lost' : '') + '" role="img" aria-label="' + label + '" title="' + label + '"><svg viewBox="0 0 24 24" aria-hidden="true"><rect class="screen" x="4" y="5.5" width="16" height="10" rx="1"/><rect class="frame" x="2.5" y="4" width="19" height="13" rx="2"/><path class="frame" d="M8 20.5h8M12 17v3.5"/>' + (lost ? '<path class="cut" d="M4 3l16 17"/>' : '') + '</svg></span>';
}
function renderSheet(s, nowS){
  var t = s.tv, i, g, pending = 0;
  for (i = 0; i < s.pending.length; i++){ g = s.pending[i]; if (g.kind === 'add') pending += g.seconds; }
  var upd;
  if (!t) upd = '<p class="c-upd">更新：—</p>';
  else { var m = Math.floor((nowS - t.last_seen) / 60); upd = m <= 0 ? '<p class="c-upd">更新：剛剛</p>' : '<p class="c-upd' + (m >= 30 ? ' late' : '') + '">更新：' + m + ' 分鐘前</p>'; }
  var big = t ? '<span class="n">' + fl(t.balance_s) + '</span><span class="u">分</span>' : '<span class="n">—</span><span class="u">分</span>';
  var line;
  if (!t) line = '<p class="c-line">尚無電視回報</p>';
  else if (t.yt === 'disabled') line = '<p class="c-line">YouTube 和 Netflix 已鎖</p>';
  else if (t.yt === 'unknown') line = '<p class="c-line">無法確認電視狀態</p>';
  else line = '<p class="c-line">YouTube 和 Netflix 開放中</p>';
  var extra = '';
  if (t && t.capped) extra += '<p class="c-line">已封頂（上限 ' + fl(s.cap_s) + ' 分）</p>';
  if (pending > 0) extra += '<p class="c-line">待電視接收 ' + fl(pending) + ' 分</p>';
  var stamp = (t && t.yt === 'disabled') ? '<p class="c-stamp" aria-hidden="true">已鎖</p>' : '';
  $('sheet').innerHTML = '<div class="c-sheet"><div class="c-sheet-top"><p class="c-lab">今天還剩</p>' + upd + '</div><p class="c-big">' + big + '</p>' + line + extra + stamp + '</div>';
  $('tvIcon').innerHTML = tvIcon(!t || (nowS - t.last_seen >= 1800));
}
function renderAlerts(s){
  var html = '';
  for (var i = 0; i < s.alerts.length; i++){ var a = s.alerts[i]; html += '<p class="c-alert ' + (a.level === 'red' ? 'red' : 'yellow') + '">' + esc(a.text) + '</p>'; }
  $('alerts').innerHTML = html;
}
function renderActs(){
  var dis = ui.sending ? ' disabled' : '';
  if (ui.confirm){
    $('acts').innerHTML = '<div class="c-confirm"><p>確定把剩餘時間歸零？電視會馬上鎖住。</p><div><button type="button" class="c-ink-btn" id="zeroOk"' + dis + '>歸零</button><button type="button" class="c-plain" id="zeroNo"' + dis + '>取消</button></div></div>';
    return;
  }
  $('acts').innerHTML = '<div class="c-acts row3"><button type="button" class="c-btn" data-min="15"' + dis + '>+15 分</button><button type="button" class="c-btn" data-min="30"' + dis + '>+30 分</button><button type="button" class="c-btn" data-min="60"' + dis + '>+60 分</button><button type="button" class="c-btn zero" id="zeroBtn"' + dis + '>歸零</button></div>';
}
function renderNote(){ $('note').textContent = ui.note; $('note').hidden = !ui.note; }
function renderUsage(s){
  var ty = s.usage.today, n = fl(ty.youtube_s + ty.netflix_s);
  $('useTitle').textContent = '今天看了 ' + n + ' 分';
  var cap = s.cap_s > 0 ? s.cap_s : 3600;
  var yw = Math.min(100, ty.youtube_s / cap * 100), nw = Math.min(100, ty.netflix_s / cap * 100);
  var line1 = 'YouTube ' + fl(ty.youtube_s) + ' 分、Netflix ' + fl(ty.netflix_s) + ' 分（滿格 ' + fl(cap) + ' 分）';
  var line2 = s.tv ? '今天扣額度 ' + fl(s.tv.used_today_s) + ' 分（爸媽時段不算）' : '';
  $('meter').innerHTML = '<div class="u-meter"><div class="u-track" role="img" aria-label="今天 YouTube ' + fl(ty.youtube_s) + ' 分，Netflix ' + fl(ty.netflix_s) + ' 分，以 ' + fl(cap) + ' 分為滿"><i class="yt" style="width:' + yw + '%"></i><i class="nf" style="width:' + nw + '%"></i></div><p>' + line1 + '</p>' + (line2 ? '<p class="c-sub">' + line2 + '</p>' : '') + '</div>';
  var w = s.usage.week, mo = s.usage.month;
  $('pills').innerHTML = '<div class="u-pill"><span>今天</span><b>' + n + '</b><small>分</small></div><div class="u-pill"><span>本週</span><b>' + ((w.youtube_s + w.netflix_s) / 3600).toFixed(1) + '</b><small>小時</small></div><div class="u-pill"><span>本月</span><b>' + ((mo.youtube_s + mo.netflix_s) / 3600).toFixed(1) + '</b><small>小時</small></div>';
}
function renderChart(s){
  var rows = s.usage.recent || [], i, r, mx = 0, totals = [];
  for (i = 0; i < rows.length; i++){ totals.push(fl(rows[i].youtube_s + rows[i].netflix_s)); if (totals[i] > mx) mx = totals[i]; }
  var yMax = Math.max(60, Math.ceil(mx / 30) * 30), v, grid = '', axis = '';
  for (v = 30; v < yMax; v += 30) grid += '<span class="grid" style="bottom:' + (v / yMax * 100) + '%"></span>';
  for (v = 0; v <= yMax; v += 30) axis += '<span style="bottom:' + (v / yMax * 100) + '%">' + v + '</span>';
  var bars = '', days = '';
  for (i = 0; i < rows.length; i++){
    r = rows[i];
    var last = i === rows.length - 1;
    var wd = WK[new Date(Date.parse(r.date + 'T00:00:00Z')).getUTCDay()];
    var y = fl(r.youtube_s), f = fl(r.netflix_s);
    var label = (last ? '今天' : r.date + '（週' + wd + '）') + '：YouTube ' + y + ' 分，Netflix ' + f + ' 分';
    bars += '<div class="bar' + (last ? ' today' : '') + '" role="img" aria-label="' + esc(label) + '"><i class="yt" style="height:' + (r.youtube_s / 60 / yMax * 100) + '%"></i>' + (r.netflix_s ? '<i class="nf" style="height:' + (r.netflix_s / 60 / yMax * 100) + '%"></i>' : '') + '<em aria-hidden="true">' + (y + f) + '</em></div>';
    days += '<span' + (last ? ' class="today"' : '') + '>' + esc(last ? '今天' : wd) + '</span>';
  }
  $('chart').innerHTML = '<div class="chart"><div class="axis" aria-hidden="true">' + axis + '</div><div class="plot"><span class="band" aria-hidden="true"></span>' + grid + bars + '</div><div class="days" aria-hidden="true">' + days + '</div></div><div class="legend"><span><i class="yt"></i>YouTube</span><span><i class="nf"></i>Netflix</span><span>單位：分，淡色是過去幾天</span></div>';
}
function renderLog(s, nowS){
  var rows = s.events, n = 4, i, html = '', rest = '';
  for (i = 0; i < rows.length && i < n; i++) html += '<li><time>' + esc(logTime(rows[i].ts, nowS)) + '</time><span><b>' + esc(rows[i].who) + '</b> ' + esc(rows[i].text) + '</span></li>';
  for (i = n; i < rows.length; i++) rest += '<li><time>' + esc(logTime(rows[i].ts, nowS)) + '</time><span><b>' + esc(rows[i].who) + '</b> ' + esc(rows[i].text) + '</span></li>';
  $('log').innerHTML = '<ul class="log">' + html + '</ul>' + (rows.length > n ? '<details class="more"><summary>看全部紀錄（' + rows.length + ' 筆）</summary><ul class="log">' + rest + '</ul></details>' : '');
}
function renderAll(s, nowS){
  capMin = fl(s.cap_s);
  curS = s;
  renderSheet(s, nowS);
  renderAlerts(s);
  if (!ui.modeBusy && !ui.modeAsk) renderMode(s, nowS);
  if (!ui.confirm) renderActs();
  renderNote();
  renderUsage(s);
  renderChart(s);
  renderLog(s, nowS);
  $('me').textContent = '目前登入：' + s.me;
}
async function refresh(){
  var r;
  try { r = await fetch('/tv/api/state', { cache: 'no-store' }); } catch (e) { return; }
  if (!r.ok) return;
  renderAll(await r.json(), Math.floor(Date.now() / 1000));
}
async function doAdd(min){
  ui.sending = true; renderActs();
  var ok = false, cap = false;
  try {
    var r = await fetch('/tv/api/grant', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ kind: 'add', minutes: min, req_id: uuid() }) });
    if (!r.ok) throw new Error('grant failed');
    cap = (await r.json()).would_cap;
    ok = true;
  } catch (e) { ui.note = '送出失敗，請重新整理確認'; }
  ui.sending = false;
  await refresh();
  if (ok) ui.note = cap ? '最多 ' + capMin + ' 分，會補到 ' + capMin + ' 分' : '已送出，電視約 1 分鐘內收到';
  renderNote();
}
async function doZero(){
  ui.sending = true; renderActs();
  try {
    var r = await fetch('/tv/api/grant', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ kind: 'zero', req_id: uuid() }) });
    if (!r.ok) throw new Error('zero failed');
    ui.confirm = false; ui.sending = false; renderActs(); ui.note = '已歸零，電視會馬上鎖住';
  } catch (e) { ui.note = '送出失敗，請重新整理確認'; ui.sending = false; renderActs(); renderNote(); return; }
  await refresh();
  renderNote();
}
$('acts').addEventListener('click', function(e){
  var b = e.target.closest ? e.target.closest('button') : null;
  if (!b || b.disabled) return;
  if (b.id === 'zeroBtn'){ ui.confirm = true; renderActs(); var ok = $('zeroOk'); if (ok) ok.focus(); }
  else if (b.id === 'zeroNo'){ ui.confirm = false; renderActs(); }
  else if (b.id === 'zeroOk'){ doZero(); }
  else if (b.getAttribute('data-min')){ doAdd(Number(b.getAttribute('data-min'))); }
});
$('modeBox').addEventListener('click', function(e){
  var b = e.target.closest ? e.target.closest('button') : null;
  if (!b || b.disabled) return;
  if (b.id === 'modeAdultBtn'){ if (!ui.modeAsk && curS && !(curS.adult && curS.adult.active)){ ui.modeAsk = true; renderMode(curS, Math.floor(Date.now() / 1000)); } }
  else if (b.id === 'modeKidBtn'){ if (ui.modeAsk){ ui.modeAsk = false; renderMode(curS, Math.floor(Date.now() / 1000)); } else { doModeKid(); } }
  else if (b.id === 'modeCancel'){ ui.modeAsk = false; renderMode(curS, Math.floor(Date.now() / 1000)); }
  else if (b.getAttribute('data-choice')){ doModeOn(b.getAttribute('data-choice')); }
});
refresh();
setInterval(refresh, 15000);
</script>
</body>
</html>`;
