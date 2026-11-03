# R2｜需求 A：TCL 55P715 瘦身工具與卡頓診斷

> 研究範圍：只做網路資料查詢與整理。**本文未連接、未操作任何實機，未執行 adb，所有指令都是「待驗證時再跑」的唯讀指令。**
> 證據等級：[官方文件]＝廠商／Google／AOSP 原始碼／專案 README；[社群實測]＝有人在同平台或同型號實際做過並回報；[推測/待驗證]＝我的推論或只在別型號成立。

---

## 0. 先講結論（給決策用）

1. **這台不需要「工具」，需要「一份針對本機的停用清單＋可還原紀錄」。** 四支工具裡只有 UAD-NG 與 CleanYourTV 會真的改套件狀態；Projectivy 只換首頁，android-tv-debloat-toolkit 是固定清單的 Windows 小工具。[官方文件][社群實測]

2. **最接近本機的實測數據**：同一 client type `TCL-AP-RT2851-S1`、`V8-R851T02-LF1V674`（AR04）的 50J7000A，停用 25 個套件後 `MemAvailable` 從約 **495 MB → 655 MB**，重開機後全數維持停用、Home 正常、HDMI 切換正常。[社群實測]

3. **但「換桌面」單獨做，幾乎不省 RAM。** TCL 的推薦列、促銷內容、開機推送是**獨立套件**（`com.google.android.tvrecommendations`、`com.tcl.tv.tclhome_passive`、`com.tcl.waterfall.overseas`、`com.tcl.bi`），換 launcher 不會停掉它們；Projectivy 自己的 override 模式甚至是「攔截原 launcher 的啟動」，代表原 launcher 還在。[官方文件][社群實測]

4. **本案一律用 `pm disable-user --user 0`，不要用 `uninstall --user 0`。** UAD-NG 官方文件自己寫「非 Recommended 等級的套件應該用 disable 而非 uninstall，uninstall 已知會造成系統不穩定」，且 disable 可以用 `pm enable` 一行還原。[官方文件]

5. **卡頓可能不全是 bloat。** 同一個 `V8-R851T02-LF1V715` 版本，XDA 上有 P8M 用戶回報「從 Android 9（V616）升到 Android 11（V715）後效能明顯變差、4K 掉格」；另一台同世代 BeyondTV4 機器量到語音熱字（`com.google.android.katniss`）常駐吃掉 **約 28% 單核 CPU**，撤銷麥克風權限後降到 0.4%。**先量測，再決定要不要動套件。**[社群實測]

---

## 1. 先把「這台到底是什麼」定位清楚

| 項目 | 值 | 意義 |
|---|---|---|
| OS | Android TV 11（API 30） | **不是 Google TV**。沒有 Google TV 首頁（`launcherx`）、沒有 Google TV 的 kids profile／Family Link 監控 |
| TCL 層 | TV+OS V5.2.0 | TCL 自家 UI／服務層，套件以 `com.tcl.*` 為主 |
| 平台 | TCL-AP-RT2851-S1（Realtek RTD2851 / `rtd285o`） | 與 50J7000A、55EP640、50P615、43P615 同族；依 50J7000A（同 client type）推定為 32-bit `armeabi-v7a`、2 GB RAM |
| 韌體 | V8-R851T02-LF1V715.000032 | RT51 世代 Android 11 build；XDA 上 P715 用戶收到的正是「RT51 V715」更新包 |

**Android TV 11 與 Google TV 的差別（本題必分清）**

- Google 官方的「兒童設定檔（kids profile）」是從 **Google TV 首頁**的頭像建立，再由 Family Link 監控；文件寫的是「Google TV devices」。**Android TV 11 沒有這條路。**[官方文件] <https://support.google.com/families/answer/10491112>
- 因此需求 B 那類「用 Family Link 管電視」在這台上直接不成立；本機只能用第三方 App、網路層或 ADB 控制。
- 反過來說，Android TV 11 對本案有利：首頁是 `com.google.android.tvlauncher`（HOME priority 2），沒有 `launcherx`／`setupwraith` 那套 Google TV 的搶 Home 問題——但 **Realtek 平台的 `setupwraith` 仍不可亂停**（見 §4）。

**有沒有 55P715 的現成清單？沒有。**
CleanYourTV 的 `examples/` 我逐一看過，TCL 有 77 份清單，最接近的是：

| 清單 | 韌體 | 平台 | 與本機關係 |
|---|---|---|---|
| `tcl-50j7000a` | V8-R851T02-LF1V674（AR04） | **TCL-AP-RT2851-S1** | client type 完全相同，最接近 |
| `tcl-43p615` | AR06 | RTD2851 | 同板、Android TV 11 |
| `tcl-55ep640` / `tcl-65ep68` | AR04 / V722 | RTD2851 | 同板，不同地區預載 |
| `tcl-50p615` / `tcl-65p615` | AR05 / V637 | RTD2851 | 同系列 P615 |
| `tcl-32s334` | Android TV 9 | **RTD2851** | 同晶片、舊 OS |

→ 這幾份只能當「語彙表」用，**不可整份套用**（規則 4）。

---

## 2. 四支工具逐一解剖

### 2.1 UAD-NG（Universal Android Debloater Next Generation）

