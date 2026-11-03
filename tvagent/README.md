# 看電視時間

電視端自行計算 YouTube 與 Netflix（共用同一份額度）前景且螢幕亮著的用量、持有額度，並每分鐘與 Cloudflare 同步。只有 `com.google.android.youtube.tv` 與 `com.netflix.ninja` 會被一起停用或啟用；沒有桌面啟動圖示。開機與鬧鐘啟動前景服務，TGuard 名單每跳檢查補回。使用同一把 `debug.keystore` 覆蓋安裝，以保留 App 的 adb 金鑰與授權（本 repo 不附 keystore，`build.sh` 會自動產生一把；公開使用請換成自己的，見下方「接手改名」）。

## 建置與安裝

```sh
sh tvagent/test.sh
sh tvagent/build.sh
adb install -r tvagent/tvagent.apk
adb shell cmd appops get com.example.tvagent APP_AUTO_START
adb shell cmd appops get com.example.tvagent APP_ASSOC_START
adb shell cmd appops get com.example.tvagent GET_USAGE_STATS
```

每個程序啟動時 App 會重新設定以上三個 appops、standby bucket 與 deviceidle 白名單。`agent.log` 的 `setup=11111` 表示五條設定均成功；失敗會在下一跳重試。

## 設定與觀察

在安全位置建立 `config.properties`，內容只有以下三個欄位（填入實際 HTTPS 同步網址及 Cloudflare Access service token；勿提交或分享憑證）：

```properties
url=https://example.com/tv/api/sync
client_id=填入服務憑證 ID
client_secret=填入服務憑證密鑰
```

以已連線的維運裝置推入 App 私有目錄，然後叫服務跑一跳：

```sh
adb push config.properties /data/local/tmp/tvagent-config.properties
adb shell 'run-as com.example.tvagent sh -c "cat /data/local/tmp/tvagent-config.properties > files/config.properties && chmod 600 files/config.properties"'
adb shell rm /data/local/tmp/tvagent-config.properties
adb shell am broadcast -n com.example.tvagent/.KickReceiver -a com.example.tvagent.KICK
adb shell run-as com.example.tvagent cat files/agent.log
```

每跳一行：`yyyy-MM-dd HH:mm:ss（台北時間） uptime pid src bal adult used_today yt_s nf_s fg yt screen act sync adb wl`，另有啟動設定結果與必要時的 `gap`、`clock=jump`。其中 `adult` 是爸媽時段剩餘秒數（0＝不在時段內）；`yt_s`／`nf_s` 是今天 YouTube／Netflix 各自在前景且螢幕亮著的累計秒數（只算有扣額的部分，爸媽時段看的不計入）。記錄超過 1MB 時只保留後約 512KB，不記憑證或私鑰。狀態保存在 `files/state.properties`（另有 `used_today_yt_s`、`used_today_nf_s`、`fg_pkg`、`adult_end_el` 四鍵，其中 `adult_end_el` 是爸媽時段結束點（elapsedRealtime 基準毫秒）；舊版 state 缺這些鍵照讀，預設 0、0、空、0）。

同步請求（`app_ver` 5）除原有欄位外另帶 `today`（yyyy-MM-dd，電視的台北日期）、`used_today_yt_s`、`used_today_nf_s`，供家長頁顯示分 App 使用時間；回應多讀 `adult_left_s`（爸媽時段剩餘秒；缺欄或不在 0～86400 內當 0，不影響同步），成功後按「最多續 10 分鐘」更新 `adult_end_el`。

額度用完（`balance`＋爸媽時段剩餘為 0）且螢幕亮著、狀態已知時，鬧鐘改 10 秒一次等加值（螢幕關或狀態未知維持 60 秒）；這一跳會停用時先同步再動作，加值已到就不停用，同步失敗照本機狀態停用。

## 爸媽時段（排程內不鎖、不扣時間）

Worker 用自己的時鐘算爸媽時段，sync 回 `adult_left_s`；電視每次成功同步後按 `min(adult_left_s, 600)` 續期（斷網最多多拿 10 分鐘免費，之後回一般規則）。時段內有效額度＝`balance`＋剩餘秒：YouTube／Netflix 照常開放且不扣額度、用量不計入每日統計；沒額度時剩 5 分鐘才提示，到期回到一般規則。開機或時鐘異常時本機時段失效，等下一次同步。

## 接手改名（把 `com.example.*` 換成你自己的）

本 repo 的套件名是佔位用的 `com.example.tvagent`（App）、`com.example.tkey`（T 鍵）、`com.example.tvgate`（Mac launchd）。接手時先自取一個命名空間（例如 `com.yourname.tvagent`），再叫 AI 一次改完，人工逐一改容易漏。給 AI 的指令範本：

> 把本 repo 內所有 `com.example` 換成 `<你的命名空間>`，含 `tvagent/AndroidManifest.xml`、`tkey/AndroidManifest.xml` 的 `package`、`tvagent/src/com/example/`、`tkey/src/com/example/` 的**目錄名**與全部 `.java` 第一行的 `package`、`tvagent/build.sh`／`test.sh`／`tkey/build.sh` 裡的路徑、`tvgate/com.example.tvgate.plist` 的**檔名**與 `Label`、以及所有文件裡的 adb 指令（`pm`／`run-as`／`appops`／`am start -n`）。改完跑 `sh tvagent/test.sh` 驗證。

注意：新包名＝全新 App，電視會當它沒裝過：要重新走一次授權（電視跳窗勾「一律允許」）、`config.properties` 重推、KICK 驗證 `sync=ok`；舊包名的 App 確認新版接管後再 `adb uninstall`。簽章 keystore 請用自己的（`build.sh` 會自動產生，不要沿用別人 repo 附的）。

## 影子模式、切換與回滾

執行開關 `enforce` 存在 Cloudflare D1，由 Worker 同步下發；預設 false 是影子模式，只記 `dry-*`，不操作 YouTube／Netflix 或提示畫面。完成影子驗證後在 D1 設為 true，下一次成功同步生效；切回 false 也是透過 D1 下發。回滾到舊方案前，先確認 YouTube／Netflix 狀態與舊方案接手情況。覆蓋安裝沿用同一把簽章金鑰。

斷網時仍使用本機保存的額度及最後收到的 enforce 值，照事件補扣；沒有設定檔時不會同步。adb 查詢失敗時當跳不操作 YouTube／Netflix、不顯示提示，直到 adb 恢復；不能保證離線或 adb 故障期間即時鎖定。

## 移除

```sh
adb uninstall com.example.tvagent
```

移除只用 `adb uninstall`；不准撤銷電視的 USB 偵錯授權。
