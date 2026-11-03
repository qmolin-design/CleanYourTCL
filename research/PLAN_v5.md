# PLAN v5：電視 App 持額度＋Cloudflare 控管（取代 v4 的 Mac）

狀態：**APPROVED**（2026-10-30 22:30，Opus 依三張裁定表改寫成單一版本；正文即現行規格，檔尾「決策紀錄」只供追溯）。下一步：派 WO-C1、WO-C2。
依據：Owner 10-30 原話（下）、`research/PLAN_v5_spike.md`、`research/batches/tvagent/NOTE.txt`（含 TGuard、WO-G2）、`research/ref/tguard/R8_tguard_report.md`、`tvagent/` 原始碼、v4 `research/PLAN_v4_ui.md`。

## Owner 原話
- 「控管平台能放 CLOUDFLARE 嗎？因為 TV 是連網，讓 TV 每分鐘跟 CF 的特定頁面連線。」
- 「我在 CloudFlare 放的是一個額度，電視固定去存取 Cloud Flare 上面的額度。當電視的額度快用完時，就去連線 Cloudflare 的網頁，如果有新增的額度，電視的額度就重新整理；如果沒有，電視的額度用完，就把指定的 APP 停用。……這 APP 能隱藏嗎？」
- Q10 原則：「小孩若有能力解開限制，意謂他真心想要，我會再跟他開導。」→ 目標是**一般使用擋得住**，不宣稱完整防繞過。

## 已證實（實機）
- 電視 App 自連 `127.0.0.1:5555` 通過 RSA 認證，能 `pm disable-user`／`pm enable`；App 金鑰（`tvagent@tv`）已在電視「一律允許」。
- 開機自啟（冷開機後約 82～90 秒）；每分鐘 AlarmManager 鬧鐘心跳；PSS 約 12MB。
- TCL 必要設定（App 拿到 adb 後自己下；**覆蓋安裝會被重置**，每個程序啟動都要重下）：`cmd appops set <pkg> APP_AUTO_START allow`、`cmd appops set <pkg> APP_ASSOC_START allow`（沒開會被 TclAppBootManager 擋鬧鐘）、`am set-standby-bucket <pkg> active`、`dumpsys deviceidle whitelist +<pkg>`。
- TCL TGuard 記憶體管家會砍背景程序；把 tvagent 加進 `mKillWhiteList`（只在記憶體，重開就回預設）＋每跳檢查補回（WO-G2，`Whitelist.java`）後，冷開機 90 秒內補回、開 Netflix 10 分鐘被砍 0 次。名單現為 9 個（拿掉 mediashell、providers.media.module、providers.tv）；Chromecast 已 `pm disable-user`。**不停用 TGuard**。
- TCL 坑：FreezeManager 凍結閒置程序（前景服務也凍）→ 只能靠鬧鐘廣播喚醒；adbd 偶爾卡「prompt pending」要重開電視；Conscrypt 私鑰取不到 publicExponent（已修）。
- v3/v4 實測：待機時網路與 adb 仍在；播放中 force-stop＋disable-user 約 2 秒回桌面；**電視重開會把 YouTube 改回啟用**；YouTube 停用時 T 鍵、語音、投放都開不了。
- v4 `gate.py` 已用系統 UsageStats 事件算 YouTube 前景時間（TCL 有記錄）。

## 架構
電視 App（沿用套件名 `com.example.tvagent`，顯示名稱「看電視時間」）在電視上**自己持有額度、自己扣、自己停用／啟用 YouTube**；每分鐘向 Cloudflare Worker 回報並領取家長新加的額度。家長用 iPhone Safari 開 `https://tv-demo.example.net/tv/` 加值、看狀態與異常。**本期不推播**，異常只顯示在家長頁頂端。Mac 不再參與控管（只留維運 adb 與 memlog）。

```
iPhone×2 ──(Access：Google 登入)──▶ Worker tvquota（route tv-demo.example.net/tv/*）──▶ D1 tvquota
電視 App ──(Access service token，每 60 秒 POST /tv/api/sync)──┘
   └─ adb 127.0.0.1:5555：查螢幕／停用／啟用 YouTube、跳提示畫面、補 TGuard 名單
```
- **不動 tc-booklist**：`tv-demo.example.net` 已由 Worker tc-booklist 用 Custom Domain 掛著（`~/FAMILY-DOCS/english/reading/tc-site`）。v5 是獨立 Worker，用 **route** `tv-demo.example.net/tv/*`；Cloudflare 文件：同主機名稱上 route 優先於 Custom Domain，所以只有 `/tv/` 底下會進 v5，其餘照舊到 tc-booklist（C3 實測確認）。`/tv`（不帶斜線）不在範圍內，照舊歸 tc-booklist。
- **選 D1**（不選 KV／Durable Objects）：KV 免費寫入額度不夠每分鐘回報、且最終一致；DO 可行但多一層概念。D1 免費額度遠高於需求，`wrangler d1 execute` 直接查，測試用 Node 內建 `node:sqlite` 模擬、零套件。
- **不用 Cron**：不推播後，失聯由家長頁讀取時用 `last_seen` 現算；舊紀錄清理放在 sync 裡順手一句 SQL。
- 不做：帳號系統（Access 管）、多台電視、圖表、電視端對內連線埠、裝置管理員（Q10）、推播（第二期）。

## 額度規則（電視 App）
### 狀態檔 `files/state.properties`（一個檔，一次寫）
`balance_s`、`last_seq`（空＝沒同步過）、`db_id`、`used_total_s`（只增不減）、`today`（台北日期）、`used_today_s`、`q_wall`／`q_el`（上次查詢的系統時間／`elapsedRealtime`，毫秒）、`boot_count`、`yt_fg`／`scr_on`（上次查詢結束時的 YouTube 前景與螢幕狀態）、`warned`、`enforce`（最後一次從 Worker 收到的值，預設 false）、`cap_s`（最後收到的值，預設 3600）。
- 寫法：寫暫存檔 → `FileOutputStream.getFD().sync()` → rename；暫存檔寫失敗＝這跳不改狀態。

### 故障時（三種情況分開）
| 情況 | 行為 |
|---|---|
| 狀態檔不存在或壞掉 | `balance_s=0`、`last_seq` 空、`enforce=false`（偏向鎖；但 enforce 要等 Worker 下發才生效） |
| 設定檔 `files/config.properties`（網址、token）不存在 | 不同步；照扣額，`enforce` 用狀態檔裡最後收到的值 |
| adb 查詢失敗 | 這跳不動 YouTube、不跳畫面，記 `adb=<類別>`；Worker 端連 3 筆失敗在家長頁顯示紅字 |

### 每一跳（鬧鐘喚醒，正常 60 秒）
1. **先排下一個 60 秒鬧鐘**（第 7 步再依情況改短）。
2. 用量（UsageStats）：`UsageStatsManager.queryEvents(q_wall, 現在)`，用 `Quota.usage()` 算「YouTube 前景 ∩ 螢幕亮」的秒數（演算法見下）。`q_wall` 空（第一次）→ 不扣，只記下起點。
   - 換算時間檢查：`boot_count`（`Settings.Global.BOOT_COUNT`）沒變，而且「系統時間差」與「`elapsedRealtime` 差」相差 > 60 秒 → 有人改時間：記 `clock=jump`，這跳改成「現在 YouTube 在前景且螢幕亮 → 扣 `elapsedRealtime` 差；否則 0」。`boot_count` 變了（重開過）→ 照事件扣。
   - 兩跳間隔 > 5 分鐘 → 記 `gap=<秒>` 並回報（事件照樣補扣，不設單跳上限）。
3. 一次 adb shell 查現況：螢幕（`dumpsys power` 的 `mWakefulness=Awake`）、YouTube 是否停用（`pm list packages -d` 有整行 `package:com.google.android.youtube.tv`）；啟用時才查前景（`dumpsys activity activities` 的 `mResumedActivity` 含 `com.google.android.youtube.tv`）；TGuard 名單檢查補回（沿用 WO-G2）。
4. 扣額：`Quota.consume(秒)`：不扣到負數；`used_total_s`、`used_today_s` 加實扣；台北日期換日時 `used_today_s` 歸 0（以這跳的系統時間判斷，只供顯示）。
5. 動作 `Quota.decide(...)`：

   | 條件 | 動作 |
   |---|---|
   | YouTube 狀態 unknown（adb 失敗） | NONE |
   | 額度 > 0 且 YouTube 停用 | UNLOCK：`pm enable --user 0` |
   | 額度 = 0、YouTube 啟用、在前景且螢幕亮 | END：`am start -n com.example.tvagent/.NoticeActivity --es kind end` → 執行緒等 10 秒 → `am force-stop` → `pm disable-user --user 0` → 讀回 |
   | 額度 = 0、YouTube 啟用、不在前景或螢幕暗 | LOCK：`am force-stop` → `pm disable-user --user 0` → 讀回 |
   | 0 < 額度 ≤ 300、在前景且螢幕亮、`warned=false` | WARN：`am start … --es kind warn`，`warned=true` |
   | 額度 > 300 | `warned=false`（之後再跨過 300 可再預告） |
   - `enforce=false`（影子模式）：只記 `act=dry-unlock|dry-end|dry-lock|dry-warn`，不碰 YouTube、不跳畫面。
