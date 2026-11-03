# R2b｜需求 A 第二位獨立研究員：交叉驗證＋補洞（TCL 55P715 瘦身）

> 研究範圍：只做網路資料查詢與整理。**未連接、未操作任何實機，未執行 adb。**
> 證據等級：[官方文件]＝廠商／Google／AOSP／專案 README 或原始碼；[社群實測]＝有人在同平台或同型號實際做過並回報；[推測/待驗證]＝推論或只在別型號成立。
> 報告語言：繁體中文（台灣用語）。
> 方法註記：依題目要求，第一部分先做獨立研究；但本次流程中 R2 檔在研究初期即已讀取（用以確定核對範圍），無法完全避免先入為主，以下第一部分只寫「我自己實際讀到的頁面」，第二部分逐條核對 R2 引用的 URL。

---

## 第一部分：獨立研究

### 1. 台灣版 TCL Android TV（2020–2021，Android TV 11，RT2851 平台）的預載與 TCL 服務

#### 1.1 P715 出廠預載（官方頁面可確認的）

TCL 全球官網 P715 產品頁列出的預載與服務如下 [官方文件]：<https://www.tcl.com/global/en/tvs/p715>

| 出廠內容 | 來源說法 |
|---|---|
| Netflix（4K HDR 認證版） | 產品頁 "Netflix" 段 |
| YouTube | 產品頁 "YouTube" 段 |
| Amazon Prime Video（認證內建版） | 產品頁 "Amazon Prime" 段 |
| Google Play Movies & TV（`com.google.android.videos`） | 產品頁 "Google Play Movies & TV" 段 |
| Google Play Music、Android Gaming（Play Games） | 產品頁對應段落（年代久遠的文案，實際韌體以 `pm list packages` 為準） |
| MagiConnect 手機遙控／投放 App（即 `com.tcl.MultiScreenInteraction_TV` 對應的手機端） | 產品頁 "Watch Your Way" 段 |
| Hands-free 語音（4 麥克風）＋ TCL AI-IN＋ Google Assistant | 產品頁 "Look, No Hands!" 段；對應 `com.google.android.katniss`、`com.google.android.tv.assistant`、`com.tcl.assistant`、`com.tcl.micmanager` 這組語音套件 |

#### 1.2 台灣在地預載：查不到台灣版專屬清單

- Mobile01 有兩篇 P715 開箱（55P715、75P715），DuckDuckGo 搜尋可找到標題，但 **Mobile01 本體回 403，內文讀不到** [推測/待驗證]：
  - <https://www.mobile01.com/topicdetail.php?f=347&t=6215949>（55P715 開箱，讀不到內文）
  - <https://www.mobile01.com/topicdetail.php?f=347&t=6317077>（75P715 開箱，讀不到內文）
- PTT Android 板有一篇 CleanYourTV 實測文（小米投影機青春版 2019，非 TCL），重點是台灣使用者的通用擔憂：「最怕就是不小心關錯系統背景軟體變磚或是無法遙控器控制，這 app 還有備份恢復設定」；另有推文提到範例電信商 Hami Video 是瓶頸、換啟動器也一樣 [社群實測]：<https://www.ptt.cc/bbs/Android/M.1792863138.A.817.html>
- TCL 台灣官網（tcl-shop.com.tw）經 Bing 搜尋只找到賣場頁，**找不到客服 FAQ／停用預載立場／保固說明**；TCL 美國支援站只有 Google TV 通用卡頓排除（重開機、關背景 App、更新 App、系統更新、開 Quick Start），無 debloat 相關內容 [官方文件]：<https://support.tcl.com/us-googletv-troubleshooting/fix-a-slow-or-laggy-tcl-google-tv>
- 結論：**台灣版 P715 是否有在地影音預載（四季 4gTV、LiTV、Hami、friDay 等）或開機廣告，我沒有讀到任何第一手頁面**。CleanYourTV 有一份 `ktc-4k-smart-tv-hongkong`（台灣販售，預載 friDay、LiTV、myVideo、Hami 等台灣電信 VOD）[社群實測]，但那是 KTC 白牌機，不是 TCL。台灣 TCL 機器的在地預載只能列入待實機驗證（`pm list packages`）。

#### 1.3 同板（RT2851／RT51）實測清單中可確認的 TCL 服務

我實際讀到的兩份同板清單（`tcl-50j7000a`：泰國、V674、client type 與本機完全相同 `TCL-AP-RT2851-S1`；`tcl-43p615`：AR06、RTD2851）：

- <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-50j7000a/debloat.txt>
- <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-43p615/debloat.txt>

