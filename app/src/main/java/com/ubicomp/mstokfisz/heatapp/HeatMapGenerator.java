package com.ubicomp.mstokfisz.heatapp;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

final class HeatMapGenerator {
    private final static String TAG = "HatMapGenerator";

    static Bitmap generateHeatMap(MeasurementDataHolder measurement) {
        Log.d(TAG, "Height: "+measurement.height + "Width: "+measurement.width);
        Bitmap bmp = Bitmap.createBitmap(measurement.width, measurement.height, Bitmap.Config.ARGB_8888);
        for (int pixelNumber = 0; pixelNumber < measurement.data.length; pixelNumber++) {
            int calculatedValue = mapValueToColor(measurement.data[pixelNumber], measurement.minVal, measurement.maxVal);
            bmp.setPixel(calculateX(pixelNumber, measurement.width), calculateY(pixelNumber, measurement.width), Color.rgb(calculatedValue, calculatedValue, calculatedValue));
        }
        return bmp;
    }

    private static int mapValueToColor(double val, double minVal, double maxVal) {
        return (int)Math.round(((val - minVal) * 255) / (maxVal-minVal));
    }

    private static int calculateX(int num, int width) {
        return num - ((num / width) * width);
    }

    private static int calculateY(int num, int width) {
        return num /  width;
    }
}