6. 存檔 → 同步（設定檔存在才做）→ 套用加值與下發值（見介面）→ 有變動就重跑第 5 步、再存檔。同步失敗記 `sync=<原因>`，不影響前面各步。
7. 改排鬧鐘 `Quota.nextAlarmMs()`：YouTube 在前景且螢幕亮時：額度 < 60 秒 → `max(額度, 5)` 秒；額度 > 300 → `min(60, 額度−300)` 秒（但不小於 5 秒）；其他 60 秒。

### `Quota.usage(events, fromWall, toWall, ytFg0, scrOn0)`（純 Java）
- 事件依時間排序、只取 `[fromWall, toWall]` 內。初始狀態用上次存的 `yt_fg`、`scr_on`。
- `ACTIVITY_RESUMED`(1)：`yt_fg = (pkg == com.google.android.youtube.tv)`；YouTube 的 `ACTIVITY_PAUSED`(2)／`ACTIVITY_STOPPED`(23)：`yt_fg=false`；`SCREEN_INTERACTIVE`(15)：`scr_on=true`；`SCREEN_NON_INTERACTIVE`(16)：`scr_on=false`；`DEVICE_SHUTDOWN`(26)：兩者 false；`DEVICE_STARTUP`(27)：`yt_fg=false`、`scr_on=true`。
- 回傳兩者同時為真的總秒數（不超過 `toWall−fromWall`）與結束狀態。
- 權限：Manifest 宣告 `android.permission.PACKAGE_USAGE_STATS`；App 用 adb 下 `cmd appops set com.example.tvagent GET_USAGE_STATS allow`（和 TCL 設定一起，每個程序啟動一次）。
- 精度：事件是系統記錄，App 被凍結、被砍、鬧鐘延遲都會在下一跳補扣；只有第 7 步的「用完即鎖」受鬧鐘時間影響（C5 實測記錄秒數）。

### 套用加值
- 依 `seq` 由小到大；`add`＝`balance = max(balance, min(balance+秒數, cap_s))`（超過封頂、回報 `capped=true`；**CAP 調小時現有額度不砍**，只擋之後的 add——Owner 21:40「暫定不砍」）；`set`＝設成該秒數（家長「歸零」＝`set 0`）。
- `last_seq` 設成**實際套用的最後一筆 seq**；沒有加值就不變。例外見下方介面（全新安裝、D1 重建）。
- CAP 預設 **3600 秒（60 分）**，Worker 從 `meta.cap_s` 下發；改數字不用改 App。

### 提示畫面 `NoticeActivity`
- 全螢幕（`@android:style/Theme.Black.NoTitleBar.Fullscreen`，程式碼建 TextView，不需 `res/`）、沒有 intent-filter、**`exported=true`**（shell 的 `am start` 不能叫起 exported=false 的 Activity；沒 intent-filter 只能用元件名叫，無害）。
- `kind=warn`：「電視時間剩 5 分鐘」，顯示 5 秒後 `finish()`，按任何鍵也關。
- `kind=end`：「電視時間已到」，攔返回鍵，12 秒後 `finish()`（服務在第 10 秒鎖 YouTube）。
- `kind` 不是這兩個值 → 立刻 `finish()`。畫面不常駐、不提供任何操作。

## Cloudflare 端
### 介面（WO-C1、WO-C2 共用，以此為準）
Worker 只處理 `/tv/` 底下的路徑，其他路徑一律 404。

**`POST /tv/api/sync`**（只接受電視 service token）請求 JSON：
```json
{"v":1,"last_seq":12,"db_id":"9f…","balance_s":1234,"capped":false,
 "used_today_s":300,"used_total_s":56789,
 "yt":"enabled|disabled|unknown","fg_yt":true,"screen":"on|off|unknown",
 "adb":"ok 或錯誤類別（connect|auth_timeout|auth_rejected|tls_required|protocol|io）",
 "enforce":false,"boot_count":12,"uptime_s":3600,"gap_s":0,"wl":"keep|set|fail|skip|unexpected","app_ver":2}
```
- `last_seq`、`db_id` 可為 `null`；`fg_yt` 可為 `null`（YouTube 停用時不查）。欄位缺、型別錯、`balance_s`／`used_today_s`／`used_total_s` 為負數或 `balance_s` > 86400（合理上限；不綁 CAP，因為 CAP 調小時不砍現有額度）、`adb`／`wl` 超過 64 字 → 400，什麼都不寫。
- 回應：`{"grants":[{"seq":13,"kind":"add","seconds":1800}],"max_seq":13,"db_id":"9f…","cap_s":3600,"enforce":false}`。
- Worker 選加值的規則：
  1. 請求 `db_id` 不是 null 且 ≠ `meta.db_id`（D1 重建過）→ `grants:[]`，記 `db-reset` 事件。
  2. 請求 `last_seq` 是 null（全新安裝或清資料）→ 有電視紀錄時回 `seq > tv.acked_seq` 的加值（家長加了但電視從沒收到的），記 `reinstall` 事件；沒有電視紀錄（第一次）→ `grants:[]`。
  3. 其他 → 回 `seq > last_seq` 的全部加值，由小到大，不設筆數上限。
- 電視收到回應：`db_id` 與自己存的不同（含自己是 null）→ 先照第 2 條套用回傳的加值（第 1 條時為空），再把 `last_seq` 設成 `max_seq`、存新 `db_id`；相同 → 照「套用加值」。`cap_s`、`enforce` 照存。
- Worker 把請求的 `last_seq` 當「電視已套用」的進度：`acked_seq = MAX(acked_seq, ?)`（只進不退）。`acked_seq` 前進記 `received` 事件；回報的 `yt` 改變記 `lock`／`unlock`；`adb` 由 ok 變連 3 筆非 ok 記 `adb-bad`、恢復記 `adb-ok`（`tv.adb_bad` 計數，單句 UPDATE）；距上次回報 ≥ `OFFLINE_MIN` 分記 `online`（detail：中斷 N 分）；`gap_s > 300` 記 `gap`。
- 每次 sync 順手 `DELETE FROM events WHERE ts < now−90 天`。

**家長端**（只接受 `PARENTS` 裡的 email）：
- `GET /tv/`：內嵌 HTML（不引用外部網址、`Cache-Control: no-store`、不含任何秘密）。
- `GET /tv/api/state` → `{"me":"爸爸","cap_s":3600,"enforce":false,"tv":{"last_seen":epoch,"balance_s":…,"capped":…,"used_today_s":…,"yt":…,"fg_yt":…,"screen":…,"adb":…,"adb_bad":n,"wl":…,"app_ver":…},"alerts":[{"level":"red|yellow","text":"…"}],"pending":[{"seq","kind","seconds","by","created_at"}],"events":[最近 30 筆]}`；`tv` 沒紀錄時為 `null`。
- `alerts`（讀取時現算，不另存）：
  - 紅：`now − last_seen ≥ OFFLINE_MIN` 分且最後回報 `screen=on` →「電視 App 已 N 分沒回報（最後螢幕開著）」；最後 `screen` 不是 on → 黃「電視 N 分前回報（之後待機或關機）」。
  - 紅：`adb_bad ≥ 3` →「電視 App 無法控制 YouTube（adb：類別）」。
  - 黃：24 小時內有 `reinstall`／`db-reset` 事件。
  - 黃：`enforce=false` →「影子模式：此頁不控制電視」。
- `POST /tv/api/grant`：`{"kind":"add","minutes":15|30|60,"req_id":"<uuid>"}` 或 `{"kind":"zero","req_id":"…"}` → `{"seq":N,"would_cap":bool}`（`would_cap`＝電視最後回報額度＋待接收 add 超過 CAP 的預估）。`req_id` 重複 → 回原本那筆的 seq、不新增。其他值、非 JSON、`req_id` 缺或 > 64 字 → 400。
- 家長端的 POST（`/tv/api/grant`）檢查 `Origin` 等於 `https://tv-demo.example.net`，否則 403。電視的 `/tv/api/sync` **不檢查 Origin**（電視不送這個 header；身分靠 service token）。