兩份共有的促銷／遙測套件（即 R2 §4.1 的核心子集）：`com.tcl.bi`、`android.autoinstalls.config.tcl.device`、`com.google.android.feedback`、`com.google.android.partnersetup`、`com.google.android.onetimeinitializer`、`com.tcl.tv.tclhome_passive`、`com.tcl.waterfall.overseas`（TCL Channel 內容流）、`com.tcl.overseasappshow`（每開機把其他促銷套件叫回來，43P615 頭註明 key entry）、`com.tcl.messagebox`、`com.tcl.esticker`、`com.tcl.notereminder`、`com.tcl.copydatatotv`、`com.google.android.tvrecommendations` [社群實測]。
- 43P615 獨有（50J7000A 無）：`com.tcl.guard`（停用）、`com.tcl.usercenter`（停用）、`com.tcl.dashboard`、`com.tcl.useragreement`、`com.tcl.ui_mediaCenter`、`com.tcl.micmanager`、`com.tcl.smartalexa`、`com.tcl.miracast`、`com.tcl.MultiScreenInteraction_TV`、`com.google.android.backdrop` 等 [社群實測]。
- 50J7000A 獨有行為：`com.tcl.guard` 因 PERSISTENT 而保留；`com.google.android.videos` 不在清單（43P615 註明韌體會每次開機復活它，改用 launcher 藏 tile）；實測 25 個套件停用後 `MemAvailable` 約 495 MB → 約 655 MB，重開機維持停用，Home／HDMI／YouTube／Netflix／Disney+／Live TV 正常 [社群實測]。
- BeyondTV4 英國 AR05（rtd288o，同世代）文件：`com.tcl.system.server` 持有 `CHANGE_COMPONENT_ENABLED_STATE`，每次開機復活 Play Movies、YouTube Music、Play Games、backdrop 等 7 個套件，解法是 `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore`；另 `com.google.android.katniss` 常駐 HOTWORD 吃約 28% 單核，`pm revoke ... RECORD_AUDIO` 後降到 0.4% [社群實測]：<https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-beyondtv4-uk-ar05.md>
- RT2851（Android TV 9，V606）遙控器文件：T 鍵走 `com.tcl.keycustomfunctionservice` → `com.tcl.partnercustomizer` 的 `TKeyActivity`；Netflix 鍵走受權限保護的 broadcast；HDMI 經 `com.tcl.tvpassthrough`，`VIEW` 會解到 `com.tcl.tv/.TVActivity`，故 `com.tcl.tv` 是承重的 [社群實測]：<https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-beyondtv-rt2851.md>

### 2. 「停用後出事」案例（不要碰清單，每條附型號與來源）

