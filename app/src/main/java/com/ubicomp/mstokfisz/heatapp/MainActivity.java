package com.ubicomp.mstokfisz.heatapp;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
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

import java.util.*;

public class MainActivity extends SensorPortraitActivity implements SensorPortraitActivity.OrientationChangeListener {

    private static final String TAG = "MainActivity";

    /**
     * Permissions that need to be explicitly requested from end user.
     */
    private static final String[] REQUIRED_SDK_PERMISSIONS = new String[] {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private static ConnectionStatus currentConnectionStatus;

    //Handles network camera operations
    private CameraHandler cameraHandler;

    private Identity connectedIdentity = null;

    private ImageView msxImage;

    private Button startMeasurementBtn;

    private Button calibrateBtn;

    private TempDifferenceCalculator tempDifferenceCalculator;

    private UsbPermissionHandler usbPermissionHandler = new UsbPermissionHandler();

    private static AlertDialog waitForCameraDialog;

    private MainActivity self;

    @Override
    public void onPortrait() {
        Log.d(TAG, "Orientation portrait");
//        msxImage.setRotation(0);
//        FaceDetector.isRotated = false;
        RotationHandler.isRotated = false;
    }

    @Override
    public void onReversePortrait() {
        Log.d(TAG, "Orientation reversed portrait");
//        msxImage.setRotation(180);
//        FaceDetector.isRotated = true;
        RotationHandler.isRotated = true;
    }

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
        self = this;
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

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onStop() {

        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Identity flir = cameraHandler.getFlirOne();
        if (flir != null) {
            connect(flir);
        } else {
            startDiscovery();
        }
        EventBus.getDefault().register(this);
        setOrientationChangeListener(this);
    }

    @Override
    protected void onPause() {
        setOrientationChangeListener(null);
        EventBus.getDefault().unregister(this);
        stopDiscovery(); // Also stops stream
        disconnect();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (tempDifferenceCalculator != null && TempDifferenceCalculator.running) { // Not working
            TempDifferenceCalculator.running = false;
        }
        super.onDestroy();
    }

    /**
     * Connect to a Camera
     */
    private void connect(Identity identity) {
        //We don't have to stop a discovery but it's nice to do if we have found the camera that we are looking for
        if (identity == null) {
            Log.d(TAG, "connect(), can't connect, no camera available"); // Add retry
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
            Log.d(TAG, "Discovery started");
            AlertDialog.Builder builder = new AlertDialog.Builder(self);
            builder.setCancelable(false); // if you want user to wait for some process to finish,
            builder.setView(R.layout.wait_for_camera_dialog);
            waitForCameraDialog = builder.create(); // Show a dialog if not finished when stopped;
            waitForCameraDialog.show();
        }

        @Override
        public void stopped() {
            Log.d(TAG, "Discovery stopped");
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
                    case CONNECTING:
                        break;
                    case CONNECTED: {
                        stopDiscovery();
                        FaceDetector.resetFaceDetector();
                        waitForCameraDialog.dismiss();
                        cameraHandler.startStream(streamDataListener);
                    }
                    break;
                    case DISCONNECTING: break;
                    case DISCONNECTED:
                        FaceDetector.resetFaceDetector();
                        break;
                }
            });
        }
    };

    private final CameraHandler.StreamDataListener streamDataListener = (msxBitmap, dcBitmap)
    -> runOnUiThread(() -> {
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
                    Log.d(TAG, "Status: "+currentConnectionStatus);
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
        startMeasurementBtn = findViewById(R.id.start_measurement);
        calibrateBtn = findViewById(R.id.calibrate);
    }

    public void changeMeasurementState(View view) {
        if (startMeasurementBtn.getText().toString().contains("Start")) {
            if (currentConnectionStatus == ConnectionStatus.CONNECTED) {
                startMeasurementBtn.setText(getResources().getString(R.string.stop_measurement_text));
                calibrateBtn.setEnabled(false);
                tempDifferenceCalculator = new TempDifferenceCalculator(this);
            }
        } else {
            startMeasurementBtn.setText(getResources().getString(R.string.start_measurement_text));
            calibrateBtn.setEnabled(true);
            tempDifferenceCalculator.stop();
            tempDifferenceCalculator = null;
        }
    }

    public void recalibrateBoundingBox(View view) {
//        cameraHandler.saveImages = true; // For saving camera screenshots
        FaceDetector.recalculateDistances = true;
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
