package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import android.graphics.Matrix;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

public class VideoProcessor {
    private static final String TAG = "VideoProcessor";
    private final Context context;
    private PoseLandmarkerHelper poseLandmarkerHelper;
    private final Handler mainHandler;
    private final ExecutorService executorService;
    private static final float MIN_VISIBILITY_THRESHOLD = 0.3f;

    // 视频尺寸
    private int videoWidth = 0;
    private int videoHeight = 0;

    public interface VideoProcessingCallback {
        void onProgress(int progress);
        void onComplete(List<VideoFrameData> frameDataList, int rotation, float frameRate);
        void onError(String error);
    }

    public static class VideoFrameData {
        public final float leftKneeAngle;
        public final float rightKneeAngle;
        public final float leftHipAngle;
        public final float rightHipAngle;
        public final float avgKneeAngle;
        public final float avgHipAngle;
        public final float hipHeight;
        public final long timestamp;
        public final List<NormalizedLandmark> landmarks;
        public final boolean hasValidPose;
        public final String debugInfo;
        public final float leftElbowAngle;
        public final float rightElbowAngle;
        public final float leftShoulderAngle;
        public final float rightShoulderAngle;
        public final float avgElbowAngle;
        public final float avgShoulderAngle;

        // 新增：原始像素坐标信息（用于调试和验证）
        public final int frameWidth;
        public final int frameHeight;

        public VideoFrameData(float leftKneeAngle, float rightKneeAngle,
                              float leftHipAngle, float rightHipAngle,
                              float leftElbowAngle, float rightElbowAngle,
                              float leftShoulderAngle, float rightShoulderAngle,
                              float avgKneeAngle, float avgHipAngle,
                              float avgElbowAngle, float avgShoulderAngle,
                              float hipHeight, long timestamp,
                              List<NormalizedLandmark> landmarks, boolean hasValidPose,
                              String debugInfo, int frameWidth, int frameHeight) {
            this.leftKneeAngle = leftKneeAngle;
            this.rightKneeAngle = rightKneeAngle;
            this.leftHipAngle = leftHipAngle;
            this.rightHipAngle = rightHipAngle;
            this.leftElbowAngle = leftElbowAngle;
            this.rightElbowAngle = rightElbowAngle;
            this.leftShoulderAngle = leftShoulderAngle;
            this.rightShoulderAngle = rightShoulderAngle;
            this.avgKneeAngle = avgKneeAngle;
            this.avgHipAngle = avgHipAngle;
            this.avgElbowAngle = avgElbowAngle;
            this.avgShoulderAngle = avgShoulderAngle;
            this.hipHeight = hipHeight;
            this.timestamp = timestamp;
            this.landmarks = landmarks;
            this.hasValidPose = hasValidPose;
            this.debugInfo = debugInfo;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
        }
    }

    public VideoProcessor(Context context) {
        this.context = context;
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.executorService = Executors.newSingleThreadExecutor();
        initializePoseLandmarker();
    }

    private void initializePoseLandmarker() {
        poseLandmarkerHelper = new PoseLandmarkerHelper(
                0.5f,
                0.5f,
                0.5f,
                RunningMode.VIDEO,
                Delegate.GPU,
                context,
                null
        );
    }

    // 关键点可见性检查
    private static boolean isLandmarkValid(NormalizedLandmark landmark) {
        return landmark != null &&
                landmark.visibility().isPresent() &&
                landmark.visibility().get() >= MIN_VISIBILITY_THRESHOLD &&
                landmark.x() >= 0 && landmark.x() <= 1 &&
                landmark.y() >= 0 && landmark.y() <= 1;
    }

    // 检查全身关键点可见性
    private boolean isFullBodyVisible(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() < 33) {
            return false;
        }

        int[] requiredIndices = {
                PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
                PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
                PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
                PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
                PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
                PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST
        };

