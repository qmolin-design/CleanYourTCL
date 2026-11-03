# tvquota

家長頁 HTML 在 `page.mjs`（`lib.mjs` 只負責引入），樣式照 `mockups/parent-v3.html` 排法 B（+15／+30／+60 一排、歸零一整排）；加值、歸零、爸媽時段一律用頁面內 `note` 顯示結果，不用 `alert()`。

電視同步額度、家長查看狀態與加值的 Cloudflare Worker。使用 Wrangler 4.151.0。

設定：D1 binding `DB`；變數 `ACCESS_TEAM`、`ACCESS_AUD`、`OFFLINE_MIN`（預設 30）；secrets `PARENTS`（email 對應爸爸／媽媽的 JSON）、`TV_CLIENT_ID`。勿將值或 service token 放進版本庫。

WO-C3 由 Owner 登入 Cloudflare，確認既有網域與服務，建立 D1 並填入 ID，遠端執行 schema.sql；在 Zero Trust 設定 Google 登入、家長政策與電視 service token，填入 team 和 AUD，以 `wrangler secret put` 設定兩個 secrets，再部署及驗收路由、電視和兩位家長的存取。詳見計畫的 WO-C3。

只有維運者透過 D1 修改影子模式與額度上限，例如：

```sh
npx -y wrangler@4.151.0 d1 execute tvquota --remote --command "UPDATE meta SET v='true' WHERE k='enforce'"
npx -y wrangler@4.151.0 d1 execute tvquota --remote --command "UPDATE meta SET v='3600' WHERE k='cap_s'"
```

## 維運（已部署：Worker `tvquota`，route `tv-demo.example.net/tv/*`，D1 `tvquota`）

- **service token `example-tv-token` 到期日：2099-12-31**。到期前在 Zero Trust → Access → Service Auth 新建一把（或延長），值存 `~/.secrets/tvquota.cf.properties`（600），`wrangler secret put TV_CLIENT_ID` 換成新的 client_id 後 `wrangler deploy`，再照 `tvagent/README.md` 重推電視的 `config.properties`。過期的表現：電視同步全失敗 → 家長頁失聯紅字。
- **緊急開放 YouTube／Netflix**（額度 0 又斷網，Mac 在家）：
  ```sh
  adb -s 192.0.2.69:5555 shell am force-stop com.example.tvagent
  adb -s 192.0.2.69:5555 shell pm enable --user 0 com.google.android.youtube.tv
  adb -s 192.0.2.69:5555 shell pm enable --user 0 com.netflix.ninja
  ```
  App 停到下次 KICK 或電視重開。網路回來後用 `UPDATE meta SET v='false' WHERE k='enforce'` 正式關控管，再 KICK：
  `adb -s 192.0.2.69:5555 shell am broadcast -n com.example.tvagent/.KickReceiver -a com.example.tvagent.KICK`
- **查電視最近狀態**：`npx -y wrangler@4.151.0 d1 execute tvquota --remote --command "SELECT last_seen, report FROM tv"`；電視端 log：`adb -s 192.0.2.69:5555 shell run-as com.example.tvagent tail files/agent.log`。
- **回滾到 v4**：見 `research/PLAN_v5.md`「與 v4 的關係 → 回滾」（v4 移除後用 tag `tvgate-v4-final`）。
- **任何時候都不准撤銷電視的「USB 偵錯授權」**（會清掉 Mac 與 App 的 adb 金鑰）。要移除 App 只用 `adb uninstall com.example.tvagent`。

## 已知限制

- 兩個受控 App（YouTube、Netflix）只停用其中一個（mixed）時，電視回報 `yt` 為 `enabled`，事件紀錄的 lock／unlock 也不分是哪一個 App。

## 使用時間統計（分 YouTube／Netflix）