| 項目 | 內容 |
|---|---|
| 本質 | Rust 寫的**桌面 GUI**（Linux/Windows/macOS），透過 ADB 改套件狀態；非腳本、非 App。注意：官方 FAQ 說 Linux/Windows 執行檔只支援 **x86-64**，ARM（含 Apple Silicon）要自行編譯 |
| 運作方式 | Android 6+：disable = `pm disable-user --user <u> <pkg>` + `pm am force-stop` + `pm clear`；uninstall = `pm uninstall --user <u> <pkg>`；還原 = `cmd package install-existing --user <u> <pkg>`。UI 上可切換 disable/uninstall 模式 [官方文件] |
| 是否真省 RAM | 是——它會把套件「凍結」，重開機後不會被喚醒（官方說法）；但只在**你選對套件**時有效 |
| Android TV 11 | 可用（Android 6+ 即可）；但**沒有電視/機型清單**，清單是「以套件為單位」的通用資料庫，TCL 條目多半來自 TCL **手機** |
| TCL 專屬支援 | 無 P715/P615 條目；有 TCL 手機套件條目（如 TGuard、NXTVISION）。有 Android TV 社群回報的踩雷：`setupwraith`、`tvlauncher` |
| 維護 | 活躍：9,624★、`pushed_at` 2026-10-26、未 archived、GPL-3.0、277 open issues |
| 前置 | 開開發者選項＋USB 偵錯；可 USB 或網路 ADB |
| 還原 | Snapshot（Backup/Restore）；或逐一套件 restore。**沒有「一鍵回到出廠」** |

**關鍵警告（官方文件自己寫的）**：套件清單裡的 **"Recommended" 不是真的推薦**（原文：「Recommended IS NOT ACTUALLY RECOMMENDED」，是舊專案遺留）。另外官方明講：**uninstall 比 disable 危險**——相依 App 仍會嘗試喚醒不存在的套件，造成耗電與不穩；而且 uninstall 會把「已更新的 APK」一起移除。[官方文件]

且 `uad_lists.json`（5,382 筆）我用程式掃過，含 "TCL" 的只有 **17 筆、全部是 `Oem`（手機）**：TGuard、NXTVISION、TCL 氣象／影片播放器、Support Center、Privacy Policy 通知等。**沒有一筆是電視的 `com.tcl.bi`、`tclhome_passive`、`waterfall`、`tvinput`。**[官方文件：<https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json>]

→ 對本案的定位：**可以當「執行引擎＋安全網（snapshot）」，但清單要自己建。**

### 2.2 CleanYourTV（前身 tv-vibehack，Gathin23/cleanyourtv）

| 項目 | 內容 |
|---|---|
| 本質 | **不是單一程式**，是一套「給 coding agent 的提示詞＋Python/Bash 工具＋自製 launcher」的組合包；也可純手動跑 |
| 運作方式 | ①備份（寫 `~/.cleanyourtv/` 紀錄：每個套件原本狀態、誰擁有 Home、三個動畫縮放值）②`pm disable-user` 停用清單（**不解除安裝**）③安裝 532 KB 的原生 launcher 並設為 Home。全程不 root、不刷機、不刪檔 [官方文件] |
| 是否真省 RAM | **是，且是四支裡唯一有同平台實測數字的**：50J7000A 停 25 個套件後 MemAvailable 495→655 MB；另一台 L40S66E 回報「停用 TGuard 後 free RAM 明顯變高」 |
| Android TV 11 | 明確支援（Android TV / Google TV 都可；launcher 需 Android 8.0+） |
| TCL 支援 | TCL 77 份清單；**但沒有 P715**；已標註多個 TCL 地雷（見 §4） |
| 已知限制 | ①中國版 TCL 會拒絕安裝任何第三方 launcher ②Fire TV 只能做 debloat、不能換 Home ③Google TV 的 `setupwraith` 可能搶 Home 甚至 crash-loop |
| 維護 | 極活躍：44,579★、`pushed_at` 2026-10-29、180 open issues、未 archived；MIT（GitHub API 顯示 `NOASSERTION`，README 寫 MIT） |
| 前置 | 電腦（macOS/Windows/Linux）＋Python 3；若要自製 launcher 需 JDK 17–22 + Android SDK 34。**iPhone 不能直接用**（另有社群 Android App） |
| 還原 | `tools/restore.sh` 依紀錄回放（含把 Home 還給原本的元件、動畫縮放還原）；沒紀錄就整批 `pm enable`；TV 連不上時，電視自己的「設定→應用程式」也能逐一啟用 |

**對本案最實際的一點**：它的 `disable.sh` 有「Home 保護」——清單裡若含目前擁有 Home 的 App，且沒有其他 App 回應 HOME，會**跳過那一條**並說明原因。這正是我們最怕的「停用 launcher 後開不了機」的人工防呆。[官方文件]

**風險提醒**：README 的賣點之一是「15 分鐘搞定、AI 幫你決定」——對本案（要可還原、要穩定）建議**不要用 agent 模式**，改成手動 `tools/tv.py` + 自建清單，逐批套用。

### 2.3 android-tv-debloat-toolkit（社群口中的「tv-debloat」）

> 名稱說明：「tv-debloat」在社群不是單一專案。我查到兩個：(a) `seun-novodev/android-tv-debloat-toolkit`（788★，最常被叫這個名字）；(b) `chrissy-dev/debloat-tlc-android-tv`（32★，**已 archived**，2024-11 後未動）。以下指 (a)。

| 項目 | 內容 |
|---|---|
| 本質 | **Windows 專用 Python + PySimpleGUI 小工具**，內建固定套件清單 |
| 運作方式 | 原始碼只有一種改法：`pm disable-user --user 0 <pkg>`（`run_toolkit.py` 第 262、276 行）。沒有 uninstall、沒有備份紀錄、沒有還原腳本 [官方文件] |
| 是否真省 RAM | 沒有公開量測。清單本身有效（停用當然會少跑），但**無數據、無回復機制** |
| Android TV 11 | 支援（含 Android 11+ 無線偵錯配對流程） |
| TCL 支援 | 就是為 TCL 寫的（另有 Google/onn/Shield） |
| 維護 | **README 自述「not actively maintained」**；最後 push 2026-06-28；MIT；未 archived |
| 前置 | Windows 10/11 + Python 3.8+；同網段 Wi-Fi |
| 還原 | **無**。只能自己記下動過哪些，再用 `pm enable` 一個個還原 |
| 清單品質 | Safe/Advanced 分級、有 ✅/⚠️/🚫 標記，但**與 CleanYourTV 直接衝突**：它把 `com.tcl.tv` 標為「⚠️ 注意」，CleanYourTV 的 TCL 清單則寫「**DO NOT DISABLE**（Live TV、tuner、HDMI）」 |

