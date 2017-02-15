package co.copperhead.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import co.copperhead.updater.PeriodicJob;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            Log.d(TAG, "schedule PeriodicJob");
            PeriodicJob.schedule(context);
        } else {
            Log.d(TAG, "unhandled action: " + action);
        }
    }
}
