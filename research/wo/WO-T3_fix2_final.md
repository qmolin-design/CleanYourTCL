已完成 WO-T3 第 2 次退修。修正後連跑兩次，**39 項測試皆通過**，分別耗時 86.233 秒及 86.413 秒；測試結束時的子程序與暫存目錄清理檢查也通過。

1. **狀態防呆**：[gate.py](/Users/example-owner/TV-wt-tvgate4/tvgate/gate.py:70) 逐欄檢查型別、有限數值及 ±400 天範圍；監控迴圈接住單次例外並繼續。測試：`test_invalid_state_and_schedule`、`test_state_fields_are_validated_independently`、`test_monitor_recovers_after_iteration_exception`。
2. **expire 紀錄**：[gate.py](/Users/example-owner/TV-wt-tvgate4/tvgate/gate.py:244) 依實際到期時間處理，`/open` 覆蓋已到期授權前先記錄。測試：`test_open_and_lock`、`test_schedule_end_to_end`、`test_expiry`、`test_open_overwrites_expired_once`。
3. **GATE_LOCK**：在 [test_gate.py](/Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py:172) 補上手動鎖、到期鎖及排程鎖的資料庫筆數斷言；保留 `test_lock_event_requires_readback` 反向測試。
4. **環境變數**：[README.md](/Users/example-owner/TV-wt-tvgate4/tvgate/README.md:65) 補齊程式讀取的變數及預設值。
5. **鑰匙不入 log**：[test_gate.py](/Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py:386) 的 `test_logs_do_not_contain_keys` 檢查文字 log 與資料庫 log。

`python3 test_gate.py` 兩次的測試進度輸出相同，均為：

```text
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
test_invalid_state_and_schedule (__main__.GateTest.test_invalid_state_and_schedule) ... ok
test_logs_do_not_contain_keys (__main__.GateTest.test_logs_do_not_contain_keys) ... ok
test_network_return_and_tv_reconnect (__main__.GateTest.test_network_return_and_tv_reconnect) ... ok
test_no_grace_without_wake (__main__.GateTest.test_no_grace_without_wake) ... ok
test_open_and_lock (__main__.GateTest.test_open_and_lock) ... ok
test_open_overwrites_expired_once (__main__.GateTest.test_open_overwrites_expired_once) ... ok
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
test_lock_event_requires_readback (__main__.PureTest.test_lock_event_requires_readback) ... ok
test_monitor_recovers_after_iteration_exception (__main__.PureTest.test_monitor_recovers_after_iteration_exception) ... ok
test_real_sample (__main__.PureTest.test_real_sample) ... ok
test_schedule_away_is_logged (__main__.PureTest.test_schedule_away_is_logged) ... ok
test_schedule_cancels_grace_and_skips_old (__main__.PureTest.test_schedule_cancels_grace_and_skips_old) ... ok
test_schedule_due (__main__.PureTest.test_schedule_due) ... ok
test_state_fields_are_validated_independently (__main__.PureTest.test_state_fields_are_validated_independently) ... ok
test_three_minute_tv_failure_does_not_trigger_arrival (__main__.PureTest.test_three_minute_tv_failure_does_not_trigger_arrival) ... ok
test_usage_bad_meta (__main__.PureTest.test_usage_bad_meta) ... ok
```

兩次各自的結尾：

```text
Ran 39 tests in 86.233s

OK
```

```text
Ran 39 tests in 86.413s

OK
```

`stat -f %m ~/Library/Logs/tvgate.log` 前後輸出相同：

```text
1793293858
```

`git -C /Users/example-owner/TV-wt-tvgate4 status --porcelain --untracked-files=all`：

```text
 M tvgate/README.md
 M tvgate/gate.py
 M tvgate/test_gate.py
```

依指示未執行真 adb、連電視或 commit；真實電視行為因此尚未實測。