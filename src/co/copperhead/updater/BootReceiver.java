package co.copperhead.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserManager;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (UserManager.get(context).isSystemUser()) {
            PeriodicJob.schedule(context);
        } else {
            context.getPackageManager().setApplicationEnabledSetting(context.getPackageName(),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED, 0);
        }
    }
}
