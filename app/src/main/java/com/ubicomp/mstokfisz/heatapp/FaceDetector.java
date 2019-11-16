package com.ubicomp.mstokfisz.heatapp;

import android.graphics.*;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.vision.face.Contour;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.*;
import org.greenrobot.eventbus.EventBus;

import java.util.List;

class FaceDetector {
    private static final String TAG = "FaceDetector";
    static Boolean isBusy = false;

    static void detectFaces(Bitmap bmpImage, Bitmap thermalImage) {
        isBusy = true;
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bmpImage);
        // [START set_detector_options]
        FirebaseVisionFaceDetectorOptions options =
                new FirebaseVisionFaceDetectorOptions.Builder()
                        .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                        .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                        .build();
        // [END set_detector_options]

        // [START get_detector]
        FirebaseVisionFaceDetector detector = FirebaseVision.getInstance()
                .getVisionFaceDetector(options);
//        EventBus.getDefault().post(new ImageReadyEvent(bmpImage));
        // [END get_detector]

        // [START run_detector]
        detector.detectInImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                Log.d(TAG, "Faces detected: "+firebaseVisionFaces.size());
                Paint paint = new Paint();
                paint.setColor(Color.GREEN);
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(5);

                Bitmap mutableBitmap = thermalImage.copy(Bitmap.Config.ARGB_8888, true);
                Canvas canvas = new Canvas(mutableBitmap);

                for (FirebaseVisionFace face : firebaseVisionFaces) {
                    FirebaseVisionFaceContour contour = face.getContour(FirebaseVisionFaceContour.FACE);
                    Path path = new Path();
                    path.moveTo(contour.getPoints().get(0).getX(), contour.getPoints().get(0).getY());
                    contour.getPoints().forEach(point -> {
                        path.lineTo(point.getX(), point.getY());
                    });
                    path.close();

                    Paint redPaint = new Paint();
                    redPaint.setColor(0XFFFF0000);
                    redPaint.setStyle(Paint.Style.STROKE);
                    redPaint.setStrokeWidth(8.0f);
                    canvas.drawPath(path, redPaint);
                }
                EventBus.getDefault().post(new ImageReadyEvent(mutableBitmap));
                isBusy = false;
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(TAG, e.getMessage());
                EventBus.getDefault().post(new ImageReadyEvent(bmpImage));
                isBusy = false;
            }
        });;
        // [END run_detector]
    }
}
