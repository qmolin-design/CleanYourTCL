FAIL

## 一、阻擋（違反規格或測試可假通過）

**B-1 `tvgate/gate.py:74`（`except` 在 :83）：state.json 內容是合法 JSON 但不是物件時，服務啟動就崩潰**
`load()` 先用 `saved.get('last_check')`；若檔案內容是 `[]`、`"x"`、`5`、`null`，會拋 `AttributeError`，而 `except (OSError, ValueError, KeyError, TypeError)` 接不到，`main()` 也沒有保護，程序直接結束。launchd `KeepAlive` 會讓它反覆重啟，結果**完全失去鎖定能力**（電視停在上一輪 SIGTERM／睡前開放時的狀態）。
違反 PLAN_v3 「到期時間：…不存在或壞掉＝沒有授權，壞掉時 log 記一行」與 v4「DB／狀態壞掉不影響鎖定」。這是本輪回歸：`main` 版用 `saved['until']`，非 dict 會拋 `TypeError` 被接住，加入 `.get` 後失效。
同一類問題還有數值未驗範圍：`until=1e300` 會讓 `display()`（:67）在 `/status`（:582）拋例外；`last_check=1e18` 會讓 `schedule_due`（:440）拋例外，**monitor 執行緒直接死掉**（不再對帳、不再切排程、不再記 expire）。
測試缺口：`tvgate/test_gate.py:320-327` 只寫入 `'bad'`（非法 JSON），沒測「合法 JSON、型別或範圍錯」，所以現在 34 個測試全綠也抓不到。

**B-2 `tvgate/gate.py:262`（寫入在 :280）：`expire` 用「授權狀態轉態」推導，手動鎖與排程切斷後必多記一筆「到期」**
`expired = previous_authorized and not now_authorized`；`/lock`（:643-648）與排程切斷（:271-276）都會把 `until` 設成 `None`，下一輪迴圈必然 `previous_authorized=True / now_authorized=False` → 多寫一筆 `expire`。`/log` 是 `rowid DESC`，所以按完「立即鎖」後，畫面最上面會是「到期 系統」。
違反 PLAN_v4 §紀錄 的動作語意（`expire` = 到期）與挑戰裁定 MS C-12（紀錄必須是「真實動作」）。反向也錯：若一次授權到期後、在 monitor 察覺前又被 `/open` 蓋掉，那次真正的到期完全不記。`test_gate.py:500-513` 只驗「真到期會記 expire」，抓不到多記。

**B-3 `tvgate/test_gate.py:661-671`：GATE_LOCK 寫入只有反向測試，實作整段刪掉也全綠**
`apply()` 在 :199-200 讀回確認 `tv=='locked'` 才寫 `events(...,'GATE_LOCK',...)`（實作正確），但唯一相關測試 `test_lock_event_requires_readback` 只斷言「讀回不是 locked 時不要寫」。34 個測試中**沒有任何一個**對 `events` 表做 `type='GATE_LOCK'` 的正向斷言（`test_calc_exact:600` 只是餵假事件給純函式）。
違反 PLAN_v4 §使用時間「每次 force-stop＋disable-user 成功，寫一筆 `events(now,'GATE_LOCK',...)`」——這正是挑戰 MS C-03「鎖定期間被算進 YouTube」的解法，也是 WO-T4 第 4 項驗收（鎖後靜置分鐘數不增加）的前提。測試可假通過。

## 二、WO-T3 完成標準第 1 項逐子項對照

