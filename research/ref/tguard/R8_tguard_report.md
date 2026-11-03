# TCL TGuard 唯讀逆向分析報告

分析日期：2026-10-30。範圍為所提供的 APK、assets、framework JAR 及反編譯輸出；未連電視、未執行 adb、未連網。以下路徑均相對於本報告所在的 `scratchpad/tguard/`。程式碼行號是 JADX 輸出行號；`src/tclfw/` 是既有 TCL 服務程式輸出，`src/tcl-framework/` 是本次另行反編譯的 framework。既有 `src/services-jadx.log` 與 `src/tguard-jadx.log` 分別報 18、24 個錯誤，且核心 `KillHandler` 有重複區塊警告：個別分支不能當作原始碼完全還原。

## 1. 白話總結

TGuard 確實有不只一份不砍名單，自動清理、一鍵清理和另一套掃描清理所用名單不同。發現一條值得優先上機測試的非 root 方法：透過 `dumpsys activity service` 對 TGuard 服務傳參數，程式會以反射更改執行中的自動清理白名單；可以把 tvagent 加入，但必須保留原有名單，而且它只在記憶體內，更新設定或服務重啟可能覆蓋。這是程式碼支持的推論，尚未實機證實 shell 是否能在該機型成功執行。它不保證一鍵清理、其他殺程序路徑或 Android 原生回收也受保護。

一般自動殺程序也有可透過同一介面調整的 `mKillEnable`；或可嘗試以 shell 停用 TGuard App，但系統內建服務與 FreezeManager 仍可能存在。建議先做唯讀基準紀錄，再試暫時加入白名單；不足時再測關閉自動殺程序，最後才考慮停用整個 App。觀察 Netflix、重開機、鬧鐘與系統穩定性，失敗即還原。現有證據**不支持**把鬧鐘消失直接歸因為 force-stop。

## 2. Q1：白名單、免砍條件與 shell 可操作性

### 自動記憶體策略

**程式碼證實：** `StrategyConfig` 預設 `mKillWhiteList`：`com.google.android.apps.mediashell`、`com.android.bluetooth`、`com.android.providers.media.module`、`com.tcl.tvinput`、`com.android.providers.tv`、`com.tcl.autopair`、`com.google.android.katniss`、`com.tcl.ttvs`、`com.tcl.versionUpdateApp`、`com.tcl.usagestats`、`com.tcl.MultiScreenInteraction_TV`；是套件名稱**完全相等**，不是前綴（`src/tguard/sources/com/tcl/guard/memorymanager/core/memorystrategy/StrategyConfig.java:45`、`src/tguard/sources/org/tcl/ttvs/common/Constants.java:13`、`src/tguard/sources/com/tcl/guard/memorymanager/core/appclassifier/AppInfoRepository.java:157`）。自動策略及 cached PID 排除集合有讀它（`src/tguard/sources/com/tcl/guard/memorymanager/core/memorystrategy/KillHandler.java:225`、`:353`）。不是所有路徑都檢查：單一目標事件直接進 `killRecord`（`:48`），一鍵清理用另一名單（`:79`）；核心大函式 JADX 有控制流程錯誤警告，需實測。

同類硬寫預設還有 `mOneKeyCleanWhiteList`，只有 `com.tcl.MultiScreenInteraction_TV`；`mLauncherAppList` 三個桌面、`mSensibleAppExtraList`、`mImportantAppList`、`mFastStartAppList` 等分別影響分類或依賴保護，不能一概解讀成永久免砍（`StrategyConfig.java:46`、`:47`、`:50`、`:53`；`KillHandler.java:328`）。`mAutoBootWhiteList` 在此輸出初始為空，且名稱容易誤導：相關邏輯把符合項從自動重啟保護清單移除以便殺掉，而不是永久免砍（`AppInfoRepository.java:22`、`:280`）。

**動態設定來源：** 啟動時透過 `ConfigGrabber` 讀取 `TGuardMemoryManager`，並註冊變更觀察者；JSON 的 `KillWhiteList`、`OneKeyCleanWhiteList` 等以新陣列**取代**原列表，`KillEnable` 也可覆寫（`StrategyConfig.java:276`、`:290`、`:314`、`:348`、`:410`）。framework 使用 `content://com.tcl.config.ConfigProvider/TGuardMemoryManager`，查詢 `config_content` 欄並解析 JSON；變更通知後重新查詢（`src/tcl-framework/sources/android/config/ConfigGrabber.java:11`、`:39`、`:53`；`src/tcl-framework/sources/android/config/ConfigObserver.java:21`、`:29`、`:41`）。此 provider 的實作、匯出狀態、寫入權限及其上游本地／雲端來源不在所給已查程式中；不能宣稱 shell 可以 `content insert`、雲端一定下發或只有雲端下發。

