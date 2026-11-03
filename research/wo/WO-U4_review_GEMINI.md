FAIL

### 審查發現清單

1. **`cfquota/page.mjs:70, 72, 186-187`**
   - **說明**：爸媽時段卡（`.adult`）採用 `display: flex; justify-content: space-between;` 左右並排，然而 worker 在 `page.mjs:70` 的 `.c-plain` 自行加上了 `white-space: nowrap;`（原 mock 的 `mockups/parent-v3.html:89` 並無此屬性）。當爸媽時段處於作用中或有下一段排程時，按鈕文字會拼接完整的起訖時間（虛構範例 `關閉這一段（11/2（週一）22:00–11/3（週二）06:00 照常計時）`），文字長達 31～40 字。在 16px 字級下，該單行按鈕寬度即達 450px～510px，遠超過手機版面可用寬度（`<420px`，卡片內部可用僅約 280px～300px）。這導致左側文字區塊（`.adult-t`）被嚴重擠壓變形，右側按鈕則超出卡片邊界，被外層 `.screen { overflow: clip }` 截斷，使用者無法看完整個按鈕文字與點擊邊界。
   - **嚴重度**：高

2. **`cfquota/page.mjs:145`**
   - **說明**：`esc()` 實作僅跳脫 `& < > "`：`replace(/[&<>"]/g, ...)`，未跳脫 `'`（單引號 `&#39;`）。雖然目前在 innerHTML 拼接的屬性（如 `aria-label="..."`、`class="..."`）皆使用雙引號包覆，但依安全審查規範與 OWASP 標準，HTML 實體跳脫應完整涵蓋 5 種字元（`& < > " '`），若未來屬性改用單引號或於單引號上下文中插入，將衍生 XSS 風險。
   - **嚴重度**：中

3. **`cfquota/page.mjs:290-296`**
   - **說明**：`adultButtons` 點擊處理中，進入前設定了 `b.disabled = true;`，但在 `try ... catch` 流程中缺少了 `finally { b.disabled = false; }`。原本舊版 `lib.mjs` 有明確使用 `finally { b.disabled = false; }`。新版若 `/tv/api/adult` 拋出錯誤且 `catch` 內的 `await refresh()` 亦遭遇網路失敗（例如離線或 API 抖動），該按鈕將永久停留在 disabled 狀態，使用者無法重試。
   - **嚴重度**：中

4. **`cfquota/page.mjs:273-276`**
   - **說明**：在 `doZero()` 歸零送出成功後，狀態設定為 `ui.confirm = false; ui.sending = false;`，但並未立即呼叫 `renderActs()`，而是依賴後續 `await refresh()` 內部的 `renderAll()` 來重繪按鈕區。若該次 `refresh()` 因網路微抖動提前 return，確認框將持續停留在畫面上且呈現 disabled，直到下一次 15 秒定時器觸發。
   - **嚴重度**：低

5. **`cfquota/page.mjs:28-34`**
   - **說明**：樣式未設定全域 `*, *::before, *::after { box-sizing: border-box; }`。`.screen` 具備 `width: 100%; border: 1px solid var(--c-rule);`，在預設 `content-box` 盒模型下，實際寬度為 `100% + 2px`，超出父層 `.wrap` 達 2px。雖由 `html, body { overflow-x: clip; }` 截斷未出現全頁橫向捲軸，但右邊界存在 1px 裁切不均問題。
   - **嚴重度**：低

6. **`cfquota/page.mjs:55, 115`**
   - **說明**：`.c-alert` 與 `.log li` 缺少 `overflow-wrap: anywhere;` 或 `word-break: break-word;`。若電視端回報不含空白字元的超長 adb 錯誤訊息或異常字串，在網格欄位中可能超出容器。
   - **嚴重度**：低

---

### 各項審查核對結果摘要

1. **XSS**
   - API 各欄位（`who`、`text`、`alerts.text`、`adb` 相關字串、日期格式字串）皆正確經由 `esc()` 跳脫，或透過 DOM API（`textContent`、`setAttribute`）填入。
   - 所有 innerHTML 屬性皆以雙引號封閉。
   - 缺失點如發現 2，`esc()` 缺 `'`。