| # | 停用的套件 | 出事內容 | 型號／版本 | 來源（實際讀到） |
|---|---|---|---|---|
| 1 | `com.google.android.tungsten.setupwraith` | `com.realtek.advancesetting` 的 `SpeakersChangeService` FATAL EXCEPTION（找不到 setupwraith 的 provider），系統音效設定崩、喇叭音量無法調；還原即修好 | Sony Bravia K-55S25（VU31）、**Android TV 11**；注意：**是 Sony 不是 TCL**，但 log 主角是 Realtek 套件，與本機同 SoC 家族 | [社群實測] <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/994> |
| 2 | `com.google.android.tvlauncher`（直接移除、無備用 launcher 規劃） | ZTE 機器下次開機觸發恢復原廠設定（UAD-NG issue 內兩台 ZTE 866 實測）；CleanYourTV README 另警告：先停原 launcher 會讓 Home 落到 factory test app | ZTE 866（Android TV，版本未註明）；factory-test 警告為通用（未綁型號） | [社群實測] <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/887>；[官方文件] CleanYourTV README（Home 保護段，見 R2 附錄 URL） |
| 3 | `com.google.android.apps.tv.launcherx`＋`setupwraith` 同時停用 | TCL QM7K：Projectivy 與 Google TV launcher 都起不來，只能恢復原廠；另有 Reddit TCL Q7 無遙控器無法重置案例被引用 | TCL QM7K（Google TV，韌體 v321／Android TV 14 等級）；Reddit 例為 TCL Q7（Google TV） | [社群實測] <https://github.com/spocky/miproja1/issues/415>（hammercompation 2026-02-20 留言） |
| 4 | `com.tcl.tv`、`com.tcl.tvinput` | Live TV／tuner／HDMI 掛掉；RT2851 實測 HDMI 經 tvpassthrough 且 VIEW 解到 `com.tcl.tv`，停了等於斷訊號源 | 通用（43P615／50J7000A／RT2851 三份文件一致列 DO NOT DISABLE）；**非單一災情回報，是三份同板清單的共同禁區** | [社群實測] 上述兩個 debloat.txt 頭註＋<https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-beyondtv-rt2851.md> |
| 5 | `com.tcl.keycustomfunctionservice`、`com.tcl.partnercustomizer`、`com.tcl.common.shortcutmenu` | 遙控器按鍵／FallbackHome／T 鍵／快捷選單失效 | 43P615（AR06）DO NOT DISABLE；T 鍵機制見 RT2851 文件 | [社群實測] <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-43p615/debloat.txt> |
| 6 | `com.tcl.autopair` | 藍牙遙控器配不回來 | 43P615／50J7000A 共同 DO NOT DISABLE | [社群實測] 同上兩份 debloat.txt |
| 7 | `com.tcl.guard`（TGuard／Safety Guard）停用或反向操作 | 正反兩面都有回報：43P615 停用無事；50J7000A 因 PERSISTENT 保留；L40S66E 回報停掉後 free RAM 變高（R2 引述，未獨立讀到該頁）；反向：Projectivy issue 中有人停掉 TCL Guard 也救不回自啟動（55Q6C），也有人稱 Guard 是自啟動開關的守門員 | 43P615／50J7000A／TCL 55Q6C（Android TV 14） | [社群實測] 兩份 debloat.txt＋<https://github.com/spocky/miproja1/issues/415>（DnaGit25、GeographicCone 留言） |
| 8 | NVM reset（非 debloat，但是「重置出事」的關鍵反例） | Wi-Fi MAC、Widevine keybox、Netflix ESN 全空（ui-800-3／-121）；只靠恢復原廠修不好，必須原廠重置＋USB 重刷完整韌體；Widevine 仍只回 L3 | TCL 50DP660（Android TV 8，MT5891）／L49C2US；**舊平台，不可直接套用到本機，僅為警示** | [社群實測] <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-50dp660.txt> |
| 9 | 恢復原廠後 GMS／WebView 過舊 | Play 2026 版＋系統內 GMS 13.2.92／WebView 58 → 無法登入 Google、Netflix -121；需手動裝新版 GMS／WebView 蓋掉 /system | 同上 50DP660（Android TV 8）；本機 Android TV 11 風險較低但未驗證 | [社群實測] 同上 |

以下 R2 §4.2 列為「不要碰」但**我找不到 TCL 直接災情**的項目（誠實標示）：`com.tcl.tvweishi`、`com.tcl.appmarket2`（反而被 debloat-toolkit 列為 Safe，見下）、`com.tcl.usercenter`（反例：43P615 實測停用且三重開機正常）、`rc.ota`／`ttvs`（同板兩份清單完全沒出現）、DRM／Widevine 套件（無直接證據；已知 ESN 遺失來自 NVM reset 而非 debloat）、`com.tcl.systemserver` 的「每次開機打開套件」有 AR05 實證但「停掉風險高」本身無停用災情回報。

debloat-toolkit 原始碼與 R2 的矛盾點（我實際讀到原始碼）[官方文件]：<https://raw.githubusercontent.com/seun-novodev/android-tv-debloat-toolkit/main/run_toolkit.py>
- `com.tcl.tv` 在 Advanced 清單標 ⚠️（R2 說法相符）。
- 但同一檔案 Safe 清單直接停 `com.tcl.guard`、`com.tcl.tvweishi`、`com.tcl.appmarket2`、`com.tcl.overseasappshow`、`com.tcl.bootadservice`、`com.tcl.dashboard`、`com.google.android.apps.mediashell`（Mediashell＝Chromecast，R2 列不要碰），且 Safe 清單順手停掉 Netflix（`com.netflix.ninja`，本案要保留）。**這正是「不可照抄固定清單」的證據**。

### 3. 卡頓的硬體面：2GB／16GB Android TV 11 在 2026 年

- Google 官方 Android TV 記憶體指南確認機制（非門檻數字）[官方文件]：<https://developer.android.com/training/tv/playback/memory>
  - 高記憶體壓力的後果：系統服務（音量、畫質面板、語音助理）變鈍、LMK 反覆殺與重啟造成資源尖峰、切回 Launcher 明顯延遲、direct reclaim 導致掉格。這正好對應「按 Home 等好幾秒」「操作卡頓」的症狀。
  - 官方把 ≥2GB＋2160p 列為 Regular（非 low-RAM），但那是給 App 開發者的記憶體預算指引，不是「2GB 夠用」的保證；背景服務一多照樣進 direct reclaim。
