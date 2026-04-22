package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.os.SystemClock;
import android.util.Log;
import com.google.mediapipe.framework.image.BitmapImageBuilder;
import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.util.List;

public class PoseLandmarkerHelper {
    private static final String TAG = "PoseLandmarkerHelper";
    private static final String MODEL_FILE = "pose_landmarker.task";

    private final float minPoseDetectionConfidence;
    private final float minPoseTrackingConfidence;
    private final float minPosePresenceConfidence;
    private final RunningMode runningMode;
    private final Delegate currentDelegate;

    private PoseLandmarker poseLandmarker;
    private final PoseLandmarkerListener poseLandmarkerListener;

    public interface PoseLandmarkerListener {
        void onError(String error, int errorCode);
        void onResults(PoseLandmarkerResult result, long inferenceTime);
    }

    public PoseLandmarkerHelper(float minPoseDetectionConfidence,
                                float minPoseTrackingConfidence,
                                float minPosePresenceConfidence,
                                RunningMode runningMode,
                                Delegate currentDelegate,
                                Context context,
                                PoseLandmarkerListener poseLandmarkerListener) {
        this.minPoseDetectionConfidence = minPoseDetectionConfidence;
        this.minPoseTrackingConfidence = minPoseTrackingConfidence;
        this.minPosePresenceConfidence = minPosePresenceConfidence;
        this.runningMode = runningMode;
        this.currentDelegate = currentDelegate;
        this.poseLandmarkerListener = poseLandmarkerListener;

        setupPoseLandmarker(context);
    }

    public void clearPoseLandmarker() {
        if (poseLandmarker != null) {
            poseLandmarker.close();
            poseLandmarker = null;
        }
    }

    public boolean isClose() {
        return poseLandmarker == null;
    }

    private void setupPoseLandmarker(Context context) {
        try {
            BaseOptions.Builder baseOptionsBuilder = BaseOptions.builder();

            if (runningMode == RunningMode.LIVE_STREAM) {
                if (poseLandmarkerListener == null) {
                    throw new IllegalStateException("poseLandmarkerListener must be set when runningMode is LIVE_STREAM");
                }
            }

            baseOptionsBuilder.setDelegate(currentDelegate);
            baseOptionsBuilder.setModelAssetPath(MODEL_FILE);

            PoseLandmarker.PoseLandmarkerOptions.Builder optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                    .setBaseOptions(baseOptionsBuilder.build())
                    .setMinPoseDetectionConfidence(minPoseDetectionConfidence)
                    .setMinTrackingConfidence(minPoseTrackingConfidence)
                    .setMinPosePresenceConfidence(minPosePresenceConfidence)
                    .setRunningMode(runningMode);

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder.setResultListener(this::returnLivestreamResult)
                        .setErrorListener(this::returnLivestreamError);
            }

            PoseLandmarker.PoseLandmarkerOptions options = optionsBuilder.build();
            poseLandmarker = PoseLandmarker.createFromOptions(context, options);

        } catch (Exception e) {
            if (poseLandmarkerListener != null) {
                poseLandmarkerListener.onError("Pose landmarker failed to initialize. See error logs for details", -1);
            }
            Log.e(TAG, "MP Task Vision failed to load the task with error: " + e.getMessage(), e);
        }
    }

    public void detectLiveStreamFrame(Bitmap imageProxy) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw new IllegalArgumentException("Attempting to call detectLiveStreamFrame while not using RunningMode.LIVE_STREAM");
        }

        long frameTime = SystemClock.uptimeMillis();
        MPImage mpImage = new BitmapImageBuilder(imageProxy).build();
        detectAsync(mpImage, frameTime);
    }

    // 新增：处理视频帧的方法
    public PoseLandmarkerResult processVideoFrame(Bitmap frame, long timestampMs) {
        if (runningMode != RunningMode.VIDEO) {
            throw new IllegalArgumentException("Attempting to call processVideoFrame while not using RunningMode.VIDEO");
        }

        try {
            // 移除旋转，使用原始图像
            MPImage mpImage = new BitmapImageBuilder(frame).build();
            return poseLandmarker.detectForVideo(mpImage, timestampMs);
        } catch (Exception e) {
            Log.e(TAG, "Error processing video frame: " + e.getMessage());
            return null;
        }
    }

    private void detectAsync(MPImage mpImage, long frameTime) {
        if (poseLandmarker != null) {
            poseLandmarker.detectAsync(mpImage, frameTime);
        }
    }

    private void returnLivestreamResult(PoseLandmarkerResult result, MPImage input) {
        long finishTimeMs = SystemClock.uptimeMillis();
        long inferenceTime = finishTimeMs - result.timestampMs();

        if (poseLandmarkerListener != null) {
            poseLandmarkerListener.onResults(result, inferenceTime);
        }
    }

    private void returnLivestreamError(RuntimeException error) {
        if (poseLandmarkerListener != null) {
            poseLandmarkerListener.onError(error.getMessage(), -1);
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, float degree) {
        if (bitmap == null) return null;

        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }
}