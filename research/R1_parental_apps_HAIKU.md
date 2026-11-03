# R1 方案 A：現成 Android TV 家長監護工具（HAIKU 研究報告）

研究日期：2026-10-29　｜　範圍：僅網路查詢，未碰實機、未執行 ADB、未登入任何設備或路由器。

**讀取狀況說明**：Google Play 原頁（play.google.com）讀取失敗，ScreenUsage 的商店資訊改以 APKPure 鏡像頁為準；YourHour 的 APKMirror 頁回 403，只能看搜尋摘要；Kidslox、Kids Place、Android Central 等僅看到搜尋摘要，未開頁。凡只有搜尋摘要或二手報導者，一律降為 [推測/待驗證]。

## 〇、結論先講

1. **目前沒有一款已查到的現成工具，同時滿足**：iPhone 遠端核准「單次 N 分鐘」、只鎖 YouTube、到期自動重鎖、防繞過。
2. **最接近的是 TVCP（TV Control for Parents，MiddlePoint）**：有 iOS 家長端、可單鍵封鎖 YouTube、可從手機手動解鎖計時鎖。但它的計時鎖是「整台電視」鎖定，違反本需求「不鎖整台電視」的範圍定義，而且沒有核准流程。付費（7 天試用後 $3.99/月或 $29.99/年，依官方頁面）。
3. **ScreenUsage（原 tvusage）**：目前查到的商店資訊只列 Android 系列裝置，未提 iPhone；沒有單次核准或加時功能的記載；每日限額與封鎖是有的。
4. **Google Family Link**：官方 FAQ 沒有列出 Android TV 支援，不能當 Android TV 11 方案。
5. **沒有任何工具找到 TCL 55P715 或 Android TV 11 的實機回報。** 所有「相容」結論都要實機確認。
6. **防繞過不能宣稱完整**：投放（Cast）、遙控器 YouTube 鍵、語音搜尋、瀏覽器、YouTube Kids、切換 Google TV 的其他 profile，都沒有查到可靠的實測資料（見第三節）。

## 一、Android TV 11 與 Google TV 的區分（先講清楚）

- 兒童設定檔（Kid profile）、Restricted profile、Google TV 上的 YouTube 監護、Family Link 在電視端的加時功能，**多數只在 Google TV 才有**。
- TCL 官方 FAQ 頁面寫的是「TCL Google TV」機型（https://www.tcl.com/mk/mk/support-tv/faq/28606）；本機 55P715 是 Android TV 11（非 Google TV），**不能直接套用**該頁。 [官方文件（他機型）]
- 本機是否真的完全沒有 Google TV 的 profile 功能，**待實機確認**（查看設定中是否有「家長控制」與「使用者設定檔」）。 [推測/待驗證]

## 二、ScreenUsage（原 tvusage）逐項 a–g

**來源**：APKPure 鏡像頁 https://apkpure.net/tvusage-parental-control-app/in.codeseed.tvusage （v10.1.1，更新日 2026-10-27）與 AppGoblin https://appgoblin.info/apps/in.codeseed.tvusage 。Play 原頁未讀到。開發者為 Codeseed，套件名 in.codeseed.tvusage。
名稱確認：目前列表的名稱為「ScreenUsage (formerly tvusage)」，和需求書所指的是同一個 App。 [官方文件（鏡像轉述）]