→ 對本案的定位：**不要用**（Windows-only、無還原、清單過時）。但它拿來當「還有哪些 TCL 套件存在」的線索可以（例如 `com.tcl.bootadservice`、`com.tcl.tvweishi`、`com.tcl.appmarket2`、`com.tcl.pvr.pvrplayer` 這幾個 CleanYourTV 清單沒列）。

### 2.4 Projectivy Launcher

| 項目 | 內容 |
|---|---|
| 本質 | **第三方 Android TV 桌面（launcher）**，不是瘦身工具；閉源、免費＋付費解鎖。1M+ 下載、4.4★/3.12K 評論、Play 最後更新 2026-10-01 [官方文件] |
| 運作方式 | ①安裝後設為預設 Home（`cmd package set-home-activity`）②或走「Override stock launcher」：**不停用原 launcher，用無障礙服務攔截對原 launcher 的呼叫**，把自己蓋上去。開發者原文：「the preferred way is still to freeze/uninstall it」[官方文件] |
| 是否真省 RAM | **只換桌面本身幾乎不省**。省的是「原 launcher 這支行程」，但 TCL 的推薦列／促銷／telemetry 是別支套件，照跑。若要省，**必須另外停用** `com.google.android.tvrecommendations`、`com.tcl.tv.tclhome_passive`、`com.tcl.waterfall.overseas`、`com.tcl.bi` 等 |
| 有數據嗎 | 唯一的量化實驗是 NVIDIA Shield（Android 11）上的「整包 profile」：停用原 launcher＋recommendations＋backdrop＋GameStream 並改用 Projectivy 後 MemAvailable **1,035,036 → 1,202,528 KiB（+163.6 MiB）**、行程 338→334。作者自己聲明「不能證明開機速度、流暢度或 CPU 表現」[社群實測，型號不同] |
| Android TV 11 | 支援（4.00 起相容到 5.1.1+）[官方文件] |
| TCL 相容性 | **有 TCL 專屬問題**：TCL 韌體會攔 HOME intent／恢復 launcher 優先權，導致「過一陣子跳回原 launcher」；Android 14 的 TCL 會變成「開機後要按一次 Home 才進 Projectivy」。回報型號為 75T7B（Android TV 12 / G10 / rtd6748）、C855（Android TV 14）、R646 [社群實測，型號不同] |
| 其他實務問題 | 待機後整個 launcher 被系統終止（#354）；明明已開無障礙仍跳「請啟用無障礙」（#377，TCL R646）——兩者僅依 issue 標題，未細讀內容；喚醒後數分鐘無反應的評論（Play 評論，2026-08） |
| 前置 | 側載 APK 或 Play 安裝；建議搭配 `pm disable-user com.google.android.tvlauncher` 才穩 |
| 還原 | 解除安裝＋`cmd package set-home-activity com.google.android.tvlauncher`；若曾停用原 launcher，要 `pm enable` |

**停用原 launcher 的風險（本機適用）**

- 沒先裝好可用 launcher 就停用 → Home 會落到 **factory test app**（CleanYourTV README 的三大踩雷之一）。[官方文件]
- 這代 TCL 的 `com.google.android.tvlauncher` 是 **HOME priority 2**，不停用它，`set-home-activity` 會印 Success 但實際無效。[社群實測，43P615 / 50J7000A]
- 停用後可能失去：Google TV 的「帳戶與設定檔」頁（C845X2 回報，Google TV 14）、部分遙控器 App 快捷鍵（依賴 `launcherx` 的機型）。**Android TV 11 的遙控器 App 鍵走 `com.tcl.keycustomfunctionservice`，理論上不受影響——但這點在本機未驗證。**[推測/待驗證]
- 還原路徑永遠存在：`pm enable com.google.android.tvlauncher` + `cmd package set-home-activity com.google.android.tvlauncher`。

**「換桌面」到底值不值得？**
對本機（2 GB RAM、常卡頓）我的判斷：**值得，但順序是「先停套件、後換桌面」**。先停促銷／推薦／telemetry（有數字支持），桌面要不要換是最後的美感決定；Projectivy 在 TCL 上有已知的 HOME 攔截問題，換上去可能換來新的不穩定。[推測/待驗證]

### 2.5 對照表

| | UAD-NG | CleanYourTV | debloat-toolkit | Projectivy |
|---|---|---|---|---|
| 類型 | GUI 停用/解除安裝 | 清單＋腳本＋launcher | Windows GUI | Launcher |
| 改套件狀態 | ✅ disable / uninstall | ✅ 只 disable | ✅ 只 disable | ❌ |
| 換首頁 | ❌ | ✅（自製 launcher） | ❌（只教你裝 FLauncher） | ✅ |
| 同平台實測 RAM | ❌ | ✅ 495→655 MB | ❌ | ⚠️ 只有 Shield +163 MiB |
| 有 TCL 電視清單 | ❌ | ✅ 77 份（無 P715） | ✅ 固定清單 | ❌ |
| 備份/還原 | Snapshot | ✅ 完整紀錄 | ❌ | n/a |
| 維護 | 活躍 | 極活躍 | 自述不活躍 | 活躍 |
| 授權 | GPL-3.0 | MIT | MIT | 閉源 |
| 平台 | mac/win/linux | mac/win/linux | **Windows only** | 電視端 |
| 對本案建議 | 可當引擎 | **主要參考** | 不用 | 最後再決定 |

