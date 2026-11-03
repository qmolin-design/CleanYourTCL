FAIL

### 問題清單（違反規格或測試可假通過）

1. **`tvgate/test_gate.py:166-171`**
   - **問題**：`test_read_failure` 測試 `mode in ('empty', 'fail')` 時，第一輪 `mode = 'empty'` 執行完畢後 `tv` 狀態已被設為 `'unknown'`，且未重設電視狀態。進入第二輪 `mode = 'fail'` 時，第 168 行 `wait_for(lambda: self.state()['tv'] == 'unknown')` 呼叫 `/status` 讀取的是記憶體快取中的 `tv`，此時 `tv` 原本就是 `'unknown'`，導致該等待條件瞬間假通過，根本沒有等待或驗證假 adb 在 exit 1 模式下是否真正讓 `tv` 轉為 `'unknown'`。
   - **為什麼違反規格**：WO-T1 完成標準第 1 項要求「假 adb 回空輸出或 exit 1 → `/status` tv=unknown，`/open` 回 ok=false 且含 until」。測試在未還原電視狀態下連續驗證兩個 failure mode，若 `gate.py` 對 `exit 1` 漏設 `tv = 'unknown'`（例如僅處理空輸出而忽略非零結束碼），此測試依然會判定通過（假通過）。

2. **`tvgate/test_gate.py:150-155`、`tvgate/test_gate.py:125-127`**
   - **問題**：`test_expiry` 在到期觸發鎖定時，僅呼叫 `self.assert_lock_sequence()`，而該方法只比對參數列表索引 4 是否為 `'force-stop'` 接 `'disable-user'`，完全未檢查假 adb 收到參數的完整性；假 adb 腳本（第 30-31 行）也僅用 `'disable-user' in a` 觸發模擬，不驗證參數。
   - **為什麼違反規格**：WO-T1 第 1 項明確要求「記錄每次收到的完整參數」與「到期時間設成約 1 秒後 → 10 秒內收到 force-stop 再 disable-user（順序檢查），tv=locked」。若 `gate.py` 在到期套用時漏帶 `-s 192.0.2.69:5555`、漏帶 `--user 0` 或指定錯目標套件名稱，此測試依然會判定通過（假通過）。

3. **`tvgate/test_gate.py:256-261`**
   - **問題**：`test_sigterm_opens` 驗證 SIGTERM 開放時，第 260 行僅檢查 `self.assertTrue(any('enable' in a for a in self.calls()))`。
   - **為什麼違反規格**：WO-T1 第 1 項要求對 SIGTERM 收到 `enable` 且假 adb 記錄完整參數。若 SIGTERM 開放時送出的 adb 指令缺少 `-s 192.0.2.69:5555`、缺少 `--user 0` 或帶錯參數，測試只要字串中含有 `'enable'` 就判定通過（假通過）。

4. **`tvgate/test_gate.py:184-194`**
   - **問題**：`test_concurrent_lock` 在慢速 `/open` 中送 `/lock` 時，第 192-193 行僅檢查了 `self.assertFalse(self.state()['authorized'])` 以及假 adb 內部狀態檔 `self.assertTrue(self.locked())`，完全沒有對 `/status` 斷言 `self.state()['tv'] == 'locked'`。
   - **為什麼違反規格**：WO-T1 第 1 項規格明確要求「慢 `/open` 進行中送 `/lock` → 最終 tv=locked、authorized=false」。測試漏驗 `/status` 的 `tv` 欄位狀態，若狀態未正確更新為 `'locked'` 仍能通過（假通過／漏測狀態）。

5. **`tvgate/test_gate.py:197-200`**
   - **問題**：`test_hook_sleep_wake_and_grace` 測試休眠收到 `sleep` 時，第 200 行僅驗證 `self.assertFalse(self.locked())`，未從 `self.calls()` 驗證假 adb 是否收到帶有完整參數的 `enable` 指令。
   - **為什麼違反規格**：WO-T1 第 1 項要求「假 hook 送 `sleep` → 收到 enable、假 hook 收到 `ok`」，測試未對假 adb 收到的完整參數進行斷言（假通過）。

6. **`tvgate/test_gate.py:125-127`**
   - **問題**：`assert_lock_sequence()` 使用 `any(commands[i:i+2] == ['force-stop', 'disable-user'] for i in range(len(commands)-1))` 比對整個測試生命週期的全域 `calls` 歷史。
   - **為什麼違反規格**：在經歷多次操作的測試中，若先前步驟已經觸發過鎖定，後續操作即使未下指令、或者下指令順序顛倒，`assert_lock_sequence()` 都會因為歷史紀錄中殘留的舊呼叫而誤判為通過（假通過風險）。

7. **`tvgate/test_gate.py:157-164`**
   - **問題**：`test_reconcile_and_music` 驗證對帳停回時，使用 `wait_for(self.locked)`，其預設逾時高達 5 秒，而測試環境設定 `TVGATE_INTERVAL='0.4'`；測試未針對執行時間做上限驗證，且未檢查停回時 adb 指令的完整參數。
   - **為什麼違反規格**：WO-T1 第 1 項要求「假 adb 狀態改成『啟用』但沒有授權 → 一個（縮短的）對帳週期內停回」。若對帳延遲數個週期才停回（例如 3～4 秒），測試仍會判定通過（假通過）。

