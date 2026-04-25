package com.example.fitness.sdk.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import com.example.fitness.sdk.model.Keypoint;
import com.example.fitness.sdk.model.SkeletonFrame;

import java.util.List;

public class PoseOverlayView extends View {

    private static final int[][] CONNECTIONS = {
            {11, 12}, {11, 13}, {13, 15}, {12, 14}, {14, 16},
            {11, 23}, {23, 25}, {25, 27}, {27, 29}, {29, 31},
            {12, 24}, {24, 26}, {26, 28}, {28, 30}, {30, 32},
            {23, 24}
    };

    private static final int[] CORE_LANDMARKS = {11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28};

    private SkeletonFrame currentSkeleton;
    private Paint skeletonPaint;
    private Paint jointPaint;
    private Paint textPaint;
    private Paint bgPaint;
    private float similarityScore = 0f;

    public PoseOverlayView(Context context) {
        super(context);
        init();
    }

    public PoseOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        skeletonPaint = new Paint();
        skeletonPaint.setStyle(Paint.Style.STROKE);
        skeletonPaint.setStrokeWidth(8f);
        skeletonPaint.setStrokeCap(Paint.Cap.ROUND);

        jointPaint = new Paint();
        jointPaint.setStyle(Paint.Style.FILL);

        textPaint = new Paint();
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(60f);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setAntiAlias(true);

        bgPaint = new Paint();
        bgPaint.setColor(Color.BLACK);
        bgPaint.setAlpha(180);
    }

    public void setSkeletonFrame(SkeletonFrame skeletonFrame) {
        this.currentSkeleton = skeletonFrame;
        invalidate();
    }

    public void setSimilarityScore(float score) {
        this.similarityScore = Math.max(0f, Math.min(1f, score));
        invalidate();
    }

    public void clear() {
        this.currentSkeleton = null;
        this.similarityScore = 0f;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentSkeleton == null || !currentSkeleton.hasValidPose()) return;

        List<Keypoint> keypoints = currentSkeleton.getKeypoints();
        if (keypoints == null || keypoints.isEmpty()) return;

        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 保存画布并旋转
        canvas.save();
        canvas.rotate(90, w / 2f, h / 2f);

        // 旋转后交换宽高
        int rotatedW = h;
        int rotatedH = w;

        // 根据相似度设置颜色
        int color = getColorByScore(similarityScore);
        skeletonPaint.setColor(color);
        jointPaint.setColor(color);

        // 绘制连接线
        for (int[] conn : CONNECTIONS) {
            Keypoint start = findKeypoint(keypoints, conn[0]);
            Keypoint end = findKeypoint(keypoints, conn[1]);
            if (start != null && end != null && start.isValid() && end.isValid()) {
                float startX = start.getX() * rotatedW;
                float startY = start.getY() * rotatedH;
                float endX = end.getX() * rotatedW;
                float endY = end.getY() * rotatedH;
                canvas.drawLine(startX, startY, endX, endY, skeletonPaint);
            }
        }

        // 绘制关节点
        for (int id : CORE_LANDMARKS) {
            Keypoint kp = findKeypoint(keypoints, id);
            if (kp != null && kp.isValid()) {
                float radius = kp.getVisibility() > 0.7f ? 12f : 8f;
                float x = kp.getX() * rotatedW;
                float y = kp.getY() * rotatedH;
                canvas.drawCircle(x, y, radius, jointPaint);
            }
        }


        canvas.restore();
    }

    private Keypoint findKeypoint(List<Keypoint> keypoints, int id) {
        for (Keypoint kp : keypoints) {
            if (kp.getId() == id) return kp;
        }
        return null;
    }

    private int getColorByScore(float score) {
        if (score >= 0.8f) return Color.parseColor("#00FF00");
        if (score >= 0.6f) return Color.parseColor("#90EE90");
        if (score >= 0.4f) return Color.parseColor("#FFA500");
        if (score >= 0.2f) return Color.parseColor("#FF6347");
        return Color.RED;
    }
}