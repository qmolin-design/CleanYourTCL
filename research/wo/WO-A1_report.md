# WO-A1 施工報告（Worker＋家長頁）

本公開副本將實際家庭排程改為虛構範例；下列固定時間只描述演算法回歸案例。


分支 adult-a1，未 commit／未 push。動到的檔只有計畫允許的 4 個。

## 改了什麼

- `cfquota/lib.mjs`：新增純函式 `adultWindow(nowS, sched, offUntil)`（台北 UTC+8 固定；前 1 天到後 3 天展開→合併→套用關閉→找目前段與下一段；排程壞掉回不活躍）；`sync` 回應加 `adult_left_s`（失敗只 `console.error`、回 0）；`state` 回應加 `adult`；新 API `POST /tv/api/adult`（parent＋Origin 檢查，`off`／`resume`，依狀態冪等、值變才寫事件）；`eventText` 加 `adult-off`／`adult-resume`；家長頁狀態下方加爸媽時段行＋按鈕（日期＋星期，按下變灰）。
- `cfquota/lib.test.mjs`：加 `adultWindow` import 與 12 個新測試（下節）。
- `cfquota/schema.sql`：加 `INSERT OR IGNORE INTO meta VALUES('adult_sched', '…初值…')`。
- `cfquota/README.md`：加「爸媽時段」一節；紀錄白話補兩種新事件。

## 新增測試（對應完成標準）

- A1-① 合成排程：平日 11:00 active、until=16:00（含 from=10:00）— 純函式。
- A1-② 週六 10:00 不在時段、next=當天 22:00、next_until=週日 06:00。
- A1-③ 週一 23:00 until=週二 06:00、next=週二 10:00–16:00（跨日合併）。
- A1-④ 合成排程：週一 05:00 until=06:00、next=10:00。
- A1-⑤ 週五 22:00 until=週六 06:00、next=週六 22:00–週日 06:00。
- A1-⑥ 合成排程：11:00 關閉 → 回應 `{active:false,…,off_until=16:00}`、eventText＝`關閉爸媽時段到 11/2（週一）16:00`；22:00 恢復 active。
- A1-⑦ 08:00 按下一段不要 → off_until=16:00；09:00 不在時段；22:00 照常 active。
- A1-⑧ off 連送兩次 → `adult-off` 事件只 1 筆；resume 後 `off_until=null`，`adult-resume` 1 筆，重送 resume 不再加。
- A1-⑨ `adult_sched` 壞掉 → sync 200、`adult_left_s=0`、state 不活躍。
- A1-⑩ 非 parent 403、錯 Origin 403、錯 action／空 body 400、空排程 off 400。
- A1-⑪ 合成排程：sync 回應 `adult_left_s` 且平日 11:00＝18000（走 DB 預設排程，順帶驗 schema.sql 初值）。
- A1-家長頁：HTML 含 `id="adult"`、`id="adultButtons"`、`/tv/api/adult`（另抽出 page `<script>` 做 `node --check` 過）。

## 驗收輸出

`cd cfquota && node --test`（Node 25）：

```
ℹ tests 26
ℹ pass 26
ℹ fail 0
```

（原有 14 個全過，未改弱任一既有測試。）

## git diff --stat（worktree 根）

```
cfquota/README.md    |  11 ++++-
cfquota/lib.mjs      | 108 ++++++++++++++++++++++++++++++++++++++++++---
cfquota/lib.test.mjs | 122 ++++++++++++++++++++++++++++++++++++++++++++++++++-
cfquota/schema.sql   |   1 +
4 files changed, 234 insertions(+), 8 deletions(-)
```

## 疑慮

1. `adult` 物件比計畫多一個 `from`（目前段開始）。家長頁要顯示「11/3（一）10:00–16:00」完整起訖，沒有 `from` 畫不出來；sync/state 既有欄位不受影響。如 review 認為必須與計畫字面一致，可拿掉並改頁面只顯示結束時間。
2. 計畫 eventText 用 `（週X）`、頁面範例用 `（一）`，兩處格式我按計畫原文各做各的（事件帶週、頁面不帶）。如要統一，等 Owner 一句話。
3. `off` 的冪等用「先讀現值、相同就不寫事件」實現（比單純依賴 `changes()>0` 穩；同值 UPSERT 在 SQLite 是否計 changes 各版本行為不一）。事件仍與 meta 更新在同一 batch。
4. 關閉列過期後留在 D1（`adult_off_until` 不自動刪除），查詢一律以 `now < off_until` 判斷，到期自動恢復，殘留列無害；`resume` 才刪列。
