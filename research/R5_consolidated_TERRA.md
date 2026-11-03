# TCL 55P715 電視瘦身與 YouTube 時間管理研究彙整

本報告整理 R1、R2、R2b、R3、R4 五份研究。各研究均表示未操作本機；以下保留研究員的結論、證據等級、限制與來源。不同說法並列，未自行裁決。

**Owner 目前只授權研究與規劃**：禁止直接執行 ADB 停用、解除安裝、修改韌體、調整路由器設定或建立開發工單。（00_REQUIREMENT.md）

## 一、需求 A 電視瘦身

### 工具比較

| 工具 | 研究內容與定位 |
|---|---|
| **CleanYourTV** | 結合套件清單、Python／Bash 工具與自製 launcher；會備份狀態、以 `pm disable-user` 停用套件，並可更換首頁與還原。Home 保護會在沒有其他可用 HOME 時跳過目前 launcher。[官方文件] 同 client type 的 50J7000A 停用 25 個套件後，`MemAvailable` 約 495→655 MB，重開機後維持停用，Home、HDMI 等正常。[社群實測] 研究員將其定位為主要參考，但不可照抄其他機型清單。（R2、R2b） |
| **UAD-NG** | 桌面 GUI，透過 ADB 停用或解除安裝套件，有 snapshot／restore；其 TCL 資料庫條目主要為手機，沒有 P715／P615 電視專屬條目，可作執行引擎與安全網，清單仍須自建。[官方文件]（R2；R2b 未核對資料庫筆數） |
| **android-tv-debloat-toolkit** | R2 所查的「tv-debloat」之一；Windows 專用固定清單工具，只做 `pm disable-user`，沒有備份或還原腳本。R2 建議本案不要使用；R2b 核對其 Safe 清單與其他清單有衝突，也包含本案要保留的 Netflix。[官方文件]（R2、R2b） |
| **Projectivy Launcher** | 第三方首頁；override 模式可在不先停用原 launcher 的情況下攔截首頁呼叫。R2 認為單純換桌面幾乎不省 RAM，推薦、促銷及遙測為獨立套件；R2b 認為機制相符，但「幾乎不省 RAM」沒有直接量化對比，證據等級偏樂觀。[官方文件][社群實測]（R2、R2b） |
| **Projectivy 的 TCL 風險** | 新款 TCL 有搶回 HOME 或開機後須按 Home 的回報；研究員未找到 TCL Android TV 11 直接回報。未先準備可用 launcher 就停用原 launcher，Home 可能落到 factory test app。[官方文件] [社群實測，型號不同]（R2、R2b） |

來源：

- UAD-NG FAQ：https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/FAQ
- CleanYourTV README：https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/README.md
- 同 client type 的 50J7000A：https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-50j7000a/debloat.txt
- toolkit 原始碼：https://raw.githubusercontent.com/seun-novodev/android-tv-debloat-toolkit/main/run_toolkit.py
- Projectivy 開發者原帖：https://xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/
- TCL Projectivy 回報：https://github.com/spocky/miproja1/issues/500 、https://github.com/spocky/miproja1/issues/415
- Shield 整包 profile 量測：https://raw.githubusercontent.com/mbcrump/shield-projectivy-debloat/main/BENCHMARKS.md

### 停用 vs 解除安裝

- **`pm disable-user --user 0`**：R2 建議本案使用停用；可用 `pm enable` 還原，APK 仍保留。UAD-NG 官方文件指出，非 Recommended 等級套件應停用而非解除安裝，因解除安裝已知可能造成系統不穩定。[官方文件] R2b 核對結果為相符。（R2、R2b）
- **`pm uninstall --user 0`**：R2 指出可透過 `install-existing` 還原，但資料已清、已更新 APK 已移除，且相依 App 可能持續嘗試喚醒不存在的套件。[官方文件]（R2）
- **修改 `/system` 的真解除安裝**：R2 指出需要 root，且只能刷回原廠映像。（R2）
- **狀態可能復活**：R2 引用 CleanYourTV 指出 OTA 後停用套件與原首頁可能回來；恢復原廠會還原被 debloat 的套件。本機 OTA 後復活比例待實測。[官方文件]（R2、R2b）

來源：https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/FAQ 、https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/670 、https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/firmware-updates.md

### 套件分類：R2 與 R2b 並列

以下套件名稱是研究中的同板或其他機型資料，**不是本機實測清單**。R2 明說不可整份套用；R2b 對各項「相符」或「部分相符」的原文核對見下一節。（R2、R2b）

#### 兩人都說可停用

