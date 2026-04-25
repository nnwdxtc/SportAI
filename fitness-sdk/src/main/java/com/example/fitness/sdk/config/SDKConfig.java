package com.example.fitness.sdk.config;

public class SDKConfig {
    private final float minDetectionConfidence;
    private final float minTrackingConfidence;
    private final int callbackFps;
    private final float angleHysteresis;
    private final long actionTimeoutMs;
    private final boolean enableAngleSmoothing;
    private final int smoothingWindowSize;

    private SDKConfig(Builder builder) {
        this.minDetectionConfidence = builder.minDetectionConfidence;
        this.minTrackingConfidence = builder.minTrackingConfidence;
        this.callbackFps = builder.callbackFps;
        this.angleHysteresis = builder.angleHysteresis;
        this.actionTimeoutMs = builder.actionTimeoutMs;
        this.enableAngleSmoothing = builder.enableAngleSmoothing;
        this.smoothingWindowSize = builder.smoothingWindowSize;
    }

    public float getMinDetectionConfidence() { return minDetectionConfidence; }
    public float getMinTrackingConfidence() { return minTrackingConfidence; }
    public int getCallbackFps() { return callbackFps; }
    public float getAngleHysteresis() { return angleHysteresis; }
    public long getActionTimeoutMs() { return actionTimeoutMs; }
    public boolean isEnableAngleSmoothing() { return enableAngleSmoothing; }
    public int getSmoothingWindowSize() { return smoothingWindowSize; }

    public static Builder builder() {
        return new Builder();
    }

    public static SDKConfig getDefault() {
        return builder().build();
    }

    public static class Builder {
        private float minDetectionConfidence = 0.5f;
        private float minTrackingConfidence = 0.5f;
        private int callbackFps = 15;
        private float angleHysteresis = 10f;
        private long actionTimeoutMs = 30000;
        private boolean enableAngleSmoothing = true;
        private int smoothingWindowSize = 3;

        public Builder setMinDetectionConfidence(float val) { this.minDetectionConfidence = val; return this; }
        public Builder setMinTrackingConfidence(float val) { this.minTrackingConfidence = val; return this; }
        public Builder setCallbackFps(int fps) { this.callbackFps = Math.min(fps, 30); return this; }
        public Builder setAngleHysteresis(float val) { this.angleHysteresis = val; return this; }
        public Builder setActionTimeoutMs(long ms) { this.actionTimeoutMs = ms; return this; }
        public Builder setEnableAngleSmoothing(boolean enable) { this.enableAngleSmoothing = enable; return this; }
        public Builder setSmoothingWindowSize(int size) { this.smoothingWindowSize = size; return this; }

        public SDKConfig build() {
            return new SDKConfig(this);
        }
    }
}