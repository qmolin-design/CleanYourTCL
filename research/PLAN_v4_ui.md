# 時間管理 v4：管理網頁、使用時間統計、設備紀錄、排程切斷

下列固定排程／時間為合成測試案例；公開副本不含實際家庭行程。使用統計樣本已縮為合成事件。

狀態：APPROVED（2026-10-30 01:35，Opus）。挑戰：DEEPSEEK FLASH max（15 條）＋MUSE SPARK xhigh（15 條），裁定見文末。
建立在 PLAN_v3_service.md 之上：v3 規則（授權、對帳、休眠開放、回家寬限、失效表）不變，**只有「回家」的觸發條件改掉**（見下），其餘本檔只寫新增部分。

## Owner 裁定（10-30 01:10）
- 管理介面做網頁，Mac 與 iPhone 共用（iPhone 加到主畫面當 App）；之後「iPhone 接手管理」直接用同一頁。
- 排程只做「時間點切斷」：例如週一～五 07:40 → 清掉授權、立刻關閉並鎖住 YouTube（正在看也切）；之後家長仍可再授權。不做禁止時段。
- 設備：爸爸 iPhone、媽媽 iPhone、MacBook，各一把鑰匙；授權紀錄要記是哪個設備給的。
- 過渡期：v3 服務已暫停（01:10 bootout，YouTube 開放；plist 改名 .paused 防登入自啟），v4 驗收後再上線。

## 實機事實（01:12 查）
- 電視時區 Asia/Taipei（+0800），與 Mac 相同。
- `dumpsys usagestats` 的「Last 24 hour events」段落保留最近 24 小時事件，跨重開機保留；格式：`time="2024-01-15 12:00:00" type=ACTIVITY_RESUMED package=com.netflix.ninja class=...`。段落以標題行開始，到下一個「In-memory」標題結束。`tvgate/testdata/usagestats_events_sample.txt` 為 4 筆合成事件，用 YouTube 300 秒、Netflix 180 秒覆蓋 parser 與計算回歸。
- 8765 被 Hermes scope_proxy 占用，正式用 8790。

## v3 行為修正：「回家」觸發條件
- v3 的觸發「電視連續讀取失敗 ≥2 分鐘後重新讀成功」會被**電視拔插頭重開**觸發（TCL 開機把 YouTube 改回啟用 → 孩子開著 YouTube → 進寬限一直看），是繞過漏洞。
- 改為：只有 (a) sleephook 送 `wake`，或 (b) **Mac 自己**離開家用網路 ≥2 分鐘後回來，才算回家。判斷 Mac 在家用網路：UDP socket `connect(("192.0.2.69", 9))` 後 `getsockname()[0]` 以 `192.0.2.` 開頭（不送封包，只看路由）。電視斷線再連上＝一般對帳（沒授權就鎖，不給寬限）。

## 新增設計

### 設備鑰匙
- 鑰匙檔改為 `~/.secrets/tvgate.tokens.json`：`{"爸爸 iPhone": "<hex>", "媽媽 iPhone": "<hex>", "MacBook": "<hex>"}`，權限 600。`TVGATE_TOKEN` 指向這個檔。舊的單一 `tvgate.token` 不再讀。
- 每個請求讀一次鑰匙檔，以 `X-Token` 找出設備名稱：找不到、檔案不存在、壞掉、不是「字串→字串」的 dict → 一律 401（不是 500），文字 log 記一行（不記鑰匙內容）。改檔即生效，不必重啟（輪替：改檔刪掉那把 → `--links` → 重傳連結）。
- Mac 也用鑰匙，不開「本機免鑰匙」（避免瀏覽器跨站請求）。
- `python3 gate.py --links`：每個設備印兩行連結：`http://<LocalHostName>.local:8790/#t=<token>` 與 `http://<en0 IP>:8790/#t=<token>`（IP 版備援）。`--port` 可改；**程式預設埠改為 8790**。不啟動服務。

