C-01：前景推算只看 ACTIVITY_RESUMED，會系統性多算 — 高
問題：v4 只存 5 種事件、用「最近 RESUMED 的 package」當前景，遇到 SCREEN_NON_INTERACTIVE／SHUTDOWN／另一個 RESUMED 才結算。CleanYourTV 桌面、HDMI 輸入、按 Home 回 launcher、force-stop＋disable 都不一定產生這三種終止事件；plan 也沒說「另一個 package」是指全部 package 還是只算 6 個追蹤 App。若只存 6 個，切到桌面就永遠結不了案。
為什麼重要：每次鎖定（播放中 force-stop，2 秒回桌面）後面都拖一條幻影收視到下次抓取；小孩切 Home、切 HDMI、長時間無事件（睡著、待機）全部算進 YouTube／Netflix。Owner 拿錯誤數字做判斷。
建議改法：事件儲存至少保留全部 package 的 ACTIVITY_RESUMED（顯示才過濾 6 個）；任何 ACTIVITY_PAUSED／STOPPED、launcher／桌面 RESUMED、force-stop／disable 動作本身都視為終止；無終止事件的開放段加硬上限（例如超過最後事件 15 分鐘就封口，不算到現在）；SPEC 明寫「估計值±」。

C-02：待機／HDMI／CleanYourTV／長時間靜默沒有終止事件 — 高
問題：待機（mWakefulness=Asleep）是否一定吐 SCREEN_NON_INTERACTIVE 未驗證（樣本前 80 行全是 STANDBY_BUCKET 噪音，一個 RESUMED 都沒有）；HDMI 輸入、CleanYourTV 閒置掛著不產生任何被追蹤事件；plan 的「最後一段若螢幕仍亮算到現在」根本沒定義怎麼判斷螢幕亮。
為什麼重要：電視開著沒人看一整個下午，會變成一整個下午的 YouTube 時數；這是最常見的家用場景。
建議改法：開放段必須有「螢幕亮的證據」才延長（最後事件是 RESUMED 且之後無 NON_INTERACTIVE／SHUTDOWN，且最後一次抓取成功在 N 分鐘內），否則封口在最後事件時間；加一個測試：RESUMED 後靜默 3 小時＋輪詢成功，時數不得增加 3 小時。

C-03：YouTube 被停用期間的語意沒定義 — 中
問題：鎖定中 YouTube 是 disable 狀態，不可能有它的 RESUMED；但舊的 RESUMED＋無終止事件會讓鎖定期間也被算成收視。等於「鎖得越久，YouTube 時數越高」。
為什麼重要：直接誤導 Owner（以為鎖了還在看），且與 v3「鎖定」語意矛盾。
建議改法：計算時把已知鎖定區間（lock／expire／schedule 事件）扣掉，或開放段遇到 disable 期間直接截斷；至少加測試：鎖定 1 小時無事件 → YouTube 時數為 0。

C-04：「算到 min(現在, 最後抓取時間)」寫錯 — 高
問題：抓取成功時 last_fetch≈now，取 min 沒意義；抓取失敗幾小時時，last_fetch 凍結，幻影段反而凍結在舊時間點。真正該做的是「抓取失敗／過期就不延長開放段」。
為什麼重要：Mac 離家、電視離線、adb 卡住時，數字不是多算就是少算，且行為不穩定，验收看到的數字不可重現。
建議改法：開放段只在「最後一次抓取成功且距 now < 15 分鐘」才延長到 now，否則封口在最後事件；last_fetch 為 null（從未成功）時所有開放段封口。

C-05：UNIQUE(ts,type,package) 去重鍵太粗＋全量 dumpsys 每次抓 — 中
問題：同秒同 type＋package 但不同 activity／class 的兩行會被合併；dumpsys 全文幾百行每 10 分鐘抓一次，TV 時鐘跳變、格式微調就去重失效（重複算或漏算）。更大問題見 C-06：抓取若走同一條 adb 路徑會擋住鎖定。
為什麼重要：去重錯＝數字錯；抓取慢＝鎖定慢（v3 對帳 30 秒＋停回 60 秒目標會破）。
建議改法：去重鍵用整行 hash 或 (ts,type,package,class)；抓取放背景執行緒、只讀、不持 adb_lock（或獨立 lock），失敗只記 last_fetch 不影響 apply；加併發測試：dumpsys 卡住時 /open、/lock、/status 照常在時限內回。