| 項目 | 結果 | 證據等級 |
|---|---|---|
| a. 單次核准 N 分鐘／bonus 加時／15·30·60 分鐘 | 頁面只列每日 App 限額、允許時段、App 群組、Watch breaks、封鎖、無限時間（教育 App）。**未提「單次核准」、加時、bonus。** | [官方文件（鏡像轉述）]：未提及。結論：不符合，未查到。 |
| b. iPhone 家長端／遠端 | 頁面描述平台為「supervised Google/Android TV, Android phone, and Android tablet」，**未提 iOS**。遠端功能為付費「ScreenUsage Remote access」，細節未寫。 | [官方文件（鏡像轉述）]：未提 iOS。遠端：[推測/待驗證]。 |
| c. 只鎖 YouTube，其他 App 不受影響 | 可對單一 App 設限與封鎖（「Full blocking of selected apps」）。「Netflix、4gTV 不受影響」未被明文寫出。 | 單 App 封鎖：[官方文件（鏡像轉述）]；不影響其他 App：[推測/待驗證]。 |
| d. 到期是否自動關閉正在播放的 App | 頁面只說「套用你設定的限額、允許時段與封鎖」，未說明到期行為。 | 未查到。 |
| e. 重開機、強制停止、清除資料、解除安裝、切換帳號 | 權限：Accessibility 為**選用**，用於「uninstall protection」與「Override launcher（支援的 Android TV）」；未開啟時上述兩項不可用（頁面明寫）。Usage Access、Overlay 權限的必要性：2020 年 XDA 報導提到 overlay 與 usage access，但屬於當時版本與 SHIELD 測試。 | 權限描述：[官方文件（鏡像轉述）]；繞過測試：未查到。 |
| f. 收費 | 免費版「功能有限」；Cloud profiles 與 Remote access 需付費方案。價格與方案內容**未查到**。 | [官方文件（鏡像轉述）]：收費結構；價格未查到。 |
| g. Android TV 11／TCL 相容性 | 2020 年開發者在 Android TV 論壇發文（列為首發，功能：每日週統計、三日使用圖、每 App 限額）；XDA 報導（2020-04-30）寫明「只在 NVIDIA SHIELD 上測試，不保證所有 Android TV 機型可用」，並提到 Mi Box 無法顯示全螢幕 overlay、部分 Sony 缺 usage access。**無 TCL 回報。** | 報導屬於他平台測試：[推測/待驗證]。TCL 55P715：未查到。 |

來源 URL：
- https://appgoblin.info/apps/in.codeseed.tvusage （已讀）
- https://apkpure.net/tvusage-parental-control-app/in.codeseed.tvusage （已讀）
- https://alternativeto.net/software/tvusage/about （已讀，定位為「Android TV only」、freemium）
- https://forums.androidcentral.com/android-tv/992935-app-androidtv-tv-usage-digital-wellbeing-screen-time-assistant.html （已讀，2020 年首發文）
- https://sony-xperi-xda.blogspot.com/2020/04/tvusage-brings-digital-wellbeing-and.html （已讀，XDA 報導轉載）

## 三、其他候選

### 3.1 Android TV／Google TV 內建 Kid profile 與家長控制

- 內建功能：kid profile、Restricted profile、Profile Lock（切換設定檔需 PIN）、每 App 核准、每日限額 15 分鐘到 8 小時、bonus 時間由家長在電視上輸入 PIN 確認加時。 來源：https://cordcuttersnews.com/how-to-set-parental-controls-on-google-tv-android-tv/ （已讀）。 [推測/待驗證]（二手報導，文中混用 Google TV 與 Android TV，未區分）
- 手機核准：**查無證據**，文章與搜尋結果均未提供 iPhone 端核准。 [推測/待驗證]
- 結論：要 iPhone 核准必須靠第三方；內建功能只能在電視端操作，且 55P715 是否有完整 kid profile，待實機確認。

### 3.2 Google Family Link

- 官方 FAQ（https://families.google/familylink/faq/ ，已讀）：家長端 iPhone 需 iOS 11 以上；受監護的兒童裝置建議 Android 7.0 以上；**頁面完全沒有提到 Android TV 或 Google TV**；Apple 裝置上的兒童只能「部分監護」，App 管理與限時對 iOS 與網頁不生效。 [官方文件]
- Google TV 的 YouTube 監護（2022 年宣布）：搜尋摘要提到 YouTube 監護可在 Google TV 使用，且後續擴大到「符合條件的電視」主 YouTube App。來源為 Android Central、blog.google、Engadget 等的搜尋摘要，**未開頁讀取**；且沒有日期與機型清單。 [推測/待驗證]
- 結論：Family Link 在 Android TV 11 的官方支援**未查到**，不能作為方案 A 的主力。

### 3.3 TCL 內建家長控制

- TCL 官方 FAQ（機型為 TCL Google TV）：路徑 Settings → System → Parental Controls；頁面文字只寫可鎖頻道、輸入來源、實體電源鍵、麥克風、密碼；操作步驟在圖片中，**文字未見「單一 App 鎖定」或 YouTube 專屬鎖**。 [官方文件（他機型）]
- 55P715（Android TV 11）是否有相同選單、是否能鎖單一 App：**待實機確認**。 [推測/待驗證]

### 3.4 TVCP（TV Control for Parents／TVCP Guardian，MiddlePoint，西班牙，2026-03 上架）

