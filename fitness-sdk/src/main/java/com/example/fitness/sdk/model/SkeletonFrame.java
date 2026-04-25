package com.example.fitness.sdk.model;

import java.util.List;

/**
 * 单帧骨骼数据
 */
public class SkeletonFrame {
    private final long timestampMs;           // 时间戳（毫秒）
    private final List<Keypoint> keypoints;   // 33个关键点
    private final boolean hasValidPose;       // 姿态是否有效

    // 角度数据（预计算）
    private final float kneeAngle;      // 膝关节角度（平均）
    private final float hipAngle;       // 髋关节角度（平均）
    private final float elbowAngle;     // 肘关节角度（平均）
    private final float shoulderAngle;  // 肩关节角度（平均）

    // 拳击专用：左右分离角度
    private final float leftElbowAngle;
    private final float rightElbowAngle;
    private final float leftShoulderAngle;
    private final float rightShoulderAngle;

    private SkeletonFrame(Builder builder) {
        this.timestampMs = builder.timestampMs;
        this.keypoints = builder.keypoints;
        this.hasValidPose = builder.hasValidPose;
        this.kneeAngle = builder.kneeAngle;
        this.hipAngle = builder.hipAngle;
        this.elbowAngle = builder.elbowAngle;
        this.shoulderAngle = builder.shoulderAngle;
        this.leftElbowAngle = builder.leftElbowAngle;
        this.rightElbowAngle = builder.rightElbowAngle;
        this.leftShoulderAngle = builder.leftShoulderAngle;
        this.rightShoulderAngle = builder.rightShoulderAngle;
    }

    // Getters
    public long getTimestampMs() { return timestampMs; }
    public List<Keypoint> getKeypoints() { return keypoints; }
    public boolean hasValidPose() { return hasValidPose; }
    public float getKneeAngle() { return kneeAngle; }
    public float getHipAngle() { return hipAngle; }
    public float getElbowAngle() { return elbowAngle; }
    public float getShoulderAngle() { return shoulderAngle; }
    public float getLeftElbowAngle() { return leftElbowAngle; }
    public float getRightElbowAngle() { return rightElbowAngle; }
    public float getLeftShoulderAngle() { return leftShoulderAngle; }
    public float getRightShoulderAngle() { return rightShoulderAngle; }

    public static class Builder {
        private long timestampMs;
        private List<Keypoint> keypoints;
        private boolean hasValidPose;
        private float kneeAngle = -1f;
        private float hipAngle = -1f;
        private float elbowAngle = -1f;
        private float shoulderAngle = -1f;
        private float leftElbowAngle = -1f;
        private float rightElbowAngle = -1f;
        private float leftShoulderAngle = -1f;
        private float rightShoulderAngle = -1f;

        public Builder setTimestampMs(long timestampMs) { this.timestampMs = timestampMs; return this; }
        public Builder setKeypoints(List<Keypoint> keypoints) { this.keypoints = keypoints; return this; }
        public Builder setHasValidPose(boolean hasValidPose) { this.hasValidPose = hasValidPose; return this; }
        public Builder setKneeAngle(float kneeAngle) { this.kneeAngle = kneeAngle; return this; }
        public Builder setHipAngle(float hipAngle) { this.hipAngle = hipAngle; return this; }
        public Builder setElbowAngle(float elbowAngle) { this.elbowAngle = elbowAngle; return this; }
        public Builder setShoulderAngle(float shoulderAngle) { this.shoulderAngle = shoulderAngle; return this; }
        public Builder setLeftElbowAngle(float leftElbowAngle) { this.leftElbowAngle = leftElbowAngle; return this; }
        public Builder setRightElbowAngle(float rightElbowAngle) { this.rightElbowAngle = rightElbowAngle; return this; }
        public Builder setLeftShoulderAngle(float leftShoulderAngle) { this.leftShoulderAngle = leftShoulderAngle; return this; }
        public Builder setRightShoulderAngle(float rightShoulderAngle) { this.rightShoulderAngle = rightShoulderAngle; return this; }

        public SkeletonFrame build() {
            return new SkeletonFrame(this);
        }
    }
}