# R8：TCL TGuard（com.tcl.guard）公開資料調查

查詢日期：2026-10-30。來源以「已 WebFetch 讀到內容」與「只見搜尋摘要」區分；搜尋摘要未驗證原文的，會特別標註。

## 結論先講

- 公開資料中，直接提到 `com.tcl.guard` / TGuard 的內容很少，只有三處有實質內容：一個 VPN 論壇串文（2024 年 10 月，含 `dumpsys ... TGuardService --ma mKillWhiteList <pkg>` 白名單指令）、Bayton 系統 App 資料庫（`pm disable-user` / `pm uninstall` 指令與警告）、以及 Universal Android Debloater 的一個 PR 討論。
- 白名單指令的來源是論壇轉引的 Telegram 貼文（二手），作者沒有貼出自己的測試結果。重開機後失效，論壇說要每次重新下。這和你觀察到的「TGuard 砍程序」相符，但不是獨立驗證。
- 找不到 `tguard-kill`、`sys.tguard.restart`、`IAMSTGuardInjector`、`INT_TGUARD_ENABLE`、`KILL_TGUARD` 相關的任何公開內容。找不到 `persist.sys.freeze` / FreezeManager 的 TCL 公開資料。
- 找不到 dontkillmyapp.com 的 TCL 條目（該頁回 404）。
- 找不到已確認的「停用 com.tcl.guard 會造成開不了機」案例；但也沒有人報告停用後的長期結果。

---

## A. 直接相關（提到 tguard / com.tcl.guard）

### A1. VPN Client Pro 論壇串 "TCl Android and Google Tv (n. 2)"（最關鍵）
- 網址：https://colucci-web.it/forum/viewtopic.php?p=8452
- 日期：2024-10-16 至 2024-10-24（已 WebFetch）
- 機型/系統：原文發文者未說明機型與 OS。串中引用的 Telegram 貼文說是 **TCL 43C735**，OS 版本未提。
- 問題：VPN App 不會開機自啟、會無故斷線（30 秒到數小時不等）。發文者懷疑是系統強制斷線。
- 已試但無效：開啟 connect on startup / connect on demand；關閉省電權限；"open before close" 多次。
- 版主建議：Global options → App settings → 勾選 **Acquire WakeLock**（版主回覆，未附結果）。
- 二手指令（Telegram 貼文，逐字）：
  - `dumpsys activity service TGuardService --help`（用來確認有 `--ma` 選項）
  - `dumpsys activity service TGuardService --ma mKillWhiteList com.tailscale.ipn`（Tailscale 範例）
  - 發文者提議給 VPN Client Pro 用：`dumpsys activity service TGuardService --ma mKillWhiteList it.colucciweb.vpnclientpro`；版主回：「from what you wrote it should be correct」
- 作者宣稱的效果（二手）：Tailscale 不再被背景砍掉。
- 副作用／持續性（二手）：**重開機後失效，需每次重新執行**；用遙控器電源鍵只是 suspend，不會真的關機，所以設定會保留。貼文未回報其他副作用。
- 同一段 Telegram 引文另提到 help 輸出列出的白名單可加 `com.tcl.logkit,com.tcl.bi`（二手）。
- 注意：發文者本人沒有執行指令、也沒有回報結果。

### A2. 同一論壇的 Tailscale 相關引述（搜尋摘要，未另外 WebFetch）
- 搜尋結果提到另一個 URL：https://www.colucci-web.it/forum/viewtopic.php?p=8482 ，WebFetch 回傳內容與 A1 同一串（同一串文章的不同錨點），沒有新增資料。
- 另有摘要指出一位 Tailscale 使用者說「在 stock Android 上把 Tailscale 設為 Always-on VPN 就不會被殺」，但未在 TCL 上測試（摘要來源未驗證原文）。

### A3. 同論壇較早的串 "Android (and Google) TV on TCL Smart TV"（沒有提 TGuard，但有背景斷線資訊）
- 網址：https://www.colucci-web.it/forum/viewtopic.php?p=6761（已 WebFetch）
- 日期：2023-10-26 至 2024-06
- 機型：TCL 65" miniLED（前一台 55C725 QLED）；OS 版本未說。試過 Android TV 9 映像，發文者說 Android TV 10 與 Google TV 也有同樣問題。
- 問題：VPN 無法開機自啟，斷線；IPVanish、CyberGhost、PureVPN 也有同樣問題。
- 版主（2023-10-27）：自啟依賴 BOOT_COMPLETED / QUICKBOOT_POWERON，若沒收到是系統端問題；斷線歸因為「裝置沒讓背景服務持續執行」的 OS 問題；建議同 A1 的 Acquire WakeLock。
- 版主（2023-10-28）：「all seem to be problems caused by the manufacturer」。
- 結果：沒有記錄解決。發文者 2024-03 賣掉電視，沒回報修法。
- 副作用：無。

