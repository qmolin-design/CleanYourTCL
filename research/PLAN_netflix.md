# PLAN：控管加入 Netflix＋v4 按鈕防連按（2026-10-31）

狀態：APPROVED（挑戰 1 輪，TERRA 11 條，裁定見檔尾；檔尾裁定優先於正文）
Owner 裁定（2026-10-31）：
1. v4 網頁「按下後到結果回來前，按鈕先變灰，不能連按」。
2. 控管 App 由 YouTube 擴大為 YouTube＋Netflix；**v4、v5 都加**。
3. v5 額度：**YouTube 與 Netflix 共用同一份額度**（上限、+15/+30/+60、歸零都不變）。

背景：14:15 Owner 連按「開放 60 分」6 次，每次覆寫授權時間，排隊中的較早請求回「授權已變更或取消」。
Netflix 套件：`com.netflix.ninja`（research/healthcheck 與 TGuard log 已見；tvgate APPS 表已有）。

## 不做
- 不做 Netflix 獨立額度／獨立按鈕。
- 不改 cfquota 回報格式（`yt` 仍只有 enabled／disabled／unknown，`fg_yt` 欄位名不改；語意擴大為「受控 App」）。
- 不改 tkey（T 鍵只叫 YouTube）。
- 不處理「Netflix 沒安裝」：這台電視有裝；之後若移除再說。

## WO-N1（v4：tvgate/gate.py）
- 動到的檔案：`tvgate/gate.py`、`tvgate/test_gate.py`、`tvgate/README.md`（只改提到 YouTube 為唯一控管的句子）。
- 要求：
  1. 受控套件改成兩個：`com.google.android.youtube.tv`、`com.netflix.ninja`。
  2. 鎖：每個受控套件都 `am force-stop` 再 `pm disable-user --user 0`。開：每個都 `pm enable --user 0`。
  3. `read_tv`：全部受控套件都在停用清單 → `locked`；全部都不在 → `open`；部分 → 第三種狀態（例如 `mixed`），讓 `apply()` 視為與目標不同而重做。網頁狀態文字對應「部分鎖定」。
  4. `foreground()`：任一受控套件是 mResumedActivity 即為 True（回家寬限要涵蓋 Netflix）。通知文字改「有人正在看 YouTube 或 Netflix，要停請按立即鎖」。
  5. 鎖定確認後的 `GATE_LOCK` 事件：每個受控套件各寫一筆（用量統計 `calc` 才會在鎖定時截斷 Netflix 區段）。
  6. 網頁：`action()` 送出前把 `.buttons` 內四個按鈕全部 `disabled=true`，結果回來（成功、失敗、例外）後一律恢復；按鈕灰色要看得出來（CSS `button:disabled` 降低不透明度）。
  7. 既有行為不變：到期、排程、休眠、離家開放、SIGTERM 開放、寬限期等邏輯只是「一個套件」→「全部受控套件」。
- 完成標準（Opus 會親自跑）：
  - `cd tvgate && python3 -m unittest test_gate` → 全部 OK，測試數 ≥ 44＋新增。
  - 新增測試至少涵蓋：①鎖定後兩個套件都被 disable-user、開放後兩個都 enable（看 fake adb calls）；②只有一個被停用時 `/status` 的 tv 不是 open 也不是 locked，且下一次 apply 會把它修正；③Netflix 在前景時，回家寬限會觸發（不立即鎖）；④網頁 HTML 含按鈕停用邏輯（`disabled`）。
  - fake adb 要能分別記兩個套件的停用狀態（改 test_gate.py 裡的 ADB_SCRIPT）。
- 禁止事項：不動 v5 檔案（tvagent/、cfquota/）；不改 port、token、log 格式、DB schema；不 commit 到 main（在指定 worktree 分支提交）。

