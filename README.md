# CleanYourTCL

讓 TCL 老電視更清爽，也讓家人更容易管理看電視的時間。

這是整理 TCL 55P715 的實作紀錄與原始碼：參考 CleanYourTV，停用不需要的服務與 App、調整桌面，再加入 YouTube／Netflix 時間管理、兒童／爸媽模式，以及手機可操作的雲端家長頁。

目前方案由電視端 App 搭配 Cloudflare 運作，日常控管不需要家中另放一台常駐電腦。電腦仍用於初次設定、編譯與維護。

## 可以做什麼

- **電視瘦身**：保留分批停用、重開機驗證與還原的紀錄，避免直接套用不適合自己機型的清單。
- **看電視時間管理**：YouTube 與 Netflix 共用額度，按 App 在前景且螢幕亮著的時間計算；用完後停用這兩個 App，並非鎖住整台電視。
- **兒童／爸媽模式**：兒童模式扣額度；爸媽模式與排程時段內不扣額度，可手動切換並自動到期。這是控管模式切換，不是兩套獨立的 Android 使用者帳號。
- **雲端管理**：家長用手機網頁查看狀態、使用時間、加時間或歸零；登入與電視同步透過 Cloudflare Access 管理。
- **桌布製作**：本機網頁編輯器可調整構圖並匯出 1920×1080 桌布，另有圖片模糊填色與羽化工具。

## 目前架構

```text
家長手機 → Cloudflare Access → Worker／D1
                                   ↑
                              電視端 App 同步
                                   ↓
                         YouTube／Netflix 額度與控制
```

`tvagent/` 與 `cfquota/` 是目前的 v5 方案。`tvgate/` 是先前需要 Mac 常駐的 v3／v4 方案，保留作為演進、除錯與替代設計參考；使用 v5 不必同時部署它。

## 從哪裡開始

1. 先讀 [隱私與範例資料說明](PRIVACY.md)。這份公開版本沒有作者的正式憑證或家庭照片，網址、IP、帳號與排程需換成自己的設定。
2. 部署目前方案，依序參考 [v5 設計與工單](research/PLAN_v5.md)、[雲端服務說明](cfquota/README.md)、[電視端說明](tvagent/README.md)。雲端帳號、網域、Access、D1 與服務憑證需要自行建立。
3. 想整理桌面，查看 [桌面實作紀錄](research/batches/launcher/NOTE.txt)；想製作桌布，可下載後用瀏覽器開啟 [本機桌布編輯器](wallpapers/editor.html)。
4. 想知道改過哪些版本、為什麼這樣設計，查看 [狀態摘要](STATE.md)、[交接與驗證紀錄](HANDOFF.md) 及 [研究資料](research/)。這些是歷史紀錄，早期方案、待辦與授權文字不代表目前的安裝指令。

也可以請 AI 協助接手，但請先讓它讀目前架構與模組 README，再查歷史紀錄；不要直接執行舊日誌裡的指令。

## 部署前要知道

- 本專案是在 **TCL 55P715** 上實作的參考方案，尚未驗證所有 TCL 型號或其他品牌 Android TV。
- 需要 Android／ADB 設定與雲端部署能力，目前沒有一鍵安裝器或現成 APK 下載。
- Android 建置腳本使用 `cleanyourtv-android-build-amd64` Docker 映像；這個映像的 Dockerfile 尚未隨 repo 提供，需要自行準備相符的 Android SDK、JDK 與工具環境。
- 初次設定需要授權 ADB、設定電視端服務及自己的憑證；更新 App 時應保留自己的簽章金鑰。
- TCL 的背景凍結、TGuard 清理、待機與重新開機行為會影響控制，相關問題與處理方式已保留在研究及驗證紀錄中。斷網或 ADB 故障時，不能保證即時收到新設定或鎖定。
- 停用系統服務前，先確認用途並保留原狀態與還原方法。不同機型不要直接照抄整份停用清單。
- 目前只控管 YouTube 與 Netflix；其他 App、投放方式或繞過路徑，需要在自己的設備上另外驗證。

## 目錄

| 目錄 | 內容 |
| --- | --- |
| `tvagent/` | 電視端額度計算、同步、提示與 App 控制 |
| `cfquota/` | Cloudflare Worker、D1 與家長網頁 |
| `tvgate/` | 舊版 Mac 區網管理服務與假資料範本 |
| `tkey/` | 電視 T 鍵相關小工具 |
| `wallpapers/` | 本機桌布編輯器、產圖工具與範例設定 |
| `mockups/` | 家長頁介面設計稿 |
| `memlog/` | 記憶體觀察工具與合成資料範例 |
| `research/` | 研究、版本演進、失敗嘗試、根因與驗證紀錄 |

## 測試

Cloudflare 測試需要支援內建 `node:sqlite` 的 Node.js；Mac 服務測試使用 Python 3.12。

```sh
cd cfquota && node --test
```

```sh
cd tvgate && python3 -m unittest test_gate.py
```

電視端的離線測試見 `tvagent/test.sh`，需先準備上述建置環境。`tvagent/testdata/` 內的金鑰是刻意公開的離線測試向量，不得授權給任何真實裝置。

## 致謝與授權狀態

桌面整理參考 [CleanYourTV](https://github.com/Gathin23/cleanyourtv)。本 repo 保留整合過程與設定紀錄，不包含修改後的完整 launcher 原始碼或預編譯 APK。

目前尚未附上本專案的 LICENSE；第三方專案與素材依各自授權處理。
