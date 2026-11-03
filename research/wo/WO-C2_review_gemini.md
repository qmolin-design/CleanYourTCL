PASS

### 審查項目逐項對照結果

- **A. 每跳流程 1～7 順序**：**符合**。
  - 第 1 步在 `AgentService.java:47` 的 `onStartCommand` 進入時即先以 `scheduleNextAlarm(this, 60000)` 排定 60 秒鬧鐘，確保即使該跳執行中斷亦能醒來。
  - 第 2～5 步依序查 UsageStats、一次 adb shell 查現況（`status()`）、`q.consume()`、`act(q, s)`。
  - 第 6 步在 `AgentService.java:130` 先 `save(q)`（確保 adb 與扣額在連網前落盤），再發起 HTTPS `sync()`；同步成功套用加值後重跑 `act(q, s)` 並再 `save(q)`。
  - 第 7 步在 `AgentService.java:132` 依 `q.nextAlarmMs()` 改排鬧鐘。
- **B. Quota 邏輯與 QuotaTest**：**符合**。
  - `Quota.usage()` 嚴格依事件時間排序、取 `[from, to]` 區間、以初始狀態起算 YouTube 前景與螢幕亮交集、處理各事件代碼（1, 2, 23, 15, 16, 26, 27），回傳秒數與結束狀態。
  - `clockJump`、`consume`（台北時區跨日歸零、不扣至負數）、`applySync`（`db_id` 改變或原為 null 時將 `last_seq` 設為 `max_seq` 並儲存新 ID；CAP 調小不砍現有額度、超過封頂記 `capped=true`；`last_seq` 僅記錄實際套用之最後一筆 seq）、`decide` 表格與 `warned` 重置條件、`nextAlarmMs` 換算均與規格完全一致。
  - `QuotaTest.java` 完整實作清單 1～7 的斷言測試，非空測。
- **C. enforce 影子模式與 END 流程**：**符合**。
  - `AgentService.java:95` 在 `!q.enforce` 時直接回傳 `"dry-" + name`，不調用 adb 碰 YouTube 亦不開啟 Activity。
  - adb 失敗時 `s.yt` 設為 `"unknown"`，`decide()` 回傳 `NONE`，不動作。
  - `END` 流程（`AgentService.java:97-101`）先呼叫 `am start NoticeActivity --es kind end`，在同一條 executor 背景執行緒 `Thread.sleep(10000)` 等待 10 秒，再執行 `am force-stop` 與 `pm disable-user --user 0`，最後透過 `pm list packages -d` 讀回驗證。
- **D. 狀態檔原子寫入、設定來源與 Intent Extra**：**符合**。
  - `save()` 先寫 `state.properties.tmp`，經 `out.getFD().sync()` 後 `renameTo()` 覆蓋目標檔；`load()` 遇壞檔時捕獲例外回退至 `new Quota()`（額度 0、enforce 為 false，偏向鎖）。
  - 同步設定僅由 `files/config.properties` 讀取。
  - Intent extra 全專案僅讀取 `reason` 與 `kind`。
- **E. 安全性**：**符合**。
  - 套件控制僅鎖定 `com.google.android.youtube.tv`，Activity 僅啟動自身的 `.NoticeActivity`。
  - `agent.log` 絕不包含憑證、密鑰或私鑰；過 1MB 時保留後半 512KB。
  - 同步請求攜帶 `CF-Access-Client-Id` 與 `CF-Access-Client-Secret`，逾時均設為 10000 ms，非 200 回應記為 `sync=http:<碼>`。
- **F. 程序啟動 5 條 adb 設定與 TGuard 名單**：**符合**。
  - `AgentService.java:71-75` 於每個程序啟動時下達 `APP_AUTO_START`、`APP_ASSOC_START`、`GET_USAGE_STATS`、`standby bucket active` 與 `deviceidle whitelist`，驗證全為 1 才標記 `configured`，失敗時下跳重試。
  - 沿用 WO-G2 之 `CMD_WHITELIST` 維護 TGuard 9 套件白名單。
- **G. Manifest**：**符合**。
  - `NoticeActivity` 為 `exported="true"` 且無 `intent-filter`，具全螢幕主題與 `excludeFromRecents="true"`。
  - 新增 `KickReceiver`，移除 `TestReceiver`。
  - 無任何 `LAUNCHER`／`LEANBACK_LAUNCHER`，無 `testOnly`，無裝置管理員權限。

---

### 問題清單

1. **嚴重度：建議**
   - **檔案:行號**：[AgentService.java:46](file:///Users/example-owner/TV-wt-c2/tvagent/src/com/example/tvagent/AgentService.java#L46)
   - **規格依據**：`PLAN_v5.md`「WO-C2 電視 App」與「額度規則」
   - **說明**：`onStartCommand` 中執行了 `if (intent != null) intent.getStringExtra("reason");`，其回傳值被直接丟棄未使用。
     - **功能影響評估**：**不影響功能**。比對規格，PLAN v5 已將 v4 的 `start reason=...` log 欄位移除，且無論啟動來源為 `boot`、`kick` 還是鬧鐘，服務行為均一致為排定鬧鐘並推進心跳；此處呼叫係為配合驗收檢查「只准讀 reason 與 kind」，丟棄該值對系統行為無負面影響。

2. **嚴重度：建議**
   - **檔案:行號**：[AgentService.java:130](file:///Users/example-owner/TV-wt-c2/tvagent/src/com/example/tvagent/AgentService.java#L130)
   - **規格依據**：`PLAN_v5.md`「每跳流程」第 6 步「同步（設定檔存在才做）→ 套用加值與下發值（見介面）→ 有變動就重跑第 5 步、再存檔。」
   - **說明**：當同步成功（`sync.equals("ok")`）時，程式直接串接 `action += "," + act(q, s); save(q);`，即使該次同步回傳的 `grants` 為空且 `enforce`、`cap` 均無變動，仍會再次執行 `act(q, s)`。雖然在無變動時 `decide()` 會判定為 `NONE`（不會重複執行 adb 指令或跳通知），但會使 log 中的 `act` 欄位記錄為 `none,none` 或 `dry-none,dry-none`。若能判斷是否有實際額度或狀態變更再重跑第 5 步，log 輸出將更為精確簡潔。
