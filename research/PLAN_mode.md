# PLAN_mode：家長頁「兒童模式／爸媽模式」切換（WO-M1）

狀態：**APPROVED**（2026-11-01 21:0x，Opus；TERRA high 挑戰 8 條，裁定在檔尾；正文是裁定後版本）

## 起因（Owner 11-01）
- 18:4x：「管理的 HTML UI 要修改，做一個爸媽/兒童模式的切換 BUTTON，放在頁面的最底部，先給我看 MOCK」。
- 20:4x 看過 mock（`mockups/parent-v4-mode.html`，artifact UrQukMkCRDsN5SNvTSBU79）後：「自動切回照 mock，上方爸媽卡拿掉，開始派工」。

## 行為
- **兒童模式**＝現行（看片扣額度、用完就鎖），排程爸媽時段照常生效。
- **按「爸媽模式」**（兒童模式中）→ 原地展開時長選項：「1 小時」「2 小時」「到 HH:MM」（＝下一段排程爸媽時段的開始；跨日顯示「到明天 HH:MM」；沒有下一段或超過 24 小時不顯示）＋「取消」。選了才送出。
- **爸媽模式中**：不鎖、不扣時間（電視端沿用 `adult_left_s`，**電視 App 不改**）；到點自動切回兒童模式。手動時段若與排程時段相接或重疊，合併成一段（例：選「到 22:00」→ 接上 22:00–24:00，24:00 才切回）。
- **按「兒童模式」**（爸媽模式中）→ 立即送出（不另確認，方向是收緊）：清掉手動時段；**只有排程時段「現在已開始」**（不含手動算，`adultWindow(now, sched, off)` active）才同時關閉這一段（等同現行 `off`）。尚未開始的排程（即使與手動相接）不關，下一段照常。
- 上方爸媽時段卡（`adultButtons` 那張）**整張拿掉**；「下一段不要」「取消關閉」不再出現在頁面（API 保留不動）。國定假日：排程時段開始後按「兒童模式」即可。

## 伺服器（cfquota）
1. meta 新鍵 `adult_manual`：JSON `{"until":<Unix 秒>,"by":"<who>","at":<Unix 秒>}`；沒有或解析失敗＝無手動時段。
2. `adultWindow(nowS, sched, offUntil, manualUntil)`：第 4 參數可省略（舊呼叫行為不變）。排程區間照現行算法合併 → 套 `offUntil` 過濾（只影響排程）→ 若 `nowS < manualUntil` 加入 `[nowS, manualUntil]` 再合併一次 → 算 `cur`／`next`。排程 JSON 壞掉時手動時段仍要生效（排程視為空）。
3. `adultLeft`、`adultInfo` 帶入手動時段；`sync` 的 `adult_left_s` 自動包含（上限 86400 不變）。
4. `adult_manual` 驗證：JSON 解析失敗、`until` 非有限數字、`until ≤ now`、`until > now+86400` → 一律視為無手動時段（不延長免費時間）。
5. `state.adult` 增加：`manual: {until, by, at} | null`（只在 `now < until` 時給）、`source: 'manual' | 'sched' | null`（現在生效的是哪種；兩者合併時＝`manual`）、`choices: [{key:'60',until},{key:'120',until},{key:'next',until}]`（`next`＝排除手動後、套 off 的下一段排程開始，`> now` 且 `≤ now+86400` 才給）。既有欄位語意不變。
6. `POST /tv/api/adult` 新增兩個 action（parent 限定、Origin 檢查同現行）：
   - `{action:'on', choice:'60'|'120'|'next'}` → until 由伺服器算（`next` 無效 → 400）；寫 `adult_manual`、事件 `adult-on`（detail＝until）。已有手動時段 → 覆蓋成新的 until。
   - `{action:'kid'}` → 刪 `adult_manual`；若（不含手動）排程現在已開始 → 照 `off` 邏輯寫 `adult_off_until`＝該段結束。刪除、寫 off、事件放**同一個 `db.batch`**（原子）。事件 `adult-kid`（值真的改變才寫）。都沒有可關的 → 回目前狀態（冪等，不報錯）。並行兩支手機＝最後寫入者勝，不另做鎖（見裁定 4）。
   - 回傳同 `adultInfo`（含新欄位）。
7. `eventText`：`adult-on` →「開爸媽模式到 M/D（週X）HH:MM」；`adult-kid` →「切回兒童模式」。

## 頁面（cfquota/page.mjs）
- 刪爸媽卡（HTML、JS、`.adult*` CSS）。
- 「紀錄」之後、「目前登入」之前新增「模式」區，照 mock 的 `.mode`／`.seg`／`.dur` 樣式與文字（mock 的黃色 MOCK 狀態列不要）：
  - 兒童模式：「現在是兒童模式：看 YouTube／Netflix 會扣時間，用完就鎖。」
  - 展開選項：「切到爸媽模式多久？期間電視不鎖、不扣時間，時間到自動切回兒童模式。」＋按鈕＋若有 next：「「到 HH:MM」＝接上爸媽時段。」
  - 「到 HH:MM」一律用**合併後的 `adult.until`**（手動接上排程時是排程結束時間），手動期限只用在「由誰開啟」那行。
  - 爸媽模式（manual）：「爸媽模式中：不鎖、不扣時間，到 HH:MM 自動切回兒童模式。」（合併後 until > manual.until 時加「（接上排程時段）」）＋小字「由 {by} 在 HH:MM 開啟。按「兒童模式」立刻切回，電視約 1 分鐘內收到。」
  - 爸媽模式（sched）：「爸媽模式中（排程時段）：到 HH:MM 自動切回兒童模式。」＋小字「按「兒童模式」＝關閉這一段排程，下一段照常。」
  - 時間跨日一律加「明天」或 M/D；`by` 經 `esc()`／`textContent`。
