package com.example.tvagent;

import android.app.*;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import org.json.*;
import java.io.*;
import java.net.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.HttpsURLConnection;

public class AgentService extends Service {
    static final String EXTRA_REASON = "reason", HEARTBEAT_ACTION = "com.example.tvagent.HEARTBEAT";
    static final String DISABLE = "pm disable-user --user 0 " + Quota.YT + "; pm disable-user --user 0 " + Quota.NF;
    static final String ENABLE = "pm enable --user 0 " + Quota.YT + "; pm enable --user 0 " + Quota.NF;
    static final String LIST = "pm list packages -d";
    static final String WHITELIST = "com.android.bluetooth,com.tcl.tvinput,com.tcl.autopair,com.google.android.katniss,com.tcl.ttvs,com.tcl.versionUpdateApp,com.tcl.usagestats,com.tcl.MultiScreenInteraction_TV,com.example.tvagent";
    static final String CMD_WHITELIST = "S=com.tcl.guard/.memorymanager.TGuardService; "
            + "dumpsys activity service $S 2>/dev/null | grep -q 'KillWhiteList:.*com[.]example[.]tvagent$' && echo keep || { "
            + "dumpsys activity service $S mKillWhiteList '" + WHITELIST + "' >/dev/null 2>/dev/null; "
            + "dumpsys activity service $S 2>/dev/null | grep -q 'KillWhiteList:.*com[.]example[.]tvagent$' && echo set || echo fail; }";
    private ExecutorService exec;
    private File keyFile, stateFile, logFile;
    private boolean configured;

