package com.example.fitness.sdk.core;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.fitness.sdk.model.ActionData;
import com.example.fitness.sdk.model.ErrorType;
import com.example.fitness.sdk.model.SkeletonFrame;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ActionMatcher {
    private static final String TAG = "ActionMatcher";
    private static final int SMOOTHING_WINDOW = 5;

    // ========== 对齐机制常量（与RealtimeActivity保持一致） ==========
    private static final long ALIGNMENT_INTERVAL_MS = 10000; // 10秒对齐间隔
    private static final float ALIGNMENT_SIMILARITY_THRESHOLD = 0.6f; // 对齐相似度阈值60%
    private static final int ALIGNMENT_SEARCH_RADIUS_RATIO = 2; // 搜索半径 = 标准帧总数 / 此值

    // ========== 连续性约束相关变量 ==========
    private LinkedList<Integer> matchOffsetHistory = new LinkedList<>();
    private static final int MATCH_HISTORY_SIZE = 5;
    private static final int MAX_OFFSET_VARIATION = 10;
    private static final float CONTINUITY_WEIGHT = 0.3f;

    // ========== 优化参数 ==========
    private static final int OPTIMIZED_WINDOW_SIZE = 8; // 姿态窗口大小
    private static final int FRAME_ADVANCE_THRESHOLD = 1; // 帧前进阈值（帧数）

    // ========== 超时检测参数 ==========
    private static final long DEFAULT_ACTION_TIMEOUT_MS = 5000; // 默认5秒超时
    private long actionTimeoutMs = DEFAULT_ACTION_TIMEOUT_MS;
    private Handler timeoutHandler;
    private Runnable timeoutRunnable;
    private boolean isTimeoutTriggered = false;

    private ActionData currentAction;
    private ActionData.ActionConfig currentConfig;

    private int standardFramePointer = 0;
    private int completedCount = 0;
    private float totalScoreSum = 0f;

    private boolean isActionActive = false;
    private long currentActionStartTime = 0;

    // 当前动作的统计数据
    private int currentActionMatchedFrames = 0;
    private float currentActionSimilaritySum = 0f;
    private float currentActionMinSimilarity = 1f;
    private final LinkedList<Float> similarityHistory = new LinkedList<>();
    private final LinkedList<Long> frameTimestampHistory = new LinkedList<>();

    // 平滑相似度
    private float lastSmoothedSimilarity = 0f;
    private static final float SIMILARITY_SMOOTHING_FACTOR = 0.7f;

    // 错误帧收集
    private final List<Bitmap> errorKeyFrames = new ArrayList<>();
    private Bitmap correctFrameRef = null;

    // 正确动作参考帧的相似度阈值
    private static final float CORRECT_FRAME_SIMILARITY_THRESHOLD = 0.7f;

    // 动态权重
    private final Map<String, Float> weights = new java.util.HashMap<>();

    // 当前帧的Bitmap（由外部传入）
    private Bitmap currentFrameBitmap;

    // 姿态窗口（用于对齐）
    private final LinkedList<SkeletonFrame> poseWindow = new LinkedList<>();

    // 对齐相关成员变量
    private long lastAlignmentTime = 0;
    private boolean isInAlignmentPeriod = false;
    private int alignmentSearchRadius = 0;

    private MatcherListener listener;

    public interface MatcherListener {
        void onActionStart();
        void onActionComplete(int score, long durationMs, int completedCount);
        void onActionError(int score, ErrorType errorType, List<Bitmap> errorKeyFrames, Bitmap correctFrameRef);
        void onSimilarityUpdate(float similarity, int matchedFrameIndex);
        void onActionTimeout();  // 新增超时回调
    }

    public void setListener(MatcherListener listener) {
        this.listener = listener;
    }

    /**
     * 设置动作超时时间
     * @param timeoutMs 超时时间（毫秒），<=0 表示禁用超时检测
     */
    public void setActionTimeout(long timeoutMs) {
        this.actionTimeoutMs = timeoutMs > 0 ? timeoutMs : DEFAULT_ACTION_TIMEOUT_MS;
        Log.d(TAG, "设置动作超时时间: " + actionTimeoutMs + "ms");
    }

    /**
     * 设置当前帧的Bitmap（用于错误帧收集）
     */
    public void setCurrentFrameBitmap(Bitmap bitmap) {
        this.currentFrameBitmap = bitmap;
    }

    public void loadAction(ActionData actionData) {
        this.currentAction = actionData;
        this.currentConfig = actionData.getConfig();
        this.standardFramePointer = 0;

        weights.clear();
        weights.putAll(currentConfig.getWeights());

        // 初始化超时Handler
        if (timeoutHandler == null) {
            timeoutHandler = new Handler(Looper.getMainLooper());
        }

        Log.d(TAG, "加载动作: " + actionData.getActionName() +
                ", 帧数: " + actionData.getFrameCount() +
                ", 完成阈值: " + currentConfig.getCompletionThreshold() +
                ", 节奏容差: " + currentConfig.getRhythmTolerance() +
                ", 最低相似度阈值: " + currentConfig.getMinSimilarityThreshold());
    }

    public void startSession() {
        reset();
        isActionActive = true;
        Log.d(TAG, "ActionMatcher 会话已开始");
    }

    public void stopSession() {
        isActionActive = false;
        cancelTimeoutTimer();
        Log.d(TAG, "ActionMatcher 会话已停止");
    }

    public void reset() {
        standardFramePointer = 0;
        completedCount = 0;
        totalScoreSum = 0f;
        currentActionStartTime = 0;
        isActionActive = false;
        similarityHistory.clear();
        frameTimestampHistory.clear();
        errorKeyFrames.clear();
        correctFrameRef = null;
        poseWindow.clear();
        matchOffsetHistory.clear();
        lastAlignmentTime = 0;
        isInAlignmentPeriod = false;
        lastSmoothedSimilarity = 0f;
        isTimeoutTriggered = false;
        cancelTimeoutTimer();
        resetCurrentActionStats();
    }

    public void resetCounter() {
        completedCount = 0;
        totalScoreSum = 0f;
        Log.d(TAG, "计数器已重置");
    }

    private void resetCurrentActionStats() {
        currentActionMatchedFrames = 0;
        currentActionSimilaritySum = 0f;
        currentActionMinSimilarity = 1f;
        errorKeyFrames.clear();
        correctFrameRef = null;
    }

    /**
     * 启动超时计时器
     */
    private void startTimeoutTimer() {
        cancelTimeoutTimer();

        if (actionTimeoutMs <= 0) {
            return; // 超时检测已禁用
        }

        isTimeoutTriggered = false;
        timeoutRunnable = () -> {
            if (isActionActive && currentActionStartTime > 0 && !isTimeoutTriggered) {
                isTimeoutTriggered = true;
                Log.w(TAG, String.format("动作超时！已用时: %dms, 超时阈值: %dms",
                        System.currentTimeMillis() - currentActionStartTime, actionTimeoutMs));

                if (listener != null) {
                    listener.onActionTimeout();
                }

                // 超时后重置当前动作，允许重新开始
                resetForNextAction();
            }
        };

        timeoutHandler.postDelayed(timeoutRunnable, actionTimeoutMs);
        Log.d(TAG, "启动超时计时器，阈值: " + actionTimeoutMs + "ms");
    }

    /**
     * 取消超时计时器
     */
    private void cancelTimeoutTimer() {
        if (timeoutHandler != null && timeoutRunnable != null) {
            timeoutHandler.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
    }

    /**
     * 重置超时计时器（每次动作开始或重置时调用）
     */
    private void resetTimeoutTimer() {
        if (isActionActive && currentActionStartTime > 0) {
            startTimeoutTimer();
        }
    }

    /**
     * 收集错误帧
     */
    private void collectErrorFrame(float similarity) {
        if (currentFrameBitmap != null && errorKeyFrames.size() < MAX_ERROR_FRAMES) {
            Bitmap copy = Bitmap.createBitmap(currentFrameBitmap);
            errorKeyFrames.add(copy);
        }
    }

    /**
     * 设置正确动作参考帧
     */
    private void setCorrectFrameRef() {
        if (correctFrameRef == null && currentFrameBitmap != null) {
            correctFrameRef = Bitmap.createBitmap(currentFrameBitmap);
        }
    }

    /**
     * 更新姿态窗口（用于对齐）
     */
    private void updatePoseWindow(SkeletonFrame realTimeFrame) {
        if (poseWindow.size() >= OPTIMIZED_WINDOW_SIZE) {
            poseWindow.removeFirst();
        }
        poseWindow.add(realTimeFrame);
    }

    /**
     * 角度差转相似度（与RealtimeActivity保持一致）
     * 使用余弦相似度算法
     */
    private float angleToSimilarity(float angleDiff) {
        angleDiff = Math.abs(angleDiff);
        float radians = (float) Math.toRadians(angleDiff);
        float cosTheta = (float) Math.cos(radians);
        float similarity = (cosTheta + 1.0f) / 2.0f;
        return Math.max(0.0f, Math.min(1.0f, similarity));
    }

    /**
     * 计算单个关节的相似度（基于角度差）
     */
    private float calculateJointSimilarity(float rtAngle, float stdAngle) {
        if (rtAngle <= 0 || stdAngle <= 0) {
            return 0f;
        }
        float angleDiff = Math.abs(rtAngle - stdAngle);
        return angleToSimilarity(angleDiff);
    }

    /**
     * 计算综合相似度（根据配置的权重）
     */
    private float calculateSimilarity(SkeletonFrame rt, SkeletonFrame std) {
        float total = 0f, weight = 0f;

        // 膝关节（深蹲/高抬腿等重视）
        float kneeWeight = getWeight("knee");
        if (kneeWeight > 0 && rt.getKneeAngle() > 0 && std.getKneeAngle() > 0) {
            total += kneeWeight * calculateJointSimilarity(rt.getKneeAngle(), std.getKneeAngle());
            weight += kneeWeight;
        }

        // 髋关节（罗马尼亚硬拉等重视）
        float hipWeight = getWeight("hip");
        if (hipWeight > 0 && rt.getHipAngle() > 0 && std.getHipAngle() > 0) {
            total += hipWeight * calculateJointSimilarity(rt.getHipAngle(), std.getHipAngle());
            weight += hipWeight;
        }

        // 肘关节（拳击等重视）
        float leftElbowWeight = getWeight("leftElbow");
        if (leftElbowWeight > 0 && rt.getLeftElbowAngle() > 0 && std.getLeftElbowAngle() > 0) {
            total += leftElbowWeight * calculateJointSimilarity(rt.getLeftElbowAngle(), std.getLeftElbowAngle());
            weight += leftElbowWeight;
        }

        float rightElbowWeight = getWeight("rightElbow");
        if (rightElbowWeight > 0 && rt.getRightElbowAngle() > 0 && std.getRightElbowAngle() > 0) {
            total += rightElbowWeight * calculateJointSimilarity(rt.getRightElbowAngle(), std.getRightElbowAngle());
            weight += rightElbowWeight;
        }

        // 肩关节（拳击等重视）
        float leftShoulderWeight = getWeight("leftShoulder");
        if (leftShoulderWeight > 0 && rt.getLeftShoulderAngle() > 0 && std.getLeftShoulderAngle() > 0) {
            total += leftShoulderWeight * calculateJointSimilarity(rt.getLeftShoulderAngle(), std.getLeftShoulderAngle());
            weight += leftShoulderWeight;
        }

        float rightShoulderWeight = getWeight("rightShoulder");
        if (rightShoulderWeight > 0 && rt.getRightShoulderAngle() > 0 && std.getRightShoulderAngle() > 0) {
            total += rightShoulderWeight * calculateJointSimilarity(rt.getRightShoulderAngle(), std.getRightShoulderAngle());
            weight += rightShoulderWeight;
        }

        return weight > 0 ? Math.min(1f, Math.max(0f, total / weight)) : 0f;
    }

    private float getWeight(String key) {
        return weights.getOrDefault(key, 0f);
    }

    /**
     * 计算连续性得分
     */
    private float calculateContinuityScore(int currentOffset) {
        if (matchOffsetHistory.size() < MATCH_HISTORY_SIZE) {
            return 0.5f;
        }

        float avgOffset = 0;
        for (int offset : matchOffsetHistory) {
            avgOffset += offset;
        }
        avgOffset /= matchOffsetHistory.size();

        float deviation = Math.abs(currentOffset - avgOffset);
        float maxDeviation = MAX_OFFSET_VARIATION;

        float continuityScore = 1.0f - Math.min(1.0f, deviation / maxDeviation);
        return continuityScore;
    }

    /**
     * 执行周期性对齐（10秒一次）
     */
    private void performPeriodicAlignment(SkeletonFrame realTimeFrame) {
        if (currentAction == null) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAlignmentTime >= ALIGNMENT_INTERVAL_MS) {
            Log.d(TAG, "执行周期性对齐（每10秒）");
            lastAlignmentTime = currentTime;
            isInAlignmentPeriod = true;

            int totalFrames = currentAction.getFrameCount();
            alignmentSearchRadius = totalFrames / ALIGNMENT_SEARCH_RADIUS_RATIO;

            findAndUpdateBestAlignment(realTimeFrame);

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                isInAlignmentPeriod = false;
                Log.d(TAG, "对齐完成，恢复同步检测");
            }, 1000);
        }
    }

    /**
     * 查找并更新最佳对齐帧
     */
    private void findAndUpdateBestAlignment(SkeletonFrame realTimeFrame) {
        if (currentAction == null || !poseWindow.isEmpty() && poseWindow.getLast() != realTimeFrame) {
            return;
        }

        try {
            List<SkeletonFrame> standardFrames = currentAction.getFrames();
            if (standardFrames == null || standardFrames.isEmpty()) return;

            int bestMatchIndex = -1;
            float bestSimilarity = 0f;

            int searchStart = Math.max(0, standardFramePointer - alignmentSearchRadius);
            int searchEnd = Math.min(standardFrames.size() - 1,
                    standardFramePointer + alignmentSearchRadius);

            Log.d(TAG, String.format("对齐搜索：范围[%d-%d]，半径%d，当前指针%d",
                    searchStart, searchEnd, alignmentSearchRadius, standardFramePointer));

            for (int i = searchStart; i <= searchEnd; i++) {
                SkeletonFrame standardFrame = standardFrames.get(i);
                if (standardFrame == null || !standardFrame.hasValidPose()) {
                    continue;
                }

                float similarity = calculateSimilarity(realTimeFrame, standardFrame);

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatchIndex = i;
                }
            }

            if (bestMatchIndex >= 0 && bestSimilarity > ALIGNMENT_SIMILARITY_THRESHOLD) {
                int oldPointer = standardFramePointer;
                standardFramePointer = bestMatchIndex;

                Log.d(TAG, String.format("对齐成功：从帧#%d对齐到帧#%d，相似度%.1f%%",
                        oldPointer, bestMatchIndex, bestSimilarity * 100));
            } else {
                Log.d(TAG, "对齐失败：未找到足够相似的帧，最高相似度" +
                        (bestSimilarity * 100) + "%");
            }

        } catch (Exception e) {
            Log.e(TAG, "对齐搜索失败: " + e.getMessage());
        }
    }

    /**
     * 应用实时平滑
     */
    private float applyRealTimeSmoothing(float currentSimilarity) {
        if (lastSmoothedSimilarity == 0f) {
            lastSmoothedSimilarity = currentSimilarity;
            return currentSimilarity;
        }
        float smoothed = (SIMILARITY_SMOOTHING_FACTOR * lastSmoothedSimilarity) +
                ((1 - SIMILARITY_SMOOTHING_FACTOR) * currentSimilarity);
        lastSmoothedSimilarity = smoothed;
        return smoothed;
    }

    public float processFrame(SkeletonFrame realTimeFrame) {
        if (!isActionActive || currentAction == null) {
            return 0f;
        }

        if (!realTimeFrame.hasValidPose()) {
            if (currentActionStartTime > 0) {
                collectErrorFrame(0f);
            }
            return 0f;
        }

        List<SkeletonFrame> standardFrames = currentAction.getFrames();
        if (standardFrames == null || standardFrames.isEmpty()) {
            return 0f;
        }

        // 更新姿态窗口（用于对齐）
        updatePoseWindow(realTimeFrame);

        // 检测动作开始
        if (standardFramePointer == 0 && currentActionStartTime == 0) {
            currentActionStartTime = System.currentTimeMillis();
            lastAlignmentTime = System.currentTimeMillis();
            resetCurrentActionStats();
            frameTimestampHistory.clear();
            isTimeoutTriggered = false;

            // 启动超时计时器
            startTimeoutTimer();

            if (listener != null) {
                listener.onActionStart();
            }
        }

        // 如果已经超时，不再处理后续帧
        if (isTimeoutTriggered) {
            return 0f;
        }

        frameTimestampHistory.add(System.currentTimeMillis());

        // 执行周期性对齐
        performPeriodicAlignment(realTimeFrame);

        // 获取当前标准帧
        SkeletonFrame standardFrame = standardFrames.get(standardFramePointer);

        // 计算相似度
        float similarity = calculateSimilarity(realTimeFrame, standardFrame);

        // 应用平滑
        float smoothed = applyRealTimeSmoothing(similarity);

        // 收集低相似度帧作为错误帧候选
        float completionThreshold = currentConfig.getCompletionThreshold();
        if (smoothed < completionThreshold) {
            collectErrorFrame(smoothed);
        } else if (correctFrameRef == null && smoothed >= CORRECT_FRAME_SIMILARITY_THRESHOLD) {
            setCorrectFrameRef();
        }

        currentActionMatchedFrames++;
        currentActionSimilaritySum += smoothed;
        if (smoothed < currentActionMinSimilarity) {
            currentActionMinSimilarity = smoothed;
        }

        // 1. 始终前进到下一帧（因为标准帧序列是顺序播放的）
        // 2. 相似度只是用来判断动作质量，不影响帧前进
        // 3. 这与原始 RealtimeActivity 的逻辑一致（标准视频播放到哪一帧，就匹配哪一帧）
        standardFramePointer++;

        // 当完成一次完整周期后，评估本次动作质量
        if (standardFramePointer >= standardFrames.size()) {
            // 取消超时计时器（动作已完成）
            cancelTimeoutTimer();

            evaluateActionCompletion(standardFrames.size());
            // 重置到下一周期开始
            standardFramePointer = 0;
            resetCurrentActionStats();

            // 重置超时计时器，为下一个动作准备
            if (isActionActive) {
                currentActionStartTime = System.currentTimeMillis();
                startTimeoutTimer();
            }
        } else {
            // 每处理一定帧数后刷新超时计时器
            // 这里选择每前进10帧刷新一次计时器
            if (standardFramePointer % 10 == 0) {
                resetTimeoutTimer();
            }
        }

        if (listener != null) {
            listener.onSimilarityUpdate(smoothed, standardFramePointer);
        }

        return smoothed;
    }

    private void evaluateActionCompletion(int totalStandardFrames) {
        long actualDuration = System.currentTimeMillis() - currentActionStartTime;
        long expectedDuration = (long) (totalStandardFrames * 1000f / currentAction.getFps());

        // 检查最小匹配帧数
        if (currentActionMatchedFrames < currentConfig.getMinMatchedFrames()) {
            onActionError(0, ErrorType.MISSING_KEYPOINT, errorKeyFrames, correctFrameRef);
            resetForNextAction();
            return;
        }

        float avgSimilarity = currentActionSimilaritySum / currentActionMatchedFrames;
        float minSimilarity = currentActionMinSimilarity;

        // 从配置读取阈值
        boolean isAngleCorrect = avgSimilarity >= currentConfig.getCompletionThreshold();
        float durationDeviation = Math.abs(actualDuration - expectedDuration) / (float) expectedDuration;
        boolean isRhythmCorrect = durationDeviation <= currentConfig.getRhythmTolerance();
        boolean isPostureAcceptable = minSimilarity >= currentConfig.getMinSimilarityThreshold();

        int currentScore = (int) (avgSimilarity * 100);

        // 判断错误
        ErrorType errorType;
        if (!isAngleCorrect) {
            errorType = ErrorType.ANGLE_DEVIATION;
        } else if (!isRhythmCorrect) {
            errorType = ErrorType.RHYTHM_DEVIATION;
        } else if (!isPostureAcceptable) {
            errorType = ErrorType.POSTURE_DEVIATION;
        } else {
            errorType = ErrorType.ANGLE_DEVIATION;
        }

        // 判断是否正确动作（三个条件都满足才计数）
        boolean isCorrect = isAngleCorrect && isRhythmCorrect && isPostureAcceptable;

        if (isCorrect) {
            completedCount++;
            totalScoreSum += avgSimilarity;
            int overallScore = (int) ((totalScoreSum / completedCount) * 100);

            if (listener != null) {
                listener.onActionComplete(overallScore, actualDuration, completedCount);
            }

            Log.d(TAG, String.format("正确动作! 相似度:%.1f%%, 次数:%d, 平均分:%d, 耗时:%.1fs",
                    avgSimilarity * 100, completedCount, overallScore, actualDuration / 1000f));
        } else {

            onActionError(currentScore, errorType, errorKeyFrames, correctFrameRef);

            Log.d(TAG, String.format("错误动作! 相似度:%.1f%%, 错误类型:%s, 最低相似度:%.1f%%, 匹配帧数:%d/%d",
                    avgSimilarity * 100, errorType.getDescription(), minSimilarity * 100,
                    currentActionMatchedFrames, totalStandardFrames));
        }

        resetForNextAction();
    }
    private void onActionError(int score, ErrorType errorType,
                               List<Bitmap> errorKeyFrames, Bitmap correctFrameRef) {
        if (listener != null) {
            listener.onActionError(score, errorType, errorKeyFrames, correctFrameRef);
        }
    }

    private void resetForNextAction() {
        standardFramePointer = 0;
        currentActionStartTime = System.currentTimeMillis();
        lastAlignmentTime = System.currentTimeMillis(); // 重置对齐时间
        similarityHistory.clear();
        frameTimestampHistory.clear();
        poseWindow.clear();
        matchOffsetHistory.clear();
        isInAlignmentPeriod = false;
        lastSmoothedSimilarity = 0f;
        isTimeoutTriggered = false;
        resetCurrentActionStats();

        // 重置超时计时器
        resetTimeoutTimer();
    }

    private float calculateCurrentSimilarity() {
        if (similarityHistory.isEmpty()) return 0f;
        float sum = 0f;
        for (float s : similarityHistory) sum += s;
        return sum / similarityHistory.size();
    }

    /**
     * 获取当前标准帧进度
     */
    public int getStandardFramePointer() {
        return standardFramePointer;
    }

    /**
     * 获取总标准帧数
     */
    public int getTotalStandardFrames() {
        return currentAction != null ? currentAction.getFrameCount() : 0;
    }

    public String getCurrentActionId() {
        return currentAction != null ? currentAction.getActionId() : null;
    }

    public int getCompletedCount() {
        return completedCount;
    }

    // ========== 常量定义 ==========
    private static final int MAX_ERROR_FRAMES = 10;
}