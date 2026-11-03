PASS

### 審查發現清單

1. **Quota.java:69-87（扣額切分與舊簽名相容性）**
   - **說明**：
     - `Quota.usage()` 7 參數多載新增 `freeToWall`（牆鐘毫秒），在事件迴圈中段及尾段皆以 `d = e.wallMs - Math.max(cursor, freeToWall)` 與 `d = to - Math.max(cursor, freeToWall)` 計算前景色時間；凡落在 `freeToWall` 之前的時間皆被 `Math.max` 截斷（`d <= 0` 不計），僅計 `freeToWall` 之後的前景秒數。
     - `total`、`yt`、`nf` 累加皆使用完全相同的 `d` 增量，秒數除以 1000 後 `seconds`、`ytS`、`nfS` 扣額完全一致。
     - 舊簽名 `usage(events, from, to, fg, fgPkg, screen)` 轉調新方法並傳入 `freeToWall = 0`；因 `cursor >= from >= 0`，`Math.max(cursor, 0)` 恆等於 `cursor`，舊呼叫端行為完全不變。
   - **嚴重度**：符合規範（PASS）

2. **AgentService.java:121-135, 141-154, 187-188（heartbeat 執行順序與 jump 公式）**
   - **說明**：
     - **順序檢查**：
       1. 第 121 行先執行失效判斷清理：`if (Quota.adultInvalid(boot, q.bootCount, el, q.qEl)) q.adultEnd = 0;`（包含 `boot < 0`、`boot != savedBoot`、`el < savedQEl`）。
       2. 第 122、130 行以舊 `adultEnd` 結算本跳牆鐘：`freeToWall = wall - (el - q.adultEnd)` 並傳入 `usage()`。
       3. 第 133-135 行處理 `jump` 路徑：`seconds = jump ? (Boolean.TRUE.equals(s.fg) && s.screen.equals("on") ? Math.max(0, (el - Math.max(q.qEl, q.adultEnd)) / 1000) : 0) : usage.seconds;`，若 `seconds > 0` 則分 App 賦予探測到的 `s.fgPkg`（YT 或 NF），公式與 [PLAN_adult.md](file:///Users/example-owner/TV-wt-a2/research/PLAN_adult.md) 第 27 行定義完全相符。
       4. 第 138 行執行 `consume()`。
       5. 第 141 行執行 `act(q, s)`。
       6. 第 147 行執行 `sync()`，僅在 sync 成功且回應解析驗證無誤後（第 187-188 行），重新取 `el2 = SystemClock.elapsedRealtime()`，並覆寫 `q.adultEnd = el2 + Math.min(adultLeftS, 600) * 1000`。
       7. 第 151 行在 sync 成功後檢查狀態變化，符合條件才補跑 `act(q, s)` 並再次 `save(q)`。
   - **嚴重度**：符合規範（PASS）

3. **AgentService.java:184-187（sync 解析 adult_left_s 容錯）**
   - **說明**：
     - 讀取時使用 `data.optLong("adult_left_s", 0)`，舊 Worker 回應無此欄位時安全回傳 0。
     - 外層額外包覆 `try { ... } catch (RuntimeException ex) { adultLeftS = 0; }` 防禦極端格式異常；並以 `if (adultLeftS < 0 || adultLeftS > 86400) adultLeftS = 0;` 嚴格限制合法區間。
     - 遇到缺欄、型態不符或負數/超額數值時，一律安全歸零，絕不拋出例外中斷 `sync()`。
   - **嚴重度**：符合規範（PASS）

4. **Quota.java:114-122, 130-136 及 AgentService.java:146, 151-152（有效額度判斷與 act 觸發條件）**
   - **說明**：
     - `decide()` 與 `nextAlarmMs()` 皆改用 `eff = balance + Math.max(0, adultLeft)` 作為判斷基礎；大於 300 重置警告、大於 0 解鎖、等於 0 鎖定/結束、小於等於 300 提示一次等邏輯完整保持，舊簽名保留且預設 `adultLeft = 0`。
     - `heartbeat` 在 sync 後重跑 `act` 之條件為：
       `q.balance != balBefore || q.enforce != enforceBefore || (effBefore > 0) != (effAfter > 0) || (effBefore <= 300) != (effAfter <= 300)`
       完整保留原有的 `balance` 與 `enforce` 變更判定，並新增有效額度翻轉（大於 0 開放/停用切換、進入或脫離 5 分鐘提示區）之觸發。
   - **嚴重度**：符合規範（PASS）

5. **AgentService.java:95-116 及 Quota.java:38, 47-50（鎖定路徑與舊 state 相容性）**
   - **說明**：
     - `act()` 中的所有 adb 執行字串（`NoticeActivity` 提示、`am force-stop`、`DISABLE`、`LIST`、`ENABLE`）與 package 驗證機制完全未被修改；例外補捉與中斷處理保持原樣。
     - `Quota.load()` 透過 `optNumber(p, "adult_end_el")` 寬鬆載入；舊版 state 缺此鍵或格式損毀時安全預設為 0，其餘欄位正常載入不遺失。
   - **嚴重度**：符合規範（PASS）

6. **tvagent/test/QuotaTest.java:74-91（測試驗證與未放寬查核）**
   - **說明**：
     - 本地執行 `sh tvagent/test.sh` 通過（exit code 0，112 個測試全數 PASS，輸出 `ALL PASS`）。
     - 比對 `git diff`，既有測試無任何一行被刪除或放寬，僅於檔尾新增 18 行斷言，覆蓋 WO-A2 ①～⑪ 項規範要求與 `save load adult` 來回序列化測試。
   - **嚴重度**：符合規範（PASS）

7. **AgentService.java:188（成人時段結束時的 adultEnd 值儲存）**
   - **說明**：
     - 當 Worker 回傳 `adult_left_s == 0` 時，依照算式 `q.adultEnd = el2 + Math.min(0, 600) * 1000 = el2`。
     - 雖然此時 `adultLeft(el)` 立即為 0，且下一跳之 `freeToWall` 落在 `wall - 60s` 屬正常無害，但 `state.properties` 中會記錄非零的歷史 `elapsedRealtime` 數值而非直接為 0。目前寫法完全忠實遵循計畫書公式 `adultEnd＝el2＋min(left,600)*1000`。
   - **嚴重度**：提示（Info）

---

### 建議

1. **部署順序建議**：
   - 務必依 [PLAN_adult.md](file:///Users/example-owner/TV-wt-a2/research/PLAN_adult.md) 上線順序，先部署 Worker（WO-A1）並確認 D1 `adult_sched` 設定生效後，再於電視端更新 `tvagent.apk`。若電視端先升級至 `app_ver = 4`，電視端能正常容錯（舊 Worker 回應無 `adult_left_s` 視為 0），但時段功能需等 Worker 上線後才會正式生效。
2. **未來維護小優化**：
   - 若未來進行微調，可在 `sync` 中考慮 `if (adultLeftS == 0) q.adultEnd = 0; else q.adultEnd = el2 + Math.min(adultLeftS, 600) * 1000;`，讓不在時段時的 `files/state.properties` 中的 `adult_end_el` 直接清零，狀態更直觀乾淨。目前版本行為完全符合規範，無需因此返工。
