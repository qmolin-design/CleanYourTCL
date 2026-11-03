PASS

### 任務 1：逐項對照 WO-T3_sol_prompt.txt「要做」1～4

* **1.1：`sleep`（回 ok 或 fail）進入「休眠中」狀態**
  * **做到**：[`tvgate/gate.py#L329-L338`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L329-L338)。在呼叫 `apply(forced_open=True)` 之前即於 `state_lock` 內將 `sleeping = True`，不論後續 `apply()` 成功或失敗（回寫 `ok` 或 `fail`），休眠旗標均已確實建立。
* **1.2：休眠中 monitor 不對帳停回**
  * **做到**：[`tvgate/gate.py#L310-L316`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L310-L316) 與 [`tvgate/gate.py#L174-L175`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L174-L175)、[`tvgate/gate.py#L202-L203`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L202-L203)。`monitor()` 中以 `not sleeping` 阻絕週期對帳；且在 [`apply()`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L168) 內部兩處雙重檢查 `if sleeping and not forced_open: return False`，完全防止休眠中對帳停回。
* **1.3：休眠中 monitor 不到期鎖**
  * **做到**：[`tvgate/gate.py#L287-L292`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L287-L292)。`expired = not sleeping and until is not None and until <= time.time()`，休眠時 `expired` 恆為 `False`，不清除 `until`、不呼叫 `save(None)`、不記 `expire` log，亦不呼叫鎖定。
* **1.4：休眠中 monitor 不排程切斷**
  * **做到**：[`tvgate/gate.py#L296-L307`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L296-L307)。排程時間點落在休眠中直接進入 `if sleeping or target <= sleep_ended:` 分支，不執行清除授權與 `apply(force_lock=True)`。
* **1.5：休眠中不抓使用時間**
  * **做到**：[`tvgate/gate.py#L427-L429`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L427-L429)。[`fetch_usage()`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L426) 開頭在 `fetch_lock` 保護下檢查 `if sleeping: return`，直接返回，不執行 adb dumpsys。
* **1.6：排程落在休眠中記 schedule-skipped detail「Mac 休眠」**
  * **做到**：[`tvgate/gate.py#L296-L297`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L296-L297)。明確呼叫 `log('schedule-skipped', 'Mac 休眠', detail='Mac 休眠')`。
* **1.7：休眠中到期不鎖，wake 後才記 expire 並套用（前景 YouTube 走寬限）**
  * **做到**：[`tvgate/gate.py#L287-L292`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L287-L292)、[`tvgate/gate.py#L225-L240`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L225-L240)。休眠期間不處理到期；收到 `wake` 後 `arrival(waking=True)` 將 `sleeping = False`，若授權已過期則設定 `grace = 'pending'`；隨後 `apply()` 若判定前景為 YouTube 則進入寬限（`grace = 'watching'`）；`monitor()` 在下一個週期處理 `expired` 時將 `until = None` 並記 `log('expire', 'expired')`，diff 移除了舊有的 `grace = False`，完整保留了 wake 觸發的回家寬限。
* **1.8：wake 離開休眠**
  * **做到**：[`tvgate/gate.py#L229-L233`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L229-L233)。`arrival(waking=True)` 內更新 `sleep_ended = schedule_clock()` 並設置 `sleeping = False`。
* **1.9：SIGTERM 不變**
  * **做到**：[`tvgate/gate.py#L702-L713`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L702-L713)。`terminate` 處理與結束時呼叫 `apply(forced_open=True)` 行為維持原樣無變動。
* **1.10：休眠狀態只在記憶體不存檔**
  * **做到**：[`tvgate/gate.py#L40-L41`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L40-L41)。`sleeping` 與 `sleep_ended` 僅為模組變數，未納入 [`save()`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L100) 與 [`load()`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L72)，重啟即回到醒著狀態。
