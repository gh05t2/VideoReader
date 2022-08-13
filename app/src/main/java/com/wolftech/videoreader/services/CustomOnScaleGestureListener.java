package com.wolftech.videoreader.services;

import android.view.ScaleGestureDetector;

import androidx.annotation.NonNull;

public class CustomOnScaleGestureListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {
    private PinchListener pinchListener;

    public CustomOnScaleGestureListener(PinchListener pinchListener){
        this.pinchListener=pinchListener;
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector){
        float scaleFactor = detector.getScaleFactor();
        if (scaleFactor > 1)
            pinchListener.onZoomOut();
        else
            pinchListener.onZoomIn();

        return true;
    }

    @Override
    public boolean onScaleBegin(@NonNull ScaleGestureDetector detector) { return true; }

    @Override
    public void onScaleEnd(@NonNull ScaleGestureDetector detector) { }
}
