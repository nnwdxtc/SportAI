// FrameAnalysisData.java 修改
package com.example.myapplication;

import java.util.ArrayList;
import java.util.List;

public class FrameAnalysisData {
    public int frameIndex;
    public float realTimeKneeAngle;
    public float realTimeHipAngle;
    public float standardKneeAngle;
    public float standardHipAngle;
    public float kneeSimilarity;
    public float hipSimilarity;
    public float totalSimilarity;
    public boolean isSelectedFrame;
    public String selectionReason;
    public List<Float> previousSimilarities = new ArrayList<>();
    public List<Float> nextSimilarities = new ArrayList<>();

    // 拳击专用字段
    public float realTimeLeftElbowAngle;
    public float realTimeRightElbowAngle;
    public float realTimeLeftShoulderAngle;
    public float realTimeRightShoulderAngle;
    public float standardLeftElbowAngle;
    public float standardRightElbowAngle;
    public float standardLeftShoulderAngle;
    public float standardRightShoulderAngle;
    public float leftElbowSimilarity;
    public float rightElbowSimilarity;
    public float leftShoulderSimilarity;
    public float rightShoulderSimilarity;

    // 动作类型
    public String actionType = "深蹲";


    public int standardFrameIndex = -1;
    public int searchStartIndex = -1;
    public int searchEndIndex = -1;
    public float windowBestSimilarity = 0f;
    public int matchOffset = 0;
    public float continuityScore = 0f;
    public float combinedScore = 0f;
    public long timestamp;
    public boolean hasValidPose;

    // 深蹲专用：肘和肩角度（平均）
    public float realTimeElbowAngle;
    public float realTimeShoulderAngle;
    public float standardElbowAngle;
    public float standardShoulderAngle;
    public float elbowSimilarity;
    public float shoulderSimilarity;


    // 原有构造器（深蹲）- 简版（7参数）
    public FrameAnalysisData(int frameIndex, float rtKnee, float rtHip,
                             float stdKnee, float stdHip, float kneeSim, float hipSim) {
        this.frameIndex = frameIndex;
        this.realTimeKneeAngle = rtKnee;
        this.realTimeHipAngle = rtHip;
        this.standardKneeAngle = stdKnee;
        this.standardHipAngle = stdHip;
        this.kneeSimilarity = kneeSim;
        this.hipSimilarity = hipSim;
        this.totalSimilarity = (kneeSim + hipSim) / 2;
        this.isSelectedFrame = false;
        this.selectionReason = "";
        this.actionType = "深蹲";
        this.timestamp = System.currentTimeMillis();
        this.hasValidPose = true;
    }

    // 拳击专用构造器（12参数）
    public FrameAnalysisData(int frameIndex, boolean isBoxing,
                             float rtLeftElbow, float rtRightElbow,
                             float rtLeftShoulder, float rtRightShoulder,
                             float stdLeftElbow, float stdRightElbow,
                             float stdLeftShoulder, float stdRightShoulder,
                             float leftElbowSim, float rightElbowSim,
                             float leftShoulderSim, float rightShoulderSim) {
        this.frameIndex = frameIndex;

        // 拳击角度
        this.realTimeLeftElbowAngle = rtLeftElbow;
        this.realTimeRightElbowAngle = rtRightElbow;
        this.realTimeLeftShoulderAngle = rtLeftShoulder;
        this.realTimeRightShoulderAngle = rtRightShoulder;
        this.standardLeftElbowAngle = stdLeftElbow;
        this.standardRightElbowAngle = stdRightElbow;
        this.standardLeftShoulderAngle = stdLeftShoulder;
        this.standardRightShoulderAngle = stdRightShoulder;

        // 拳击相似度
        this.leftElbowSimilarity = leftElbowSim;
        this.rightElbowSimilarity = rightElbowSim;
        this.leftShoulderSimilarity = leftShoulderSim;
        this.rightShoulderSimilarity = rightShoulderSim;

        // 拳击总相似度计算（左右肘各30%，左右肩各20%）
        this.totalSimilarity = (leftElbowSim * 0.3f + rightElbowSim * 0.3f +
                leftShoulderSim * 0.2f + rightShoulderSim * 0.2f);

        this.isSelectedFrame = false;
        this.selectionReason = "";
        this.actionType = "拳击";
        this.timestamp = System.currentTimeMillis();
        this.hasValidPose = true;
    }

    // 深蹲全角度构造器（12参数）- 使用不同的参数顺序区分
    public FrameAnalysisData(int frameIndex,
                             float rtKnee, float rtHip, float rtElbow, float rtShoulder,
                             float stdKnee, float stdHip, float stdElbow, float stdShoulder,
                             float kneeSim, float hipSim, float elbowSim, float shoulderSim) {
        this.frameIndex = frameIndex;
        this.realTimeKneeAngle = rtKnee;
        this.realTimeHipAngle = rtHip;
        this.realTimeElbowAngle = rtElbow;
        this.realTimeShoulderAngle = rtShoulder;
        this.standardKneeAngle = stdKnee;
        this.standardHipAngle = stdHip;
        this.standardElbowAngle = stdElbow;
        this.standardShoulderAngle = stdShoulder;
        this.kneeSimilarity = kneeSim;
        this.hipSimilarity = hipSim;
        this.elbowSimilarity = elbowSim;
        this.shoulderSimilarity = shoulderSim;
        this.totalSimilarity = (kneeSim + hipSim ) / 2;
        this.isSelectedFrame = false;
        this.selectionReason = "";
        this.timestamp = System.currentTimeMillis();
        this.hasValidPose = true;
        this.actionType = "深蹲";
    }

    // ========== 添加设置方法 ==========
    public void setMatchInfo(int stdFrameIndex, int searchStart, int searchEnd, float windowBest) {
        this.standardFrameIndex = stdFrameIndex;
        this.searchStartIndex = searchStart;
        this.searchEndIndex = searchEnd;
        this.windowBestSimilarity = windowBest;
    }

    public void setContinuityInfo(int offset, float continuityScore, float combinedScore) {
        this.matchOffset = offset;
        this.continuityScore = continuityScore;
        this.combinedScore = combinedScore;
    }
}