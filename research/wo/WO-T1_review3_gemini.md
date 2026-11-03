PASS

### 退修清單（WO-T1_fix1_prompt.txt）逐項查核結果

- **A1**：做到（[test_gate.py:18-20](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L18-L20)、[131-134](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L131-L134)、[153](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L153)、[158](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L158)、[167](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L167)、[178](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L178)、[181](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L181)、[219](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L219)、[227](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L227)、[232](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L232)、[256](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L256)、[269](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L269)、[326](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L326)）
  `assert_lock_sequence` 僅截取 `before` 之後的呼叫記錄，比對相鄰指令嚴格等於 `[FORCE_STOP, DISABLE]` 完整參數清單；開指令使用 `ENABLE` 完整參數比對；已完整套用於所列 6 個測試。
- **A2**：做到（[test_gate.py:184-195](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L184-L195)）
  `test_read_failure` 分別走訪 `('empty', 'fail')` 兩種獨立情境，各輪皆先清空 mode 並等待 `tv` 進入成功讀取狀態（open 或 locked），再切入故障模式等待 `tv` 變為 `unknown`，隨後驗證 `/open` 回傳 `ok=false` 且包含 `until`，並驗證觸發了 `['connect', DEVICE]`。
- **A3**：做到（[test_gate.py:157](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L157)、[217](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L217)）
  在 `test_open_and_lock` 與 `test_concurrent_lock` 中，送出 `/lock` 後皆斷言 `/status` 的 `tv == 'locked'`。
- **A4**：做到（[test_gate.py:175-177](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L175-L177)）
  `test_reconcile_and_music` 設定 `limit = 3 * float(self.env['TVGATE_INTERVAL'])`，記錄起點時間並以 `self.assertLess(time.monotonic() - start, limit)` 驗證在 3 個對帳週期內完成停回。
- **A5**：做到（[test_gate.py:257-270](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L257-L270)）
  在 `test_hook_sleep_wake_and_grace` 中，先刪除通知檔標記、送 `wake`（前景為 YouTube）、等待通知呼叫並睡眠 2 個對帳週期確認未鎖；接著送 `/lock`，以 `self.assertLess(time.monotonic() - start, 1)` 斷言 1 秒內完成，且斷言 `tv='locked'`、`authorized=false` 與完整鎖定順序。
- **A6**：做到（[test_gate.py:140-146](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L140-L146)）
  `test_auth_and_validation` 涵蓋 `/open?min=15`、`/lock`、`/status` 在缺 token 與錯 token 時皆回傳 401；401 後確認正確 token 查詢的 `authorized` 與 `until` 未受竄改；測試 `/status`、`/open`、`/lock` 的 GET 請求皆回傳 405；測試 `/open?min=7` 回傳 400；並驗證未對假 adb 發出啟用或停用指令。
- **A7**：做到（[test_gate.py:301-305](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L301-L305)）
  `test_restart_and_corrupt_state` 記錄重啟前的 `until`，重啟後斷言 `self.assertEqual(self.state()['until'], until)` 完全一致。
- **B1**：做到（[gate.py:105](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L105)、[108](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L108)、[119](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L119)）
  重連指令由 `command` 與內部重試直接發出 `[ADB, 'connect', DEVICE]`（不帶 `-s`）；其餘非 connect 裝置指令一律強制帶 `['-s', DEVICE]`。
- **B2**：做到（[gate.py:134-136](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L134-L136)、[154-155](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L154-L155)；[test_gate.py:271-287](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L271-L287)）
  `foreground()` 在 exit≠0、逾時或輸出空白時回傳 `None`；`apply()` 判斷若 `watching is None` 則直接返回 `False`，維持寬限狀態不鎖定亦不跳通知；僅在 `watching is False` 時清除寬限並鎖回。在 `test_gate.py` 新增 `test_wake_foreground_failure` 完整測試 dumpsys 失敗時不鎖不通知，恢復且前景非 YouTube 後鎖回。
