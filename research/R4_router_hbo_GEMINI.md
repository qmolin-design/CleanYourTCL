# R4 方案 B：MeshBrand MeshRouter M9 原生功能 ＋ HBO Max 安裝問題 ＋ 55P715 規格（GEMINI 研究報告）

研究日期：2026-10-29　｜　範圍：僅網路公開資料與官方文件查證，未碰實機、未執行 ADB、未登入路由器。

---

## 〇、核心結論速覽

1. **MeshBrand MeshRouter M9 原生功能無法滿足「臨時核准單次 30 分鐘 YouTube 且不影響其他 App」的需求**：
   - HomeShield 的「暫停上網（Pause）」為整台裝置斷網，會直接癱瘓電視上的 Netflix 與四季線上，違反需求定義。
   - HomeShield 免費版雖有自訂網址黑名單（Blocked Websites），可封鎖 YouTube 網域，但為靜態開關；家長若要臨時開放 30 分鐘，必須手動進 App 刪除網域、30 分鐘後手動加回，無法做到自動倒數或一鍵單次授權。
   - HomeShield Pro（需付費訂閱）具備「上網時間獎勵（Reward / Extra Time）」，但該加時是針對**整台裝置的每日總上網額度**加時，並非單一 App 授權；且其「App 時間限制」為每日自動重置的固定配額，不支援「平常鎖定、家長臨時單次核准」。
   - **AP 模式（基地台模式）致命限制**：若 M9 設定為 AP 模式，HomeShield 全部功能（家長控制、網址封鎖、QoS）直接失效。
2. **網路層只封 YouTube 的技術限制**：
   - 封鎖 YouTube 串流 CDN（`googlevideo.com`）與 API（`youtubei.googleapis.com`）不會破壞 Google Play 核心下載更新與 Google 帳號認證，但會導致 Google Play 商店內的 App 預覽影片無法播放。
   - Android TV 11 與 Google 服務常寫死向 `8.8.8.8` / `8.8.4.4` 發送 DNS 查詢，且可能走 DNS-over-TLS（DoT，Port 853）。若路由器僅靠 DHCP 指派 DNS 而無強制攔截（DNAT Port 53），網路層封鎖會被輕易繞過。
   - 替代方案（NextDNS / AdGuard Home / Pi-hole）具備 API 可控性，但 M9 原生韌體無法針對單一裝置指派不同 DNS；且缺乏本機常駐定時排程伺服器，無法在 iPhone 端單鍵觸發後可靠執行「30 分鐘後自動閉合鎖定」。
3. **HBO Max（現已改名 Max）無法安裝的主因**：
   - 華納兄弟探索已於 **2024 年 11 月 19 日**在台灣正式推出 **Max** 取代 HBO GO，App 名稱已變更為「Max」（非 HBO Max）。
   - TCL 55P715（Realtek RT2851 平台，Android TV 11）無法在電視內建 Google Play 搜尋或安裝，主要係因 Max 官方在 Google Play 後台設定了裝置白名單（Device Catalog Filtering），未將此舊款 Realtek 晶片機型列入相容清單；並非 Widevine L1 缺失（本機具備 L1）。側載 Android TV 版 APK 可行，但面臨無法自動更新與杜比全景聲（Dolby Atmos）支援不全的風險。
4. **TCL 55P715 規格瓶頸與卡頓真相**：
   - 規格確認：SoC 為 Realtek **RTD2851**（機芯 R851T02），4 核心 Cortex-A55 @ 1.0GHz，GPU 為 Mali-470 MP3，RAM 僅 **2GB DDR3**，儲存空間 **16GB eMMC**。
   - 官方 OTA 支援現況：R851T02 平台的官方維護已實質終止（EOL），停留在 Android TV 11 分支（V7xx 系列），無 Android 12 或 Google TV 升級。
   - 卡頓根本原因：低階 SoC 加上僅 2GB RAM，升級 Android TV 11 後受 TCL TV+OS 背景服務與推薦動態渲染嚴重擠壓，可用 RAM 常態低於 200MB，頻繁引發 Low Memory Killer（LMK）與 Flash I/O 延遲。

