package com.example.fitness.sdk;

import android.content.Context;
import android.view.ViewGroup;

import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import com.example.fitness.sdk.config.CameraConfig;
import com.example.fitness.sdk.config.SDKConfig;
import com.example.fitness.sdk.core.FitnessEngine;
import com.example.fitness.sdk.core.FitnessEngineImpl;
import com.example.fitness.sdk.listener.FitnessSDKListener;
import com.example.fitness.sdk.model.ActionData;
import com.example.fitness.sdk.model.CameraFrame;
import com.example.fitness.sdk.ui.PoseOverlayView;

public class FitnessSDK {

    private static volatile FitnessSDK instance;
    private FitnessEngine engine;
    private boolean isInitialized = false;

    private FitnessSDK() {}

    public static FitnessSDK getInstance() {
        if (instance == null) {
            synchronized (FitnessSDK.class) {
                if (instance == null) {
                    instance = new FitnessSDK();
                }
            }
        }
        return instance;
    }

    public void init(Context context, SDKConfig config, FitnessSDKListener listener) {
        if (isInitialized) {
            if (listener != null) {
                listener.onInitError(1001, "SDK已初始化，请勿重复初始化");
            }
            return;
        }
        engine = new FitnessEngineImpl();
        ((FitnessEngineImpl) engine).setContext(context);
        engine.init(config, listener);
        isInitialized = true;
    }

    public boolean openCamera(LifecycleOwner lifecycleOwner, PreviewView previewView, CameraConfig cameraConfig) {
        checkInitialized();
        return engine.openCamera(lifecycleOwner, previewView, cameraConfig);
    }

    public void closeCamera() {
        checkInitialized();
        engine.closeCamera();
    }

    public void startSession() {
        checkInitialized();
        engine.startSession();
    }

    public void stopSession() {
        checkInitialized();
        engine.stopSession();
    }

    public void loadStandardAction(ActionData actionData) {
        checkInitialized();
        engine.loadStandardAction(actionData);
    }

    public void switchAction(ActionData actionData) {
        checkInitialized();
        engine.switchAction(actionData);
    }

    public void resetCounter() {
        checkInitialized();
        engine.resetCounter();
    }

    public String getCurrentActionId() {
        checkInitialized();
        return engine.getCurrentActionId();
    }

    public boolean isSessionActive() {
        checkInitialized();
        return engine.isSessionActive();
    }

    public PoseOverlayView getPoseOverlayView() {
        checkInitialized();
        return ((FitnessEngineImpl) engine).getPoseOverlayView();
    }

    public void release() {
        if (engine != null) {
            engine.release();
            engine = null;
        }
        isInitialized = false;
        instance = null;
    }

    private void checkInitialized() {
        if (!isInitialized || engine == null) {
            throw new IllegalStateException("SDK未初始化，请先调用 init()");
        }
    }
    public void setPoseOverlayView(PoseOverlayView overlayView) {
        checkInitialized();
        ((FitnessEngineImpl) engine).setPoseOverlayView(overlayView);
    }
    public void updateFrame(CameraFrame cameraFrame) {
        checkInitialized();
        ((FitnessEngineImpl) engine).updateFrame(cameraFrame);
    }
}