## WO-N2（v5：tvagent＋cfquota 文字）
- 動到的檔案：`tvagent/src/com/example/tvagent/Quota.java`、`AgentService.java`、`tvagent/test/QuotaTest.java`、`tvagent/README.md`、`cfquota/lib.mjs`（只改 alert 文字「無法控制 YouTube」→「無法控制 YouTube／Netflix」）、cfquota 若有測試比對該字串一併改。
- 要求：
  1. `Quota` 受控套件集合＝YouTube＋Netflix。`usage()`：
     - type 1（RESUMED）：記下目前前景套件 `cur`，`fg = 受控(pkg)`。
     - type 2／23（PAUSED／STOPPED）：只有當 pkg 受控且（`cur` 尚未知 或 `cur` 就是 pkg）時才 `fg=false`。理由：YouTube→Netflix 直接切換時，YouTube 的 STOPPED 事件會排在 Netflix RESUMED 之後，不能把 Netflix 的前景清掉。
     - 其他 type 照舊。`ytFg`、`yt_fg` 欄位名不改（語意＝受控 App 前景）。
  2. `AgentService` 狀態探測：
     - 停用狀態：兩個都停用 → `disabled`；兩個都啟用 → `enabled`；只停一個 → 回報 `enabled`（有東西可看），但動作判斷要能修正：額度 > 0 且有任何一個停用 → UNLOCK；額度 = 0 且有任何一個啟用 → LOCK／END。用一個額外欄位（例如 `Status.mixed`）或等效作法，`Quota.decide` 的對應測試要寫。
     - 只有兩個都停用時才跳過 `dumpsys activity activities`；前景判斷：mResumedActivity 行含任一受控套件即 true。
  3. 動作：LOCK／END 對兩個套件都 `am force-stop` 再 `pm disable-user --user 0`；UNLOCK 對兩個都 `pm enable --user 0`；verify 要確認兩個都達到目標，否則照舊丟 `verify` 例外。
  4. README 第一段改成 YouTube＋Netflix 共用額度。
- 完成標準（Opus 會親自跑）：
  - `sh tvagent/test.sh` → 最後一行 `ALL PASS`。
  - 新增 QuotaTest 至少涵蓋：①Netflix 前景＋螢幕亮會扣額度；②YouTube→Netflix 直接切換（YT PAUSED、NF RESUMED、YT STOPPED）時前景不中斷；③Netflix→桌面（NF PAUSED、launcher RESUMED）停止扣；④mixed 狀態下 decide 在額度 > 0 回 UNLOCK、額度 = 0 回 LOCK（或 END）。
  - `sh tvagent/build.sh` 成功產出 apk（Opus 跑）。
  - cfquota：`grep -n '無法控制' cfquota/lib.mjs` 顯示新文字；cfquota 有測試就跑（`ls cfquota` 看 test 指令）。
- 禁止事項：不動 v4（tvgate/）；不改回報欄位名或允許值；不改 TGuard 名單、appops 設定、金鑰、testdata；不安裝到電視、不部署 Worker（Opus 之後處理）；不 commit 到 main。

## 部署（Opus，不委派）
- v4：合併後 `launchctl kickstart -k gui/$(id -u)/com.example.tvgate`，看 log 與 `/status`；電視開機時實測：立即鎖 → 兩個都在 `pm list packages -d`；開放 → 都不在。
- v5：電視開機、adb 連線後才裝：`adb install -r tvagent/tvagent.apk`，確認 agent.log `setup=11111`、`APP_ASSOC_START allow`；`npx wrangler deploy`（cfquota 文字）需確認部署方式後再做。電視沒開就停在這步，回報 Owner。
- TESTPLAN（research/batches/v5/TESTPLAN.md）補 Netflix 測項；C4 第 6 項比對改成 v4 的 YouTube＋Netflix 前景分鐘合計。

