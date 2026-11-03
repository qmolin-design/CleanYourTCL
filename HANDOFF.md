# TCL 55P715 交接（最新：2026-11-01 03:45，Opus team-dev session）

實際家庭排程與兒童個資已移除或改為示例；技術驗證紀錄保留。

## ★★★★★ 新 session 從這裡開始（2026-11-01 03:45）
**先讀**：`STATE.md`（現況總表）→ 本節 → 下一節（03:00）→ `research/PLAN_adult.md`（APPROVED）→ `.claude/.team-dev-anchor.md` 最上面。
**分工**：大腦 OPUS high／挑戰者 TERRA high／工人 MUSE SPARK xhigh／審核 GEMINI FLASH high／接手 SOL medium。新 session 派工前先問 Owner 一次。

### P1 爸媽時段：已上線（03:23）
- Owner 03:0x～03:1x：爸媽時段合成範例 00:00–06:00、10:00–16:00（只有週一～五）、22:00–24:00；時段內不鎖、不扣；國定假日用家長頁「關閉這一段／下一段不要」（到該段結束自動恢復）；授權全程自主。
- 計畫 `research/PLAN_adult.md`（TERRA 11 條裁定在檔尾）。WO-A1 Worker＋頁、WO-A2 電視 App：MUSE SPARK 施工、GEMINI 審；A2 Opus 審定抓到 2 個少扣 bug（GEMINI 沒抓到）→ fix1 → 複審 PASS。main 0000003 已 push；worktree 已移除。
- 上線：D1 `meta.adult_sched` 已寫；Worker **00000013**（回滾 `wrangler rollback 000000000023-0000-4000-8000-000000000000`）；電視 install -r（app_ver 4，versionCode 不變）：bal／last_seq 保留、appops 都 allow、log 有 `adult=`。
- 實機回歸 PASS（03:24～03:41，Opus，by Opus測試，靜音）：bal=0 時段內 YouTube 開放、看 10 分以上 used_today 不動；測試排程到 03:34 → 03:29:03 剩 296 秒 warn → 03:34 end、兩個停用；新時段一分鐘內 unlock；寫 `adult_off_until` → 51 秒內 end；時段內 bal=0 `adb reboot` → 77 秒回報、開放、wl=set。測完還原排程、`set 3600`；期間無家長操作。重開機已自動解除靜音。
- 改排程時間：`cd cfquota && npx -y wrangler@4.151.0 d1 execute tvquota --remote --command "UPDATE meta SET v='<JSON>' WHERE k='adult_sched'"`（格式見 cfquota/README）。
- 電視 apk 回滾：`git show 0000007:tvagent/tvagent.apk > /tmp/old.apk && adb install -r /tmp/old.apk`。
- 未做（Owner 要再說）：手動「現在開始爸媽時段」、頁面改排程、推播。
- **太太登入（Owner 09:0x）**：Owner 更正太太 email 為 **mother@example.com**（舊拼寫少一個字母，已移除）：Access policy parents 已換、Worker secret PARENTS 已換（媽媽）。待太太實際登入確認。
- WO-U4 家長頁改版：規格 `research/PLAN_page.md`（排法 B），DISPATCHED MUSE SPARK（worktree ~/TV-wt-u4，prompt research/wo/WO-U4_prompt.txt）。
- 家長頁新的一行 Owner 尚未目視。
- **B 站 TV App 實測（11-01 晚，另一個 session）**：3 個客戶端都播不了，根因 B 站 48007（Google 註冊帳號被擋第三方 TV App 登入），已全部移除、電視還原。詳見 `research/batches/bilibili/NOTE.txt`；待作者回覆 newBV issue #322。若要納入管控另開工單。
- **WO-M1 兒童／爸媽模式切換：已上線（21:21）**（Owner 20:4x：自動切回照 mock、上方爸媽卡拿掉）：`research/PLAN_mode.md` APPROVED（TERRA 8 條裁定）；MUSE SPARK 施工、Opus 修一行（18 點前不寫「今晚」）、GEMINI PASS；main 已 push；Worker **00000019**（回滾 `wrangler rollback 000000020-0000-4000-8000-000000000000`）。實機（D1 模擬，by Opus測試，bal=0）：開爸媽 → 1 秒 unlock；切兒童 → 54 秒 lock ✓；測完 meta 無 adult_manual／adult_off_until（同原值）。**Owner 手機實按 PASS**：開爸媽 1 小時後 unlock；切兒童 26 秒後 lock；22:00 合成範例排程照常開始（未開始的排程沒被關，TERRA #1 ✓），meta 無殘留。未測：bal>0 切回不鎖（低風險）。
- 臨時爸媽時段（曾加一筆週日白天時段）：**Owner 當日要求提早移除**，排程還原 3 筆；同時 Owner 要求加 30 分（grant seq 26，by『爸爸（Opus代）』）。測試後電視額度為 1800 秒、adult=0、開放。
- **WO-A3 加值 10 秒反應**（Owner 09:3x「改 10 秒一次」）：`research/PLAN_poll.md` APPROVED（TERRA 8 條裁定），MUSE SPARK 施工、GEMINI PASS、Opus 審定 → main 0000011 已 merge／push（worktree 已移除）。**13:03 已 install -r（Owner 同意）**：bal／last_seq 保留、appops allow、鎖住＋螢幕亮時約 11 秒一跳。驗收第 1 輪：13:04 寫 +60（Opus測試）時有人關電視待機，**TGuard 13:04:00 砍 tvagent（tguard-kill，前所未見）**，43 秒後鬧鐘自己拉起（pid 2461）並 unlock（41 秒，螢幕關＝60 秒模式，不算數）；之後寫 set 0 → 電視 sync 收到 bal=0，但待機中 adb=io 無法停用 → **YouTube 目前是開放狀態、bal=0**，醒來第一跳（≤60 秒）會鎖 → **15:54 電視醒來，第一跳就 act=lock，YouTube 未被開過（fg=null），之後 14 秒一跳 ✓**。待辦：查 tguard-kill 是否與新版或 install 有關（看 TGuard 名單、下次待機是否再砍）；**驗收完成（20:03～20:38，Owner 在家、電視切 HDMI 玩 Switch、bal 本來就 0）**：
  - 加值反應：+60（Opus測試）3 次，寫入完成到 log unlock **5／3／3 秒**（含 wrangler 時間上限 10／6／5）✓（標準每次 ≤20、平均 ≤15）。Owner 17:01:14 實際加值 → 17:01:23 unlock（9 秒）；先前說 71 秒是誤讀（伺服器 unlock 事件記在下一次 sync）。
  - 30 分鐘冒煙：pid 3139 不變、tguard-kill 次數不變（11）、PSS 21.5→21.8 MB、MemAvailable 約 730 MB；log 間隔 11～12 秒 ✓。
  - 驗收 3（剛好用完時加值）沒做（要有人在看 YouTube）；留給自然發生時看 log。
  - 還原：測試前後 bal 都是 0，期間無家長操作 → 不需 set。
  - **TGuard**：當日 tguard-kill 11 次（待機時 1 次；長待機醒來後 7 分鐘內 10 次），白名單裡有 tvagent 仍被砍；每次 App 約 10 秒內由鬧鐘拉回、鎖定沒漏。其後 4.5 小時 0 次。**晚間 TGuard 大掃除一次砍 14 個程序（gms、vending、tv.settings…），tvagent 在列，被砍時 oom adj=915（cached）→ 兩跳之間 tvagent 不是前景服務狀態，白名單沒保護到；12 秒內鬧鐘拉回。** 待 C10 看：是否讓前景服務常駐（不 stopSelf）以避免 cached；是否與 app_ver 5 有關（比對 app_ver 4 時期的待機醒來）。
