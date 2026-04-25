package com.example.fitness.sdk.core;

import android.util.Log;

import com.example.fitness.sdk.model.ActionData;
import com.example.fitness.sdk.model.ErrorType;
import com.example.fitness.sdk.model.SkeletonFrame;

import java.util.LinkedList;
import java.util.List;

public class ActionMatcher {
    private static final String TAG = "ActionMatcher";
    private static final int SMOOTHING_WINDOW = 5;
    private static final float SIMILARITY_THRESHOLD = 0.1f;

    private ActionData currentAction;
    private int standardFramePointer = 0;
    private int completedCount = 0;
    private boolean isActionActive = false;
    private long actionStartTime = 0;
    private final LinkedList<Float> similarityHistory = new LinkedList<>();
    private MatcherListener listener;

    private float kneeWeight = 0.5f, hipWeight = 0.5f, elbowWeight = 0f, shoulderWeight = 0f;

    public interface MatcherListener {
        void onActionStart();
        void onActionComplete(int score, long durationMs, int completedCount);
        void onActionError(int score, ErrorType errorType);
        void onSimilarityUpdate(float similarity, int matchedFrameIndex);
    }

    public void setListener(MatcherListener listener) {
        this.listener = listener;
    }

    public void loadAction(ActionData actionData) {
        this.currentAction = actionData;
        this.standardFramePointer = 0;
        updateWeights(actionData.getActionName());
        Log.d(TAG, "加载动作: " + actionData.getActionName() + ", 帧数: " + actionData.getFrameCount());
    }

    private void updateWeights(String actionName) {
        if (actionName.contains("拳击")) {
            kneeWeight = 0.1f; hipWeight = 0.1f; elbowWeight = 0.4f; shoulderWeight = 0.4f;
        } else if (actionName.contains("高抬腿")) {
            kneeWeight = 0.7f; hipWeight = 0.3f; elbowWeight = 0f; shoulderWeight = 0f;
        } else if (actionName.contains("硬拉")) {
            kneeWeight = 0.2f; hipWeight = 0.8f; elbowWeight = 0f; shoulderWeight = 0f;
        } else {
            kneeWeight = 0.5f; hipWeight = 0.5f; elbowWeight = 0f; shoulderWeight = 0f;
        }
    }

    public void startSession() {
        reset();
        isActionActive = true;
        actionStartTime = System.currentTimeMillis();
    }

    public void stopSession() {
        isActionActive = false;
    }

    public void reset() {
        standardFramePointer = 0;
        completedCount = 0;
        isActionActive = false;
        similarityHistory.clear();
    }

    public void resetCounter() {
        completedCount = 0;
    }

    public float processFrame(SkeletonFrame realTimeFrame) {
        Log.d(TAG, "processFrame 被调用, isActionActive=" + isActionActive
                + ", currentAction=" + (currentAction != null)
                + ", hasValidPose=" + (realTimeFrame != null && realTimeFrame.hasValidPose()));

        if (!isActionActive || currentAction == null) {
            Log.w(TAG, "processFrame 跳过: isActionActive=" + isActionActive + ", currentAction=" + currentAction);
            return 0f;
        }

        if (!realTimeFrame.hasValidPose()) {
            Log.w(TAG, "processFrame 跳过: 无效姿态");
            return 0f;
        }

        List<SkeletonFrame> standardFrames = currentAction.getFrames();
        if (standardFrames == null || standardFrames.isEmpty()) {
            Log.w(TAG, "standardFrames 为空");
            return 0f;
        }


        // 如果到达末尾，完成一次动作并重置指针
        if (standardFramePointer >= standardFrames.size()) {
            if (currentAction.isNeedCounting()) {
                completeAction();
            }
            standardFramePointer = 0;  // 重置指针，继续循环
        }

        SkeletonFrame standardFrame = standardFrames.get(standardFramePointer);
        float similarity = calculateSimilarity(realTimeFrame, standardFrame);
        float smoothed = smoothSimilarity(similarity);

        if (smoothed >= SIMILARITY_THRESHOLD) {
            standardFramePointer++;
        }

        return smoothed;
    }