C-06：sqlite 在 ThreadingHTTPServer＋背景執行緒下會鎖死 — 高
問題：plan 只說「sqlite3 標準庫」，沒說 WAL、busy_timeout、單寫鎖、每執行緒連線。HTTP 每連線一執行緒＋主迴圈＋抓取執行緒同時讀寫，遲早遇到 `database is locked` 或 `check_same_thread` 崩潰；現有 gate.py 只有 state.json＋RLock，完全沒 DB 併發設計。週／月查詢是全表掃＋逐段重算，更放大競爭。
為什麼重要：服務崩潰或 /open 被 DB 卡住＝小孩不能看或不能鎖，比數字不準更糟；且是必現的工程缺陷，不是機率問題。
建議改法：單一 db_lock 包住所有 sqlite 操作、WAL＋`PRAGMA busy_timeout=5000`、(ts) 索引、events 保留期（如 40 天）＋定期 prune；WO-T3 加併發測試：/open＋/usage＋抓取同時打。

C-07：排程「上次檢查＜規則≤現在」＋重啟／跨午夜語意矛盾 — 高
問題：主迴圈 5 秒一次沒問題，但「Mac 睡著或沒跑就跳過、不補做」與「跨過午夜的檢查區間正確」測試打架：重啟後 last_check 若重置為啟動時間，一個兩天的區間會把週一規則在週三觸發；若只比「今天」的規則，跨午夜 00:00 前後的規則歸屬（算哪一天的 days）未定義；時區只寫「本機時間」，沒寫 timestamp 存法。
為什麼重要：07:40 上學切斷是最重要的自動化，打錯一天或重啟後誤觸／漏觸，Owner 不會信任排程；且這是可被小孩利用的繞過（見 C-08）。
建議改法：把觸發函式簽名釘死（rule、last_check epoch、now epoch→是否觸發＋觸發哪一個 calendar occurrence）；啟動時 last_check=now（永不回補）；每個落在 (last_check,now] 的 occurrence 用它自己的日期查 days；多天區間只觸發區間內、測試釘死「兩天沒跑→不補做」；timestamp 一律存 epoch，顯示才轉 Asia/Taipei。

C-08：排程不補做＝已知的免費缺口，沒寫進失效表 — 中
問題：v3 失效表誠實列了闔蓋／關機／斷網會怎樣；v4 排程「Mac 睡著就跳過」等於 07:40 時 Mac 沒醒＝當天不切，但 plan 沒把它當 bypass 寫明，WO-T4 也沒測。
為什麼重要：Owner 以為「設了 07:40 就一定切」，實際是最容易失效的那一次（早上 Mac 蓋著）。
建議改法：失效表加一列「規則時間 Mac 睡／服務沒跑→跳過不補，當天等於沒設」；WO-T4 加測或至少文件警告：重要規則前確保 Mac 醒著（或認列為接受風險）。

C-09：三把鑰匙全是管理員＋URL／AirDrop／localStorage 鏈 — 中
問題：爸爸／媽媽／MacBook 三把鑰匙權限完全一樣（都能 open／lock／改排程）；`--links` 把 token 印到 stdout，經訊息／AirDrop 轉發留在聊天記錄，localStorage 明文存放，被同網段側錄（v3 已承認區網明文）或小孩拿到任一連結＝完全接管。沒有撤銷／輪換流程（小孩看過媽媽手機一次就永久有效）。
為什麼重要：家用防繞過的核心就是「小孩拿不到控制權」；現在是一把鑰匙外洩＝滿盤輸，且事後無法只廢一把。
建議改法：至少文件寫明「token＝完整管理權」＋輪換步驟（改 tokens.json 任一把＋重啟服務＋重發連結）；gate 啟動時檢查檔案 600；`--links` 加 IP fallback（mDNS 在部分 iPhone 不通）；X-Token 絕不寫入 log；長遠再做唯讀鑰匙，現在不要。

C-10：GET / 未鑑權＋其他 GET 405 的邊界沒釘死 — 低
問題：`GET /` 回整頁 HTML（內嵌 JS 含端點形狀），區網任何人可拿；plan 說「其他 GET 仍 405」，但 /usage、/log、/schedule 用 POST＋query（`?range=`、`?limit=`），worker 很容易把 GET /usage 做成 404 或漏成 200；/log limit 非數字／負數／>200 是 clamp 還是 400 也沒寫。
為什麼重要：小洞，但 WO-T3 只測 `GET /status 仍 405`，其他路徑隨便寫都過（假通過）。
建議改法：SPEC 列出每個路徑×方法的期望碼（GET /＝200，GET 其他一律 405）；limit 非法→400，>200→截到 200；測試逐條打。

