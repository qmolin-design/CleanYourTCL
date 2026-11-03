FAIL

## 一、阻擋問題（規格違反／測試可假通過）

**1. `tvgate/test_gate.py:419-421` — 去重測試可假通過，且根本無法證明去重**
`self.event('wake'); time.sleep(.5)` 之後才比對 `/usage` 數字。若第二次 `dumpsys usagestats` 在 0.5 秒內沒跑完（或根本沒跑），斷言照樣成立，測試沒有等第二次抓取真的發生（沒有 `wait_for` 第二次 `usagestats` 出現在 `calls`）。
更嚴重：即使真的抓到兩次，這個斷言也不會變——`gate.py:377-384` 對同秒、同 package 的兩筆 RESUMED 會「不切段」，重複插入同批事件算出的秒數完全相同（測試樣本只有一段 YouTube、一個 SCREEN_NON_INTERACTIVE，複製貼上後總和一樣）。所以 WO 子項「抓取與去重」沒有任何有效證據；只要在 `events` 插兩份同資料仍會過。
（對照：`test_gate.py:423-424` 用 `count(*)` 查 Netflix/UNKNOWN 才是有效寫法，去重也該用 `count(*)`。）
違反：WO-T3 完成標準 1「抓取與去重」；屬「測試可假通過」。

**2. `tvgate/test_gate.py:425-430` — 「dumpsys 失敗不改變 tv、不觸發寬限」也可假通過**
先寫 `mode=usage_fail`，再 `self.event('wake'); time.sleep(.5)`，然後比對 `tv` 與 `notified`。整個檢查沒有任何證據證明「有一次失敗的 usagestats 抓取發生過」；失敗抓取沒跑，斷言一樣成立。而且 `wake→arrival→apply` 本身就會動 tv，這個比對還被喚醒流程干擾。
正確寫法：等到 `calls` 出現第二次 `usagestats`、且 `/log` 或文字 log 出現 `usage fetch failed`，再看 `tv`/`notified`。
違反：WO-T3「抓取不擋鎖定」後半（dumpsys 失敗不改變狀態、不觸發寬限）。

**3. `tvgate/test_gate.py:462-463` — `--links`「不啟動 HTTP 服務」的斷言檢查錯的埠**
`self.port` 是本次測試服務的隨機埠，且上面已 `self.port` 的服務早在 `test_gate.py:451` `self.stop()` 結束，所以 `create_connection` 一定失敗，與 `--links` 無關；`--links` 子程序實際帶的是 `TVGATE_PORT=8790`（`test_gate.py:455`）。因此「不啟動服務」這個子項完全沒有被驗到（若 `--links` 真在 8790 起服務，本測試仍會綠）。
違反：WO-T3「--links … 不啟動 HTTP 服務」。

## 二、WO-T3 完成標準第 1 項逐條對照

| 子項 | 對應測試 | 判定 |
|---|---|---|
| 全過／150 秒內／無殘留子程序／真 log mtime 不變 | 無測試斷言 150 秒；`tearDown` 用 `killpg` 驗殘留；`TVGATE_LOG` 全部隔離（`test_gate.py:96`） | 部分（需 Opus 實跑計時） |
| 原有 14 測試不放寬 | 見第三節 | PASS |
| 兩把鑰匙 `/open`、`/lock` → `/log` device 正確 | `test_devices_and_revocation`（`test_gate.py:341-346`） | PASS |
| 未知鑰匙／缺檔／壞掉／值非字串 → 401、log 沒新增、服務沒掛 | 同上 `348-357` | PASS（少驗「合法 JSON 但不是 dict／是 list」） |
| 刪一把鑰匙不重啟即 401 | 同上 `358-359` | PASS |
| log 種類 expire／schedule／relock／away／grace／schedule-add／schedule-delete | `test_event_log_expiry_relock_sleep_grace`、`test_schedule_locks_during_grace`、`test_schedule_end_to_end`、`test_schedule_validation_and_logs`、`test_hook_sleep_wake_and_grace` | PASS |
| calc 6 組精確值 | `PureTest.test_calc_exact`（`test_gate.py:561-572`），含跨午夜與 4 小時上限 | PASS |
| 抓取與去重 | `test_usage_fetch_dedup_and_failure_isolated` | **FAIL（阻擋 1）** |
| 真實樣本 ≥40 筆 RESUMED、YT/Netflix >0 且 ≤ 跨度 | `test_real_sample` | PASS（未讀 testdata，依測試通過與 PLAN 樣本描述） |
| 抓取不擋鎖定（卡 20 秒時 `/open` 20 秒內 ok、`/status` 2 秒內） | `test_usage_hang_does_not_block` | PASS |
| 抓取不擋鎖定（失敗不改 tv、不觸發寬限） | 同上第 425-430 行 | **FAIL（阻擋 2）** |
| 併發 20 執行緒 × 5 × 4 端點無 500 | `test_concurrent_endpoints` | PASS |
| DB 損壞 → 啟動、`/open`／`/lock` 正常、`/usage` 503 | `test_db_corrupt` | PASS |
| schedule_due 4 案（含週日用時間點日期、隔兩天列全部） | `PureTest.test_schedule_due` | PASS |
| 排程端到端（寬限中照鎖、>600 秒 skipped、重開恢復 last_check） | `test_schedule_locks_during_grace`、`test_schedule_end_to_end` | PASS（「不回補」斷言偏弱，見非阻擋 6） |
| 回家觸發（電視重連不給寬限、`TVGATE_HOME_NET` 回來給寬限） | `test_three_minute_tv_failure_does_not_trigger_arrival`、`test_network_return_and_tv_reconnect` | PASS |
| 端點錯誤輸入、重複規則、delete 404、range=year 400、DB 空全 0 | `test_schedule_validation_and_logs`、`test_empty_database_and_links` | PASS（month/week 形狀未測，非阻擋） |
| 網頁 200／no-store／不含鑰匙／含三詞、HEAD 無 body、GET `/status` 405 | `test_web_page` | PASS |
| `--links` 每設備 2 行、埠 8790、不啟服務 | `test_empty_database_and_links` | **FAIL（阻擋 3）** |

