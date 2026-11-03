PASS

## 一、逐項對照 WO-T3「要做」1～4

**1. gate.py 休眠狀態 — 做到**

| 子項 | 證據 |
|---|---|
| sleep（ok/fail 都算）→ 休眠中 | `gate.py:329-333`：先 `with state_lock: sleeping = True`（331），再 `apply(forced_open=True)`，所以 ok/fail 都已在休眠狀態 |
| 休眠中不對帳停回 | `gate.py:174-175`（apply 入口）、`gate.py:202-203`（拿 state_lock 後再檢查一次）、`gate.py:310`（monitor 連 `read_tv()` 都不做） |
| 休眠中不到期鎖 | `gate.py:287` `expired = not sleeping and ...`，且 apply 本身被 174/202 擋住 |
| 休眠中不排程切斷 | `gate.py:296-297`，skip 發生在 `until/grace` 被清（301-305）之前，授權不會被吃掉 |
| 休眠中不抓使用時間 | `gate.py:426-429` `if sleeping: return` |
| 排程落在休眠中記 schedule-skipped＋detail「Mac 休眠」 | `gate.py:296-297`；另有 `target <= sleep_ended` 分支（296），處理「整段休眠沒跑 monitor」時醒來才發現的排程，語意正確 |
| 休眠中到期 → 不鎖，wake 後記 expire 並套用（前景 YouTube 走寬限） | `gate.py:287-292` 延後 expire；`gate.py:288-292` 移除原本的 `grace = False`（diff `-grace = False`），使 wake 給的 `grace='pending'/'watching'` 不會被 expire 清掉 → 前景 YouTube 仍走寬限（`gate.py:181-200`） |
| wake 離開休眠 | `gate.py:225-239`：`arrival(waking=True)` → `sleeping = False`（233），且 `sleep_ended = schedule_clock()`（232）只在此時更新 |
| SIGTERM 不變 | diff 未動 `terminate()`／`main()`；`main()` 收尾的 `apply(forced_open=True)` 是 forced，不受 174 影響 |
| 休眠狀態只在記憶體 | `save()`（`gate.py:126-138`）只寫 `until`/`last_check`；`sleeping`/`sleep_ended` 只宣告在 `gate.py:40-41` |

**2. test_gate.py — 做到**

- 競態段改成確定性：`test_gate.py:267-287`。先 `sleep` 並等到 hook 回 `ok`（268-269）才改假電視（270-271），休眠旗標此時保證已設；再用 `sleeping_cycles()` 推進（274）。
- 「至少 3 個週期」不是裸 sleep：`test_gate.py:153-155` 用 `state.json` 的 `last_check >= 呼叫時 + 3×INTERVAL` 當條件，而 `last_check` 只在 `gate.py:281-283` 的 monitor 迭代內推進，因此成立時 monitor 確實又跑了 ≥3 次。斷言在之後（275-277）。
- 新增休眠中到期測試：`test_gate.py:332-348`（不鎖 341、無 expire 342、until 未被清 343；wake 後鎖 345、expire 恰 1 筆 346-348）。
- 新增休眠中排程測試：`test_gate.py:350-373`（不切 368-370、恰 1 筆 skipped 372、detail 含「Mac 休眠」373）。
- 另加 `test_sleep_pauses_usage_fetch`（375-387）與兩個 PureTest（817-833、835-851）。

**3. README — 做到**：`README.md:55` 新增「Mac 休眠（含 Power Nap 暗喚醒）期間服務不會鎖電視。」另外把 57 的舊句改寫成「休眠中錯過的排程記錄為略過；服務停止後 10 分鐘內恢復會補切…」，與新行為一致（原句「Mac 睡著…10 分鐘內恢復會補切」已不成立）。

**4. 其他測試斷言未被刪除／放寬 — 做到**：`git diff -U0 -- tvgate/test_gate.py` 的刪除行數為 **0**（全檔只有新增）。`gate.py` 唯一被移除的斷言相關行是 `grace = False`（見上，屬修正而非放寬）。

## 二、競態與鎖（無阻擋問題）

