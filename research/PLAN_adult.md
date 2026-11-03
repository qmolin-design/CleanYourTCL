# PLAN_adult：爸媽時段（排程內不鎖、不扣時間）

公開副本已移除實際家庭時段；本文件及下列測試時間均為虛構回歸範例。

狀態：**APPROVED**（2026-11-01 03:30，Opus；TERRA high 挑戰 1 輪，裁定在檔尾；正文是裁定後版本。Owner 授權全程自主）

## 起因（Owner）
- 11-01 02:4x：小孩睡了／出門時大人看電視不限時、不扣額度。02:5x：要排程（起床前、上學、睡後）。
- 11-01 03:0x 答：
  1. 爸媽時間 **00:00–06:00、10:00–16:00、22:00–24:00**，這段時間不鎖、不扣時間。
  2. 國家假期大人也在家 → 用手機**關閉爸媽模式**。
- 11-01 03:1x 答：**週末白天不算**（10:00–16:00 只有週一～五；00:00–06:00、22:00–24:00 每天）。
- 11-01 03:1x 授權：**全程自主，做完再報**（挑戰裁定後 Opus 直接 APPROVED、派工、部署、實機回歸；需要 Owner 動手才停）。

## 做法（一句話）
Worker 用自己的時鐘算「現在是否爸媽時段、還剩幾秒」，sync 回應多一欄 `adult_left_s`；電視用 `elapsedRealtime` 倒數，**每次 sync 最多續 10 分鐘**；倒數期間不扣額度、照常開放 YouTube／Netflix；到期回到一般規則（有額度照扣，沒額度 → 剩 5 分提示 → 時間已到 → 停用）。

## 定義
- **排程**：D1 `meta` 的 `adult_sched`（JSON），初值：
  `[{"days":"0123456","from":"00:00","to":"06:00"},{"days":"12345","from":"10:00","to":"16:00"},{"days":"0123456","from":"22:00","to":"24:00"}]`
  （`days`＝台北時間星期幾，0＝週日；`to` 可為 `24:00`）。之後改時間由 Opus 用 wrangler 改這一列，**本期不做頁面編輯**。
- **時段合併**：相鄰或重疊的時段合併成一段（22:00–24:00 接 00:00–06:00＝22:00 到隔天 06:00）。展開範圍：**前 1 天到後 3 天**（足以算出目前段與下一段的完整起訖）。
- **關閉（假日用）**：家長頁按鈕 → `meta.adult_off_until`＝「現在所在的那一段」的結束時間；不在時段內就是「下一段」的結束時間。`now < adult_off_until` 期間，結束時間 ≤ `adult_off_until` 的時段都不算。到期自動恢復。另有「取消關閉」（刪掉這一列）。兩個動作都是**依狀態冪等**（重送結果相同），不用 req_id；事件只在值真的改變時寫（同一 batch，`changes()>0`）。
- `adult_left_s`＝現在所在（合併後、未被關閉）時段的剩餘秒數；不在時段內＝0。上限 86400。
- **電視端倒數**：sync 成功、解析完回應**之後**取一次 `el2 = elapsedRealtime()`，`adultEnd = el2 + min(adult_left_s, 600)*1000`（每次成功 sync 都覆寫，含變 0）。
  - 續期上限 10 分鐘：斷網（含小孩拔網路）最多多拿 10 分鐘免費；長時間斷網＝回一般規則（安全側）。正常連線每分鐘 sync 一次，倒數不會中斷；到真正結束前剩 ≤600 秒時就是精確值。
  - 失效：`boot < 0`、`boot != 存檔 bootCount`、或 `el < 存檔 qEl` → `adultEnd = 0`（等下一次 sync，開機約 80 秒內）。
- **有效額度**＝`balance + adultLeft`（adultLeft＝`max(0, adultEnd − el)/1000`）。`decide()`、`warned` 重置、`nextAlarmMs()` 一律用有效額度 → 時段內停用的會被開放、時段結束前 5 分且沒額度才提示、結束時沒額度才鎖。
- **扣額（依實際觀看事件切分）**：本跳結算一律用**本跳開始前已知的** `adultEnd`（先結算、後 sync 覆寫）。換成牆鐘：`freeToWall = wall − (el − adultEnd)`（adultEnd 失效時＝0）。`Quota.usage()` 新增參數 `freeToWall`：每一段前景區間 `[a, b]` 只算 `max(0, b − max(a, freeToWall))`，`seconds`、`ytS`、`nfS` 都照這規則（所以分 App 也正確）。`clock=jump` 路徑：`seconds = 前景 ? max(0, el − max(qEl, adultEnd))/1000 : 0`，分 App 給探到的前景套件。
- **使用統計**：只算扣額的部分（＝小孩時間）；爸媽時段看的不進 usage_daily。
- 已知誤差（接受）：時段開始時，到電視下一次 sync 前（≤60 秒）照扣；家長按關閉後，到電視下一次 sync 前（≤60 秒）仍免費；Worker 與電視之間只差一次 sync 往返。

