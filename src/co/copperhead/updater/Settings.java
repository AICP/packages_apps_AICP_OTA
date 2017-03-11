package co.copperhead.updater;

import android.os.Bundle;
import android.content.SharedPreferences;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class Settings extends PreferenceActivity {
    static final String KEY_NETWORK_TYPE = "network_type";

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);
        final Preference networkType = findPreference(KEY_NETWORK_TYPE);
        networkType.setOnPreferenceChangeListener((final Preference preference, final Object newValue) -> {
            final int value = Integer.parseInt((String) newValue);
            final SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(Settings.this);
            preferences.edit().putInt(KEY_NETWORK_TYPE, value).apply();
            PeriodicJob.schedule(Settings.this);
            return true;
        });
    }
}
