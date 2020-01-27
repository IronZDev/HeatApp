package com.ubicomp.mstokfisz.heatapp;

import android.graphics.Bitmap;
import android.icu.text.SimpleDateFormat;
import android.os.Environment;
import android.util.Log;
import com.flir.thermalsdk.androidsdk.image.BitmapAndroid;
import com.flir.thermalsdk.image.Rectangle;
import com.flir.thermalsdk.image.ThermalImage;
import com.flir.thermalsdk.image.fusion.FusionMode;
import com.flir.thermalsdk.live.Camera;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.live.discovery.DiscoveryFactory;
import com.flir.thermalsdk.live.streaming.ThermalImageStreamListener;
import com.ubicomp.mstokfisz.heatapp.events.ImageReadyEvent;
import com.ubicomp.mstokfisz.heatapp.events.MeasurementReadyEvent;
import org.greenrobot.eventbus.EventBus;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;

import static com.ubicomp.mstokfisz.heatapp.RotationHandler.rotateBitmap;

class CameraHandler {

    private static final String TAG = "CameraHandler";
    Boolean saveImages = false;
    Boolean isFaceTrackingOn = true;

    public interface StreamDataListener {
        void images(Bitmap msxBitmap, Bitmap dcBitmap);
    }

    // Discovered FLIR cameras
    LinkedList<Identity> foundCameraIdentities = new LinkedList<>();

    // A FLIR Camera
    private Camera camera;


    public interface DiscoveryStatus {
        void started();
        void stopped();
    }

    public CameraHandler() {
    }