**特別發現：暫時修改白名單的 dumpsys 路徑。** TGuard 的 service `dump` 收到至少兩個參數時呼叫 `dumpSet`，其 `Dump.set` 以參數成對查欄位並反射設定；`java.util.List` 用逗號拆開，`mKillWhiteList` 是 public 欄位（`src/tguard/sources/com/tcl/guard/memorymanager/TGuardService.java:82`、`StrategyConfig.java:227`、`src/tguard/sources/com/tcl/guard/memorymanager/utils/Dump.java:10`、`:36`、`:61`、`:83`）。AMS 服務 dump 會把參數傳給 App（`src/services/sources/com/android/server/am/ActiveServices.java:3712`、`:3737`）。因此 shell 通常可使用下列方式測試，**但是否允許此機型 shell DUMP、參數是否原樣傳遞及修改是否成功，都須上機確認**：

```sh
adb shell dumpsys activity service com.tcl.guard/.memorymanager.TGuardService
# 將上一指令列出的每個 KillWhiteList 值按原樣組成逗號清單，再附加 tvagent：
adb shell dumpsys activity service com.tcl.guard/.memorymanager.TGuardService mKillWhiteList '原有第一個套件,原有第二個套件,com.example.tvagent'
```

這是**整份取代，不是新增**；不能照抄佔位文字。要保留全部目前清單，並再次無參數 dump 驗證。若 ConfigProvider 後續推送或服務重建，可能恢復其他值；即使成功也不能保護其他清理路徑。另一份 `mOneKeyCleanWhiteList` 可被同一反射機制設定，但是否應變更、及手動清理的其餘路徑未驗證，不建議先動。

### 其他清理名單與資料來源

- **程式碼證實：** `dev/apk/assets/sys_config.xml:5` 的 `mem` 清單是舊 portal 掃描流程的排除名單，不是上述 `mKillWhiteList` 的已證實輸入。`RubbishRules` 優先讀指定檔，無資料才讀 asset；命中後 `isRubbishProcess` 返回 false，`MemManager` 不加入掃描結果（`src/tguard/sources/com/tcl/guard/portal/sys/RubbishRules.java:19`、`:74`、`:107`；`src/tguard/sources/com/tcl/guard/portal/sys/MemManager.java:31`）。`autoselfstart_config.xml:1` 另有自啟名單，未找到它接到自動殺程序白名單的證據；`ops_config.xml` 同樣未證實可保活。
- `dev/apk/assets/custom_config.xml` 是二進位資料；未找到與該檔直接相連的解密入口、金鑰與演算法，不能把其他通用 AES 類當作其證據。**未解密，未產生解密檔；內容和是否含名單均未查明。**
- TGuard 的 SharedPreference provider 宣告不匯出（`src/tguard/resources/AndroidManifest.xml:286`）；未找到 MMKV／SharedPreferences 是 `mKillWhiteList` 來源的證據。這不排除其他未分析的偏好設定。
- `Settings.Secure default_input_method` 被讀來辨認輸入法（`StrategyConfig.java:258`），不是套件白名單設定。沒有找到供 shell `settings put`、`am broadcast`、`appops` 或 `pm grant` 直接加入 `mKillWhiteList` 的已證實入口；不要憑名稱猜設定 key。

### 條件性保護與限制

**程式碼證實：** system_server 的 `killPidsByUser` 在整包層先排除分割畫面 docked App、目前頂層 App，以及「套件名小寫含 input 且輸入法視窗可見」；每個 PID 再排除 `setAdj <= 0`、有可見 Activity、某些目前前景 App 所依賴的 service／provider（`src/tclfw/sources/com/android/server/am/TGuardService.java:68`、`:82`、`:88`、`:314`、`:365`、`:386`、`:429`、`:471`；`src/tclfw/sources/com/android/server/am/AMSTGuardInjector.java:28`）。注意輸入法判斷是名字與可見狀態的具體實作，不等於所有預設輸入法永久免殺。小程式前綴也只在頂層時有該分支保護（`AMSTGuardInjector.java:21`、`:171`；`TGuardService.java:391`）。