- 社群普遍性：r/AndroidTV 2024 年3月名串 "Why are Android TVs so slow in general"（YouTube 等串流 App lag 抱怨）存在，但 Reddit 本體未讀到內文，只能確認題目存在 [社群實測，僅讀到標題]：<https://www.reddit.com/r/AndroidTV/comments/1bb2tal/why_are_android_tvs_so_slow_in_general/>。TCL 美國官方支援頁承認卡頓常見，給的只是重開機／關背景／更新三招 [官方文件]：<https://support.tcl.com/us-googletv-troubleshooting/fix-a-slow-or-laggy-tcl-google-tv>。
- debloat 能改善多少（同板唯一量化）：50J7000A 停 25 套件 MemAvailable 約 495→655 MB（+160 MB），HDMI／串流正常 [社群實測]。Shield（Android 11，非 TCL）整包 profile（停原 launcher＋recommendations＋backdrop＋GameStream＋Projectivy）+163.6 MiB，作者自述不能證明流暢度 [社群實測，型號不同]：<https://raw.githubusercontent.com/mbcrump/shield-projectivy-debloat/main/BENCHMARKS.md>。**沒有「新版 YouTube／Play 服務變肥」的直接量測數據，不要寫成事實**。
- 何時放棄、改用外接盒：
  - Google TV Streamer (4K) 台灣公司貨（聯強）官方建議售價 NT$3,250，僅陶瓷米 [社群實測，媒體報導]：<https://www.techbang.com/posts/118477-google-tv-streamer4k-chromecast-3250>；PChome、momo 皆有售（價格以賣場即時頁為準）。
  - Chromecast with Google TV 已是上一代（Google 被 Streamer 取代），台灣只能找 BigGo 比價／二手，664 筆量級但多為舊貨 [社群實測，僅讀到搜尋摘要]：<https://biggo.com.tw/s/chromecast%20with%20google%20tv/>。
  - 判斷線 [推測/待驗證]：若 `MemAvailable` 長期 <200–300 MB、LMK 頻繁殺行程、且清單已停完促銷／telemetry 仍卡，或 `/data` 可用 <500 MB 且清不出來，就該把錢花在外接盒而非繼續停系統套件。門檻數字是經驗法則，非官方。

### 4. Projectivy Launcher 在 Android TV 11（非 Google TV）＋TCL 的實際回報

- 開發者原帖（經 r.jina.ai 取得，註明透過代理）確認機制 [官方文件]：<https://xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/>
  - "Override stock launcher：try to replace the stock launcher even if it hasn't been disabled (**which is still the preferred way**)"——R2「攔截原 launcher 啟動、原 launcher 還在」的說法相符。
  - 無 root 時凍結 launcher 要自己下 `pm disable-user --user 0 com.google.android.tvlauncher`（原帖 command 段）。
- TCL 搶 HOME 的實報（全部是新機種，非 Android TV 11）[社群實測，型號不同]：
  - #500：TCL 75T7B（Android TV 12、G10、rtd6748、2GB）間歇跳回原 launcher；TCL Safety Guard 在開機把無障礙／自啟動關掉，解法是 `appops set ... APP_AUTO_START allow`＋deviceidle 白名單：<https://github.com/spocky/miproja1/issues/500>
  - #415：TCL C855（Android TV 14）開機不自動進 Projectivy，按 Home 才進；TCL QM7K／QM8K／55Q6C／65C6K 類似；有人同時停 launcherx＋setupwraith 直接變磚重刷；最新進展是 TCL `AppBootPolicy` 的 `autoStartWhiteList` 才是真正的門（65C6K 實證，兩次冷開機）：<https://github.com/spocky/miproja1/issues/415>
- **Android TV 11＋TCL＋Projectivy 的直接回報：我沒找到**。開發者自稱買了 TCL C1 投影機（Android TV 11）想重現 TCL 問題但重現不了（#500 內 spocky 2026-05-18 留言）。R2「在 Android TV 11＋tvlauncher 上會不會搶 HOME 屬推測」的保留是對的。
- 停用原 launcher 的風險與還原（本機適用）：
  - 沒先裝好可用 launcher 就停 → Home 落到 factory test app（CleanYourTV README 原文）[官方文件]。
  - `com.google.android.tvlauncher` 是 HOME priority 2，`set-home-activity` 印 Success 但無效，要先停用（50DP660 Android TV 8 實證；43P615／50J7000A 頭註同理）[社群實測，型號不同]。
  - 還原永遠是 `pm enable com.google.android.tvlauncher`＋`cmd package set-home-activity ...`（原帖 command 段）[官方文件]。
  - Android TV 11 遙控器 App 鍵走 `com.tcl.keycustomfunctionservice`（RT2851 文件）故理論上不受影響——但本機未驗證 [推測/待驗證]。
