# WO-U1 施工報告（tvagent 分 App 累計）

## 改了什麼

- `tvagent/src/com/example/tvagent/Quota.java`：新增 `usedTodayYt`／`usedTodayNf`／`fgPkg` 三欄位；`usage()` 新增 6 參數版本（`fgPkg` 起始套件），逐段把前景秒數累進 `ytS`／`nfS`，`seconds`／`ytFg`／`scrOn` 語意不變，舊 5 參數保留為相容轉發；`load()` 三新鍵寬鬆讀取（缺鍵／格式錯／負數→預設值，絕不丟例外）；`save()` 存三新鍵；`consume()` 換日同時歸零兩個新欄位，扣額行為不變。
- `tvagent/src/com/example/tvagent/AgentService.java`：`Status` 新增 `fgPkg`（從 `mResumedActivity` 行判斷 YT／NF）；heartbeat 以 6 參數 `usage()` 計算並累加 `usedTodayYt/Nf`，`clock=jump` 路徑把同秒數記給 `status()` 探到的套件；`q.fgPkg` 與 `q.ytFg` 同步更新；sync 請求加 `today`／`used_today_yt_s`／`used_today_nf_s`，`app_ver` 2→3（回應解析不動）；heartbeat log 行在 `used_today=` 後加 `yt_s=<秒> nf_s=<秒>`。
- `tvagent/test/QuotaTest.java`：只加新斷言，現有斷言一字未動；新增 11 個 PASS（見下）。
- `tvagent/README.md`：更新 log 行格式說明，加 sync 回報新欄位與 state 新鍵說明。
- `tvagent/tvagent.apk`：`sh tvagent/build.sh` 重建。

## 新增測試（對應完成標準）

- ① `usage per-app yt60 nf30`：YT 60 秒＋NF 30 秒 → ytS=60、nfS=30、seconds=90。
- ② `usage switch split with fgPkg`＋`usage switch chain keeps app`：YT→NF 同時間戳切換（含跨呼叫串接 `fgPkg`，STOPPED(23) 不影響），分到正確 App。
- ③ `usage unknown pkg`＋`usage null pkg`：`fg=true`＋`fgPkg` 空／null → ytS=nfS=0、seconds=100 照舊。
- ④ `consume same day keeps per-app`＋`consume rollover resets per-app`：換日 `usedToday`／兩新欄位歸零（同日 consume 不動分 App 計數）。
- ⑤ `load old state`：拔掉三新鍵的舊版 state → `balance`／`lastSeq`／`enforce` 保留，新鍵為預設值。
- ⑥ `load bad new keys`：`used_today_yt_s=abc`／負數／`fg_pkg` 非法字串 → 其他欄位保留、新鍵預設值。
- ⑦ `save load new keys`：三新鍵 save→load 來回保留。
- 另加 `usage compat overload`：舊 5 參數呼叫 seconds 行為不變。

## 驗收指令輸出

- `sh tvagent/test.sh`：exit=0，`PASS` 共 96 行（含 AdbClientTest），`ALL PASS` ×2（QuotaTest、AdbClientTest 各一），FAIL 0。註：grep "fail|error" 只命中測試名稱本身（如 `PASS wl-fail`、`PASS conscrypt-broken-error-specific`），皆為 PASS 行。
- `sh tvagent/build.sh`：成功，`tvagent/tvagent.apk` 已重建（33174 bytes；僅 Note: NoticeActivity deprecation，既有提示）。

## git diff --stat

```
 tvagent/README.md                                |   4 ++-
 tvagent/src/com/example/tvagent/AgentService.java |  25 +++++++------
 tvagent/src/com/example/tvagent/Quota.java        |  44 +++++++++++++++--------
 tvagent/test/QuotaTest.java                      |  20 +++++++++++
 tvagent/tvagent.apk                              | Bin 33174 -> 33174 bytes
 5 files changed, 68 insertions(+), 25 deletions(-)
```

只動 WO-U1 列出的 4 檔（＋apk）；未碰 `AdbClient.java`／`Whitelist.java`／`NoticeActivity.java`／Manifest／簽章／`testdata`；未改鎖定／提示／鬧鐘邏輯與 sync 回應解析；無 commit／push；無 adb／wrangler 操作。

## 疑慮

1. **升級第一跳**：舊版 state 的 `yt_fg=true` 只知「在看」，不知哪個 App；新碼按裁定 9 該段 seconds 照算、分 App 歸 0，之後由 `status()` 糾正。這是計畫要的行為，無需改。
2. **jump 路徑整段歸一 App**：`clock=jump` 把整段秒數記給跳末探到的套件，若該跳內切換過 App 會全算給後者；誤差 ≤1 跳，屬計畫已知誤差類，未另處理。
3. **分 App 與扣額合計對不上**：`usedTodayYt+Nf` 是原始前景秒，不隨 `balance` 上限打折（`consume` 的 `min(balance,…)` 只影響 `usedToday`），額度歸零那幾秒兩者會差幾秒——計畫定義接受，家長頁保留「今天扣了 X 分」那行即為此故。
4. **上線順序**：apk 已是 `app_ver` 3，若先裝到電視、Worker 還是舊版，舊 Worker 需能忽略多餘 JSON 欄位。計畫上線順序（Worker 先 deploy、電視後裝）是對的，Opus 執行時不要顛倒即可。
5. **`optNumber` 把負數也視為格式錯→0**：計畫只寫「格式錯」，負秒數本就不該出現，我採嚴格 сторону（歸 0），新測試 ⑥ 已鎖定此行為。
