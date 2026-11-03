WO-T1 第 2 次退修已完成，指定的四項檢查均通過。

修改位置：

- [test_gate.py](/Users/example-owner/TV-wt-tvgate/tvgate/test_gate.py)：**A1** 指令檢查限定在各步驟新增的呼叫，並比對完整參數與鎖定順序；**A2** 空輸出及 exit 1 分別從成功讀取狀態開始測試；**A3** 兩個鎖定測試加入 `/status` 的 `tv=locked`；**A4** 對帳停回限制在三個週期內；**A5** 先確認通知與兩個週期的寬限，再測 `/lock` 一秒內鎖定；**A6** 補上各端點的 token、狀態不變及 GET 檢查；**A7** 比對重啟前後的 `until`。
- [gate.py](/Users/example-owner/TV-wt-tvgate/tvgate/gate.py)：**B1** 重連改用不帶 `-s` 的 `adb connect`；**B2** 前景讀取失敗時保留待判斷狀態，下一輪再判斷，並新增喚醒及寬限中的故障測試。
- [com.example.tvgate.plist](/Users/example-owner/TV-wt-tvgate/tvgate/com.example.tvgate.plist)：**C** 加入指定的標準輸出與錯誤日誌路徑。

四項指令的實際輸出末幾行：

```text
python3 test_gate.py
Ran 14 tests in 39.741s

OK
```

測試結束碼為 0；各案例的子程序清理檢查均通過。

```text
swiftc -o "$TMPDIR/sleephook" sleephook.swift
```

無輸出，結束碼為 0。

```text
plutil -lint com.example.tvgate.plist
com.example.tvgate.plist: OK
```

```text
git -C /Users/example-owner/TV-wt-tvgate status --porcelain --untracked-files=all
?? tvgate/README.md
?? tvgate/com.example.tvgate.plist
?? tvgate/gate.py
?? tvgate/sleephook.swift
?? tvgate/test_gate.py
```

未執行真 adb、連接電視或進行實機驗收；實機行為仍待 WO-T2 驗證。