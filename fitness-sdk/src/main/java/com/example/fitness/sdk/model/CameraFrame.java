package com.example.fitness.sdk.model;

import android.graphics.Bitmap;

/**
 * 相机帧数据
 */
public class CameraFrame {
    private final Bitmap bitmap;      // 相机帧图像
    private final long timestampMs;   // 时间戳（毫秒）
    private final int width;          // 图像宽度
    private final int height;         // 图像高度
    private final int rotation;       // 旋转角度

    public CameraFrame(Bitmap bitmap, long timestampMs) {
        this.bitmap = bitmap;
        this.timestampMs = timestampMs;
        this.width = bitmap != null ? bitmap.getWidth() : 0;
        this.height = bitmap != null ? bitmap.getHeight() : 0;
        this.rotation = 0;
    }

    public CameraFrame(Bitmap bitmap, long timestampMs, int rotation) {
        this.bitmap = bitmap;
        this.timestampMs = timestampMs;
        this.width = bitmap != null ? bitmap.getWidth() : 0;
        this.height = bitmap != null ? bitmap.getHeight() : 0;
        this.rotation = rotation;
    }

    public Bitmap getBitmap() { return bitmap; }
    public long getTimestampMs() { return timestampMs; }
    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public int getRotation() { return rotation; }

    public boolean isValid() {
        return bitmap != null && !bitmap.isRecycled();
    }

    public void recycle() {
        if (bitmap != null && !bitmap.isRecycled()) {
            bitmap.recycle();
        }
    }
}