package com.ubicomp.mstokfisz.heatapp;

public class MeasurementDataHolder {
    final double[] data;
    final double minVal;
    final double maxVal;
    final int width;
    final int height;

    public MeasurementDataHolder(double[] data, double minVal, double maxVal, int width, int height) {
        this.data = data;
        this.minVal = minVal;
        this.maxVal = maxVal;
        this.width = width;
        this.height = height;
    }
}