- **C**：做到（[com.example.tvgate.plist:9-10](file:///Users/example-owner/TV-wt-tvgate/tvgate/com.example.tvgate.plist#L9-L10)）
  已加入 `<key>StandardOutPath</key><string>/Users/example-owner/Library/Logs/tvgate.out</string>` 與 `<key>StandardErrorPath</key><string>/Users/example-owner/Library/Logs/tvgate.err</string>`。

---

### 任務 1：WO-T1 完成標準第 1 項測試案例逐條對照

| 規格要求測試案例 | [test_gate.py](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py) 對應測試名稱與行號 | 假通過與狀態檢查查核 |
|---|---|---|
| 獨立暫存目錄 TVGATE_HOME、自生 token、假 adb 記錄完整參數與模擬輸出、假 hook、真 gate.py 子程序隨機埠 HTTP 呼叫、環境變數縮短對帳週期 | `GateTest.setUp`、`start`（[test_gate.py:70-90](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L70-L90)） | 通過：每測獨立 tempdir，隔離真實環境；隨機 port 避免衝突。 |
| 錯/缺 token → 401（無 adb 指令）；GET → 405；min=7 → 400 | `test_auth_and_validation`（[test_gate.py:135-149](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L135-L149)） | 通過：逐一驗證 401/405/400，檢查 401 後狀態不變，且斷言 adb 無 enable/disable/force-stop。 |
| `/open?min=15` → 收到 enable 完整參數，`/status` authorized=true、tv=open | `test_open_and_lock`（[test_gate.py:150-155](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L150-L155)） | 通過：比對 `ENABLE` 完整清單，檢查 `/status` 的 authorized 與 tv。 |
| 到期約 1 秒後 → 10 秒內收到 force-stop 再 disable-user（順序檢查），tv=locked | `test_expiry`（[test_gate.py:160-169](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L160-L169)） | 通過：以 `assert_lock_sequence` 嚴格比對相鄰順序，並等待 `/status` tv 變為 locked。 |
| `/lock` → 同上順序，authorized=false | `test_open_and_lock`（[test_gate.py:155-159](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L155-L159)） | 通過：以 `assert_lock_sequence` 檢查，並確認 authorized=false、tv=locked。 |
| 假 adb 改為啟用但無授權 → 對帳週期內停回 | `test_reconcile_and_music`（[test_gate.py:170-178](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L170-L178)） | 通過：設時間上限斷言（3 週期內），驗證停回完整鎖定順序。 |
| 假 adb 輸出含 `package:...youtube.tvmusic` 但不含 `youtube.tv` → 判定為啟用（不誤認） | `test_reconcile_and_music`（[test_gate.py:174-183](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L174-L183)） | 通過：假 adb 啟用時印出 `tvmusic`，gate 正確判讀為未鎖定而觸發鎖回，隨後開指令確認讀回為 open。若字串部分匹配會誤判為 locked 而使測試失敗。 |
| 假 adb 回空輸出或 exit 1 → `/status` tv=unknown，`/open` 回 ok=false 且含 until | `test_read_failure`（[test_gate.py:184-195](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L184-L195)） | 通過：empty 與 exit 1 各自獨立建立 open/locked 前置，驗證 unknown 與 `/open` 傳回 ok=false 且附帶 until。 |
| 假 adb 卡住（>8 秒）→ `/open` 在 20 秒內回 ok=false；`/status` 在 2 秒內回應 | `test_hung_adb_status_responsive`（[test_gate.py:196-206](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L196-L206)） | 通過：併發測試，status 免取 adb 鎖立即回傳 200，open 在 20 秒內返回 ok=false。 |
| 慢 `/open` 進行中送 `/lock` → 最終 tv=locked、authorized=false | `test_concurrent_lock`（[test_gate.py:207-220](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L207-L220)） | 通過：偵測到 enable 後併發送 `/lock`，斷言 authorized=false、tv=locked、假 adb 狀態檔鎖定，且檢驗 lock 指令順序。 |
| 假 hook 送 sleep → enable 且 hook 收到 ok；adb 故障 hook 收到 fail；wake 前景非 YouTube 鎖回；wake 前景是 YouTube 進入寬限並通知；離開 YouTube 鎖回；寬限中 `/lock` 立即鎖 | `test_hook_sleep_wake_and_grace`（[test_gate.py:221-270](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L221-L270)） | 通過：涵蓋 sleep (ok/fail)、wake、通知被觸發、2 週期未鎖、dumpsys 失敗不誤鎖、離開 YouTube 鎖回；寬限中送 `/lock` 驗證 `< 1` 秒立即完成鎖定與狀態切換。 |
| 平常對帳前景是 YouTube 且無授權 → 照鎖（寬限不外溢） | `test_no_grace_without_wake`（[test_gate.py:288-294](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L288-L294)） | 通過：未送 wake 時前景設為 YouTube 且狀態為啟用，對帳立即鎖回且未發出通知。 |
| 假 hook 自己結束 → gate.py 重新啟動（log 有記錄） | `test_hook_restart`（[test_gate.py:295-299](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L295-L299)） | 通過：送 exit 結束子程序，等待啟動次數 ≥2，並檢查 log 含 `restarting`。 |
| 重開 gate.py → state.json 恢復到期時間；state.json 損毀 → authorized=false；TVGATE_HOME 不可寫 → `/open` 失敗且無 enable | `test_restart_and_corrupt_state`（[test_gate.py:300-310](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L300-L310)）、`test_unwritable_open`（[test_gate.py:311-320](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L311-L320)） | 通過：檢驗重啟 until 相同、損毀 authorized=false、目錄 0o500 下 `/open` 失敗且 calls 無 enable。 |
| 送 SIGTERM → 收到 enable 後程序結束 | `test_sigterm_opens`（[test_gate.py:321-327](file:///Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py#L321-L327)） | 通過：送 SIGTERM 後確認電視解鎖，且 calls 內包含 `ENABLE`。 |

> 結論：WO-T1 第 1 項之規格項目皆有具體測試案例對應，無缺漏測試；各項測試已加強完整參數、順序與狀態檢驗，無假通過漏洞。

---

### 任務 2：gate.py 規格查核

1. **套用時讀當下該開／該鎖**（[gate.py:148](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L148)、[170](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L170)）：
   `apply` 取得 `adb_lock` 後，於第 148 行與第 170 行皆重新讀取當下的 `authorized()` 決定 `desired`，非依賴發起請求時的舊狀態；慢速請求無法覆寫較新的 `/lock`。符合規格。
2. **整行比對 package:com.google.android.youtube.tv**（[gate.py:127](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L127)）：
   以 `'package:' + PACKAGE in result.stdout.splitlines()` 比對切割後的每一行，嚴格防止誤認 `tvmusic`。符合規格。
3. **讀取成功條件**（[gate.py:122-129](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L122-L129)）：
   `result is None or result.returncode or not result.stdout.strip()` 時判定為失敗並設 `tv = 'unknown'`；僅在 returncode 為 0 且輸出非空時才算成功，並更新 `checked_at`。符合規格。
4. **adb 一律帶 -s 且有 8 秒上限**（[gate.py:86](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L86)、[105](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L105)、[108](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L108)）：
   單次 subprocess 的 timeout 取 `min(8, ...)`，逾時即 kill；除 `connect` 指令外（依 B1 裁定）一律加上 `['-s', DEVICE]`。鎖與開均以 `read_tv` 讀回判定成功與否，不看指令 return code。符合規格。
5. **回家寬限只由 wake 或斷線 ≥2 分鐘後重連觸發、不外溢**（[gate.py:150-168](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L150-L168)、[188-195](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L188-L195)、[212-216](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L212-L216)、[235-236](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L235-L236)）：
   `arrival()` 僅由 `wake` 或 monitor 偵測到斷線 ≥ 120 秒恢復時呼叫；若電視正在看 YouTube 則跳通知並標記 `grace`；寬限中 dumpsys 失敗時不鎖不通知；一旦離開 YouTube 則鎖定；任何 `/lock` 或 `/open` 立即清除 `grace`；平常對帳不進入 `arrival()`，直接鎖定。符合規格。
6. **SIGTERM 盡力開放**（[gate.py:322-331](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L322-L331)）：
   收到 SIGTERM 關閉 server 迴圈後，以 7.8 秒 deadline 呼叫 `apply(forced_open=True)` 盡力開放，隨後清理子程序與 hook。符合規格。
7. **token 檢查在所有端點**（[gate.py:272-277](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L272-L277)）：
   所有 POST 請求在處理路徑（`/status`、`/open`、`/lock`）前一律先驗證 `X-Token`，錯/缺立即回傳 401 且狀態不變。符合規格。
8. **寫檔失敗處理**（[gate.py:294-301](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L294-L301)、[312-315](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L312-L315)）：
   `/open` 寫檔失敗時不更新記憶體 `until`，不呼叫 `apply()`，回傳 500 錯誤；`/lock` 寫檔失敗時仍將記憶體 `until` 清空並照常鎖定電視，回應中附帶錯誤訊息。符合規格。

---

### 任務 3：sleephook.swift 休眠等待機制查核

- [sleephook.swift:8-15](file:///Users/example-owner/TV-wt-tvgate/tvgate/sleephook.swift#L8-L15)
  收到 `NSWorkspace.willSleepNotification` 後，透過 `pollfd` 監聽 `STDIN_FILENO`，deadline 設定為 10 秒；在 10 秒內單 byte 讀取直到收到 ASCII 10（`\n`）、EOF 或逾時才結束回呼返回。確實在 sleep 後等待 stdin 回覆（最多 10 秒）才返回。

---

### 任務 4：com.example.tvgate.plist 配置查核

- [tvgate/com.example.tvgate.plist](file:///Users/example-owner/TV-wt-tvgate/tvgate/com.example.tvgate.plist)
  1. 程式路徑：`<string>/Users/example-owner/TV/tvgate/gate.py</string>`（正確）
  2. RunAtLoad：`<key>RunAtLoad</key><true/>`（正確）
  3. KeepAlive：`<key>KeepAlive</key><true/>`（正確）
  4. PATH：`<string>/opt/homebrew/bin:/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin</string>`（包含 `/opt/homebrew/bin`，正確）
  5. StandardOutPath / StandardErrorPath：已配置為 `/Users/example-owner/Library/Logs/tvgate.out` 與 `/Users/example-owner/Library/Logs/tvgate.err`（正確）

---

### 問題清單

#### 阻擋問題（違反規格或測試可假通過）
無。

#### 非阻擋（風格／改進建議）
1. [gate.py:165](file:///Users/example-owner/TV-wt-tvgate/tvgate/gate.py#L165)：`osascript` 在 launchd 背景守護程序執行時可能因無 GUI session 或缺乏通知權限而拋出非零回傳碼，程式內雖已有 `except (OSError, subprocess.TimeoutExpired)` 容錯防護，WO-T2 實機驗收時可特別留意通知是否能正常在桌面顯示。
2. [sleephook.swift:14](file:///Users/example-owner/TV-wt-tvgate/tvgate/sleephook.swift#L14)：`read(STDIN_FILENO, &byte, 1) != 1 || byte == 10` 單 byte 讀取若遇無換行輸入會持續等候至 10 秒逾時；由於 `gate.py` 保證送出結尾帶 `\n` 之回應，現況運作正常無礙。