8. **`tvgate/test_gate.py:142-147`**
   - **問題**：`test_open_and_lock` 在呼叫 `/lock` 之後，只檢查了 `assert_lock_sequence()` 與 `self.assertFalse(self.state()['authorized'])`，未對 `/status` 端點驗證 `self.state()['tv'] == 'locked'`。
   - **為什麼違反規格**：WO-T1 第 1 項要求 `/lock` 後需讀回確認，漏查 `/status` 的電視狀態欄位。

---

### 任務 1：WO-T1 完成標準第 1 項測試案例逐條對照表

| WO-T1 規格測試案例 | [test_gate.py](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py) 對應測試名稱 | 審核結果與問題 |
|---|---|---|
| 錯 token、缺 token → 401，假 adb 沒收到 enable／disable-user／force-stop；GET → 405；min=7 → 400 | `test_auth_and_validation` | **部分缺漏**：僅對 `/open` 測了 token 缺／錯，未測 `/lock` 的 token；GET 405 僅測了 `/status`，未測 `/open` 或 `/lock` |
| `/open?min=15` → 假 adb 收到 `-s 192.0.2.69:5555 shell pm enable --user 0 com.google.android.youtube.tv`，`/status` authorized=true、tv=open | `test_open_and_lock` | **完整符合**：檢查了完整 adb 參數與 `/status` 狀態 |
| 到期時間設成約 1 秒後 → 10 秒內收到 force-stop 再 disable-user（順序檢查），tv=locked | `test_expiry` | **可假通過**：`assert_lock_sequence` 沒驗完整參數（漏檢查 `-s`、`--user 0` 等）；adb 腳本也未檢查參數 |
| `/lock` → 同上順序，authorized=false | `test_open_and_lock` | **缺漏**：未驗證 `/status` 的 `tv == 'locked'` 狀態 |
| 假 adb 狀態改成「啟用」但沒有授權 → 一個（縮短的）對帳週期內停回 | `test_reconcile_and_music` | **可假通過**：等待逾時使用預設 5 秒，未約束在一個縮短週期（0.4 秒）內停回；未驗完整 adb 參數 |
| 假 adb 輸出含 `package:com.google.android.youtube.tvmusic` 但不含 youtube.tv → 判定為啟用（不誤認） | `test_reconcile_and_music` | **混合測試**：與前項混在同一個測試中，透過後續 `/open` 驗證未被誤判為鎖定 |
| 假 adb 回空輸出或 exit 1 → `/status` tv=unknown，`/open` 回 ok=false 且含 until | `test_read_failure` | **可假通過**：第二輪 `fail` 執行前未重設狀態，此時狀態已是 `unknown`，導致 wait 瞬間假通過 |
| 假 adb 卡住（sleep 超過上限）→ `/open` 在 20 秒內回 ok=false；同時 `/status` 仍能在 2 秒內回應 | `test_hung_adb_status_responsive` | **完整符合** |
| 慢 `/open` 進行中送 `/lock` → 最終 tv=locked、authorized=false | `test_concurrent_lock` | **可假通過／缺漏**：漏驗 `/status` 的 `tv == 'locked'`，僅讀內部模擬檔 |
| 假 hook 送 `sleep` → 收到 enable、假 hook 收到 `ok`；假 adb 故障時假 hook 收到 `fail`；送 `wake` 且假 adb 前景不是 YouTube → 鎖回；送 `wake` 且前景是 YouTube → 不鎖、log 有記錄、通知指令被呼叫，之後前景改成非 YouTube → 一個週期內鎖回；寬限中 `/lock` → 立即鎖 | `test_hook_sleep_wake_and_grace` | **部分假通過**：收到 `sleep` 時未驗證 adb `enable` 指令的完整參數與順序 |
| 沒有 `wake`、平常對帳時前景是 YouTube 且沒授權 → 照鎖（寬限不外溢） | `test_no_grace_without_wake` | **完整符合** |
| 假 hook 自己結束 → gate.py 重新啟動它（log 有記錄） | `test_hook_restart` | **完整符合** |
| 重開 gate.py → 從 state.json 恢復到期時間；state.json 內容壞掉 → authorized=false；`TVGATE_HOME` 不可寫 → `/open` 回錯誤且沒有 enable | `test_restart_and_corrupt_state`、`test_unwritable_open` | **部分缺漏**：重開恢復未檢查 `until` 時間數值是否精確一致，只檢查了 `authorized` 為 True |
| 對 gate.py 送 SIGTERM → 收到 enable 後程序結束 | `test_sigterm_opens` | **可假通過**：僅以 `any('enable' in a ...)` 檢查，未驗證完整參數 |

---

### 任務 2：[gate.py](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py) 對照規格檢查結果