- 官方 landing（https://tvcp-landing.pages.dev/ ，已讀）：
  - TV 端：Android TV 與 Google TV 才支援；**明確不支援** LG webOS、Samsung Tizen、Roku、Fire TV、Vizio。
  - 家長端：iOS（App Store）與 Android 都有。 [官方文件]
  - 功能：單鍵封鎖 YouTube 或任何 App（「Block YouTube, Netflix, or any app with a single tap」）；「Screen timers」到時「TV locks the moment it runs out」（**整台電視鎖定**）；4 位數 PIN；Volume cap；遠端鎖定。 [官方文件]
  - 手機可提前解除計時鎖（「unlock a timer lock early if needed」）。 [官方文件]
  - 限制：被封鎖的 App 在其他 profile（例如 Google TV Kids profile）仍可開啟；排程、計時、鎖定涵蓋所有 profile。 [官方文件]
  - 收費：7 天試用，之後 $3.99/月（1 台）、$29.99/年（1 台），每增 1 台 +$1/月，最多 5 台。價格依國家與商店而異。 [官方文件]
- AppGoblin 的 TVCP Guardian 頁（https://appgoblin.info/apps/io.middlepoint.tvcp.guard ，已讀）：只列 Android 手機版，與官方 landing 寫的 iOS 版**互相矛盾**。 [待驗證]
- 其他 AppGoblin 數據：6.4K 安裝、約 931 MAU、尚無評分（AppGoblin 估計）。 [推測/待驗證]
- 權限：Play 頁列出「存取已安裝 App 清單」；Accessibility、Device Admin、Usage Access 未見提及。 [推測/待驗證]
- **未見「核准流程」或「孩子請求加時」功能。** 單次 30 分鐘要靠家長手動設計時或手動解除封鎖，**是否能自動重新鎖定 YouTube 未驗證**。
- 關鍵衝突：TVCP 的計時鎖是整台電視，不符合本需求「只限制 YouTube」的範圍；若要用它，必須先確認「只對 YouTube 計時」是否可行。 [推測/待驗證]

### 3.5 TVGuard（hunted.space，獨立開發者）

- 產品頁（https://hunted.space/product/tvguard ，已讀）：TV 端為 Android TV（未提 Google TV）；手機與電視在同一 Wi-Fi 直連，預設不走雲端；遠端為「選用、加密、預設關閉」。 [官方文件（開發者頁）]
- 功能：每 App PIN 鎖、每日限額、就寢時段、每 App 使用統計；TV 設定以 PIN 保護；PIN 鍵盤會打亂；需 Accessibility 權限偵測前景 App。 [官方文件]
- **未見加時或核准功能**；iOS 家長端**未確認**；收費未查到。
- 狀態：自述「early stage」，Product Hunt 0 票，風險高。 [官方文件]

### 3.6 YourHour（Mindefy Labs，Android TV 版）

- 來源：APKMirror 搜尋摘要（頁面 403，未開頁）。 [推測/待驗證]
- 功能（摘要）：以配對碼連結手機與電視，手機看使用統計；可從手機「Instantly lock or restrict TV apps」；1.2.8 版新增 PIN 忘記流程，由手機家長核准重設 PIN。
- **未見單次 N 分鐘核准或加時**；iPhone 配對未查到。
- https://www.apkmirror.com/apk/mindefy-labs/yourhour-screen-time-control-android-tv/yourhour-screen-time-control-android-tv-1-2-8-yhtv-release/ （403，未讀）

### 3.7 Kidslox

- 搜尋摘要（vendor 頁與評論）：家長端跨 Android／iOS（可用 Android 管理 iPhone）；有「學習任務換時間」與「孩子請求加時」的說法；價格約 $49.99–79.99／年。 [推測/待驗證]（未開頁；**Android TV 支援未查到**）
- 若 Kidslox 能在 Android TV 11 上執行，它是最接近「iPhone 核准加時」的候選，**需試用確認**。

### 3.8 Xunison ATV

- 官方 help（https://help.xunison.com/how-to-supervise-time-from-android-tv ，已讀）：Android TV 的時間監督與限時、排程、通知；家長端為網頁儀表板；未提 iOS、遠端加時、Android TV 11 相容或價格。 [官方文件]
- 不符合需求，列為排除。

### 3.9 Kids Place

- 搜尋摘要：支援 Android 手機、平板與 Chromebook，未列 Android TV；作為 launcher，屬於整機替換，不符合「不影響其他 App」。 [推測/待驗證]
- 排除。

## 四、入口封鎖能力（逐項）

