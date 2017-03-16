package co.copperhead.updater;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

public class IdleReboot extends JobService {
    private static final String TAG = "IdleReboot";
    private static final int JOB_ID_IDLE_REBOOT = 3;

    static void schedule(final Context context) {
        final JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        final ComponentName serviceName = new ComponentName(context, IdleReboot.class);
        final int result = scheduler.schedule(new JobInfo.Builder(JOB_ID_IDLE_REBOOT, serviceName)
            .setRequiresDeviceIdle(true)
            .build());
        if (result == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Job schedule success");
        } else {
            Log.d(TAG, "Job schedule failed");
        }
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        pm.reboot(null);
        return false;
    }

    @Override
    public boolean onStopJob(final JobParameters params) {
        return false;
    }
}
