# WO-U4 回報（家長頁改版，排法 B）

## 改了什麼

- `cfquota/page.mjs`（新增）：家長頁整份 HTML。CSS 與版面照 `mockups/parent-v3.html` 的 `row3` 搬（c-sheet、c-acts row3、u-meter、chart、log、c-confirm 質感）；爸媽時段卡是新的（c-confirm 外觀，左文字右 c-plain 按鈕，時段中多「不扣時間」小標）。頁面 JS 在模板字串內，全用字串串接、無反引號與 `${`。
- `cfquota/lib.mjs`：刪掉舊 `page` 常數、改 `import { page } from './page.mjs'`；`usage()` 查詢起點擴到 `min(週起算、月初、今天−6)` 並新增 `recent`（7 筆舊→新、沒資料補 0）；`emptyUsage()` 補 `recent: []`。grant／zero／adult／sync／路由邏輯完全沒動。
- `cfquota/lib.test.mjs`：只加 3 個新測試（見下），既有測試一字未改。
- `cfquota/README.md`：`usage.recent` 說明、家長頁區塊改列 7 天圖與扣額度行、頁首加一句頁面來源（`page.mjs`、排法 B、note 取代 `alert()`）。

## 新增測試（對應完成標準）

- ① `U4-① 家長頁新版面`：`GET /tv/` 回 200、`text/html`，含 `c-sheet`、`row3`、`adultButtons`、`這 7 天`。
- ② `U4-② 頁面 script … node --check`：抽出 `<script>`，斷言無反引號與 `${`，寫暫存檔後 `node --check` 通過。
- ③ `U4-③ usage.recent 跨月`：時鐘撥到 11-02（台北），`usage_daily` 只有 10-28（200／20）與 11-01（300／30）→ `recent` 共 7 筆、日期 10-27～11-02、該兩天有值、其餘 5 天 0。
- ④ `days` 仍只含本月：沿用既有 `U2-④`／`U2-④b` 即證（本輪未動，照過）。

## 驗收輸出

`cd cfquota && node --test`：tests 29、pass 29、fail 0（含既有 26＋新增 3）。

## 本機看頁面的方式與結果

 genuine 瀏覽器不在本機可用，做法：① `node` 起 DOM stub 跑頁面 JS（`/tmp/u4-smoke.mjs`、`u4-interact.mjs`，不進版控），餵 6 種假 state 看渲染輸出；② 另起流程點按鈕（歸零→確認框→取消／確認、+30 送出、note、disabled）驗互動；③ 產出 `research/wo/WO-U4_preview.html`（單檔，內嵌假資料＋頂部狀態切單，雙 script 皆 `node --check` 通過），請 Opus 開檔目檢。

各狀態渲染（皆符合計畫）：

- A 開放中：電視在線圖示、更新 1 分前、大數字 38 分、「YouTube 和 Netflix 開放中」；下一段「11/2（週一）22:00–11/3（週二）06:00」＋「下一段不要」按鈕；今天看了 25 分、滿格 60 分、扣額度 15 分；藥丸 25 分／3.0／13.6 小時；7 根 bar 各有 `role="img"`＋aria-label、最後一根深色標今天；紀錄「今天 09:12 爸爸 加 30 分」。
- B 已鎖＋封頂＋待接收＋時段中：「YouTube 和 Netflix 已鎖」＋已鎖印章、「已封頂（上限 60 分）」、「待電視接收 30 分」；紅框外多黃警示（影子模式）；爸媽時段行＋「不扣時間」小標＋關閉按鈕。
- C 未知：「無法確認電視狀態」；爸媽時段「已關閉到 …」＋「取消關閉」。
- D 失聯：灰螢幕＋斜線圖示、「更新：35 分鐘前」紅字（late）、紅框警示。
- E 無電視：大數字「—」、「尚無電視回報」、「更新：—」；使用量全 0、紀錄空。
- F XSS：who `<script>alert(1)</script>`、text `<img src=x>`、alert 內 `<b>io</b>` 全部跳脫為 `&lt;…&gt;`；紀錄時間「今天／昨天／M/D HH:MM」三種格式皆對，5 筆→前 4＋「看全部紀錄（5 筆）」。
- 互動：歸零→原地換確認框（確定把剩餘時間歸零？電視會馬上鎖住。＋歸零／取消）；取消回按鈕；+30 送出後 note「已送出，電視約 1 分鐘內收到」；歸零確認後 note「已歸零，電視會馬上鎖住」；全程無 `alert()`（斷言過）。

## git diff --stat

`git diff --stat` 只列已追蹤檔（`page.mjs` 是新增、本機 `git status` 可見，未 commit）：

```
 cfquota/README.md    |  6 ++++--
 cfquota/lib.mjs      | 17 ++++++++---------
 cfquota/lib.test.mjs | 37 ++++++++++++++++++++++++++++++++++++-
```

另有未追蹤：`cfquota/page.mjs`（新增，約 300 行）、`research/wo/WO-U4_preview.html`（預覽用，要求產出）、本檔。`git status` 無其他修改；未動計畫檔、mockups、既有測試、`wrangler.toml`、`worker.mjs`、`schema.sql`；沒跑 adb／deploy／remote。

