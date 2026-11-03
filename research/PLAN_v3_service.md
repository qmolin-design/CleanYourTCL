# 時間管理服務 v3（Mac 版）：取代 PLAN.md §2 運作方式／前置第 4 點／步驟 4

狀態：APPROVED（TERRA 第 1 輪 20 條已裁定，見文末；Owner Q5 已裁定；2026-10-30，Opus）。依據：PLAN.md v2 §2＋挑戰第 1 輪裁定、步驟 2 閘門結果（research/batches/gate/NOTE.txt）、Owner 10-29 23:43 裁定。
未改的部分照 PLAN.md v2（入口表、token、逐一處理、安全寫入）。

## 閘門結果（步驟 2，實機 10-29 23:48–00:10）
- 停用 `com.google.android.youtube.tv` 後：CleanYourTV 桌面圖示消失、T 鍵無反應、語音無反應；Netflix／四季／Spotify 正常。
- 遙控器沒有 YouTube 鍵；電視沒有瀏覽器、沒有 YouTube Kids、只有 user 0 → 這些入口不適用。
- iPhone 投放：家中不用（Owner）；**未測，Owner 接受**，不算擋住。其他 App 的 YouTube 連結、第三方播放器：未測（本機無第三方播放器）。
- 設定 → 應用程式 → YouTube 有「啟用」鈕，可手動解鎖 → Owner 接受，靠對帳停回。
- 播放中 `am force-stop`＋`pm disable-user`：2 秒內回桌面、無殘留。
- **重開機（1 次）：YouTube 被開機程序改回啟用**（其他 38 個停用不受影響）；adb 50 秒內自動重連。時序只有 1 次樣本，WO-T2 再測。
- 待機（8 分鐘，mWakefulness=Asleep）：adb 仍連著、重連第 1 次成功、狀態維持 → 「Mac 睡前電視待機」不會擋住開放指令；斷網或電視拔電仍會。

## 和 v2 不同的地方
| v2 | v3 | 原因 |
|---|---|---|
| Linux 小主機＋systemd | Owner 的 MacBook Pro＋launchd（`com.example.tvgate`，RunAtLoad＋KeepAlive） | Owner 20:15 裁定 |
| 連不上就維持最後狀態 | **Mac 不在（休眠、關機）＝盡力把 YouTube 開放** | Owner Q1：家人要看不能看比漏管更糟 |
| 未校時一律鎖 | 刪除，時鐘錯誤列入失效表 | Mac 有網路校時；完整校時監控過度設計 |
| 對帳 60 秒 | 30 秒，外加電視從離線變在線、Mac 喚醒時立即對帳 | 留餘裕讓「手動啟用 60 秒內停回」成立；電視重開會改回啟用 |

## 運作方式
**授權有效**＝ `到期時間 > 現在`。Mac 醒著時，電視應處於「授權有效就開、否則鎖」；Mac 要睡／服務要停之前，服務盡力把 YouTube 打開。

- **服務**：`tvgate/gate.py`，只用 Python 標準函式庫，`ThreadingHTTPServer` 監聽 `0.0.0.0:8790`，每個連線 15 秒逾時。
- **端點**（全部 POST；`X-Token` header 錯或缺 → 401，狀態不變；未知路徑 404、非 POST 405、`min` 缺／非 15|30|60 → 400）：
  - `/open?min=15|30|60`：寫入到期時間＝現在＋N 分（寫檔失敗 → 回錯誤、不啟用）→ 套用 → 讀回確認已啟用才回 `{"ok":true,"until":"HH:MM"}`；電視沒開成功回 `{"ok":false,"error":"...","until":"HH:MM","note":"授權仍有效，電視恢復連線後會自動開放；要取消請按立即鎖"}`。
  - `/lock`：清到期時間（寫檔失敗仍照鎖電視，回應附錯誤）→ 套用 → 讀回確認。
  - `/status`：`{"authorized":bool,"until":"HH:MM"|null,"tv":"locked|open|unknown","tv_checked_at":"HH:MM:SS"|null}`；`tv`／`tv_checked_at` 只來自最後一次**成功**讀取，讀不到就是 `unknown`。