### 紀錄
- 文字 log 照舊（`log()` 一個函式同時寫文字 log 與 DB；DB 失敗只影響 DB，不影響文字 log 與鎖定控制）。文字 log 路徑可用 `TVGATE_LOG` 換（測試必設），預設 `~/Library/Logs/tvgate.log`。既有 log 文字不改名。
- DB：`$TVGATE_HOME/tvgate.db`。單一連線（`check_same_thread=False`）＋單一 `db_lock` 包住所有操作，`PRAGMA journal_mode=WAL`、`busy_timeout=5000`，`CREATE TABLE IF NOT EXISTS`。DB 打不開或損壞 → 服務照常啟動與鎖定，`/usage`、`/log`、`/schedule` 回 `{"error":"db"}`（HTTP 503）。
- 表 `log(ts_epoch, device, action, minutes, until, detail)`。記：`open`（設備、分鐘、到期）、`lock`（設備）、`expire`（系統）、`schedule`（排程）、`schedule-skipped`（排程，detail＝錯過的規則時間）、`relock`（系統：對帳發現沒授權卻是啟用而停回）、`away`（系統：睡前／停止時開放）、`grace`（系統：回家寬限開始）、`schedule-add`／`schedule-delete`（設備）。
- `/log` 固定回最近 30 筆，無參數。

### 使用時間（「前景時間」：App 在畫面最前面的時間，不等於真的在看）
- **抓取**：獨立背景執行緒，每 10 分鐘一次（Mac 喚醒、回家時立即一次）。用自己的鎖、自己的 adb 呼叫（15 秒上限），**不拿 adb_lock、不呼叫 read_tv、不改 failed_since 或任何鎖定狀態**。只取「Last 24 hour events」段落中 type 為 ACTIVITY_RESUMED（所有 package）、SCREEN_NON_INTERACTIVE、DEVICE_SHUTDOWN、DEVICE_STARTUP 的行，存入 `events(ts_epoch, type, package, class)`，`UNIQUE(ts_epoch, type, package, class)`。未知類型忽略不報錯。成功 → 記 `last_fetch`（存 DB）。失敗只寫文字 log。
- **gate 自己的鎖定也當事件**：每次 force-stop＋disable-user 成功，寫一筆 `events(now, 'GATE_LOCK', 'com.google.android.youtube.tv', '')`。
- **計算**：純函式 `calc(events, now, last_fetch, day_start, day_end)`，不讀系統時鐘。事件依時間排序（同秒時 RESUMED 排在結束類事件之後不影響結果）：
  - 「前景 App」＝最近一個 ACTIVITY_RESUMED 的 package。
  - 段落結束於：另一個 package 的 ACTIVITY_RESUMED、SCREEN_NON_INTERACTIVE、DEVICE_SHUTDOWN、DEVICE_STARTUP、或該 package 的 GATE_LOCK。同 package 連續 RESUMED（例如 ShellActivity→MainActivity）不切段、不重複算。
  - 最後一段沒有結束事件：算到 `min(now, last_fetch)`（`last_fetch` 是最後一次確認「之後還沒有結束事件」的時間；抓取失敗時就停在那裡，下次抓到結束事件會自動補正）；`last_fetch` 為 null → 不延伸。
  - 任一段最長 4 小時，超過截斷（防 HDMI 等沒有事件的情況無限延伸）。
  - 以秒計，切到 `[day_start, day_end)` 內；顯示時無條件捨去到分鐘。
- 統計的 App：YouTube、Netflix（com.netflix.ninja）、四季（tv.fourgtv.video）、Spotify（com.spotify.tv.android）、Disney+（com.disney.disneyplus）、HBO Max（com.wbd.stream）。其他 package 只用來結束段落，不顯示。
- 範圍：今天、本週（週一起）、本月（1 日起），本機時間。今天與本週另回每天分鐘數；本月只回總計。
- `events` 保留 40 天，每天清一次舊資料。
- 限制（頁面要寫）：Mac 離家超過 24 小時，中間電視事件會遺失而少算；HDMI 或沒有事件的情況最多多算到 4 小時；資料從服務上線才開始。