**驗證**（每個請求都做，`lib.mjs` 一個函式）：讀 `Cf-Access-Jwt-Assertion`；header 必須 `alg=RS256`，依 `kid` 從 `https://<ACCESS_TEAM>.cloudflareaccess.com/cdn-cgi/access/certs` 取公鑰（快取 1 小時；遇到未知 `kid` 重抓，每分鐘最多一次）；驗簽章、`aud` 含 `ACCESS_AUD`、`exp` 未過、`iss` 等於 `https://<ACCESS_TEAM>.cloudflareaccess.com`。沒有或驗不過 → 401。claims 有 `email` 且在 `PARENTS` → 家長；`common_name` 等於 `TV_CLIENT_ID` → 電視；其他或路徑身分不符 → 403。

### D1 表（`cfquota/schema.sql`）
```sql
CREATE TABLE IF NOT EXISTS grants(seq INTEGER PRIMARY KEY AUTOINCREMENT, kind TEXT NOT NULL CHECK(kind IN ('add','set')), seconds INTEGER NOT NULL CHECK(seconds BETWEEN 0 AND 86400), by TEXT NOT NULL, req_id TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL);
CREATE TABLE IF NOT EXISTS tv(id INTEGER PRIMARY KEY CHECK(id=1), last_seen INTEGER NOT NULL, report TEXT NOT NULL, acked_seq INTEGER, adb_bad INTEGER NOT NULL DEFAULT 0);
CREATE TABLE IF NOT EXISTS meta(k TEXT PRIMARY KEY, v TEXT NOT NULL);
INSERT OR IGNORE INTO meta VALUES ('db_id', lower(hex(randomblob(16)))), ('cap_s','3600'), ('enforce','false');
CREATE TABLE IF NOT EXISTS events(id INTEGER PRIMARY KEY AUTOINCREMENT, ts INTEGER NOT NULL, who TEXT NOT NULL, kind TEXT NOT NULL, detail TEXT);
CREATE INDEX IF NOT EXISTS events_ts ON events(ts);
```
- 時間一律 epoch 秒、伺服器時間。「今天扣了幾分」直接顯示電視回報的 `used_today_s`。
- `events.kind`：`grant`、`zero`、`received`、`lock`、`unlock`、`online`、`adb-bad`、`adb-ok`、`reinstall`、`db-reset`、`gap`。
- `meta.enforce`、`meta.cap_s` 只由 Opus 用 `wrangler d1 execute` 改，家長頁沒有按鈕。

### 家長頁（iPhone Safari，手機優先、大按鈕，繁體中文）
1. 頂端異常列：`alerts` 逐條顯示（紅／黃），沒有就不顯示。
2. 狀態：剩餘 X 分（電視 HH:MM 回報；`capped` 時加「已封頂（上限 N 分）」）＋待電視接收 Y 分；YouTube 開放中／已鎖；電視在線（2 分內有回報）／N 分前回報。每 15 秒更新。
3. 按鈕：+15、+30、+60、歸零（立即鎖）。送出時產生 `crypto.randomUUID()` 當 `req_id`，按鈕鎖住到收到回應；`would_cap` 時提示「會封頂在 N 分」。
4. 今天 YouTube 用了幾分。
5. 紀錄最近 30 筆（時間、誰、動作）。頁尾顯示目前登入的是爸爸或媽媽。

## 斷網與異常（失效表）
| 情況 | 結果 |
|---|---|
| 電視斷網（Wi-Fi、路由器、CF 掛） | 照扣、用完照停；新加值等網路回來（約 1 分鐘內套用）。螢幕開著斷網 ≥ 30 分 → 家長頁紅字。 |
| 斷網且額度 0 | **YouTube 維持鎖住**（Owner Q5）。緊急開放（Mac 在家）：`adb shell am force-stop com.example.tvagent`（鬧鐘跟著取消，App 停到 KICK 或重開）再 `pm enable --user 0 com.google.android.youtube.tv`；網路回來後用 `meta.enforce=false` 正式關。README 寫明。 |
| 電視拔插頭／重開 | TCL 開機把 YouTube 改回啟用；App 約 82～90 秒後啟動、第一跳就鎖回 → **約 1.5～2 分鐘空窗**（同 v4）。額度在檔案；重開前的使用量照事件補扣。 |
| 電視待機（電源鍵） | 不扣（螢幕暗）；待機時心跳約每小時一跳（19:00 實測），所以失聯紅字只看「最後螢幕開著」。 |
| 小孩改系統時間 | 偵測到 `clock=jump` 改用 `elapsedRealtime`；「今天用了幾分」可能錯（只供顯示）。 |
| adbd 卡 prompt pending／USB 偵錯被關 | App 無法控制 → 家長頁紅字；家長重開電視或處理。 |
| D1 被重建 | `db_id` 不同 → 電視不套舊加值、對齊 `max_seq`；額度在電視上不受影響；家長頁黃字。 |
| service token 外洩 | 只能送假回報（可能壓住失聯紅字），不能加值；到 Access 撤換 token、重推設定檔。 |
| service token 到期 | 電視同步全 401/403 → 家長頁失聯紅字。到期日記進 README 與 Owner 行事曆。 |
| TCL 韌體更新改掉 TGuard 或權限 | 名單補回失敗 `wl=fail`、可能被砍 → 失聯紅字；上線初期保留 v4 tag 可重裝。 |

## 可繞過清單（照實告訴 Owner，不另防）
| 繞過 | 結果 |
|---|---|
| 設定 → 應用程式 → YouTube →「啟用」 | 下一跳（≤60 秒）停回。 |
| 強制停止本 App | 鬧鐘被取消，App 停擺、YouTube 停在當下狀態，直到家長 KICK（Mac adb）或電視重開（App 自啟後鎖回）。螢幕開著 30 分 → 家長頁紅字。 |
| 解除安裝／清除資料 | 同上；清資料還會讓 adb 金鑰消失 → 電視跳授權視窗：按「允許」→ App 恢復、額度 0（偏向鎖）、家長頁黃字；按「拒絕」→ adb 紅字。復原步驟：重推 `config.properties` → KICK → 電視按允許。 |
| 斷 Wi-Fi | 照扣照停，沒好處；30 分後紅字。 |
| 改時間 | 無效（見上）。 |
| 拔插頭 | 每次約 1.5～2 分鐘空窗；額度不重置。 |
| 恢復原廠設定 | 全部失效；失聯紅字。代價大，不另防。 |
| 關「USB 偵錯」或撤銷授權 | adb 紅字。⚠ 也會讓 Mac 的 adb（memlog、維運）斷線。 |
| 投放、語音、T 鍵 | YouTube 停用時開不了（v4 實測）；C5 逐項重測。 |
| 瀏覽器、YouTube Kids 等其他入口 | 電視上沒有（v4 結論）；C5 重測並記錄。 |
| 拿到家長頁 | Access 只放行兩個 Google 帳號。 |

## 「App 能不能隱藏？」
- 桌面看不到：已做到（沒有 LAUNCHER／LEANBACK_LAUNCHER）。
- 設定 → 應用程式清單裡看得到「看電視時間」：一般 App 沒辦法從系統設定隱藏（要 root 或 Device Owner，不採）。不用裝置管理員（Q10），強制停止／解除安裝／清資料都擋不了，列入可繞過清單。