- **到期時間**：存 `$TVGATE_HOME/state.json`（預設 `~/Library/Application Support/tvgate/`），寫暫存檔再 `os.replace`；不存在或壞掉＝沒有授權，壞掉時 log 記一行。
- **套用（唯一會下 adb 改狀態的地方）**：拿 adb 鎖 → 讀**當下**的「該開／該鎖」（不是呼叫者當初要的）→ 讀電視實際狀態 → 不同才下指令 → 讀回。改狀態的請求只更新到期時間（瞬間完成），再呼叫套用；所以慢的 `/open` 不會蓋掉較新的 `/lock`，到期與立即鎖不必排在舊動作後面重做。
- **主迴圈**：每 5 秒醒一次；跨過到期時間、離上次對帳滿 30 秒、或上次讀取失敗後本次重連成功 → 呼叫套用。
- **讀電視狀態**：`adb -s 192.0.2.69:5555 shell pm list packages -d --user 0`；exit 0 且輸出非空（本機恆有 30+ 個停用套件）才算讀成功；**整行等於** `package:com.google.android.youtube.tv` 才算鎖定（避免誤認 `youtube.tvmusic`）。其餘＝unknown。
- **adb**：一律帶 `-s 192.0.2.69:5555`；每次 subprocess 8 秒上限；失敗先 `adb connect` 再試一次；`$ADB` 環境變數可換掉 adb 執行檔（測試用）。鎖＝`am force-stop` 再 `pm disable-user --user 0`；開＝`pm enable --user 0`；兩者都以讀回結果判定成功，不看 exit code。
- **休眠／喚醒**：`tvgate/sleephook.swift`（swiftc 編譯）由 gate.py 以子程序啟動（路徑可由 `$TVGATE_HOOK` 換掉，測試用）。收到 `NSWorkspace.willSleepNotification` → stdout 印 `sleep`，等 stdin 一行回覆（最多 10 秒）才從 handler 返回；收到 `didWakeNotification` → 印 `wake`。
  - gate.py 收到 `sleep` → 啟用 YouTube（不改到期時間），8 秒內回 `ok` 或 `fail`；收到 `wake` → 進入「回家」處理（見下）。
- **回家處理**（Owner Q5，10-30）：觸發＝sleephook 送 `wake`，或電視連續讀取失敗 ≥2 分鐘後重新讀成功。若沒有授權、且電視前景是 YouTube（`dumpsys activity activities` 的 `mResumedActivity` 含 `com.google.android.youtube.tv`）→ **不切**：log 記一行＋Mac 跳通知（`osascript -e 'display notification "有人正在看 YouTube，要停請按立即鎖" with title "tvgate"'`），進入寬限；寬限中每次對帳若前景已不是 YouTube → 鎖；家長按 `/lock` 或 `/open` → 結束寬限、照新指令。寬限只由「回家」觸發，平常對帳發現孩子自己開的照鎖。前景不是 YouTube → 照常套用。
  - 子程序結束 → log 記一行並重啟它。
  - **盡力而為**：Mac 是否真的等 handler 返回才睡，WO-T2 實測；不保證。
- **SIGTERM**（Mac 關機、登出、`launchctl bootout`／`kickstart -k`）→ 盡力啟用 YouTube（最多 8 秒）→ 結束。`kickstart -k`／當機重啟後服務會依授權重新套用（沒授權就鎖回），中間可能短暫開放，接受。
- **log**：`~/Library/Logs/tvgate.log`，一行一個事件（時間、動作、結果）。
- **token**：`~/.secrets/tvgate.token`，權限 600，不進 repo。區網 HTTP 明文傳送，同網路的人可側錄；不對外網開放。
- **iPhone 捷徑**：開放 15／30／60、立即鎖、查狀態共 5 個；網址 `http://ExampleMacBook-Pro.local:8790/...`，「取得 URL 內容」POST、header 帶 `X-Token`。顯示文字分清「已授權到 HH:MM」與「電視已開放／電視沒回應」。
- 不做：外網操作（之後要再加 Tailscale）、iPhone 推播（Owner 要再說）、網頁介面、多台電視、時鐘監控。

## 失效時的行為（照實寫）
| 情況 | 結果 |
|---|---|
| Mac 闔蓋、電池閒置休眠 | 盡力在睡前開放；Mac 若不等就睡、或電視當下待機／斷網 → **停在鎖定，直到 Mac 回來** |
| Mac 正常關機／登出 | 同上，SIGTERM 時盡力開放 |
| Mac 突然沒電、當機、kill -9 | 停在最後狀態（通常是鎖）直到 Mac 回來 |
| Mac 醒來／回家 | 沒有授權：前景不是 YouTube 就鎖；正在看 YouTube → 不切、記 log、Mac 跳通知，等離開 YouTube 才鎖（Owner Q5）。孩子一直不離開就一直可看，直到家長按立即鎖 |
| 電視重開 | 開機程序把 YouTube 改回啟用；每 30 秒對帳＋重連即套用 → 實際幾分鐘內鎖回以 WO-T2 實測為準 |
| 孩子進設定按「啟用」 | 電視在線且 adb 正常時，目標 60 秒內停回（30 秒週期＋指令耗時）；離線時等重連 |
| 授權中電視斷網並跨過到期 | 斷線期間可看，重連後立即鎖 |
| `/open` 時電視沒回應 | 回失敗，但授權照記；電視恢復後會自動開放，除非按立即鎖 |
| Mac 時鐘錯 | 跳快提前鎖、跳慢多看；不另外防 |

