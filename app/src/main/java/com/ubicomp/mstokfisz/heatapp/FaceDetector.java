package com.ubicomp.mstokfisz.heatapp;

import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.*;
import org.greenrobot.eventbus.EventBus;

import java.util.List;

class FaceDetector extends Thread {
    private static final String TAG = "FaceDetector";

    @Override
    public void run() {
        super.run();
    }

    static void detectFaces(Bitmap bmpImage) {
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bmpImage);
        // [START set_detector_options]
        FirebaseVisionFaceDetectorOptions options =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                        .build();
        // [END set_detector_options]

        // [START get_detector]
        FirebaseVisionFaceDetector detector = FirebaseVision.getInstance()
                .getVisionFaceDetector(options);
//        EventBus.getDefault().post(new ImageReadyEvent(bmpImage));
        // [END get_detector]

        // [START run_detector]
        detector.detectInImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
            @Override
            public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                EventBus.getDefault().post(new ImageReadyEvent(bmpImage));
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                EventBus.getDefault().post(new ImageReadyEvent(bmpImage));
            }
        });;

        // [END run_detector]
    }
}