- PTT 台灣實測（小米投影機）：Clean Your TV App（Android 手機版）連 Wi-Fi ADB 清理後「原來不是硬體太舊，是背景垃圾在搞」，佐證「換桌面＋停背景」對低 RAM 機器的體感差異 [社群實測，型號不同]：<https://www.ptt.cc/bbs/Android/M.1792863138.A.817.html>

---

## 第二部分：交叉驗證（R2 第 0 節＋第 4 節）

核對方法：R2 附錄的 URL 逐一用 webfetch 重新打開。星級／push 日期等 GitHub API 數字未重查（API 未讀），下表只核對「說法與來源內容是否一致」與「證據等級是否恰當」。

### 2.1 第 0 節「先講結論」逐條核對

| # | R2 的說法 | 核對結果 | 依據 URL（實際讀到） |
|---|---|---|---|
| 0-1 | 四支工具只有 UAD-NG 與 CleanYourTV 真的改套件狀態；Projectivy 只換首頁；android-tv-debloat-toolkit 是固定清單 Windows 小工具 | **相符**。UAD-NG FAQ 列出 disable/uninstall 指令；CleanYourTV README 確認 disable＋換 launcher；toolkit 原始碼只有 `pm disable-user`；Projectivy 原帖確認是 launcher＋Override 模式 | <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/FAQ>、<https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/README.md>、<https://raw.githubusercontent.com/seun-novodev/android-tv-debloat-toolkit/main/run_toolkit.py>、<https://xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/>（經 r.jina.ai） |
| 0-2 | 同 client type `TCL-AP-RT2851-S1`、V674 的 50J7000A，停 25 套件 MemAvailable 約 495→655 MB，重開機維持、Home／HDMI 正常 | **相符**（逐字吻合 debloat.txt 頭註：25/25、495→655 MB、reboot 後全維持、YouTube／Netflix／AIS／Disney+／Live TV、HDMI Inputs 正常） | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-50j7000a/debloat.txt> |
| 0-3 | 換桌面單獨做幾乎不省 RAM；推薦列／促銷／開機推送是獨立套件；Projectivy override 是攔截原 launcher 啟動、原 launcher 還在 | **相符**（機制描述與原帖一致；套件獨立性與兩份 debloat.txt 一致）。「幾乎不省 RAM」的量化對比無直接數據，屬合理推論，等級標 [官方文件][社群實測] 可接受但偏樂觀 | 同上＋<https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-43p615/debloat.txt> |
| 0-4 | 本案一律 `disable-user` 不用 `uninstall --user 0`；UAD-NG 官方寫非 Recommended 用 disable、uninstall 造成不穩；disable 可 `pm enable` 還原 | **相符**。FAQ 原文："If you're debloating anything other than Recommended, you should disable rather than uninstall, as uninstalling is known to cause system stability issues."；issue #670 補強相依 App 亂喚醒機制 | <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/FAQ>、<https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/670> |
| 0-5a | 同 V715 版本、XDA P8M 用戶「Android 9（V616）升 Android 11（V715）後效能明顯變差、4K 掉格」 | **查不到**。經 r.jina.ai 讀取該串 page-187，內容是 C715／P8S 韌體版本問答（V662／V715 對照、RT51 刷機），**沒找到效能變差原文**。R2 自己也標註「二手摘要」，應視為未證實 | <https://xdaforums.com/t/tcl-android-tv-model-55p8m-android-recovery-tcl-beyondtv-beyondtv.4025755/page-187>（經 r.jina.ai 取得） |
| 0-5b | BeyondTV4 機器 katniss 常駐吃約 28% 單核，撤麥克風權限後 0.4% | **相符**（AR05 文件逐字：HOTWORD＋ECHO_REFERENCE、28%→0.4%、`pm revoke ... RECORD_AUDIO`） | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-beyondtv4-uk-ar05.md> |

R2 §0 證據等級總評：0-1～0-4 的 [官方文件] 標示恰當（確為 README／Wiki／原始碼）；0-5a 雖在正文標 [社群實測] 但附錄已自首是二手摘要，**建議降級為 [推測/待驗證]**；其餘無不當。

### 2.2 第 4 節套件分類逐項核對＋反例

