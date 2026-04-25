package com.example.fitness.sdk;

import android.content.Context;

import com.example.fitness.sdk.config.CameraConfig;
import com.example.fitness.sdk.config.SDKConfig;
import com.example.fitness.sdk.core.FitnessEngine;
import com.example.fitness.sdk.core.FitnessEngineImpl;
import com.example.fitness.sdk.listener.FitnessSDKListener;
import com.example.fitness.sdk.model.ActionData;
import com.example.fitness.sdk.model.CameraFrame;
import com.example.fitness.sdk.model.NormalizedLandmark;
import com.example.fitness.sdk.ui.PoseOverlayView;

import java.util.List;

/**
 * 健身动作识别SDK对外入口（单例）
 */
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

    public void attachOverlayView(PoseOverlayView overlayView) {
        checkInitialized();
        if (engine instanceof FitnessEngineImpl) {
            ((FitnessEngineImpl) engine).attachOverlayView(overlayView);
        }
    }

    public void updateLandmarks(List<NormalizedLandmark> landmarks, int imageWidth, int imageHeight) {
        checkInitialized();
        engine.updateLandmarks(landmarks, imageWidth, imageHeight);
    }

    public void release() {
        if (engine != null) {
            engine.release();
            engine = null;
        }
        isInitialized = false;
        instance = null;
    }

    public boolean isSessionActive() {
        checkInitialized();
        return engine.isSessionActive();
    }

    private void checkInitialized() {
        if (!isInitialized || engine == null) {
            throw new IllegalStateException("SDK未初始化，请先调用 init()");
        }
    }
}