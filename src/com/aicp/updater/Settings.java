package com.aicp.updater;

import android.app.ActionBar;
import android.app.job.JobInfo;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.UserManager;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

public class Settings extends BaseSettingsActivity {
    private static final int DEFAULT_NETWORK_TYPE = JobInfo.NETWORK_TYPE_UNMETERED;
    private static final String KEY_AUTO_UPDATE = "auto_update";
    private static final String KEY_AUTO_UPDATE_PROMPT_DOWNLOAD = "auto_update_prompt_download";
    private static final String KEY_NETWORK_TYPE = "network_type";
    private static final String KEY_UPDATE_STATUS = "update_status";
    private static final String PROPERTY_CHECK_TIME = "checktime";
    private static final String DEFAULT_CHECK_TIME = "86400000"; // One day
    static final String KEY_BATTERY_NOT_LOW = "battery_not_low";
    static final String KEY_IDLE_REBOOT = "idle_reboot";
    static final String KEY_WAITING_FOR_REBOOT = "waiting_for_reboot";

    private Preference mUpdateStatusPref;

    private int mUpdateInfo = Service.INFO_NONE;
    private long mUpdateProgress = 0;
    private String mUpdateFilename = null;

    static SharedPreferences getPreferences(final Context context) {
        final Context deviceContext = context.createDeviceProtectedStorageContext();
        return PreferenceManager.getDefaultSharedPreferences(deviceContext);
    }

    static boolean getAutoUpdate(final Context context) {
        return getPreferences(context).getBoolean(KEY_AUTO_UPDATE, true);
    }

    static boolean getAutoUpdatePromptDownloadRequired(final Context context) {
        return getPreferences(context).getBoolean(KEY_AUTO_UPDATE_PROMPT_DOWNLOAD, true);
    }

    static int getNetworkType(final Context context) {
        return getPreferences(context).getInt(KEY_NETWORK_TYPE, DEFAULT_NETWORK_TYPE);
    }


    static boolean getBatteryNotLow(final Context context) {
        return getPreferences(context).getBoolean(KEY_BATTERY_NOT_LOW, true);
    }

