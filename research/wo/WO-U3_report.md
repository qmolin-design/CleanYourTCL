# WO-U3 施工報告：END/LOCK 後殘留前景狀態造成重開機多算 91 秒

## 改了什麼（每檔一兩行）

- `tvagent/src/com/example/tvagent/AgentService.java`：`act()` 的 END／LOCK 分支在停用驗證成功（`s.yt = "disabled"` 那行）之後，加設 `q.ytFg = false; q.fgPkg = "";`，讓隨後的 `save(q)` 把「已停用、無前景」寫進磁碟。UNLOCK 分支未動。
- `tvagent/test/QuotaTest.java`：補兩個回歸測試（見下）。既有測試一行未動。
- `tvagent/tvagent.apk`：`sh tvagent/build.sh` 重新產生（33174 bytes）。

## 新增的測試

1. `reboot events lost cleared fg counts zero`：上一跳狀態 `fg=false、fgPkg=""`、從 qWall 到現在零事件（模擬重開機事件遺失）→ `Quota.usage()` 回傳 `seconds=0、nfS=0（ytS=0、ytFg=false）`。這是修完後每次 END／LOCK 存下的狀態，證明下一跳即使事件全丟也不多算。
2. `stale fg netflix overcounts 91s`：同樣零事件但上一跳狀態 `fg=true、fgPkg=Netflix` → 算出 `seconds=91、nfS=91`。對照組，證明測試抓得到差異（91 秒＝實機 477→568 的差值）。

## 驗收指令最後輸出

- `sh tvagent/test.sh`：exit=0，兩個測試類各印一次 `ALL PASS`（AdbClientTest＋QuotaTest）。共 98 行 PASS，0 FAIL（註：有個測試名叫 `wl-fail`，是白名單失敗情境的 PASS，不是失敗）。兩個新測試皆 PASS，既有測試全部照過。
- `sh tvagent/build.sh`：成功（僅一個既有的 deprecation Note：NoticeActivity 用了 deprecated API），產出 `tvagent/tvagent.apk`。
- `git diff --stat`：
  ```
  tvagent/src/com/example/tvagent/AgentService.java | 2 +-
  tvagent/test/QuotaTest.java                      | 4 ++++
  tvagent/tvagent.apk                              | Bin 33174 -> 33174 bytes
  3 files changed, 5 insertions(+), 1 deletion(-)
  ```

## 有疑慮的地方

1. **修法只擋「END／LOCK 成功停用」這條路**：若停用驗證失敗（throw verify）或 adb 掛掉，`q.ytFg/q.fgPkg` 仍保持「前景＝Netflix」，重開機一樣多算。這是按工單範圍照做的，但失敗路徑的殘留風險還在。
2. **heartbeat 裡的 `s.fgPkg` 沒清**：`act()` 只把 `s.fg` 設 null，`s.fgPkg` 保持舊值；不過 `s` 是每次 heartbeat 新建的區域變數，不進磁碟，所以不影響下一跳。`q.fgPkg` 已清，夠了。
3. **測試只測 `Quota.usage()` 純函數**：真正的因果鏈（act 清狀態 → save → 重開機 → usage 從 qWall 累計）沒有端到端測試，因為 act 依賴 adb 呼叫無法在單元測試跑。若以後想補，得把 act 切出可 mock 的 adb 層才測得到。
4. 未跑 adb／wrangler／deploy，未 commit／push／merge，改動留在工作區。
