# 方案 C 自建輕量機制（iPhone → 家中常駐服務 → ADB 控制 YouTube）技術可行性

結論：不必開發 Android TV App 即可做到核心閉環（iPhone 核准單次 N 分鐘 → 到期自動鎖回），但有三個硬傷必須接受：(1) 網路 ADB 在 TCL 本機重開機／待機後是否保持連線未驗證，是最大單點風險；(2) 孩子能否自行在「設定 → 應用程式」按啟用解鎖，未驗證，是最大防繞過風險；(3) 瀏覽器開 youtube.com 與第三方播放器需另行封堵，無法只靠停用 YouTube App 解決。

建議架構：Home Assistant（家中常駐）＋ Android TV 整合的 `adb_command` 發送 `pm disable-user / enable`＋`am force-stop`；iPhone 用 HA App 按鈕或捷徑呼叫 HA webhook／script；計時以服務端時間戳為準，輪詢 reconcile。

## 1. 網路 ADB 在 Android TV 11 的現況

- Android TV 11 沒有「無線偵錯（配對碼）」。官方 adb 文件寫明：配對碼式無線偵錯只支援手機 Android 11+，TV／Wear OS 要 Android 13+ 才有。Android TV 11 只能用傳統方式：開發者選項開「ADB 偵錯」＋「網路偵錯」，電腦 `adb connect <IP>:5555`。[官方文件] https://developer.android.com/tools/adb
- TV 通常沒有可用的 USB 偵錯（USB 孔只給隨身碟／鍵盤），實務上就是走網路 5555；首次連線電視會跳「允許 USB 偵錯？」對話框，要用遙控器勾「一律允許」。[社群實測] https://adbkit.com/blog/control-android-tv-with-adb ；同樣流程 [社群實測] https://www.appitika.com/read/enable-developer-options-android-tv-adb-debugging
- TCL 機種同一套流程（設定 → 開發者選項 → USB 偵錯開 → `adb connect <TV_IP>:5555` → 電視按允許）。[社群實測] https://github.com/mojothemonkey2/TCL_Guide 。注意該 repo 是 C645K（英國）與 R51MT02 平台（Realtek RTD2851M＋Android TV），本機是 R851T02＋RT2851，不可把該文的套件清單當本機可用。
- RSA「一律允許」授權本身是持久的（存在電視端，已授權金鑰），HA 官方文件也要求勾「always allow」。[官方文件] https://www.home-assistant.io/integrations/androidtv/ 。但這不等於「重開機後 5555 還在聽」。
- 重開機後網路 ADB 是否保留（開關是否跳回關閉）：查不到 TCL P715／R851T02 的回報。只找到 Google TV Streamer 使用者在 HA 論壇回報重開機後 debugging 被關掉連不上（不同裝置、Google TV，不可直接套用，且該串最後放棄）。[社群實測] https://community.home-assistant.io/t/android-debug-bridge-google-tv-streamer-not-working/955714 → 本機行為列入待實機驗證。
- 待機：很多電視在待機時會斷網，ADB 連不上；解法是關掉省電待機／quick start 類選項，給電視固定 IP。[社群實測] https://adbkit.com/blog/control-android-tv-with-adb 。AOSP 的 TV 待機機制（inattentive sleep）是關螢幕省電，`PARTIAL_WAKE_LOCK` 不受影響，但 Wi-Fi／adbd 在待機是否存活是各廠實作差異。[官方文件] https://source.android.com/docs/core/power/tv-standby → TCL 本機待機可連性列入待實機驗證。
- 同一時間只能有一個 ADB client 連電視（HA 佔住後，電腦 `adb` 要先斷開）。[官方文件] https://www.home-assistant.io/integrations/androidtv/ 。設計上所有命令都走 HA 的 `adb_command`，不要另起 adb client。

## 2. 鎖 YouTube 的手段比較（disable-user／enable／force-stop／suspend）

