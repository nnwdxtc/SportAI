package com.example.fitness.sdk.config;

/**
 * 摄像头配置
 */
public class CameraConfig {
    private final int lensFacing;      // CAMERA_FACING_BACK 或 CAMERA_FACING_FRONT
    private final int targetWidth;     // 目标宽度
    private final int targetHeight;    // 目标高度
    private final boolean enableUsbCamera;  // 是否启用USB摄像头

    private CameraConfig(Builder builder) {
        this.lensFacing = builder.lensFacing;
        this.targetWidth = builder.targetWidth;
        this.targetHeight = builder.targetHeight;
        this.enableUsbCamera = builder.enableUsbCamera;
    }

    // Getters
    public int getLensFacing() { return lensFacing; }
    public int getTargetWidth() { return targetWidth; }
    public int getTargetHeight() { return targetHeight; }
    public boolean isEnableUsbCamera() { return enableUsbCamera; }

    public static Builder builder() {
        return new Builder();
    }

    public static CameraConfig getDefault() {
        return builder().build();
    }

    public static class Builder {
        private int lensFacing = android.hardware.Camera.CameraInfo.CAMERA_FACING_BACK;
        private int targetWidth = 640;
        private int targetHeight = 480;
        private boolean enableUsbCamera = false;

        public Builder setLensFacing(int facing) { this.lensFacing = facing; return this; }
        public Builder setTargetSize(int width, int height) { this.targetWidth = width; this.targetHeight = height; return this; }
        public Builder setEnableUsbCamera(boolean enable) { this.enableUsbCamera = enable; return this; }

        public CameraConfig build() {
            return new CameraConfig(this);
        }
    }
}