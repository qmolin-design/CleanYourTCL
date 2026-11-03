1. **高｜扣額公式混淆「經過時間」與「觀看時間」**：計畫把整個心跳區間與爸媽時段的重疊秒數，直接從觀看秒數扣掉。例：60 秒心跳中，前 30 秒是爸媽時段但沒看電視，後 30 秒才觀看；公式得到 `30−30＝0`，小孩時段完全沒扣額度。**建議**：按實際前景與螢幕事件切分觀看區間，只排除落在爸媽時段內的觀看秒數；測試觀看分別落在邊界前、後及跨邊界的情況。[計畫第 25 行](/Users/example-owner/TV/research/PLAN_adult.md:25)、[AgentService.java 第 123 行](/Users/example-owner/TV/tvagent/src/com/example/tvagent/AgentService.java:123)

2. **高｜手機關閉後拔網路，可保留整段免費時間**：電視會離線沿用已取得的 `adultEnd`；例如早上已收到到 16:00 的時段，家長按關閉後，小孩拔網路就可繼續看至 16:00。計畫的「一分鐘內停用」只在連線正常且同步成功時成立。**建議**：明定離線授權最長有效期，定期成功 sync 才續期；加入「關閉前後斷網」驗收。這會取捨長時間斷網時爸媽能否繼續看，應寫清楚。[計畫第 23、58 行](/Users/example-owner/TV/research/PLAN_adult.md:23)

3. **高｜同步後才計算舊區間，可能把免費時間扣成額度或反過來**：計畫只保存結束時間，沒有保存電視何時開始知道該時段，也沒定義關閉後如何結算上一個心跳區間。同步前先扣、同步後覆寫截止時間的現有順序，以及同步延遲或時段被關閉，都會影響邊界。**建議**：明確保存本機已確認的有效起訖區間，在套用新回應前先依舊區間結算；測試開始、到期、關閉及延遲回應的交界。[AgentService.java 第 131 行](/Users/example-owner/TV/tvagent/src/com/example/tvagent/AgentService.java:131)

4. **高｜重開機判斷依賴可能失效的 `boot_count`**：現有程式讀取失敗時回傳 `-1`；若重開機前後都是 `-1`，僅比較是否不同無法辨識重開機，舊的 `elapsedRealtime` 截止值可能被重新當作未過期，甚至延長免費時間。**建議**：`boot_count` 不可取得或不可信時，清除離線爸媽授權；也檢查本次 elapsed 是否倒退。加入未知 boot count 與重開機測試。[AgentService.java 第 119 行](/Users/example-owner/TV/tvagent/src/com/example/tvagent/AgentService.java:119)、[計畫第 48 行](/Users/example-owner/TV/research/PLAN_adult.md:48)

5. **高｜只在有效額度跨門檻時重新 `act()`，會漏掉加值後應重排的警告**：例如原有餘額 100 秒，加值 60 秒後仍在 `1–300`，門檻沒變，但原本已提示過的狀態及下一次鬧鐘必須按新餘額正確處理；更直接的是原本 `balance` 改變時會再執行 `act()`，計畫改成只看門檻，可能改掉既有行為。**建議**：保留原本 `balance/enforce` 變更觸發條件，再加上爸媽時段相關變化，並測試同步加值、歸零、解鎖與警告狀態。[AgentService.java 第 142 行](/Users/example-owner/TV/tvagent/src/com/example/tvagent/AgentService.java:142)、[計畫第 49 行](/Users/example-owner/TV/research/PLAN_adult.md:49)

6. **中｜每台 App 的使用時間會被錯誤歸屬**：按 YouTube 優先截斷，不能判斷哪個 App 在爸媽時段內。例：免費時段看 YouTube 30 秒，之後扣額時段看 Netflix 30 秒，計畫會記成 YouTube 30 秒。**建議**：與第 1 點一起按實際事件及時段交界切分，再分別累加兩個 App；測試跨邊界切換 App。[計畫第 26 行](/Users/example-owner/TV/research/PLAN_adult.md:26)

7. **中｜關閉請求的去重方案不可執行或不具冪等性**：`events` 沒有 `req_id` 欄位或唯一約束；而「meta 值相同」無法防止舊請求在取消關閉或跨時段後重送、再次改變狀態。**建議**：使用可原子去重的持久化請求 ID，連同狀態變更及事件在同一批次處理；測試重送、交錯 off/resume 與跨時段重送。[schema.sql 第 5 行](/Users/example-owner/TV/cfquota/schema.sql:5)、[計畫第 35 行](/Users/example-owner/TV/research/PLAN_adult.md:35)

8. **中｜只展開昨天、今天、明天不足以完整合併下一段**：例如週一晚間目前位於跨午夜時段，若要提供週二 22:00 的下一段，其結束時間需要週三 00:00–06:00 才能合併；只展開到週二會錯報結束時間，也可能影響「下一段不要」。**建議**：展開到足以確定目前與下一段的完整起訖，測試晚間目前時段、下一晚跨午夜及週末交界。[計畫第 32 行](/Users/example-owner/TV/research/PLAN_adult.md:32)

9. **中｜假日操作的對象不夠明確**：「關閉這一段」若前一晚操作，會關掉當晚至隔天 06:00，而非假日白天；週末白天本來沒有時段，「下一段不要」會關掉當晚至隔晨。頁面只顯示 `HH:MM`，跨日容易誤判。**建議**：顯示具體日期、星期與完整起訖，按鈕明確標示將關閉哪一段，並驗收平日假日前晚、假日早上與週末白天操作。[計畫第 20、21、37 行](/Users/example-owner/TV/research/PLAN_adult.md:20)

10. **中｜實機回歸與回滾無法證明不破壞既有額度**：驗收要求把真實 `bal` 設為 0 後「還原」，但沒定義測試期間加值、扣額、待處理 grant 與回報如何對帳；直接寫回舊值可能覆蓋真實變動。另外「前一版覆蓋」是否允許降版未驗證。**建議**：先在隔離環境測完歸零及邊界；實機確認維護窗口、記錄餘額與 grant 序號，禁止測試期間加值，事先驗證可行的降版命令與狀態保留方式，並把每一步的通過條件寫清楚。[計畫第 54 行](/Users/example-owner/TV/research/PLAN_adult.md:54)

11. **中｜測試清單缺少時鐘修改與同步耗時驗收**：`el` 在心跳開始時取得，實際同步前還要查狀態及執行控制；計畫卻寫成「收到時的 `el`」，若沿用舊值會提早到期。現有 `clockJump` 又走不同觀看估算路徑，不能僅測一般情況。**建議**：明確規定採樣時間、網路延遲的安全側處理，測試電視時間前撥、後撥、慢同步及跨時段邊界；把「最晚 60 秒」改成附帶正常連線與排程成功條件的可量測標準。[AgentService.java 第 119 行](/Users/example-owner/TV/tvagent/src/com/example/tvagent/AgentService.java:119)、[計畫第 27 行](/Users/example-owner/TV/research/PLAN_adult.md:27)

未執行測試或實機操作；FRIDAY 記憶工具因目前核准政策無法讀取。審查僅依指定檔案，未確認其他部署設定。

總評：需修改後施工。