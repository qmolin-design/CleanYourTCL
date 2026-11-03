# PLAN_poll：加值反應時間 60 秒 → 10 秒（WO-A3）

狀態：**APPROVED**（2026-11-01 09:4x，Opus；TERRA high 挑戰 8 條，裁定在檔尾；正文是裁定後版本。Owner 授權自主推進）

## 起因（Owner 11-01 09:0x～09:2x）
- 早上加值 4 次，電視實際開放等了 53／36／4／35 秒；08:33 剛好用完時加值，電視先跳「時間已到」關掉 YouTube 才又開放。
- Owner：「改 10 秒一次，這時間可以接受」；擔心電視 RAM 吃緊（Opus 實測 tvagent PSS 約 23 MB、MemAvailable 約 600 MB；多醒來不增加常駐記憶體）。

## 改法（只改電視 App）
1. **鎖住等加值時 10 秒一次**：`nextAlarmMs` 新增條件——有效額度（`balance + adultLeft`）為 0、**螢幕亮**、且本跳結束時 `s.yt` 不是 `unknown`（＝已停用、或 act 後確認的狀態）→ 10 秒。`unknown`（adb 壞、驗證失敗）維持 60 秒，不加速重試。其他情況照舊（有額度看片照現行公式、螢幕關 60 秒）。目標：加值到電視開放**通常 ≤15 秒**（鬧鐘間隔 10 秒＋查詢與 adb 約 2～5 秒），不是硬保證。
2. **要停用前先問伺服器**：heartbeat 裡，若這一跳會得到 END 或 LOCK（`wouldStop`），**先 sync 再 act**：`save(q)` → sync（**獨立 try/catch，任何例外或非 ok 都繼續往下**）→ sync 成功就**先 `save(q)` 落盤**（新 balance／last_seq／adultEnd）→ `act` →（act 有改狀態就）再 `save(q)`。sync 成功且有效額度 >0 → 不停用、不跳「時間已到」；sync 失敗 → 照本機狀態停用（最多比現行晚一次 sync 逾時，約 20 秒，只發生在要鎖的那一跳）。其他情況維持現行「先 act 再 sync」順序。這一跳只 sync 一次。
3. log：10 秒模式每跳照記（log 檔 1 MB 自動截半；約 11 小時的鎖定紀錄）。不另加旗標。

## 不做
- 伺服器主動推播／長連線（Owner 擔心 RAM；Opus 不建議）。
- 家長頁加值後 kick 電視（伺服器連不到電視）。

## 成本
- Worker 請求：最壞（全天鎖住且螢幕亮）8640 次／天；D1 寫入每次 sync 約 4 列 → 約 3.5 萬／天，免費額度 10 萬內。
- 電視：每 10 秒忙約 1～2 秒（adb 查詢＋HTTPS），只在鎖住＋螢幕亮時。

## WO-A3 電視 App（tvagent）
動到：`Quota.java`、`AgentService.java`、`tvagent/test/QuotaTest.java`、`tvagent/README.md`（＋重建 apk）。
1. `Quota.nextAlarmMs`：新增多載（帶 `screenOn`、`locked`）實作改法 1；舊簽名行為不變。
2. `Quota` 新增純函式 `boolean wouldStop(String yt, boolean mixed, long eff)`（＝`!yt.equals("unknown") && eff == 0 && (yt.equals("enabled") || mixed)`），heartbeat 用它決定改法 2 的順序。**`decide()` 本身不改。**
3. heartbeat：wouldStop 為真 → `save(q)` → sync →（成功則 applySync 已在 sync 內）→ act → save；否則照現行流程。兩條路徑 sync 都只呼叫一次，log 的 `act=`／`sync=` 照實記。
4. `app_ver` 改 5。
完成標準：`sh tvagent/test.sh` 全 PASS（既有測試不放寬），QuotaTest 新增：①eff=0、螢幕亮、已停用 → 10000；②eff=0、螢幕關 → 60000；③eff=0、螢幕亮、yt=unknown → 60000；④eff>0 各情況與舊公式相同（抽 3 組）；⑤wouldStop：enabled+0 → true、disabled+0 → false、unknown → false、mixed+0 → true、enabled+60 → false。`sh tvagent/build.sh` 成功。
禁止：不改 `AdbClient.java`、`Whitelist.java`、`NoticeActivity.java`、Manifest、簽章、`testdata/`；不改鎖定／解鎖的 adb 指令與驗證；不改 `decide()`、扣額、爸媽時段邏輯。

## 上線與驗收（Opus，需電視沒人在看）
1. merge、push → 電視 `install -r`（記 bal／last_seq；照 README 檢查 appops／bucket／deviceidle／TGuard）。
2. 實機：bal=0、非爸媽時段（必要時暫改 adult_sched）、螢幕亮：log 間隔約 10 秒；D1 加 +15（by Opus測試）3 次，各記「grant 寫入時刻 → log unlock 時刻」，**每次 ≤20 秒、平均 ≤15 秒**。
3. 剛好用完時加值：bal=60 看 YouTube，在 bal≤10 時寫 grant；以 log 判定：若停用前那一跳的 sync 已帶回此 grant（log 該跳 `bal` 已增加）→ 不得出現 end、YouTube 沒被關；若 grant 晚於那次 sync，出現 end 屬預期（記錄即可）。
4. 穩定（最低冒煙；長期看 11-02 的 C10 與 memlog）：鎖住＋螢幕亮連續 30 分鐘：pid 不變、`dumpsys activity exit-info` 無新 crash、`dumpsys meminfo com.example.tvagent` TOTAL PSS 與開始時差 <5 MB。
5. 測完還原 bal 與排程（by Opus測試）。回滾：前一版 apk `install -r`。

## 挑戰裁定（TERRA high，2026-11-01 09:4x；原文 `research/wo/PLAN_poll_challenge_TERRA.md`）
| # | 裁定 | 理由／改法 |
|---|---|---|
| 1 sync 失敗跳過鎖定／延遲 | **採納** | 預先 sync 獨立 try/catch，失敗照鎖；明寫最多晚約 20 秒、只在要鎖那一跳 |
| 2 先 act 後存檔 | **採納** | sync 成功先 save 再 act |
| 3 10 秒內無法保證 | **採納** | 目標改「通常 ≤15 秒」；驗收 3 次各 ≤20、平均 ≤15 |
| 4 升級／還原／回滾對帳 | 部分採納 | 照 PLAN_adult 作法（記 bal／last_seq、查 events 無家長操作才 set 回）；versionCode 相同，回滾 install -r 已驗過（WO-U3、A2）——駁回其餘 |
| 5 加值競態 | **採納** | 驗收改以 log 判定該跳 sync 是否已帶回 grant |
| 6 10 秒條件與測試矛盾 | **採納** | 條件改「eff=0＋螢幕亮＋yt 非 unknown」；unknown 維持 60 秒；測試③改 |
| 7 只測純函式 | 部分採納 | AgentService 無法在 JVM 單元測試；順序與失敗路徑列為 GEMINI／Opus 審查必查項＋實機 2、3；斷網失敗路徑靠審查（實機斷網 Owner 說不測） |
| 8 30 分鐘不足 | 部分採納 | 30 分鐘列為最低冒煙，長期看 11-02 C10／memlog；成本上線後看 Cloudflare 用量 |