## 與 v4 的關係（影子、切換、移除、回滾）
| 階段 | v4（Mac tvgate） | v5（電視 App） |
|---|---|---|
| WO-C4 影子期 **96 小時**（Owner 可提早拍板） | 照常控管，家長用 v4 網頁 | `meta.enforce=false`：扣虛擬額度、回報；不碰 YouTube、不跳畫面 |
| WO-C5 切換 | bootout | `meta.enforce=true` |
| C5 通過後 | 打 tag、從 Mac 移除 | 正式控管 |
- **切換**（每步確認再下一步）：家長頁「歸零」→ 確認 `/tv/api/state` 的 pending 清空（acked ≥ 歸零 seq）→ `launchctl bootout gui/$(id -u)/com.example.tvgate`（會先開放 YouTube）→ `wrangler d1 execute tvquota --remote --command "UPDATE meta SET v='true' WHERE k='enforce'"` → 2 分鐘內家長頁 `yt=disabled`、`adb shell pm list packages -d | grep -x package:com.google.android.youtube.tv` 有輸出。
- **移除 v4**（C5 全部通過後，Owner Q13）：`git tag tvgate-v4-final <main 最新>` 並 push → `launchctl bootout`（若還在）→ 刪 `~/Library/LaunchAgents/com.example.tvgate.plist*`、`~/Library/Application Support/tvgate`、`~/Library/Logs/tvgate.log`。`~/.secrets/tvgate.*` 留著（回滾時舊連結仍可用）。**Mac 的 adb 授權永久保留**（memlog、維運）。
- **回滾**（v5 出問題）：`meta.enforce=false` → 確認家長頁回報 `enforce=false`（電視離線時改用 Mac adb 直接 `pm enable`）→ v4 移除前：`launchctl bootstrap` 原 plist；移除後：`git worktree add <dir> tvgate-v4-final`，照該 tag 的 `tvgate/README.md` 安裝 launchd（plist 範本在 `tvgate/com.example.tvgate.plist`）。
- **任何階段都不准「撤銷 USB 偵錯授權」**（會清掉 Mac 金鑰）。要移除 App：`adb uninstall com.example.tvagent`，就這樣。
- Mac 端 `com.example.tvmemlog` 不受影響。

## Milestone 與工單
| M | 工單 | 誰 | 依賴 |
|---|---|---|---|
| M1 | WO-C1 Worker 施工 | 委派（工人） | — |
| M1 | WO-C2 電視 App 施工 | 委派（工人），可與 C1 平行 | — |
| M2 | WO-C3 Cloudflare 建置部署 | **Opus＋Owner，不委派** | C1 |
| M3 | WO-C4 影子模式上電視（96 小時） | **Opus＋Owner，不委派** | C2、C3 |
| M4 | WO-C5 切換＋實機驗收＋移除 v4 | **Opus＋Owner，不委派** | C4 |

### WO-C1 Cloudflare Worker（委派）
- **新增**（只准這些）：`cfquota/lib.mjs`（全部邏輯）、`cfquota/worker.mjs`（≤10 行：`export default { fetch: (req, env) => handle(req, env, Date.now()) }`）、`cfquota/schema.sql`（照上節原文）、`cfquota/wrangler.toml`、`cfquota/lib.test.mjs`、`cfquota/README.md`。
- 規格：照「Cloudflare 端」全節。`lib.mjs` 匯出 `handle(request, env, now)`；只用 Web 標準 API（`fetch`、`crypto.subtle`、`Response`）與 D1 的 `prepare().bind().run()/all()/first()`、`batch()`。**不得 import 任何套件**。
- 環境變數：`DB`（D1）、`ACCESS_TEAM`、`ACCESS_AUD`、`PARENTS`（secret，JSON：email→「爸爸」／「媽媽」）、`TV_CLIENT_ID`（secret）、`OFFLINE_MIN`（預設 30）。
- `wrangler.toml`：`name="tvquota"`、`main="worker.mjs"`、`compatibility_date="2026-10-01"`、`workers_dev=false`、`preview_urls=false`、`routes=[{pattern="tv-demo.example.net/tv/*", zone_name="example.net"}]`（**不得用 `custom_domain`**）、D1 binding `DB`（`database_name="tvquota"`、`database_id="REPLACE_IN_WO_C3"`）、`[vars] OFFLINE_MIN="30"`、`ACCESS_TEAM="REPLACE_IN_WO_C3"`、`ACCESS_AUD="REPLACE_IN_WO_C3"`；**沒有 `[triggers]`；不得出現 email、token、金鑰**。
- 測試 `lib.test.mjs`（`node:test`＋`node:sqlite`，Node 25 內建）：`DatabaseSync(':memory:')` 載入 `schema.sql`，寫約 20 行的 D1 轉接物件；`globalThis.fetch` 換成假的攔 certs；測試自己用 `crypto.subtle.generateKey` 產生 RSA 金鑰簽 JWT（**金鑰不落地、不進 repo**）。至少涵蓋：
  1. 驗證：沒 JWT、簽章錯、`alg` 不是 RS256、`aud` 錯、`iss` 錯、過期 → 401；未知 `kid` 會重抓 certs（換鑰後通過），一分鐘內第二個未知 `kid` 不再抓；連打兩次已知 `kid` 只抓一次 certs；家長打 `/tv/api/sync`、電視打 `/tv/api/state`、不在 `PARENTS` 的 email → 403；`/`、`/foo`、`/tvx` → 404。
  2. 加值：add 30 → `{seq:1}`、D1 有 1800 秒與 `by`；同 `req_id` 再送 → 同 seq、grants 只有 1 筆；`minutes:7`、非 JSON、缺 `req_id` → 400；`Origin` 缺或不同 → 403；zero → `set 0`；`would_cap`：電視回報 3000 秒時 add 15 → true。
  3. 同步：`last_seq:0` 回全部；`last_seq:1` 只回 seq>1、由小到大；回應含 `db_id`、`cap_s:3600`、`enforce:false`（改 meta 後跟著變）；回報後 `/tv/api/state.pending` 不含 ≤ acked；`acked_seq` 不會因較小的 `last_seq` 倒退；`last_seq:null` 第一次（沒電視紀錄）→ `grants:[]`；之後先 add 一筆、電視再以 `last_seq:null` 同步 → 只回 `seq > acked_seq` 的那筆，並記 `reinstall`；`db_id` 錯 → `grants:[]`、記 `db-reset`；欄位錯（缺欄、型別錯、負數、`balance_s` 86401、`adb` 65 字）→ 400 且 `tv` 表沒變。
  4. 異常：`last_seen` 31 分鐘前＋`screen:on` → `alerts` 有紅字；`screen:off` → 黃字不是紅字；之後回報 → 紅字消失、events 有 `online`（中斷 31 分）；adb 連 3 筆非 ok → 紅字＋1 筆 `adb-bad`、第 4 筆不再記、ok → `adb-ok` 且紅字消失；`enforce=false` → 影子黃字；`yt` 改變記 `lock`／`unlock`；`gap_s:400` 記 `gap`。
  5. 網頁：`GET /tv/`（家長 JWT）200、`text/html`、`no-store`、含「+15」「+30」「+60」「歸零」、不含 `TV_CLIENT_ID` 的值。
  6. 每次 sync 刪掉 90 天前的 events，90 天內的保留。
- 完成標準（工人自跑、Opus 審定再跑）：
  1. `node --test --test-reporter=tap cfquota/lib.test.mjs` → exit 0，輸出含 `# fail 0`，30 秒內（Node 25 的 `--test` 不接受目錄）。
  2. `grep -nE "^import" cfquota/lib.mjs` → 無輸出；`grep -c "import" cfquota/worker.mjs` → `1`（只 import `./lib.mjs`）。
  3. `grep -rnE "@(gmail|icloud|yahoo|hotmail|outlook)\." cfquota/` → 無輸出；`grep -nE "custom_domain|crons|triggers" cfquota/wrangler.toml` → 無輸出；`grep -c 'tv-demo.example.net/tv/\*' cfquota/wrangler.toml` → `1`。
  4. `cd cfquota && npx -y wrangler@4.151.0 deploy --dry-run --outdir "$TMPDIR/cfq"` → 成功（版本已寫死；README 同寫）。
  5. `git status --porcelain --untracked-files=all` → 只有 `cfquota/` 底下。
- 禁止：不登入 Cloudflare、不執行真的 `wrangler deploy`（只准 `--dry-run`）、不建任何 CF 資源、不碰 `tvgate/`、`tvagent/`、`memlog/`、`~/.secrets`、launchd、電視、`~/FAMILY-DOCS`；不加 npm 依賴（不建 `package.json`／`node_modules`）；不 commit。