- 送出中狀態用一個 UI 旗標管理：送出中或選項展開中，refresh **不重繪模式區**，按鈕保持 disabled（finally 恢復），慢回應跨過 refresh 也不能重送；成功後 `note`：開→「已切到爸媽模式，電視約 10 秒內開放」；切回→「已切回兒童模式，電視約 1 分鐘內收到」；失敗→「送出失敗，請重新整理確認」。refresh 時選項展開中不重繪模式區。

## 電視
不改。延遲：鎖住等待時 10 秒一次（WO-A3）→ 開爸媽模式約 10 秒內開放；看片中切回兒童模式 → 下一次 sync（≤60 秒）收到 `adult_left_s=0`，`adultEnd` 清 0，額度 0 就 END。

## WO-M1（cfquota）
動到：`cfquota/lib.mjs`、`cfquota/page.mjs`、`cfquota/lib.test.mjs`、`cfquota/README.md`。
完成標準：`cd cfquota && node --test` 全 PASS（既有測試不放寬；U4 測試①與 A1 家長頁測試裡對 `adult`／`adultButtons` 頁面元素的斷言改成新模式區 id 屬規格變更，可改；API 行為測試不可改），新增：
①`adultWindow` 手動：單獨生效；與排程相接合併（手動到 22:00＋排程 22:00–24:00 → until 24:00）；off 不影響手動；手動過期＝無；排程 JSON 壞掉手動仍生效；省略第 4 參數＝舊結果。
②`on`：60／120／next 的 until 正確；next 無效 → 400；覆蓋；事件 `adult-on`；sync `adult_left_s` 反映手動。
③`kid`：只有手動 → 刪手動、不寫 off；手動＋**尚未開始**的排程相接 → 只刪手動、不寫 off、該排程段之後照常生效；手動＋**已開始**的排程重疊 → 刪手動且 off＝排程段結束，`adult_left_s`=0；只有排程 → 寫 off；都沒有 → 冪等、不寫事件。
④`state.adult` 的 `manual`／`source`／`choices` 正確（含 next 跨日、超過 24 小時不給；合併後 `until` 是排程結束）。
④b 壞資料與授權：`adult_manual` JSON 壞、`until` 字串／負數／過期／超過 24 小時 → 無手動、`adult_left_s` 不增加；`on`／`kid` 非 parent → 403、錯 Origin → 403、`choice` 非法 → 400。
⑤頁面：`GET /tv/` 含模式區 id、不含 `adultButtons`；頁面 script `node --check` 過（沿用）。
⑥本機假資料預覽 390px 截圖：兒童、展開、爸媽 manual、爸媽 sched 四態，`scrollWidth` ≤390。
禁止：不改 Access 驗證、grant／sync 其他邏輯、路由（除 adult action 擴充）、`wrangler.toml`、`worker.mjs`、`schema.sql`、電視 App；不部署。

## 上線與驗收（Opus）
1. merge、push、`wrangler deploy`（記前一版 Version ID 供 rollback）。
2. 實機（電視沒人用；Opus 用 D1 寫 `adult_manual` 模擬＋Owner 手機實按一次）：**先確認 bal=0、電視在線 sync=ok**：鎖住時開爸媽模式 → ≤20 秒 unlock；切回兒童 → ≤70 秒 lock。另測 bal>0 時切回兒童 → 不鎖、開始扣額。排程時段中切回 → 寫 off、（bal=0）lock。
3. 測試前記錄 `adult_manual`／`adult_off_until`／bal 原值與測試寫入值；還原前核對目前值仍等於測試寫入值、events 無家長操作，才自動還原；否則停手、請 Owner 確認。

## 挑戰裁定（TERRA high，2026-11-01 21:0x；原文 `research/wo/PLAN_mode_challenge_TERRA.md`）
| # | 裁定 | 改法 |
|---|---|---|
| 1 切回時關掉未開始的排程 | **採納** | 只關「已開始」的排程段；測試③拆成相接（未開始）／重疊（已開始） |
| 2 切回＝鎖的假設 | **採納** | 驗收先確認 bal=0；另測 bal>0 不鎖、開始扣 |
| 3 合併後結束時間 | **採納** | 畫面用合併後 until，手動期限只用於「由誰開啟」；加「（接上排程時段）」 |
| 4 並行一致性 | 部分採納 | kid 的寫入放同一個 db.batch（原子）；兩支手機同時按＝最後寫入者勝，家用情境不另做鎖——駁回其餘 |
| 5 refresh 解除 disabled | **採納** | UI 旗標：送出中／展開中不重繪 |
| 6 既有測試範圍 | **採納** | 允許改 U4①與 A1 的頁面元素斷言，API 測試不可改 |
| 7 還原覆蓋家長操作 | **採納** | 記原值＋測試值，核對一致且無家長事件才還原 |
| 8 壞資料／授權測試 | **採納** | 新增④b |
