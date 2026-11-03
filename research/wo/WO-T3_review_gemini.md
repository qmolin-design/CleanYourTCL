FAIL

### 任務 1：WO-T3 完成標準第 1 項子項逐條對照與審核

對照 [PLAN_v4_ui.md](file:///Users/example-owner/TV-wt-tvgate4/research/PLAN_v4_ui.md#L89-L109) 完成標準第 1 項之所有子項：

1. **基本與前置要求**
   - **測試執行與環境**：`cd tvgate && python3 test_gate.py` 全過、無殘留子程序。
   - **日誌隔離**：測試環境皆設定 `TVGATE_LOG` 指向測試暫存目錄（[test_gate.py:92](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L92)），子程序與模組匯入均未寫入 `~/Library/Logs/tvgate.log`。
   - **原有 14 個測試**：完全保留並全數通過，無放寬斷言（詳見任務 2）。

2. **新增測試子項逐項對照**
   - **鑰匙**：對應 [`test_devices_and_revocation`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L340-L356)。
     - 兩把鑰匙各 `/open`、`/lock` 驗證 `/log` 之 `device`（第 341–346 行）。
     - 未知鑰匙、不存在、格式損壞、值非字串均回 401 且 log 筆數不變、服務正常運作（第 347–353 行）。
     - 刪除一把鑰匙不重啟即 401（第 354–356 行）。
   - **log 種類**：對應 [`test_event_log_expiry_relock_sleep_grace`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L465-L479)、[`test_schedule_validation_and_logs`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L358-L375)、[`test_schedule_locks_during_grace`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L497-L518) 與 [`test_schedule_end_to_end`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L519-L548)。
     - `expire`、`schedule`、`relock`、`away`、`grace`、`schedule-add`、`schedule-delete` 均有明確斷言。
   - **calc 精確值**：對應 [`PureTest.test_calc_exact`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L557-L573)。
     - 6 組規格指定精確值（包含雙 App、跨午夜切分、同 App 連續 RESUMED＋GATE_LOCK、無後續算至 last_fetch／null、4 小時上限 14400 秒、DEVICE_STARTUP）皆以純函式精確比對數值，完全符合。
   - **抓取與去重**：對應 [`test_usage_fetch_dedup_and_failure_isolated`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L410-L431)。
     - 驗證假 adb 輸出、二次抓取數值不變、UNKNOWN_TYPE_ 與 In-memory 後續段落不寫入資料庫。
     - **存在可假通過漏洞**：見問題清單。
   - **真實樣本**：對應 [`PureTest.test_real_sample`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L574-L580)。
     - 斷言解析筆數 ≥ 40，YouTube 與 Netflix 前景秒數均 > 0 且 ≤ 跨度，完全符合。
   - **抓取不擋鎖定**：對應 [`test_usage_hang_does_not_block`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L432-L440) 與 [`test_usage_fetch_dedup_and_failure_isolated`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L425-L430)。
     - dumpsys 卡 20 秒時，`/open` 在 20 秒內成功、`/status` 在 2 秒內回覆。
     - dumpsys 失敗不改變 `/status` 的 tv 值。
     - **存在可假通過漏洞**：見問題清單。
   - **併發**：對應 [`test_concurrent_endpoints`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L401-L409)。
     - 20 個執行緒併發請求 4 個端點各 5 次，皆回 200，無 500。
   - **DB 損壞**：對應 [`test_db_corrupt`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L377-L388)。
     - 資料庫寫入垃圾資料時，服務啟動，`/open`、`/lock` 正常，`/usage`、`/log`、`/schedule` 皆回 503。
   - **schedule_due**：對應 [`PureTest.test_schedule_due`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L619-L629)。
     - 週一 07:40 窗口內觸發 1 次、窗口外不觸發、週六不觸發、週日 23:59 跨午夜正確判定週日、跨兩天列出 3 個時間點，完全符合。
   - **排程端到端**：對應 [`test_schedule_locks_during_grace`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L497-L518)、[`test_schedule_end_to_end`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L519-L548) 與 [`PureTest.test_schedule_cancels_grace_and_skips_old`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L582-L606)。
     - 寬限期中照鎖、逾 600 秒記 `schedule-skipped`、重啟由 `state.json` 恢復且不回補。
   - **回家觸發**：對應 [`test_network_return_and_tv_reconnect`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L480-L496) 與 [`PureTest.test_three_minute_tv_failure_does_not_trigger_arrival`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L607-L618)。
     - 電視離線重連不給寬限；Mac 離家網 ≥2 分鐘後返家＋前景 YouTube 給寬限。
   - **端點**：對應 [`test_schedule_validation_and_logs`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L358-L375) 與 [`test_empty_database_and_links`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L442-L450)。
     - 格式錯誤、超額回 400，重複規則回舊 id，刪除不存在回 404，`range=year` 回 400，空 DB 回 200 全 0 與 null。
   - **網頁**：對應 [`test_web_page`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L389-L400)。
     - `GET /` 回 200 與 `Cache-Control: no-store`、不含鑰匙、含指定中文關鍵字；`HEAD /` 無 body；`GET /status` 回 405。
   - **--links**：對應 [`test_empty_database_and_links`](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L451-L463)。
     - 輸出 4 行（各 2 行 .local 與 IP）、埠號 8790、不啟動服務。
     - **存在可假通過漏洞**：見問題清單。

---

### 任務 2：原有 14 個測試的斷言檢查

比對 `git -C /Users/example-owner/TV-wt-tvgate4 diff main -- tvgate/test_gate.py`：
- 原有的 14 個測試函式主體（`test_auth_and_validation` 至 `test_sigterm_opens`，[test_gate.py:146-338](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L146-L338)）**完全沒有任何一行被修改、刪除或放寬**。
- `git diff` 僅在：
  1. `setUp` 與輔助函式 `request` 中配合 v4 多設備金鑰改為 JSON 格式，並將暫存目錄指定在 `dir=HERE`。
  2. 新增環境變數 `TVGATE_LOG` 與 `TVGATE_HOME_NET`。
  3. 檔案尾端追加新增測試案例。
- **結論**：原有 14 個測試的斷言**沒有任何被刪除或放寬**。

---

### 任務 3：`gate.py` 規格合規性審核

1. **`calc` 規則**（[gate.py:369-387](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L369-L387)）：
   - **段落結束**：同 package 連續 RESUMED 不切斷（`package != current` 才 close）；換 package、SCREEN_NON_INTERACTIVE、DEVICE_SHUTDOWN、DEVICE_STARTUP 或該 package 的 GATE_LOCK 皆正確結束段落。
   - **4 小時上限**：`min(end, start + 14400, day_end)` 精確截斷單段上限為 14400 秒。
   - **`min(now, last_fetch)`**：最後無結束事件之段落算至 `min(now, last_fetch)`；`last_fetch` 為 None 時不呼叫 close 不延伸。
   - **跨午夜切分**：`max(start, day_start)` 與 `min(..., day_end)` 正確將時間段切入 `[day_start, day_end)` 區間。
   - **純函式特性**：無讀取系統時鐘，依輸入參數計算。完全符合規格。
2. **`GATE_LOCK` 事件**（[gate.py:191-196](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L191-L196)）：
   - `am force-stop` 與 `pm disable-user` 均回傳 0 時，寫入 `(time.time(), 'GATE_LOCK', PACKAGE, '')` 至 `events` 表，完全符合規格。
3. **抓取執行緒隔離性**（[gate.py:390-422](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L390-L422)）：
   - 僅持有獨立的 `fetch_lock`，不獲取 `adb_lock`，不呼叫 `read_tv()`，不修改 `failed_since`、`tv`、`until` 或 `grace`，執行逾時設為 15 秒，完全符合規格。
4. **回家觸發條件**（[gate.py:218-230, 244-250, 303-304](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L218-L230)）：
   - 僅在 sleephook 收到 `wake` 或 Mac 離家網（UDP 探測非 `192.0.2.`）≥2 分鐘返回時呼叫 `arrival()`。
   - 電視斷線重連於 `monitor()` 中僅呼叫 `apply()`，不呼叫 `arrival()`，不給寬限。完全符合規格。
5. **排程機制**（[gate.py:251-275, 436-449](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L251-L275)）：
   - `schedule_due` 依時間點本身日期判斷 `isoweekday()`。
   - 主迴圈中 `last_check = now` 先存入 `state.json` 後才執行 `due_rules` 切斷。
   - 600 秒窗口內鎖定並記錄 `schedule`，超過 600 秒記錄 `schedule-skipped`。首次啟動無 `last_check` 設為 `now` 不回補。完全符合規格。
6. **鑰匙檔處理**（[gate.py:476-485, 564-568](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L476-L485)）：
   - 每次請求重新讀取 `tvgate.tokens.json`，改檔即生效。
   - 檔案不存在、格式損壞或型態非字串字典時均捕獲並回傳 401，不引發 500。
7. **SQLite 連線架構**（[gate.py:313-344](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L313-L344)）：
   - 單一連線、`check_same_thread=False`、`db_lock` 保護、WAL 模式、busy_timeout=5000。
   - DB 損壞時只影響 `/usage`、`/log`、`/schedule`（回 503），`/open` 與 `/lock` 之鎖定控制完全不受影響。完全符合規格。
8. **HTTP 方法與路徑規則**（[gate.py:539-614](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L539-L614)）：
   - `GET /` 回 200，`HEAD /` 無 body；其他 GET/HEAD 回 405；其他 HTTP method 回 405；未定義之 POST 路徑回 404。完全符合規格。
9. **網頁安全與行為**（[gate.py:501-514](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L501-L514)）：
   - 頁面內嵌無外部相依，無靜態金鑰；優先讀 `location.hash` 且不清除 hash，寫入並回退至 localStorage。完全符合規格。
10. **`--links` 行為**（[gate.py:487-498, 682-687](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L487-L498)）：
    - 預設埠為 8790，印出 `.local` 與 `en0` IP 兩行，直接退出程序不啟動 HTTP 服務。完全符合規格。

---

### 任務 4：安全審核

1. **金鑰洩漏風險**：
   - 審查 `gate.py` 所有 `log()` 呼叫點，僅記錄動作、設備名稱、時間、分鐘數與排程時間字串，**未將金鑰 token 寫入文字日誌或資料庫**。
2. **XSS / innerHTML 注入風險**：
   - 檢視 `gate.py` 內嵌網頁腳本（[gate.py:504-513](file:///Users/example-owner/TV-wt-tvgate4/tvgate/gate.py#L504-L513)），頁面**完全沒有使用 `innerHTML`**。
   - 所有伺服器資料（設備名 `s.device`、日誌明細 `x.device`、`x.detail` 等）均透過 `textContent` 或 `document.createTextNode` 進行文字節點賦值，無未跳脫插入問題。

---

### 問題清單

#### 阻擋項目（FAIL 理由：違反規格或測試可假通過）

1. **[tvgate/test_gate.py:462-463](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L462-L463)**
   - **問題**：測試 `--links`「不啟動 HTTP 服務」時，斷言連線失敗的目標埠號誤用了 `self.port`（先前測試起服後又被 `self.stop()` 關閉的暫時埠號），而非程式實際設定與預設的服務埠 `8790`。
   - **為什麼違反規格**：違反 WO-T3 完成標準第 1 項「`--links`：... 不啟動 HTTP 服務」的驗證標準。此寫法構成**測試假通過**：因為 `self.port` 早在該測試第 451 行即已被 `self.stop()` 停止，對 `self.port` 進行 socket 連線必定拋出 `OSError`。即使 `gate.py --links` 發生迴歸而在預設埠 `8790` 上啟動了 HTTP 服務，此斷言仍會因檢查錯誤的已關閉埠號而誤判通過。

2. **[tvgate/test_gate.py:425-430](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L425-L430)**
   - **問題**：驗證「dumpsys 失敗不改變 /status 的 tv 值、不觸發寬限」時，測試環境的電視前景狀態檔 `(self.p/'front')` 維持預設值 `'0'`（非 YouTube 前景）。
   - **為什麼違反規格**：違反 WO-T3 完成標準第 1 項「dumpsys 失敗不改變 /status 的 tv 值、不觸發寬限」的精確狀態驗證標準。此寫法構成**測試假通過／未檢查完整狀態**：在 `front == '0'` 狀態下，無論是否有喚醒或抓取失敗，電視端都不是 YouTube，系統本就不會觸發 Mac 通知（`notified` 檔）。即便程式內部因 dumpsys 失敗而錯誤地進入了寬限流程（如誤改狀態或誤判為電視連續失敗），斷言 `self.assertFalse((self.p/'notified').exists())` 也會因為前景非 YouTube 而永遠成立，掩蓋了寬限邏輯可能的漏洞。

---

#### 非阻擋項目（建議改善）

1. **[tvgate/test_gate.py:546](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L546)**
   - **問題**：在 `test_schedule_end_to_end` 中，服務重開後使用固定真時鐘等待 `time.sleep(.6)` 來斷言 `schedule-skipped` 紀錄筆數未增加。
   - **說明**：測試環境的 `TVGATE_INTERVAL` 為 0.4 秒，在負載較重或排程較慢的環境下，0.6 秒可能不足以讓後台 `monitor()` 執行完畢，可能產生 flaky 情況。建議改用能明確標記主迴圈已完成一次檢查的同步機制（如狀態檔或條件輪詢）。

2. **[tvgate/test_gate.py:420, 428](file:///Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py#L420)**
   - **問題**：在 `test_usage_fetch_dedup_and_failure_isolated` 中，透過 `self.event('wake')` 觸發背景抓取後，以 `time.sleep(.5)` 等待背景執行緒完成。
   - **說明**：仰賴固定 0.5 秒真時鐘等待背景執行緒，若系統資源緊繃可能因背景執行緒尚未完成寫入而造成偶發性失敗。
