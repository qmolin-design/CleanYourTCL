package com.example.tvagent;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.RSAPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

/**
 * 離線協定測試（純 Java）：內建假 adbd（ServerSocket 127.0.0.1）。
 * 用法：java com.example.tvagent.AdbClientTest &lt;testdata 目錄&gt;
 * 最後一行印 ALL PASS；任一失敗以非 0 結束。
 */
public class AdbClientTest {

    static int failures = 0;

    static void check(boolean cond, String name, String detail) {
        if (cond) {
            System.out.println("PASS " + name);
        } else {
            System.out.println("FAIL " + name + " :: " + detail);
            failures++;
        }
    }

    // ---------- 小工具 ----------

    static byte[] readFile(File f) throws Exception {
        FileInputStream fis = new FileInputStream(f);
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] tmp = new byte[8192];
            int n;
            while ((n = fis.read(tmp)) >= 0) {
                b.write(tmp, 0, n);
            }
            return b.toByteArray();
        } finally {
            fis.close();
        }
    }

    static int freePort() throws Exception {
        ServerSocket s = new ServerSocket(0);
        try {
            return s.getLocalPort();
        } finally {
            s.close();
        }
    }

    static String runClient(int port, PrivateKey priv, PublicKey pub, String cmd,
            long authMs, long ioMs) throws AdbClient.AdbException {
        AdbClient c = new AdbClient("127.0.0.1", port, priv, pub);
        c.setAuthTimeoutMs(authMs);
        c.setIoTimeoutMs(ioMs);
        return c.shell(cmd);
    }

    // ---------- 獨立驗算（不經過 AdbClient 的金鑰程式碼） ----------

    static final class ParsedKey {
        BigInteger n;
        BigInteger e;
    }

    static ParsedKey parseMincrypt(byte[] raw) throws Exception {
        if (raw.length != 524) {
            throw new Exception("pubkey raw length " + raw.length + " != 524");
        }
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        int len = b.getInt();
        if (len != 64) {
            throw new Exception("len field " + len + " != 64");
        }
        long n0inv = b.getInt() & 0xffffffffL;
        byte[] nLe = new byte[256];
        byte[] rrLe = new byte[256];
        b.get(nLe);
        b.get(rrLe);
        int e = b.getInt();
        byte[] nBe = new byte[256];
        byte[] rrBe = new byte[256];
        for (int i = 0; i < 256; i++) {
            nBe[i] = nLe[255 - i];
            rrBe[i] = rrLe[255 - i];
        }
        BigInteger n = new BigInteger(1, nBe);
        BigInteger rr = new BigInteger(1, rrBe);
        BigInteger two32 = BigInteger.ONE.shiftLeft(32);
        if (!n.multiply(BigInteger.valueOf(n0inv)).mod(two32).equals(two32.subtract(BigInteger.ONE))) {
            throw new Exception("n0inv mismatch");
        }
        if (!BigInteger.ONE.shiftLeft(4096).mod(n).equals(rr)) {
            throw new Exception("rr mismatch");
        }
        ParsedKey k = new ParsedKey();
        k.n = n;
        k.e = BigInteger.valueOf(e & 0xffffffffL);
        return k;
    }

    static void verifySignature(byte[] sig, byte[] token, ParsedKey k) throws Exception {
        if (sig.length != 256) {
            throw new Exception("sig length " + sig.length);
        }
        BigInteger m = new BigInteger(1, sig).modPow(k.e, k.n);
        byte[] be = m.toByteArray();
        byte[] em = new byte[256];
        int copyFrom = Math.max(0, be.length - 256);
        int copyLen = Math.min(be.length, 256);
        System.arraycopy(be, copyFrom, em, 256 - copyLen, copyLen);
        byte[] prefix = {
            (byte) 0x30, (byte) 0x21, (byte) 0x30, (byte) 0x09,
            (byte) 0x06, (byte) 0x05, (byte) 0x2b, (byte) 0x0e,
            (byte) 0x03, (byte) 0x02, (byte) 0x1a, (byte) 0x05,
            (byte) 0x00, (byte) 0x04, (byte) 0x14,
        };
        int diLen = prefix.length + token.length;
        byte[] expect = new byte[256];
        expect[0] = 0x00;
        expect[1] = 0x01;
        Arrays.fill(expect, 2, 256 - diLen - 1, (byte) 0xff);
        expect[256 - diLen - 1] = 0x00;
        System.arraycopy(prefix, 0, expect, 256 - diLen, prefix.length);
        System.arraycopy(token, 0, expect, 256 - token.length, token.length);
        if (!Arrays.equals(em, expect)) {
            throw new Exception("sig^e mod n != PKCS#1 v1.5(SHA-1 DigestInfo || token)");
        }
    }

    // ---------- 假 adbd ----------

    static final class Fake {
        enum Auth {
            REJECT_FIRST, ACCEPT_FIRST, SILENT, CLOSE_AFTER_TOKEN, STLS,
            BAD_MAGIC, BAD_CHECKSUM, GARBAGE_CMD
        }

        enum Shell {
            ECHO_OK, BIG, TRUNCATED, NONE
        }

        final Auth auth;
        final Shell shell;
        final int version;
        final int maxdata;
        ParsedKey knownKey; // ACCEPT_FIRST 用：上一輪記下的公鑰
        byte[] bigOutput;

        ServerSocket ss;
        Thread thread;
        volatile String failure;
        volatile boolean sawPubkey;
        volatile ParsedKey capturedKey;
        volatile byte[] capturedSig1;
        volatile byte[] capturedToken1;

        Fake(Auth auth, Shell shell, int version, int maxdata) {
            this.auth = auth;
            this.shell = shell;
            this.version = version;
            this.maxdata = maxdata;
        }

        int start() throws Exception {
            ss = new ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"));
            thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Socket s = ss.accept();
                        try {
                            handle(s);
                        } finally {
                            try {
                                s.close();
                            } catch (Exception ignored) {
                                // 關閉失敗不影響判定
                            }
                        }
                    } catch (Exception e) {
                        note("accept/handler: " + e);
                    }
                }
            });
            thread.setDaemon(true);
            thread.start();
            return ss.getLocalPort();
        }

        void awaitDone(long timeoutMs) throws Exception {
            thread.join(timeoutMs);
            try {
                ss.close();
            } catch (Exception ignored) {
                // 關閉失敗不影響判定
            }
        }

        void note(String f) {
            if (failure == null) {
                failure = f;
            }
        }

        void expect(boolean cond, String what) {
            if (!cond) {
                note(what);
                throw new RuntimeException(what);
            }
        }

        void checkCap(AdbClient.Msg m, String what) {
            expect(m.payload.length <= maxdata,
                    what + " payload " + m.payload.length + " exceeds maxdata " + maxdata);
        }

        static void writeRawHeader(OutputStream out, int cmd, int a0, int a1,
                int len, int cksum, int magic) throws Exception {
            ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
            h.putInt(cmd);
            h.putInt(a0);
            h.putInt(a1);
            h.putInt(len);
            h.putInt(cksum);
            h.putInt(magic);
            out.write(h.array());
            out.flush();
        }

        void handle(Socket s) throws Exception {
            s.setSoTimeout(10000);
            DataInputStream in = new DataInputStream(s.getInputStream());
            OutputStream out = s.getOutputStream();

            AdbClient.Msg hello;
            try {
                hello = AdbClient.readMessage(in, -1);
            } catch (Exception e) {
                note("reading client CNXN: " + e);
                return;
            }
            try {
                expect(hello.cmd == AdbClient.CMD_CNXN, "first must be CNXN");
                checkCap(hello, "client CNXN");
            } catch (RuntimeException e) {
                return;
            }

            byte[] devId = "device::\0".getBytes(StandardCharsets.UTF_8);
            if (auth == Auth.BAD_MAGIC) {
                writeRawHeader(out, AdbClient.CMD_CNXN, version, maxdata, 0, 0, 0x12345678);
                return;
            }
            if (auth == Auth.BAD_CHECKSUM) {
                ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
                h.putInt(AdbClient.CMD_CNXN);
                h.putInt(version);
                h.putInt(maxdata);
                h.putInt(devId.length);
                h.putInt(AdbClient.checksum(devId, devId.length) + 1); // 故意寫錯
                h.putInt(AdbClient.CMD_CNXN ^ 0xffffffff);
                out.write(h.array());
                out.write(devId);
                out.flush();
                return;
            }
            if (auth == Auth.STLS) {
                AdbClient.writeMessage(out, AdbClient.CMD_STLS, 0, 0, AdbClient.EMPTY);
                return;
            }
            if (auth == Auth.GARBAGE_CMD) {
                AdbClient.writeMessage(out, AdbClient.CMD_OKAY, 1, 2, AdbClient.EMPTY);
                return;
            }
            if (auth == Auth.SILENT) {
                // 真 adbd 未授權時只回 AUTH TOKEN（不會先回 CNXN）；沉默即一個都不回。
                try {
                    AdbClient.readMessage(in, 0x01000000L); // 等客戶端自己放棄並斷線
                } catch (Exception ignored) {
                    // 客戶端斷線是預期行為
                }
                return;
            }

            byte[] token1 = new byte[20];
            for (int i = 0; i < 20; i++) {
                token1[i] = (byte) (i + 1);
            }
            byte[] token2 = new byte[20];
            for (int i = 0; i < 20; i++) {
                token2[i] = (byte) (0x51 + i);
            }
            AdbClient.writeMessage(out, AdbClient.CMD_AUTH, AdbClient.AUTH_TOKEN, 0, token1);
            if (auth == Auth.CLOSE_AFTER_TOKEN) {
                s.close();
                return;
            }

            AdbClient.Msg sig1;
            try {
                sig1 = AdbClient.readMessage(in, version);
                expect(sig1.cmd == AdbClient.CMD_AUTH && sig1.arg0 == AdbClient.AUTH_SIGNATURE,
                        "expect SIGNATURE, got " + AdbClient.cmdName(sig1.cmd));
                checkCap(sig1, "client SIGNATURE");
                expect(sig1.payload.length == 256, "sig length " + sig1.payload.length);
            } catch (RuntimeException e) {
                return;
            }

            if (auth == Auth.ACCEPT_FIRST) {
                try {
                    expect(knownKey != null, "no known key for round 2");
                    verifySignature(sig1.payload, token1, knownKey);
                    AdbClient.writeMessage(out, AdbClient.CMD_CNXN, version, maxdata, devId);
                } catch (Exception e) {
                    note("round2 verify: " + e.getMessage());
                    return;
                }
            } else {
                // 拒絕第一輪簽章，要求送公鑰
                AdbClient.writeMessage(out, AdbClient.CMD_AUTH, AdbClient.AUTH_TOKEN, 0, token2);
                AdbClient.Msg pub;
                try {
                    pub = AdbClient.readMessage(in, version);
                    expect(pub.cmd == AdbClient.CMD_AUTH
                            && pub.arg0 == AdbClient.AUTH_RSAPUBLICKEY,
                            "expect RSAPUBLICKEY, got " + AdbClient.cmdName(pub.cmd));
                    checkCap(pub, "client RSAPUBLICKEY");
                    expect(pub.payload.length > 0
                            && pub.payload[pub.payload.length - 1] == 0,
                            "RSAPUBLICKEY must end with NUL");
                    sawPubkey = true;
                    byte[] raw = Base64.getDecoder().decode(new String(pub.payload, 0,
                            pub.payload.length - 1, StandardCharsets.US_ASCII).split(" ")[0]);
                    capturedKey = parseMincrypt(raw);
                    capturedToken1 = token1;
                    capturedSig1 = sig1.payload;
                    verifySignature(sig1.payload, token1, capturedKey);
                    AdbClient.writeMessage(out, AdbClient.CMD_CNXN, version, maxdata, devId);
                } catch (RuntimeException e) {
                    return;
                } catch (Exception e) {
                    note("pubkey/sig verify: " + e.getMessage());
                    return;
                }
            }

            // 認證完成，接 OPEN
            AdbClient.Msg open;
            try {
                open = AdbClient.readMessage(in, version);
                expect(open.cmd == AdbClient.CMD_OPEN, "expect OPEN");
                checkCap(open, "client OPEN");
            } catch (RuntimeException e) {
                return;
            }
            int local = open.arg0;
            int remote = 7;
            String dest = new String(open.payload, 0,
                    Math.max(0, open.payload.length - 1), StandardCharsets.UTF_8);
            try {
                if (shell == Shell.ECHO_OK) {
                    expect(dest.equals("shell:echo ok"), "unexpected dest " + dest);
                    byte[] ok = "ok\n".getBytes(StandardCharsets.UTF_8);
                    AdbClient.writeMessage(out, AdbClient.CMD_OKAY, remote, local,
                            AdbClient.EMPTY);
                    AdbClient.writeMessage(out, AdbClient.CMD_WRTE, remote, local, ok);
                    AdbClient.writeMessage(out, AdbClient.CMD_CLSE, remote, local,
                            AdbClient.EMPTY);
                    AdbClient.Msg a1 = AdbClient.readMessage(in, version);
                    expect(a1.cmd == AdbClient.CMD_OKAY, "expect OKAY ack");
                    AdbClient.Msg a2 = AdbClient.readMessage(in, version);
                    expect(a2.cmd == AdbClient.CMD_CLSE, "expect CLSE reply");
                } else if (shell == Shell.BIG) {
                    AdbClient.writeMessage(out, AdbClient.CMD_OKAY, remote, local,
                            AdbClient.EMPTY);
                    int off = 0;
                    int n = 0;
                    while (off < bigOutput.length) {
                        int len = Math.min(2000, bigOutput.length - off);
                        byte[] chunk = Arrays.copyOfRange(bigOutput, off, off + len);
                        if (n == 1) {
                            // 其中一片故意 checksum 填 0（version>=1 視為合法）
                            ByteBuffer h = ByteBuffer.allocate(24)
                                    .order(ByteOrder.LITTLE_ENDIAN);
                            h.putInt(AdbClient.CMD_WRTE);
                            h.putInt(remote);
                            h.putInt(local);
                            h.putInt(chunk.length);
                            h.putInt(0);
                            h.putInt(AdbClient.CMD_WRTE ^ 0xffffffff);
                            out.write(h.array());
                            out.write(chunk);
                            out.flush();
                        } else {
                            AdbClient.writeMessage(out, AdbClient.CMD_WRTE, remote, local, chunk);
                        }
                        off += len;
                        n++;
                    }
                    AdbClient.writeMessage(out, AdbClient.CMD_CLSE, remote, local,
                            AdbClient.EMPTY);
                    for (int i = 0; i < n; i++) {
                        AdbClient.Msg a = AdbClient.readMessage(in, version);
                        expect(a.cmd == AdbClient.CMD_OKAY, "expect OKAY ack " + i);
                    }
                    AdbClient.Msg c = AdbClient.readMessage(in, version);
                    expect(c.cmd == AdbClient.CMD_CLSE, "expect CLSE reply");
                } else if (shell == Shell.TRUNCATED) {
                    AdbClient.writeMessage(out, AdbClient.CMD_OKAY, remote, local,
                            AdbClient.EMPTY);
                    ByteBuffer h = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN);
                    h.putInt(AdbClient.CMD_WRTE);
                    h.putInt(remote);
                    h.putInt(local);
                    h.putInt(100); // 宣稱 100，實際只送 10 就斷線
                    h.putInt(0);
                    h.putInt(AdbClient.CMD_WRTE ^ 0xffffffff);
                    out.write(h.array());
                    out.write(new byte[10]);
                    out.flush();
                    s.close();
                }
            } catch (RuntimeException e) {
                return;
            }
        }
    }

    // ---------- 測試 ----------

    static void testVectors(String td) throws Exception {
        PrivateKey priv = AdbClient.loadPrivateKey(new File(td + "/adbkey"));
        RSAPrivateCrtKey crt = (RSAPrivateCrtKey) priv;
        PublicKey pub = KeyFactory.getInstance("RSA").generatePublic(
                new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        String mine = AdbClient.adbPublicKeyString(pub).split(" ")[0];
        String theirs = new String(readFile(new File(td + "/adbkey.pub")),
                StandardCharsets.US_ASCII).split("\\s+")[0];
        check(mine.equals(theirs), "vector-pubkey",
                "pubkey base64 differs (len " + mine.length() + " vs " + theirs.length() + ")");
        byte[] token = readFile(new File(td + "/token.bin"));
        byte[] expectSig = readFile(new File(td + "/token.sig"));
        byte[] sig = AdbClient.signToken(priv, token);
        check(Arrays.equals(sig, expectSig), "vector-sig", "signature bytes differ");
        byte[] wire = (AdbClient.adbPublicKeyString(pub) + "\0").getBytes(StandardCharsets.UTF_8);
        check(wire[wire.length - 1] == 0, "vector-pubkey-nul", "wire pubkey must end with NUL");
        // 已存在的金鑰檔（只有私鑰 PKCS#8）：loadOrGenerateKey 推導出的公鑰必須與向量一致
        KeyPair fromFile = AdbClient.loadOrGenerateKey(new File(td + "/adbkey"));
        check(Arrays.equals(fromFile.getPublic().getEncoded(), pub.getEncoded()),
                "vector-loadOrGenerate-pubkey", "public key derived from key file differs");
    }

    static void testOpaquePrivateKey() throws Exception {
        // 模擬 Conscrypt：金鑰物件不實作 RSAPrivateCrtKey。
        // 舊程式碼在此直接 (RSAPrivateCrtKey) 轉型會丟 ClassCastException（實機 agent.log 的錯誤）。
        KeyPair kp = freshKey();
        final PrivateKey real = kp.getPrivate();
        final byte[] enc = real.getEncoded();
        PrivateKey opaque = new PrivateKey() {
            @Override
            public String getAlgorithm() {
                return "RSA";
            }

            @Override
            public String getFormat() {
                return "PKCS#8";
            }

            @Override
            public byte[] getEncoded() {
                return enc.clone();
            }
        };
        PublicKey derived = AdbClient.publicKeyFromPrivate(opaque);
        check(Arrays.equals(derived.getEncoded(), kp.getPublic().getEncoded()),
                "opaque-priv-pubkey", "public key derived from opaque private key differs");
        PublicKey direct = AdbClient.publicKeyFromPrivate(real);
        check(Arrays.equals(direct.getEncoded(), kp.getPublic().getEncoded()),
                "direct-priv-pubkey", "public key derived from provider key differs");
    }

    static void testConscryptBrokenObjectStillLoadsFromFile(String td) throws Exception {
        // 模擬實機 Conscrypt（電視 Android 11 agent.log）：
        // KeyFactory.getKeySpec(priv, RSAPrivateCrtKeySpec.class) 回傳不含 CRT 參數的
        // RSAPrivateKeySpec（舊程式碼隱式轉型即丟 ClassCastException），且金鑰物件的 getEncoded()
        // 不含可用的 publicExponent。正式路徑一律從檔案 DER 解析，不碰金鑰物件。
        final byte[] noExponent = new byte[] { 0x30, 0x03, 0x02, 0x01, 0x00 };
        PrivateKey broken = new PrivateKey() {
            @Override
            public String getAlgorithm() {
                return "RSA";
            }

            @Override
            public String getFormat() {
                return "PKCS#8";
            }

            @Override
            public byte[] getEncoded() {
                return noExponent.clone();
            }
        };
        boolean threw = false;
        String msg = "";
        try {
            AdbClient.publicKeyFromPrivate(broken);
        } catch (Exception e) {
            threw = true;
            msg = String.valueOf(e.getMessage());
        }
        check(threw, "conscrypt-broken-object-throws",
                "publicKeyFromPrivate should refuse key material without publicExponent");
        check(msg.contains("publicExponent") || msg.contains("PKCS#8"),
                "conscrypt-broken-error-specific",
                "error must name the step/field, got [" + msg + "]");
        // 關鍵斷言：同一個「壞物件」情境下，從 testdata/adbkey 檔載入仍與 adbkey.pub 逐 byte 相同
        KeyPair fromFile = AdbClient.loadOrGenerateKey(new File(td + "/adbkey"));
        String mine = AdbClient.adbPublicKeyString(fromFile.getPublic()).split(" ")[0];
        String theirs = new String(readFile(new File(td + "/adbkey.pub")),
                StandardCharsets.US_ASCII).split("\\s+")[0];
        check(mine.equals(theirs), "conscrypt-file-pubkey-matches-pub",
                "file-derived pubkey base64 differs (len " + mine.length()
                        + " vs " + theirs.length() + ")");
    }

    static void testGenerateThenLoadSameKey() throws Exception {
        // 產生與載入兩條路徑必須得到相同公鑰（寫系統暫存目錄，不污染 repo）
        File tmp = File.createTempFile("tvagent-conscrypt-test", ".pem");
        tmp.delete();
        try {
            KeyPair k1 = AdbClient.loadOrGenerateKey(tmp);
            check(tmp.exists(), "generate-writes-file", "key file was not created");
            KeyPair k2 = AdbClient.loadOrGenerateKey(tmp);
            check(Arrays.equals(k1.getPublic().getEncoded(), k2.getPublic().getEncoded()),
                    "generate-load-same-pubkey",
                    "public key differs between generate and reload");
        } finally {
            tmp.delete();
        }
    }

    static KeyPair freshKey() throws Exception {

        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        return g.generateKeyPair();
    }

    static void testAuthRoundTrip() throws Exception {
        KeyPair kp = freshKey();
        // 第一輪：簽章被拒 → 送公鑰 → 通過
        Fake f1 = new Fake(Fake.Auth.REJECT_FIRST, Fake.Shell.ECHO_OK, 0x01000000, 2048);
        int p1 = f1.start();
        long t0 = System.currentTimeMillis();
        String out = runClient(p1, kp.getPrivate(), kp.getPublic(), "echo ok", 10000, 5000);
        long dt = System.currentTimeMillis() - t0;
        f1.awaitDone(8000);
        check(f1.failure == null, "round1-fake-clean",
                f1.failure == null ? "" : f1.failure);
        check(out.trim().equals("ok"), "round1-echo", "got [" + out.trim() + "]");
        check(f1.sawPubkey, "round1-sent-pubkey", "client never sent RSAPUBLICKEY");
        check(f1.capturedKey != null, "round1-key-parsed", "fake could not parse pubkey");
        check(dt < 15000, "round1-time", "took " + dt + " ms");

        // 第二輪：同一把金鑰簽章直接通過，不送公鑰
        Fake f2 = new Fake(Fake.Auth.ACCEPT_FIRST, Fake.Shell.ECHO_OK, 0x01000000, 4096);
        f2.knownKey = f1.capturedKey;
        int p2 = f2.start();
        String out2 = runClient(p2, kp.getPrivate(), kp.getPublic(), "echo ok", 10000, 5000);
        f2.awaitDone(8000);
        check(f2.failure == null, "round2-fake-clean",
                f2.failure == null ? "" : f2.failure);
        check(out2.trim().equals("ok"), "round2-echo", "got [" + out2.trim() + "]");
        check(!f2.sawPubkey, "round2-no-pubkey", "client sent RSAPUBLICKEY again");
    }

    static void testBigOutput() throws Exception {
        KeyPair kp = freshKey();
        byte[] big = new byte[10000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) (0x20 + (i % 95)); // 純 ASCII，UTF-8 往返無損
        }
        Fake f = new Fake(Fake.Auth.REJECT_FIRST, Fake.Shell.BIG, 0x01000001, 4096);
        f.bigOutput = big;
        int p = f.start();
        String out = runClient(p, kp.getPrivate(), kp.getPublic(), "echo big", 10000, 5000);
        f.awaitDone(8000);
        check(f.failure == null, "big-fake-clean", f.failure == null ? "" : f.failure);
        byte[] gotRaw = out.getBytes(StandardCharsets.UTF_8);
        check(Arrays.equals(gotRaw, big), "big-concat",
                "got " + gotRaw.length + " bytes, want " + big.length);
    }

    static void testTruncated() throws Exception {
        KeyPair kp = freshKey();
        Fake f = new Fake(Fake.Auth.REJECT_FIRST, Fake.Shell.TRUNCATED, 0x01000000, 4096);
        int p = f.start();
        long t0 = System.currentTimeMillis();
        boolean threw = false;
        try {
            runClient(p, kp.getPrivate(), kp.getPublic(), "echo x", 10000, 5000);
        } catch (AdbClient.AdbException e) {
            threw = true;
        }
        long dt = System.currentTimeMillis() - t0;
        f.awaitDone(8000);
        check(threw, "truncated-throws", "client did not report truncated packet");
        check(dt < 15000, "truncated-time", "took " + dt + " ms");
    }

    static void expectKind(String name, Fake.Auth auth, String wantKind,
            boolean kindOr, long authMs) throws Exception {
        KeyPair kp = freshKey();
        Fake f = new Fake(auth, Fake.Shell.NONE, 0x01000000, 4096);
        int p = f.start();
        long t0 = System.currentTimeMillis();
        String kind = null;
        try {
            runClient(p, kp.getPrivate(), kp.getPublic(), "echo ok", authMs, 1000);
        } catch (AdbClient.AdbException e) {
            kind = e.kind;
        }
        long dt = System.currentTimeMillis() - t0;
        f.awaitDone(8000);
        boolean kindOk = wantKind.equals(kind) || (kindOr && AdbClient.IO.equals(kind));
        check(kindOk, name + "-kind", "got kind " + kind + ", want " + wantKind
                + (kindOr ? " (or io)" : ""));
        check(dt < 15000, name + "-time", "took " + dt + " ms");
    }

    static void testErrors() throws Exception {
        // 連不上
        long t0 = System.currentTimeMillis();
        String kind = null;
        try {
            KeyPair kp = freshKey();
            runClient(freePort(), kp.getPrivate(), kp.getPublic(), "echo ok", 5000, 2000);
        } catch (AdbClient.AdbException e) {
            kind = e.kind;
        }
        long dt = System.currentTimeMillis() - t0;
        check(AdbClient.CONNECT.equals(kind), "err-connect-kind", "got " + kind);
        check(dt < 15000, "err-connect-time", "took " + dt + " ms");

        // 授權逾時（短逾時）：伺服器送完 CNXN 就沉默
        t0 = System.currentTimeMillis();
        KeyPair kp2 = freshKey();
        Fake fs = new Fake(Fake.Auth.SILENT, Fake.Shell.NONE, 0x01000000, 4096);
        int ps = fs.start();
        kind = null;
        try {
            runClient(ps, kp2.getPrivate(), kp2.getPublic(), "echo ok", 1500, 500);
        } catch (AdbClient.AdbException e) {
            kind = e.kind;
        }
        dt = System.currentTimeMillis() - t0;
        fs.awaitDone(12000);
        check(AdbClient.AUTH_TIMEOUT.equals(kind), "err-authtimeout-kind", "got " + kind);
        check(dt >= 1200 && dt < 15000, "err-authtimeout-time", "took " + dt + " ms");

        expectKind("err-disconnect", Fake.Auth.CLOSE_AFTER_TOKEN, AdbClient.AUTH_REJECTED, true,
                8000);
        expectKind("err-stls", Fake.Auth.STLS, AdbClient.TLS_REQUIRED, false, 8000);
        expectKind("err-badmagic", Fake.Auth.BAD_MAGIC, AdbClient.PROTOCOL, false, 8000);
        expectKind("err-badcksum", Fake.Auth.BAD_CHECKSUM, AdbClient.PROTOCOL, false, 8000);
        expectKind("err-garbage", Fake.Auth.GARBAGE_CMD, AdbClient.PROTOCOL, false, 8000);
    }

    public static void main(String[] args) {
        try {
            String td = args.length > 0 ? args[0] : "testdata";
            testVectors(td);
            testOpaquePrivateKey();
            testConscryptBrokenObjectStillLoadsFromFile(td);
            testGenerateThenLoadSameKey();
            testAuthRoundTrip();
            testBigOutput();
            testTruncated();
            testErrors();
            check("keep".equals(Whitelist.wlStatus(" keep\n")), "wl-keep", "keep");
            check("set".equals(Whitelist.wlStatus("set")), "wl-set", "set");
            check("fail".equals(Whitelist.wlStatus("fail")), "wl-fail", "fail");
            check("unexpected".equals(Whitelist.wlStatus("")), "wl-empty", "empty");
            check("unexpected".equals(Whitelist.wlStatus("亂碼")), "wl-garbage", "garbage");
            check("keep".equals(Whitelist.wlStatus("Failed to write while dumping service activity: Broken pipe\nkeep\n")), "wl-broken-pipe", "keep after error");
        } catch (Exception e) {
            System.out.println("FAIL harness :: " + e);
            e.printStackTrace(System.out);
            failures++;
        }
        if (failures == 0) {
            System.out.println("ALL PASS");
        } else {
            System.out.println(failures + " FAILURES");
            System.exit(1);
        }
    }
}