## 三、原有 14 個測試的斷言：沒有被刪除或放寬

- `git show main:tvgate/test_gate.py` 有 14 個 `def test_`；現行 31 個（新增 12 個 GateTest ＋ 5 個 PureTest）。
- `git diff main -- tvgate/test_gate.py`：307 insertions / 5 deletions，5 個刪除行全部是必要的非斷言改動：
  1. `TemporaryDirectory()` → `TemporaryDirectory(dir=HERE)`（`test_gate.py:79`）
  2. token 檔內容 `'test-secret'` → 兩把鑰匙的 JSON（`test_gate.py:85`）
  3. `self.env` 那行新增 `TVGATE_LOG`、`TVGATE_HOME_NET`（`test_gate.py:96-97`）
  4. `request()` 簽名多 `body=None`（`test_gate.py:125`）
  5. `Request(...)` 的 data 運算式（`test_gate.py:127`，`body=None` 時仍是 `b''`，行為等價）
- 原有測試的斷言內容、`assert_lock_sequence` 的完整參數比對、`TVGATE_INTERVAL` 上限都沒有變動。**無放寬**。

## 四、gate.py 規格逐項（任務 3）

全部符合，僅列關鍵證據：

- **calc 段落結束規則**：`gate.py:378-384` — 換 package 的 RESUMED、SCREEN_NON_INTERACTIVE、DEVICE_SHUTDOWN/STARTUP、該 package 的 GATE_LOCK 才切段；同 package 連續 RESUMED 不切。
- **4 小時上限**：`gate.py:375`（`start + 14400` 相對段落起點截斷）。
- **`min(now, last_fetch)`、last_fetch null 不延伸**：`gate.py:385-386`。
- **跨午夜切分**：由 `usage()` 逐日 `calc(..., lo, hi)`（`gate.py:461-471`）以日界裁切，測試 `test_calc_exact` 有對應精確值。
- **GATE_LOCK 事件**：`gate.py:195`（force-stop、disable-user 後寫入，`calc` 只在同 package 時採用）。
- **抓取執行緒不拿 adb_lock、不改鎖定狀態**：`fetch_usage` 只用自己的 `fetch_lock`、自己的 Popen、寫 DB 與文字 log，不碰 `tv/until/grace/failed_since`；抓取 600 秒一次、喚醒/回家由 `arrival()` 觸發 `fetch_now`。
- **回家觸發只有 wake 或 Mac 離開家用網路 ≥2 分鐘回來**：`gate.py:245-250`（只在 `away_since` 超過門檻時 `arrival()`）、`gate.py:304-305`（hook `wake`）；重連成功不再呼叫 `arrival`（`gate.py:278-284` 只做對帳）。`home_network()` 與規格一致（UDP `connect` 只看 `getsockname`，不送封包）。
- **schedule_due 用時間點日期判斷週幾**：`gate.py:436-450` 逐日 `date.isoweekday()`。
- **last_check 先存再處理、600 秒窗口**：`gate.py:253-256`（先存）→`gate.py:265-275`（`now - target > 600` → skipped，否則清授權＋`force_lock`）。
- **鑰匙檔錯誤一律 401、改檔即生效**：`gate.py:476-484` 每次請求重讀，任何 OSError/ValueError → `{}` → `do_POST` 401（不是 500），log 只記 `token` 動作不記內容。
- **sqlite 單一連線＋db_lock、WAL、busy_timeout**：`gate.py:317-330`；**DB 壞不影響鎖定**：`database()` 回 None、`log()` 文字照寫、`init_db` 失敗 `db=None`。
- **GET／HEAD／POST 路徑**：`gate.py:539-549`（`GET /` 200 no-store、`HEAD` 無 body、其他 GET/HEAD 405）、`gate.py:562-575, 645-646`（未知路徑 POST 404、`/usage` 400）。PUT/PATCH/OPTIONS 也 405。
- **網頁不含鑰匙、讀 hash 不清 hash**：`gate.py:504`（只讀 `location.hash`、`setItem`，沒有改寫 hash／history）。
- **`--links` 預設 8790、印 IP 版、不啟服務**：`gate.py:653-663` + `links()`。

