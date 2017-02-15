package co.copperhead.updater;

import android.app.IntentService;
import android.content.Intent;
import android.os.SystemProperties;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import android.os.UpdateEngine;
import android.os.UpdateEngine.ErrorCodeConstants;
import android.os.UpdateEngineCallback;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import co.copperhead.updater.Receiver;

public class Service extends IntentService {
    private static final String TAG = "Service";
    private static final int CONNECT_TIMEOUT = 60000;
    private static final int READ_TIMEOUT = 60000;
    private static final File UPDATE_PATH = new File("/data/ota_package/update.zip");

    public Service() {
        super(TAG);
    }

    private InputStream fetchData(String path) throws IOException {
        URL url = new URL(getString(R.string.url) + path);
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(CONNECT_TIMEOUT);
        urlConnection.setReadTimeout(READ_TIMEOUT);
        return urlConnection.getInputStream();
    }

    static private void onDownloadFinished() throws IOException {
        try {
            android.os.RecoverySystem.verifyPackage(UPDATE_PATH, null, null);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }

        List<String> lines = new ArrayList<String>();
        long payloadOffset = 0;

        ZipFile zipFile = new ZipFile(UPDATE_PATH);
        Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();
        long offset = 0;
        while (zipEntries.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) zipEntries.nextElement();
            long fileSize = 0;
            long extra = entry.getExtra() == null ? 0 : entry.getExtra().length;
            final long zipHeaderLength = 30;
            offset += zipHeaderLength + entry.getName().length() + extra;
            if (!entry.isDirectory()) {
                fileSize = entry.getCompressedSize();
                if ("payload.bin".equals(entry.getName())) {
                    payloadOffset = offset;
                } else if ("payload_properties.txt".equals(entry.getName())) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(zipFile.getInputStream(entry)));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        lines.add(line);
                    }
                }
            }
            offset += fileSize;
        }

        UpdateEngine engine = new UpdateEngine();
        engine.bind(new UpdateEngineCallback() {
            @Override
            public void onStatusUpdate(int status, float percent) {
                Log.v(TAG, "onStatusUpdate: " + status + ", " + percent);
            }

            @Override
            public void onPayloadApplicationComplete(int errorCode) {
                if (errorCode == ErrorCodeConstants.SUCCESS) {
                    Log.v(TAG, "onPayloadApplicationComplete success");
                } else {
                    Log.v(TAG, "onPayloadApplicationComplete: " + errorCode);
                }
                UPDATE_PATH.delete();
            }
        });

        engine.applyPayload("file://" + UPDATE_PATH, payloadOffset, 0, lines.toArray(new String[lines.size()]));
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        Log.d(TAG, "onHandleIntent");
        String device = SystemProperties.get("ro.product.device");
        try {
            InputStream input = fetchData(device);
            BufferedReader reader = new BufferedReader(new InputStreamReader(input, "UTF-8"));

            String[] metadata = reader.readLine().split(" ");
            reader.close();

            String version = metadata[0];
            long buildDate = Long.parseLong(metadata[1]);
            long installedBuildDate = SystemProperties.getLong("ro.build.date.utc", 0);
            if (buildDate <= installedBuildDate) {
                Log.v(TAG, "buildDate: " + buildDate + " lower than installedBuildDate: " + installedBuildDate);
                return;
            }

            String installedIncremental = SystemProperties.get("ro.build.version.incremental");

            if (UPDATE_PATH.exists()) {
                UPDATE_PATH.delete();
            }
            OutputStream output = new FileOutputStream(UPDATE_PATH);

            input = fetchData(device + "-ota_update-" + version + ".zip");
            int n;
            byte[] buffer = new byte[8192];
            while ((n = input.read(buffer)) != -1) {
                output.write(buffer, 0, n);
            }
            output.close();
            input.close();

            UPDATE_PATH.setReadable(true, false);

            onDownloadFinished();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            Receiver.completeWakefulIntent(intent);
        }
    }
}
