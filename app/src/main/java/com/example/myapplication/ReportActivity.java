package com.example.myapplication;

import android.graphics.*;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.poselandmarker.R;
import java.util.ArrayList;
import java.util.List;
//AI辅助生成 Kimi k2.5 2026.3.4
public class ReportActivity extends AppCompatActivity {

    private ReportData report;
    private ImageView chartView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report);

        report = (ReportData) getIntent().getSerializableExtra("REPORT_DATA");
        if (report == null) {
            finish();
            return;
        }

        initHeader();
        initChart();
    }

    private void initHeader() {
        ((TextView) findViewById(R.id.tv_action)).setText("动作：" + report.actionType);
        ((TextView) findViewById(R.id.tv_duration)).setText("时长：" + (report.durationMs / 1000) + " 秒");
        ((TextView) findViewById(R.id.tv_similarity)).setText("平均相似度：" + String.format("%.1f%%", report.avgSimilarity * 100));
        ((TextView) findViewById(R.id.tv_poses)).setText("有效帧：" + report.validFrames + " / " + report.totalFrames);
        ((TextView) findViewById(R.id.tv_count)).setText("深蹲次数：" + report.squatCount);
    }

    private void initChart() {
        chartView = findViewById(R.id.chart_view);
        Bitmap bmp = drawChartWithAxis(600, 350);
        chartView.setImageBitmap(bmp);
    }

    /* 画带坐标轴的折线图 */
    private Bitmap drawChartWithAxis(int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        // 背景
        canvas.drawColor(Color.WHITE);

        final float pad = 50f;          // 留边
        final float graphW = w - 2 * pad;
        final float graphH = h - 2 * pad - 30f; // 底部留 30 给横轴标签

        // 1. 网格 + 边框
        p.setColor(0xFFCCCCCC);
        p.setStrokeWidth(1f);
        for (int i = 0; i <= 6; i++) {
            float y = pad + i * graphH / 6;
            canvas.drawLine(pad, y, w - pad, y, p); // 横向
        }
        canvas.drawRect(pad, pad, w - pad, pad + graphH, p); // 外框

        // 2. 坐标轴
        p.setColor(Color.BLACK);
        p.setStrokeWidth(2f);
        canvas.drawLine(pad, pad + graphH, w - pad, pad + graphH,p); // 横轴
        canvas.drawLine(pad, pad, pad, pad + graphH,p);             // 纵轴
        canvas.drawLine(w - pad, pad, w - pad, pad + graphH, p);

        // 3. 纵轴标签（角度 0-180）
        p.setTextSize(20f);
        p.setTextAlign(Paint.Align.RIGHT);
        for (int i = 0; i <= 6; i++) {
            float y = pad + i * graphH / 6;
            int val = 180 - i * 30;
            canvas.drawText(val + "°", pad - 8, y + 6, p);
        }

        // 4. 折线
        drawLine(canvas, report.kneeAngleList, Color.GREEN, pad, graphW, graphH, 180f, 0f);
        drawLine(canvas, report.hipAngleList, Color.RED, pad, graphW, graphH, 180f, 0f);
        drawLine(canvas, scaleList(report.similarityList, 100f), Color.BLUE, pad, graphW, graphH, 100f, 0f);

        // 5. 图例（颜色与折线一致）
        float legendY = h - 15f;
        float legendTextSize = 22f;
        p.setTextSize(legendTextSize);
        p.setTextAlign(Paint.Align.LEFT);

        // 膝角度
        p.setColor(Color.GREEN);
        canvas.drawLine(pad, legendY, pad + 30, legendY, p);
        canvas.drawText("膝角度", pad + 35, legendY + 6, p);

        // 髋角度
        p.setColor(Color.RED);
        float x2 = pad + 130;
        canvas.drawLine(x2, legendY, x2 + 30, legendY, p);
        canvas.drawText("髋角度", x2 + 35, legendY + 6, p);

        // 相似度
        p.setColor(Color.BLUE);
        float x3 = pad + 260;
        canvas.drawLine(x3, legendY, x3 + 30, legendY, p);
        canvas.drawText("相似度%", x3 + 35, legendY + 6, p);

        return bmp;
    }

    /* 通用画线 */
    private void drawLine(Canvas canvas, List<Float> data, int color,
                          float pad, float graphW, float graphH,
                          float max, float min) {
        if (data == null || data.size() < 2) return;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStrokeWidth(4f);
        float step = graphW / (data.size() - 1);
        for (int i = 1; i < data.size(); i++) {
            float x1 = pad + (i - 1) * step;
            float x2 = pad + i * step;
            float y1 = pad + graphH * (1f - (data.get(i - 1) - min) / (max - min));
            float y2 = pad + graphH * (1f - (data.get(i) - min) / (max - min));
            canvas.drawLine(x1, y1, x2, y2, p);
        }
    }

    /* 0-1 -> 0-scale */
    private List<Float> scaleList(List<Float> src, float scale) {
        List<Float> dst = new ArrayList<>();
        for (Float v : src) dst.add(v * scale);
        return dst;
    }
}