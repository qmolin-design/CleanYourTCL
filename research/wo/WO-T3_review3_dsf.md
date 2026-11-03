PASS

（本輪未執行任何測試、未修改檔案；以下行號皆以目前 worktree 為準。）

## 一、WO-T3 完成標準第 1 項逐條對照

| 子項 | 對應測試 | 判定 |
|---|---|---|
| 鑰匙：兩把各 `/open`、`/lock` → `/log` device 正確 | test_gate.py:416-421（比對最新兩筆 `(action, device)` 精確等於 `[('lock',name),('open',name)]`） | 有，精確 |
| 未知鑰匙／檔不存在／內容壞掉／值非字串 → 401、log 沒新增、服務沒掛 | test_gate.py:423-429（三種壞檔＋`{'爸爸':4}`＋`wrong`；`:429` 比對 `/log` items 與之前完全相同，之後 `/status` 200） | 有 |
| 改檔刪掉一把 → 不重啟即 401 | test_gate.py:433-435 | 有 |
| log 種類：expire／schedule／relock／away／grace／schedule-add／schedule-delete | expire：:198（恰 1 筆）；schedule：:623、:639；relock＋away＋grace：:576；add/delete：:453-454 | 有 |
| calc 6 組精確值 | test_gate.py:667-683：1800/1200、跨午夜 600/600、Shell+Main+GATE_LOCK 1200、2400、null→0、14400、DEVICE_STARTUP 1800 | 有，全部寫死數值 |
| 抓取與去重（含 UNKNOWN、In-memory 之後不誤讀） | test_gate.py:490-517（:510 筆數=2、:511 netflix=0、:512 UNKNOWN=0、:513 等第二次 usagestats、:516 筆數不變） | 有（A1 要求的「比筆數不比分鐘」） |
| 真實樣本 ≥40、兩 App >0 且 ≤ 跨度 | test_gate.py:684-690 | 有（樣本檔內容依指示未讀） |
| 抓取不擋鎖定、dumpsys 失敗不改 tv／不觸發寬限 | test_gate.py:530-537（卡 20 秒，`/open` <20s、`/status` <2s）、:518-528 | 有 |
| 併發 20×5 四端點無 500 | test_gate.py:481-488（每個都 assertEqual 200） | 有 |
| DB 損壞 → 啟動、open/lock 正常、usage 503 | test_gate.py:457-467 | 有 |
| schedule_due 5 個案例 | test_gate.py:791-804 | 有 |
| 排程端到端（寬限中也切、>600 skipped、重開恢復不回補） | test_gate.py:603-624（寬限＋前景 YouTube 照鎖）、:626-657（601 skipped；:656-657 重開後仍 1 筆，可區分 last_check 有沒有恢復） | 有 |
| 回家觸發（重連不給寬限；Mac 離家回來才給） | test_gate.py:583-601、:322-327、:779-790 | 有 |
| 端點錯誤輸入 | test_gate.py:437-455、:540-548 | 有 |
| 網頁（200／no-store／無鑰匙／HEAD 無 body／GET 405） | test_gate.py:469-479 | 有 |
| --links（每設備 2 行、埠、不起服務） | test_gate.py:554-566 | 有；**預設 8790 未被斷言**（見非阻擋 1） |

沒有發現「靠真時鐘 sleep」的測試：全檔只剩 `wait_for` 內 0.05 秒輪詢（test_gate.py:76-81），5 處 `time.sleep(2*INTERVAL)` / `time.sleep(.3)` 都已改成等可觀察條件（見下）。

## 二、原有 14 個測試的斷言有沒有被放寬

沒有。`git diff main -- tvgate/test_gate.py` 的刪除行只有這幾類：
- 假 adb 分派改寫（新增 usagestats 分支、`arrival_open`）— gate.py:test_gate.py:47-55
- 鑰匙檔改格式（`test-secret` → JSON dict）— :90
- env 增加 `TVGATE_LOG`、`TVGATE_HOME_NET` — :99
- `request()` 增加 `body=` 參數 — :130
- 5 處固定 sleep → `wait_for`（比原本更嚴：先等到「真的跑了兩個對帳週期」才斷言）

14 個測試名一字未改（main 版 test_gate.py:135/150/160/170/184/196/207/221/271/288/295/300/311/321），完整 argv 比對（`assert_lock_sequence`）、`assertLess(...,20)`、狀態斷言全部保留；`test_open_and_lock`、`test_expiry` 還多加了 GATE_LOCK 與 expire 斷言。

## 三、gate.py 對規格逐項（皆符合）