* **2.1：test_hook_sleep_wake_and_grace 改確定性（斷言前確定 monitor 跑過 ≥3 週期）**
  * **做到**：[`tvgate/test_gate.py#L152-L154`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/test_gate.py#L152-L154)、[`tvgate/test_gate.py#L274`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/test_gate.py#L274)、[`tvgate/test_gate.py#L296`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/test_gate.py#L296)。定義 `sleeping_cycles()` 透過等待 `state.json` 的 `last_check >= target`（至少 3 個 interval），並在 `test_hook_sleep_wake_and_grace` 的 ok 與 fail 兩段斷言前呼叫。
* **2.2：新增休眠中到期測試**
  * **做到**：[`tvgate/test_gate.py#L331-L348`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/test_gate.py#L331-L348)（整合測試 `test_sleep_expiry_waits_for_wake`）與 [`tvgate/test_gate.py#L835-L850`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/test_gate.py#L835-L850)（單元測試 `test_expiry_preserves_wake_grace`）。
* **2.3：新增休眠中排程測試**
  * **做到**：[`tvgate/test_gate.py#L350-L374`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/test_gate.py#L350-L374)（整合測試 `test_sleep_schedule_is_skipped`）與 [`tvgate/test_gate.py#L817-L833`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/test_gate.py#L817-L833)（單元測試 `test_wake_skips_schedule_crossed_while_asleep`）。
* **3：README 限制段補句**
  * **做到**：[`tvgate/README.md#L54`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/README.md#L54)。新增「- Mac 休眠（含 Power Nap 暗喚醒）期間服務不會鎖電視。」
* **4：其他測試斷言沒被刪除或放寬**
  * **做到**：[`tvgate/test_gate.py`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/test_gate.py)。`git diff` 顯示 `test_gate.py` 為 119 行新增、0 行刪除，既有斷言完全無刪除或放寬。

---

### 任務 2：競態與 API 行為審核

1. **休眠旗標的讀寫是否都在正確的鎖內？**
   * **正確**。
     * 寫入端：[`hooks()`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L321) 設 `sleeping = True` 在 `with state_lock:` 內（L330-L331）；[`arrival()`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L225) 設 `sleeping = False` 在 `with adb_lock: with state_lock:` 內（L228-L233）。
     * 關鍵讀取端：[`monitor()`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L264) 計算 `expired`（L287）在 `state_lock` 內；[`apply()`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L168) 在進入執行鎖定前（L201-L203）於 `state_lock` 內做 double check；其餘外層檢查（如 L174、L296、L310）作為 fast-path，因在 CPython 下讀取布林值為原子操作，且有內層鎖保證，無競態漏洞。
2. **sleep 與 monitor 同時進行時，monitor 是否可能在 sleep 處理到一半時鎖電視？**
   * **不可能**。
     * 若 `hooks()` 先設 `sleeping = True`，`apply()` 會搶 `adb_lock` 開啟電視；此時 `monitor()` 無論是在 L310 看到 `not sleeping` 為 `False`，或排隊進入 `apply()`，均會被 L174 或 L202 的 `if sleeping and not forced_open: return False` 攔阻，絕不會執行 force-stop 或 disable。
     * 若 `monitor()` 恰好已在執行 `apply()` 鎖電視，在它完成後 `hooks()` 的 `apply(forced_open=True)` 隨即接手執行 force-open，保證休眠前電視保持開啟。
3. **wake 與到期同時發生是否會漏記或重複記 expire？**
   * **不會**。
     * 清除 `until = None` 與寫入 `log('expire', 'expired')` 嚴格在 `monitor()` 內的 `with state_lock:`（L287-L292）原子進行，`arrival()` 完全不更動 `until`。
     * 一旦判定到期，`until` 立即被設為 `None`，後續週期不可能重複觸發；休眠期間 `until` 保持原值，wake 後必定在下一次 monitor 週期被抓住記一筆 expire，既不漏記也不重複記。
4. **API（網頁 open/lock、POST）在休眠中被呼叫時行為是否合理、不會崩潰？**
   * **合理且不崩潰**。
     * `/status`、`/usage`、`/log`、`/schedule` 等端點僅操作記憶體或 SQLite，完全不依賴休眠狀態，不崩潰。
     * `/open`：在休眠中更新 `until` 與 `state.json`，呼叫 `apply()` 因 `sleeping` 返回 `False`，API 回傳 HTTP 200 帶 `{"ok": false, "error": "電視未確認開放", "note": "授權仍有效..."}`，授權已存檔並待 Mac 喚醒時自動生效，符合 PLAN 規範。
     * `/lock`：在休眠中清空 `until`，`apply()` 返回 `False`，API 回傳 HTTP 200 `{"ok": false, "error": "電視未確認鎖定"}`，喚醒後對帳將立即鎖電視。全程無拋出未捕捉例外。

---

### 任務 3：測試確定性與 Flaky 分析

* **非假通過、非 Flaky**。
  * `sleeping_cycles()` 不是依賴無效的 `time.sleep`，而是監聽 `state.json` 中的 `last_check` 欄位（該欄位唯一由 `monitor()` 主迴圈逐圈寫入）。只有當 `monitor()` 確實完成 ≥3 個週期的排程與對帳檢查且更新磁碟狀態後，等待才會解除，百分之百確保測試斷言前 monitor 已經跑過。
  * `test_sleep_schedule_is_skipped` 注入了固定時鐘 `TVGATE_CLOCK`，完全排除系統真實時鐘微小漂移；且在時間跳轉後等待 `schedule-skipped` 出現才斷言，流程完全確定性。
  * 單元測試 `test_wake_skips_schedule_crossed_while_asleep` 與 `test_expiry_preserves_wake_grace` 透過 mock 精確注入邊界條件，完全不依賴時序。

---

### 任務 4：服務穩定性與崩潰路徑分析

* **無任何新路徑會讓 monitor 執行緒死掉或服務崩潰**。
  * `schedule_clock()` 完整捕捉 `(KeyError, OSError, ValueError)`，任何異常皆安全退回 `time.time()`。
  * `monitor()` 主迴圈內層包含頂層 `try ... except Exception as e:` 保護，單次迭代即使有未預期異常也不會讓執行緒終止。
  * 鎖的獲取順序嚴格維持 `adb_lock` 先於 `state_lock`，無死鎖風險。

---

### 問題清單

無阻擋問題（所有項目均符合工單 1～4 要求與設計規格，無違反規格、無假通過、無崩潰風險）。

* **[非阻擋] [`tvgate/gate.py#L296`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L296)、[`tvgate/gate.py#L310`](file:///Users/example-owner/TV-wt-tvgate5/tvgate/gate.py#L310)**
  * **細節說明**：`monitor()` 中的 `for rule, target in due_rules:` 與 `if not sleeping ...` 是在釋放 `state_lock` 後讀取 `sleeping`。此處在 CPython 下為原子讀取，且後續呼叫的 `apply()` 內部有在 `state_lock` 內進行 double check，因此不構成競態或邏輯問題。若未來需要完全嚴密的靜態檢查，可考慮將這兩處讀取一併包入前一個 `with state_lock:` 區塊中。
