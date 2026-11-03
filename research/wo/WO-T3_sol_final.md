已完成三檔修改：休眠狀態只存記憶體，暫停背景鎖定、到期處理及使用時間抓取；休眠排程記為 `schedule-skipped`／「Mac 休眠」。喚醒後恢復處理並保留 YouTube 寬限，SIGTERM 不變。README 已同步，其他測試斷言未放寬。

原競態測試已改成確認 monitor 跑過至少三個週期。新增測試：

- `test_sleep_expiry_waits_for_wake`
- `test_sleep_schedule_is_skipped`
- `test_sleep_pauses_usage_fetch`
- `test_wake_skips_schedule_crossed_while_asleep`
- `test_expiry_preserves_wake_grace`

指定五次測試指令的實際輸出：

```text
OK
OK
OK
OK
OK
```

`stat -f %m ~/Library/Logs/tvgate.log` 前後均為：

```text
1793293858
```

指定 Git status 指令實際輸出：

```text
 M tvgate/README.md
 M tvgate/gate.py
 M tvgate/test_gate.py
```

`git diff --check` 通過。未 commit、未執行真 adb 或連電視；Power Nap 實機行為仍待驗收。