> 「R2 說法」指 §4.1「通常可停用」與 §4.2「不要碰」。反例標準：同板（RT2851 家族）實測與 R2 相反。

#### §4.1（可安全停用）

| 套件（群） | R2 說法 | 核對結果 | 依據 |
|---|---|---|---|
| `com.tcl.bi`、feedback、partnersetup、onetimeinitializer、autoinstalls | 可停（遙測／歸因） | **相符**（兩份同板清單皆停） | 兩份 debloat.txt |
| `com.tcl.overseasappshow`（先關）、tclhome_passive、waterfall、messagebox、esticker、copydatatotv、notereminder | 可停（促銷） | **相符**（兩份皆停；overseasappshow＝key entry 說法與 43P615 頭註一致） | 同上 |
| `com.tcl.dashboard`、`com.tcl.useragreement` | 可停 | **部分相符**：只在 43P615 出現，50J7000A 無此二套件。不是反例，但 R2 未註明「同板不同地區韌體有無差異」 | 43P615 有，50J7000A 無 |
| `com.google.android.tvrecommendations` | 可停 | **相符**（兩份皆停） | 同上 |
| `com.tcl.guard` | 兩派做法都有，先觀望 | **相符**（43P615 停／50J7000A 因 PERSISTENT 保留；toolkit Safe 清單直接停）。補充反例方向：Projectivy #415 顯示新韌體 Guard 與自啟動白名單連動，停 Guard 未必省事 | 兩份 debloat.txt＋run_toolkit.py＋#415 |
| katniss、tv.assistant、tcl.assistant、micmanager、smartalexa | 可停（代價 mic 鍵失效） | **部分相符**：43P615 五個全停；50J7000A 只停前三（micmanager、smartalexa 出廠已停）。28%→0.4% 只在 BeyondTV4 實證 | 兩份 debloat.txt＋AR05 文件 |
| miracast、MultiScreenInteraction | 可停（可選） | **相符但需註明**：43P615 停；50J7000A 機主保留（非禁區，是選擇） | 兩份 debloat.txt |
| backdrop、dreams.basic | 可停 | **部分相符**：只在 43P615；50J7000A 未列（AR05 文件說 backdrop 會被 system.server 復活，留了改 appops）。R2 未提復活差異 | 43P615＋AR05 文件 |
| amazonvideo、vectorunit 等預載遊戲 | 可停（但本案保留串流） | **相符**（50J7000A 停；43P615 停的是 Netflix／Disney+／Max 等整批串流——機主自選，與本案需求相反，不可抄） | 兩份 debloat.txt |

#### §4.2（不要碰）——反例重點

| 套件 | R2 說法 | 核對結果 | 依據 |
|---|---|---|---|
| `com.tcl.tv`、`com.tcl.tvinput`、tvpassthrough | 不要碰；toolkit 標 ⚠️ 與 CleanYourTV DO NOT DISABLE 矛盾 | **相符**。toolkit 原始碼確為 ⚠️；兩份清單確為 DO NOT DISABLE；RT2851 文件佐證承重 | 兩份 debloat.txt＋run_toolkit.py＋rt2851 文件 |
| keycustomfunctionservice、shortcutmenu、partnercustomizer | 不要碰 | **部分相符**：shortcutmenu 只在 43P615 禁區，50J7000A 未提；T 鍵機制有 RT2851 文件佐證 | 43P615＋rt2851 文件 |
| inputmethod、settings、initsetup、factory.view、providers.config | 不要碰 | **部分相符**：providers.config 只在 50J7000A 禁區，43P615 禁區無它（改列 com.tvos）。R2 併列時未註明此差異 | 兩份 debloat.txt |
| xian.StartandroidService、systemserver、autopair | 不要碰 | **相符**（兩份皆列）。但 R2 把 `com.tcl.systemserver` 與 `com.tcl.system.server` 併寫，實際兩份清單是前者、AR05 文件是後者，是否同一套件在不同韌體改名**未證實**，應註明 | 兩份 debloat.txt＋AR05 文件 |
| versionUpdateApp／rc.ota／ttvs（OTA） | 不要碰 | **查不到（rc.ota、ttvs）**：同板兩份清單完全沒出現這兩個名字；versionUpdateApp 兩份皆保留，相符。R2 未給 rc.ota／ttvs 來源 | 兩份 debloat.txt（全文無 rc.ota／ttvs） |
| `com.tcl.usercenter` | §4.2 列不要碰；矛盾表又寫 43P615 停 | **不符（R2 自相矛盾）＋反例成立**：43P615 實測**停用** usercenter 且三重開機正常；50J7000A 是「留著未動」而非「禁區」（DO NOT DISABLE 行無它）。R2 §4.2 把它放不要碰是錯的，應移出或降級 | 43P615（停用）vs 50J7000A（未動） |
| setupwraith（Realtek 必留） | 絕對不要碰 | **部分相符**：issue #994 機制（provider 綁定）與 log 皆吻合，但**機型是 Sony K-55S25 不是 TCL**；43P615 頭註 "setupwraith does not claim HOME on this build"。結論方向對，但「本機是 Realtek 故絕對不要碰」是跨型號推論，應標 [社群實測，型號不同] 而非既定事實 | #994＋43P615 頭註 |
| gms／vending、mediashell、tv.remote.service | 不要碰 | **相符（kept／禁區一致）但無災情等級證據**：mediashell 兩份是「機主保留」非禁區；gms／vending 無 TCL 直接回報，屬常識性保留。R2 §4 本來就沒給逐條等級，此處不算錯，但不應讀成「停了必出事」 | 兩份 debloat.txt |
| tvweishi、appmarket2 | 不要碰（推測） | **有反例方向**：toolkit Safe 清單直接停這兩個且無警告；同板兩份清單完全沒提。R2 誠實標推測，維持推測即可，**不要升級為禁區** | run_toolkit.py（Safe 含 tvweishi、appmarket2） |
| DRM／widevine 相關 | 不要碰 | **查不到直接證據**。唯一 DRM 災情（Widevine keybox／ESN 遺失）來自 50DP660 的 NVM reset，與 debloat 無關 | 50dp660.txt |