- `usage_daily(date, app, seconds)`：電視每天回報的當日前景秒數（app_ver 3 才送；app_ver 2 舊回報無此三欄，sync 照舊有效）。同日期重送取 MAX，不倒退；寫入失敗只 `console.error`，不影響 sync 回 200 與 grants。
- sync 新欄位（三個要一起出現）：`today`（yyyy-MM-dd，電視的台北日期）、`used_today_yt_s`、`used_today_nf_s`（整數，≤86400）。缺任一個或格式錯 → 400。
- `/tv/api/state` 多 `usage`：`{today, week, month}` 各 `{youtube_s, netflix_s}`，`days` 為本月 1 日到今天有資料的日子（新到舊，每項 `{date, youtube_s, netflix_s}`），`recent` 為今天往前 6 天到今天共 7 筆（舊到新，最後一筆是今天，沒資料補 0，跨月照算）。週一起算；「今天」以 Worker 收到的時間＋8 小時（台北）算，不採電視送的日期。
- 家長頁使用時間：今天／本週／本月各顯示 YouTube、Netflix、合計分鐘數（秒→分一律無條件捨去，合計＝兩秒數相加後再捨去）；下方「這 7 天」圖用 `usage.recent`（舊到新，最後一根標「今天」，Y 軸上限＝`max(60, ceil(最大日合計分/30)*30)`）；下方「今天扣額度 N 分（爸媽時段不算）」＝電視回報的 `used_today_s`。舊的「今天用了 N 分」（扣額合計）那行保留。
- 紀錄白話：單一來源為服務端 `eventText`，`/tv/api/state` 的每筆 event 帶 `text`（家長頁直接渲染，不另存對照表）：`grant`→加 N 分、`zero`→歸零、`received`→電視收到加值、`lock`→已鎖 YouTube／Netflix、`unlock`→已開放 YouTube／Netflix、`online`→恢復連線（detail）、`gap`→回報中斷 N 分、`adb-bad`→無法控制（detail）、`adb-ok`→恢復控制、`reinstall`→電視 App 重新安裝、`db-reset`→資料庫重建、`adult-off`→關閉爸媽時段到 M/D（週X）HH:MM（台北時間）、`adult-resume`→恢復爸媽時段；未知 kind 照原樣印。

## 兒童／爸媽模式（WO-M1）

- 兒童模式＝現行（看片扣額度、用完就鎖）；爸媽模式中不鎖、不扣時間（電視端沿用 `adult_left_s`），到點自動切回，排程爸媽時段照常生效。頁面「紀錄」之後、「目前登入」之前有「模式」區：兒童／爸媽切換鈕＋時長選項（1 小時／2 小時／到下一段排程開始，跨日顯示明天；送出中與展開中按鈕鎖住，慢回應跨過 refresh 也不能重送）。
- 手動時段存在 D1 `meta` 的 `adult_manual`（JSON `{until, by, at}`，Unix 秒）。`until` 非數字、`until≤now`、`until>now+86400`、JSON 解析失敗一律視為無手動時段。
- 排程存在 `meta` 的 `adult_sched`（JSON 陣列，初值見 `schema.sql`；初值是虛構範例）：`days`＝台北時間星期幾（0＝週日）、`from`／`to`＝HH:MM（`to` 可為 `24:00`）。改時間由維運者用 wrangler 改這一列。相鄰或重疊合併（22:00–24:00 接 00:00–06:00＝22:00 到隔天 06:00）；手動時段若與排程相接或重疊也合併（`off_until` 只影響排程、不影響手動）；排程解析失敗視為空、手動照常生效。
- `sync` 回應多 `adult_left_s`（現在所在時段的剩餘秒數，含手動合併，不在時段內＝0，上限 86400；電視用它做 10 分鐘倒數，見 PLAN_adult）。
- `/tv/api/state` 多 `adult`：`{active, from, until, next_from, next_until, off_until}`（Unix 秒；沒有就是 0，`off_until` 沒有就是 null）＋`manual {until, by, at}|null`（手動生效中才給）、`source 'manual'|'sched'|null`（合併生效時＝`manual`）、`choices [{key:'60'|'120'|'next', until}]`（`next`＝排除手動、套 off 後的下一段排程開始，`>now` 且 `≤now+86400` 才給）。`until` 是合併後的結束時間。
- `POST /tv/api/adult`（只限 parent，同加值的 Origin 檢查）：`{action:'on', choice:'60'|'120'|'next'}` 開爸媽模式（`next` 無效 → 400；覆寫既有手動；事件 `adult-on`）、`{action:'kid'}` 切回兒童模式（刪手動；只有排程「已開始」才同時寫 `off_until`＝該段結束，未開始的排程不动；刪除＋寫 off＋事件在同一個 batch；都沒有可關的 → 回目前狀態、不寫事件；事件 `adult-kid`）。舊 `{action:'off'}`／`{action:'resume'}` 保留（頁面已拿掉按鈕）。值真的改變才寫事件；並行兩支手機＝最後寫入者勝。
- 紀錄白話加兩種：`adult-on`→開爸媽模式到 M/D（週X）HH:MM、`adult-kid`→切回兒童模式。