## 疑慮

1. 無電視時右上顯示「更新：—」是自選（計畫沒寫）；若想要改成不顯示，請說。
2. 時段中爸媽時段行保留了現行文字內的「（不扣時間）」又加小標「不扣時間」，同義出現兩次，是照「沿用現行文字＋多一個小標」做的；若覺得贅，拿掉括號版即可。
3. `recent` 在 `usage()` 丟例外時回 `[]`（非常態，`emptyUsage`）；正常一定 7 筆。
4. 目檢只做到 DOM 字串層級，沒有真瀏覽器截圖；預覽檔請 Opus 開起來看（手機寬 420px 置中、深色今天柱、印章歪斜等細節）。

---

# fix1（Opus 截圖＋GEMINI 審查後的修正，分支 page-u4 工作區未 commit）

## 改了什麼（都在 `cfquota/page.mjs`，單檔；lib／測試／README 未動）

- 爸媽時段卡改上下三行：HTML 把 `adultTag` 小標移到說明 `<p id="adult">` 上面；`.adult` 由左右 flex 改 `display:grid`（小標／說明／按鈕各一行）；按鈕短文字「關閉這一段」「下一段不要」「取消關閉」（範圍只寫在說明行）。說明文字本身沿用現行三種、不動。
- 移除 `.c-plain` 的 `white-space:nowrap`（還原 mock 原樣）；`.adult .c-plain` 左右 padding 歸 0 靠左。
- `.adult .adult-t` 用 `display:grid`（注意：原本 `.adult-t` 的 grid 被 `.c-confirm div{display:flex}` 蓋掉——權重 0,1,1 > 0,1,0，這是上一輪左右擠壓的另一半原因；第一版 fix 漏看，Playwright 量測抓到後已改 `.adult .adult-t` 權重 0,2,0）。
- 防溢出：全域 `*,*::before,*::after{box-sizing:border-box}`；`.c-desk > *,.c-body > *,.c-sheet,.c-confirm,.c-acts > *,.u-pills > *（本來就有 minmax(0,1fr)）、.log li > * 加 `min-width:0`；`.c-alert`、`.log li`、`.c-line`、`.note`、爸媽說明行加 `overflow-wrap:anywhere`。
- `esc()` 補單引號（`'` → `&#39;`）。
- 爸媽時段按鈕 handler 加 `finally { b.disabled = false; }`；`doZero()` 成功後立即 `ui.sending=false; renderActs()` 再 `refresh()`，不再依賴 refresh 成功才重繪按鈕區。
- GEMINI 低嚴重度 3 項（#4 立即重繪、#5 box-sizing、#6 長字斷行）全採納；#1–#3 見上。無不採納項。

## 驗收

- `cd cfquota && node --test`：tests 29、pass 29、fail 0（沿用既有 3 個新測試，未加新測試——本次只改 page 顯示層與按鈕狀態，無新資料語意）。
- 預覽檔 `research/wo/WO-U4_preview.html` 已用最新 page 內容重建（page 模板抽出＋原 pvbar 假資料樁插入，雙 script 皆 `node --check` 通過）。
- Playwright（本機 bundled chromium，`python3 -c "import playwright"` 成功）：390×844，A～F 各一張全頁圖 `research/wo/u4fix_<狀態>.png`；六態 `document.documentElement.scrollWidth` 皆 =390，`.c-desk` 的 scrollWidth<=clientWidth（356<=356），console 無 pageerror。
- B（時段中）實測三行：小標 y490 h22／說明 y518 h45／按鈕 y571 h44；A「下一段不要」、C「取消關閉」短按鈕；D/E 無排程時按鈕區為空（h0）、右側電視圖示／+60／已鎖印章／歸零全可見。u4_A/u4_B 舊圖的問題（右側被切、說明擠成 2～3 字一列、橢圓小標）已消失。

## git diff --stat（本次起點＝上一輪工作區狀態）

追蹤檔 diff 與上一輪相同（`cfquota/README.md`、`cfquota/lib.mjs`、`cfquota/lib.test.mjs`、`research/PLAN_page.md` 共 4 檔；PLAN 是計畫更新，非我改）。我實際動的：未追蹤 `cfquota/page.mjs`（fix1 修改）、`research/wo/WO-U4_preview.html`（重建）、新增 `research/wo/u4fix_A～F.png`（6 張驗證截圖）、本檔追加。未 commit、未動其他資料夾。

## 疑慮

1. 無電視（E）／失聯無排程（D）時爸媽卡顯示「無爸媽時段排程」＋空按鈕區，卡片偏空——沿用原邏輯（按鈕本來就只在三種狀態出現），若想隱藏整張卡請說。
2. 時段中說明行保留「（不扣時間）」＋上方小標「不扣時間」並存，是照計畫第 4 點「小標＋沿用現行文字」做的；若覺得贅，拿掉括號版即可（改一行）。
