# TCL 55P715 電視瘦身與 YouTube 使用時間管理需求（Owner 原文）

## 一、設備現況

| 項目 | 資訊 |
|---|---|
| 品牌／型號 | TCL 55P715 |
| 作業系統 | Android TV 11 |
| TCL 系統 | TV+OS V5.2.0 |
| 硬體平台 | TCL-AP-RT2851-S1 |
| 韌體 | V8-R851T02-LF1V715.000032 |
| RAM／儲存 | 公開資料約 2GB／16GB，待實機確認 |
| 家用路由器 | MeshBrand MeshRouter M9 台灣版 V4.0 |
| 家庭手機 | 全部使用 iPhone |

目前電視經常操作卡頓，甚至無回應，需要重新啟動。

## 二、需求 A：Android TV 系統瘦身

目標：改善操作流暢度、降低背景資源占用，盡可能延長電視可用年限。

首頁只保留五個影音 App：YouTube、Netflix、Disney+、HBO Max（目前疑似無法安裝，需確認原因）、四季線上電視（4gTV）。
其他不必要的預載 App、廣告、推薦內容、追蹤服務與背景程式，希望盡可能移除或停用。

請研究與比較：
- CleanYourTV、tv-debloat、UAD-NG、Projectivy Launcher 等工具。
- 哪些只是更換桌面，哪些真正減少 RAM 與背景 CPU 消耗。
- Android TV 11、TCL 55P715 的實際相容性。
- 是否能安全解除安裝，或應以 ADB 停用為主。
- 如何檢查卡頓是否來自 RAM、儲存空間不足、背景服務或其他原因。

限制：優先不 Root、不刷 ROM；保留 Wi-Fi、遙控器、HDMI、Google Play、DRM、系統更新及影音播放必要元件；所有修改必須可還原；不可直接套用其他 TCL 型號的完整停用清單；不追求極限精簡，以穩定性為優先。

## 三、需求 B：YouTube 使用時間管理

核心：孩子完成學習任務後，由家長決定是否開放 YouTube 30 分鐘。
流程：平常 YouTube 鎖定 → 孩子完成任務 → 家長用 iPhone 按「開放 30 分鐘」→ 電視 YouTube 可看 → 30 分鐘到期自動關閉／鎖定 → 再次開放須家長授權。

重要條件：
- 是「家長臨時核准的單次 30 分鐘」，不是每天固定開放 30 分鐘。
- 優先 iPhone 控制，不要求 Android 手機；最好能從 iPhone 遠端操作。
- YouTube 鎖定時，媽媽仍能正常使用 Netflix、四季線上及其他影音 App。
- 孩子不能輕易透過重開機、切換帳號或重新開啟 App 繞過。
- 最好支援 15／30／60 分鐘不同授權時間。
- 若不能可靠單獨限制 YouTube，可考慮網路層管控，但不能犧牲其他 App。

## 四、技術方案
- 方案 A：現成 Android TV 家長監護工具（例如 ScreenUsage，原 tvusage）。確認 iPhone 遠端核准、單次倒數、自動重新鎖定、TCL Android TV 11 相容性。
- 方案 B：MeshBrand MeshRouter M9 原生功能。研究台灣 V4.0 韌體、HomeShield 免費與付費限制、Router／AP 模式差異。優先不訂閱，但不能影響其他影音 App。
- 方案 C：自建輕量機制：iPhone 捷徑或簡易網頁 → 家中常駐服務 → ADB 控制 YouTube。研究是否不必自行開發 Android TV App 即可完成單次授權、到期鎖定、斷線恢復與防繞過。不預設一定要自架。

## 五、成本與執行原則
優先免費、開源、免月費；優先利用現有 TCL 電視、M9、iPhone；不建大型管理系統；不先開發新 App；重視操作簡單、維護成本低、長期穩定；必須新增硬體或訂閱要列出必要性及成本。

## YouTube 鎖定範圍定義

核心：只限制 TCL 55P715 上的 YouTube 觀看功能，不鎖整台電視、不切整台電視網路、不影響其他影音 App。

必須封鎖（未授權時）：電視原生 YouTube App；遙控器 YouTube 快捷鍵；YouTube Kids（若有）；電視瀏覽器開 youtube.com；iPhone 投放 YouTube 到電視；語音搜尋或其他 App 開 YouTube；其他第三方 YouTube 播放器（封鎖或移除）。以上是期望，須逐項確認技術可行性。

不得影響：Netflix、Disney+、HBO Max、四季線上；Wi-Fi、HDMI、遙控器、系統設定；家中其他 iPhone、電腦、網路設備；不採會造成 Netflix 或四季線上斷線的整機網路封鎖。

30 分鐘授權規則：預設鎖定；iPhone 核准；從核准當下倒數；不暫停計時（切 App、關電視都繼續）；到期自動關閉正在播放的 YouTube 並恢復鎖定；不自動續期；重開機不得重置額度或解除鎖定。

共用電視：不建兒童／成人兩套帳號；鎖定針對整台電視的 YouTube；大人要看也須解鎖。

最低驗收：iPhone 能核准單次 30 分鐘；到時自動結束 YouTube；到期後無法直接重開 YouTube；Netflix、四季線上完全不受影響；重開機、網路短暫中斷後鎖定狀態正確。
防繞過：逐項測瀏覽器、投放、遙控器快捷鍵、語音搜尋、YouTube Kids；封不了的列為風險，不可宣稱完整防繞過。
優先順序：先可靠鎖 YouTube App，再處理其他入口；不得為封 YouTube 破壞其他功能。

**目前只授權研究與規劃。禁止直接執行 ADB 停用、解除安裝、修改韌體、調整路由器設定或建立開發工單。**
