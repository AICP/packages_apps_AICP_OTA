package co.copperhead.updater;

import android.support.v4.content.WakefulBroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import co.copperhead.updater.Service;

public class TriggerUpdateReceiver extends WakefulBroadcastReceiver {
    private static final String TAG = "TriggerUpdateReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (context.getResources().getString(R.string.trigger_update).equals(action)) {
            Log.d(TAG, "startWakefulService");
            startWakefulService(context, new Intent(context, Service.class));
        } else {
            Log.d(TAG, "unhandled action: " + action);
        }
    }
}
