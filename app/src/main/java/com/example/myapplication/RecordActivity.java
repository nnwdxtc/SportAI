package com.example.myapplication;

import static com.example.poselandmarker.R.*;

import android.annotation.SuppressLint;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.poselandmarker.R;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class RecordActivity extends AppCompatActivity {

    private static final String TAG = "RecordActivity";

    private ImageView ivHome, ivHistory, ivProfile;
    private TextView tvHome, tvHistory, tvProfile;
    private TextView tvCurrentDate, tvTotalWorkouts, tvTotalDuration;
    private LinearLayout layoutListHeader, layoutRecordsContainer, layoutEmptyState;
    private ScrollView scrollViewRecords;
    private Button btnStartWorkout;
    private TextView tvTodayCount, tvTodayDuration;

    // 删除模式相关控件
    private Button btnDeleteMode, btnDeleteConfirm, btnDeleteCancel;
    private LinearLayout layoutDeleteBar;
    private boolean isDeleteMode = false;
    private List<WorkoutRecord> currentRecords = new ArrayList<>();
    private List<Integer> selectedPositions = new ArrayList<>();

    private WorkoutDataService workoutDataService;

    private BroadcastReceiver workoutDataReceiver;

    private static final int COLOR_PRIMARY = 0xFF9933FF;
    private static final int COLOR_DEFAULT = 0xFF999999;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.record);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        workoutDataService = new WorkoutDataService(this);

        initViews();
        setBottomNavListener();
        setButtonListeners();

        registerWorkoutDataReceiver();

        refreshData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshData();
        exitDeleteMode();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (workoutDataReceiver != null) {
            try {
                unregisterReceiver(workoutDataReceiver);
            } catch (Exception e) {
                Log.e(TAG, "注销广播接收器失败: " + e.getMessage());
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerWorkoutDataReceiver() {
        workoutDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("ACTION_WORKOUT_DATA_UPDATED".equals(intent.getAction())) {
                    Log.d(TAG, "收到运动数据更新广播，刷新记录");
                    refreshData();
                    exitDeleteMode();

                    String actionName = intent.getStringExtra("action_name");
                    float similarity = intent.getFloatExtra("similarity", 0f);
                    boolean isNonRealtime = intent.getBooleanExtra("is_non_realtime", false);

                    String type = isNonRealtime ? "非实时比对" : "实时运动";
                    Toast.makeText(RecordActivity.this,
                            String.format("%s报告已保存\n动作: %s 相似度: %.1f%%", type, actionName, similarity * 100),
                            Toast.LENGTH_SHORT).show();
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("ACTION_WORKOUT_DATA_UPDATED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(workoutDataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(workoutDataReceiver, filter);
        }
    }

    private void initViews() {
        ivHome = findViewById(R.id.iv_home);
        ivHistory = findViewById(R.id.iv_history);
        ivProfile = findViewById(R.id.iv_profile);
        tvHome = findViewById(R.id.tv_home);
        tvHistory = findViewById(R.id.tv_history);
        tvProfile = findViewById(R.id.tv_profile);

        tvCurrentDate = findViewById(R.id.tv_current_date);
        tvTotalWorkouts = findViewById(R.id.tv_total_workouts);
        tvTotalDuration = findViewById(R.id.tv_total_duration);
        layoutListHeader = findViewById(R.id.layout_list_header);
        layoutRecordsContainer = findViewById(R.id.layout_records_container);
        layoutEmptyState = findViewById(R.id.layout_empty_state);
        scrollViewRecords = findViewById(R.id.scroll_view_records);
        btnStartWorkout = findViewById(R.id.btn_start_workout);

        // 删除模式控件
        btnDeleteMode = findViewById(R.id.btn_delete_mode);
        btnDeleteConfirm = findViewById(R.id.btn_delete_confirm);
        btnDeleteCancel = findViewById(R.id.btn_delete_cancel);
        layoutDeleteBar = findViewById(R.id.layout_delete_bar);

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy年M月d日 EEEE", Locale.CHINESE);
        String currentDate = sdf.format(new Date());
        if (tvCurrentDate != null) {
            tvCurrentDate.setText(currentDate);
        }
    }

    private void refreshData() {
        Log.d(TAG, "刷新数据...");

        SharedPreferences userSp = getSharedPreferences("user", MODE_PRIVATE);
        String token = userSp.getString("token", "");

        if (token.isEmpty()) {
            loadLocalData();
        } else {
            loadServerData(token);
        }
    }

    private void loadLocalData() {
        int[] totalStats = workoutDataService.getLocalTotalStats();
        int totalCount = totalStats[0];
        int totalDurationSec = totalStats[1] / 1000;

        int[] todayStats = workoutDataService.getLocalTodayStats();
        int todayCount = todayStats[0];
        int todayDurationSec = todayStats[1] / 1000;

        Log.d(TAG, String.format("本地数据: 总次数=%d, 总时长=%d秒, 今日次数=%d, 今日时长=%d秒",
                totalCount, totalDurationSec, todayCount, todayDurationSec));

        updateStatsUI(todayCount, todayDurationSec, totalCount, totalDurationSec);

        currentRecords = workoutDataService.getLocalRecords();
        displayRecords(currentRecords);
    }

    private void loadServerData(String token) {
        loadLocalData();

        workoutDataService.fetchServerReports(token, new WorkoutDataService.ServerReportsCallback() {
            @Override
            public void onSuccess(List<WorkoutRecord> records) {
                runOnUiThread(() -> {
                    Log.d(TAG, "服务器数据获取成功，记录数: " + records.size());
                    currentRecords = records;
                    displayRecords(records);

                    int totalCount = 0;
                    int totalDurationSec = 0;
                    for (WorkoutRecord record : records) {
                        totalCount++;
                        totalDurationSec += record.getDurationSeconds();
                    }

                    String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
                    int todayCount = 0;
                    int todayDurationSec = 0;
                    for (WorkoutRecord record : records) {
                        String recordDate = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
                                .format(new Date(record.getStartTime()));
                        if (today.equals(recordDate)) {
                            todayCount++;
                            todayDurationSec += record.getDurationSeconds();
                        }
                    }

                    updateStatsUI(todayCount, todayDurationSec, totalCount, totalDurationSec);
                });
            }

            @Override
            public void onFailure(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "获取服务器数据失败: " + error);
                });
            }
        });
    }

    private void updateStatsUI(int todayCount, int todayDurationSec, int totalCount, int totalDurationSec) {
        if (tvTotalWorkouts != null) {
            tvTotalWorkouts.setText(String.valueOf(todayCount));
        }
        if (tvTotalDuration != null) {
            tvTotalDuration.setText(todayDurationSec + "秒");
        }
    }

    private void displayRecords(List<WorkoutRecord> records) {
        if (records == null || records.isEmpty()) {
            showEmptyState();
            return;
        }

        showRecordList();
        layoutRecordsContainer.removeAllViews();

        for (int i = 0; i < records.size(); i++) {
            WorkoutRecord record = records.get(i);
            View recordCard = createRecordCard(record, i);
            layoutRecordsContainer.addView(recordCard);
        }
    }

    private View createRecordCard(WorkoutRecord record, int position) {
        View card = LayoutInflater.from(this).inflate(R.layout.item_workout_record, null);

        TextView tvActionName = card.findViewById(R.id.tv_action_name);
        TextView tvDateTime = card.findViewById(R.id.tv_date_time);
        TextView tvDuration = card.findViewById(R.id.tv_duration);
        TextView tvCount = card.findViewById(R.id.tv_count);
        TextView tvSimilarity = card.findViewById(R.id.tv_similarity);
        ImageView ivSyncStatus = card.findViewById(R.id.iv_sync_status);
        ImageView ivCategoryImage = card.findViewById(R.id.iv_category_image);
        CheckBox cbSelect = card.findViewById(R.id.cb_select);
        View cardContent = card.findViewById(R.id.card_content);

        if (tvActionName != null) tvActionName.setText(record.getActionName());
        if (tvDateTime != null) tvDateTime.setText(record.getFormattedDateTime());
        if (tvDuration != null) tvDuration.setText(record.getFormattedDuration());
        if (tvCount != null) {
            if (record.getCount() > 0) {
                tvCount.setText(record.getCount() + "次");
                tvCount.setVisibility(View.VISIBLE);
            } else {
                tvCount.setVisibility(View.GONE);
            }
        }
        if (tvSimilarity != null) {
            int simPercent = (int)(record.getAvgSimilarity() * 100);
            tvSimilarity.setText("相似度: " + simPercent + "%");
        }

        if (ivCategoryImage != null) {
            String actionName = record.getActionName();
            if ("深蹲".equals(actionName)) {
                ivCategoryImage.setImageResource(R.drawable.img_category_squat);
            } else if ("拳击".equals(actionName)) {
                ivCategoryImage.setImageResource(R.drawable.img_category_boxing);
            } else if ("高抬腿".equals(actionName)) {
                ivCategoryImage.setImageResource(drawable.img_category_highknees);}
            else if ("罗马尼亚硬拉".equals(actionName)) {
                ivCategoryImage.setImageResource(R.drawable.img_category_deadlift);}
            else {
                ivCategoryImage.setImageResource(R.drawable.img_category_squat);
            }
        }

        if (ivSyncStatus != null) {
            if (record.isSynced()) {
                ivSyncStatus.setImageResource(android.R.drawable.presence_online);
                ivSyncStatus.setVisibility(View.VISIBLE);
            } else {
                ivSyncStatus.setImageResource(android.R.drawable.presence_offline);
                ivSyncStatus.setVisibility(View.VISIBLE);
            }
        }

        // 删除模式下的CheckBox显示逻辑
        if (cbSelect != null) {
            if (isDeleteMode) {
                cbSelect.setVisibility(View.VISIBLE);
                cbSelect.setChecked(selectedPositions.contains(position));
                cbSelect.setOnClickListener(v -> {
                    if (cbSelect.isChecked()) {
                        if (!selectedPositions.contains(position)) {
                            selectedPositions.add(position);
                        }
                    } else {
                        selectedPositions.remove(Integer.valueOf(position));
                    }
                    updateDeleteButtonText();
                });
            } else {
                cbSelect.setVisibility(View.GONE);
                cbSelect.setChecked(false);
            }
        }

        // 卡片内容点击事件（非删除模式时查看详情）
        View contentView = cardContent != null ? cardContent : card;
        contentView.setOnClickListener(v -> {
            if (isDeleteMode) {
                // 删除模式下点击切换选中状态
                if (cbSelect != null) {
                    cbSelect.setChecked(!cbSelect.isChecked());
                    if (cbSelect.isChecked()) {
                        if (!selectedPositions.contains(position)) {
                            selectedPositions.add(position);
                        }
                    } else {
                        selectedPositions.remove(Integer.valueOf(position));
                    }
                    updateDeleteButtonText();
                }
            } else {
                // 正常模式查看详情
                if (record.isSynced() && record.getReportId() > 0) {
                    Intent intent = new Intent(RecordActivity.this, ReportDetailActivity.class);
                    intent.putExtra("report_id", record.getReportId());
                    startActivity(intent);
                } else if (record.getReportFilePath() != null && !record.getReportFilePath().isEmpty()) {
                    showLocalReportDetail(record);
                } else {
                    showReportDetail(record);
                }
            }
        });

        return card;
    }

    private void updateDeleteButtonText() {
        if (btnDeleteConfirm != null) {
            int count = selectedPositions.size();
            if (count > 0) {
                btnDeleteConfirm.setText("删除(" + count + ")");
            } else {
                btnDeleteConfirm.setText("删除");
            }
        }
    }
