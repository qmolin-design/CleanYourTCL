# R8：用 ntfy 或 Telegram 直接設定額度（第二階段研究）

研究日期：2026-10-30
作者：Haiku 5.5（研究員）
範圍：只查資料、寫整理，沒有改程式、沒有動 /Users/example-owner/TV 其他檔案。

標記說明：
- 【已證實】有官方文件或可靠來源可以直接對照。
- 【未證實】查到的資料互相矛盾，或查不到官方來源，需要實機測試。
- 【推測】根據文件的合理推論，沒有直接證據。

---

## 0. 先講結論（給老闆看的版本）

- **ntfy** 能收通知，但「按按鈕直接加額度」在 iPhone 上**還沒有確定能用**，要實機測。ntfy 也**不能**靠收到訊息來自動跑捷徑。
- **Telegram Bot** 的「按鈕 + 伺服器接收」是官方明確支援的做法，而且可以用使用者 ID 把人限制在爸媽兩人。費用免費，流量遠低於限制。
- 老闆要的是「按按鈕加額度 + 收異常通知」，Telegram 這條路目前證據比較扎實。

---

## 1. ntfy 在 iPhone 上

**1-1 iOS App 能不能收即時推播？**
- 【已證實】ntfy 官方說明：「You can use the ntfy Android App or iOS app to receive notifications directly on your phone.」iOS App 可在 App Store 下載。
  來源：https://docs.ntfy.sh/subscribe/phone/
- 【已證實】iOS 版的通知要靠 ntfy.sh 的「上游」（upstream）傳遞。官方說明：若伺服器沒設 upstream，「notifications will still eventually get to your device, but delivery can take hours.」官方文件摘要提到活躍使用時延遲約 20 到 30 分鐘。
  來源：https://docs.ntfy.sh/config/
- 【已證實】「即時」的意思是：自架伺服器會把一個「訊息編號」（X-Poll-ID）和主題網址的 SHA256 雜湊送到 ntfy.sh，手機再回自架伺服器拿內容。ntfy.sh 看不到訊息內容，只看到「New message」這四個字。
  來源：https://docs.ntfy.sh/config/

**1-2 自架伺服器時，iOS 推播要不要透過 ntfy.sh？**
- 【已證實】要。想要即時推播，自架伺服器的 `upstream-base-url` 要設成 `https://ntfy.sh`。`upstream-access-token` 只在被限流或上游有保護時才需要。
  來源：https://docs.ntfy.sh/config/
- 【已證實】官方也提到，若你自己做 iOS App，可以用接在 APNs 或 Firebase 的其他上游。這對我們不實際。
  來源：https://docs.ntfy.sh/config/

**1-3 直接用公共 ntfy.sh ＋ 私密主題／存取權杖，安全性如何？**
- 【已證實】公共 ntfy.sh 預設是「誰知道主題名稱就能讀、能寫」。所以主題名稱要夠長、夠隨機（例如 `tv-fam-7f3k9q2xw81`），這是最主要的防線。
  來源：https://docs.ntfy.sh/config/
- 【已證實】自架伺服器才能完整設定：`auth-default-access: deny-all`、帳號、ACL、存取權杖。權杖格式是 `tk_` 開頭，32 字元。
  來源：https://docs.ntfy.sh/config/
- 【已證實】官方警告：存取權杖等於整個帳號的完整權限，目前沒有「只能做某件事」的細分權杖。也警告，帳密用 HTTP Basic Auth 傳，一定要用 HTTPS。
  來源：https://docs.ntfy.sh/config/
- 【推測】如果走公共 ntfy.sh，沒有自架，就沒辦法設「只有我們能寫」的權限。主題名稱一旦外洩，別人能讀通知，也能偽造按鈕或通知。這點要靠隨機主題名稱和不放敏感資料來降低風險。
- 【推測】主題名稱本身也可能被當成「密碼」保護。不要把孩子的個資、密碼或完整操作紀錄寫在通知內容裡。

---

## 2. ntfy 的「按鈕」（actions）

**2-1 官方有哪些按鈕種類？iOS 支援嗎？**
- 【已證實】官方動作種類：`view`（開網址或 App）、`http`（送一個 HTTP 請求）、`broadcast`（只支援 Android）。
  來源：https://docs.ntfy.sh/publish/
- 【已證實】`view` 必填欄位是 `action`、`label`、`url`；`http` 預設是 **POST**（不是 GET），可設 `method`、`headers`、`body`，也可設 `clear`（請求成功才清掉通知）。
  來源：https://docs.ntfy.sh/publish/
