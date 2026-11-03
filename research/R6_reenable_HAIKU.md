# R6：TCL Android TV 停用套件後開機被復原的原因與持久化做法（研究報告）

- 日期：2026-10-29
- 研究範圍：只做網路查詢。未接觸任何設備，未執行 adb。
- 本機：TCL 55P715，ro.product.model=BeyondTV2，board rtd285o / RT2851，Android TV 11，韌體 V8-R851T02-LF1V715，fingerprint TCL/BeyondTV2/BeyondTV:11/PPR1.180610.011/AR05。
- 標記說明：[官方文件] 指 Android 官方網站實際讀到的內容；[社群實測] 指論壇、專案文件或使用者回報；[推測/待驗證] 指作者推論或一般知識，未由來源核實。

## 摘要

1. 目前最直接的線索來自 CleanYourTV 專案的 BeyondTV4 AR05 裝置文件：它寫明 com.tcl.system.server 會在每次開機時把停用的套件重新啟用，並持有 CHANGE_COMPONENT_ENABLED_STATE 與 BootReceiver。[社群實測] 但這份文件的型號是 BeyondTV4/BeyondTV6，本機是 BeyondTV2；套件名也寫成 com.tcl.system.server，與本機的 com.tcl.systemserver 不同。只能當作同平台線索，需在本機確認。
2. 「停用後重開機會恢復」在其他品牌也有回報，例如 TiVo Stream 4K（com.nes.coreservice）與 Amazon Fire TV（AppDisableService）。機制都是系統服務在開機或更新時重套黑名單。不過回報對「每次重開都要重套」的說法不一致。
3. 不 root 的前提下，官方文件確認 pm disable-user 可以指定元件，pm uninstall --user 可以限定使用者。但沒有找到能證明 TCL 上 pm uninstall -k --user 0 撐過重開的可靠實測。只有一則 Sony 論壇回報（僅搜尋摘要，頁面未能開啟）。
4. 韌體更新會把停用的套件全部還原，這點有多個來源提到。它與重開機是不同事件。
5. 查不到：任何 TCL 機型上停用 com.tcl.guard 或 com.tcl.systemserver 後，開不了機、遙控器失效或 HDMI 失效的實際案例。搜尋到的 TCL 風險清單（例如停用 com.tcl.suspension 會無法切 HDMI）來自單一 AI 提示詞文件，未經驗證。

## 1. 誰在把停用的套件重新啟用（問題 1）

### 1.1 CleanYourTV 的 BeyondTV4 AR05 文件 [社群實測]
- 來源（已讀取，經 WebFetch 摘要工具整理，非逐字核對）：https://github.com/Gathin23/cleanyourtv/blob/main/docs/devices/tcl-beyondtv4-uk-ar05.md
- 文件摘錄：
  - 「com.tcl.system.server turns packages back on at every boot.」
  - 「It holds CHANGE_COMPONENT_ENABLED_STATE and a BootReceiver,」
  - 「re-enabled Play Movies, YouTube Music, Play Games, backdrop, Messaging, Camera and Print spooler after each reboot.」
  - 「Keep com.tcl.system.server enabled anyway: it is on the AR10 list's never-disable line.」
- 同一文件提到，那幾個被重新啟用的套件改以 `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` 限制背景執行。文件沒有說明 appops 設定是否跨重開保留。
- 型號與版本：標題為 TCL Smart TV Pro, UK Freeview Play（BeyondTV4 / BeyondTV6, AR05），Android TV 11（API 30），Build RP1A.200622.001 / AR05。
- 與本機的差異：
  - 型號不同：文件是 BeyondTV4/6，本機是 BeyondTV2。
  - 套件名不同：文件寫 com.tcl.system.server，本機是 com.tcl.systemserver。兩者是否同一個套件，未能確認。
  - Android 版本同為 11，但建置與韌體不同。