    private float calculateSimilarity(SkeletonFrame rt, SkeletonFrame std) {
        float total = 0f, weight = 0f;

        if (kneeWeight > 0 && rt.getKneeAngle() > 0 && std.getKneeAngle() > 0) {
            total += kneeWeight * angleToSimilarity(Math.abs(rt.getKneeAngle() - std.getKneeAngle()));
            weight += kneeWeight;
        }
        if (hipWeight > 0 && rt.getHipAngle() > 0 && std.getHipAngle() > 0) {
            total += hipWeight * angleToSimilarity(Math.abs(rt.getHipAngle() - std.getHipAngle()));
            weight += hipWeight;
        }
        if (elbowWeight > 0 && rt.getLeftElbowAngle() > 0 && std.getLeftElbowAngle() > 0) {
            float left = angleToSimilarity(Math.abs(rt.getLeftElbowAngle() - std.getLeftElbowAngle()));
            float right = rt.getRightElbowAngle() > 0 ? angleToSimilarity(Math.abs(rt.getRightElbowAngle() - std.getRightElbowAngle())) : 1f;
            total += elbowWeight * ((left + right) / 2);
            weight += elbowWeight;
        }
        if (shoulderWeight > 0 && rt.getLeftShoulderAngle() > 0 && std.getLeftShoulderAngle() > 0) {
            float left = angleToSimilarity(Math.abs(rt.getLeftShoulderAngle() - std.getLeftShoulderAngle()));
            float right = rt.getRightShoulderAngle() > 0 ? angleToSimilarity(Math.abs(rt.getRightShoulderAngle() - std.getRightShoulderAngle())) : 1f;
            total += shoulderWeight * ((left + right) / 2);
            weight += shoulderWeight;
        }

        return weight > 0 ? Math.min(1f, Math.max(0f, total / weight)) : 0f;
    }

    private float angleToSimilarity(float diff) {
        if (diff <= 5f) return 1.0f;
        if (diff <= 12f) return 1.0f - (diff - 5f) * 0.02f;
        if (diff <= 25f) return 0.86f - (diff - 12f) * 0.01f;
        if (diff <= 40f) return 0.73f - (diff - 25f) * 0.012f;
        if (diff <= 60f) return 0.55f - (diff - 40f) * 0.0125f;
        return Math.max(0.1f, 0.30f - (diff - 60f) * 0.007f);
    }

    private float smoothSimilarity(float similarity) {
        similarityHistory.add(similarity);
        while (similarityHistory.size() > SMOOTHING_WINDOW) {
            similarityHistory.removeFirst();
        }
        float sum = 0f;
        for (float s : similarityHistory) sum += s;
        return similarityHistory.isEmpty() ? similarity : sum / similarityHistory.size();
    }

    private void completeAction() {
        completedCount++;
        long durationMs = System.currentTimeMillis() - actionStartTime;
        int score = calculateScore();

        // 重置开始时间用于下一次动作
        actionStartTime = System.currentTimeMillis();

        if (listener != null) {
            if (score < 60) {
                listener.onActionError(score, ErrorType.ANGLE_DEVIATION);
            } else {
                listener.onActionComplete(score, durationMs, completedCount);
            }
        }

        Log.d(TAG, "动作完成! 次数=" + completedCount + ", 得分=" + score);
    }

    private int calculateScore() {
        if (similarityHistory.isEmpty()) return 50;
        float avg = 0f;
        for (float s : similarityHistory) avg += s;
        avg /= similarityHistory.size();
        if (avg >= 0.85f) return 95;
        if (avg >= 0.70f) return 80;
        if (avg >= 0.50f) return 65;
        return 50;
    }

    public String getCurrentActionId() {
        return currentAction != null ? currentAction.getActionId() : null;
    }

    public int getCompletedCount() {
        return completedCount;
    }
}