| 套件 | R2 說法 | R2b 核對 |
|---|---|---|
| `com.tcl.bi`、`com.google.android.feedback`、`com.google.android.partnersetup`、`com.google.android.onetimeinitializer`、`android.autoinstalls.config.tcl.device` | 可停，遙測／歸因 | **相符**：兩份同板清單皆停。（R2、R2b） |
| `com.tcl.overseasappshow`、`com.tcl.tv.tclhome_passive`、`com.tcl.waterfall.overseas`、`com.tcl.messagebox`、`com.tcl.esticker`、`com.tcl.copydatatotv`、`com.tcl.notereminder` | 可停，促銷；R2 寫 `overseasappshow` 先關 | **相符**：兩份皆停；`overseasappshow` 的 key entry 說法與 43P615 頭註一致。（R2、R2b） |
| `com.google.android.tvrecommendations` | 可停 | **相符**：兩份皆停。（R2、R2b） |
| `com.tcl.dashboard`、`com.tcl.useragreement` | 可停 | **部分相符**：只在 43P615 出現，50J7000A 無此二套件。（R2、R2b） |
| `com.google.android.katniss`、`com.google.android.tv.assistant`、`com.tcl.assistant`、`com.tcl.micmanager`、`com.tcl.smartalexa` | 可選停用；代價是麥克風鍵失效 | **部分相符**：43P615 五個全停；50J7000A 只停前三，後兩者出廠已停。28%→0.4% 只在 BeyondTV4 實證。（R2、R2b） |
| `com.tcl.miracast`、`com.tcl.MultiScreenInteraction_TV` | 可選停用 | **相符但需註明**：43P615 停；50J7000A 機主保留。（R2、R2b） |
| `com.google.android.backdrop`、`com.android.dreams.basic` | 可停 | **部分相符**：只在 43P615；50J7000A 未列。AR05 文件說 backdrop 會被復活。（R2、R2b） |
| `com.amazon.amazonvideo.livingroom`、`com.vectorunit.purple.googleplay` 等 | 可停不需要的預載 App／遊戲；本案保留需求中的串流 App | **相符**；另一份清單停了本案要保留的串流 App，不可抄。（R2、R2b） |

來源：https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-43p615/debloat.txt 、https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-50j7000a/debloat.txt 、https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-beyondtv4-uk-ar05.md

#### 兩人都說不要碰

| 套件 | R2 與 R2b 的研究說法 |
|---|---|
| `com.tcl.tv`、`com.tcl.tvinput`、`com.tcl.tvpassthrough` | R2 列不要碰；R2b 核對相符，同板清單與 RT2851 文件指出 Live TV／HDMI 承重。[社群實測]（R2、R2b） |
| `com.tcl.keycustomfunctionservice`、`com.tcl.common.shortcutmenu`、`com.tcl.partnercustomizer` | R2 列不要碰；R2b 核對**部分相符**，`shortcutmenu` 只在 43P615 禁區，T 鍵機制有 RT2851 文件佐證。[社群實測]（R2、R2b） |
| `com.tcl.inputmethod.international`、`com.tcl.settings`、`com.tcl.initsetup`、`com.tcl.factory.view`、`providers.config` | R2 列不要碰；R2b 核對**部分相符**，兩份清單對 `providers.config` 有差異。（R2、R2b） |
| `xian.StartandroidService`、`com.tcl.systemserver`、`com.tcl.autopair` | R2 列不要碰；R2b 核對相符，但指出 `com.tcl.systemserver` 與 `com.tcl.system.server` 是否為不同韌體中的同一套件改名，未證實。（R2、R2b） |
| `com.tcl.versionUpdateApp` | R2 列為 OTA 不要碰；R2b 核對兩份同板清單皆保留，相符。（R2、R2b） |
| `com.google.android.gms`、`com.android.vending`、`com.google.android.apps.mediashell`、`com.google.android.tv.remote.service` | R2 列不要碰；R2b 核對**相符（kept／禁區一致）但無災情等級證據**，其中 mediashell 是機主保留，非禁區。（R2、R2b） |
| `com.google.android.tungsten.setupwraith` | R2 列絕對不要碰；R2b 核對**部分相符**：音效設定崩潰案例為 Sony K-55S25，非 TCL，套用本機屬跨型號推論。[社群實測，型號不同]（R2、R2b） |

來源：https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-beyondtv-rt2851.md 、https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/994

#### 說法不一或只有一人提到

| 套件 | 並列研究說法 |
|---|---|
| `com.tcl.guard` | R2：43P615 停、50J7000A 因 PERSISTENT 保留，兩派都有，先觀望。R2b：核對相符；另指出新韌體 Guard 與自啟動白名單連動的回報。（R2、R2b） |
| `com.tcl.usercenter` | R2 §4.2 列不要碰，但矛盾表又寫 43P615 停用。R2b 判定 **不符（R2 自相矛盾）＋反例成立**：43P615 停用且三次重開正常；台灣版帳號功能依賴未知。（R2、R2b） |
| `com.tcl.tvweishi`、`com.tcl.appmarket2` | R2：部分機型可能是更新與安裝通路，屬推測，本案不要停。R2b：toolkit Safe 清單直接停；同板清單未提，維持推測，不要升級為禁區。（R2、R2b） |
| `rc.ota`、`ttvs` | R2 列為 OTA 更新器不要碰；R2b 在兩份同板清單**查不到**，且指出 R2 未給這兩個名稱來源。（R2、R2b） |
| DRM／Widevine 相關 | R2 列不要碰；R2b 查不到 debloat 直接災情，已知 Widevine／ESN 遺失案例來自 NVM reset。（R2、R2b） |
| `com.tcl.bootadservice` | R2 作為 toolkit 提供的套件線索；R2b 列本機是否存在及台灣版開機廣告待驗證。（R2、R2b） |

### R2b 對 R2 的核對結果：原文搬錄

以下三個核對表依 R2b 原文搬錄；「相符／不符／查不到／部分相符／未核對」維持原狀。（R2b）

#### 2.1 第 0 節「先講結論」逐條核對