### 排程切斷
- 表 `schedule(id, days, time)`：days＝1（週一）…7（週日）的集合，time＝`HH:MM`（00:00～23:59，兩位數）。最多 20 條，超過 400。
- 判斷函式 `schedule_due(rules, prev_epoch, now_epoch) -> [(rule, target_epoch)]`：列出所有落在 `(prev, now]` 的規則時間點，**用每個時間點自己的日期判斷週幾**。
- 主迴圈：每次醒來先取 now → `due = schedule_due(rules, last_check, now)` → 先把 `last_check=now` 存進 state.json → 再處理 due：`now - target ≤ 600 秒` → 清掉授權、取消回家寬限、立刻 force-stop＋disable-user（不看前景、不給寬限）、log `schedule`；超過 600 秒（Mac 睡過頭、服務停過）→ 不切，log `schedule-skipped`。
- 服務第一次啟動（state.json 沒有 last_check）→ last_check＝now，不回補。
- 排程切斷之後，v3 的「Mac 睡前開放」照常（Owner Q1：Mac 不在就開放）；也就是排程只在 Mac 在家醒著時有效。寫進失效表。

### 網頁
- HTML、CSS、JS 全部內嵌在 gate.py，不引用外部網址；頁面本身不含任何秘密。`GET /` 回 200、`Cache-Control: no-store`；`HEAD /` 不回 body；其他路徑的 GET／HEAD 一律 405；未知路徑的 POST 404。
- 鑰匙：每次載入先讀 `location.hash` 的 `t=`（**不清掉 hash**，讓書籤與主畫面捷徑保留完整連結），讀到就同時存 localStorage；沒有 hash 才讀 localStorage；都沒有 → 顯示「請用設備連結開啟」。
- 頁面（繁體中文，手機優先、大按鈕）：
  1. 狀態：已授權到 HH:MM／未授權；電視：已開放／已鎖／無法確認；最後確認時間。每 15 秒更新。
  2. 按鈕：開放 15／30／60 分、立即鎖；按完顯示結果文字（成功／電視沒回應＋note）。
  3. 前景時間：今天／本週／本月分頁；每個 App 一條橫條＋分鐘；今天與本週列每天合計。固定顯示「資料起點」「最後抓取時間」與限制說明一行。
  4. 紀錄：最近 30 筆（時間、設備、動作、分鐘、到期），動作用中文。
  5. 排程：列出規則可刪除；新增＝勾週幾＋`<input type="time">`。
  - 頁尾：目前設備名稱。

### 端點（全部 POST＋X-Token；body 一律 JSON 或空）
- `/status`：多回 `device`。
- `/usage?range=day|week|month`：`{"range","from":"YYYY-MM-DD","apps":[{"name","minutes"}],"days":[{"date","minutes":{App:N}}]（month 為 []）,"data_since":"YYYY-MM-DD HH:MM"|null,"last_fetch":"YYYY-MM-DD HH:MM"|null}`；DB 空 → 200、全 0、null。range 錯 → 400。
- `/log`：`{"items":[...]}`。
- `/schedule`、`/schedule/add`（`{"days":[1,2,3,4,5],"time":"07:40"}`；非 JSON、days 空／重複／不在 1～7、time 格式錯、超過 20 條 → 400；相同規則已存在 → 回既有 id）、`/schedule/delete`（`{"id":N}`，不存在 → 404）。

## 失效表（新增列）
| 情況 | 結果 |
|---|---|
| 排程時間 Mac 睡著 | 不切；log `schedule-skipped`（Mac 休眠）。（2026-10-30 WO-T3 修正：休眠中一律不切，含 Power Nap 暗喚醒；原「10 分鐘內醒來仍會切」只適用服務沒跑） |
| 排程時間服務沒跑 | 不切；10 分鐘內恢復會補切，否則 log `schedule-skipped` |
| 排程切斷後 Mac 闔蓋 | 照 Q1 開放 YouTube（Mac 不在＝開放） |
| 電視拔插頭重開 | 一般對帳鎖回，**不給寬限** |
| 一把鑰匙外洩 | 那把有完整管理權；改鑰匙檔刪掉即失效（不必重啟） |