- **WO-U4 家長頁改版：已上線**（09:29）：fix1 後 GEMINI 複審 PASS、Opus 審定（esc 全查）；main 0000014；Worker **00000016**（回滾 `wrangler rollback 000000000019-0000-4000-8000-000000000000`）；電視 sync=ok。小瑕疵：7 天圖「今天」底色右緣凸出約 4px（不造成捲動）。Owner 尚未目視新頁。

## （前一版 03:00）
## ★★★★ 新 session 從這裡開始（2026-11-01 03:00）
**先讀**：本節 → 下一節（02:05 那節，有回歸／WO-U3／改版／P1 的細節）→ `.claude/.team-dev-anchor.md` 最上面。
**分工**：大腦 OPUS high／挑戰者 TERRA high／工人 MUSE SPARK xhigh／審核 GEMINI FLASH high／接手 SOL medium／查詢 HAIKU。Owner 02:3x 說「不用跑 /team-dev，直接派」，新 session 要派工前照規定先問一次。
**Owner 授權**：可用 adb＋D1（`by='Opus測試'`）自行操作電視測試；出聲的先靜音（`input keyevent 164`）。

### 現況（03:00）
- v5 控管中、PLAN_usage 上線、回歸 PASS、**WO-U3 上線**（main 0000007，電視 02:28 install -r，bal=3600 保留）。電視目前 bal=3600、開放、未靜音。
- **家長頁改版**：hallmark（Nutlope/hallmark，commit 4f6a46c，MIT）；先前 clone 在 job tmp，**會被清掉**，要用時重新 `git clone` 並 checkout 4f6a46c；沒全域安裝、沒裝 hook。
  - mock 定稿方向：**Coral 溫暖柔和第 2 版、按鈕 3+1 排法**（Owner：之後應會再調）。檔案 `mockups/parent-v3.html`（第 2 版；第 1 版三調性比較 `mockups/parent-v2.html`）。
  - 下一步：Owner 確認後開 WO-U4（MUSE SPARK 改 `cfquota/lib.mjs` 的 page，只改畫面、資料 API 不動）→ GEMINI 審 → deploy。可與 P1 一起做。
- **P1 爸媽模式＋大人時段排程**（Owner 02:4x～02:5x）：大人看不限時、不扣額度；手動（到某時間）＋排程（起床前／上學／睡後）合一，伺服器下發「大人時段剩餘秒數」，電視用 elapsedRealtime 倒數。v4 的排程是「時間點切斷」，不同東西。
  - **待 Owner 答**：①平日起床／出門／回家／睡覺時間、週末另一套？②手動預設到期「明早 06:00」可否？③太太加進 Access 登入名單？④是否授權 Claude 自主開發（見 03:00 對話：Opus 建議只留「計畫核准」一個關卡）。
  - 尚未寫計畫檔；寫時放 `research/PLAN_adult.md`，照 team-dev：TERRA 挑戰 → APPROVED → 工單。
- 剩 Owner 動手：C2 待機 10 分、C8 語音／T 鍵（只記錄）、提示跳出時 YouTube 是否自動暫停（目視）、家長頁「使用時間」實頁目視。C5 拔插頭 Owner 可免。
- Opus 待辦：11-02 看記憶體與 log（C10）；全過 → 移除 v4（先打 tag）→ 白話總整理頁面；移除 worktree `~/TV-wt-u1`、`~/TV-wt-u2`、`~/TV-wt-u3`。

## （前一版 02:05）
## ★★★ 新 session 從這裡開始（2026-11-01 02:05）
**先讀**：本節 → 下一節「v5 已切換上線」→ `research/PLAN_usage.md`（APPROVED）→ `.claude/.team-dev-anchor.md` 最上面。
**分工**：大腦 OPUS high／挑戰者 TERRA high／工人 **MUSE SPARK xhigh**（Owner 11-01 選）／審核 GEMINI FLASH high／接手 SOL medium。新 session 先跑 `/team-dev`。
**Owner 授權**：可用 adb＋D1（`by='Opus測試'`）自行操作電視測試；會跳畫面或出聲的先靜音（`input keyevent 164`，重開機會自動解除靜音）。

### 現況（02:05）
- **v5 控管中**（enforce=true），v4 已 bootout。家長頁 https://tv-demo.example.net/tv/ 。
- **PLAN_usage 已上線**（Owner 01:4x 指出 v5 頁少了累計時間、紀錄單位是秒 → 選「要分 YouTube／Netflix」）：
  - WO-U1 電視 App（分 App 當日前景秒數、app_ver 3、新鍵寬鬆讀取）、WO-U2 Worker＋頁（usage_daily 表、今天／本週／本月、紀錄白話）：MUSE SPARK 施工、GEMINI 審（U2 第 1 輪 FAIL：跨月週漏算／對照表兩份／測試缺口 → 修 1 次 PASS）、Opus 審定。main 0000008、0000015 已 push；worktree `~/TV-wt-u1`、`~/TV-wt-u2` 還在（可移除）。
  - 02:00 部署：D1 schema（只加 usage_daily）、Worker **00000012**（回滾 `wrangler rollback 0000000000021-0000-4000-8000-000000000000`）、電視 `install -r`：bal=3600／last_seq=16 保留、appops 都 allow、D1 收到 app_ver 3 與 usage_daily 兩列。
  - 電視 apk 回滾：`git show 0000005:tvagent/tvagent.apk > /tmp/old.apk && adb install -r`（舊版會忽略多的 state 鍵）。
- **回歸 PASS（02:02～02:16，Opus）**：set 600 → YouTube 2 分 → Netflix；02:07:21 warn、02:12:38 end、02:12:57 兩個停用回桌面；log `yt_s=123 nf_s=477`，D1 usage_daily 同值。bal=0 `adb reboot` → 開機仍停用、79 秒 agent 回報 wl=set、自動解除靜音。02:16 已 `set 3600`（by Opus測試）還原 Owner 額度，電視 bal=3600、unlock。家長頁走 Access，頁面數字未目視（D1 對）。
- **WO-U3 DONE（02:28 上線）**：停用後 `q.ytFg/fgPkg` 沒清 → 重開機多算 Netflix（實測 +91 秒）。MUSE SPARK 修（act() END/LOCK 驗證成功後清前景，QuotaTest +2）、GEMINI PASS、Opus 審定；main 0000007 已 push；電視 install -r：bal=3600 保留、setup=11111。今天 D1 的 nf 568 秒會留著（MAX 合併）。worktree `~/TV-wt-u3` 可移除。
- **家長頁改版（進行中）**：Owner 嫌陽春 → HAIKU 查參考（Family Link 等，資料薄）＋查設計 skill；Owner 選 hallmark（Nutlope/hallmark，MIT，臨時 clone 於 job tmp，沒全域安裝、沒裝 hook）。三種調性 mock（Coral 溫暖／Ink 工具感／Hum 活潑，App screens 模式）：`mockups/parent-v2.html`。等 Owner 選一種 → 開工單派 MUSE SPARK 改 `cfquota/lib.mjs` 的 page（只改畫面）→ GEMINI 審 → 部署。新功能（孩子請求加時、睡前自動鎖）另議。
- Owner 02:4x：mock 先選 **B（3+1 排法）**，之後還會再調（mock v3：`mockups/parent-v3.html`，同一 artifact 第 2 版）。
- **未來功能 P1：爸媽模式**（Owner 02:4x 提出）：小孩睡了／出門時大人看電視不限時、不扣額度。Opus 已說明設計難度（中低；重點是忘了關→必須自動到期、改用伺服器給的剩餘秒數、到期前提醒、太太要能切換需加 Access 名單）。尚未開計畫，待 Owner 確認細節。
  - Owner 02:5x 補充：**排程也要**（小孩起床前、上學時間、晚上睡覺後＝大人時段）。註：v4 的排程是「時間點切斷（鎖）」，v5 刻意拿掉（PLAN_usage 記「v4 排程不補」）；這次要的是相反的「大人免計時段」。Opus 提案：手動爸媽模式與排程合一，伺服器算「現在是否大人時段、剩幾秒」下發，電視只吃剩餘秒數（用 elapsedRealtime 倒數，不信電視時鐘）；排程難點：跨午夜、「今天暫停／暫停到某天」（病假、寒暑假）、到期前 5 分提醒。待 Owner 給：平日起床／出門／回家／睡覺時間、週末是否另一套；預設到期 06:00、太太加 Access 名單待答。