| # | R2 的說法 | 核對結果 | 依據 URL（實際讀到） |
|---|---|---|---|
| 0-1 | 四支工具只有 UAD-NG 與 CleanYourTV 真的改套件狀態；Projectivy 只換首頁；android-tv-debloat-toolkit 是固定清單 Windows 小工具 | **相符**。UAD-NG FAQ 列出 disable/uninstall 指令；CleanYourTV README 確認 disable＋換 launcher；toolkit 原始碼只有 `pm disable-user`；Projectivy 原帖確認是 launcher＋Override 模式 | <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/FAQ>、<https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/README.md>、<https://raw.githubusercontent.com/seun-novodev/android-tv-debloat-toolkit/main/run_toolkit.py>、<https://xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/>（經 r.jina.ai） |
| 0-2 | 同 client type `TCL-AP-RT2851-S1`、V674 的 50J7000A，停 25 套件 MemAvailable 約 495→655 MB，重開機維持、Home／HDMI 正常 | **相符**（逐字吻合 debloat.txt 頭註：25/25、495→655 MB、reboot 後全維持、YouTube／Netflix／AIS／Disney+／Live TV、HDMI Inputs 正常） | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-50j7000a/debloat.txt> |
| 0-3 | 換桌面單獨做幾乎不省 RAM；推薦列／促銷／開機推送是獨立套件；Projectivy override 是攔截原 launcher 啟動、原 launcher 還在 | **相符**（機制描述與原帖一致；套件獨立性與兩份 debloat.txt 一致）。「幾乎不省 RAM」的量化對比無直接數據，屬合理推論，等級標 [官方文件][社群實測] 可接受但偏樂觀 | 同上＋<https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-43p615/debloat.txt> |
| 0-4 | 本案一律 `disable-user` 不用 `uninstall --user 0`；UAD-NG 官方寫非 Recommended 用 disable、uninstall 造成不穩；disable 可 `pm enable` 還原 | **相符**。FAQ 原文："If you're debloating anything other than Recommended, you should disable rather than uninstall, as uninstalling is known to cause system stability issues."；issue #670 補強相依 App 亂喚醒機制 | <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/FAQ>、<https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/670> |
| 0-5a | 同 V715 版本、XDA P8M 用戶「Android 9（V616）升 Android 11（V715）後效能明顯變差、4K 掉格」 | **查不到**。經 r.jina.ai 讀取該串 page-187，內容是 C715／P8S 韌體版本問答（V662／V715 對照、RT51 刷機），**沒找到效能變差原文**。R2 自己也標註「二手摘要」，應視為未證實 | <https://xdaforums.com/t/tcl-android-tv-model-55p8m-android-recovery-tcl-beyondtv-beyondtv.4025755/page-187>（經 r.jina.ai 取得） |
| 0-5b | BeyondTV4 機器 katniss 常駐吃約 28% 單核，撤麥克風權限後 0.4% | **相符**（AR05 文件逐字：HOTWORD＋ECHO_REFERENCE、28%→0.4%、`pm revoke ... RECORD_AUDIO`） | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-beyondtv4-uk-ar05.md> |

R2 §0 證據等級總評：0-1～0-4 的 [官方文件] 標示恰當（確為 README／Wiki／原始碼）；0-5a 雖在正文標 [社群實測] 但附錄已自首是二手摘要，**建議降級為 [推測/待驗證]**；其餘無不當。（R2b）

#### 2.2 第 4 節套件分類逐項核對＋反例

> 「R2 說法」指 §4.1「通常可停用」與 §4.2「不要碰」。反例標準：同板（RT2851 家族）實測與 R2 相反。

**§4.1（可安全停用）**

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

**§4.2（不要碰）——反例重點**

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

#### 2.3 R2 引用的其他關鍵頁（§0／§4 範圍內連帶核對）

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

### 卡頓診斷

- R2 建議先量剛開機閒置 2 分鐘與正常使用 2 小時後的記憶體、CPU、儲存與背景行程。Google Android TV 記憶體文件描述高記憶體壓力可能造成系統服務變鈍、LMK 反覆殺／重啟、回 Launcher 延遲及 direct reclaim 掉格。[官方文件]（R2、R2b）
- R2 的 `MemAvailable` 常態低於 300 MB、低於 200 MB 及前後下降比例等判讀，是經驗推論，**非 Google 官方門檻**。[推測/待驗證]（R2、R2b）
- R2 引用 AOSP 的低儲存門檻：分割區 5% 與 500 MB 上限取較低值，OEM 可覆寫；本機 `/data` 分割區與實際可用空間待確認。[官方文件]（R2）
- R2b 的同板量化資料是 50J7000A 停用 25 套件後約增加 160 MB；Shield 整包 profile 增加 163.6 MiB，但作者未證明流暢度。[社群實測] [社群實測，型號不同]（R2、R2b）
- R4 認為本機卡頓根本原因是低階 SoC、2 GB RAM、Android TV 11 與 TCL 背景服務／推薦渲染，並描述空閒 RAM 常為 150～250 MB、eMMC 磨損與快取造成 I/O 阻塞；R4 同時將本機 RAM 實測列入待驗證。[社群實測]（R4）

來源：https://developer.android.com/training/tv/playback/memory 、https://developer.android.com/tools/dumpsys 、https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/os/storage/StorageManager.java 、https://www.mobile01.com/topicdetail.php?f=347&t=6259025

### 非工具手段

