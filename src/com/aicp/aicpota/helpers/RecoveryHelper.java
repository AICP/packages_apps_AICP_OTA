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

package com.aicp.aicpota.helpers;

import android.content.Context;
import android.util.SparseArray;

import com.aicp.aicpota.IOUtils;
import com.aicp.aicpota.R;
import com.aicp.aicpota.Utils;
import com.aicp.aicpota.helpers.recovery.CwmBasedRecovery;
import com.aicp.aicpota.helpers.recovery.RecoveryInfo;
import com.aicp.aicpota.helpers.recovery.TwrpRecovery;

public class RecoveryHelper {

    private final SparseArray<RecoveryInfo> mRecoveries = new SparseArray<>();

    public RecoveryHelper(Context context) {

        mRecoveries.put(Utils.CWM_BASED, new CwmBasedRecovery(context));
        mRecoveries.put(Utils.TWRP, new TwrpRecovery());
    }

    private RecoveryInfo getRecovery(int id) {
        for (int i = 0; i < mRecoveries.size(); i++) {
            int key = mRecoveries.keyAt(i);
            RecoveryInfo info = mRecoveries.get(key);
            if (info.getId() == id) {
                return info;
            }
        }
        return null;
    }

    public String getCommandsFile(int id) {

        RecoveryInfo info = getRecovery(id);

        if (info != null) {
            return info.getCommandsFile();
        }
        return null;
    }

    public String getRecoveryFilePath(int id, String filePath) {

        RecoveryInfo info = getRecovery(id);

        String internalStorage = null;
        if (info != null) {
            internalStorage = info.getInternalSdcard();
        }
        String externalStorage = null;
        if (info != null) {
            externalStorage = info.getExternalSdcard();
        }

        String primarySdcard = IOUtils.getPrimarySdCard();
        String secondarySdcard = IOUtils.getSecondarySdCard();

        String[] internalNames = new String[] {
                primarySdcard,
                "/mnt/sdcard",
                "/storage/sdcard/",
                String.valueOf(R.string.sdcard),
                "/storage/sdcard0",
                "/storage/emulated/0"
        };
        String[] externalNames = new String[] {
                secondarySdcard == null ? " " : secondarySdcard,
                "/mnt/extSdCard",
                "/storage/extSdCard/",
                "/extSdCard",
                "/storage/sdcard1",
                "/storage/emulated/1"
        };
        for (int i = 0; i < internalNames.length; i++) {
            String internalName = internalNames[i];
            String externalName = externalNames[i];
            if (filePath.startsWith(externalName)) {
                filePath = filePath.replace(externalName, "/" + externalStorage);
                break;
            } else if (filePath.startsWith(internalName)) {
                filePath = filePath.replace(internalName, "/" + internalStorage);
                break;
            }
        }

        while (filePath.startsWith("//")) {
            filePath = filePath.substring(1);
        }

        return filePath;
    }

    public String[] getCommands(int id, String[] items, boolean wipeData,
                                boolean wipeCaches, String backupFolder, String backupOptions) {

        RecoveryInfo info = getRecovery(id);

        if (info != null) {
            return info.getCommands(items, wipeData, wipeCaches, backupFolder,
                    backupOptions);
        }
        return null;
    }
}
