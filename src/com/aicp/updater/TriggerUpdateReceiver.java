package com.aicp.updater;

import android.support.v4.content.WakefulBroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TriggerUpdateReceiver extends WakefulBroadcastReceiver {
    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (Service.ACTION_ABORT.equals(intent.getAction())) {
            Service.requestStop();
        } else {
            Service.allowStart();
        }
        startWakefulService(context, new Intent(context, Service.class)
                .setAction(intent.getAction()));
    }
}
