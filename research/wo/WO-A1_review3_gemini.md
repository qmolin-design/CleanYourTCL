PASS

### 任務 1：工單要做 1～7 逐項檢驗

| 項目 | 結果 | 檔案與行號 | 說明 |
|---|---|---|---|
| **1. AlarmManager 驅動精確鬧鐘** | 做到 | [`AgentService.java#L82-L92`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L82-L92)<br>[`AlarmReceiver.java#L13-L19`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AlarmReceiver.java#L13-L19)<br>[`AndroidManifest.xml#L20`](file:///Users/example-owner/TV-wt-tvagent/tvagent/AndroidManifest.xml#L20) | 每 60 秒以 `setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, ...)` 排定精確鬧鐘；未用 `setRepeating`；送明確 Intent 廣播給 `AlarmReceiver`（`exported="false"`）。每次觸發在 [`AgentService.java#L140`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L140) 重新排下一次。 |
| **2. AlarmReceiver 叫起服務與移除週期任務** | 做到 | [`AlarmReceiver.java#L15-L19`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AlarmReceiver.java#L15-L19)<br>[`AgentService.java#L73`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L73)<br>[`AgentService.java#L131-L141`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L131-L141) | `AlarmReceiver` 以 `startForegroundService` 送 action `HEARTBEAT`；`onStartCommand` 收到後排入單次心跳並排下一次鬧鐘；`ScheduledExecutorService` 週期任務（`scheduleAtFixedRate`）已完全移除，改為單次工作佇列 `Executors.newSingleThreadExecutor()`。 |
| **3. 服務啟動確保鬧鐘與 PendingIntent 旗標** | 做到 | [`AgentService.java#L46`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L46)<br>[`AgentService.java#L88-L89`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L88-L89)<br>[`AgentService.java#L140`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L140)<br>[`AgentService.java#L157`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L157) | 包含 boot、test、other 及 null intent，都在 `onStartCommand` 呼叫 [`scheduleNextAlarm`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L82)；使用固定 `ALARM_REQ = 1` 搭配 `FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`，確保同一時間系統內只有一個待發鬧鐘。 |
| **4. 心跳紀錄行欄位 `src=alarm|start`** | 做到 | [`AgentService.java#L136`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L136)<br>[`AgentService.java#L163`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L163)<br>[`AgentService.java#L204`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L204)<br>[`AgentService.java#L222`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L222) | 在 `pid` 後面加入 `src=` 欄位，輸出為 `uptime=<秒> pid=<pid> src=<alarm|start> adb=<status>`，其餘時間格式與欄位完全不變。 |
| **5. 30 秒去重邏輯** | 做到 | [`AgentService.java#L45`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L45)<br>[`AgentService.java#L64`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L64)<br>[`AgentService.java#L207-L211`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L207-L211) | `heartbeat()` 內使用 `SystemClock.elapsedRealtime()` 比對 `lastHeartbeatElapsed`，若間隔 `< DEDUP_MS`（30 秒）則直接 return 略過。 |
| **6. SELFTEST 流程與 README 說明** | 做到 | [`AgentService.java#L165-L172`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L165-L172)<br>[`AgentService.java#L225-L290`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L225-L290)<br>[`README.md#L42-L62`](file:///Users/example-owner/TV-wt-tvagent/tvagent/README.md#L42-L62) | `runSelftest()` 測試步驟（步驟 0～4、復原 `tryEnable`）保持原樣；README 補入「TCL 凍結機制與鬧鐘心跳」完整機制說明章節。 |
| **7. Manifest 配置與權限** | 做到 | [`AndroidManifest.xml#L5-L7`](file:///Users/example-owner/TV-wt-tvagent/tvagent/AndroidManifest.xml#L5-L7)<br>[`AndroidManifest.xml#L20`](file:///Users/example-owner/TV-wt-tvagent/tvagent/AndroidManifest.xml#L20) | `AlarmReceiver` 明確宣告 `android:exported="false"`；權限維持原 3 個（`INTERNET`、`RECEIVE_BOOT_COMPLETED`、`FOREGROUND_SERVICE`），未新增任何權限。 |

---

### 任務 2：Android 11（API 30、targetSdk 30）實機風險審核

1. **從 AlarmReceiver（背景）呼叫 startForegroundService 是否允許？**
   - **允許**。在 Android 8.0～11（API 26～30），App 收到 AlarmManager 的喚醒廣播時，系統會暫時將該 App 放進背景啟動白名單（whitelisted to start services）。因此廣播接收器在 `onReceive` 呼叫 `startForegroundService` 是官方標準做法（Android 12 / API 31+ 才有更嚴格的背景啟動限制，但本專案 targetSdk=30、設備為 Android 11）。
2. **服務若是新建，是否在 5 秒內 startForeground？**
   - **是，保證在 5 秒內完成**。[`AgentService.java#L69`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L69) 在 `onCreate()` 主執行緒第一行即呼叫 `startAsForeground()`，並在 [`onStartCommand#L125`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L125) 再次保險呼叫。該方法僅建立 NotificationChannel 與發送 Notification，毫秒級完成，未夾帶任何網路或 adb 阻塞操作。
3. **鬧鐘鏈會不會斷？**
   - **某次心跳例外**：不會斷。下一次鬧鐘是在 [`onStartCommand#L140`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L140) 於主執行緒直接排定，並非在背景心跳執行完才排；且 [`heartbeat()#L213-L220`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L213-L220) 有完整 `catch (Exception e)`，例外不會衝垮服務或阻止鬧鐘排程。
   - **服務被殺（OOM / 系統清理）**：不會斷。已排進系統 `AlarmManager` 的鬧鐘獨立於 App 程序存在。時間到時系統喚醒靜態註冊的 `AlarmReceiver`，接收器再次呼叫 `startForegroundService` 重建服務並排下一次鬧鐘。
   - **onStartCommand 收到 null intent**：不會斷。服務以 `START_STICKY` 回傳，若系統重啟服務送入 null intent，跳過 `HEARTBEAT_ACTION` 分支後，在 [`AgentService.java#L157`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L157) 仍會執行 `scheduleNextAlarm(this)`，重新接回鬧鐘鏈。
   - **重開機後**：不會斷。開機後 `BootReceiver` 收到 `BOOT_COMPLETED` 啟動服務，`AgentService` 在 [`onStartCommand#L157`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L157) 排定第一次鬧鐘，後續循環成立。
4. **PendingIntent flags 是否正確？**
   - **正確**。採用 `PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE`。API 30 支援 `FLAG_IMMUTABLE`（API 23 引進），且固定 `ALARM_REQ = 1` 與明確 Intent 能確保覆蓋同一個 PendingIntent，同一時間只有一個待發鬧鐘。
5. **setExactAndAllowWhileIdle 用法是否正確？**
   - **正確**。時間基準使用 `AlarmManager.ELAPSED_REALTIME_WAKEUP`，對應時間計算使用 `SystemClock.elapsedRealtime() + HEARTBEAT_MS`，兩者單位與時間軸完全吻合。在 targetSdk 30 下呼叫此 API 不需要額外的 `SCHEDULE_EXACT_ALARM` 權限。

---

### 任務 3：30 秒去重邏輯與 SELFTEST 序列化審核

1. **30 秒去重邏輯會不會把正常的鬧鐘心跳也略過或造成永遠不寫？**
   - **不會略過正常鬧鐘**：鬧鐘週期為 60 秒（`HEARTBEAT_MS = 60000`），大於 30 秒閾值（`DEDUP_MS = 30000`）。
   - **不會造成永遠不寫**：當觸發去重 return 時，[`AgentService.java#L208-L210`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L208-L210) **不會** 更新 `lastHeartbeatElapsed`，上次時間戳維持原狀；下一個 60 秒鬧鐘到達時，時間差必大於 30 秒，必定寫入。
2. **SELFTEST 是否仍在同一單執行緒序列化？**
   - **是**。`exec` 由 [`Executors.newSingleThreadExecutor()`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L73) 建立，無論是鬧鐘心跳（`heartbeat("alarm")`）、啟動心跳（`heartbeat("start")`）還是自我測試（[`runSelftest()`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L170)），均透過 `exec.execute(...)` 依序排入同一個單執行緒佇列，保證 FIFO 依序執行、不並行。

---

### 任務 4：安全審查

1. **AlarmReceiver exported 狀態**：
   - [`AndroidManifest.xml#L20`](file:///Users/example-owner/TV-wt-tvagent/tvagent/AndroidManifest.xml#L20) 明確設定 `android:exported="false"`，外部 App 無法向其發送廣播。
2. **外部指令注入路徑**：
   - 無任何外部注入途徑。`AlarmReceiver` 不解析任何 Intent extra，僅發送固定 action；`AgentService` 只解析固定 reason（`boot`／`test`）與 selftest 布林值，所有 adb 指令（`echo ok`、`pm disable-user`、`pm list packages -d`、`pm enable`）皆為寫死的靜態字串常數。
3. **套件鎖定與 YouTube**：
   - 全文無任何 `youtube` 字串；目標套件嚴格鎖定 `com.example.tkey`。

---

### 問題清單

#### 非阻擋問題

1. [`tvagent/src/com/example/tvagent/AgentService.java#L64`](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AgentService.java#L64)
   - **問題**：`lastHeartbeatElapsed` 初值為 `0`，若電視開機在 30 秒內即送達 `BOOT_COMPLETED`，首次 `src=start` 心跳可能被略過。
   - **理由**：若開機速度極快（開機時間 `SystemClock.elapsedRealtime() < 30000`），第一次執行 `heartbeat("start")` 時，`now - 0 < 30000` 會成立而提早 return。但開機記錄 `start reason=boot` 仍會由主執行緒寫入，且 60 秒後的鬧鐘心跳 `src=alarm` 會正常寫下，不影響鬧鐘鏈與系統運作。未來若想追求開機第一秒必記心跳，可改判 `if (lastHeartbeatElapsed != 0 && now - lastHeartbeatElapsed < DEDUP_MS)`。