### 2.3 R2 引用的其他關鍵頁（§0／§4 範圍內連帶核對）

| R2 引用的 URL／說法 | 核對結果 |
|---|---|
| Google Family Link 兒童設定檔僅 Google TV（support.google.com/families/answer/10491112） | **相符** [官方文件]：頁面標題即 "Get started with a Google kids profile on Google TV"，建檔限 Google TV 首頁頭像＋Family Link 監控；Android TV 11 無此路 |
| Android TV 記憶體指南（LMK、direct reclaim、Launcher 延遲） | **相符** [官方文件]：四項後果（系統服務變鈍、LMK 殺／重啟尖峰、回 Launcher 延遲、direct reclaim 掉格）逐字在頁 |
| UAD-NG #887（tvlauncher 移除風險） | **部分相符**：原文是 ZTE 866 兩台移除後恢復原廠設定＋Expert 分級討論；R2 寫「ZTE 會回復原廠設定」正確，但這是 UAD-NG 資料庫條目討論，不是 TCL 實測 |
| CleanYourTV README（Home 保護、OTA 復活、還原） | **相符** [官方文件]：Home 保護（skip＋說明）、OTA 後套件復活＋check.py --fix、restore.sh＋~/.cleanyourtv 紀錄、Google TV setupwraith crash-loop 警告皆在原文 |
| Projectivy #500（75T7B HOME 被搶）／#415（C855 開機不自動進） | **相符** [社群實測]：機型、版本、現象（間歇跳回／要按 Home）、Safety Guard／APP_AUTO_START 解法皆吻合；但**全部非 Android TV 11**，R2 已註明，恰當 |
| Shield BENCHMARKS（+163.6 MiB、338→334） | **相符** [社群實測]：數字逐字吻合；作者自限（非速度實測）R2 也有引述，恰當 |
| P715 收到 RT51 V715 更新包（XDA page-176） | **未核對**（本次未讀 page-176；page-187 經 jina 讀到的是韌體版本問答，無 P715 更新包原文）。R2 用它只做「同機型佐證」，影響小，但嚴格說 §0-5 的 V715 系譜缺一環 |
| R2 自述數字（UAD-NG 9,624★、CleanYourTV 44,579★／77 份、toolkit 788★、uad_lists.json 5,382 筆／TCL 17 筆） | **未核對**（未讀 GitHub API／uad_lists.json）。其中「TCL 77 份」與我讀到的 README 品牌表（TCL · 77）一致，其餘不評 |

---

## 待實機驗證清單