## Owner 裁定（10-30 00:10）
- Q5：MacBook 在家不會睡（插電 sleep 0，實查）。Mac 回家時有人正在看 YouTube → 先記 log、跳通知（Mac；其他管理設備之後再說），不立刻切。

## 工單
### WO-T1 服務施工（工人 MUSE SPARK）
- 新增：`tvgate/gate.py`、`tvgate/sleephook.swift`、`tvgate/com.example.tvgate.plist`、`tvgate/test_gate.py`、`tvgate/README.md`（安裝、還原、捷徑設定）。不改其他檔。
- 完成標準（Opus 審定時跑）：
  1. `cd tvgate && python3 test_gate.py` 全過、exit 0、總時間 60 秒內、結束後沒有殘留子程序。測試要求：
     - 每個案例用新的暫存目錄當 `TVGATE_HOME`，自己產生 token；`ADB` 指向測試產生的假 adb 腳本（記錄每次收到的完整參數、依狀態檔模擬 `pm list packages -d` 輸出）；`TVGATE_HOOK` 指向測試產生的假 hook；起真的 gate.py 子程序、隨機埠，用 HTTP 打。對帳週期可用環境變數縮短。
     - 錯 token、缺 token → 401，假 adb 沒收到 enable／disable-user／force-stop；GET → 405；`min=7` → 400。
     - `/open?min=15` → 假 adb 收到 `-s 192.0.2.69:5555 shell pm enable --user 0 com.google.android.youtube.tv`，`/status` authorized=true、tv=open。
     - 到期時間設成約 1 秒後 → 10 秒內收到 force-stop 再 disable-user（順序檢查），tv=locked。
     - `/lock` → 同上順序，authorized=false。
     - 假 adb 狀態改成「啟用」但沒有授權 → 一個（縮短的）對帳週期內停回。
     - 假 adb 輸出含 `package:com.google.android.youtube.tvmusic` 但不含 youtube.tv → 判定為啟用（不誤認）。
     - 假 adb 回空輸出或 exit 1 → `/status` tv=unknown，`/open` 回 ok=false 且含 until。
     - 假 adb 卡住（sleep 超過上限）→ `/open` 在 20 秒內回 ok=false；同時 `/status` 仍能在 2 秒內回應。
     - 慢 `/open` 進行中送 `/lock` → 最終 tv=locked、authorized=false。
     - 假 hook 送 `sleep` → 收到 enable、假 hook 收到 `ok`；假 adb 故障時假 hook 收到 `fail`；送 `wake` 且假 adb 前景不是 YouTube → 鎖回；送 `wake` 且前景是 YouTube → 不鎖、log 有記錄、通知指令被呼叫（`TVGATE_NOTIFY` 可換成假指令），之後前景改成非 YouTube → 一個週期內鎖回；寬限中 `/lock` → 立即鎖。
     - 沒有 `wake`、平常對帳時前景是 YouTube 且沒授權 → 照鎖（寬限不外溢）。
     - 假 hook 自己結束 → gate.py 重新啟動它（log 有記錄）。
     - 重開 gate.py → 從 state.json 恢復到期時間；state.json 內容壞掉 → authorized=false；`TVGATE_HOME` 不可寫 → `/open` 回錯誤且沒有 enable。
     - 對 gate.py 送 SIGTERM → 收到 enable 後程序結束。
  2. `swiftc -o "$TMPDIR/sleephook" tvgate/sleephook.swift` 編譯成功（只算編譯檢查）。
  3. `plutil -lint tvgate/com.example.tvgate.plist` OK。
  4. `git status --porcelain` 只有 `tvgate/` 下的新檔。
- 禁止：不執行真的 adb、不連電視、不安裝套件、不 load launchd、不讀寫 `~/Library/Application Support/tvgate` 與 `~/.secrets`、不改 tvgate/ 以外的檔。

