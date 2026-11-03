package com.example.tvagent;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.util.Base64;

import javax.crypto.Cipher;

/**
 * adb 傳輸協定連線端（純 Java，不依賴任何 android.* API）。
 *
 * 流程：TCP 連線 → 送 CNXN → 回 AUTH TOKEN 就送 SIGNATURE，被拒再送
 * RSAPUBLICKEY → 對方回 CNXN 即認證完成 → OPEN "shell:&lt;cmd&gt;" 收 WRTE 回 CLSE。
 *
 * 錯誤分類（AdbException.kind）：connect / auth_timeout / auth_rejected /
 * tls_required / protocol / io。
 */
public class AdbClient {

    public static final String CONNECT = "connect";
    public static final String AUTH_TIMEOUT = "auth_timeout";
    public static final String AUTH_REJECTED = "auth_rejected";
    public static final String TLS_REQUIRED = "tls_required";
    public static final String PROTOCOL = "protocol";
    public static final String IO = "io";

    public static class AdbException extends Exception {
        public final String kind;

        public AdbException(String kind, String message) {
            super(message);
            this.kind = kind;
        }

        public AdbException(String kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }
    }

    // adb protocol message commands（小端組出 ASCII）。
    static final int CMD_CNXN = 0x4e584e43; // "CNXN"
    static final int CMD_AUTH = 0x48545541; // "AUTH"
    static final int CMD_OPEN = 0x4e45504f; // "OPEN"
    static final int CMD_OKAY = 0x59414b4f; // "OKAY"
    static final int CMD_WRTE = 0x45545257; // "WRTE"
    static final int CMD_CLSE = 0x45534c43; // "CLSE"
    static final int CMD_STLS = 0x534c5453; // "STLS"

    static final int AUTH_TOKEN = 1;
    static final int AUTH_SIGNATURE = 2;
    static final int AUTH_RSAPUBLICKEY = 3;

    static final int VERSION = 0x01000000;
    static final int MAXDATA = 4096;
    static final int MAX_PAYLOAD = 4 * 1024 * 1024;
    static final byte[] EMPTY = new byte[0];

    static final String PUBKEY_COMMENT = "tvagent@tv";

    // SHA-1 DigestInfo 前綴（不含 digest 本體）：adbd 把 20 bytes token
    // 當成現成 digest 直接包進去，不再雜湊（等同 RSA_sign(NID_sha1)）。
    static final byte[] DIGEST_INFO_PREFIX = {
        (byte) 0x30, (byte) 0x21, (byte) 0x30, (byte) 0x09,
        (byte) 0x06, (byte) 0x05, (byte) 0x2b, (byte) 0x0e,
        (byte) 0x03, (byte) 0x02, (byte) 0x1a, (byte) 0x05,
        (byte) 0x00, (byte) 0x04, (byte) 0x14,
    };

    static final class Msg {
        int cmd;
        int arg0;
        int arg1;
        byte[] payload;
    }

    // ---- 封包序列化（測試的假 adbd 也共用；純靜態，不碰 socket 狀態） ----

    static int checksum(byte[] b, int len) {
        long sum = 0;
        for (int i = 0; i < len; i++) {
            sum += b[i] & 0xff;
        }
        return (int) (sum & 0xffffffffL);
    }

    static void writeMessage(OutputStream out, int cmd, int arg0, int arg1, byte[] payload)
            throws IOException {
        ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(cmd);
        h.putInt(arg0);
        h.putInt(arg1);
        h.putInt(payload.length);
        h.putInt(checksum(payload, payload.length));
        h.putInt(cmd ^ 0xffffffff);
        out.write(h.array());
        out.write(payload);
        out.flush();
    }