- 剩 Owner 動手的 C 項：**C2 待機 10 分**（有額度播 YouTube → 遙控電源待機 10 分 → 開回，報時間）、C5 真拔插頭（Owner 可決定免測，adb reboot 已過）、C8 語音／T 鍵（只記錄）。C6 斷網、C8 投放 Owner 說不測。C10：11-02 看記憶體與 log。
- 全過 → 移除 v4（先打 tag）→ 白話總整理頁面（Owner 交辦）。
- 未記：提示跳出時 YouTube 是否自動暫停（待 Owner 目視）。

## （前一版 00:22）

## ★★ 2026-11-01 00:21 v5 已切換上線（B PASS），v4 已停
- Owner 11-01 00:0x：「全做吧，管理介面先不考慮我太太，我能用就GO」→ A4 免測、A 其餘全 PASS 後不等影子期直接切換。
- B 實況：00:20 Owner 家長頁歸零 → 00:20:34 電視 bal=0 → 00:20:44 `launchctl bootout gui/$(id -u)/com.example.tvgate`（v4 log `away stop`）→ D1 `enforce=true` → 00:21:12 kick：`act=dry-end,end`，`pm list packages -d` 含 `com.google.android.youtube.tv` 與 `com.netflix.ninja`，前景回桌面。
- **現在控管＝電視 App（v5）＋家長頁**；v4 launchd 已 bootout（plist 還在 `~/Library/LaunchAgents/`，沒刪）。
- **回滾**：`cd cfquota && npx -y wrangler@4.151.0 d1 execute tvquota --remote --command "UPDATE meta SET v='false' WHERE k='enforce'"` → kick 電視 → `launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.example.tvgate.plist`（v4 起來會鎖 YouTube＋Netflix）。
- **C 自主測試結果（11-01 00:31～01:26，Opus 用 adb＋D1 加值 `by='Opus測試'`；額度縮短為 7／10 分；電視靜音測）**：
  - C1 PASS：+7 分看 YouTube，00:34:02 bal=299 `act=warn` 跳提示 5 秒關；00:39:12 跳「時間已到」、00:39:25 回桌面、兩個都停用。
  - C1b PASS：+10 分，YouTube 4 分後切 Netflix；Netflix 中 00:48:43 warn、00:54:02 end、兩個都停用；used 823→1423（共扣 600）。
  - C2 前半 PASS：+10 分停桌面 00:57→01:07，bal 600 不變。**待機那半未測**（待機會讓 adb offline，要 Owner）。
  - C3 PASS：`settings put global auto_time 0`＋`cmd alarm set-time` 調快 1h：uptime 123 秒扣 122；調回：61 秒扣 61；log 有 `clock=jump`。已恢復 auto_time=1、時間對齊 Mac。
  - C4 PASS（用 `pm enable --user 0`，非設定畫面）：YouTube 36 秒、Netflix 61 秒停回。
  - C5 PASS（用 `adb reboot`，非拔插頭）：bal=0 重開全程停用、開機 79 秒 agent 回報、wl=set；bal=300 重開額度保留、開放。
  - C9 PASS：鎖住時 Spotify、四季（tv.fourgtv.video）都開得起來。
  - C8 強制停止：同 A5 實況（App 停到 kick／重開機，約 30 分後家長頁紅字）；不另測。
  - 測完歸零（01:25 `set 0` by Opus測試），電視 bal=0、兩個都停用。
- Owner 11-01 01:4x：**C6 斷網、C8 手機投放不測**（edge case 不管）。C7 Owner 01:38 按 +30 兩次（seq 15、16 相隔 3 秒，都入帳 → 電視 bal=3600 封頂）；按鈕送出時變灰，Owner 確認「不能連點二下」。
- **還要 Owner 動手**：C2 待機 10 分、C5 真的拔插頭、C6 關 Wi-Fi 35 分、C7 家長頁歸零＋加值＋連按兩下（媽媽免）、C8 投放／語音／T 鍵；C10 隔天 Opus 看記憶體與 log。
- 下一步：TESTPLAN C1～C10（多數要 Owner 動手：+15 看到用完、待機、調時間、設定裡啟用、拔插頭、關 Wi-Fi）→ 全過移除 v4（先打 tag）→ 白話總整理頁面。
- A2 未記：提示跳出時 YouTube 是否自動暫停（dumpsys 看不出，待 Owner 目視；C1 會再遇到）。

## （前一版）
## ★ 新 session 從這裡開始（2026-10-31 23:30）：v5 實機測試進行中
**先讀**：本節 → `research/batches/v5/TESTPLAN.md`（含第 0 節 Netflix）→ `research/PLAN_netflix.md` → 需要再讀下面「前一版」各節。
**分工**：上一個 session 用 大腦 OPUS high／挑戰者 TERRA high／工人 SONNET（Agent）／審核 GEMINI FLASH high。新 session 先跑 `/team-dev`，工人照問 Owner。
**Owner 授權**：10-31 22:20「開始測，現在 TV 沒有人用，可以隨你測」——Opus 可用 adb＋v4 API 自行操作電視測試；會跳畫面打擾觀看的測項（A2）先確認沒人在看（看 usagestats 有沒有人為切換、遙控按鍵 `TclShortCutKeyManager`）。

### 一定要先做
1. ~~叫醒 tvagent~~（11-01 已完成，A5 PASS，以下留作參考）：A5 測試時 22:40:03 `am force-stop com.example.tvagent`，之後電視待機、adb offline，沒叫回來（23:23 有一次 kick 顯示 completed 但無法確認）。電視一開機就：
   `adb -s 192.0.2.69:5555 shell am broadcast -n com.example.tvagent/.KickReceiver -a com.example.tvagent.KICK -f 0x20`，再 `run-as com.example.tvagent tail -2 files/agent.log` 確認有新行（sync=ok、setup=11111）。叫不起來（`process is bad`）就先 `adb shell am start -n com.example.tvagent/.NoticeActivity`（無 kind 會立刻結束）再 kick。
2. 請 Owner 重新整理家長頁 **https://tv-demo.example.net/tv/** 確認紅字消失 → A5 完整通過。

