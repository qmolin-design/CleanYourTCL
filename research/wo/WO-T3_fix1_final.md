已完成 WO-T3 第二次退修，兩輪完整測試均通過。修改位置如下：

**測試 A1～A8**：在 [test_gate.py](/Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py:422)。

- A1：等待第二次抓取及資料庫更新後，比對 `events` 總筆數。
- A2：以縮短抓取週期觸發失敗，確認假 adb 呼叫與失敗文字紀錄，再檢查鎖定、授權、通知及寬限紀錄。
- A3：`--links` 使用測試選出的空閒埠，核對三個設備各自的兩條連結，並檢查退出後無監聽。
- A4：使用固定的中午時間與注入排程時鐘；授權到期測試仍使用相對時間。
- A5：補測 600 秒與 601 秒。
- A6：重啟前保存規則時間之前的 `last_check`，重啟後驗證恰好一筆 skipped。
- A7：刪除多餘的啟停，背景等待改為觀察呼叫、資料庫或狀態檔。
- A8：暫存目錄改回系統預設位置。

**後端 B1～B5**：在 [gate.py](/Users/example-owner/TV-wt-tvgate4/tvgate/gate.py:156)。

- B1：鎖定讀回確認後才寫入 `GATE_LOCK`。
- B2：離家時錯過的排程記錄 `schedule-skipped`，明細為「Mac 不在家」。
- B3：忽略空鑰匙，其他合法設備仍可使用。
- B4：無效的 `last_fetch` 視為 null；`data_since` 不再讀取舊 meta。
- B5：`data_since` 改取資料庫最早事件時間。

**網頁 C1～C5**：在 [gate.py 的 PAGE](/Users/example-owner/TV-wt-tvgate4/tvgate/gate.py:511)。

- C1：加入全頁 `box-sizing`、卡片寬度限制、換行按鈕列及長字串斷行。
- C2：前景時間改為名稱、橫條、分鐘數的對齊 grid。
- C3：排程及勾選標籤使用中文星期，連續星期顯示範圍。
- C4：紀錄時間顯示為 `MM-DD HH:MM`。
- C5：[test_web_page](/Users/example-owner/TV-wt-tvgate4/tvgate/test_gate.py:401) 加入兩項 CSS 斷言。

指定指令的結果如下；測試輸出節錄結尾，兩次均為退出碼 0：

```text
$ python3 test_gate.py
Ran 34 tests in 61.733s
OK

$ python3 test_gate.py
Ran 34 tests in 61.922s
OK
```

兩次都通過測試內的程序群組殘留檢查；輸出另有 Python `ResourceWarning`，涉及未關閉的測試資料庫連線、socket 與 HTTPError。

```text
$ stat -f %m ~/Library/Logs/tvgate.log
1793293858
```

開工前也是 `1793293858`，未變。

```text
$ git -C /Users/example-owner/TV-wt-tvgate4 status --porcelain --untracked-files=all
 M tvgate/README.md
 M tvgate/gate.py
 M tvgate/test_gate.py
```

未連接電視或執行真 adb；未以真實瀏覽器實測 375px 畫面，因此手機版無左右捲動仍需畫面驗收。