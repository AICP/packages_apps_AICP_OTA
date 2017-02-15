package co.copperhead.updater;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import co.copperhead.updater.PeriodicJob;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";
    private static final int JOB_ID = 1;
    private static final long INTERVAL_MILLIS = 60 * 60 * 1000;

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();

        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            JobInfo jobInfo = scheduler.getPendingJob(JOB_ID);
            if (jobInfo != null &&
                    jobInfo.getNetworkType() == JobInfo.NETWORK_TYPE_ANY &&
                    jobInfo.isPersisted() &&
                    jobInfo.getIntervalMillis() == INTERVAL_MILLIS) {
                Log.d(TAG, "Job already registered");
                return;
            }
            ComponentName serviceName = new ComponentName(context, PeriodicJob.class);
            jobInfo = new JobInfo.Builder(JOB_ID, serviceName)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(INTERVAL_MILLIS)
                .build();
            int result = scheduler.schedule(jobInfo);
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "Job schedule success");
            } else {
                Log.d(TAG, "Job schedule failed");
            }
        } else {
            Log.d(TAG, "unhandled action: " + action);
        }
    }
}