1. **套用時讀當下該開／該鎖**：**符合**。
   - [gate.py:146, 154](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L146-L154)：在拿取 `adb_lock` 後，動態呼叫 `authorized()` 決定 `desired`，慢速 `/open` 不會覆蓋較新的 `/lock`。
2. **整行比對 package:com.google.android.youtube.tv**：**符合**。
   - [gate.py:127](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L127)：使用 `'package:' + PACKAGE in result.stdout.splitlines()`，以行列表元素精確比對，不會誤認 `youtube.tvmusic`。
3. **讀取成功條件**：**符合**。
   - [gate.py:122-126](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L122-L126)：必須 `result is not None`、`result.returncode == 0` 且 `result.stdout.strip()` 非空才算成功，否則設為 `unknown` 並回傳 `False`。
4. **adb 一律帶 -s 且有 8 秒上限**：**符合**。
   - [gate.py:87, 106-110](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L87-L110)：所有 adb 指令開頭均帶 `[ADB, '-s', DEVICE]`；子程序單次執行設有 `timeout = min(8, ...)` 且逾時強制 `kill()`。
5. **回家寬限只由 wake 或斷線 ≥2 分鐘後重連觸發、不外溢**：**符合**。
   - [gate.py:182-183, 208-211, 231-232](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L182-L232)：僅在 sleephook 送來 `wake` 或斷線超過 120 秒恢復時才呼叫 `arrival()` 設定寬限；平常對帳不觸發寬限；前景離開 YouTube（[gate.py:151-152](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L151-L152)）或家長按 `/open`、`/lock`（[gate.py:289](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L289)）均立即清除寬限。
6. **SIGTERM 盡力開放**：**符合**。
   - [gate.py:319-322, 327-328](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L319-L328)：信號處理器停止主伺服器後，在 7.8 秒 deadline 內執行 `apply(forced_open=True, deadline=deadline)` 啟用 YouTube。
7. **token 檢查在所有端點**：**符合**。
   - [gate.py:269-273](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L269-L273)：在 `Handler.do_POST` 最開頭使用 `hmac.compare_digest` 檢查 `X-Token`，所有端點（`/status`、`/open`、`/lock`）均受到保護。非 POST 方法統一由 `do_GET` 回傳 405。
8. **寫檔失敗處理**：**符合**。
   - [gate.py:291-298](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L291-L298)：`/open` 寫檔失敗回傳 500 且不套用啟用；[gate.py:295, 309-312](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L295-L312) `/lock` 寫檔失敗仍將 `until` 清空並執行鎖定電視，回應中附帶錯誤訊息。

---

### 任務 3：[sleephook.swift](file:///Users/example-owner/TV-wt-tvgate/tvgate/sleephook.swift) 檢查結果

- **符合規格**。
  - [sleephook.swift:5-16](file:///Users/example-owner/TV-wt-tvgate/tvgate/sleephook.swift#L5-L16)：註冊 `NSWorkspace.willSleepNotification` 後，印出 `sleep` 並 `fflush(stdout)`；接著使用 `poll(&descriptor, 1, milliseconds)` 與 `read(STDIN_FILENO, &byte, 1)`，以 10 秒為硬性截止時間（`deadline = Date().addingTimeInterval(10)`），直到讀到換行符號（byte 10）、EOF 或逾時才結束 closure 返回系統。

---

### 任務 4：[com.example.tvgate.plist](file:///Users/example-owner/TV-wt-tvgate/tvgate/com.example.tvgate.plist) 檢查結果

- **符合規格**。
  - 程式路徑：第 5 行指定 `/Users/example-owner/TV/tvgate/gate.py`。
  - RunAtLoad：第 7 行 `<key>RunAtLoad</key><true/>`。
  - KeepAlive：第 8 行 `<key>KeepAlive</key><true/>`。
  - PATH：第 6 行 `<string>/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin</string>` 包含 `/opt/homebrew/bin`。

---

### 非阻擋建議（風格與改善建議）

1. **`tvgate/gate.py:118-120`**：空輸出重試時呼叫 `command(['connect', DEVICE], ...)`，底層會組合為 `adb -s 192.0.2.69:5555 connect 192.0.2.69:5555`，雖然 adb 容許 `-s`，但傳入兩次位址略顯冗餘。
2. **`tvgate/gate.py:204-213`**：`monitor()` 週期檢查時若讀取成功且未進入 recovery，先呼叫了一次 `read_tv()`，隨後呼叫 `apply()`，而 `apply()` 開頭又呼叫一次 `read_tv()`，導致常規對帳每次連續發出兩次 `pm list packages -d`。
3. **`tvgate/gate.py:124-125`**：`failed_since` 只有在 `monitor()` 對帳成功時才會被重設為 `None`。若外部透過 HTTP 請求觸發 `apply()` 且連線成功，並未同步重設 `failed_since`（雖然會在 5 秒內被 `monitor()` 處理，但直接在 `read_tv()` 成功時重設邏輯更嚴謹）。