### 測試進度（TESTPLAN）
| 項目 | 結果 |
|---|---|
| N1 開放後開 Netflix | PASS（登入畫面 Owner 沒印象；**電視 display FLAG_SECURE，screencap 一律空檔**，畫面只能請 Owner 看） |
| N2 鎖後 Netflix | PASS（正在開的被關、LEANBACK 啟動與 nflx:// 都 resolve 不到；遙控 Netflix 鍵走 `com.netflix.action.NETFLIX_KEY_START`，同樣會解析不到） |
| A3 家長頁 +15 | PASS（kick 後 bal=900） |
| A1 計時 | **PASS**：YouTube 約 60 秒／分；Netflix 11-01 00:13:49→00:16:54 牆鐘 185 秒、used 8→191（183 秒） |
| A5 停擺紅字 | **PASS**（前半 Owner 23:2x 看到「電視 App 已 42 分沒回報（最後螢幕開著）」；後半：23:23 kick 已叫醒 pid 2682、23:28 起每分鐘 alarm sync=ok，Owner 11-01 00:0x 重新整理紅字消失） |
| A2 剩 5 分提示 | **PASS**（11-01 00:17:55 `am start NoticeActivity --es kind warn`，YouTube 前景時跳出、00:18:02 已自動關回 YouTube）；YouTube 是否自動暫停：dumpsys 看不出（YouTube 無 media session、音訊走 tunnel），待 Owner 目視 |
| A4 太太登入 | **免測**（Owner 11-01：「管理介面先不考慮我太太，我能用就GO」；Owner 00:12 自己按 +15 成功） |
之後：A 全過 → Owner 決定切換時間（影子期照原訂 11/4 00:17，可提早）→ B → C → 移除 v4 → 白話總整理頁面。

### 今天新修的 v5 bug（WO-N3，已上線）
- 現象：18:16～18:19 tvagent crash ×4：`RemoteServiceException: Context.startForegroundService() did not then call Service.startForeground()`，之後 Android 標 `process is bad`，鬧鐘／kick 都叫不起來，停擺到 22:32。觸發條件：bal=0＋fg=true（影子模式 dry-end）→ `nextAlarmMs` 每 5 秒一次。
- 修正（merge 0000001，已 push，22:38 裝電視）：`onStartCommand` 每次都 `startAsForeground()`；`nextAlarmMs` 在 bal=0 回 60 秒。實機連續 kick 15 次 pid 不變、無 crash。
- 查 crash：`adb shell dumpsys activity exit-info com.example.tvagent`、`adb shell logcat -b crash -d`。

### 其他現況
- v4（Mac，launchd com.example.tvgate）照常控管 YouTube＋Netflix，23:03 授權到期已鎖回。v4 API 測試用 token 在 `~/.secrets/tvgate.tokens.json`（不要印出）。
- main 已 push（含 WO-N1／N2／N3、PLAN_netflix、TESTPLAN 第 0 節）。
- Mac 時鐘與 log 對照：v4 log `~/Library/Logs/tvgate.log`；電視 agent.log 為台北時間。

## （前一版）2026-10-31 17:30 更新：控管加入 Netflix（v4、v5 都加）＋v4 按鈕防連按
- 計畫 `research/PLAN_netflix.md`（APPROVED，TERRA 挑戰 11 條，裁定在檔尾）；Owner 裁定：v4、v5 都加；v5 YouTube＋Netflix **共用同一份額度**。
- 已合併 main（a253e75）、已 push。影子期不重算，照原本 11/4 00:17；A 過了 Owner 可提早切換（Owner 10-31「都做吧」）。v4 已重啟上線（17:24，pid 見 launchctl）：鎖／開兩個 App 一起；只鎖到一個顯示「部分鎖定」並自動重做；按鈕送出時變灰。
- v5 新 apk 已裝電視（17:25，setup=11111、sync=ok、APP_ASSOC_START allow）。**影子期起點可視為 17:25 重算**（是否照 96h 重算由 Owner 決定）。
- cfquota 頁面文字（「YouTube／Netflix」）已部署（17:40，Version 00000004）；電視同步 ok、tv-demo.example.net 首頁照常導向 English-Reading。
- 實機已查：Netflix 停用時桌面找不到入口（resolve-activity 無）、資料區保留（登入不會掉）；**N1／N2 手測（TESTPLAN 第 0 節）待 Owner**。

## （前一版 00:40）v5 已部署、影子模式中，等 Owner 實機測試
**先讀**：本節 → `research/batches/v5/TESTPLAN.md`（明天照這份逐項測）→ `research/PLAN_v5.md`（APPROVED，main）→ `cfquota/README.md`「維運」→ `tvagent/README.md`。
**分工**：大腦 OPUS high／挑戰者 TERRA high／工人 **pi MUSE SPARK xhigh**（Owner 10-31 00:18 改）／審核 GEMINI FLASH high／接手 SOL medium。新 session 先跑 `/team-dev`。
**Owner 交辦**：整個計畫做完（C5 結束、v4 移除）後，給 Owner 一份**詳細但白話**的總整理（寫成頁面）。

### 現況（10-31 00:40）
- main = v5-design 已合併（0000018 起），已 push。v4（`tvgate/`，launchd com.example.tvgate）照常控管，沒動。
- **WO-C1／C2／C2b／C3 DONE**：Worker `tvquota` 在 `tv-demo.example.net/tv/*`（tc-booklist 不受影響，已驗）；D1 `tvquota`；Access（Google 登入兩個 email＋service token `example-tv-token`，**2099-12-31 到期**，值在 `~/.secrets/tvquota.cf.properties`）。
- **WO-C4 進行中**：10-31 00:16 電視裝 v2 App（00:26 再裝 C2b 版），config 已推，`sync=ok adb=ok wl=keep setup=11111`，appops／deviceidle／bucket 都對。**影子期 00:17 起算**（96h＝11/4 00:17；Owner 可提早拍板）。
- Owner 說「現在沒空測，功能先做完，明天抽時間逐一測」→ 剩下全是測試：TESTPLAN A（=C4 第 3～5 項＋太太登入）→ Owner 決定切換時間 → B 切換 → C（=C5 驗收 1～10）→ 移除 v4 → 白話總整理。
- 觀察：每次 install／KICK 後 Mac 的 adb 會斷一次，10 秒內重連；影子期留意 v4 是否受影響（看 `~/Library/Logs/tvgate.log`）。
- Owner 10-30 22:46 左右在 iPhone 按過加值／歸零（D1 events 爸爸 ×5），電視已套用、虛擬額度 0；C5 前「歸零」一次即可，不必清。
- C4 第 6 項（v5 用量 vs v4 前景分鐘數，差 ≤2 分）可用明天白天孩子在 v4 下看 YouTube 的資料比對：agent.log 現在每行有台北時間與 `src=`。

# （前一版）交接 2026-10-30 21:55

## （前一版）新 session 從這裡開始：v5 設計（Owner 21:35 指定交給新 session）
**先讀**：本節 → `research/PLAN_v5.md`（分支 `v5-design`，已 push）→ `research/batches/tvagent/NOTE.txt` 最後三節 → `research/ref/tguard/R8_tguard_report.md`。
**溝通**：Owner 授權新 session 直接問舊 session（本 session，Opus team-dev）。舊 session 只回答事實與現況，**不代 Owner 做決定**：13 題答案、LINE／Telegram、APPROVED 都要 Owner 本人回答。
**上一個 session 的分工**：大腦 OPUS high／挑戰者 TERRA high／工人 **TERRA high priority**（Owner 20:25 改：`codex exec -m openai/gpt-6.1-terra -c model_reasoning_effort=high -c service_tier='"fast"'`）／審核 GEMINI FLASH high／接手 SOL medium。新 session 先跑 `/team-dev`。

