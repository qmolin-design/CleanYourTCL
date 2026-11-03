測試向量（Opus 2026-10-30 產生，獨立於工人實作）：
- adbkey / adbkey.pub：真的 `adb keygen` 產生的一次性測試金鑰，從未授權給任何裝置，不得用在電視上。
  此私鑰是刻意公開的測試材料，必須永久視為已公開；不得授權其公鑰給任何真實裝置，也不得用於正式環境。
  adbkey.pub＝base64(524 bytes mincrypt 結構) + " " + 註解，檔案本身結尾沒有 NUL（傳給 adbd 時才加 \0）。
- token.bin：20 bytes（01..14 hex）。
- token.sig：`openssl pkeyutl -sign -inkey adbkey -pkeyopt digest:sha1 -in token.bin`（PKCS#1 v1.5＋SHA-1 DigestInfo，對原始 token，不再雜湊），256 bytes。