- calc 段落結束：gate.py:394-401（同 package RESUMED 不切段；SCREEN_NON_INTERACTIVE／DEVICE_*／同 package GATE_LOCK 才結束）；同秒 tiebreaker :394
- 4 小時上限：:392 `min(end, start+14400, day_end)`
- `min(now,last_fetch)`：:402-403；`last_fetch=None` 不延伸
- 跨午夜切分：:392 夾到 `[day_start,day_end)`；每日視窗 :489-496
- GATE_LOCK：:209-211（**讀回 locked 才寫**，值為 `('GATE_LOCK', PACKAGE, '')`）
- 抓取不拿 adb_lock、不改鎖定狀態：:407-438 只有 `fetch_lock`＋自己的 adb（15 秒 :416），不呼叫 `read_tv`、不碰 `failed_since`；arrival 只 `fetch_now.set()` :226
- 回家觸發只有 wake／Mac 回來：:219-226、:256-261；v3 的「讀取失敗恢復→寬限」分支已移除（:293-299 只 read_tv＋apply）
- schedule_due 用時間點日期判週幾：:459-461
- last_check 先存再處理：:264-267（save 在 due 之前）
- 600 秒窗口：:281-292（>600 skipped；=600 會切；不在家記 skipped＋detail「Mac 不在家」）
- 鑰匙錯誤一律 401、改檔即生效：:500-508（每次請求重讀、空字串忽略 :505）、:588-592
- sqlite 單一連線＋db_lock：:334、:335、:353、:428、:616、:629；DB 壞不影響鎖定 :343-346、:350-351、:359-360
- GET/HEAD/POST：:563-573（`/` 200＋no-store、HEAD 無 body、其他路徑 405）、:574-576、:586-592、:637-638（未知 POST 404）
- 網頁不含鑰匙、不清 hash：:528（`hash||localStorage`，只寫不刪）
- --links 預設 8790＋IP 版：:511-522、:710

## 四、安全

- 鑰匙不進任何 log：`log()` 只寫 action/result/device/minutes/until/detail（:51-59）；401 只記 `'token' 'unauthorized'`（:591）；檔錯只記 `'token' 'invalid file'`（:507）；`log_message` 只記 request line（:546）。`--links` 印到 stdout 是設計行為。
- 網頁沒有 innerHTML：整個 PAGE（:525-538）只用 `textContent`（:530）與 `createElement`；設備名、log 欄位、排程都走同一條跳脫路徑，**沒有 XSS**。

## 五、fix2（本輪指定）1～5

1. **做到**：gate.py:70-90（非 dict／型別錯／非有限／±400 天 → 該欄位視為不存在＋log 一行，另一欄位不受影響）、:249-301（每輪 `except Exception` + log）；測試 test_gate.py:345-374（7 種壞內容＋`/open` 正常＋排程可觸發）、:745-761、:762-772。
2. **做到**：gate.py:271-277（只有主迴圈看到 `until<=now` 才 expire）、:654-656（`/open` 覆蓋前先補記一筆）；`/lock`（:644-656 走 `value=None`）、排程（:283-290）都不記 expire；測試 :183-184（lock 後兩週期無 expire）、:198（恰 1 筆）、:384、:644。
3. **做到**：gate.py:209-211；測試 :177-180（`/lock` +1）、:191-194（到期 +1，`wait_for(==+1)` 抓不到 +2）、:637-641（排程 +1）、:733-744（讀回非 locked → `db.assert_not_called()`）。
4. **做到**：README.md:65-78 列出程式實際讀的 12 個變數（HOME／TOKEN／LOG／PORT／INTERVAL／HOOK／NOTIFY／HOME_NET／AWAY_SECONDS／FETCH_INTERVAL／CLOCK／ADB），與 gate.py:21-30、230、247、251、443、679、710 一致，預設值都對。
5. **做到**：test_gate.py:386-396（文字 log 與 DB `log` 表都不含三把鑰匙）。

## 六、fix1 A1～A8、B1～B5、C1～C5 有沒有退步

A1 做到 test_gate.py:513-516｜A2 做到 :496（縮短抓取週期，不經 wake）＋:521-528｜A3 做到 :554-566（測試挑空埠、5 秒內結束、埠沒人聽、三把各自兩行；因 fix1 指定用注入埠，未驗預設 8790，見非阻擋 1）｜A4 做到（裸 sleep 已全移除；排程走 TVGATE_CLOCK :359-371／:603-624／:626-657；calc、schedule_due 走純函式 :667-683、:791-804；:360 仍用真 now 但以「分」為單位且週幾用目標日日期判斷，跨午夜安全）｜A5 做到 :692（`for age in (10,600,601)`）｜A6 做到 :645-657｜A7 做到 :76-81 與 :626-657（start/stop 都對應不同重開情境）｜A8 做到 :90。
B1 做到 gate.py:207-211｜B2 做到 :291-292｜B3 做到 :505｜B4 做到 :473-481（data_since 已改由事件推得）｜B5 做到 :497。
C1 做到 gate.py:526（`box-sizing`＋`overflow-wrap`＋`flex-wrap`＋`minmax(0,1fr)`＋`section{max-width:100%}`）｜C2 做到 :526（grid `5em minmax(0,1fr) auto`）｜C3 做到 :536-537（連續→「週一～週五」、不連續→「週一、三、五」、勾選框中文）｜C4 做到 :535（`slice(5,16)`）｜C5 做到 test_gate.py:475。

