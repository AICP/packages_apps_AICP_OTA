/*
 * Copyright 2014 ParanoidAndroid Project
 *
 * This file is part of Paranoid OTA.
 *
 * Paranoid OTA is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Paranoid OTA is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Paranoid OTA.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.aicp.aicpota.updater;

import android.app.Activity;
import android.content.Context;

import com.aicp.aicpota.R;
import com.aicp.aicpota.Utils;
import com.aicp.aicpota.Version;
import com.aicp.aicpota.helpers.SettingsHelper;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public abstract class Updater implements Response.Listener<JSONObject>, Response.ErrorListener {

    public interface PackageInfo extends Serializable {

        String getMd5();

        String getFilename();

        String getPath();

        String getHost();

        String getSize();

        Version getVersion();

        boolean isGapps();
    }

    static final String PROPERTY_DEVICE = "ro.aicp.device";
    static final String PROPERTY_DEVICE_EXT = "ro.product.device";

    public static final int NOTIFICATION_ID = 122303225;

    public interface UpdaterListener {

        void startChecking(boolean isRom);

        void versionFound(PackageInfo[] info, boolean isRom);

        void checkError(String cause, boolean isRom);
    }

    private final Context mContext;
    private final Server[] mServers;
    private PackageInfo[] mLastUpdates = new PackageInfo[0];
    private final List<UpdaterListener> mListeners = new ArrayList<>();
    private final RequestQueue mQueue;
    private SettingsHelper mSettingsHelper;
    private Server mServer;
    private boolean mScanning = false;
    private final boolean mFromAlarm;
    private boolean mServerWorks = false;
    private int mCurrentServer = -1;

    Updater(Context context, Server[] servers, boolean fromAlarm) {
        mContext = context;
        mServers = servers;
        mFromAlarm = fromAlarm;
        mQueue = Volley.newRequestQueue(context);
    }

    protected abstract Version getVersion();

    protected abstract String getDevice();

    protected abstract boolean isRom();

    protected abstract int getErrorStringId();

    Context getContext() {
        return mContext;
    }

    SettingsHelper getSettingsHelper() {
        return mSettingsHelper;
    }

    public PackageInfo[] getLastUpdates() {
        return mLastUpdates;
    }

    public void setLastUpdates(PackageInfo[] infos) {
        if (infos == null) {
            infos = new PackageInfo[0];
        }
        mLastUpdates = infos;
    }

    public void addUpdaterListener(UpdaterListener listener) {
        mListeners.add(listener);
    }

    public void check() {
        check(false);
    }

    public void check(boolean force) {
        if (mScanning) {
            return;
        }
        if (mSettingsHelper == null) {
            mSettingsHelper = new SettingsHelper(getContext());
        }
        if (mFromAlarm) {
            if (!force && (mSettingsHelper.getCheckTime() < 0
                    || (!isRom() && !mSettingsHelper.getCheckGapps()))) {
                return;
            }
        }
        mServerWorks = false;
        mScanning = true;
        fireStartChecking();
        nextServerCheck();
    }

    private void nextServerCheck() {
        mScanning = true;
        mCurrentServer++;
        mServer = mServers[mCurrentServer];
        JsonObjectRequest jsObjRequest = new JsonObjectRequest(Request.Method.GET, mServer.getUrl(
                getDevice(), getVersion()), null, this, this);
        mQueue.add(jsObjRequest);
    }

    @Override
    public void onResponse(JSONObject response) {
        mScanning = false;
        try {
            PackageInfo[] lastUpdates;
            setLastUpdates(null);
            List<PackageInfo> list = mServer.createPackageInfoList(response);
            String error = mServer.getError();
            if (!isRom()) {
                int gappsType = mSettingsHelper.getGappsType();
                PackageInfo info;
                for (int i = 0; i < list.size(); i++) {
                    info = list.get(i);
                    String fileName = info.getFilename();
                    if ((gappsType == SettingsHelper.GAPPS_MINI && !fileName.contains("-mini"))
                            ||
                            (gappsType == SettingsHelper.GAPPS_FULLINVERTED && !fileName
                                    .contains("-fullinverted"))
                            ||
                            (gappsType == SettingsHelper.GAPPS_FULL && !fileName.contains("-full"))
                            ||
                            (gappsType == SettingsHelper.GAPPS_MINIINVERTED && !fileName
                                    .contains("-miniinverted"))) {
                        list.remove(i);
                        i--;
                    }
                }
            }
            lastUpdates = list.toArray(new PackageInfo[list.size()]);
            if (lastUpdates.length > 0) {
                mServerWorks = true;
                if (mFromAlarm) {
                    if (!isRom()) {
                        Utils.showNotification(getContext(), null, lastUpdates);
                    } else {
                        Utils.showNotification(getContext(), lastUpdates, null);
                    }
                }
            } else {
                if (error != null && !error.isEmpty()) {
                    if (versionError(error)) {
                        return;
                    }
                } else {
                    mServerWorks = true;
                    if (mCurrentServer < mServers.length - 1) {
                        nextServerCheck();
                        return;
                    }
                }
            }
            mCurrentServer = -1;
            setLastUpdates(lastUpdates);
            fireCheckCompleted(lastUpdates);
        } catch (Exception ex) {
            System.out.println(response.toString());
            ex.printStackTrace();
            versionError(null);
        }
    }

    @Override
    public void onErrorResponse(VolleyError ex) {
        mScanning = false;
        versionError(null);
    }

    private boolean versionError(String error) {
        if (mCurrentServer < mServers.length - 1) {
            nextServerCheck();
            return true;
        }
        if (!mFromAlarm && !mServerWorks) {
            int id = getErrorStringId();
            if (error != null) {
                Utils.showToastOnUiThread(getContext(), getContext().getResources().getString(id)
                        + ": " + error);
            } else {
                if (id != R.string.check_gapps_updates_error) {
                    Utils.showToastOnUiThread(getContext(), id);
                }
            }
        }
        mCurrentServer = -1;
        fireCheckCompleted(null);
        fireCheckError(error);
        return false;
    }

    public boolean isScanning() {
        return mScanning;
    }

    private void fireStartChecking() {
        if (mContext instanceof Activity) {
            ((Activity) mContext).runOnUiThread(new Runnable() {

                public void run() {
                    for (UpdaterListener listener : mListeners) {
                        listener.startChecking(isRom());
                    }
                }
            });
        }
    }

    private void fireCheckCompleted(final PackageInfo[] info) {
        if (mContext instanceof Activity) {
            ((Activity) mContext).runOnUiThread(new Runnable() {

                public void run() {
                    for (UpdaterListener listener : mListeners) {
                        listener.versionFound(info, isRom());
                    }
                }
            });
        }
    }

    private void fireCheckError(final String cause) {
        if (mContext instanceof Activity) {
            ((Activity) mContext).runOnUiThread(new Runnable() {

                public void run() {
                    for (UpdaterListener listener : mListeners) {
                        listener.checkError(cause, isRom());
                    }
                }
            });
        }
    }
}
