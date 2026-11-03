# PLAN_usage：v5 家長頁補「累計使用時間（分 YouTube／Netflix）」＋紀錄白話化

狀態：**APPROVED**（2026-11-01 01:44，Opus；TERRA high 挑戰 1 輪，裁定在檔尾；正文已改成裁定後版本）

## 起因（Owner 11-01 01:4x）
- 「V4 V5 的 HTML UI 不一樣，LOGGER 也不同，累計使用時間不見了，LOGGER 的時間單位從 MIN 改成 SEC？」
- 問「累計要做到哪」→ Owner 選 **「要分 YouTube／Netflix」**（知道要改電視 App、重裝、重驗鎖定）。
- 紀錄白話化（分鐘＋中文）Opus 直接排入，不另問。
- v4 的「排程」不補（v5 設計刻意拿掉，Owner 沒要求）。

## 現況
- 電視 `Quota.usage()` 只回「(YouTube 或 Netflix) 前景 ∩ 螢幕亮」合計秒；`consume()` 只記 `used_today_s`／`used_total_s` 合計；sync 只送合計。
- Worker `tv.report` 只存最後一次回報；沒有每日歷史。家長頁只顯示「今天用了 X 分」。
- 紀錄（events）直接印 `who kind detail`：`爸爸 grant 1800`、`電視 received 9`、`電視 lock`。

## 定義
- 分 App 數字＝**該 App 在前景且螢幕亮的實際秒數**（同 v4「前景時間」語意），**不拆扣額**；扣額度邏輯（`consume`、`used_today_s`）完全不動。enforce 下額度 0 幾秒內就鎖，兩者差距只在用完那幾秒。家長頁另外保留「今天扣了 X 分」那行。
- 已知誤差（接受，不修）：跨午夜那一跳整段算新的一天、電視離線跨日時前一天最後未送出的部分漏掉、重裝／回滾同日計數從 0 起（Worker 取 MAX 會少算）——都是統計誤差，平常每天 ≤1 分。
- 日期＝電視的台北日期（沿用 `consume()` 換日規則）。週＝週一起算（同 v4）；月＝當月 1 日起。

## WO-U1 電視 App（tvagent）
動到：`tvagent/src/com/example/tvagent/Quota.java`、`AgentService.java`、`tvagent/test/QuotaTest.java`、`tvagent/README.md`（log／回報欄位說明）。

1. `Quota.usage()`：把前景狀態從 boolean 改成「目前前景的受控套件」（null／YT／NF），另外回傳 `ytS`、`nfS`（各自毫秒累計後 /1000；不要求兩者相加等於 `seconds`）。原本的 `seconds`／`ytFg`／`scrOn` 計算與語意**不變**（現有 QuotaTest 全部照過）。RESUMED(1)：受控→設成該套件，非受控→null；PAUSED(2)：受控套件就清成 null（同現行）；15/16/26/27 同現行。
   - 起始狀態：新增保存 `fg_pkg`（"" ／YT／NF）。`usage()` 新增參數 `fgPkg`；`fg=true` 但 `fgPkg` 空（升級後第一跳）→ 該段**不算給任何 App**（只影響分 App 統計，`seconds` 照算）。
2. 新欄位 `usedTodayYt`、`usedTodayNf`：每跳加 `ytS`、`nfS`（`clock=jump` 那條路徑：把同一個 jump 秒數加給 `status()` 探到的前景套件）。換日判斷沿用 `consume()` 的 `today`：在 `consume()` 換日歸 0 `usedToday` 的同一處一起歸 0 兩個新欄位。**`consume()` 的扣額行為不改。**
3. `Status` 新增 `fgPkg`（從 `mResumedActivity` 那行判斷是 YT 還是 NF）；heartbeat 裡現在設定 `q.ytFg` 的地方同時設 `q.fgPkg`。
4. **相容（必要）**：`Quota.load()` 對三個新鍵 `used_today_yt_s`、`used_today_nf_s`、`fg_pkg` **寬鬆讀取：缺少或格式錯都用預設值（0、0、""），絕不丟例外、絕不影響其他欄位**。現有欄位的嚴格驗證不變。
5. sync 請求加：`today`（yyyy-MM-dd）、`used_today_yt_s`、`used_today_nf_s`；`app_ver` 改 3。
6. heartbeat log 行加 `yt_s=<秒> nf_s=<秒>`（當日累計），放在 `used_today=` 後面。

