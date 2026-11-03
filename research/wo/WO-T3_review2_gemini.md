PASS

---

### 一、退修清單逐項查驗（/Users/example-owner/TV/research/wo/WO-T3_fix1_prompt.txt）

#### A. 測試
- **A1. 去重斷言**：**做到**（[test_gate.py:445-448](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L445-L448)）
  等 calls 出現第 2 次 usagestats 且 last_fetch 更新後，以 `SELECT count(*) FROM events` 斷言筆數維持不變（2 筆）。
- **A2. dumpsys 失敗不影響鎖定**：**做到**（[test_gate.py:450-460](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L450-L460)）
  前景設為 YouTube、tv 為 locked、無授權；以 `TVGATE_FETCH_INTERVAL=1` 觸發 dumpsys usagestats 失敗（非 wake）；等 calls 出現抓取且 log 有 `usage fetch failed` 後，斷言 `/status` 的 tv 仍為 locked、authorized 為 False、無通知、`/log` 無 grace。
- **A3. --links 測試**：**做到**（[test_gate.py:485-499](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L485-L499)）
  綁定測試空閒埠傳入 `TVGATE_PORT`，subprocess 設 `timeout=5`，斷言產生 6 行連結（3 設備各 .local 與 IP 兩行，各自帶專屬 token）；結束後以 socket 連線斷言該埠無人監聽。
- **A4. 遠離午夜固定時間**：**做到**（[test_gate.py:424](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L424), [538](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L538), [560](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L560), [599-610](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L599-L610), [692-700](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L692-L700)）
  統計抓取使用 `2099-10-26 12:00`；排程端到端與寬限測試以 `TVGATE_CLOCK` 注入 `2026-10-26 12:00`；calc 與 schedule_due 皆為純函式使用固定時間點，不受實際執行時刻影響。
- **A5. 排程 600 秒邊界補測**：**做到**（[test_gate.py:621-643](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L621-L643)）
  `test_schedule_cancels_grace_and_skips_old` 針對 age 為 10、600、601 進行測試，精確斷言 age<=600 觸發鎖定與 `schedule`，age==601 記 `schedule-skipped` 且不鎖定。
- **A6. 重開不回補區分 last_check 恢復**：**做到**（[test_gate.py:574-585](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L574-L585)）
  重開前 last_check 設為 target-1，重開時時鐘注入 target+601，斷言記 1 筆 `schedule-skipped`；再次重開時鐘設 target+602，斷言筆數仍維持 1 筆（不重複跳過）。
- **A7. 移除多餘 start/stop 與固定 sleep**：**做到**（[test_gate.py:217](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L217), [260](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L260), [267](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L267), [281](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L281), [298](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L298), [559-586](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L559-L586)）
  原有測試中的 `time.sleep` 全部改為以 calls 計數為條件的 `wait_for`；`test_schedule_end_to_end` 精簡為必要步驟。
- **A8. 暫存目錄回歸系統暫存**：**做到**（[test_gate.py:86](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L86)）
  使用預設 `tempfile.TemporaryDirectory()`，不再在 tvgate/ 目錄下建立暫存。

#### B. gate.py
- **B1. GATE_LOCK 只在鎖定讀回成功後寫入**：**做到**（[gate.py:198-200](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L198-L200)）
  先執行 `read_tv(deadline)` 確認 tv 實際狀態，`if desired == 'locked' and tv == 'locked':` 才寫入 DB events。
- **B2. 排程時間點 600 秒內但 Mac 不在家**：**做到**（[gate.py:277-278](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L277-L278)）
  `elif at_home:` 切斷，`else:` 記 `schedule-skipped`，detail 明確標註 `Mac 不在家`。
- **B3. 鑰匙檔空字串項忽略、格式錯才 401**：**做到**（[gate.py:488-494](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L488-L494)）
  檢查必須為 dict 且鍵值皆為 str，透過 `{k: v for k, v in data.items() if v}` 忽略空字串值；整份解析失敗或非字串映射才記 invalid 並回傳空 dict（全部 401）。
- **B4. meta 非數值轉為 null 不得 500**：**做到**（[gate.py:459-467](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L459-L467)）
  對 float 轉換加上 `math.isfinite` 判斷與例外捕捉（KeyError、ValueError、TypeError、OverflowError），異常時設為 None。