SystemApp 理由要求 adj ≥900 才殺；音訊理由排除 adj ≤200；浮窗和 GMS 理由排除 adj ≤100；一般理由只排除 ≤0（`TGuardService.java:345`、`:365`）。播放音訊、前景通知、系統 App、浮窗都**不是絕對豁免**；判斷會隨理由和排名而改變（`KillHandler.java:232`）。App 自身、top、剛建立、近期自動重啟等在部分策略中被略過，且自動重啟保護會被重新整理（`KillHandler.java:175`、`:212`、`:153`；`AppInfoRepository.java:305`）。可見視窗判斷具體是 visible **activities**，不等同任何通知或所有 overlay。無障礙、桌布、裝置管理員、特定權限、系統簽章／UID 的普遍豁免未證實；不可把未查到說成不存在。

第三方 App 若在電視上持續顯示頂層畫面可受頂層條件保護，但會佔用畫面，無法同時作 Netflix 背景服務。`cmd deviceidle whitelist +com.example.tvagent` 針對 Android idle 白名單，已查 TGuard 自動清理程式沒有以該名單作上述豁免的證據；`appops set`、`pm grant`、前景 service 通知同理，不能當作可靠替代。

## 3. Q2：觸發、挑選及殺程序

**程式碼證實：** App 啟動時初始化分類器、native 記憶體監控與策略，啟動 polling（`src/tguard/sources/com/tcl/guard/memorymanager/TGuard.java:30`、`:44`、`:58`；`src/tguard/sources/com/tcl/guard/memorymanager/core/appclassifier/AppClassifier.java:33`）。預設週期 30,000 ms，至少 5 秒才排程；low-memory callback、polling、一鍵清理、螢幕狀態與特定 App 事件分流處理（`StrategyConfig.java:55`、`:61`；`StrategyManager.java:68`、`:105`、`:125`、`:158`；`src/tguard/sources/com/tcl/guard/memorymanager/eventreceiver/EventReceiver.java:63`）。前景變化由 system_server AppRecord 送往 App 作分類（`src/tclfw/sources/com/android/server/am/apprecord/AppRecordTransition.java:268`），但未證實「每次切換必殺」。已查 Java 路徑的 cold-start boost 實作固定回 false（`AppInfoRepository.java:35`），沒有證實 Netflix 或 YouTube 特別黑名單；Netflix 造成記憶體趨勢變化進而觸發是合理推測，非確定原因。

門檻並非單看使用者看到的 MemAvailable：預設 warning 由 LMK watermark、native high watermark 和 magic 1.5 計算，按記憶體等級與趨勢選 reclaim 或 kill；可被動態設定覆蓋（`StrategyConfig.java:74`、`:241`、`:363`、`:422`；`PollingStrategy.java:13`；`AbstractStrategy.java:38`、`:51`）。因此 590 MB 無法單獨證明未跨 TGuard 自己的門檻；需要讀當下 dump／native 值與時間序列。

自動策略依 package rank 迭代、估算 PSS 及釋放目標，必要時先殺 cached，並對系統 App、GMS、音訊、浮窗等標不同理由（`KillHandler.java:120`、`:136`、`:142`、`:169`、`:232`、`:245`、`:288`；rank 定義 `src/tguard/sources/com/tcl/guard/memorymanager/core/appclassifier/PackageRankFlag.java:4`）。system_server 的 cached 路徑僅對 adj ≥900 且不在傳入的排除 PID 清單者殺到估算 PSS 足額（`TGuardService.java:198`）。但一般 `tguard-kill` 的 PID 過濾只要求 adj >0、符合其餘條件，所以 adj 200、500、800 **可以**被砍；這與一般前景服務的存活預期不同（`TGuardService.java:365`）。SystemApp/Gms 是理由與分支，不是全部系統簽章應用必免殺。

**App / system_server 分工：** App 負責接收資料、分類、決定目標與理由；`killRecord` 交給 `killPidsByUser`（`KillHandler.java:401`、`:417`；`src/tguard/sources/com/tcl/guard/memorymanager/utils/KillWrapper.java:10`）。system_server 的 `TGuardService` 檢查 top、可見、adj、依賴後呼叫 `ProcessRecord.kill`（`src/tclfw/sources/com/android/server/am/TGuardService.java:68`、`:116`、`:125`）。`AMSTGuardInjector` 儲存前景與分割畫面狀態供檢查，並非 App 本身（`src/tclfw/sources/com/android/server/am/AMSTGuardInjector.java:10`、`:28`、`:139`）。另一個 `killPackageDependentsUID` 是獨立入口，要求 `KILL_UID`，轉入 AMS 的 `tGuardKillPackageProcesses`（`TGuardService.java:159`、`:169`、`:185`；`src/services/sources/com/android/server/am/ActivityManagerService.java:4215`）。現有 `killRecord` 證據指向 PID 路徑，**不是**這個獨立入口，也未顯示呼叫 force-stop 或移除 alarm。