| 入口 | 查到的資料 | 證據等級 |
|---|---|---|
| YouTube App | ScreenUsage、TVCP、TVGuard、YourHour 都有「單一 App 封鎖／鎖定」的描述。封鎖後是否完全不可開啟，未實測。 | [官方文件]（功能描述）；實測效果：[推測/待驗證] |
| 遙控器 YouTube 鍵 | 未查到。推測遙控器鍵會啟動 YouTube 的 Intent，若 App 被封鎖，理論上會被擋，但未驗證。 | [推測/待驗證] |
| YouTube Kids | 未查到。它是另一個 package，需要分別封鎖；TVCP 頁面未提。 | [推測/待驗證] |
| 瀏覽器開 youtube.com | 查到的工具都是「App 層」封鎖，未見網站或 URL 封鎖功能。**高風險，視為未封鎖。** | [推測/待驗證] |
| iPhone 投放 YouTube（Chromecast built-in） | 搜尋未找到對 Android TV 投放與家長 App 關係的實測。投放由系統接收端處理，App 封鎖是否會擋住投放，**未查到**。**高風險。** | 未查到 |
| 語音搜尋開 YouTube | 未查到。 | 未查到 |
| 切換 Google TV 的其他 profile | TVCP 官方頁明寫「被封鎖的 App 在其他 profile 仍可開啟」。 | [官方文件] |

## 五、比較表（工具 × 條件）

欄位說明：Android TV 11 相容＝只看有無官方列出或實測；iPhone 家長端＝是否有 iOS 端；遠端＝不同 Wi-Fi 操作；單次 N 分鐘核准＝是否有「核准後倒數」；只鎖 YouTube＝是否能單獨封 YouTube；到期自動關閉；防繞過；費用。

| 工具 | Android TV 11 相容 | iPhone 家長端 | 遠端 | 單次 N 分鐘核准 | 只鎖 YouTube | 到期自動關閉 | 防繞過（權限） | 費用 |
|---|---|---|---|---|---|---|---|---|
| ScreenUsage（tvusage） | 列為 Android TV 支援；11 與 TCL 未驗證 [推測/待驗證] | 未提及 [官方文件] | 付費 Remote，細節未查到 [官方文件] | 未提及 [官方文件] | 單 App 封鎖 [官方文件]；不影響他 App [推測/待驗證] | 未說明 [未查到] | Accessibility 可選；uninstall 保護 [官方文件]；重開機等未查到 | 免費版＋付費方案；價格未查到 [官方文件] |
| Android TV／Google TV 內建 kid profile | 僅 Google TV 功能描述；55P715 待確認 [推測/待驗證] | 無 [未查到] | 無 [未查到] | 電視端輸入 PIN 加時 [推測/待驗證] | 逐 App 核准 [推測/待驗證] | 未查到 | Profile Lock PIN [推測/待驗證] | 免費 [推測/待驗證] |
| Google Family Link | 官方未列 Android TV [官方文件] | iOS 11+ 家長端 [官方文件] | 有網頁設定 [官方文件] | 未查到 | Google TV 監護 YouTube 的 2022 公告 [推測/待驗證] | 未查到 | 未查到 | 免費 [推測/待驗證] |
| TCL 內建家長控制 | 針對 TCL Google TV 機型 [官方文件（他機型）] | 無 [未查到] | 無 [未查到] | 未查到 | 未確認 [官方文件，文字未見] | 未查到 | 密碼保護 [官方文件] | 免費 [推測/待驗證] |
| TVCP（TV Control for Parents） | Android TV 與 Google TV [官方文件] | iOS 與 Android 家長端 [官方文件]；Guardian Play 頁僅 Android [待驗證] | 遠端鎖定與解除計時鎖 [官方文件] | 無核准流程；手機可提前解除計時 [官方文件] | 單鍵封 YouTube [官方文件]；但計時鎖為整台電視 [官方文件] | 計時到即鎖整台電視 [官方文件] | 他 profile 可繞過 [官方文件]；PIN [官方文件] | 7 天試用；$3.99/月 或 $29.99/年 [官方文件] |
| TVGuard | Android TV（未提 Google TV）[官方文件] | 未確認 [未查到] | 區網為主，遠端選用 [官方文件] | 未提及 [官方文件] | 每 App PIN 鎖 [官方文件]；只鎖 YouTube 未明說 | 未說明 [未查到] | 設定 PIN、防亂碼鍵盤、需 Accessibility [官方文件] | 未查到 |
| YourHour | Android TV 版 [推測/待驗證] | 未查到 [未查到] | 配對碼＋手機鎖定 App [推測/待驗證] | 未提及 [推測/待驗證] | 「lock or restrict TV apps」 [推測/待驗證] | 未查到 | 未查到 | 未查到 |
| Kidslox | Android TV 未確認 [推測/待驗證] | 跨 Android／iOS 家長端 [推測/待驗證] | 有 [推測/待驗證] | 學習換時間、請求加時 [推測/待驗證] | 未查到 | 未查到 | 未查到 | 約 $49.99–79.99／年 [推測/待驗證] |
| Xunison ATV | Android TV [官方文件] | 未提及 [官方文件] | 未提及 [官方文件] | 未提及 [官方文件] | 未提及 [官方文件] | 未說明 [官方文件] | 未提及 [官方文件] | 未查到 |

