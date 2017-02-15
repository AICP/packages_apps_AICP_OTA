package co.copperhead.updater;

import android.app.job.JobService;
import android.app.job.JobParameters;
import android.content.Intent;
import android.util.Log;

public class PeriodicJob extends JobService {
    static final String TAG = "PeriodicJob";

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
