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

import android.content.Context;
import android.os.Environment;
import android.os.StatFs;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Scanner;

public class IOUtils {

    private static final String SDCARD = Environment.getExternalStorageDirectory()
            .getAbsolutePath();

    public static final String DOWNLOAD_PATH = new File(Environment
            .getExternalStorageDirectory(), "AICP_ota/").getAbsolutePath();

    private static final String PREFIX = "aicp_";
    private static final String SUFFIX = ".zip";

    private static Properties sDictionary;
    private static String sPrimarySdcard;
    private static String sSecondarySdcard;
    private static boolean sSdcardsChecked;

    public static void init() {
        File downloads = new File(DOWNLOAD_PATH);
        //noinspection ResultOfMethodCallIgnored
        downloads.mkdirs();

        readMounts();
    }

    public static boolean isExternalStorageAvailable() {
        return Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState());
    }

    public static String getPrimarySdCard() {
        return sPrimarySdcard;
    }

    public static String getSecondarySdCard() {
        return sSecondarySdcard;
    }

    private static void readMounts() {
        if (sSdcardsChecked) {
            return;
        }

        ArrayList<String> mounts = new ArrayList<>();
        ArrayList<String> vold = new ArrayList<>();

        Scanner scanner = null;
        try {
            scanner = new Scanner(new File("/proc/mounts"));
            while (scanner.hasNext()) {
                String line = scanner.nextLine();
                if (line.startsWith("/dev/block/vold/")) {
                    String[] lineElements = line.split(" ");
                    String element = lineElements[1];

                    mounts.add(element);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
        boolean addExternal = mounts.size() == 1 && isExternalStorageAvailable();
        if (mounts.size() == 0 && addExternal) {
            mounts.add("/mnt/sdcard");
        }
        File fstab = findFstab();
        scanner = null;
        if (fstab != null) {
            try {

                scanner = new Scanner(fstab);
                while (scanner.hasNext()) {
                    String line = scanner.nextLine();
                    if (line.startsWith("dev_mount")) {
                        String[] lineElements = line.split(" ");
                        String element = lineElements[2];

                        if (element.contains(":")) {
                            element = element.substring(0, element.indexOf(":"));
                        }

                        if (!element.toLowerCase().contains("usb")) {
                            vold.add(element);
                        }
                    } else if (line.startsWith("/devices/platform")) {
                        String[] lineElements = line.split(" ");
                        String element = lineElements[1];

                        if (element.contains(":")) {
                            element = element.substring(0, element.indexOf(":"));
                        }

                        if (!element.toLowerCase().contains("usb")) {
                            vold.add(element);
                        }
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (scanner != null) {
                    scanner.close();
                }
            }
        }
        if (addExternal && (vold.size() == 1 && isExternalStorageAvailable())) {
            mounts.add(vold.get(0));
        }
        if (vold.size() == 0 && isExternalStorageAvailable()) {
            vold.add("/mnt/sdcard");
        }

        for (int i = 0; i < mounts.size(); i++) {
            String mount = mounts.get(i);
            File root = new File(mount);
            if (!vold.contains(mount)
                    || (!root.exists() || !root.isDirectory() || !root.canWrite())) {
                mounts.remove(i--);
            }
        }

        for (int i = 0; i < mounts.size(); i++) {
            String mount = mounts.get(i);
            if (!mount.contains("sdcard0") && !mount.equalsIgnoreCase("/mnt/sdcard")
                    && !mount.equalsIgnoreCase(String.valueOf(R.string.sdcard))) {
                sSecondarySdcard = mount;
            } else {
                sPrimarySdcard = mount;
            }
        }

        if (sPrimarySdcard == null) {
            sPrimarySdcard = String.valueOf(R.string.sdcard);
        }

        sSdcardsChecked = true;
    }

    private static File findFstab() {
        File file;

        file = new File("/system/etc/vold.fstab");
        if (file.exists()) {
            return file;
        }

        String fstab = Utils
                .exec();
        if (fstab != null) {
            String[] files = fstab.split("\n");
            for (String file1 : files) {
                file = new File(file1);
                if (file.exists()) {
                    return file;
                }
            }
        }

        return null;
    }

    public static double getSpaceLeft() {
        StatFs stat = new StatFs(Environment.getExternalStorageDirectory().getPath());
        double sdAvailSize = (double) stat.getAvailableBlocksLong()
                * (double) stat.getBlockSizeLong();
        // One binary gigabyte equals 1,073,741,824 bytes.
        return sdAvailSize / 1073741824;
    }

    public static String md5(File file) {
        InputStream is = null;
        try {
            is = new FileInputStream(file);
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] md5sum = digest.digest();
            BigInteger bigInt = new BigInteger(1, md5sum);
            String md5 = bigInt.toString(16);
            while (md5.length() < 32) {
                md5 = "0" + md5;
            }
            return md5;
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static Properties getDictionary(Context context) {
        if (sDictionary == null) {
            sDictionary = new Properties();
            try {
                sDictionary.load(context.getAssets().open("dictionary.properties"));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return sDictionary;
    }

    public static boolean hasAndroidSecure() {
        return folderExists(SDCARD + "/.android-secure");
    }

    public static boolean hasSdExt() {
        return folderExists("/sd-ext");
    }

    private static boolean folderExists(String path) {
        File f = new File(path);
        return f.exists() && f.isDirectory();
    }
}
