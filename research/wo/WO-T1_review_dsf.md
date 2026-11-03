FAIL

## 阻擋問題（違反規格／測試可假通過）

**1. `tvgate/test_gate.py:165-171`（`test_read_failure`）— 「exit 1 → tv=unknown」這條完成標準實質上沒被驗到**
`wait_for(lambda: self.state()['tv'] == 'unknown')` 的條件在測試開始時本來就成立（`gate.py:32` 的初值就是 `'unknown'`），而 `read_tv`（`gate.py:122-123`）對任何失敗也都設成 `'unknown'`。因此：
- `'empty'` 那圈可能在 gate 根本還沒讀過電視時就通過這個等待；
- 進到 `'fail'`（exit 1）那圈時，`tv` 已經被上一圈的 `/open` 失敗留在 `'unknown'`，所以這個等待必然瞬間成立 —— 沒有任何斷言是在「exit 1 造成失敗讀取之後」才檢查 `tv`（171 行之後就結束了）。
結果：即使 gate 對 exit 1 的處理與空輸出不一樣，測試照樣全綠。違反 WO-T1 完成標準「假 adb 回空輸出或 exit 1 → `/status` tv=unknown」必須逐項成立的要求。

**2. `tvgate/test_gate.py:184-193`（`test_concurrent_lock`）— 只驗假 adb 的狀態檔，沒驗 `/status` 的 `tv`**
完成標準寫的是「最終 **tv=locked**、authorized=false」，但 193 行 `self.assertTrue(self.locked())` 讀的是測試自己的 `locked` 狀態檔（假電視），192 行只驗 `authorized`。`/status` 回傳的 `tv` 完全沒被斷言，所以「裝置被鎖住但 API 回報 tv=open（例如回讀失敗卻仍回舊值）」這種違反 `運作方式`「`tv`／`tv_checked_at` 只來自最後一次成功讀取」的情況，這個案例抓不到 → 可假通過。

**3. `tvgate/test_gate.py:218-223`（`test_hook_sleep_wake_and_grace` 尾段）— 「寬限中按 `/lock` 立即鎖」是空斷言，且會間歇失敗**
- 216-217 行已把 `front=0` 並等到鎖回，**寬限早就結束了**；218-220 重新 `wake` 後只用固定 `time.sleep(.3)` 等，沒有任何可觀察證據（`notified` 檔在 211 行就存在、未清除）確認 grace 這次真的成立。
- 若 grace 沒建立（或 `/lock` 比 arrival 早到），一般對帳（`front=1`、無授權、無 grace）也會在 0.4 秒內鎖回，223 行 `assertTrue(self.locked())` 照樣通過 → 這個案例無法區分「寬限中 `/lock` 立即鎖」與「本來就會鎖」。
- 反向也會不穩：若 arrival 在 `/lock` 之後才設定 `grace=True`，`apply` 的 `in_grace`（`gate.py:147-150`）會因前景是 YouTube 而直接 `return True` **不鎖**，223 行就失敗。
違反完成標準「寬限中 `/lock` → 立即鎖」這一條的可驗證性。

## 非阻擋

- `tvgate/test_gate.py:125-127`：`assert_lock_sequence` 掃全部 `calls`，未用 `before` 界定範圍；目前各案例前段不會產生鎖指令所以還準確，但之後加步驟容易誤判。
- `tvgate/test_gate.py:129-136`：只驗「假 adb 沒收到改狀態指令」，沒驗 401 後 `/status` 的 `authorized`/`until` 未變（`運作方式`：「401，狀態不變」）。
- `tvgate/test_gate.py:160`：等「一個（縮短的）對帳週期」卻用預設 5 秒（INTERVAL=0.4），上限比標準寬鬆 12 倍。
- `tvgate/gate.py:143-154`：先 `read_tv()` 再算 `desired`，與規格敘述順序（先讀該開／該鎖）相反；因整個過程持 `adb_lock`，語意等價（甚至更新），不算違規。
- `tvgate/gate.py:178`：`foreground()` 讀失敗（exit≠0）會被當成「前景不是 YouTube」而直接切；規格未定義讀取失敗行為，睡眠喚醒時 dumpsys 一時失敗會直接鎖（風險）。
- `tvgate/gate.py:288-298`：`/open` 寫檔失敗會先清掉 `grace` 再回 500，等於撤銷寬限但沒有任何新到期時間；`/lock` 寫檔失敗時 `until` 仍照清（符合規格）。
- `tvgate/gate.py:299`：`/open` deadline 19 秒，標準要求 20 秒內回應，餘裕僅 1 秒（慢機器可能 flaky）。
- `tvgate/gate.py:118-120`：空輸出時多一次 `connect`（規格只寫「失敗先 connect 再試一次」），不影響語意。
- `tvgate/com.example.tvgate.plist`：沒有 `StandardOutPath`／`StandardErrorPath`，stderr 會落到 system log（非規格要求）。
- `tvgate/README.md:17`：安裝假設資料夾已在 `/Users/example-owner/TV/tvgate`（與 plist 一致），但目前工作樹是 `TV-wt-tvgate`，需先複製。

