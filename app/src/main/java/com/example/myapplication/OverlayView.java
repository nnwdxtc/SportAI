package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import androidx.annotation.NonNull;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.util.List;
import java.util.Arrays;

public class OverlayView extends View {
    private static final float LANDMARK_STROKE_WIDTH = 4f;
    private static final float CONNECTION_STROKE_WIDTH = 6f;
    private static final float TEXT_SIZE = 40f;
    private static final float LARGE_TEXT_SIZE = 80f;
    private static final float TEXT_MARGIN = 30f;
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


    private static final int[][] POSE_CONNECTIONS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 7}, {0, 4}, {4, 5}, {5, 6}, {6, 8},
            {9, 10}, {11, 12}, {11, 13}, {13, 15}, {12, 14}, {14, 16},
            {11, 23}, {23, 25}, {25, 27}, {27, 29}, {29, 31},
            {12, 24}, {24, 26}, {26, 28}, {28, 30}, {30, 32},
            {23, 25}, {25, 27}, {27, 29}, {29, 31},
            {24, 26}, {26, 28}, {28, 30}, {30, 32},
            {11, 12}, {23, 24}
    };

    // 全身关键点检测相关 - 手肘索引（13=左肘，14=右肘）
    private static final int[] REQUIRED_LANDMARK_INDICES = {
            11, 12, // 左肩、右肩
            13, 14, // 左肘、右肘
            23, 24, // 左髋、右髋
            25, 26, // 左膝、右膝
            27, 28  // 左踝、右踝
    };
    private static final float MIN_VISIBILITY_THRESHOLD = 0.5f;

    // 主要关键点定义（手肘到主要关节数组）
    private static final int[] LEFT_SIDE_MAJOR_JOINTS = {
            11, // 左肩
            13, // 左肘
            23, // 左髋
            25, // 左膝
            27  // 左踝
    };

    private static final int[] RIGHT_SIDE_MAJOR_JOINTS = {
            12, // 右肩
            14, // 右肘
            24, // 右髋
            26, // 右膝
            28  // 右踝
    };

    // 距离比例和尺寸限制参数
    private float shoulderToHipEuclidRatio = 0.1f;
    private static final float MIN_DISTANCE_RATIO = 6f;
    private static final float MAX_DISTANCE_RATIO = 25f;
    private static final float MAX_LANDMARK_RADIUS = 60f;
    private static final float MAX_MAJOR_LANDMARK_RADIUS = 120f;


    public void setRotationDegrees(int degrees) {
        this.rotationDegrees = degrees % 360;
        invalidate();
    }
    // 设置运动状态
    public void setTrackingState(boolean tracking) {
        this.isTracking = tracking;
        invalidate();
    }
    public void setFrontCamera(boolean front) {
        this.isFrontCamera = front;
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
        // 实时姿态画笔
        landmarkPaint = new Paint();
        landmarkPaint.setColor(Color.RED);
        landmarkPaint.setStrokeWidth(LANDMARK_STROKE_WIDTH);
        landmarkPaint.setStyle(Paint.Style.FILL);

        connectionPaint = new Paint();
        connectionPaint.setColor(Color.GREEN);
        connectionPaint.setStrokeWidth(CONNECTION_STROKE_WIDTH);
        connectionPaint.setStyle(Paint.Style.STROKE);

        // 标准姿态画笔
        standardLandmarkPaint = new Paint();
        standardLandmarkPaint.setColor(Color.BLUE);
        standardLandmarkPaint.setStrokeWidth(LANDMARK_STROKE_WIDTH);
        standardLandmarkPaint.setStyle(Paint.Style.FILL);

        standardConnectionPaint = new Paint();
        standardConnectionPaint.setColor(Color.CYAN);
        standardConnectionPaint.setStrokeWidth(CONNECTION_STROKE_WIDTH);
        standardConnectionPaint.setStyle(Paint.Style.STROKE);

        // 文本画笔
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

    // 设置标准姿态关键点
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

        // 再绘制实时姿态（上层）
        if (poseResults != null && !poseResults.landmarks().isEmpty()) {
            drawPoseLandmarks(canvas);
            // 只有在运动状态时才显示相似度
            if (isTracking) {
                drawSimilarityInfo(canvas);
            }
        }
    }
    // 绘制标准姿态
    private void drawStandardPoseLandmarks(Canvas canvas) {
        canvas.save();
        applyTransformations(canvas);
        // 绘制连接线
        drawPoseConnections(canvas, standardLandmarks, standardConnectionPaint);

        // 绘制关键点
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
    }

    private void drawSimilarityInfo(Canvas canvas) {
        // 原有逻辑不变
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
        // 原有逻辑不变
        float hue = score * 120f;
        return Color.HSVToColor(new float[]{
                Math.min(hue, 120f),
                0.9f,
                0.9f
        });
    }

    /**
     * 检查单侧主要关键点可见性
     */
    private boolean areMajorJointsVisible(List<NormalizedLandmark> landmarks, int[] jointIndices) {
        if (landmarks == null || landmarks.size() < 33) {
            return false;
        }

        int visibleCount = 0;
        for (int index : jointIndices) {
            if (index < landmarks.size()) {
                NormalizedLandmark landmark = landmarks.get(index);
                if (landmark != null && landmark.visibility().isPresent() &&
                        landmark.visibility().get() >= MIN_VISIBILITY_THRESHOLD &&
                        landmark.x() >= 0 && landmark.x() <= 1 &&
                        landmark.y() >= 0 && landmark.y() <= 1) {
                    visibleCount++;
                }
            }
        }

        // 单侧所有主要关键点都可见才返回true
        return visibleCount == jointIndices.length;
    }

    /**
     * 获取关键点绘制颜色
     */
    private int getLandmarkColor(List<NormalizedLandmark> landmarks) {
        boolean leftVisible = areMajorJointsVisible(landmarks, LEFT_SIDE_MAJOR_JOINTS);
        boolean rightVisible = areMajorJointsVisible(landmarks, RIGHT_SIDE_MAJOR_JOINTS);

        if (leftVisible && rightVisible) {
            return Color.GREEN; // 双侧都可见 - 绿色
        } else if (leftVisible || rightVisible) {
            return Color.YELLOW; // 单侧可见 - 黄色
        } else {
            return Color.RED; // 都不可见 - 红色
        }
    }

    /**
     * 检查是否为主要关节点
     */
    private boolean isMajorJoint(int landmarkIndex) {
        for (int joint : LEFT_SIDE_MAJOR_JOINTS) {
            if (joint == landmarkIndex) return true;
        }
        for (int joint : RIGHT_SIDE_MAJOR_JOINTS) {
            if (joint == landmarkIndex) return true;
        }
        return false;
    }

    /**
     * 获取主要关节点的颜色
     */
    private int getMajorJointColor(int jointIndex, List<NormalizedLandmark> landmarks) {
        int[] sideJoints = Arrays.stream(LEFT_SIDE_MAJOR_JOINTS).anyMatch(i -> i == jointIndex) ?
                LEFT_SIDE_MAJOR_JOINTS : RIGHT_SIDE_MAJOR_JOINTS;

        boolean sideVisible = areMajorJointsVisible(landmarks, sideJoints);
        return sideVisible ? Color.GREEN : Color.RED;
    }

    private void drawPoseLandmarks(Canvas canvas) {
        for (List<NormalizedLandmark> landmarks : poseResults.landmarks()) {
            if (landmarks == null || landmarks.isEmpty()) return;

            // 计算肩到髋的欧氏距离
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

            // 检查全身可见性
            boolean isFullBody = isFullBodyVisible(landmarks);

            // 根据主要关键点可见性调整颜色
            int landmarkColor = getLandmarkColor(landmarks);
            int connectionColor = landmarkColor;

            landmarkPaint.setColor(landmarkColor);
            connectionPaint.setColor(connectionColor);

            // 应用变换
            canvas.save();
            applyTransformations(canvas);

            // 先绘制连接线（
            drawPoseConnections(canvas, landmarks, connectionPaint);

            // 绘制核心关键点
            for (int i : REQUIRED_LANDMARK_INDICES) {
                if (i < landmarks.size()) {
                    NormalizedLandmark landmark = landmarks.get(i);
                    if (landmark.visibility().isPresent() && landmark.visibility().get() >= MIN_VISIBILITY_THRESHOLD) {
                        float x = landmark.x() * getWidth();
                        float y = landmark.y() * getHeight();

                        // 动态计算半径
                        float baseRadius = LANDMARK_STROKE_WIDTH;
                        float radius = baseRadius * (shoulderToHipEuclidRatio / MIN_DISTANCE_RATIO)/2;

                        if (isMajorJoint(i)) {
                            radius = radius * 2;
                            radius = Math.min(radius, MAX_MAJOR_LANDMARK_RADIUS);
                            landmarkPaint.setColor(getMajorJointColor(i, landmarks));
                        } else {
                            radius = Math.min(radius, MAX_LANDMARK_RADIUS);
                            landmarkPaint.setColor(landmarkColor);
                        }

                        canvas.drawCircle(x, y, radius, landmarkPaint);
                    }
                }
            }

            canvas.restore();
        }
    }

    // 连接线绘制方法
    private void drawPoseConnections(Canvas canvas, List<NormalizedLandmark> landmarks, Paint paint) {
        if (landmarks == null || landmarks.isEmpty()) return;

        for (int[] connection : POSE_CONNECTIONS) {
            if (connection[0] < landmarks.size() && connection[1] < landmarks.size()) {
                NormalizedLandmark start = landmarks.get(connection[0]);
                NormalizedLandmark end = landmarks.get(connection[1]);

                // 检查关键点可见性
                boolean startVisible = start.visibility().isPresent() &&
                        start.visibility().get() >= MIN_VISIBILITY_THRESHOLD;
                boolean endVisible = end.visibility().isPresent() &&
                        end.visibility().get() >= MIN_VISIBILITY_THRESHOLD;

                if (startVisible && endVisible) {
                    float startX = start.x() * getWidth();
                    float startY = start.y() * getHeight();
                    float endX = end.x() * getWidth();
                    float endY = end.y() * getHeight();

                    if (startX >= 0 && startX <= getWidth() && startY >= 0 && startY <= getHeight() &&
                            endX >= 0 && endX <= getWidth() && endY >= 0 && endY <= getHeight()) {

                        canvas.drawLine(startX, startY, endX, endY, paint);
                    }
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

    private boolean isFullBodyVisible(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() < 33) {
            return false;
        }

        int visibleCount = 0;
        for (int index : REQUIRED_LANDMARK_INDICES) {
            if (index < landmarks.size()) {
                NormalizedLandmark landmark = landmarks.get(index);
                float visibility = landmark.visibility().orElse(0f);
                if (landmark.x() >= 0 && landmark.x() <= 1 &&
                        landmark.y() >= 0 && landmark.y() <= 1 &&
                        visibility >= MIN_VISIBILITY_THRESHOLD) {
                    visibleCount++;
                }
            }
        }

        return visibleCount >= REQUIRED_LANDMARK_INDICES.length;
    }
}