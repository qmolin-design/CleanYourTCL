PASS

### 審查結論總覽
本輪（第 3 輪審查，第 2 次退修後）針對 [gate.py](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py)、[test_gate.py](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py)、[README.md](file:///Users/example-owner/TV-wt-tvgate4/tvgate/README.md) 進行全量逐行比對與規格稽核。
- **原有 14 個測試**：無任何斷言被放寬或刪除，多處原先使用固定 `time.sleep` 的脆弱等待均已收緊改為確定性的條件輪詢（`wait_for`），並加補了 `GATE_LOCK` 與 `expire` 的嚴格斷言。
- **退修項目**：`WO-T3_fix2_prompt.txt` 之 1～5、以及 `WO-T3_fix1_prompt.txt` 之 A1～A8、B1～B5、C1～C5 全部落實無退步。
- **測試總數**：共計 39 個測試方法，覆蓋率完整，無依賴系統時間漂移或假通過漏洞。
- **安全與強健性**：無金鑰洩漏風險；前端網頁無 `innerHTML` XSS 注入點；各壞資料（state.json、sqlite DB、tokens.json、meta）均具備隔離防護，不會使服務當機或導致 `monitor` 執行緒中止。

---

### 一、WO-T3 完成標準第 1 項每個子項逐條對照

| 完成標準子項 | 規格要求重點 | 對應測試方法與位置 | 判定與防假通過查核 |
|---|---|---|---|
| **基礎要求** | 測試全過、無殘留子程序、`tvgate.log` 的 mtime 不變、原 14 測試不放寬 | [test_gate.py:84-156](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L84-L156)（`setUp`/`tearDown`/`stop`） | 完整。使用 `killpg` 確保清理無殘留；`TVGATE_LOG` 隔離在測試暫存目錄。 |
| **鑰匙管理** | 兩把各 open/lock 記錄正確 device；未知鑰匙、檔案不存在、壞檔、值非字串一律 401 且 log 沒新增；刪除一把不重啟即 401 | [test_gate.py:416-436](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L416-L436) `test_devices_and_revocation` | 完整。明確驗證了 401 時 `/log` 內容長度前後不變、刪除一把後即時 401。 |
| **log 種類** | 涵蓋 `expire`、`schedule`、`relock`、`away`、`grace`、`schedule-add`、`schedule-delete` | [test_gate.py:568-582](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L568-L582) `test_event_log_expiry_relock_sleep_grace`、[L437-456](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L437-L456) `test_schedule_validation_and_logs`、[L626-644](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L626-L644) `test_schedule_end_to_end` | 完整。7 種規格要求之 log action 均有具體觸發並斷言檢查。 |
| **calc 精確值** | 6 組數值：（a）1800/1200秒、（b）跨午夜 600/600秒、（c）同App多RESUMED+GATE_LOCK 1200秒、（d）無後續+last_fetch 2400秒/null為0、（e）4小時上限截斷 14400秒、（f）STARTUP 1800秒 | [test_gate.py:667-683](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L667-L683) `PureTest.test_calc_exact` | 完整。純函式注入，無讀取系統時鐘，6 組數值均做嚴格等值（`assertEqual`）驗證。 |
| **抓取與去重** | 假 adb usagestats 回測資；抓兩次不重複；忽略 UNKNOWN_TYPE 與 In-memory 後段 | [test_gate.py:490-529](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L490-L529) `test_usage_fetch_dedup_and_failure_isolated` | 完整。明確斷言第一次抓取 count=2、忽略 UNKNOWN 及 In-memory 之後的 Netflix；等待第二次抓取完成後斷言 DB `SELECT count(*)` 仍為 2。 |
| **真實樣本** | `usagestats_events_sample.txt` RESUMED ≥ 40 筆，YouTube/Netflix 前景秒數 > 0 且 ≤ 跨度 | [test_gate.py:684-691](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L684-L691) `PureTest.test_real_sample` | 完整。嚴格驗證筆數與跨度區間。 |
| **抓取不擋鎖定** | dumpsys 卡 20 秒時，/open 在 20 秒內 ok=True、/status 2 秒內回；dumpsys 失敗不改 tv 值、不觸發寬限 | [test_gate.py:530-539](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L530-L539) `test_usage_hang_does_not_block`、[L518-529](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L518-L529) | 完整。驗證計時上限（<20s 與 <2s）；並在 usagestats 失敗時斷言 tv 仍為 locked、未發出通知、log 無 grace。 |
| **併發請求** | 20 執行緒同時打 /usage、/log、/open、/status 各 5 次 → 無 500 | [test_gate.py:481-489](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L481-L489) `test_concurrent_endpoints` | 完整。ThreadPoolExecutor 20 workers 執行，全數斷言 status == 200。 |
| **DB 損壞隔離** | tvgate.db 寫入垃圾 → 服務啟動、/open 與 /lock 正常、/usage 回 503 | [test_gate.py:457-468](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L457-L468) `test_db_corrupt` | 完整。驗證 /open 與 /lock 仍回 ok=True，/usage、/log、/schedule 正確回 503 `{"error": "db"}`。 |
| **schedule_due** | 週一07:40邊界觸發1次/後續0次/週六0次/跨午夜週日23:59以週日計/跨兩天列出所有點 | [test_gate.py:791-801](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L791-L801) `PureTest.test_schedule_due` | 完整。純函式精準時間點斷言，跨日與週幾判定皆符合規格。 |
| **排程端到端** | 寬限中且前景 YouTube 照切；超過 600 秒不鎖且記 schedule-skipped；重開恢復 last_check 不回補 | [test_gate.py:603-625](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L603-L625) `test_schedule_locks_during_grace`、[L626-658](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L626-L658) `test_schedule_end_to_end`、[L692-716](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L692-L716) | 完整。包含 age=600（切斷）與 601（略過）邊界測試，重開機後斷言 skipped 數不重複累加。 |
| **回家觸發** | 電視失敗 3 分鐘恢復+前景 YouTube+未授權 → 照鎖不進寬限；Mac 離家 ≥2 分鐘回來+前景 YouTube → 進寬限 | [test_gate.py:583-602](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L583-L602) `test_network_return_and_tv_reconnect`、[L779-790](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L779-L790) | 完整。模擬電視重連直接鎖定無通知；模擬 `TVGATE_HOME_NET` 離家達門檻再返家成功觸發通知並維持開放。 |
| **端點輸入校驗** | /schedule/add 格式錯誤（非 JSON、time 錯、days 錯/重複、第21條）回 400；重複規則回既有 id；delete 不存在回 404；/usage range 錯回 400；DB 空回全 0 且 data_since=null | [test_gate.py:437-456](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L437-L456) `test_schedule_validation_and_logs`、[L540-549](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L540-L549) `test_empty_database_and_links` | 完整。測試涵蓋了 `days: []`、`[0]`、`[1,1]`、`time: "25:00"`、`"7:40"`、21 條上限、重複 ID 比對與 404。 |
| **網頁** | GET / 回 200、Cache-Control: no-store、不含鑰匙、含關鍵詞；HEAD / 無 body；GET /status 回 405 | [test_gate.py:469-480](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L469-L480) `test_web_page` | 完整。檢驗 Header、內容字串、金鑰過濾、HEAD 空 Body、非 GET/POST 405。 |
| **--links** | 每設備 2 行（.local 與 IP），預設 8790，不啟動服務 | [test_gate.py:550-567](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L550-L567) `test_empty_database_and_links` | 完整。驗證輸出格式 6 行（3 設備 × 2）、各自帶對應 token、結束後驗證 TCP socket 連不上（確認未起服務）。 |

---

### 二、原有 14 個測試的斷言比對（git diff main 查核）

經執行 `git -C /Users/example-owner/TV-wt-tvgate4 diff main -- tvgate/test_gate.py` 詳細核對：
1. `test_auth_and_validation`：完全保留，無修改。
2. `test_open_and_lock`：增加 `self.assertEqual(self.lock_events(), events + 1)` 與 `self.assertNotIn('expire', ...)`；原斷言全數保留。
3. `test_expiry`：增加 `wait_for(lambda: self.lock_events() == events + 1)` 與 `self.assertEqual(sum(x['action'] == 'expire' ...), 1)`；原斷言全數保留。
4. `test_reconcile_and_music`：完全保留，無修改。
5. `test_read_failure`：完全保留，無修改。
6. `test_hung_adb_status_responsive`：將原有固定的 `time.sleep(.3)` 收緊改為 `wait_for(lambda: any('list' in a for a in self.calls()))`，斷言時間上限 `< 20` 與 `/status` 逾時 2 秒均未放寬。
7. `test_concurrent_lock`：完全保留，無修改。
8. `test_hook_sleep_wake_and_grace`：將 3 處依賴時鐘的 `time.sleep(2 * float(self.env['TVGATE_INTERVAL']))` 改為確定性條件等待 `wait_for(lambda: sum('list' in a for a in self.calls()[count:]) >= 2)`，其餘所有斷言與時序比對全數保留。
9. `test_wake_foreground_failure`：同上，將固定 `time.sleep` 改為確定性條件等待，斷言無刪除。
10. `test_no_grace_without_wake`：完全保留，無修改。
11. `test_hook_restart`：完全保留，無修改。
12. `test_restart_and_corrupt_state`：完全保留，無修改。
13. `test_unwritable_open`：完全保留，無修改。
14. `test_sigterm_opens`：完全保留，無修改。

**結論**：原有 14 個測試的斷言**沒有任何一項被刪除或放寬**，修改部分全屬移除 flaky sleep 並收緊為確定性輪詢與加強正向斷言。

---

### 三、gate.py 規格遵循逐項查核

1. **calc 的段落結束規則**：
   - [gate.py:386-404](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L386-L404)：事件依 `(ts, kind == 'ACTIVITY_RESUMED')` 排序，同秒結束事件優先於開始事件；同 package 連續 RESUMED 不切段；結束事件包含不同 package 的 RESUMED、`SCREEN_NON_INTERACTIVE`、`DEVICE_SHUTDOWN`、`DEVICE_STARTUP`，以及「該 package 的 `GATE_LOCK`」（`kind == 'GATE_LOCK' and package == current`）。符合規格。
2. **4 小時上限與跨午夜切分**：
   - [gate.py:392](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L392)：`seconds = max(0, min(end, start + 14400, day_end) - max(start, day_start))`。單段若超過 14400 秒即行截斷；跨午夜時分別傳入當日與次日的 `[day_start, day_end)`，能精準切分跨日秒數。符合規格。
3. **min(now, last_fetch)**：
   - [gate.py:402-404](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L402-L404)：最後一段未結束時，若 `last_fetch is not None` 則計算至 `min(now, last_fetch)`；若為 null 則不延伸。符合規格。
4. **GATE_LOCK 事件**：
   - [gate.py:209-210](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L209-L210)：只在下達鎖定指令後、經 `read_tv()` 讀回確認 `tv == 'locked'` 時，才執行 `INSERT OR IGNORE INTO events ... ('GATE_LOCK', PACKAGE, '')`。符合規格。
5. **抓取執行緒不拿 adb_lock、不改鎖定狀態**：
   - [gate.py:407-438](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L407-L438)（`fetch_usage`）：使用獨立的 `fetch_lock`，不碰 `adb_lock`，不呼叫 `read_tv`，完全不修改 `tv`、`until`、`grace`、`failed_since`。失敗時僅記錄文字 log。符合規格。
6. **回家觸發條件（電視重連不給寬限）**：
   - [gate.py:258-261](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L258-L261)、[L320-321](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L320-L321)：僅有 (a) sleephook 送出 `wake`，或 (b) Mac 在家用網路離開 ≥ `TVGATE_AWAY_SECONDS`（預設 120 秒）後返回，才會呼叫 `arrival()` 設定 `grace = 'pending'`。
   - [gate.py:293-299](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L293-L299)：電視離線恢復時，僅清除 `failed_since` 並呼叫 `apply()` 對帳，未授權時直接鎖定，不給寬限。符合規格。
7. **schedule_due 用時間點日期判斷週幾、last_check 先存再處理、600 秒窗口**：
   - [gate.py:452-465](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L452-L465)：以各時間點實際產生的 `date.isoweekday()` 比對規則設定的星期。
   - [gate.py:263-268](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L263-L268)：主迴圈取出 `due_rules` 後，先將 `last_check = now` 透過 `save(until)` 寫入 state.json，之後才進入 `for rule, target in due_rules:`。
   - [gate.py:281-292](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L281-L292)：若 `now - target > 600`，記 `schedule-skipped`；600 秒內若在家則清授權並強制鎖定，若不在家則記 `schedule-skipped`（detail 註明「Mac 不在家」）。符合規格。
8. **鑰匙檔錯誤一律 401 且改檔即生效**：
   - [gate.py:500-508](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L500-L508)、[L589-592](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L589-L592)：每次請求重新讀取 `TOKEN` 檔案，若檔不存在、非 JSON、非 dict 或型別錯誤皆回傳 `{}` 並記錄文字 log，Handler 比對失敗一律回傳 401。改檔毋須重啟即刻生效。符合規格。
9. **sqlite 單一連線＋db_lock、DB 壞不影響鎖定**：
   - [gate.py:330-360](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L330-L360)：全域單一連線（`check_same_thread=False`），設定 `PRAGMA journal_mode=WAL`、`busy_timeout=5000`，所有操作由 `db_lock` 保護。若 DB 損壞或連線失敗，`database()` 返回 `None`，API 終端回傳 503 `{"error": "db"}`，核心鎖定控制流程完全不受影響。符合規格。
10. **GET／HEAD／POST 路徑規則**：
    - [gate.py:563-576](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L563-L576)：`GET /` 回 200 與 `Cache-Control: no-store`；`HEAD /` 無 body；其他 GET/HEAD 回 405；未知 POST 回 404；其他 HTTP Method 回 405。符合規格。
11. **網頁不含鑰匙、讀 hash 不清 hash**：
    - [gate.py:528](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L528)：`new URLSearchParams(location.hash.slice(1)).get('t')`，讀取後儲存至 `localStorage`，不更動或清空 `location.hash`。前端 HTML 模板不含金鑰。符合規格。
12. **--links 預設 8790 且印 IP 版**：
    - [gate.py:511-522](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L511-L522)、[L708-713](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L708-L713)：預設埠為 8790，每個設備印出 `.local` 與 `en0 IP` 兩行，執行完畢以 `sys.exit` 退出，不啟動 HTTP 伺服器。符合規格。

---

### 四、安全查核

1. **鑰匙不寫進任何 log**：
   - [gate.py:51-60](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L51-L60) `log()`：文字 log 僅記錄時間、動作、結果；DB log 表欄位為 `(ts_epoch, device, action, minutes, until, detail)`。
   - [gate.py:546-547](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L546-L547) HTTP log 僅記錄 request line，不記錄 Header（`X-Token` 不被印出）。
   - 鑰匙認證失敗僅記錄 `log('token', 'unauthorized')` 或 `log('token', 'invalid file')`。
   - [test_gate.py:386-397](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L386-L397) 專門測試確認文字 log 與 DB log 皆不含任何測試金鑰字串。
2. **網頁 innerHTML 與 XSS 注入查核**：
   - 經全檔搜尋，[gate.py](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py) 前端 JavaScript **完全沒有使用 `innerHTML` 或 `document.write`**。
   - 所有伺服器回傳之動態資料（設備名稱 `s.device`、log 時間與項目、排程規則文字、使用時間字串等）全數經由 `textContent` 或 `document.createTextNode` 賦值（[gate.py:530-536](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L530-L536)），杜絕 DOM XSS 漏洞。

---

### 五、第 3 輪退修專項查核（WO-T3_fix2 與 fix1）

#### 1. WO-T3_fix2_prompt.txt 項目（1～5）逐項確認

- **項目 1：state.json 防呆與 monitor 存活**
  - **回答**：做到。
  - **檔案與行號**：[gate.py:70-95](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L70-L95)（`load()` 針對非 dict、非數值、非有限數、超過 ±400 天獨立防呆歸為 None 並記錄 log）、[gate.py:248-302](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L248-L302)（`monitor()` 主迴圈單次例外全數被 `try...except Exception as e:` 捕捉並記錄，執行緒不中斷）；[test_gate.py:345-375](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L345-L375)（`test_invalid_state_and_schedule` 完整測試各種惡意 JSON 與排程驗證）、[test_gate.py:745-772](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L745-L772)。
- **項目 2：expire 僅記真實到期**
  - **回答**：做到。
  - **檔案與行號**：[gate.py:271-277](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L271-L277)（僅在主迴圈確認 `until is not None and until <= time.time()` 執行鎖定時記 `expire`；`/lock` 與排程切斷不產生 expire）、[gate.py:654-656](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L654-L656)（`/open` 覆蓋過期授權時先補記 1 筆 `expire`）；[test_gate.py:184](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L184)、[L198](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L198)、[L374](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L374)、[L376-385](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L376-L385)、[L644](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L644)。
- **項目 3：GATE_LOCK 正向測試**
  - **回答**：做到。
  - **檔案與行號**：[test_gate.py:178-180](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L178-L180)（`/lock` 成功後 GATE_LOCK 筆數 +1）、[test_gate.py:191-194](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L191-L194)（到期鎖 GATE_LOCK 筆數 +1）、[test_gate.py:637-641](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L637-L641)（排程切斷 GATE_LOCK 筆數 +1）、[test_gate.py:733-744](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L733-L744)（反向：讀回非 locked 時不寫入事件）。
- **項目 4：README 環境變數完整說明**
  - **回答**：做到。
  - **檔案與行號**：[README.md:65-78](file:///Users/example-owner/TV-wt-tvgate4/tvgate/README.md#L65-L78)（完整列出 `TVGATE_HOME`、`TVGATE_TOKEN`、`TVGATE_LOG`、`TVGATE_PORT`、`TVGATE_INTERVAL`、`TVGATE_HOOK`、`TVGATE_NOTIFY`、`TVGATE_HOME_NET`、`TVGATE_AWAY_SECONDS`、`TVGATE_FETCH_INTERVAL`、`TVGATE_CLOCK`、`ADB` 及各預設值）。
- **項目 5：紀錄金鑰遮蔽測試**
  - **回答**：做到。
  - **檔案與行號**：[test_gate.py:386-397](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L386-L397)（`test_logs_do_not_contain_keys` 測試文字 log 與 DB log 不含任何已知或未知鑰匙）。

---

#### 2. WO-T3_fix1_prompt.txt 項目（A1～A8、B1～B5、C1～C5）逐項確認

- **A1（去重筆數斷言）**：做到。[test_gate.py:508-516](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L508-L516)（等第二次抓取完成後以 `SELECT count(*) FROM events` 斷言筆數維持 2 筆）。
- **A2（dumpsys 失敗不影響鎖定）**：做到。[test_gate.py:518-529](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L518-L529)（模擬 usage_fail，斷言 tv 維持 locked、authorized=False、無通知且 log 無 grace）。
- **A3（--links 空閒埠與金鑰驗證）**：做到。[test_gate.py:553-567](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L553-L567)（動態綁定隨機 port，驗證 3 把鑰匙各產生 2 行，子程序結束後 socket 連線失敗確認未開服務）。
- **A4（避免午夜時鐘依賴）**：做到。[test_gate.py:492](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L492)、[L606](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L606)、[L628](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L628)、[L667-801](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L667-L801)（端到端使用固定中午時間，單元測試注入 fixed timestamp）。
- **A5（排程 600 秒邊界）**：做到。[test_gate.py:648-652](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L648-L652)、[test_gate.py:693-715](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L693-L715)（`PureTest.test_schedule_cancels_grace_and_skips_old` 覆蓋 age=10、600、601）。
- **A6（重開恢復 last_check 區分）**：做到。[test_gate.py:647-657](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L647-L657)（重開機後檢查 `schedule-skipped` 僅 1 筆，二次重開驗證 last_check 更新至 target+602 且 skipped 數不重複累加）。
- **A7（移除多餘 start/stop 與固定 sleep 改 wait_for）**：做到。[test_gate.py:231](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L231)、[L274](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L274)、[L281](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L281)、[L295](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L295)、[L312](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L312)、[L627-658](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L627-L658)。
- **A8（暫存目錄改回系統 tempfile）**：做到。[test_gate.py:15](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L15)、[L86](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L86)、[L747](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L747)（使用 `tempfile.TemporaryDirectory()`，不在 tvgate 下建立 tmp）。
- **B1（GATE_LOCK 讀回確認後寫入）**：做到。[gate.py:207-210](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L207-L210)。
- **B2（排程 600 秒內 Mac 不在家記 schedule-skipped）**：做到。[gate.py:291-292](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L291-L292)（`log('schedule-skipped', 'Mac 不在家', detail='Mac 不在家')`）。
- **B3（鑰匙檔空字串忽略）**：做到。[gate.py:505](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L505)（`{k: v for k, v in data.items() if v}`）。
- **B4（meta last_fetch 非數字視為 null 不得 500）**：做到。[gate.py:473-481](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L473-L481)；[test_gate.py:773-778](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L773-L778)。
- **B5（data_since 採 DB 最早事件時間）**：做到。[gate.py:497](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L497)（`display(min(row[0] for row in rows), ...)`）。
- **C1（CSS box-sizing / overflow-wrap / flex-wrap）**：做到。[gate.py:526](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L526)。
- **C2（前景時間 grid 固定欄寬對齊）**：做到。[gate.py:526](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L526)、[L533](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L533)（`.usage-row` 採 `grid-template-columns: 5em minmax(0,1fr) auto`）。
- **C3（排程顯示與核取方塊中文星期）**：做到。[gate.py:536-537](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L536-L537)（連續寫成「週一～週五」，不連續寫成「週一、三、五」，核取方塊標籤為「週一」等）。
- **C4（紀錄時間顯示 MM-DD HH:MM）**：做到。[gate.py:535](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L535)（`x.time.slice(5, 16)`）。
- **C5（test_web_page 斷言 box-sizing 與 flex-wrap）**：做到。[test_gate.py:475](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L475)。

---

### 六、壞資料存活路徑分析（健壯性稽核）

1. **`state.json` 損壞**：
   - 包含壞 JSON、非 dict、字串、布林值、陣列、無限大、`1e300` 或超出 ±400 天：[gate.py:74-95](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L74-L95) 於 `load()` 中均將異常欄位單獨重設為 `None` 並記 log，不影響服務啟動。
   - `save()` 寫入失敗（如磁碟滿、權限錯誤）：[gate.py:268-270](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L268-L270) 被 `try...except OSError:` 接住，不拋出未捕捉例外。
2. **`tvgate.db` 損壞**：
   - 寫入亂碼或檔案損毀時，[gate.py:343-347](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L343-L347) `init_db()` 捕捉 `sqlite3.Error` 將 `db` 設為 `None`，服務依然正常監聽啟動。
   - 核心鎖定邏輯（`/open`、`/lock`、`apply`、`read_tv`）完全不被 DB 阻斷；涉及 DB 查詢的端點（`/usage`、`/log`、`/schedule`）均安全回覆 503 `{"error": "db"}`。
3. **`tvgate.tokens.json` 損壞**：
   - 包含壞 JSON、非字串、檔案遺失、權限異常：[gate.py:506-508](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L506-L508) 捕捉 `(OSError, ValueError, UnicodeError)` 返回空字典 `{}`。所有 API 回應 401，伺服器不崩潰。
4. **`meta` 表資料異常**：
   - `last_fetch` 若為非數值字串、NaN、Inf 或超出時間戳範圍：[gate.py:476-481](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L476-L481) 分別被 `(KeyError, ValueError, TypeError)` 及 `(ValueError, OverflowError, OSError)` 捕捉，安全回退為 `None`，不引發 500。
5. **`monitor` 執行緒循環存活**：
   - [gate.py:300-301](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L300-L301)：主迴圈內部包含完整的 `try...except Exception as e:`，單次 iteration 的任何例外（包括時間注入異常、ADB 子程序問題、網路探測例外等）均被記錄為 `iteration failed: <ExceptionName>`，執行緒不終止，下一週期自動繼續運作。

---

### 七、非阻擋發現事項（Non-blocking Findings）

以下項目不違反規格且不影響審定通過，列為觀察與建議參考：

1. **`test_usage_fetch_dedup_and_failure_isolated` 去重斷言形式**：
   - 規格提及「抓兩次 /usage 數字不變」，而退修指示 A1 要求「用 `SELECT count(*) FROM events` 斷言筆數不變（不是只比分鐘數）」。
   - 目前 [test_gate.py:516-517](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L516-L517) 嚴格遵循 A1 指示斷言了 DB 筆數維持 2 筆，並呼叫 `/usage` 檢查 `data_since`。由於底層 DB 資料筆數未重複增加，上層計算之分鐘數必然不變，邏輯嚴密。
2. **外部竄改 DB events 或 schedule 內容之潛在例外**：
   - 若有外部程式直接往 `schedule.days` 寫入非 JSON 字串，[gate.py:449](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L449) 之 `json.loads(row[1])` 可能拋出 `JSONDecodeError`；在 `monitor` 執行緒中會被 [gate.py:300](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L300) 的例外捕捉保護，而在 HTTP `/schedule` 查詢時會回覆 500。正常運作下 `schedule.days` 皆由 gate.py 經 `json.dumps()` 寫入，無實質影響。
