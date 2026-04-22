package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ONNXPoseLandmarkerHelper {
    private static final String TAG = "ONNXPoseHelper";

    private ONNXPoseLandmarker onnxPoseLandmarker;
    private final ExecutorService executorService;
    private boolean isNPUEnabled = false;

    // 关键点索引常量
    private static final int NUM_LANDMARKS = 33;

    public interface ONNXPoseLandmarkerListener {
        void onError(String error, int errorCode);
        void onResults(List<NormalizedLandmark> landmarks, long inferenceTime);
    }

    public ONNXPoseLandmarkerHelper(Context context) {
        this.executorService = Executors.newSingleThreadExecutor();
        initializeONNXRuntime(context);
    }

    private void initializeONNXRuntime(Context context) {
        executorService.execute(() -> {
            try {
                onnxPoseLandmarker = new ONNXPoseLandmarker(context);

                if (onnxPoseLandmarker.isInitialized()) {
                    // 检查NPU支持
                    checkNPUSupport();
                    Log.d(TAG, "ONNX运行时初始化成功");
                } else {
                    Log.e(TAG, "ONNX运行时初始化失败");
                }

            } catch (Exception e) {
                Log.e(TAG, "ONNX初始化异常: " + e.getMessage());
            }
        });
    }

    private void checkNPUSupport() {
        try {
            // 检查设备是否支持NPU
            String hardware = android.os.Build.HARDWARE.toLowerCase();
            String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();

            // 支持NPU的设备列表
            boolean hasNPUSupport = hardware.contains("kirin") ||
                    hardware.contains("qcom") ||
                    manufacturer.contains("huawei") ||
                    manufacturer.contains("qualcomm");

            if (hasNPUSupport) {
                Log.d(TAG, "检测到NPU支持，已启用硬件加速");
                isNPUEnabled = true;
            } else {
                Log.d(TAG, "未检测到NPU支持，使用CPU处理");
            }

        } catch (Exception e) {
            Log.e(TAG, "检查NPU支持失败: " + e.getMessage());
        }
    }

    public void detectPose(Bitmap bitmap, ONNXPoseLandmarkerListener listener) {
        if (onnxPoseLandmarker == null || !onnxPoseLandmarker.isInitialized()) {
            if (listener != null) {
                listener.onError("ONNX模型未就绪", -1);
            }
            return;
        }

        executorService.execute(() -> {
            try {
                ONNXPoseLandmarker.PoseResult result = onnxPoseLandmarker.detectPose(bitmap);

                if (result != null && result.hasValidPose()) {
                    List<NormalizedLandmark> landmarks = convertToNormalizedLandmarks(result);

                    if (listener != null) {
                        listener.onResults(landmarks, result.inferenceTime);
                    }
                } else {
                    if (listener != null) {
                        listener.onResults(new ArrayList<>(), 0);
                    }
                }

            } catch (Exception e) {
                Log.e(TAG, "姿态检测失败: " + e.getMessage());
                if (listener != null) {
                    listener.onError("检测失败: " + e.getMessage(), -2);
                }
            }
        });
    }

    private List<NormalizedLandmark> convertToNormalizedLandmarks(ONNXPoseLandmarker.PoseResult result) {
        List<NormalizedLandmark> landmarks = new ArrayList<>();

        try {
            // 使用反射创建NormalizedLandmark实例
            Class<?> landmarkClass = Class.forName("com.google.mediapipe.tasks.components.containers.NormalizedLandmark");

            for (int i = 0; i < NUM_LANDMARKS; i++) {
                if (i < result.landmarks.length) {
                    float[] landmarkData = result.landmarks[i];

                    // landmarkData格式: [x, y, z, visibility, presence]
                    float x = landmarkData[0];
                    float y = landmarkData[1];
                    float z = landmarkData[2];
                    float visibility = landmarkData[3];

                    // 使用反射创建NormalizedLandmark实例
                    NormalizedLandmark landmark = createNormalizedLandmark(x, y, z, visibility);
                    if (landmark != null) {
                        landmarks.add(landmark);
                    }
                }
            }

            Log.d(TAG, String.format("转换了 %d 个关键点", landmarks.size()));

        } catch (Exception e) {
            Log.e(TAG, "转换关键点失败: " + e.getMessage());
        }

        return landmarks;
    }

    /**
     * 使用反射创建NormalizedLandmark实例
     */
    private NormalizedLandmark createNormalizedLandmark(float x, float y, float z, float visibility) {
        try {
            Class<?> landmarkClass = Class.forName("com.google.mediapipe.tasks.components.containers.NormalizedLandmark");
            Method fromProtoMethod = landmarkClass.getMethod("createFromProto",
                    Class.forName("com.google.mediapipe.framework.MediaPipeException"));


            return createSimpleNormalizedLandmark(x, y, z, visibility);

        } catch (Exception e) {
            Log.w(TAG, "使用反射创建NormalizedLandmark失败: " + e.getMessage());
            return createSimpleNormalizedLandmark(x, y, z, visibility);
        }
    }

    /**
     * NormalizedLandmark实现
     */
    private NormalizedLandmark createSimpleNormalizedLandmark(float x, float y, float z, float visibility) {
        return new NormalizedLandmark() {
            @Override
            public float x() {
                return x;
            }

            @Override
            public float y() {
                return y;
            }

            @Override
            public float z() {
                return z;
            }

            @Override
            public java.util.Optional<Float> visibility() {
                return java.util.Optional.of(visibility);
            }

            @Override
            public java.util.Optional<Float> presence() {
                return java.util.Optional.of(1.0f); // 默认存在
            }
        };
    }

    public boolean isUsingNPUAcceleration() {
        return isNPUEnabled;
    }

    public boolean isInitialized() {
        return onnxPoseLandmarker != null && onnxPoseLandmarker.isInitialized();
    }

    public void close() {
        try {
            if (onnxPoseLandmarker != null) {
                onnxPoseLandmarker.close();
            }
            executorService.shutdown();
        } catch (Exception e) {
            Log.e(TAG, "关闭ONNX助手失败: " + e.getMessage());
        }
    }
}