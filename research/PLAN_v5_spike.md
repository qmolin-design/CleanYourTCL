# PLAN v5 實驗：電視 App 自己當 adb 連線端（APPROVED 2026-10-30，TERRA 1 輪）

> 2026-10-30 Owner 開工單。目的：驗證「電視上的 App 不靠 Mac 就能停用／啟用 App」是否可行，作為 Cloudflare 額度方案（v5）的前提。**本計畫只做實驗，不做額度、不碰 Cloudflare、不取代 v4。**

## 背景
- v4（Mac 用 adb 鎖 YouTube）07:22 已上線，實驗期間照常跑，不能被干擾。
- 一般 App 沒有權限停用別的 App。可行路線：App 用 adb 協定連電視本機 `127.0.0.1:5555`（`persist.adb.tcp.port=5555`、`ro.adb.secure=1`，第一次要在電視上按「允許」），以 shell 身分下 `pm disable-user`／`pm enable`。
- 電視：Android TV 11（API 30）、2GB RAM、32 位元使用者空間；TCL 可能有自家的背景清理機制。
- 編譯：沿用 `tkey/build.sh` 的方式（Docker 映像 `cleanyourtv-android-build-amd64`，javac＋d8，無 Gradle、無外部函式庫）。映像內有 Java 17，沒有 python；Mac 沒有 Java。

## 要回答的三個問題
1. App 能否連上本機 adb、通過 RSA 認證，並且停用＋啟用一個 App？
2. 電視重開（`adb reboot`）與冷開機（拔插頭）後，App 能不能自己啟動？
3. 常駐 24 小時會不會被系統砍掉？記憶體用多少？

## WO-A1 實驗 App（工人施工）
新目錄 `tvagent/`（套件 `com.example.tvagent`），仿 `tkey/` 結構：`AndroidManifest.xml`、`build.sh`、`src/…`、`debug.keystore`（build.sh 自動產生，與 tkey 分開）。

功能：
- **沒有桌面圖示**（不宣告 LAUNCHER／LEANBACK_LAUNCHER）。`android:debuggable="true"`（方便 `run-as` 讀紀錄；實驗用）。minSdk 26、targetSdk 30。
- **adb 連線端**（純 Java，`java.net.Socket`＋`java.security`）：實作 adb 傳輸協定 CNXN／AUTH（先送簽章 token，被拒再送 RSAPUBLICKEY）／OPEN／OKAY／WRTE／CLSE，執行 `shell:<指令>` 並回傳輸出。RSA 2048 金鑰第一次執行時產生，存 App 私有目錄；公鑰用 Android adb 格式（mincrypt RSAPublicKey 結構：len=64、n0inv、n 小端、rr、exponent，base64 後接 ` tvagent@tv\0`）；簽章＝對 20 bytes token 做 PKCS#1 v1.5 簽章並帶 SHA-1 DigestInfo 前綴（等同 adbd 的 RSA_verify(NID_sha1)）。這個類別**不得依賴任何 android.* API**，才能在 Mac 的 Docker JVM 上單獨執行。
- 該類別附 `main(String[] args)`：`java … AdbClient <host> <port> <keyfile> <shell 指令>`，金鑰檔不存在就產生；印出指令輸出，失敗印原因並以非 0 結束。
- **開機自啟**：`BOOT_COMPLETED` 接收器 → `startForegroundService` 啟動前景服務（通知頻道，文字「tvagent 實驗」）。
- **前景服務**：每 60 秒做一次心跳：連 `127.0.0.1:5555` 執行 `echo ok`，在 App 私有目錄 `files/agent.log` 追加一行：`<本地時間> uptime=<SystemClock.elapsedRealtime 秒> pid=<pid> adb=<ok|錯誤類別:訊息>`。服務啟動時記一行 `start reason=<boot|test|restart>`。檔案超過 1MB 時只保留後半。
- **自我測試**：明確廣播 `am broadcast -n com.example.tvagent/.TestReceiver -a com.example.tvagent.SELFTEST` → 在背景執行緒依序執行並把每步結果寫進 agent.log：
  1. `pm disable-user --user 0 com.example.tkey`
  2. `pm list packages -d` 確認含 `com.example.tkey`
  3. `pm enable com.example.tkey`
  4. `pm list packages -d` 確認不含 `com.example.tkey`
  5. 最後一行 `selftest PASS` 或 `selftest FAIL <步驟>`。**無論中途哪步失敗，都要嘗試 `pm enable com.example.tkey`。**
  這個廣播也要確保服務在跑（沒在跑就啟動，reason=test）。