## 七、非阻擋（不構成 FAIL 理由，但建議處理）

1. **test_gate.py:557-564、558**：`--links` 一律用 `TVGATE_PORT` 注入埠，**沒有任何斷言檢查預設 8790**，也沒跑過 `--port` 旗標。實作目前正確（gate.py:521、:679、:710），但若預設回退成 8765（會撞 Hermes scope_proxy），39 個測試照樣全過。建議補一行（無 `TVGATE_PORT` 時斷言 `:8790/`，或直接斷言 argparse 預設值）；WO-T4 安裝步驟本來也會再確認 8790 監聽。因 fix1 A3 明文要求用測試挑的埠，故列非阻擋。
2. **gate.py:440-444（配合 :436）**：`fetch_loop` 沒有 try/except，`fetch_usage` 只擋 `OSError`。`communicate()` 在 `text=True` 下的 `UnicodeDecodeError`（或任何非 OSError 例外）會讓**抓取執行緒靜默死亡**——不再抓取、不寫 log、不會重啟。建議在 `fetch_loop` 包一層並記 log。
3. **gate.py:304-327**：`hooks()` 只擋 `OSError`；`arrival()`／`apply()`／`hook.stdin` 若拋非 OSError（例如 stdin 已關閉的 `ValueError`），睡眠／喚醒執行緒死亡 → 之後 sleep/wake 永久失效且無任何 log。這是「monitor 以外」的同類風險。
4. **gate.py:247（以及 :443）**：`float(os.environ[...])` 在 try 之外（:247 在 :248 的 while 之前）：`TVGATE_AWAY_SECONDS` 格式錯 → **monitor 執行緒一啟動就死**，fix2 第 1 項的「每輪例外都接住」救不到這種啟動期例外；`TVGATE_FETCH_INTERVAL` 同類。建議移到 try 內或啟動時驗證。
5. **gate.py:262（get_rules 在 :249 的 try 內，但在到期判斷 :271 之前）**：若 DB 的 `schedule.days` 是壞 JSON，`get_rules()` 每輪都在到期／對帳之前拋例外 → 執行緒活著但**到期清理、對帳、排程全部停擺**（每 5 秒一行 log）。這是資料列層級損壞，不在 test_db_corrupt（只有「整檔不是 DB」）覆蓋範圍。建議逐列 try，或把 get_rules 移到到期處理之後。
6. **gate.py:468-497、:602-605、:606-608**：DB 列型別污染（`events.ts_epoch` 為 NULL/TEXT、`log.ts_epoch` 非數值）→ `min()`／`display()` 拋 `TypeError`，`/usage`、`/log` 是「請求執行緒中斷」，不是規格要求的 503；`/schedule` 遇到壞 JSON 同理（server 本身不會死，monitor 由 :300 接住）。
7. **gate.py:595-596、:618、:631**：`reply()` 在持有 `state_lock`／`db_lock` 時寫 socket（連線逾時 15 秒 :544）。慢用戶端可讓鎖卡住十幾秒，期間 `/status`、`/open`、monitor 都在等。區網風險低，僅記錄。
8. **gate.py:203-204**：`first`／`second` 是未使用的死變數（成功與否本來就由讀回判定，符合 v3）。
9. **未測清單（都不在 WO 必測項目內）**：calc 的「同秒 RESUMED 排在結束類事件之後」tiebreaker（:394）沒有直接案例（Shell/Main 同秒同 package 不需要 tiebreaker）；「離家未滿門檻回來不給寬限」沒有反向測試；`events` 40 天清理（:432）；`/log` 30 筆上限；`/usage` 的 `days` 結構與 `from`。
10. **本輪無法驗證的宣稱**：檔案內確實有 **39 個 test**（GateTest 29＋PureTest 10），但審查指示禁止執行測試，所以「39 全過、150 秒內、無殘留子程序、真 log mtime 不變」我無法確認；另外提示中「已跑過 31 個」與實際 39 個不符（31 應是上一輪數字）。PureTest 匯入 gate 時 `LOG` 仍指向真實 `~/Library/Logs/tvgate.log`，目前沒有任何 PureTest 會走到 `log()`（皆 mock 或無 log 路徑），但只要未來有人在 monitor 路徑加一行 log，`test_three_minute_tv_failure_does_not_trigger_arrival` 就會寫到真 log、破壞 mtime 驗收——建議 PureTest 也設 `TVGATE_LOG`。