---

## 3. disable-user vs uninstall --user 0 vs 真解除安裝

| | `pm disable-user --user 0` | `pm uninstall --user 0` | 真解除安裝（改 /system） |
|---|---|---|---|
| 實際做什麼 | 標記為停用（DISABLED_USER），APK 仍在 `/system` 或 `/data` | 對 user 0 移除，APK 仍在 `/system`，但對使用者「不存在」 | 需要 root 改唯讀分割區 |
| 可逆 | ✅ `pm enable <pkg>` | ✅ `cmd package install-existing --user 0 <pkg>`（但**資料已清**、更新版本已移除） | ❌ 只能刷回原廠映像 |
| 對其他 App 的可見性 | 仍可見（Settings、`QUERY_ALL_PACKAGES` 的 App 看得到），可能被「依賴它」的程式叫醒 | 看起來不存在，但官方 API 仍可列出 | 完全不存在 |
| 風險 | 「最安全、對穩定性影響最小」；但**可能靜默失敗**（馬上被系統復活）；更新不會被移除 | 官方明講「**較不安全**，相依 App 會一直嘗試喚醒不存在的套件」，且會把已更新的 APK 一起移除 | 不可逆 |
| OTA 後 | **會回來**。CleanYourTV 實測：更新「裝在清理過的系統上」，清單套件全部復活、Home 回原 launcher，還會多出清單沒列的新預載 | 同上 | 跟著韌體走 |
| 恢復原廠 | **一定復活**。UAD-NG 官方：「factory reset 會還原所有被 debloat 的套件」（`/data` 被清） | 同上 | 同上 |
| 例外 | TCL 韌體會**主動**復活特定套件：`com.google.android.videos`（每次開機）、`com.tcl.usercenter`／`notereminder`／`tclhome_master`／`smartalexa`（`com.tcl.overseasappshow` 還在時）| 同左 | — |

**本案建議：全部用 `disable-user`。**
理由：(1) 一行還原；(2) 不會把使用者已更新的 APK 弄掉；(3) 官方自己說 disable 才是低風險做法；(4) 這台是共用電視，隨時可能要「先還原再說」。
**不要用 uninstall 的具體情境**：只要那個套件有任何機會是別的東西的相依（HDMI/TvInput、DRM、藍牙遙控、OTA），一律 disable。
**真要 uninstall 的唯一合理情境**：disable 一直靜默失敗、套件又明顯在吃資源——而且必須先記下 `pm path <pkg>`，確認還原指令可用。

---

## 4. TCL Android TV 預載套件分類（**僅為語彙表，必須本機 `pm list packages` 比對**）

> 以下名稱全部來自 CleanYourTV 的 `tcl-43p615` / `tcl-50j7000a`（同板）與 `tcl-beyondtv4-uk-ar05` 實測清單，以及 debloat-toolkit 的內建清單。**「可安全停用」是指「有人在同一塊板子上停過且回報沒事」，不是你這台一定沒事。**

### 4.1 通常可停用（先停促銷、再停 telemetry）

| 類別 | 套件 | 說明 |
|---|---|---|
| 遙測／歸因 | `com.tcl.bi`、`com.google.android.feedback`、`com.google.android.partnersetup`、`com.google.android.onetimeinitializer`、`android.autoinstalls.config.tcl.device` | TCL BI（Firebase/Crashlytics）、送意見、夥伴 App 歸因、夥伴 App 自動安裝器 |
| TCL 內容／促銷 | **`com.tcl.overseasappshow`（先關這支）**、`com.tcl.tv.tclhome_passive`、`com.tcl.waterfall.overseas`、`com.tcl.messagebox`、`com.tcl.esticker`、`com.tcl.dashboard`、`com.tcl.copydatatotv`、`com.tcl.useragreement`、`com.tcl.notereminder` | `overseasappshow` 是「開機把其他促銷套件叫回來」的關鍵；`tclhome_passive` 會在開機把 TCL 內容推上首頁 |
| 推薦列 | `com.google.android.tvrecommendations` | 首頁推薦列（換桌面後仍要單獨停） |
| TGuard | `com.tcl.guard` | 43P615 停得掉；50J7000A 因 PERSISTENT 而保留；L40S66E 回報停掉後 free RAM 變高。**兩派做法都有**，建議先觀望 |
| 語音（可選，代價：麥克風鍵失效） | `com.google.android.katniss`、`com.google.android.tv.assistant`、`com.tcl.assistant`、`com.tcl.micmanager`、`com.tcl.smartalexa` | 若不停用，至少考慮撤銷麥克風權限 |
| 鏡像（可選） | `com.tcl.miracast`、`com.tcl.MultiScreenInteraction_TV` | MagiConnect／Miracast |
| 螢幕保護 | `com.google.android.backdrop`、`com.android.dreams.basic` | 換桌面後通常不需要 |
| 預載 App／遊戲 | `com.amazon.amazonvideo.livingroom`、`com.vectorunit.purple.googleplay`… | **本案要保留 Netflix/Disney+/HBO/YouTube/4gTV，不要照抄這段** |

### 4.2 不要碰（停錯就是「電視怪怪的」）

