# 審核結果：PASS

本審核針對 `/Users/example-owner/TV-wt-a3` 工作區（分支 `poll-a3`）的 WO-A3 變更進行唯讀審查。依據 [PLAN_poll.md](file:///Users/example-owner/TV-wt-a3/research/PLAN_poll.md)（含檔尾 TERRA 8 條裁定）、[WO-A3_report.md](file:///Users/example-owner/TV-wt-a3/research/wo/WO-A3_report.md) 及 `git diff`，必查項目全部通過。

---

## 必查項目逐項驗證（計畫裁定 #7 指定）

### ① heartbeat 兩條路徑 sync 都只呼叫一次
- **驗證位置**：[`AgentService.java#L141-L170`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/AgentService.java#L141-L170)
- **結果**：**PASS**
- **細節**：
  - `if (pre)` 分支（`wouldStop == true`）：在 L146 呼叫一次 `sync = sync(q, s, gap, boot, el, today);`，此分支無其他 `sync()` 呼叫。
  - `else` 分支（`pre == false`）：在 L160 呼叫一次 `sync = sync(q, s, gap, boot, el, today);`，此分支無其他 `sync()` 呼叫。
  - 兩條路徑互斥，任何一次 heartbeat 執行都保證剛好呼叫一次 `sync()`。

---

### ② wouldStop 路：save → sync（獨立 try/catch，任何例外／非 ok 都繼續）→ 成功先 save → act → save；sync 失敗一定還會 act 停用
- **驗證位置**：[`AgentService.java#L144-L153`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/AgentService.java#L144-L153)
- **結果**：**PASS**
- **細節**：
  - **順序完全符合**：
    1. `try { save(q); } catch (Exception ignored) { }`（先落盤當前扣額狀態）
    2. `try { sync = sync(...); } catch (Exception ex) { sync = "io"; }`（獨立 try/catch，網路中斷、超時等任何例外皆捕捉並賦值 `"io"`，非 ok 如 `"skip"`／`"config"`／`"http:xxx"` 亦不丟例外）
    3. `if (sync.equals("ok")) { try { save(q); } catch (Exception ignored) { } }`（sync 成功帶回之新 balance／last_seq／adultEnd 先落盤）
    4. `action = act(q, s);`（必然執行 `act()`）
    5. `try { save(q); } catch (Exception ignored) { }`（act 執行後落盤）
  - **sync 失敗保證停用**：sync 失敗或非 ok 時，`q.balance` 與 `q.adultLeft` 維持 0（`eff == 0`），進入 `act(q, s)` 呼叫 `decide`，必然回傳 `Action.END` 或 `Action.LOCK`，執行 `adb` force-stop 與 `DISABLE`，正確停用受控 App。

---

### ③ sync 帶回加值時不會 END／LOCK（decide 看到 eff>0）
- **驗證位置**：[`AgentService.java#L148-L151`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/AgentService.java#L148-L151)、[`Quota.java#L114-L122`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/Quota.java#L114-L122)
- **結果**：**PASS**
- **細節**：
  - 若 sync 帶回加值（`Grant` 增加 balance 或帶回 `adult_left_s`），在 `sync()` 內部已透過 `applySync` 或更新 `adultEnd` 使本機有效額度 `eff = balance + adultLeft > 0`。
  - 接續執行 `act(q, s)` 時，`decide` 判斷 `eff > 0`：
    - 不再滿足 `eff == 0 && (yt.equals("enabled") || mixed)`，絕不回傳 `END` 或 `LOCK`。
    - 若 `s.mixed` 為真，回傳 `UNLOCK` 補齊停用 App；若原本即為 `enabled` 且未 mixed，回傳 `NONE`（或 `eff <= 300` 且未提示過時回傳 `WARN`）。
  - YouTube 不會被關閉、NoticeActivity 不會彈出「時間已到」。

---

### ④ 鎖住等加值（yt=disabled、eff=0）時走舊路，加值後同一跳 UNLOCK
- **驗證位置**：[`Quota.java#L132-L134`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/Quota.java#L132-L134)、[`AgentService.java#L153-L170`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/AgentService.java#L153-L170)
- **結果**：**PASS**
- **細節**：
  - 鎖住且無額度時：`s.yt = "disabled"`、`mixed = false`、`eff = 0`。
  - `Quota.wouldStop("disabled", false, 0)`：因 `"enabled".equals(yt)` 為假且 `mixed` 為假，回傳 `false`。
  - 進入 `else` 舊路徑：
    1. 第一次 `action = act(q, s);`，`decide` 回傳 `NONE`（`action = "none"`）。
    2. 執行 `sync` 詢問伺服器。
    3. 若家長加值，`sync.equals("ok")` 且 `q.balance != balBefore`（或 `effBefore > 0 != effAfter > 0`）成立。
    4. 觸發 `action += "," + act(q, s);`，第二次 `act(q, s)` 看到 `eff > 0` 且 `s.yt == "disabled"`，`decide` 回傳 `Action.UNLOCK`。
    5. 執行 `adb(ENABLE)`，同一跳內完成解鎖，`action` 記為 `"none,unlock"`。

---

### ⑤ nextAlarmMs 新多載：eff=0＋螢幕亮＋yt 非 unknown → 10000，其他與舊公式一致
- **驗證位置**：[`Quota.java#L145-L149`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/Quota.java#L145-L149)、[`AgentService.java#L172`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/AgentService.java#L172)
- **結果**：**PASS**
- **細節**：
  - 實作：
    ```java
    long nextAlarmMs(boolean fgAndScreen, long adultLeft, boolean screenOn, boolean locked) {
        long eff = balance + Math.max(0, adultLeft);
        if (eff == 0 && screenOn && locked) return 10000;
        return nextAlarmMs(fgAndScreen, adultLeft);
    }
    ```
  - `AgentService` 呼叫端傳入：`screenOn = s.screen.equals("on")`、`locked = !"unknown".equals(s.yt)`。
  - 條件符合 `eff == 0 && screenOn && locked` 時回傳 10000 ms（10 秒）。
  - 若螢幕關閉（`screenOn = false`）或狀態未知（`locked = false`），走原公式回傳 60000 ms。
  - 若 `eff > 0`，走原公式（5 秒～60 秒動態計算）。舊多載 `nextAlarmMs(boolean)` 與 `nextAlarmMs(boolean, long)` 保留不變。

---

### ⑥ enforce=false（影子模式）、爸媽時段 adult_left_s、mixed、unknown 各情境沒有回歸
- **驗證位置**：[`AgentService.java`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/AgentService.java)、[`Quota.java`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/Quota.java)
- **結果**：**PASS**
- **細節**：
  - **`enforce = false`（影子模式）**：`pre` 路徑下先 sync，若 sync 將 enforce 改為 true 則當跳立刻執行真實停用；若維持 false，`act()` 回傳 `"dry-end"`／`"dry-lock"`，不碰 adb，狀態維持 `s.yt == "enabled"`，下次鬧鐘因螢幕亮且非 unknown 設為 10 秒；舊路徑亦保留 enforce 變更補跑 act 的機制。
  - **爸媽時段 `adult_left_s`**：`eff = balance + adultLeft`，時段中 `eff > 0` 走 `pre = false` 舊路；若剛過時段且未加值，`pre = true` 先 sync，若伺服器延長爸媽時段（`adult_left_s > 0`），更新 `adultEnd` 後 `decide` 不鎖定；時段失效檢測與 `freeToWall` 扣額邏輯完全未受影響。
  - **`mixed`（單邊停用）**：`wouldStop("enabled", true, 0)` 為 `true`，先 sync；若無加值則 act 停用兩套件並清 `mixed = false`；若有加值則 act 觸發 `UNLOCK` 解鎖補齊兩套件。
  - **`unknown`（adb 異常）**：`wouldStop("unknown", ...)` 回傳 `false` 不進預同步；`nextAlarmMs` 因 `locked = false` 維持 60000 ms，不加速重試。

---

### ⑦ 禁止的檔沒動、decide()／扣額／爸媽邏輯沒動
- **驗證位置**：`git diff --stat` 與 `git diff`
- **結果**：**PASS**
- **細節**：
  - 禁止動到的檔案皆未更動：
    - `AdbClient.java`：未動
    - `Whitelist.java`：未動
    - `NoticeActivity.java`：未動
    - `AndroidManifest.xml`：未動
    - 簽章檔（`debug.keystore` 等）：未動
    - `testdata/`：未動
  - 核心計算邏輯：
    - `Quota.decide(...)`：逐字無改動
    - 扣額邏輯（`Quota.consume(...)`、`Quota.usage(...)`）：逐字無改動
    - 爸媽邏輯（`adultLeft`、`freeToWall`、`adultInvalid`）：逐字無改動
  - 本次僅改動：[`README.md`](file:///Users/example-owner/TV-wt-a3/tvagent/README.md)、[`AgentService.java`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/AgentService.java)、[`Quota.java`](file:///Users/example-owner/TV-wt-a3/tvagent/src/com/example/tvagent/Quota.java)、[`QuotaTest.java`](file:///Users/example-owner/TV-wt-a3/tvagent/test/QuotaTest.java)、以及由 `build.sh` 建置之 `tvagent.apk`。

---

### ⑧ sh tvagent/test.sh 全過
- **驗證指令**：`sh tvagent/test.sh`
- **結果**：**PASS**
- **細節**：
  - `AdbClientTest`：42 項測試全過，`ALL PASS`。
  - `QuotaTest`：62 項測試全過（含既有測試與 WO-A3 新增之 8 項測試），`ALL PASS`。
  - 包含計畫指定之 5 項單元測試標準：
    1. `poll locked screen on 10s` & `poll locking screen on 10s`（10000 ms）
    2. `poll locked screen off 60s`（60000 ms）
    3. `poll unknown 60s`（60000 ms）
    4. `poll eff20 same`、`poll eff330 same`、`poll adult eff50 same`（eff>0 抽樣與舊公式完全一致）
    5. `wouldStop` 各情境真假值驗證（enabled+0、disabled+0、unknown、mixed+0、enabled+60）。

---

## 結論
**PASS**。WO-A3 實作完全契合 `research/PLAN_poll.md` 與審查裁定規範，無回歸風險，可進行後續實機驗收（計畫第 31～37 行之實機步驟 1～5）。