//AI辅助生成 Kimi k2.5 2026.3.5
    /**
     * 进入删除模式
     */
    private void enterDeleteMode() {
        isDeleteMode = true;
        selectedPositions.clear();

        // 显示删除操作栏
        if (layoutDeleteBar != null) {
            layoutDeleteBar.setVisibility(View.VISIBLE);
        }

        // 隐藏开始运动按钮
        if (btnStartWorkout != null) {
            btnStartWorkout.setVisibility(View.GONE);
        }

        // 刷新列表显示CheckBox
        displayRecords(currentRecords);

        updateDeleteButtonText();
        Toast.makeText(this, "已进入删除模式，可多选记录", Toast.LENGTH_SHORT).show();
    }

    /**
     * 退出删除模式
     */
    private void exitDeleteMode() {
        isDeleteMode = false;
        selectedPositions.clear();

        // 隐藏删除操作栏
        if (layoutDeleteBar != null) {
            layoutDeleteBar.setVisibility(View.GONE);
        }

        // 显示开始运动按钮
        if (btnStartWorkout != null) {
            btnStartWorkout.setVisibility(View.VISIBLE);
        }

        // 刷新列表隐藏CheckBox
        displayRecords(currentRecords);
    }

    /**
     * 全选/取消全选
     */
    private void toggleSelectAll() {
        if (selectedPositions.size() == currentRecords.size()) {
            // 已全选，取消全选
            selectedPositions.clear();
        } else {
            // 全选
            selectedPositions.clear();
            for (int i = 0; i < currentRecords.size(); i++) {
                selectedPositions.add(i);
            }
        }
        displayRecords(currentRecords);
        updateDeleteButtonText();
    }

    /**
     * 确认删除选中的记录
     */
    private void confirmDelete() {
        if (selectedPositions.isEmpty()) {
            Toast.makeText(this, "请选择要删除的记录", Toast.LENGTH_SHORT).show();
            return;
        }

        new AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("确定要删除选中的 " + selectedPositions.size() + " 条运动记录吗？此操作不可恢复。")
                .setPositiveButton("删除", (dialog, which) -> performDelete())
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 执行删除操作（异步）
     */
    private void performDelete() {
        SharedPreferences userSp = getSharedPreferences("user", MODE_PRIVATE);
        String token = userSp.getString("token", "");
        boolean isLoggedIn = !token.isEmpty();

        List<WorkoutRecord> toDelete = new ArrayList<>();
        for (int pos : selectedPositions) {
            toDelete.add(currentRecords.get(pos));
        }

        Log.d(TAG, "========== 开始删除 ==========");
        Log.d(TAG, "登录状态: " + isLoggedIn);
        Log.d(TAG, "待删除记录数: " + toDelete.size());

        // 显示进度对话框
        ProgressDialog progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("正在删除...");
        progressDialog.setCancelable(false);
        progressDialog.show();

        // 在后台线程中执行删除
        new Thread(() -> {
            int successCount = 0;
            int failCount = 0;
            List<String> successNames = new ArrayList<>();
            List<String> failNames = new ArrayList<>();

            for (WorkoutRecord record : toDelete) {
                boolean deleted = false;
                String errorMsg = null;

                if (isLoggedIn && record.isSynced() && record.getReportId() > 0) {
                    // 登录模式：删除服务器端报告（使用异步方法）
                    try {
                        boolean result = workoutDataService.deleteServerReportSync(record.getReportId(), token);
                        deleted = result;
                        errorMsg = deleted ? "成功" : "删除失败";
                    } catch (Exception e) {
                        deleted = false;
                        errorMsg = e.getMessage();
                    }
                } else {
                    // 游客模式或未同步：删除本地文件
                    deleted = workoutDataService.deleteLocalReport(record);
                    errorMsg = deleted ? "成功" : "本地删除失败";
                }

                if (deleted) {
                    successCount++;
                    successNames.add(record.getActionName());
                } else {
                    failCount++;
                    failNames.add(record.getActionName() + "(" + (errorMsg != null ? errorMsg : "未知错误") + ")");
                }
            }

            final int finalSuccessCount = successCount;
            final int finalFailCount = failCount;
            final List<String> finalSuccessNames = successNames;
            final List<String> finalFailNames = failNames;

            // 回到UI线程更新界面
            runOnUiThread(() -> {
                progressDialog.dismiss();

                // 退出删除模式
                exitDeleteMode();

                // 刷新数据
                refreshData();

                // 显示结果
                if (finalSuccessCount > 0) {
                    String msg = "成功删除 " + finalSuccessCount + " 条记录";
                    if (!finalSuccessNames.isEmpty()) {
                        msg += " (" + TextUtils.join(", ", finalSuccessNames) + ")";
                    }
                    if (finalFailCount > 0) {
                        msg += "\n失败 " + finalFailCount + " 条: " + TextUtils.join(", ", finalFailNames);
                    }
                    Toast.makeText(RecordActivity.this, msg, Toast.LENGTH_LONG).show();
                } else if (finalFailCount > 0) {
                    Toast.makeText(RecordActivity.this, "删除失败: " + TextUtils.join(", ", finalFailNames), Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }
    private void showLocalReportDetail(WorkoutRecord record) {
        try {
            File reportFile = new File(record.getReportFilePath());
            if (!reportFile.exists()) {
                showReportDetail(record);
                return;
            }

            java.io.FileInputStream fis = new java.io.FileInputStream(reportFile);
            byte[] data = new byte[(int) reportFile.length()];
            fis.read(data);
            fis.close();
            String jsonContent = new String(data, "UTF-8");

            JSONObject json = new JSONObject(jsonContent);

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setTitle("运动报告");

            StringBuilder content = new StringBuilder();
            content.append("动作类型: ").append(record.getActionName()).append("\n");
            content.append("开始时间: ").append(record.getFormattedDateTime()).append("\n");
            content.append("运动时长: ").append(record.getFormattedDuration()).append("\n");
            if (record.getCount() > 0) {
                content.append("完成次数: ").append(record.getCount()).append("次\n");
            }
            content.append("动作相似度: ").append((int)(record.getAvgSimilarity() * 100)).append("%\n");

            if (json.has("isNonRealtime") && json.getBoolean("isNonRealtime")) {
                content.append("\n📹 视频比对报告\n");
                if (json.has("totalFrames")) {
                    content.append("分析帧数: ").append(json.getInt("totalFrames")).append("\n");
                }
                if (json.has("kneeSimilarity")) {
                    content.append(String.format("膝关节相似度: %.1f%%\n", json.getDouble("kneeSimilarity") * 100));
                }
                if (json.has("hipSimilarity")) {
                    content.append(String.format("髋关节相似度: %.1f%%\n", json.getDouble("hipSimilarity") * 100));
                }
            }

            builder.setMessage(content.toString());
            builder.setPositiveButton("确定", null);
            builder.show();

        } catch (Exception e) {
            Log.e("RecordActivity", "读取本地报告失败: " + e.getMessage());
            showReportDetail(record);
        }
    }

    private void showReportDetail(WorkoutRecord record) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("运动报告");

        StringBuilder content = new StringBuilder();
        content.append("动作类型: ").append(record.getActionName()).append("\n");
        content.append("开始时间: ").append(record.getFormattedDateTime()).append("\n");
        content.append("运动时长: ").append(record.getFormattedDuration()).append("\n");
        if (record.getCount() > 0) {
            content.append("完成次数: ").append(record.getCount()).append("次\n");
        }
        content.append("动作相似度: ").append((int)(record.getAvgSimilarity() * 100)).append("%\n");

        if (!record.isSynced()) {
            content.append("\n⚠ 未同步到服务器");
        }

        builder.setMessage(content.toString());
        builder.setPositiveButton("确定", null);
        builder.show();
    }

    private void showRecordList() {
        layoutEmptyState.setVisibility(View.GONE);
        layoutListHeader.setVisibility(View.VISIBLE);
        scrollViewRecords.setVisibility(View.VISIBLE);
    }

    private void showEmptyState() {
        layoutEmptyState.setVisibility(View.VISIBLE);
        layoutListHeader.setVisibility(View.GONE);
        scrollViewRecords.setVisibility(View.GONE);
    }

    private void setBottomNavListener() {
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            Intent intent = new Intent(this, Initial_activity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            finish();
        });

        findViewById(R.id.nav_history).setOnClickListener(v -> {
            Toast.makeText(this, "当前在记录页", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            Intent intent = new Intent(this, WodeActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            finish();
        });

        highlightCurrentNav();
    }

    private void highlightCurrentNav() {
        resetNavStyle();
        if (ivHistory != null) ivHistory.setColorFilter(COLOR_PRIMARY);
        if (tvHistory != null) tvHistory.setTextColor(COLOR_PRIMARY);
    }

    private void resetNavStyle() {
        if (ivHome != null) ivHome.setColorFilter(COLOR_DEFAULT);
        if (ivHistory != null) ivHistory.setColorFilter(COLOR_DEFAULT);
        if (ivProfile != null) ivProfile.setColorFilter(COLOR_DEFAULT);
        if (tvHome != null) tvHome.setTextColor(COLOR_DEFAULT);
        if (tvHistory != null) tvHistory.setTextColor(COLOR_DEFAULT);
        if (tvProfile != null) tvProfile.setTextColor(COLOR_DEFAULT);
    }

    private void setButtonListeners() {
        if (btnStartWorkout != null) {
            btnStartWorkout.setOnClickListener(v -> {
                Intent intent = new Intent(this, Initial_activity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
                finish();
            });
        }

        Button btnRefresh = findViewById(R.id.btn_refresh);
        if (btnRefresh != null) {
            btnRefresh.setOnClickListener(v -> {
                refreshData();
                Toast.makeText(this, "已刷新", Toast.LENGTH_SHORT).show();
            });
        }

        // 删除模式按钮
        if (btnDeleteMode != null) {
            btnDeleteMode.setOnClickListener(v -> {
                if (currentRecords.isEmpty()) {
                    Toast.makeText(this, "暂无记录可删除", Toast.LENGTH_SHORT).show();
                } else {
                    enterDeleteMode();
                }
            });
        }

        // 全选按钮
        Button btnSelectAll = findViewById(R.id.btn_select_all);
        if (btnSelectAll != null) {
            btnSelectAll.setOnClickListener(v -> toggleSelectAll());
        }

        // 确认删除按钮
        if (btnDeleteConfirm != null) {
            btnDeleteConfirm.setOnClickListener(v -> confirmDelete());
        }

        // 取消删除按钮
        if (btnDeleteCancel != null) {
            btnDeleteCancel.setOnClickListener(v -> exitDeleteMode());
        }
    }
}