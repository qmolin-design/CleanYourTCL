PASS

### 審查結論與檢驗摘要

本審查為唯讀比對，未修改任何檔案、未連網、未執行部署或登入。針對規格 A～E 項進行逐行程式碼與測試對照，審查結果如下：

1. **A. 驗證**：[lib.mjs:5-37](file:///Users/example-owner/TV-wt-c1/cfquota/lib.mjs#L5-L37) 固定 `RS256` 驗證、依 `kid` 選鑰、未知 `kid` 節流重抓（每分鐘最多一次）、憑證快取 1 小時、完整比對 `aud`（支援陣列或字串）、`exp`、`iss`；身分由 `email ∈ PARENTS` 判定家長、`common_name == TV_CLIENT_ID` 判定電視；身分與路徑不符回 403；`/tv/` 以外路徑第一時間擋下回 404。
2. **B. sync**：[lib.mjs:46-55](file:///Users/example-owner/TV-wt-c1/cfquota/lib.mjs#L46-L55) 的 `validReport` 完整驗證 16 個欄位之型別、數值範圍（0～86400、負數檢查）與 64 字長度限制，不合規直接回 400 且不執行寫入；選加值三條規則（`db_id` 不符回空並記 `db-reset`、`last_seq: null` 依是否有電視紀錄分別回空或 `> acked_seq` 並記 `reinstall`、一般回 `> last_seq` 由小到大）；`acked_seq` 以 `MAX(COALESCE(...))` 確保只進不退；所有事件（`received`、`lock`/`unlock`、`adb-bad` 連 3 筆才記且只記一次、`adb-ok` 恢復、`online`、`gap`、`reinstall`、`db-reset`）皆正確判斷並寫入；每次同步以 `db.batch` 一同清理 90 天前 events；回應結構完整包含 `grants`、`max_seq`、`db_id`、`cap_s`、`enforce`。
3. **C. 家長端**：[lib.mjs:81-107](file:///Users/example-owner/TV-wt-c1/cfquota/lib.mjs#L81-L107) `/tv/api/state` 包含所有指定欄位，alerts 規則（紅：失聯且最後 screen=on、adb_bad≥3；黃：失聯但 screen 非 on、24 小時內 reinstall/db-reset、enforce=false）完整覆蓋；`/tv/api/grant` 驗證 `req_id`（1～64 字）、以 `INSERT OR IGNORE` 達成冪等且不重複計入事件、`minutes` 嚴格限制 `15|30|60`、`zero` 轉為 `set 0`、預估 `would_cap` 正確；`Origin` 僅於 `POST /tv/api/grant` 檢查；[lib.mjs:108-112](file:///Users/example-owner/TV-wt-c1/cfquota/lib.mjs#L108-L112) `GET /tv/` 為內嵌 HTML，設定 `Cache-Control: no-store`，無外部 URL、無機密外洩，UI 具備 +15/+30/+60/歸零按鈕、送出時產生 `crypto.randomUUID()` 並鎖定按鈕至完成，所有動態渲染均使用 `textContent` 避免 XSS。
4. **D. 設定與 Schema**：[schema.sql](file:///Users/example-owner/TV-wt-c1/cfquota/schema.sql) 與 PLAN 原文 6 句 DDL 完全一致；[wrangler.toml](file:///Users/example-owner/TV-wt-c1/cfquota/wrangler.toml) 使用 routes 規則而非 custom_domain、無 triggers、無 email/token/金鑰；[worker.mjs](file:///Users/example-owner/TV-wt-c1/cfquota/worker.mjs) 僅 2 行（≤ 10 行）且僅 import `./lib.mjs`。
5. **E. 測試與安全性**：[lib.test.mjs](file:///Users/example-owner/TV-wt-c1/cfquota/lib.test.mjs) 真實測試 WO-C1 清單 1～6 每一個驗證細項（非僅測 200，涵蓋各式異常碼、快取次數比對、冪等與狀態變更）；[lib.mjs](file:///Users/example-owner/TV-wt-c1/cfquota/lib.mjs) 所有 SQL 操作皆為靜態字串或 `?` bind 參數，無字串拼接注入風險。

---

### 審查項目清單

#### 1. 建議 ｜ [lib.mjs:2](file:///Users/example-owner/TV-wt-c1/cfquota/lib.mjs#L2) ｜ 規格「驗證（讀 Cf-Access-Jwt-Assertion）」
- **說明**：
  JWT 解碼函式 `decode` 目前寫法為：
  ```javascript
  const decode = s => Uint8Array.from(atob(s.replace(/-/g, '+').replace(/_/g, '/')), c => c.charCodeAt(0));
  ```
  在 Cloudflare Workers (workerd) 與 Node.js 22+ 的 `atob` 實作中已可直接容忍未補齊 `=` padding 的 base64 字串，目前測試與 dry-run 亦完全正常。
  若未來需提高防禦性以應對部分嚴格檢查 padding 的環境，可考慮補充 padding 計算 `s + '='.repeat((4 - s.length % 4) % 4)`。目前在目標環境無功能障礙，屬非阻擋性防禦建議。

#### 2. 建議 ｜ [lib.mjs:115](file:///Users/example-owner/TV-wt-c1/cfquota/lib.mjs#L115) ｜ PLAN_v5.md「架構」第 28 行
- **說明**：
  程式碼中使用 `if (!path.startsWith('/tv/')) return error(404);`。
  當請求為 `https://tv-demo.example.net/tv`（不帶結尾斜線）時，Worker 端會回應 404。
  這與 PLAN_v5.md 第 28 行「`/tv`（不帶斜線）不在範圍內，照舊歸 tc-booklist」完全相符，在此特別確認 Worker 端行為符合設計預期。