| 手段 | 研究發現 |
|---|---|
| 清 App 快取 | R2 指出可釋放 `/data`，對 RAM 無效；清除資料則會登出。（R2） |
| 調整動畫 | R2 指出可讓操作「感覺」變順，不減少 RAM／CPU，可還原。（R2） |
| 限制背景執行 | AR05 案例用 `RUN_ANY_IN_BACKGROUND ignore` 處理被系統復活的 App；R2 指出可能影響通知或更新。[社群實測]（R2、R2b） |
| 撤銷語音麥克風權限 | BeyondTV4 案例 `katniss` 約 28%→0.4% 單核；語音功能受影響，本機效果待驗證。[社群實測]（R2、R2b） |
| 關閉自動更新 | R2 認為可避免更新後突然變卡，並指出安全性更新停滯的風險。（R2） |
| 恢復原廠 | R2、R2b 引用舊款 50DP660 的 NVM reset 災情：Wi-Fi MAC、Widevine keybox、Netflix ESN 遺失；同機原廠後過舊 GMS／WebView 亦造成登入問題。該案例是 Android 8 舊平台，不可直接套用本機。[社群實測]（R2、R2b） |

來源：https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-beyondtv4-uk-ar05.md 、https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-50dp660.txt

### 何時改用外接電視盒

- R2b 的判斷線：若 `MemAvailable` 長期低於 200～300 MB、LMK 頻繁殺行程，且促銷／telemetry 清單已停完仍卡；或 `/data` 可用低於 500 MB 且清不出來，可改用外接盒。這些門檻是經驗法則，非官方。[推測/待驗證]（R2b）
- R2b 引用 Google TV Streamer 4K 台灣公司貨官方建議售價 NT$3,250 的媒體報導；價格以賣場即時頁為準。[社群實測，媒體報導]（R2b）
- R4 引述社群建議，硬體老化嚴重時外接 Apple TV 4K 或 Google TV 串流棒，讓電視作為顯示器。[社群實測]（R4）

來源：https://www.techbang.com/posts/118477-google-tv-streamer4k-chromecast-3250 、https://www.mobile01.com/topicdetail.php?f=347&t=6259025

## 二、需求 B YouTube 時間管理

### 方案 A：現成家長監護 App

R1 結論：目前沒有查到一款現成工具，同時滿足 iPhone 遠端核准單次 N 分鐘、只鎖 YouTube、到期自動重鎖及防繞過。沒有找到 TCL 55P715 或 Android TV 11 的實機回報。（R1）

| 候選 | 研究發現 |
|---|---|
| **TVCP** | 官方頁列 Android TV／Google TV、iOS 與 Android 家長端、單鍵封鎖 YouTube、遠端鎖定及解除計時鎖；計時到期鎖的是整台電視，被封鎖 App 在其他 profile 仍可開啟。7 天試用後 $3.99／月或 $29.99／年，價格依國家與商店而異。[官方文件] 未見核准流程，是否可只對 YouTube 計時並自動重鎖未驗證。[推測/待驗證]（R1） |
| **ScreenUsage／tvusage** | 商店鏡像頁列每日 App 限額、時段與單 App 封鎖；未提單次核准、bonus 或 iOS，付費 Remote 細節與價格未查到。是否不影響其他 App、到期行為及 TCL 相容性待驗證。[官方文件（鏡像轉述）] [推測/待驗證]（R1） |
| **TVGuard** | 每 App PIN 鎖、每日限額；同 Wi-Fi 直連，遠端選用；未見核准或加時，iOS 與價格未確認。[官方文件（開發者頁）]（R1） |
| **YourHour** | 搜尋摘要描述手機配對與鎖定 TV App；未見單次 N 分鐘核准，iPhone 支援未查到。頁面 403。[推測/待驗證]（R1） |
| **Kidslox** | 搜尋摘要提到跨 iOS／Android 家長端、學習任務換時間與請求加時；Android TV 支援與價格未確認。[推測/待驗證]（R1） |
| **Google Family Link／內建控制** | Family Link 官方 FAQ 未列 Android TV；Google TV 兒童設定檔及 TCL Google TV 的控制功能不能直接套用本機，55P715 選單與逐 App 鎖定待實測。[官方文件] [官方文件（他機型）] [推測/待驗證]（R1、R2、R2b） |
| **Xunison ATV／Kids Place** | R1 將 Xunison 列為不符合需求；Kids Place 搜尋摘要未列 Android TV，作為 launcher 屬整機替換。[官方文件] [推測/待驗證]（R1） |

來源：https://tvcp-landing.pages.dev/ 、https://appgoblin.info/apps/io.middlepoint.tvcp.guard 、https://apkpure.net/tvusage-parental-control-app/in.codeseed.tvusage 、https://appgoblin.info/apps/in.codeseed.tvusage 、https://hunted.space/product/tvguard 、https://www.apkmirror.com/apk/mindefy-labs/yourhour-screen-time-control-android-tv/yourhour-screen-time-control-android-tv-1-2-8-yhtv-release/ 、https://families.google/familylink/faq/ 、https://www.tcl.com/mk/mk/support-tv/faq/28606 、https://help.xunison.com/how-to-supervise-time-from-android-tv

### 方案 B：路由器與網路層

