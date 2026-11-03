PASS

### 逐條發現

1. **`tvagent/src/com/example/tvagent/Quota.java:126` 與 `AgentService.java:122`【Bug 1 修正審定】**
   - **說明**：Bug 1（原本 `freeToWall = wall - (el - adultEnd)` 在 `adultEnd == 0` 時會計算為開機時刻，導致重開機後第一跳開機前的使用時間被排除不扣）已抽成靜態純函式 `Quota.freeToWall(wall, el, adultEnd)`。當 `adultEnd == 0` 時明確回傳 `0`。此時在 `Quota.usage()` 內 `Math.max(cursor, freeToWall)` 等同於 `Math.max(cursor, 0) = cursor`（因 `cursor >= from = q.qWall > 0`），計算式退化回原有的 `d = e.wallMs - cursor`，開機前後前景使用時間完全按實扣額，重開機首跳少扣問題確實修復。
   - **嚴重度**：審查通過（正確修復）。

2. **`tvagent/src/com/example/tvagent/AgentService.java:188`【Bug 2 修正審定】**
   - **說明**：Bug 2（原本 `sync` 在 `adult_left_s == 0` 時賦值 `q.adultEnd = el2`，導致下一跳 `freeToWall` 落在上一跳 `qWall` 之後，每一跳都將 `status() + act() + sync()` 的耗時區間排除不扣）已修正為三元運算子 `adultLeftS > 0 ? el2 + Math.min(adultLeftS, 600) * 1000 : 0`。當不在時段（`adultLeftS == 0`）時直接歸零，下一跳 `freeToWall` 必定為 `0`，非時段每一跳漏扣問題確實修復。
   - **嚴重度**：審查通過（正確修復）。

3. **`tvagent/src/com/example/tvagent/AgentService.java:121-133` 與 `Quota.java:75, 86, 126`【Heartbeat 全情境與扣額路徑追蹤】**
   - **情境分析**：
     - **重開機後第一跳**：`boot != q.bootCount`，觸發 `adultInvalid == true`，`q.adultEnd` 即刻歸零，`freeToWall = 0`，全段照扣，無少扣。
     - **不在時段時每跳**：`adultLeftS == 0` 使 `q.adultEnd = 0`，`freeToWall = 0`，全段照扣，無少扣。
     - **時段中**：`q.adultEnd > el`，`freeToWall = wall + (q.adultEnd - el) > wall`，所有事件區間 `d = e.wallMs - freeToWall <= 0`，`usage.seconds = 0`，完全不扣額。
     - **時段剛結束**：`q.adultEnd > 0` 但 `el > q.adultEnd`，`freeToWall = wall - (el - q.adultEnd)` 精確切在時段結束的牆鐘時刻。時段內區間 `d <= 0` 免費，時段後區間 `d = e.wallMs - freeToWall > 0` 計入扣額；隨後 `sync` 回傳 `adultLeftS = 0` 將 `q.adultEnd` 清零，下一跳自然無縫回到 `freeToWall = 0`，交接精確無誤差。
     - **斷網中 adultEnd 已過期**：斷網期間未能 sync，`q.adultEnd` 保持過期前的舊值（`el > q.adultEnd`）。此時 `freeToWall` 為過去的結束時刻，小於當前跳起點 `q.qWall`，因此 `Math.max(cursor, freeToWall) == cursor`，所有前景區間完全照扣，無漏扣。
     - **時鐘跳變（`clock=jump`）**：`jump` 分支計算式為 `(el - Math.max(q.qEl, q.adultEnd)) / 1000`。不在時段時 `adultEnd == 0`，`Math.max` 為 `q.qEl`，行為與舊版完全一致；時段中時 `adultEnd >= el`，算式結果為 `0`；時段中途結束時僅扣結束後至當前 `el` 的秒數，且分 App 歸屬探測到的前景套件，無多扣或少扣。
     - **多扣／少扣數學邊界**：在 `usage()` 內，`Math.max(cursor, freeToWall) >= cursor` 恆成立，因此區間長度 `d` 恆不大於 `e.wallMs - cursor`，在數學上絕對不會造成多扣（不會大於實際觀看時間）。
   - **嚴重度**：審查通過（邏輯嚴密，無異常路徑）。

4. **`tvagent/test/QuotaTest.java:92-95`【新測試 ⑫⑬⑭ 與既有測試審定】**
   - **說明**：
     - 測試 ⑫（`freeToWall(1000000, 50000, 0) == 0`）：驗證 `adultEnd == 0` 時牆鐘回傳 0，直接守護 Bug 1。
     - 測試 ⑬（`freeToWall(1000000, 50000, 40000) == 990000`）：驗證 `adultEnd > 0` 時 el 基準映射回牆鐘之計算正確性。
     - 測試 ⑭（以 `freeToWall(..., 0)` 傳入 `usage()` 測試 60 秒前景全扣）：直接整合驗證非時段下 `freeToWall` 不會吃掉任何前景秒數。
     - 經 `git diff -U0 tvagent/test` 查驗，既有測試無任何刪除、修改或門檻放寬；本機執行 `sh tvagent/test.sh` 輸出 115 個 PASS，QuotaTest 與 AdbClientTest 皆 `ALL PASS`。
   - **嚴重度**：審查通過（測試具實質防護力，既有測試未放寬）。

5. **`tvagent/src/com/example/tvagent/AgentService.java:185`【防禦性程式碼】**
   - **說明**：`try { adultLeftS = data.optLong("adult_left_s", 0); } catch (RuntimeException ex) { adultLeftS = 0; }`。Android 平台的 `JSONObject.optLong` 遇到缺少欄位或格式非數值時本就會安全回傳預設值（不擲出例外的實作），外包一層 `try/catch` 屬冗餘防禦，但不影響正確性與執行效能。
   - **嚴重度**：提示（Info，不影響功能）。

---

### 建議

1. **部署與回歸驗證**：
   - 本次 fix1 代碼修改精確且經完整邏輯推演與測試驗收（無少扣、無多扣、無回滾相容風險）。
   - 請按計畫進行後續流程：
     1. 請工程指揮（Opus）進行最終審定。
     2. 遵守上線順序：先部署 Cloudflare Worker（WO-A1）確保有 `adult_left_s` 欄位與 D1 排程，再安裝 TV 端 apk（WO-A2）。
     3. 依 `PLAN_adult.md` 第 60 行規劃，於深夜時段依序進行測試排程覆蓋、手動 off 關閉、以及時段內重開機（`adb reboot`）之實機回歸驗證。
