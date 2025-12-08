package com.atakmap.android.meshtastic;

import com.atakmap.android.meshtastic.util.Constants;
import com.atakmap.android.meshtastic.util.FileTransferManager;
import java.util.concurrent.CompletableFuture;

import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.atakmap.android.maps.MapView;
import com.atakmap.android.missionpackage.api.SaveAndSendCallback;
import com.atakmap.android.missionpackage.file.MissionPackageManifest;
import com.atakmap.android.missionpackage.file.task.MissionPackageBaseTask;
import com.atakmap.coremap.filesystem.FileSystemUtils;
import com.atakmap.coremap.log.Log;
import org.meshtastic.proto.ConfigProtos;
import org.meshtastic.proto.LocalOnlyProtos;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.File;

public class MeshtasticCallback implements SaveAndSendCallback {
    private final static String TAG = "MeshtasticCallback";

    @Override
    public void onMissionPackageTaskComplete(MissionPackageBaseTask missionPackageBaseTask, boolean success) {

        Log.d(TAG, "onMissionPackageTaskComplete: " + success);

        MissionPackageManifest missionPackageManifest = missionPackageBaseTask.getManifest();

        File file = new File(missionPackageManifest.getPath());
        Log.d(TAG, file.getAbsolutePath());

        SharedPreferences prefs = new ProtectedSharedPreferences(PreferenceManager.getDefaultSharedPreferences(MapView.getMapView().getContext()));
        SharedPreferences.Editor editor = prefs.edit();

        if (FileSystemUtils.isFile(file)) {
            // check file size
            if (FileSystemUtils.getFileSize(file) > 1024 * 56) {
                Toast.makeText(MapView.getMapView().getContext(), "File is too large to send, 56KB Max", Toast.LENGTH_LONG).show();
                return;
            }

            Log.d(TAG, "File is small enough to send: " + FileSystemUtils.getFileSize(file));

            // Check if we're on Short_Turbo modem preset
            byte[] config = MeshtasticMapComponent.getConfig();
            if (config == null || config.length == 0) {
                Toast.makeText(MapView.getMapView().getContext(), "Cannot get radio config. Is Meshtastic connected?", Toast.LENGTH_LONG).show();
                return;
            }

            LocalOnlyProtos.LocalConfig c;
            try {
                c = LocalOnlyProtos.LocalConfig.parseFrom(config);
            } catch (InvalidProtocolBufferException e) {
                Log.e(TAG, "Failed to parse config", e);
                Toast.makeText(MapView.getMapView().getContext(), "Failed to read radio config", Toast.LENGTH_LONG).show();
                return;
            }

            ConfigProtos.Config.LoRaConfig lc = c.getLora();
            int currentModemPreset = lc.getModemPreset().getNumber();

            // Check if on Short_Turbo
            if (currentModemPreset != ConfigProtos.Config.LoRaConfig.ModemPreset.SHORT_TURBO_VALUE) {
                String presetName = lc.getModemPreset().name();
                Toast.makeText(MapView.getMapView().getContext(),
                        "File transfer requires Short_Turbo preset.\nCurrently on: " + presetName,
                        Toast.LENGTH_LONG).show();
                Log.w(TAG, "File transfer blocked - not on Short_Turbo. Current preset: " + presetName);
                return;
            }

            // We're on Short_Turbo, proceed with file transfer
            Log.d(TAG, "On Short_Turbo preset, proceeding with file transfer");

            // Block other transfers while file transfer is in progress
            editor.putBoolean(Constants.PREF_PLUGIN_CHUNKING, true);
            editor.apply();

            CompletableFuture.runAsync(() -> {
                // Start file transfer with proper tracking
                FileTransferManager transferManager = FileTransferManager.getInstance();
                CompletableFuture<Boolean> transferFuture = transferManager.startTransfer();

                if (MeshtasticMapComponent.sendFile(file)) {
                    Log.d(TAG, "File sending initiated");

                    // Wait for transfer completion with timeout
                    try {
                        boolean transferSuccess = transferFuture.get();
                        if (transferSuccess) {
                            Log.d(TAG, "File transfer completed successfully");
                        } else {
                            Log.w(TAG, "File transfer failed or timed out");
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error during file transfer", e);
                        transferManager.cancelTransfer();
                    }
                } else {
                    Log.d(TAG, "File send initiation failed");
                    transferManager.cancelTransfer();
                }

                // Clear chunking flag when done
                editor.putBoolean(Constants.PREF_PLUGIN_CHUNKING, false);
                editor.apply();
            }).exceptionally(ex -> {
                Log.e(TAG, "Error in file transfer operation", ex);
                editor.putBoolean(Constants.PREF_PLUGIN_CHUNKING, false);
                editor.apply();
                return null;
            });
        } else {
            Log.d(TAG, "Invalid file");
        }
    }
}
