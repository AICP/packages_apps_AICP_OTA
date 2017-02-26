package co.copperhead.updater;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class PeriodicJob extends JobService {
    private static final String TAG = "PeriodicJob";
    private static final int JOB_ID = 1;
    private static final int DEFAULT_NETWORK_TYPE = JobInfo.NETWORK_TYPE_ANY;
    private static final long INTERVAL_MILLIS = 60 * 60 * 1000;

    static void schedule(Context context) {
        final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        final int networkType = preferences.getInt(Settings.KEY_NETWORK_TYPE, DEFAULT_NETWORK_TYPE);
        Log.d(TAG, "networkType: " + networkType);

        final JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        final JobInfo jobInfo = scheduler.getPendingJob(JOB_ID);
        if (jobInfo != null &&
                jobInfo.getNetworkType() == networkType &&
                jobInfo.isPersisted() &&
                jobInfo.getIntervalMillis() == INTERVAL_MILLIS) {
            Log.d(TAG, "Job already registered");
            return;
        }
        final ComponentName serviceName = new ComponentName(context, PeriodicJob.class);
        final int result = scheduler.schedule(new JobInfo.Builder(JOB_ID, serviceName)
            .setRequiredNetworkType(networkType)
            .setPersisted(true)
            .setPeriodic(INTERVAL_MILLIS)
            .build());
        if (result == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Job schedule success");
        } else {
            Log.d(TAG, "Job schedule failed");
        }
    }

    static void cancel(Context context) {
        final JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.cancel(JOB_ID);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "onStartJob");
        sendBroadcast(new Intent(this, TriggerUpdateReceiver.class));
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
