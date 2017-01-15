/*
 * Copyright 2014 ParanoidAndroid Project
 * Copyright 2015 AICP Project
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

package com.aicp.aicpota;

import java.io.Serializable;

/**
 * Class to manage different versions in the zip name.
 * <p>
 * Format<br>
 * pa_A-B-C.DE-FG-H.zip<br>
 * where<br>
 * A = device name, required<br>
 * B = extra information, not required (for gapps)<br>
 * C = major, integer from 0 to n, required<br>
 * D = minor, integer from 0 to 9, required<br>
 * E = maintenance, integer from 0 to n, not required<br>
 * F = phase, possible values are A, B or RC, not required, default is
 * gold/production<br>
 * G = phase number, integer from 0 to n, not required<br>
 * H = date, YYYYMMDD, not required, the format can be YYYYMMDDx where x is a
 * letter (for gapps)
 * <p>
 * All the default values not specified above are 0
 * <p>
 * Examples<br>
 * pa_find5-3.99-RC2-20140212.zip<br>
 * pa_gapps-modular-mini-4.3-20141010-signed.zip
 */
public class Version implements Serializable {

    private final String[] PHASES = {
            "EXPERIMENTAL", "RELEASE", "NIGHTLY", "STABLE"
    };

    private static final String SEPARATOR = "-";

    private static final int EXPERIMENTAL = 0;
    private static final int RELEASE = 1;
    private static final int NIGHTLY = 2;
    private static final int STABLE = 3;

    private String mDevice;
    private int mMajor = 0;
    private int mMinor = 0;
    private int mMaintenance = 0;
    private int mPhase = STABLE;
    private int mPhaseNumber = 0;
    private String mDate = "0";

    public Version() {
    }

    public Version(String fileName) {

        String[] STATIC_REMOVE = {
                ".zip", "aicp_"
        };
        for (String remove : STATIC_REMOVE) {
            fileName = fileName.replace(remove, "");
        }

        String[] split = fileName.split(SEPARATOR);

        mDevice = split[0];

        // remove gapps extra names (modular, full, mini, etc)
        while (split[1].matches("\\w+\\.?")) {
            String[] newSplit = new String[split.length - 1];
            newSplit[0] = split[0];
            System.arraycopy(split, 2, newSplit, 1, split.length - 2);
            split = newSplit;
            if (split.length <= 1) {
                break;
            }
        }

        if (split.length <= 1) {
            // malformed version
            return;
        }

        try {
            String version = split[1];
            int index;
            if ((index = version.indexOf(".")) > 0) {
                mMajor = Integer.parseInt(version.substring(0, index));
                version = version.substring(index + 1);
                if (version.length() > 0) {
                    mMinor = Integer.parseInt(version.substring(0, 1));
                }
                if (version.length() > 1) {
                    String maintenance = version.substring(1);
                    if (maintenance.startsWith(".")) {
                        maintenance = maintenance.substring(1);
                    }
                    mMaintenance = Integer.parseInt(maintenance);
                }
            } else {
                mMajor = Integer.parseInt(version);
            }

            if (!Utils.isNumeric(split[2].substring(0, 1))) {
                version = split[2];

                if (version.startsWith("E")) {
                    mPhase = EXPERIMENTAL;
                    if (version.startsWith("EXPERIMENTAL")) {
                        version = version.substring(12);
                    } else {
                        version = version.substring(1);
                    }
                } else if (version.startsWith("N")) {
                    mPhase = NIGHTLY;
                    if (version.startsWith("NIGHTLY")) {
                        version = version.substring(7);
                    } else {
                        version = version.substring(1);
                    }
                } else if (version.startsWith("R")) {
                    mPhase = RELEASE;
                    version = version.substring(7);
                }
                if (!version.isEmpty()) {
                    mPhaseNumber = Integer.parseInt(version);
                }
                mDate = split[3];
            } else {
                mDate = split[2];
            }
        } catch (NumberFormatException ex) {
            // malformed version, write the log and continue
            // C derped something for sure
            ex.printStackTrace();
        }
    }

    private int getMajor() {
        return mMajor;
    }

    private int getMinor() {
        return mMinor;
    }

    private int getMaintenance() {
        return mMaintenance;
    }

    private int getPhase() {
        return mPhase;
    }

    private String getPhaseName() {
        return PHASES[mPhase];
    }

    private int getPhaseNumber() {
        return mPhaseNumber;
    }

    private String getDate() {
        return mDate;
    }

    public String toString() {
        return toString(true);
    }

    public String toString(boolean showDevice) {
        return (showDevice ? mDevice + " " : "")
                + mMajor
                + "."
                + mMinor
                + (mMaintenance > 0 ? "."
                        + mMaintenance : "")
                + (mPhase != STABLE ? " " + getPhaseName() : "")
                + (mPhaseNumber != 0 ? "" + mPhaseNumber : "")
                + " (" + mDate + ")";
    }

    public static Version fromGapps(String platform, String version) {
        return new Version("gapps-" + platform.substring(0, 1) + "."
                + (platform.length() > 1 ? platform.substring(1) : "") + "-" + version);
    }

    public static int compare(Version v1, Version v2) {
        if (v1.getMajor() != v2.getMajor()) {
            return v1.getMajor() < v2.getMajor() ? -1 : 1;
        }
        if (v1.getMinor() != v2.getMinor()) {
            return v1.getMinor() < v2.getMinor() ? -1 : 1;
        }
        if (v1.getMaintenance() != v2.getMaintenance()) {
            return v1.getMaintenance() < v2.getMaintenance() ? -1 : 1;
        }
        if (v1.getPhase() != v2.getPhase()) {
            return v1.getPhase() < v2.getPhase() ? -1 : 1;
        }
        if (v1.getPhaseNumber() != v2.getPhaseNumber()) {
            return v1.getPhaseNumber() < v2.getPhaseNumber() ? -1 : 1;
        }
        if (!v1.getDate().equals(v2.getDate())) {
            return v1.getDate().compareTo(v2.getDate());
        }
        return 0;
    }
}
