package com.example.fitness.sdk.core;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.camera.view.PreviewView;
import androidx.lifecycle.LifecycleOwner;

import com.example.fitness.sdk.config.CameraConfig;
import com.example.fitness.sdk.config.SDKConfig;
import com.example.fitness.sdk.listener.FitnessSDKListener;
import com.example.fitness.sdk.model.ActionData;
import com.example.fitness.sdk.model.ErrorType;
import com.example.fitness.sdk.model.Keypoint;
import com.example.fitness.sdk.model.SkeletonFrame;
import com.example.fitness.sdk.ui.PoseOverlayView;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class FitnessEngineImpl implements FitnessEngine {
    private static final String TAG = "FitnessEngineImpl";
    private static final String SDK_VERSION = "1.0.0";

    private Context context;
    private SDKConfig config;
    private FitnessSDKListener listener;
    private ActionMatcher actionMatcher;
    private PoseOverlayView poseOverlayView;
    private PoseLandmarker poseLandmarker;
    private CameraManager cameraManager;

    private final AtomicBoolean isInitialized = new AtomicBoolean(false);
    private final AtomicBoolean isSessionActive = new AtomicBoolean(false);
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private long lastCallbackTime = 0;
    private long callbackIntervalMs = 1000 / 30;
    private boolean isActionStarted = false;
    private int currentImageWidth = 640;
    private int currentImageHeight = 480;

    public void setContext(Context context) {
        this.context = context.getApplicationContext();
    }

    public PoseOverlayView getPoseOverlayView() {
        return poseOverlayView;
    }

    @Override
    public void init(SDKConfig config, FitnessSDKListener listener) {
        this.config = config != null ? config : SDKConfig.getDefault();
        this.listener = listener;
        this.callbackIntervalMs = 1000 / Math.min(this.config.getCallbackFps(), 30);



        executorService.execute(() -> {
            try {
                actionMatcher = new ActionMatcher();
                setupActionMatcherListener();
                initPoseLandmarker();
                isInitialized.set(true);
                notifyInitSuccess();
            } catch (Exception e) {
                Log.e(TAG, "初始化失败: " + e.getMessage(), e);
                notifyError(1000, "初始化失败: " + e.getMessage());
            }
        });
    }

    private void initPoseLandmarker() {
        try {
            String modelPath = "pose_landmarker.task";
            BaseOptions baseOptions = BaseOptions.builder()
                    .setModelAssetPath(modelPath)
                    .setDelegate(Delegate.CPU)
                    .build();

            PoseLandmarker.PoseLandmarkerOptions options =
                    PoseLandmarker.PoseLandmarkerOptions.builder()
                            .setBaseOptions(baseOptions)
                            .setRunningMode(RunningMode.VIDEO)
                            .build();

            poseLandmarker = PoseLandmarker.createFromOptions(context, options);
            Log.d(TAG, "MediaPipe 姿态检测器初始化成功");
        } catch (Exception e) {
            Log.e(TAG, "MediaPipe 初始化失败: " + e.getMessage(), e);
            throw new RuntimeException("姿态检测初始化失败", e);
        }
    }

    @Override
    public void release() {
        isInitialized.set(false);
        isSessionActive.set(false);
        if (cameraManager != null) {
            cameraManager.release();
            cameraManager = null;
        }
        if (poseLandmarker != null) {
            poseLandmarker.close();
            poseLandmarker = null;
        }
        if (actionMatcher != null) {
            actionMatcher.reset();
            actionMatcher = null;
        }
        executorService.shutdown();
        Log.d(TAG, "SDK已释放");
    }

    @Override
    public boolean openCamera(LifecycleOwner lifecycleOwner, PreviewView previewView, CameraConfig cameraConfig) {
        if (cameraManager == null) {
            cameraManager = new CameraManager(context, lifecycleOwner);
        }
        return cameraManager.openCamera(previewView, cameraConfig, this::processCameraFrame);
    }

    @Override
    public void closeCamera() {
        if (cameraManager != null) {
            cameraManager.closeCamera();
        }
    }

    private void processCameraFrame(Bitmap bitmap) {
        if (!isSessionActive.get() || poseLandmarker == null) return;

        try {
            currentImageWidth = bitmap.getWidth();
            currentImageHeight = bitmap.getHeight();

            long timestampMs = System.currentTimeMillis();
            MPImage mpImage = new BitmapImageBuilder(bitmap).build();
            PoseLandmarkerResult result = poseLandmarker.detectForVideo(mpImage, timestampMs);

            if (result != null && !result.landmarks().isEmpty()) {
                processPoseResult(result);
            }
        } catch (Exception e) {
            Log.e(TAG, "处理相机帧失败: " + e.getMessage(), e);
        }
    }

    private void processPoseResult(PoseLandmarkerResult result) {
        if (result == null || result.landmarks().isEmpty()) return;

        List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> mediapipeLandmarks = result.landmarks().get(0);

        List<Keypoint> keypoints = new ArrayList<>();
        for (int i = 0; i < mediapipeLandmarks.size() && i < 33; i++) {
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark lm = mediapipeLandmarks.get(i);
            float visibility = lm.visibility().isPresent() ? lm.visibility().get() : 0f;
            keypoints.add(new Keypoint(i, lm.x(), lm.y(), lm.z(), visibility));
        }

        float kneeAngle = calculateKneeAngle(keypoints, currentImageWidth, currentImageHeight);
        float hipAngle = calculateHipAngle(keypoints, currentImageWidth, currentImageHeight);
        float leftElbowAngle = calculateElbowAngle(keypoints, currentImageWidth, currentImageHeight, true);
        float rightElbowAngle = calculateElbowAngle(keypoints, currentImageWidth, currentImageHeight, false);
        float leftShoulderAngle = calculateShoulderAngle(keypoints, currentImageWidth, currentImageHeight, true);
        float rightShoulderAngle = calculateShoulderAngle(keypoints, currentImageWidth, currentImageHeight, false);
        float elbowAngle = (leftElbowAngle + rightElbowAngle) / 2;
        float shoulderAngle = (leftShoulderAngle + rightShoulderAngle) / 2;

        SkeletonFrame skeletonFrame = new SkeletonFrame.Builder()
                .setTimestampMs(System.currentTimeMillis())
                .setKeypoints(keypoints)
                .setHasValidPose(true)
                .setKneeAngle(kneeAngle)
                .setHipAngle(hipAngle)
                .setElbowAngle(elbowAngle)
                .setShoulderAngle(shoulderAngle)
                .setLeftElbowAngle(leftElbowAngle)
                .setRightElbowAngle(rightElbowAngle)
                .setLeftShoulderAngle(leftShoulderAngle)
                .setRightShoulderAngle(rightShoulderAngle)
                .build();

        poseOverlayView.setSkeletonFrame(skeletonFrame);

        if (actionMatcher != null) {
            float similarity = actionMatcher.processFrame(skeletonFrame);
            poseOverlayView.setSimilarityScore(similarity);

            long now = System.currentTimeMillis();
            if ((now - lastCallbackTime) >= callbackIntervalMs && listener != null) {
                lastCallbackTime = now;
                listener.onSkeletonFrame(null, keypoints);
            }

            if (similarity > 0.3f && !isActionStarted) {
                isActionStarted = true;
                if (listener != null) {
                    listener.onActionStart(actionMatcher.getCurrentActionId());
                }
            } else if (similarity < 0.1f) {
                isActionStarted = false;
            }
        }
    }
    public void setPoseOverlayView(PoseOverlayView overlayView) {
        this.poseOverlayView = overlayView;
    }
    @Override
    public void startSession() {
        if (!isInitialized.get()) {
            Log.w(TAG, "SDK未初始化");
            return;
        }
        isSessionActive.set(true);
        isActionStarted = false;
        if (actionMatcher != null) {
            actionMatcher.startSession();
        }
        Log.d(TAG, "会话已开始");
    }

    @Override
    public void stopSession() {
        isSessionActive.set(false);
        isActionStarted = false;
        if (actionMatcher != null) {
            actionMatcher.stopSession();
        }
        Log.d(TAG, "会话已停止");
    }

    @Override
    public boolean isSessionActive() {
        return isSessionActive.get();
    }

    @Override
    public void loadStandardAction(ActionData actionData) {
        if (actionData == null || actionMatcher == null) return;
        executorService.execute(() -> {
            actionMatcher.loadAction(actionData);
            Log.d(TAG, "已加载标准动作: " + actionData.getActionName());
        });
    }

    @Override
    public void switchAction(ActionData actionData) {
        if (actionData == null || actionMatcher == null) return;
        executorService.execute(() -> {
            actionMatcher.loadAction(actionData);
            Log.d(TAG, "已切换动作: " + actionData.getActionName());
            if (listener != null) {
                listener.onActionSwitched(actionData.getActionId());
            }
        });
    }

    @Override
    public void resetCounter() {
        if (actionMatcher != null) {
            actionMatcher.resetCounter();
        }
    }

    @Override
    public String getCurrentActionId() {
        return actionMatcher != null ? actionMatcher.getCurrentActionId() : null;
    }

    // ========== 角度计算方法 ==========
    private float calculateKneeAngle(List<Keypoint> keypoints, int w, int h) {
        Keypoint hip = findKeypoint(keypoints, 23);
        Keypoint knee = findKeypoint(keypoints, 25);
        Keypoint ankle = findKeypoint(keypoints, 27);
        if (hip != null && knee != null && ankle != null && hip.isValid() && knee.isValid() && ankle.isValid()) {
            return calculateAngle(hip, knee, ankle, w, h);
        }
        return -1f;
    }

    private float calculateHipAngle(List<Keypoint> keypoints, int w, int h) {
        Keypoint shoulder = findKeypoint(keypoints, 11);
        Keypoint hip = findKeypoint(keypoints, 23);
        Keypoint knee = findKeypoint(keypoints, 25);
        if (shoulder != null && hip != null && knee != null && shoulder.isValid() && hip.isValid() && knee.isValid()) {
            return calculateAngle(shoulder, hip, knee, w, h);
        }
        return -1f;
    }

    private float calculateElbowAngle(List<Keypoint> keypoints, int w, int h, boolean isLeft) {
        int shoulderId = isLeft ? 11 : 12;
        int elbowId = isLeft ? 13 : 14;
        int wristId = isLeft ? 15 : 16;
        Keypoint shoulder = findKeypoint(keypoints, shoulderId);
        Keypoint elbow = findKeypoint(keypoints, elbowId);
        Keypoint wrist = findKeypoint(keypoints, wristId);
        if (shoulder != null && elbow != null && wrist != null && shoulder.isValid() && elbow.isValid() && wrist.isValid()) {
            return calculateAngle(shoulder, elbow, wrist, w, h);
        }
        return -1f;
    }

    private float calculateShoulderAngle(List<Keypoint> keypoints, int w, int h, boolean isLeft) {
        int hipId = isLeft ? 23 : 24;
        int shoulderId = isLeft ? 11 : 12;
        int elbowId = isLeft ? 13 : 14;
        Keypoint hip = findKeypoint(keypoints, hipId);
        Keypoint shoulder = findKeypoint(keypoints, shoulderId);
        Keypoint elbow = findKeypoint(keypoints, elbowId);
        if (hip != null && shoulder != null && elbow != null && hip.isValid() && shoulder.isValid() && elbow.isValid()) {
            return calculateAngle(hip, shoulder, elbow, w, h);
        }
        return -1f;
    }

    private float calculateAngle(Keypoint a, Keypoint b, Keypoint c, int w, int h) {
        double ax = a.getX() * w, ay = a.getY() * h;
        double bx = b.getX() * w, by = b.getY() * h;
        double cx = c.getX() * w, cy = c.getY() * h;

        double baX = ax - bx, baY = ay - by;
        double bcX = cx - bx, bcY = cy - by;

        double dot = baX * bcX + baY * bcY;
        double magBA = Math.sqrt(baX * baX + baY * baY);
        double magBC = Math.sqrt(bcX * bcX + bcY * bcY);

        if (magBA < 0.001 || magBC < 0.001) return 0f;
        double cos = dot / (magBA * magBC);
        cos = Math.max(-1.0, Math.min(1.0, cos));
        return (float) Math.toDegrees(Math.acos(cos));
    }

    private Keypoint findKeypoint(List<Keypoint> keypoints, int id) {
        for (Keypoint kp : keypoints) {
            if (kp.getId() == id) return kp;
        }
        return null;
    }

    private void setupActionMatcherListener() {
        if (actionMatcher == null) return;
        actionMatcher.setListener(new ActionMatcher.MatcherListener() {
            @Override
            public void onActionStart() {
                if (listener != null) listener.onActionStart(actionMatcher.getCurrentActionId());
            }
            @Override
            public void onActionComplete(int score, long durationMs, int completedCount) {
                if (listener != null) {
                    listener.onActionSuccess(score, durationMs, actionMatcher.getCurrentActionId(), completedCount);
                }
            }
            @Override
            public void onActionError(int score, ErrorType errorType) {
                if (listener != null) listener.onActionError(score, errorType, null, null);
            }
            @Override
            public void onSimilarityUpdate(float similarity, int matchedFrameIndex) {}
        });
    }

    private void notifyInitSuccess() {
        mainHandler.post(() -> {
            if (listener != null) listener.onInitSuccess(SDK_VERSION);
        });
    }

    private void notifyError(int code, String message) {
        mainHandler.post(() -> {
            if (listener != null) listener.onInitError(code, message);
        });
    }
}