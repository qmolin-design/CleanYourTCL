# WO-A3 施工報告（加值反應 60 秒 → 10 秒）

分支 `poll-a3`，工作區保留未 commit。白話：電視沒額度鎖住時，原本每 60 秒才問一次伺服器「有人加值嗎」，現在螢幕亮著就每 10 秒問一次；而且要在關掉 YouTube 的那一跳，先問完伺服器、拿到加值就不關。

## 改了什麼

- `tvagent/src/com/example/tvagent/Quota.java`：新增純函式 `wouldStop(yt, mixed, eff)`（是否這一跳會停用）；`nextAlarmMs` 新增多載（多 `screenOn`、`locked` 兩個參數）：額度為 0＋螢幕亮＋狀態已知 → 10 秒，其餘轉舊公式；舊簽名行為不變，`decide()`、扣額、爸媽時段邏輯沒動。
- `tvagent/src/com/example/tvagent/AgentService.java`：heartbeat 分兩條路（見下節）；sync 請求 `app_ver` 4→5；排鬧鐘改傳新多載（螢幕狀態＋`s.yt` 是否已知）。
- `tvagent/test/QuotaTest.java`：只加新 `check`，既有測試一字未改。
- `tvagent/README.md`：`app_ver` 5＋加兩句話（10 秒條件、先同步再動作）。
- `tvagent/tvagent.apk`：`build.sh` 重建。

## heartbeat 新流程逐步說明

每跳先算 `pre = wouldStop(s.yt, s.mixed, balance + adultLeft)`（`s.yt` 是 adb 剛查到的實際狀態）。

- **成功加值（要鎖的那跳 sync 帶回 grant）**：走 `pre=true` 路。`save(q)` → sync（ok，`applySync` 更新 balance／last_seq／adultEnd）→ 再 `save(q)` 落盤 → `act()`：此時有效額度 >0，`decide` 不再是 END／LOCK，改走 UNLOCK（或 NONE），YouTube 不被關、不跳「時間已到」→ 最後 `save(q)`。整跳 sync 只呼叫一次，`act=` 記這次結果。
- **無加值（sync ok 但額度仍 0）**：走 `pre=true` 路。sync 成功、落盤後 `act()` 照本機狀態 END／LOCK（force-stop＋disable＋verify），`s.yt` 變 disabled → 下次鬧鐘 10 秒（螢幕亮）。sync 一次。
- **sync 失敗（例外或非 ok：skip／config／http:xxx／io）**：走 `pre=true` 路。sync 獨立 try/catch，失敗直接往下 `act()` 照本機狀態停用，不跳過、不重試。這跳比現行最多晚一次 sync 逾時（約 20 秒），只發生在要鎖的那一跳；之後每 10 秒（螢幕亮）會再試 sync，加值在下一跳就拿得到。
- **enforce 變更（sync 把 enforce true↔false 切換）**：分兩種：若本跳本來就要停用（`pre=true`），sync 後 `act()` 當場用新 enforce 判斷（切成 false → `dry-end`，不碰 adb）；若本跳不需停用（`pre=false`，舊路），維持現行「先 act → sync → 有變化再 act 一次」。
- **爸媽時段生效（sync 帶回 adult_left_s>0）**：`pre=true` 路裡 `applySync` 之外同步更新 `adultEnd`，落盤後 `act()` 看到有效額度 >0 → 不停用；`pre=false` 路維持現行（sync 後有效額度 0→正或跨 300 秒門檻才補跑一次 act）。
- `pre=false`（其他所有情況：有額度、已停用、unknown 等）：與舊流程逐字相同（act → save → sync → 有變化再 act → save），sync 一次。

## 新增測試（對應完成標準編號）

- ① `poll locked screen on 10s`：eff=0、螢幕亮、已停用 → 10000；另加 `poll locking screen on 10s`（啟用中、LOCK 那跳 → 10000）。
- ② `poll locked screen off 60s`：eff=0、螢幕關 → 60000。
- ③ `poll unknown 60s`：eff=0、螢幕亮、yt=unknown → 60000。
- ④ `poll eff20 same`／`poll eff330 same`／`poll adult eff50 same`：eff>0 三組，新多載在 screenOn／locked 各種組合下與舊公式相同。
- ⑤ `wouldStop`：enabled+0→true、disabled+0→false、unknown→false（含 mixed）、mixed+0→true、enabled+60→false。

## 驗收輸出

- `sh tvagent/test.sh`：exit=0，共 123 行 PASS，尾段含 8 個新 PASS，以 `ALL PASS` 結束；既有測試（AdbClientTest＋QuotaTest 全部舊 check）無修改、全過。
- `sh tvagent/build.sh`：exit=0（僅 NoticeActivity deprecated 舊提示，與本次無關），apk 已重建。
- `git diff --stat`：

```
 tvagent/README.md                                |   4 ++-
 tvagent/src/com/example/tvagent/AgentService.java |  42 +++++++++++++++--------
 tvagent/src/com/example/tvagent/Quota.java        |  11 ++++++
 tvagent/test/QuotaTest.java                      |  14 ++++++++
 tvagent/tvagent.apk                              | Bin 33174 -> 33174 bytes
 5 files changed, 56 insertions(+), 15 deletions(-)
```

## 疑慮

1. AgentService 的 heartbeat 順序與失敗路徑無法在 JVM 單元測試覆蓋（Android API 依賴），本次只驗了純函式；順序（先 sync 後 act、sync 只一次、失敗照鎖）與 `pre=false` 舊路逐字保留，已靠 diff 人工對過，實機驗收（計畫第 2、3 節）＋外部 review 仍須執行。
2. `pre=true` 路最後一次 `save(q)` 失敗時不改寫 `sync`（維持 sync 真實結果；下跳重 sync 靠 seq 去重補回，不重複加值）。舊路 `save` 失敗會把 `sync` 記成 `io`，兩路不一致，但舊路按「不動現行」原則保留原樣。
3. 要鎖那一跳若斷網，最多晚約 20 秒才停用（connect＋read 各 10 秒逾時）；計畫已裁定可接受，只發生在要鎖的那一跳。
