package com.example.myapplication;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
//AI辅助生成 Kimi k2.5 2026.3.5
public class SimilarityChartView extends View {

    // 数据
    private List<Float> similarities = new ArrayList<>();  // 相似度数据（0-1）
    private List<Float> progressList = new ArrayList<>();   // 进度 0~100%

    // 画笔
    private Paint gridPaint;
    private Paint linePaint;
    private Paint pointPaint;
    private Paint textPaint;
    private Paint fillPaint;
    private Paint axisPaint;

    // 图表边距
    private int paddingLeft = 60;
    private int paddingRight = 40;
    private int paddingTop = 30;
    private int paddingBottom = 40;

    // 坐标范围
    private float minX = 0;
    private float maxX = 100;
    private float minY = 0;
    private float maxY = 100;

    // 工具
    private Path linePath;
    private Path fillPath;

    public SimilarityChartView(Context context) {
        super(context);
        init();
    }

    public SimilarityChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SimilarityChartView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        // 网格线画笔
        gridPaint = new Paint();
        gridPaint.setColor(Color.parseColor("#E0E0E0"));
        gridPaint.setStrokeWidth(1);
        gridPaint.setStyle(Paint.Style.STROKE);
        gridPaint.setPathEffect(new DashPathEffect(new float[]{5, 5}, 0));

        // 折线画笔
        linePaint = new Paint();
        linePaint.setColor(Color.parseColor("#9933FF"));
        linePaint.setStrokeWidth(4);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setAntiAlias(true);

        // 数据点画笔
        pointPaint = new Paint();
        pointPaint.setColor(Color.parseColor("#9933FF"));
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setAntiAlias(true);

        // 文字画笔
        textPaint = new Paint();
        textPaint.setColor(Color.parseColor("#666666"));
        textPaint.setTextSize(28);
        textPaint.setAntiAlias(true);

        // 填充区域画笔
        fillPaint = new Paint();
        fillPaint.setColor(Color.parseColor("#339933FF"));
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setAntiAlias(true);

        // 坐标轴画笔
        axisPaint = new Paint();
        axisPaint.setColor(Color.parseColor("#999999"));
        axisPaint.setStrokeWidth(2);
        axisPaint.setStyle(Paint.Style.STROKE);