## 不做
推播通知（之後 iPhone 接手管理時再做）、外網、帳號系統、修改或刪除歷史紀錄、禁止時段、事後重算、其他 App 的鎖定、唯讀鑰匙。

## 工單

### WO-T3 施工（工人 TERRA high priority，Owner 指定）
- 改：`tvgate/gate.py`、`tvgate/test_gate.py`、`tvgate/README.md`；可新增 `tvgate/testdata/` 下的檔。不改 plist 與 sleephook.swift。
- 完成標準（Opus 審定時跑）：
  1. `cd tvgate && python3 test_gate.py` 全過、150 秒內、無殘留子程序；跑完 `~/Library/Logs/tvgate.log` 的 mtime 不變（測試全部用 `TVGATE_LOG`）。原有 14 個測試照樣過（鑰匙檔改格式後跟著改，斷言不得放寬）。新增測試至少：
     - **鑰匙**：兩把各 `/open`、`/lock` → `/log` 的 device 正確；未知鑰匙、鑰匙檔不存在、內容壞掉、值不是字串 → 401 且 log 沒新增、服務沒掛；改鑰匙檔刪掉一把 → 不重啟即 401。
     - **log 種類**：到期 → `expire`；排程 → `schedule`；對帳停回 → `relock`；sleep → `away`；wake＋前景 YouTube → `grace`；新增／刪除排程 → `schedule-add`／`schedule-delete`。
     - **calc 精確值**（直接呼叫函式，注入 now／last_fetch；某日 D）：
       - D 10:00 RESUMED YouTube、10:30 RESUMED Netflix、10:50 SCREEN_NON_INTERACTIVE → YouTube 1800 秒、Netflix 1200 秒。
       - D 23:50 RESUMED YouTube、D+1 00:10 RESUMED 桌面（com.gathin.tvhome）→ D 的 YouTube 600 秒、D+1 的 YouTube 600 秒。
       - 10:00 RESUMED YouTube（ShellActivity）＋10:00 RESUMED YouTube（MainActivity）、10:20 GATE_LOCK → YouTube 1200 秒（不重複）。
       - 10:00 RESUMED YouTube、無後續事件、last_fetch=10:40、now=12:00 → 2400 秒；last_fetch=null → 0。
       - 10:00 RESUMED YouTube、無後續、last_fetch=now=16:00 → 14400 秒（4 小時上限）。
       - 10:00 RESUMED YouTube、10:30 DEVICE_STARTUP → 1800 秒。
     - **抓取與去重**：假 adb 的 `dumpsys usagestats` 回測試資料；抓兩次 `/usage` 數字不變；含 UNKNOWN_TYPE_ 與「In-memory daily stats」之後段落的資料不被誤讀。
     - **合成樣本**：`testdata/usagestats_events_sample.txt` 解析出 3 筆 ACTIVITY_RESUMED；YouTube 精確 300 秒、Netflix 精確 180 秒，總秒數不超過樣本跨度。
     - **抓取不擋鎖定**：假 adb 的 dumpsys 卡 20 秒期間，`/open` 在 20 秒內 ok=true、`/status` 2 秒內回；dumpsys 失敗不改變 `/status` 的 tv 值、不觸發寬限。
     - **併發**：20 個執行緒同時打 `/usage`、`/log`、`/open`、`/status` 各 5 次 → 沒有 500。
     - **DB 損壞**：`tvgate.db` 寫成垃圾 → 服務啟動、`/open`／`/lock` 正常、`/usage` 503。
     - **schedule_due**：週一 07:40 規則：prev=週一 07:39:58、now=07:40:03 → 觸發 1 次；prev=07:40:03、now=07:40:08 → 不觸發；週六不觸發；週日 23:59 規則、prev=週日 23:59:58、now=週一 00:00:02 → 觸發（以週日判斷）；prev 與 now 相隔兩天 → 列出區間內所有時間點（主迴圈再依 600 秒規則決定 skipped）。
     - **排程端到端**（注入或縮短時鐘皆可，不得真等到整點）：觸發時即使在回家寬限中、前景是 YouTube 也照鎖；超過 600 秒 → 不鎖、log `schedule-skipped`；重開服務 → last_check 從 state.json 恢復、不回補。
     - **回家觸發**：電視讀取失敗 3 分鐘後恢復、前景 YouTube、沒授權 → 照鎖、不進寬限；`TVGATE_HOME_NET` 類環境變數模擬 Mac 離開家用網路 ≥2 分鐘後回來＋前景 YouTube → 進寬限。
     - **端點**：`/schedule/add` 錯誤輸入（非 JSON、time=25:00、7:40、days=[0]、[]、[1,1]、第 21 條）→ 400；重複規則回既有 id；`/schedule/delete` 不存在 → 404；`/usage?range=year` → 400；DB 空 → `/usage` 200 全 0 且 data_since=null。
     - **網頁**：`GET /` 200、含 `Cache-Control: no-store`、內容不含任何鑰匙字串、含「前景時間」「紀錄」「排程」；`HEAD /` 無 body；`GET /status` 405。
     - **--links**：每個設備 2 行（.local 與 IP），埠 8790，不啟動 HTTP 服務。
  2. `git status --porcelain --untracked-files=all` 只有 tvgate/ 下的檔。
  3. Opus：用假 adb 起服務、curl 取 `/` 與各端點確認可用。