## WO-A1 Worker＋家長頁（cfquota）
動到：`cfquota/lib.mjs`、`cfquota/lib.test.mjs`、`cfquota/schema.sql`、`cfquota/README.md`。
1. `schema.sql`：`INSERT OR IGNORE INTO meta VALUES('adult_sched', '<上面初值>')`（不另開表）。
2. 純函式 `adultWindow(nowS, sched, offUntil)` → `{active, until, next_from, next_until}`（全部 Unix 秒；台北＝UTC+8 固定，無日光節約）：展開「前 1 天到後 3 天」→ 合併 → 套用關閉 → 找包含 now 的那段與下一段。`sched` 解析失敗或格式錯 → 當作沒有排程（不丟例外，sync 照常）。
3. `sync` 回應加 `adult_left_s`（`active ? until − now : 0`，上限 86400）。adult 計算失敗只 `console.error`，回 0，sync 照常。
4. `state` 回應加 `adult: {active, until, next_from, next_until, off_until}`。
5. 新 API `POST /tv/api/adult`（只限 parent、同 grant 的 Origin 檢查）：body `{action:'off'|'resume'}`。`off`＝依定義寫 `adult_off_until`（沒有目前段也沒有下一段 → 400）；`resume`＝刪掉。事件 `adult-off`（detail＝off_until 秒）、`adult-resume`，只在值改變時寫。回應新的 `adult` 物件。
6. `eventText`：`adult-off`→`關閉爸媽時段到 M/D（週X）HH:MM`、`adult-resume`→`恢復爸媽時段`（台北時間）。
7. 家長頁（沿用現有樣式，不改版；改版另走 WO-U4）：狀態下方一行，**時間一律帶日期與星期**（例 `11/3（一）10:00–16:00`）：
   - 時段中：「爸媽時段（不扣時間）：11/3（一）10:00–16:00」＋按鈕「關閉這一段（11/3（一）10:00–16:00 照常計時）」。
   - 不在時段：「下一段爸媽時段：11/3（一）22:00–11/4（二）06:00」＋按鈕「下一段不要（…照常計時）」。
   - 已關閉：「爸媽時段已關閉到 11/3（一）16:00」＋按鈕「取消關閉」。
   - 按鈕送出時變灰（同加值按鈕）。
完成標準：`cd cfquota && node --test` 全 PASS（現有測試不能改弱），新增：①以下時間皆為合成回歸案例：平日 11:00 → active、until＝16:00；②週六 10:00 → 不在時段、next＝當天 22:00、next_until＝週日 06:00；③週一 23:00 → until＝週二 06:00、next＝週二 10:00–16:00；④週一 05:00 → until 06:00、next 10:00；⑤週五 22:00 → until 週六 06:00、next＝週六 22:00–週日 06:00（週末白天沒有）；⑥11:00 關閉 → active=false、off_until＝16:00，22:00 恢復；⑦08:00 按「下一段不要」→ 10:00–16:00 不算、22:00 照常；⑧off 連送兩次 → 事件只 1 筆；resume 後 off_until 消失；⑨`adult_sched` 壞掉 → sync 200、`adult_left_s=0`；⑩`/tv/api/adult` 非 parent 403、錯 Origin 403、錯 action 400；⑪sync 回應有 `adult_left_s` 且平日 11:00＝18000。
禁止：不改 Access 驗證、grant／歸零、usage 寫入、路由前綴、`wrangler.toml`、`worker.mjs`；不部署。