- **B5. data_since 改為 DB 最早事件時間**：**做到**（[gate.py:483](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L483)）
  改為 `display(min(row[0] for row in rows), '%Y-%m-%d %H:%M') if rows else None`。

#### C. 網頁（375px 手機適配）
- **C1. box-sizing、flex-wrap、overflow-wrap**：**做到**（[gate.py:512](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L512)）
  全域宣告 `*{box-sizing:border-box;overflow-wrap:anywhere}`，section 設 `max-width:100%`，按鈕與星期容器設 `flex-wrap:wrap`。
- **C2. 前景時間 grid 對齊**：**做到**（[gate.py:512](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L512), [519](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L519)）
  `.usage-row{display:grid;grid-template-columns:5em minmax(0,1fr) auto;gap:8px;align-items:center}`，進度條起點齊一。
- **C3. 排程中文星期與勾選框**：**做到**（[gate.py:522-523](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L522-L523)）
  判斷連續天數呈現「週一～週五」，不連續呈現「週一、三、五」；勾選框標籤均使用「週一」～「週日」。
- **C4. 紀錄時間格式 MM-DD HH:MM**：**做到**（[gate.py:521](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L521)）
  前端顯示取 `x.time.slice(5, 16)`，截取月日時分。
- **C5. test_web_page 斷言 CSS 屬性**：**做到**（[test_gate.py:407](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L407)）
  測試中加入 `self.assertIn('box-sizing', page)` 與 `self.assertIn('flex-wrap', page)`。

---

### 二、WO-T3 完成標準第 1 項子項與測試對照

| 完成標準子項 | 對應測試函式 | 行號 | 檢查完整性與偽通過檢視 |
|---|---|---|---|
| (1) 鑰匙認證、多把鑰匙紀錄、改檔即生效、401 | `test_devices_and_revocation` | [test_gate.py:349-369](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L349-L369) | 完整驗證兩把鑰匙對應設備名稱、未授權時 log 不新增、空字串過濾、動態刪除免重啟即 401。無偽通過漏洞。 |
| (2) log 種類覆蓋（expire, schedule, relock, away, grace, schedule-add/del） | `test_event_log_expiry_relock_sleep_grace`<br>`test_schedule_validation_and_logs`<br>`test_schedule_locks_during_grace` | [test_gate.py:500-514](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L500-L514)<br>[385-386](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L385-L386)<br>[555](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L555) | 完整覆蓋規格要求的各類系統與設備 action。 |
| (3) calc 6 組精確值驗證 | `PureTest.test_calc_exact` | [test_gate.py:595-611](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L595-L611) | 6 組數值（1800/1200、跨午夜 600/600、同 App 連續 1200、無後續 2400/0、4 小時上限 14400、DEVICE_STARTUP 1800）全數精確斷言。 |
| (4) 抓取與去重、未知事件略過、In-memory 截斷 | `test_usage_fetch_dedup_and_failure_isolated` | [test_gate.py:423-449](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L423-L449) | 斷言 DB 事件筆數兩次抓取後不變，確認 UNKNOWN_TYPE 與 In-memory 區塊不被寫入。 |
| (5) 真實樣本 usagestats 解析與時間跨度 | `PureTest.test_real_sample` | [test_gate.py:612-619](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L612-L619) | 斷言 ACTIVITY_RESUMED 筆數 >= 40，YouTube 與 Netflix 秒數 > 0 且 <= 總跨度。 |
| (6) 抓取不擋鎖定、卡住 20 秒內完成、失敗不影響 tv | `test_usage_hang_does_not_block`<br>`test_usage_fetch_dedup_and_failure_isolated` | [test_gate.py:462-470](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L462-L470)<br>[450-461](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L450-L461) | 驗證 dumpsys 延遲 20 秒時 `/open` 正常返回、`/status` < 2 秒；dumpsys 失敗時 tv 保持 locked、不誤觸 grace。 |
| (7) 併發測試（20 執行緒打各端點） | `test_concurrent_endpoints` | [test_gate.py:413-421](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L413-L421) | 20 worker 同步呼叫各端點 5 次（共 400 次請求），斷言無 500。 |
| (8) DB 損壞容錯 | `test_db_corrupt` | [test_gate.py:389-400](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L389-L400) | 檔案填入垃圾字元，斷言服務正常起跑、open/lock 成功、資料庫相關端點回傳 503。 |
| (9) schedule_due 判定邊界與跨天計算 | `PureTest.test_schedule_due` | [test_gate.py:691-701](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L691-L701) | 週一、週六、週日 23:59 跨午夜判斷週日、相隔兩天列出 3 次觸發，皆精確斷言。 |
| (10) 排程端到端與 600 秒跳過 | `test_schedule_locks_during_grace`<br>`PureTest.test_schedule_cancels_grace_and_skips_old`<br>`test_schedule_end_to_end` | [test_gate.py:535-557](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L535-L557)<br>[620-644](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L620-L644)<br>[558-586](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L558-L586) | 寬限中照鎖、600 秒邊界測試、重開機讀取 last_check 且不重複跳過。 |
| (11) 回家觸發與電視重連無寬限 | `PureTest.test_three_minute_tv_failure_does_not_trigger_arrival`<br>`test_network_return_and_tv_reconnect` | [test_gate.py:680-690](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L680-L690)<br>[515-534](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L515-L534) | 電視斷線 3 分鐘恢復不觸發 arrival；Mac 離家網路滿門檻回來觸發寬限與通知。 |
| (12) 端點驗證與空 DB | `test_schedule_validation_and_logs`<br>`test_empty_database_and_links` | [test_gate.py:370-388](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L370-L388)<br>[472-480](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L472-L480) | 錯誤輸入 400（重複 days、time 超界、21 條限制）、刪除 404、空 DB 200 全 0 與 null。 |
| (13) 網頁 HEAD/GET 與 Cache-Control | `test_web_page` | [test_gate.py:401-412](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L401-L412) | 200, no-store, 無 token, HEAD 無 body, GET /status 405。 |
| (14) --links 輸出格式 | `test_empty_database_and_links` | [test_gate.py:481-499](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L481-L499) | 預設 8790 埠、輸出 6 行（.local 與 IP）、不啟動 HTTP 服務。 |

