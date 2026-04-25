package com.example.fitness.sdk.core;

import com.example.fitness.sdk.config.SDKConfig;
import com.example.fitness.sdk.listener.FitnessSDKListener;
import com.example.fitness.sdk.model.ActionData;
import com.example.fitness.sdk.model.NormalizedLandmark;  // 添加这行导入

import java.util.List;

public interface FitnessEngine {

    void init(SDKConfig config, FitnessSDKListener listener);

    void release();

    void startSession();

    void stopSession();

    boolean isSessionActive();

    void loadStandardAction(ActionData actionData);

    void resetCounter();

    String getCurrentActionId();

    void updateLandmarks(List<NormalizedLandmark> landmarks, int imageWidth, int imageHeight);

    void attachOverlayView(Object overlayView);
}