- 只准停用／啟用 `com.example.tkey`，不得停用任何其他套件，程式裡不得出現 `youtube` 字樣（實驗期間 v4 在管 YouTube）。
- `tvagent/README.md`：用途、安裝、讀紀錄（`adb shell run-as com.example.tvagent cat files/agent.log`）、移除（`adb uninstall com.example.tvagent`）。

### 完成標準（工人自己跑、Opus 審定再跑）
1. `sh tvagent/build.sh` 成功，產出 `tvagent/tvagent.apk`。
2. `docker run … aapt2 dump badging tvagent/tvagent.apk`（或 `aapt dump badging`）顯示 package `com.example.tvagent`、targetSdkVersion 30、**沒有** `launchable-activity`／`leanback-launchable-activity`。
3. 離線協定測試 `sh tvagent/test.sh`：在 Docker 內編譯並執行 `AdbClientTest.java`（純 Java）。測試內建假 adbd（`ServerSocket` 在 127.0.0.1）：
   - 假 adbd 依 adb 協定驗證每個封包的 checksum 與 magic（`command ^ 0xffffffff`）。
   - 第一輪：收到簽章就拒絕 → 要求客戶端送 RSAPUBLICKEY；假 adbd **用 BigInteger 從 adb 格式公鑰解出 n、e，獨立計算 sig^e mod n**，檢查 PKCS#1 v1.5 填充＋SHA-1 DigestInfo 前綴＋原 token；之後回 CNXN。
   - 第二輪（同一金鑰檔）：簽章直接通過，不送公鑰。
   - `shell:echo ok` 回 `ok`；分兩個 WRTE 回傳的輸出要完整拼接；adbd 斷線時客戶端回錯誤不卡住（5 秒內）。
   - 驗證 n0inv 與 rr 的值（n0inv＝-1/n mod 2^32；rr＝(2^2048)^2 mod n）。
   - 最後一行印 `ALL PASS`，任一失敗以非 0 結束。
4. `grep -rn "disable-user" tvagent/src` 只出現在自我測試，且目標只有 `com.example.tkey`；`grep -rn "youtube" tvagent/src` 無結果。
5. `git status --porcelain --untracked-files=all` 只有 `tvagent/` 底下的檔案。

### 禁止
- 不連電視、不執行真 adb（`adb` 指令一律不准）、不裝套件到電視、不碰 `tvgate/`、`tkey/`、`memlog/`、`~/.secrets`、launchd。
- 不加 Gradle 或任何外部函式庫。不 commit。

