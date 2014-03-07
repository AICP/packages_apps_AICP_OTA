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

package com.schism.schismota.updater;

import android.content.Context;

import com.schism.schismota.R;
import com.schism.schismota.Utils;
import com.schism.schismota.Version;
import com.schism.schismota.updater.server.SchismServer;

public class RomUpdater extends Updater {

    public RomUpdater(Context context, boolean fromAlarm) {
        super(context, new Server[] { new SCHISMServer() }, fromAlarm);
    }

    @Override
    public Version getVersion() {
        String version = getDevice() + "-" + Utils.getProp(Utils.MOD_VERSION);
        return new Version(version);
    }

    @Override
    public boolean isRom() {
        return true;
    }

    @Override
    public String getDevice() {
        String device = Utils.getProp(PROPERTY_DEVICE);
        if (device == null || device.isEmpty()) {
            device = Utils.getProp(PROPERTY_DEVICE_EXT);
            device = Utils.translateDeviceName(getContext(), device);
        }
        return device == null ? "" : device.toLowerCase();
    }

    @Override
    public int getErrorStringId() {
        return R.string.check_rom_updates_error;
    }

}
