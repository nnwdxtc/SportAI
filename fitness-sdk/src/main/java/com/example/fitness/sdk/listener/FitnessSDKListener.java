package com.example.fitness.sdk.listener;

import android.graphics.Bitmap;
import com.example.fitness.sdk.model.ErrorType;
import com.example.fitness.sdk.model.SkeletonFrame;

import java.util.List;

/**
 * SDK回调接口
 */
public interface FitnessSDKListener {

    void onInitSuccess(String sdkVersion);

    void onInitError(int code, String message);

    /**
     * 骨骼帧回调
     * @param frame 原始相机帧
     * @param skeletonFrame 骨骼数据
     * @param similarity 当前相似度 (0-1)
     */
    void onSkeletonFrame(Bitmap frame, SkeletonFrame skeletonFrame, float similarity);

    void onActionStart(String actionId);

    void onActionSuccess(int score, long durationMs, String actionId, int completedCount);

    void onActionError(int score, ErrorType errorType, Bitmap errorFrame, Bitmap correctFrameRef);

    void onActionSwitched(String newActionId);

    void onActionTimeout(String actionId);
}