- `pm disable-user --user 0 <pkg>`：把該使用者下的 App 停用，不砍資料，可逆（`pm enable` 恢復）。不需要 root，adb shell（uid=shell）權限即可執行，狀態是 per-user 覆寫，不動唯讀系統分割區。[社群實測] https://bytescope.dev/en/web-adb/commands/pm-disable-user/ ；手機側同樣用法（`pm uninstall -k --user 0`／`pm disable --user 0` 不需 root）。[社群實測] https://syndamia.com/blog/managing-apps-with-adb-without-root/
- `pm enable`：恢復。注意有廠商 ROM 會拒絕重新啟用（回傳仍是 disabled 狀態但 exit 0），要讀回狀態行確認。[社群實測] https://bytescope.dev/en/web-adb/commands/pm-disable-user/
- `am force-stop <pkg>`：只殺進程，不改啟用狀態，官方命令表有列（`force-stop`）。[官方文件] https://developer.android.com/tools/adb 。用途是到期瞬間中斷正在播放的影片，必須搭配 disable，單用無鎖定效果。
- `pm suspend`／`cmd package suspend`（Android 7+ 機制）：App 圖示變灰、點了跳系統對話框說被暫停；被暫停的 App 不能起 Activity、通知被壓住、不出現在最近任務、不能播聲音。[官方文件] https://developer.android.com/work/dpc/security 。但該文件描述的是 DPC（device owner／profile owner）呼叫 `setPackagesSuspended()` 的路徑；adb shell 能否直接 `cmd package suspend` 達到同等效果，本次沒有讀到可引用的來源 → [推測/待驗證]。預設以 `disable-user` 為主力，suspend 列為候選（優點是視覺提示明確，缺點是 TV launcher 是否灰化顯示未知）。
- 重開機後狀態保持：
  - `disable-user` 是寫在使用者設定裡的持久狀態（不是暫存），一般會跨重開機保留；但本機 TV+OS 未驗證 → [推測/待驗證]。
  - `suspend` 是否跨重開機：查不到，列入待驗證。
  - 兩者都不需要 root 即可由 adb shell 下達（disable-user 已證實；suspend 待驗證）。
- 各入口在 YouTube 被 disable-user 後的行為：
  - Launcher 圖示：從啟動器消失（disable 的標準行為）。[社群實測] https://bytescope.dev/en/web-adb/commands/pm-disable-user/ 、 https://tvboxtools.com/debloat-android-tv/
  - 遙控器 YouTube 快捷鍵：會送指向該套件的 intent，套件停用後按鍵無反應或跳「未安裝」類提示 → [推測/待驗證]（待實機按）。
  - Google Assistant 語音「播放 YouTube」：解析目標就是官方 YouTube App；App 停用後語音無法開播。旁證：SmartTube 要接管語音搜尋，必須先解除安裝官方 YouTube（一般 Android TV 需 root 才能卸），說明語音路徑綁定官方套件。[官方文件] https://github.com/yuliskov/SmartTube 。確切失敗訊息 → [推測/待驗證]。
  - Chromecast built-in（iPhone YouTube App 投放）：Android TV 的 Cast 接收端就是 TV App 本體（Cast Connect：TV App 作為 receiver，sender 發起在 TV App 內播放）。[官方文件] https://developers.google.com/cast/docs/android_tv_receiver 。因此停用 YouTube TV App 後，「投放 YouTube 到電視」應失敗（接收端不在了）；Netflix 等其他 App 各自有 receiver，不受影響。確切失敗 UX → [推測/待驗證]。
  - YouTube Kids：套件名推測為 `com.google.android.youtube.tvkids`（Play 商店頁抓取 404，未能驗證）→ [推測/待驗證]，實機用 `pm list packages | grep -i youtube` 確認後比照停用。
  - 第三方播放器（SmartTube 等）：獨立套件，與官方 YouTube 停用無關；對策是根本不安裝＋關閉「安裝未知應用」；若已裝就同法 disable。SmartTube 官方 README 確認其投放需手機輸 TV-code 配對且 TV 端 App 須先開著（不能自動喚醒電視）。[官方文件] https://github.com/yuliskov/SmartTube
  - 內建瀏覽器開 youtube.com：App 層停用擋不住，必須另行處理（停用／移除 TCL 瀏覽器、或路由器層擋網域）。TCL Guide 在別台 TCL 上用的瀏覽器套件是 `com.tcl.browser`，型號不同僅供參考。[社群實測] https://github.com/mojothemonkey2/TCL_Guide
- 套件名確認：YouTube TV 版為 `com.google.android.youtube.tv`（SmartTube README 的 root 移除指令中出現）。[官方文件] https://github.com/yuliskov/SmartTube

