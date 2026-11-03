# PLAN_page：家長頁改版（WO-U4）

狀態：**APPROVED**（2026-11-01 03:5x，Opus；Owner 選定 mock「溫暖柔和第 2 版、排法 B 三個加一個」並說「先開 WO-U4」。只改畫面，低風險，不另做挑戰）

## 依據
- 樣式來源：`mockups/parent-v3.html`（排法 B＝`row3`）。**照它的 CSS 與版面搬**，範例數字換成真資料；mock 外框（intro、兩欄、分頁、範例狀態切換）不要。
- 資料：`/tv/api/state`、`/tv/api/grant`、`/tv/api/adult` 現有欄位。唯一新增資料：`usage.recent`（見下）。

## WO-U4（cfquota）
動到：新增 `cfquota/page.mjs`（`export const page = '...'` 或模板字串）、`cfquota/lib.mjs`（刪掉舊 `page` 常數改 import、`usage()` 加 `recent`）、`cfquota/lib.test.mjs`、`cfquota/README.md`。

### 畫面（由上到下）
1. **頂列**：「看電視時間」＋電視圖示（mock 的 `tvIcon`）。`now − tv.last_seen ≥ 1800` 或沒有 tv → 失聯樣式（灰螢幕＋斜線），否則在線。
2. **剩餘卡**（`c-sheet`）：左上「今天還剩」、右上「更新：N 分鐘前」（`floor((now−last_seen)/60)`；0 → 「更新：剛剛」；≥30 分加 `late`）。大數字＝`floor(balance_s/60)` 分。下面一行：
   - `yt=enabled`：「YouTube 和 Netflix 開放中」；`disabled`：「YouTube 和 Netflix 已鎖」＋「已鎖」印章；`unknown`：「無法確認電視狀態」。
   - `capped`：多一行「已封頂（上限 N 分）」；有待接收：多一行「待電視接收 N 分」。
   - 沒有 tv：大數字顯示「—」，一行「尚無電視回報」。
3. **警示**：`alerts` 每筆一個 `c-alert`（red 用 `--c-accent-2` 框、yellow 用 `--c-warn` 框）。
4. **爸媽時段卡**（新，mock 沒有；用 `c-confirm` 的質感：cotton 底、`--c-edge`、圓角 8）：左邊文字、右邊 `c-plain` 按鈕，上下排：第一行小標（時段中「不扣時間」，其他狀態不顯示）、第二行說明文字（帶日期星期、`arange` 規則，沿用現行頁面三種文字），第三行按鈕。**按鈕文字短**：「關閉這一段」「下一段不要」「取消關閉」（範圍已寫在說明裡，不重複）。（11-01 Opus 審定修正：長按鈕撐破版面）
5. **按鈕**：排法 B：+15 分、+30 分、+60 分一排，歸零一整排（`row3`）。
   - 按歸零 → 原地換成確認框（mock 的 `c-confirm`：「確定把剩餘時間歸零？電視會馬上鎖住。」＋「歸零」「取消」），按「歸零」才送出。
   - 送出中全部按鈕 disabled；成功後按鈕下方 `note`：一般「已送出，電視約 1 分鐘內收到」；`would_cap` →「最多 N 分，會補到 N 分」；歸零 →「已歸零，電視會馬上鎖住」。失敗 →「送出失敗，請重新整理確認」。**不再用 `alert()`**。
6. **今天看了 N 分**（`u-meter`）：N＝`floor((today.youtube_s+today.netflix_s)/60)`；雙色進度條以 `cap_s` 為滿格；下面「YouTube N 分、Netflix N 分（滿格 N 分）」；再下一行小字「今天扣額度 N 分（爸媽時段不算）」＝`floor(tv.used_today_s/60)`。
7. **三顆藥丸**：今天（分）、本週（小時，`toFixed(1)`）、本月（小時，`toFixed(1)`）。
8. **這 7 天**圖：資料用 `usage.recent`（7 筆，舊→新，最後一筆標「今天」，其他標星期「一」～「日」）。Y 軸上限＝`max(60, ceil(最大日合計分/30)*30)`，格線每 30 分；今天深色、其他淡色（mock 的 `.today`）。每根 `role="img"` 帶 aria-label。
9. **紀錄**：前 4 筆＋`details`「看全部紀錄（N 筆）」。時間欄：今天 →「今天 HH:MM」、昨天 →「昨天 HH:MM」、更早 →「M/D HH:MM」（台北時間）。內容＝`<b>who</b> text`（`text` 用 Worker 給的 `eventText`）。
10. 頁尾「目前登入：爸爸」。

### 資料：`usage.recent`
- `state.usage` 新增 `recent`：今天往前 6 天到今天共 7 筆 `{date, youtube_s, netflix_s}`，**舊→新、沒資料補 0**，跨月照算（`usage_daily` 查詢範圍擴到 `today−6`）。`today/week/month/days` 的值與語意**完全不變**（現有 U2 測試照過）。

### 技術
- 字型照 mock 用 Google Fonts（Hanken Grotesk、Geist Mono、Noto Sans TC）；載入失敗要能退回系統字。
- **XSS**：所有來自 API 的字串（who、text、alerts 的 text、adb 錯誤字串等）一律經 `esc()` 或用 `textContent`；不得把未跳脫的資料放進 innerHTML。
- 每 15 秒 refresh（沿用）；refresh 時不要把使用者正在看的確認框或 note 洗掉（確認框打開時跳過重繪按鈕區即可）。
- 行動版寬度優先（mock 的 `max-width: 420px` 置中），16px 側邊距，無橫向捲動；`prefers-reduced-motion` 照 mock。
- 頁面 JS 若放在模板字串內，不得含反引號與 `${`（或改用一般字串串接）。
- 不做：深色模式、month 每日清單（被 7 天圖取代）、圖表互動。

### 完成標準
- `cd cfquota && node --test` 全 PASS（現有測試不能改弱），新增：①`GET /tv/` 回 200、`text/html`，含 `c-sheet`、`row3`、`adultButtons`（或你的爸媽時段容器 id）、`這 7 天`；②抽出頁面 `<script>` 用 `node --check` 通過（寫成測試）；③`usage.recent` 跨月：今天 11-02，資料在 10-28、11-01 → 7 筆、日期 10-27～11-02、該兩天有值、其他 0；④`days` 仍只含本月（沿用 U2 測試即證）。
- 用一份假 state（含 alerts、pending、capped、adult 三種狀態、yt 三種狀態、events 含 `<script>` 字樣的 who/text）在本機開頁面看過（可用 node 起一個小 server 或直接把 page 寫成檔、用假 fetch），截圖或描述寫進回報。
- `git diff --stat` 只動上列 4 檔。
禁止：不改 Access 驗證、grant／歸零／adult／sync 邏輯、路由、`wrangler.toml`、`worker.mjs`、`schema.sql`；不部署。