- R4 結論：M9 原生功能無法滿足臨時核准單次 30 分鐘 YouTube 且不影響其他 App。Pause 是整台裝置斷網；免費版網址黑名單是靜態開關，需手動移除及加回；Pro 的 Reward 是整台裝置每日總上網額度加時，App Time Limits 是每日固定配額。[官方文件]（R4）
- R4 指出 Tether 綁定 MeshBrand ID 後可遠端管理；其研究結論為 AP 模式下 HomeShield、家長控制及 QoS 失效，須 Router 模式。[官方文件]（R4）
- R4 認為精準封鎖 `youtube.com`、`youtubei.googleapis.com`、`googlevideo.com` 可使 YouTube 無法使用，而其他指定影音服務正常；同一研究將 Netflix、Disney+、四季線上是否正常及 DNS 繞過列入實機驗證。[社群實測] [推測/待驗證]（R4）
- R4 指出封 `googlevideo.com` 會影響 Google Play 商店的 App 預覽影片；萬用字元封鎖 `googleapis.com` 或 `google.com` 會誤傷其他 Google 功能。[社群實測]（R4）
- **NextDNS** 有 REST API，但研究認為受 M9 分裝置 DNS 與 iPhone 背景計時限制；**AdGuard Home** 支援依客戶端分流與 API，但需常駐硬體及後端排程；**Pi-hole** 支援群組，但 R4 指出指定群組的單次暫停需自寫腳本。[官方文件] [推測/待驗證] [社群實測]（R4）

來源：https://www.meshbrand.com/tw/support/download/archer-M9/v4/ 、https://www.meshbrand.com/tw/homeshield/ 、https://www.meshbrand.com/en/support/faq/3264/ 、https://www.meshbrand.com/en/support/faq/1366/ 、https://nextdns.io/pricing 、https://github.com/AdguardTeam/AdGuardHome

### 方案 C：自建 iPhone → 常駐服務 → ADB

R3 結論：不必開發 Android TV App，即可設計 iPhone 核准單次 N 分鐘、到期鎖回的核心閉環；但本機 ADB 重開機／待機後連線、孩子能否在設定中自行啟用，以及瀏覽器與第三方播放器，是尚待處理的限制。（R3）

- R3 建議 Home Assistant 常駐，透過 `androidtv.adb_command` 執行啟用、`force-stop` 與停用；iPhone 使用 HA App、捷徑或 webhook。15／30／60 分鐘可用按鈕或 script。[官方文件]（R3）
- 服務端記錄絕對到期時間，切 App、關電視仍計時；重連時以時間戳比對補鎖。HA 官方指出 timer 在 HA 停機期間結束時，`timer.finished` 不會補觸發。[官方文件] 設計細節待驗證。[推測/待驗證]（R3）
- 若授權期間服務掛掉或電視斷線，到期命令不能執行；恢復連線後才能補鎖。重開機後 ADB 是否維持、本機停用狀態與設定中能否自行啟用均待驗證。[推測/待驗證]（R3）
- 家中需常駐服務；R3 未查樹莓派、HA Green 或 NAS 的具體售價。遠端可考慮 Tailscale 或付費 Home Assistant Link；ADB 5555 只留區網。[官方文件]（R3）

來源：https://www.home-assistant.io/integrations/androidtv/ 、https://www.home-assistant.io/actions/androidtv.adb_command/ 、https://www.home-assistant.io/integrations/timer/ 、https://companion.home-assistant.io/docs/integrations/siri-shortcuts/ 、https://tailscale.com/pricing 、https://www.nabucasa.com/link/ 、https://developer.android.com/tools/adb

### YouTube 各入口封鎖能力對照

表內「未查到」表示原研究未查到資料；保留原研究有提供的證據等級。

| 入口 | 方案 A：現成 App | 方案 B：網路層 | 方案 C：ADB |
|---|---|---|---|
| **原生 YouTube App** | 有單 App 封鎖描述；完全不可開啟未實測。[官方文件] [推測/待驗證]（R1） | 封特定 YouTube 網域後無法連線或播放；本機與其他 App 影響待驗證。[社群實測] [推測/待驗證]（R4） | 停用 `com.google.android.youtube.tv`；到期搭配 `force-stop`。[社群實測] [官方文件]（R3） |
| **遙控器快捷鍵** | 理論上 App 被封鎖可能被擋，未驗證。[推測/待驗證]（R1） | R4 未逐項查到快捷鍵表現；網域封鎖效果待實測。（R4） | 推測停用後無反應或顯示未安裝，待實測。[推測/待驗證]（R3） |
| **YouTube Kids** | 另一個 package，須分別封鎖；未查到工具實測。[推測/待驗證]（R1） | R4 未逐項查到 Kids 的封鎖結果；網域效果待實測。（R4） | 推測套件為 `com.google.android.youtube.tvkids`，須實機確認後分別停用。[推測/待驗證]（R3） |
| **瀏覽器** | 未見網站／URL 封鎖；R1 視為高風險、未封鎖。[推測/待驗證]（R1） | 封 `youtube.com` 等使瀏覽器無法開啟；DNS／加密流量可能有漏擋盲區。[社群實測] [推測/待驗證]（R4） | 停 YouTube App 擋不住；須另處理瀏覽器或網域。[社群實測]（R3） |
| **iPhone 投放** | 未查到投放與家長 App 關係的實測，高風險。（R1） | R4 未逐項查到投放封鎖結果；待實測。（R4） | R3 依 Cast Connect 文件推論停用 TV App 後應失敗；確切結果待驗證。[官方文件] [推測/待驗證]（R3） |
| **語音** | 未查到。（R1） | R4 未逐項查到語音入口結果。（R4） | R3 認為語音路徑綁官方套件；確切失敗訊息待驗證。[官方文件] [推測/待驗證]（R3） |
| **第三方播放器** | R1 未逐項查到封鎖結果。（R1） | R4 未逐項查到第三方播放器封鎖結果；DNS／加密流量有待驗證。[推測/待驗證]（R4） | 獨立套件，不受官方 YouTube 停用影響；需逐個處理。[官方文件]（R3） |

