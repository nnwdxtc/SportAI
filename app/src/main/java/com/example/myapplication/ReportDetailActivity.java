package com.example.myapplication;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.poselandmarker.R;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import androidx.core.graphics.Insets;           // ✅ 新增
import androidx.core.view.ViewCompat;           // ✅ 新增
import androidx.core.view.WindowInsetsCompat;   // ✅ 新增
import androidx.activity.EdgeToEdge;

public class ReportDetailActivity extends AppCompatActivity {

    private static final String TAG = "ReportDetailActivity";
    private static final String BASE_URL = "http://114.55.105.76:8080";
    private static final String REPORT_DETAIL_URL = BASE_URL + "/api/report/";
    private static final int REQUEST_PERMISSION_STORAGE = 2001;

    private Toolbar toolbar;
    private TextView tvSportName, tvStartTime, tvDuration, tvExerciseCount, tvAvgSimilarity;
    private TextView tvMaxSimilarity, tvMinSimilarity, tvAvgSimilarityDetail;
    private SimilarityChartView chartView;
    private ProgressBar progressBar;
    private ScrollView scrollView;

    private OkHttpClient client;
    private long reportId;
    private WorkoutRecord currentRecord;
    private boolean isLoading = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        EdgeToEdge.enable(this);
        setContentView(R.layout.report_detail);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        reportId = getIntent().getLongExtra("report_id", 0);
        if (reportId == 0) {
            Toast.makeText(this, "报告ID无效", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupToolbar();
        initHttpClient();
        loadReportDetail();
    }

    private void initViews() {
        toolbar = findViewById(R.id.toolbar);
        tvSportName = findViewById(R.id.tv_sport_name);
        tvStartTime = findViewById(R.id.tv_start_time);
        tvDuration = findViewById(R.id.tv_duration);
        tvExerciseCount = findViewById(R.id.tv_exercise_count);
        tvAvgSimilarity = findViewById(R.id.tv_avg_similarity);
        tvMaxSimilarity = findViewById(R.id.tv_max_similarity);
        tvMinSimilarity = findViewById(R.id.tv_min_similarity);
        tvAvgSimilarityDetail = findViewById(R.id.tv_avg_similarity_detail);
        chartView = findViewById(R.id.chart_view);
        progressBar = findViewById(R.id.progress_bar);
        scrollView = findViewById(R.id.scroll_view);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.report_detail_menu, menu);
        for (int i = 0; i < menu.size(); i++) {
            MenuItem item = menu.getItem(i);
            if (item.getIcon() != null) {
                item.getIcon().setTint(getResources().getColor(R.color.white));
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_download_pdf) {
            if (currentRecord != null && !isLoading) {
                checkStoragePermissionAndDownload();
            } else {
                Toast.makeText(this, "数据加载中，请稍后...", Toast.LENGTH_SHORT).show();
            }
            return true;
        } else if (id == R.id.action_share) {
            if (currentRecord != null && !isLoading) {
                shareReport();
            } else {
                Toast.makeText(this, "数据加载中，请稍后...", Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void checkStoragePermissionAndDownload() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ 不需要存储权限
            generateAndDownloadPdf();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 不需要存储权限，直接保存
            generateAndDownloadPdf();
        } else {
            // Android 9及以下需要存储权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_PERMISSION_STORAGE);
            } else {
                generateAndDownloadPdf();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_PERMISSION_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                generateAndDownloadPdf();
            } else {
                Toast.makeText(this, "需要存储权限才能下载PDF", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void generateAndDownloadPdf() {
        showProgress(true);

        // 捕获图表截图
        Bitmap chartBitmap = captureChartBitmap();

        // 生成PDF
        new Thread(() -> {
            String pdfPath = PdfReportGenerator.generateReport(this, currentRecord, chartBitmap);
            runOnUiThread(() -> {
                showProgress(false);
                if (pdfPath != null) {
                    showSuccessDialog(pdfPath);
                } else {
                    Toast.makeText(this, "PDF生成失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private void shareReport() {
        showProgress(true);

        Bitmap chartBitmap = captureChartBitmap();

        new Thread(() -> {
            String pdfPath = PdfReportGenerator.generateReport(this, currentRecord, chartBitmap);
            runOnUiThread(() -> {
                showProgress(false);
                if (pdfPath != null) {
                    sharePdfFile(pdfPath);
                } else {
                    Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show();
                }
            });
        }).start();
    }

    private Bitmap captureChartBitmap() {
        if (chartView == null) return null;

        try {
            chartView.setDrawingCacheEnabled(true);
            Bitmap bitmap = Bitmap.createBitmap(chartView.getDrawingCache());
            chartView.setDrawingCacheEnabled(false);
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "截图失败: " + e.getMessage());
            return null;
        }
    }

    private void sharePdfFile(String pdfPath) {
        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        android.content.Intent shareIntent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        shareIntent.setType("application/pdf");
        shareIntent.putExtra(android.content.Intent.EXTRA_STREAM, androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", pdfFile));
        shareIntent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

        startActivity(android.content.Intent.createChooser(shareIntent, "分享运动报告"));
    }

    private void showSuccessDialog(String pdfPath) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("下载成功");
        builder.setMessage("PDF报告已保存到:\n" + pdfPath + "\n\n是否立即查看？");
        builder.setPositiveButton("查看", (dialog, which) -> openPdfFile(pdfPath));
        builder.setNegativeButton("取消", null);
        builder.show();
    }

    private void openPdfFile(String pdfPath) {
        File pdfFile = new File(pdfPath);
        if (!pdfFile.exists()) {
            Toast.makeText(this, "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
        intent.setDataAndType(androidx.core.content.FileProvider.getUriForFile(
                this, getPackageName() + ".fileprovider", pdfFile), "application/pdf");
        intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);

        try {
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "请安装PDF阅读器应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void showProgress(boolean show) {
        if (progressBar != null) {
            progressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        }
        if (scrollView != null) {
            scrollView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void initHttpClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build();
    }

    private void loadReportDetail() {
        SharedPreferences sp = getSharedPreferences("user", MODE_PRIVATE);
        String token = sp.getString("token", "");

        if (token.isEmpty()) {
            Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        showProgress(true);
        isLoading = true;

        Request request = new Request.Builder()
                .url(REPORT_DETAIL_URL + reportId)
                .addHeader("Authorization", token)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> {
                    showProgress(false);
                    isLoading = false;
                    Toast.makeText(ReportDetailActivity.this,
                            "加载失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    finish();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String result = response.body().string();
                Log.d(TAG, "报告详情响应: " + result);

                if (response.isSuccessful()) {
                    try {
                        JSONObject json = new JSONObject(result);
                        if (json.optInt("code") == 0) {
                            JSONObject data = json.optJSONObject("data");
                            if (data != null) {
                                runOnUiThread(() -> {
                                    parseAndDisplayData(data);
                                    isLoading = false;
                                    showProgress(false);
                                });
                            } else {
                                runOnUiThread(() -> {
                                    isLoading = false;
                                    showProgress(false);
                                    Toast.makeText(ReportDetailActivity.this,
                                            "报告数据为空", Toast.LENGTH_SHORT).show();
                                    finish();
                                });
                            }
                        } else {
                            runOnUiThread(() -> {
                                isLoading = false;
                                showProgress(false);
                                Toast.makeText(ReportDetailActivity.this,
                                        json.optString("msg", "加载失败"),
                                        Toast.LENGTH_SHORT).show();
                                finish();
                            });
                        }
                    } catch (Exception e) {
                        runOnUiThread(() -> {
                            isLoading = false;
                            showProgress(false);
                            Toast.makeText(ReportDetailActivity.this,
                                    "解析数据失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            finish();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        isLoading = false;
                        showProgress(false);
                        Toast.makeText(ReportDetailActivity.this,
                                "HTTP错误: " + response.code(), Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
            }
        });
    }

    private void parseAndDisplayData(JSONObject data) {
        try {
            currentRecord = new WorkoutRecord();

            // 基本信息
            String sportName = data.optString("sportName", "未知运动");
            long startTime = data.optLong("startTime", 0);
            int durationMs = data.optInt("durationMs", 0);
            int exerciseCount = data.optInt("exerciseCount", 0);
            float similarityAvg = (float) data.optDouble("similarityAvg", 0);

            currentRecord.setActionName(sportName);
            currentRecord.setStartTime(startTime);
            currentRecord.setDurationMs(durationMs);
            currentRecord.setCount(exerciseCount);
            currentRecord.setAvgSimilarity(similarityAvg);
            currentRecord.setReportId(reportId);
            currentRecord.setSynced(true);

            // 更新UI
            tvSportName.setText(sportName);

            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
            tvStartTime.setText(sdf.format(new Date(startTime)));

            int durationSec = (durationMs + 999) / 1000;
            int minutes = durationSec / 60;
            int seconds = durationSec % 60;
            if (minutes > 0) {
                tvDuration.setText(minutes + "分钟" + (seconds > 0 ? seconds + "秒" : ""));
            } else {
                tvDuration.setText(seconds + "秒");
            }

            tvExerciseCount.setText(exerciseCount > 0 ? exerciseCount + "次" : "无");
            tvAvgSimilarity.setText(String.format("%.1f%%", similarityAvg * 100));

            // 解析时序数据
            JSONArray timestampsArray = data.optJSONArray("timestamps");
            JSONArray similaritiesArray = data.optJSONArray("similarities");

            List<Long> timestamps = new ArrayList<>();
            List<Float> similarities = new ArrayList<>();

            if (timestampsArray != null && similaritiesArray != null && timestampsArray.length() > 0) {
                int size = Math.min(timestampsArray.length(), similaritiesArray.length());
                for (int i = 0; i < size; i++) {
                    long ts = timestampsArray.getLong(i);
                    float sim = (float) similaritiesArray.getDouble(i);
                    timestamps.add(ts);
                    similarities.add(sim);
                    currentRecord.addTimestamp(ts);
                    currentRecord.addSimilarity(sim);
                }
                Log.d(TAG, "加载时序数据: " + size + "个点");
            }

            // 如果没有时序数据，生成演示数据
            if (similarities.isEmpty()) {
                generateDemoData(similarities, timestamps, durationMs, similarityAvg);
                for (int i = 0; i < similarities.size(); i++) {
                    currentRecord.addTimestamp(timestamps.get(i));
                    currentRecord.addSimilarity(similarities.get(i));
                }
            }

            // 绘制折线图
            if (!similarities.isEmpty()) {
                // 生成 0% ~ 100% 进度数据
                List<Float> progressList = new ArrayList<>();
                int totalPoints = similarities.size();
                for (int i = 0; i < totalPoints; i++) {
                    float progress = (i / (float)(totalPoints - 1)) * 100;
                    progressList.add(progress);
                }

                // 传入进度，不再传入时间
                chartView.setData(similarities, progressList);

                float maxSim = 0;
                float minSim = 100;
                float sumSim = 0;
                for (float s : similarities) {
                    float percent = s * 100;
                    if (percent > maxSim) maxSim = percent;
                    if (percent < minSim) minSim = percent;
                    sumSim += s;
                }
                float avgSim = (sumSim / similarities.size()) * 100;

                tvMaxSimilarity.setText(String.format("最高: %.1f%%", maxSim));
                tvMinSimilarity.setText(String.format("最低: %.1f%%", minSim));
                tvAvgSimilarityDetail.setText(String.format("平均: %.1f%%", avgSim));
            }

        } catch (Exception e) {
            Log.e(TAG, "解析数据失败: " + e.getMessage(), e);
        }
    }

    private void generateDemoData(List<Float> similarities, List<Long> timestamps,
                                  int durationMs, float avgSimilarity) {
        int durationSec = (durationMs + 999) / 1000;
        int dataPoints = Math.min(durationSec, 30);
        if (dataPoints < 5) dataPoints = 10;

        for (int i = 0; i <= dataPoints; i++) {
            long timestampMs = ((long)i * durationMs / dataPoints / 1000) * 1000;
            timestamps.add(timestampMs);
            double angle = (i * 2 * Math.PI / dataPoints);
            float similarity = (float) (avgSimilarity + 0.15 * Math.sin(angle));
            similarity = Math.max(0.3f, Math.min(0.95f, similarity));
            similarities.add(similarity);
        }
    }
}