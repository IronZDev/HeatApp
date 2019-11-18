package com.ubicomp.mstokfisz.heatapp.events;

import android.graphics.Rect;
import com.flir.thermalsdk.image.ThermalImage;

public class MeasurementReadyEvent {
    public final Rect roiRect;
    public final ThermalImage thermalImage;

    public MeasurementReadyEvent(Rect roiRect, ThermalImage thermalImage) {
        this.roiRect = roiRect;
        this.thermalImage = thermalImage;
    }
}