### A4. Bayton System App Database — com.tcl.guard
- 網址：https://sysapps.bayton.org/packages/com.tcl.guard （已 WebFetch）
- 日期：頁面資料顯示版本範圍 2.20.13_3f95962_250325 → 2.20.30_c16155d_260318（尾碼疑似日期，未確認）。頁面「最後更新」約 2026-03（搜尋摘要）。
- 機型：7 個裝置樣本，全部 TCL；APK 16.6–20.1 MB；targetSdk 34–35。
- 標籤：TGuard（主）、Smart Manager（少數）。
- 權限：未見 kill / process / usage-stats 類權限；有 WAKE_LOCK、FOREGROUND_SERVICE、RECEIVE_BOOT_COMPLETED、以及 `com.tcl.guard.onlinesupport.aidlservice`、`com.tcl.ttvs.deviceauthen.permission.READ_DNUM` 等廠商權限。
- 簽章：有三個不同憑證，頁面推測是 TCL 自家金鑰、以 platform 權限執行（未給 hash）。
- 指令（逐字）：
  - 停用：`adb shell pm disable-user --user 0 com.tcl.guard`
  - 移除：`adb shell pm uninstall -k --user 0 com.tcl.guard`
  - 重新啟用：`adb shell pm enable com.tcl.guard`
  - 還原：`adb shell pm install-existing com.tcl.guard`
- 警告（逐字）：「Verify on a test device first - this database doesn't yet classify which packages are safe to change.」頁面也說 uninstall 會保留 /system 上的 APK，因此可用 install-existing 還原。
- 副作用：頁面沒有列出任何實測副作用。

### A5. Universal Android Debloater PR #1022 "pkg(tcl): add and update many packs"
- 網址：https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/pull/1022 （已 WebFetch）
- 日期：2025-07-19 開；2025-12-09 合併。
- 內容：2025-11-20，Rudxain 貼出 TCL/TCT 三個套件的 manifest，其中包括 `com.tcl.guard`（TGuard）、`com.tcl.ttvs`、`com.tct.contentcenter`。
- 審閱意見：Muriilow（2025-10-29）說網路上找不到什麼重要資料，但描述「seems fair」；AnonymousWP 要求改部分 inline 評論。
- 注意：WebFetch 只讀到前 10 萬字，且頁面沒有明確寫出 `com.tcl.guard` 的分類（例如 Recommended / Expert / Unsafe）。**不能確認它最後是否真的在合併後的 uad_lists.json 中、分類為何。**

### A6. 搜尋摘要（未能 WebFetch 驗證原文）
- Reddit r/AndroidTV 1nvs4kj "why do my tcl android tv customizations…"（https://libreddit.winscloud.net/r/AndroidTV/comments/1nvs4kj/why_do_my_tcl_android_tv_customizations 等）：摘要稱 Projectivy 的無障礙權限會自己被關、Home 鍵退回 TCL 原生 launcher。原文未驗證。
- providence.root.sx/post/768/8874（搜尋摘要，未讀取）：摘要稱「TCL Guard 有一個 mKillWhiteList 內部白名單」。與 A1 同一來源鏈，未獨立驗證。
- Reddit birdcat / tweaktown 的 TCL debloat 文章（https://www.tweaktown.com/news/114177/tcl-google-tv-owner-hands-the-remote-to-claude-code-and-the-ads-dont-survive/index.html、https://reddit.birdcat.cafe/r/AndroidTV/comments/1k9gfpx/release_android_tv_debloat_toolkit_clean_safe/）：兩者 WebFetch 皆回 403，無法讀取。搜尋摘要稱：某 TCL 用戶用 `pm uninstall` 後裝置壞掉、需建立第二個使用者才能恢復出廠；**此說法未驗證**。

---

## B. 間接相關（TCL 或 Android TV 一般的背景被砍討論／debloat）