    @Override public void onCreate() {
        super.onCreate(); keyFile = new File(getFilesDir(), "adbkey"); stateFile = new File(getFilesDir(), "state.properties"); logFile = new File(getFilesDir(), "agent.log");
        exec = Executors.newSingleThreadExecutor(); startAsForeground();
    }
    static void scheduleNextAlarm(Context context, long delay) {
        AlarmManager am = (AlarmManager) context.getSystemService(ALARM_SERVICE);
        if (am != null) am.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delay,
                PendingIntent.getBroadcast(context, 1, new Intent(context, AlarmReceiver.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
    private void startAsForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm != null) nm.createNotificationChannel(new NotificationChannel("tvagent", "看電視時間", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, new Notification.Builder(this, "tvagent").setContentTitle("看電視時間").setContentText("額度服務執行中").setSmallIcon(android.R.drawable.stat_notify_sync).build());
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startAsForeground(); // 每次 startForegroundService 都必須回應 startForeground，否則 5 秒規則會當掉
        String reason = intent != null ? intent.getStringExtra(EXTRA_REASON) : null;
        if (!"boot".equals(reason) && !"kick".equals(reason)) reason = "alarm";
        final String src = reason;
        scheduleNextAlarm(this, 60000);
        exec.execute(new Runnable() { public void run() { heartbeat(src); } });
        return START_STICKY;
    }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() { exec.shutdown(); super.onDestroy(); }
    private String adb(String command) throws AdbClient.AdbException {
        try { AdbClient c = AdbClient.withKeyFile("127.0.0.1", 5555, keyFile); c.setAuthTimeoutMs(60000); c.setIoTimeoutMs(10000); return c.shell(command); }
        catch (AdbClient.AdbException e) { throw e; }
        catch (Exception e) { throw new AdbClient.AdbException(AdbClient.IO, "key error"); }
    }
    private Quota load() {
        try (FileInputStream in = new FileInputStream(stateFile)) { Properties p = new Properties(); p.load(in); return Quota.load(p); }
        catch (Exception e) { return new Quota(); }
    }
    private void save(Quota q) throws IOException {
        File tmp = new File(getFilesDir(), "state.properties.tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) { q.save().store(out, null); out.getFD().sync(); }
        if (!tmp.renameTo(stateFile)) throw new IOException("rename");
    }
    private static final class Status { String yt = "unknown", screen = "unknown", adb = "ok", wl = "skip", fgPkg; Boolean fg; boolean mixed; }
    private Status status() {
        Status s = new Status();
        try {
            if (!configured) {
                String setup = adb("for c in 'cmd appops set com.example.tvagent APP_AUTO_START allow' 'cmd appops set com.example.tvagent APP_ASSOC_START allow' 'cmd appops set com.example.tvagent GET_USAGE_STATS allow' 'am set-standby-bucket com.example.tvagent active' 'dumpsys deviceidle whitelist +com.example.tvagent'; do sh -c \"$c\" >/dev/null 2>&1 && printf 1 || printf 0; done; echo");
                configured = setup.trim().equals("11111");
                // Setup result is included in the heartbeat log without command output.
                setupResult = setup.trim().matches("[01]{5}") ? setup.trim() : "failed";
            }
            String out = adb("echo __POWER__; dumpsys power || echo __FAIL__; echo __DISABLED__; pm list packages -d || echo __FAIL__; echo __ACTIVITY__; if [ \"$(pm list packages -d | grep -cx -e package:com.google.android.youtube.tv -e package:com.netflix.ninja)\" = 2 ]; then echo __SKIP__; else dumpsys activity activities || echo __FAIL__; fi; echo __END__");
            int p = out.indexOf("__POWER__"), d = out.indexOf("__DISABLED__"), a = out.indexOf("__ACTIVITY__"), e = out.indexOf("__END__");
            if (out.contains("__FAIL__") || p < 0 || d <= p || a <= d || e <= a) throw new AdbClient.AdbException(AdbClient.IO, "status");
            String power = out.substring(p, d), disabled = out.substring(d, a), activity = out.substring(a, e);
            if (!power.contains("mWakefulness=")) throw new AdbClient.AdbException(AdbClient.IO, "power");
            s.screen = power.contains("mWakefulness=Awake") ? "on" : "off";
            List<String> off = Arrays.asList(disabled.split("\\r?\\n"));
            boolean ytOff = off.contains("package:" + Quota.YT), nfOff = off.contains("package:" + Quota.NF);
            s.yt = ytOff && nfOff ? "disabled" : "enabled"; s.mixed = ytOff != nfOff;
            if (!(ytOff && nfOff)) { if (!activity.contains("mResumedActivity")) throw new AdbClient.AdbException(AdbClient.IO, "activity"); s.fg = false; s.fgPkg = null; for (String line : activity.split("\\r?\\n")) if (line.contains("mResumedActivity") && (line.contains(Quota.YT) || line.contains(Quota.NF))) { s.fg = true; s.fgPkg = line.contains(Quota.YT) ? Quota.YT : Quota.NF; } }
            s.wl = Whitelist.wlStatus(adb(CMD_WHITELIST));
        } catch (AdbClient.AdbException ex) { s.adb = ex.kind; s.yt = "unknown"; s.screen = "unknown"; s.fg = null; s.mixed = false; }
        return s;
    }
    private String setupResult = "pending";
    private String act(Quota q, Status s) {
        Quota.Action action = q.decide(s.yt, s.mixed, Boolean.TRUE.equals(s.fg), s.screen.equals("on"), q.adultLeft(SystemClock.elapsedRealtime()));
        String name = action.name().toLowerCase(Locale.US);
        if (action == Quota.Action.NONE) return name;
        if (!q.enforce) return "dry-" + name;
        try {
            if (action == Quota.Action.WARN || action == Quota.Action.END) adb("am start -n com.example.tvagent/.NoticeActivity --es kind " + (action == Quota.Action.WARN ? "warn" : "end"));
            if (action == Quota.Action.END) Thread.sleep(10000);
            if (action == Quota.Action.END || action == Quota.Action.LOCK) {
                adb("am force-stop " + Quota.YT + "; am force-stop " + Quota.NF + "; " + DISABLE);
                List<String> off = Arrays.asList(adb(LIST).split("\\r?\\n"));
                if (!off.contains("package:" + Quota.YT) || !off.contains("package:" + Quota.NF)) throw new AdbClient.AdbException(AdbClient.IO, "verify");
                s.yt = "disabled"; s.fg = null; s.mixed = false; q.ytFg = false; q.fgPkg = "";
            } else if (action == Quota.Action.UNLOCK) {
                adb(ENABLE);
                List<String> off = Arrays.asList(adb(LIST).split("\\r?\\n"));
                if (off.contains("package:" + Quota.YT) || off.contains("package:" + Quota.NF)) throw new AdbClient.AdbException(AdbClient.IO, "verify");
                s.yt = "enabled"; if (!s.mixed) s.fg = false; s.mixed = false; // mixed 時保持探測到的前景值
            }
        } catch (AdbClient.AdbException ex) { s.adb = ex.kind; s.yt = "unknown"; s.fg = null; s.mixed = false; }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); s.adb = "io"; s.yt = "unknown"; s.fg = null; s.mixed = false; }
        return name;
    }
    private void heartbeat(String src) {
        Quota q = load(); long wall = System.currentTimeMillis(), el = SystemClock.elapsedRealtime();
        long boot = Settings.Global.getInt(getContentResolver(), Settings.Global.BOOT_COUNT, -1);
        if (Quota.adultInvalid(boot, q.bootCount, el, q.qEl)) q.adultEnd = 0; // WO-A2：開機／時鐘異常時本機時段失效，等下一次 sync
        long freeToWall = Quota.freeToWall(wall, el, q.adultEnd); // WO-A2：adultEnd 換成牆鐘；之前的前景不扣額（adultEnd＝0→0，全段照算）
        long gap = q.qEl > 0 && q.bootCount == boot ? Math.max(0, (el - q.qEl) / 1000) : 0;
        boolean jump = q.qWall > 0 && Quota.clockJump(q.qWall, wall, q.qEl, el, q.bootCount, boot);
        Quota.Usage usage = new Quota.Usage(0, 0, 0, q.ytFg, q.fgPkg, q.scrOn);
        if (q.qWall > 0 && wall >= q.qWall) {
            UsageStatsManager manager = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
            List<Quota.Ev> events = new ArrayList<>(); UsageEvents results = manager.queryEvents(q.qWall, wall); UsageEvents.Event event = new UsageEvents.Event();
            while (results.hasNextEvent()) { results.getNextEvent(event); events.add(new Quota.Ev(event.getTimeStamp(), event.getEventType(), event.getPackageName())); }
            usage = Quota.usage(events, q.qWall, wall, q.ytFg, q.fgPkg, q.scrOn, freeToWall);
        }
        Status s = status();
        long seconds = jump ? (Boolean.TRUE.equals(s.fg) && s.screen.equals("on") ? Math.max(0, (el - Math.max(q.qEl, q.adultEnd)) / 1000) : 0) : usage.seconds;
        long ytS = usage.ytS, nfS = usage.nfS;
        if (jump) { ytS = 0; nfS = 0; if (seconds > 0) { if (Quota.YT.equals(s.fgPkg)) ytS = seconds; else if (Quota.NF.equals(s.fgPkg)) nfS = seconds; } }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US); format.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
        String today = format.format(new Date(wall));
        q.consume(seconds, today); q.usedTodayYt += ytS; q.usedTodayNf += nfS;
        q.qWall = wall; q.qEl = el; q.bootCount = boot; q.ytFg = usage.ytFg; q.fgPkg = usage.fgPkg; q.scrOn = usage.scrOn;
        if (!s.yt.equals("unknown")) { q.ytFg = Boolean.TRUE.equals(s.fg); q.fgPkg = s.fgPkg == null ? "" : s.fgPkg; q.scrOn = s.screen.equals("on"); }
        // WO-A3 改法 2：這一跳會停用（END／LOCK）→先 sync 再 act；sync 獨立 try/catch，失敗照本機狀態停用
        boolean pre = Quota.wouldStop(s.yt, s.mixed, q.balance + q.adultLeft(SystemClock.elapsedRealtime()));
        String action, sync;
        if (pre) {
            try { save(q); } catch (Exception ignored) { }
            try { sync = sync(q, s, gap, boot, el, today); }
            catch (Exception ex) { sync = "io"; }
            if (sync.equals("ok")) {
                try { save(q); } catch (Exception ignored) { } // 新 balance／last_seq／adultEnd 先落盤再 act
            }
            action = act(q, s); // sync 帶回加值（eff>0）→不鎖；失敗→照本機狀態鎖
            try { save(q); } catch (Exception ignored) { } // act 後狀態落盤
        } else {
            action = act(q, s); sync = "skip";
            try {
                save(q);
                long balBefore = q.balance;
                boolean enforceBefore = q.enforce;
                long effBefore = q.balance + q.adultLeft(el);
                sync = sync(q, s, gap, boot, el, today);
                if (sync.equals("ok")) {
                    long elAfter = SystemClock.elapsedRealtime();
                    long effAfter = q.balance + q.adultLeft(elAfter);
                    if (q.balance != balBefore || q.enforce != enforceBefore
                            || (effBefore > 0) != (effAfter > 0) || (effBefore <= 300) != (effAfter <= 300)) action += "," + act(q, s);
                    save(q);
                }
            }
            catch (Exception ex) { sync = "io"; }
        }
        long adultSec = q.adultLeft(SystemClock.elapsedRealtime());
        scheduleNextAlarm(this, q.nextAlarmMs(Boolean.TRUE.equals(s.fg) && s.screen.equals("on"), adultSec, s.screen.equals("on"), !"unknown".equals(s.yt)));
        log("uptime=" + el / 1000 + " pid=" + android.os.Process.myPid() + " src=" + src + " bal=" + q.balance + " adult=" + adultSec + " used_today=" + q.usedToday + " yt_s=" + q.usedTodayYt + " nf_s=" + q.usedTodayNf + " fg=" + s.fg + " yt=" + s.yt + " screen=" + s.screen + " act=" + action + " sync=" + sync + " adb=" + s.adb + " wl=" + s.wl + " setup=" + setupResult + (gap > 300 ? " gap=" + gap : "") + (jump ? " clock=jump" : ""));
    }
    private String sync(Quota q, Status s, long gap, long boot, long el, String today) throws Exception {
        Properties config = new Properties();
        try (FileInputStream in = new FileInputStream(new File(getFilesDir(), "config.properties"))) { config.load(in); }
        catch (FileNotFoundException e) { return "skip"; }
        String url = config.getProperty("url"), id = config.getProperty("client_id"), secret = config.getProperty("client_secret");
        if (url == null || id == null || secret == null || id.isEmpty() || secret.isEmpty()) return "config";
        JSONObject request = new JSONObject();
        request.put("v", 1); request.put("last_seq", q.lastSeq < 0 ? JSONObject.NULL : q.lastSeq); request.put("db_id", q.dbId == null ? JSONObject.NULL : q.dbId);
        request.put("balance_s", q.balance); request.put("capped", q.capped); request.put("used_today_s", q.usedToday); request.put("used_total_s", q.usedTotal);
        request.put("today", today); request.put("used_today_yt_s", q.usedTodayYt); request.put("used_today_nf_s", q.usedTodayNf);
        request.put("yt", s.yt); request.put("fg_yt", s.fg == null ? JSONObject.NULL : s.fg); request.put("screen", s.screen); request.put("adb", s.adb); request.put("enforce", q.enforce);
        request.put("boot_count", boot); request.put("uptime_s", el / 1000); request.put("gap_s", gap); request.put("wl", s.wl); request.put("app_ver", 5);
        HttpsURLConnection c = (HttpsURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestMethod("POST"); c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json"); c.setRequestProperty("CF-Access-Client-Id", id); c.setRequestProperty("CF-Access-Client-Secret", secret);
            try (OutputStream out = c.getOutputStream()) { out.write(request.toString().getBytes("UTF-8")); }
            int code = c.getResponseCode(); if (code != 200) return "http:" + code;
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(); try (InputStream in = c.getInputStream()) { byte[] buf = new byte[4096]; int n; while ((n = in.read(buf)) != -1) { bytes.write(buf, 0, n); if (bytes.size() > 1048576) throw new IOException("response too large"); } }
            JSONObject data = new JSONObject(bytes.toString("UTF-8")); JSONArray array = data.getJSONArray("grants"); List<Quota.Grant> grants = new ArrayList<>();
            long max = data.getLong("max_seq"), cap = data.getLong("cap_s"); String db = data.getString("db_id"); boolean enforce = data.getBoolean("enforce");
            if (max < 0 || cap < 0 || db.isEmpty()) throw new IOException("response");
            for (int i = 0; i < array.length(); i++) { JSONObject g = array.getJSONObject(i); long seq = g.getLong("seq"), seconds = g.getLong("seconds"); String kind = g.getString("kind"); if (seq < 0 || seq > max || seconds < 0 || seconds > 86400 || (!kind.equals("add") && !kind.equals("set"))) throw new IOException("grant"); grants.add(new Quota.Grant(seq, kind, seconds)); }
            q.applySync(grants, max, db, cap, enforce);
            long adultLeftS;
            try { adultLeftS = data.optLong("adult_left_s", 0); } catch (RuntimeException ex) { adultLeftS = 0; }
            if (adultLeftS < 0 || adultLeftS > 86400) adultLeftS = 0; // 舊 Worker 沒這欄＝0；錯值不讓整次 sync 失敗
            long el2 = SystemClock.elapsedRealtime(); // 解析完回應後重取（WO-A2 裁定 #11）
            q.adultEnd = adultLeftS > 0 ? el2 + Math.min(adultLeftS, 600) * 1000 : 0; // 每次成功 sync 都覆寫；0→清零（fix1：否則下一跳排除掉 status＋act＋sync 耗時對應的前景）；>0 最多續 10 分鐘
            return "ok";
        } finally { c.disconnect(); }
    }
    private void log(String line) {
        try {
            SimpleDateFormat ts = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            ts.setTimeZone(TimeZone.getTimeZone("Asia/Taipei"));
            line = ts.format(new Date()) + " " + line;
            if (logFile.length() > 1048576) {
                try (RandomAccessFile file = new RandomAccessFile(logFile, "rw")) { long start = file.length() - 524288; file.seek(start); file.readLine(); start = file.getFilePointer(); byte[] tail = new byte[(int)(file.length() - start)]; file.readFully(tail); file.seek(0); file.write(tail); file.setLength(tail.length); }
            }
            try (FileWriter out = new FileWriter(logFile, true)) { out.write(line + "\n"); }
        } catch (IOException ignored) { }
    }
}
