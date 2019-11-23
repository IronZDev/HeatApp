package com.ubicomp.mstokfisz.heatapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.androidsdk.live.connectivity.UsbPermissionHandler;
import com.flir.thermalsdk.live.CameraType;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatus;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.log.ThermalLog;
import com.ubicomp.mstokfisz.heatapp.events.ImageReadyEvent;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends SensorPortraitActivity {

    private static final String TAG = "MainActivity";

    /**
     * Permissions that need to be explicitly requested from end user.
     */
    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    // WakeLock for keeping the screen always on
    protected PowerManager.WakeLock mWakeLock;

    private static ConnectionStatus currentConnectionStatus;

    //Handles network camera operations
    private CameraHandler cameraHandler;

    private Identity connectedIdentity = null;

    private ImageView msxImage;

    private Button startMeasurement;

    private TempDifferenceCalculator tempDifferenceCalculator;

    private UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();
    /**
     * Show message on the screen
     */
    public interface ShowMessage {
        void show(String message);
    }

    @Subscribe
    public void onImageReady(ImageReadyEvent event) {
        runOnUiThread(() -> {
            msxImage.setImageBitmap(event.img);
//                photoImage.setImageBitmap(event.img);
        });
    }

    @SuppressLint("InvalidWakeLockTag")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);

        ThermalLog.LogLevel enableLoggingInDebug = BuildConfig.DEBUG ? ThermalLog.LogLevel.DEBUG : ThermalLog.LogLevel.NONE;

        //ThermalSdkAndroid has to be initiated from a Activity with the Application Context to prevent leaking Context,
        // and before ANY using any ThermalSdkAndroid functions
        //ThermalLog will show log from the Thermal SDK in standards android log framework
        ThermalSdkAndroid.init(getApplicationContext(), enableLoggingInDebug);

        cameraHandler = new CameraHandler();

        setupViews();

        checkPermissions();

        // Keeps the screen on when the Activity is in the foreground
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    public void startDiscovery(View view) {
        startDiscovery();
    }

    public void stopDiscovery(View view) {
        stopDiscovery();
    }


    public void connectFlirOne(View view) {
        connect(cameraHandler.getFlirOne());
    }

    public void disconnect(View view) {
        disconnect();
    }

    @Override
    protected void onStart() {
        super.onStart();
        startDiscovery();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        EventBus.getDefault().unregister(this);
        stopDiscovery();
        disconnect();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    /**
     * Connect to a Camera
     */
    private void connect(Identity identity) {
        //We don't have to stop a discovery but it's nice to do if we have found the camera that we are looking for
        cameraHandler.stopDiscovery(discoveryStatusListener);

        if (connectedIdentity != null) {
            Log.d(TAG, "connect(), in *this* code sample we only support one camera connection at the time");
            showMessage.show("connect(), in *this* code sample we only support one camera connection at the time");
            return;
        }

        if (identity == null) {
            Log.d(TAG, "connect(), can't connect, no camera available");
            showMessage.show("connect(), can't connect, no camera available");
            return;
        }

        connectedIdentity = identity;

        //IF your using "USB_DEVICE_ATTACHED" and "usb-device vendor-id" in the Android Manifest
        // you don't need to request permission, see documentation for more information
        if (UsbPermissionHandler.isFlirOne(identity)) {
            usbPermissionHandler.requestFlirOnePermisson(identity, this, permissionListener);
        } else {
            cameraHandler.connect(identity, connectionStatusListener);
        }

    }

    private UsbPermissionHandler.UsbPermissionListener permissionListener = new UsbPermissionHandler.UsbPermissionListener() {
        @Override
        public void permissionGranted(@NotNull Identity identity) {
            cameraHandler.connect(identity, connectionStatusListener);
        }

        @Override
        public void permissionDenied(@NotNull Identity identity) {
            MainActivity.this.showMessage.show("Permission was denied for identity ");
        }

        @Override
        public void error(UsbPermissionHandler.UsbPermissionListener.ErrorType errorType, final Identity identity) {
            MainActivity.this.showMessage.show("Error when asking for permission for FLIR ONE, error:"+errorType+ " identity:" +identity);
        }
    };

    /**
     * Disconnect to a camera
     */
    private void disconnect() {
        connectedIdentity = null;
        Log.d(TAG, "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
        cameraHandler.disconnect();
    }

    /**
     * Start camera discovery
     */
    private void startDiscovery() {
        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
    }

    /**
     * Stop camera discovery
     */
    private void stopDiscovery() {
        cameraHandler.stopDiscovery(discoveryStatusListener);
    }

    /**
     * Callback for discovery status, using it to update UI
     */
    private CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
        @Override
        public void started() {

        }

        @Override
        public void stopped() {
        }
    };

    /**
     * Camera connecting state thermalImageStreamListener, keeps track of if the camera is connected or not
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private ConnectionStatusListener connectionStatusListener = new ConnectionStatusListener() {
        @Override
        public void onConnectionStatusChanged(@NotNull ConnectionStatus connectionStatus, @org.jetbrains.annotations.Nullable ErrorCode errorCode) {
            Log.d(TAG, "onConnectionStatusChanged connectionStatus:" + connectionStatus + " errorCode:" + errorCode);

            runOnUiThread(() -> {
                currentConnectionStatus = connectionStatus;
                switch (connectionStatus) {
                    case CONNECTING: break;
                    case CONNECTED: {
                        cameraHandler.startStream(streamDataListener);
                    }
                    break;
                    case DISCONNECTING: break;
                    case DISCONNECTED: break;
                }
            });
        }
    };

    private final CameraHandler.StreamDataListener streamDataListener = (msxBitmap, dcBitmap) -> runOnUiThread(() -> {
//                    msxImage.setImageBitmap(msxBitmap);
//                    photoImage.setImageBitmap(dcBitmap);
    });



    /**
     * Camera Discovery thermalImageStreamListener, is notified if a new camera was found during a active discovery phase
     * <p>
     * Note that callbacks are received on a non-ui thread so have to eg use {@link #runOnUiThread(Runnable)} to interact view UI components
     */
    private DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {
        @Override
        public void onCameraFound(Identity identity) {
            Log.d(TAG, "onCameraFound identity:" + identity);
            runOnUiThread(() -> {
                if (identity.cameraType == CameraType.FLIR_ONE) {
                    cameraHandler.add(identity);
                    if (currentConnectionStatus != ConnectionStatus.CONNECTING && currentConnectionStatus != ConnectionStatus.CONNECTED)
                        connect(cameraHandler.getFlirOne());
                }
            });
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
            Log.d(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);

            runOnUiThread(() -> {
                stopDiscovery();
                MainActivity.this.showMessage.show("onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
            });
        }
    };

    private ShowMessage showMessage = message -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();


    private void setupViews() {
        msxImage = findViewById(R.id.msx_image);
        startMeasurement = findViewById(R.id.start_measurement);
    }

    public void changeMeasurementState(View view) {
        if (startMeasurement.getText().toString().contains("Start")) {
            if (currentConnectionStatus == ConnectionStatus.CONNECTED) {
                startMeasurement.setText(getResources().getString(R.string.stop_measurement_text));
                tempDifferenceCalculator = new TempDifferenceCalculator(this);
            }
        } else {
            startMeasurement.setText(getResources().getString(R.string.start_measurement_text));
            tempDifferenceCalculator.stop();
//            tempDifferenceCalculator = null;
        }
    }

    /**
     * Checks the dynamically-controlled permissions and requests missing permissions from end user.
     */
    private void checkPermissions() {
        final List<String> missingPermissions = new ArrayList<>();
        // check all required dynamic permissions
        for (final String permission : REQUIRED_SDK_PERMISSIONS) {
            final int result = ContextCompat.checkSelfPermission(this, permission);
            if (result != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(permission);
            }
        }
        if (!missingPermissions.isEmpty()) {
            // request all missing permissions
            final String[] permissions = missingPermissions
                    .toArray(new String[0]);
            ActivityCompat.requestPermissions(this, permissions, 1);
        } else {
            final int[] grantResults = new int[REQUIRED_SDK_PERMISSIONS.length];
            Arrays.fill(grantResults, PackageManager.PERMISSION_GRANTED);
            onRequestPermissionsResult(1, REQUIRED_SDK_PERMISSIONS,
                    grantResults);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == 1) {
            for (int index = permissions.length - 1; index >= 0; --index) {
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    // exit the app if one permission is not granted
                    Toast.makeText(this, "Required permission '" + permissions[index]
                            + "' not granted, exiting", Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
            }
        }
    }
}
