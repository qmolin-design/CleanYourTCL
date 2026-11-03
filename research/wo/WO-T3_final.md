WO-T3 已施工完成；最後一輪 **31 項測試全部通過，耗時 63.667 秒**。測試前後正式文字日誌的修改時間相同，工作樹只有指定的三個 `tvgate/` 檔案變動。

- [gate.py](/Users/example-owner/TV-wt-tvgate4/tvgate/gate.py)：加入設備鑰匙、內嵌管理網頁、SQLite 紀錄與前景時間統計、排程及相關端點。修正回家觸發條件，保留 v3 的授權、對帳與睡眠開放行為。
- [test_gate.py](/Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py)：保留原有 14 項測試斷言，並加入 WO-T3 的計算、故障、並發、排程、網頁與設備鑰匙測試。測試使用假 adb 和隔離目錄，並檢查服務子程序是否清理。
- [README.md](/Users/example-owner/TV-wt-tvgate4/tvgate/README.md)：補上網頁使用、設備連結、鑰匙輪替、限制與還原步驟。捷徑說明改為使用各設備自己的 `X-Token`。

WO-T3 第 1 項的測試對應如下；前綴 `test_` 以下省略一次：

- 鑰匙：`devices_and_revocation`
- 紀錄種類：`event_log_expiry_relock_sleep_grace`、`schedule_end_to_end`、`schedule_validation_and_logs`
- 六組 `calc` 精確值：`calc_exact`
- 抓取、去重與忽略無關事件：`usage_fetch_dedup_and_failure_isolated`
- 真實樣本：`real_sample`
- 抓取不擋鎖定、失敗隔離：`usage_hang_does_not_block`、`usage_fetch_dedup_and_failure_isolated`
- 併發：`concurrent_endpoints`
- 資料庫損壞：`db_corrupt`
- `schedule_due`：`schedule_due`
- 排程端到端與寬限：`schedule_end_to_end`、`schedule_locks_during_grace`、`schedule_cancels_grace_and_skips_old`
- 回家觸發：`network_return_and_tv_reconnect`、`three_minute_tv_failure_does_not_trigger_arrival`
- 端點輸入與空資料庫：`schedule_validation_and_logs`、`empty_database_and_links`
- 網頁：`web_page`
- `--links`：`empty_database_and_links`

指定指令的最後一次實際輸出：

```text
$ cd /Users/example-owner/TV-wt-tvgate4/tvgate && python3 test_gate.py
test_auth_and_validation (__main__.GateTest.test_auth_and_validation) ... ok
test_concurrent_endpoints (__main__.GateTest.test_concurrent_endpoints) ... ok
test_concurrent_lock (__main__.GateTest.test_concurrent_lock) ... ok
test_db_corrupt (__main__.GateTest.test_db_corrupt) ... ok
test_devices_and_revocation (__main__.GateTest.test_devices_and_revocation) ... ok
test_empty_database_and_links (__main__.GateTest.test_empty_database_and_links) ... ok
test_event_log_expiry_relock_sleep_grace (__main__.GateTest.test_event_log_expiry_relock_sleep_grace) ... ok
test_expiry (__main__.GateTest.test_expiry) ... ok
test_hook_restart (__main__.GateTest.test_hook_restart) ... ok
test_hook_sleep_wake_and_grace (__main__.GateTest.test_hook_sleep_wake_and_grace) ... ok
test_hung_adb_status_responsive (__main__.GateTest.test_hung_adb_status_responsive) ... ok
test_network_return_and_tv_reconnect (__main__.GateTest.test_network_return_and_tv_reconnect) ... ok
test_no_grace_without_wake (__main__.GateTest.test_no_grace_without_wake) ... ok
test_open_and_lock (__main__.GateTest.test_open_and_lock) ... ok
test_read_failure (__main__.GateTest.test_read_failure) ... ok
test_reconcile_and_music (__main__.GateTest.test_reconcile_and_music) ... ok
test_restart_and_corrupt_state (__main__.GateTest.test_restart_and_corrupt_state) ... ok
test_schedule_end_to_end (__main__.GateTest.test_schedule_end_to_end) ... ok
test_schedule_locks_during_grace (__main__.GateTest.test_schedule_locks_during_grace) ... ok
test_schedule_validation_and_logs (__main__.GateTest.test_schedule_validation_and_logs) ... ok
test_sigterm_opens (__main__.GateTest.test_sigterm_opens) ... ok
test_unwritable_open (__main__.GateTest.test_unwritable_open) ... ok
test_usage_fetch_dedup_and_failure_isolated (__main__.GateTest.test_usage_fetch_dedup_and_failure_isolated) ... ok
test_usage_hang_does_not_block (__main__.GateTest.test_usage_hang_does_not_block) ... ok
test_wake_foreground_failure (__main__.GateTest.test_wake_foreground_failure) ... ok
test_web_page (__main__.GateTest.test_web_page) ... ok
test_calc_exact (__main__.PureTest.test_calc_exact) ... ok
test_real_sample (__main__.PureTest.test_real_sample) ... ok
test_schedule_cancels_grace_and_skips_old (__main__.PureTest.test_schedule_cancels_grace_and_skips_old) ... ok
test_schedule_due (__main__.PureTest.test_schedule_due) ... ok
test_three_minute_tv_failure_does_not_trigger_arrival (__main__.PureTest.test_three_minute_tv_failure_does_not_trigger_arrival) ... ok

----------------------------------------------------------------------
Ran 31 tests in 63.667s

OK
```

```text
$ stat -f %m ~/Library/Logs/tvgate.log
1793293858
```

測試前與測試後的輸出均為 `1793293858`。

```text
$ git -C /Users/example-owner/TV-wt-tvgate4 status --porcelain --untracked-files=all
 M tvgate/README.md
 M tvgate/gate.py
 M tvgate/test_gate.py
```

規格有一處時間矛盾：週日 **23:59** 排程早於範例的 `prev=23:59:58`，不落在明定的 `(prev, now]` 區間。實作遵照該區間規則；跨日測試使用 `prev=23:58:58`。

未執行真實 adb、連線電視或上線；實機行為仍須依 WO-T4 驗收。