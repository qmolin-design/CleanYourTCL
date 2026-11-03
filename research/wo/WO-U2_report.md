# WO-U2 施工報告（分支 usage-u2，未 commit／未 push）

## 改了什麼（每檔一兩行）

- `cfquota/schema.sql`：新增 `usage_daily(date, app, seconds)` 表（PRIMARY KEY(date,app)，app 限 youtube／netflix，seconds≥0）。`IF NOT EXISTS`，不清舊資料。
- `cfquota/lib.mjs`：
  - `validReport` 加可選三欄 `today`／`used_today_yt_s`／`used_today_nf_s`：全缺＝舊回報有效；出現任一就三個都要有，`today` 合 `^\d{4}-\d{2}-\d{2}$`、兩秒數為整數且 ≤86400，否則 400。
  - `sync` 既有 batch 不動；成功後另起 batch 做兩列 MAX-upsert（重送／亂序不倒退），包 try/catch，失敗只 `console.error`、照回 200＋grants。
  - `state` 回應加 `usage`（today／week／month 各 `{youtube_s,netflix_s}`、`days` 本月1日→今天、新到舊）；「今天」用 Worker 時間＋8小時算台北日期、週一為週起算；表不存在時回全零不 500。
  - 家長頁加「使用時間」區塊（今天／本週／本月三列＋本月每天；秒→分一律 floor，合計＝兩秒數相加後 floor）與紀錄白話化（`eventText`，服務端 export＋頁面內嵌同邏輯各一份）；舊「今天用了 N 分」（扣額）那行保留。
- `cfquota/lib.test.mjs`：只新增 6 個測試，既有 7 個一字未改。
- `cfquota/README.md`：補「使用時間統計」一節（表、sync 欄位、state usage、頁面、白話對照）。

## 新增了哪些測試（對應完成標準編號）

- ① `U2-①`：舊回報（無新欄位）sync 200、grants 正常、`usage_daily` 零列。
- ①b `U2-①b`：DROP 掉 `usage_daily` 後新回報 sync 仍 200、grants 正常、有 console.error；且 `state().usage.today` 為零不炸。
- ② `U2-②`：兩次 sync（yt 300→100、nf 50→200），庫內 yt 保持 300、nf 變 200。
- ③ `U2-③`：9 種壞輸入（缺任一欄、日期格式錯×2、>86400、負數、小數、字串）皆 400；完整三欄 200。
- ④ `U2-④`：SQL 直寫跨週（today-8）、跨月（上月最後一天）、昨天、本月1日＋sync 寫今天；`today` 精確相等，week／month／days 與獨立 filter 期望值 deepEqual，另加非循環錨點（week／month 含今天、上月那筆不在 days）。
- ⑤ `U2-⑤`：`eventText('grant','1800')==='加 30 分'`＋全對照表 12 項（含未知 kind 照原樣）。

## 驗收指令最後輸出（PASS/FAIL 統計）

`cd cfquota && node --test`：tests 13、pass 13、fail 0（含既有 7 個全過）。既有測試未改弱（test 1～6 原樣保留）。

## git diff --stat 結果

```
 cfquota/README.md    |   8 ++++
 cfquota/lib.mjs      |  63 ++++++++++++++++++++++++++++--
 cfquota/lib.test.mjs | 106 ++++++++++++++++++++++++++++++++++++++++++++++++++-
 cfquota/schema.sql   |   1 +
 4 files changed, 174 insertions(+), 4 deletions(-)
```

只動 WO-U2 允許的 4 檔；`worker.mjs`、`wrangler.toml`、Access 驗證、grant／歸零邏輯、路由皆未碰。未 commit，未跑任何 adb／wrangler 指令。

## 你覺得有疑慮的地方

