package com.example.tvagent;

import java.util.*;

final class Quota {
    static final String YT = "com.google.android.youtube.tv", NF = "com.netflix.ninja";
    static boolean ctl(String pkg) { return YT.equals(pkg) || NF.equals(pkg); }
    long balance, lastSeq = -1, usedTotal, usedToday, usedTodayYt, usedTodayNf, qWall, qEl, bootCount = -1, cap = 3600, adultEnd;
    String dbId, today = "", fgPkg = "";
    boolean ytFg, scrOn, warned, enforce, capped;
    static final class Ev {
        final long wallMs; final int type; final String pkg;
        Ev(long wallMs, int type, String pkg) { this.wallMs = wallMs; this.type = type; this.pkg = pkg; }
    }
    static final class Usage {
        long seconds, ytS, nfS; boolean ytFg, scrOn; String fgPkg;
        Usage(long seconds, boolean ytFg, boolean scrOn) { this(seconds, 0, 0, ytFg, "", scrOn); }
        Usage(long seconds, long ytS, long nfS, boolean ytFg, String fgPkg, boolean scrOn) {
            this.seconds = seconds; this.ytS = ytS; this.nfS = nfS; this.ytFg = ytFg;
            this.fgPkg = fgPkg == null ? "" : fgPkg; this.scrOn = scrOn;
        }
    }
    static final class Grant {
        final long seq, seconds; final String kind;
        Grant(long seq, String kind, long seconds) { this.seq = seq; this.kind = kind; this.seconds = seconds; }
    }
    static Quota load(Properties p) {
        try {
            Quota q = new Quota();
            q.balance = number(p, "balance_s"); q.usedTotal = number(p, "used_total_s"); q.usedToday = number(p, "used_today_s");
            q.qWall = number(p, "q_wall"); q.qEl = number(p, "q_el"); q.bootCount = Long.parseLong(required(p, "boot_count"));
            q.cap = number(p, "cap_s"); String seq = required(p, "last_seq"); q.lastSeq = seq.isEmpty() ? -1 : Long.parseLong(seq);
            q.dbId = required(p, "db_id"); if (q.dbId.isEmpty()) q.dbId = null;
            q.today = required(p, "today"); q.ytFg = bool(p, "yt_fg"); q.scrOn = bool(p, "scr_on");
            q.usedTodayYt = optNumber(p, "used_today_yt_s"); q.usedTodayNf = optNumber(p, "used_today_nf_s");
            String fp = p.getProperty("fg_pkg"); q.fgPkg = (YT.equals(fp) || NF.equals(fp)) ? fp : "";
            q.warned = bool(p, "warned"); q.enforce = bool(p, "enforce"); q.capped = bool(p, "capped");
            q.adultEnd = optNumber(p, "adult_end_el"); // WO-A2：爸媽時段結束點（elapsedRealtime 基準 ms），舊 state 沒有→0
            if (q.lastSeq < -1 || q.bootCount < -1 || q.balance > 86400) throw new IllegalArgumentException();
            return q;
        } catch (RuntimeException e) { return new Quota(); }
    }
    private static String required(Properties p, String k) { String v = p.getProperty(k); if (v == null) throw new IllegalArgumentException(); return v; }
    private static long number(Properties p, String k) { long n = Long.parseLong(required(p, k)); if (n < 0) throw new IllegalArgumentException(); return n; }
    private static boolean bool(Properties p, String k) { String v = required(p, k); if (!v.equals("true") && !v.equals("false")) throw new IllegalArgumentException(); return Boolean.parseBoolean(v); }
    // 新鍵寬鬆讀取（缺少或格式錯→預設值，絕不丟例外；WO-U1 三鍵＋WO-A2 adult_end_el）
    private static long optNumber(Properties p, String k) {
        try { String v = p.getProperty(k); if (v == null) return 0; long n = Long.parseLong(v); return n < 0 ? 0 : n; }
        catch (RuntimeException e) { return 0; }
    }
    Properties save() {
        Properties p = new Properties();
        p.setProperty("balance_s", "" + balance); p.setProperty("last_seq", lastSeq < 0 ? "" : "" + lastSeq);
        p.setProperty("db_id", dbId == null ? "" : dbId); p.setProperty("used_total_s", "" + usedTotal); p.setProperty("today", today);
        p.setProperty("used_today_s", "" + usedToday); p.setProperty("used_today_yt_s", "" + usedTodayYt); p.setProperty("used_today_nf_s", "" + usedTodayNf);
        p.setProperty("fg_pkg", fgPkg == null ? "" : fgPkg); p.setProperty("q_wall", "" + qWall); p.setProperty("q_el", "" + qEl);
        p.setProperty("boot_count", "" + bootCount); p.setProperty("yt_fg", "" + ytFg); p.setProperty("scr_on", "" + scrOn);
        p.setProperty("warned", "" + warned); p.setProperty("enforce", "" + enforce); p.setProperty("cap_s", "" + cap); p.setProperty("capped", "" + capped);
        p.setProperty("adult_end_el", "" + adultEnd);
        return p;
    }
    static Usage usage(List<Ev> events, long from, long to, boolean fg, boolean screen) {
        return usage(events, from, to, fg, "", screen);
    }
    static Usage usage(List<Ev> events, long from, long to, boolean fg, String fgPkg, boolean screen) {
        return usage(events, from, to, fg, fgPkg, screen, 0);
    }
    // WO-A2：freeToWall（牆鐘 ms）之前的每段前景區間不計（爸媽時段不扣額）；舊簽名＝0，行為不變
    static Usage usage(List<Ev> events, long from, long to, boolean fg, String fgPkg, boolean screen, long freeToWall) {
        List<Ev> sorted = new ArrayList<>(events); Collections.sort(sorted, new Comparator<Ev>() { public int compare(Ev a, Ev b) { return Long.compare(a.wallMs, b.wallMs); } });
        long cursor = from, total = 0, yt = 0, nf = 0;
        String pkg = fgPkg == null ? "" : fgPkg;
        for (Ev e : sorted) {
            if (e.wallMs < from || e.wallMs > to) continue;
            if (fg && screen) { long d = e.wallMs - Math.max(cursor, freeToWall); if (d > 0) { total += d; if (YT.equals(pkg)) yt += d; else if (NF.equals(pkg)) nf += d; } }
            cursor = e.wallMs;
            switch (e.type) {
                case 1: if (ctl(e.pkg)) { fg = true; pkg = e.pkg; } else { fg = false; pkg = ""; } break;
                case 2: if (ctl(e.pkg)) { fg = false; pkg = ""; } break; // STOPPED(23) 會晚於新 App 的 RESUMED，不處理
                case 15: screen = true; break;
                case 16: screen = false; break;
                case 26: fg = false; screen = false; pkg = ""; break;
                case 27: fg = false; screen = true; pkg = ""; break;
            }
        }
        if (fg && screen && to > cursor) { long d = to - Math.max(cursor, freeToWall); if (d > 0) { total += d; if (YT.equals(pkg)) yt += d; else if (NF.equals(pkg)) nf += d; } }
        return new Usage(total / 1000, yt / 1000, nf / 1000, fg, pkg, screen);
    }
    static boolean clockJump(long oldWall, long wall, long oldEl, long el, long oldBoot, long boot) {
        return oldBoot >= 0 && oldBoot == boot && Math.abs((wall - oldWall) - (el - oldEl)) > 60000;
    }
    void consume(long seconds, String date) {
        if (!today.equals(date)) { today = date; usedToday = 0; usedTodayYt = 0; usedTodayNf = 0; }
        long n = Math.min(balance, Math.max(0, seconds)); balance -= n; usedTotal += n; usedToday += n;
    }
    void applySync(List<Grant> grants, long maxSeq, String id, long capS, boolean enforceValue) {
        cap = capS; enforce = enforceValue; capped = false;
        List<Grant> sorted = new ArrayList<>(grants); Collections.sort(sorted, new Comparator<Grant>() { public int compare(Grant a, Grant b) { return Long.compare(a.seq, b.seq); } });
        boolean changedDb = !Objects.equals(dbId, id);
        for (Grant g : sorted) {
            if (!changedDb && g.seq <= lastSeq) continue;
            if (g.kind.equals("set")) balance = g.seconds;
            else if (g.kind.equals("add")) { long sum = balance + g.seconds; if (sum > cap) capped = true; balance = Math.max(balance, Math.min(sum, cap)); }
            lastSeq = g.seq;
        }
        if (changedDb) { lastSeq = maxSeq; dbId = id; }
    }
    enum Action { NONE, LOCK, UNLOCK, WARN, END }
    Action decide(String yt, boolean fg, boolean screen) { return decide(yt, false, fg, screen); }
    // mixed：兩個受控套件只停用一個（yt 此時回報 enabled）
    Action decide(String yt, boolean mixed, boolean fg, boolean screen) { return decide(yt, mixed, fg, screen, 0); }
    Action decide(String yt, boolean fg, boolean screen, long adultLeft) { return decide(yt, false, fg, screen, adultLeft); }
    // WO-A2：有效額度＝balance＋adultLeft（爸媽時段剩餘秒）；舊簽名＝adultLeft 0，行為不變
    Action decide(String yt, boolean mixed, boolean fg, boolean screen, long adultLeft) {
        long eff = balance + Math.max(0, adultLeft);
        if (eff > 300) warned = false;
        if (yt.equals("unknown")) return Action.NONE;
        if (eff > 0 && (yt.equals("disabled") || mixed)) return Action.UNLOCK;
        if (eff == 0 && (yt.equals("enabled") || mixed)) return fg && screen ? Action.END : Action.LOCK;
        if (eff > 0 && eff <= 300 && fg && screen && !warned) { warned = true; return Action.WARN; }
        return Action.NONE;
    }
    // WO-A2：爸媽時段剩餘秒（elapsedRealtime 基準）；失效時呼叫端先清 adultEnd（見 adultInvalid）
    long adultLeft(long el) { return Math.max(0, (adultEnd - el) / 1000); }
    // fix1：adultEnd＝0（不在時段或剛失效）時回 0，不在時段整段照扣；>0 時把 el 基準的結束點換成牆鐘
    static long freeToWall(long wall, long el, long adultEnd) { return adultEnd > 0 ? wall - (el - adultEnd) : 0; }
    // WO-A2：開機（boot 不同或 boot<0）或時鐘倒退（el<存檔 qEl）→本機時段失效，等下一次 sync
    static boolean adultInvalid(long boot, long savedBoot, long el, long savedQEl) {
        return boot < 0 || boot != savedBoot || el < savedQEl;
    }
    // WO-A3：這一跳會停用（END／LOCK）時先 sync 再 act；decide() 本身不改
    static boolean wouldStop(String yt, boolean mixed, long eff) {
        return !"unknown".equals(yt) && eff == 0 && ("enabled".equals(yt) || mixed);
    }
    long nextAlarmMs(boolean fgAndScreen) { return nextAlarmMs(fgAndScreen, 0); }
    long nextAlarmMs(boolean fgAndScreen, long adultLeft) {
        long eff = balance + Math.max(0, adultLeft);
        if (!fgAndScreen || eff == 0) return 60000; // 0 額度：END／LOCK 這一跳已試過（影子模式只 dry-end），不必每 5 秒重試
        if (eff < 60) return Math.max(eff, 5) * 1000;
        if (eff > 300) return Math.max(5, Math.min(60, eff - 300)) * 1000;
        return 60000;
    }
    // WO-A3 改法 1：locked＝s.yt 非 unknown（adb 正常、act 後已確認 enabled／disabled）；
    // eff=0＋螢幕亮＋locked→10 秒等加值（unknown 維持 60 秒不加速重試）；其他照舊公式
    long nextAlarmMs(boolean fgAndScreen, long adultLeft, boolean screenOn, boolean locked) {
        long eff = balance + Math.max(0, adultLeft);
        if (eff == 0 && screenOn && locked) return 10000;
        return nextAlarmMs(fgAndScreen, adultLeft);
    }
}
