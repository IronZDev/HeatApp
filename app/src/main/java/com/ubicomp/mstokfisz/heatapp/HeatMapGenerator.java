package com.ubicomp.mstokfisz.heatapp;

import android.graphics.Bitmap;
import android.graphics.Color;
import com.google.android.gms.tasks.Task;

class HeatMapGenerator {
    static Bitmap generateHeatMap(double[] data, int width, int height, double minVal, double maxVal) {
        int[] mappedData = new int[data.length];
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        for (int i = 0; i < data.length; i++) {
            mappedData[i] = mapValueToColor(data[i], minVal, maxVal);
            bmp.setPixel(calculateX(i, width), calculateY(i, width), Color.rgb(mappedData[i], mappedData[i], mappedData[i]));
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