---

### 三、原有 14 個測試之斷言比對（git diff main 審查）

比對 `git -C /Users/example-owner/TV-wt-tvgate4 diff main -- tvgate/test_gate.py`，原有 14 個測試：
1. **無任何斷言被刪除**。
2. **無任何斷言被放寬**：
   - 假 adb 狀態比對、`assert_lock_sequence`（force-stop 接 disable-user 順序）、狀態碼與回傳 JSON 鍵值比對完全保留。
   - 舊有測試中依賴固定 `time.sleep` 等待對帳週期的寫法（如 [test_gate.py:217, 260, 267, 281, 298](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L217)），全數改為等待可觀察條件 `wait_for(lambda: sum('list' in a for a in self.calls()[count:]) >= 2)` 或 `any('list' in a ...)`，比對條件更嚴格且消除 flaky 風險。
   - Token 檔初始化配合 v4 格式改為 JSON 字串，認證邏輯與測試預期結果保持一致。

---

### 四、gate.py 規格遵循性稽核

1. **calc 段落計算與邊界**：
   - [gate.py:380](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L380)：排序使用 `(e[0], e[1] == 'ACTIVITY_RESUMED')`，同秒時非 RESUMED（結束事件）優先處理，符合規格。
   - [gate.py:382-387](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L382-L387)：同 package 連續 RESUMED 不切段；其他 package RESUMED 或 `SCREEN_NON_INTERACTIVE`、`DEVICE_SHUTDOWN`、`DEVICE_STARTUP` 或該 package 之 `GATE_LOCK` 結束段落。
   - [gate.py:378](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L378)：`seconds = max(0, min(end, start + 14400, day_end) - max(start, day_start))`，落實 4 小時（14400 秒）上限截斷與 `[day_start, day_end)` 跨午夜切分。
   - [gate.py:388-390](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L388-L390)：末段若無結束事件且 `last_fetch is not None`，算至 `min(now, last_fetch)`；為 null 則不延伸。
2. **GATE_LOCK 事件**：
   - [gate.py:198-200](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L198-L200)：執行 `am force-stop` 與 `pm disable-user` 後，再次呼叫 `read_tv()` 成功讀回且確認 `tv == 'locked'`，才寫入 `GATE_LOCK` 事件。