### v5 設計現況（21:55 更新）
- `research/PLAN_v5.md`（分支 `v5-design`，最新 v5-design HEAD，已 push）：**CHALLENGED＋Owner 全部答覆**。優先順序：檔內「Owner 裁定 21:50」＞「Owner 裁定 21:40」＞「挑戰裁定」（TERRA 25 條）＞正文。兩張 Owner 裁定表都是 tv-66 session 問 Owner 後轉述的。
- Owner 重點：CAP 60 分；YouTube 前景＋螢幕亮就扣；不過期；+15／+30／+60／歸零；剩 5 分預告、用完跳「電視時間已到」10 秒後鎖；網址 `https://tv-demo.example.net/tv/`（**已有 Worker tc-booklist，不得改動**，v5 用獨立 Worker route `/tv/*`）；Access 用 Google 登入（father@example.com、mother@example.com）；本期不推播；App 名「看電視時間」；不用裝置管理員；影子期 96 小時；上線後從 Mac 移除 v4。
- 「依 19:00 數據調整」已填。
- **下一步（新 session）**：
  1. 先把 main merge 進 `v5-design`（分支從 43fa9ca 分出，沒有 TGuard／WO-G2）。
  2. 依三張裁定表把正文改寫成單一版本，包括介面路徑加 `/tv`、拿掉 ntfy 和裝置管理員、NoticeActivity、CAP，以及 WO-C1～C5 的驗收。
  3. 狀態改 APPROVED，告訴 Owner。
  4. 派 WO-C1（Worker）、WO-C2（TV App）。工人用 TERRA priority。
- 4 項待實測（C2 前）：
  - 跳畫面時 YouTube 會不會自動暫停。
  - shell 的 `am start` 能不能叫起 exported=false 的 Activity。
  - TCL 會不會擋這個 `am start`。
  - CAP 調小時要不要砍現有額度（暫定不砍）。
- 第二期 TODO：Telegram／ntfy 控制台。研究在 main 0000017 `research/R8_notify_control_HAIKU.md`；PLAN_v5「不做」已記第二階段 TODO。

### 今天解掉的阻擋：TCL TGuard 砍 tvagent（v5 前提「電視 App 常駐」）
- 19:00 觀察 FAIL：TCL 記憶體管家 TGuard（`com.tcl.guard`）開 Netflix 後半小時砍 tvagent 8 次。
- 逆向（TERRA，`research/ref/tguard/R8_tguard_report.md`；反編譯原始碼沒進 repo）＋網路查詢（`R8_web_en.md`、`R8_web_cn.md`）：TGuard 有保護名單 `mKillWhiteList`，shell 可整份取代：
  `dumpsys activity service com.tcl.guard/.memorymanager.TGuardService mKillWhiteList '<逗號清單>'`（只在記憶體，重開機或 TGuard 重啟就變回預設 11 個）。**不停用 TGuard**（網路上沒有停用後正常運作的案例，TGuard 另外還負責開機時套用禁用清單）。
- Owner 選 C：名單改成 9 個（拿掉 mediashell、providers.media.module、providers.tv，加入 tvagent），Chromecast `pm disable-user`（還原 `pm enable --user 0 com.google.android.apps.mediashell`）。
- **WO-G2 已上線**（merge 0000006）：tvagent 每分鐘心跳時檢查名單，沒有就補回，log 記 `wl=keep|set|fail|skip`。實機：模擬重置 → wl=set；**Owner 21:23 冷開機 → 開機 90 秒 wl=set**；之後開 Netflix，tvagent 被砍 0 次（同時段 TGuard 砍其他程式 24 次）。
- **正式 App 要沿用**：名單補回機制；TCL 權限 `APP_AUTO_START allow`＋**`APP_ASSOC_START allow`**（覆蓋安裝會被重置，沒開會被 TclAppBootManager 擋鬧鐘）＋standby bucket active＋deviceidle 白名單。每次重裝都要檢查。
- 記憶體實況：實體約 1990MB，kernel 可管 1621MB，CMA 608MB（影片），zram 600MB 已用約 490MB → 這台記憶體確實很緊，名單不要亂加。
- 未解（寫進 v5 驗收）：①tvagent 用 PendingIntent 鬧鐘，之前被砍後鬧鐘消失，原因未定；②TclAppBootManager 每次心跳記 `start_foreground isAllow=false default_borbid`，但心跳照跑；程序若被非 TGuard 的機制砍掉，能不能被鬧鐘叫起來，還沒測；③TCL 韌體更新可能改掉 TGuard → v5 上線初期保留 v4 Mac 當備援。

### 電視與 v4 現況（21:55）
- 電視：tvagent（含白名單補回，pid 3392）執行中，TGuard 名單 9 個，Chromecast 停用；舊 session 的背景觀察約 22:35 自動結束，不用理會。
- v4 執行中，沒有變動；WO-T4 驗收還在等 Owner 裝 iPhone 連結（見下一節）。

# （前一版）交接 2026-10-30 10:00

## （前一版）新 session 從這裡開始
**先讀**：本節 → `.claude/.team-dev-anchor.md`（最上面：本 session 分工與 WO-A1／WO-A2／WO-T4 狀態）→ 需要細節再讀下列檔案。
**上一個 session 的分工**（Dev Team Mode，`.claude/.dev-mode`＝team）：大腦 OPUS high／挑戰者 TERRA high／工人 MUSE SPARK xhigh／審核 GEMINI FLASH high／接手 SOL medium。新 session 先跑 `/team-dev`，工人照問 Owner。

### 兩條工作線
**A. v4（Mac 用 adb 鎖 YouTube）——已上線，WO-T4 驗收未做**
- 07:22 上線，launchd `com.example.tvgate` 執行中（port 8790）。回滾：`research/wo/WO-T4_golive.md`（bootout 會先開放 YouTube）。
- Owner 用 **IP 版連結**（`~/.secrets/tvgate.links.txt`；`.local` 被瀏覽器升級 https 而失敗，Owner 決定不修、不換 MacBook 鑰匙）。
- 待 Owner：兩支 iPhone 用 AirDrop 收 IP 連結、Safari 加入主畫面；M9 替 MacBook（192.0.2.26）與電視（MAC 02:00:AA:BB:CC:DD→192.0.2.69）做 DHCP 保留。
- 之後 Opus＋Owner 跑 `research/PLAN_v4_ui.md` WO-T4 驗收 1～7（不委派）。另有 WO-T2b（闔蓋含 Power Nap、kickstart、手動啟用停回秒數）。
- 已知小問題（未修，待 Owner 說）：網頁每 15 秒輪詢都寫進 `~/Library/Logs/tvgate.log`、無輪替（分頁常開約 1.3MB／天）。已知限制 3 條見 WO-T4_golive.md。

