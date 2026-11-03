# 電視 YouTube＋Netflix 時間管理

Mac 清醒且在家時管理一台電視的 YouTube 與 Netflix。家長可用 Mac 或 iPhone 網頁開放 15、30、60 分鐘或立即鎖定；時間到會鎖回。Mac 睡眠或服務正常停止前會盡力開放 YouTube 與 Netflix。v4 尚須完成 WO-T4 實機驗收後才上線。

## 安裝與設備連結

由熟悉終端機的協助者與 Owner 一起操作。電視 adb 須已授權；`192.0.2.69:5555` 是文件範例，請將 `TVGATE_DEVICE` 設成自己的電視 ADB 位址；先記錄 YouTube 與 Netflix 原本啟用或停用的狀態。Mac 需有 Python 3.12、Swift 和 adb；Mac、手機與電視需在同一個家用網路。

公開版附 `tvgate.tokens.example.json`（空白鑰匙範本）與 `tvgate.links.example.txt`（假連結），不包含任何人的真實管理鑰匙。直接下載後尚不能管理電視；完成下列本機設定後才能使用，不需要原作者的鑰匙。空白範本不會授予管理權。

參照範本建立 `~/.secrets/tvgate.tokens.json`，每個設備使用不同的隨機鑰匙，例如以 `openssl rand -hex 32` 分別產生三次，填入以下 JSON，並將檔案權限設成 600：

```json
{"爸爸 iPhone":"第一把鑰匙", "媽媽 iPhone":"第二把鑰匙", "MacBook":"第三把鑰匙"}
```

保護檔案與連結，不要公開或放入版本庫。從 v3 升級者才需保留舊版 `~/.secrets/tvgate.token` 與 v3 版本直到 WO-T4 驗收通過；全新安裝不需要這個舊檔。把本資料夾放在 `/Users/example-owner/TV/tvgate`，確認鑰匙檔已建立，再由協助者在終端機執行：

複製 LaunchAgent 前，請修改 `com.example.tvgate.plist`：將程式與日誌的 `/Users/example-owner/...` 路徑換成本機實際路徑，Python 路徑換成 `command -v python3` 的結果（需 Python 3.12 或更新版本）；將 `EnvironmentVariables` 中的 `TVGATE_DEVICE` 值換成自己的電視位址（如 `192.0.2.69:5555`，此處仍是範例）。launchd 不會自動沿用終端機的環境變數。直接從終端機執行時，可用 `TVGATE_DEVICE=<電視IP>:5555 python3 gate.py`。

```sh
cd /Users/example-owner/TV/tvgate
chmod 600 ~/.secrets/tvgate.tokens.json
swiftc -o sleephook sleephook.swift
mkdir -p ~/Library/LaunchAgents
cp com.example.tvgate.plist ~/Library/LaunchAgents/
launchctl bootstrap gui/$(id -u) ~/Library/LaunchAgents/com.example.tvgate.plist
```

若 macOS 防火牆詢問，允許連入。在該資料夾產生連結：

```sh
python3 gate.py --links
```

每個設備會列出 `.local` 與 IP 兩個連結；把各自的連結安全傳給對應設備，優先使用 `.local`，無法連線時用 IP 備援。可用 `--port` 指定埠，預設為 8790。網頁連結含有完整管理權的鑰匙，請勿轉傳給其他人。

## 網頁使用

在各設備用自己的連結開啟。iPhone 可用 Safari「分享」→「加入主畫面」，Mac 可加入書籤。首頁顯示授權、電視狀態和最後確認時間，按鈕可開放或立即鎖；頁面每 15 秒更新。前景時間可切換今天、本週、本月，紀錄顯示最近 30 筆，排程可勾選星期與時間後新增或刪除。排程是時間點切斷，並非禁止時段，家長之後仍可重新授權。

如顯示「請用設備連結開啟」，請重新使用該設備的完整連結；若鑰匙被刪除或輪替，舊連結會失效。

## 鑰匙輪替