**鬧鐘消失：未定論。** 此系統的 `OnAlarmListener` binder 死亡時會移除該 listener alarm（`src/services/sources/com/android/server/AlarmManagerService.java:1409`）；如果 tvagent 用該 API，進程被殺即可解釋。若是 `PendingIntent` alarm，需驗證是否另有取消、重新排程失敗、`PACKAGE_RESTARTED`／force-stop 等；AMS force-stop 會送該廣播，AlarmManager 收到後按 UID／套件移除（`ActivityManagerService.java:4205`；`AlarmManagerService.java:3948`、`:4002`）。只有 event log 和消失現象不能證明 stopped state。應同時比對 alarm 類型、死亡前後 dumpsys、`dumpsys package` stopped 狀態及 PACKAGE_RESTARTED；不要假定兩種 alarm 行為相同。

## 4. Q3：停用與系統設定

| 方法 | shell／效果及可逆性 | 證據與限制 |
|---|---|---|
| `dumpsys activity service ... mKillEnable false` | 預期 shell 可暫時關掉低記憶體／polling 的 strategy K；用原值還原，重啟／設定更新可覆蓋。未實機測權限。 | `Dump.java:10`、`StrategyConfig.java:56`、`AbstractStrategy.java:76`。不保證單包事件、一鍵清理或系統外部殺程序停用；`HandleEnable` 在 `StrategyManager.java:160` 僅記 log，不能據此保證關閉策略。 |
| `pm disable-user --user 0 com.tcl.guard` | shell 對一般套件通常可做，實際系統保護政策待測；`pm enable --user 0 com.tcl.guard` 還原。會停用整包功能。 | SystemServer 啟動 App service（`src/services/sources/com/android/server/SystemServer.java:2508`）；獨立 `tguard` 系統服務在 feature 開啟時註冊（`:2149`），不是同一進程。App 不運作預期不再發出其決策，但不能保證其他 caller 不使用 system_server API。未找到此範圍內自動重新 enable 的證據，也不能排除外部 watchdog／OTA。重開後須測。 |
| `pm suspend --user 0 com.tcl.guard` | shell 是否能對 system/shared UID 套用、是否讓正在執行的 service 停止，均未證實；還原 `pm unsuspend --user 0 com.tcl.guard`。 | 不能把 suspend 當 disable，也不能預言可停止既有策略；建議不作首選。 |
| `INT_TGUARD_ENABLE` | **不是已證實的 Settings key。** 不建議 `settings put global INT_TGUARD_ENABLE 0`：沒有讀取該 Settings 的證據。 | `TclFeatures.hasFeature` 且 `getIntFeature(...) == 1` 才註冊 system_server `tguard`；同時仍在該條件外呼叫 `startTGuard`（`SystemServer.java:2149`、`:2152`、`:2158`）。因此 feature 缺失／非 1 對此註冊等同關閉，並不證明其配置預設值或會阻止 App 被啟動。TclFeatures 的實作、資料源及 shell 可寫性未取得。 |
| `sys.tguard.restart` | 不建議更改；不是已證實的使用者停用開關，shell `setprop` 是否允許亦未知。 | 正常開機 system_server 設 false（`SystemServer.java:2156`）；AppRecord 偵測 `com.tcl.guard` 死亡設 true 並通知屬性變動（`src/tclfw/sources/com/android/server/am/apprecord/AppRecordTransition.java:255`）。這些檔案沒有證明其讀取者、實際重啟／禁用動作或設 true 可停用；init 腳本未提供。 |
| 工廠 P mode | 不建議當停用方法。 | 本機 SystemServer 判斷 `/userdata/sita_P` 是否存在，存在才跳過該啟動區塊（`SystemServer.java:666`、`:2149`；注意同區塊內 log 字串與分支相反，應看條件）。另一個 `TclUtils` 非海外版本檢查 `persist.P_MODE`，海外檢查檔案（`src/tclfw/sources/com/android/server/tclutils/TclUtils.java:64`）；不能推定更改 property 會改 SystemServer 的判斷。shell 寫入權限、工廠模式副作用均未查。 |