- 【未證實】`http` 按鈕在 iOS App 能不能用。證據互相矛盾：
  - 官方文件的鏡像版本寫 `http` 在 Apple 平台支援。
  - 2025 年一個 GitHub 討論串（Home Assistant ntfy 整合，hbrennhaeuser/homeassistant_integration_ntfy PR #21）有人說 iOS 只有基本通知、按鈕不能用；另一人說 iPhone 和 iPad 可以用。
  - 我沒有在 docs.ntfy.sh 原頁找到明確的 iOS 支援表，所以無法確認。
  - **結論：要實機測。** 測法：發一則帶 `Actions: http, 測試, https://httpbin.org/post, method=POST` 的通知，看 iPhone 上能不能按、有沒有送出。

**2-2 `http` 按鈕能不能直接打 Cloudflare Worker，帶認證 header？**
- 【已證實】官方範例可以設 `headers.Authorization=Bearer ...`，格式符合 Worker 常見的 Bearer 認證。
  來源：https://docs.ntfy.sh/publish/
- 【推測】請求通常由手機 App 直接發出，不經過 ntfy 伺服器。所以 Worker 只要收到正確的 Authorization，就能處理。
- 【推測・安全風險】按鈕的 header 會跟著通知內容一起送到所有訂閱這個主題的裝置或人。**如果把長效權杖寫進按鈕，等於任何能讀這個主題的人都拿得到這把鑰匙。** 建議：
  - 不要用能做所有事的權杖。Worker 用一個只能「加額度」的專用權杖，或用有效期很短的一次性簽章網址。
  - Worker 端一定要檢查：只接受允許的動作（+15、+30、+60、歸零），不接受任意數字，並記錄是誰、何時操作。
  - 按鈕是 POST，誤觸就會送出，所以要有「確認」或撤銷機制（例如 Worker 記錄後 60 秒內可撤銷）。這是設計建議，不是官方功能。

---

## 3. ntfy 與 iPhone／Mac 捷徑（Shortcuts）

**3-1 捷徑能不能「發」ntfy 訊息或呼叫 HTTP API？**
- 【已證實】捷徑的「取得 URL 內容」（Get Contents of URL）可以選 GET、POST、PUT、PATCH。選 POST 等之後會出現「Request Body」欄位，可以放 JSON、表單或檔案。
  來源：https://support.apple.com/en-az/guide/shortcuts-mac/apd58d46713f/mac（這是 Mac 版說明，iPhone 的步驟可能略有不同）
- 【已證實】設定 header 有已知問題。有使用者反映在「取得 URL 內容」設定 `Content-Type: application/x-www-form-urlencoded` 時不能正確送出，討論串沒有官方修正。
  來源：https://discussions.apple.com/thread/251563782
- 【推測】所以捷徑可以發 ntfy 訊息（對 `https://ntfy.sh/你的主題` 送 POST，內容是文字），也能呼叫 Worker 的 API（POST 加 Authorization header）。
- 【未證實】社群部落格有人示範「捷徑執行後發 ntfy 通知給兩支手機」，但只是個人經驗，我沒有找到官方教學。
  來源（個人部落格，可信度低）：https://dev-parent.pages.dev/posts/custom-shared-notifications-shortcuts-ntfy-part-1

**3-2 ntfy 收到訊息能不能「觸發」捷徑？**
- 【未證實，查無支援】我搜尋了 ntfy 與捷徑整合的資料，沒有找到 ntfy 能主動觸發 iPhone 捷徑的功能。ntfy 官方文件也沒提到。
- 【已證實】iPhone 捷徑的「自動化」（Automation）要在 iPhone 本機建立，Windows 和 Mac 不能遠端設定。
  來源：https://www.howtogeek.com/442760/how-to-create-automations-on-an-iphone-or-ipad/
- 【未證實】iPhone 自動化的完整觸發條件清單我沒有查到官方來源，不敢斷言「收到通知」能不能當觸發條件。
- 【未證實】有第三方 App（Pushcut）提供 JSON API 與 Web 請求功能，理論上可以串起來，但需要另外付費確認，我沒有查到它與 ntfy 的直接整合說明。
  來源：https://apps.apple.com/sg/app/id1450936447

**3-3 實際可行的串法**
1. 【推測・最可行】按鈕走 ntfy（或 Telegram），按下去直接打 Worker。捷徑不必參與。
2. 【推測・備援】在爸媽手機上做一個捷徑，內容是「取得 URL 內容」POST 到 Worker 的加額度網址，並加上 Bearer 權杖。手動點一下就能用，不依賴 ntfy 的按鈕支援。
3. 【不建議】想用「收到 ntfy 通知自動跑捷徑」。目前查不到可靠做法。

---

## 4. Telegram Bot