| 套件 | 作用 | 停掉會怎樣 |
|---|---|---|
| `com.tcl.tv` | Live TV、**channel MIME 的唯一 handler** | 訊號源／Live TV 打不開；部分機型 Inputs 選單壞 |
| `com.tcl.tvinput` | tuner + HDMI/AV（TvInputService） | HDMI 全掛 |
| `com.tcl.tvpassthrough`（新式） | HDMI/AV 直通 | 同上 |
| `com.tcl.keycustomfunctionservice` | 遙控器按鍵、**FallbackHome** | 遙控器／Home 異常 |
| `com.tcl.common.shortcutmenu` | 自訂鍵／快捷選單 | 快捷鍵失效 |
| `com.tcl.partnercustomizer` | 遙控器 **T 鍵**的 `TKeyActivity` | T 鍵沒反應 |
| `com.tcl.inputmethod.international` | 預設輸入法 | 無法輸入（搜尋、帳密） |
| `com.tcl.settings`、`com.tcl.initsetup`、`com.tcl.factory.view`、`com.tcl.providers.config` | 設定／初始化／工廠模式 | 開機精靈或設定崩 |
| `com.tcl.xian.StartandroidService` | 裝置認證 | 部分服務（含 OTA 裝置號）失效 |
| `com.tcl.systemserver` / `com.tcl.system.server` | 系統服務；**會拿 `CHANGE_COMPONENT_ENABLED_STATE` 在每次開機把套件打開** | 停掉風險高；正確做法是「讓它開，再用 appops 限制背景」 |
| `com.tcl.autopair` | 藍牙遙控器配對 | 遙控器配不回來 |
| `com.tcl.versionUpdateApp` / `rc.ota` / `ttvs` | **TCL OTA 更新器** | 收不到韌體更新（有人故意停，本案不建議） |
| `com.tcl.usercenter` | TCL 帳號 | 部分地區開機自己復活；停用後帳號功能消失 |
| **`com.google.android.tungsten.setupwraith`** | 設定精靈；**Realtek 機器上 `com.realtek.advancesetting` 要跟它要 provider** | **UAD-NG issue #994 實測：停用後 `com.realtek.advancesetting` FATAL EXCEPTION，系統音效設定崩、喇叭音量無法調整**。本機是 Realtek，**列為絕對不要碰** |
| `com.google.android.gms` / `com.android.vending` | Play 服務／商店 | 串流 App 全掛 |
| `com.google.android.apps.mediashell` | Chromecast | 投放失效 |
| `com.google.android.tv.remote.service` | 手機遙控 | 手機 App 遙控失效 |
| `com.tcl.tvweishi`、`com.tcl.appmarket2` | TCL 衛士／應用商店 | 部分機型是更新與安裝通路（推測）；**本案不要停** |
| DRM／播放授權相關 | 任何含 `widevine`、`drm`、`mediadrm` 名稱者 | Netflix/Disney+ 黑畫面或授權失敗 |

**兩份清單互相矛盾的地方（正是「不可照抄」的證明）**

| 套件 | CleanYourTV（43P615/50J7000A） | debloat-toolkit |
|---|---|---|
| `com.tcl.tv` | 「DO NOT DISABLE」 | 標「⚠️ 注意」（可停） |
| `com.tcl.guard` | 一份停、一份保留 | Safe 清單裡直接停 |
| `com.tcl.usercenter` | 43P615 停 | Advanced ✅ 停 |

→ 同一塊 Realtek 板子、同一世代的工具，結論就不一樣。**必須在本機 `pm list packages` + 逐批停用 + 每批重開機驗證。**

---

## 5. 卡頓診斷：可執行的唯讀指令與判讀

> 全部唯讀。先量 baseline（剛開機 2 分鐘、停在首頁），再對照「用了兩小時後」。數值請記下來，不要憑感覺。

### 5.1 記憶體

| 指令 | 看什麼 | 判讀 |
|---|---|---|
| `cat /proc/meminfo` | `MemTotal` / `MemAvailable` / `Cached` / `SwapTotal` | 2 GB 機種 `MemAvailable` 常態 < 300 MB＝記憶體壓力大；< 200 MB 隨時可能被 LMK 殺行程。[推測，基於 LMK/direct reclaim 機制] 同平台的健康值參考：停用後約 655 MB |
| `dumpsys meminfo` | 全機 `Total PSS by process`、`Free RAM`、`Used RAM`、`Lost RAM` | 找「不該那麼大」的第三方；`Lost RAM` 異常大＝kernel/ION 洩漏。[官方文件] 注意：`Lost RAM` 佔比沒有官方門檻，只能前後對比 |
| `dumpsys meminfo <pkg>` | 單一 App 的 `TOTAL PSS`、`Private Dirty` | PSS 是「可比較的實際重量」；`Private Dirty` 才是殺掉行程才能回收的部分。[官方文件] |
| `dumpsys procstats --hours 3` | 每個 App 的 PSS/USS/RSS 區間與**背景執行時間百分比** | 找出「幾乎 100% 在跑但你不用的」——這才是該停的對象。[官方文件] |

**官方對「卡頓」的定義（可以直接引用在報告裡）**：Android TV 官方文件列出的高記憶體後果包含——系統服務（音量、畫質面板、語音助理）變鈍、**LMK 反覆殺與重啟造成資源尖峰**、**切回 Launcher 明顯延遲**、kernel 進入 **direct reclaim** 導致掉格。也就是說：「按 Home 要等好幾秒」「開 App 卡一下」正是記憶體壓力的典型症狀。[官方文件] <https://developer.android.com/training/tv/playback/memory>

### 5.2 CPU／背景活動

| 指令 | 看什麼 | 判讀 |
|---|---|---|
| `dumpsys cpuinfo` | 各 process 的 `%CPU`（開機累計） | 單一 App 長期 > 10% 就是異常。實例：`com.google.android.katniss` 常駐熱字吃 **28% 單核**，`pm revoke ... RECORD_AUDIO` 後降到 0.4%。[社群實測] |
| `top -n 1 -o %CPU` | 即時前幾名 | 對照 `dumpsys cpuinfo` 確認是不是持續性 |
| `dumpsys activity processes` | Persistent / Cached / Empty 行程清單與 oom adj | Persistent 清單愈長＝開機就註定吃掉的 RAM 愈多 |
| `logcat -b events -d \| grep -i am_kill` | 被 LMK 殺掉的行程與 oom score | 短短幾分鐘就有多筆＝記憶體真的不夠。[待驗證：tag 名稱依平台版本可能不同，先 `logcat -b events -d \| head` 確認] |

