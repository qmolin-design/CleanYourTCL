**審核結論：PASS**

本審核依據 `research/PLAN_mode.md`（含 TERRA 挑戰裁定）、`research/wo/WO-M1_report.md`、`git diff` 及 `research/wo/m1_*.png` 進行全項查驗，工作區保持唯讀，無任何改檔、commit、adb 或 wrangler 執行。

---

### 十大必查項目查驗報告

| # | 必查項目 | 查驗結果 | 細節說明 |
|---|---|:---:|---|
| ① | **kid 邏輯**（只在排程已開始時寫 off、相接未開始不關、原子 batch、冪等） | **PASS** | `cfquota/lib.mjs:314-328`：`base` 呼叫未帶第 4 參數（純排程），`target = base.active ? base.until : 0`，未開始的相接時段因 `active === false` 使 `target = 0`，不寫入 `adult_off_until`；刪手動、寫 off、事件全在同一 `db.batch` 執行；無手動且 off 未變更時直接回傳目前狀態不寫事件，具備冪等性。 |
| ② | **on 邏輯**（until 伺服器算、choice 驗證、next 無效 400、覆蓋） | **PASS** | `cfquota/lib.mjs:297-313`：`until` 完全由伺服器依 60 / 120 / next 計算；非有效 choice 或 next 不在 `(now, now+86400]` 區間一律回傳 null（路由轉為 400）；寫入採 `INSERT ... ON CONFLICT(k) DO UPDATE` 覆蓋既有手動時段。 |
| ③ | **adultWindow 行為**（第 4 參數省略相容、排程 JSON 壞掉手動仍生效且不產生假排程） | **PASS** | `cfquota/lib.mjs:88-141`：`manualUntil` 省略（undefined）時 `Number.isFinite(mu)` 為 false，`final` 與既有 `live` 完全一致；排程 JSON 壞掉時 `list` 清空，不產生任何排程區間，手動時段獨立生效，`next_from` 為 0。M1-① 測試具體斷言相容性。 |
| ④ | **adult_manual 壞資料防護與上限**（壞資料視為無、不延長免費、until 上限 24h） | **PASS** | `cfquota/lib.mjs:75-84`（`parseManual`）：壞 JSON、非有限數字、`until <= now`、`until > now + 86400` 均回傳 null；`adultLeft` 取得 null 後第 4 參數傳 undefined，不延長任何時間。M1-④b 五種壞資料測試全過。 |
| ⑤ | **sync adult_left_s 整合**（包含手動、上限 86400 不變） | **PASS** | `cfquota/lib.mjs:146-152`：`adultLeft` 整合手動時段算出的 `w.until`，維持 `Math.min(Math.max(w.until - seconds, 0), 86400)` 上限 86400 秒不變。M1-② 實測 60/120 分鐘與 M1-③ 切回 0 秒皆符合預期。 |
| ⑥ | **API 權限與安全**（on／kid 非 parent 403、錯 Origin 403） | **PASS** | `cfquota/lib.mjs:336-338`：`/tv/api/adult` 統一驗證 Origin 必須為 `https://tv-demo.example.net`（否則 403），且角色必須為 `parent`（電視或非家長一律 403）。M1-④b 測試均涵蓋。 |
| ⑦ | **家長頁實作**（XSS 防護、refresh 旗標、disabled 恢復、合併 until、爸媽卡移除） | **PASS** | 1. `cfquota/page.mjs` 中所有 API 欄位（`ad.until`、`ad.manual.by`、`ad.manual.at`、`choices` 等）均通過 `esc()` 或 `textContent` 輸出。<br>2. `renderAll` 具備 `if (!ui.modeBusy && !ui.modeAsk) renderMode(...)`，送出中與展開中不被 15 秒 refresh 重繪。<br>3. 送出結束（無論成功或 catch 失敗）皆執行 `setModeDisabled(false)` 恢復按鈕（見下方審查註記）。<br>4. 模式卡 `到 HH:MM` 一律採用合併後的 `ad.until`，並在接上排程時顯示「（接上排程時段）」。<br>5. 舊爸媽卡 DOM、CSS（`.adult*`）、事件監聽與格式化函式均已徹底移除。 |
| ⑧ | **既有測試修改範圍審查**（頁面斷言與 A1-⑥ deepEqual） | **PASS** | 見下方「關於既有測試 A1-⑥ 之判定」。 |
| ⑨ | **禁止修改項目**（Access 驗證、grant/sync 核心、路由、wrangler、worker、schema、電視 App） | **PASS** | `git diff --stat` 僅更動 4 個允許檔案（`cfquota/README.md`、`cfquota/lib.mjs`、`cfquota/lib.test.mjs`、`cfquota/page.mjs`），無任何禁止檔案與設定檔被動到。 |
| ⑩ | **測試驗證**（`cd cfquota && node --test`） | **PASS** | 實跑結果：`tests 35, pass 35, fail 0`，全數通過。 |

---

### 關於既有測試 A1-⑥ deepEqual 補新鍵之判定（查核點 ⑧）

* **判定結論：不等同放寬，屬於「嚴格規格同步（Strict Specification Sync）」。**
* **理由**：
  1. `assert.deepEqual` 是嚴格全欄位比對。在計畫第 5、6 條中，`state.adult` 與 `adultApi` 回傳結構依規格擴充了 `manual`、`source`、`choices` 三個欄位。
  2. 若既有測試不補這三鍵，`deepEqual` 會因回傳包含新規格欄位而失敗；補上的期望值為 `{ manual: null, source: null, choices: [...] }`，精確斷言了「關閉排程時，手動狀態為空且提供正確選項」的完整行為。
  3. 既有所有斷言欄位（`active: false, from: 0, until: 0, next_from: ..., next_until: ..., off_until: ...`）均**原封不動保留**，且未降級為部分匹配（如只測 `active`），斷言強度並未放寬。
  4. 家長頁的斷言調整（`id="adultButtons"` 改為 `id="modeBox"`）則已獲 `PLAN_mode.md` 完成標準及 TERRA 挑戰裁定第 6 條明確授權。

---

### 審核觀察與改進建議（非阻擋性，供參考）

1. **`setModeDisabled(false)` 與 `await refresh()` 的先後順序**：
   - 位置：[cfquota/page.mjs#L215-L216](file:///Users/example-owner/TV-wt-m1/cfquota/page.mjs#L215-L216)、[#L230-L231](file:///Users/example-owner/TV-wt-m1/cfquota/page.mjs#L230-L231)
   - 現況：`ui.modeBusy = false; setModeDisabled(false);` 寫在 `await refresh();` 之前。若 `refresh()` 網路請求稍慢，在 DOM 尚未被新狀態覆蓋前，按鈕已被解鎖。雖然 `doModeOn` 具備冪等性，且此寫法與原頁面 `doAdd` 一致，但若改寫為 `try { ... } finally { setModeDisabled(false); ui.modeBusy = false; }` 並包覆 `refresh()`，防重複點擊的保護期會更嚴密。
2. **視覺與標籤實測**：
   - 經檢查 `research/wo/m1_*.png` 四張截圖，版面皆無橫向捲軸（`scrollWidth <= 390`），且「同日 18:00 前不標今晚」的邏輯已確認生效（[cfquota/page.mjs#L162](file:///Users/example-owner/TV-wt-m1/cfquota/page.mjs#L162)）。