## WO-A2 實機實驗（Opus＋Owner，不委派）
1. **先在 Mac 驗協定**（不裝 App）：Docker 跑 `AdbClient` 對 `192.0.2.69:5555` 執行 `echo ok` → 電視跳出 RSA 提示，**Owner 按「允許」（不勾一律允許）**。失敗 → 協定問題，退回 WO-A1。
2. 安裝 `tvagent.apk` → `am broadcast` SELFTEST → 電視跳 RSA 提示，Owner 勾「一律允許」→ 讀 agent.log 看到 `selftest PASS`，且 `pm list packages -d` 不含 tkey、T 鍵正常。
3. `adb reboot` → 不碰電視，5 分鐘內 agent.log 出現 `start reason=boot` 與 `adb=ok` 心跳。
4. 冷開機（Owner 拔插頭 30 秒）→ 同第 3 項。
5. 常駐 24 小時：心跳間隔最大值（扣掉待機時段另計）、`dumpsys meminfo com.example.tvagent` 的 TOTAL PSS；同時看 memlog 有無變差、v4 是否照常鎖。
6. 記錄結果到 `research/batches/tvagent/NOTE.txt`；結論給 Owner 決定是否開 v5（額度＋Cloudflare）。
- 收尾：Owner 決定不做 → `adb uninstall com.example.tvagent`（RSA 授權可在開發者選項撤銷）。

## 風險
- adbd 拒絕本機連線或 TCL 擋 → 問題 1 失敗，v5 改走「網頁搬 Cloudflare、Mac 照樣下 adb」。
- RSA 授權被電視清掉（例如重設 adb）→ App 失效，要重按允許；記錄實驗中是否發生。
- 這把 adb 金鑰＝電視 shell 權限，存在 App 私有目錄；App 不開任何對內連線埠。

## 挑戰裁定（TERRA high，原文 research/challenge/PLAN_v5_TERRA.md）
| # | 意見 | 裁定 | 處理（以下為 WO-A1／A2 的追加規格，與上文衝突時以此為準） |
|---|---|---|---|
| 1 | 協定版本、maxdata 未訂 | 採納 | CNXN 固定 version `0x01000000`、maxdata 4096，**送出的封包一律算 checksum**；送出 payload 不超過對方 CNXN 宣告的 maxdata；收到的封包驗 magic、長度、checksum（version≥0x01000001 時對方可能送 0，checksum 為 0 視為合法）。測試含 >4096 輸出分多個 WRTE、截斷封包。 |
| 2 | 公鑰格式測試有空隙 | 採納 | **用獨立測試向量**：`tvagent/testdata/`（Opus 用真 `adb keygen`＋openssl 產生，見 README.txt）。測試必須：從 `adbkey`（PEM）載入私鑰 → 客戶端產生的公鑰字串的 base64 部分與 `adbkey.pub` 的 base64 部分**逐 byte 相同**（524 bytes）；客戶端對 `token.bin` 的簽章與 `token.sig` **逐 byte 相同**（PKCS#1 v1.5 是確定性的）。客戶端送 RSAPUBLICKEY 的 payload 結尾必須是 `\0`。 |
| 3 | 假 adbd 認證流程不精確 | 採納 | 假 adbd 依序檢查 `AUTH TOKEN → SIGNATURE（拒絕）→ AUTH TOKEN → RSAPUBLICKEY → CNXN`；第二輪同金鑰 `AUTH TOKEN → SIGNATURE → CNXN`。等待授權逾時（預設 60 秒，可設定）與一般 I/O 逾時（10 秒）分開；測試授權逾時與斷線都在設定時間內回錯誤。 |
| 4 | A_STLS 未列分支 | 採納 | 收到 `STLS` → 回錯誤類別 `tls_required`，不實作 TLS。錯誤分類固定：`connect`（連不上）、`auth_timeout`、`auth_rejected`／斷線、`tls_required`、`protocol`、`io`。測試含 STLS。 |
| 5 | 首次授權與自我測試順序 | 採納 | 接收器只啟動服務；**所有 adb 動作在服務內同一個單執行緒序列化**。SELFTEST 先確認 `echo ok` 成功（必要時等待授權）才開始；沒有授權就記 `selftest FAIL auth` 結束。 |
| 6 | Mac 驗證要用獨立金鑰 | 採納 | WO-A2 第 1 步用**新產生的臨時金鑰**（不是 testdata、不是 Mac 的 `~/.android/adbkey`），記錄失敗階段；只有 protocol 類錯誤才退回 WO-A1。 |
| 7 | 前景服務先決條件 | 採納 | Manifest 明列 `INTERNET`、`RECEIVE_BOOT_COMPLETED`、`FOREGROUND_SERVICE`；接收器 exported 設定寫清楚（BOOT 接收器 exported=true；TestReceiver exported=true 但只接受 shell 送的明確廣播即可，實驗不另做權限）；`onStartCommand` 立即 `startForeground()`。安裝後先用 SELFTEST 廣播啟動一次（脫離 stopped 狀態）。 |
| 8 | 私鑰與備份 | 採納 | `android:allowBackup="false"`；私鑰、簽章不得寫進 log。 |
| 9 | 撤銷授權會清掉 Mac 的 v4 金鑰 | 採納 | 收尾**只** `adb uninstall`，不做「撤銷 USB 偵錯授權」。電視端殘留的公鑰對應私鑰已隨 App 刪除，無法再被使用。 |
| 10 | 自我測試影響 T 鍵、復原 | 採納 | SELFTEST 開始前檢查 tkey 是否已停用：已停用 → 記 `selftest SKIP tkey-disabled`，不動。每步驗 `pm list` 實際狀態。復原指令（Mac）：`adb -s 192.0.2.69:5555 shell pm enable com.example.tkey`；WO-A2 測後 Owner 按一次 T 鍵確認。 |
| 11 | grep 可假通過 | 採納 | 由第 2 條測試向量解決格式風險；電視端可觸發的只有固定的心跳與 SELFTEST，**沒有任何從 Intent extra／檔案讀入指令的路徑**（審核要確認）；`AdbClient.main` 只供 Docker 使用。 |
| 12 | 24 小時無門檻、輪替破壞證據 | 採納 | **不做輪替**（每分鐘約 100 bytes，一天約 150KB）。心跳行加 `pid`；pid 變化＝程序重啟。WO-A2 判定：Owner 開著電視的時段，心跳間隔 ≤ 3 分鐘、程序重啟次數記錄；待機時段另列不判定。PSS 在安裝後 1 小時與 24 小時各採一次。`adb=ok` 必須是輸出 trim 後等於 `ok`。 |
| 13 | 可再縮小 | 採納 | 砍掉 1MB 裁切；start reason 只記 `boot`／`test`／`other`。 |