**B. v5 實驗（電視 App 自當 adb 連線端，不需要 Mac）——問題 1、2 PASS，常駐觀察中**
- 背景：Owner 想把控管放 Cloudflare，電視上 App 持「額度」、快用完向 CF 要、用完自己停用 YouTube。前提是 App 能自己下 adb → 先做實驗。
- 計畫 `research/PLAN_v5_spike.md`（APPROVED＋檔尾「實機發現與計畫修正」）；原始碼 `tvagent/`（純 Java，Docker 映像 `cleanyourtv-android-build-amd64` 編譯；`sh tvagent/build.sh`、`sh tvagent/test.sh`；OrbStack 要開著）；實機紀錄 `research/batches/tvagent/NOTE.txt`。
- 結果：App 自連 127.0.0.1:5555 停用/啟用 tkey PASS；`adb reboot` 與冷開機後 82 秒自啟 PASS；每分鐘鬧鐘心跳 adb=ok；PSS 約 12MB。
- **TCL 必要設定**（已對 tvagent 下過，重開仍在；正式 App 首次授權後要自己下）：`cmd appops set <pkg> APP_AUTO_START allow`、`am set-standby-bucket <pkg> active`、`dumpsys deviceidle whitelist +<pkg>`。
- **TCL 實機坑**：①FreezeManager（`persist.sys.freeze=true`）閒置十幾秒就凍結程序，前景服務也凍，廣播／鬧鐘可解凍 → 心跳必須用 AlarmManager。②無桌面圖示 App 待機分類 50，鬧鐘延 24 小時。③`APP_AUTO_START` 預設 ignore，收不到 BOOT_COMPLETED。④電視開機久了 adbd 授權卡「prompt pending」不跳視窗 → 重開電視。⑤Conscrypt 私鑰物件取不到 publicExponent → 公鑰一律從金鑰檔 DER 解析。
- App 金鑰已在電視「一律允許」（Owner 早上第二次授權有勾）。
- **下一步**：Owner 指定 **10-30 19:00** 讀 `adb -s 192.0.2.69:5555 shell run-as com.example.tvagent cat files/agent.log`：清醒時段心跳間隔 ≤3 分鐘、pid 變化次數、`dumpsys meminfo com.example.tvagent` 的 TOTAL PSS；記進 NOTE.txt → 回報 Owner，由 Owner 決定是否開 v5（電視 App 額度＋Cloudflare）。v5 開工需新計畫＋挑戰（含：額度只在 YouTube 前景扣、用 elapsedRealtime、加值編號防重、平常 1～5 分鐘回報、CF Access 保護家長頁、App 被停用要通知）。
- 收尾不做 v5 時：只 `adb uninstall com.example.tvagent`，**不要撤銷 USB 偵錯授權**（會清掉 Mac 金鑰，v4 斷線）。
- 測試向量 `tvagent/testdata/`（真 `adb keygen`＋openssl 產生、從未授權）不得改。

### 現況快照（10:00）
- v4 執行中、YouTube 鎖定；tvagent 每分鐘心跳；tkey（T 鍵 App）啟用；電視 09:5x 冷開機過。
- git：main 已 push，無 worktree；`memlog/mem.csv` 由 launchd 持續寫入（不用 commit）。

# （前一版）交接 2026-10-30 03:00～09:50

## v5 實驗（電視 App 自當 adb 連線端）進行中（2026-10-30 09:50）
- 計畫 research/PLAN_v5_spike.md（APPROVED＋檔尾實機發現）；App 原始碼 tvagent/；實機紀錄 research/batches/tvagent/NOTE.txt；anchor 有工單狀態。
- 已證實：App 能自連本機 adb 停用/啟用 App；開機自啟與每分鐘鬧鐘心跳可行（需 shell 設 APP_AUTO_START allow、standby bucket、deviceidle 白名單）；TCL 會凍結背景 App，鬧鐘廣播可解凍。
- 待做：冷開機（Owner 拔插頭）、24 小時常駐觀察（10-31 09:45 讀 agent.log、PSS）；之後 Owner 決定是否開 v5（Cloudflare 額度）。tvagent 目前裝在電視、每分鐘心跳，移除：adb uninstall com.example.tvagent（不要撤銷 USB 偵錯授權）。

## 新 session 從這裡開始：時間管理 v4 **已上線（2026-10-30 07:22）**，WO-T4 安裝與驗收進行中
- Mac 瀏覽器開 .local 連結失敗：瀏覽器自動改用 https 連 8790（log 出現 TLS 握手的 400）；IP 版連結可用。.local 解析正常（192.0.2.26）。IP 會變，建議 M9 也替 Mac 做 DHCP 保留（Owner 設）。
- 07:22 bootstrap，YouTube 已鎖；回滾見 research/wo/WO-T4_golive.md。已知小問題：網頁每 15 秒輪詢都寫進 tvgate.log（無輪替，分頁常開約 1.3MB／天），待 Owner 決定是否修。
- **WO-T3 DONE**（03:00）：SOL 修正（Mac 休眠中含 Power Nap 暗喚醒不鎖電視；sleep/wake 測試競態改確定性；44 測試）→ GEMINI＋DSF 第 4 輪皆 PASS（research/wo/WO-T3_review4_*.md）→ Opus 審定：worktree 5/5 OK、merge 後 main 5/5 OK、log mtime 不變、只動 3 檔 → merge 0000010、push；worktree 已移除。PLAN_v4 失效表已同步休眠排程行為。
- **預覽**（假 adb，不控制電視）：scratchpad 的 preview 目錄，port 18790，PID 在 preview/pid；截圖 research/wo/WO-T3_preview_375.png、WO-T3_preview_desk.png。預覽不跨 session，要重做見下方舊段「預覽」。
- **上線材料**：research/wo/WO-T4_golive.md（指令、回滾、已知限制 3 條）。鑰匙 `~/.secrets/tvgate.tokens.json`、連結 `~/.secrets/tvgate.links.txt`（皆 600）已產生。
- **下一步**：Owner 說「上線」→ 照 WO-T4_golive.md bootstrap → PLAN_v4_ui.md WO-T4 安裝與驗收 1～7（Opus＋Owner，不委派）。另有 WO-T2b（闔蓋含 Power Nap 實測、kickstart、手動啟用停回秒數）。
- 目前：v3/v4 服務都沒跑；YouTube 開放。

# （前一版）交接 2026-10-30 02:30

## ★ 新 session 從這裡開始：時間管理 v4 收尾（WO-T3 審查 → 預覽網頁 → 等 Owner 上線）
- **先讀**：`.claude/.team-dev-anchor.md`（分工與每張工單狀態，最上面幾節）、`research/PLAN_v4_ui.md`（v4 規格，APPROVED）、`research/PLAN_v3_service.md`（v3 規則）。
- **分工（Owner 本 session 指定）**：大腦 OPUS high；工人 **TERRA high priority**（`codex exec -m openai/gpt-6.1-terra -c model_reasoning_effort=high -c service_tier='"fast"'`）；審核 GEMINI FLASH high＋DEEPSEEK FLASH max **平行**；接手 SOL medium。Owner 02:00 去睡：「你自主做，做好再開網頁給我」。
- **目前狀態**：
  - v4 已 merge 到 main（3835da0，已 push），但 Opus 在 main 跑測試 `test_hook_sleep_wake_and_grace` 連 3 次失敗（測試競態）＋發現真 bug（Power Nap 暗喚醒會把 YouTube 鎖回，違反「Mac 不在＝開放」）→ WO-T3 ESCALATED。
  - **SOL medium 已交件**（02:30）：worktree `/Users/example-owner/TV-wt-tvgate5`（分支 `tvgate-v4fix`），改 gate.py／test_gate.py／README.md，未 commit。prompt `research/wo/WO-T3_sol_prompt.txt`，回報 `research/wo/WO-T3_sol_final.md`。
- **下一步**：
  1. 審查 SOL 的修正：GEMINI＋DSF 平行（prompt 範本 `research/wo/WO-T3_review_prompt3.txt`，加上 SOL prompt 的檢查項），第一行 PASS/FAIL。
  2. Opus 審定：在 worktree 跑 `python3 test_gate.py` 5 次全過、`~/Library/Logs/tvgate.log` mtime 不變、`git status` 只有 3 檔；**merge 後在 main 再跑一次**（上次就是 main 上才暴露）。
  3. 接手後仍 FAIL → 停下回報 Owner。
  4. 通過 → commit、merge、push、移除 worktree。
  5. 開**預覽網頁**給 Owner（假 adb，不控制電視）：做法見下方「預覽」；用 `open` 在 Mac 瀏覽器打開，告訴 Owner 這是預覽。
  6. 準備上線但**不上線**：產生 `~/.secrets/tvgate.tokens.json`（爸爸 iPhone／媽媽 iPhone／MacBook，600）、`python3 tvgate/gate.py --links` 的連結先存好。上線（WO-T4）要等 Owner 說「上線」。