### WO-C2 電視 App（委派）
- **改**（只准 `tvagent/` 底下）：
  - `AndroidManifest.xml`：`versionCode=2`；`android:label="看電視時間"`；加 `PACKAGE_USAGE_STATS` 權限；拿掉 `TestReceiver`；新增 `KickReceiver`（exported，action `com.example.tvagent.KICK`，只叫服務跑一跳）、`NoticeActivity`（`exported="true"`、**沒有 intent-filter**、theme `@android:style/Theme.Black.NoTitleBar.Fullscreen`、`excludeFromRecents="true"`）。仍然**沒有** LAUNCHER／LEANBACK_LAUNCHER。保留 `allowBackup=false`、`debuggable=true`（方便 `run-as` 讀 log、推設定）。不加 `testOnly`、不加裝置管理員。
  - `build.sh`：不需改（沒有 `res/`）；**同一把 `debug.keystore`**，覆蓋安裝才會保留已授權的 adb 金鑰。
  - 新增 `src/.../Quota.java`：**純 Java、不得用 android.\***。照「額度規則」：`load/save(Properties)`、`usage(events, fromWall, toWall, ytFg0, scrOn0)`（事件用 Quota 自己的小類別 `Ev(wallMs, type, pkg)`）、`clockJump(...)`、`consume(secs, taipeiDate)`、`applySync(grants, maxSeq, dbId, capS, enforce)`、`decide(ytState, fgYt, screenOn)` → `NONE|LOCK|UNLOCK|WARN|END`（並更新 `warned`）、`nextAlarmMs(fgAndScreen)`。
  - `AgentService.java`：每跳流程照「額度規則」1～7；拿掉 SELFTEST 與 tkey；每個程序啟動時用 adb 下 5 條設定（APP_AUTO_START、APP_ASSOC_START、GET_USAGE_STATS、standby bucket、deviceidle），結果記 log；沿用 WO-G2 名單補回；`UsageStatsManager.queryEvents` 轉成 `Ev` 交給 Quota；同步用 `HttpsURLConnection` POST JSON（`org.json`）到設定的網址，header `CF-Access-Client-Id`／`CF-Access-Client-Secret`，連線與讀取各 10 秒；非 200 記 `sync=http:<碼>`。先做 adb 與存檔、最後才連網。END 的 10 秒等待在同一條 executor 執行緒。
  - `NoticeActivity.java`：照「提示畫面」。
  - 設定檔 `files/config.properties`：`url`、`client_id`、`client_secret`。**設定只從這個檔來**；Intent extra 只准讀 `reason`（服務）與 `kind`（NoticeActivity，只認 warn／end）。
  - `agent.log`：每跳一行 `uptime pid bal used_today fg yt screen act sync adb wl [gap=] [clock=jump]`；超過 1MB 只留後 512KB。不得寫入 client_secret、私鑰、簽章。
  - 刪 `TestReceiver.java`；新增 `test/QuotaTest.java`；`test.sh` 兩個測試都跑；`README.md` 改寫（用途、安裝 `adb install -r`、裝完檢查 appops、推設定、讀 log、KICK、影子／切換／回滾、enforce 在 D1、斷網＋無 adb 的限制、移除只用 `adb uninstall`、**不准撤銷 USB 偵錯授權**）。
- `QuotaTest` 至少：
  1. `usage`：螢幕一直亮，YouTube RESUMED 在 +10 秒、PAUSED 在 +70 秒 → 60；中間螢幕暗 20 秒 → 40；其他 App RESUMED 結束 YouTube 前景；初始 `ytFg0=true,scrOn0=true` 且無事件 → 整段；`DEVICE_SHUTDOWN` 後不算；視窗外事件不算；結果不超過視窗長度；結束狀態正確。
  2. `clockJump`：同 boot、系統時間差比 elapsed 差多 3600 秒 → true；差 30 秒 → false；`boot_count` 不同 → false。
  3. `consume`：額度 30 扣 60 → 0、`used_total` 只加 30；換台北日期 → `used_today` 從 0 起。
  4. `applySync`：`last_seq=3`，加值 3（重複）、4 add 1800、5 set 0、6 add 900（亂序給）→ 額度 900、`last_seq=6`；同一批再套一次不變；沒有加值 → `last_seq` 不變；`db_id` 不同（或原本 null）→ 套用回傳加值後 `last_seq=maxSeq`、存新 `db_id`；add 超過 CAP → 封頂 3600、`capped=true`；`cap_s` 降到 1800 時現有 3000 不砍、再 add 也不增加；`enforce`、`cap_s` 照存。
  5. `decide`：unknown → NONE；額度 >0＋停用 → UNLOCK；0＋啟用＋前景＋亮 → END；0＋啟用＋不在前景 → LOCK；200＋前景＋亮 → WARN、再一次 → NONE；額度回到 >300 後再降到 200 → 又 WARN；200＋不在前景 → NONE。
  6. `nextAlarmMs`：前景＋20 → 20000；前景＋2 → 5000；前景＋330 → 30000；前景＋302 → 5000；前景＋3000 → 60000；不在前景 → 60000。
  7. `save`→`load` 來回相同；內容是垃圾／數字壞掉 → 額度 0、`last_seq` 空、`enforce=false`；套用加值後不存檔、重新 load 再套同一批 → 只套一次。
- 完成標準（工人自跑、Opus 審定再跑；OrbStack 要開著）：
  1. `sh tvagent/test.sh` → 輸出含兩個 `ALL PASS`（AdbClientTest 與 QuotaTest），exit 0。
  2. `sh tvagent/build.sh` 成功，產出 `tvagent/tvagent.apk`。
  3. 設 `D='docker run --rm --platform linux/amd64 -v '"$PWD"'/tvagent:/w -w /w cleanyourtv-android-build-amd64'`、`BT=/opt/android-sdk/build-tools/34.0.0`：`$D $BT/aapt2 dump badging tvagent.apk` → package `com.example.tvagent`、`versionCode='2'`、`application-label:'看電視時間'`、`targetSdkVersion:'30'`、含 `PACKAGE_USAGE_STATS`、**沒有** `launchable-activity`／`leanback-launchable-activity`；`$D $BT/aapt2 dump xmltree --file AndroidManifest.xml tvagent.apk` 含 `NoticeActivity` 與 `KickReceiver`、不含 `TestReceiver`、`testOnly`、`BIND_DEVICE_ADMIN`。
  4. 簽章相同：`git cat-file -e main:tvagent/tvagent.apk` 成功 → `git show main:tvagent/tvagent.apk > tvagent/old.apk`；`$D $BT/apksigner verify --print-certs old.apk` 與 `… tvagent.apk` 的 `SHA-256 digest` 那行相同；做完 `rm tvagent/old.apk`。
  5. `git diff --quiet main -- tvagent/debug.keystore tvagent/testdata` → exit 0。
  6. `grep -rn "disable-user" tvagent/src` → 只出現在 YouTube 常數那一處；`grep -rn "com.example.tkey\|SELFTEST" tvagent/src` → 無輸出；`grep -rnE "get(String|Int|Long|Boolean)Extra" tvagent/src` → 只剩 `reason`、`kind`。grep 只當輔助：審核要讀碼確認只停用／啟用 YouTube 一個套件、只 `am start` 自己的 NoticeActivity。
  7. `git status --porcelain --untracked-files=all` → 只有 `tvagent/` 底下。
- 禁止：不連電視、不執行真 adb、不裝到電視、不碰 `tvgate/`、`tkey/`、`cfquota/`、`memlog/`、`~/.secrets`、launchd；不改 `debug.keystore`、`testdata/`；不加 Gradle 或外部函式庫；程式只准停用／啟用 `com.google.android.youtube.tv`；不 commit。

### WO-C3 Cloudflare 建置與部署（Opus＋Owner，不委派）
1. Owner 登入 CF（管 example.net 的帳號）；Mac `npx -y wrangler@4.151.0 login`。先 `wrangler whoami`、確認 zone 名稱與 tc-booklist 的 Custom Domain 現況（只讀，不改）。
2. `wrangler d1 create tvquota` → id 填進 `wrangler.toml`；`wrangler d1 execute tvquota --remote --file schema.sql`。
3. Zero Trust（Owner 操作、Opus 唸步驟）：建 team；登入方式加 **Google**（可能要建 Google OAuth client）；Access 應用程式（自架）網域 `tv-demo.example.net`、路徑 `tv`；政策 A「允許」father@example.com、mother@example.com；政策 B「Service Auth」＝新建 service token「tv」（記到期日）。記下 AUD、team 名填進 `wrangler.toml`。service token 存 `~/.secrets/tvquota.cf.properties`（600），**不進 repo、不貼聊天**。
4. `wrangler secret put PARENTS`／`TV_CLIENT_ID` → `wrangler deploy`。
5. 驗收：
   - `curl -s -o /dev/null -w '%{http_code}' https://tv-demo.example.net/tv/` → `302`（被 Access 擋去登入）。
   - tc-booklist 不受影響：`curl -s -o /dev/null -w '%{http_code}' https://tv-demo.example.net/` 與部署前相同（部署前先記下），首頁內容相同。
   - 帶 service token `curl -s -X POST https://tv-demo.example.net/tv/api/sync -H 'CF-Access-Client-Id: …' -H 'CF-Access-Client-Secret: …' -H 'Content-Type: application/json' -d '{…last_seq:null…}'` → 200、含 `max_seq`、`db_id`；同 token 打 `/tv/api/state` → 403。（若 200 變 403：JWT 的 `common_name` 格式跟 `TV_CLIENT_ID` 不同，以實際 claims 修正。）
   - 兩支 iPhone Safari 開網址 → Google 登入 → 看到頁面、頁尾是爸爸／媽媽；加入主畫面後從主畫面開仍能登入（iOS 主畫面 App 的 cookie 與 Safari 分開，可能要再登入一次；不行就改用 Safari 書籤）。
   - 驗完清掉測試資料：`DELETE FROM tv; DELETE FROM events; DELETE FROM grants;`。