- 補充 [推測/待驗證]：本機 fingerprint 的 release 標示為 11，但 build ID 為 PPR1.180610.011。依一般 Android build ID 命名慣例（未由來源核實），PPR1 開頭較接近 Android 9 時代的格式。這可能只是廠商客製，建議在本機以 getprop 確認 ro.build.version.release 與 ro.build.version.sdk。

### 1.2 其他品牌的同類機制（非 TCL）
- TiVo Stream 4K：系統套件 com.nes.coreservice 會把使用者移除的 app 還原。社群用 `pm disable-user com.nes.coreservice` 處理。回報對重開後是否需要重套的說法不一致：一則說需要多次停用才會在重開後保留；另一則說每次重開後常需重套。[社群實測，僅搜尋摘要] https://www.aftvnews.com/?p=46214
- Amazon Fire TV（rooted Fire TV 2，韌體 5.0.5.1）：`pm disable com.amazon.tv.settings/com.amazon.tv.settings.tv.AppDisableService` 可讓被封鎖的 app 恢復，文章說重開後仍有效，原廠重置後需重做。讀者留言有反例：出現「class 不存在」的錯誤，以及 `pm list packages -d` 看不到該元件被停用。[社群實測，單一型號舊版本] https://www.aftvnews.com/?p=13903 （已讀取）

這兩個例子都顯示：元件層級的停用，可能被另一個系統服務在開機時重設。但它們不是 TCL。

### 1.3 com.tcl.guard（TGuard）
- 來源（已讀取）：https://sysapps.bayton.org/packages/com.tcl.guard
- 頁面內容：標籤為 TGuard，OEM 預載，版本 2.20.13 到 2.20.30，觀察於 7 個 TCL 機型，Android 13 到 16。宣告 18 個權限，含 RECEIVE_BOOT_COMPLETED。頁面列出 CHANGE_COMPONENT_ENABLED_STATE 為「not found」。
- 與使用者的觀察相反：使用者本機觀察到 com.tcl.guard 持有 CHANGE_COMPONENT_ENABLED_STATE。可能是版本不同（頁面資料為 Android 13 到 16），也可能是資料不完整。以本機 dumpsys 為準。[待驗證]
- TCL Safety Guard 可以逐 app 設定 Auto Launch Permission（開機自啟）。搜尋摘要來自 ablesign.tv 與 OptiSigns 支援頁，未開啟原文。這是自啟控制，不是 component enable 狀態的重設，不能直接解釋 pm disable 被撤銷。[推測/待驗證]

### 1.4 com.tcl.partnercustomizer
- 搜尋摘要顯示，APKMirror 上 TCL Channel（Android TV）的套件名就是 com.tcl.partnercustomizer，最低需要 Android 8.0。這個套件的實際功能不是公開可查的系統服務。[僅搜尋摘要] https://apkmirror.com/apk/shenzhen-tcl-new-technology-co-limited/tcl-channel-android-tv/tcl-channel-android-tv-8-61-1c318a6-release/tcl-channel-android-tv-8-61-1c318a6-android-apk-download
- 一般而言，Google 的 partner customization 機制用來讓 OEM 提供 Leanback 客製化 APK。這點來自一般知識，未由來源核實。[推測/待驗證]

### 1.5 com.tcl.systemserver
- 查不到任何公開來源描述這個套件。sysapps.bayton.org 上的 com.tcl.systemserver 頁面回傳 404，無法開啟。
- 與 1.1 文件中的 com.tcl.system.server 只差一個點，兩者是否同一個套件未能確認。

## 2. 有效的持久化做法（不 root）（問題 2）

- `pm disable-user --user 0 <pkg>`
  - [官方文件] https://developer.android.com/tools/adb （已讀取）：指令存在，`--user` 選項指定使用者。文件沒有說明是否跨重開保留，也沒有說明 user 0 的實際影響範圍。
  - [社群實測] 多數 debloat 指南與工具推薦此做法，且都強調可用 `pm enable` 還原。
  - 本機已觀察：重開後被復原。