C-11：過度設計：週／月＋每日明細 — 中
問題：TV 只留 24 小時事件，週／月數字依賴 Mac 連續輪詢一個月不斷線；斷一次就少一塊，但 UI 照樣畫出權威長條圖。Owner 真正要的是「今天看了多久」。
為什麼重要：低維護＋不過度設計是 Owner 明確原則；週／月是工人數天工時＋日後 debug 成本，且先天不準。
建議改法：砍掉本月（或週／月都砍，只留今天＋最近 7 天合計一行）；若保留，標題寫「估計（缺 X 天資料）」並回缺測天數。

C-12：過度設計：7 種 log action＋device 歸因 — 低
問題：open／lock／schedule 之外，expire／away／grace／manual-relock 全是系統自動行，記進同一張表還要在 UI 顯示 30 筆；對帳每 30 秒跑一次，manual-relock 去重／節流沒寫，小孩一直重開＝log 洗版；v3 本來就有檔案 log，現在是雙軌。
為什麼重要：工人要為每種 action 寫測試，Owner 手機上看到一堆「系統」雜訊。
建議改法：sqlite 只記 open／lock／schedule（＋expire 一行），away／grace／manual-relock 回檔案 log 就好；或給自動行加節流（同 action 5 分鐘內只記一次）。

C-13：WO-T3 完成標準可假通過 — 高
問題：①「手寫小型事件分鐘數完全符合」——符合的前提（C-01～C-04 語意）還沒定，工人自己定數字自己過；②「真實樣本能解析、分鐘數≥0」——回全 0 也過；③ 排程函式簽名（型別、週一＝1 還是 Python weekday()、時區）沒釘，測試可硬編碼過；④「Opus 審定時跑」違反專案鐵律（施工後必須外部模型獨立 review，AGY→Codex），自己審自己；⑤ 120 秒＋真子程序＋時間窗測試（07:39:58→07:40:03）抖動必有 flake。
為什麼重要：WO 照現在寫法，工人交什麼都會「全過」，挑戰與验收都建立在沙上。
建議改法：SPEC 先定 C-01～C-04 語意並給出小資料的期望分鐘表（由挑戰裁定）：含 Home 結案、force-stop 結案、跨午夜拆分、靜默不延長四個必測；真實樣本改斷言「至少解析出 N 個 RESUMED 且 YouTube 分鐘在 [a,b] 區間」（先跑一次看實際分布再填數）；排程函式簽名＋週編號＋epoch 秒釘死；審定改外部 reviewer（AGY gemini-3.8-flash-high 先，失敗才 Codex），且要求跑 Python 合約測試＋給 VERDICT；時間窗測試用注入時鐘，不用真 sleep。

C-14：WO-T3 缺的測試（併發、時區、遷移、增長） — 中
問題：沒要求：sqlite 併發、adb 抓取不擋鎖定、事件 naive 時間解析（Mac 改時區／NTP 偏時怎辦）、舊單一 tvgate.token 還在＋新 tokens.json 缺失時的行為（必須 401 不 crash）、events 無限增長（prune）、重啟後排程表還在但 last_check 重置（重啟恰在規則前會漏）、/usage 初次 last_fetch=null 的形狀。
為什麼重要：這些全是上線後半夜打電話的項目，現在不測以後 Owner 付。
建議改法：WO-T3 追加：併發打 /open＋/usage＋抓取；舊 token 檔存在時忽略；tokens.json 壞掉→全 401；events 超保留期被清；重啟→排程仍在、last_check 不回補；last_fetch null 回 null 不回 1970。

C-15：WO-T4 验收缺校準、缺邊界、範圍蔓延 — 高
問題：①「今天有數字且與印象相符」是主觀題，首日數字必含幻影（C-01），Owner 會把灌水基線當真；② 2 分鐘後排程只測 happy path，沒測週幾對應、刪除後不再觸、重啟後排程還在、Mac 睡過規則時間（必跳過）；③ 三設備各開 15 分鐘沒測無鑰匙開頁提示、書籤無 #t、輪換後舊連結失效；④ 結尾「接著做 WO-T2 還沒做的實機項目」把 v3 欠債（闔蓋、重開、手動啟用秒數）併進 v4 验收，v4 永遠關不了。
為什麼重要：验收當天看起來全過，上學日第一個 07:40 就露餡。
建議改法：验收加校準腳本：看 10 分鐘 YouTube＋10 分鐘 Home，誤差 ±2 分鐘才算過；排程測新增→觸發→紀錄 device=排程→刪除→隔天不觸（用短週期或注入時鐘，不要真等一天）；三設備測含「無鑰匙提示」與「錯鑰匙 401 不寫 log」；WO-T2 欠債另開工單，不擋 v4 關閉；寫死回滾（新 tokens 生效前保留舊 token，失敗切回 v3）。

