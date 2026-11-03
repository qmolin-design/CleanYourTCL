# WO-G2 完成報告

- `tvagent/src/com/example/tvagent/AgentService.java`：加入九個套件的名單常數與單次 shell 檢查、設定、驗證；心跳記錄 `wl=`，並處理錯誤。
- `tvagent/src/com/example/tvagent/Whitelist.java`：純 Java 名單結果判斷。
- `tvagent/test/AdbClientTest.java`、`tvagent/test.sh`：加入並執行五種離線狀態測試。
- `tvagent/README.md`：說明名單、狀態、記憶體限制與還原指令。
- `tvagent/tvagent.apk`：重新建置。

`sh tvagent/test.sh` 成功，最後五行：

```text
PASS wl-set
PASS wl-fail
PASS wl-empty
PASS wl-garbage
ALL PASS
```

`sh tvagent/build.sh` 成功；APK 修改時間為 2026-10-30 21:08:35（台北時間）。使用原有 `debug.keystore`，建置前後 SHA-256 相同。編譯器提示既有程式使用已棄用 API，未阻止建置。

首次測試時 OrbStack 未啟動，啟動後重跑通過。未執行 adb 或操作電視，故尚未驗證新 APK 的實機行為；已在名單中的情況只檢查 tvagent，不校正其他成員。未修改 testdata、AdbClient 協定或 keystore，也未 commit、push。

## 退修 1

- `AgentService.java` 的三次 `dumpsys` 均明確加入 `2>/dev/null`，保留 `grep -q`。
- `Whitelist.java` 改以最後一個非空白行判斷結果；`AdbClientTest.java` 保留原有五個案例，新增 Broken pipe 訊息後接 `keep` 的案例。
- `sh tvagent/build.sh` 成功，重新產生 APK；沿用既有 `debug.keystore`，建置前後 SHA-256 相同。編譯器仍提示既有程式使用已棄用 API。
- `sh tvagent/test.sh` 最後七行：

```text
PASS wl-keep
PASS wl-set
PASS wl-fail
PASS wl-empty
PASS wl-garbage
PASS wl-broken-pipe
ALL PASS
```

未執行 adb 或操作電視，尚待實機驗證。
