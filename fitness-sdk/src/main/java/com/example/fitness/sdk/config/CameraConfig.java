package com.example.fitness.sdk.config;

public class CameraConfig {
    public static final int CAMERA_FACING_BACK = 0;
    public static final int CAMERA_FACING_FRONT = 1;

    private final int lensFacing;
    private final int targetWidth;
    private final int targetHeight;
    private final boolean enableUsbCamera;

    private CameraConfig(Builder builder) {
        this.lensFacing = builder.lensFacing;
        this.targetWidth = builder.targetWidth;
        this.targetHeight = builder.targetHeight;
        this.enableUsbCamera = builder.enableUsbCamera;
    }

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
        private int lensFacing = CAMERA_FACING_BACK;
        private int targetWidth = 640;
        private int targetHeight = 480;
        private boolean enableUsbCamera = false;

        public Builder setLensFacing(int facing) {
            this.lensFacing = facing;
            return this;
        }
        public Builder setTargetSize(int width, int height) {
            this.targetWidth = width;
            this.targetHeight = height;
            return this;
        }
        public Builder setEnableUsbCamera(boolean enable) {
            this.enableUsbCamera = enable;
            return this;
        }
        public CameraConfig build() {
            return new CameraConfig(this);
        }
    }
}