        int visibleCount = 0;
        for (int index : requiredIndices) {
            if (index < landmarks.size() && isLandmarkValid(landmarks.get(index))) {
                visibleCount++;
            }
        }
        return visibleCount >= 8; // 至少8个关键点可见
    }


    private double calculateAngleWithPixelCoordinates(
            NormalizedLandmark a, NormalizedLandmark b, NormalizedLandmark c,
            int imageWidth, int imageHeight) {

        if (!isLandmarkValid(a) || !isLandmarkValid(b) || !isLandmarkValid(c)) {
            return 0.0;
        }

        try {
            // 1. 将归一化坐标转换为像素坐标
            double aX = a.x() * imageWidth;
            double aY = a.y() * imageHeight;
            double bX = b.x() * imageWidth;
            double bY = b.y() * imageHeight;
            double cX = c.x() * imageWidth;
            double cY = c.y() * imageHeight;

            // 2. 计算向量 BA 和 BC
            double baX = aX - bX;
            double baY = aY - bY;
            double bcX = cX - bX;
            double bcY = cY - bY;

            // 3. 计算点积
            double dotProduct = (baX * bcX) + (baY * bcY);

            // 4. 计算向量模长
            double magnitudeBA = Math.sqrt(baX * baX + baY * baY);
            double magnitudeBC = Math.sqrt(bcX * bcX + bcY * bcY);

            if (magnitudeBA < 0.001 || magnitudeBC < 0.001) {
                return 0.0;
            }

            // 5. 计算余弦值（限制在[-1, 1]范围内）
            double cosAngle = dotProduct / (magnitudeBA * magnitudeBC);
            cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));

            // 6. 转换为角度
            double angle = Math.toDegrees(Math.acos(cosAngle));

            // 7. 确保角度在合理范围内 [0, 180]
            return Math.min(180.0, Math.max(0.0, angle));

        } catch (Exception e) {
            Log.e(TAG, "像素坐标角度计算错误: " + e.getMessage());
            return 0.0;
        }
    }

    /**
     * 专门计算膝关节角度（使用像素坐标）
     */
    private double calculateKneeAngleWithPixels(
            NormalizedLandmark hip, NormalizedLandmark knee, NormalizedLandmark ankle,
            int imageWidth, int imageHeight) {

        if (!isLandmarkValid(hip) || !isLandmarkValid(knee) || !isLandmarkValid(ankle)) {
            return 0.0;
        }

        double angle = calculateAngleWithPixelCoordinates(hip, knee, ankle, imageWidth, imageHeight);

        // 调试日志
        if (angle > 10 && angle < 170) {
            Log.v(TAG, String.format("膝关节角度(像素坐标): %.1f° (图像尺寸: %dx%d)",
                    angle, imageWidth, imageHeight));
        }

        return angle;
    }

    /**
     * 专门计算髋关节角度（使用像素坐标）
     */
    private double calculateHipAngleWithPixels(
            NormalizedLandmark shoulder, NormalizedLandmark hip, NormalizedLandmark knee,
            int imageWidth, int imageHeight) {

        if (!isLandmarkValid(shoulder) || !isLandmarkValid(hip) || !isLandmarkValid(knee)) {
            return 0.0;
        }

        double angle = calculateAngleWithPixelCoordinates(shoulder, hip, knee, imageWidth, imageHeight);

        // 调试日志
        if (angle > 10 && angle < 170) {
            Log.v(TAG, String.format("髋关节角度(像素坐标): %.1f° (图像尺寸: %dx%d)",
                    angle, imageWidth, imageHeight));
        }

        return angle;
    }

    /**
     * 计算肘关节角度（使用像素坐标）
     */
    private double calculateElbowAngleWithPixels(
            NormalizedLandmark shoulder, NormalizedLandmark elbow, NormalizedLandmark wrist,
            int imageWidth, int imageHeight) {

        if (!isLandmarkValid(shoulder) || !isLandmarkValid(elbow) || !isLandmarkValid(wrist)) {
            return 0.0;
        }

        double angle = calculateAngleWithPixelCoordinates(shoulder, elbow, wrist, imageWidth, imageHeight);

        if (angle > 10 && angle < 170) {
            Log.v(TAG, String.format("肘关节角度(像素坐标): %.1f°", angle));
        }

        return angle;
    }

    /**
     * 计算肩关节角度（使用像素坐标）
     */
    private double calculateShoulderAngleWithPixels(
            NormalizedLandmark hip, NormalizedLandmark shoulder, NormalizedLandmark elbow,
            int imageWidth, int imageHeight) {

        if (!isLandmarkValid(hip) || !isLandmarkValid(shoulder) || !isLandmarkValid(elbow)) {
            return 0.0;
        }

        double angle = calculateAngleWithPixelCoordinates(hip, shoulder, elbow, imageWidth, imageHeight);

        if (angle > 10 && angle < 170) {
            Log.v(TAG, String.format("肩关节角度(像素坐标): %.1f°", angle));
        }

        return angle;
    }

    // 计算髋部高度（使用像素坐标）
    public static float calculateHipHeightWithPixels(List<NormalizedLandmark> landmarks, int imageHeight) {
        if (landmarks == null || landmarks.size() <= Math.max(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)) {
            return 0f;
        }

        NormalizedLandmark leftHip = landmarks.get(PoseLandmark.LEFT_HIP);
        NormalizedLandmark rightHip = landmarks.get(PoseLandmark.RIGHT_HIP);

        if (isLandmarkValid(leftHip) && isLandmarkValid(rightHip)) {
            // 使用像素坐标计算高度
            float leftHipY = leftHip.y() * imageHeight;
            float rightHipY = rightHip.y() * imageHeight;
            float avgY = (leftHipY + rightHipY) / 2.0f;

            // 转换为高度值（0-1范围，顶部为0，底部为1）
            float height = avgY / imageHeight;
            return Math.max(0, Math.min(1, height));
        }

        return 0f;
    }

    // 角度计算结果容器类
    private static class AngleCalculationResult {
        float leftKneeAngle = 0f;
        float rightKneeAngle = 0f;
        float leftHipAngle = 0f;
        float rightHipAngle = 0f;
        float leftElbowAngle = 0f;
        float rightElbowAngle = 0f;
        float leftShoulderAngle = 0f;
        float rightShoulderAngle = 0f;
        float avgKneeAngle = 0f;
        float avgHipAngle = 0f;
        float avgElbowAngle = 0f;
        float avgShoulderAngle = 0f;
        boolean hasValidAngles = false;
        String debugInfo = "";
    }

    // 改进的角度计算：检查关键点可见性
    private boolean areLandmarksValidForAngleCalculation(List<NormalizedLandmark> landmarks, int... indices) {
        if (landmarks == null || landmarks.size() < 33) {
            return false;
        }

        int validCount = 0;
        for (int index : indices) {
            if (index < landmarks.size() && isLandmarkValid(landmarks.get(index))) {
                validCount++;
            }
        }
        return validCount == indices.length;
    }

    // 计算所有关键角度（使用像素坐标的版本）
    private AngleCalculationResult calculateAllAnglesWithPixels(
            List<NormalizedLandmark> landmarks,
            int frameIndex,
            int imageWidth,
            int imageHeight) {

        AngleCalculationResult result = new AngleCalculationResult();
        StringBuilder debug = new StringBuilder();

        if (landmarks == null || landmarks.size() < 33) {
            result.debugInfo = "关键点不足";
            return result;
        }

        try {
            debug.append("帧").append(frameIndex).append("(像素坐标): ");

            // 检查关键点可见性
            boolean leftKneeValid = areLandmarksValidForAngleCalculation(landmarks,
                    PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE);
            boolean rightKneeValid = areLandmarksValidForAngleCalculation(landmarks,
                    PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE);
            boolean leftHipValid = areLandmarksValidForAngleCalculation(landmarks,
                    PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE);
            boolean rightHipValid = areLandmarksValidForAngleCalculation(landmarks,
                    PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE);
            boolean leftElbowValid = areLandmarksValidForAngleCalculation(landmarks,
                    PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST);
            boolean rightElbowValid = areLandmarksValidForAngleCalculation(landmarks,
                    PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST);
            boolean leftShoulderValid = areLandmarksValidForAngleCalculation(landmarks,
                    PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW);
            boolean rightShoulderValid = areLandmarksValidForAngleCalculation(landmarks,
                    PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW);

            // 计算各个角度（使用像素坐标）
            if (leftKneeValid) {
                NormalizedLandmark leftHip = landmarks.get(PoseLandmark.LEFT_HIP);
                NormalizedLandmark leftKnee = landmarks.get(PoseLandmark.LEFT_KNEE);
                NormalizedLandmark leftAnkle = landmarks.get(PoseLandmark.LEFT_ANKLE);
                result.leftKneeAngle = (float) calculateKneeAngleWithPixels(
                        leftHip, leftKnee, leftAnkle, imageWidth, imageHeight);
                if (result.leftKneeAngle > 0) {
                    debug.append(String.format("左膝%.1f° ", result.leftKneeAngle));
                }
            }

            if (rightKneeValid) {
                NormalizedLandmark rightHip = landmarks.get(PoseLandmark.RIGHT_HIP);
                NormalizedLandmark rightKnee = landmarks.get(PoseLandmark.RIGHT_KNEE);
                NormalizedLandmark rightAnkle = landmarks.get(PoseLandmark.RIGHT_ANKLE);
                result.rightKneeAngle = (float) calculateKneeAngleWithPixels(
                        rightHip, rightKnee, rightAnkle, imageWidth, imageHeight);
                if (result.rightKneeAngle > 0) {
                    debug.append(String.format("右膝%.1f° ", result.rightKneeAngle));
                }
            }

            if (leftHipValid) {
                NormalizedLandmark leftShoulder = landmarks.get(PoseLandmark.LEFT_SHOULDER);
                NormalizedLandmark leftHip = landmarks.get(PoseLandmark.LEFT_HIP);
                NormalizedLandmark leftKnee = landmarks.get(PoseLandmark.LEFT_KNEE);
                result.leftHipAngle = (float) calculateHipAngleWithPixels(
                        leftShoulder, leftHip, leftKnee, imageWidth, imageHeight);
                if (result.leftHipAngle > 0) {
                    debug.append(String.format("左髋%.1f° ", result.leftHipAngle));
                }
            }

            if (rightHipValid) {
                NormalizedLandmark rightShoulder = landmarks.get(PoseLandmark.RIGHT_SHOULDER);
                NormalizedLandmark rightHip = landmarks.get(PoseLandmark.RIGHT_HIP);
                NormalizedLandmark rightKnee = landmarks.get(PoseLandmark.RIGHT_KNEE);
                result.rightHipAngle = (float) calculateHipAngleWithPixels(
                        rightShoulder, rightHip, rightKnee, imageWidth, imageHeight);
                if (result.rightHipAngle > 0) {
                    debug.append(String.format("右髋%.1f° ", result.rightHipAngle));
                }
            }

            if (leftElbowValid) {
                NormalizedLandmark leftShoulder = landmarks.get(PoseLandmark.LEFT_SHOULDER);
                NormalizedLandmark leftElbow = landmarks.get(PoseLandmark.LEFT_ELBOW);
                NormalizedLandmark leftWrist = landmarks.get(PoseLandmark.LEFT_WRIST);
                result.leftElbowAngle = (float) calculateElbowAngleWithPixels(
                        leftShoulder, leftElbow, leftWrist, imageWidth, imageHeight);
                if (result.leftElbowAngle > 0) {
                    debug.append(String.format("左肘%.1f° ", result.leftElbowAngle));
                }
            }

            if (rightElbowValid) {
                NormalizedLandmark rightShoulder = landmarks.get(PoseLandmark.RIGHT_SHOULDER);
                NormalizedLandmark rightElbow = landmarks.get(PoseLandmark.RIGHT_ELBOW);
                NormalizedLandmark rightWrist = landmarks.get(PoseLandmark.RIGHT_WRIST);
                result.rightElbowAngle = (float) calculateElbowAngleWithPixels(
                        rightShoulder, rightElbow, rightWrist, imageWidth, imageHeight);
                if (result.rightElbowAngle > 0) {
                    debug.append(String.format("右肘%.1f° ", result.rightElbowAngle));
                }
            }

            if (leftShoulderValid) {
                NormalizedLandmark leftHip = landmarks.get(PoseLandmark.LEFT_HIP);
                NormalizedLandmark leftShoulder = landmarks.get(PoseLandmark.LEFT_SHOULDER);
                NormalizedLandmark leftElbow = landmarks.get(PoseLandmark.LEFT_ELBOW);
                result.leftShoulderAngle = (float) calculateShoulderAngleWithPixels(
                        leftHip, leftShoulder, leftElbow, imageWidth, imageHeight);
                if (result.leftShoulderAngle > 0) {
                    debug.append(String.format("左肩%.1f° ", result.leftShoulderAngle));
                }
            }

            if (rightShoulderValid) {
                NormalizedLandmark rightHip = landmarks.get(PoseLandmark.RIGHT_HIP);
                NormalizedLandmark rightShoulder = landmarks.get(PoseLandmark.RIGHT_SHOULDER);
                NormalizedLandmark rightElbow = landmarks.get(PoseLandmark.RIGHT_ELBOW);
                result.rightShoulderAngle = (float) calculateShoulderAngleWithPixels(
                        rightHip, rightShoulder, rightElbow, imageWidth, imageHeight);
                if (result.rightShoulderAngle > 0) {
                    debug.append(String.format("右肩%.1f° ", result.rightShoulderAngle));
                }
            }

            // 计算平均值
            List<Float> validKneeAngles = new ArrayList<>();
            List<Float> validHipAngles = new ArrayList<>();
            List<Float> validElbowAngles = new ArrayList<>();
            List<Float> validShoulderAngles = new ArrayList<>();

            if (result.leftKneeAngle > 5 && result.leftKneeAngle <= 180.0) {
                validKneeAngles.add(result.leftKneeAngle);
            }
            if (result.rightKneeAngle > 5 && result.rightKneeAngle <= 180.0) {
                validKneeAngles.add(result.rightKneeAngle);
            }
            if (result.leftHipAngle > 5 && result.leftHipAngle <= 180.0) {
                validHipAngles.add(result.leftHipAngle);
            }
            if (result.rightHipAngle > 5 && result.rightHipAngle <= 180.0) {
                validHipAngles.add(result.rightHipAngle);
            }
            if (result.leftElbowAngle > 5 && result.leftElbowAngle <= 180.0) {
                validElbowAngles.add(result.leftElbowAngle);
            }
            if (result.rightElbowAngle > 5 && result.rightElbowAngle <= 180.0) {
                validElbowAngles.add(result.rightElbowAngle);
            }
            if (result.leftShoulderAngle > 5 && result.leftShoulderAngle <= 180.0) {
                validShoulderAngles.add(result.leftShoulderAngle);
            }
            if (result.rightShoulderAngle > 5 && result.rightShoulderAngle <= 180.0) {
                validShoulderAngles.add(result.rightShoulderAngle);
            }

            // 计算平均角度
            if (!validKneeAngles.isEmpty()) {
                result.avgKneeAngle = (float) validKneeAngles.stream()
                        .mapToDouble(Float::doubleValue).average().orElse(0);
                debug.append(String.format("平均膝%.1f° ", result.avgKneeAngle));
            }
            if (!validHipAngles.isEmpty()) {
                result.avgHipAngle = (float) validHipAngles.stream()
                        .mapToDouble(Float::doubleValue).average().orElse(0);
                debug.append(String.format("平均髋%.1f° ", result.avgHipAngle));
            }
            if (!validElbowAngles.isEmpty()) {
                result.avgElbowAngle = (float) validElbowAngles.stream()
                        .mapToDouble(Float::doubleValue).average().orElse(0);
                debug.append(String.format("平均肘%.1f° ", result.avgElbowAngle));
            }
            if (!validShoulderAngles.isEmpty()) {
                result.avgShoulderAngle = (float) validShoulderAngles.stream()
                        .mapToDouble(Float::doubleValue).average().orElse(0);
                debug.append(String.format("平均肩%.1f° ", result.avgShoulderAngle));
            }

            result.hasValidAngles = (!validKneeAngles.isEmpty() || !validHipAngles.isEmpty() ||
                    !validElbowAngles.isEmpty() || !validShoulderAngles.isEmpty());
            result.debugInfo = debug.toString();

            // 记录详细的像素坐标信息（调试用）
            if (result.hasValidAngles) {
                Log.d(TAG, String.format("%s (图像尺寸: %dx%d)",
                        result.debugInfo, imageWidth, imageHeight));
            }

        } catch (Exception e) {
            Log.e(TAG, "像素坐标角度计算时出错: " + e.getMessage());
            result.debugInfo = "计算错误: " + e.getMessage();
        }

        return result;
    }

    public void processVideo(Uri videoUri, VideoProcessingCallback callback) {
        executorService.execute(() -> {
            MediaMetadataRetriever retriever = null;
            try {
                retriever = new MediaMetadataRetriever();
                retriever.setDataSource(context, videoUri);

                // 获取视频信息
                String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                String frameRateStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE);

                if (durationStr == null) {
                    throw new Exception("无法获取视频时长");
                }

                // 获取视频第一帧以确定视频尺寸
                Bitmap firstFrame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST);
                if (firstFrame != null) {
                    videoWidth = firstFrame.getWidth();
                    videoHeight = firstFrame.getHeight();
                    Log.d(TAG, String.format("检测到视频尺寸: %d x %d", videoWidth, videoHeight));

                    // 记录到全局，供后续处理使用
                    if (videoWidth <= 0 || videoHeight <= 0) {
                        // 使用默认值
                        videoWidth = 1920;
                        videoHeight = 1080;
                        Log.w(TAG, "视频尺寸无效，使用默认值: 1920x1080");
                    }
                } else {
                    Log.w(TAG, "无法获取视频第一帧，使用默认尺寸");
                    videoWidth = 1920;
                    videoHeight = 1080;
                }

                long durationMs = Long.parseLong(durationStr);
                float frameRate = frameRateStr != null ? Float.parseFloat(frameRateStr) : 30f;
                long frameIntervalMs = (long) (1000 / frameRate);
                int totalFrames = (int) (durationMs / frameIntervalMs);

                if (totalFrames <= 0) {
                    throw new Exception("视频时长过短或无法读取");
                }

                List<VideoFrameData> frameDataList = new ArrayList<>();
                String rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
                int rotation = rotationStr == null ? 0 : Integer.parseInt(rotationStr);

                Log.d(TAG, String.format("开始处理视频(像素坐标) - 尺寸: %dx%d, 时长: %dms, 帧率: %.1ffps, 总帧数: %d, 旋转: %d°",
                        videoWidth, videoHeight, durationMs, frameRate, totalFrames, rotation));

                int processedFrames = 0;
                long startTime = System.currentTimeMillis();

                // 记录角度范围统计
                float minKneeAngle = 180f, maxKneeAngle = 0f;
                float minHipAngle = 180f, maxHipAngle = 0f;
                float minElbowAngle = 180f, maxElbowAngle = 0f;
                float minShoulderAngle = 180f, maxShoulderAngle = 0f;

                // 立即发送0%进度
                mainHandler.post(() -> callback.onProgress(0));

                // 统计有效帧信息
                int framesWithValidPose = 0;
                int framesWithValidAngles = 0;

                for (long timestampMs = 0; timestampMs < durationMs; timestampMs += frameIntervalMs) {
                    Bitmap frame = retriever.getFrameAtTime(timestampMs * 1000, MediaMetadataRetriever.OPTION_CLOSEST);
                    if (frame == null) {
                        Log.w(TAG, "无法获取时间戳 " + timestampMs + " 的帧");
                        continue;
                    }

                    if (rotation != 0) {
                        Matrix matrix = new Matrix();

                        int correctRotation = -rotation;
                        matrix.postRotate(correctRotation);
                        frame = Bitmap.createBitmap(frame, 0, 0,
                                frame.getWidth(), frame.getHeight(),
                                matrix, true);
                    }

                    // 检测姿态
                    PoseLandmarkerResult result = poseLandmarkerHelper.processVideoFrame(frame, timestampMs);

                    List<NormalizedLandmark> landmarks = new ArrayList<>();
                    AngleCalculationResult angleResult = new AngleCalculationResult();
                    float hipHeight = 0f;
                    boolean hasValidPose = false;

                    if (result != null && !result.landmarks().isEmpty()) {
                        landmarks = result.landmarks().get(0);

                        // 检查全身可见性
                        hasValidPose = isFullBodyVisible(landmarks);

                        if (hasValidPose) {
                            framesWithValidPose++;

                            // 计算所有角度（使用像素坐标）
                            angleResult = calculateAllAnglesWithPixels(
                                    landmarks, processedFrames, videoWidth, videoHeight);

                            // 计算髋部高度（使用像素坐标）
                            hipHeight = calculateHipHeightWithPixels(landmarks, videoHeight);

                            if (angleResult.hasValidAngles) {
                                framesWithValidAngles++;

                                // 更新角度范围统计
                                if (angleResult.avgKneeAngle > 0) {
                                    minKneeAngle = Math.min(minKneeAngle, angleResult.avgKneeAngle);
                                    maxKneeAngle = Math.max(maxKneeAngle, angleResult.avgKneeAngle);
                                }
                                if (angleResult.avgHipAngle > 0) {
                                    minHipAngle = Math.min(minHipAngle, angleResult.avgHipAngle);
                                    maxHipAngle = Math.max(maxHipAngle, angleResult.avgHipAngle);
                                }
                                if (angleResult.avgElbowAngle > 0) {
                                    minElbowAngle = Math.min(minElbowAngle, angleResult.avgElbowAngle);
                                    maxElbowAngle = Math.max(maxElbowAngle, angleResult.avgElbowAngle);
                                }
                                if (angleResult.avgShoulderAngle > 0) {
                                    minShoulderAngle = Math.min(minShoulderAngle, angleResult.avgShoulderAngle);
                                    maxShoulderAngle = Math.max(maxShoulderAngle, angleResult.avgShoulderAngle);
                                }
                            }
                        }
                    }

                    // 创建帧数据（包含图像尺寸信息）
                    VideoFrameData frameData = new VideoFrameData(
                            angleResult.leftKneeAngle,
                            angleResult.rightKneeAngle,
                            angleResult.leftHipAngle,
                            angleResult.rightHipAngle,
                            angleResult.leftElbowAngle,
                            angleResult.rightElbowAngle,
                            angleResult.leftShoulderAngle,
                            angleResult.rightShoulderAngle,
                            angleResult.avgKneeAngle,
                            angleResult.avgHipAngle,
                            angleResult.avgElbowAngle,
                            angleResult.avgShoulderAngle,
                            hipHeight,
                            timestampMs,
                            landmarks,
                            hasValidPose,
                            angleResult.debugInfo,
                            videoWidth,
                            videoHeight
                    );

                    frameDataList.add(frameData);
                    processedFrames++;

                    // 计算并更新进度
                    int progress = (int) ((timestampMs * 100) / durationMs);
                    progress = Math.max(0, Math.min(100, progress));

                    // 每处理10%或最后一步时更新进度
                    if (progress % 10 == 0 || timestampMs + frameIntervalMs >= durationMs) {
                        final int finalProgress = progress;
                        mainHandler.post(() -> {
                            callback.onProgress(finalProgress);
                            Log.d(TAG, "视频处理进度: " + finalProgress + "%");
                        });
                    }
                }

                // 确保发送100%进度
                mainHandler.post(() -> callback.onProgress(100));

                long endTime = System.currentTimeMillis();

                // 输出详细的处理统计
                Log.d(TAG, String.format("视频处理完成 - 耗时: %dms", (endTime - startTime)));
                Log.d(TAG, String.format("帧统计 - 总帧: %d, 有效姿态帧: %d, 有效角度帧: %d",
                        processedFrames, framesWithValidPose, framesWithValidAngles));
                Log.d(TAG, String.format("角度范围统计(像素坐标):"));
                Log.d(TAG, String.format("  膝关节: %.1f° ~ %.1f° (范围: %.1f°)",
                        minKneeAngle, maxKneeAngle, maxKneeAngle - minKneeAngle));
                Log.d(TAG, String.format("  髋关节: %.1f° ~ %.1f° (范围: %.1f°)",
                        minHipAngle, maxHipAngle, maxHipAngle - minHipAngle));
                Log.d(TAG, String.format("  肘关节: %.1f° ~ %.1f° (范围: %.1f°)",
                        minElbowAngle, maxElbowAngle, maxElbowAngle - minElbowAngle));
                Log.d(TAG, String.format("  肩关节: %.1f° ~ %.1f° (范围: %.1f°)",
                        minShoulderAngle, maxShoulderAngle, maxShoulderAngle - minShoulderAngle));
                Log.d(TAG, String.format("视频信息 - 尺寸: %dx%d, 帧率: %.1ffps, 旋转: %d°",
                        videoWidth, videoHeight, frameRate, rotation));

                mainHandler.post(() -> callback.onComplete(frameDataList, rotation, frameRate));

            } catch (Exception e) {
                Log.e(TAG, "视频处理失败: " + e.getMessage(), e);
                mainHandler.post(() -> callback.onError("处理失败: " + e.getMessage()));
            } finally {
                if (retriever != null) {
                    try {
                        retriever.release();
                    } catch (Exception e) {
                        Log.e(TAG, "释放MediaMetadataRetriever失败", e);
                    }
                }
            }
        });
    }


    public static List<VideoFrameData> smoothAngles(List<VideoFrameData> frameDataList, int windowSize) {
        if (frameDataList == null || frameDataList.size() < windowSize) {
            return frameDataList;
        }

        List<VideoFrameData> smoothedList = new ArrayList<>();

        for (int i = 0; i < frameDataList.size(); i++) {
            VideoFrameData current = frameDataList.get(i);

            // 收集窗口内的有效角度
            List<Float> kneeAngles = new ArrayList<>();
            List<Float> hipAngles = new ArrayList<>();
            List<Float> elbowAngles = new ArrayList<>();
            List<Float> shoulderAngles = new ArrayList<>();

            int start = Math.max(0, i - windowSize / 2);
            int end = Math.min(frameDataList.size() - 1, i + windowSize / 2);

            for (int j = start; j <= end; j++) {
                VideoFrameData frame = frameDataList.get(j);
                if (frame.hasValidPose) {
                    if (frame.avgKneeAngle > 0) kneeAngles.add(frame.avgKneeAngle);
                    if (frame.avgHipAngle > 0) hipAngles.add(frame.avgHipAngle);
                    if (frame.avgElbowAngle > 0) elbowAngles.add(frame.avgElbowAngle);
                    if (frame.avgShoulderAngle > 0) shoulderAngles.add(frame.avgShoulderAngle);
                }
            }

            // 计算平滑后的角度
            float smoothedKnee = kneeAngles.isEmpty() ? 0 :
                    (float) kneeAngles.stream().mapToDouble(Float::doubleValue).average().orElse(0);
            float smoothedHip = hipAngles.isEmpty() ? 0 :
                    (float) hipAngles.stream().mapToDouble(Float::doubleValue).average().orElse(0);
            float smoothedElbow = elbowAngles.isEmpty() ? 0 :
                    (float) elbowAngles.stream().mapToDouble(Float::doubleValue).average().orElse(0);
            float smoothedShoulder = shoulderAngles.isEmpty() ? 0 :
                    (float) shoulderAngles.stream().mapToDouble(Float::doubleValue).average().orElse(0);

            // 创建平滑后的帧数据
            VideoFrameData smoothedFrame = new VideoFrameData(
                    current.leftKneeAngle,
                    current.rightKneeAngle,
                    current.leftHipAngle,
                    current.rightHipAngle,
                    current.leftElbowAngle,
                    current.rightElbowAngle,
                    current.leftShoulderAngle,
                    current.rightShoulderAngle,
                    smoothedKnee,
                    smoothedHip,
                    smoothedElbow,
                    smoothedShoulder,
                    current.hipHeight,
                    current.timestamp,
                    current.landmarks,
                    current.hasValidPose,
                    current.debugInfo + " [平滑]",
                    current.frameWidth,
                    current.frameHeight
            );

            smoothedList.add(smoothedFrame);
        }

        Log.d(TAG, String.format("角度平滑完成 - 窗口大小: %d, 输入帧: %d, 输出帧: %d",
                windowSize, frameDataList.size(), smoothedList.size()));

        return smoothedList;
    }

    public void release() {
        if (poseLandmarkerHelper != null) {
            poseLandmarkerHelper.clearPoseLandmarker();
        }
        executorService.shutdown();
    }
}