/*
 * Copyright 2014 AicpAndroid Project
 *
 * This file is part of Aicp OTA.
 *
 * Aicp OTA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Aicp OTA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Aicp OTA.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.aicp.aicpota.activities;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.support.v4.app.NavUtils;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;

import com.aicp.aicpota.R;
import com.aicp.aicpota.URLStringReader;
import com.aicp.aicpota.URLStringReader.URLStringReaderListener;
import com.aicp.aicpota.Utils;
import com.aicp.aicpota.helpers.SettingsHelper;
import com.aicp.aicpota.widget.Preference;

public class SettingsActivity extends PreferenceActivity implements
        OnSharedPreferenceChangeListener {


    private SettingsHelper mSettingsHelper;
    private ListPreference mCheckTime;
    private CheckBoxPreference mCheckGapps;

    @Override
    @SuppressWarnings("deprecation")
    protected void onCreate(Bundle savedInstanceState) {

        mSettingsHelper = new SettingsHelper(this);

        super.onCreate(savedInstanceState);

        getActionBar().setDisplayHomeAsUpEnabled(true);

        addPreferencesFromResource(R.layout.activity_settings);

        mCheckTime = (ListPreference) findPreference(SettingsHelper.PROPERTY_CHECK_TIME);
        mCheckGapps = (CheckBoxPreference) findPreference(SettingsHelper.PROPERTY_CHECK_GAPPS);

        mCheckTime.setValue(String.valueOf(mSettingsHelper.getCheckTime()));
        mCheckGapps.setChecked(mSettingsHelper.getCheckGapps());

        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    @Deprecated
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            android.preference.Preference preference) {
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (SettingsHelper.PROPERTY_CHECK_TIME.equals(key)) {
            Utils.setAlarm(this, mSettingsHelper.getCheckTime(), false, true);
        }
    }
}