1. `pm list packages` 全列：確認有無 `com.tcl.tvpassthrough`、`com.tcl.dashboard`、`com.tcl.tvweishi`、`com.tcl.bootadservice`、`com.tcl.appmarket2`、`com.tcl.usercenter`、`com.tcl.guard`、`com.realtek.advancesetting`、`com.tcl.systemserver`／`com.tcl.system.server`（釐清 §4.2 兩個复名）。
2. 台灣版在地預載：四季 4gTV 套件名、有無 LiTV／Hami／friDay／myVideo、有無開機廣告服務（`bootadservice` 是否存在）。
3. `MemAvailable` 基準（剛開機閒置 2 分鐘 vs 使用 2 小時）與 `df -h /data`（500 MB 門檻）。
4. `cmd package resolve-activity -c android.intent.category.HOME`：確認首頁是否 `com.google.android.tvlauncher` priority 2。
5. `ro.adb.secure`、5555 行為（開 USB 偵錯即開 port？）、`dumpsys -l | grep disk`（diskstats 是否存在）。
6. `pm revoke com.google.android.katniss RECORD_AUDIO` 前後 CPU（驗證 28%→0.4% 在本機是否重現；代價 mic 鍵失效）。
7. 停 `com.tcl.overseasappshow` 後重開機，促銷套件是否復活（驗證 key-entry 說法）。
8. 停 `com.google.android.tvrecommendations` 後首頁與各 App 推薦列行為。
9. `com.tcl.usercenter` 停用測試（43P615 反例支持可停，但台灣版帳號功能是否依賴它未知，先測帳號／開機行為）。
10. `com.tcl.guard` 先保留：50J7000A PERSISTENT＋新韌體 Guard 連動自啟動白名單，動它風險收益比差。
11. Projectivy：先不換桌面；若要換，先在不禁用原 launcher 下用 Override 試跑一週，確認無 HOME 被搶再決定（Android TV 11＋TCL 無直接回報，本機就是白老鼠）。
12. OTA 一次後復活比例與新增預載（CleanYourTV 說一定復活，待本機實證）。
13. HBO Max 裝不了的原因（Play 相容性 vs 套件缺失）——R2 已聲明不在範圍，本報告同樣未查。
14. 四季 4gTV 是否依賴 `com.tcl.tv` 的 channel MIME handler（停促銷套件時一併確認播放正常）。

## 查不到／不確定

- **沒有 55P715 專屬清單**：R2 說 77 份無 P715，「77」與 README 品牌表吻合，但 examples 總表過長，本次未逐一窮舉確認無 P715；UAD-NG 資料庫（uad_lists.json）本次未讀，17 筆說法未核對。
- **台灣版在地預載與開機廣告**：Mobile01 開箱文讀不到（403）、TCL 台灣無客服 FAQ、無 PTT／巴哈台灣 TCL 停用回報。`waterfall.overseas`（TCL Channel）是否在台灣版啟用未知。
- **R2 §0-5a 的 V715 變慢說**：XDA page-187 經代理讀到的只是韌體版本問答，無效能原文；R2 自標二手摘要，**應降級為 [推測/待驗證]，不得作為決策依據**。
- **R2 §4.2 的 `rc.ota`、`ttvs`、`com.tcl.tvweishi`、`com.tcl.appmarket2`、DRM 套件**：同板兩份清單無此四者（除 versionUpdateApp 外），無 TCL 直接災情；toolkit 反而把 tvweishi／appmarket2 列 Safe。維持推測，不得寫成禁區。
- **`com.tcl.usercenter` 被 R2 誤置禁區**：43P615 停用無事是反例；但台灣版 TCL 帳號／綁定手機功能是否依賴它未知，實機先測。
- **`com.tcl.systemserver` vs `com.tcl.system.server`**：是否同一套件改名未證實；開機復活行為（AR05）屬實，但「停掉風險高」無停用災情回報。
- **Projectivy 在 Android TV 11＋TCL**：零直接回報；現有 TCL 案例全是 Android TV 12／14 新機（75T7B、C855、R646、QM7K、55Q6C、65C6K）。開發者自己的 TCL C1（Android TV 11）重現不了問題——這既是好消息（舊韌體可能沒 Safety Guard 作祟）也是壞消息（無人測過）。
- **2GB／16GB 跑 2026 年版 YouTube／Play 服務是否變肥**：無量化數據；Reddit 慢板只讀到標題；改善幅度只有 50J7000A 的 +160 MB 與 Shield（非 TCL）的 +163.6 MiB，兩者都不是流暢度實測。
- **TCL 官方對停用的立場與保固影響**：查不到官方頁，一律以 TCL 台灣官方說法為準。
- **星級／push 日期／uad_lists 筆數等 R2 自述數字**：本次未讀 GitHub API，不背書。
- XDA 兩頁經 r.jina.ai 代理取得（XDA 本體 Cloudflare 擋），引述時注意為代理快照；Mobile01 因 403 完全未讀。