1. `days` 只查 `date<=今天`：若電視時鐘或 `today` 欄位送未來日期，該列寫入庫但 state 不顯示（month/week 也不計）。實務上電視日期即台北當天，影響極小；若上線後發現未來列，可再議是否擋。
2. `validUsageFields` 只驗 regex，未驗「真實存在日期」（如 2028-02-30 會通過並寫入）。照計畫字面只要求 regex；D1 側無 CHECK，髒資料只影響統計顯示，不影響額度。可接受，若要嚴可在 review 提出。
3. 頁面內嵌 `eventLabel` 與服務端 `eventText` 是兩份同邏輯（故意的：頁面跑在瀏覽器不能 import）；日後改對照表要改兩處，已在 README 註記對照關係，review 時請特別核對兩份一致。
4. 測試 ④ 的 weekStart 沿用「週一」公式自行計算（與實作同式），雖另有非循環錨點佐證，嚴格說 week 邊界仍缺一組「寫死日期」的黃金案例；已用 today-8（恆為上週）與精確 today 覆蓋主要風險。

## 第 2 次修正（審核 FAIL 逐條修，原文 /Users/example-owner/TV/research/wo/WO-U2_review_GEMINI.md）

### 問題 1：跨月週 `state.usage.week` 漏算上月紀錄（lib.mjs usage）
- 怎麼修：`usage()` 查詢起始改為 `queryStart = weekStart < monthStart ? weekStart : monthStart`（取兩者較早）；`days` 加 `.filter(d => d.date >= monthStart)`（定義＝本月 1 日到今天）；`sum(from)` 改走 `byDate.values()` 且加 `d.date <= today` 上界（week 含上月、month 不含、未來列不計）。
- 行號：`cfquota/lib.mjs` usage 段（約 120–135 行）。

### 問題 2：白話對照表雙份（eventText vs 頁內 eventLabel）
- 怎麼修：`state()` 查 events 後 `map(e => ({...e, text: eventText(e.kind, e.detail)}))`，回傳含 `text`；家長頁刪除 `eventLabel` 函式（grep 確認零殘留），紀錄行改渲染 `e.text`。全系統只剩 `eventText` 一份對照邏輯。README 補一句「單一來源為服務端 eventText」。
- 行號：`cfquota/lib.mjs` state 段（約 148–154 行）與 page 段（約 168–170 行）。

### 問題 3：測試 U2-④／⑤ 不全
- 新增測試 `U2-④b 跨月週：week 含上月紀錄，month/days 不含`：把 `now` advance 到台北 2026-11-01（週日，weekStart=10-26 < monthStart=11-01），直寫 10-26（100/10）、10-28（200/20）＋sync 寫今天（300/30），斷言 `week={600,60}`、`month={300,30}`、`days` 僅今天一筆。舊程式（查 monthStart 起）在此例 week 只會得 {300,30}，必 FAIL，已達攔截效果。
- 測試 `U2-⑤` 追加端到端：grant 30 分＋兩次 sync（enabled→disabled 觸發 lock），斷言 `state().events` 中 `grant.text==='加 30 分'`、`lock.text==='已鎖 YouTube／Netflix'`，且每筆 `e.text===eventText(e.kind,e.detail)`。

### node --test 最後輸出
```
ℹ tests 14
ℹ pass 14
ℹ fail 0
```
（含既有 7 個全過；既有測試一字未改，只新增 U2-④b 與 U2-⑤ 追加斷言。）

### git diff --stat（本次修正後全量）
```
 cfquota/README.md    |   8 ++++
 cfquota/lib.mjs      |  67 ++++++++++++++++++++++++--
 cfquota/lib.test.mjs | 132 ++++++++++++++++++++++++++++++++++++++++++++++++++-
 cfquota/schema.sql   |   1 +
 4 files changed, 203 insertions(+), 5 deletions(-)
```
只動 WO-U2 允許的 4 檔；`worker.mjs`、`wrangler.toml`、Access 驗證、grant／歸零邏輯、路由未碰。未 commit，未跑任何 adb／wrangler 指令。

### 疑慮
1. `state().events` 多了 `text` 欄位：舊家長頁是同包 `page` 一起發的，無版本錯位問題；純加欄位不影響既有消費者（測試裡 alerts/pending 斷言照過）。
2. `days` 過濾用字串比較 `d.date >= monthStart`：日期固定 yyyy-MM-dd 格式，字典序＝時序，成立；`validReport` 已擋格式。
3. U2-④b 用 `c.advance(target - c.now)` 把時間撥到 2026-11-01（往過去撥）：setup 的憑證 exp 用 `now` 計算，撥後重算的一致，無 401 問題（實測全過）。