- `pm disable-user <pkg>/<component>`（元件層級）
  - [官方文件] 同一頁面：指令參數為 package_or_component，可指定元件。
  - [社群實測] Fire TV 的例子用此方式（見 1.2），但未找到 TCL 的實例。
  - [推測/待驗證] 如果復原來源是 BootReceiver 重套，元件層級的停用同樣可能被重設。需要實測。
- `pm uninstall -k --user 0 <pkg>`
  - [官方文件] 同一頁面：`--user` 指定對象使用者，不指定時會對所有使用者移除；`-k` 保留資料與快取。
  - [社群實測，單一回報] Sony Bravia 論壇的搜尋摘要提到，用此方式移除 Samba TV 套件後，重開後服務未再出現。頁面無法開啟（HTTP 403）。來源：https://community.sony.bg/t5/android-tv/android-7-0-nougat-update-discussion-thread/td-p/2326414/page/19 。型號與版本未能確認。
  - [社群實測，單一回報，未找到原始 URL] 有人提到 TCL 上移除以 TCL 命名的套件後，無法原廠重置，後來用建立第二個使用者的方式恢復。此回報無法追溯來源，不列為證據。
  - [推測/待驗證] 如果 TCL 的還原機制是呼叫 setApplicationEnabledSetting，對已在 user 0 解除安裝的套件可能比較無效；但如果是開機時重新安裝 preinstall 套件，仍會被還原。
- `pm suspend` / `cmd package suspend`
  - 指令存在，效果是 app 無法啟動、圖示變灰。[社群參考，搜尋摘要] https://www.github.com/aistra0528/Hail/blob/master/README_EN.md
  - 未查到是否跨重開保留。[查不到]
- `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`
  - CleanYourTV 文件用於 7 個被重新啟用的套件，但未說明是否跨重開保留。[社群實測]
  - Android AppOps 官方頁面未能讀到相關段落。[查不到]
  - 本機已觀察：重開後被清除。
- `settings put` 或 TCL「自動啟動管理」等 TCL 專用設定：查不到具體設定名稱或保存方式。[查不到]
- 韌體更新：
  - CleanYourTV 文件（已讀取）：「Firmware update: the listed packages come back」，並提供 `tools/check.py`（比對已套用清單與現況）與 `--fix`（還原）。[社群實測]
  - 文件也提到：部分清單會停用電視本身的更新程式，停用後必須重新啟用才會收到韌體更新。[社群實測]

## 3. 停用 com.tcl.guard 或 com.tcl.systemserver 的實例（問題 3）

- 查不到：沒有找到任何 TCL 機型上停用 com.tcl.guard 或 com.tcl.systemserver（或 com.tcl.system.server）後的實際回報，無論成功或失敗。
- 間接線索：
  - CleanYourTV 的 BeyondTV4 AR05 文件把 com.tcl.system.server 列為不能停用（never-disable）。這是清單建議，不是實測。[社群實測]
  - 同一套專案在停用前會寫下還原紀錄，並提供還原指令。[社群實測]
  - 一則 AI 提示詞文件（we0.ai，僅搜尋摘要）列出 TCL 保留套件，包含 com.tcl.tv、com.tcl.tvinput、com.tcl.ttvs、com.tcl.suspension、com.tcl.autopair；並提到停用 com.tcl.suspension 會無法切換到 HDMI、停用 com.android.location.fused 可能造成 boot loop、停用 launcher 會黑屏。作者未驗證，無型號與版本資料。[推測/待驗證] https://we0.ai/articles/android-tv-cleanup-with-claude-code-adb-f-947d9523
  - XDA 上有 TCL 2022 Google TV 的使用者回報：用 ADB 停用預設 launcher 後黑屏，靠 TCL 的 Quick 功能進入設定，才重新開啟 WiFi 與 ADB。[社群實測，單一回報，僅搜尋摘要] https://forum.xda-developers.com/t/tcl-2022-google-tvs-p635-p735-c635-c735-c835-c935.4432635/post-88078083 （頁面重導向後回 403，未能讀取原文）
  - DumbTV 列出 TCL Smart TV Pro G08 於 Android 14，但未標示測試狀態。[社群] https://github.com/humblemuzzu/dumbtv/wiki （已讀取）