## WO-A2 電視 App（tvagent）
動到：`Quota.java`、`AgentService.java`、`tvagent/test/QuotaTest.java`、`tvagent/README.md`（＋重建 apk）。
1. `Quota` 新欄位 `adultEnd`（ms，elapsedRealtime 基準），存檔鍵 `adult_end_el`，**寬鬆讀取**（缺／錯→0）。新方法 `adultLeft(long el)`。`decide()`、`nextAlarmMs()` 新增帶 adultLeft 的多載，用有效額度；舊簽名保留＝adultLeft 0，行為不變。
2. `Quota.usage()` 新增 `freeToWall` 參數的多載（舊簽名＝0，現有測試照過）。heartbeat：先依「失效」規則清 `adultEnd`，再依「扣額」規則結算（含 clock=jump 路徑），然後 `consume`。
3. sync 回應解析：`adult_left_s` 用 `optLong(…, 0)`，不在 0～86400 → 當 0（**不得讓整次 sync 失敗**；舊 Worker 沒這欄＝0）。成功後依「電視端倒數」設 `adultEnd`。sync 後重跑 `act()` 的條件：**保留原本「balance 或 enforce 變了」**，再加「有效額度 >0 有變」或「有效額度 ≤300 有變」。
4. log 行加 `adult=<剩餘秒>`（放在 `bal=` 後）；`app_ver` 改 4（Manifest versionCode 不動，回滾可直接 install -r）。
完成標準：`sh tvagent/test.sh` 全 PASS（現有測試一條都不能改弱），QuotaTest 新增：①balance=0、adultLeft=600、YouTube 停用 → UNLOCK；②balance=0、adultLeft=290、前景 → WARN 一次；③balance=0、adultLeft=0、前景 → END；④balance=1000、adultLeft=600 → 不 WARN；⑤前景區間全在 freeToWall 之前 → seconds=ytS=nfS=0；⑥前 30 秒在時段內但沒看、後 30 秒看 YouTube → seconds=ytS=30（TERRA #1 例）；⑦時段內看 YouTube 30 秒、時段後看 Netflix 30 秒 → ytS=0、nfS=30、seconds=30；⑧`nextAlarmMs` 用有效額度（balance=0、adultLeft=50、前景 → 50 秒）；⑨舊 state（無 `adult_end_el`）load → 其他欄位保留、adultEnd=0；⑩`adult_end_el=abc` → 同上；⑪失效判斷（若抽成 Quota 靜態方法）：boot 不同、boot<0、el<qEl 都失效。`sh tvagent/build.sh` 成功。
禁止：不改 `AdbClient.java`、`Whitelist.java`、`NoticeActivity.java`、Manifest、簽章、`testdata/`；不改鎖定／解鎖的 adb 指令與驗證。

## 上線順序（Opus）
1. 兩張 review PASS＋Opus 審定 → merge main、push。
2. D1 `INSERT OR IGNORE` adult_sched → `wrangler deploy`（舊電視 app_ver 3 忽略多的欄位，照常 sync）→ 家長頁 state 有 `adult`。
3. 電視 `install -r`（安裝前確認沒人看、`pending` 空、記 bal／last_seq）→ 照 README 檢查 appops／bucket／deviceidle／TGuard 名單 → log 有 `adult=`、bal／last_seq 不變。
4. 回歸（深夜、沒人看、確認期間 events 沒有家長加值；先記 bal／last_seq）：用 wrangler 把 `adult_sched` 暫改成「現在 +8 分鐘結束」的測試排程，`set 0`（by Opus測試）：時段內 YouTube 開放且 bal 不動 → 剩 5 分內提示 → 到點時間已到、停用；再開一段測試時段 → 呼叫 off（D1 直接寫 `adult_off_until`）→ 一分鐘內停用；時段內 `adb reboot`（bal=0）→ 開機先停用、sync 後開放。測完還原排程、`set` 回原 bal（若期間有家長操作，改依 events 對帳再決定）。
5. 回滾：apk `git show <前一 commit>:tvagent/tvagent.apk` → `adb install -r`（versionCode 相同；舊版忽略 `adult_end_el`）；Worker `wrangler rollback <前一版>`；D1 多的 meta 列無害。

## 不做（要再說）
- 手動「現在開始爸媽時段」（Owner 這次只要排程＋關閉）。
- 頁面上改排程時間（改由 Opus 用 wrangler 改）。
- 國定假日日曆自動判斷（Owner 選手動關閉）。
- 太太加進 Access 名單（Owner 11-01 說管理介面先不考慮太太）。
- 時段開始／結束推播。

## 挑戰裁定（TERRA high，2026-11-01 03:2x；原文 `research/wo/PLAN_adult_challenge_TERRA.md`）
| # | 裁定 | 理由／改法 |
|---|---|---|
| 1 重疊扣法混淆經過與觀看 | **採納** | 改成 `usage()` 依事件切分，`freeToWall` 之前的前景不算；加測試⑥ |
| 2 關閉後拔網路保留整段 | **採納（換做法）** | 每次 sync 最多續 10 分鐘；斷網最多多拿 10 分 |
| 3 sync 前後結算順序 | 部分採納 | 明定先用舊 adultEnd 結算再覆寫；邊界誤差 ≤1 次 sync，列已知誤差 |
| 4 boot_count 失效 | **採納** | boot<0 或 el<qEl 也失效 |
| 5 重跑 act 條件改掉原行為 | **採納** | 保留原條件，再加有效額度門檻變化 |
| 6 分 App 歸屬錯 | **採納** | 同 #1 一起解；加測試⑦ |
| 7 關閉請求去重 | 部分採納 | 拿掉 req_id，改依狀態冪等，值變才記事件；加測試⑧ |
| 8 展開天數不足 | **採納** | 前 1 天到後 3 天；加測試⑤ |
| 9 假日操作對象不明 | **採納** | 頁面一律顯示日期＋星期＋完整起訖，按鈕寫明哪一段 |
| 10 實機回歸對帳／降版 | 部分採納 | 記 bal／last_seq、確認期間無家長操作才 set 回；versionCode 相同，install -r 不是降版（駁回這半） |
| 11 el 採樣時間 | **採納** | 解析回應後重新取 elapsedRealtime；jump 路徑明定 |
