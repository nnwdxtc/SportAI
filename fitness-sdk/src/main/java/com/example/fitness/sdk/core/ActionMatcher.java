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
    private static final float CORRECT_ACTION_THRESHOLD = 0.6f;  // 正确动作阈值

    private ActionData currentAction;
    private int standardFramePointer = 0;
    private int completedCount = 0;           // 正确动作次数
    private float totalSimilaritySum = 0f;     // 所有正确动作的相似度总和
    private boolean isActionActive = false;
    private long currentActionStartTime = 0;   // 当前动作开始时间
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
        Log.d(TAG, "ActionMatcher 会话已开始");
    }

    public void stopSession() {
        isActionActive = false;
        Log.d(TAG, "ActionMatcher 会话已停止");
    }

    public void reset() {
        standardFramePointer = 0;
        completedCount = 0;
        totalSimilaritySum = 0f;
        currentActionStartTime = 0;
        isActionActive = false;
        similarityHistory.clear();
    }

    public void resetCounter() {
        completedCount = 0;
        totalSimilaritySum = 0f;
        Log.d(TAG, "计数器已重置");
    }

    public float processFrame(SkeletonFrame realTimeFrame) {
        if (!isActionActive || currentAction == null) {
            return 0f;
        }

        if (!realTimeFrame.hasValidPose()) {
            return 0f;
        }

        List<SkeletonFrame> standardFrames = currentAction.getFrames();
        if (standardFrames == null || standardFrames.isEmpty()) {
            return 0f;
        }

        // 检测动作开始（第一帧或相似度首次超过阈值）
        if (standardFramePointer == 0 && currentActionStartTime == 0) {
            currentActionStartTime = System.currentTimeMillis();
            if (listener != null) {
                listener.onActionStart();
            }
        }

        if (standardFramePointer >= standardFrames.size()) {
            // 完成一次动作周期
            float avgSimilarity = calculateAverageSimilarity();
            boolean isCorrect = avgSimilarity >= CORRECT_ACTION_THRESHOLD;

            long durationMs = System.currentTimeMillis() - currentActionStartTime;
            int score = (int) (avgSimilarity * 100);

            if (isCorrect) {
                // 正确动作：增加计数，累加相似度
                completedCount++;
                totalSimilaritySum += avgSimilarity;

                // 计算所有动作的平均相似度
                int overallScore = (int) ((totalSimilaritySum / completedCount) * 100);

                if (listener != null) {
                    listener.onActionComplete(overallScore, durationMs, completedCount);
                }
                Log.d(TAG, String.format("正确动作! 本次相似度:%.2f, 总次数:%d, 平均得分:%d",
                        avgSimilarity, completedCount, overallScore));
            } else {
                // 错误动作：不增加计数，不累加相似度
                int errorScore = (int) (avgSimilarity * 100);
                if (listener != null) {
                    listener.onActionError(errorScore, ErrorType.ANGLE_DEVIATION);
                }
                Log.d(TAG, String.format("错误动作! 本次相似度:%.2f, 不计入次数", avgSimilarity));
            }

            // 重置当前动作状态，准备下一次
            standardFramePointer = 0;
            currentActionStartTime = System.currentTimeMillis();
            similarityHistory.clear();
            return avgSimilarity;
        }

        SkeletonFrame standardFrame = standardFrames.get(standardFramePointer);
        float similarity = calculateSimilarity(realTimeFrame, standardFrame);
        float smoothed = smoothSimilarity(similarity);

        if (smoothed >= SIMILARITY_THRESHOLD) {
            standardFramePointer++;
        }

        return smoothed;
    }

    private float calculateAverageSimilarity() {
        if (similarityHistory.isEmpty()) return 0f;
        float sum = 0f;
        for (float s : similarityHistory) {
            sum += s;
        }
        return sum / similarityHistory.size();
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

    public String getCurrentActionId() {
        return currentAction != null ? currentAction.getActionId() : null;
    }

    public int getCompletedCount() {
        return completedCount;
    }
}