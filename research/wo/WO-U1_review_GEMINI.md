PASS

### 審查理由與重點項目核對

1. **扣額行為、鎖定／提示／鬧鐘邏輯、sync 回應解析維持完全不變**
   - [`Quota.consume()`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/Quota.java#L86-L89)：額度扣減（`balance -= n`）、`usedTotal += n`、`usedToday += n` 計算與邊界限制完全保留原本邏輯，不介入分 App 計算。
   - 鎖定／提示／鬧鐘邏輯：[`Quota.decide()`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/Quota.java#L103-L112)、[`Quota.nextAlarmMs()`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/Quota.java#L113-L118) 與 [`AgentService.act()`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/AgentService.java#L95-L117) 皆未修改。
   - 同步回應解析：[`AgentService.sync()`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/AgentService.java#L167-L174) 與 [`Quota.applySync()`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/Quota.java#L90-L101) 對 grants、seq、cap 與 dbId 的驗證與套用邏輯完全未改動。

2. **[`Quota.load()`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/Quota.java#L27-L41) 寬鬆讀取與相容性**
   - 三個新鍵（`used_today_yt_s`、`used_today_nf_s`、`fg_pkg`）缺少或格式錯誤時：
     - 透過 [`optNumber()`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/Quota.java#L46-L49) 讀取秒數，缺鍵、非數字或負數皆捕獲例外並回傳預設值 `0`。
     - `fg_pkg` 僅接受 `Quota.YT` 或 `Quota.NF`，其餘字串或缺鍵時安全回退為預設值 `""`。
     - 寬鬆讀取不影響其他欄位，絕不丟出例外。
   - 舊欄位（`balance_s`、`last_seq`、`db_id` 等）維持 `required()`、`number()` 與 `bool()` 嚴格校驗，損壞或遺漏時維持原有機制重置為預設 [`Quota`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/Quota.java#L8-L10)。

3. **[`Quota.usage()`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/Quota.java#L60-L82) 計算與分 App 統計**
   - 原有 `seconds`、`ytFg`、`scrOn` 狀態推進與總秒數計算與原版邏輯完全一致，提供舊參數簽章的相容重載。
   - `ytS` 與 `nfS` 分別在受控套件前景且螢幕開啟時獨立累加毫秒後除以 1000 回傳，不強求兩者相加等於 `seconds`。
   - 當 `fg == true` 但 `fgPkg` 為空或 null 時，該段時間只累計到 `seconds`，不累加至 `ytS` 或 `nfS`（符合裁定 #9）。
   - 在 [`AgentService.heartbeat()`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/AgentService.java#L131-L133) 的 `clock=jump` 處理路徑中，jump 秒數正確歸給 `status()` 探得之前景套件。

4. **換日重置**
   - 在 [`Quota.consume()`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/Quota.java#L87) 中，當 `!today.equals(date)` 時，`usedToday = 0; usedTodayYt = 0; usedTodayNf = 0;` 同步歸零。

5. **完成標準 ①～⑦ 與測試完整性**
   - 既有測試未被刪除或改弱（[`QuotaTest.java`](file:///Users/example-owner/TV-wt-u1/tvagent/test/QuotaTest.java#L8-L49) 原有測試皆保留）。
   - 新增並覆蓋完成標準 ①～⑦（見 [`QuotaTest.java`](file:///Users/example-owner/TV-wt-u1/tvagent/test/QuotaTest.java#L50-L69)）：
     - ① [第 51-52 行](file:///Users/example-owner/TV-wt-u1/tvagent/test/QuotaTest.java#L51-L52)：YT 60 秒＋NF 30 秒 → ytS=60、nfS=30、seconds=90。
     - ② [第 53-56 行](file:///Users/example-owner/TV-wt-u1/tvagent/test/QuotaTest.java#L53-L56)：YT→NF 批次切換分到正確 App，跨批次保留前景套件。
     - ③ [第 57-60 行](file:///Users/example-owner/TV-wt-u1/tvagent/test/QuotaTest.java#L57-L60)：`fg=true` 且 `fgPkg` 為空或 null 時，ytS=nfS=0 且 seconds 照算。
     - ④ [第 61-63 行](file:///Users/example-owner/TV-wt-u1/tvagent/test/QuotaTest.java#L61-L63)：換日 consume 重置 `usedToday`、`usedTodayYt`、`usedTodayNf` 為 0。
     - ⑤ [第 64-66 行](file:///Users/example-owner/TV-wt-u1/tvagent/test/QuotaTest.java#L64-L66)：缺三新鍵的舊 state 讀取後 `balance`、`lastSeq`、`enforce` 完整保留。
     - ⑥ [第 67-68 行](file:///Users/example-owner/TV-wt-u1/tvagent/test/QuotaTest.java#L67-L68)：新鍵格式錯誤（abc、-5、未知套件名）保留其他欄位且新鍵為預設值。
     - ⑦ [第 69 行](file:///Users/example-owner/TV-wt-u1/tvagent/test/QuotaTest.java#L69)：`save()` → `load()` 來回完整保留三新鍵與 Properties 對等。
   - 所有 check 均為嚴格相等斷言，邏輯出錯必定失敗。

6. **修改範圍限制**
   - 僅修改指定 4 個檔案（[`Quota.java`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/Quota.java)、[`AgentService.java`](file:///Users/example-owner/TV-wt-u1/tvagent/src/com/example/tvagent/AgentService.java)、[`QuotaTest.java`](file:///Users/example-owner/TV-wt-u1/tvagent/test/QuotaTest.java)、[`README.md`](file:///Users/example-owner/TV-wt-u1/tvagent/README.md)）。
   - 未改動 `AdbClient.java`、`Whitelist.java`、`NoticeActivity.java`、Manifest、簽章金鑰或 `testdata/`。