2. **行為**
   - 歸零確認框才送出：符合，點選歸零原地替換為 `c-confirm`，點確認才呼叫 `doZero()`，點取消復原。
   - 送出中 disabled：加值與歸零送出期間按鈕均加上 `disabled`；爸媽時段按鈕點擊後反灰（缺失如發現 3）。
   - note 文字與 would_cap：符合，成功與失敗皆寫入 `#note`，不呼叫 `alert()`；`would_cap` 呈現「最多 N 分，會補到 N 分」。
   - refresh 時不洗掉打開中的確認框：符合，`renderAll` 內以 `if (!ui.confirm) renderActs();` 保護。
   - 爸媽時段三狀態與 `/tv/api/adult`：狀態切換、action 參數傳遞正確。
   - 失聯（≥1800 秒）判斷：符合，圖示加入 `.lost` 與斜線 `<path class="cut">`，滿 30 分鐘更新時間加 `.late`。
   - yt 三狀態：`disabled`（顯示文字＋已鎖印章）、`unknown`（無法確認）、`enabled`（開放中）均正確。
   - 無 tv：顯示「—分」、「尚無電視回報」，計算與欄位有 null 保護，無報錯崩潰。
3. **資料**
   - `usage.recent` 確保 7 筆舊→新、無資料補 0、跨月（UTC 日期運算）正確，且已通過單元測試。
   - `today`/`week`/`month`/`days` 計算與過濾語意完全不變。
   - `lib.mjs` 逐行比對 diff，除引入 `page.mjs`、`usage` 加入 `recentStart` 與 `recent` 外，其餘路由、驗證、計算邏輯一字未改。
4. **版面**
   - 結構與 CSS 完全遵循 `mockups/parent-v3.html` 排法 B（`c-acts row3`）。
   - 橫向捲動／長字串風險：發現 1（爸媽時段卡按鈕長字串＋`white-space: nowrap` 破版裁切）屬高風險缺失，需修復。
5. **測試執行**
   - `cd cfquota && node --test` 通過（29/29 pass，0 fail）。

---

### 建議

1. **修復爸媽時段卡長字串與排版（首要修復）**：
   - 移除 `page.mjs:70` 中 `.c-plain` 的 `white-space: nowrap;`（還原 mock 原版樣式）。
   - 針對 `.adult`，考量手機窄螢幕，建議改為允許換行或垂直堆疊：
     ```css
     .adult { display: flex; flex-wrap: wrap; justify-content: space-between; align-items: center; gap: 8px 12px; }
     ```
   - 精簡按鈕文案：左側文字區塊已清楚載明時段範圍（如「下一段爸媽時段：11/2（週一）22:00–...」），按鈕文案不需再重複冗長的 `arange` 字串，可直接簡化為「關閉這一段（照常計時）」或「下一段不要」，既符合直覺亦徹底消除排版溢出風險。
2. **補齊 `esc()` 跳脫涵蓋度**：
   - 將 `page.mjs:145` 修改為：
     ```javascript
     function esc(s){ return String(s).replace(/[&<>"']/g, function(c){ return {'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]; }); }
     ```
3. **健全按鈕非同步狀態重設機制**：
   - 在 `page.mjs` 的 `adultButtons` 事件監聽器中加上 `finally { b.disabled = false; }`。
   - 在 `doZero()` 的成功路徑中，將 `ui.confirm = false; ui.sending = false;` 後立即明確執行 `renderActs();`，不被動依賴 `refresh()` 的 fetch 成功回傳。
4. **加入全域盒模型保護與長字串斷行**：
   - 在 CSS 最前方加入 `*, *::before, *::after { box-sizing: border-box; }`。
   - 在 `.c-alert`、`.log li` 加入 `overflow-wrap: anywhere;`，防止未來任何外部字串在行動裝置造成邊界外溢。
