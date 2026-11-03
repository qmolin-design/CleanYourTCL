# R8：中國網路對 TCL「TGuard / com.tcl.guard」的公開資料蒐集

- 蒐集時間：2026-10-30（Asia/Taipei）
- 目標：TCL Android TV 內建 `com.tcl.guard`（TGuard，系統 uid）、其砍後台行為、常駐/白名單做法、停用/卸載後果、`custom_config.xml` 逆向資料
- 方法：curl（瀏覽器 UA、`-L`、25s timeout）抓搜尋結果頁與原文；共 20+ 組關鍵字、5 個可用引擎
- 重要前提：本次找到的一手技術資料幾乎都來自 **GitHub 專案文件**（英文/中文），不是「中國搜尋引擎上的文章」。搜尋引擎本身只負責帶路。

---

## 0. 先講結論（白話）

1. **`com.tcl.guard` 在公開資料裡被確認是「TCL 安全衛士 / TGuard」，系統 uid，路徑 `/system_ext/app/TGuard`（新機型）**，並且是「開機／喚醒時執行廠商禁用清單」的執行者。這點有兩個獨立 GitHub 專案互相印證。
2. **沒有任何中文文章直接把 `tguard-kill`、`tguard-kill-SystemApp`、`persist.sys.freeze`、`sys_config.xml`、`autoselfstart_config.xml`、`custom_config.xml` 解密寫出來。** 這些字串在中國搜尋引擎上的命中數為 0，屬「社群沒有公開挖到這一層」。
3. **讓第三方 App 常駐的正解不是「關掉記憶體清理」，而是 TCL 自己的一套開機自啟審查**：`TclAppBootManagerImpl` + 兩個自訂 AppOps（`APP_AUTO_START` / `APP_ASSOC_START`）。沒放行時 log 會出現 `forbid bind service`。
4. **`pm uninstall --user 0 com.tcl.guard` 在新機型上是不可行的**：預裝 App 在唯讀分區，廠商卸載白名單為空，指令必定失敗；能做的上限是「禁用」。而對 TCL system uid 的 App 亂下 `pm disable/pm enable` 有整機變磚的實測紀錄。
5. 使用者情境（2GB RAM、TGuard 1.01.00_f761a12_220511、`/product/app/TGuard`）比這次找到的資料**更舊**（資料多在 MT9655/MT5879、Android 11–14、`/system_ext/app/TGuard`）。下面的機制大方向可參考，但路徑與 op 名稱要自己在機器上驗。

---

## 1. 直接提到 tguard / com.tcl.guard / TCL 卫士

### 1.1 `xnklerEd/ffalcontrol`（英文 README）— 最直接的 TGuard 行為描述
- 網址：https://github.com/xnklerEd/ffalcontrol
- 日期：仓库 v0.1.4（README 擷取於 2026-10-30，無標註發布日）
- 機型：TCL / 雷鳥（FFALCON）Android TV，主要驗證機為 MT9655 系列
- 關鍵內容（原文重點翻譯）：
  - 「TCL 把大量系統行為放在 `com.tcl.providers.config` provider 裡。**廠商自己的 `com.tcl.guard`（system uid）在開機時與待機喚醒時，從那裡讀取禁用清單**。」
  - `POST /guard` 寫入 `Guard_disable_App`（package 級與 component 級禁用）；`POST /provision` 一鍵同時寫 Guard + InstallConfig + AppBootPolicy。
  - 對照表：`pm disable-user` 是「立即生效、`enabled=3`」；`Guard_disable_App` 是「由 `com.tcl.guard` 在開機時套用、`enabled=2`」，**兩者畫面結果相同**。
  - 一鍵恢復會寫入 **22 個 package + 41 個 component**，並追加 **44 條 `AppBootPolicy`** 條目。
  - 限制（重要）：
    - 「component 級禁用有一個 top-skip：**若 `com.tcl.cyberui` 在前台，guard 會跳過整個 component 清單**（避免把正在跑的桌面禁用掉變黑屏）。實務上 component 禁用開機時不會執行。」
    - 「背景 service（例如 `LauncherUpdateService`）兩套機制都蓋不到：`AppBootPolicy` 沒有 service 黑名單，guard 也跳過。**目前無法禁用**。」
    - 「**預裝 App 無法真正移除**：它們在唯讀分區，且廠商卸載白名單為空，所以 `pm uninstall --user 0` 會失敗。**禁用是上限**。」
    - 「`PackageManagerService` 拒絕 shell 改 component 狀態（`SecurityException: Shell cannot change component state`）」、「`DevicePolicyManager` 出廠即停用（`mHasFeature=false`），沒有 device owner」。