## 五、安全（任務 4）

- **鑰匙不寫進 log**：`log()` 的呼叫點沒有任何一處帶 token；`do_POST` 401 只記 `token unauthorized`；`log_message` 只記 request line（token 在 header／fragment，不進 request line）。`--links` 印出 token 是規格要求的 stdout，不算 log。
- **innerHTML／XSS**：`gate.py` 全檔沒有 `innerHTML`、`insertAdjacentHTML`、`document.write`；裝置名、log 的 `device/detail`、`error`、`until` 全部走 `textContent`（`text()` 輔助函式，`gate.py:505-515`），伺服器資料未跳脫直接插 HTML 的路徑不存在。
- 小風險（非阻擋）：`Handler.log_message` 會把完整 request line 寫進文字 log，若有人手動把鑰匙放進 query string，就會落進 log（App 自己的連結用 `#t=`，不會送出）。

## 六、非阻擋問題

1. `tvgate/gate.py:195`：GATE_LOCK 以「兩個指令 exit code 0」判定成功，早於 `gate.py:199` 的讀回確認；v3 規定鎖定成功以讀回為準，若指令回 0 但實際沒鎖，統計會錯誤截斷 YouTube 段落。
2. `tvgate/test_gate.py:412`、`519-542`：用真時鐘算日期／分鐘。`test_usage_fetch_dedup` 若在 00:00–00:20 之間跑，20 分鐘事件會跨日而必定失敗（flaky 窗口）；`test_schedule_end_to_end` 也在換日邊界有同類風險。
3. `tvgate/test_gate.py:583`：600 秒邊界只測 10 與 601，`age == 600` 未測（mutant `>= 600` 或 `> 500` 仍會過）。
4. `tvgate/test_gate.py:546-547`：「重開不回補」用 `schedule-skipped == 1` 檢查，但若 `last_check` 根本沒被恢復（改為 now）也同樣是 1 筆，無法區分；恢復本身是靠 `527-530` 間接證明。
5. `tvgate/test_gate.py:533-536`：先 `start()` 立刻 `stop()`，沒有等待也沒有斷言，是多餘且會製造競態噪音的段落。
6. `tvgate/gate.py:266-268`：到期點在 600 秒內但 Mac 不在家用網路時，靜默不切、也不記 `schedule-skipped`；規格只定義「睡著／服務沒跑」要記 skipped，離家情境未定義（建議補記）。
7. `tvgate/gate.py:416`：40 天清理在每次抓取（10 分鐘）都跑，規格寫「每天清一次」（效果相同）。
8. `tvgate/gate.py:495-497`：`--links` 每行前面多了 `設備名: `，規格字面是「印兩行連結」；測試只做子字串比對，也沒驗每個設備拿到自己的那把鑰匙（同一把印三次也會過）。
9. `tvgate/gate.py:479`：任何一個值是空字串就讓整份鑰匙檔失效（全部設備 401），規格字面只說「不是字串→字串的 dict」才 401。
10. `tvgate/gate.py:562-566`：先驗 token 再看路徑，所以「未知路徑的 POST」若沒帶鑰匙會回 401 而非 404（規格未定順序）。
11. `tvgate/gate.py:457,473`：`meta` 的 `last_fetch`／`data_since` 若被寫成非數字，`float()` 會讓 `/usage` 變 500（非 DB 檔案層損壞）。
12. 測試暫存目錄改到 `dir=HERE`（`test_gate.py:79`）：跑測試時 repo 內會出現 `tvgate/tmpXXXX/`，本次審查期間就短暫看到 `?? tvgate/tmpsnfhkt4b/`；中斷的測試會留下殘留目錄（不影響 `git status` 的「只有 tvgate/ 檔」條件，但會讓人誤判）。
13. `/usage` 的 week／month 回應形狀（`month` 的 `days: []`、`from`）沒有測試；`days` 每天合計在頁面上是「所有 App 加總」，不是逐 App，符合規格字面但 Owner 看的時候要知道。
14. 150 秒總時間與「無殘留子程序」屬實際執行條件，本次唯讀審查未跑測試，需 Opus 實跑計時確認。
