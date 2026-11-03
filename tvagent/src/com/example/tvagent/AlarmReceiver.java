package com.example.tvagent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 鬧鐘心跳接收器：由 AlarmManager 每 60 秒用精確鬧鐘喚醒（明確廣播，
 * exported=false）。收到後只負責把服務叫起來（action HEARTBEAT）；
 * 自己不做任何 adb 動作。TCL 的 FreezeManager 會凍結閒置程序，
 * 但收到廣播時系統會立即解凍該程序，所以鬧鐘能穿透凍結。
 */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent svc = new Intent(context, AgentService.class);
        svc.setAction(AgentService.HEARTBEAT_ACTION);
        BootReceiver.startServiceCompat(context, svc);
    }

    /** 保持向後相容的呼叫形狀（目前未使用，預留直接啟動服務用）。 */
    static void startServiceCompat(Context context, Intent svc) {
        BootReceiver.startServiceCompat(context, svc);
    }
}
