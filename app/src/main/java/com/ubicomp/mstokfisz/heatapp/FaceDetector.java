package com.ubicomp.mstokfisz.heatapp;

import android.graphics.*;
import android.os.Build;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import com.flir.thermalsdk.image.Rectangle;
import com.flir.thermalsdk.image.ThermalImage;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.face.*;
import com.ubicomp.mstokfisz.heatapp.events.ImageReadyEvent;
import com.ubicomp.mstokfisz.heatapp.events.MeasurementReadyEvent;
import org.greenrobot.eventbus.EventBus;

import java.util.Arrays;
import java.util.List;

class FaceDetector {
    private static final FirebaseVisionFaceDetectorOptions options =
            new FirebaseVisionFaceDetectorOptions.Builder()
                    .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                    .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                    .setMinFaceSize(0.3f)
                    .build();
    private static final FirebaseVisionFaceDetector detector = FirebaseVision.getInstance()
            .getVisionFaceDetector(options);

    private static final String TAG = "FaceDetector";
    static Boolean isBusy = false;

    static void detectFaces(Bitmap bmpImage, Bitmap msxImage, double[] vals) {
        isBusy = true;
        // Detect in image
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(bmpImage);

        detector.detectInImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
            @RequiresApi(api = Build.VERSION_CODES.N)
            @Override
            public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                Log.d(TAG, "Faces detected: "+firebaseVisionFaces.size());
                Bitmap mutableBitmap = msxImage.copy(Bitmap.Config.ARGB_8888, true);
                Canvas canvas = new Canvas(mutableBitmap);
                Rect boundingRect = null;
                for (FirebaseVisionFace face : firebaseVisionFaces) {
                    FirebaseVisionFaceContour contour = face.getContour(FirebaseVisionFaceContour.FACE);
                    Path path = new Path();
                    path.moveTo(contour.getPoints().get(0).getX(), contour.getPoints().get(0).getY());
                    contour.getPoints().forEach(point -> {
                        path.lineTo(point.getX() , point.getY());
                    });
                    path.close();
                    Paint paint = new Paint();
                    paint.setColor(Color.RED);
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(8.0f);
                    boundingRect = face.getBoundingBox();
                    canvas.drawRect(boundingRect, paint);
                    paint.setColor(Color.GREEN);
                    canvas.drawPath(path, paint);
                }
                if (firebaseVisionFaces.size() != 0) {
//                    Rectangle flirRectangle = new Rectangle(boundingRect.left, boundingRect.top, boundingRect.width(), boundingRect.height());
//                    thermalImage.getMeasurements().addRectangle(flirRectangle.x, flirRectangle.y, flirRectangle.width, flirRectangle.height);
//                    Log.d(TAG, ""+thermalImage.getMeasurements().getRectangles().get(0).getAverage());
//                    thermalImage.getMeasurements().clear();
                    Bitmap res = HeatMapGenerator.generateHeatMap(vals, mutableBitmap.getWidth(), mutableBitmap.getHeight(), Arrays.stream(vals).min().getAsDouble(), Arrays.stream(vals).max().getAsDouble());
                    EventBus.getDefault().post(new ImageReadyEvent(res));
//                    Log.d(TAG, arr.toString());
//                    EventBus.getDefault().post(new MeasurementReadyEvent());
                } else {
                    EventBus.getDefault().post(new ImageReadyEvent(bmpImage));
                }
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
    }
}