完成標準：
- `sh tvagent/test.sh` 全 PASS（現有測試一條都不能改弱），且 QuotaTest 新增並通過：①YT 60 秒＋NF 30 秒 → ytS=60、nfS=30、seconds=90；②YT→NF 同時間戳切換（沿用現有 batch 情境）分到正確 App；③`fg=true`＋`fgPkg` 空 → ytS=nfS=0、seconds 照舊；④換日 `usedToday`／兩個新欄位都歸 0；⑤**舊版 state（沒有三個新鍵）load 後 `balance`、`lastSeq`、`enforce` 保留**；⑥新鍵格式錯（如 `used_today_yt_s=abc`）→ 其他欄位保留、新鍵為預設值；⑦save→load 來回三個新鍵保留。
- `sh tvagent/build.sh` 成功產出 `tvagent/tvagent.apk`。
- `git diff --stat` 只動上列 4 檔（＋apk）。
禁止：不改 `AdbClient.java`、`Whitelist.java`、`NoticeActivity.java`、Manifest、簽章金鑰、`testdata/`；不改鎖定／提示／鬧鐘邏輯；不改 sync 回應的解析。

## WO-U2 Worker＋家長頁（cfquota）
動到：`cfquota/schema.sql`、`cfquota/lib.mjs`、`cfquota/lib.test.mjs`、`cfquota/README.md`。

1. schema 新增：`CREATE TABLE IF NOT EXISTS usage_daily(date TEXT NOT NULL, app TEXT NOT NULL CHECK(app IN ('youtube','netflix')), seconds INTEGER NOT NULL CHECK(seconds>=0), PRIMARY KEY(date,app));`（不清舊資料，一年約 730 列）。
2. `validReport`：`today`／`used_today_yt_s`／`used_today_nf_s` **可選**（app_ver 2 的舊回報照樣有效）；有出現時三個要一起出現，`today` 符合 `^\d{4}-\d{2}-\d{2}$`，兩個秒數是整數且 ≤86400。
3. `sync`：既有 batch **不動**；batch 成功後，有三欄時**另外**執行 upsert 兩列：`INSERT INTO usage_daily VALUES(?,?,?) ON CONFLICT(date,app) DO UPDATE SET seconds=MAX(seconds,excluded.seconds)`（重送／亂序不倒退），包在 try/catch：**失敗只 `console.error`，sync 照樣回 200 與 grants**（統計寫入絕不能擋加值與 enforce）。
4. `state` 回應加 `usage`：`{today:{youtube_s,netflix_s}, week:{…}, month:{…}, days:[{date,youtube_s,netflix_s}]}`；`days`＝本月 1 日到今天有資料的日子（新到舊）；「今天」用台北時間（Worker 用 `now`＋8 小時算日期，不靠電視）。
5. 家長頁：
   - 新增「使用時間」區塊：今天／本週／本月三列，每列「YouTube N 分、Netflix N 分、合計 N 分」；下面列本月每天（日期＋兩個分鐘數）。秒→分一律 `Math.floor(s/60)`。
   - 紀錄白話化，對照表：`grant`→`加 N 分`（detail 秒÷60）、`zero`→`歸零`、`received`→`電視收到加值`、`lock`→`已鎖 YouTube／Netflix`、`unlock`→`已開放 YouTube／Netflix`、`online`→`恢復連線（detail）`、`gap`→`回報中斷 N 分`（detail 秒÷60）、`adb-bad`→`無法控制（detail）`、`adb-ok`→`恢復控制`、`reinstall`→`電視 App 重新安裝`、`db-reset`→`資料庫重建`；未知 kind 照原樣印。時間格式沿用現行。
