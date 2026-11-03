PASS

### 審查結論
本次為第 2 次審查。經比對工單 [`PLAN_usage.md`](file:///Users/example-owner/TV-wt-u2/research/PLAN_usage.md) 規範與第 1 次審查報告 [`WO-U2_review_GEMINI.md`](file:///Users/example-owner/TV/research/wo/WO-U2_review_GEMINI.md)，前次指出的三項缺陷（跨月週漏算、對照表雙份違反單一來源、測試缺口）已全數修復完成。重點檢查項目與工單完成標準均嚴格符合，判定為 **PASS**。

---

### 上次 FAIL 項目覆核確認

1. **跨月週 `state.usage.week` 漏查上月紀錄已修復**：
   - [`lib.mjs` 第 85-86 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L85-L86)：改採 `queryStart = weekStart < monthStart ? weekStart : monthStart`，確保在週跨月（如 1 日為週日）時完整查出週一至週日跨越兩月的每日秒數。
   - [`lib.mjs` 第 93 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L93)：`days` 明確過濾 `d.date >= monthStart`，符合工單「本月 1 日到今天有資料的日子」之定義。
   - [`lib.mjs` 第 94 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L94)：週加總 `sum(weekStart)` 正確統計 `date >= weekStart && date <= today`，不再漏算上月天數。

2. **白話對照表已落實「單一來源（Single Source of Truth）」**：
   - 服務端 [`lib.mjs` 第 106-107 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L106-L107)：在 [`state`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L99-L113) 查詢 events 後，透過 [`eventText`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L59-L76) 直接為每筆事件附加 `text` 欄位。
   - 前端家長頁 [`lib.mjs` 第 124 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L124)：已移除頁面內重複的 `eventLabel` 函式，直接渲染 `e.text`。全系統對照表僅留服務端一份，完全符合單一來源要求。

3. **完成標準 ④ 與 ⑤ 測試缺口已補齊**：
   - [`lib.test.mjs` 第 231-246 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.test.mjs#L231-L246)：新增 `U2-④b 跨月週：week 含上月紀錄，month/days 不含` 測試，模擬 2026-11-01（週日）情境，寫入 10-26、10-28 紀錄，驗證 `week` 正確包含上月秒數（YT 600, NF 60），而 `month` 與 `days` 僅含 11-01。
   - [`lib.test.mjs` 第 264-273 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.test.mjs#L264-L273)：在 `U2-⑤` 中加入對 `(await state(c)).events` 的端到端驗證，確認 API 實際回傳的事件包含對應的白話 `text`。

---

### 重點檢查項目逐項確認

1. **既有 sync batch 隔離與降級機制**：
   - 既有 TV report 更新與過期 events 清理 batch 完全未變（[`lib.mjs` 第 45-48 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L45-L48)）。
   - `usage_daily` upsert 在既有 batch 成功之後獨立執行（[`lib.mjs` 第 49-56 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L49-L56)），包覆在 `try/catch` 內，失敗僅 `console.error`，仍正常回傳 200 與 grants（經 `U2-①b` 測試驗證）。

2. **validReport 驗證邏輯**：
   - [`lib.mjs` 第 26-32 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L26-L32)：三個新欄位皆無時視為有效（相容 app_ver 2 舊回報）；僅給 1~2 個欄位時回傳 400（經 `U2-③` 9 種異常案例驗證）。
   - `today` 嚴格比對 `^\d{4}-\d{2}-\d{2}$`，秒數檢驗 `Number.isSafeInteger(n) && n >= 0 && n <= 86400`。

3. **MAX upsert 正確性**：
   - [`lib.mjs` 第 52-53 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L52-L53)：採用 `INSERT ... ON CONFLICT(date,app) DO UPDATE SET seconds=MAX(seconds,excluded.seconds)`，較小秒數不會覆蓋較大秒數（經 `U2-②` 測試驗證）。

4. **state.usage 統計計算**：
   - 日期計算以台北時間為準（`now + 8 * 3600` 秒轉換），週以週一起算（`getUTCDay() + 6) % 7`）。
   - 跨週、跨月邊界計算嚴謹，包含月初週跨至上月的邊界查詢（`queryStart` 取較小者），`days` 僅取當月並由新到舊排序。

5. **家長頁排版與白話化對照表**：
   - 使用時間區塊（[`lib.mjs` 第 124 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L124)）：包含今天／本週／本月三列，每列顯示 YouTube、Netflix、合計（合計嚴格採用 `Math.floor((youtube_s + netflix_s) / 60)`），下方依序列出本月每日明細。原扣額累計（`used_today_s`）保留。
   - 白話化對照表（[`lib.mjs` 第 59-76 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L59-L76)）：涵蓋 `grant`（加 N 分）、`zero`、`received`、`lock`、`unlock`、`online`、`gap`（回報中斷 N 分）、`adb-bad`、`adb-ok`、`reinstall`、`db-reset` 共 11 種，未知 kind 原樣輸出，格式與 detail 解析精確無誤。

6. **測試完整性與既有測試保全**：
   - 14 項測試全數 PASS（含原有 7 項與新增的 `U2-①`、`U2-①b`、`U2-②`、`U2-③`、`U2-④`、`U2-④b`、`U2-⑤`）。
   - 既有測試未被改弱或刪除。

7. **禁止項檢查**：
   - `git diff --stat` 僅更動 [`cfquota/README.md`](file:///Users/example-owner/TV-wt-u2/cfquota/README.md)、[`cfquota/lib.mjs`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs)、[`cfquota/lib.test.mjs`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.test.mjs)、[`cfquota/schema.sql`](file:///Users/example-owner/TV-wt-u2/cfquota/schema.sql) 4 檔。
   - Access 驗證（[`identity`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L5-L37)）、grant／歸零邏輯（[`grant`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L114-L125)）、路由轉發（[`handle`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L127-L145)）、`wrangler.toml`、`worker.mjs` 均未碰觸。
