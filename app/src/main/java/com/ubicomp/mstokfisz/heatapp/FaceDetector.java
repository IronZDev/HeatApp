package com.ubicomp.mstokfisz.heatapp;

import android.graphics.*;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.ml.vision.FirebaseVision;
import com.google.firebase.ml.vision.common.FirebaseVisionImage;
import com.google.firebase.ml.vision.common.FirebaseVisionPoint;
import com.google.firebase.ml.vision.face.*;
import com.ubicomp.mstokfisz.heatapp.events.ImageReadyEvent;
import org.greenrobot.eventbus.EventBus;

import java.util.List;

import static com.ubicomp.mstokfisz.heatapp.RotationHandler.rotateBitmap;

class FaceDetector {
    static class DistanceContainer {
        private float distanceLeft;
        private float distanceRight;
        private float distanceUp;
        private float disanceDown;
    }
    static class Rectangle {
        private FirebaseVisionPoint leftUp;
        private FirebaseVisionPoint rightUp;
        private FirebaseVisionPoint rightDown;
        private FirebaseVisionPoint leftDown;
    }

    private static final FirebaseVisionFaceDetectorOptions options =
            new FirebaseVisionFaceDetectorOptions.Builder()
                    .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                    .setContourMode(FirebaseVisionFaceDetectorOptions.ALL_CONTOURS)
                    .setLandmarkMode(FirebaseVisionFaceDetectorOptions.ALL_LANDMARKS)
                    .setClassificationMode(FirebaseVisionFaceDetectorOptions.NO_CLASSIFICATIONS)
                    .setMinFaceSize(0.3f)
                    .build();
    private static final FirebaseVisionFaceDetector detector = FirebaseVision.getInstance()
            .getVisionFaceDetector(options);

    private static final String TAG = "FaceDetector";
    static Boolean isBusy = false;
    private static DistanceContainer distanceContainer;
    static Boolean recalculateDistances = false;

    static void detectFaces(Bitmap bmpImage, Bitmap msxImage, MeasurementDataHolder currentData) {
        isBusy = true;
        Bitmap imgToDetect;
        Bitmap imgToDraw;

        imgToDetect = rotateBitmap(msxImage);
        imgToDraw = rotateBitmap(msxImage);
        // Detect in image
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(imgToDetect);

        detector.detectInImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
            @Override
            public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                Log.d(TAG, "Faces detected: "+firebaseVisionFaces.size());
                Bitmap mutableBitmap = imgToDraw.copy(Bitmap.Config.ARGB_8888, true);
                Canvas canvas = new Canvas(mutableBitmap);
                for (FirebaseVisionFace face : firebaseVisionFaces) {
//                    RectF contourBounds = new RectF();
//                    FirebaseVisionFaceContour contour = face.getContour(FirebaseVisionFaceContour.FACE);
//                    Path path = new Path();
//                    path.moveTo(contour.getPoints().get(0).getX(), contour.getPoints().get(0).getY());
//                    contour.getPoints().forEach(point -> {
//                        path.lineTo(point.getX(), point.getY());
//                    });
//                    path.close();
//                    path.computeBounds(contourBounds, false);
                    FirebaseVisionFaceLandmark leftEye = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE);
                    FirebaseVisionFaceLandmark rightEye = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE);

