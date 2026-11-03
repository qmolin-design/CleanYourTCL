# WO-A2 施工報告（電視 App 爸媽時段）

## 改了什麼

- `tvagent/src/com/example/tvagent/Quota.java`：新增 `adultEnd`（ms，elapsedRealtime 基準），存檔鍵 `adult_end_el`，寬鬆讀取（缺／錯→0）；新方法 `adultLeft(el)`（剩餘秒）與靜態 `adultInvalid(boot, savedBoot, el, savedQEl)`（boot<0、boot 不同、el<存檔 qEl 即失效）；`decide()` 新增 4 參數／5 參數多載，用有效額度（balance＋adultLeft）判斷，舊簽名＝adultLeft 0 轉發、行為不變；`nextAlarmMs()` 新增帶 adultLeft 多載，同理；`usage()` 新增 7 參數多載（`freeToWall` 牆鐘 ms，之前的前景區間不計，seconds／ytS／nfS 同規則），舊簽名＝0 轉發、行為不變。
- `tvagent/src/com/example/tvagent/AgentService.java`：heartbeat 先依 `adultInvalid` 清 `adultEnd`，再算 `freeToWall = wall − (el − adultEnd)` 做本跳結算（含 `clock=jump` 路徑用 `max(qEl, adultEnd)`，分 App 給探到的前景套件）；sync 回應 `optLong("adult_left_s", 0)`，不在 0～86400→0（不讓整次 sync 失敗），成功後重取 `el2` 設 `adultEnd = el2 + min(adult_left_s,600)*1000`（每次覆寫含變 0）；sync 後重跑 `act()` 保留原條件再加「有效額度 >0 有變」或「≤300 有變」；`act()` 與 `nextAlarmMs()` 改傳 `adultLeft`；log 行 `bal=` 後加 `adult=<剩餘秒>`；`app_ver` 3→4（Manifest 不動）。
- `tvagent/test/QuotaTest.java`：只加新斷言，現有斷言一字未動；新增 13 個 PASS（見下）。
- `tvagent/README.md`：log 行加 `adult` 說明；state 新鍵 `adult_end_el` 說明；sync `app_ver` 4＋回應 `adult_left_s` 說明；新增「爸媽時段」一節（續期上限、有效額度、不扣額、失效條件）。
- `tvagent/tvagent.apk`：`sh tvagent/build.sh` 重建。

## 新增測試（對應完成標準）

- ① `adult unlock`：balance=0、adultLeft=600、YT 停用 → UNLOCK。
- ② `adult warn once`：balance=0、adultLeft=290、前景 → 第一次 WARN、第二次 NONE。
- ③ `adult end`：balance=0、adultLeft=0、前景 → END。
- ④ `adult no warn`：balance=1000、adultLeft=600（warned 先設 true）→ NONE 且 warned 被重置。
- ⑤ `adult free full`：前景區間全在 freeToWall 之前 → seconds=ytS=nfS=0。
- ⑥ `adult split idle then yt`：前 30 秒沒看、後 30 秒看 YT → seconds=ytS=30。
- ⑦ `adult yt free nf billed`：時段內 YT 30 秒＋時段後 NF 30 秒 → ytS=0、nfS=30、seconds=30。
- ⑧ `adult alarm`：balance=0、adultLeft=50、前景 → nextAlarmMs=50000。
- ⑨ `load old state adult 0`：拔掉 `adult_end_el` 的舊 state → balance／lastSeq／enforce 保留、adultEnd=0。
- ⑩ `load bad adult 0`：`adult_end_el=abc` → 其他欄位保留、adultEnd=0。
- 另加 `save load adult`：adultEnd save→load 來回保留。
- ⑪ `adult invalid`：boot 不同、boot<0、el<qEl 都 true，正常 false。

## 驗收指令輸出

- `sh tvagent/test.sh`：exit=0，`PASS` 共 112 行（含 AdbClientTest），`ALL PASS`（QuotaTest；AdbClientTest 各自 ALL PASS），FAIL 0。註：grep "fail|error" 只命中測試名稱本身（`PASS wl-fail`、`PASS conscrypt-broken-error-specific`），皆為 PASS 行。
- `sh tvagent/build.sh`：成功（exit=0），`tvagent/tvagent.apk` 已重建（33174 bytes；僅既有的 NoticeActivity deprecation Note）。

## git diff --stat

```
 tvagent/README.md                                |   8 +++--
 tvagent/src/com/example/tvagent/AgentService.java |  32 ++++++++++++-----
 tvagent/src/com/example/tvagent/Quota.java        |  44 ++++++++++++++++-------
 tvagent/test/QuotaTest.java                      |  18 ++++++++++
 tvagent/tvagent.apk                              | Bin 33174 -> 33174 bytes
 5 files changed, 79 insertions(+), 23 deletions(-)
```