### 5.3 儲存空間（很容易被忽略的卡頓源）

| 指令 | 看什麼 | 判讀 |
|---|---|---|
| `df -h /data` | 可用空間與使用率 | **Android 的「低儲存」門檻是 `min(分割區 5%, 500 MB)`**（AOSP `StorageManager.getStorageLowBytes()`，`DEFAULT_STORAGE_THRESHOLD_PERCENT_LOW = 5`、上限 500 MB；OEM 可用 `sys_storage_threshold_*` 覆寫）。16 GB 機種 → 5% 約 550 MB，取上限後門檻就是 **500 MB**。低於此，系統會開始砍快取、Play 安裝失敗、App 寫入變慢。[官方文件] <https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/os/storage/StorageManager.java> |
| `dumpsys diskstats` | 各分割區 I/O 延遲百分位（p50/p95/p99）、fsync 次數 | p95/p99 延遲與同機歷史相比明顯惡化＝儲存媒體老化。[推測/待驗證：此服務是否在本韌體存在需先 `dumpsys -l \| grep disk`] |
| `cmd package list packages -3` | 使用者自行安裝的 App | 第三方愈多，背景復活源愈多 |

**實務數據點**：CleanYourTV 在某台 TCL（V8-T221T01-LF1V121）記錄「**安裝任何東西前需要 500 MB 可用空間**，用 `pm trim-caches` 清」；另一台則記錄「`/data` 幾乎滿 → 安裝被擋；RAM 太低時 TGuard 會在安裝中途殺掉 Play」。這兩條同時說明：**空間不足與 RAM 不足會互相放大。**[社群實測]

### 5.4 其他訊號

| 指令 | 用途 |
|---|---|
| `getprop \| grep -E "ro.build|ro.product|ro.adb"` | 確認韌體／API／`ro.adb.secure`（本族 `ro.adb.secure=1`）[社群實測] |
| `dumpsys gfxinfo <pkg> framestats` | 該 App 的掉格統計（官方建議用法） |
| `dumpsys -l` | 確認這台到底有哪些服務可查（不同 TCL 韌體差很多） |
| `logcat -b crash -d` | 有沒有常駐 crash loop（crash loop 本身就是卡頓源） |
| `dumpsys package <pkg> \| grep -E "versionName\|firstInstallTime\|lastUpdateTime"` | 判斷是不是「某次 Play 更新後才開始卡」 |

### 5.5 判讀流程（建議順序；門檻為經驗法則，非官方數字）

1. 剛開機閒置 2 分鐘 → 記錄 `MemAvailable`、`dumpsys meminfo` 的 Free RAM、`dumpsys procstats --hours 1`。
2. 正常使用 2 小時（開 Netflix／YouTube／四季）→ 再量一次。
3. `MemAvailable` 掉超過 40% 且回不來 → 記憶體壓力（往 §4.1 找促銷／telemetry）。
4. `MemAvailable` 沒掉但 CPU 一直 > 50% → 找單一燃燒者（katniss 類）。
5. 兩者都正常，只有「換 App／回 Home」慢 → 可能是 launcher 或儲存 I/O（看 `df`、`dumpsys diskstats`）。
6. `df /data` < 500 MB → 先清空間，不要急著停套件。

---

## 6. 非工具的其他改善手段

| 手段 | 做法 | 效果 | 風險 |
|---|---|---|---|
| 清 App 快取 | 設定→應用程式→逐一「清除快取」 | 釋放 `/data`；對 RAM 無效 | 幾乎無；但別按「清除資料」（會登出） |
| `pm trim-caches` | ADB 觸發系統修剪快取 | 快速回收數百 MB（安裝前必做）[社群實測] | 低 |
| 關閉動畫縮放 | 開發者選項的 3 個 scale 設 0.5（或 `settings put global window_animation_scale 0.5`） | **感覺**變順，不減少 RAM/CPU；CleanYourTV 也會改這三個值並記錄以便還原 | 低（可還原） |
| 限制背景執行 | `cmd appops set <pkg> RUN_ANY_IN_BACKGROUND ignore` | 對「被系統開機復活」的套件有效，且**不動套件狀態**——AR05 那台就是用這招處理 7 個被 `com.tcl.system.server` 復活的 App [社群實測] | 中：可能讓該 App 的通知／更新失效 |
| 撤銷麥克風權限 | `pm revoke com.google.android.katniss android.permission.RECORD_AUDIO` | 實測 28% → 0.4% 單核；**本題最划算的一招**（如果不用語音搜尋） | 語音搜尋失效（可 `pm grant` 還原） |
| 停用推薦列 | 停 `com.google.android.tvrecommendations` + TCL 促銷套件 | 首頁變乾淨，少幾個背景服務 | 低（可還原） |
| 關閉自動更新 | Play Store→設定→自動更新應用程式改「不要自動更新」 | 避免「更新後突然變卡」；也避免更新把停用的東西帶回來 | 中：安全性更新停滯（本案可接受） |
| 恢復原廠 | 設定→重設 | 一次性清乾淨；可解「怎麼停都怪」 | **高**：①所有停用全部復活（要重做）②TCL 舊機有實測紀錄：NVM reset 後若沒做「原廠＋USB 重刷」，**Wi-Fi MAC、Widevine keybox、Netflix ESN 會一直是空的**（50DP660，Android 8 舊機）③同機案例：原廠後預載的 GMS/WebView 太舊，會導致無法登入 Google、Netflix -121（本機是 Android 11，風險較低但未驗證） |
| 換 launcher | Projectivy / CleanYourTV launcher | 首頁乾淨、可能少一支行程 | 中：TCL 會搶 HOME（見 §2.4） |