### B1. TCL 官方支援頁（只有「關閉背景 App」與「強制停止」，沒提背景被砍）
- https://support.tcl.com/fix-app-crashes-on-your-tcl-google-tv
- https://support.tcl.com/en_US/my-tcl-google-tv-is-slowlaggy
- 建議：更新 App、從 Settings > Apps 強制停止、清除快取。沒有提 TGuard、白名單或 autostart。

### B2. TCL 的 debloat 保留清單（WebFetch 自 tv.cobanov.dev）
- 網址：https://tv.cobanov.dev/en/ （已 WebFetch）
- 頁面沒有提 com.tcl.guard，也沒有提背景砍程序。
- 列為「不要停用」的 TCL 套件：`com.tcl.suspension`（Inputs/Source 選單）、`com.tcl.ttvs`（系統更新）、`com.tcl.tv`、`com.tcl.tvinput`（HDMI/天線）、`com.tcl.providers.config`、`com.tcl.initsetup`（部分機型停用會斷 Wi-Fi）、`com.tcl.tcl_bt_rcu_service`、`com.tcl.autopair`（遙控）。
- 頁面也提到：開啟 USB 偵錯時某些 TV 會切斷 USB 埠供電。

### B3. Android TV Debloat Toolkit（seun-novodev）
- 版本說明：https://newreleases.io/project/github/seun-novodev/android-tv-debloat-toolkit/release/v1.1 （搜尋摘要）
- 內容：Windows GUI，透過 ADB 停用 TCL/Google 的 bloatware；v1.1 寫明「disables apps using pm disable-user, not uninstalls」。
- 搜尋摘要稱一位用戶用 Claude Code 透過 ADB 停用 25 個 App，首頁記憶體由 192 MB 降到 83 MB（Reddit r/AndroidTV 1k9gfpx，未驗證原文）。摘要也提到回報某 TCL 停用 launcher 的選項無效，並建議重新啟用 `com.tcl.miracast` 與 `com.google.android.gms`。
- 這些清單沒有 com.tcl.guard。

### B4. Tasker 與電池最佳化（搜尋摘要，未 WebFetch）
- https://tasker.joaoapps.com/userguide/en/faqs/faq-problem.html 與 https://tasker.joaoapps.com/userguide/de/androidpowermanagement.html（摘要）
- 摘要：Tasker 說某些裝置會殺 Tasker，建議加入電池最佳化白名單，並讓 Tasker 的「背景活動」開啟；建議查 dontkillmyapp.com 取得廠商做法。
- 沒有 TCL 專屬內容。

### B5. ADB deviceidle 白名單（搜尋摘要，來源為 Syncthing 的 Android TV wiki）
- 網址：https://git.join-lemmy.org/nutomic/syncthing-android/src/commit/f01f8d26ba69522ded707b66ca813ccf78cd2ccd/wiki/Android-TV-preparations.md
- 指令：`adb shell dumpsys deviceidle whitelist +<package>`（負號移除）。頁面講的是 nVidia Shield，不是 TCL。
- 目前沒有證據顯示這能解 TGuard 的殺程序（TGuard 殺的是 TCL 自家服務，不是 Doze）。

### B6. Android 原生 Cached apps freezer（搜尋摘要）
- https://source.android.com/devices/tech/perf/cached-apps-freezer
- 摘要：Android 11 起可凍結 cached 程序；Android 14 起 cached 程序進入後約 10 秒凍結。凍結的程序可能讓自訂背景功能失效。
- 這是 AOSP 行為，不是 TCL 專屬。與 TGuard 的關係未確認。

### B7. BenQ FAQ（Google TV / Android TV 會自動關閉背景 App）
- https://www.benq.eu/en-eu/support/downloads-faq/faq/product/application/projector-faq-kn-00249.html （搜尋摘要）
- 摘要：Google TV / Android TV 為了省記憶體與電力，會自動關閉背景 App；說目前機型可用的防止方式有限。這是 BenQ 的說法，不是 TCL。

### B8. TCL 上停用 startupwraith 的一則留言（搜尋摘要）
- https://lemmy.blehiscool.com/post/255180/975315
- 摘要：一位用戶在 TCL 上停用 `startupwraith` 後，效果似乎和換 launcher 一樣。未驗證原文，也未確認是否與 TGuard 有關。

