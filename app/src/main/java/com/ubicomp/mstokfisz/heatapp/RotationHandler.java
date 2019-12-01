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
    static Bitmap zoomBitmap (Bitmap bmp, Double scale) {
        int widthOffset = (int) ((1 - scale)/2 * bmp.getWidth());
        int heightOffset = (int) ((1 - scale)/2 * bmp.getHeight());
        int numWidthPixels = bmp.getWidth() - 2 * widthOffset;
        int numHeightPixels = bmp.getHeight() - 2 * heightOffset;
        Bitmap rescaledBitmap = Bitmap.createBitmap(bmp, widthOffset, heightOffset, numWidthPixels, numHeightPixels, null, true);
        return rescaledBitmap;
    }
}
