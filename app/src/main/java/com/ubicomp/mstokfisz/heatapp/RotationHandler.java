package com.ubicomp.mstokfisz.heatapp;

import android.graphics.Bitmap;
import android.graphics.Matrix;

final class RotationHandler {
    static Boolean isRotated = false;
    static Bitmap rotateBitmap (Bitmap bmp) {
        if (isRotated) {
            Matrix matrix = new Matrix();
            matrix.setRotate(180);
            return Bitmap.createBitmap(bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), matrix, true);
        } else {
            return bmp;
        }
    }
}