### WO-C4 影子模式上電視（Opus＋Owner，不委派）
- 前置：C2 完成、C3 驗收過；`meta.enforce=false`；v4 照跑。
1. `adb -s 192.0.2.69:5555 install -r tvagent/tvagent.apk` → `run-as com.example.tvagent ls files/adbkey` 仍在（電視**不應**跳授權視窗）。
2. 推 `config.properties` → KICK → 2 分鐘內：agent.log 有 `sync=ok`；`cmd appops get com.example.tvagent` 顯示 APP_AUTO_START、APP_ASSOC_START、GET_USAGE_STATS 皆 allow；TGuard 名單含 tvagent；家長頁顯示影子黃字、電視在線。
3. **C2 前待確認的四項在這裡實測**（記進 `research/batches/v5/NOTE.txt`）：①App `queryEvents` 拿得到 YouTube 與螢幕事件（看 YouTube 2 分鐘，`used_today_s` 增加約 2 分）；②YouTube 播放中用 Mac `adb shell am start -n com.example.tvagent/.NoticeActivity --es kind warn` 叫畫面（shell 身分與 App 自己的 adb 相同）：能叫起（exported 設計正確）、TCL 沒擋；③畫面跳出時 YouTube 是否自動暫停（照實記錄）；④CAP 調小不砍額度（QuotaTest 已涵蓋，只確認）。任一項不如預期 → 回 C2 修。
4. 加值：家長頁 +15 → 2 分鐘內「待接收」消失、電視回報額度 900（虛擬）。
5. 失聯：螢幕開著時 Opus `adb shell am force-stop com.example.tvagent` → 30～32 分鐘後家長頁紅字 → KICK → 紅字消失、events 有 `online`。
6. 連續 96 小時（Owner 可提早拍板）：挑一段實測（記起訖時間）比對 v5 `used_today_s` 增量與 v4 網頁 YouTube 前景分鐘數，差 ≤ 2 分；agent.log 清醒時段心跳間隔 ≤ 3 分、`wl=fail` 不連續出現；TOTAL PSS ≤ 20MB；memlog 沒變差。
- 通過 1～6 才進 C5；不通過 → 回 C1／C2 修（enforce 本來就是 false，不影響 v4）。

### WO-C5 切換、實機驗收、移除 v4（Opus＋Owner，不委派）
- 切換：照「與 v4 的關係 → 切換」逐步確認（Owner 挑沒人看電視的時間）。
- 驗收（記錄時間與結果到 `research/batches/v5/NOTE.txt`）：
  1. +15 → 2 分鐘內 YouTube 可開；剩 5 分鐘時跳一次預告；看到用完 → 跳「電視時間已到」、10 秒後鎖、圖示消失；記錄「歸 0→跳畫面」「跳畫面→鎖」各幾秒、YouTube 是否自動暫停。
  2. 有額度停在桌面 10 分 → 額度不減（±1 分）；YouTube 播放中按電源待機 10 分 → 不減（±1 分）。
  3. 有額度時把電視時間改快 1 小時 → 額度不多扣（±1 分）；log 有 `clock=jump`；改回。
  4. 額度 0：設定裡手動啟用 YouTube → 2 分鐘內停回。
  5. 額度 0 拔插頭 → 開機後 3 分鐘內停用，記空窗秒數；額度 >0 拔插頭：拔前記 `used_today_s`、插回 5 分鐘後再比，額度保留。
  6. 有額度看 YouTube 時斷電視 Wi-Fi 35 分 → 照扣、用完照停；家長頁出現紅字；接回後紅字消失、數字補上。
  7. 歸零 → 2 分鐘內 YouTube 關閉。爸爸、媽媽各加值一次 → 紀錄顯示正確的人；雙擊加值只加一次。
  8. 可繞過清單逐項重測（強制停止、清資料後的復原步驟、投放、語音、T 鍵、其他入口）→ 照實記錄給 Owner。
  9. YouTube 鎖住時 Netflix／四季／Spotify 正常。
  10. 24 小時後：memlog、PSS 正常；agent.log 沒有連續 adb 失敗。
- 通過 1～7、9、10；第 8 項照實記錄。失敗 → 回滾。
- 通過後：照「移除 v4」做，告訴 Owner。

## 風險
| 風險 | 機率 | 對策 |
|---|---|---|
| TCL 擋 shell `am start` 或提示畫面被凍結 | 低～中（待 C4 實測） | 回 C2：畫面拿掉、只保留鎖定（規則不受影響） |
| UsageStats 事件在 TCL 上拿不到或不完整 | 低（v4 用同來源） | C4 第 3 項實測；不行回 C2 改回每分鐘取樣 |
| 凍結期間 HTTPS 被卡斷，同步常失敗 | 中（推測） | 扣額與上鎖在連網前完成；下跳重試；C4 看 `sync=` 成功率 |
| TGuard 名單被韌體更新改掉 | 低、影響大 | `wl=fail` 進 log 與家長頁；保留 v4 tag 可重裝 |
| 鬧鐘被砍後消失（原因未定）、非 TGuard 機制砍程序 | 低～中 | C4 觀察；失聯紅字；必要時再研究 |
| adbd 卡 prompt pending／金鑰被清 | 低～中 | adb 紅字；README 寫「重開電視」 |
| route 與 tc-booklist 衝突 | 低（文件確認 route 優先） | C3 部署前後比對 tc-booklist 首頁 |
| Access Google 登入／iOS 主畫面登入 | 待查 | C3 實測；不行改 Safari 書籤 |
| 家長沒開頁面就不知道異常（本期不推播） | 中 | Owner 接受（Q8／Q10）；第二期 Telegram |
| v4、v5 同時控管打架 | 低 | 影子期 enforce=false；切換逐步確認 |
| 誤撤銷 USB 偵錯授權 | 低、影響大 | 每份說明書與本檔都寫「不准」 |

## 成本（每月 0 元；數字以 Cloudflare 現行公開頁面為準，屬推測，C3 複查）
| 項目 | 免費額度（推測） | v5 用量（估） |
|---|---|---|
| Workers 請求 | 10 萬次／天 | 電視 1,440＋網頁（開著 4 次／分）≈ 2～3 千 |
| D1 | 寫 10 萬列／天、讀 500 萬列／天、5GB | 寫約 3～5 千列／天；儲存 <10MB／年 |
| Access（Zero Trust Free） | 50 人 | 2 人＋1 token（註冊是否要綁信用卡：待查） |
| 網域 | — | 用既有 example.net，0 |
| 電視流量 | — | 約 1～2MB／天 |

## 不做
電視上的持續倒數（只做「剩 5 分預告」與「時間已到」兩個畫面）、推播與額度用完通知（第二期）、每日自動額度（Q3）、裝置管理員（Q10）、Web Push、使用時間圖表、多台電視、其他 App 的控管、電視端對內連線埠、Cron。
- **第二階段 TODO：通知與手機直接設定額度**（Telegram 或 ntfy；LINE Bot 不夠用）。研究見 `research/R8_notify_control_HAIKU.md`。結論：Telegram 適合當控制台，有官方按鈕＋webhook、用 secret token＋from.id 白名單、免費；ntfy 在 iOS 上支不支援按鈕證據互相矛盾，要實機測；iPhone 捷徑能主動 POST 加額度，但查不到「收到通知自動跑捷徑」的做法。

