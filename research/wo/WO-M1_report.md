# WO-M1 施工報告（兒童／爸媽模式切換）

分支 `mode-m1`，工作區未 commit／未 push。計畫 `research/PLAN_mode.md`（裁定後版本）＋ mock `mockups/parent-v4-mode.html`。

## 改了什麼（每檔一兩行）

- `cfquota/lib.mjs`：`adultWindow` 加第 4 參數手動時段（排程壞掉視為空、手動照常；`off` 只過濾排程；合併後算 cur／next）；`adult_manual` 解析驗證（壞 JSON／非數字／過期／>24h 視為無）；`adultInfo` 加 `manual`／`source`／`choices`；`POST /tv/api/adult` 加 `on`（60／120／next，until 伺服器算，事件 `adult-on`）與 `kid`（刪手動＋該關就寫 off＋事件同一個 batch，冪等）；`eventText` 加 `adult-on`／`adult-kid`。
- `cfquota/page.mjs`：刪整張爸媽卡（HTML／JS／`.adult*` CSS、`adultButtons` 監聽、`afmt`／`arange`）；「紀錄」後加「模式」區，照 mock `.mode`／`.seg`／`.dur` 樣式與 V 文字；`ui.modeAsk`／`ui.modeBusy` 旗標——送出中或展開中 refresh 不重繪模式區、按鈕鎖住，成功／失敗才寫對應 note。
- `cfquota/lib.test.mjs`：A1-⑥ exact `deepEqual` 補新鍵（規格變更同步、非放寬，見疑慮）；A1 家長頁＋U4-①頁面元素斷言改成 `modeBox`（計畫允許）；新增 M1-①②③④④b⑤。
- `cfquota/README.md`：「爸媽時段」節改寫為「兒童／爸媽模式」：手動時段、`choices`、新 action、事件白話。

## kid／on 各情境走哪條路

- `on`：choice 60→`now+3600`，120→`now+7200`，next→排除手動、套 off 後的下一段排程開始（不存在／≤now／>now+86400 → 400）；寫 `adult_manual`＋事件 `adult-on`（覆蓋舊手動）。例：週一 20:00 `on next` → `until=22:00`，合併接上 22:00–24:00＋00:00–06:00 → `until` 週二 06:00、`source='manual'`。
- `kid`：一律刪 `adult_manual`；同時用「不含手動」的 `adultWindow(now, sched, off)` 看排程是否已開始→是才寫 `off_until`＝該段結束（未開始的相接段不动，下一段照常）；兩寫＋事件同一個 `db.batch`；無手動又無可關排程→回目前狀態、不寫事件（冪等）。值真的改變（刪掉有效手動或寫了 off）才寫 `adult-kid`。

## 新增測試（對應計畫編號）

- M1-①（→①）：手動單獨生效；手動 22:00 接排程 22:00–24:00 → until 隔天 06:00；off 不影響手動；過期＝無；排程壞 JSON＋手動仍生效；省第 4 參數＝舊結果。
- M1-②（→②）：on 60／120／next 的 until；sched 空時 next→400；覆蓋；事件 `adult-on`；`sync adult_left_s` 跟著手動。
- M1-③（→③）：只手動（刪、不寫 off）；手動＋未開始排程相接（只刪，22:00 照常 active）；手動＋已開始重疊（刪＋off＝段結束、`adult_left_s`=0）；只排程（寫 off）；都沒有（冪等、零事件）。
- M1-④（→④）：`manual`／`source`／`choices`（合併 until＝排程結束；週二限定排程下週一 10:00 choices 含跨日 next、08:00 超 24h 不給 next）。
- M1-④b（→④b）：五種壞 `adult_manual`→無手動、`adult_left_s`=0；on／kid 非 parent→403、錯 Origin→403、非法 choice→400。
- M1-⑤（→⑤）：`GET /tv/` 含 `modeBox`／模式／`/tv/api/adult`，不含 `adultButtons`／`id="adult"`／`adultTag`；U4-②（既有）續保 script 無反引號／`${` 且 `node --check` 過。

## node --test 輸出

`cd cfquota && node --test`：tests 35、pass 35、fail 0（含既有 29 全過：A1-①～⑪、U2 全、U4-①②③）。

## 截圖與 scrollWidth

本機假資料＋`channel="chrome"`、viewport 390x844，模式區捲入中央後截圖；`scrollWidth = max(documentElement, body)`：

- `research/wo/m1_kid.png`（兒童）：scrollWidth 390
- `research/wo/m1_ask.png`（展開選項：1 小時／2 小時／到今晚 HH:MM＋「接上爸媽時段」）：scrollWidth 390
- `research/wo/m1_adult_manual.png`（手動：到 HH:MM＋由爸爸在 HH:MM 開啟＋1 分鐘）：scrollWidth 390
- `research/wo/m1_adult_sched.png`（排程：到 24:00＋關閉這一段）：scrollWidth 390

四態皆 ≤390，無橫向溢出。

## git diff --stat

```
 cfquota/README.md    |  17 +++---
 cfquota/lib.mjs      | 110 ++++++++++++++++++++++++++++-------
 cfquota/lib.test.mjs | 158 +++++++++++++++++++++++++++++++++++++++++++++++++--
 cfquota/page.mjs     | 126 ++++++++++++++++++++++++++++++----------
 4 files changed, 346 insertions(+), 65 deletions(-)
```

## 疑慮

1. A1-⑥ 的 exact `deepEqual` 被迫加 `manual`／`source`／`choices` 三鍵，否則新欄位上線後該測試必紅。這是規格變更的斷言同步（期望值只增不減、行為斷言未動），不算放寬，但字面上碰了「API 行為測試不可改」那條——請 Owner 裁示是否接受這種處理；若不接受，替代方案只有把新欄位拆到別的 endpoint（違反計畫⑥「回傳同 adultInfo」）。
2. 跨日午夜顯示：排程 22:00–24:00 的 `until` 是隔天 00:00，頁面顯示「到 24:00」（跟 mock 一致），而不是計畫字面「一律加明天或 M/D」。其他跨日（非午夜）照計畫用「明天 HH:MM」／M/D。若要連午夜也顯示「到明天 00:00」，一行就改得掉。
3. 預覽 harness（`/tmp/m1shot.py`、`/tmp/m1_*.html`）放在 worktree 外，未污染 `git status`；`research/wo/m1_*.png` 四張是計畫要求的產出。