- 效果/副作用：不改系統分區、不 hook、不刷機；代價是需重開機讓 guard 套用，且 component 級幾乎無效。

### 1.2 `mmaotv/ffalcontrol-tcl`（中文 README，上游的衍生版）— 有 `pm path` 自檢與機型清單
- 網址：https://github.com/mmaotv/ffalcontrol-tcl
- 日期：v0.3.0（README 擷取於 2026-10-30）
- 機型/固件（實測）：`TCL Smart TV Pro · MT9655_4K_CN · Android 14`，固件 `TCL/MT9655_4K_CN/MT9655:14/UTT2.250416.001/AU01:user/release-keys`
- 關鍵內容：
  - 系統組件位置：**`com.tcl.providers.config`（`/system_ext/priv-app/ConfigProvider`）、`com.tcl.guard`（`/system_ext/app/TGuard`）**，兩者同時存在機制才有效。
  - 不用裝工具的判断法：`getprop ro.board.platform` 是 `MT9655`/`MT9653`，且 **`pm path com.tcl.guard` 有輸出**。
  - 「**`com.tcl.guard` 會在開機時執行包級禁用**，`AppBootPolicy.activityBlackList` 會攔 activity 啟動，所以裝完必須復查。」
  - 「**斷電冷啟動**（拔插頭 30 秒再上電）。包級禁用由 `com.tcl.guard` 在開機廣播時執行，不重啟不生效。」
  - 配置行 → 消費者對照：`Guard_disable_App` → `com.tcl.guard`（開機/喚醒執行）；`AppBootPolicy.activityBlackList` → TCL 框架（activity 啟動前攔）；`InstallConfig.strategies[...]` → `PackageInstaller_tv`。
  - 「不可用」清單：非 MT96 平台（Amlogic/海思舊機）、**海外版 Google TV 固件（沒有 ConfigProvider / guard）**。
- 副作用：直接寫系統配置，「寫錯會讓你點不開應用」；復原靠 `/backups` 寫回或恢復出廠；OTA 可能收緊策略。
- 與本案的關聯：這證明 **TGuard 是「開機時套用禁用」的執行端，不是單純的記憶體清理器**；它同時是「砍第三方 App」與「開機禁用」兩條線的交會點。

### 1.3 `pengchujin/aerial-tv` 機型筆記 — `com.tcl.guard` 是 installerPackageName
- 檔案：https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt5879-cn.md
- 日期：內文實測紀錄到 2026-10-24 / 2026-10-22
- 機型：TCL MT5879 國行、Android 11
- 關鍵原文：
  - 「TCL 的 `com.tcl.guard`（安全衛士）是**大部分 App 的 installerPackageName**，其中的 `PackageInstallerService` 是 Binder 接口，shell 用不了。」
  - 「安全衛士 `com.tcl.guard` **能裝 U 盤裡的 APK**（戶主確認過），沒有找到能傳本地路徑的入口。」
  - 「`persist.tcl.installapk.enable=1` 和 `package_verifier_enable=0` 這些屬性對上面的校驗鏈都沒有影響。」（＝網路上流傳的 ADB 解鎖屬性對新機型無效）
- 效果/副作用：想靠「用 TGuard 自己當 installer」繞過安裝限制，需要人能站在電視前用遙控器操作，無法自動化。

### 1.4 `pengchujin/aerial-tv` 機型筆記 — 不要對 TCL 系統 App 下 `pm disable`
- 檔案：https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-m7221-cn.md
- 日期：內文實測到 2026-10 月底
- 機型：TCL 75V2（m7221，Android 8）
- 關鍵原文：
  - 「⚠ **別去 `pm disable` / `pm enable` TCL 的系統應用。** 實測誤操作 `pm enable com.tcl.walleve` 之後 system_server 變殭屍、zygote64 進 D 狀態，整機崩，只能 `setprop sys.powerctl reboot` 救回。」
  - 權限邊界表：「`adb install`（streamed）可以做；`pm disable` / `pm enable` → `SecurityException`；**動 TCL 的 system uid 應用**列在『不能做』。」
  - 「TCL 有兩個自己的無障礙服務（`com.tcl.walleve`，『小T助手』），改 `enabled_accessibility_services` 時要保留。」
- 對本案的直接意義：**`com.tcl.guard` 正是 system uid App，屬於這條警戒線內**；文件沒有示範 `pm disable-user com.tcl.guard` 成功，反而記載 shell 改組件狀態會被拒。

