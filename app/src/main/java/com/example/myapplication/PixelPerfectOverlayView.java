package com.example.myapplication;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.util.ArrayList;
import java.util.List;

/**
 * 精准把标准姿态画在视频实际显示区域上（像素级）
 */
public class PixelPerfectOverlayView extends View {

    /* ------------------ 绘制配置 ------------------ */
    // 修正的骨骼连接定义 - 只保留身体核心连接，去除脸部和手部
    private static final int[][] CONNECTIONS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 7}, {0, 4}, {4, 5}, {5, 6}, {6, 8},
            {9, 10}, {11, 12}, {11, 13}, {13, 15}, {12, 14}, {14, 16},
            {11, 23}, {23, 25}, {25, 27}, {27, 29}, {29, 31},
            {12, 24}, {24, 26}, {26, 28}, {28, 30}, {30, 32},
            {23, 25}, {25, 27}, {27, 29}, {29, 31},
            {24, 26}, {26, 28}, {28, 30}, {30, 32},
            {11, 12}, {23, 24}
    };

    // 核心关键点索引（只包含身体核心）
    private static final int[] CORE_LANDMARK_INDICES = {
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW
    };

    private final Paint landmarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint connectionPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /* ------------------ 数据 ------------------ */
    private List<NormalizedLandmark> landmarks = new ArrayList<>();
    private RectF videoDisplayRect = new RectF(); // 视频实际显示区域（像素）
    private int rotationDegrees = 0; // 新增字段
    private boolean isFrontCamera = false;

    public void setRotationDegrees(int degrees) {
        this.rotationDegrees = degrees % 360;
        postInvalidate();
    }

    public void setFrontCamera(boolean front) {
        isFrontCamera = front;
        postInvalidate();
    }

    /* ------------------ 构造 ------------------ */
    public PixelPerfectOverlayView(Context context) {
        super(context);
        init();
    }

    public PixelPerfectOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        landmarkPaint.setColor(Color.CYAN);
        landmarkPaint.setStyle(Paint.Style.FILL);
        landmarkPaint.setStrokeWidth(12f);

        connectionPaint.setColor(Color.BLUE);
        connectionPaint.setStyle(Paint.Style.STROKE);
        connectionPaint.setStrokeWidth(8f);
        connectionPaint.setStrokeCap(Paint.Cap.ROUND);
        connectionPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    /* ------------------ 对外接口 ------------------ */
    /** 更新视频实际显示区域（像素级） */
    public void setVideoDisplayRect(RectF rect) {
        videoDisplayRect.set(rect);
        postInvalidate();
    }

    /** 更新标准姿态 landmarks */
    public void setStandardLandmarks(List<NormalizedLandmark> list) {
        landmarks = list;
        postInvalidate();
    }

    /* ------------------ 绘制 ------------------ */
    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.clipRect(videoDisplayRect);
        if (landmarks == null || landmarks.isEmpty() || videoDisplayRect.isEmpty()) return;

        canvas.save();

        // 应用视频的旋转
        if (rotationDegrees != 0) {
            canvas.rotate(rotationDegrees, getWidth() / 2f, getHeight() / 2f);
        }

        // 应用镜像（如果是前置摄像头）
        if (isFrontCamera) {
            canvas.scale(-1, 1, getWidth() / 2f, getHeight() / 2f);
        }

        // 画连接线
        for (int[] c : CONNECTIONS) {
            if (c[0] >= landmarks.size() || c[1] >= landmarks.size()) continue;

            NormalizedLandmark landmark1 = landmarks.get(c[0]);
            NormalizedLandmark landmark2 = landmarks.get(c[1]);

            // 检查关键点可见性
            if (isLandmarkValid(landmark1) && isLandmarkValid(landmark2)) {
                PointF p0 = map(landmark1);
                PointF p1 = map(landmark2);
                canvas.drawLine(p0.x, p0.y, p1.x, p1.y, connectionPaint);
            }
        }

        // 画关键点 - 只绘制核心关键点
        for (int index : CORE_LANDMARK_INDICES) {
            if (index < landmarks.size()) {
                NormalizedLandmark l = landmarks.get(index);
                if (isLandmarkValid(l)) {
                    PointF p = map(l);

                    // 根据关节重要性调整点的大小
                    float radius = 8f; // 默认大小

                    // 重要关节点（髋、膝、肩）绘制更大
                    if (index == PoseLandmark.LEFT_HIP || index == PoseLandmark.RIGHT_HIP ||
                            index == PoseLandmark.LEFT_KNEE || index == PoseLandmark.RIGHT_KNEE ||
                            index == PoseLandmark.LEFT_SHOULDER || index == PoseLandmark.RIGHT_SHOULDER) {
                        radius = 12f;
                    }

                    canvas.drawCircle(p.x, p.y, radius, landmarkPaint);
                }
            }
        }

        canvas.restore();
    }

    // 检查关键点是否有效
    private boolean isLandmarkValid(NormalizedLandmark landmark) {
        return landmark != null &&
                landmark.visibility().isPresent() &&
                landmark.visibility().get() >= 0.3f;
    }

    // 修正坐标映射方法
    private PointF map(NormalizedLandmark l) {
        float x = l.x();
        float y = l.y();

        /* ---------- 应用镜像（如果是前置摄像头） ---------- */
        if (isFrontCamera) {
            x = 1.0f - x; // 水平镜像
        }

        /* ---------- 映射到屏幕像素 ---------- */
        return new PointF(
                videoDisplayRect.left + x * videoDisplayRect.width(),
                videoDisplayRect.top + y * videoDisplayRect.height()
        );
    }
}