    /**
     * Start discovery of USB and Emulators
     */
    public void startDiscovery(DiscoveryEventListener cameraDiscoveryListener, DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().scan(cameraDiscoveryListener, CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.started();
    }

    /**
     * Stop discovery of USB and Emulators
     */
    public void stopDiscovery(DiscoveryStatus discoveryStatus) {
        DiscoveryFactory.getInstance().stop(CommunicationInterface.EMULATOR, CommunicationInterface.USB);
        discoveryStatus.stopped();
    }

    public void connect(Identity identity, ConnectionStatusListener connectionStatusListener) {
        camera = new Camera();
        camera.connect(identity, connectionStatusListener);
    }

    public void disconnect() {
        if (camera == null) {
            return;
        }
        if (camera.isGrabbing()) {
            camera.unsubscribeAllStreams();
        }
        camera.disconnect();
    }

    /**
     * Start a stream of {@link ThermalImage}s images from a FLIR ONE or emulator
     */
    public void startStream(StreamDataListener listener) {
        camera.subscribeStream(thermalImageStreamListener);
    }

    /**
     * Stop a stream of {@link ThermalImage}s images from a FLIR ONE or emulator
     */
    public void stopStream(ThermalImageStreamListener listener) {
        camera.unsubscribeStream(listener);
    }

    /**
     * Add a found camera to the list of known cameras
     */
    public void add(Identity identity) {
        foundCameraIdentities.add(identity);
    }

    @Nullable
    public Identity get(int i) {
        return foundCameraIdentities.get(i);
    }

    /**
     * Get a read only list of all found cameras
     */
    @Nullable
    public List<Identity> getCameraList() {
        return Collections.unmodifiableList(foundCameraIdentities);
    }

    @Nullable
    public Identity getFlirOne() {
        for (Identity foundCameraIdentity : foundCameraIdentities) {
            boolean isFlirOneEmulator = foundCameraIdentity.deviceId.contains("EMULATED FLIR ONE");
            boolean isCppEmulator = foundCameraIdentity.deviceId.contains("C++ Emulator");
            if (!isFlirOneEmulator && !isCppEmulator) {
                return foundCameraIdentity;
            }
        }

        return null;
    }

    private void withImage(ThermalImageStreamListener listener, Camera.Consumer<ThermalImage> functionToRun) {
        camera.withImage(listener, functionToRun);
    }


    /**
     * Called whenever there is a new Thermal Image available, should be used in conjunction with {@link Camera.Consumer}
     */
    private final ThermalImageStreamListener thermalImageStreamListener = new ThermalImageStreamListener() {
        @Override
        public void onImageReceived() {
            //Will be called on a non-ui thread
            Log.d(TAG, "onImageReceived(), we got another ThermalImage");
            withImage(this, handleIncomingImage);
        }
    };

    /**
     * Function to process a Thermal Image and update UI
     */
    private final Camera.Consumer<ThermalImage> handleIncomingImage = new Camera.Consumer<ThermalImage>() {
        @Override
        public void accept(ThermalImage thermalImage) {
            Log.d(TAG, "accept() called with: thermalImage = [" + thermalImage.getDescription() + "]");
            //Will be called on a non-ui thread,
            // extract information on the background thread and send the specific information to the UI thread

            //Get a bitmap with only IR data
            Bitmap msxBitmap;
            {
                thermalImage.getFusion().setFusionMode(FusionMode.VISUAL_ONLY);
                msxBitmap = BitmapAndroid.createBitmap(thermalImage.getImage()).getBitMap();
            }

            Bitmap dcBitmap = BitmapAndroid.createBitmap(thermalImage.getFusion().getPhoto()).getBitMap();
//            dcBitmap = zoomBitmap(dcBitmap, 0.725);

//            Get a bitmap with the visual image CANT USE THAT MEMORY LEAK!!
//            Bitmap dcBitmap;
//            {
//                thermalImage.getFusion().setFusionMode(FusionMode.VISUAL_ONLY); // Has to be done that way to preserve ratio and zoom
//                dcBitmap = BitmapAndroid.createBitmap(thermalImage.getImage()).getBitMap();
//            }


//            Log.d(TAG, ""+ Arrays.toString(thermalImage.getValues(new Rectangle(0, 0, msxBitmap.getWidth(), msxBitmap.getHeight()))).length());
//            Log.d(TAG,"Expected: "+ msxBitmap.getHeight()*msxBitmap.getWidth());

//            thermalImage.getMeasurements().addSpot(1,1);
//            MeasurementSpot measurementSpot = thermalImage.getMeasurements().getSpots().get(0);
//            Log.d(TAG, measurementSpot.getPosition().x+" "+measurementSpot.getPosition().y+" "+measurementSpot.getValue().asCelsius());
//            thermalImage.getMeasurements().clear();

            // Generate face detection

            double[] vals = thermalImage.getValues(new Rectangle(0, 0, msxBitmap.getWidth(), msxBitmap.getHeight()));
//            MeasurementDataHolder currentMeasurement = new MeasurementDataHolder(vals,  Arrays.stream(vals).min().getAsDouble(), Arrays.stream(vals).max().getAsDouble(), msxBitmap.getWidth(), msxBitmap.getHeight());
            if (isFaceTrackingOn) {
                if (!FaceDetector.isBusy) {
                    FaceDetector.detectFaces(dcBitmap, msxBitmap, vals);
                }
            } else {
                EventBus.getDefault().post(new ImageReadyEvent(RotationHandler.rotateBitmap(msxBitmap)));
                if (TempDifferenceCalculator.running) {
                    EventBus.getDefault().post(new MeasurementReadyEvent(new MeasurementDataHolder(vals, Arrays.stream(vals).min().getAsDouble(), Arrays.stream(vals).max().getAsDouble(), msxBitmap.getWidth(), msxBitmap.getHeight(), null)));
                }
            }
            if (saveImages) {
                saveFiles(dcBitmap, msxBitmap, vals);
            }
//            Log.d(TAG,"adding images to cache");
//            streamDataListener.images(msxBitmap, msxBitmap);
//            streamDataListener.images(msxBitmap, dcBitmap);
        }
    };

    private void saveFiles(Bitmap dcBitmap, Bitmap msxBitmap, double[] rawTempVals) {
        saveImages = false;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd-HH-mm-ssZ", Locale.getDefault());
        String formatedDate = sdf.format(new Date());
        Bitmap rawBitmap = rotateBitmap(HeatMapGenerator.generateHeatMap(new MeasurementDataHolder(rawTempVals, Arrays.stream(rawTempVals).min().getAsDouble(), Arrays.stream(rawTempVals).max().getAsDouble(), msxBitmap.getWidth(), msxBitmap.getHeight(), null)));
        String rawFileName = "RAW-" + formatedDate + ".png";
        String msxFileName = "MSX-" + formatedDate + ".png";
        String dcFileName = "DC-" + formatedDate + ".png";
        File dir = new File(Environment.getExternalStorageDirectory() + File.separator + "HeatApp");
        if(!dir.exists())
            dir.mkdirs();
        File rawFile = new File(dir,rawFileName);
        File msxFile = new File(dir, msxFileName);
        File dcFile = new File(dir, dcFileName);
        FileOutputStream fOut;
        try {
            fOut = new FileOutputStream(rawFile);
            rawBitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut = new FileOutputStream(msxFile);
            msxBitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut = new FileOutputStream(dcFile);
            dcBitmap.compress(Bitmap.CompressFormat.PNG, 100, fOut);
            fOut.flush();
            fOut.close();
        } catch (IOException e) {
            Log.d(TAG, "File not saved");
            Log.e(TAG, Objects.requireNonNull(e.getMessage()));
        }

    }


}