| 子項 | 對應測試 | 結果 |
|---|---|---|
| 未知鑰匙／檔不存在／壞掉／值不是字串 → 401、log 沒新增、服務沒掛 | `:349-368`（401 在 :357-362） | 有；「log 沒新增」只查 DB（:364），文字 log 那行沒驗 → 見非阻擋 N-3 |
| 兩把鑰匙各 open/lock → /log device 正確 | `:350-355` | 有（精確比對 `[('lock',name),('open',name)]`） |
| 改鑰匙檔刪一把 → 不重啟即 401 | `:366-368` | 有 |
| log 種類 expire/schedule/relock/away/grace/schedule-add/schedule-delete | `:500-513`、`:558-572`、`:386-387` | 都有；**缺 GATE_LOCK（B-3）** |
| calc 六組精確值 | `:595-610` | 六組都在，數值精確（1800/1200、600+600、1200、2400、0、14400、1800） |
| 抓取去重＋UNKNOWN／In-memory 不誤讀 | `:422-449`（count==2 :442、Netflix 0 :443、UNKNOWN 0 :444、第二次抓取後 count 不變 :445-448） | 有，優於規格（比筆數不比分鐘） |
| 真實樣本 ≥40 筆、YouTube/Netflix >0 且 ≤ 跨度 | `:612-618` | 有（樣本 `Last 24 hour events` 開頭、54 筆 RESUMED、無 `In-memory` 段） |
| 抓取不擋鎖定（卡 20 秒、/open <20s、/status <2s） | `:462-469` | 有 |
| 抓取失敗不改 tv、不觸發寬限 | `:450-460` | 有（tv/auth/notified/log 無 grace 全查） |
| 併發 20×5×4 無 500 | `:413-420` | 有（直接斷言 200，更嚴） |
| DB 損壞 → 啟動、open/lock 正常、/usage 503 | `:389-399` | 有（/log、/schedule 也驗 503） |
| schedule_due 五個案例 | `:691-700` | 有（含週日 23:59→週一、兩天區間 3 次） |
| 排程端到端：寬限中照鎖／>600 skipped／重開不回補 | `:535-556`、`:558-585` | 有（600 邊界另見 A5） |
| 回家觸發：3 分鐘失敗恢復照鎖／離家≥2 分回來給寬限 | `:679-689`（單元）、`:515-533`（整合，電視重連不給寬限 :520-522） | 有；整合用 `TVGATE_AWAY_SECONDS=0.5`（:516），預設 120 秒只靠讀碼 → 非阻擋 N-2 |
| 端點錯誤碼（非 JSON、25:00、7:40、[0]、[]、[1,1]、第 21 條、重複 id、delete 404、range=year 400、空 DB） | `:370-387`、`:472-480` | 全部有 |
| 網頁（200/no-store/無鑰匙/三個中文標題/HEAD 無 body/GET 405） | `:401-411` | 有 |
| `--links`（每設備 2 行、自帶鑰匙、5 秒內結束、不停服務） | `:486-498` | 有；埠用測試自選（:489），**預設 8790 未斷言**（A3 允許）→ 非阻擋 |
| 150 秒內、log mtime 不變、git status 只有 tvgate/ | 未執行（依指示）| `TVGATE_LOG` 隔離在 `:97`；git status 實查只有 3 個 tvgate 檔、無 tmp* |

（另註：檔內實測 **34 個 test 方法**（GateTest 26＋PureTest 8），與「已跑過 31 個全過」不符，建議確認 run 輸出真的是 34 個；列非阻擋。）

## 三、原有 14 個測試的斷言有沒有被刪／放寬

**沒有。** 以 `git diff main -- tvgate/test_gate.py` 並逐行比對 `main` 版所有含 `assert` 的行：**每一行都逐字存在**於新檔，沒有任何參數比對變寬、時間上限變大或檢查被移除（`3*INTERVAL`、10 秒、20 秒、1 秒、`assert_lock_sequence`、`assertIn(ENABLE, ...)`、`assertNotIn('grace', ...)` 全部原樣）。
被改動的只有 5 處固定 sleep，而且都是**改嚴**：
- `:213-216`（hung_adb）`time.sleep(.3)` → `wait_for(any('list' in ...))`，改為等假 adb 真的收到 `list` 才打 /status。
- `:259-262`、`:267-270`、`:280-282`、`:297-299`（grace 相關 4 處）`time.sleep(2*INTERVAL)` → `wait_for(2 次 list 呼叫)`，改為確認對帳真的又跑了兩輪才斷言「還是沒被鎖」。
其餘 `setUp` 差異是規格要求（token 改 JSON、加 `TVGATE_LOG`／`TVGATE_HOME_NET`），`request()` 多一個 `body=`，都不影響舊斷言。

## 四、gate.py 規格逐項