        linePath = new Path();
        fillPath = new Path();
    }

    /**
     * 设置图表数据（横轴 = 进度 0~100%）
     */
    public void setData(List<Float> similarities, List<Float> progressList) {
        this.similarities = similarities;
        this.progressList = progressList;
        calculateRange();
        invalidate();
    }

    /**
     * 计算坐标范围
     */
    private void calculateRange() {
        if (similarities == null || similarities.isEmpty()) {
            minX = 0;
            maxX = 100;
            minY = 0;
            maxY = 100;
            return;
        }

        // X轴固定为 0~100 进度
        minX = 0;
        maxX = 100;

        // Y轴范围：0~100%
        minY = 0;
        maxY = 100;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (similarities == null || similarities.isEmpty()) {
            drawNoDataMessage(canvas);
            return;
        }

        drawGrid(canvas);
        drawAxes(canvas);
        drawLineAndPoints(canvas);
        drawAxisLabels(canvas);
    }

    /**
     * 绘制无数据提示
     */
    private void drawNoDataMessage(Canvas canvas) {
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(40);
        textPaint.setColor(Color.parseColor("#999999"));
        canvas.drawText("暂无数据", getWidth() / 2f, getHeight() / 2f, textPaint);
        textPaint.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * 绘制网格线
     */
    private void drawGrid(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        int chartWidth = width - paddingLeft - paddingRight;
        int chartHeight = height - paddingTop - paddingBottom;

        // 水平网格线（5条）
        for (int i = 0; i <= 4; i++) {
            float y = paddingTop + chartHeight * i / 4f;
            canvas.drawLine(paddingLeft, y, width - paddingRight, y, gridPaint);
        }

        // 垂直网格线（6条）
        for (int i = 0; i <= 6; i++) {
            float x = paddingLeft + chartWidth * i / 6f;
            canvas.drawLine(x, paddingTop, x, height - paddingBottom, gridPaint);
        }
    }

    /**
     * 绘制坐标轴
     */
    private void drawAxes(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();

        // X轴
        canvas.drawLine(paddingLeft, height - paddingBottom,
                width - paddingRight, height - paddingBottom, axisPaint);
        // Y轴
        canvas.drawLine(paddingLeft, paddingTop,
                paddingLeft, height - paddingBottom, axisPaint);
    }

    /**
     * 绘制折线和数据点
     */
    private void drawLineAndPoints(Canvas canvas) {
        if (similarities == null || similarities.size() < 2) {
            return;
        }

        int width = getWidth();
        int height = getHeight();
        int chartWidth = width - paddingLeft - paddingRight;
        int chartHeight = height - paddingTop - paddingBottom;

        linePath.reset();
        fillPath.reset();

        boolean firstPoint = true;
        List<PointF> points = new ArrayList<>();

        for (int i = 0; i < similarities.size(); i++) {
            float similarityPercent = similarities.get(i) * 100;
            float progress = progressList.get(i);

            // X：进度百分比
            float x = paddingLeft + (progress / maxX) * chartWidth;

            // Y：相似度
            float y = paddingTop + chartHeight * (1 - similarityPercent / maxY);

            x = Math.max(paddingLeft, Math.min(width - paddingRight, x));
            y = Math.max(paddingTop, Math.min(height - paddingBottom, y));

            PointF point = new PointF(x, y);
            points.add(point);

            if (firstPoint) {
                linePath.moveTo(x, y);
                fillPath.moveTo(x, y);
                firstPoint = false;
            } else {
                linePath.lineTo(x, y);
                fillPath.lineTo(x, y);
            }
        }

        // 填充区域
        if (!points.isEmpty()) {
            fillPath.lineTo(points.get(points.size() - 1).x, height - paddingBottom);
            fillPath.lineTo(paddingLeft, height - paddingBottom);
            fillPath.close();
            canvas.drawPath(fillPath, fillPaint);
        }

        // 折线
        canvas.drawPath(linePath, linePaint);

        // 数据点
        for (PointF point : points) {
            canvas.drawCircle(point.x, point.y, 6, pointPaint);
            Paint whitePaint = new Paint();
            whitePaint.setColor(Color.WHITE);
            whitePaint.setStyle(Paint.Style.STROKE);
            whitePaint.setStrokeWidth(2);
            canvas.drawCircle(point.x, point.y, 6, whitePaint);
        }
    }

    /**
     * 绘制坐标轴标签
     */

    private void drawAxisLabels(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        int chartWidth = width - paddingLeft - paddingRight;
        int chartHeight = height - paddingTop - paddingBottom;

        textPaint.setTextSize(24);
        textPaint.setTextAlign(Paint.Align.CENTER);

        // X 轴刻度：0% 25% 50% 75% 100%
        int[] showProgress = {0, 25, 50, 75, 100};
        for (int p : showProgress) {
            float x = paddingLeft + chartWidth * p / 100f;
            canvas.drawText(p + "%", x, height - paddingBottom + 25, textPaint);
        }

        // Y 轴刻度
        for (int i = 0; i <= 4; i++) {
            float y = paddingTop + chartHeight * i / 4f;
            float similarityValue = maxY * (1 - i / 4f);
            String label = String.format("%.0f%%", similarityValue);
            canvas.drawText(label, paddingLeft - 15, y + 8, textPaint);
        }

        textPaint.setTextSize(32);
        textPaint.setColor(Color.parseColor("#333333"));


        textPaint.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText("进度", width - paddingRight - 40, height - paddingBottom + 25, textPaint);


        textPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText("相似度", paddingLeft, paddingTop + 35, textPaint);
    }
}