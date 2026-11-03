# WO-T4 上線材料（2026-10-30 03:00 備妥，**未上線**，等 Owner 說「上線」）

## 已備妥
- 程式：main 0000009（含 WO-T3 SOL 修正），44 測試在 main 連 5 次 OK。
- 鑰匙：`~/.secrets/tvgate.tokens.json`（爸爸 iPhone／媽媽 iPhone／MacBook，600）。
- 連結：`~/.secrets/tvgate.links.txt`（600；每設備 .local 版＋IP 192.0.2.26 版）。**含鑰匙，只用 AirDrop 傳，不貼到聊天／訊息。**
- plist：`~/Library/LaunchAgents/com.example.tvgate.plist.paused` 與 `tvgate/com.example.tvgate.plist` 內容相同（v4 沿用）。
- 環境檢查：8790 空著；8765 是 Hermes（127.0.0.1）；Mac 防火牆關閉（不用另外放行）；sleephook 為 arm64 執行檔。
- 回滾：tag `tvgate-v3`、`~/.secrets/tvgate.token` 保留。

## Owner 說「上線」後 Opus 執行
```bash
mv ~/Library/LaunchAgents/com.example.tvgate.plist.paused ~/Library/LaunchAgents/com.example.tvgate.plist
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.example.tvgate.plist
sleep 5; lsof -nP -iTCP:8790 -sTCP:LISTEN; tail -20 ~/Library/Logs/tvgate.log
```
上線會**立刻鎖 YouTube**（未授權狀態）。之後照 PLAN_v4_ui.md「WO-T4」安裝段（Owner 加書籤、AirDrop 連結、加入主畫面）與驗收 1～7。

## 回滾
```bash
launchctl bootout gui/$(id -u)/com.example.tvgate   # SIGTERM 會先開放 YouTube
mv ~/Library/LaunchAgents/com.example.tvgate.plist ~/Library/LaunchAgents/com.example.tvgate.plist.paused
adb -s 192.0.2.69:5555 shell pm enable com.google.android.youtube.tv   # 保險
```

## 已知限制（審查非阻擋，記給 Owner）
1. 休眠中按「立即鎖」不會鎖（回「電視未確認鎖定」）；Mac 睡著時網頁本來就連不到，實務影響小。
2. hook 若剛好在休眠中重啟、漏掉 wake，服務會一直停在「休眠中」＝YouTube 開放，直到下一次睡／醒。方向是開放（符合 Q1）；要保險再開工單加逾時。
3. 休眠中狀態頁的「電視／最後確認」停在睡前的值。