## 3.「單次 N 分鐘」授權邏輯設計（不寫 TV App 的做法）

- 授權指令：`pm enable com.google.android.youtube.tv`（如需自動開播再加 `am start`／`monkey -p`，非必要）。
- 到期指令：`am force-stop com.google.android.youtube.tv && pm disable-user --user 0 com.google.android.youtube.tv`。`force-stop` 先中斷播放是必要步驟，否則畫面可能停在最後一幀。
- 計時基準：核准當下記 `expires_at = now + N 分鐘`，存服務端；不暫停（切 App、關電視都繼續走）。這是設計選擇，無需外部引用 → [推測/待驗證]（指實作細節待定案，非事實爭議）。
- 電視關機／重開也照算：到期時間是絕對時間戳，重開機不會重置額度 → 同上，設計選擇。
- 到期時電視關機／斷線：下次 ADB 連上時做 reconcile——若 `now > expires_at` 且 YouTube 是啟用狀態，立即 force-stop＋disable。需要服務端開機自檢＋定時輪詢（每 1–2 分鐘）執行同一 reconcile。HA timer helper 有 `restore` 可在重啟後恢復計時，但官方明確寫：若 timer 在 HA 停機期間走完，`timer.finished` 不會補觸發。[官方文件] https://www.home-assistant.io/integrations/timer/ 。所以不要只靠 `timer.finished`，要用「時間戳比對」當唯一真相（automation 條件或 script 開頭檢查）。
- 服務端斷線時電視停在什麼狀態：若在授權期內服務掛掉，到期動作不會執行，YouTube 會一直開著直到服務恢復（reconcile 補鎖）。空窗長度＝斷線時長，這是本方案固有弱點，只能縮小（常駐服務高可用、看門狗、UPS 非必要）不能消除。
- HA 側全部用現成積木即可：`androidtv.adb_command` 可送任意 shell／按鍵命令並回傳輸出。[官方文件] https://www.home-assistant.io/actions/androidtv.adb_command/ ；`media_player.select_source: "!com.xxx"` 可停 App。[官方文件] https://www.home-assistant.io/integrations/androidtv/ 。到期後可用 `GET_PROPERTIES` 查狀態。[官方文件] https://www.home-assistant.io/integrations/androidtv/

## 4. 家中常駐服務選項與成本

- Home Assistant 是最省事的載體：Android TV 整合內建 Python ADB（官方推薦用內建實作，不要用 ADB Server）。[官方文件] https://www.home-assistant.io/integrations/androidtv/ 。社群 ADB Server add-on 已棄用封存（2021 年後無更新，`archived:true`）。[官方文件] https://github.com/hassio-addons/addon-adb/blob/main/adb/DOCS.md 、 https://api.github.com/repos/hassio-addons/addon-adb 。底層 `python-androidtv` 仍在維護（2026-10 還有更新）。[官方文件] https://api.github.com/repos/JeffLIrion/python-androidtv
- HA 不寫程式即可做到「單次 30 分鐘」：自動化（按鈕/script 觸發 → enable＋啟 timer）＋ timer 結束觸發（force-stop＋disable），15／30／60 做三個 script 或一個帶時長參數的 script，全程可用視覺化編輯器。Timer／automation／trigger 都是 HA 內建。[官方文件] https://www.home-assistant.io/integrations/timer/ 、 https://www.home-assistant.io/docs/automation/trigger/
- 硬體選項：
  - 現有 Mac 常駐：零新增成本；要求開蓋／不睡眠且人在家中時開機，出門即失效，可靠度最低。→ [推測/待驗證]（使用條件評估）。
  - 樹莓派／舊電腦／NAS Docker：一次性硬體成本、功耗低、全天開機，適合跑 HA。HA 官方支援裝在 Pi、x86 電腦、容器／虛擬機。[官方文件] https://www.home-assistant.io/hardware/ 。具體型號與金額本次未查價，不列數字。
  - Home Assistant Green 等官方盒子：免組裝，但需另購。[官方文件] https://www.home-assistant.io/hardware/
  - 路由器 M9 本體：原廠韌體不能跑 HA／ADB 常駐程序，不可作為常駐服務 → [推測/待驗證]（依原廠韌體能力判斷，未實測）。
