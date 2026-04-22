package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;
import ai.onnxruntime.*;
import java.nio.FloatBuffer;
import java.util.*;
//AI辅助生成：Kimi K2.5 2025.12.3
public class ONNXPoseLandmarker {
    private static final String TAG = "ONNXPoseLandmarker";

    // 模型配置
    private static final int INPUT_SIZE = 256;
    private static final float MIN_SCORE_THRESHOLD = 0.5f;

    private OrtEnvironment environment;
    private OrtSession session;
    private boolean isInitialized = false;

    public ONNXPoseLandmarker(Context context) {
        initializeModel(context);
    }

    private void initializeModel(Context context) {
        try {
            environment = OrtEnvironment.getEnvironment();

            // 从assets加载模型
            java.io.InputStream modelStream = context.getAssets().open("pose_landmark_heavy.onnx");
            byte[] modelData = new byte[modelStream.available()];
            modelStream.read(modelData);
            modelStream.close();

            OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions();

            // 使用NNAPI加速
            try {
                sessionOptions.addNnapi();
                Log.d(TAG, "NNAPI加速已启用");
            } catch (Exception e) {
                Log.w(TAG, "NNAPI不可用，使用CPU: " + e.getMessage());
            }

            // 设置优化选项
            sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            sessionOptions.setMemoryPatternOptimization(true);

            session = environment.createSession(modelData, sessionOptions);
            isInitialized = true;

            Log.d(TAG, "ONNX模型加载成功");

        } catch (Exception e) {
            Log.e(TAG, "ONNX模型初始化失败: " + e.getMessage());
            isInitialized = false;
        }
    }

    public PoseResult detectPose(Bitmap bitmap) {
        if (!isInitialized || session == null) {
            Log.e(TAG, "ONNX模型未初始化");
            return null;
        }

        try {
            // 预处理图像
            float[][][][] inputData = preprocessImage(bitmap);

            // 创建输入tensor
            long[] shape = {1, INPUT_SIZE, INPUT_SIZE, 3};
            OnnxTensor inputTensor = OnnxTensor.createTensor(environment, FloatBuffer.wrap(flattenArray(inputData)), shape);

            // 运行推理
            long startTime = System.currentTimeMillis();
            OrtSession.Result results = session.run(Collections.singletonMap("input_1", inputTensor));
            long inferenceTime = System.currentTimeMillis() - startTime;

            // 获取输出
            float[][] landmarks = new float[][]{((float[][]) results.get(0).getValue())[0]};
            float[][] worldLandmarks = new float[][]{((float[][]) results.get(1).getValue())[0]};
            float[] segmentationMask = ((float[][]) results.get(2).getValue())[0];
            float[] presenceScore = ((float[][]) results.get(3).getValue())[0];

            inputTensor.close();

            Log.d(TAG, String.format("ONNX推理完成 - 耗时: %dms", inferenceTime));

            return new PoseResult(landmarks, worldLandmarks, presenceScore, inferenceTime);

        } catch (Exception e) {
            Log.e(TAG, "ONNX推理失败: " + e.getMessage());
            return null;
        }
    }

    private float[][][][] preprocessImage(Bitmap bitmap) {
        // 调整图像大小
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);

        float[][][][] inputData = new float[1][INPUT_SIZE][INPUT_SIZE][3];

        // 转换为RGB并归一化
        for (int y = 0; y < INPUT_SIZE; y++) {
            for (int x = 0; x < INPUT_SIZE; x++) {
                int pixel = resizedBitmap.getPixel(x, y);

                // 提取RGB通道并归一化到[0,1]
                inputData[0][y][x][0] = ((pixel >> 16) & 0xFF) / 255.0f; // R
                inputData[0][y][x][1] = ((pixel >> 8) & 0xFF) / 255.0f;  // G
                inputData[0][y][x][2] = (pixel & 0xFF) / 255.0f;         // B
            }
        }

        if (resizedBitmap != bitmap) {
            resizedBitmap.recycle();
        }

        return inputData;
    }

    private float[] flattenArray(float[][][][] array) {
        int totalSize = 1 * INPUT_SIZE * INPUT_SIZE * 3;
        float[] flat = new float[totalSize];
        int index = 0;

        for (int i = 0; i < INPUT_SIZE; i++) {
            for (int j = 0; j < INPUT_SIZE; j++) {
                for (int k = 0; k < 3; k++) {
                    flat[index++] = array[0][i][j][k];
                }
            }
        }

        return flat;
    }

    public void close() {
        try {
            if (session != null) {
                session.close();
            }
            if (environment != null) {
                environment.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "关闭ONNX资源失败: " + e.getMessage());
        }
    }

    public boolean isInitialized() {
        return isInitialized;
    }

    // 姿态检测结果类
    public static class PoseResult {
        public final float[][] landmarks;        // [33, 5] - x, y, z, visibility, presence
        public final float[][] worldLandmarks;   // [33, 3] - x, y, z
        public final float[] presenceScore;
        public final long inferenceTime;

        public PoseResult(float[][] landmarks, float[][] worldLandmarks,
                          float[] presenceScore, long inferenceTime) {
            this.landmarks = landmarks;
            this.worldLandmarks = worldLandmarks;
            this.presenceScore = presenceScore;
            this.inferenceTime = inferenceTime;
        }

        public boolean hasValidPose() {
            return presenceScore != null && presenceScore.length > 0 && presenceScore[0] > MIN_SCORE_THRESHOLD;
        }
    }
}