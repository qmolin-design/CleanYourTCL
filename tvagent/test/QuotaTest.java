package com.example.tvagent;
import java.util.*;
public class QuotaTest {
    static void check(boolean b, String name) { if (!b) throw new AssertionError(name); System.out.println("PASS " + name); }
    static Quota.Ev e(long sec, int type, String pkg) { return new Quota.Ev(sec * 1000, type, pkg); }
    static Quota.Usage usage(Quota.Ev... e) { return Quota.usage(Arrays.asList(e), 0, 100000, false, true); }
    static Quota.Grant g(long n, String kind, long sec) { return new Quota.Grant(n, kind, sec); }
    public static void main(String[] args) {
        String y = Quota.YT;
        Quota.Usage u = usage(e(10,1,y), e(70,2,y)); check(u.seconds == 60 && !u.ytFg && u.scrOn, "usage 60 and end state");
        u = usage(e(10,1,y),e(30,16,null),e(50,15,null),e(70,2,y)); check(u.seconds == 40, "usage screen off");
        check(usage(e(10,1,y),e(30,1,"other")).seconds == 20, "usage other resumed");
        check(Quota.usage(Collections.emptyList(), 0, 100000, true, true).seconds == 100, "usage initial");
        u = usage(e(10,1,y),e(30,26,null),e(40,27,null)); check(u.seconds == 20 && !u.ytFg && u.scrOn, "usage shutdown startup");
        u = usage(e(-1,1,y),e(10,1,y),e(101,2,y)); check(u.seconds == 90 && u.ytFg && u.seconds <= 100, "usage bounds");
        String n = Quota.NF;
        u = usage(e(10,1,n), e(70,2,n)); check(u.seconds == 60 && !u.ytFg, "netflix foreground consumes quota");
        u = usage(e(10,1,y), e(40,2,y), e(40,1,n), e(70,23,y), e(100,2,n)); check(u.seconds == 90 && !u.ytFg, "youtube to netflix switch uninterrupted");
        u = Quota.usage(Arrays.asList(e(40,2,y), e(40,1,n)), 0, 50000, true, true); check(u.ytFg && u.seconds == 50, "youtube to netflix batch 1");
        u = Quota.usage(Arrays.asList(e(60,23,y)), 50000, 100000, u.ytFg, u.scrOn); check(u.ytFg && u.seconds == 50, "netflix fg survives later youtube STOPPED");
        u = usage(e(10,1,n), e(40,2,n), e(40,1,"launcher"), e(100,23,n)); check(u.seconds == 30 && !u.ytFg, "netflix to launcher stops");
        u = Quota.usage(Arrays.asList(e(10,23,y)), 0, 100000, true, true); check(u.ytFg && u.seconds == 100, "STOPPED does not clear fg");
        check(Quota.clockJump(1000,3602000,1000,2000,1,1), "clock jump");
        check(!Quota.clockJump(1000,32000,1000,2000,1,1) && !Quota.clockJump(1000,3602000,1000,2000,1,2), "clock tolerance boot");
        Quota q = new Quota(); q.balance = 30; q.consume(60,"2026-10-30"); check(q.balance == 0 && q.usedTotal == 30 && q.usedToday == 30, "consume limited");
        q.balance = 50; q.consume(10,"2026-10-31"); check(q.usedToday == 10 && q.usedTotal == 40, "consume new date");
        q = new Quota(); q.dbId = "db"; q.lastSeq = 3;
        List<Quota.Grant> gs = Arrays.asList(g(6,"add",900),g(3,"add",300),g(5,"set",0),g(4,"add",1800));
        q.applySync(gs,6,"db",3600,true); check(q.balance == 900 && q.lastSeq == 6 && q.enforce, "sync sorted and dedup");
        q.applySync(gs,6,"db",3600,true); q.applySync(Collections.emptyList(),20,"db",3600,true); check(q.balance == 900 && q.lastSeq == 6, "sync repeated empty");
        q.applySync(Arrays.asList(g(1,"add",100)),8,"new",3600,false); check(q.balance == 1000 && q.lastSeq == 8 && q.dbId.equals("new") && !q.enforce, "sync reset");
        Quota fresh = new Quota(); fresh.applySync(Arrays.asList(g(1,"add",100)),4,"db",3600,false); check(fresh.balance == 100 && fresh.lastSeq == 4, "sync initial");
        q.balance = 3500; q.applySync(Arrays.asList(g(9,"add",500)),9,"new",3600,true); check(q.balance == 3600 && q.capped, "sync capped");
        q.balance = 3000; q.applySync(Arrays.asList(g(10,"add",500)),10,"new",1800,false); check(q.balance == 3000 && q.cap == 1800 && q.capped && !q.enforce, "sync cap decrease");
        q = new Quota(); check(q.decide("unknown",true,true) == Quota.Action.NONE, "decide unknown");
        q.balance = 1; check(q.decide("disabled",false,true) == Quota.Action.UNLOCK, "decide unlock");
        q.balance = 0; check(q.decide("enabled",true,true) == Quota.Action.END && q.decide("enabled",false,true) == Quota.Action.LOCK, "decide end lock");
        q.balance = 200; check(q.decide("enabled",true,true) == Quota.Action.WARN && q.decide("enabled",true,true) == Quota.Action.NONE, "decide warn once");
        q.balance = 301; q.decide("enabled",true,true); q.balance = 200; check(q.decide("enabled",true,true) == Quota.Action.WARN && q.decide("enabled",false,true) == Quota.Action.NONE, "decide warn reset");
        q = new Quota(); q.balance = 1; check(q.decide("enabled",true,false,true) == Quota.Action.UNLOCK && q.decide("enabled",false,false,true) == Quota.Action.NONE, "decide mixed positive unlocks");
        q.balance = 0; check(q.decide("enabled",true,false,true) == Quota.Action.LOCK && q.decide("enabled",true,true,true) == Quota.Action.END, "decide mixed zero locks");
        check(q.decide("unknown",true,true,true) == Quota.Action.NONE, "decide mixed unknown");
        q.balance = 90; u = Quota.usage(Arrays.asList(e(0,1,y), e(60,2,y), e(60,1,n)), 0, 120000, false, true); q.consume(u.seconds,"2026-10-31");
        check(u.seconds == 120 && u.ytFg && q.balance == 0 && q.decide("enabled",u.ytFg,true) == Quota.Action.END, "youtube then netflix share quota to END");
        long[] balances = {20,2,0,330,302,3000}; long[] alarms = {20000,5000,60000,30000,5000,60000}; for (int i=0;i<balances.length;i++) { q.balance=balances[i]; check(q.nextAlarmMs(true)==alarms[i], "alarm " + balances[i]); } check(q.nextAlarmMs(false)==60000,"alarm background");
        Properties saved = q.save(); Quota copy = Quota.load(saved); check(copy.save().equals(saved), "save load");
        check(Quota.load(new Properties()).balance == 0 && Quota.load(new Properties()).lastSeq == -1 && !Quota.load(new Properties()).enforce, "load garbage");
        saved.setProperty("balance_s", "bad"); check(Quota.load(saved).lastSeq == -1 && !Quota.load(saved).enforce, "load bad number");
        q = new Quota(); q.dbId="db"; q.lastSeq=3; Properties before=q.save(); q.applySync(gs,6,"db",3600,false); q=Quota.load(before); q.applySync(gs,6,"db",3600,false); check(q.balance==900 && q.lastSeq==6, "retry after unsaved sync");
        check(Quota.usage(Arrays.asList(e(10,1,y), e(70,2,y)), 0, 100000, false, true).seconds == 60, "usage compat overload");
        Quota.Usage v = Quota.usage(Arrays.asList(e(10,1,y), e(70,2,y), e(70,1,n), e(100,2,n)), 0, 100000, false, "", true);
        check(v.seconds == 90 && v.ytS == 60 && v.nfS == 30, "usage per-app yt60 nf30");
        Quota.Usage v1 = Quota.usage(Arrays.asList(e(40,2,y), e(40,1,n)), 0, 50000, true, y, true);
        check(v1.seconds == 50 && v1.ytS == 40 && v1.nfS == 10 && v1.fgPkg.equals(n), "usage switch split with fgPkg");
        Quota.Usage v2 = Quota.usage(Arrays.asList(e(60,23,y)), 50000, 100000, v1.ytFg, v1.fgPkg, v1.scrOn);
        check(v2.seconds == 50 && v2.ytS == 0 && v2.nfS == 50, "usage switch chain keeps app");
        Quota.Usage v3 = Quota.usage(Collections.emptyList(), 0, 100000, true, "", true);
        check(v3.seconds == 100 && v3.ytS == 0 && v3.nfS == 0, "usage unknown pkg");
        Quota.Usage v3b = Quota.usage(Collections.emptyList(), 0, 100000, true, null, true);
        check(v3b.seconds == 100 && v3b.ytS == 0 && v3b.nfS == 0, "usage null pkg");
        Quota w = new Quota(); w.balance = 1000; w.today = "2026-10-30"; w.usedToday = 100; w.usedTodayYt = 60; w.usedTodayNf = 30;
        w.consume(10, "2026-10-30"); check(w.usedToday == 110 && w.usedTodayYt == 60 && w.usedTodayNf == 30, "consume same day keeps per-app");
        w.consume(10, "2026-10-31"); check(w.usedToday == 10 && w.usedTodayYt == 0 && w.usedTodayNf == 0, "consume rollover resets per-app");
        Quota o = new Quota(); o.balance = 500; o.lastSeq = 7; o.enforce = true; o.dbId = "db"; o.usedTodayYt = 12; o.usedTodayNf = 34; o.fgPkg = n;
        Properties op = o.save(); op.remove("used_today_yt_s"); op.remove("used_today_nf_s"); op.remove("fg_pkg");
        Quota oc = Quota.load(op); check(oc.balance == 500 && oc.lastSeq == 7 && oc.enforce && oc.usedTodayYt == 0 && oc.usedTodayNf == 0 && oc.fgPkg.equals(""), "load old state");
        Properties bp = o.save(); bp.setProperty("used_today_yt_s", "abc"); bp.setProperty("used_today_nf_s", "-5"); bp.setProperty("fg_pkg", "com.evil.app");
        Quota bc = Quota.load(bp); check(bc.balance == 500 && bc.lastSeq == 7 && bc.enforce && bc.usedTodayYt == 0 && bc.usedTodayNf == 0 && bc.fgPkg.equals(""), "load bad new keys");
        Quota ro = Quota.load(o.save()); check(ro.usedTodayYt == 12 && ro.usedTodayNf == 34 && ro.fgPkg.equals(n) && ro.save().equals(o.save()), "save load new keys");
        Quota.Usage r = Quota.usage(Collections.emptyList(), 0, 91000, false, "", true);
        check(r.seconds == 0 && r.nfS == 0 && r.ytS == 0 && !r.ytFg, "reboot events lost cleared fg counts zero");
        Quota.Usage b = Quota.usage(Collections.emptyList(), 0, 91000, true, n, true);
        check(b.seconds == 91 && b.nfS == 91 && b.ytFg, "stale fg netflix overcounts 91s");
        q = new Quota(); q.balance = 0; check(q.decide("disabled", false, true, 600) == Quota.Action.UNLOCK, "adult unlock"); // WO-A2 ①
        q = new Quota(); q.balance = 0; check(q.decide("enabled", true, true, 290) == Quota.Action.WARN && q.decide("enabled", true, true, 290) == Quota.Action.NONE, "adult warn once"); // WO-A2 ②
        q = new Quota(); q.balance = 0; check(q.decide("enabled", true, true, 0) == Quota.Action.END, "adult end"); // WO-A2 ③
        q = new Quota(); q.balance = 1000; q.warned = true; check(q.decide("enabled", true, true, 600) == Quota.Action.NONE && !q.warned, "adult no warn"); // WO-A2 ④
        Quota.Usage f = Quota.usage(Arrays.asList(e(10,1,y), e(70,2,y)), 0, 100000, false, y, true, 80000);
        check(f.seconds == 0 && f.ytS == 0 && f.nfS == 0, "adult free full"); // WO-A2 ⑤
        f = Quota.usage(Arrays.asList(e(30,1,y)), 0, 60000, false, "", true, 30000);
        check(f.seconds == 30 && f.ytS == 30 && f.nfS == 0, "adult split idle then yt"); // WO-A2 ⑥
        f = Quota.usage(Arrays.asList(e(0,1,y), e(30,2,y), e(30,1,n), e(60,2,n)), 0, 60000, false, "", true, 30000);
        check(f.seconds == 30 && f.ytS == 0 && f.nfS == 30, "adult yt free nf billed"); // WO-A2 ⑦
        q = new Quota(); q.balance = 0; check(q.nextAlarmMs(true, 50) == 50000, "adult alarm"); // WO-A2 ⑧
        Quota aq = new Quota(); aq.balance = 500; aq.lastSeq = 7; aq.enforce = true; aq.dbId = "db"; aq.adultEnd = 123456;
        Properties ap = aq.save(); ap.remove("adult_end_el");
        Quota ac = Quota.load(ap); check(ac.balance == 500 && ac.lastSeq == 7 && ac.enforce && ac.adultEnd == 0, "load old state adult 0"); // WO-A2 ⑨
        ap = aq.save(); ap.setProperty("adult_end_el", "abc");
        ac = Quota.load(ap); check(ac.balance == 500 && ac.lastSeq == 7 && ac.enforce && ac.adultEnd == 0, "load bad adult 0"); // WO-A2 ⑩
        Quota ar = Quota.load(aq.save()); check(ar.adultEnd == 123456 && ar.save().equals(aq.save()), "save load adult");
        check(Quota.adultInvalid(2, 1, 1000, 500) && Quota.adultInvalid(-1, 1, 1000, 500) && Quota.adultInvalid(1, 1, 400, 500) && !Quota.adultInvalid(1, 1, 1000, 500), "adult invalid"); // WO-A2 ⑪
        check(Quota.freeToWall(1000000, 50000, 0) == 0, "adult freeToWall zero"); // fix1 ⑫
        check(Quota.freeToWall(1000000, 50000, 40000) == 990000, "adult freeToWall mapped"); // fix1 ⑬
        Quota.Usage z = Quota.usage(Arrays.asList(e(0, 1, y), e(60, 2, y)), 0, 60000, true, y, true, Quota.freeToWall(1000000, 50000, 0));
        check(z.seconds == 60 && z.ytS == 60 && z.nfS == 0, "adult zero end full bill"); // fix1 ⑭：不在時段 60 秒前景全扣
        q = new Quota(); q.balance = 0; // WO-A3 ①②③
        check(q.nextAlarmMs(false, 0, true, true) == 10000, "poll locked screen on 10s");
        check(q.nextAlarmMs(true, 0, true, true) == 10000, "poll locking screen on 10s");
        check(q.nextAlarmMs(false, 0, false, true) == 60000, "poll locked screen off 60s");
        check(q.nextAlarmMs(false, 0, true, false) == 60000, "poll unknown 60s");
        q.balance = 20; // WO-A3 ④
        check(q.nextAlarmMs(true, 0, true, true) == 20000 && q.nextAlarmMs(true, 0, false, false) == 20000, "poll eff20 same");
        q.balance = 330;
        check(q.nextAlarmMs(true, 0, true, true) == 30000 && q.nextAlarmMs(true, 0, true, false) == 30000, "poll eff330 same");
        q.balance = 0;
        check(q.nextAlarmMs(true, 50, true, true) == 50000 && q.nextAlarmMs(true, 50, false, false) == 50000, "poll adult eff50 same");
        check(Quota.wouldStop("enabled", false, 0) && !Quota.wouldStop("disabled", false, 0)
                && !Quota.wouldStop("unknown", false, 0) && !Quota.wouldStop("unknown", true, 0)
                && Quota.wouldStop("enabled", true, 0) && !Quota.wouldStop("enabled", false, 60), "wouldStop"); // WO-A3 ⑤
        System.out.println("ALL PASS");
    }
}
