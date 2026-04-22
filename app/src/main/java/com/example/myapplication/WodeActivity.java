package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

import java.io.IOException;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WodeActivity extends AppCompatActivity {

    private static final String TAG = "WodeActivity";
    private static final int COLOR_PRIMARY = 0xFF9933FF;
    private static final int COLOR_DEFAULT = 0xFF999999;

    // 底部导航栏控件
    private ImageView ivHome, ivHistory, ivProfile;
    private TextView tvHome, tvHistory, tvProfile;

    // 用户信息控件
    private TextView tvNickname, tvAccount, tvHeight, tvWeight;
    private TextView tvProfileName;   // 显示昵称（头像下方大字）
    private TextView tvProfileId;     // 显示账号ID（头像下方小字）
    private Button btnEdit, btnClear, btnAuthAction;

    // 运动统计控件（只显示总数据）
    private TextView tvTotalCount, tvTotalDuration;

    // 网络请求客户端（支持自签名证书）
    private final OkHttpClient client = getUnsafeOkHttpClient();

    // 服务器地址
    private static final String BASE_URL = "http://114.55.105.76:8080";
    private static final String USER_INFO_URL = BASE_URL + "/api/user/me";
    private static final String UPDATE_USER_URL = BASE_URL + "/api/user/update";
    private static final String DELETE_ACCOUNT_URL = BASE_URL + "/api/user/deleteAccount";
    private static final String LOGOUT_URL = BASE_URL + "/api/user/logout";

    // 当前用户数据缓存
    private String currentNickname = "";
    private String currentHeight = "";
    private String currentWeight = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.wode);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        setBottomNavListener();
        setButtonListeners();
        loadUserInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadUserInfo();

        // 根据登录状态加载运动统计
        SharedPreferences sp = getSharedPreferences("user", MODE_PRIVATE);
        String token = sp.getString("token", "");

        if (token.isEmpty()) {
            // 游客模式：从本地加载运动统计
            loadLocalTotalStats();
        } else {
            // 登录模式：从服务器加载运动统计
            loadTotalStats();
        }
    }

    /**
     * 初始化视图控件
     */
    private void initViews() {
        // 底部导航栏
        ivHome = findViewById(R.id.iv_home);
        ivHistory = findViewById(R.id.iv_history);
        ivProfile = findViewById(R.id.iv_profile);
        tvHome = findViewById(R.id.tv_home);
        tvHistory = findViewById(R.id.tv_history);
        tvProfile = findViewById(R.id.tv_profile);

        // 用户信息
        tvNickname = findViewById(R.id.tv_nickname);
        tvAccount = findViewById(R.id.tv_account);
        tvHeight = findViewById(R.id.tv_height);
        tvWeight = findViewById(R.id.tv_weight);
        tvProfileName = findViewById(R.id.tv_profile_name);
        tvProfileId = findViewById(R.id.tv_profile_id);

        // 按钮
        btnEdit = findViewById(R.id.btn_edit);
        btnClear = findViewById(R.id.btn_clear);
        btnAuthAction = findViewById(R.id.btn_auth_action);

        // 运动统计控件（只显示总数据）
        tvTotalCount = findViewById(R.id.tv_total_count);
        tvTotalDuration = findViewById(R.id.tv_total_duration);

        // 设置智能按钮点击事件
        btnAuthAction.setOnClickListener(v -> handleAuthAction());
    }

    /**
     * 智能处理登录/退出按钮点击
     */
    private void handleAuthAction() {
        if (isLoggedIn()) {
            showLogoutConfirm();
        } else {
            startActivity(new Intent(WodeActivity.this, LoginActivity.class));
        }
    }

    /**
     * 判断是否已登录
     */
    private boolean isLoggedIn() {
        SharedPreferences sp = getSharedPreferences("user", MODE_PRIVATE);
        String token = sp.getString("token", "");
        return !token.isEmpty();
    }

    /**
     * 显示退出登录确认对话框
     */
    private void showLogoutConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("退出登录")
                .setMessage("确定要退出登录吗？")
                .setPositiveButton("确定", (dialog, which) -> {
                    logout();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 执行退出登录
     */
    private void logout() {
        SharedPreferences sp = getSharedPreferences("user", MODE_PRIVATE);
        String token = sp.getString("token", "");

        if (!token.isEmpty()) {
            // 调用后端登出接口
            Request request = new Request.Builder()
                    .url(LOGOUT_URL)
                    .addHeader("Authorization", token)
                    .post(RequestBody.create("", MediaType.parse("application/json")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "登出请求失败: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    response.close();
                }
            });
        }

        // 清除本地登录信息
        sp.edit().clear().apply();

        // 更新UI为游客状态
        updateUIForGuest();
        Toast.makeText(this, "已退出登录", Toast.LENGTH_SHORT).show();
    }

    /**
     * 更新UI为游客状态
     */
    private void updateUIForGuest() {
        showGuestInfo();
        updateAuthButton();


    }

    /**
     * 更新按钮文字和样式
     */
    private void updateAuthButton() {
        if (isLoggedIn()) {
            btnAuthAction.setText("退出登录");
            btnAuthAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFFFE5E5));
            btnAuthAction.setTextColor(0xFFFF6B6B);
        } else {
            btnAuthAction.setText("登录");
            btnAuthAction.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFFE5E5FF));
            btnAuthAction.setTextColor(0xFF9933FF);
        }
    }

    /**
     * 忽略 HTTPS 自签名证书错误
     */
    private OkHttpClient getUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };

            final SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new SecureRandom());
            final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

            OkHttpClient.Builder builder = new OkHttpClient.Builder();
            builder.sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0]);
            builder.hostnameVerifier((hostname, session) -> true);

            return builder.build();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 加载用户信息 - 同时更新头像卡片和基本信息区域
     */
    private void loadUserInfo() {
        SharedPreferences sp = getSharedPreferences("user", MODE_PRIVATE);
        String token = sp.getString("token", "");

        Log.d(TAG, "Token长度: " + token.length());

        updateAuthButton();

        if (token.isEmpty()) {
            showGuestInfo();
            return;
        }

        Request request = new Request.Builder()
                .url(USER_INFO_URL)
                .addHeader("Authorization", token)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "网络请求失败: " + e.getMessage());
                runOnUiThread(() -> {
                    Toast.makeText(WodeActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
                    showGuestInfo();
                    updateAuthButton();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String result = response.body().string();
                int httpCode = response.code();

                Log.d(TAG, "HTTP状态码: " + httpCode + ", 返回: " + result);

                runOnUiThread(() -> {
                    if (httpCode != 200) {
                        Toast.makeText(WodeActivity.this, "获取信息失败(" + httpCode + ")", Toast.LENGTH_SHORT).show();
                        showGuestInfo();
                        updateAuthButton();
                        return;
                    }

                    try {
                        JSONObject json = new JSONObject(result);
                        int code = json.optInt("code", -1);

                        if (code == 0) {
                            JSONObject data = json.optJSONObject("data");

                            if (data == null) {
                                Log.e(TAG, "data 为 null");
                                showGuestInfo();
                                updateAuthButton();
                                return;
                            }

                            // 解析用户信息
                            currentNickname = getSafeString(data, "nickname", "");

                            // 尝试驼峰命名（后端 UserEntity 应该是这个）
                            currentHeight = getSafeString(data, "heightCm", "");
                            currentWeight = getSafeString(data, "weightKg", "");

                            // 如果驼峰为空，再尝试下划线（兼容旧数据）
                            if (currentHeight.isEmpty()) {
                                currentHeight = getSafeString(data, "height_cm", "");
                            }
                            if (currentWeight.isEmpty()) {
                                currentWeight = getSafeString(data, "weight_kg", "");
                            }

                            String account = getSafeString(data, "account", "无");
                            String nickname = currentNickname.isEmpty() ? "游客" : currentNickname;
                            String height = currentHeight.isEmpty() ? "未知" : currentHeight;
                            String weight = currentWeight.isEmpty() ? "未知" : currentWeight;

                            Log.d(TAG, "解析成功: account=" + account
                                    + ", nickname=" + nickname
                                    + ", heightCm=" + currentHeight
                                    + ", weightKg=" + currentWeight);

                            // 更新基本信息区域
                            tvAccount.setText(account);
                            tvNickname.setText(nickname);
                            tvHeight.setText(height.equals("未知") ? "未知" : height + " cm");
                            tvWeight.setText(weight.equals("未知") ? "未知" : weight + " kg");

                            // 更新头像卡片区域 - 显示昵称和账号ID
                            tvProfileName.setText(nickname);
                            tvProfileId.setText("ID：" + account);

                            updateAuthButton();

                        } else {
                            String msg = json.optString("msg", "未知错误");
                            Toast.makeText(WodeActivity.this, msg, Toast.LENGTH_SHORT).show();
                            showGuestInfo();
                            updateAuthButton();
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析异常: " + e.getMessage());
                        Toast.makeText(WodeActivity.this, "数据解析错误", Toast.LENGTH_SHORT).show();
                        showGuestInfo();
                        updateAuthButton();
                    }
                });
            }
        });
    }

    /**
     * 安全获取JSON字符串值
     */
    private String getSafeString(JSONObject data, String key, String defaultValue) {
        if (data == null) return defaultValue;
        if (data.isNull(key)) return defaultValue;

        String value = data.optString(key, defaultValue);
        if (value == null || value.isEmpty() || value.equals("null") || value.equals("NULL")) {
            return defaultValue;
        }
        return value;
    }

    /**
     * 显示游客状态 - 同时清空头像卡片
     */
    private void showGuestInfo() {
        // 基本信息区域
        tvAccount.setText("无");
        tvNickname.setText("游客");
        tvHeight.setText("未知");
        tvWeight.setText("未知");

        // 头像卡片区域也显示游客状态
        tvProfileName.setText("游客");
        tvProfileId.setText("请登录");

        // 清空缓存
        currentNickname = "";
        currentHeight = "";
        currentWeight = "";
    }

    /**
     * 加载总运动统计（只加载总次数和总时长）
     */
    private void loadTotalStats() {
        SharedPreferences sp = getSharedPreferences("user", MODE_PRIVATE);
        String token = sp.getString("token", "");

        if (token.isEmpty()) {
            // 游客模式：显示 0
            updateTotalStatsUI(0, 0);
            return;
        }

        // 登录用户：从服务器获取总统计
        Request request = new Request.Builder()
                .url(USER_INFO_URL)
                .addHeader("Authorization", token)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "获取用户统计失败: " + e.getMessage());
                runOnUiThread(() -> updateTotalStatsUI(0, 0));
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String result = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(result);
                        if (json.optInt("code") == 0) {
                            JSONObject data = json.optJSONObject("data");
                            if (data != null) {
                                int totalCount = data.optInt("totalExerciseCount", 0);
                                int totalDuration = data.optInt("totalExerciseDurationMs", 0);
                                updateTotalStatsUI(totalCount, totalDuration);
                            } else {
                                updateTotalStatsUI(0, 0);
                            }
                        } else {
                            updateTotalStatsUI(0, 0);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析失败: " + e.getMessage());
                        updateTotalStatsUI(0, 0);
                    }
                });
            }
        });
    }

    /**
     * 从本地加载总运动统计（游客模式或网络失败时使用）
     */
    private void loadLocalTotalStats() {
        WorkoutDataService service = new WorkoutDataService(this);
        int[] totalStats = service.getLocalTotalStats();
        updateTotalStatsUI(totalStats[0], totalStats[1]);
    }

    /**
     * 更新本地缓存的总运动统计
     */
    private void updateLocalTotalStats(int totalCount, int totalDurationMs) {
        SharedPreferences sp = getSharedPreferences("workout_local", MODE_PRIVATE);
        sp.edit()
                .putInt("total_count", totalCount)
                .putInt("total_duration_ms", totalDurationMs)
                .apply();
        Log.d(TAG, "更新本地缓存: 总次数=" + totalCount + ", 总时长=" + totalDurationMs);
    }

    /**
     * 更新总运动统计UI
     */
    private void updateTotalStatsUI(int totalCount, int totalDurationMs) {
        int totalDurationSec = totalDurationMs / 1000;

        if (tvTotalCount != null) {
            tvTotalCount.setText(String.valueOf(totalCount));
        }
        if (tvTotalDuration != null) {
            tvTotalDuration.setText(totalDurationSec + "秒");
        }

        Log.d(TAG, "更新UI: 总次数=" + totalCount + ", 总时长=" + totalDurationSec + "秒");
    }

    /**
     * 设置底部导航栏监听
     */
    private void setBottomNavListener() {
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            startActivity(new Intent(this, Initial_activity.class));
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            finish();
        });

        findViewById(R.id.nav_history).setOnClickListener(v -> {
            startActivity(new Intent(this, RecordActivity.class));
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
            finish();
        });

        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            Toast.makeText(this, "当前在个人中心", Toast.LENGTH_SHORT).show();
        });

        highlightCurrentNav();
    }

    /**
     * 高亮当前选中的导航项
     */
    private void highlightCurrentNav() {
        resetNavStyle();
        if (ivProfile != null) ivProfile.setColorFilter(COLOR_PRIMARY);
        if (tvProfile != null) tvProfile.setTextColor(COLOR_PRIMARY);
    }

    /**
     * 重置导航栏样式
     */
    private void resetNavStyle() {
        if (ivHome != null) ivHome.setColorFilter(COLOR_DEFAULT);
        if (ivHistory != null) ivHistory.setColorFilter(COLOR_DEFAULT);
        if (ivProfile != null) ivProfile.setColorFilter(COLOR_DEFAULT);
        if (tvHome != null) tvHome.setTextColor(COLOR_DEFAULT);
        if (tvHistory != null) tvHistory.setTextColor(COLOR_DEFAULT);
        if (tvProfile != null) tvProfile.setTextColor(COLOR_DEFAULT);
    }

    /**
     * 设置按钮监听器
     */
    private void setButtonListeners() {
        // 编辑信息按钮
        btnEdit.setOnClickListener(v -> {
            if (!isLoggedIn()) {
                new AlertDialog.Builder(this)
                        .setTitle("未登录")
                        .setMessage("请先登录后再编辑信息")
                        .setPositiveButton("去登录", (dialog, which) -> {
                            startActivity(new Intent(WodeActivity.this, LoginActivity.class));
                        })
                        .setNegativeButton("取消", null)
                        .show();
                return;
            }
            showEditDialog();
        });

        // 注销账号按钮（永久删除账号）
        btnClear.setOnClickListener(v -> {
            if (!isLoggedIn()) {
                Toast.makeText(this, "请先登录", Toast.LENGTH_SHORT).show();
                return;
            }
            showDeleteAccountConfirm();
        });
    }

    /**
     * 显示注销账号确认对话框（二次确认）
     */
    private void showDeleteAccountConfirm() {
        new AlertDialog.Builder(this)
                .setTitle("注销账号")
                .setMessage("⚠️ 警告：注销账号后，您的所有数据将被永久删除，无法恢复！\n\n确定要注销账号吗？")
                .setPositiveButton("确定注销", (dialog, which) -> {
                    // 二次确认
                    new AlertDialog.Builder(this)
                            .setTitle("最终确认")
                            .setMessage("请再次确认，此操作不可撤销！")
                            .setPositiveButton("确认注销", (dialog2, which2) -> {
                                showPasswordInputDialog();
                            })
                            .setNegativeButton("取消", null)
                            .show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 显示密码输入对话框（后端需要密码验证）
     */
    private void showPasswordInputDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        TextView tvLabel = new TextView(this);
        tvLabel.setText("请输入密码以确认注销");
        tvLabel.setTextSize(14);
        layout.addView(tvLabel);

        EditText etPassword = new EditText(this);
        etPassword.setHint("密码");
        etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPassword);

        new AlertDialog.Builder(this)
                .setTitle("验证密码")
                .setView(layout)
                .setPositiveButton("确认注销", (dialog, which) -> {
                    String password = etPassword.getText().toString().trim();
                    if (password.isEmpty()) {
                        Toast.makeText(this, "密码不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    deleteAccount(password);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 调用后端接口注销账号（需要密码验证）
     */
    private void deleteAccount(String password) {
        SharedPreferences sp = getSharedPreferences("user", MODE_PRIVATE);
        String token = sp.getString("token", "");

        if (token.isEmpty()) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this, "正在注销...", Toast.LENGTH_SHORT).show();

        try {
            JSONObject json = new JSONObject();
            json.put("password", password);

            String jsonStr = json.toString();
            Log.d(TAG, "发送注销请求: " + jsonStr);

            RequestBody body = RequestBody.create(jsonStr, MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(DELETE_ACCOUNT_URL)
                    .addHeader("Authorization", token)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "注销失败: " + e.getMessage());
                        Toast.makeText(WodeActivity.this, "网络错误，请重试", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String result = response.body().string();
                    int httpCode = response.code();

                    Log.d(TAG, "注销返回: HTTP=" + httpCode + ", body=" + result);

                    runOnUiThread(() -> {
                        if (httpCode == 200) {
                            try {
                                JSONObject json = new JSONObject(result);
                                int code = json.optInt("code", -1);

                                if (code == 0) {
                                    // 注销成功，清除本地数据
                                    sp.edit().clear().apply();

                                    // 清空本地运动记录
                                    WorkoutDataService service = new WorkoutDataService(WodeActivity.this);
                                    service.clearLocalRecords();

                                    Toast.makeText(WodeActivity.this, "账号已注销", Toast.LENGTH_LONG).show();

                                    // 跳转到登录页，并清除任务栈
                                    Intent intent = new Intent(WodeActivity.this, LoginActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    String msg = json.optString("msg", "注销失败");
                                    Toast.makeText(WodeActivity.this, msg, Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "解析失败", e);
                                if (result.contains("\"code\":0")) {
                                    sp.edit().clear().apply();
                                    WorkoutDataService service = new WorkoutDataService(WodeActivity.this);
                                    service.clearLocalRecords();
                                    Toast.makeText(WodeActivity.this, "账号已注销", Toast.LENGTH_LONG).show();
                                    Intent intent = new Intent(WodeActivity.this, LoginActivity.class);
                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                                    startActivity(intent);
                                    finish();
                                } else {
                                    Toast.makeText(WodeActivity.this, "注销失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else if (httpCode == 401) {
                            Toast.makeText(WodeActivity.this, "密码错误", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(WodeActivity.this, "注销失败(" + httpCode + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "构建请求失败: " + e.getMessage());
            Toast.makeText(this, "请求错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * 显示编辑信息对话框
     */
    private void showEditDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 30, 50, 30);

        // 昵称输入
        TextView tvNicknameLabel = new TextView(this);
        tvNicknameLabel.setText("昵称");
        layout.addView(tvNicknameLabel);

        EditText etNickname = new EditText(this);
        etNickname.setText(currentNickname);
        etNickname.setHint("请输入昵称");
        layout.addView(etNickname);

        // 身高输入
        TextView tvHeightLabel = new TextView(this);
        tvHeightLabel.setText("身高 (cm)");
        tvHeightLabel.setPadding(0, 20, 0, 0);
        layout.addView(tvHeightLabel);

        EditText etHeight = new EditText(this);
        etHeight.setText(currentHeight);
        etHeight.setHint("请输入身高");
        etHeight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etHeight);

        // 体重输入
        TextView tvWeightLabel = new TextView(this);
        tvWeightLabel.setText("体重 (kg)");
        tvWeightLabel.setPadding(0, 20, 0, 0);
        layout.addView(tvWeightLabel);

        EditText etWeight = new EditText(this);
        etWeight.setText(currentWeight);
        etWeight.setHint("请输入体重");
        etWeight.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(etWeight);

        new AlertDialog.Builder(this)
                .setTitle("编辑信息")
                .setView(layout)
                .setPositiveButton("保存", (dialog, which) -> {
                    String newNickname = etNickname.getText().toString().trim();
                    String newHeight = etHeight.getText().toString().trim();
                    String newWeight = etWeight.getText().toString().trim();

                    if (newNickname.isEmpty()) {
                        Toast.makeText(this, "昵称不能为空", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    updateUserInfo(newNickname, newHeight, newWeight);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /**
     * 调用后端接口更新用户信息
     */
    private void updateUserInfo(String nickname, String height, String weight) {
        SharedPreferences sp = getSharedPreferences("user", MODE_PRIVATE);
        String token = sp.getString("token", "");

        if (token.isEmpty()) {
            Toast.makeText(this, "未登录", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            JSONObject json = new JSONObject();
            json.put("nickname", nickname);

            if (!height.isEmpty()) {
                json.put("heightCm", new BigDecimal(height));
            } else {
                json.put("heightCm", JSONObject.NULL);
            }

            if (!weight.isEmpty()) {
                json.put("weightKg", new BigDecimal(weight));
            } else {
                json.put("weightKg", JSONObject.NULL);
            }

            String jsonStr = json.toString();
            Log.d(TAG, "发送更新: " + jsonStr);

            RequestBody body = RequestBody.create(jsonStr, MediaType.parse("application/json; charset=utf-8"));

            Request request = new Request.Builder()
                    .url(UPDATE_USER_URL)
                    .addHeader("Authorization", token)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            Toast.makeText(this, "保存中...", Toast.LENGTH_SHORT).show();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> {
                        Log.e(TAG, "更新失败: " + e.getMessage());
                        Toast.makeText(WodeActivity.this, "网络错误", Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String result = response.body().string();
                    int httpCode = response.code();

                    Log.d(TAG, "更新返回: HTTP=" + httpCode + ", body=" + result);

                    runOnUiThread(() -> {
                        if (httpCode == 200) {
                            try {
                                JSONObject json = new JSONObject(result);
                                int code = json.optInt("code", -1);

                                if (code == 0) {
                                    Toast.makeText(WodeActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                                    new android.os.Handler().postDelayed(() -> {
                                        loadUserInfo();
                                    }, 500);
                                } else {
                                    String msg = json.optString("msg", "保存失败");
                                    Toast.makeText(WodeActivity.this, "失败: " + msg, Toast.LENGTH_LONG).show();
                                }
                            } catch (Exception e) {
                                Log.e(TAG, "解析失败", e);
                                if (result.contains("\"code\":0")) {
                                    Toast.makeText(WodeActivity.this, "保存成功", Toast.LENGTH_SHORT).show();
                                    new android.os.Handler().postDelayed(() -> loadUserInfo(), 500);
                                } else {
                                    Toast.makeText(WodeActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
                                }
                            }
                        } else {
                            Toast.makeText(WodeActivity.this, "保存失败(" + httpCode + ")", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "构建请求失败: " + e.getMessage());
            Toast.makeText(this, "请求错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}