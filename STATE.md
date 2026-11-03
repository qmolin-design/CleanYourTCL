# 看電視時間（TCL 55P715 家長控管）現況

實際家庭排程與兒童個資已移除或改為示例；技術驗證紀錄保留。

更新：2026-11-01 22:0x（Claude Code，Opus）。新 session 先讀本檔，再讀 `HANDOFF.md` 最上面那節（逐步經過、回滾指令都在那裡）。

## 一句話
小孩看 YouTube／Netflix 要扣「看電視時間」，用完電視自動鎖；爸媽用手機家長頁加時間、歸零、切兒童／爸媽模式。v5 全部上線運作中。

## 現在上線的版本（2026-11-01）
| 部分 | 版本 | 回滾 |
|---|---|---|
| 家長頁＋伺服器（Cloudflare Worker `tvquota`，tv-demo.example.net/tv/） | **00000019**（WO-M1 模式切換） | `cd cfquota && npx -y wrangler@4.151.0 rollback 000000020-0000-4000-8000-000000000000` |
| 電視 App `com.example.tvagent` | **app_ver 5**（WO-A3，13:03 安裝） | 前一版 apk：`git show 0000003:tvagent/tvagent.apk > /tmp/old.apk && adb install -r /tmp/old.apk` |
| 資料庫 | D1 `tvquota` | — |

## 規則（Owner 拍板）
- 管控對象：**只有 YouTube（`com.google.android.youtube.tv`）與 Netflix（`com.netflix.ninja`）**，共用一份時間。其他 App 不管。
- 爸媽時段（不鎖、不扣時間，文件用合成範例）：每天 00:00–06:00、22:00–24:00；**週一～五** 10:00–16:00（週末白天不算）。改時間用 wrangler 改 D1 `meta.adult_sched`（格式見 `cfquota/README.md`）。
- 家長頁最底部「兒童模式｜爸媽模式」切換（11-01 上線）：開爸媽模式選 1 小時／2 小時／到下一段排程開始，時間到自動切回；接上排程時段就延到排程結束。切回兒童模式只關「已開始」的排程段，下一段照常。上方舊爸媽卡已拿掉。國定假日：排程時段開始後按「兒童模式」。
- 登入：爸爸 father@example.com、媽媽 mother@example.com（Cloudflare Access）。

## 反應時間（11-01 實測）
- 加值 → 電視開放：**3～9 秒**（鎖住且螢幕亮時每 10 秒問一次伺服器）。
- 開爸媽模式 → 開放：1 秒～10 秒；切回兒童模式 → 鎖：約 1 分鐘內（看片中每 60 秒問一次）。
- 螢幕關／待機時 60 秒～1 小時才醒一次；電視醒來第一跳就補鎖（11-01 15:54 實測）。

## 已知問題
- **TGuard（TCL 記憶體管家）會砍 tvagent**：11-01 共 12 次（待機那刻、長待機醒來 7 分鐘內、晚間系統大掃除）。tvagent 在保護名單內仍被砍，被砍時是 cached 狀態（兩跳之間不是前景服務）。每次 10 秒左右由鬧鐘拉回，**鎖定沒漏過**。待 11-02 記憶體與 log 檢查（C10）評估「前景服務常駐」。
- 7 天圖「今天」底色右緣凸出約 4px（不造成捲動，未修）。

## 待辦
- [ ] 11-02：C10 記憶體／log 檢查（含 TGuard 被砍分析、app_ver 5 長期穩定）。
- [ ] 全部通過 → 移除 v4（先打 tag）→ 白話總整理頁面。
- [ ] 太太（媽媽）實際登入家長頁確認。
- [ ] Owner 手動項：C2 待機 10 分、C8 語音／T 鍵、提示跳出時 YouTube 是否自動暫停（目視）。
- [ ] WO-A3 驗收 3（剛好用完時加值不跳「時間已到」）：等自然發生時看 log。

## B 站（bilibili）TV App 實測（2026-11-01 晚，另一個 session；Owner：電視不急，先記錄）
- **結論：目前在這台電視上不能用，已全部移除，電視已還原。** 詳細證據：`research/batches/bilibili/NOTE.txt`。
- 台灣區 Play 商店沒有 B 站 TV 版，只能側載。試了 3 個：1.6.6 修復版（`com.bilibili.tv`，「視頻解析失敗」）、BBLL（`com.xx.blbl`，SOURCE ERROR）、newBV（`dev.frost819.newbv`，掃碼登入失敗）。
- 根因：B 站伺服器回 **48007「当前账号暂不支持在本APP登录」**——拒絕 Owner 的 Google 註冊帳號（無大陸手機綁定）登入第三方 TV App；未登入拿不到片源。**不是鎖區**（同網路 Mac 用 yt-dlp 抓 UP 主影片正常）。
- 待辦：追 newBV issue #322（https://github.com/Frost819/newBV/issues/322）；作者回覆後重裝測試。若 Google 帳號無解：換綁大陸手機的帳號／官方雲視聽小電視／開回 Chromecast 或 TCL 投屏（兩者目前停用）。
- **若之後真的裝上 B 站：它不在管控內（不鎖、不扣時間），要另開工單加進 tvagent。**
- 測試期間 TGuard 曾砍掉系統安裝程式與 Play 商店，安裝失敗時可再試一次。

## 機密
API key、token、密碼不進 repo（`~/.secrets/` 下，不要印出）。