**4-1 Inline keyboard（按鈕）＋ Cloudflare Worker 的 webhook 做法**
- 【已證實】Telegram Bot API 支援 inline keyboard，按下按鈕會送出一個 `callback_query` 更新給 bot。
  來源：https://core.telegram.org/bots/api（Update 物件的 `callback_query` 欄位）
- 【已證實】webhook 是 Telegram 主動把更新送到你設定的網址（Worker）。官方說 webhook 與 getUpdates（長輪詢）不能同時使用。
  來源：https://core.telegram.org/bots/faq
- 【已證實】`setWebhook` 可以設 `secret_token`，之後 Telegram 每次送來都會帶 `X-Telegram-Bot-Api-Secret-Token` 這個 header。Worker 要檢查這個 header，否則任何人打你的 Worker 網址都能假冒 Telegram。
  來源：https://core.telegram.org/bots/api
- 【已證實】按鈕的 `callback_data` 有大小限制，是 1 到 64 位元組（不是字元，中文字會更快超過）。
  來源（第三方鏡像，官方頁面我沒讀到原文）：https://grammy.dev/ref/types/inlinekeyboardbutton/callbackbutton
- 【推測】實作流程：爸媽按「+15」→ Telegram 送 callback_query 到 Worker → Worker 驗證 secret token → 驗證 from.id 在白名單 → 寫入 D1 額度 → 呼叫 answerCallbackQuery 讓按鈕停止轉圈 → 回覆一則「已加 15 分」。

**4-2 費用與限制**
- 【已證實】基本免費。官方：「By default, bots are able to message their users at no cost.」
  來源：https://core.telegram.org/bots/faq
- 【已證實】單一聊天不要超過每秒 1 則；可以短暫超過，但持續超過會收到 429 錯誤。群組內每分鐘不能超過 20 則。
  來源：https://core.telegram.org/bots/faq
- 【已證實】群發上限約每秒 30 則。付費群發（需要 100,000 顆 Stars 餘額與 10 萬月活）可到每秒 1000 則。對我們來說完全用不到。
  來源：https://core.telegram.org/bots/faq
- 【推測】爸媽兩人、電視每分鐘同步一次，流量遠低於上面所有限制。
- 【已證實】bot 不能主動先傳訊息給沒有先按 `/start` 的人。這是常見的錯誤來源。爸媽要先在 bot 裡按一次 /start。
  來源（非官方，但多個來源一致）：https://support.sinch.com/hc/en-us/articles/54629170811027-Is-it-possible-to-use-a-Telegram-Bot-to-send-campaign-or-regular-updates-to-Telegram-users

**4-3 如何只允許爸媽兩個帳號（user id 白名單）**
- 【已證實】每個 Telegram 收到的更新都含 `from.id`（使用者 ID），是一個不會變的數字。
  來源：https://core.telegram.org/bots/api（User 的 id 欄位）
- 【推測】做法：把爸爸、媽媽的 `from.id` 寫在 Worker 的環境變數裡。按鈕的更新和一般訊息都要檢查 from.id 是否在白名單，不在就直接忽略。
- 【推測・注意】user id 要先取得：讓爸媽各自傳一則訊息給 bot，Worker 記錄下 from.id，再寫進白名單。
- 【推測・注意】群組內的訊息要另外注意。如果未來把 bot 加進群組，要以 from.id 判斷，不能只看群組。

**4-4 iPhone 推播可靠度**
- 【未證實】Telegram 的 iOS 通知會透過 Apple 的推播服務送出。我沒有查到 Telegram 官方對延遲或送達率的正式說明。
- 【推測】一般使用者回報通常是即時的，但會受勿擾模式、省電模式與「通知摘要」設定影響。建議實測：關掉 Wi-Fi 與 App 後台，看 30 秒內能不能收到。
- 【推測・隱私】Telegram 的 bot 對話預設是雲端存取，不是端對端加密。不要把孩子的敏感資訊放進訊息。

**4-5 Cloudflare 免費額度（兩案共用）**
- 【已證實】Workers 免費方案：每天 10 萬次請求；每次請求 CPU 時間 10 毫秒；每次呼叫 50 個子請求（subrequests）；免費方案也可以用 Cron 排程（最多 5 個）。
  來源：https://developers.cloudflare.com/workers/platform/limits/
- 【已證實】D1 免費方案：每天可讀 500 萬列、每天可寫 10 萬列、儲存 5 GB（以帳號為單位計）。
  來源：https://developers.cloudflare.com/d1/platform/pricing/
- 【推測】每分鐘同步一次，一天約 1440 次；加上按鈕操作，遠低於免費額度。**注意：10 毫秒 CPU 很緊**，Telegram 或 ntfy 回呼的處理要保持輕量，不要在 Worker 裡做大量運算。

