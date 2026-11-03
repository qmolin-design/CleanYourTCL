package com.example.tvagent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

/** 開機自啟：只負責把服務叫起來，所有 adb 動作都在服務內的單執行緒做。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }
        Intent svc = new Intent(context, AgentService.class);
        svc.putExtra(AgentService.EXTRA_REASON, "boot");
        startServiceCompat(context, svc);
    }

    static void startServiceCompat(Context context, Intent svc) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(svc);
            } else {
                context.startService(svc);
            }
        } catch (Exception e) {
            try {
                context.startService(svc);
            } catch (Exception ignored) {
                // 啟動失敗只能等下次廣播
            }
        }
    }
}