- 維護面：HA 方案的維護＝HA 本體更新＋電視 IP 固定（DHCP 綁定）＋ADB 授權維持；比自己寫 daemon 省，但仍是一個 24/7 服務，不是零維護。

## 5. iPhone 端與遠端操作

- 同區網：HA App  dashboard 放三個按鈕（15／30／60），或用 Siri／捷徑呼叫 HA。HA Companion App 內建 App Intents：「Perform action／Run script／Trigger automation／Press button」，捷徑直接選動作＋填 JSON，不用自己組 HTTP。[官方文件] https://companion.home-assistant.io/docs/integrations/siri-shortcuts/
- 純捷徑打 HTTP 也行：iOS「取得 URL 內容」支援 GET／POST／PUT／PATCH／DELETE＋JSON body。[官方文件] https://support.apple.com/guide/shortcuts/request-your-first-api-apd58d46713f/ios 。對應 HA webhook trigger：`POST /api/webhook/<webhook_id>`，預設僅區網（`local_only`），webhook_id 視同密碼不可外洩。[官方文件] https://www.home-assistant.io/docs/automation/trigger/
- 在外遠端：
  - Tailscale：Personal 版免費（裝置無上限、最多 6 使用者），手機＋HA 同 tailnet，不需開 port。[官方文件] https://tailscale.com/pricing 。需在 HA 主機上裝 Tailscale。
  - Home Assistant Link（原 Cloud，原 Nabu Casa）：月費 6.50 美元／年費 65 美元，31 天試用，免開 port、免自管憑證。[官方文件] https://www.nabucasa.com/link/ ；HA 文件確認遠端存取是其訂閱功能。[官方文件] https://www.home-assistant.io/cloud/
  - Cloudflare Tunnel：`cloudflared` 只建對外連線，源站不需公網 IP／不開 inbound port。[官方文件] https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/ 。網域與免費額度細節本次未深入 → [推測/待驗證]。
- 資安：ADB 5555 絕對只留區網，不做 port forward、不上雲；HA 對外只走上述三者之一；給捷徑用的 credential 用 HA 長效 token（效期 10 年，可撤銷）或 webhook_id，遺失即換。[官方文件] https://developers.home-assistant.io/docs/auth_api/ 、 https://www.home-assistant.io/docs/automation/trigger/

## 6. 防繞過

- 關鍵風險（未驗證）：孩子能否自己進「設定 → 應用程式 → YouTube → 啟用」把 disable-user 解開。AOSP 預設行為是停用的 App 在設定裡仍可由使用者手動啟用，本次沒讀到 TCL TV+OS 的實際畫面 → [推測/待驗證]。若可自行啟用，disable-user 方案防繞過即破功，上線前必須實機確認；屆時再評估 `suspend` 是否不可由使用者解除（同樣待驗證）。
- 重開機繞過：disable 狀態本身應跨重開機（待驗證）；但若網路 ADB 開關在重開機後跳掉，服務連不上，電視會停在「鎖定」還是「開放」取決於重開機前一刻的狀態——重開前是鎖的，開機仍是鎖的（安全側）；重開前是授權中，開機後授權繼續但服務若連不上就無法到期鎖回，直到連上才 reconcile。這段是機制推演 → [推測/待驗證]。
- 恢復原廠設定會清掉一切（含 ADB 授權、disable 狀態、HA 設定不變但連線失效）：後果是家長要全部重設，且重置過程孩子可看 YouTube。屬通用行為，列為接受風險 → [推測/待驗證]。
- 切帳號繞過：Android TV 11 沒有兒童設定檔／多使用者這類 Google TV 專屬機制可切換（Family Link／兒童設定檔是 Google TV 功能，不在 Android TV 11 上），本機是共用單一環境，鎖整台電視的 YouTube 即可，不存在「切帳號解鎖」路徑 → [推測/待驗證]（OS 功能差異，需實機確認設定內無多使用者選項）。
- 其他入口（Kids／SmartTube／瀏覽器／投放／語音）見第 2 節：投放與語音綁官方套件，停用即失效；瀏覽器與第三方 App 必須逐個處理，不可宣稱單一 disable 完成全面封鎖。

