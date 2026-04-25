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
     * @param frame 原始相机帧（可为null）
     * @param keypoints 关键点列表
     */
    void onSkeletonFrame(Bitmap frame, List<Keypoint> keypoints);

    void onActionStart(String actionId);

    void onActionSuccess(int score, long durationMs, String actionId, int completedCount);

    void onActionError(int score, ErrorType errorType, Bitmap errorFrame, Bitmap correctFrameRef);

    void onActionSwitched(String newActionId);

    void onActionTimeout(String actionId);
}