來源：https://developers.google.com/cast/docs/android_tv_receiver 、https://github.com/yuliskov/SmartTube 、https://tvcp-landing.pages.dev/

### 30 分鐘授權規則對照

| 規則 | 方案 A | 方案 B | 方案 C |
|---|---|---|---|
| **單次核准** | R1 未找到完整符合的現成工具；TVCP 未見核准流程，ScreenUsage 未提單次核准。[官方文件] [官方文件（鏡像轉述）]（R1） | M9 原生無單次 30 分鐘 YouTube 自動化；Pro Reward 是整機上網加時。[官方文件]（R4） | iPhone 按鈕／捷徑呼叫 HA，設計單次 15／30／60 分鐘。[官方文件]（R3） |
| **不暫停計時** | 各候選未查到符合本規則的單次授權行為。（R1） | 靜態黑名單無自動倒數；App 限時是每日固定配額。[官方文件]（R4） | 服務端記錄絕對到期時間；切 App、關電視繼續計時，實作待定案。[推測/待驗證]（R3） |
| **到期關閉、重鎖** | TVCP 計時到鎖整台；是否只重鎖 YouTube 未驗證；ScreenUsage 到期播放行為未查到。[官方文件] [推測/待驗證]（R1） | 黑名單需手動加回；播放中網路封鎖後可能先播放 RAM 緩衝，R4 寫一般約 30 秒至 2 分鐘。[社群實測]（R4） | 到期 `force-stop`＋`disable-user`；若服務或電視斷線，到重新連線才補鎖。[官方文件] [推測/待驗證]（R3） |
| **重開機保護** | 重開機與其他防繞過測試未查到；TVCP 其他 profile 可開被封鎖 App。[官方文件]（R1） | R4 未提供符合單次授權規則的重開機保護結論。（R4） | 停用狀態一般應跨重開機，但本機與 ADB 連線未驗證；授權中重開後若連不上，到期可能無法準時鎖回。[推測/待驗證]（R3） |

## 三、HBO Max 安裝問題與 55P715 規格

### 安裝問題

- R4 寫台灣於 2024 年 11 月 19 日推出 Max、取代 HBO GO；其研究稱官方 App 名稱為 Max，套件為 `com.wbd.stream`／`com.wbd.stream.tv`。[官方文件]（R4）
- R4 寫 Android TV 11 符合其所查最低 Android TV OS 7.1 門檻；並稱無法安裝完全由 Play 商店裝置白名單造成，而非 Widevine L1 缺失。[官方文件] [官方文件/社群實測] [社群實測]（R4）
- R4 的「查不到／不確定」同時寫：Max 官方 Play 後台具體型號白名單無法取得，只能透過舊機搜尋不到的現象推斷過濾機制。（R4）
- R4 引述側載可行，但有強制更新、音訊、遙控器焦點及來源安全風險；另提出 iPhone Max 投放至電視內建 Chromecast，並將側載與投放都列入實機驗證。[社群實測] [官方文件/社群實測]（R4）
- R2、R2b 均表示 Max 安裝原因不在各自研究範圍，未查。（R2、R2b）

來源：https://pressroom.warnermedia.com/ 、https://udn.com/ 、https://help.max.com/

### 規格與更新

| 項目 | R4 查證結果 | 證據等級與來源 |
|---|---|---|
| SoC | Realtek RTD2851，機芯 R851T02，Android 設備型號 `BeyondTV` | [官方文件/社群實測]（R4） |
| CPU | 4 核心 ARM Cortex-A55 約 1.0 GHz、64-bit | [官方文件]（R4） |
| GPU | ARM Mali-470 MP3 | [官方文件]（R4） |
| RAM | 2 GB DDR3 | [官方文件/社群實測]（R4） |
| 儲存 | 16 GB eMMC 5.1；使用者可用約 10～11 GB | [官方文件/社群實測]（R4） |
| 出廠系統 | Android TV 9.0 | [官方文件]（R4） |
| 目前系統 | Android TV 11、TV+OS V5.2.0、`V8-R851T02-LF1V715.000032` | [需求書/實機資訊]（R4、00_REQUIREMENT.md） |
| OTA | R4 稱 R851T02 平台官方維護於 2022 年底至 2023 年實質終止，無 Android 12 或 Google TV 重大升級 | [社群實測]（R4） |

來源：https://www.displayspecifications.com/en/model/49961f77 、https://xdaforums.com/t/official-rooted-image-for-tcl-rt51-rt2851-beyondtv-android-9-platform.4150535/ 、https://xdaforums.com/t/tcl-android-tv-model-55p8m-android-recovery-tcl-beyondtv-beyondtv.3934372/ 、https://4pda.to/

## 四、研究員之間的矛盾清單

以下並列原研究說法與來源，不裁決。

1. **V715 升級後效能變差**
   - R2：「同一個 `V8-R851T02-LF1V715` 版本，XDA 上有 P8M 用戶回報『從 Android 9（V616）升到 Android 11（V715）後效能明顯變差、4K 掉格』」[社群實測]；R2 附錄也說是二手摘要。（R2）
   - R2b：「**查不到**……**沒找到效能變差原文**。R2 自己也標註『二手摘要』，應視為未證實」；建議降級為 [推測/待驗證]。（R2b）