- **上線前狀態**：v3 服務 01:10 已暫停（bootout；SIGTERM 時已開放 YouTube）；plist 在 `~/Library/LaunchAgents/com.example.tvgate.plist.paused`（防登入自啟）；舊 token `~/.secrets/tvgate.token` 留著當回滾；回滾 tag `tvgate-v3`。YouTube 目前開放。
- **預覽**：`scratchpad` 不跨 session，重做：建目錄放假 adb（`pm list packages -d` 依 locked 檔回 youtube.tv、disable-user 建 locked、pm enable 刪、`dumpsys usagestats` 回 `tvgate/testdata/usagestats_events_sample.txt`、`dumpsys activity` 回桌面）、假 hook（`sleep 99999`）、tokens.json；`TVGATE_HOME=… TVGATE_TOKEN=… ADB=… TVGATE_HOOK=… TVGATE_NOTIFY=true TVGATE_PORT=18790 TVGATE_LOG=… nohup python3 tvgate/gate.py &`，網址 `http://127.0.0.1:18790/#t=<token>`。headless Chrome 最小寬約 500px，要看手機版用 375px iframe 包起來截圖。
- **WO-T4 上線驗收**：見 PLAN_v4_ui.md「WO-T4」。另有 WO-T2b（v3 欠的實機項：闔蓋、kickstart、手動啟用停回秒數，含 Power Nap 闔蓋實測）。
- **Owner 已裁定（本 session）**：Mac 不在／休眠＝YouTube 開放；Mac 回家時有人在看 YouTube → log＋通知、等離開才鎖；孩子手動在設定啟用可接受；投放家中不用（未測）；M9 DHCP 保留 Owner 自己設（未做，MAC 02:00:AA:BB:CC:DD→192.0.2.69）；網頁 Mac/iPhone 共用；排程＝時間點切斷；設備＝爸爸 iPhone／媽媽 iPhone／MacBook；之後 iPhone 要接手 Mac 當管理設備、兩支手機都能管（另開工單，含推播）。
- **實機事實**：停用 YouTube 後桌面圖示消失、T 鍵／語音無反應、無 YouTube 鍵／瀏覽器／Kids；設定頁有「啟用」鈕；播放中鎖 2 秒回桌面；**電視重開會把 YouTube 改回啟用**；待機時 adb 可連（偶發斷約 1 分鐘）；8765 被 Hermes 占用，tvgate 用 8790。

# （舊）TCL 55P715 瘦身 交接（2026-10-29 22:00）

## （已完成）時間管理工單開工：時間管理工單（Owner 10-29 23:45 開工）
- **Owner 裁定**：現在開工，不等 11/1 的記憶體回量（memlog 照跑，11/1 再看）。服務先跑在 Owner 的 MacBook（Owner 在家時才管控），小主機上線再搬（anchor 20:15 裁定）。
- **先讀**：
  - `research/00_REQUIREMENT.md` 的需求 B 和「YouTube 鎖定範圍定義」。
  - `research/PLAN.md`：§2、步驟 2（手動閘門測試）、步驟 4（服務與驗收），以及檔尾的挑戰裁定表。
  - `research/RUNBOOK.md` 階段 C。
  - `research/R3_selfhost_adb_MUSE.md`。
- **開工前先問 Owner**：
  1. Mac 不在家或休眠時，YouTube 預設是鎖住還是開放？PLAN 的設計是「連不上就維持最後狀態」，所以授權中如果斷線，YouTube 會一直可以看。
  2. iPhone 投放和語音入口，要在步驟 2 實機測。
  3. M9 的 DHCP 位址保留（要 Owner 同意）。
  4. Mac 的休眠問題：C-10 原本裁定「Mac 不跑服務」，已被 20:15 裁定推翻，要重新決定怎麼處理休眠，以及服務怎麼掛在 launchd。
- **PLAN 和現況不一樣的地方**：
  - PLAN 寫 Linux 小主機加 systemd，現在改成 Mac 加 launchd。
  - PLAN 寫 Projectivy，實際用的是 CleanYourTV 桌面（`com.gathin.tvhome`），App 列有 YouTube 格。YouTube 停用後那格點下去會怎樣，要測。
- **YouTube 入口（補進 PLAN §2 入口表）**：
  - T 鍵：會開 `com.example.tkey`，再由它開原廠 YouTube。YouTube 停用時，`getLeanbackLaunchIntentForPackage` 會回傳 null，按了不會有反應（這是推論，要實測）。
  - 遙控器 YouTube 鍵（keyCode 4065）：由 `TclShortCutKeyManager` 照 `/product/etc/shortcutkey.conf` 直接開 `com.google.android.youtube.tv`。這支遙控器有沒有這顆鍵還沒確認。
- **已排除**：
  - Family Link：Android TV 11 不支援。
  - 大人帳號的 Shorts 上限設 0：在電視上沒效。
  - SmartTube：Owner 實測後否決。
  - 結論：電視上的 Shorts 擋不掉，只能靠管總時數。
- **其他**：防繞過以「一般使用擋得住」為目標。`.claude/.team-dev-anchor.md` 開頭寫的「只授權研究」已經過時，以本檔為準。
- **環境**：
  - adb 192.0.2.69:5555 已授權。
  - Docker 映像 `cleanyourtv-android-build-amd64` 可以編譯 APK，`tkey/build.sh` 是範例。
  - repo 是 `github.com/example-org/tcl-tv`（private），改完要 commit 並 push。家人照片和 `memlog/raw` 不上傳（已寫在 `.gitignore`）。

## 新 session 開工先讀
1. 本檔
2. `.claude/.team-dev-anchor.md`（分工、Owner 裁定、實機進度）
3. `research/PLAN.md`（v2 計畫；§1 瘦身、步驟 3b 換桌面）
4. `research/batches/*/NOTE.txt`（每批結果）、`research/ref/`（CleanYourTV 同平台參考文件）

## 設備與連線
- TCL 55P715，ro.product.model=BeyondTV2，board rtd285o（RT2851），Android TV 11，fingerprint `TCL/BeyondTV2/BeyondTV:11/PPR1.180610.011/AR05`（UK 版韌體），2GB RAM，4 核 A55（32-bit 使用者空間），安全性修補 2021-12-05。
- Mac 已裝 adb（`brew install --cask android-platform-tools`）。電視 `192.0.2.69:5555`（DHCP，MAC 02:00:AA:BB:CC:DD），已授權這台 Mac。連線：`adb connect 192.0.2.69:5555`。
- HOME 目前＝`com.google.android.tvlauncher`（原廠）。

## 目前狀態（全部可還原）
- 停用中 15 個（清單 `research/batches/keep_disabled.txt`）：
  com.tcl.bi、com.tcl.overseasappshow、com.tcl.messagebox、com.tcl.waterfall.overseas、com.tcl.esticker、com.google.android.feedback、com.google.android.partnersetup、com.google.android.tvrecommendations、com.tcl.tv.tclhome_passive、com.tcl.copydatatotv、com.tcl.MultiScreenInteraction_TV、com.amazon.amazonvideo.livingroom、com.google.android.videos、com.google.android.youtube.tvmusic、com.google.android.play.games
- Hami Video 已解除安裝（user app，Owner 決定不留）。
- 還原單一套件：`adb -s 192.0.2.69:5555 shell pm enable <pkg>`。
- 效果：MemAvailable 剛開機 542MB → 約 615MB；Owner 冒煙測試通過（YouTube／Netflix／四季／Spotify 可播、遙控器全鍵、HDMI 切換、Wi-Fi 頁可進）。HBO Max／Disney+ 會員過期無法測播放。

