package com.ubicomp.mstokfisz.heatapp;

import android.graphics.Bitmap;

public class ImageReadyEvent {
    public final Bitmap img;

    public ImageReadyEvent(Bitmap bitmap) {
        this.img = bitmap;
    }
}