### B9. Projectivy / Button Mapper 在 TCL 上無障礙權限自己被關（搜尋摘要）
- 來源：Reddit r/AndroidTV 相關串（見 A6）；另有 Button Mapper 在 TCL C635 上重開機後無障礙會被關的說法（搜尋摘要，來源 xda 串 https://forum.xda-developers.com/t/tcl-c635-any-way-to-set-app-to-startup-at-boot-or-map-buttons-to-open-an-app.4550577/ 未 WebFetch）。
- 未驗證。

### B10. Sony 社群討論（非 TCL，僅作參考）
- Sony Android TV 有「how to close running apps」與 logcat 相關串。與 TGuard 無關，不採信。

---

## C. 找不到或未查到的項目

- `tguard-kill`、`sys.tguard.restart`、`IAMSTGuardInjector`、`INT_TGUARD_ENABLE`、`KILL_TGUARD`：搜尋 `"tguard-kill" OR "com.tcl.guard" adb` 沒有找到任何相關內容。
- `persist.sys.freeze` / FreezeManager（TCL）：搜尋沒有找到 TCL 公開資料。
- `sys_config.xml` 的 `<sysrubish>` 白名單：沒有找到公開資料。
- dontkillmyapp.com/tcl：頁面回 404（WebFetch）。
- "TCL Smart Manager" / memory cleaner 的 TCL 公開設定：搜尋只得到 Samsung、Sony、Roku 結果，沒有 TCL 條目。
- AdbKiller（https://github.com/libxzr/AdbKiller）：Magisk 模組，用來殺 adbd 防資料外洩，與 TCL 無關（已 WebFetch）。
- Tailscale GitHub issue #14906（https://github.com/tailscale/tailscale/issues/14906）：講 exit node，與 TV 背景殺程序無關。
- Huawei cloud phone 的背景 App 白名單文件（https://support.huaweicloud.com/intl/en-us/usermanual-cph/cph_ug_0066.html）：不是 TCL，指令不適用。

---

## D. 試過的查詢（17 次 WebSearch；10 次 WebFetch，其中 7 次成功、3 次失敗）

WebSearch：
1. com.tcl.guard TGuard TCL TV background apps killed
2. "tguard-kill" OR "com.tcl.guard" adb
3. TCL Google TV debloat adb pm disable-user bloatware
4. TCL Android TV apps keep closing background killed Tasker Button Mapper
5. dontkillmyapp TCL
6. reddit TCL TV app killed in background Projectivy Launcher
7. TCL TGuard mKillWhiteList dumpsys TGuardService
8. github com.tcl.guard debloat list TCL android tv
9. TCL TV "Smart Manager" memory cleaner kills apps background setting disable
10. TCL Android TV "persist.sys.freeze" OR FreezeManager apps frozen background
11. xdaforums TCL Google TV background process killed whitelist adb command
12. Tailscale Android TV TCL disconnects killed background
13. "TCL Guard" Android TV app closed reddit
14. TCL Google TV disable "com.tcl" pm disable-user side effects bricked boot loop
15. Android TV Tasker background task killed "TCL" fix battery optimization adb whitelist deviceidle
16. TCL Google TV Smart TV kills apps autostart SmartTube Kodi Plex background not running
17. Google TV Android TV am_kill tguard OR tcl logcat app killed background memory

WebFetch 失敗：
- https://www.dontkillmyapp.com/tcl（404）
- https://reddit.birdcat.cafe/...1nvs4kj（403）
- https://www.tweaktown.com/news/114177/...（403）
- 成功讀取：colucci p=8452、p=8482、p=6761；Bayton com.tcl.guard；UAD PR #1022；tv.cobanov.dev；github libxzr/AdbKiller。

---

## E. 給你的實務提醒（都只是公開資料，需自行驗證）

1. 白名單指令只見於一則二手轉述（Telegram 貼文，TCL 43C735），且重開機會失效。若要試，先在測試機上跑 `dumpsys activity service TGuardService --help` 確認你的韌體有 `--ma` 選項與 `mKillWhiteList` 欄位。
2. Bayton 與 tv.cobanov 都建議先用 `pm disable-user --user 0`，不要用 uninstall。要還原用 `pm enable com.tcl.guard`（或 install-existing）。
3. 沒有找到任何對 com.tcl.guard 停用後的長期（數週以上）回報，也沒有確認的開不了機案例。
4. 沒有找到 TCL 官方承認 TGuard 會殺背景 App 的文件。