---

## 7. 本案建議的執行順序（等 Owner 放行才做）

1. **先量，不改**：跑 §5.1–5.3 的唯讀指令，記下三個數字（MemAvailable、`df /data`、top 1 的 CPU%）。
2. **先做零風險項**：清快取、`pm trim-caches`、關自動更新、動畫 0.5、（若不用語音）撤麥克風權限。
3. **建本機清單**：`pm list packages` 全列，對照 §4 分類，**只挑 §4.1 的促銷／telemetry**，一批 5–8 個。
4. **每批之後**：重開機 → 確認 Wi-Fi／遙控器／HDMI／Netflix／YouTube／四季 → 再下一批。
5. **最後才考慮換桌面**；換之前先確認 `com.google.android.tvlauncher` 的停用與還原指令都寫在纸上。
6. **全程留紀錄**：每個 `pm disable-user` 都對應一行 `pm enable`，存成純文字檔（CleanYourTV 的 `~/.cleanyourtv` 紀錄做法可直接借用）。

---

## 附錄：本文引用的來源（全部以 webfetch／HTTP 實際讀取）

> 上文提到的檔名（例如 `examples/tcl-43p615/debloat.txt`）對應下表 URL。

**AOSP / Google 官方**

| 主題 | URL |
|---|---|
| dumpsys（meminfo、procstats、gfxinfo 欄位定義） | <https://developer.android.com/tools/dumpsys> |
| Android TV 記憶體指南（LMK、direct reclaim、Launcher 延遲） | <https://developer.android.com/training/tv/playback/memory> |
| lmkd（Android 11 的 PSI 機制、low-RAM 參數） | <https://source.android.com/docs/core/perf/lmkd> |
| 儲存低水位 `getStorageLowBytes()`（5% / 上限 500 MB） | <https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/master/core/java/android/os/storage/StorageManager.java> |
| Google TV 兒童設定檔（僅 Google TV） | <https://support.google.com/families/answer/10491112> |

**UAD-NG**

| 主題 | URL |
|---|---|
| README（定位、detached fork、隱私） | <https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/README.md> |
| 專案狀態（9,624★、GPL-3.0、未 archived、pushed 2026-10-26） | <https://api.github.com/repos/Universal-Debloater-Alliance/universal-android-debloater-next-generation> |
| FAQ（disable vs uninstall、實際指令、原廠會還原、Recommended 警語） | <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/FAQ> |
| Getting started（前置、ADB、ARM 需自編） | <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/Getting-started> |
| Usage（snapshot、只顯示 system 套件） | <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/wiki/Usage> |
| 套件資料庫（5,382 筆，TCL 僅 17 筆且皆為手機） | <https://raw.githubusercontent.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/main/resources/assets/uad_lists.json> |
| #670 uninstall 導致相依 App 亂叫 | <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/670> |
| #887 `tvlauncher` 移除風險（ZTE 會回復原廠設定） | <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/887> |
| #994 **Realtek**：移除 `setupwraith` → `com.realtek.advancesetting` 崩、音量無法調 | <https://github.com/Universal-Debloater-Alliance/universal-android-debloater-next-generation/issues/994> |

**CleanYourTV**

| 主題 | URL |
|---|---|
| README（流程、Home 保護、還原、OTA、支援範圍） | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/README.md> |
| 專案狀態（44,579★、未 archived、pushed 2026-10-29） | <https://api.github.com/repos/Gathin23/cleanyourtv> |
| TCL 77 份清單索引 | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/README.md> |
| 43P615（RTD2851、Android TV 11、AR06）清單與 DO-NOT-DISABLE | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-43p615/debloat.txt> |
| 50J7000A（**TCL-AP-RT2851-S1**、V674、MemAvailable 495→655 MB） | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/examples/tcl-50j7000a/debloat.txt> |
| 還原機制與限制 | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/restore.md> |
| OTA 後套件復活、`check.py --fix` | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/firmware-updates.md> |
| RT2851 遙控器 T 鍵／Netflix 鍵、HDMI passthrough | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-beyondtv-rt2851.md> |
| BeyondTV4 AR05（katniss 28% CPU、`com.tcl.system.server` 開機復活套件） | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-beyondtv4-uk-ar05.md> |
| 50DP660 裝置註記（NVM reset 導致 MAC/Widevine/ESN 遺失、原廠後 GMS 過舊） | <https://raw.githubusercontent.com/Gathin23/cleanyourtv/main/docs/devices/tcl-50dp660.txt> |

**android-tv-debloat-toolkit（「tv-debloat」）**

| 主題 | URL |
|---|---|
| 專案狀態（788★、MIT、未 archived、自述不活躍） | <https://api.github.com/repos/seun-novodev/android-tv-debloat-toolkit> |
| README（Windows-only、Safe/Advanced、FLauncher） | <https://raw.githubusercontent.com/seun-novodev/android-tv-debloat-toolkit/main/README.md> |
| 原始碼（只做 `pm disable-user`；`com.tcl.tv` 標 ⚠️） | <https://raw.githubusercontent.com/seun-novodev/android-tv-debloat-toolkit/main/run_toolkit.py> |

**Projectivy Launcher**