### WO-T2 上線與實機驗收（Opus＋Owner，不委派）
- 前置：記錄原始狀態（YouTube 啟用與否）；Owner 在 M9 設電視位址保留；確認電視 adb 授權。
- 安裝：產生 token（600）、編譯 sleephook 到固定位置、load launchd、macOS 防火牆允許連入（Owner 按允許）、iPhone 捷徑（Owner 照 README 做）。
- 驗收（記錄核准、到期、實際停止時間）：
  1. PLAN v2 步驟 4 第 1～5、7 項（含 iPhone 捷徑實際按 15 分鐘並看到到期時間、錯 token）。
  2. 斷線：v2 第 6(a)(b)(c) 項。
  3. 闔蓋 5 次（其中 1 次電視待機中、1 次 YouTube 播放中）：每次記錄電視上 YouTube 是否已開放；打開蓋子 → 沒授權就鎖回。
  4. 正常關機與登出各 1 次、`launchctl kickstart -k` 1 次：記錄最終電視狀態與到期時間。
  5. Mac 重開機後登入 → 服務自動啟動、log 有紀錄、iPhone 查狀態可用。
  6. 電視重開 3 次：記錄 adb 可用、YouTube 被改回啟用、再次停用的時間。
  7. 手動在設定按「啟用」→ 記錄停回秒數（目標 60 秒內）。
  8. 入口表逐項填：擋住／擋不住（列風險）／不適用／未測（Owner 接受）。
- 通過：1、2、5、7 全過；3、4、6 記錄實際結果，與失效表不符的回報 Owner。
- 還原：`launchctl bootout gui/$(id -u)/com.example.tvgate`，確認 gate.py 與 sleephook 都已結束，再把 YouTube 恢復成前置記錄的原始狀態。

## 挑戰第 1 輪裁定（TERRA high，20 條，原文 research/challenge/PLAN_v3_TERRA.md）
| 意見 | 裁定 | 處理 |
|---|---|---|
| C-01 睡前通知時序無保證 | 採納 | 改為盡力而為；WO-T2 闔蓋 5 次實測 |
| C-02 休眠時電視離線無法開放 | 採納 | 失效表明列；WO-T2 含電視待機中闔蓋 |
| C-03 SIGTERM 不保證完成 | 採納 | 改寫成盡力；WO-T2 測關機、登出、kickstart |
| C-04 KeepAlive 與停止即開放衝突 | 部分採納 | 當機重啟後鎖回是正確行為（Mac 還在）；短暫開放接受；WO-T2 記錄 |
| C-05 重開後改回啟用時序 | 採納 | 保留定期對帳；WO-T2 重開 3 次；不承諾秒數 |
| C-06 60 秒沒計入耗時 | 採納 | 對帳改 30 秒、adb 上限 8 秒；WO-T2 量停回秒數 |
| C-07 adb 成功定義不足 | 採納 | 一律 `-s`、以讀回判定 |
| C-08 查不到≠啟用、tvmusic 誤認 | 採納 | 整行比對、exit 0＋非空才算讀成功，否則 unknown；加測試 |
| C-09 立即鎖優先未落實 | 採納（簡化） | 套用時讀當下該開／該鎖；請求只改到期時間；加併發測試 |
| C-10 HTTP 阻塞與錯誤處理 | 採納 | ThreadingHTTPServer、連線逾時、400/404/405、卡住時 status 仍可回 |
| C-11 應該 vs 實際 | 採納 | 欄位改 authorized／tv（只取成功讀取）／tv_checked_at |
| C-12 失敗仍保留授權 | 採納 | 保留規則，回應明示；捷徑文字區分 |
| C-13 寫檔失敗未定義 | 採納 | open 失敗不啟用；lock 照鎖並回錯誤；加測試 |
| C-14 時鐘 | 部分採納 | 只列入失效表；不加時鐘測試（Mac 系統時間，過度設計） |
| C-15 sleephook 複雜度 | 部分採納 | 保留（Q1 必要）；加回覆逾時、子程序重啟、log；先在 WO-T2 驗證 |
| C-16 測試可假通過 | 採納 | 隔離 TVGATE_HOME、強制假 adb、驗完整參數與順序、git status 含未追蹤檔、總時間上限、審核讀測試碼 |
| C-17 sleep 測試沒走真管線 | 採納 | 用 `TVGATE_HOOK` 假 hook 走同一子程序管線，測 ok／fail／斷線 |
| C-18 啟動環境未驗收 | 採納 | WO-T2 第 5 項 |
| C-19 實機情境不足、還原寫死 | 採納 | WO-T2 補情境；還原依前置記錄 |
| C-20 明文 token、入口未測 | 採納 | token 600、明文風險寫明；未測入口標「未測（Owner 接受）」 |

第 2 輪挑戰：不進行。本輪全部是收緊規格與驗收，沒有新增元件；Owner 要求時再跑。