- 版本差異：本機是 Android 11；sysapps 對 com.tcl.guard 的資料是 Android 13 到 16；找到的 TCL 停用失敗案例多數是 Google TV 2022 或 Android 14 機型。這些都與本機不同。

## 4. 開機後自動重套（問題 4）

有實例，但都需要人工或外部主機觸發，沒有找到 TV 端的無 root 自動機制。

- CleanYourTV 的 `tools/check.py --fix`：比對清單與現況，還原韌體更新撤銷的部分。[社群實測]（文件已讀取，未實際執行）
- Universal Android Debloater issue #48（Pixel 4a，Android 11，不是 TV）：AOSP 清單在重開後被還原。回報者說「重跑 5 次、每次重開一次」才生效；第一次 factory reset 後執行一次都成功，之後越來越難。[社群實測，單一回報] https://gitlab.com/W1nst0n/universal-android-debloater/-/issues/48 （已讀取）
- TiVo CoreService：據回報需要在每次重開後重新套用，但另一則說法是多次停用後會保留（見 1.2）。
- 網路 ADB：搜尋摘要稱某台 Google TV Chromecast 上 `adb tcpip 5555` 重開後失效。來源 URL 未能確認。若要做外部主機自動重套，需先確認 TV 端的 ADB 網路設定能否跨重開保留，不能只假設。[僅搜尋摘要，待驗證]
- 查不到：沒有找到任何 TCL 機型上，外部主機開機後自動重套並長期穩定運作的實際案例。

## 來源與可信度

| 來源 | 狀態 | 用途 |
|---|---|---|
| https://github.com/Gathin23/cleanyourtv/blob/main/docs/devices/tcl-beyondtv4-uk-ar05.md | 已讀取（WebFetch 摘要） | 1.1，主要線索 |
| https://github.com/Gathin23/cleanyourtv | 已讀取（WebFetch 摘要） | 韌體更新、還原、check.py |
| https://developer.android.com/tools/adb | 已讀取 | pm 指令的官方說明 |
| https://sysapps.bayton.org/packages/com.tcl.guard | 已讀取 | 1.3，版本為 Android 13 到 16 |
| https://sysapps.bayton.org/packages/com.tcl.systemserver | 404 | 無法取得 |
| https://www.aftvnews.com/?p=13903 | 已讀取 | Fire TV 的元件停用例子 |
| https://www.aftvnews.com/?p=46214 | 僅搜尋摘要 | TiVo CoreService |
| https://gitlab.com/W1nst0n/universal-android-debloater/-/issues/48 | 已讀取 | 重套需多次嘗試 |
| https://github.com/humblemuzzu/dumbtv/wiki | 已讀取 | 韌體更新撤銷，TCL G08 Android 14 |
| https://github.com/seun-novodev/android-tv-debloat-toolkit | 已讀取 | 工具的 pm disable-user 做法，未列 TCL 套件 |
| https://developer.android.com/reference/android/content/pm/PackageManager | 已讀取但段落被截斷 | 未能讀到 setApplicationEnabledSetting |
| https://developer.android.com/reference/android/app/admin/DevicePolicyManager | 已讀取但段落被截斷 | 未能讀到 setPackagesSuspended |
| https://developer.android.com/reference/android/app/AppOpsManager | 已讀取但段落被截斷 | 未能讀到 RUN_ANY_IN_BACKGROUND |
| https://community.sony.bg/... 與 .be、.es 相關 thread | 403，僅搜尋摘要 | Sony 單一回報 |
| https://forum.xda-developers.com/... TCL 2022 | 重導向後 403，僅搜尋摘要 | TCL 黑屏單一回報 |
| https://www.tweaktown.com/news/114177/... | 403，僅搜尋摘要 | 未能取得內容 |
| https://www.pulse.bot/... | 連線錯誤，僅搜尋摘要 | 二手彙整，未採信 |

