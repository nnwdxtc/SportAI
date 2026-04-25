package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.util.Arrays;
import java.util.List;

public class OverlayView extends View {
    private static final float LANDMARK_STROKE_WIDTH = 4f;
    private static final float CONNECTION_STROKE_WIDTH = 6f;
    private static final float TEXT_SIZE = 40f;
    private static final float LARGE_TEXT_SIZE = 80f;
    private int rotationDegrees = 90;
    private boolean isFrontCamera = false;
    private PoseLandmarkerResult poseResults;
    private long inferenceTime = 0;
    private Paint landmarkPaint;
    private Paint connectionPaint;
    private Paint textPaint;
    private Paint largeTextPaint;
    private Paint backgroundPaint;

    // 标准姿态相关
    private List<NormalizedLandmark> standardLandmarks;
    private boolean drawStandardPose = false;
    private Paint standardLandmarkPaint;
    private Paint standardConnectionPaint;
    private boolean isTracking = false;
    private int squatCount = 0;
    private float similarityScore = 0f;

    // 新增：直接从SDK接收的骨架数据
    private List<NormalizedLandmark> directLandmarks;

    private static final int[][] POSE_CONNECTIONS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 7}, {0, 4}, {4, 5}, {5, 6}, {6, 8},
            {9, 10}, {11, 12}, {11, 13}, {13, 15}, {12, 14}, {14, 16},
            {11, 23}, {23, 25}, {25, 27}, {27, 29}, {29, 31},
            {12, 24}, {24, 26}, {26, 28}, {28, 30}, {30, 32},
            {23, 25}, {25, 27}, {27, 29}, {29, 31},
            {24, 26}, {26, 28}, {28, 30}, {30, 32},
            {11, 12}, {23, 24}
    };

    // 全身关键点检测相关
    private static final int[] REQUIRED_LANDMARK_INDICES = {
            11, 12, 13, 14, 23, 24, 25, 26, 27, 28
    };
    private static final float MIN_VISIBILITY_THRESHOLD = 0.5f;

    private static final int[] LEFT_SIDE_MAJOR_JOINTS = {
            11, 13, 23, 25, 27
    };

    private static final int[] RIGHT_SIDE_MAJOR_JOINTS = {
            12, 14, 24, 26, 28
    };

    private float shoulderToHipEuclidRatio = 0.1f;
    private static final float MIN_DISTANCE_RATIO = 6f;
    private static final float MAX_DISTANCE_RATIO = 25f;
    private static final float MAX_LANDMARK_RADIUS = 60f;
    private static final float MAX_MAJOR_LANDMARK_RADIUS = 120f;

    public void setRotationDegrees(int degrees) {
        this.rotationDegrees = degrees % 360;
        invalidate();
    }

    public void setTrackingState(boolean tracking) {
        this.isTracking = tracking;
        invalidate();
    }

    public void setFrontCamera(boolean front) {
        this.isFrontCamera = front;
        invalidate();
    }

    // 新增：直接从SDK设置骨架数据
    public void setDirectLandmarks(List<NormalizedLandmark> landmarks) {
        this.directLandmarks = landmarks;
        invalidate();
    }

    public OverlayView(Context context) {
        super(context);
        initPaints();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    public OverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }

    private void initPaints() {
        landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.RED);
        landmarkPaint.setStrokeWidth(LANDMARK_STROKE_WIDTH);
        landmarkPaint.setStyle(Paint.Style.FILL);

        connectionPaint = new Paint();
        connectionPaint.setColor(Color.GREEN);
        connectionPaint.setStrokeWidth(CONNECTION_STROKE_WIDTH);
        connectionPaint.setStyle(Paint.Style.STROKE);

        standardLandmarkPaint = new Paint();
        standardLandmarkPaint.setColor(Color.BLUE);
        standardLandmarkPaint.setStrokeWidth(LANDMARK_STROKE_WIDTH);
        standardLandmarkPaint.setStyle(Paint.Style.FILL);

        standardConnectionPaint = new Paint();
        standardConnectionPaint.setColor(Color.CYAN);
        standardConnectionPaint.setStrokeWidth(CONNECTION_STROKE_WIDTH);
        standardConnectionPaint.setStyle(Paint.Style.STROKE);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(TEXT_SIZE);
        textPaint.setAntiAlias(true);

        largeTextPaint = new Paint();
        largeTextPaint.setColor(Color.WHITE);
        largeTextPaint.setTextSize(LARGE_TEXT_SIZE);
        largeTextPaint.setAntiAlias(true);
        largeTextPaint.setTextAlign(Paint.Align.CENTER);

        backgroundPaint = new Paint();
        backgroundPaint.setColor(Color.BLACK);
        backgroundPaint.setAlpha(128);
        backgroundPaint.setStyle(Paint.Style.FILL);
    }

    public void setPoseResults(PoseLandmarkerResult poseLandmarkerResult, long inferenceTime) {
        this.poseResults = poseLandmarkerResult;
        this.inferenceTime = inferenceTime;
        invalidate();
    }

    public void setStandardPoseLandmarks(List<NormalizedLandmark> landmarks) {
        this.standardLandmarks = landmarks;
        this.drawStandardPose = (landmarks != null && !landmarks.isEmpty());
        invalidate();
    }

    public void setSquatInfo(int count, float similarityScore) {
        this.squatCount = count;
        this.similarityScore = similarityScore;
        invalidate();
    }

    public void clear() {
        this.poseResults = null;
        this.directLandmarks = null;
        this.inferenceTime = 0;
        this.standardLandmarks = null;
        this.drawStandardPose = false;
        invalidate();
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);

        // 先绘制标准姿态（底层）
        if (drawStandardPose && standardLandmarks != null) {
            drawStandardPoseLandmarks(canvas);
        }

        // 绘制实时姿态 - 优先使用 directLandmarks（来自SDK）
        if (directLandmarks != null && !directLandmarks.isEmpty()) {
            drawDirectLandmarks(canvas, directLandmarks);
            if (isTracking) {
                drawSimilarityInfo(canvas);
            }
        }
        // 兼容旧的 poseResults
        else if (poseResults != null && !poseResults.landmarks().isEmpty()) {
            drawPoseLandmarks(canvas);
            if (isTracking) {
                drawSimilarityInfo(canvas);
            }
        }
    }

    private void drawStandardPoseLandmarks(Canvas canvas) {
        canvas.save();
        applyTransformations(canvas);
        drawPoseConnections(canvas, standardLandmarks, standardConnectionPaint);

        for (int i : REQUIRED_LANDMARK_INDICES) {
            if (i < standardLandmarks.size()) {
                NormalizedLandmark landmark = standardLandmarks.get(i);
                if (landmark.visibility().isPresent() && landmark.visibility().get() >= MIN_VISIBILITY_THRESHOLD) {
                    canvas.drawCircle(
                            landmark.x() * getWidth(),
                            landmark.y() * getHeight(),
                            LANDMARK_STROKE_WIDTH / 2,
                            standardLandmarkPaint
                    );
                }
            }
        }
        canvas.restore();
    }

    private void drawDirectLandmarks(Canvas canvas, List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.isEmpty()) return;

        canvas.save();
        applyTransformations(canvas);

        // 计算肩髋距离
        if (landmarks.size() >= 33) {
            NormalizedLandmark leftShoulder = landmarks.get(11);
            NormalizedLandmark leftHip = landmarks.get(23);
            if (leftShoulder != null && leftHip != null) {
                float dist = (float) Math.sqrt(
                        Math.pow(leftHip.x() - leftShoulder.x(), 2) +
                                Math.pow(leftHip.y() - leftShoulder.y(), 2)
                ) * 100;
                shoulderToHipEuclidRatio = Math.max(dist, MIN_DISTANCE_RATIO);
                shoulderToHipEuclidRatio = Math.min(shoulderToHipEuclidRatio, MAX_DISTANCE_RATIO);
            }
        }

        int landmarkColor = getLandmarkColor(landmarks);
        connectionPaint.setColor(landmarkColor);
        landmarkPaint.setColor(landmarkColor);

        // 绘制连接线
        drawPoseConnections(canvas, landmarks, connectionPaint);

        // 绘制关键点
        for (int i : REQUIRED_LANDMARK_INDICES) {
            if (i < landmarks.size()) {
                NormalizedLandmark landmark = landmarks.get(i);
                float visibility = landmark.visibility().orElse(0f);
                if (visibility >= MIN_VISIBILITY_THRESHOLD) {
                    float x = landmark.x() * getWidth();
                    float y = landmark.y() * getHeight();

                    float baseRadius = LANDMARK_STROKE_WIDTH;
                    float radius = baseRadius * (shoulderToHipEuclidRatio / MIN_DISTANCE_RATIO) / 2;

                    if (isMajorJoint(i)) {
                        radius = radius * 2;
                        radius = Math.min(radius, MAX_MAJOR_LANDMARK_RADIUS);
                    } else {
                        radius = Math.min(radius, MAX_LANDMARK_RADIUS);
                    }

                    canvas.drawCircle(x, y, radius, landmarkPaint);
                }
            }
        }

        canvas.restore();
    }

    private void drawSimilarityInfo(Canvas canvas) {
        Paint similarityPaint = new Paint(largeTextPaint);
        similarityPaint.setColor(getSimilarityColor(similarityScore));

        String similarityText = String.format("%.0f%%", similarityScore * 100);
        float centerX = getWidth() / 2f;
        float centerY = getHeight() / 4f;

        float textWidth = similarityPaint.measureText(similarityText);
        float padding = 40f;
        canvas.drawRect(centerX - textWidth/2 - padding,
                centerY - LARGE_TEXT_SIZE - padding/2,
                centerX + textWidth/2 + padding,
                centerY + padding/2,
                backgroundPaint);

        canvas.drawText(similarityText, centerX, centerY, similarityPaint);
    }

    private int getSimilarityColor(float score) {
        float hue = score * 120f;
        return Color.HSVToColor(new float[]{Math.min(hue, 120f), 0.9f, 0.9f});
    }

    private boolean areMajorJointsVisible(List<NormalizedLandmark> landmarks, int[] jointIndices) {
        if (landmarks == null || landmarks.size() < 33) return false;
        int visibleCount = 0;
        for (int index : jointIndices) {
            if (index < landmarks.size()) {
                NormalizedLandmark landmark = landmarks.get(index);
                if (landmark != null && landmark.visibility().isPresent() &&
                        landmark.visibility().get() >= MIN_VISIBILITY_THRESHOLD) {
                    visibleCount++;
                }
            }
        }
        return visibleCount == jointIndices.length;
    }

    private int getLandmarkColor(List<NormalizedLandmark> landmarks) {
        boolean leftVisible = areMajorJointsVisible(landmarks, LEFT_SIDE_MAJOR_JOINTS);
        boolean rightVisible = areMajorJointsVisible(landmarks, RIGHT_SIDE_MAJOR_JOINTS);
        if (leftVisible && rightVisible) {
            return Color.GREEN;
        } else if (leftVisible || rightVisible) {
            return Color.YELLOW;
        } else {
            return Color.RED;
        }
    }

    private boolean isMajorJoint(int landmarkIndex) {
        for (int joint : LEFT_SIDE_MAJOR_JOINTS) {
            if (joint == landmarkIndex) return true;
        }
        for (int joint : RIGHT_SIDE_MAJOR_JOINTS) {
            if (joint == landmarkIndex) return true;
        }
        return false;
    }

    private void drawPoseLandmarks(Canvas canvas) {
        for (List<NormalizedLandmark> landmarks : poseResults.landmarks()) {
            if (landmarks == null || landmarks.isEmpty()) return;

            if (landmarks.size() >= 33) {
                NormalizedLandmark leftShoulder = landmarks.get(11);
                NormalizedLandmark leftHip = landmarks.get(23);
                NormalizedLandmark rightShoulder = landmarks.get(12);
                NormalizedLandmark rightHip = landmarks.get(24);

                float leftDist = (float) Math.sqrt(
                        Math.pow(leftHip.x() - leftShoulder.x(), 2) +
                                Math.pow(leftHip.y() - leftShoulder.y(), 2)
                ) * 100;
                float rightDist = (float) Math.sqrt(
                        Math.pow(rightHip.x() - rightShoulder.x(), 2) +
                                Math.pow(rightHip.y() - rightShoulder.y(), 2)
                ) * 100;
                shoulderToHipEuclidRatio = Math.max(leftDist, rightDist);
                shoulderToHipEuclidRatio = Math.max(shoulderToHipEuclidRatio, MIN_DISTANCE_RATIO);
                shoulderToHipEuclidRatio = Math.min(shoulderToHipEuclidRatio, MAX_DISTANCE_RATIO);
            }

            int landmarkColor = getLandmarkColor(landmarks);
            connectionPaint.setColor(landmarkColor);
            landmarkPaint.setColor(landmarkColor);

            canvas.save();
            applyTransformations(canvas);
            drawPoseConnections(canvas, landmarks, connectionPaint);

            for (int i : REQUIRED_LANDMARK_INDICES) {
                if (i < landmarks.size()) {
                    NormalizedLandmark landmark = landmarks.get(i);
                    if (landmark.visibility().isPresent() && landmark.visibility().get() >= MIN_VISIBILITY_THRESHOLD) {
                        float x = landmark.x() * getWidth();
                        float y = landmark.y() * getHeight();
                        float baseRadius = LANDMARK_STROKE_WIDTH;
                        float radius = baseRadius * (shoulderToHipEuclidRatio / MIN_DISTANCE_RATIO) / 2;
                        radius = Math.min(radius, isMajorJoint(i) ? MAX_MAJOR_LANDMARK_RADIUS : MAX_LANDMARK_RADIUS);
                        canvas.drawCircle(x, y, radius, landmarkPaint);
                    }
                }
            }
            canvas.restore();
        }
    }

    private void drawPoseConnections(Canvas canvas, List<NormalizedLandmark> landmarks, Paint paint) {
        if (landmarks == null || landmarks.isEmpty()) return;
        for (int[] connection : POSE_CONNECTIONS) {
            if (connection[0] < landmarks.size() && connection[1] < landmarks.size()) {
                NormalizedLandmark start = landmarks.get(connection[0]);
                NormalizedLandmark end = landmarks.get(connection[1]);
                boolean startVisible = start.visibility().isPresent() && start.visibility().get() >= MIN_VISIBILITY_THRESHOLD;
                boolean endVisible = end.visibility().isPresent() && end.visibility().get() >= MIN_VISIBILITY_THRESHOLD;
                if (startVisible && endVisible) {
                    float startX = start.x() * getWidth();
                    float startY = start.y() * getHeight();
                    float endX = end.x() * getWidth();
                    float endY = end.y() * getHeight();
                    canvas.drawLine(startX, startY, endX, endY, paint);
                }
            }
        }
    }

    private void applyTransformations(Canvas canvas) {
        if (rotationDegrees != 0) {
            canvas.rotate(rotationDegrees, getWidth() / 2f, getHeight() / 2f);
        }
        if (isFrontCamera) {
            canvas.scale(-1, 1, getWidth() / 2f, getHeight() / 2f);
        }
    }
}