package com.example.tvagent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public class KickReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (!"com.example.tvagent.KICK".equals(intent.getAction())) return;
        Intent service = new Intent(context, AgentService.class);
        service.putExtra("reason", "kick");
        BootReceiver.startServiceCompat(context, service);
    }
}