## 關鍵發現：TCL 開機會把停用的套件還原
- 1a 那 5 個一直停得住；其餘 10 個（含 `pm uninstall -k --user 0`、appops）每次重開都被還原／裝回。
- 線索：`persist.tcl.whitelistpath=/data/data/com.tcl.versionUpdateApp/shared_prefs/whitelist_data.xml`；`com.tcl.versionUpdateApp` 的 `SystemUpdateReceiver` 收 BOOT_COMPLETED（priority 1000）。
- 有效做法：`pm disable-user com.tcl.versionUpdateApp` 後重開 → 更新程式自己被重設回啟用，但清單不再被還原；之後連續 `adb reboot` 2 次 15 個仍停用。**機制未確認**。
- 停用元件（SystemUpdateReceiver）會被拒：shell 不能改 system uid App 的元件狀態。
- 被還原時：`research/batches/reapply.sh`（檢查→停更新程式→重套→建議再重開）。
- **尚未驗證冷開機（拔插頭）**。Owner 很在意：過去每 2～3 天要拔插頭重開一次。

## 下一步（Owner 指示：今天把 CleanYourTV 做完）
1. **拔插頭冷開機測試**（Owner 操作：拔 30 秒→插回開機）→ 跑 `reapply.sh` 看 15 個是否仍停用。被還原就重套＋再測一次冷開機，結果記進 `research/batches/upd/NOTE.txt`。
2. **CleanYourTV 極簡桌面**（Owner 要極簡 launcher；首頁目前極度雜亂）：
   - 先讀官方 repo `github.com/Gathin23/cleanyourtv` 的 `PROMPT.md`、`README.md`、`launcher/` 的 build 腳本，**整段讀完再執行**（第三方腳本）。
   - **只用它的 launcher 部分**；它的停用清單**不要跑**：沒有 P715／BeyondTV2 清單，而本機已手動停好 15 個。若 PROMPT 流程會自動套清單，跳過該步。
   - 同版韌體參考：`research/ref/devices_tcl-beyondtv4-uk-ar05.md`、`research/ref/beyondtv4-uk_debloat.txt`（BeyondTV4，非本機，只作參考）。
   - 編譯需要 JDK＋Android SDK（Mac 上尚未安裝；安裝前告訴 Owner 大小與內容）。
   - 首頁只放：YouTube、Netflix、四季線上（tv.fourgtv.video）、Spotify、Disney+、HBO Max（com.wbd.stream）＋HDMI 輸入（Owner 的 Switch 接 HDMI）。
   - 順序：裝好新 launcher 並驗證能接手 HOME → Owner 測遙控器 Home／返回／訊號源鍵／HDMI 切換、五個 App → 才停用原廠 `com.google.android.tvlauncher`（約 80MB PSS）→ 重開＋冷開機各驗證一次 HOME 沒被搶回。
   - 停用原廠桌面前確認 adb 可連（出事時 `pm enable com.google.android.tvlauncher` 還原）。
3. 2～3 天後回量 `cat /proc/meminfo`（MemAvailable、SwapFree）跟今天比，並問 Owner 是否還有卡死。

## 不要碰
- `com.tcl.tv`、`com.tcl.tvinput`、`com.tcl.tvpassthrough`、`com.tcl.keycustomfunctionservice`、`com.tcl.partnercustomizer`、`com.tcl.settings`、`com.tcl.initsetup`、`com.tcl.factory.view`、`com.tcl.inputmethod.international`、`com.tcl.systemserver`、`com.tcl.autopair`、`com.tcl.xian.StartandroidService`、`com.google.android.gms`、`com.android.vending`、`com.google.android.apps.mediashell`、`com.google.android.tv.remote.service`、`com.google.android.tungsten.setupwraith`、Widevine／DRM。
- TCL 媒體類：`com.tcl.ui_mediaCenter`、`com.tcl.gallery`、`com.tcl.miracast`（同版韌體上是 HDMI 底層）。
- `com.tcl.guard`（研究有爭議）、`com.google.android.katniss`（Owner：語音助理保留）。
- 不恢復原廠設定；不動路由器。

## Owner 裁定摘要
- 瘦身先做；時間管理（YouTube 單次授權）先用 MacBook 跑，小主機上線再搬；目前尚未開工，需另開工單。
- 白名單 App：YouTube、Netflix、Disney+、HBO Max、四季線上、Spotify。
- 防繞過以一般使用擋得住為目標。
- 投屏不用。TCL 更新程式可停（電視很久沒更新）。

## 10-29 22:40 更新（第二個 session）
- 冷開機測試 2 次通過（21:52、22:36）：15 個維持停用。紀錄 research/batches/upd/NOTE.txt、launcher/NOTE.txt。
- CleanYourTV launcher 已上線：HOME=com.gathin.tvhome，原廠 com.google.android.tvlauncher 已停用（重開＋冷開機都沒被搶回）。只用 launcher，沒跑它的停用清單。原始碼與修改在 scratchpad（臨時目錄），APK 存 research/tvhome.apk；改動摘要見 research/batches/launcher/NOTE.txt。
- 語音鍵正常（重裝桌面後要重開一次才恢復）。
- 桌布：Owner 可用本機桌布編輯器（匯出 1920×1080 JPG 到下載資料夾），提供檔案後由 Claude 推到電視（推法見 launcher/NOTE.txt）。目前電視桌布待替換。
- 未做：2～3 天後回量 /proc/meminfo、問 Owner 有沒有卡死；結束時是否關閉網路 ADB（時間管理還要用，先保留）。
- 記憶體紀錄（22:47 上線）：Mac launchd `com.example.tvmemlog` 每小時執行 `memlog/pull.sh`；電視在線就記 `mem.csv`（可用記憶體／Swap／前景 App），`raw/` 存 procstats（電視約保留 15h），並存 events 日誌（將電視 events 緩衝區設為 4MB 約 13h；預設 256K 約 30 分鐘）。停用：`launchctl bootout gui/$(id -u)/com.example.tvmemlog`。
- Owner 裁定（10-29 22:50）：先跑 2～3 天記憶體紀錄（約 11/1），看完瘦身效果再開「MacBook 控管 YouTube 時間」工單。開工單時先問：Mac 不在家時預設鎖住還是開放；iPhone 投放與語音入口要實機測。
- 待 Owner：App 列不透明度數字（編輯器預設 60%，寫入電視 config.json 的 dock_opacity，不必重編）；自製桌布 JPG（editor.html 匯出後給檔名）。

## 10-29 23:25 更新：T 鍵改開 YouTube
- T 鍵（keyCode 4020）由 system_server 的 TclShortCutKeyManager 處理，對應表在唯讀的 /product/etc/shortcutkey.conf；它對 action `com.tcl.partnercustomizer.tkey` 做隱式 startActivity，本機只有 partnercustomizer 的 WithIcon 會接。
- 解法：自製無畫面 App `com.example.tkey`（原始碼 tkey/，`sh tkey/build.sh` 用 Docker 映像編譯）宣告同一個 action，Owner 在選單選「一律」→ T 鍵直接開原廠 YouTube（已實測）。改 SmartTube：改 Main.java 的 TARGET 與 manifest 的 queries 後重編重裝（同一把 debug.keystore 才能覆蓋安裝）。
- 還原：`adb -s 192.0.2.69:5555 uninstall com.example.tkey`。
- Shorts：大人帳號在手機設 Shorts 上限 0，重開後電視仍有 Shorts → 官方 App 在電視上擋不掉，SmartTube 32.68 實測後 Owner 否決（UI 不理想、進影片太慢），已解除安裝（10-29）。