    static String getCheckTime(final Context context) {
        return getPreferences(context).getString(PROPERTY_CHECK_TIME, DEFAULT_CHECK_TIME);
    }


    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!UserManager.get(this).isSystemUser()) {
            throw new SecurityException("system user only");
        }
        getPreferenceManager().setStorageDeviceProtected();
        addPreferencesFromResource(R.xml.settings);

        final Preference networkType = findPreference(KEY_NETWORK_TYPE);
        networkType.setOnPreferenceChangeListener((final Preference preference, final Object newValue) -> {
            final int value = Integer.parseInt((String) newValue);
            getPreferences(this).edit().putInt(KEY_NETWORK_TYPE, value).apply();
            if (!getPreferences(this).getBoolean(KEY_WAITING_FOR_REBOOT, false)) {
                PeriodicJob.schedule(this);
            }
            return true;
        });

        final Preference batteryNotLow = findPreference(KEY_BATTERY_NOT_LOW);
        batteryNotLow.setOnPreferenceChangeListener((final Preference preference, final Object newValue) -> {
            getPreferences(this).edit().putBoolean(KEY_BATTERY_NOT_LOW, (boolean) newValue).apply();
            if (!getPreferences(this).getBoolean(KEY_WAITING_FOR_REBOOT, false)) {
                PeriodicJob.schedule(this);
            }
            return true;
        });

        final Preference idleReboot = findPreference(KEY_IDLE_REBOOT);
        idleReboot.setOnPreferenceChangeListener((final Preference preference, final Object newValue) -> {
            final boolean value = (Boolean) newValue;
            if (!value) {
                IdleReboot.cancel(this);
            }
            return true;
        });

        final Preference autoUpdate = findPreference(KEY_AUTO_UPDATE);
        autoUpdate.setOnPreferenceChangeListener((final Preference preference, final Object newValue) -> {
            final boolean value = (Boolean) newValue;
            if (!value) {
                getPreferences(this).edit().putBoolean(KEY_AUTO_UPDATE, (boolean) newValue).apply();
                if (!getPreferences(this).getBoolean(KEY_WAITING_FOR_REBOOT, false)) {
                    // This also cancels jobs if needed
                    PeriodicJob.schedule(this);
                    // If download already in progress, stop it in case it was started automatically
                    Service.requestStop();
                }
            }
            return true;
        });

        mUpdateStatusPref = findPreference(KEY_UPDATE_STATUS);
        mUpdateStatusPref.setOnPreferenceClickListener((final Preference preference) -> {
            if (getPreferences(this).getBoolean(KEY_WAITING_FOR_REBOOT, false)) {
                // Reboot and apply update
                sendBroadcast(new Intent(this, RebootReceiver.class));
            } else if (mUpdateInfo == Service.INFO_DOWNLOADING) {
                // Stop update
                Service.requestStop();
            } else if (mUpdateInfo == Service.INFO_DOWNLOAD_PENDING || mUpdateInfo == Service.INFO_UPDATE_PENDING) {
                sendBroadcast(new Intent(this, TriggerUpdateReceiver.class).setAction(Service.ACTION_DOWNLOAD));
            } else {
                // Check for update
                Service.allowStart();
                sendBroadcast(new Intent(this, TriggerUpdateReceiver.class));
            }
            return true;
        });

        LocalBroadcastManager.getInstance(this).registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mUpdateProgress = intent.getLongExtra(Service.EXTRA_PROGRESS, 0);
                mUpdateInfo = intent.getIntExtra(Service.EXTRA_INFO, Service.INFO_NONE);
                mUpdateFilename = intent.getStringExtra(Service.EXTRA_FILENAME);
                updateUpdateStatus();
            }
        }, new IntentFilter(Service.INTENT_UPDATE));

        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowCustomEnabled(true);
            View titleView = getLayoutInflater().inflate(R.layout.actionbar_title, null);
            titleView.setOnClickListener((final View v) -> {
                View logo = v.findViewById(R.id.actionbar_logo);
                v.findViewById(R.id.actionbar_logo).setVisibility(View.VISIBLE);
                ColorDrawable background =
                        new ColorDrawable(getColor(R.color.action_bar_bg_sheep));
                int foreground = getColor(R.color.action_bar_fg_sheep);
                getActionBar().setBackgroundDrawable(background);
                ((TextView) (((ViewGroup) v).findViewById(R.id.actionbar_title)))
                        .setTextColor(foreground);
                getWindow().setStatusBarColor(getColor(R.color.status_bar_bg_sheep));
                // Fix status bar fg color for changed bg
                int oldSystemUiFlags = getWindow().getDecorView().getSystemUiVisibility();
                int newSystemUiFlags = oldSystemUiFlags;
                if (getResources().getBoolean(R.bool.status_bar_sheep_is_light)) {
                    newSystemUiFlags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                } else {
                    newSystemUiFlags &= ~(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                }
                if (newSystemUiFlags != oldSystemUiFlags) {
                    getWindow().getDecorView().setSystemUiVisibility(newSystemUiFlags);
                }
            });
            actionBar.setCustomView(titleView);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final ListPreference networkType = (ListPreference) findPreference(KEY_NETWORK_TYPE);
        networkType.setValue(Integer.toString(getNetworkType(this)));
        updateUpdateStatus();
        sendBroadcast(new Intent(this, TriggerUpdateReceiver.class).setAction(Service.ACTION_INFO));
    }

    private void updateUpdateStatus() {
        if (getPreferences(this).getBoolean(KEY_WAITING_FOR_REBOOT, false)) {
            mUpdateStatusPref.setTitle(R.string.update_status_update_ready_title);
            mUpdateStatusPref.setSummary(R.string.update_status_update_ready_summary);
            return;
        }
        switch (mUpdateInfo) {
            case Service.INFO_DOWNLOADING:
                mUpdateStatusPref.setTitle(R.string.update_status_downloading_title);
                mUpdateStatusPref.setSummary(getString(R.string.udpate_status_downloading_summary,
                        mUpdateProgress/1_000_000));
                break;
            case Service.INFO_UP_TO_DATE:
                mUpdateStatusPref.setTitle(R.string.update_status_up_to_date_title);
                mUpdateStatusPref.setSummary(R.string.update_status_up_to_date_summary);
                break;
            case Service.INFO_UPDATE_PENDING:
                mUpdateStatusPref.setTitle(R.string.update_status_update_pending_title);
                mUpdateStatusPref.setSummary(R.string.update_status_update_pending_summary);
                break;
            case Service.INFO_DOWNLOAD_PENDING:
                mUpdateStatusPref.setTitle(R.string.update_status_download_pending_title);
                mUpdateStatusPref.setSummary(R.string.update_status_download_pending_summary);
                break;
            case Service.INFO_NO_BUILDS_AVAILABLE:
                mUpdateStatusPref.setTitle(R.string.update_status_no_builds_title);
                mUpdateStatusPref.setSummary(R.string.update_status_no_builds_summary);
                break;
            case Service.INFO_ERROR:
                mUpdateStatusPref.setTitle(R.string.update_status_error_title);
                mUpdateStatusPref.setSummary(R.string.update_status_error_summary);
                break;
            case Service.INFO_NONE:
            default:
                mUpdateStatusPref.setTitle(R.string.update_status_none_title);
                mUpdateStatusPref.setSummary(R.string.update_status_none_summary);
                break;
        }
    }
}
