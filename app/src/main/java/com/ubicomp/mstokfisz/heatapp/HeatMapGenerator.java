package com.ubicomp.mstokfisz.heatapp;

import android.graphics.Bitmap;
import android.graphics.Color;

class HeatMapGenerator {
    static Bitmap generateHeatMap(MeasurementDataHolder measurement) {
        int[] mappedData = new int[measurement.data.length];
        Bitmap bmp = Bitmap.createBitmap(measurement.width, measurement.height, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < measurement.data.length; i++) {
            mappedData[i] = mapValueToColor(measurement.data[i], measurement.minVal, measurement.maxVal);
            bmp.setPixel(calculateX(i, measurement.width), calculateY(i, measurement.width), Color.rgb(mappedData[i], mappedData[i], mappedData[i]));
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