    /** peerVersion &lt; 0 表尚未收到對方 CNXN（此時 checksum 0 不算合法）。 */
    static Msg readMessage(DataInputStream in, long peerVersion) throws IOException, AdbException {
        byte[] h = new byte[24];
        in.readFully(h); // 不足即丟 EOFException，由呼叫端依階段分類
        ByteBuffer b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN);
        Msg m = new Msg();
        m.cmd = b.getInt();
        m.arg0 = b.getInt();
        m.arg1 = b.getInt();
        int len = b.getInt();
        int cksum = b.getInt();
        int magic = b.getInt();
        if (magic != (m.cmd ^ 0xffffffff)) {
            throw new AdbException(PROTOCOL, "bad magic for " + cmdName(m.cmd));
        }
        if (len < 0 || len > MAX_PAYLOAD) {
            throw new AdbException(PROTOCOL, "bad length " + len);
        }
        byte[] p = new byte[len];
        in.readFully(p);
        if (cksum != 0 || peerVersion < 0 || peerVersion < 0x01000001L) {
            // 對方 version>=0x01000001 時可能送 checksum 0，視為合法。
            if (checksum(p, len) != cksum) {
                throw new AdbException(PROTOCOL, "bad checksum for " + cmdName(m.cmd));
            }
        }
        m.payload = p;
        return m;
    }

    static String cmdName(int cmd) {
        byte[] b = new byte[4];
        b[0] = (byte) (cmd & 0xff);
        b[1] = (byte) ((cmd >> 8) & 0xff);
        b[2] = (byte) ((cmd >> 16) & 0xff);
        b[3] = (byte) ((cmd >> 24) & 0xff);
        return new String(b, StandardCharsets.US_ASCII).replaceAll("[^A-Z]", "?");
    }

    // ---- 金鑰 ----

    /** PEM 解析結果：DER 位元組＋是否為 PKCS#1。 */
    static final class PemDer {
        byte[] der;
        boolean pkcs1;
    }

    /** PEM 文字 → DER 位元組，接受 PKCS#8（BEGIN PRIVATE KEY）或 PKCS#1（BEGIN RSA PRIVATE KEY）。
     * 失敗一律丟帶步驟的 IOException（哪一步、什麼內容），並鏈住原始例外。 */
    static PemDer pemToDer(String pem) throws IOException {
        String body;
        boolean pkcs1;
        if (pem.contains("BEGIN RSA PRIVATE KEY")) {
            pkcs1 = true;
            body = pem.replace("-----BEGIN RSA PRIVATE KEY-----", "")
                    .replace("-----END RSA PRIVATE KEY-----", "");
        } else if (pem.contains("BEGIN PRIVATE KEY")) {
            pkcs1 = false;
            body = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                    .replace("-----END PRIVATE KEY-----", "");
        } else {
            throw new IOException(
                    "key PEM: unknown type (want 'BEGIN PRIVATE KEY' or 'BEGIN RSA PRIVATE KEY')");
        }
        body = body.replaceAll("\\s", "");
        try {
            PemDer r = new PemDer();
            r.der = Base64.getDecoder().decode(body);
            r.pkcs1 = pkcs1;
            return r;
        } catch (IllegalArgumentException e) {
            throw new IOException("key PEM: base64 decode failed: " + e.getMessage(), e);
        }
    }

    /** 從 PEM 文字載入私鑰，接受 PKCS#8（BEGIN PRIVATE KEY）或 PKCS#1（BEGIN RSA PRIVATE KEY）。 */
    public static PrivateKey loadPrivateKey(String pem) throws Exception {
        PemDer pd = pemToDer(pem);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        try {
            if (!pd.pkcs1) {
                return kf.generatePrivate(new PKCS8EncodedKeySpec(pd.der));
            }
            return kf.generatePrivate(parsePkcs1(pd.der));
        } catch (Exception e) {
            throw new IOException("load private key: KeyFactory generatePrivate failed ("
                    + (pd.pkcs1 ? "PKCS#1" : "PKCS#8") + ", der " + pd.der.length + " bytes): "
                    + e.getMessage(), e);
        }
    }

    public static PrivateKey loadPrivateKey(File f) throws Exception {
        return loadPrivateKey(readAll(f));
    }

    /** 從 DER 位元組直接解析 modulus／publicExponent 產生公鑰：不碰任何金鑰物件，
     * 不呼叫 getKeySpec、不讀金鑰物件的 getEncoded、不做任何轉型。
     * Conscrypt（電視）與桌面 JVM 走同一條路，行為一致。 */
    static PublicKey publicKeyFromDer(byte[] der, boolean pkcs1) throws Exception {
        RSAPrivateCrtKeySpec crt;
        try {
            crt = pkcs1 ? parsePkcs1(der) : parsePkcs8(der);
        } catch (IOException e) {
            throw new IOException("derive public key: DER parse failed ("
                    + (pkcs1 ? "PKCS#1" : "PKCS#8") + ", " + der.length + " bytes): "
                    + e.getMessage(), e);
        }
        return publicKeyFromCrt(crt, "derive public key");
    }

    /** 用已解析出的 CRT 參數產生公鑰；modulus／publicExponent 缺失時丟具體錯誤。 */
    static PublicKey publicKeyFromCrt(RSAPrivateCrtKeySpec crt, String step) throws Exception {
        BigInteger n = crt.getModulus();
        BigInteger e = crt.getPublicExponent();
        if (n == null || n.signum() <= 0) {
            throw new IOException(step + ": modulus missing or not positive");
        }
        if (e == null || e.signum() <= 0) {
            throw new IOException(step + ": publicExponent missing or not positive"
                    + " (modulus " + n.bitLength() + " bits)"
                    + " — key material does not carry a usable public exponent");
        }
        try {
            return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
        } catch (Exception ex) {
            throw new IOException(step + ": KeyFactory generatePublic failed"
                    + " (modulus " + n.bitLength() + " bits, e=" + e + "): " + ex.getMessage(), ex);
        }
    }

    /** 金鑰檔不存在就產生 RSA 2048 存成 PKCS#8 PEM；存在就載入。回傳 {private, public}。
     * 公鑰一律從金鑰檔的 DER 位元組解析（parsePkcs8／parsePkcs1），絕不從私鑰物件推導：
     * 不呼叫 getKeySpec、不讀私鑰物件的 getEncoded、不做任何轉型。Conscrypt 回傳非 CRT
     * 物件（getKeySpec 丟 RSAPrivateKeySpec、getEncoded 缺 publicExponent）的電視上也照樣可用；
     * 舊版只存了私鑰 PKCS#8 的金鑰檔照樣能讀。私鑰物件只用於簽章（Cipher RSA/ECB/NoPadding）。 */
    public static KeyPair loadOrGenerateKey(File f) throws Exception {
        if (f.exists()) {
            String pem;
            try {
                pem = readAll(f);
            } catch (IOException e) {
                throw new IOException("load key file " + f + ": read failed: " + e.getMessage(), e);
            }
            PemDer pd;
            try {
                pd = pemToDer(pem);
            } catch (IOException e) {
                throw new IOException("load key file " + f + ": " + e.getMessage(), e);
            }
            PrivateKey priv = loadPrivateKey(pem); // 內部錯誤已帶步驟（generatePrivate 失敗時）
            PublicKey pub = publicKeyFromDer(pd.der, pd.pkcs1);
            return new KeyPair(pub, priv);
        }
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        KeyPair kp = g.generateKeyPair();
        // getEncoded() 只用於把私鑰寫檔持久化，不用於推導公鑰參數。
        byte[] enc = kp.getPrivate().getEncoded();
        if (enc == null || enc.length == 0) {
            throw new IOException(
                    "generate key: private getEncoded() empty, cannot persist key file " + f);
        }
        String b64 = Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(enc);
        try {
            writeAll(f, "-----BEGIN PRIVATE KEY-----\n" + b64 + "\n-----END PRIVATE KEY-----\n");
        } catch (IOException e) {
            throw new IOException(
                    "generate key: write key file " + f + " failed: " + e.getMessage(), e);
        }
        // 產生後立刻把剛寫入的檔案重新讀回並解析：產生與載入兩條路徑得到相同公鑰。
        PemDer pd = pemToDer(readAll(f));
        PublicKey pub = publicKeyFromDer(pd.der, pd.pkcs1);
        return new KeyPair(pub, kp.getPrivate());
    }

    /** 僅供離線測試相容（既有測試呼叫）；App 正式路徑（loadOrGenerateKey）不呼叫此方法。
     * 從 priv.getEncoded() 的 PKCS#8 DER 解析：不呼叫 getKeySpec、不做任何轉型。
     * 注意：Conscrypt 非 CRT 金鑰物件的 getEncoded() 可能缺 publicExponent，此時丟出具體錯誤
     * （哪一步、哪個欄位）而非 ClassCastException；電視正式路徑不受影響（走檔案 DER）。 */
    static PublicKey publicKeyFromPrivate(PrivateKey priv) throws Exception {
        byte[] enc;
        try {
            enc = priv.getEncoded();
        } catch (Exception e) {
            throw new IOException("publicKeyFromPrivate: private getEncoded() threw "
                    + e.getClass().getName() + ": " + e.getMessage(), e);
        }
        if (enc == null || enc.length == 0) {
            throw new IOException("publicKeyFromPrivate: private getEncoded() empty"
                    + " (provider class " + priv.getClass().getName() + ")");
        }
        RSAPrivateCrtKeySpec spec;
        try {
            spec = parsePkcs8(enc);
        } catch (IOException e) {
            throw new IOException("publicKeyFromPrivate: parse PKCS#8 DER from key object failed"
                    + " (" + enc.length + " bytes, provider " + priv.getClass().getName() + "): "
                    + e.getMessage(), e);
        }
        try {
            return publicKeyFromCrt(spec, "publicKeyFromPrivate(" + priv.getClass().getName() + ")");
        } catch (IOException e) {
            throw new IOException(e.getMessage(), e);
        }
    }

    /** 最小 DER 解析：PKCS#8（外層 SEQUENCE 包著內層 PKCS#1 OCTET STRING），回傳內層 CRT 參數。
     * 失敗訊息帶欄位名與位移；原始例外一律鏈住（cause），不吞 suppressed。 */
    static RSAPrivateCrtKeySpec parsePkcs8(byte[] pkcs8) throws IOException {
        int[] pos = new int[] { 0 };
        expectTag(pkcs8, pos, 0x30, "PKCS#8 outer SEQUENCE");
        readLen(pkcs8, pos, "PKCS#8 outer length");
        expectTag(pkcs8, pos, 0x02, "PKCS#8 version INTEGER");
        int vn = readLen(pkcs8, pos, "PKCS#8 version length");
        if (vn < 0 || pos[0] + vn > pkcs8.length) {
            throw new IOException("PKCS#8 version overruns input"
                    + " (need " + vn + " bytes at offset " + pos[0] + ", total " + pkcs8.length + ")");
        }
        pos[0] += vn;
        expectTag(pkcs8, pos, 0x30, "PKCS#8 AlgorithmIdentifier SEQUENCE");
        int an = readLen(pkcs8, pos, "PKCS#8 AlgorithmIdentifier length");
        if (an < 0 || pos[0] + an > pkcs8.length) {
            throw new IOException("PKCS#8 AlgorithmIdentifier overruns input"
                    + " (need " + an + " bytes at offset " + pos[0] + ", total " + pkcs8.length + ")");
        }
        pos[0] += an;
        expectTag(pkcs8, pos, 0x04, "PKCS#8 privateKey OCTET STRING");
        int innerLen = readLen(pkcs8, pos, "PKCS#8 privateKey length");
        if (innerLen < 0 || pos[0] + innerLen > pkcs8.length) {
            throw new IOException("PKCS#8 privateKey length bad"
                    + " (innerLen=" + innerLen + " at offset " + pos[0]
                    + ", total " + pkcs8.length + ")");
        }
        byte[] inner = new byte[innerLen];
        System.arraycopy(pkcs8, pos[0], inner, 0, innerLen);
        try {
            return parsePkcs1(inner);
        } catch (IOException e) {
            throw new IOException("PKCS#8 inner PKCS#1 parse failed: " + e.getMessage(), e);
        }
    }

    static final String[] PKCS1_FIELDS = { "version", "modulus n", "publicExponent e",
        "privateExponent d", "prime1 p", "prime2 q", "exponent1 dp", "exponent2 dq",
        "coefficient qinv" };

    /** 最小 DER 解析：PKCS#1 RSAPrivateKey（9 個 INTEGER）。失敗訊息帶欄位名與位移。 */
    static RSAPrivateCrtKeySpec parsePkcs1(byte[] der) throws IOException {
        int[] pos = new int[] { 0 };
        expectTag(der, pos, 0x30, "PKCS#1 SEQUENCE");
        readLen(der, pos, "PKCS#1 SEQUENCE length");
        BigInteger[] v = new BigInteger[9];
        for (int i = 0; i < 9; i++) {
            String field = "PKCS#1 field[" + i + "] " + PKCS1_FIELDS[i];
            expectTag(der, pos, 0x02, field);
            int n = readLen(der, pos, field + " length");
            if (n < 0 || pos[0] + n > der.length) {
                throw new IOException(field + " overruns input"
                        + " (need " + n + " bytes at offset " + pos[0] + ", total " + der.length + ")");
            }
            byte[] raw = new byte[n];
            System.arraycopy(der, pos[0], raw, 0, n);
            pos[0] += n;
            v[i] = new BigInteger(raw);
        }
        return new RSAPrivateCrtKeySpec(v[1], v[2], v[3], v[4], v[5], v[6], v[7], v[8]);
    }

    private static void expectTag(byte[] der, int[] pos, int tag, String what) throws IOException {
        if (pos[0] >= der.length) {
            throw new IOException(what + ": unexpected end of input at offset " + pos[0]
                    + " (total " + der.length + ")");
        }
        int got = der[pos[0]++] & 0xff;
        if (got != tag) {
            throw new IOException(what + ": bad tag 0x" + Integer.toHexString(got)
                    + ", want 0x" + Integer.toHexString(tag) + " at offset " + (pos[0] - 1));
        }
    }

    private static int readLen(byte[] der, int[] pos, String what) throws IOException {
        if (pos[0] >= der.length) {
            throw new IOException(what + ": unexpected end of input at offset " + pos[0]
                    + " (total " + der.length + ")");
        }
        int b = der[pos[0]++] & 0xff;
        if (b < 0x80) {
            return b;
        }
        int n = b & 0x7f;
        if (n <= 0 || n > 4 || pos[0] + n > der.length) {
            throw new IOException(what + ": bad length prefix 0x" + Integer.toHexString(b)
                    + " at offset " + (pos[0] - 1) + " (total " + der.length + ")");
        }
        int len = 0;
        for (int i = 0; i < n; i++) {
            len = (len << 8) | (der[pos[0]++] & 0xff);
        }
        return len;
    }

    /** Android adb 格式公鑰本體：524 bytes mincrypt 結構（len=64、n0inv、n 小端、rr、e）。
     * n、e 優先經 RSAPublicKey 介面直接取得（Conscrypt 有實作，見工單要求 2；
     * instanceof 永不丟例外，後面的轉型因已檢查而安全），非 RSAPublicKey 實作才走
     * KeyFactory.getKeySpec(RSAPublicKeySpec)。全程不碰私鑰、不轉型私鑰。 */
    public static byte[] mincryptPublicKey(PublicKey pub) throws Exception {
        BigInteger n;
        BigInteger e;
        if (pub instanceof RSAPublicKey) {
            RSAPublicKey rsa = (RSAPublicKey) pub;
            n = rsa.getModulus();
            e = rsa.getPublicExponent();
        } else {
            RSAPublicKeySpec spec =
                    KeyFactory.getInstance("RSA").getKeySpec(pub, RSAPublicKeySpec.class);
            n = spec.getModulus();
            e = spec.getPublicExponent();
        }
        long n32 = n.and(BigInteger.valueOf(0xffffffffL)).longValue();
        long x = 1; // Newton 求 n mod 2^32 的反元素
        for (int i = 0; i < 5; i++) {
            x = (x * (2 - n32 * x)) & 0xffffffffL;
        }
        long n0inv = (0x100000000L - x) & 0xffffffffL;
        BigInteger rr = BigInteger.ONE.shiftLeft(4096).mod(n);
        ByteBuffer b = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(64);
        b.putInt((int) n0inv);
        b.put(toLittleEndian256(n));
        b.put(toLittleEndian256(rr));
        b.putInt(e.intValue());
        return b.array();
    }

    static byte[] toLittleEndian256(BigInteger v) {
        byte[] be = v.toByteArray();
        int start = 0;
        while (be.length - start > 256) {
            start++; // 不應發生（2048 位元）；防呆
        }
        if (be.length - start > 0 && be[start] == 0 && be.length - start == 257) {
            start++;
        }
        byte[] le = new byte[256];
        int n = be.length - start;
        for (int i = 0; i < n && i < 256; i++) {
            le[i] = be[be.length - 1 - i];
        }
        return le;
    }

    /** "base64(524 bytes) tvagent@tv"；送給 adbd 時尾端再加一個 \0。 */
    public static String adbPublicKeyString(PublicKey pub) throws Exception {
        return Base64.getEncoder().encodeToString(mincryptPublicKey(pub)) + " " + PUBKEY_COMMENT;
    }

    /**
     * 對 20 bytes token 簽章：EM = 00 01 FF.. 00 || DigestInfo(SHA-1, token)，
     * 再做 RSA 私鑰運算（確定性，等同 RSA_sign(NID_sha1)；不用 SHA1withRSA，
     * 那會先把 token 再雜湊一次，結果不同）。
     */
    public static byte[] signToken(PrivateKey priv, byte[] token) throws Exception {
        if (token.length != 20) {
            throw new IOException("token must be 20 bytes");
        }
        int diLen = DIGEST_INFO_PREFIX.length + 20;
        byte[] em = new byte[256];
        em[0] = 0x00;
        em[1] = 0x01;
        for (int i = 2; i < 256 - diLen - 1; i++) {
            em[i] = (byte) 0xff;
        }
        em[256 - diLen - 1] = 0x00;
        System.arraycopy(DIGEST_INFO_PREFIX, 0, em, 256 - diLen, DIGEST_INFO_PREFIX.length);
        System.arraycopy(token, 0, em, 256 - 20, 20);
        Cipher c = Cipher.getInstance("RSA/ECB/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, priv);
        return c.doFinal(em);
    }

    static String readAll(File f) throws IOException {
        FileInputStream fis = new FileInputStream(f);
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = fis.read(tmp)) >= 0) {
                b.write(tmp, 0, n);
            }
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            fis.close();
        }
    }

    static void writeAll(File f, String s) throws IOException {
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(s.getBytes(StandardCharsets.UTF_8));
        } finally {
            fos.close();
        }
    }

    // ---- 連線 ----

    private final String host;
    private final int port;
    private final PrivateKey priv;
    private final PublicKey pub;
    private long authTimeoutMs = 60000;
    private long ioTimeoutMs = 10000;

    private Socket sock;
    private DataInputStream in;
    private OutputStream out;
    private long peerVersion = -1;
    private int peerMaxdata = MAXDATA;
    private int nextLocalId = 1;

    public AdbClient(String host, int port, PrivateKey priv, PublicKey pub) {
        this.host = host;
        this.port = port;
        this.priv = priv;
        this.pub = pub;
    }

    public static AdbClient withKeyFile(String host, int port, File keyFile) throws Exception {
        KeyPair kp = loadOrGenerateKey(keyFile);
        return new AdbClient(host, port, kp.getPrivate(), kp.getPublic());
    }

    public void setAuthTimeoutMs(long ms) {
        authTimeoutMs = ms;
    }

    public void setIoTimeoutMs(long ms) {
        ioTimeoutMs = ms;
    }

    /** 執行一條 shell 指令並回傳完整輸出（多個 WRTE 會拼接）。 */
    public String shell(String command) throws AdbException {
        try {
            connect();
        } catch (SocketTimeoutException e) {
            throw new AdbException(CONNECT, "connect timed out: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new AdbException(CONNECT, "connect failed: " + e.getMessage(), e);
        }
        try {
            authenticate();
            return openShell(command);
        } finally {
            closeQuietly();
        }
    }

    private void connect() throws IOException {
        sock = new Socket();
        sock.connect(new InetSocketAddress(host, port), (int) Math.min(ioTimeoutMs, 60000));
        sock.setSoTimeout((int) Math.min(ioTimeoutMs, 60000));
        in = new DataInputStream(sock.getInputStream());
        out = sock.getOutputStream();
    }

    private void closeQuietly() {
        try {
            if (sock != null) {
                sock.close();
            }
        } catch (IOException ignored) {
            // 關閉失敗不用回報
        }
        sock = null;
    }

    private void send(int cmd, int arg0, int arg1, byte[] payload) throws AdbException {
        if (payload.length > peerMaxdata) {
            throw new AdbException(PROTOCOL, "payload " + payload.length
                    + " exceeds peer maxdata " + peerMaxdata);
        }
        try {
            writeMessage(out, cmd, arg0, arg1, payload);
        } catch (IOException e) {
            throw new AdbException(IO, "send failed: " + e.getMessage(), e);
        }
    }

    private Msg recv() throws IOException, AdbException {
        return readMessage(in, peerVersion);
    }

    private void authenticate() throws AdbException {
        send(CMD_CNXN, VERSION, MAXDATA, "host::tvagent\0".getBytes(StandardCharsets.UTF_8));
        long deadline = System.currentTimeMillis() + authTimeoutMs;
        byte[] pubkeyPayload = null;
        int tokenCount = 0;
        for (;;) {
            Msg m;
            try {
                m = recv();
            } catch (SocketTimeoutException e) {
                if (System.currentTimeMillis() >= deadline) {
                    throw new AdbException(AUTH_TIMEOUT, "waiting for auth CNXN timed out");
                }
                continue;
            } catch (EOFException e) {
                throw new AdbException(AUTH_REJECTED, "disconnected during auth");
            } catch (SocketException e) {
                throw new AdbException(AUTH_REJECTED, "disconnected during auth: " + e.getMessage());
            } catch (AdbException e) {
                throw e;
            } catch (IOException e) {
                throw new AdbException(IO, "io during auth: " + e.getMessage(), e);
            }
            if (m.cmd == CMD_STLS) {
                throw new AdbException(TLS_REQUIRED, "server requires TLS");
            }
            if (m.cmd == CMD_CNXN) {
                peerVersion = m.arg0 & 0xffffffffL;
                if (m.arg1 <= 0) {
                    throw new AdbException(PROTOCOL, "bad peer maxdata " + m.arg1);
                }
                peerMaxdata = m.arg1;
                return; // 認證完成
            }
            if (m.cmd == CMD_AUTH && m.arg0 == AUTH_TOKEN) {
                if (m.payload.length != 20) {
                    throw new AdbException(PROTOCOL, "bad token length " + m.payload.length);
                }
                tokenCount++;
                try {
                    if (tokenCount == 2) {
                        if (pubkeyPayload == null) {
                            String s = adbPublicKeyString(pub);
                            pubkeyPayload = (s + "\0").getBytes(StandardCharsets.UTF_8);
                        }
                        send(CMD_AUTH, AUTH_RSAPUBLICKEY, 0, pubkeyPayload);
                    } else {
                        send(CMD_AUTH, AUTH_SIGNATURE, 0, signToken(priv, m.payload));
                    }
                } catch (AdbException e) {
                    throw e;
                } catch (Exception e) {
                    throw new AdbException(IO, "sign failed: " + e.getMessage(), e);
                }
                if (System.currentTimeMillis() >= deadline) {
                    throw new AdbException(AUTH_TIMEOUT, "auth timed out");
                }
                continue;
            }
            if (m.cmd == CMD_CLSE) {
                throw new AdbException(AUTH_REJECTED, "closed during auth");
            }
            throw new AdbException(PROTOCOL, "unexpected " + cmdName(m.cmd) + " during auth");
        }
    }

    private String openShell(String command) throws AdbException {
        int local = nextLocalId++;
        byte[] req = ("shell:" + command + "\0").getBytes(StandardCharsets.UTF_8);
        send(CMD_OPEN, local, 0, req);
        int remote = -1;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (;;) {
            Msg m;
            try {
                m = recv();
            } catch (SocketTimeoutException e) {
                throw new AdbException(IO, "shell timed out");
            } catch (EOFException e) {
                throw new AdbException(IO, "disconnected during shell");
            } catch (AdbException e) {
                throw e;
            } catch (IOException e) {
                throw new AdbException(IO, "io during shell: " + e.getMessage(), e);
            }
            if (m.cmd == CMD_STLS) {
                throw new AdbException(TLS_REQUIRED, "server requires TLS");
            }
            if (m.cmd == CMD_OKAY && m.arg1 == local) {
                remote = m.arg0;
                continue;
            }
            if (m.cmd == CMD_WRTE && m.arg1 == local && (remote < 0 || m.arg0 == remote)) {
                if (remote < 0) {
                    remote = m.arg0;
                }
                buf.write(m.payload, 0, m.payload.length);
                send(CMD_OKAY, local, remote, EMPTY);
                continue;
            }
            if (m.cmd == CMD_CLSE && (m.arg1 == local || m.arg0 == remote)) {
                try {
                    send(CMD_CLSE, local, remote < 0 ? 0 : remote, EMPTY);
                } catch (AdbException ignored) {
                    // 回 CLSE 失敗不影響已收到的輸出
                }
                return new String(buf.toByteArray(), StandardCharsets.UTF_8);
            }
            throw new AdbException(PROTOCOL, "unexpected " + cmdName(m.cmd) + " during shell");
        }
    }

    /**
     * 供 Docker／Mac 單獨使用（電視上不用）：java AdbClient host port keyfile shell指令…
     * 金鑰檔不存在就產生。成功印指令輸出；失敗印「類別: 訊息」並以非 0 結束。
     */
    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("usage: AdbClient <host> <port> <keyfile> <shell command...>");
            System.exit(2);
            return;
        }
        StringBuilder cmd = new StringBuilder();
        for (int i = 3; i < args.length; i++) {
            if (i > 3) {
                cmd.append(' ');
            }
            cmd.append(args[i]);
        }
        try {
            AdbClient c = withKeyFile(args[0], Integer.parseInt(args[1]), new File(args[2]));
            System.out.print(c.shell(cmd.toString()));
        } catch (AdbException e) {
            System.err.println(e.kind + ": " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println(IO + ": " + e.getMessage());
            System.exit(1);
        }
    }
}
