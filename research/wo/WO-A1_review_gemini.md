PASS

本公開副本將實際家庭排程改為虛構範例；下列固定時間只描述演算法回歸案例。


### 審查發現清單

1. **`cfquota/lib.mjs:112, 129`**
   - **說明**：`adultWindow` 與 `adultInfo` 回傳的物件比原計畫規格多帶了 `from` 欄位（目前時段的起算秒數）。經比對，計畫第 7 條與 TERRA 挑戰裁定 9 明訂家長頁需呈現完整起訖時間（例如 `11/3（一）10:00–16:00`）；若無 `from`，前端在目前時段中將無法計算出起始時間。此增設屬合理的擴充，不影響既有欄位與 sync/state 契約相容性。
   - **嚴重度**：低（規格延伸／必要補充）

2. **`cfquota/lib.mjs:117, 261`**
   - **說明**：後端 `eventText` 輸出的星期格式為「（週X）」（例：`11/2（週一）16:00`），家長頁 JS 輸出的星期格式為「（X）」（例：`11/2（一）16:00`）。工人嚴格按 PLAN_adult 第 6 條與第 7 條的文字範例實作，兩者分開看皆合規，但若追求文案一致性可統一風格。
   - **嚴重度**：低（視覺微調）

3. **`cfquota/lib.mjs:129`**
   - **說明**：`adultInfo` 回傳之 `off_until` 直接取自 D1 `meta.adult_off_until`。時段關閉過期後，該列保留在 DB 中，因此 `state.adult.off_until` 仍會帶有過去的時間戳記。目前家長頁 JS 透過 `Date.now()/1000 < ad.off_until` 做過濾，且 `adultWindow` 亦有 `nowS < off` 判斷，功能運作正確無誤；但若未來有其他 client 端直接依賴 `off_until != null` 判斷是否處於關閉狀態，可能會產生誤解。
   - **嚴重度**：低（防禦性建議）

---

### 詳細查核結果

1. **`adultWindow` 演算法與心算驗證**：
   - **展開與時區**：使用 `TAIPEI_OFFSET = 8 * 3600`，取台北午夜為基準展開 `d = -1` 至 `3`（前 1 天至後 3 天），以 UTC 日期方法取得星期，不受執行環境本地時區干擾，範圍足以涵蓋相鄰與次段跨夜。
   - **時段合併**：`adultHM("24:00")` 換算為 1440 分鐘（86400 秒），正好接合隔日 00:00；排序後以 `iv[0] <= last[1]` 合併相鄰與重疊段，無縫接合。
   - **週末白天排除**：10:00–16:00 設定為 `days: "12345"`，週六（6）與週日（0）自然不產生日間區間。
   - **關閉規則**：以 `live = Number.isFinite(off) && nowS < off ? merged.filter(iv => iv[1] > off) : merged` 過濾，結束時間 $\le$ `off_until` 的區段被正確排除。
   - **心算驗證標準 ①～⑦**：
     - ① 平日 11:00：落於 10:00–16:00 $\rightarrow$ `active: true`，`until: 16:00`（心算符合）。
     - ② 週六 10:00：週末無日間段 $\rightarrow$ `active: false`，`next_from: 週六 22:00`，`next_until: 週日 06:00`（心算符合）。
     - ③ 週一 23:00：週一 22:00 跨夜接週二 06:00 $\rightarrow$ `active: true`，`until: 週二 06:00`，下一段為 `週二 10:00–16:00`（心算符合）。
     - ④ 週一 05:00：週日 22:00 跨夜接週一 06:00 段中 $\rightarrow$ `active: true`，`until: 週一 06:00`，下一段為 `週一 10:00`（心算符合）。
     - ⑤ 週五 22:00：週五 22:00 跨夜接週六 06:00 $\rightarrow$ `until: 週六 06:00`，週六白天無段，下一段為 `週六 22:00–週日 06:00`（心算符合）。
     - ⑥ 11:00 關閉：設定 `off_until: 16:00` $\rightarrow$ 10:00 時該段被濾除，`active: false`，下一段為 `22:00`；22:00 時 `nowS < off` 不成立，自動恢復 `active: true`（心算符合）。
     - ⑦ 08:00 按「下一段不要」：不在段內，抓 `next_until: 16:00` 設為 `off_until` $\rightarrow$ 09:00 時因該段已排除呈現 `active: false`；22:00 恢復，22:00 正常 `active: true`（心算符合）。

2. **`sync` 失敗隔離**：
   - `sync` 的資料庫寫入與 `grants` 查詢在 `adultLeft` 呼叫前即已完成。
   - `adultLeft` 與 `adultWindow` 雙重包覆 `try...catch`，遇到排程壞掉或解析例外時僅印 log 並回傳 `0`。實測 A1-⑨ 確認 `adult_sched` 損壞時 `sync` 回傳 HTTP 200 且 `adult_left_s: 0`，不影響額度與同步。

3. **`/tv/api/adult` 端點**：
   - 權限檢驗在 `handle` 中與 `/tv/api/grant` 共用 Origin 白名單檢查（`https://tv-demo.example.net`），且要求角色為 `parent`，電視端呼叫回傳 403。
   - `off` 在無當前段也無下一段時（如空排程）回傳 400（測試 A1-⑩）。
   - 冪等性：`off` 在現值等於 target 時直接 return；`resume` 在 meta 鍵不存在時直接 return；DB batch 亦搭配 `WHERE changes()>0`，確認重送請求不重複產生事件（測試 A1-⑧）。

4. **家長頁 JS**：
   - 日期時間格式化（`afmt`、`arange`）加計 8 小時偏移並使用 UTC 欄位，徹底隔離使用者瀏覽器本地時區；跨日顯示完整起訖。
   - 按鈕點擊後立即設定 `b.disabled = true`，送出中變灰且防止連續連點；完成後重新整理或 finally 恢復。
   - 全頁狀態文字與事件清單一律使用 `textContent` 與 DOM 建構（`createElement`、`replaceChildren`），無任何 `innerHTML` 漏洞。

5. **測試驗證**：
   - 檢視 `git diff`，既有測試 14 條未被放寬或改動。
   - 於 worktree 執行 `cd cfquota && node --test`，全數 26 條測試通過（pass 26, fail 0）。

---

### 建議

1. **`off_until` 過期判定下沉至 API 層**：目前 `adultInfo` 會原樣輸出過期的 `off_until`，建議可調整為 `off_until: Number.isFinite(n) && n > nowS ? Math.floor(n) : null`。如此一來，任何呼叫 `/tv/api/state` 的客戶端（包括日後的自動化腳本或 app）不必自行重複比對 `now` 與 `off_until`，API 語意更為直觀一致。
2. **週別字串格式統一**：家長頁若希望與系統紀錄文案（`eventText`）一致，可評估將家長頁的 `wk` 陣列或格式加上「週」字（例：`11/3（週一）`），不過目前兩邊各自符合計畫文字範例，維持現狀亦完全可接受。
3. **本工單品質優良，可直接放行進入部署流程。**
