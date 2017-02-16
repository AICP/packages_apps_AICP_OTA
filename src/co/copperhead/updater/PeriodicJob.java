package co.copperhead.updater;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PeriodicJob extends JobService {
    private static final String TAG = "PeriodicJob";
    private static final int JOB_ID = 1;
    private static final int NETWORK_TYPE = JobInfo.NETWORK_TYPE_ANY;
    private static final long INTERVAL_MILLIS = 60 * 60 * 1000;

    static void schedule(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        JobInfo jobInfo = scheduler.getPendingJob(JOB_ID);
        if (jobInfo != null &&
                jobInfo.getNetworkType() == NETWORK_TYPE &&
                jobInfo.isPersisted() &&
                jobInfo.getIntervalMillis() == INTERVAL_MILLIS) {
            Log.d(TAG, "Job already registered");
            return;
        }
        ComponentName serviceName = new ComponentName(context, PeriodicJob.class);
        jobInfo = new JobInfo.Builder(JOB_ID, serviceName)
            .setRequiredNetworkType(NETWORK_TYPE)
            .setPersisted(true)
            .setPeriodic(INTERVAL_MILLIS)
            .build();
        int result = scheduler.schedule(jobInfo);
        if (result == JobScheduler.RESULT_SUCCESS) {
            Log.d(TAG, "Job schedule success");
        } else {
            Log.d(TAG, "Job schedule failed");
        }
    }

    static void cancel(Context context) {
        JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        scheduler.cancel(JOB_ID);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "onStartJob");
        sendBroadcast(new Intent(getString(R.string.trigger_update)));
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }
}