## 對本機的建議（僅研究意見）

以下建議由研究得出，本機實際操作由你決定。我沒有接觸設備。

1. 先確認復原來源，全部用唯讀指令：
   - `pm path com.tcl.systemserver`，確認套件是否存在。
   - `dumpsys package com.tcl.systemserver`，檢查是否持有 CHANGE_COMPONENT_ENABLED_STATE，以及是否有 BOOT_COMPLETED 的 receiver。比對 CleanYourTV 文件的描述。
   - 同時對 com.tcl.guard、com.tcl.partnercustomizer、com.tcl.overseasappshow 做同樣檢查，因為你的觀察已列出這些套件。
   - 測試時，先清 logcat（`logcat -c`），停用一個測試套件後重開，再用 `logcat -d` 找 setEnabledSetting 相關紀錄與發出的 UID 或 package。[推測/待驗證]
2. 依風險排序的做法：
   - 維持現況，每次開機或韌體更新後重跑一次停用清單。風險最低，也是社群最常見的做法，但需要人工或外部主機配合。
   - 對非必要的 com.tcl.* 套件改用 `pm uninstall -k --user 0`，每次只改一個，重開後檢查 `pm list packages -d` 與 `pm list packages --user 0`。還原可能用 `cmd package install-existing --user 0 <pkg>`，此指令為一般知識，未經來源核實。[推測/待驗證]
   - 停用 com.tcl.systemserver 或 com.tcl.system.server 本身：所有找到的來源都建議保留，不建議作為第一步。若要試，須先確認遙控器與 HDMI 能用、確保有 factory reset 或 recovery 路徑，並分批進行。
   - 停用 com.tcl.guard：沒有公開實例，且本機與 sysapps 資料不一致。不建議。
   - appops 與 pm suspend：只當補充，不能視為跨重開的保證。
3. 避免同時使用多種手段，否則找不到真正的復原來源。CleanYourTV 文件與多個工具都建議一次只改少量套件，並保存還原紀錄。
4. 韌體更新：保留 TCL 更新程式可運作，才能收到修正。若更新後清單失效，依 check.py 的概念重新比對與套用。
5. 不要停用遙控器、輸入源切換、藍牙或 ADB 相關套件。這些套件在來源中被列為保留項目，但未經實測驗證。

## 查不到／不確定

- com.tcl.systemserver 的功能與來源（sysapps 頁面 404）。
- com.tcl.system.server（文件）與 com.tcl.systemserver（本機）是否為同一套件、是否存在於 BeyondTV2 上。
- 在 TCL Android 11（BeyondTV2）上，pm disable-user 被重開還原的實際機制與負責的 receiver。
- pm uninstall -k --user 0 在 TCL 上是否跨重開有效。只有 Sony 單一回報，且頁面無法開啟。
- 停用 com.tcl.guard 或 com.tcl.systemserver 後，開不了機、遙控器失效或 HDMI 失效的實際案例：查無。
- 關閉 TCL「自動啟動管理」或以 `settings put` 修改 TCL 設定的做法：查無。
- appops 是否跨重開保留：官方頁面未能讀到，查無來源。
- pm suspend 是否跨重開保留：查無。
- Android 官方 setApplicationEnabledSetting、setPackagesSuspended 與 AppOpsManager 的持久化說明：三個頁面都被截斷，未能讀到對應段落。
- XDA、Sony、TweakTown 等頁面無法開啟，以上只採用搜尋摘要，可信度較低。
- 有一則搜尋摘要提到 TCL 使用者刪除 TCL 套件後無法原廠重置，原始來源未能確認，未列為證據。
- AI 提示詞文件中的 TCL 套件清單（we0.ai、tv.cobanov.dev）未經驗證，不應直接套用。
- 本報告的 [社群實測] 條目，除 CleanYourTV 文件外，多為單一回報或搜尋摘要，不足以作為定論。