---
# 決策紀錄（已併入正文，僅供追溯；與正文衝突時以正文為準）
## 待 Owner 決定（每題附建議）
1. **扣額規則**：只在 YouTube 前景＋螢幕亮時扣（暫停計時），取代原需求「核准後不暫停、切 App 關電視都繼續倒數」？→ 建議**採用**（就是 Owner 說的「額度」）。**Owner 裁定：採用**；「開著就扣」，逛選單也扣（只看 YouTube 在前景＋螢幕亮，不看是否在播放）。
2. **額度會不會過期**：不過期／每天某時歸零？→ 建議**不過期**；要清就按「歸零」。**Owner 裁定：累積、不過期，但有上限**（21:50 定 60 分）；加值超過上限就封頂，家長頁顯示封頂。
3. **預設每日額度**：→ 建議 **0**（維持「家長臨時核准」）；要的話 Worker 每天 00:00 自動加 N 分（加一條 cron，小改）。 **Owner 裁定：0，不自動加。**
4. **加值選項**：→ 建議沿用 **15／30／60＋歸零**。 **Owner 裁定：+15／+30／+60／歸零**（上限 60，+60＝補滿）。
5. **斷網政策**：額度用完照停、斷網且額度 0 維持鎖（和 v4「Mac 不在＝開放」相反）？→ 建議**照停、維持鎖**（斷網少見、大人還有 Netflix 等；緊急用 Mac 備援）。失聯通知門檻 → 建議 **15 分鐘且最後螢幕開著**。 **Owner 裁定：斷網且 0 維持鎖；失聯門檻改 30 分鐘且失聯前螢幕開著。**
6. **Cloudflare 帳號與網域**：→ 建議用 **Owner 自己的 CF 帳號**；已有網域就用子網域（例 `tv.<網域>`）；沒有先試 workers.dev＋Access，C3 驗不過再買網域（約 US$10／年）。 **Owner 裁定：`https://tv-demo.example.net/tv/`**，見「Owner 裁定 21:50」。
7. **Access 登入**：哪兩個 email（Owner、太太）、session 多久？→ 建議 email 一次性 PIN、**30 天**。 **Owner 裁定：Google 帳號登入**，只允許 father@example.com、mother@example.com；電視用 service token。
8. **通知管道**：→ 建議 **ntfy**（免費、兩支 iPhone 裝 App 訂閱同一私密 topic）；備案 Telegram bot。 **Owner 裁定：本期不推播**，異常只顯示在家長頁頂端；通知／控制台列第二期。
9. **App 在設定清單裡的名稱**：→ 建議不起眼的「TV 服務」。 **Owner 裁定：「看電視時間」。**
10. **用裝置管理員擋解除安裝／清資料**（移除時多一步）：→ 建議**採用**。 **Owner 裁定：不採用。**
11. **正在看時額度用完**：直接關回桌面（v4 到期同樣行為，v5 不再有「回家寬限」）？→ 建議**直接關**。**Owner 裁定（改）**：歸 0 → 電視跳「電視時間已到」畫面 → **10 秒後鎖**（force-stop＋disable），孩子按什麼都一樣。
12. **影子期長度**：→ 建議 **48 小時**。 **Owner 裁定：96 小時**，Owner 可提早拍板切換。
13. **v4 退場**：切換後 v4 保持 `.paused` 多久、之後是否刪？Mac adb 授權保留（memlog、維運、回滾）。→ 建議 **2 週後再問**，授權永久保留。 **Owner 裁定：V5 上線後從 Mac 移除 v4**，程式只留 GitHub（打 tag）。

## Owner 裁定 21:50（2026-10-30，tv-66 session 逐題問 Owner 後轉述；本節優先於一切）
| 題 | 裁定 | 改寫正文時要動的地方 |
|---|---|---|
| CAP | 60 分＝3600 秒 | Quota 預設 3600；D1 `meta.cap_s`=3600；QuotaTest 封頂改 3600；`balance_s` 驗證範圍改 0～3600 |
| Q3 | 不自動加（0） | 不加 cron 加值 |
| Q4 | +15／+30／+60／歸零 | 家長頁按鈕；+60＝補滿（受 CAP 封頂） |
| Q5 | 斷網且 0 維持鎖；失聯門檻 **30 分**，且失聯前螢幕開著 | `OFFLINE_MIN=30`；保留「最後螢幕開著」條件（取代挑戰 #13 的拿掉條件） |
| Q6 | 網址 `https://tv-demo.example.net/tv/` | **tv-demo.example.net 已有 Worker tc-booklist（~/FAMILY-DOCS/EReading/tc-site，D1 tc-booklist），不得改動。** v5 另開獨立 Worker，route `tv-demo.example.net/tv/*`；所有路徑加 `/tv` 前綴（家長頁 `/tv/`、API `/tv/api/grant`、電視 `/tv/api/sync`）；Access 應用程式只保護 `/tv` 路徑；C3 部署前先確認 route 不搶 tc-booklist 的路徑 |
| Q7 | Access 用 **Google 登入**，只允許 father@example.com（父）、mother@example.com（母）；電視用 service token | C3 在 Zero Trust 加 Google 登入方式（可能要建 Google OAuth client，Owner 操作）；取代 email PIN |
| Q8 | 本期**不推播** | 拿掉 ntfy（通知段、`NTFY_TOPIC`、C4「手機鎖屏收得到」）；失聯／異常只在家長頁頂端常駐顯示（挑戰 #14 的常駐異常列）。第二期 TODO：Telegram 當控制台、ntfy 待實測 iOS 按鈕（研究：main `research/R8_notify_control_HAIKU.md`，未 commit）。**風險**：家長沒打開頁面就不知道異常（與 Q10 原則一致，Opus 認為本期可接受） |
| Q9 | App 名稱「看電視時間」 | Manifest `android:label` |
| Q10 | 裝置管理員**不採用**。Owner 原則：「小孩若有能力解開限制，意謂他真心想要，我會再跟他開導」 | 拿掉 AdminReceiver、`dpm set-active-admin`、`admin=` 回報、挑戰 #12／#15 的裝置管理員驗收；強制停止／清資料／解除安裝列入「可繞過清單」 |
| Q11 | 已在 21:40 裁定 | — |
| Q12 | 影子期 **96 小時**，Owner 可提早拍板切換 | C4 第 6 項改 96 小時 |
| Q13 | V5 上線後從 Mac 移除 v4（launchd plist、`~/Library/Application Support/tvgate`、`tvgate.log`），程式只留 GitHub（打 tag 備存）；Mac adb 授權永久保留（memlog 用） | 回滾改成「從 GitHub tag 重裝 v4」；C5 後加移除步驟 |

## Owner 裁定 21:40（2026-10-30，經 tv-66 session 轉述；本節優先於本文與「挑戰裁定」#4）
> 來源：Owner 在 tv-66 session 的裁定，由 tv-66 轉給本 session 寫入。CAP 數字與下列「待確認」仍待 Owner 本人確認。
| 項目 | 裁定 | 改法 |
|---|---|---|
| Q1 扣額 | 採用：YouTube 前景＋螢幕亮就扣，逛選單也扣 | 照「挑戰裁定」#2 的 UsageStats 事件計算，不另判斷是否播放 |
| Q2 過期 | 累積、不過期、**有上限 CAP＝60 分**（21:50 定） | 見「額度規則」套用加值；D1 `meta.cap_s`；家長頁顯示封頂；Worker `POST /api/grant` 回應帶 `capped` 預估 |
| Q11 用完 | 跳「電視時間已到」→ 10 秒後鎖 | 同一跳內：顯示畫面 → 等 10 秒（executor 執行緒 sleep；畫面在前景時 App 不會被凍結）→ force-stop＋disable → 讀回。取代 #4 的「30 秒內回桌面」：驗收改「歸 0→跳畫面」「跳畫面→鎖」實測秒數 |
| 新增：5 分預告 | 剩 300 秒時跳一次預告（每次額度跨過 300 秒只跳一次；加值後又高於 300 秒可再跳） | 狀態檔加 `warned=true/false`；`nextAlarmMs` 加 300 秒時間點 |
| 新增：提示畫面 | 從「不做」移出 | 新增 `NoticeActivity`（全螢幕、無 LAUNCHER）；tvagent 用自己的 adb 下 `am start -n com.example.tvagent/.NoticeActivity --es kind warn|end`（避開背景啟動 Activity 限制），顯示完 `finish()`，不常駐；`end` 畫面 10 秒內攔返回鍵 |

**待確認（C2 前實測，不影響規則，記錄即可）**：①畫面跳出時 YouTube 是否自動暫停；②shell 的 `am start` 能不能叫起 **exported=false** 的 Activity（一般不能 → 可能要 `exported=true` 且不加 intent-filter；無害，但要寫明）；③TCL TclAppBootManager／FreezeManager 會不會擋這個 `am start`；④CAP 改小時現有額度砍不砍（暫定不砍）。
**C2 工單要補**：Manifest 加 NoticeActivity；`Quota` 加 CAP、`warned`、預告判斷 `shouldWarn()`；QuotaTest 加：CAP 封頂、跨 300 秒只預告一次、加值回到 >300 後再跨可再預告、歸 0 回 `END` 動作。

