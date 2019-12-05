package com.ubicomp.mstokfisz.heatapp;

import java.util.ArrayList;

public class MeasurementDataHolder {
    final double[] data;
    final double minVal;
    final double maxVal;
    final int width;
    final int height;
    final ArrayList<Integer> pointsList;

    public MeasurementDataHolder(double[] data, double minVal, double maxVal, int width, int height, ArrayList<Integer> pointsList) {
        this.data = data;
        this.minVal = minVal;
        this.maxVal = maxVal;
        this.width = width;
        this.height = height;
        this.pointsList = pointsList;
    }
}