## 六、對本需求的初步判斷

- 方案 A 目前**沒有完整符合**的現成工具。
- 若只能選一個試用：**TVCP**（有 iOS 家長端、單鍵封 YouTube、遠端解鎖、7 天試用）。但要先驗證：(1) 能否只對 YouTube 計時、不鎖整台；(2) 計時到期是否自動重新封鎖；(3) 投放與遙控器 YouTube 鍵是否被擋；(4) 其他 profile 逃逸是否可接受。
- **Kidslox** 的「孩子請求加時」概念最貼近需求，但 Android TV 支援未確認；若 Android TV 11 不支援，就不能用。
- **ScreenUsage** 免費版可作為「每日 YouTube 限額」的對照組，但它不是單次核准，不符合 Owner 要求。
- Google 內建與 Family Link 在 Android TV 11 上不能作為可靠方案。

## 待實機驗證清單

1. 55P715 是否有「家長控制」或「使用者設定檔」選單，是否能逐 App 鎖定（先確認 TCL 內建路徑）。
2. ScreenUsage（in.codeseed.tvusage）安裝後，檢查是否有 iPhone 端或遠端 Remote 運作方式，以及付費方案價格；檢查 Accessibility 未開啟時能否封鎖 YouTube。
3. TVCP：免費試用期間，依序測試：單鍵封 YouTube 後 Netflix、四季線上是否不受影響；計時鎖是否只鎖 YouTube 或整台；計時到期是否自動重鎖；手機解除後是否可直接重開 YouTube。
4. TVCP 的 iOS 家長端是否存在（App Store 搜尋 TVCP Guardian）。
5. Kidslox 是否支援 Android TV 11 的安裝與封鎖（需 Play 商店 TV 版確認）。
6. 遙控器 YouTube 鍵、YouTube Kids、瀏覽器 youtube.com、iPhone 投放（Chromecast built-in）、語音搜尋：逐項在封鎖後測試。
7. 重開機、強制停止、清除資料、解除安裝、切換帳號：逐項測試各工具的防繞過能力，記錄是否還能開啟 YouTube。
8. 55P715 的 RAM／儲存與實際 App 相容性（與 TCL 機型差異）。
9. TVCP 與 TVGuard 所需權限（Accessibility、Usage Access、Overlay）是否會影響 Netflix、四季線上的播放。

## 查不到／不確定

- ScreenUsage 的「單次核准 N 分鐘」、bonus 加時、15／30／60 分鐘選項：**未查到**。
- ScreenUsage 到期時是否自動關閉正在播放的 App：**未查到**。
- ScreenUsage Remote 的運作方式（是否為雲端中繼、是否支援 iPhone）：**未查到**。
- ScreenUsage 的具體價格：**未查到**。
- Google Play 原頁讀取失敗，ScreenUsage 的商店資訊來自 APKPure 鏡像頁，需以 Play 原頁核對。
- Family Link 在 Android TV 11 的支援：**未查到**（官方 FAQ 未提）。
- Google TV 的 YouTube 監護（2022 公告）是否適用於 Android TV 11：**未查到**。
- TCL 55P715 或 Android TV 11 上任何工具的實機回報：**未查到**。
- 投放（Cast）、遙控器 YouTube 鍵、語音搜尋是否被 App 封鎖擋住：**未查到**。
- TVCP 的 iOS 家長端：官方 landing 寫有，AppGoblin 的 Guardian 頁只寫 Android，兩者矛盾，**未解決**。
- TVCP 是否有「家長核准加時」的非公開功能：列表與 landing 未提，**不確定**。
- Kidslox 的 Android TV 支援與價格：僅有搜尋摘要，**不確定**。
- YourHour 的 iPhone 支援與計時功能：APKMirror 頁讀取失敗，**不確定**。
- Kids Place 是否支援 Android TV：搜尋摘要未列，**不確定**（判定為不支援）。