## 挑戰裁定（TERRA high，2026-10-30 10:36；原文 `research/challenge/PLAN_v5design_TERRA.md`）
> Opus 裁定。採納 21、部分採納 4、整條駁回 0。最大的改動是 #2：扣額改用系統 UsageStats 前景事件精確計時，取代每分鐘取樣（v4 `tvgate/gate.py` 已用同一來源算前景時間，TCL 有記錄這些事件）。

| # | 裁定 | 改法（正文改寫時照此） |
|---|---|---|
| 1 重開判斷 | 採納 | 加讀 `Settings.Global.BOOT_COUNT`（API 24+，一般 App 可讀）；存檔的 `boot_count` 不同＝重開過。扣額改由 #2 的事件計算後，重開前的使用量照事件扣，不靠 elapsedRealtime 差值。 |
| 2 取樣可被繞過 | **採納（改架構）** | 每跳用 `UsageStatsManager.queryEvents(上次查詢時間, 現在)` 取 YouTube 的 `ACTIVITY_RESUMED/PAUSED` 與 `SCREEN_INTERACTIVE/NON_INTERACTIVE`，算「YouTube 前景 ∩ 螢幕亮」的實際秒數來扣。權限由 App 自己用 adb 下 `cmd appops set com.example.tvagent GET_USAGE_STATS allow`（與 TCL 三條設定同時）。事件時間戳是系統時間 → 防改時間：時間戳只用來算「區間長度」，並以 elapsedRealtime 差值為上限做交叉檢查（兩者差 >60 秒記 log、以較大者扣）。WO-C0 加實機確認：tvagent 能否 query 到事件。 |
| 3 180 秒上限＝免費看 | 採納（#2 解決） | 事件是系統記錄的，App 被凍結或延遲多久都會補扣；拿掉 180 秒上限。間隔 >5 分鐘記 `gap=` 進 log 並回報。 |
| 4 10 秒內鎖定無保證 | 採納 | 每跳**第一件事**先排下一個鬧鐘，網路同步放最後。驗收改成「額度歸 0 後 **30 秒內**回桌面」，C5 實測記錄實際秒數。 |
| 5 分頁 max_seq 跳號 | 採納 | 拿掉 100 筆上限（每天加值個位數）；電視的 `last_seq` 只能設成**實際套用的最後一筆** seq，不再對齊 `max_seq`（初始化例外見 #6）。 |
| 6 首次同步吞掉新加值 | 採納 | `last_seq=null` 時 Worker 回 `seq > tv.acked_seq` 的加值（家長加了但電視**從沒收到**的），不回已收到的歷史；全新第一次（沒有 tv 紀錄）回空。測試加「先加值、後重裝同步」。 |
| 7 D1 重建 | 部分採納 | 加 `meta(db_id)`（建表時隨機產生），sync 回應帶 `db_id`；電視存檔，不同時：不套任何加值、`last_seq` 設 `max_seq`、回報 `db_reset` → 推播。不做還原流程（D1 只有 Opus 手動才會重建）。 |
| 8 原子持久化 | 採納（澄清） | 額度、`last_seq`、`boot_count`、上次查詢時間在**同一個檔**，套完一批加值後一次寫：寫暫存檔→`FileOutputStream.getFD().sync()`→rename。暫存檔寫失敗＝這跳不改狀態。QuotaTest 加「套用後存檔前丟例外 → 重載後只套一次」。 |
| 9 家長加值防重 | 採納 | `POST /api/grant` 必帶 `req_id`（網頁 `crypto.randomUUID()`，按鈕送出後鎖住到回應）；`grants.req_id TEXT UNIQUE`；重複 → 回原本的 seq。測試雙擊與重送。 |
| 10 切換／回滾打架 | 採納 | 每步確認再下一步：切換＝歸零→確認 `/api/state` 的 acked ≥ 歸零 seq→bootout v4→開 enforce→確認 `yt=disabled`。回滾＝關 enforce→確認回報 `enforce=false`→bootstrap v4。 |
| 11 回滾依賴 Mac adb | 採納 | `enforce` 改放 **D1**（`meta.enforce`，Opus 用 `wrangler d1 execute` 改，不給家長頁按鈕），sync 回應帶下去；電視設定檔只放網址與 token。回滾不再需要 Mac adb。無網路＋無 adb 時的限制寫進 README。 |
| 12 強制停止後不會復活 | 採納 | 正確：強制停止會取消鬧鐘，要明確啟動才恢復。C4 第 3 項改成**上線門檻**：記錄 TCL 上「強制停止」是否被裝置管理員擋；擋不住 → 寫明「孩子強制停止後 YouTube 停在當下狀態，直到家長 KICK 或電視重開（重開後 App 自啟會鎖回）」，交 Owner 接受。 |
| 13 失聯通知盲點 | 採納 | 依 19:00 數據：待機時心跳有在跑 → 拿掉「最後螢幕開著」條件，任何失聯 ≥ 門檻都推；沒在跑 → 保留條件並把盲點寫進 README 交 Owner 接受。 |
| 14 通知失敗漏報 | 採納 | `tv.alerts` 只在 ntfy 回 2xx 後才標「已通知」；失敗下次 cron 重推；家長頁頂端持續顯示目前未恢復的異常（不靠推播）。 |
| 15 裝置管理員是假設 | 採納 | 同 #12 列為 C4 實測門檻；保留「每次啟動若被停用就自動重新啟用＋推播」（孩子關掉 → 下一跳自動恢復），README 寫明移除時要先停 App。 |
| 16 其他 YouTube 入口 | 部分採納 | 範圍沿用 v4 已接受的結論（只停原生 YouTube；瀏覽器、投放、Kids 等已在 v4 階段測過的照舊記錄）；C5 驗收第 9 項改成逐項重測並列「可繞過清單」，不宣稱整體封鎖。不擴大控制套件。 |
| 17 JWT 測試 | 採納 | 固定 `alg=RS256`、依 `kid` 選鑰、未知 `kid` 重抓 certs（每分鐘最多 1 次）；測試補錯演算法、未知 kid、錯 issuer、換鑰。 |
| 18 service token 到期 | 採納 | C3 建立時記到期日進 README＋Owner 行事曆提醒；sync 連續 401/403 → 電視回不了報 → 失聯通知會觸發（已涵蓋偵測）。 |
| 19 D1 併發 | 部分採納 | `acked_seq` 用 `MAX(acked_seq, ?)` 只進不退；告警狀態更新用單句條件 UPDATE。駁回「真 D1 併發驗收」：只有一台電視、cron 每 5 分鐘，過度。 |
| 20 故障規則矛盾 | 採納 | 正文改成三列表：狀態檔壞→額度 0；設定檔（網址/token）不存在→不同步，但 `enforce` 用最後一次收到的值（存在狀態檔）；adb 查詢失敗→這跳不動手、連 3 跳推播。 |
| 21 今天扣了幾分 | 採納（簡化） | 改由電視自己用 #2 的事件算「台北今天 YouTube 秒數」回報（`used_today_s`），Worker 只顯示；拿掉 `daily` 表。校準基準改「同一段實測（記起訖時間）兩邊差 ≤ 2 分」。 |
| 22 驗收假 PASS | 採納 | C3 的 curl 測試後 `DELETE FROM tv; DELETE FROM events;`；拔插頭少扣量測：拔前記 `used_today_s`、插回後 5 分鐘再比。清資料後的復原步驟寫完整（重推設定＋電視按允許）。 |
| 23 檢查不可執行 | 採納 | wrangler 版本 Opus 在 APPROVED 時寫死；C2 檢查 4 先 `git cat-file -e main:tvagent/tvagent.apk`；grep 只當輔助，審核要讀碼確認只碰 YouTube。 |
| 24 刪減 | 部分採納 | 刪 `daily` 表（#21）。保留 events（家長頁要看「誰加值」）、保留 90 天清理（一句 SQL）；裝置管理員依 #12 實測決定去留。 |
| 25 ntfy 長期送達 | 採納 | 已由 #14 家長頁常駐異常補位；C4 驗收加「兩支手機鎖屏收得到」。 |

**駁回**：無整條駁回；#19 的「真 D1 併發驗收」、#7 的「還原流程」為部分駁回，理由見上。
