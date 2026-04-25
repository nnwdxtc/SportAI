package com.example.fitness.sdk.core;

import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import com.example.fitness.sdk.config.CameraConfig;
import com.example.fitness.sdk.config.SDKConfig;
import com.example.fitness.sdk.listener.FitnessSDKListener;
import com.example.fitness.sdk.model.ActionData;

public interface FitnessEngine {

    void init(SDKConfig config, FitnessSDKListener listener);

    void release();

    boolean openCamera(LifecycleOwner lifecycleOwner, PreviewView previewView, CameraConfig cameraConfig);

    void closeCamera();

    void startSession();

    void stopSession();

    boolean isSessionActive();

    void loadStandardAction(ActionData actionData);

    void switchAction(ActionData actionData);

    void resetCounter();

    String getCurrentActionId();
}