## 實機發現與計畫修正（2026-10-30 09:05，Opus）
- 第 1 步（Mac 臨時金鑰）PASS：電視開機久了 adbd 會卡「prompt currently pending」、框架沒收到公鑰、不跳視窗；`adb reboot` 後正常跳出。上線版要把「授權視窗不跳就重開電視」寫進操作說明。
- 第 2 步首次：Conscrypt 私鑰轉型錯（WO-A1 退修 1 已修）。
- **TCL 凍結機制**：`persist.sys.freeze=true`，system_server 的 FreezeManager 在 App 閒置約 15～85 秒後把整個程序放進 `freezer:/frozen`（前景服務、procState=4、adj 200 也照凍；Play 商店也被凍）。執行緒停在 `__refrigerator`，`ScheduledExecutor` 的心跳全停。**送廣播會立即解凍**（noteFreezeStateChange isFreeze=false），積壓的排程一次補跑。
- 修正（WO-A1 退修 2，屬計畫假設錯誤，不計入工人失敗次數）：心跳改由 AlarmManager 每 60 秒的精確鬧鐘（廣播）驅動；前景服務保留（序列化 adb 與避免被殺）。WO-A2 第 5 項判定改看「鬧鐘心跳」間隔。
- 09:46 WO-A2 進度：問題 1 PASS；問題 2 PASS（需 shell 設 `APP_AUTO_START allow`、standby bucket、deviceidle 白名單，見 research/batches/tvagent/NOTE.txt）；問題 3 鬧鐘心跳可行，24 小時觀察進行中。v5 正式 App 要在第一次授權後自己用 adb 下這三個設定。