`persist.sys.freeze=true` 對應獨立 TCL FreezeManager 的設定；其初始化還依賴同一 TCL feature，並有獨立的 skip 名單與 config（`src/tclfw/sources/com/android/server/am/appfreeze/FreezeManager.java:89`、`:103`；`src/tclfw/sources/com/android/server/am/appfreeze/TclFreezeConfigUpdater.java:24`、`:41`、`:64`）。停 App 不等於停用 freeze；freeze 和 kill 也不能從 property 值混為一談。是否有 shell 權限改 property、何時重讀、設定更新覆蓋與副作用未驗證，不建議盲改。

## 5. Q4：自製 MemoryControl 的可行性與風險

TGuard APK 除自動記憶體清理外，還有一鍵／深度清理、健康資訊、使用時間與權限管理的元件（`src/tguard/resources/AndroidManifest.xml:59`、`:73`、`:166`、`:213`、`:278`）；portal 有獨立記憶體掃描（`src/tguard/sources/com/tcl/guard/portal/sys/MemManager.java:31`）。`dev/apk/assets/child/` 的存在不能單獨證明這一機型啟用距離或坐姿偵測，更不能確定停 App 必失哪些功能；開機加速實際分工也未查完。APK 停用有影響的可能性高於僅變更記憶體中的白名單。

`dev/apk/assets/config/kernel_config.xml` 內容是健康資訊讀取項，如 `/proc/meminfo`、`/proc/vmstat`、`/sys/block/zram0/mm_stat`，未見在該檔設定 lmk/minfree/zram；App 讀此檔作 health-info（`src/tguard/sources/com/tcl/guard/healthinfo/service/HealthInfoHandleService.java:1149`，XML 約第 1、24、82、152 行）。這不代表整個系統、native library、其他 init 設定沒有調參；也不保證 TGuard 未執行 reclaim/GPU 壓縮（`AbstractStrategy.java:100`；`StrategyConfig.java:57`）。2 GB 是否單靠 lmkd 足夠，必須以該電視真實負載測試，不能從靜態檔保證。

只做「保護 tvagent，其餘交給 lmkd」的最低成本版本：優先用上述 TGuard 執行時名單，並測重啟與 provider 更新；若需要持久，需有允許的持久設定來源或授權的開機自動套用機制。普通 App 沒有自動獲得修改 TGuard／system_server 策略的特權；shell dump 有 DUMP 能力也不代表 App UID 有該能力。即使保護 TGuard 路徑，Android LMKD、force-stop、使用者停用、freeze、重啟等仍需設計可恢復的工作排程。需查 tvagent alarm 實作、重建策略及實際系統權限，再決定是否需要具有 shell／系統簽章權限的受控管理工具。

風險由低至高：① 只讀觀察；② 暫時保留原名單再附加 tvagent；③ 暫時關閉 strategy K；④ 停用整個 TGuard App；⑤ 修改工廠模式、系統屬性、provider、kernel 或替換系統元件。後兩者可能影響其他功能或設備穩定，不建議先做。

## 6. 建議上機驗證（本次均未執行）

以下命令在電腦執行；每次先保存結果與時間。先確認有其他方式可操作電視、記錄原狀，避免遙控器依賴 TGuard 而失去操作。若任何命令回 Permission Denial、服務不存在、dump 無 client 或結果異常，停止並還原；不使用 root。

1. **只讀基準，不需還原。**

   ```sh
   adb shell dumpsys activity service com.tcl.guard/.memorymanager.TGuardService
   adb shell dumpsys package com.tcl.guard
   adb shell dumpsys package com.example.tvagent
   adb shell dumpsys alarm | grep -i -C 8 com.example.tvagent
   adb shell dumpsys meminfo
   adb shell getprop sys.tguard.restart
   adb shell getprop persist.sys.freeze
   adb logcat -b events -d -v time | grep -i 'tguard-kill'
   ```

   預期 service dump 顯示 `KillWhiteList`、`OneKeyCleanWhiteList`、`mKillEnable`、warning 與 rank；若服務沒啟動或無權限，不能使用後續 dump 設定。記錄 tvagent 原套件 stopped 狀態、alarm 的類型和服務 PID。只讀 ConfigProvider 可另試 `adb shell content query --uri content://com.tcl.config.ConfigProvider/TGuardMemoryManager --projection config_content`；預期有 JSON 或權限拒絕，均不證明可寫。還原：無。