- **calc 段落結束規則** `:372-391`：同 package 連續 RESUMED 不切（:381-383）、其他 package 的 RESUMED／SCREEN_NON_INTERACTIVE／DEVICE_SHUTDOWN／DEVICE_STARTUP／同 package 的 GATE_LOCK 才結束（:384-386）、同秒排序 RESUMED 在後（:380）→ 符合。
- **4 小時上限** `:378` `min(end, start+14400, day_end)` → 符合。
- **min(now,last_fetch)** `:387-388`，`last_fetch is None` 不延伸（:387 條件）→ 符合。
- **跨午夜切分** `:376-378` 用 `max(start, day_start)`／`day_end`；`schedule_due`（:438-451）逐日以「該時間點的日期」判週幾 → 符合。
- **GATE_LOCK 事件** `:197-200` 先讀回、`tv=='locked'` 才寫 → 符合（測試正向缺口見 B-3）。
- **抓取執行緒不拿 adb_lock、不呼叫 read_tv、不改鎖定狀態** `:393-424`（不碰 `tv`／`failed_since`／`grace`，只用 `fetch_lock`＋自己的 15 秒 adb）→ 符合；失敗只寫文字 log（`'usage'` 不在 DB 動作表 :58）。
- **回家觸發只有 wake 或 Mac 回家**：`hooks()` :310-311（wake→`arrival()`）、`monitor()` :245-251（離家 ≥`TVGATE_AWAY_SECONDS` 才 `arrival()`）；電視重連走 :281-287 的普通對帳、不呼叫 `arrival` → 符合（有 `test_three_minute...`、`test_network_return...` 佐證）。
- **schedule_due 用時間點日期判週幾** `:443-450` → 符合。
- **last_check 先存再處理** `:252-260`（`last_check=now` 後立刻 `save`，之後才跑 due；存檔失敗就丟棄 due）→ 符合。
- **600 秒窗口** `:267` `now - target > 600`（600 觸發、601 skipped）→ 符合，`:620-643` 精確驗邊界。
- **鑰匙檔錯誤一律 401、改檔即生效** `:486-494`＋`:573-578`（每請求重讀、非 dict／非字串→ValueError→`{}`→401、值空字串濾掉 :491）→ 符合。
- **sqlite 單一連線＋db_lock、DB 壞不影響鎖定** `:316-347`、`:393-424` → 符合。
- **GET／HEAD／POST 路徑** `:549-558`（GET/HEAD 只放行 `/`、其餘 405）、`:544-547`（未支援方法 501→405）、`:621-622`（未知路徑 POST 404）→ 符合；註：未知路徑但鑰匙錯時先回 401（`:576-578`），與 v3 相同、屬既有優先序（非阻擋）。
- **網頁不含鑰匙、讀 hash 不清 hash** `:514` → 符合。
- **`--links` 預設 8790 且印 IP 版** `:693-698`、`:497-509` → 符合。

## 五、安全

- **鑰匙不寫進任何 log**：`log()`（:51-60）只寫 action/result；鑰匙錯誤只寫 `'invalid file'`／`'unauthorized'`（:493、:577），未帶內容；`log_message` 只記請求列（token 在 header，不在 URL）。→ 通過。
- **innerHTML**：全檔**沒有** `innerHTML`／`insertAdjacentHTML`；狀態、設備名、紀錄、排程全部用 `textContent`／`createElement`（:514-523）。log 的 `detail` 甚至沒被渲染。→ 通過。
- 非阻擋：沒有測試斷言「文字 log 不含任何鑰匙字串」（實作正確，但無回歸保護）。

## 六、非阻擋

- **N-1** `test_gate.py:673-677` `test_usage_bad_meta`：`database` 回傳 `rows=[]`，`data_since` 由 `min(row[0] ...)`（`gate.py:483`）算，空表恆 None，這半段斷言恆真。B4 的 `data_since` 已改由 DB 算，meta 值不再使用，故實際不影響。
- **N-2** `gate.py:239` `TVGATE_AWAY_SECONDS` 預設 120、`:429` `TVGATE_FETCH_INTERVAL` 預設 600：測試都用縮短值，規格寫死的「2 分鐘」「10 分鐘」只靠讀碼；README 環境變數段也沒列 `TVGATE_HOME_NET`／`TVGATE_AWAY_SECONDS`／`TVGATE_FETCH_INTERVAL`／`TVGATE_CLOCK`。
- **N-3** `gate.py:577` 的 401 文字 log 與「log 不含鑰匙」沒有測試；`test_devices_and_revocation:364` 比的 `before` 只涵蓋 DB rows（`'token'` 不進 DB），所以那條斷言必然成立。
- **N-4** `gate.py:433-435` `get_rules()` 的 `json.loads` 例外未接：若 `schedule.days` 被寫壞（非 NULL／非合法 JSON），`monitor`（:252 在 try 外）會整個執行緒死掉，`/schedule*` 也會變成連線中斷而非 503。只在「DB 行內容被破壞」才觸發，門檻高。
- **N-5** `gate.py:70-85` 只驗 `until`／`last_check` 的型別，未驗範圍（併入 B-1 陳述，修法相同）。
- **N-6** `test_gate.py:581-585` 第三段（clock=target+602）本身無法區分 last_check 是否有恢復（兩種情況都會是 0 筆新 skipped）；可區分的是第二段 `:573-580`，A6 目的仍達成。
- **N-7** `test_gate.py:193` `limit = 3*INTERVAL`（1.2 秒）與 `:286` `/lock <1 秒` 仍是真時鐘上限，機器負載高時理論上會 flaky；這兩條是 main 版原樣保留，未放寬。
- **N-8** `gate.py:210-215` `arrival()` 會無上限等 `adb_lock`（HTTP `/open` 最多持有 ~19 秒），會延後同一輪的排程切斷；600 秒窗口足夠，非錯誤。
- **N-9** `gate.py:498-504` 鑰匙檔壞掉時 `--links` 不印任何東西且 exit 0，容易被誤認為成功。
- **N-10** `gate.py:402-404` `last_fetch` 取抓取「完成後」的時間（最多比 dump 取樣點晚 15 秒），規格未釘死，影響 ≤15 秒。
- **N-11** 37 個 `test_schedule_validation_and_logs`（:370-387）會用真時鐘建立 07:40／08:00–08:18 的規則；若剛好在那些分鐘跑，monitor 可能多切一次，但該測試不驗授權狀態，不會誤判。