- 禁止：同 WO-T1（不碰真 adb／電視、不裝套件、不 launchctl、不讀寫 `~/Library/Application Support/tvgate`、`~/.secrets`、`~/Library/LaunchAgents`、`~/Library/Logs/tvgate.log`、不 commit）。

### WO-T4 上線（Opus＋Owner，不委派）
- 回滾準備：保留 v3 的 `tvgate.token` 與 v3 版 gate.py（git tag `tvgate-v3`）直到 WO-T4 通過；失敗 → bootout、checkout v3、YouTube 恢復開放。
- 安裝：產生 `tvgate.tokens.json`（3 把，600）；plist 改回 `.plist` 並 bootstrap；`--links` 產生連結；確認 8790 監聽、8765 仍是 Hermes；Mac 防火牆允許連入。
- Owner：Mac 開 MacBook 連結並加書籤；AirDrop 把爸爸／媽媽連結傳到兩支 iPhone，用 Safari 開、加入主畫面；從主畫面打開確認還認得設備。
- 驗收：
  1. 三個設備各按一次開放 15 分 → 紀錄顯示正確設備；立即鎖 → 紀錄有 lock。
  2. 沒有鑰匙的網址 → 顯示「請用設備連結開啟」；刪掉一把鑰匙 → 該手機 401、其他正常，再加回。
  3. 校準：Owner 記錄 YouTube 10 分鐘＋桌面閒置 10 分鐘＋Netflix 5 分鐘的起訖時間，與今日前景時間比對，誤差各 ≤ 3 分鐘。
  4. 播放中立即鎖後靜置 30 分鐘 → YouTube 今日分鐘數不增加。
  5. YouTube → 切 HDMI 10 分鐘 → 切回：記錄 YouTube 分鐘數變化（若 HDMI 切換沒有事件，記為已知誤差，不擋上線）。
  6. 新增 2 分鐘後的排程 → 準時切斷、紀錄 `schedule`；刪除後不再觸發；重開服務後排程仍在。
  7. 電視拔插頭重開、前景開 YouTube → 約 1～2 分鐘內被鎖（不給寬限）。
- v3 WO-T2 未做的實機項目（闔蓋、kickstart、手動啟用停回秒數）另列 WO-T2b，不擋 v4。