### 1.5 TCL「電視管家」＝第三方 App 自啟的守門人（不是 TGuard 本體，但同一條控制鏈）
- 來源：https://github.com/pengchujin/aerial-tv （README「支持的機型」表）
- 日期：README 擷取於 2026-10-30
- 機型：TCL Q9L Pro（Android 14）
- 原文：「⚠️ 只能 USB 線連 adb；**要在電視管家裡允許 Aerial 自啟**；信號源切了沒畫面（[#792](https://github.com/pengchujin/aerial-tv/issues/792)）」
- 效果：這是公開資料裡最明確的「**加白名單＝在電視管家裡打開該 App 的自啟動**」敘述。

### 1.6 ZNDS 教學：TCL「電視管家」裡的自啟動／直接進入應用（2022）
- 網址：https://www.znds.com/tv-1219253-1-1.html
- 日期：2022-06-20
- 機型：作者未標型號（TCL Android TV，需 ADB）
- 具體做法：
  1. 設定 → 系統 → 系統資訊 → 遥控器依序按「上下左右」叫出 ADB 開關。
  2. `adb connect <電視IP>`。
  3. 用 `package view` APK 列出所有包名，紅色＝預裝、綠色＝系統廣告、黃色＝系統桌面。
  4. `adb install Emoth UI.apk`，然後「**進入原始桌面，找到電視管家將『自啟動』和『直接進入應用』都設置為 Emoth UI**」。
  5. 凍結原始桌面後即成為默認桌面。
- 效果/副作用（同一頁留言，重要）：
  - `Failure [INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES: disable install launcher app not signature by tcl]`（第三方桌面被簽名校驗擋）
  - `遙控器卸載 顯示卸載失敗`
  - 有人吐槽「cmd 操作界面對應的是康佳電視的 app」，即此類教學常互相抄襲、機型不一定對得上。
- 註：這裡的「電視管家」是 TCL 桌面內的自啟管理 UI；`com.tcl.guard` 是背後的系統服務。兩者是否同一支 App，公開資料沒有明講，需在本機用 `pm list packages | grep -i guard` 對照。

### 1.7 有人刪掉「電視衛士」之後的實際後果（唯一一筆使用者案例）
- 網址：https://www.znds.com/tv-1189064-1-1.html
- 日期：2021-01-23（提問）
- 機型：未標（TCL 电视）
- 原文：「之前內存不夠把電視衛士刪掉了，今天下載就打不開了怎麼回事呢，出來一串字母然後未安裝什麼的」
- 回覆：只能「恢復出廠」或「找刷機包自己提取一個」。
- 效果/副作用：這是「刪掉衛士 → 想裝回來裝不上」的間接證據（衛士是系統簽名 App，重新 sideload 會簽名校驗失敗）。

---

## 2. 讓第三方 App 常駐／不被殺（可操作做法）

### 2.1 正解：TCL 開機自啟審查 + 兩個自訂 AppOps（有一手機型實測）
- 來源：https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt5879-cn.md
- 日期：實測 2026-10-20
- 機型：TCL MT5879 國行、Android 11
- 機制原文：
  - 「冷啟動後，第三方 App 的服務綁定、廣播接收和進程啟動，都要經過 **`TclAppBootManagerImpl`**（在 `/apex/com.tcl.tcore/javalib/tcl-service.jar` 裡，由 `ActiveServices` 呼叫 `getTclAppBootManagerInjector().canBindServiceGo`）。**默認會拒絕**，日誌裡是 `ActivityManager: forbid bind service app.orchard.tv/...HomeGuardService`，所以開機後停在 TCL 桌面。」
  - 「放行條件是兩個 TCL 自訂的 AppOps：**`APP_AUTO_START`（op 100，系統拉起）和 `APP_ASSOC_START`（op 101，其他 App 關聯拉起）**。默認分別是 `ignore` 和 `default`。」
- 指令（把 `<pkg>` 換成你的 App 包名）：
  ```bash
  adb shell appops set <pkg> APP_AUTO_START allow
  adb shell appops set <pkg> APP_ASSOC_START allow
  # 舊/不同機型上的 op 名稱可能是 AUTO_START / ASSOC_START
  ```
- 效果：「2026-10-20 實測：放行後 `adb reboot`，開機約 0.5 秒通知監聽就被綁定，Aerial 被拉起。」
- 副作用/限制：
  - 另一個機型（雷鳥 75S78A Plus / FFALCON T966D5，Android 14）上，op 叫 `AUTO_START` / `ASSOC_START`，且「電視『權限管理 → 應用自動啟動』或 `appops set` 能打開，但**每次開機前被固件改回 `ignore`**」→ 只管到下次重啟。（來源：https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/ffalcon-t966d5.md）
  - 對「已經在跑、被 TGuard 中途砍掉」的情境，AppOps 只保證「綁得上」，不保證「不被殺」；Aerial 的解法是加無障礙守護 + 通知監聽復活（見 2.4）。

### 2.2 開機直接拉指定 Activity（比守護乾淨，但有副作用）
- 來源：同上 tcl-m7221-cn.md（TCL 75V2，Android 8）
- 指令：
  ```bash
  adb shell setprop persist.sys.launcher.component <pkg>/<pkg>.MainActivity
  adb shell cmd package set-home-activity --user 0 <pkg>/<pkg>.MainActivity
  adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME
  ```
- 效果：真重啟後 48 次採樣 0.5s/次，前台一直是目標 App，`com.tcl.cyberui` 全程沒出現。
- 副作用（原文警告）：「**設了這個屬性，從 Aerial 打開別的 App 會崩**（#590，75V2 和 65C6 都復現）：固件在啟動 Activity 時把被啟動應用的包名換成屬性裡的包名，第三方 App 在 `Activity.attach` 裡拋 `SecurityException: Package ... does not belong to <uid>`」。清空：`setprop persist.sys.launcher.component ""`。
- 另註：「它不校驗組件有沒有聲明 HOME，給任何 Activity 都能用」；「固件屬性只管『開機拉誰』，不管『Android 認為誰是默認桌面』——兩者兩套機制，必須都對齊」。

### 2.3 UI 路徑（免指令）
- 「電視管家 → 自啟動 / 直接進入應用」設成你要的 App（ZNDS 2022-06-20，見 1.6）。
- 「權限管理 → 應用自動啟動」（雷鳥 T966D5，見 2.1 副作用）。
- 第三方工具：當貝市場「管理 → 自啟動項管理」、當貝助手「自啟動管理」（https://www.znds.com/tv-1016131-1-1.html，2017-11-30，TCL 智能電視）。僅為 2017 年資料，新固件是否仍有效未驗證。

### 2.4 被殺後「復活」：無障礙 + 通知監聽（Aerial 的做法）
- 來源：https://github.com/pengchujin/aerial-tv （README「它怎麼接管桌面」）
- 日期：README 擷取 2026-10-30
- 原文：「國產電視大多只認自家桌面，**待機時還會清理第三方 App**。所以 Aerial 用一個無障礙服務，原桌面一出現就切回來（只看哪個窗口在前台，不讀螢幕內容）；**被清理後再靠通知監聽讓系統把它拉起來**（不讀通知）。」
- 指令（安裝時用）：
  ```bash
  adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS
  adb shell cmd notification allow_listener <pkg>/<pkg>.guard.RevivalListener
  adb shell appops set <pkg> SYSTEM_ALERT_WINDOW allow
  adb shell cmd package compile -m speed -f <pkg>
  ```
- 副作用：雷鳥 T966D5 上「固件大約每 52 秒把 `enabled_accessibility_services` 清成 `null`」，shell 寫也一樣 → 此路不通（見 2.1 同檔）。

### 2.5 舊一代「假解鎖」屬性（新機型已失效，僅供對照）
- ZNDS 教學（2022-02-28，未標機型）：https://www.znds.com/forum.php?mod=viewthread&tid=1212290
  ```bash
  adb shell setprop persist.tcl.installapk.enable 1
  adb shell setprop persist.tcl.debug.installapk 1
  pm uninstall --user 0 com.tcl.tshop   # 範例：移除 T商店
  ```
- 反證：aerial-tv 在 MT5879 上實測「`persist.tcl.installapk.enable=1` 和 `package_verifier_enable=0` 這些屬性對上面的校驗鏈都沒有影響」；但 55V2 Pro（x32a0）則「先 `setprop persist.tcl.debug.installapk 1` 才能 adb 安裝」（README 機型表）。→ **同一個屬性在不同世代效果不一致，必須逐機驗證。**

---

## 3. 停用／卸載 TCL 卫士：能做什麼、會有什麼後果

| 做法 | 公開資料怎麼說 | 風險 |
|---|---|---|
| `pm uninstall --user 0 com.tcl.guard` | **必定失敗**：「預裝應用在唯讀分區，廠商卸載白名單為空，`pm uninstall --user 0` 必然失敗，**能禁用不能真刪**」（https://github.com/xnklerEd/ffalcontrol ；中文版 https://github.com/mmaotv/ffalcontrol-tcl ） | 無效果，浪費時間；反覆嘗試無意義 |
| `pm disable-user com.tcl.guard` | 文件只示範對「非 TCL 自家 App」用 `pm disable-user`；對 TCL system uid App 明列「不能做」，且 shell 改組件狀態會被 PMS 拒絕（`SecurityException: Shell cannot change component state`）（https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-m7221-cn.md 、 https://github.com/xnklerEd/ffalcontrol ） | 實測誤操作 `pm enable com.tcl.walleve` → system_server 殭屍、zygote64 D 狀態、整機崩，只能 `setprop sys.powerctl reboot` |
| `pm hide` / `pm block`（冷凍） | 舊教學（2018-01-05，雷鳥 I55-UI，Android 6）：「安卓 6.0 使用 `pm hide <pkg>`；安卓 4.0 使用 `pm block <pkg>`；解凍 `pm unhide` / `pm unblock`」（https://www.znds.com/tv-1021116-1-1.html） | 2018 年做法，Android 9+ 上 `pm hide` 多數已失效；且 hide 的是同一個 system uid 元件，風險同上 |
| 透過 `Guard_disable_App` 曲線禁用 | 廠商自己的通道：寫 `Guard_disable_App`，由 `com.tcl.guard` 開機套用（`enabled=2`），效果等價 `pm disable-user`（`enabled=3`）。需 root/系統權限或 ffalcontrol 這類工具代寫 | **這是「用 TGuard 停用別的 App」的通道，不是「停用 TGuard 自己」**；反過來停 TGuard 沒有公開做法 |
| 直接刪 APK / 刪 `/product/app/TGuard` | 沒有人成功紀錄；ZNDS 只有「刪了電視衛士 → 重裝裝不上 → 只能恢復出廠」（見 1.7） | 系統簽名 App 重裝會踩簽名校驗；且 TGuard 是 `installerPackageName`，刪了可能連「安裝 App」這條路都斷 |

**社群實務共識（隱含）**：中國論壇流傳的「安全凍結清單」普遍**不含** `com.tcl.guard`。
- 例：ZNDS「TCL电视AT系列晶晨芯片可安全冻结软件包名」（2020-11-07，https://www.znds.com/tv-1184106-1-1.html ）列了 23 個可安全凍結的包（`com.tcl.smarttvhelp`、`com.tcl.bootadservice`、`com.tcl.cyberui`（警告勿禁）、`com.tcl.appmarket2`…），**沒有 `com.tcl.guard`**。
- 另一份 52pojie 精簡清單（2020-04-07，https://www.52pojie.cn/thread-1150601-1-1.html ，TCL A962T23 機芯）同樣只動 `com.tcl.bootadservice`、`com.tcl.cyberui`、`com.tencent.qqmusictv` 等 16 個包，**沒有 `com.tcl.guard`**。
- 解讀：不是「不能停」，而是「停過的人沒把結果寫出來」或「停了之後踩雷所以沒後續」。**目前沒有可信的『停用 TGuard 且系統正常』公開案例。**

---

## 4. `custom_config.xml` 解密 / TGuard 逆向 / 雲端白名單下發

**結論：找不到。**

- 没有任何文章提到 `custom_config.xml`、`sys_config.xml` 的 `<sysrubish>`、`autoselfstart_config.xml`、`IAMSTGuardInjector`、`INT_TGUARD_ENABLE`、`KILL_TGUARD`、`sys.tguard.restart`、`persist.sys.freeze`。
- 最接近「TGuard 逆向」的公開成果，是**沒有拆 TGuard 本體、而是拆 `PackageInstaller_tv` + 讀寫 `ConfigProvider`** 的兩個 GitHub 專案：
  - `xnklerEd/ffalcontrol`：透過 `com.tcl.providers.config` 讀寫 `Guard_disable_App` / `AppBootPolicy` / `InstallConfig` / `bootStagedStartupConfig`，附 `/guard`、`/config-write`、`/backups` 端點，號稱「不 hook、不刷機、不改系統分區」。
  - `mmaotv/ffalcontrol-tcl`：把 `PackageInstaller_tv.apk` 反編譯出六條安裝策略（`launcherStrategy`、`blackListStrategy`、`overDueStrategy`、`thridStrategy`、`pmStrategy`、`safeStrategy`），並附 `AxmlReader`（自寫二進位 manifest 解析）。
- 也就是說：**社群拆的是「TCL 的安裝校驗鏈」與「配置 provider」，不是 TGuard APK 本身。** 加密的 `custom_config.xml`、雲端白名單下發機制，中文社群沒有任何公開分析。
- 唯一可用的「配置讀取」指令（一手，MT9655 / Android 14）：
  ```bash
  adb shell content query --uri content://com.tcl.providers.config/all | grep InstallConfig
  ```
  - 註記：「`launcherStrategy` 開著、名單為空時，帶 HOME 的包會被拒，只能裝 `guard` 版。**這一行 shell 改不了（`content update` 返回 0，不生效）**。」（https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt9655-cn.md ）

---

## 5. TCL 其他後台限制機制（FreezeManager / 凍結 / 自啟動）

| 機制 | 公開資料 | 出處 / 日期 |
|---|---|---|
| `TclAppBootManagerImpl` + `canBindServiceGo` | 開機時對第三方 App 的 service 綁定、廣播、進程啟動做白名單審查；預設拒絕，log `forbid bind service` | https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt5879-cn.md （2026-10-20 實測） |
| AppOps `APP_AUTO_START`(100) / `APP_ASSOC_START`(101)；雷鳥叫 `AUTO_START` / `ASSOC_START` | 放行條件；部分機型每次開機被固件改回 `ignore` | 同上；https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/ffalcon-t966d5.md |
| 電視管家「自啟動／直接進入應用」 | UI 白名單入口 | https://www.znds.com/tv-1219253-1-1.html （2022-06-20）；aerial-tv README Q9L Pro |
| 待機時清理第三方 App | 「國產電視大多只認自家桌面，**待機時還會清理第三方 App**」 | https://github.com/pengchujin/aerial-tv README |
| 「應用凍結」開關（使用者可見） | 只在一則 bug 討論被提到「關掉應用凍結也一樣（#371）」，**沒有獨立教學說明這個開關在哪、對什麼生效** | https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt5879-cn.md |
| `pm hide` / `pm block` 冷凍法 | Android 4.0 用 `pm block`、6.0 用 `pm hide`，解凍 `pm unhide`/`pm unblock` | https://www.znds.com/tv-1021116-1-1.html （2018-01-05，雷鳥 I55-UI） |
| 應用黑名單檔（安裝/回桌面攔截） | `/system/etc/*black_list.json`、`appinfo/blacklist_*.xml`、`str_someapp_need_goback_home.xml`、`disable_beforebootcomplete_packages.xml`；需 root 才能刪 | https://www.znds.com/forum.php?mod=viewthread&tid=1165179 （2020-02-06，TCL MS828C 機芯） |
| `boot_app_mode` / `boot_app_componentname` | TCL「開機進入」設定，由 `com.tcl.ttvs` 的 MMMService 管理；MT5879 實測「**試過但沒用**」 | https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt5879-cn.md |
| `persist.sys.launcher.component` | 開機直接拉指定 Activity，有效但有「開其他 App 會崩」副作用 | https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-m7221-cn.md |

**沒找到的**：`FreezeManager`、`persist.sys.freeze` 沒有任何中文頁面。最接近的是「應用凍結」這個 UI 名詞與 `pm hide` 冷凍法，兩者是否同一支程式**無法確認**。

---

## 6. 依使用者情境（2GB RAM、`/product/app/TGuard`、版本 1.01.00、2022 年 5 月）的判讀

1. **世代差異**：這次找到的一手機型是 MT9655 / MT5879（Android 11–14，TGuard 在 `/system_ext/app/TGuard`）。使用者的機器是 `/product/app/TGuard`、2022-05 build，屬較舊分區佈局。`/product` 而非 `/system_ext` 意味著 **Android 9/10 世代的 TCL 固件**，`com.tcl.providers.config` 是否存在、`Guard_disable_App` 是否存在，都需本機 `pm path com.tcl.providers.config` / `content query` 驗證。
2. **「記憶體不缺也砍」與公開資料吻合**：TGuard 的觸發條件不是「低記憶體」，而是**開機/喚醒時套用廠商禁用清單**（`Guard_disable_App`）+ 待機清理（aerial README）。這解釋了為什麼 `logcat` 會看到 `tguard-kill` 而 `meminfo` 還有剩餘 RAM。
3. **優先順序建議（依證據強度）**：
   - ① 先做 `appops set <pkg> APP_AUTO_START allow` + `APP_ASSOC_START allow`（有實測、可逆、不碰系統 App）。
   - ② 再試 UI 的「電視管家 → 自啟動」白名單。
   - ③ 需要「被砍後自動回來」再加無障礙 + 通知監聽復活（Aerial 路線）。
   - ④ **不要**先試 `pm disable-user com.tcl.guard`：沒有任何成功案例，卻有「動 TCL system uid App → 整機崩」的同類實測。
4. **若真要走「停用衛士」**：目前唯一有紀錄的「安全」路徑是「能禁用不能真刪」＋「用廠商自己的 `Guard_disable_App` 通道」，而不是對 `com.tcl.guard` 本身下手。要停 TGuard 自己，等於要關掉「開機禁用清單的執行者」，副作用是**所有靠它套用的禁用都會失效**（等於把 ffalcontrol 那類工具的成果全部還原），且 system uid App 被停用後 TCL 框架的行為沒有任何人記錄過。

---

## 7. 找不到的部分（明確聲明）

以下關鍵字／主題，在本次可用的中國搜尋引擎與 CSDN API 上**命中數為 0 或全為無關結果**，沒有可引用文章：

- `tguard-kill`、`tguard-kill-SystemApp`（0 命中，只有 DNF 遊戲的 TGuard 進程清洗 .bat）
- `TGuardService`、`com.tcl.guard.memorymanager`、`IAMSTGuardInjector`（0 命中）
- `INT_TGUARD_ENABLE`、`KILL_TGUARD`、`sys.tguard.restart`（0 命中）
- `persist.sys.freeze` / `FreezeManager`（0 命中；只有 `persist.sys.*` 的一般 Android 屬性文章）
- `custom_config.xml` 解密、`<sysrubish>` 白名單、`autoselfstart_config.xml`（0 命中）
- 「TGuard 逆向」：只有 `PackageInstaller_tv` 與 `ConfigProvider` 的逆向，**沒有 TGuard APK 本體的反編譯分析**
- 酷安（coolapk）：站內搜尋需登入，`site:coolapk.com` 在 360 上 0 命中
- 知乎：直接抓 403；僅能從搜尋摘要看到一題「有没有不杀后台进程或可以让App一直在后台运行的电视?」（https://www.zhihu.com/question/412040570 ，2020-10-12），**摘要明說「想在家里的TCL智能电视上运行一个App，但总是运行一段时间之后就被系统杀掉」，但正文無法取得**
- 吾愛破解／看雪：站內搜尋需登入，`site:` 查詢被引擎擋；唯一可用的是 52pojie 的精簡清單（見 3.）

---

## 8. 試過的查詢與被擋的引擎（可重現）

### 8.1 引擎可用性

| 引擎 | 端點 | 結果 |
|---|---|---|
| 360 搜索（PC） | `https://www.so.com/s?q=` | ✅ 前 14 組可用；之後轉 `qcaptcha.so.com`（訪問異常頁） |
| 360 搜索（移動） | `https://m.so.com/s?q=` | ✅ 可用（PC 被擋後仍可用），但結果品質差、摻大量軟體下載站 |
| 神馬搜索 | `https://m.sm.cn/s?q=` | ✅ HTTP 200，但結果極少（`"com.tcl.guard"` 只回 3 條無關） |
| 夸克 | `https://quark.sm.cn/s?q=` | ✅ HTTP 200（未深入解析） |
| 頭條搜索 | `https://so.toutiao.com/search?keyword=` | ✅ HTTP 200 / 1.7MB，但標題 JSON 雙重編碼、雜訊多；曾帶出 CSDN 的 Aerial/TCL 系列文 |
| CSDN 站內 API | `https://so.csdn.net/api/v3/search?q=&t=all&p=1` | ✅ 可用（JSON），但 2026 年後大量 AI 生成文，品質低 |
| Bilibili API | `https://api.bilibili.com/x/web-interface/search/all/v2?keyword=` | ✅ 可用（未找到 TGuard 相關影片） |
| 百度 | `https://www.baidu.com/s?wd=` / `m.baidu.com` | ❌ 302 → `wappass.baidu.com/.../captcha/tuxing_v2.html`（圖形驗證碼） |
| 搜狗 | `https://www.sogou.com/web?query=` | ❌ 第一次成功，之後轉 `antispider`（`static/js/antispider.min.js`） |
| 必應中國 | `https://cn.bing.com/search?q=` | ❌ 可用但**結果被污染**：查 `com.tcl.guard` 回「Ages of Conflict / SteamDB」完全無關結果 |
| 知乎 | `https://www.zhihu.com/search?q=` | ❌ 403 |
| 百度貼吧 | `https://tieba.baidu.com/f/search/res?qw=` | ❌ 403 |
| grep.app（GitHub code search 替代） | `https://grep.app/api/search?q=` | ❌ Vercel Security Checkpoint |
| GitHub code search | `https://github.com/search?type=code` | ❌ 需登入（HTML 有 login wall，抓不到結果） |
| DuckDuckGo | `https://html.duckduckgo.com/html/`、`lite.` | ❌ HTTP 202 anomaly 頁 |

### 8.2 實際下過的查詢（至少 12 組，全列出）

360 搜索（PC，成功）：
1. `com.tcl.guard`
2. `tguard-kill`
3. `TCL卫士 卸载`
4. `TCL电视 后台 被杀`
5. `TCL电视 应用 自动关闭 后台`
6. `TCL电视 内存清理 关闭`
7. `TCL 电视 adb 禁用 系统应用`
8. `TCL电视 自启动 白名单`
9. `TGuardService`
10. `TCL电视 保活`
11. `TCL电视 冻结 persist.sys.freeze`
12. `TCL 精简 adb 列表`
13. `TCL电视 电视卫士 删除`
14. `TCL Android TV 后台应用 清理`
15. `"TGuard" TCL 电视`
16. `site:github.com tcl guard tv`（0）
17. `site:github.com "com.tcl.guard"`（0）
18. `site:coolapk.com TCL 后台`（0）
19. `site:zhihu.com TCL电视 后台 应用 被杀`（7 條，只有 412040570 相關）

360 移動（成功）：
20. `TCL卫士 停用`
21. `TCL电视 应用冻结` → 帶出 znds tv-1184106 / tv-1021116
22. `TCL电视 自启动 权限管理` → 帶出 znds tv-1016131 / mydigit 140633

CSDN API（成功）：
23. `com.tcl.guard`
24. `tguard`
25. `TCL 卫士`
26. `TCL电视 后台 被杀`
27. `TCL电视 保活`
28. `TCL 精简 系统应用 adb`
29. `TCL雷鸟电视ADB调试指南 卸载预装应用`

神馬／頭條：
30. `"com.tcl.guard"`（sm.cn）
31. `TCL电视 应用 被杀 后台 保活`（toutiao）
32. `TCL 卫士 com.tcl.guard 停用`（toutiao）

GitHub API（成功，用來找專案而非 code）：
33. `https://api.github.com/search/repositories?q=tguard`（75 個，全是 Telegram/DNF 的 TGuard，**無 TCL 相關**）
34. `https://api.github.com/search/repositories?q=aerial-tv+android+tv`（找到 `pengchujin/aerial-tv`、`mmaotv/ffalcontrol-tcl`）

### 8.3 實際抓取並閱讀的原文（非搜尋頁）

- https://github.com/xnklerEd/ffalcontrol （README，v0.1.4）
- https://github.com/mmaotv/ffalcontrol-tcl （README，v0.3.0）
- https://github.com/pengchujin/aerial-tv （README）
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt5879-cn.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-mt9655-cn.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/tcl-m7221-cn.md
- https://github.com/pengchujin/aerial-tv/blob/main/docs/devices/ffalcon-t966d5.md
- https://www.znds.com/tv-1170800-1-1.html （TCL 免 ROOT 用 ADB 隱藏預裝 App，2020-03-25）
- https://www.znds.com/tv-1219253-1-1.html （ADB 卸載預裝 + 改默認桌面，2022-06-20）
- https://www.znds.com/forum.php?mod=viewthread&tid=1212290 （開 ADB + 解除安裝限制，2022-02-28）
- https://www.znds.com/forum.php?mod=viewthread&tid=1165179 （去除應用黑名單，2020-02-06）
- https://www.znds.com/tv-1016131-1-1.html （自啟動項管理，2017-11-30）
- https://www.znds.com/tv-1184106-1-1.html （AT 系列安全凍結清單，2020-11-07）
- https://www.znds.com/tv-1021116-1-1.html （ADB 冷凍預裝，2018-01-05）
- https://www.znds.com/tv-1189064-1-1.html （電視衛士刪掉怎麼辦，2021-01-23）
- https://www.52pojie.cn/thread-1150601-1-1.html （免 ROOT 精簡，2020-04-07）

---

## 9. 證據等級標註（避免誤用）

| 敘述 | 等級 | 理由 |
|---|---|---|
| `com.tcl.guard` 是 system uid 的 TCL 安全衛士，開機/喚醒時套用 `Guard_disable_App` | **A（強）** | 兩個獨立 GitHub 專案（英文上游＋中文衍生）一致，且給出 `/system_ext/app/TGuard` 路徑與 `pm path` 自檢法 |
| `pm uninstall --user 0 com.tcl.guard` 必失敗 | **A（強）** | 同上，並解釋機制（唯讀分區＋卸載白名單為空） |
| `pm disable-user com.tcl.guard` 會怎樣 | **C（推論）** | 沒有任何成功/失敗的直接紀錄；只有「別動 TCL system uid App」的同類事故（`com.tcl.walleve`） |
| `appops set APP_AUTO_START allow` 可讓第三方 App 開機被拉起 | **B（中）** | MT5879 有一次完整實測（含 log 與重啟後 0.5s 綁定），但只在單一機型、單一 App；雷鳥機型會被固件改回 |
| 「電視管家」＝TGuard 的 UI | **D（未證）** | 僅有「電視管家裡允許自啟」與「com.tcl.guard 是安全衛士」兩條間接線索，**沒有資料證明是同一個 App** |
| `tguard-kill` log tag、`custom_config.xml` 加密、雲端白名單下發 | **E（無資料）** | 全部 0 命中 |

---

REPORT_DONE
