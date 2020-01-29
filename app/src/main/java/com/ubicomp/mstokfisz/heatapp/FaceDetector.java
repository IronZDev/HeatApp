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
import com.ubicomp.mstokfisz.heatapp.events.MeasurementReadyEvent;
import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.ubicomp.mstokfisz.heatapp.RotationHandler.rotateBitmap;

class FaceDetector {
    static class DistanceContainer {
        private float distanceLeft;
        private float distanceRight;
        private float distanceUp;
        private float distanceDown;
    }
    static class Rectangle {
        private FirebaseVisionPoint leftUp;
        private FirebaseVisionPoint rightUp;
        private FirebaseVisionPoint rightDown;
        private FirebaseVisionPoint leftDown;

        int getHeight () {
            return Math.abs(Math.round(leftDown.getY() - leftUp.getY()));
        }

        int getWidth () {
            return Math.abs(Math.round(rightDown.getX() - leftDown.getX()));
        }
    }

    static class FirstMeasurement {
        private final float angle;
        private final FirebaseVisionPoint middlePoint;
        private final ArrayList<Integer> pointsList;
        private final int width;
        private final int height;

        public FirstMeasurement(float angle, FirebaseVisionPoint middlePoint, ArrayList<Integer> pointsList, int width, int height) {
            this.angle = angle;
            this.middlePoint = middlePoint;
            this.pointsList = pointsList;
            this.width = width;
            this.height = height;
        }
    }

    private static final FirebaseVisionFaceDetectorOptions options =
            new FirebaseVisionFaceDetectorOptions.Builder()
                    .setPerformanceMode(FirebaseVisionFaceDetectorOptions.FAST)
                    .setContourMode(FirebaseVisionFaceDetectorOptions.NO_CONTOURS)
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
    private static FirstMeasurement firstMeasurement = null;

