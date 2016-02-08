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

import com.aicp.aicpota.Version;
import com.aicp.aicpota.updater.Updater.PackageInfo;

import java.io.Serializable;

public class UpdatePackage implements PackageInfo, Serializable {

    private String mMd5 = null;
    private String mFilename = null;
    private String mPath = null;
    private String mHost = null;
    private String mSize = null;
    private final Version mVersion;
    private boolean mIsGapps = false;

    public UpdatePackage(String name, Version version, String size, String url,
                         String md5) {
        this.mFilename = name;
        this.mVersion = version;
        this.mSize = size;
        this.mPath = url;
        this.mMd5 = md5;
        this.mIsGapps = false;
        mHost = mPath.replace("http://", "");
        mHost = mHost.replace("https://", "");
        mHost = mHost.substring(0, mHost.indexOf("/"));
    }

    @Override
    public String getMd5() {
        return mMd5;
    }

    @Override
    public String getFilename() {
        return mFilename;
    }

    @Override
    public String getPath() {
        return mPath;
    }

    @Override
    public String getHost() {
        return mHost;
    }

    @Override
    public Version getVersion() {
        return mVersion;
    }

    @Override
    public String getSize() {
        return mSize;
    }

    @Override
    public boolean isGapps() {
        return mIsGapps;
    }
}