---

## 5. 兩案比較表

| 項目 | ntfy（＋ 按鈕 http 直接打 Worker） | Telegram Bot（inline keyboard＋webhook） | ntfy＋捷徑（備援） |
|---|---|---|---|
| 設定難度 | 中。要寫按鈕 header、隨機主題、可能要自架＋設 upstream | 中高。要 BotFather、webhook、secret token、白名單、callback | 低到中。在 iPhone 手動做一次捷徑 |
| 家人要裝什麼 | ntfy iOS App，並訂閱主題 | Telegram App，並先按一次 /start | 不用裝（捷徑是系統內建） |
| 能不能按按鈕加額度 | 【未證實】iOS 上 http 按鈕能否用，要實機測 | 【已證實】官方支援 inline 按鈕 | 能（手動點捷徑，不是按鈕） |
| 能不能收異常通知 | 能（ntfy 發通知） | 能（bot 主動發訊息，前提是爸媽已 /start） | 不適用（捷徑不主動收通知） |
| 和捷徑整合 | 捷徑可 POST 到 ntfy 或 Worker；ntfy 不能觸發捷徑（未找到支援） | 捷徑可直接呼叫 Telegram API 或 Worker | 本身就是捷徑 |
| 安全性 | 中低。按鈕 header 會隨通知外流；公共主題靠隨機名稱 | 中高。官方 secret token＋from.id 白名單，可精準限制人 | 中。捷徑內的權杖存在手機，要保護好 |
| 費用 | 公共 ntfy.sh 免費（【未證實】目前條款沒查）；自架要主機費用 | 免費（官方）；Cloudflare 免費額度內 | 免費（Cloudflare 免費額度內） |
| iPhone 推播 | 公共伺服器＋upstream 可即時；自架沒設 upstream 會延遲數小時 | 【未證實】依 Telegram 與 Apple 推播，要實機測 | 不適用（手動觸發） |

---

## 6. 建議

**用 Telegram Bot 當主要控制入口，因為按鈕、白名單與 webhook 驗證都是官方明確支援的功能，不必先賭 ntfy 在 iPhone 上的按鈕是否能用。**

建議的實測順序（不用寫程式就能做）：
1. 在 iPhone 上用 ntfy 測一則 `Actions: http` 通知（見 2-1），確認按鈕能不能按。如果能，ntfy 可以留作「異常通知」的低成本方案。
2. 不論結果如何，都先在 iPhone 捷徑做一個「POST 到 Worker」的手動加額度，當備援。
3. 若要收異常通知（電視失聯、App 被停用），Telegram 或 ntfy 都可以，建議先選一個，避免兩套並行。

---

## 附錄：來源清單

- ntfy 設定（upstream、access control）：https://docs.ntfy.sh/config/
- ntfy 發送與按鈕（actions）：https://docs.ntfy.sh/publish/
- ntfy 手機 App：https://docs.ntfy.sh/subscribe/phone/
- Telegram Bot API：https://core.telegram.org/bots/api
- Telegram Bots FAQ（限制、webhook、免費）：https://core.telegram.org/bots/faq
- Telegram 按鈕 callback_data（第三方）：https://grammy.dev/ref/types/inlinekeyboardbutton/callbackbutton
- Telegram 按鈕的社群討論：https://github.com/python-telegram-bot/python-telegram-bot/wiki/Arbitrary-callback_data
- Telegram 不能主動先傳訊（第三方）：https://support.sinch.com/hc/en-us/articles/54629170811027-Is-it-possible-to-use-a-Telegram-Bot-to-send-campaign-or-regular-updates-to-Telegram-users
- Cloudflare Workers 免費限制：https://developers.cloudflare.com/workers/platform/limits/
- Cloudflare D1 免費額度：https://developers.cloudflare.com/d1/platform/pricing/
- Apple 捷徑（Mac 版說明）：https://support.apple.com/en-az/guide/shortcuts-mac/apd58d46713f/mac
- Apple 社群：捷徑 POST header 問題：https://discussions.apple.com/thread/251563782
- iPhone 自動化（第三方教學）：https://www.howtogeek.com/442760/how-to-create-automations-on-an-iphone-or-ipad/
- Pushcut（第三方 App）：https://apps.apple.com/sg/app/id1450936447
- ntfy 按鈕 iOS 支援的討論（Home Assistant 整合）：https://gh.hhj520.top/hbrennhaeuser/homeassistant_integration_ntfy/pull/21（搜尋結果中的鏡像網址，未直接讀過原頁）
- 個人部落格（ntfy＋捷徑範例，可信度低）：https://dev-parent.pages.dev/posts/custom-shared-notifications-shortcuts-ntfy-part-1