2. **`com.tcl.usercenter`**
   - R2 §4.2：列入「不要碰」；同報告矛盾表又寫 43P615 停用。（R2）
   - R2b：「**不符（R2 自相矛盾）＋反例成立**」：43P615 停用且三重開機正常，50J7000A 是未動而非禁區。（R2b）

3. **`setupwraith`**
   - R2：「本機是 Realtek，**列為絕對不要碰**」；引用停用後音效設定崩潰案例。（R2）
   - R2b：案例機型是 Sony K-55S25，不是 TCL；本機結論是跨型號推論，應標 [社群實測，型號不同]。（R2b）

4. **`com.tcl.tvweishi`、`com.tcl.appmarket2`**
   - R2：部分機型是更新與安裝通路，屬推測，本案不要停。（R2）
   - R2b：toolkit Safe 清單直接停兩者；同板清單未提，維持推測，「**不要升級為禁區**」。（R2b）

5. **RAM 改善幅度與本機可用 RAM**
   - R2、R2b：同 client type 的**另一台** 50J7000A 停 25 套件後約 495→655 MB；沒有 P715 實測數字。[社群實測]（R2、R2b）
   - R4：P715／C715 的可用 RAM 常態約 150～250 MB，停用服務能釋放 250～400 MB；本機 RAM 量測仍列待驗證。[社群實測]（R4）

6. **單純更換 launcher 的效果**
   - R2：「換桌面」單獨做，幾乎不省 RAM。[官方文件][社群實測]（R2）
   - R2b：機制描述相符，但「幾乎不省 RAM」沒有直接量化對比，證據等級可接受但偏樂觀。（R2b）

7. **YouTube 投放與語音**
   - R1：投放、語音是否被 App 封鎖擋住，未查到可靠實測。（R1）
   - R3：投放與語音綁官方套件，停用後應失效；確切失敗表現與本機效果仍待驗證。[官方文件] [推測/待驗證]（R3）

8. **其他影音 App 不受影響**
   - R3：停用 YouTube 時，Netflix／Disney+／四季線上為獨立套件，不受影響。（R3）
   - R4：精準封鎖 YouTube 網域能讓 Netflix、Disney+、四季線上完全正常；同時將這些 App 是否正常列入實機驗證。[社群實測] [推測/待驗證]（R4）
   - R1：ScreenUsage 單 App 封鎖有描述，但 Netflix、4gTV 不受影響未被明文寫出。[推測/待驗證]（R1）

## 五、整理者註記

以下僅列研究間或研究與需求原文的形式問題。

- **整理者註記：R4 的 Max 白名單結論與同報告的不確定清單。** R4 正文稱 55P715 未被加入白名單、無法安裝「完全」由白名單造成；同報告末尾稱具體白名單無法取得，僅能由現象推斷。（R4）
- **整理者註記：R4 的 AP 模式引用對象。** R4 將 HomeShield 全部失效結論套用 M9，所附 FAQ URL 標題在原文為「Differences between Deco Router Mode and AP Mode」。https://www.meshbrand.com/en/support/faq/3264/（R4）
- **整理者註記：R4 規格表的來源與標記。** CPU、GPU、出廠 OS 等標為 [官方文件]，表列來源是 DisplaySpecifications；SoC 等另列 XDA。https://www.displayspecifications.com/en/model/49961f77（R4）
- **整理者註記：R4 的部分結論沒有對應的具體文章 URL。** 例如 YouTube 各網域封鎖表的多項 [社群實測]、Max 側載的 Mobile01／PTT 回報，以及 RAM 緩衝時間，未在該段附具體實測文章 URL。（R4）
- **整理者註記：R2b 的研究方法限制。** R2b 自述研究初期已讀 R2，無法完全避免先入為主；第一部分只寫研究員自己實際讀到的頁面。（R2b）
- **整理者註記：R1 的 TVCP iOS 資料不一致。** 官方 landing 寫有 iOS 家長端；AppGoblin Guardian 頁只列 Android。R1 原文將其列為未解決。（R1）
- **整理者註記：R2 與 R2b 的 `com.tcl.systemserver`／`com.tcl.system.server`。** R2 併寫兩者；R2b 指出是否為不同韌體改名未證實。（R2、R2b）
- **整理者註記：R4 規格表與需求原文的目前韌體一致；R2 同板實測使用的是 V674，並非本機 V715。**（R2、R4、00_REQUIREMENT.md）

## 六、合併後的待實機驗證清單

以下是研究員列出的待驗證事項，合併重複項；**目前尚未獲授權執行設定變更或 ADB 操作**。（00_REQUIREMENT.md）