只動 WO-A2 列出的 4 檔（＋apk）；未碰 `AdbClient.java`／`Whitelist.java`／`NoticeActivity.java`／Manifest／簽章／`testdata`；未改鎖定／解鎖 adb 指令與驗證；無 commit／push；無 adb／wrangler 操作。

## 疑慮

1. **sync 失敗時 adultEnd 保持舊值**：斷網期間不續期，倒數照 elapsedRealtime 自然歸零（安全側），符合「每次成功 sync 才覆寫」；但若斷網發生在時段剛開始，電視要等下一次成功 sync 才知道有時段，開頭最多一跳（≤60 秒）照扣——計畫已知誤差，無需改。
2. **`effBefore` 用 heartbeat 起始的 `el`**：sync 前後約差 1 秒，adultLeft 誤差 1 秒；門檻翻轉（>0／≤300）恰好落在邊界時可能多跑或少跑一次 `act()`，但 `decide()` 本身冪等（WARN 一次性由 warned 保護），屬無害。
3. **`data.optLong` 外多包了一層 try/catch**：Android 內建 org.json 的 optLong 理論上不丟例外，但為落實「不得讓整次 sync 失敗」，多一層保險；review 若嫌冗餘可刪。
4. **上線順序**：apk 已是 `app_ver` 4，若先裝到電視、Worker 還是舊版（WO-A1 未上），舊 Worker 回應無 `adult_left_s`→按 0 處理，等於沒時段，行為等同舊版，是安全的；但計畫上線順序（Worker 先 deploy）仍應遵守。

## fix1（Opus 審定兩 bug 修正）

### 改了什麼

- `Quota.java`：新增靜態 `freeToWall(wall, el, adultEnd)`——`adultEnd > 0 ? wall − (el − adultEnd) : 0`。adultEnd＝0（不在時段或剛失效）時回 0，本跳區間 `[qWall, wall]` 整段落在 free 之後，全部照扣。
- `AgentService.java` heartbeat：`freeToWall` 改調 `Quota.freeToWall(wall, el, q.adultEnd)`（原內聯算式在 adultEnd＝0 時等於「本次開機的牆鐘」，重開機後第一跳會把開機前觀看錯誤排除）。
- `AgentService.java` sync：`q.adultEnd = adultLeftS > 0 ? el2 + min(adultLeftS,600)*1000 : 0`（原 `adult_left_s == 0` 時設 `el2` 非 0，下一跳 freeToWall 落在上一跳 qWall 之後，差 status＋act＋sync 耗時、END 含 10 秒 sleep，這段前景每跳被排除不扣；現 0 直接清零）。
- jump 路徑 `Math.max(q.qEl, q.adultEnd)` 確認不用改：adultEnd＝0 時 max＝qEl（qEl≥0），行為與舊版一致。
- `QuotaTest.java`：只加 3 個新斷言，現有斷言一字未動。
- `tvagent.apk`：`sh tvagent/build.sh` 重建。

### 新增測試（對應 fix1 編號）

- ⑫ `adult freeToWall zero`：`freeToWall(1_000_000, 50_000, 0) == 0`。
- ⑬ `adult freeToWall mapped`：`freeToWall(1_000_000, 50_000, 40_000) == 990_000`。
- ⑭ `adult zero end full bill`：用 freeToWall 結果（adultEnd＝0→0）當參數呼叫 usage()，60 秒 YT 前景 → seconds＝ytS＝60、nfS＝0（不在時段完全照扣）。

### 驗收指令輸出

- `sh tvagent/test.sh`：exit=0，`PASS` 共 115 行（上一輪 112＋新增 3），`ALL PASS` ×2（QuotaTest、AdbClientTest），FAIL 0。
- `sh tvagent/build.sh`：成功（exit=0），apk 已重建（僅既有的 NoticeActivity deprecation Note）。
- `git diff --stat`（相對 HEAD，含上一輪未 commit 改動）：

```
 tvagent/README.md                                |   8 +++-
 tvagent/src/com/example/tvagent/AgentService.java |  32 ++++++++++++----
 tvagent/src/com/example/tvagent/Quota.java        |  46 ++++++++++++++++-------
 tvagent/test/QuotaTest.java                      |  22 +++++++++++
 tvagent/tvagent.apk                              | Bin 33174 -> 33174 bytes
 5 files changed, 85 insertions(+), 23 deletions(-)
```

只動 WO-A2 允許的 4 檔（＋apk）；無 commit／push；無 adb／wrangler 操作。

### 疑慮

無新增疑慮。上一輪報告疑慮 1–4 仍有效；其中疑慮 2 提到的 sync 前後 el 誤差，經本輪確認與 adultEnd＝0 清零無交互（effBefore/effAfter 都經 adultLeft 計算，0 即 0）。