## 7. 做得到／做不到清單（不開發 TV App 前提下）

不必開發 Android TV App 就能做到：
- iPhone 一鍵核准單次 15／30／60 分鐘（HA script＋button／捷徑，不寫程式只貼 shell 字串）。
- 到期自動 `force-stop`＋`disable-user`，無需人工。
- 授權期間與到期狀態以服務端時間戳為準；服務／電視重啟後靠輪詢 reconcile 補鎖。
- 遙控器快捷鍵、語音搜尋、iPhone 投放 YouTube 連帶失效（綁同一官方套件），Netflix／Disney+／四季線不受影響（獨立套件）。
- 在外用 Tailscale（免費）或 HA Link（付費）遠端核准，不暴露 ADB port。

做不到／需額外手段：
- 擋不住電視瀏覽器開 youtube.com（需另停瀏覽器或路由器擋網域）。
- 擋不住已安裝的第三方 YouTube 客戶端（需逐個停用；根本之道是不裝＋關未知來源）。
- 不能保證孩子無法自行在設定裡按「啟用」（待實機驗證；若可，方案破功）。
- 不能保證重開機後網路 ADB 仍開著（待實機驗證；若關掉，需有人拿遙控器重開）。
- 待機中可能連不上 ADB（待實機驗證；靠喚醒後 reconcile 補救，到期鎖定會延遲）。
- 服務斷線期間的授權無法準時收回（架構固有，只能縮小空窗）。
- 恢復原廠設定即全部清空（接受為殘餘風險）。

## 待實機驗證清單

1. `adb connect <TV_IP>:5555` 首次配對流程與「一律允許」是否如預期；記下 TV 開發者選項內實際開關名稱（ADB 偵錯／網路偵錯）。
2. 重開機後：(a) 網路 ADB 開關是否保持開啟；(b) `adb connect` 是否免確認直連；(c) `disable-user` 狀態是否保留。
3. 待機（假關機）10 分鐘後 `adb connect`／已連線是否存活；省電／快速開機類選項的影響。
4. `pm disable-user --user 0 com.google.android.youtube.tv` 後：launcher 圖示、遙控器 YouTube 鍵、語音、iPhone 投放各是什麼反應（錄影記錄）。
5. 設定 → 應用程式 → YouTube（已停用）是否出現「啟用」按鈕，孩子帳號（無 PIN 情境）能否按下。
6. `cmd package suspend com.google.android.youtube.tv` 在 shell 下能否執行、效果與跨重開機行為；與 disable-user 擇優。
7. `am force-stop` 對播放中 YouTube 是否立即黑屏回桌面。
8. 實機 `pm list packages | grep -i -E "youtube|kids|tube|browser"` 列出全部相關套件（含 Kids、TCL 瀏覽器），確認套件名。
9. HA `androidtv` 整合連線本機：Python 實作是否穩定；`adb_command` 送上述命令是否成功；固定 IP（DHCP 綁定）。
10. 到期時電視關機→開機，reconcile 補鎖是否如設計（用短 timer 如 2 分鐘演練）。
11. HA 主機斷電／斷網期間授權逾期的行為演練。
12. 恢復原廠設定後的重建步驟與耗時（只演練流程，不預先執行）。

## 查不到／不確定

- TCL 55P715（V8-R851T02-LF1V715）在重開機／待機後網路 ADB 開關行為：無同型號回報。
- `disable-user` 的 App 在 TCL TV+OS 設定選單中是否可被一般使用者手動啟用：無來源。
- `cmd package suspend` 經 adb shell 使用的可行性與跨重開機行為：無可引用來源。
- 停用後遙控器快捷鍵／語音／投放的確切錯誤 UX：無同平台實測。
- YouTube Kids TV 版套件名（Play 商店頁抓取失敗，只能實機列舉）。
- Android TV 11 是否有任何多使用者／兒童模式可被利用繞過：傾向沒有（兒童設定檔屬 Google TV），但無直接來源。
- M9 台灣 V4.0 原廠韌體是否提供任何可用於本方案的能力（如 Wake-on-LAN 定時、網域排程）：超出本題範圍，未查。
- Cloudflare Tunnel 免費額度與自訂網域需求：未深入。
- 樹莓派／HA Green／NAS 的具體售價與功耗數字：未查價，不列。
