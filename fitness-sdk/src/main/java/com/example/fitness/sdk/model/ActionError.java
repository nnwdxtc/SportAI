package com.example.fitness.sdk.model;

import android.graphics.Bitmap;

public class ActionError {
    private final ErrorType errorType;
    private final int score;
    private final String message;
    private final float actualValue;
    private final float expectedValue;
    private final long timestampMs;
    private Bitmap errorFrame;
    private Bitmap correctFrameRef;

    private ActionError(Builder builder) {
        this.errorType = builder.errorType;
        this.score = builder.score;
        this.message = builder.message;
        this.actualValue = builder.actualValue;
        this.expectedValue = builder.expectedValue;
        this.timestampMs = builder.timestampMs;
        this.errorFrame = builder.errorFrame;
        this.correctFrameRef = builder.correctFrameRef;
    }

    // Getters
    public ErrorType getErrorType() { return errorType; }
    public int getScore() { return score; }
    public String getMessage() { return message; }
    public float getActualValue() { return actualValue; }
    public float getExpectedValue() { return expectedValue; }
    public long getTimestampMs() { return timestampMs; }
    public Bitmap getErrorFrame() { return errorFrame; }
    public Bitmap getCorrectFrameRef() { return correctFrameRef; }

    public String getFormattedMessage() {
        if (actualValue > 0 && expectedValue > 0) {
            return String.format("%s: 实际值 %.1f°, 期望值 %.1f°",
                    errorType.getDescription(), actualValue, expectedValue);
        }
        return String.format("%s: %s", errorType.getDescription(), message);
    }

    public static class Builder {
        private ErrorType errorType;
        private int score;
        private String message;
        private float actualValue = -1f;
        private float expectedValue = -1f;
        private long timestampMs;
        private Bitmap errorFrame;
        private Bitmap correctFrameRef;

        public Builder setErrorType(ErrorType errorType) {
            this.errorType = errorType;
            this.message = errorType.getDescription();
            return this;
        }
        public Builder setScore(int score) { this.score = score; return this; }
        public Builder setMessage(String message) { this.message = message; return this; }
        public Builder setActualValue(float actualValue) { this.actualValue = actualValue; return this; }
        public Builder setExpectedValue(float expectedValue) { this.expectedValue = expectedValue; return this; }
        public Builder setTimestampMs(long timestampMs) { this.timestampMs = timestampMs; return this; }
        public Builder setErrorFrame(Bitmap errorFrame) { this.errorFrame = errorFrame; return this; }
        public Builder setCorrectFrameRef(Bitmap correctFrameRef) { this.correctFrameRef = correctFrameRef; return this; }

        public ActionError build() {
            return new ActionError(this);
        }
    }
}