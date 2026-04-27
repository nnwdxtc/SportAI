package com.example.fitness.sdk.listener;

import android.graphics.Bitmap;
import com.example.fitness.sdk.model.ErrorType;
import com.example.fitness.sdk.model.Keypoint;

import java.util.List;

public interface FitnessSDKListener {

    void onInitSuccess(String sdkVersion);

    void onInitError(int code, String message);

    /**
     * 骨骼帧回调
     * @param frame 原始相机帧
     * @param keypoints 关键点列表
     */
    void onSkeletonFrame(Bitmap frame, List<Keypoint> keypoints);

    void onActionStart(String actionId);

    /**
     * 正确动作完成
     * @param score 得分 (0-100)
     * @param durationMs 耗时（毫秒）
     * @param actionId 动作ID
     * @param completedCount 已完成次数
     */
    void onActionSuccess(int score, long durationMs, String actionId, int completedCount);

    /**
     * 错误动作完成
     * @param score 得分 (0-100)
     * @param errorType 错误类型
     * @param errorKeyFrames 错误关键帧列表
     * @param correctFrameRef 正确动作参考帧
     */
    void onActionError(int score, ErrorType errorType, List<Bitmap> errorKeyFrames, Bitmap correctFrameRef);

    void onActionSwitched(String newActionId);

    void onActionTimeout(String actionId);
}