## 七、退修清單 A1–A8／B1–B5／C1–C5

**A（測試）**
- **A1 做到**：`test_gate.py:445-448`（等第 2 次 `usagestats`、`last_fetch` 變大後用 `SELECT count(*) FROM events` 斷言筆數不變）。
- **A2 做到**：`:453-460`（用 `TVGATE_FETCH_INTERVAL=1` 觸發失敗，非 wake；`:456` 等文字 log 出現 `usage fetch failed`；`:457-460` 斷言 tv=locked、未授權、無通知、`/log` 無 grace）。
- **A3 做到**：`:486-498`（自選空閒埠 :486-489、`timeout=5` :490、結束後連線失敗 :497-498、三把不同鑰匙逐把比對 :494-496）。
- **A4 做到**：`:535-537`、`:558-564` 注入 `TVGATE_CLOCK`（gate.py:240-244）；calc／schedule_due 走純函式 `:595-610`、`:691-700`。唯一的真時鐘留在 N-11（不影響斷言）。
- **A5 做到**：`:621` `for age in (10, 600, 601)`，`:634-643` 分別斷言 600 觸發、601 skipped。
- **A6 做到**：`:575-580`（重開前 `last_check=target-1`、重啟時 `target+601` → 恰 1 筆 skipped；若改成 now 則 window 空、0 筆，可區分）；載入端 `gate.py:74`、使用端 `:254`。
- **A7 做到**：`test_schedule_end_to_end`（:558-585）三段各自 `stop→改檔→start`（:559/569、:573/577、:581/583），沒有多餘的 start/stop；測試主體已無 `time.sleep`（只剩 :36/37/46 假 adb、:67 假 hook、:80 `wait_for` 的 0.05）。
- **A8 做到**：`:86` `tempfile.TemporaryDirectory()`（無 `dir=`）；`git status --porcelain -uall` 只有 `tvgate/README.md`、`gate.py`、`test_gate.py`，無 `tvgate/tmp*`。

**B（gate.py）**
- **B1 做到（實作）**：`:197-200`（先 `read_tv` 成功且 `tv=='locked'` 才 INSERT）。**測試只做一半** → 見 B-3。
- **B2 做到**：`:267-278`（>600 先判、`at_home` 為 False 走 `schedule-skipped`／`Mac 不在家`）；單元測試 `:645-659` 精確比對 `assert_any_call('schedule-skipped','Mac 不在家',detail='Mac 不在家')`。
- **B3 做到**：`gate.py:489-491`（空字串以 `if v` 濾掉，其他設備照常；非 dict／非字串→ValueError→`{}`→401）；測試 `:364-368`。
- **B4 做到**：`gate.py:458-466`（`float`＋`isfinite`＋`display` try/except）；`data_since` 已不再讀 meta（:483 由 rows 算）；測試 `:673-677`（data_since 那半恆真，見 N-1）。
- **B5 做到**：`gate.py:483` `min(row[0] for row in rows)`；測試 `:449` 精確比對 `data_since`。

**C（網頁）**
- **C1 做到**：`gate.py:512`（`*{box-sizing:border-box;overflow-wrap:anywhere}`、`section{max-width:100%}`、`.buttons,#days{display:flex;flex-wrap:wrap}`）。
- **C2 做到**：`gate.py:512` `.usage-row{display:grid;grid-template-columns:5em minmax(0,1fr) auto}`＋`:519` 每列用 `p.usage-row` 依序 append 名稱／progress／分鐘。
- **C3 做到**：`gate.py:522`（連續→`週一～週五`、否則 `週一、三、五`）；勾選框中文 `:523`。
- **C4 做到**：`gate.py:521` `x.time.slice(5,16)` → `MM-DD HH:MM`。
- **C5 做到**：`test_gate.py:407` 斷言 HTML 含 `box-sizing` 與 `flex-wrap`。

**上一輪問題是否仍存在**：A1–A8、B2–B5、C1–C5 都已消除；B1 只做到反向（B-3），另新發現 B-1（state.json 非 dict 崩潰，本輪新增 `.get` 造成）、B-2（`expire` 誤記）。
