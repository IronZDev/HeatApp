package com.ubicomp.mstokfisz.heatapp.events;

import com.ubicomp.mstokfisz.heatapp.MeasurementDataHolder;

public class MeasurementReadyEvent {
    public final MeasurementDataHolder measurementDataHolder;

    public MeasurementReadyEvent(MeasurementDataHolder measurementDataHolder) {
        this.measurementDataHolder = measurementDataHolder;
    }
}