在 Mac 修改鑰匙 JSON，刪除或替換洩漏設備的鑰匙並保存，保留其他設備的鍵值；權限維持 600。修改後立即生效，不須重啟。執行 `python3 gate.py --links`，安全傳送該設備的新連結，並替換其書籤或主畫面捷徑。每把鑰匙都有完整管理權。

## 也可用捷徑

在 iPhone「捷徑」使用下列網址，方法選 POST，header `X-Token` 帶各自設備鑰匙：

- `/open?min=15`、`/open?min=30`、`/open?min=60`：開放相應分鐘。
- `/lock`：立即鎖；`/status`：查狀態。

網址前綴使用設備連結中的 `http://主機名稱.local:8790` 或 IP。開放回應的 `ok` 為真才代表電視已確認開放；失敗時顯示 `until` 和 `note`，授權可能仍有效。狀態中的 `tv` 為 `open`、`locked` 或 `unknown`，`tv_checked_at` 是最後成功確認時間。

## 限制與失效情況

- Mac 休眠（含 Power Nap 暗喚醒）期間服務不會鎖電視。
- 前景時間是 App 位於畫面最前面的時間，不等於實際觀看；Mac 離家超過 24 小時，中間電視事件可能遺失而少算。HDMI 或缺少事件可能多算，單段最多 4 小時。資料從服務上線才開始。
- 休眠中錯過的排程記錄為略過；服務停止後 10 分鐘內恢復會補切，超過則記錄錯過。排程切斷後 Mac 闔蓋仍盡力開放 YouTube 與 Netflix。
- 電視拔插頭重開後重新連線屬一般對帳：沒授權會鎖回，不給寬限。只有 Mac 喚醒或離家網路至少 2 分鐘後回來，才可能在正在看 YouTube 或 Netflix 時給寬限；家長仍可立即鎖。
- Mac 太快睡著、電視離線、斷電或當機可能使電視保留當時狀態；授權期間電視離線，到期後要等重新連線才能鎖。時鐘錯誤影響授權、統計及排程。
- 區網使用 HTTP 明文傳送鑰匙，同網路的人可能側錄；不要開放到外網。文字紀錄預設在 `~/Library/Logs/tvgate.log`。

## 還原

先停止服務：`launchctl bootout gui/$(id -u)/com.example.tvgate`。確認 gate 和 sleephook 已結束，請熟悉 adb 的協助者依安裝前記錄恢復 YouTube 與 Netflix 的啟用或停用狀態。若 v4 驗收失敗，保留並還原 v3 版 gate.py（git tag `tvgate-v3`）和原本的單一 token，再由協助者依 v3 文件復原服務；移除服務時可刪除 LaunchAgent、鑰匙和狀態資料。

## 環境變數

- `TVGATE_DEVICE`：電視的 ADB 位址（`IP:port`）；預設 `192.0.2.69:5555` 僅為文件範例，部署時必須替換。
- `TVGATE_HOME`：狀態和資料庫目錄，預設 `~/Library/Application Support/tvgate`。
- `TVGATE_TOKEN`：鑰匙 JSON，預設 `~/.secrets/tvgate.tokens.json`。
- `TVGATE_LOG`：文字紀錄，預設 `~/Library/Logs/tvgate.log`。
- `TVGATE_PORT`：服務埠，預設 `8790`。
- `TVGATE_INTERVAL`：對帳間隔秒數，預設 `30`。
- `TVGATE_HOOK`：睡眠監聽程式，預設為 gate.py 同目錄的 `sleephook`。
- `TVGATE_NOTIFY`：通知程式，預設 `osascript`。
- `TVGATE_HOME_NET`：測試用網路狀態檔，內容為 `1` 表示在家；預設未設定，依本機網路判斷。
- `TVGATE_AWAY_SECONDS`：離家多久後回家觸發寬限，預設 `120` 秒。
- `TVGATE_FETCH_INTERVAL`：使用時間抓取間隔，預設 `600` 秒。
- `TVGATE_CLOCK`：測試用排程時鐘檔，內容為 Unix 時間；預設未設定，使用系統時間。
- `ADB`：adb 程式，預設 `adb`。