                    Paint paint = new Paint();
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(8.0f);
//                    boundingRect = face.getBoundingBox();
//                    canvas.drawRect(contourBounds, paint);
                    paint.setColor(Color.GREEN);
                    if (leftEye != null && rightEye != null) {
                        canvas.drawPoint(leftEye.getPosition().getX(), leftEye.getPosition().getY(), paint);
                        canvas.drawPoint(rightEye.getPosition().getX(), rightEye.getPosition().getY(), paint);
                        if (distanceContainer == null || recalculateDistances) {
                            calculateDistances(leftEye.getPosition(), rightEye.getPosition(), face.getBoundingBox());
                            recalculateDistances = false;
                        }
                        FirebaseVisionPoint middlePoint = calculateMiddlePoint(leftEye.getPosition(), rightEye.getPosition());
                        double angle = -Math.atan2(leftEye.getPosition().getX()-rightEye.getPosition().getX(), leftEye.getPosition().getY() - rightEye.getPosition().getY());
                        Rectangle boundingBox = calculateBoundingBox(middlePoint);
                        boundingBox = rotateBoundingBox(boundingBox, (float) angle, middlePoint);
                        // Draw boundingBox
                        Path path = new Path();
                        path.moveTo(boundingBox.leftUp.getX(), boundingBox.leftUp.getY());
                        path.lineTo(boundingBox.rightUp.getX(), boundingBox.rightUp.getY());
                        path.lineTo(boundingBox.rightDown.getX(), boundingBox.rightDown.getY());
                        path.lineTo(boundingBox.leftDown.getX(), boundingBox.leftDown.getY());
                        path.close();
                        canvas.drawRect(face.getBoundingBox(), paint);
                        paint.setColor(Color.RED);
                        canvas.drawPath(path, paint);
                        canvas.drawPoint(middlePoint.getX(), middlePoint.getY(), paint);

                        EventBus.getDefault().post(new ImageReadyEvent(mutableBitmap));
                        isBusy = false;
                        return;
                    }
                }
                EventBus.getDefault().post(new ImageReadyEvent(imgToDraw));
                isBusy = false;
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.e(TAG, e.getMessage());
                EventBus.getDefault().post(new ImageReadyEvent(imgToDetect));
                isBusy = false;
            }
        });;
    }

    private static void calculateDistances(FirebaseVisionPoint leftEye, FirebaseVisionPoint rightEye, Rect boundingRect) {
        FirebaseVisionPoint betweenEyes = calculateMiddlePoint(leftEye, rightEye);
        DistanceContainer distContainer = new DistanceContainer();
        distContainer.disanceDown = boundingRect.bottom - betweenEyes.getY();
        distContainer.distanceUp  = boundingRect.top - betweenEyes.getY();
        distContainer.distanceRight = boundingRect.right - betweenEyes.getX();
        distContainer.distanceLeft = boundingRect.left - betweenEyes.getX();
        FaceDetector.distanceContainer = distContainer;
    }

    private static Float calculateMeanValue(float num1, float num2) {
        return (num1 + num2) / 2;
    }

    private static FirebaseVisionPoint calculateMiddlePoint (FirebaseVisionPoint leftEye, FirebaseVisionPoint rightEye) {
        return new FirebaseVisionPoint(calculateMeanValue(leftEye.getX(), rightEye.getX()), calculateMeanValue(leftEye.getY(), rightEye.getY()), 0f);
    }

    private static FirebaseVisionPoint rotatePoint(FirebaseVisionPoint rotationOrigin, FirebaseVisionPoint pointToRotate, float angle)
    {
        float s = (float)Math.sin(angle);
        float c = (float)Math.cos(angle);
        // translate point back to origin:
        float x = pointToRotate.getX() - rotationOrigin.getX();
        float y = pointToRotate.getY() - rotationOrigin.getY();

        // rotate point
        float newX = x * c - y * s;
        float newY = x * s + y * c;

        // translate point back:
        return new FirebaseVisionPoint(newX + rotationOrigin.getX(), newY + rotationOrigin.getY(), 0f);
    }

    private static Rectangle calculateBoundingBox (FirebaseVisionPoint middlePoint) {
        Rectangle rect = new Rectangle();
        float leftBound = middlePoint.getX() + distanceContainer.distanceLeft;
        float rightBound = middlePoint.getX() + distanceContainer.distanceRight;
        float upperBound = middlePoint.getY() + distanceContainer.distanceUp;
        float lowerBound = middlePoint.getY() + distanceContainer.disanceDown;
        rect.leftUp = new FirebaseVisionPoint(leftBound, upperBound, 0f);
        rect.leftDown = new FirebaseVisionPoint(leftBound, lowerBound, 0f);
        rect.rightDown = new FirebaseVisionPoint(rightBound, lowerBound, 0f);
        rect.rightUp = new FirebaseVisionPoint(rightBound, upperBound, 0f);
        return rect;
    }

    private static Rectangle rotateBoundingBox (Rectangle rect, float angle, FirebaseVisionPoint middlePoint) {
        Rectangle rotatedRect = new Rectangle();
        rotatedRect.rightUp = rotatePoint(middlePoint, rect.rightUp, angle);
        rotatedRect.rightDown = rotatePoint(middlePoint, rect.rightDown, angle);
        rotatedRect.leftDown = rotatePoint(middlePoint, rect.leftDown, angle);
        rotatedRect.leftUp = rotatePoint(middlePoint, rect.leftUp, angle);
        return rotatedRect;
    }
}
