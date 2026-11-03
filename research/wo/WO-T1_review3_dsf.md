PASS

（唯讀審核；未執行測試、未跑 adb／git status，僅讀指定檔案。）

## 1. WO-T1 完成標準第 1 項：逐條對照

| 規格要求 | 對應測試（test_gate.py） | 判定 |
|---|---|---|
| 每案新暫存目錄＋自產 token＋假 adb（記錄完整 argv）＋假 hook＋真 gate.py＋隨機埠＋HTTP | setUp/start 70-89；假 adb 記錄 26-27；TVGATE_INTERVAL=0.4 82 | 有 |
| 錯/缺 token → 401 且無 enable/disable-user/force-stop；GET→405；min=7→400 | test_auth_and_validation 135-148（before 在 139，只驗新增呼叫；148 逐元素比對） | 有 |
| /open?min=15 → 假 adb 收到完整 enable 參數、status authorized=true/tv=open | test_open_and_lock 150-154（`assertIn(ENABLE, calls[before:])`，ENABLE 18 為完整參數） | 有 |
| 到期約 1 秒 → 10 秒內 force-stop 後 disable-user（順序）、tv=locked | test_expiry 160-168（assert_lock_sequence 131-133 保留相對順序並要求相鄰） | 有 |
| /lock → 同順序、authorized=false | test_open_and_lock 155-158 | 有 |
| 假 adb 改為啟用但無授權 → 縮短週期內停回 | test_reconcile_and_music 170-178（limit=3×INTERVAL 175-177） | 有（比 WO-T1 原文「一個週期」寬，見非阻擋 3） |
| 只含 youtube.tvmusic → 判定為啟用 | 假 adb 35：open 狀態只印 tvmusic；test_reconcile_and_music 182、test_open_and_lock 154 的 tv=='open' 即為此判定 | 有（隱性，未獨立命名） |
| 空輸出或 exit 1 → tv=unknown、/open ok=false 且含 until | test_read_failure 184-194（empty/fail 各自先 186-187 取得成功讀取，再切故障 189-190） | 有 |
| adb 卡住 → /open 20 秒內 ok=false、/status 2 秒內回應 | test_hung_adb_status_responsive 196-205 | 有 |
| 慢 /open 中送 /lock → 最終 tv=locked、authorized=false | test_concurrent_lock 207-219（before_lock 214 為界，217 驗 /status） | 有 |
| hook sleep → enable＋回 ok；adb 故障 → 回 fail | test_hook_sleep_wake_and_grace 224-228、235-237 | 有 |
| wake＋前景非 YouTube → 鎖回 | 229-232 | 有 |
| wake＋前景 YouTube → 不鎖、log、通知、之後非 YouTube → 一週期內鎖回 | 239-256 | 有 |
| 寬限中 /lock → 立即鎖 | 257-269（先清 notified、再 wake、確認 2 週期未鎖，之後 <1 秒鎖且 tv=locked/authorized=false） | 有 |
| 無 wake、平常對帳前景 YouTube 且無授權 → 照鎖（不外溢） | test_no_grace_without_wake 288-293 | 有 |
| hook 自行結束 → 重啟＋log | test_hook_restart 295-298 | 有 |
| 重開恢復 until／state 壞掉 authorized=false／TVGATE_HOME 不可寫 → /open 錯誤且無 enable | test_restart_and_corrupt_state 300-309、test_unwritable_open 311-319 | 有 |
| SIGTERM → 收到 enable 後結束 | test_sigterm_opens 321-326（stop() 98 等程序結束，326 驗完整 ENABLE 參數） | 有 |

沒有任何一條是「只比對假 adb 狀態檔」或「沒限定窗口」的假通過寫法：所有「收到某指令」的斷言都在 148/153/158/167/178/181/194/213/219/227/232/251/256/269/281/285/317/326 以 `before=len(calls)` 限定窗口，且 enable/force-stop/disable 均用完整參數（18-20）比對。

## 2. gate.py 對「運作方式」逐項

| 規格點 | 位置 | 判定 |
|---|---|---|
| 套用時讀當下該開/該鎖（非呼叫者原意） | 139-183；desired 在 read_tv 後重算 147-148、169-171；請求只改 until 再呼叫 apply 302 | 無違反 |
| 整行等於 package:com.google.android.youtube.tv | 127（`splitlines()` 成員判定，非子字串；tvmusic 不會誤中） | 無違反 |
| 讀取成功＝exit 0 且輸出非空，其餘 unknown | 122-126；checked_at 只在成功時更新 128 | 無違反 |
| adb 一律帶 -s、每次 8 秒上限、失敗先 connect 再試一次 | 105（connect 例外，見 B1）、86、107-110；以讀回判定成功 179-183 | 無違反（connect 依 B1 裁定） |
| 回家寬限只由 wake 或斷線 ≥2 分鐘後重連觸發、不外溢 | wake→236→arrival 188-194（僅未授權才設 pending）；重連 207-215（`>=120` 212）；平常對帳走 217 apply；寬限僅在 149-168，非 YouTube 立即 grace=False 157；/lock /open 清 grace 293 | 無違反 |
| 寬限中前景「不知道」不鎖也不通知，下一輪再判斷 | 154-155（foreground 失敗回 None 132-136） | 無違反 |
| SIGTERM 盡力開放（最多 8 秒） | 322-331（stop 設後仍允許帶 deadline 的指令 89） | 無違反 |
| token 檢查在所有端點（含 /status） | 270-281（先驗 token 再看路徑；401 前無副作用） | 無違反 |
| 寫檔失敗處理：open 回錯誤不啟用／lock 照鎖回錯誤 | 294-301（open 失敗不更新 until 且 return）、298-299＋312-315（lock 失敗仍 until=None 並照鎖） | 無違反 |
| 主迴圈 5 秒／跨到期／滿 30 秒／失敗後重連成功即套用 | 200-217 | 無違反 |
| 唯一會改電視狀態的地方 | 只 174-178 | 無違反 |