## 挑戰裁定（DSF＝DEEPSEEK FLASH max；MS＝MUSE SPARK xhigh；原文 research/challenge/PLAN_v4_DSF.md、PLAN_v4_MUSE.md）
| 意見 | 裁定 | 處理 |
|---|---|---|
| DSF C-01、MS C-01／C-02 段落沒有結束 | 採納 | 存所有 package 的 RESUMED；加 DEVICE_STARTUP、GATE_LOCK 結束；4 小時上限；WO-T4 HDMI 實測 |
| MS C-03 鎖定期間被算進 YouTube | 採納 | GATE_LOCK 事件結束段落；WO-T4 第 4 項 |
| MS C-04「算到 min(now,last_fetch)」寫錯 | 駁回 | last_fetch 是「最後確認仍無結束事件」的時間，延伸到那裡才正確；抓取失敗時不再延伸，下次抓到會補正 |
| DSF C-02 排程 Mac 睡著 | 部分採納 | last_check 存 state.json、600 秒窗口、錯過記 `schedule-skipped`；不做補切（Mac 不在本來就開放，Q1） |
| DSF C-03 排程被睡前開放與寬限抵銷 | 部分採納 | 睡前開放照 Q1 保留（寫進失效表）；**寬限觸發改成只有 wake 或 Mac 自己回家**，電視重開不再給寬限（修 v3 漏洞） |
| DSF C-04、MS C-13 calc 測試可假通過 | 採納 | calc 改純函式；WO 寫死 6 組精確值；真實樣本已重建為合成資料並改成精確秒數斷言；排程函式簽名釘死 |
| MS C-13④ 審定不應由 Opus | 駁回 | 外部審核員已有（GEMINI＋DSF）；Opus 審定是 team-dev 規定的機械閘門 |
| DSF C-05、MS C-06 sqlite 執行緒 | 採納 | 單一連線＋db_lock＋WAL＋busy_timeout；DB 壞不影響鎖定；併發測試 |
| DSF C-06、MS C-05 抓取擋鎖定、誤觸寬限 | 採納 | 獨立執行緒與鎖、不碰鎖定狀態；去重鍵加 class；卡住測試 |
| DSF C-07 --links 埠錯 | 採納 | 預設 8790、印 IP 備援 |
| DSF C-08 前景≠觀看 | 採納 | 改稱「前景時間」、顯示資料起點與最後抓取 |
| DSF C-09 localStorage | 部分採納 | 先讀 hash、不清 hash，再存 localStorage 備用 |
| DSF C-10、MS C-09 鑰匙錯誤處理與輪替 | 採納 | 讀檔錯一律 401；改檔即生效；README 寫輪替；不另做唯讀鑰匙 |
| DSF C-11 紀錄雙軌、不改名 | 採納 | log() 同時寫文字與 DB；既有文字不改名 |
| MS C-12 系統事件太多 | 駁回 | 都是真實動作、頻率低，Owner 要知道誰開的與為什麼被鎖 |
| DSF C-12 測試寫到真 log、介面未定 | 採納 | TVGATE_LOG；mtime 斷言；schedule_due 簽名與「用時間點日期判斷週幾」 |
| DSF C-13、MS C-14 缺的測試 | 採納 | 已併入 WO-T3（mutation 檢查不做，由審核員讀測試） |
| DSF C-14、MS C-15 WO-T4 缺項 | 採納 | 校準、鎖後靜置、HDMI、刪排程、拔插頭、鑰匙刪除、回滾；v3 欠債另開 WO-T2b |
| DSF C-15、MS C-11 過度設計 | 部分採納 | HTML 內嵌、/log 固定 30、本月只總計、排程上限 20；週／月保留（Owner 明確要求） |
| MS C-08 排程不補做沒寫進失效表 | 採納 | 已加 |
| MS C-10 GET 邊界 | 採納 | 方法×路徑寫明並測 |

第 2 輪挑戰：不進行。修改為收緊規格與補測試，唯一行為變更（寬限觸發）是修漏洞、方向更保守。