完成標準：
- `cd cfquota && node --test` 全 PASS，新增測試：①舊回報（無新欄位）sync 照舊 200；①b `usage_daily` 表不存在（測試裡 DROP）時新回報 sync 仍 200 且 grants 正常；②新回報 upsert，兩次 sync 秒數較小者不覆蓋較大者；③只給部分新欄位 → 400；④`/tv/api/state` 的 `usage` today/week/month 加總正確（造跨週、跨月資料）；⑤紀錄對照函式把 `grant 1800` 轉成 `加 30 分`。
- `git diff --stat` 只動上列 4 檔。
禁止：不改 Access 驗證、grant／歸零邏輯、路由、`wrangler.toml`、`worker.mjs`；不部署（部署由 Opus）。

## 上線順序（Opus，不委派）
1. 兩張都 review PASS＋Opus 審定 → merge main、push。
2. `wrangler d1 execute tvquota --remote --file schema.sql`（只會新增表）→ `wrangler deploy` → 家長頁正常、電視（舊 app_ver 2）sync 仍 ok。
3. 安裝前：確認沒人在看（fg 不是 true）、家長頁沒有待接收、記下 bal 與 `cat files/state.properties`。電視覆蓋安裝新 apk（`adb install -r`）→ 照 README 檢查 `APP_AUTO_START`／`APP_ASSOC_START` allow、bucket、deviceidle、TGuard 名單 → agent.log 新行有 `yt_s=`、`sync=ok`、**bal 與安裝前相同（安裝期間不在前景，不會扣）**、yt 狀態與安裝前相同。
4. 回歸（縮短版 C1b）：+10 分，YouTube 2 分→Netflix 看到用完：warn、end、兩個停用；家長頁「今天」YouTube、Netflix 相對安裝前的**增量**約 2、8 分（±1）。再做一次 `adb reboot`（額度 0）→ 開機後仍停用、agent 回報。
5. 回滾：apk 用前一版 `git show <前一 commit>:tvagent/tvagent.apk` 覆蓋安裝（state 新鍵舊版會忽略？**舊版 `required()` 遇到多的鍵沒問題，只怕少鍵**）；Worker `wrangler rollback`。

## 不做
- v4 的排程、Mac 離家說明、資料起點／最後抓取顯示。
- 圖表、週／月切換按鈕（三列直接列出）。
- 回補 11/1 以前的分 App 歷史（沒有資料；今天合計留在 `used_today_s`）。

## 挑戰裁定（TERRA high，2026-11-01 01:40；原文 `research/wo/PLAN_usage_challenge_TERRA.md`）
| # | 裁定 | 理由／改法 |
|---|---|---|
| 1 升級當天合計對不起來 | 部分採納 | 分 App 改成獨立的「前景秒數」，不再宣稱等於扣額合計；驗收改比增量 |
| 2 回滾再升級遺失／高估 | 駁回 | 只影響統計，回滾本身罕見；列入已知誤差 |
| 3 新欄位壞掉整份重置 | **採納** | 三個新鍵寬鬆讀取，壞了只用預設值；加測試⑥ |
| 4 統計寫入失敗擋 sync | **採納** | upsert 移出既有 batch、try/catch，失敗照回 200；加測試①b |
| 5 離線跨日漏前一天 | 駁回 | 待機網路仍在、每分鐘同步，漏的通常 <1 分；列入已知誤差 |
| 6 跨午夜整段算隔天 | 駁回 | 同上，誤差 ≤1 跳；列入已知誤差 |
| 7 比例拆帳歸錯 App | 採納（換做法） | 改記實際前景秒數，不拆扣額，問題消失 |
| 8 秒數／分鐘捨入加總誤差 | 部分採納 | 不再要求分項＝合計；分鐘一律 floor，頁面合計用兩秒數相加後 floor |
| 9 升級第一跳猜 YouTube | **採納** | 起始套件不明的那段不算給任何 App |
| 10 MAX 遇同日重置少算 | 駁回 | 重裝罕見、只影響統計；列入已知誤差 |
| 11 上線驗收不足 | 採納 | 安裝前確認沒人看、無待接收；加 reboot 回歸與 yt 狀態核對 |
