package com.aicp.updater;

import static android.os.Build.DEVICE;
import static android.os.Build.FINGERPRINT;
import static android.os.Build.VERSION.INCREMENTAL;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.RecoverySystem;
import android.os.SystemProperties;
import android.os.UpdateEngine;
import android.os.UpdateEngine.ErrorCodeConstants;
import android.os.UpdateEngineCallback;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Service extends IntentService {

    private static final boolean DEBUG_ALWAYS_UPDATE = false;

    public static final String INTENT_UPDATE = "com.aicp.updater.update";
    public static final String EXTRA_PROGRESS = "com.aicp.updater.progress";
    public static final String EXTRA_INFO = "com.aicp.updater.info";
    public static final String EXTRA_FILENAME = "com.aicp.updater.filename";
    public static final String ACTION_INFO = "com.aicp.updater.action.info";
    public static final String ACTION_DOWNLOAD = "com.aicp.updater.action.download";
    public static final String ACTION_ABORT = "com.aicp.updater.action.abort";

    public static final int INFO_NONE = 0;
    public static final int INFO_DOWNLOADING = 1;
    public static final int INFO_UP_TO_DATE = 2;
    public static final int INFO_UPDATE_PENDING = 3;
    public static final int INFO_DOWNLOAD_PENDING = 4;
    public static final int INFO_ERROR = 100;

    private static final String TAG = "OTAService";
    private static final int NOTIFICATION_ID_AVAILABLE = 1;
    private static final int NOTIFICATION_ID_PROMPT = 2;
    private static final int NOTIFICATION_ID_PROGRESS = 3;
    private static final String NOTIFICATION_CHANNEL_ID_UPDATE_AVAILABLE = "updates_available";
    private static final String NOTIFICATION_CHANNEL_ID_UPDATE_PROMPT = "update_prompt";
    private static final String NOTIFICATION_CHANNEL_ID_UPDATE_PROGRESS = "update_progress";
    private static final int PENDING_REBOOT_ID = 1;
    private static final int PENDING_SETTINGS_ID = 2;
    private static final int PENDING_DOWNLOAD_ID = 3;
    private static final int PENDING_ABORT_ID = 4;
    private static final int CONNECT_TIMEOUT = 60000;
    private static final int READ_TIMEOUT = 60000;
    private static final File CARE_MAP_PATH = new File("/data/ota_package/care_map.txt");
    static final File UPDATE_PATH = new File("/data/ota_package/update.zip");
    private static final String PREFERENCE_CHANNEL = "channel";
    private static final String PREFERENCE_DOWNLOAD_FILE = "download_file";
    private static final int HTTP_RANGE_NOT_SATISFIABLE = 416;

    final String AICP_DEVICE = SystemProperties.get("ro.aicp.device");

    final String MOD_VERSION = SystemProperties.get("ro.aicp.version.update", "unknown");

    private static final int STATUS_NONE = 0;
    private static final int STATUS_UPDATING = 1;
    private static final int STATUS_ABORT_PENDING = 2;
    private static final int STATUS_UPDATE_PENDING = 3;
    private static AtomicInteger mUpdatingStatus = new AtomicInteger(STATUS_NONE);
    private static long mDownloaded = 0;
    private static volatile int mUpdateInfo = INFO_NONE;

    public Service() {
        super(TAG);
    }

    static boolean isAbUpdate() {
        return SystemProperties.getBoolean("ro.build.ab_update", false);
    }

    private URLConnection fetchData(final String path) throws IOException {
        final URL url = new URL(getString(isAbUpdate() ? R.string.url : R.string.url_legacy) + path);
        final URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(CONNECT_TIMEOUT);
        urlConnection.setReadTimeout(READ_TIMEOUT);
        return urlConnection;
    }

    private URLConnection fetchROM(final String path) throws IOException {
        final URL url = new URL(getString(R.string.url_download) + path);
        final URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(CONNECT_TIMEOUT);
        urlConnection.setReadTimeout(READ_TIMEOUT);
        return urlConnection;
    }


    private void applyUpdate(final long payloadOffset, final String[] headerKeyValuePairs) {
        final CountDownLatch monitor = new CountDownLatch(1);
        final UpdateEngine engine = new UpdateEngine();
        engine.bind(new UpdateEngineCallback() {
            @Override
            public void onStatusUpdate(int status, float percent) {
                Log.d(TAG, "onStatusUpdate: " + status + ", " + percent * 100 + "%");
            }

            @Override
            public void onPayloadApplicationComplete(int errorCode) {
                if (errorCode == ErrorCodeConstants.SUCCESS) {
                    Log.d(TAG, "onPayloadApplicationComplete success");
                    annoyUser();
                } else {
                    Log.d(TAG, "onPayloadApplicationComplete: " + errorCode);
                    mUpdatingStatus.set(STATUS_NONE);
                }
                UPDATE_PATH.delete();
                monitor.countDown();
            }
        });
        if (SystemProperties.getBoolean("sys.update.streaming_test", false)) {
            Log.d(TAG, "streaming update test");
            final SharedPreferences preferences = Settings.getPreferences(this);
            final String downloadFile = preferences.getString(PREFERENCE_DOWNLOAD_FILE, null);
            engine.applyPayload(getString(R.string.url) + downloadFile, payloadOffset, 0, headerKeyValuePairs);
        } else {
            UPDATE_PATH.setReadable(true, false);
            engine.applyPayload("file://" + UPDATE_PATH, payloadOffset, 0, headerKeyValuePairs);
        }
        try {
            monitor.await();
        } catch (InterruptedException e) {}
    }

    private static ZipEntry getEntry(final ZipFile zipFile, final String name) throws GeneralSecurityException {
        final ZipEntry entry = zipFile.getEntry(name);
        if (entry == null) {
            throw new GeneralSecurityException("missing zip entry: " + name);
        }
        return entry;
    }

    private void onDownloadFinished(final long targetBuildDate, final String channel) throws IOException, GeneralSecurityException {
        try {
            RecoverySystem.verifyPackage(UPDATE_PATH,
                (int progress) -> Log.d(TAG, "verifyPackage: " + progress + "%"), null);

            final ZipFile zipFile = new ZipFile(UPDATE_PATH);

            final ZipEntry metadata = getEntry(zipFile, "META-INF/com/android/metadata");
            final BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(metadata)));
            String device = null;
            String serialno = null;
            String type = null;
            String sourceIncremental = null;
            String sourceFingerprint = null;
            String streamingPropertyFiles[] = null;
            long timestamp = 0;
            for (String line; (line = reader.readLine()) != null; ) {
                final String[] pair = line.split("=");
                if ("post-timestamp".equals(pair[0])) {
                    timestamp = Long.parseLong(pair[1]);
                } else if ("serialno".equals(pair[0])) {
                    serialno = pair[1];
                } else if ("pre-device".equals(pair[0])) {
                    device = pair[1];
                } else if ("ota-type".equals(pair[0])) {
                    type = pair[1];
                } else if ("ota-streaming-property-files".equals(pair[0])) {
                    streamingPropertyFiles = pair[1].trim().split(",");
                } else if ("pre-build-incremental".equals(pair[0])) {
                    sourceIncremental = pair[1];
                } else if ("pre-build".equals(pair[0])) {
                    sourceFingerprint = pair[1];
                }
            }
            if (timestamp != targetBuildDate) {
                throw new GeneralSecurityException("timestamp does not match server metadata");
            }
            if (!DEVICE.equals(device)) {
                throw new GeneralSecurityException("device mismatch, is \"" + device + "\" instead of \"" + DEVICE);
            }
            if (serialno != null) {
                if ("INCREMENTAL".equals(channel) || "WEEKLY".equals(channel)) {
                    throw new GeneralSecurityException("serialno constraint not permitted for channel " + channel);
                }
                if (!serialno.equals(Build.getSerial())) {
                    throw new GeneralSecurityException("serialno mismatch");
                }
            }
            if ("AB".equals(type) != isAbUpdate()) {
                throw new GeneralSecurityException("update type does not match device");
            }
            if (sourceIncremental != null && !sourceIncremental.equals(INCREMENTAL)) {
                throw new GeneralSecurityException("source incremental mismatch");
            }
            if (sourceFingerprint != null && !sourceFingerprint.equals(FINGERPRINT)) {
                throw new GeneralSecurityException("source fingerprint mismatch");
            }

            if (!isAbUpdate()) {
                annoyUser();
                return;
            }

            long payloadOffset = 0;
            for (final String streamingPropertyFile : streamingPropertyFiles) {
                final String properties[] = streamingPropertyFile.split(":");
                if ("payload.bin".equals(properties[0])) {
                    payloadOffset = Long.parseLong(properties[1]);
                }
            }

            Files.deleteIfExists(CARE_MAP_PATH.toPath());
            final ZipEntry careMapEntry = zipFile.getEntry("care_map.txt");
            if (careMapEntry == null) {
                Log.w(TAG, "care_map.txt missing");
            } else {
                Files.copy(zipFile.getInputStream(careMapEntry), CARE_MAP_PATH.toPath());
                CARE_MAP_PATH.setReadable(true, false);
            }

            final ZipEntry payloadProperties = getEntry(zipFile, "payload_properties.txt");
            final BufferedReader propertiesReader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(payloadProperties)));
            applyUpdate(payloadOffset, propertiesReader.lines().toArray(String[]::new));
        } catch (GeneralSecurityException e) {
            UPDATE_PATH.delete();
            throw e;
        }
    }

    private void annoyUser() {
        PeriodicJob.cancel(this);
        final SharedPreferences preferences = Settings.getPreferences(this);
        preferences.edit().putBoolean(Settings.KEY_WAITING_FOR_REBOOT, true).apply();
        if (preferences.getBoolean(Settings.KEY_IDLE_REBOOT, false)) {
            IdleReboot.schedule(this);
        }

        final String title = getString(isAbUpdate() ? R.string.notification_title : R.string.notification_title_legacy);
        final String text = getString(isAbUpdate() ? R.string.notification_text : R.string.notification_text_legacy);
        final String rebootText = getString(isAbUpdate() ? R.string.notification_reboot_action : R.string.notification_reboot_action_legacy);

        final PendingIntent reboot = PendingIntent.getBroadcast(this, PENDING_REBOOT_ID, new Intent(this, RebootReceiver.class), 0);
        final PendingIntent settings = PendingIntent.getActivity(this, PENDING_SETTINGS_ID, new Intent(this, Settings.class), 0);
        final NotificationManager notificationManager = getSystemService(NotificationManager.class);
        final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_UPDATE_PROMPT,
            getString(R.string.notification_channel_update_prompt), NotificationManager.IMPORTANCE_HIGH);
        channel.enableLights(true);
        channel.enableVibration(true);
        notificationManager.createNotificationChannel(channel);
        notificationManager.notify(NOTIFICATION_ID_PROMPT, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID_UPDATE_PROMPT)
            .addAction(R.drawable.ic_restart, rebootText, reboot)
            .setContentIntent(settings)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_system_update_white_24dp)
            .build());
    }
    private void promptDownload() {
        PeriodicJob.cancel(this);

        final String title = getString(R.string.notification_title_download);
        final String text = getString(R.string.notification_text_download);
        final String downloadText = getString(R.string.notification_download_action);

        final PendingIntent download = PendingIntent.getBroadcast(this, PENDING_DOWNLOAD_ID, new Intent(this, TriggerUpdateReceiver.class).setAction(ACTION_DOWNLOAD), 0);
        final PendingIntent settings = PendingIntent.getActivity(this, PENDING_SETTINGS_ID, new Intent(this, Settings.class), 0);

        final NotificationManager notificationManager = getSystemService(NotificationManager.class);
        final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_UPDATE_AVAILABLE,
            getString(R.string.notification_channel_update_available), NotificationManager.IMPORTANCE_DEFAULT);
        channel.enableLights(true);
        channel.enableVibration(false);
        notificationManager.createNotificationChannel(channel);
        notificationManager.notify(NOTIFICATION_ID_AVAILABLE, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID_UPDATE_AVAILABLE)
            .addAction(R.drawable.ic_download, downloadText, download)
            .setContentIntent(settings)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(false)
            .setSmallIcon(R.drawable.ic_system_update_white_24dp)
            .build());
    }
    private void cancelNotification(int id) {
        final NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.cancel(id);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        Log.d(TAG, "onHandleIntent: " + intent.getAction());

        if (ACTION_INFO.equals(intent.getAction())) {
            publishProgress();
            return;
        } else if (ACTION_ABORT.equals(intent.getAction())) {
            requestStop();
            cancelNotification(NOTIFICATION_ID_PROGRESS);
            return;
        }
        boolean startDownload = ACTION_DOWNLOAD.equals(intent.getAction());
        if (startDownload) {
            cancelNotification(NOTIFICATION_ID_AVAILABLE);
        }

        final PowerManager pm = getSystemService(PowerManager.class);
        final WakeLock wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        try {
            wakeLock.acquire();

            if (!mUpdatingStatus.compareAndSet(STATUS_UPDATE_PENDING, STATUS_UPDATING)) {
                Log.d(TAG, "updating not allowed, status is " + mUpdatingStatus.get() +
                        ", returning early");
                return;
            }
            final SharedPreferences preferences = Settings.getPreferences(this);
            if (preferences.getBoolean(Settings.KEY_WAITING_FOR_REBOOT, false)) {
                Log.d(TAG, "updated already, waiting for reboot");
                return;
            }

            final String channel = SystemProperties.get("sys.update.channel",
                preferences.getString(PREFERENCE_CHANNEL, "WEEKLY"));

            Log.d(TAG, "fetching metadata for " + AICP_DEVICE + " in " + channel + " with version: " + MOD_VERSION);
            InputStream input = fetchData(AICP_DEVICE + "&type=" + channel).getInputStream();
            final BufferedReader reader = new BufferedReader(new InputStreamReader(input));
            final String[] metadata;
            try {
                metadata = reader.readLine().split(" ");
            } finally {
                reader.close();
            }

            final String targetIncremental = metadata[0];
            final long targetBuildDate = Long.parseLong(metadata[1]);
            final long sourceBuildDate = SystemProperties.getLong("ro.build.date.utc", 0);
            if (targetBuildDate <= sourceBuildDate) {
                Log.d(TAG, "targetBuildDate: " + targetBuildDate + " not higher than sourceBuildDate: " + sourceBuildDate);
                if (!DEBUG_ALWAYS_UPDATE) {
                    mUpdateInfo = INFO_UP_TO_DATE;
                    publishProgress();
                    return;
                }
            }

            if (!startDownload && Settings.getAutoUpdatePromptDownloadRequired(this)) {
                mUpdateInfo = INFO_DOWNLOAD_PENDING;
                promptDownload();
                return;
            }

            String downloadFile = preferences.getString(PREFERENCE_DOWNLOAD_FILE, null);
            mDownloaded = UPDATE_PATH.length();
            mUpdateInfo = INFO_DOWNLOADING;
            publishProgress();

            final String incrementalUpdate = "aicp_" + AICP_DEVICE + "-incremental-" + INCREMENTAL + "-" + targetIncremental + ".zip";
            final String fullUpdate = "aicp_" + AICP_DEVICE + "_" + MOD_VERSION + "-" + channel + "-" + targetIncremental + ".zip";

            if (incrementalUpdate.equals(downloadFile) || fullUpdate.equals(downloadFile)) {
                Log.d(TAG, "resume fetch of " + downloadFile + " from " + mDownloaded + " bytes");
                final HttpURLConnection connection = (HttpURLConnection) fetchROM("device" + "/" + AICP_DEVICE + "/" +  channel + "/" + downloadFile);
                connection.setRequestProperty("Range", "bytes=" + mDownloaded + "-");
                if (connection.getResponseCode() == HTTP_RANGE_NOT_SATISFIABLE) {
                    Log.d(TAG, "download completed previously");
                    onDownloadFinished(targetBuildDate, channel);
                    return;
                }
                input = connection.getInputStream();
            } else {
                try {
                    Log.d(TAG, "fetch incremental " + incrementalUpdate);
                    downloadFile = incrementalUpdate;
                    input = fetchROM("device" + "/" + AICP_DEVICE + "/" +  channel + "/" + downloadFile).getInputStream();
                } catch (IOException e) {
                    Log.d(TAG, "incremental not found, fetch full update " + fullUpdate);
                    downloadFile = fullUpdate;
                    input = fetchROM("device" + "/" + AICP_DEVICE + "/" +  channel + "/" + downloadFile).getInputStream();
                }
                mDownloaded = 0;
                Files.deleteIfExists(UPDATE_PATH.toPath());
            }

            final OutputStream output = new FileOutputStream(UPDATE_PATH, mDownloaded != 0);
            preferences.edit().putString(PREFERENCE_DOWNLOAD_FILE, downloadFile).commit();

            int bytesRead;
            long last = System.nanoTime();
            final byte[] buffer = new byte[8192];
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                mDownloaded += bytesRead;
                final long now = System.nanoTime();
                if (now - last > 1000 * 1000 * 1000) {
                    Log.d(TAG, "downloaded " + mDownloaded + " bytes");
                    last = now;
                    publishProgress(downloadFile);
                }
                if (mUpdatingStatus.get() != STATUS_UPDATING) {
                    // status is i.e. STATUS_ABORT_PENDING
                    Log.d(TAG, "Update aborted");
                    return;
                }
            }
            output.close();
            input.close();

            Log.d(TAG, "download completed");
            onDownloadFinished(targetBuildDate, channel);
        } catch (Exception e) {
            Log.e(TAG, "failed to download and install update", e);
            PeriodicJob.scheduleRetry(this);
            mUpdateInfo = INFO_ERROR;
            publishProgress();
        } finally {
            publishProgress();
            mUpdatingStatus.set(STATUS_NONE);
            if (mUpdateInfo == INFO_DOWNLOADING) {
                mUpdateInfo = INFO_UPDATE_PENDING;
                publishProgress();
            }
            Log.d(TAG, "release wake locks");
            wakeLock.release();
            TriggerUpdateReceiver.completeWakefulIntent(intent);
        }
    }

    private void publishProgress() {
        publishProgress(null);
    }
    private void publishProgress(String downloadFile) {
        Intent update = new Intent(INTENT_UPDATE);
        update.putExtra(EXTRA_PROGRESS, mDownloaded);
        update.putExtra(EXTRA_INFO, mUpdateInfo);
        if (downloadFile != null) {
            update.putExtra(EXTRA_FILENAME, downloadFile);
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(update);
        final SharedPreferences preferences = Settings.getPreferences(this);
        final NotificationManager notificationManager = getSystemService(NotificationManager.class);
        final NotificationChannel channel = new NotificationChannel(NOTIFICATION_CHANNEL_ID_UPDATE_PROGRESS,
            getString(R.string.notification_channel_update_prompt), NotificationManager.IMPORTANCE_LOW);
        channel.enableLights(false);
        channel.enableVibration(false);

        if (mUpdateInfo == INFO_DOWNLOADING) {
            final String title = getString(R.string.notification_title_progress);
            final String text = getString(R.string.notification_text_progress, mDownloaded/1_000_000);
            final String abortText = getString(R.string.notification_abort_action);
            final PendingIntent abort = PendingIntent.getBroadcast(this, PENDING_ABORT_ID, new Intent(this, TriggerUpdateReceiver.class).setAction(ACTION_ABORT), 0);
            final PendingIntent settings = PendingIntent.getActivity(this, PENDING_SETTINGS_ID, new Intent(this, Settings.class), 0);
            notificationManager.createNotificationChannel(channel);
            notificationManager.notify(NOTIFICATION_ID_PROGRESS, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID_UPDATE_PROGRESS)
                .addAction(R.drawable.ic_abort, abortText, abort)
                .setContentIntent(settings)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(true)
                .setSmallIcon(R.drawable.ic_system_update_white_24dp)
                .build());
        } else if (mUpdateInfo == INFO_ERROR) {
            final String title = getString(R.string.notification_title_error);
            final String text = getString(R.string.notification_text_error);
            final PendingIntent settings = PendingIntent.getActivity(this, PENDING_SETTINGS_ID, new Intent(this, Settings.class), 0);
            notificationManager.createNotificationChannel(channel);
            notificationManager.notify(NOTIFICATION_ID_PROGRESS, new Notification.Builder(this, NOTIFICATION_CHANNEL_ID_UPDATE_PROGRESS)
                .setContentIntent(settings)
                .setContentTitle(title)
                .setContentText(text)
                .setOngoing(false)
                .setSmallIcon(R.drawable.ic_system_update_white_24dp)
                .build());
        } else {
            cancelNotification(NOTIFICATION_ID_PROGRESS);
        }
    }

    /**
     * Request download service to stop.
     * @return true if ongoing update should get cancelled, false if no update active
     */
    public static boolean requestStop() {
        return mUpdatingStatus.compareAndSet(STATUS_UPDATING, STATUS_ABORT_PENDING);
    }

    /**
     * Set service ready to start.
     * If not done, startService will be ignored. Call once to ensure only scheduling one action.
     * @return true if preparing service to start was successful, false if not allowed
     * (i.e. already active)
     */
    public static boolean allowStart() {
        return mUpdatingStatus.compareAndSet(STATUS_NONE, STATUS_UPDATE_PENDING);
    }
}
