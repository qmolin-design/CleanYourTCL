PASS

### 審核查核項目

#### 1. 要做 1～4 逐項查核
* **要做 1（公鑰參數一律直接從金鑰檔 DER 解析，不從 Conscrypt 金鑰物件取得，私鑰只用於簽章）**：
  * **做到**
  * **檔案:行號**：[AdbClient.java:223-252](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AdbClient.java#L223-L252), [AdbClient.java:260-277](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AdbClient.java#L260-L277), [AdbClient.java:486-503](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AdbClient.java#L486-L503)
  * **說明**：`publicKeyFromDer` 直接以 `parsePkcs8`／`parsePkcs1` 解析檔案 DER 得到 `RSAPrivateCrtKeySpec`，並經 `KeyFactory.generatePublic(RSAPublicKeySpec)` 產出公鑰；金鑰載入時完全不對私鑰呼叫 `getKeySpec` 或讀取金鑰物件 `getEncoded()`；私鑰物件僅在 `signToken`（`Cipher.init(Cipher.ENCRYPT_MODE, priv)`）做 RSA/ECB/NoPadding 簽章。

* **要做 2（第一次產生金鑰時產生與載入得到相同公鑰）**：
  * **做到**
  * **檔案:行號**：[AdbClient.java:278-298](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AdbClient.java#L278-L298)
  * **說明**：產生新金鑰對並寫入檔案後，立即重新由檔案讀回 PEM 並呼叫 `publicKeyFromDer(pd.der, pd.pkcs1)` 解析公鑰，確保產生與日後載入走完全相同的 DER 解析路徑，公鑰必定一致。

* **要做 3（解析失敗時錯誤訊息具體，帶步驟與欄位，不吞被抑制例外）**：
  * **做到**
  * **檔案:行號**：[AdbClient.java:186-252](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AdbClient.java#L186-L252), [AdbClient.java:334-424](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AdbClient.java#L334-L424)
  * **說明**：DER 解析器定義了 `PKCS1_FIELDS`，每個欄位標籤與長度解析皆標記具體欄位名稱（如 `PKCS#1 field[2] publicExponent e`）與 offset；所有 catch 塊皆鏈結原始例外物件（cause），無任何 suppress 或吞掉例外情事。

* **要做 4（新增測試且不改既有斷言，模擬 Conscrypt 壞私鑰物件情境）**：
  * **做到**
  * **檔案:行號**：[AdbClientTest.java:504-563](file:///Users/example-owner/TV-wt-tvagent/tvagent/test/AdbClientTest.java#L504-L563), [AdbClientTest.java:697-698](file:///Users/example-owner/TV-wt-tvagent/tvagent/test/AdbClientTest.java#L697-L698)
  * **說明**：新增 `testConscryptBrokenObjectStillLoadsFromFile`（模擬不含 publicExponent 的壞私鑰物件，驗證由檔案載入公鑰仍與 adbkey.pub 逐 byte 相同）與 `testGenerateThenLoadSameKey`。`git diff -U0 -- tvagent/test | grep -c '^-[^-]'` 結果為 0，既有斷言零刪除。

---

#### 2. App 在電視上的實際執行路徑追蹤
* **執行路徑**：`AdbClient.withKeyFile` → `loadOrGenerateKey` → `authenticate()` → `mincryptPublicKey(pub)` / `signToken(priv, token)`。
* **私鑰操作檢查**：
  * 在既有金鑰檔載入路徑（[AdbClient.java:261-277](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AdbClient.java#L261-L277)），完全沒有呼叫 `priv.getKeySpec()`、`priv.getEncoded()`，亦無任何 `(RSAPrivateCrtKey)` 或 `(RSAPrivateCrtKeySpec)` 轉型。
  * 第一次產生路徑（[AdbClient.java:278-298](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AdbClient.java#L278-L298)）中，僅有 `kp.getPrivate().getEncoded()` 用於寫檔持久化，隨後公鑰由重新讀取之檔案 DER 解析，不從私鑰物件推導。
  * 簽章時（[AdbClient.java:486-503](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AdbClient.java#L486-L503)）直接以 `Cipher.init(Cipher.ENCRYPT_MODE, priv)` 執行，無任何私鑰轉型或 spec 查詢。
* **公鑰來源檢查**：
  * 公鑰皆由檔案 DER 經 `parsePkcs8`／`parsePkcs1` 取得 `n`、`e`，並呼叫 `KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e))` 產出。
  * 送公鑰時（[AdbClient.java:430-443](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AdbClient.java#L430-L443) `mincryptPublicKey`），優先檢查 `pub instanceof RSAPublicKey` 並直接取得 `rsa.getModulus()` 與 `rsa.getPublicExponent()`，全程不碰私鑰，完全不受 Conscrypt 私鑰物件限制。

---

#### 3. 金鑰檔載入與產生路徑例外檢查
* **既有金鑰檔（PEM PKCS#8 完整 CRT）載入路徑**：
  * `readAll` 讀入 PEM，`pemToDer` 解碼 Base64。
  * `loadPrivateKey` 走 `kf.generatePrivate(new PKCS8EncodedKeySpec(pd.der))`，為 Conscrypt 支援之標準路徑。
  * `publicKeyFromDer` 走 `parsePkcs8` → `parsePkcs1`，因電視金鑰為標準 9 欄位 CRT，各欄位標籤與長度比對吻合，能正確還原出 `n` 與 `e`，`generatePublic(RSAPublicKeySpec)` 正常產出公鑰，**不丟例外**。
* **第一次產生路徑**：
  * `KeyPairGenerator.getInstance("RSA")` 產生 2048 位元金鑰，私鑰序列化寫入檔案後，透過相同的檔案 DER 解析路徑取得公鑰，**不丟例外**。

---

#### 4. 測試斷言檢查
* `git diff -U0 -- tvagent/test | grep -c '^-[^-]'` 輸出為 `0`。
* `tvagent/test/AdbClientTest.java` 僅新增測試方法與呼叫，未刪減或放寬任何既有測試斷言。

---

### 問題清單

* **[AdbClient.java:274](file:///Users/example-owner/TV-wt-tvagent/tvagent/src/com/example/tvagent/AdbClient.java#L274)**
  * **問題**（非阻擋）：`loadOrGenerateKey` 載入既有金鑰時，於行 270 先執行 `pd = pemToDer(pem)`，隨後行 274 `loadPrivateKey(pem)` 內部再次呼叫 `pemToDer(pem)`，存在重複 Base64 解碼。
  * **理由**：微幅冗餘運算，對程式正確性與電視實際執行無任何負面影響，不影響功能。