    static void detectFaces(Bitmap bmpImage, Bitmap msxImage, double[] vals) {
        isBusy = true;
        Bitmap imgToDetect;
        Bitmap imgToDraw;

        imgToDetect = rotateBitmap(msxImage);
        imgToDraw = rotateBitmap(msxImage);
        if (RotationHandler.isRotated) { // Reverse array to matched rotated image
            reverseArray(vals);
        }
        // Detect in image
        FirebaseVisionImage image = FirebaseVisionImage.fromBitmap(imgToDetect);

        detector.detectInImage(image).addOnSuccessListener(new OnSuccessListener<List<FirebaseVisionFace>>() {
            @Override
            public void onSuccess(List<FirebaseVisionFace> firebaseVisionFaces) {
                Log.d(TAG, "Faces detected: "+firebaseVisionFaces.size());
                Bitmap mutableBitmap = imgToDraw.copy(Bitmap.Config.ARGB_8888, true);
                Canvas canvas = new Canvas(mutableBitmap);
                for (FirebaseVisionFace face : firebaseVisionFaces) {
                    FirebaseVisionFaceLandmark leftEye = face.getLandmark(FirebaseVisionFaceLandmark.LEFT_EYE);
                    FirebaseVisionFaceLandmark rightEye = face.getLandmark(FirebaseVisionFaceLandmark.RIGHT_EYE);

                    Paint paint = new Paint();
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setStrokeWidth(8.0f);
                    paint.setColor(Color.GREEN);
                    if (leftEye != null && rightEye != null) {
                        canvas.drawPoint(leftEye.getPosition().getX(), leftEye.getPosition().getY(), paint);
                        canvas.drawPoint(rightEye.getPosition().getX(), rightEye.getPosition().getY(), paint);
                        FirebaseVisionPoint middlePoint = calculateMiddlePoint(leftEye.getPosition(), rightEye.getPosition());
                        if (distanceContainer == null || recalculateDistances) {
                            calculateDistances(middlePoint, face.getBoundingBox());
                            recalculateDistances = false;
                        }
                        // Calculate angle of head rotation
                        float angle = (float) Math.atan2(rightEye.getPosition().getY() - leftEye.getPosition().getY(), rightEye.getPosition().getX()-leftEye.getPosition().getX());
                        Rectangle boundingBox = calculateBoundingBox(middlePoint);
                        ArrayList<Integer> pointsToCalculate = null;
                        if (TempDifferenceCalculator.running) {
                            if (firstMeasurement != null) {
                                float angleToFirstMeasurement = angle - firstMeasurement.angle;
                                pointsToCalculate = getNewPointsInRect(angleToFirstMeasurement, middlePoint, imgToDetect.getWidth(), imgToDetect.getHeight());
                                boundingBox = rotateBoundingBox(boundingBox, angleToFirstMeasurement, middlePoint);
                            } else {
                                pointsToCalculate = getAllPointsInRect(boundingBox, imgToDetect.getWidth());
                                firstMeasurement = new FirstMeasurement(angle, middlePoint, pointsToCalculate, boundingBox.getWidth(), boundingBox.getHeight());
                            }
                            MeasurementDataHolder currentMeasurement = new MeasurementDataHolder(vals,  Arrays.stream(vals).min().getAsDouble(), Arrays.stream(vals).max().getAsDouble(), firstMeasurement.width, firstMeasurement.height, pointsToCalculate);
                            EventBus.getDefault().post(new MeasurementReadyEvent((currentMeasurement)));
//                            TempDifferenceCalculator.newMeasurement(currentMeasurement);
                        }
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
//                        // Draw points inside rect
//                        if (pointsToCalculate != null && firstMeasurement != null) {
//                            for (Integer point : pointsToCalculate) {
//                                canvas.drawPoint(get2DX(point, imgToDetect.getWidth()), get2DY(point, imgToDetect.getWidth()), paint);
//                            }
//                        }
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

    private static void calculateDistances(FirebaseVisionPoint middlePoint, Rect boundingRect) {
        DistanceContainer distContainer = new DistanceContainer();
        distContainer.distanceDown = boundingRect.bottom - middlePoint.getY();
        distContainer.distanceUp  = boundingRect.top - middlePoint.getY();
        distContainer.distanceRight = boundingRect.right - middlePoint.getX();
        distContainer.distanceLeft = boundingRect.left - middlePoint.getX();
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
        float lowerBound = middlePoint.getY() + distanceContainer.distanceDown;
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

    static void resetFaceDetector() {
        isBusy = false;
        distanceContainer = null;
        recalculateDistances = false;
        firstMeasurement = null;
    }

    // To do: Cancel measurement if boundary is not entirely in Bitmap
    static ArrayList<Integer> getAllPointsInRect(Rectangle rect, int width) {
        ArrayList<Integer> points = new ArrayList<>();
        for (int y = Math.round(rect.leftUp.getY()); y < Math.round(rect.leftUp.getY()) + rect.getHeight(); y++) {
            for (int x = Math.round(rect.leftDown.getX()); x < Math.round(rect.leftDown.getX()) + rect.getWidth(); x++) {
                points.add(get1D(x, y, width)); // Calculate pixel number
            }
        }
        return points;
    }

    static ArrayList<Integer> getNewPointsInRect(float newAngle, FirebaseVisionPoint newMiddlePoint, int bitmapWidth, int bitmapHeight) {
        ArrayList<Integer> newPoints = new ArrayList<>();
        float yOffset = newMiddlePoint.getY() - firstMeasurement.middlePoint.getY();
        float xOffset = newMiddlePoint.getX() - firstMeasurement.middlePoint.getX();
        for (int point : firstMeasurement.pointsList) {
            FirebaseVisionPoint translatedPoint = new FirebaseVisionPoint(get2DX(point, bitmapWidth) + xOffset, get2DY(point, bitmapWidth) + yOffset, 0f);
            FirebaseVisionPoint rotatedPoint = rotatePoint(newMiddlePoint, translatedPoint, newAngle);
            if (checkIfPointInBitmap(rotatedPoint, bitmapWidth, bitmapHeight)) {
                newPoints.add(get1D(Math.round(rotatedPoint.getX()), Math.round(rotatedPoint.getY()), bitmapWidth));
            } else {
                newPoints.add(null); // Null if out of bounds
            }
        }
        return newPoints;
    }

    private static Boolean checkIfPointInBitmap (FirebaseVisionPoint point, int bitmapWidth, int bitmapHeight){
        int roundedX = Math.round(point.getX());
        int roundedY = Math.round(point.getY());
        return roundedX >= 0 && roundedY >= 0 && roundedY <= bitmapHeight - 1 && roundedX <= bitmapWidth - 1;
    }

    private static int get2DX(int num, int width) {
        return num - ((num / width) * width);
    }

    private static int get2DY(int num, int width) {
        return num /  width;
    }

    private static int get1D(int x, int y, int width) {
        return y * width + x;
    }

    private static void reverseArray(double[] arr) {
        for(int i = 0; i < arr.length / 2; i++)
        {
            double temp = arr[i];
            arr[i] = arr[arr.length - i - 1];
            arr[arr.length - i - 1] = temp;
        }
    }
}