## 挑戰裁定（TERRA high，1 輪；Opus 裁定，優先於正文）
1. 採納（文件）：v5 現在是影子模式（enforce=false，只記 dry-*），**v4 是唯一控制者**，直到 TESTPLAN B 切換。部署段落與 TESTPLAN 註明。
2. 採納：部署前 Opus 實機先單獨試 Netflix：停用 → 桌面／Netflix 鍵打不開 → 啟用 → 能開、仍是登入狀態。任何一步不如預期就停，回報 Owner。
3. 採納，改設計（取代正文 WO-N2 第 1 點）：`usage()` **type 23（STOPPED）不再影響 fg**；type 2（PAUSED）只有 pkg 受控時才 `fg=false`；type 1 照「`fg = 受控(pkg)`」。理由：Android 一定先送舊 App 的 PAUSED、再送新 App 的 RESUMED，STOPPED 才會排在後面；只看 PAUSED 就不需要跨心跳記住前景套件。若既有測試依賴 23，改測試並在回報寫理由。新增測試：分兩次 `usage()` 呼叫，第二次只收到 YouTube 的 STOPPED，Netflix 前景要延續。
4. 採納，明訂：`Status` 加 `mixed`（只停一個）。`decide()` 要拿得到 mixed：額度 > 0 且（disabled 或 mixed）→ UNLOCK；額度 = 0 且（enabled 或 mixed）→ LOCK／END（fg&&screen 才 END）。動作成功後 `mixed=false` 並更新 `yt`；**UNLOCK 成功時 fg 只有在原本是兩個都停用時才設 false**（mixed 時保持探測值）。動作失敗照舊 `yt=unknown`。同一心跳第二次 `act()` 要用更新後的 Status。測試：兩種 mixed 方向 × 正額／零額，以及 enforce=false 回 dry-*。
5. 採納：cfquota 頁面與 alert 文字的「YouTube」改成「YouTube／Netflix」（如「今天 YouTube／Netflix 用了」）。API 欄位名稱與允許值不改。已知限制寫進 cfquota README：mixed 回報為 enabled，事件紀錄的 lock／unlock 不分是哪一個 App。
6. 採納：v4 `GATE_LOCK` 改為「讀回確認哪個套件已停用就寫那個」，即使整體是 mixed。新增測試：一個成功一個失敗 → 只寫成功那個；重試成功 → 補寫另一個。
7. 採納，改設計：**每個動作只用一次 adb 呼叫**，不增加呼叫數：
   - 鎖：`['shell', 'am force-stop A; am force-stop B; pm disable-user --user 0 A; pm disable-user --user 0 B']`（可以拆成 force-stop 一次、disable 一次，維持原本兩次）。
   - 開：`['shell', 'pm enable --user 0 A; pm enable --user 0 B']`。
   fake adb 要能解析這種合併指令。既有慢速／逾時測試必須照樣通過。
8. 部分採納：`action()` 在送出前把四個按鈕 `disabled=true`，用 `finally` 在 api 回來後恢復（不等 refresh）。範圍只限同一頁，不處理其他分頁或另一支手機（後端照舊）。不加瀏覽器自動化測試（過度設計）；改由 Opus 實際開頁面手測：按下後按鈕變灰、回來後恢復。test_gate 只檢查頁面 JS 有 `disabled=true` 和 `finally`。
9. 駁回：AgentService 綁 Android API，為了測試而重構過度設計。改成實機驗收，寫進 TESTPLAN C：切換後兩個都鎖、兩個都開、讀回 `pm list packages -d`。
10. 採納（文件）：TESTPLAN C4 第 6 項比對條件限定為同一時段、螢幕亮、額度全程足夠、無時鐘跳變；比較對象是 v4 YouTube＋Netflix 前景分鐘合計，誤差維持 ≤2 分。QuotaTest 加「YouTube 看完接 Netflix，共扣同一份額度，扣到 0 時 decide 回 END」。
11. 採納：驗收以具名情境為準。cfquota 測試指令：`node --test cfquota/lib.test.mjs`。

### 驗收指令（定案）
- WO-N1：`cd tvgate && python3 -m unittest test_gate` → OK（含上列新增情境）。
- WO-N2：`sh tvagent/test.sh` → `ALL PASS`；`sh tvagent/build.sh` 成功；`node --test cfquota/lib.test.mjs` → fail 0。