| 主題 | URL |
|---|---|
| Play 商店（版本/更新日/評分/開發者說明） | <https://play.google.com/store/apps/details?id=com.spocky.projengmenu> |
| XDA 開發者原帖（Override stock launcher 機制、freeze 需 root、已知問題） | <https://xdaforums.com/t/app-android-tv-projectivy-launcher.4436549/> |
| #500 TCL 75T7B：HOME 被搶回原 launcher | <https://github.com/spocky/miproja1/issues/500> |
| #415 TCL C855：Android 14 開機不自動進 Projectivy | <https://github.com/spocky/miproja1/issues/415> |
| 其他 issue（#354 待機被終止、#377 R646 無障礙提示；僅讀標題） | <https://github.com/spocky/miproja1/issues> |

**其他實測**

| 主題 | URL |
|---|---|
| Shield Android 11 前後對照（MemAvailable +163.6 MiB、338→334 行程） | <https://raw.githubusercontent.com/mbcrump/shield-projectivy-debloat/main/BENCHMARKS.md> |
| P715 收到 RT51 V715 更新包（同機型佐證） | <https://xdaforums.com/t/tcl-android-tv-model-55p8m-android-recovery-tcl-beyondtv-beyondtv.4025755/page-176> |
| P8M：Android 9（V616）→ V715 後明顯變慢（二手摘要，見「查不到」） | <https://xdaforums.com/t/tcl-android-tv-model-55p8m-android-recovery-tcl-beyondtv-beyondtv.4025755/page-187> |

---

## 待實機驗證清單

1. `pm list packages` 實際清單 vs §4 的清單——**這台到底有沒有 `com.tcl.tvpassthrough`、`com.tcl.dashboard`、`com.tcl.tvweishi`、`com.tcl.bootadservice`**。
2. 這台是不是也用 `com.google.android.tvlauncher`（priority 2）當首頁；`cmd package resolve-activity -c android.intent.category.HOME` 的實際結果。
3. `ro.adb.secure` 與 5555 埠行為：同 client type 的 50J7000A 是「開 USB 偵錯就開 5555」，本機是否相同（決定要不要無線配對）。
4. `com.realtek.advancesetting` 是否存在（決定 `setupwraith` 的危險程度）。
5. `MemAvailable` 基準值：停用前後各量一次，驗證是否也能拿到 +150 MB 等級的改善。
6. `/data` 實際可用空間與分割區大小（換算 5%/500 MB 門檻）。
7. `dumpsys diskstats` 是否存在、p95/p99 數值常態為何（eMMC 老化判斷）。
8. 停用 `com.tcl.overseasappshow` 後，重開機是否真的不再復活其他促銷套件。
9. 停用 `com.google.android.tvrecommendations` 後，首頁與 YouTube/Netflix 的「recommendation row」是否正常消失（不是壞掉）。
10. 撤銷 katniss 麥克風權限後，遙控器語音鍵與 YouTube 語音搜尋的實際行為。
11. 換 Projectivy 後，TCL 韌體是否會把 HOME 搶回去（對照 issue #500 的 75T7B 行為）。
12. 停用原 launcher 後，遙控器上的 Netflix／YouTube／T 鍵／訊號源鍵是否仍正常（本機走 `com.tcl.keycustomfunctionservice`，理論上不受影響，未驗證）。
13. 四季線上（4gTV）在這台上的實際套件名與是否需要 `com.tcl.tv` 的 MIME handler。
14. 一次 TCL OTA 之後，停用清單復活的比例與新增的預載（CleanYourTV 說「一定會復活」，要實測本機）。

## 查不到／不確定

- **沒有 55P715 專屬清單**：CleanYourTV 77 份 TCL 清單裡沒有 P715；UAD-NG 沒有任何電視機型清單；debloat-toolkit 是泛 TCL 固定清單。本文所有套件名都是「同板推定」，不是本機實測。
- **沒有 P715 的 RAM 實測數字**：495→655 MB 是 50J7000A（同 client type、V674、AR04），不是 P715/V715。
- **Projectivy 在 TCL Android TV 11（非 Google TV）的行為沒有直接回報**：現有 TCL 回報集中在較新的機種（75T7B 報 Android TV 12、C855 報 Android 14、R646）。它在 Android TV 11 + `tvlauncher` 上會不會搶 HOME，屬推測。
- **Projectivy 本身的 RAM 足跡沒有可靠數字**：唯一量化是 Shield 上的「整包 profile」（+163.6 MiB），無法分離出 launcher 單獨貢獻。「stock 450 MB → Projectivy 140 MB」這種說法只出現在某個第三方 toolkit 的宣傳文字，**未經證實，我不採用**。
- **eMMC 老化的量化門檻查不到官方或可信社群數據**：`dumpsys diskstats` 的百分位要跟「這台自己的歷史」比才有意義，沒有絕對值。
- **`MemAvailable < 300 MB` 這類門檻是我的推論**，不是 Google 官方數字；官方只描述機制（LMK、direct reclaim），沒有給 consumer 端門檻。
- **disable 後是否影響 Widevine/Netflix ESN**：查不到直接證據；已知的 ESN 遺失案例來自「NVM reset + 未重刷」，不是 debloat。
- **HBO Max 在 Android TV 11 的安裝問題**（需求文件提到「疑似無法安裝」）**不在本題範圍**，未查；那是另一條需要單獨確認的線（Play 商店裝置相容性 vs 側載）。
- **TCL 官方對「停用預載 App」的立場**：查不到官方支援頁；只有社群專案引用 TCL 韌體行為。保固風險請以 TCL 台灣官方說法為準（本文無法提供）。
- XDA 上「V715 升級後變慢」的原文，我是透過搜尋結果頁讀到摘要（頁面本身被 Cloudflare 擋，改用 r.jina.ai 只取到部分內容），**引述時請視為二手摘要**：<https://xdaforums.com/t/tcl-android-tv-model-55p8m-android-recovery-tcl-beyondtv-beyondtv.4025755/page-187>