- 休眠旗標的**寫入**都在 `state_lock` 內：`gate.py:330-331`（sleep）、`gate.py:228-233`（wake）。monitor 的關鍵讀取 `gate.py:287` 也在 `state_lock` 內 → sleep/expire 互斥，不會「睡前判到期」。
- sleep 與 monitor 同時：`apply()` 有**兩道**檢查（174 在拿 adb_lock 後、202 在拿 state_lock 後）。第二道是關鍵：任何在 sleep 前排隊等 `adb_lock` 的 monitor apply，拿到鎖後一定重讀到 `sleeping=True` 而放棄。因此 monitor 最壞只在 sleep 的 forced open 之前短暫鎖一下，隨後被 forced open 蓋回；`adb_lock` 序列化保證 **hook 回 ack 之後不可能再有鎖命令在飛**，所以 `test_gate.py:276/298` 的 `assertNotIn(FORCE_STOP, ...)` 是結構性成立、不是機率。
- wake 與到期同時：`expire` 只在 `gate.py:288-292` 一處、同一 `state_lock` 內清 `until` → 最多記 1 次。wake 先到（記 grace）或 expire 先到（wake 再判未授權給 grace），兩種順序結果都正確、不會漏記或重複。
- 鎖順序：`arrival()` 與 `apply()` 都是 adb_lock → state_lock；monitor 的 `state_lock` 區塊內不取 adb_lock（`gate.py:301-307` 的 apply 在 with 之外）→ 無死鎖。`arrival()` 在雙鎖內做 `schedule_clock()` 檔案 I/O，只延遲、不崩。
- API 在休眠中被呼叫不會崩：`/open` → `apply()` 立即回 False → 走 `gate.py:686-694` 的 `note` 分支回 200；`/lock` → 200 `ok=false`＋`電視未確認鎖定`；`/status`、`/usage`、`/schedule` 只讀 DB。見下方非阻擋第 1 點。

## 三、新測試是否假通過／flaky（無阻擋）

- 不會假通過：把 `gate.py` 的 174/202/287/296/310/428 任一還原，四個新測試都會失敗（relock／FORCE_STOP／expire／`schedule` 動作會出現）。
- `sleeping_cycles()` 不是真時鐘 sleep，且 `state.json` 讀不到就 timeout 失敗，不會靜默通過。
- `test_gate.py:285` 的 `INTERVAL + .3`（0.7s）我算過：wake 的 grace 分支在 `gate.py:190/200` 提早 return，**不會更新 `last_apply`**，此時 `last_apply` 還是睡前 forced open 的時間，所以 `due` 已為 True，下一個迭代（≤0.4s + adb 時間）就鎖 → 餘裕約 0.15～0.3s，可接受（非阻擋）。
- `test_sleep_pauses_usage_fetch:382` 的 `before` 取樣點在 ack 之後，理論上仍有一個「fetch 已通過 `if sleeping` 檢查但還沒 Popen」的微秒級窗口，機率可忽略（非阻擋）。

## 四、新路徑是否會弄死 monitor／服務（無阻擋）

- `schedule_clock()`（`gate.py:257-261`）捕捉 `KeyError/OSError/ValueError`，且 monitor 整體仍有 `except Exception`（`gate.py:317-318`）→ 無新增例外外洩路徑。
- `apply()` 的兩處提前 return 都在 `try/finally` 內（`gate.py:171-175`、`222-223`）→ adb_lock 一定釋放。
- hooks 執行緒在 sleep 時多做的 `with state_lock` 不會拋例外。

## 非阻擋（不構成 FAIL，但建議記進交接）

1. `gate.py:174`、`gate.py:202`：這道鎖把 **HTTP 的 `/lock`／`/open` 也一起擋掉**，不只 monitor。休眠中家長按「立即鎖」會回 `ok=false`（`gate.py:697-700`），`until` 卻已被清空（`gate.py:669-677`），wake 後若前景是 YouTube 反而進入寬限（`gate.py:236-237`）。WO 只要求「monitor 不鎖」，且 `README.md:55` 已把「休眠中不鎖電視」寫成限制，故與文件一致；但「家長仍可立即鎖」（`README.md:58`）在此情境實際不成立，建議 /lock 走 forced lock 或在 UI 明講。
2. `gate.py:331` + `gate.py:339-340`：**沒有 wake 就沒有安全逾時**。若 hook 重啟的 ~1 秒窗口剛好漏掉 `didWake`，`sleeping` 會永久為 True，服務自此不再鎖電視（直到下一次 sleep/wake 對）。這正是本 WO 要處理的「收不到 didWake」情境，建議加保險（例如記錄進入休眠的 monotonic 時間，超過 N 小時或 hook 重啟時強制清除）。
3. `research/PLAN_v4_ui.md:76` 仍寫「（10 分鐘內醒來仍會切）」，與新行為（`gate.py:296` 只要 `target <= sleep_ended` 一律略過，不看 600 秒）衝突。WO 明確要求「休眠中一律不切」，實作與 `README.md:57` 都對，**PLAN 未同步**（WO 禁止改 PLAN）→ 規格文件待補。
4. `gate.py:296`：`sleeping`／`sleep_ended` 在 `state_lock` 外讀（影響已分析為良性）。另 `sleep_ended` 若被寫成 `inf`／`nan`（只有 `TVGATE_CLOCK` 測試環境變數能造成）會永久略過排程或完全不略過——非正式路徑。
5. `gate.py:310`：休眠中連 `read_tv()` 都停，`/status` 的 `tv` 與「最後確認」會凍結在睡前值；文件未提，屬觀感問題。
6. `README.md:57` 改了既有句而非只「補句」，但改後內容才與 `gate.py:296` 一致，屬必要更正。