## 3. sleephook.swift

第 5-16 行：收到 willSleep 後印 `sleep`（6-7）、用 `poll`＋`read` 等 stdin 一行（8-15），上限 10 秒（9-12：poll 逾時／EOF／讀到換行即返回）。**確實會等回覆（最多 10 秒）才從 handler 返回**，與 gate.py 的 7.8 秒 deadline（229）搭配成立。

## 4. plist

- 5：`/Users/example-owner/TV/tvgate/gate.py` ✅（與 README 的安裝位置、HOOK 預設 19 一致）
- 7：`RunAtLoad true` ✅
- 8：`KeepAlive true` ✅
- 6：PATH 開頭 `/opt/homebrew/bin` ✅
- 9-10：StandardOutPath／StandardErrorPath 為 `~/Library/Logs/tvgate.out|err` ✅（C 已補）

## 5. 退修清單逐項（A1–A7、B1、B2、C）

- **A1 做到**：assert_lock_sequence 131-133 改為窗口＋完整參數；用於 158/167/178/219/232/256/269/285；enable 完整參數比對 153/181/227/326；只比對窗口者見 148/194/213/251/317。
- **A2 做到**：test_read_failure 185-194，兩種模式各自先 186-187 取得成功讀取（open/locked）再切 189-190，各自驗 192-193 `ok=false` 且含 `until`。
- **A3 做到**：157（`(False,'locked')`）、217（`(False,'locked')`）皆為 /status，而非只看 218 的假 adb 狀態檔。
- **A4 做到**：175-177 `limit=3*TVGATE_INTERVAL` 並以 177 時間上限斷言。
- **A5 做到**：257 清 notified、260-263 wake 後確認通知且 2 週期未鎖、264-269 /lock 於 1 秒內並驗 tv=locked/authorized=false。
- **A6 做到**：140-144（/open、/lock、/status × 缺/錯 token → 401，並比對 authorized/until 未變）、145-146（GET /status /open /lock → 405）。
- **A7 做到**：302 取 until、303 重開、305 比對相等。
- **B1 做到**：gate.py 105 連線指令不帶 -s（`args[0] == 'connect'`），其餘一律 `['-s', DEVICE]`；測試 194/213 亦以 `['connect', DEVICE]` 驗證。
- **B2 做到**：gate.py 132-136 把 exit≠0／逾時／空輸出視為 None；154-155 遇到 None 直接返回（不鎖、不通知）；只有確定非 YouTube 才走 157→鎖；新增測試 test_wake_foreground_failure 271-286（wake＋dumpsys 失敗不鎖、不通知 279-281；恢復後前景非 YouTube → 鎖 284-285）。
- **C 做到**：plist 9-10。

上一輪 A/B/C 的問題在目前程式碼中已看不到殘留；未發現新引入的回歸。

## 非阻擋（不影響 PASS）

1. 你回報「13 個全過」，但 test_gate.py 有 **14** 個 test_ 方法（135/150/160/170/184/196/207/221/271/288/295/300/311/321）；最可能是 B2 新增的 `test_wake_foreground_failure` 不在那次執行範圍。建議再跑一次確認 14/14，否則完成標準「全過」的證據不完整。
2. `test_hook_sleep_wake_and_grace` 沒斷言 sleep 後 authorized 仍為 false（規格：不改到期時間）；也沒斷言 8 秒內回覆。屬 WO-T2／後續可補。
3. test_reconcile_and_music 用「3 個週期」上限（175），比 WO-T1 原文「一個對帳週期」寬鬆；這是 A4 指定，仍能抓到退化，但抓不到「慢一整輪」的情形。
4. 「斷線 ≥2 分鐘後重連觸發回家」(gate.py 212) 沒有自動測試；WO-T1 未要求，屬 WO-T2 實機項。
5. sleephook 的 10 秒等待只有編譯檢查（WO-T1 只算編譯），實際語意要 WO-T2 闔蓋實測。
6. gate.py 148/155/157：寬限期間 apply 提前返回不更新 last_apply，導致每輪都做 read_tv＋dumpsys（adb 流量偏多）；行為正確但耗損。
7. gate.py 267：`do_HEAD = do_GET` 會對 HEAD 回 JSON body（語意不佳；規格只要求非 POST → 405）。
8. gate.py 124-125：成功讀取不清 failed_since（由 monitor 213 清）；若成功讀取由 HTTP 觸發，monitor 之後才依舊值判 recovery，仍符合規格，但屬隱性耦合。
9. plist 5 硬編 `/opt/homebrew/bin/python3.12`；README 已寫需安裝 3.12，但機器升級 Python 時會無聲啟動失敗。
10. B1 讓「connect 不帶 -s」與 PLAN 原文「一律帶 -s」字面不符；建議在 PLAN 或 README 補一行，免 WO-T2 驗收時被當成缺陷。