## 逐條對照 WO-T1 完成標準第 1 項

| 完成標準案例 | 測試 |
|---|---|
| 每案新暫存目錄／自產 token／假 adb 記錄完整參數／假 hook／真 gate.py＋隨機埠／縮短週期 | `setUp` 60-79 |
| 錯 token、缺 token → 401 且假 adb 沒收到改狀態指令 | `test_auth_and_validation` 132-133、136 |
| GET → 405 | 同上 134 |
| `min=7` → 400 | 同上 135 |
| `/open?min=15` → enable 完整參數、authorized=true、tv=open | `test_open_and_lock` 139-141 |
| 到期 → 10 秒內 force-stop 再 disable-user、tv=locked | `test_expiry` 148-155 |
| `/lock` → 同順序、authorized=false | `test_open_and_lock` 142-146 |
| 無授權但裝置被啟用 → 對帳停回 | `test_reconcile_and_music` 157-161 |
| `youtube.tvmusic` 不誤認 | `test_reconcile_and_music` 157-163、`test_expiry` 151（`locked=0` 時假 adb 只吐 music 行） |
| 空輸出／exit 1 → tv=unknown、ok=false 且含 until | `test_read_failure` 165-171（見阻擋 1） |
| adb 卡住 → `/open` 20 秒內 ok=false、`/status` 2 秒內 | `test_hung_adb_status_responsive` 173-182 |
| 慢 `/open` 中送 `/lock` → 最終 locked、authorized=false | `test_concurrent_lock` 184-193（見阻擋 2） |
| sleep → enable、hook 收 ok；adb 故障 → hook 收 fail | `test_hook_sleep_wake_and_grace` 197-207 |
| wake 且前景非 YouTube → 鎖回 | 同上 201-202 |
| wake 且前景 YouTube → 不鎖、log 記錄、通知被呼叫 | 同上 209-215 |
| 之後前景改非 YouTube → 一週期內鎖回 | 同上 216-217 |
| 寬限中 `/lock` → 立即鎖 | 同上 218-223（見阻擋 3） |
| 無 wake、前景 YouTube、無授權 → 照鎖（不外溢） | `test_no_grace_without_wake` 225-230 |
| 假 hook 自己結束 → 重啟並記 log | `test_hook_restart` 232-235 |
| 重啟恢復 until／state.json 壞掉 → authorized=false | `test_restart_and_corrupt_state` 237-244 |
| `TVGATE_HOME` 不可寫 → `/open` 錯誤且無 enable | `test_unwritable_open` 246-254（chmod 後 `calls` 檔本身仍 644，假 adb 仍可記錄，斷言有效） |
| SIGTERM → enable 後結束 | `test_sigterm_opens` 256-262 |

## 已核對且符合（任務 2-4）

- `gate.py` 套用流程：`adb_lock` → 讀電視 → 以**當下** `authorized()` 算 desired → 不同才下指令 → 讀回判定（143-165）；「慢 `/open` 不蓋掉較新 `/lock`」由 288-302 的 `until == value`＋`authorized()` 複驗成立。
- 整行比對：`'package:' + PACKAGE in result.stdout.splitlines()`（127），非子字串。
- 讀取成功條件：exit 0 且輸出非空（122）；空輸出另有一次 connect 重試（118-120）。
- adb 一律 `-s 192.0.2.69:5555`（105、108）；每次 `min(8, …)` 上限（87-97）。
- 回家寬限：只有 `hooks()` 的 `wake`（231）與 `monitor` 的 `failed_since ≥ 120` 重連成功（208-211）會進 `arrival()`；`arrival` 另用 `revision`＋二次授權檢查避免誤判（179-183）；`apply` 只在 `grace` 且 desired=locked 時走前景判斷（147-152），平常對帳不受影響。
- SIGTERM：`terminate` → `serve_forever` 返回後 `apply(forced_open=True, deadline=+7.8)`（336-340），`command` 對 stop 的短路僅在 `deadline is None` 生效（90），故關機路徑仍會下 enable。
- token 檢查在 `/status`、`/open`、`/lock` 之前（273-277），且 `hmac.compare_digest`；token 檔缺失/空 → 一律 401。
- 寫檔：暫存檔＋`os.replace`（76-82）；`/open` 失敗回 500 且不改 `until`、不啟用（291-298）；`/lock` 失敗仍鎖並附 `error`（295-296、309-312）。
- `sleephook.swift:5-16`：印 `sleep` 後以 `poll` 迴圈讀 stdin 直到換行或 `Date()+10s` 到期才返回 handler，符合「最多 10 秒」。
- `plist:5-8`：`/Users/example-owner/TV/tvgate/gate.py`、`RunAtLoad`、`KeepAlive`、`PATH` 首段 `/opt/homebrew/bin` 皆符合。