3. **抓取執行緒隔離性**：
   - [gate.py:393-431](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L393-L431)：`fetch_usage()` 使用獨立的 `fetch_lock` 與自有的 `dumpsys usagestats` 呼叫（上限 15 秒），不碰 `adb_lock`，不呼叫 `read_tv`，不改動 `failed_since`、`tv`、`until` 或鎖定狀態。
4. **回家觸發與寬限條件**：
   - [gate.py:245-251](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L245-L251), [306-307](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L306-L307)：`arrival()` 僅由 sleephook 的 `wake` 或 Mac 離開家用網路滿門檻（預設 120 秒）回來時觸發。
   - [gate.py:281-288](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L281-L288)：電視故障重連恢復（`had_failure`）僅呼叫一般對帳 `apply()`，不呼叫 `arrival()`，絕不誤給寬限。
5. **排程邏輯（schedule_due 與 600 秒窗口）**：
   - [gate.py:438-451](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L438-L451)：逐日遍歷並依每個時間點當日的 `date.isoweekday()` 比對。
   - [gate.py:255-257](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L255-L257)：主迴圈每次取得 `due_rules` 後，先將 `last_check = now` 存入 `state.json`，再行處理切斷或跳過。
   - [gate.py:267-278](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L267-L278)：若 `now - target > 600` 則記 `schedule-skipped`；若在 600 秒內但 Mac 不在家亦不切並記 `schedule-skipped`。
6. **鑰匙管理與安全機制**：
   - [gate.py:486-495](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L486-L495), [574-578](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L574-L578)：每次 POST 動態讀取鑰匙檔，以 `hmac.compare_digest` 安全比對；檔案不存在、格式異常均回 401；改檔即時生效。
7. **SQLite 單一連線與故障隔離**：
   - [gate.py:316-347](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L316-L347)：單一連線（`check_same_thread=False`）配搭 `db_lock`，開啟 `WAL` 與 `busy_timeout=5000`；DB 損壞時捕獲 `sqlite3.Error`，鎖定控制邏輯依然運作，端點回傳 503。
8. **HTTP 方法與路徑限制**：
   - [gate.py:549-563](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L549-L563)：僅 `GET /` 回傳 200，`HEAD /` 回傳 200 且無 body；其他路徑 GET/HEAD 一律 405；非指定路徑之 POST 回傳 404。
9. **網頁安全與 hash 處理**：
   - [gate.py:514](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L514)：前端讀取 `location.hash` 後儲存於 localStorage，**完全不清空 hash**。
10. **--links 參數**：
    - [gate.py:497-508](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L497-L508), [693-697](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L693-L697)：預設埠 8790，輸出本機 .local 與 en0 IP 兩版本，執行完畢即結束，不啟動服務。

---

### 五、安全審查

1. **Log 敏感資訊洩漏**：
   - [gate.py:55](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L55), [493](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L493), [533](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L533), [577](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L577), [643](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L643)：文字 log 與 DB log 均未記錄 raw token 內容；未授權時僅記 `unauthorized`；HTTP access log 僅記錄請求行，不包含 `X-Token` 標頭。
2. **XSS / innerHTML 檢查**：
   - 審查 [gate.py:511-524](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L511-L524)：前端腳本**完全沒有使用 `innerHTML`**。所有由伺服器回傳之設備名稱、log detail、App 名稱、狀態字串，均透過 `textContent` 或 `document.createTextNode()` 建立並以 `append()` 插入 DOM，徹底杜絕 XSS 風險。

---

### 六、非阻擋項目

- **[非阻擋] [tvgate/test_gate.py:445-449](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L445-L449)**：
  在 `test_usage_fetch_dedup_and_failure_isolated` 中，測試依照 Fix1 A1 要求，在第二次抓取後直接以 `SELECT count(*) FROM events` 斷言事件筆數維持不變（並隨後呼叫一次 `/usage` 檢查 `data_since`），未在第 1 次抓取後與第 2 次抓取後各呼叫一次 `/usage` 比對其 `minutes` 欄位。因 DB 事件資料筆數未變且包含唯一限制（UNIQUE），經純函式 calc 計算之分鐘數必然相同，不影響去重正確性，屬非阻擋項目。