2. **僅改執行期自動白名單。** 將步驟 1 每行 `KillWhiteList` 值按原順序拼成逗號清單 `ORIGINAL`，確定無空值且未遺漏，再在同一 shell 中手動替換下列佔位符：

   ```sh
   adb shell dumpsys activity service com.tcl.guard/.memorymanager.TGuardService mKillWhiteList 'ORIGINAL,com.example.tvagent'
   adb shell dumpsys activity service com.tcl.guard/.memorymanager.TGuardService
   ```

   預期 dump 新增 tvagent 並保留所有原成員；若無變更或有欄位錯誤則方法失敗。用相同 Netflix 使用情境觀察至少先前的 25 分鐘，並比對 kill、alarm 與其他 App 行為；重啟服務／電視後再查持久性。還原：`adb shell dumpsys activity service com.tcl.guard/.memorymanager.TGuardService mKillWhiteList 'ORIGINAL'`，再 dump 確認。`ORIGINAL` 必須替換成實際完整原值；若清單原本為空，本反射解析空字串的結果需另行驗證，勿盲還原。此操作會取代清單且可能被 ConfigProvider 覆蓋。

3. **比較性驗證自動 strategy K，影響較大。** 先記下 `mKillEnable` 實際原值，若原值為 true：

   ```sh
   adb shell dumpsys activity service com.tcl.guard/.memorymanager.TGuardService mKillEnable false
   adb shell dumpsys activity service com.tcl.guard/.memorymanager.TGuardService
   ```

   預期 dump 顯示 false，polling／low-memory 的 strategy K 不再派殺；觀察 event log、可用記憶體與播放表現，不能推定一鍵清理及其他殺程序停止。還原：若原本為 true，執行 `adb shell dumpsys activity service com.tcl.guard/.memorymanager.TGuardService mKillEnable true` 並讀回；若原值不同，還原原值。執行前也應還原步驟 2，才能單獨比較其效果。

4. **最後才測停用整包。** 先記錄原先 package enabled 狀態及上述資料；以下假設原本 enabled：

   ```sh
   adb shell pm disable-user --user 0 com.tcl.guard
   adb shell dumpsys package com.tcl.guard
   adb shell service check tguard
   ```

   預期 package 顯示 disabled-user，system_server 的 `tguard` 服務可能仍存在；持續觀察相同 Netflix 情境、event log、鬧鐘、操作介面與 freeze，並在設備允許時測重啟後狀態。若 disable 被拒或不符預期，記錄錯誤。還原：`adb shell pm enable --user 0 com.tcl.guard`，再確認 enabled 並視需要重開電視；若原本不是 enabled，應還原成先前狀態，而不是直接 enable。不要在無法恢復的環境做此步。

5. **釐清 alarm：只讀。** 檢查 tvagent 原始碼究竟使用 `OnAlarmListener` 還是 `PendingIntent`；殺前後分別記錄 `dumpsys alarm`、`dumpsys package com.example.tvagent` 與相關 log。預期前者 listener 在 binder 死亡時可能消失；後者若消失則追查取消／PACKAGE_RESTARTED／stopped。還原：無。不要以 `am force-stop` 對照測試而不先理解它會清除鬧鐘並改變套件狀態。

## 7. 未查／不確定事項

- `custom_config.xml` 解密、演算法與內容；native 庫完整控制流程、加殼與動態載入、JNI 記憶體計量來源。
- ConfigProvider 實際程式、匯出權限、寫入或雲端上游；真機實際下發的白名單／threshold。尚未證實可 shell `content insert`。
- `TclFeatures` 的資料來源、`INT_TGUARD_ENABLE` 原始預設值、SELinux／shell 權限；init 對 `sys.tguard.restart` 的讀取與其他自動啟用監控。
- 真機 dumpsys 權限、service 參數傳遞及反射寫入、所有重啟情境；`pm disable-user`／suspend 的實機行為；未能執行 adb。
- 完整的系統 App PSS 分支與 JADX 損壞控制流程、個別事件的完整觸發鏈、是否存在其他背景清理者；freeze 具體條件；兒童功能及開機加速的實際啟用。
- tvagent 的 alarm 類型和原始碼、鬧鐘消失的直接原因；2 GB 實際壓力下的 lmkd／zram 和播放穩定度。

REPORT_DONE
