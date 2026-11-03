# 公開副本的隱私界線

- `example.com`／`example.net`／`example.test`、`example-owner`、`ExampleMacBook-Pro`、`192.0.2.x` 與零值服務識別符均為範例或去識別化值，不是正式連線設定；部署時須自行替換。
- 實際家庭排程與兒童出生資料已移除；排程範例和測試 fixture 為合成資料。其餘技術驗證日期、效能數字與演進紀錄保留，不應被當成合成資料。
- `mockups/` 與 `research/wo/*.png` 是 UI 示意／測試截圖，不是家庭照片。桌布功能保留；上傳前只放可公開的通用圖，不放家人照片或其衍生圖。
- 不提交真實憑證、`.env`／`.dev.vars`／`config.properties`、簽章檔、APK、私人照片或本機原始日誌。`tvagent/testdata/adbkey*` 是未授權的測試向量，僅供離線測試。
- `tvgate/testdata/usagestats_events_sample.txt` 與 `memlog/mem.example.csv` 是最小合成樣本；`memlog/mem.csv` 是本機實測追加檔並已忽略。
- `*.links.txt`、`tvgate.tokens.json` 與舊版 `tvgate.token` 可能含完整管理鑰匙，僅保存在版本庫外的安全位置；忽略規則只防止新加入，不能清除已提交的歷史。