1. **本機設備與套件清單**：確認 RAM、儲存、`/data` 分割區及實際 App 相容性；列出 TCL、Realtek、YouTube、Kids、瀏覽器、4gTV 等套件，確認 `systemserver`／`system.server`、`tvpassthrough`、`guard`、`usercenter`、`bootadservice` 等是否存在；確認台灣在地預載與開機廣告。（R1、R2、R2b、R3、R4）
2. **卡頓基準**：開機閒置 2 分鐘與使用 2 小時後記錄 `MemAvailable`、背景服務、CPU、`/data` 可用空間；確認 `diskstats` 是否存在及常態數值。若未來有核准的停用作業，再比較前後數值。（R2、R2b、R4）
3. **首頁與遙控器**：確認 HOME 元件與 priority；若未來測 Projectivy，確認 HOME 是否被搶回，以及 Netflix、YouTube、T 鍵、訊號源鍵的行為。（R2、R2b）
4. **個別套件行為**：`overseasappshow`、`tvrecommendations` 停用後的首頁與復活行為；`usercenter` 與帳號／開機行為；`katniss` 撤麥克風權限前後 CPU 與語音鍵；OTA 後停用清單復活比例與新增預載。（R2、R2b）
5. **內建及現成家長工具**：確認 55P715 是否有家長控制／設定檔與逐 App 鎖定；ScreenUsage 的 iPhone／Remote、價格與 Accessibility；TVCP 的 iOS 端、單 App 計時、到期重鎖、其他 App 影響；Kidslox 的 Android TV 11 支援；TVCP／TVGuard 權限對 Netflix、四季線上的影響。（R1）
6. **所有 YouTube 入口與防繞過**：逐項測原生 App、遙控器快捷鍵、Kids、瀏覽器、iPhone 投放、語音及第三方播放器；測重開機、強制停止、清除資料、解除安裝、切換帳號與設定中手動啟用；確認 Netflix、Disney+、四季線上等不受影響。（R1、R3、R4）
7. **ADB 與 HA 連線**：首次授權及開關名稱、`ro.adb.secure`／5555、重開後連線與停用狀態、待機 10 分鐘連線、固定 IP、HA `adb_command`、`suspend` 可行性及跨重開機行為。（R2、R2b、R3）
8. **授權到期與故障恢復**：驗證播放中 `force-stop`、電視關機後到期再開機時補鎖、HA 主機斷電／斷網後逾期行為；恢復原廠後重建只演練流程，不預先執行。（R3）
9. **M9 網路層**：確認 Router／AP 模式與免費黑名單功能；測指定 YouTube 網域封鎖時各影音 App 的連線與播放，並確認是否有 `8.8.8.8:53` 或 DoT 繞過。（R4）
10. **Max**：確認 Play 商店相容性與套件；若未來獲准測試，驗證側載登入、遙控器焦點、4K／音訊，以及 iPhone 投放畫質與穩定度。（R2b、R4）
11. **4gTV 依賴**：確認套件名、播放正常，以及是否依賴 `com.tcl.tv` 的 channel MIME handler。（R2、R2b）

## 七、合併後的查不到／不確定清單

1. **55P715 專屬停用清單與數據**：R2 稱 CleanYourTV 無 P715 清單；R2b 未逐一窮舉確認，也未核對 UAD-NG 資料庫筆數。沒有 P715 停用前後 RAM 實測。（R2、R2b）
2. **台灣預載、開機廣告與 TCL 保固立場**：台灣版在地預載、`waterfall.overseas` 是否啟用及 TCL 官方對停用預載的立場未查到；Mobile01 開箱內文 403。（R2、R2b）
3. **Projectivy 在 TCL Android TV 11**：沒有直接回報；launcher 本身的 RAM 足跡未有可靠數字。現有 TCL HOME 問題多為其他機型／版本。（R2、R2b）
4. **效能門檻與老化**：`MemAvailable <300 MB` 等是經驗推論；eMMC 老化無官方或可信社群絕對門檻；2026 年版 YouTube／Play 服務是否變肥無量化數據。（R2、R2b）
5. **套件風險與名稱**：`rc.ota`、`ttvs`、`tvweishi`、`appmarket2`、DRM 相關的 TCL 直接災情不足；`systemserver` 與 `system.server` 是否同套件改名未證實；`usercenter` 的台灣版依賴未知。（R2b）
6. **ScreenUsage**：單次 N 分鐘、bonus、15／30／60 分鐘選項、到期是否關閉播放、Remote 是否支援 iPhone／雲端及具體價格未查到；Play 原頁讀取失敗。（R1）
7. **其他現成工具與內建功能**：Family Link 在 Android TV 11 的支援、Google TV 的 YouTube 監護是否適用、TVCP 是否有未公開的核准加時、Kidslox Android TV 支援與價格、YourHour iPhone／計時功能、Kids Place Android TV 支援，均未確定；未找到任何工具在 TCL 55P715 或 Android TV 11 的實機回報。（R1）
8. **入口封鎖**：投放、遙控器快捷鍵、語音被 App 封鎖後的本機實測與錯誤畫面未查到；YouTube Kids TV 套件名未確認。（R1、R3）
9. **ADB 防繞過與持續性**：55P715 重開機／待機後網路 ADB 狀態、設定中能否手動啟用停用 App、shell `suspend` 及跨重開機行為、Android TV 11 是否有可利用的多使用者／兒童模式，均未確定。（R3）
10. **網路層與遠端成本**：M9 台灣 V4.0 對 DoT 的攔截能力未確定；Cloudflare Tunnel 免費額度與網域需求未深入；R3 未查樹莓派、HA Green、NAS 的具體價格與功耗。（R3、R4）
11. **Max 與降級**：Max Play 後台具體白名單無法取得；55P715 能否安全降回 Android TV 9、是否有 anti-rollback 或破壞 Widevine L1，缺乏本機確切回報。（R4）
12. **研究來源限制**：R2b 未重查 GitHub API 星級／push 日期與 UAD 清單數字；XDA 部分內容經 r.jina.ai 代理，Mobile01 內文未讀。R1 的部分候選只有搜尋摘要或二手頁面。（R1、R2b）

未查清單外檔案，也未上網或操作設備；所有實機效果與研究中列出的風險仍待驗證。