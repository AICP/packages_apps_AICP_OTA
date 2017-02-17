package co.copperhead.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

public class RebootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        final PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        pm.reboot(null);
    }
}
