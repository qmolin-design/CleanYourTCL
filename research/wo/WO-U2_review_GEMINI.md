FAIL

### 審查結論與總覽
工單 WO-U2 在既有 sync batch 隔離、`usage_daily` 失敗降級（try/catch）、欄位驗證、MAX upsert、家長頁排版與禁止項遵守上皆符合規範；但在**跨月週統計邏輯**、**白話對照表單一來源**與**測試覆蓋有效性**上有重大缺陷，因此判定為 **FAIL**。

---

### 問題清單、檔案行號與建議改法

#### 1. 跨月週時 `state.usage.week` 統計錯誤（漏查屬於本週的上月紀錄）
- **檔案行號**：[`lib.mjs` 第 80-96 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L80-L96)
- **問題說明**：
  當「本週一」落於上個月時（例如某月 1 日是週日，`weekStart` 為上月 26 日，即 `weekStart < monthStart`）：
  第 85 行 SQL 查詢為：
  ```javascript
  const rows = await all(db, 'SELECT date,app,seconds FROM usage_daily WHERE date>=? AND date<=?', monthStart, today);
  ```
  查詢起始日寫死為 `monthStart`，導致資料庫中屬於本週、但日期在上個月的紀錄完全沒有被查出。
  隨後第 93 行的 `sum(weekStart)` 僅對 `days`（只有本月紀錄）做加總，導致跨月時本週累計時間嚴重漏算（最多可能漏掉週一至週六整整 6 天的使用量）。
- **建議改法**：
  1. SQL 查詢起始日改取 `weekStart` 與 `monthStart` 兩者中較早者：
     ```javascript
     const queryStart = weekStart < monthStart ? weekStart : monthStart;
     const rows = await all(db, 'SELECT date,app,seconds FROM usage_daily WHERE date>=? AND date<=?', queryStart, today);
     ```
  2. 依工單定義「`days`＝本月 1 日到今天有資料的日子」，`days` 應過濾保留 `date >= monthStart`：
     ```javascript
     const days = [...byDate.values()]
       .filter(d => d.date >= monthStart)
       .sort((a, b) => (a.date < b.date ? 1 : a.date > b.date ? -1 : 0));
     ```
  3. 週累計 `sum(weekStart)` 應統計 `byDate` 中所有 `date >= weekStart && date <= today` 的資料（不限於本月）。

---

#### 2. 白話對照表在伺服器端與前端重複定義，違反「單一來源（Single Source of Truth）」
- **檔案行號**：[`lib.mjs` 第 97-114 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L97-L114)（[`eventText`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L97-L114)）與 [`lib.mjs` 第 166-167 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L166-L167)（家長頁內的 `eventLabel`）
- **問題說明**：
  伺服器端導出了 [`eventText`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L97-L114)，但在 [`state`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L136-L151) 與生產流程中完全沒有調用它；而家長頁的 HTML 字串內部卻另外複製了一份幾乎一模一樣的 `eventLabel` 函式。
  這造成：
  1. 雙重維護：未來若新增或修改事件 kind，必須兩邊同時改動，極易不同步。
  2. 伺服器端的 [`eventText`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L97-L114) 淪為「只給單元測試跑」的死代碼，家長頁實際運行的代碼並未被單元測試直接保護。
- **建議改法**：
  在伺服器端 [`state`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L136-L151) 產出 `events` 時，直接透過 [`eventText(e.kind, e.detail)`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L97-L114) 附上白話文字欄位（例如 `text`），家長頁直接渲染 `e.text`；並移除家長頁內部的 `eventLabel` 函式，全系統只保留唯一一份對照邏輯。

---

#### 3. 完成標準 ④ 與 ⑤ 的測試不全，無法在邏輯錯誤時攔截
- **檔案行號**：[`lib.test.mjs` 第 148-180 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.test.mjs#L148-L180)（測試 U2-④）與 [`lib.test.mjs` 第 181-197 行](file:///Users/example-owner/TV-wt-u2/cfquota/lib.test.mjs#L181-L197)（測試 U2-⑤）
- **問題說明**：
  1. **測試 U2-④**：測試中的時間固定在 `c.now = 1800000000000`（即 2027-01-15）。該日位於月中，`weekStart`（1 月 11 日）晚於 `monthStart`（1 月 1 日），**完全沒有模擬「週跨入上個月」（`weekStart < monthStart`）的情境**。這導致第 85 行的重大漏查缺陷在測試執行時依然 PASS，未達到工單「造跨週、跨月資料且加總正確」的防護要求。
  2. **測試 U2-⑤**：僅測試了獨立的 [`eventText`](file:///Users/example-owner/TV-wt-u2/cfquota/lib.mjs#L97-L114)，未測試 `/tv/api/state` 回傳的事件列表中是否真正含有正確轉換後的白話文字。
- **建議改法**：
  1. 在測試 U2-④ 中增加月初跨月週測試案例（例如將 `now` 設在 2026-11-01 週日），寫入 2026-10-28 的紀錄，驗證 `u.week` 確實包含該筆秒數，而 `u.month` 與 `u.days` 不包含該筆紀錄。
  2. 在測試 U2-⑤ 中加入對 `(await state(c)).events` 的斷言，確保 API 端到端確實輸出對應的白話文字。