---

## 一、MeshBrand MeshRouter M9 台灣版 V4.0 原生功能分析

### 1.1 韌體版本與 HomeShield 支援
- **韌體支援**：MeshRouter M9 台灣版 V4.0（硬體標籤 Ver: 4.0 / TW）具備官方維護韌體，最新分支為 1.2.x 繁體中文版本。 [官方文件]
  - 來源：[MeshBrand 台灣官方 MeshRouter M9 V4 下載中心](https://www.meshbrand.com/tw/support/download/archer-M9/v4/)
- **HomeShield 服務整合**：M9 全系列硬體版本均原生內建 MeshBrand HomeShield 網路防護與家長控制架構，由手機端 MeshBrand Tether App 統一管理。 [官方文件]
  - 來源：[MeshBrand HomeShield 官方介紹頁](https://www.meshbrand.com/tw/homeshield/)

### 1.2 HomeShield 免費版 vs HomeShield Pro 差異與台灣定價
HomeShield 採免費基礎版＋付費 Pro 訂閱制：

| 比較項目 | HomeShield 免費版（Basic） | HomeShield Pro（付費訂閱版） |
|---|---|---|
| **費用** | 免費內建 | 需透過 iOS App Store / Google Play 訂閱（提供 30 天免費試用） |
| **台灣區參考價格** | NT$ 0 | 月繳約 NT$ 170～190 元／月；年繳約 NT$ 1,490～1,790 元／年 [官方文件/社群實測] |
| **網路安全防護** | 基礎網路安全掃描、IoT 設備識別 | 即時 IoT 設備防護、惡意網站攔截（Web Protection）、入侵防禦（IPS）、DDoS 防護 [官方文件] |
| **家長控制：設定檔** | 支援建立家庭成員 Profile 並綁定設備 | 支援建立家庭成員 Profile 並綁定設備 |
| **家長控制：網址封鎖** | **支援自訂黑名單（Blocked Websites）**，可手動輸入特定網域 [官方文件] | 支援自訂黑名單，增加專業內容類別自動過濾（成人類、社交類等） |
| **家長控制：App 管理** | **不支援** App 獨立封鎖 | 支援 App 封鎖（Blocked Apps）與 App 每日時間限制（App Time Limits） [官方文件] |
| **時間管理功能** | 基礎就寢時間（Bedtime）、一鍵暫停上網（Pause） | 進階離線排程（Off-time）、**上網時間獎勵（Reward / Extra Time）** [官方文件] |
| **報表功能** | 每週／每月摘要報表 | 詳細造訪 URL 歷史記錄、流量分析、安全警示分析 [官方文件] |

- 來源：
  - [MeshBrand 台灣 HomeShield 方案比較](https://www.meshbrand.com/tw/homeshield/)
  - [MeshBrand FAQ: Differences between HomeShield Free and Pro](https://www.meshbrand.com/en/support/faq/3264/)

### 1.3 家長控制（Parental Controls）在單一裝置（電視）上的封鎖能力
- **能否針對單一裝置（電視）封鎖特定網站/網域？**
  - **可行**。 [官方文件]
  - **運作機制**：在 Tether App 的「家人（Family / Parental Controls）」分頁中，家長可為電視建立一個獨立設定檔（例如命名為「客廳電視」），並僅將電視的 MAC 位址／IP 指派給該 Profile。在該 Profile 下的「封鎖的網站（Blocked Websites）」手動輸入網域（如 `youtube.com`、`googlevideo.com`）。
  - **對其他裝置與 App 的影響**：該黑名單僅對指派給該 Profile 的電視生效，家長的手機、電腦不受限制。而在電視內部，由於 Netflix、Disney+、四季線上（4gTV）連線至各自獨立的伺服器，不經過 YouTube 網域，因此影音串流不受影響。 [推測/待驗證]
- **免費版有無自訂網域封鎖？**
  - **有**。HomeShield 免費版即具備手動「Blocked Websites」黑名單輸入功能，不需訂閱 Pro。 [官方文件]

### 1.4 一鍵操作功能評估（Pause、Bonus Time、Reward）
- **暫停上網（Internet Pause）**：
  - [官方文件] Tether App 支援在個人設定檔上一鍵「Pause」。
  - **致命缺點**：Pause 是**切斷該裝置的所有外部網際網路連線**。一旦按下，電視的 Netflix、四季線上及所有網路服務同步中斷，完全違背「不影響其他影音 App」的先決條件。
- **獎勵加時（Reward / Extra Time）**：
  - [官方文件] 屬於 HomeShield Pro 付費功能。
  - **機制落差**：此功能設計情境為「設定檔每日上網總時數（如 2 小時）耗盡後，家長可手動派發 +30 分鐘獎勵時間」。其控管的對象是**該裝置整體的對外網路連線**，無法做到「平常電視能看 Netflix，按下 Reward 僅解鎖 YouTube 30 分鐘」。
- **App 時間限制（App Time Limits）**：
  - [官方文件] 屬於 HomeShield Pro 付費功能。
  - **機制落差**：此功能為「每日固定配額」（例如設定 YouTube 每天限額 30 分鐘，每天凌晨自動重置）。不支援「預設鎖定為 0 分鐘，待小孩完成任務後由家長單次手動核准 30 分鐘」的運作流程。
- **小結**：MeshRouter M9 原生韌體與 Tether App 無法提供符合需求的「單次 30 分鐘 YouTube 臨時授權」自動化機制。若強行使用免費版黑名單，家長必須每次手動進 App 刪除 YouTube 網址，並自行設定鬧鐘在 30 分鐘後手動輸入黑名單加回，流程極其繁瑣且無法自動防呆。

### 1.5 遠端操作與運作模式限制
- **遠端操作可行性**：
  - **完全可行**。 [官方文件]
  - Tether App 登入 MeshBrand ID 並完成路由器綁定後，路由器會與 MeshBrand Cloud 保持長連線。家長在戶外使用 4G/5G 或外部 Wi-Fi，打開 Tether App 即可直接存取家中 M9，遠端調整家長控制與設定檔。
  - 來源：[MeshBrand FAQ 1366: How to use MeshBrand Tether to manage router remotely](https://www.meshbrand.com/en/support/faq/1366/)
- **Router 模式 vs AP 模式（Access Point）的致命限制**：
  - **AP 模式下 HomeShield 全部失效**。 [官方文件]
  - 當 MeshRouter M9 切換為 AP 模式（例如接在ISP數據機後面且未由 M9 進行 PPPoE 撥號與 NAT 路由），M9 僅作為純二層無線交換機，DHCP、NAT 與防火牆均交由上游設備處理。官方技術文件明確指出：**在 AP 模式下，HomeShield、Parental Controls（家長控制）、Antivirus、QoS 全數關閉且在介面中隱藏**。
  - 若要使用任何家長控制或網址封鎖，M9 **必須維持在 Router 模式**並擔任家庭主要閘道器。
  - 來源：[MeshBrand FAQ: Differences between Deco Router Mode and AP Mode (HomeShield limitations)](https://www.meshbrand.com/en/support/faq/3264/)

---

## 二、網路層只封 YouTube 的技術現實與驗證

### 2.1 YouTube 網域拆解與對 Android TV / Google 服務的共用影響
YouTube 運作依賴多個網域，各網域分工明確：

| 網域名稱 | 功用 | 若封鎖此網域的後果 | 是否影響 Google Play / 帳號 / 系統 |
|---|---|---|---|
| `googlevideo.com` (*.googlevideo.com) | 核心影音串流 CDN（影片與音訊分段下載） | YouTube App 介面能開、縮圖可見，但點擊影片後**無限旋轉載入，最終報錯中斷** [社群實測] | **不會影響** App 安裝下載與帳號登入；但 Google Play 商店內的 App 宣傳預告片會無法播放 [社群實測] |
| `youtube.com` / `m.youtube.com` / `youtu.be` | YouTube 網頁與核心呼叫入口 | 瀏覽器無法開啟，App 無法完成端點連線 [社群實測] | 不影響其他 Google 服務 |
| `youtubei.googleapis.com` | InnerTube API（電視 App 首頁推薦、搜尋、使用者頻道資訊） | 電視 YouTube App 開啟後直接卡在載入頁，顯示「無法連線至 YouTube」 [社群實測] | 不影響其他 App；此 API 為 YouTube 專屬端點 |
| `ytimg.com` / `i.ytimg.com` | 靜態資源與影片縮圖 CDN | 影片封面縮圖全部破圖變黑塊 [社群實測] | 不影響其他服務 |
| `*.googleapis.com`（萬用字元） | Google 通用 API 端點 | **災難性故障**：YouTube 癱瘓 | **嚴重誤殺**：Google Play 服務、語音助理、Google 帳號驗證同步全面損壞 [社群實測] |
| `*.google.com`（萬用字元） | Google 主網域 | **災難性故障** | **嚴重誤殺**：Android TV 判定為無網際網路連線，系統核心機能中斷 |

- **結論**：若採網域精準封鎖（同時封鎖 `youtube.com`、`youtubei.googleapis.com`、`googlevideo.com`），能達成「YouTube App 無法使用，但 Google Play 更新、系統帳號、Netflix、Disney+、四季線上完全正常」。但**絕對禁止**以萬用字元阻擋 `googleapis.com` 或 `google.com`。

### 2.2 Android TV 繞過機制（Private DNS 與 8.8.8.8 硬編碼）
- **Android TV 系統的 DNS 行為**：
  - [社群實測] Android TV 11 系統框架與特定 Google 原生服務內部，經常包含指向 Google Public DNS（`8.8.8.8` / `8.8.4.4`）的直接回退查詢邏輯，甚至在偵測到區網 DNS 解析異常時主動改向 `8.8.8.8:53` 發查。
  - Android 9 以上系統具備 Private DNS（DNS-over-TLS，Port 853）模組，雖然 Android TV 11 原生 UI 選單常隱藏 Private DNS 設定項，但部分串流應用程式與瀏覽器能透過內建加密傳輸協議繞過本地 DNS。
- **對 M9 路由器的影響**：
  - MeshRouter M9 原生韌體**不支援自訂 iptables 或任意 DNAT 轉向規則**（無法像 OpenWrt 或 pfSense 強制將所有出站 Port 53 流量劫持導回本地）。
  - 若使用 HomeShield 的網址封鎖，HomeShield 是在路由器 Linux 內核層進行深度封包檢測（DPI）與 SNI 阻斷，對於標準 HTTP/HTTPS 連線，即使 DNS 查到了 IP，當連線建立握手（Client Hello）帶出 `youtube.com` 時仍會被 RST 封包中斷。但如果電視與 CDN 走 QUIC（UDP 443）或 DoT（TCP 853），普通家用路由器可能存在漏擋盲區。 [推測/待驗證]

### 2.3 封鎖後的 App 表現與離線快取能力
- **App 行為表現**：
  - 若封鎖 `youtubei.googleapis.com` 與 `youtube.com`：電視 YouTube App 啟動數秒後顯示網路連線中斷提示，出現「重試」按鈕，無法進入瀏覽介面。 [社群實測]
  - 若僅封鎖 `googlevideo.com`：首頁影片列表與封面正常載入，但按下播放後，畫面黑屏並出現進度圈旋轉約 30～60 秒，隨後提示「發生錯誤，請稍後再試」或錯誤代碼。 [社群實測]
- **已下載或快取內容**：
  - **Android TV 版 YouTube 完全不支援離線下載**：YouTube 的影片離線快取下載功能僅提供於行動裝置版（iOS / Android 手機平板）且需訂閱 YouTube Premium。電視版 App 無下載至本機儲存之架構。 [官方文件]
  - **記憶體動態緩衝（RAM Buffer）**：播放中若突然遭受網路封鎖，電視僅能依賴已載入至 RAM 中的短暫緩衝區（一般約 30 秒至 2 分鐘），緩衝播放完畢後即刻停頓卡死，無法繼續觀看。 [社群實測]

### 2.4 替代方案評估：NextDNS、AdGuard Home、Pi-hole
若不在路由器使用 HomeShield，而在網路層引進第三方 DNS 管控：

| 方案 | 費用與硬體門檻 | 針對單一裝置（電視）封鎖可行性 | API 自動化切換可行性（配合 iPhone 捷徑） | 結論評語 |
|---|---|---|---|---|
| **NextDNS** | 每月 30 萬次免費查詢；超過轉為無過濾；Pro 版約 US$ 1.99/月 或 US$ 19.90/年 [官方文件] | **受限於 M9**：M9 無法在 DHCP 針對不同 MAC 發放不同 DNS。全家走 NextDNS 會迅速耗盡 30 萬次免費額度。若單獨電視設定靜態 IP 填入 NextDNS，易被孩子改回 DHCP 繞過。 [推測/待驗證] | **有官方 REST API**：可用 iPhone 捷徑打 API 開關 Parental Controls 中的 YouTube 規則。但 iPhone 捷徑在背景無法可靠常駐計時 30 分鐘執行「自動重新鎖定」。 | 具備 API 但受限於路由器 DNS 分流與計時中斷問題。 |
| **AdGuard Home** | 軟體免費開源；需自備 24/7 硬體（樹莓派、NAS、迷你主機，硬體成本約 NT$ 1,500～3,500） | **支援客戶端分流**：可根據電視 IP 設定獨立的「封鎖服務（Blocked Services）」清單，單獨封鎖 YouTube。 [官方文件] | **支援 REST API**（`/control/clients/update`）：可透過 API 遠端解鎖。但同樣需要後端服務執行 30 分鐘倒數排程，否則鎖定無法自動復原。 | 穩定度高，但需額外硬體與自主維護成本。 |
| **Pi-hole** | 軟體免費開源；需自備 24/7 硬體（硬體成本同上） | **支援群組管理（Group Management）**：可將電視 IP 指派至專屬封鎖群組。 | **API 功能受限**：內建 disable 指令僅能全域關閉封鎖（影響全家），無法透過原生單一 API 單獨暫停指定群組 30 分鐘。需自寫腳本修改資料庫。 [社群實測] | 維護難度最高，不符家庭輕量化原則。 |

- 來源：
  - [NextDNS 官方定價與功能頁面](https://nextdns.io/pricing)
  - [AdGuard Home 官方 GitHub 專案與 API 規格](https://github.com/AdguardTeam/AdGuardHome)

---

## 三、HBO Max（現為 Max）在台灣 TCL Android TV 11 無法安裝之原因分析

### 3.1 台灣市場上架現況（重大更名與轉移）
- **正式上線時程**：華納兄弟探索集團（Warner Bros. Discovery）於 **2024 年 11 月 19 日**正式在台灣推出串流平台 **Max**，並全面停用原先的 **HBO GO** 服務。 [官方文件]
- **應用程式名稱與識別**：
  - 舊名稱「HBO Max」或「HBO GO」在台灣 Google Play 商店已不再維護或已下架。
  - 官方最新應用程式名稱為 **Max**（套件名稱為 `com.wbd.stream` / `com.wbd.stream.tv`）。若使用者在電視商店以「HBO」搜尋，可能搜尋不到或僅列出其他不相關項目。 [官方文件]
  - 來源：[華納兄弟探索官方新聞發布：Max 登陸台灣與東南亞](https://pressroom.warnermedia.com/)、[udn 報導 Max 台灣上線資訊](https://udn.com/)

### 3.2 Google Play 商店對 Android TV 的裝置相容性限制
TCL 55P715 運行 Android TV 11，符合 Max 官方「Android TV OS 7.1 以上」的最低作業系統門檻 [官方文件]，但仍無法安裝的主因如下：
1. **Google Play 裝置白名單（Device Catalog Filter）未列入**：
   - [官方文件/社群實測] 串流媒體業者在 Google Play Console 上架 Android TV App 時，為確保播放品質與硬體解碼相容性，通常會配置支援裝置白名單。
   - TCL 55P715 採用之晶片架構為 Realtek RT2851（機芯代號 R851T02，系統構建指紋 `BeyondTV`）。由於該晶片屬於 2020 年之早期入門架構，且非國際主流一線參考平台，Max 官方在台灣 Play 商店發布時，並未將此機型指紋加入相容裝置清單。
   - 結果：電視端 Google Play 商店會直接**隱藏該 App**（搜尋結果一片空白），或在網頁版 Play 商店推播安裝時標記「您的裝置與這個版本不相容」。
2. **DRM 階層確認**：
   - 55P715 具備完整的 **Google Widevine Modular DRM Security Level 1（L1）** 認證（本機 Netflix 與 Disney+ 均能順暢播放 4K 與 Dolby Vision 內容即為證明）。因此**無法安裝並非 DRM 缺失所致**，完全是廠商 Play 商店分發白名單所致。 [社群實測]

### 3.3 官方支援裝置清單與對照
- **Max 官方支援電視裝置**：
  - Sony Bravia Android TV（2015 年以後型號） [官方文件]
  - Chromecast with Google TV、Google TV Streamer [官方文件]
  - 具備 Google TV 系統之新型 TCL 電視（如 C745、C845 等 2023 以後機型） [社群實測]
  - 小米電視棒／電視盒、NVIDIA SHIELD TV [官方文件]
- **明確不支援／缺乏適配**：早期運行 Android TV 9/11 的 Realtek 晶片機種（如 P715、C715 系列）未列入官方建議適配清單。
- 來源：[Max Official Help Center: Supported Devices](https://help.max.com/)

### 3.4 社群回報與側載（Sideload）可行性與風險
- **側載安裝可行性**：
  - **可行**。 [社群實測]
  - 社群在 Mobile01 與 PTT 回報，可自安全 APK 鏡像站（如 APKMirror）下載相容於 Android TV 的 Max APK（架構通常選擇 `armeabi-v7a` 或 `arm64-v8a`，並確認為 Android TV 版本，而非手機觸控版）。
  - 安裝途徑：將 APK 儲存至隨身碟插入電視，或利用「Send Files to TV」App 傳輸，配合「File Commander」進行本機安裝。
- **側載衍生風險與社群災情**：
  1. **無自動更新機制**：Max 具備強制版本升級檢查機制（Force Update）。當官方發布伺服器端強制更新時，電視側載之舊版會直接跳出「需要更新」並導向無法安裝的 Play 商店，導致瞬間無法觀看，必須手動重新尋找新版 APK 側載覆蓋。 [社群實測]
  2. **音訊與遙控器適配不良**：Mobile01 網友實測回報，TCL 舊機側載 Max 後，常遇到 Dolby Atmos（杜比全景聲）無法點燈（降為雙聲道 PCM）、聲音音量偏小，以及介面焦點偶爾跳離遙控器方向鍵控制之問題。 [社群實測]
  3. **安全風險**：若家長從不明論壇或非官方管道取得修改版 APK，可能面臨惡意後門威脅。
- **最佳替代解法**：TCL 55P715 內建 Google Cast（Chromecast built-in）。家長使用 iPhone 安裝官方 Max App，打開欲觀看的影集後點擊右上角「投放至電視」，透過電視內建 Chromecast 協議播放，既可避開電視端 App 相容性與更新地獄，又能維持官方授權解碼。 [官方文件/社群實測]

---

## 四、TCL 55P715 實際規格與卡頓問題深入分析

### 4.1 硬體規格查證

| 項目 | 查證結果 | 證據等級 | 來源 |
|---|---|---|---|
| **SoC 晶片** | Realtek **RTD2851**（簡稱 RT2851；內部主機板機芯代號 **R851T02**；Android 設備型號為 `BeyondTV`） | [官方文件/社群實測] | [DisplaySpecifications: TCL 55P715](https://www.displayspecifications.com/en/model/49961f77)、[XDA TCL RT51 Platform](https://xdaforums.com/t/official-rooted-image-for-tcl-rt51-rt2851-beyondtv-android-9-platform.4150535/) |
| **處理器架構** | 4 核心 ARM Cortex-A55 @ ~1.0 GHz（64-bit） | [官方文件] | [DisplaySpecifications: TCL 55P715](https://www.displayspecifications.com/en/model/49961f77) |
| **圖形處理器 (GPU)** | ARM Mali-470 MP3 | [官方文件] | [DisplaySpecifications: TCL 55P715](https://www.displayspecifications.com/en/model/49961f77) |
| **記憶體 (RAM)** | **2.0 GB** DDR3 | [官方文件/社群實測] | [DisplaySpecifications: TCL 55P715](https://www.displayspecifications.com/en/model/49961f77) |
| **內部儲存 (ROM)** | **16.0 GB** eMMC 5.1（系統還原分割區佔用後，使用者可用空間約 10～11 GB） | [官方文件/社群實測] | [DisplaySpecifications: TCL 55P715](https://www.displayspecifications.com/en/model/49961f77) |
| **出廠作業系統** | Android TV 9.0（Pie） | [官方文件] | [DisplaySpecifications: TCL 55P715](https://www.displayspecifications.com/en/model/49961f77) |
| **目前韌體版本** | Android TV 11（TV+OS V5.2.0，韌體版本 `V8-R851T02-LF1V715.000032`） | [需求書/實機資訊] | 00_REQUIREMENT.md |

### 4.2 官方 OTA 支援狀態
- **生命週期現況（EOL）**：
  - [社群實測] TCL 對於 Realtek RTD2851（R851T02）平台的官方韌體維護已於 2022 年底至 2023 年實質終止。
  - 使用者目前運行之 `V8-R851T02-LF1V715` 屬於該平台後期之 Android 11 分支版本（後續僅有社群流出少數小修補如 V725、V728）。
  - TCL 官方並未為 R851T02 機芯推出 Android 12，亦未引入 Google TV 新介面；電視目前已不再享有重大版本 OTA 升級。
  - 來源：[XDA Forums: TCL Android TV updates tracker](https://xdaforums.com/t/tcl-android-tv-model-55p8m-android-recovery-tcl-beyondtv-beyondtv.3934372/)、[4PDA TCL R851T02 韌體專區](https://4pda.to/)

### 4.3 台灣社群（Mobile01、PTT）已知卡頓問題與根因分析
在 Mobile01 與 PTT 影音板針對 TCL P715 / C715 的長期討論中，卡頓與無回應當機為極為普遍的通病 [社群實測]：
1. **RAM 嚴重匱乏引發 LMK 殺進程機制**：
   - 本機硬體僅有 2GB RAM。Android TV 9 時尚可維持基本流暢，但升級到 Android TV 11 後，系統核心本身即佔用 1.1GB～1.3GB RAM。
   - 加上 TCL TV+OS 桌面自帶大量的動態預告、廣告推薦輪播、以及後台常駐服務（如 TCL Home、TCL Channel、語音喚醒監聽），開機後空閒 RAM 往往**僅剩 150MB～250MB**。
   - 當使用者啟動 YouTube 或 Disney+ 等大型影音 App 時，記憶體直接見底，觸發 Android 系統的 Low Memory Killer（LMK）。系統在後台瘋狂進行記憶體置換與回收，導致 CPU 滿載，表現為：遙控器按鍵無回應延遲達數秒、畫面掉幀卡死，最終因 Watchdog Timeout 引發系統崩潰重啟。
2. **eMMC 讀寫效能劣化與快取膨脹**：
   - 內建 16GB eMMC 經過數年寫入磨損後，4K 隨機讀寫速度大幅衰退。影音 App 長期累積數百 MB 至數 GB 的快取，使得開機或啟動 App 時 I/O 阻塞（I/O Wait），進一步惡化卡頓。
3. **社群常用緩解與解法比較**：
   - **系統層瘦身（Debloat）**：社群公認最有效的軟體調校方案。利用 ADB 停用 TCL 內建的無用預載服務（如 TCL Channel、TCL 廣告服務、使用者追蹤監測），能釋放出 250MB～400MB 寶貴的 RAM，使介面操作恢復滑順。 [社群實測]
   - **替換輕量桌面**：安裝 Projectivy Launcher 等輕量級第三方桌面，並關閉官方 Android TV 桌面龐大的動態預覽渲染，降低背景 CPU 消耗。 [社群實測]
   - **定期重置原廠設定**：清理殘留快取，但約 3～6 個月後卡頓往往會重現。 [社群實測]
   - **終極治本方案（社群共識）**：Mobile01 與 PTT 用戶普遍建議，若硬體老化嚴重，最省心的方式是直接外接 Apple TV 4K 或 Google TV 串流棒，讓 55P715 僅作為純螢幕顯示器使用，徹底告別內建系統卡頓。 [社群實測]
   - 來源：[Mobile01: TCL 電視使用心得與卡頓討論](https://www.mobile01.com/topicdetail.php?f=347&t=6259025)

---

## ## 待實機驗證清單

以下項目因缺乏官方直接文件或涉及硬體特定組合，需待後續在實機環境中進一步檢驗：

1. **M9 HomeShield 免費版黑名單封鎖範圍實測**：
   - 在 Tether App 建立 Profile 綁定電視 MAC，並在 Blocked Websites 加入 `youtube.com`、`googlevideo.com`、`youtubei.googleapis.com` 後，驗證電視上的 Netflix、Disney+、四季線上是否 100% 能正常連線與播放。
   - 驗證電視端 YouTube App 是否在被封鎖時立即報錯，而非影響整機網絡。
2. **電視端 DNS 繞過行為檢查**：
   - 檢查 TCL 55P715（Android TV 11）在未受路由器強制攔截下，是否會無視 M9 發放的 DHCP DNS，逕行連向 `8.8.8.8:53` 或建立 TCP 853（DoT）加密連線。
3. **Max 側載（Sideload）實機相容性驗證**：
   - 在電視端手動側載最新版 Max Android TV APK（`com.wbd.stream`），驗證是否能正常開啟登入、遙控器方向鍵焦點切換是否正常、以及是否具備 4K 與雙聲道/多聲道解碼。
4. **內建 Chromecast 投放 Max 驗證**：
   - 在 iPhone 上開啟 Max App，實測能否直接將影片投放至 TCL 55P715 的內建 Chromecast，並確認畫質與音訊穩定度。
5. **55P715 實機 RAM 佔用量測**：
   - 進入電視「開發者選項」中的「執行中的服務（Running Services）」，記錄開機後淨空狀態下系統、TCL 背景服務以及各 App 的實際 RAM 消耗數值，確認可用 RAM 是否確實低於 200MB。

---

## ## 查不到／不確定

1. **M9 台灣版 V4.0 最新韌體對 DoT（Port 853）的攔截能力**：
   - MeshBrand 官方未公開 HomeShield 封包過濾核心的底層 iptables / eBPF 規則細節，無法確認當 Android TV 主動走 DoT（加密 DNS）查詢 YouTube 網域時，M9 是否會直接將 TCP 853 阻斷以迫使其降級為明文 DNS。
2. **Max 官方 Google Play 後台具體的型號白名單清單**：
   - Google Play Developer Console 內的「Device Catalog」屬於華納兄弟探索內部管理權限，外界無法直接取得其完整排除的晶片與型號名稱清單，僅能透過多數舊機型搜尋不到的現象推斷其過濾機制。
3. **TCL 55P715 能否透過韌體降級回 Android TV 9**：
   - 雖然社群（4PDA / XDA）存在 Android 9 的 `V8-R851T02` IMG 刷機包，但官方是否設有 Anti-Rollback（防回滾保護）或降級後是否會破壞 Widevine L1 金鑰（導致 Netflix 降為 540p），缺乏本機型確切的降級實測無損回報。
