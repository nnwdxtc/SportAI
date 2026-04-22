package com.example.myapplication;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class FrameData {

    public float kneeAngle;
    public float hipAngle;
    public float hipHeight;
    public long timestamp;
    public List<NormalizedLandmark> landmarks;
    public boolean hasValidPose;
    public float elbowAngle;
    public float shoulderAngle;

    // 拳击专用字段
    public float leftElbowAngle;
    public float rightElbowAngle;
    public float leftShoulderAngle;
    public float rightShoulderAngle;

    // 默认构造函数（必须，用于 Gson JSON 反序列化）
    public FrameData() {
        // 初始化默认值
        this.kneeAngle = 0f;
        this.hipAngle = 0f;
        this.hipHeight = 0f;
        this.timestamp = 0L;
        this.landmarks = null;
        this.hasValidPose = false;
        this.elbowAngle = 0f;
        this.shoulderAngle = 0f;
        this.leftElbowAngle = 0f;
        this.rightElbowAngle = 0f;
        this.leftShoulderAngle = 0f;
        this.rightShoulderAngle = 0f;
    }

    // 构造函数 1：通用运动（深蹲等）
    public FrameData(float kneeAngle, float hipAngle, float elbowAngle, float shoulderAngle,
                     float hipHeight, long timestamp,
                     List<NormalizedLandmark> landmarks, boolean hasValidPose) {
        this.kneeAngle = kneeAngle;
        this.hipAngle = hipAngle;
        this.hipHeight = hipHeight;
        this.timestamp = timestamp;
        this.landmarks = landmarks;
        this.hasValidPose = hasValidPose;
        this.elbowAngle = elbowAngle;
        this.shoulderAngle = shoulderAngle;

        // 拳击专用字段默认值
        this.leftElbowAngle = elbowAngle;
        this.rightElbowAngle = elbowAngle;
        this.leftShoulderAngle = shoulderAngle;
        this.rightShoulderAngle = shoulderAngle;
    }

    // 构造函数 2：拳击专用
    public FrameData(float kneeAngle, float hipAngle,
                     float leftElbowAngle, float rightElbowAngle,
                     float leftShoulderAngle, float rightShoulderAngle,
                     float hipHeight, long timestamp,
                     List<NormalizedLandmark> landmarks, boolean hasValidPose) {
        this.kneeAngle = kneeAngle;
        this.hipAngle = hipAngle;
        this.hipHeight = hipHeight;
        this.timestamp = timestamp;
        this.landmarks = landmarks;
        this.hasValidPose = hasValidPose;

        this.leftElbowAngle = leftElbowAngle;
        this.rightElbowAngle = rightElbowAngle;
        this.leftShoulderAngle = leftShoulderAngle;
        this.rightShoulderAngle = rightShoulderAngle;

        // 计算平均值
        this.elbowAngle = calculateValidAverage(leftElbowAngle, rightElbowAngle);
        this.shoulderAngle = calculateValidAverage(leftShoulderAngle, rightShoulderAngle);
    }

    // 构造函数 3：从 VideoFrameData 创建
    public FrameData(VideoProcessor.VideoFrameData videoFrameData) {
        this.kneeAngle = videoFrameData.avgKneeAngle;
        this.hipAngle = videoFrameData.avgHipAngle;
        this.elbowAngle = videoFrameData.avgElbowAngle;
        this.shoulderAngle = videoFrameData.avgShoulderAngle;
        this.hipHeight = videoFrameData.hipHeight;
        this.timestamp = videoFrameData.timestamp;
        this.landmarks = videoFrameData.landmarks;
        this.hasValidPose = videoFrameData.hasValidPose;

        // 拳击专用字段
        this.leftElbowAngle = videoFrameData.leftElbowAngle;
        this.rightElbowAngle = videoFrameData.rightElbowAngle;
        this.leftShoulderAngle = videoFrameData.leftShoulderAngle;
        this.rightShoulderAngle = videoFrameData.rightShoulderAngle;
    }

    private float calculateValidAverage(float left, float right) {
        int count = 0;
        float sum = 0;

        if (isValidAngle(left)) {
            sum += left;
            count++;
        }
        if (isValidAngle(right)) {
            sum += right;
            count++;
        }

        return count > 0 ? sum / count : 0f;
    }

    private boolean isValidAngle(float angle) {
        return !Float.isNaN(angle) && !Float.isInfinite(angle) && angle >= 0 && angle <= 180;
    }
}