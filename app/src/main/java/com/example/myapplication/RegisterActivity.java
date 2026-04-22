package com.example.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.poselandmarker.R;

import org.json.JSONObject;

import java.io.IOException;
import java.security.SecureRandom;
import javax.net.ssl.*;
import java.security.cert.X509Certificate;
import java.util.regex.Pattern;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText etPhone;
    private EditText etPassword, etConfirmPassword, etNickname, etHeight, etWeight;
    private ImageView ivEye, ivEyeConfirm;
    private Button btnRegister;
    private TextView tvBackToLogin;

    private boolean isPasswordVisible = false;
    private boolean isConfirmPasswordVisible = false;

    private static final String BASE_URL = "http://114.55.105.76:8080";
    private static final String REGISTER_URL = BASE_URL + "/api/auth/register";
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    // 手机号正则表达式（中国大陆手机号）
    private static final Pattern PHONE_PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        initViews();
        setupListeners();
    }

    private void initViews() {
        etPhone = findViewById(R.id.et_phone);
        etPassword = findViewById(R.id.et_password);
        etConfirmPassword = findViewById(R.id.et_confirm_password);
        etNickname = findViewById(R.id.et_nickname);
        etHeight = findViewById(R.id.et_height);
        etWeight = findViewById(R.id.et_weight);

        ivEye = findViewById(R.id.iv_eye);
        ivEyeConfirm = findViewById(R.id.iv_eye_confirm);
        btnRegister = findViewById(R.id.btn_register);
        tvBackToLogin = findViewById(R.id.tv_back_to_login);
    }

    private void setupListeners() {
        ivEye.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                etPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ivEye.setImageResource(R.drawable.ic_eye_open);
            } else {
                etPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ivEye.setImageResource(R.drawable.ic_eye_closed);
            }
            etPassword.setSelection(etPassword.length());
        });

        ivEyeConfirm.setOnClickListener(v -> {
            isConfirmPasswordVisible = !isConfirmPasswordVisible;
            if (isConfirmPasswordVisible) {
                etConfirmPassword.setInputType(android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ivEyeConfirm.setImageResource(R.drawable.ic_eye_open);
            } else {
                etConfirmPassword.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ivEyeConfirm.setImageResource(R.drawable.ic_eye_closed);
            }
            etConfirmPassword.setSelection(etConfirmPassword.length());
        });

        tvBackToLogin.setOnClickListener(v -> finish());
        btnRegister.setOnClickListener(v -> attemptRegister());
    }


    private boolean isValidPhone(String phone) {
        return PHONE_PATTERN.matcher(phone).matches();
    }

    private void attemptRegister() {

        String phone = etPhone.getText().toString().trim();
        String password = etPassword.getText().toString().trim();
        String confirmPwd = etConfirmPassword.getText().toString().trim();
        String nickname = etNickname.getText().toString().trim();
        String height = etHeight.getText().toString().trim();
        String weight = etWeight.getText().toString().trim();


        if (phone.isEmpty()) {
            Toast.makeText(this, "请输入手机号", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidPhone(phone)) {
            Toast.makeText(this, "请输入正确的11位手机号", Toast.LENGTH_SHORT).show();
            return;
        }
        if (password.isEmpty() || nickname.isEmpty()) {
            Toast.makeText(this, "密码、昵称不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!password.equals(confirmPwd)) {
            Toast.makeText(this, "两次密码不一致", Toast.LENGTH_SHORT).show();
            return;
        }

        // 使用手机号作为账号
        Log.d("RegisterActivity", "Register with phone: " + phone);

        registerToServer(phone, password, nickname, height, weight);
    }

    /**
     * 使用支持 HTTPS 自签名证书的客户端
     */
    private OkHttpClient getUnsafeOkHttpClient() {
        try {
            final TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}
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

    private void registerToServer(String account, String password, String nickname, String height, String weight) {
        try {
            // 构建 JSON 对象，字段名必须与后端 RegisterDTO 一致
            JSONObject json = new JSONObject();
            json.put("account", account);  // 手机号作为账号
            json.put("password", password);
            json.put("nickname", nickname);

            // 数值字段：如果为空就不传，或者传 null；有值时转为数值类型
            if (!height.isEmpty()) {
                json.put("heightCm", Double.parseDouble(height));
            }
            if (!weight.isEmpty()) {
                json.put("weightKg", Double.parseDouble(weight));
            }

            RequestBody body = RequestBody.create(json.toString(), JSON);

            Request request = new Request.Builder()
                    .url(REGISTER_URL)
                    .post(body)
                    .header("Content-Type", "application/json")
                    .build();

            // 使用支持自签名证书的客户端
            OkHttpClient client = getUnsafeOkHttpClient();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    runOnUiThread(() -> Toast.makeText(RegisterActivity.this,
                            "网络失败：" + e.getMessage(), Toast.LENGTH_SHORT).show());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String result = response.body().string();
                    int httpCode = response.code();

                    runOnUiThread(() -> {
                        if (httpCode != 200) {
                            Toast.makeText(RegisterActivity.this,
                                    "服务器错误(" + httpCode + ")", Toast.LENGTH_LONG).show();
                            return;
                        }

                        try {
                            JSONObject respJson = new JSONObject(result);
                            int apiCode = respJson.optInt("code", -1);
                            String msg = respJson.optString("msg", "");

                            // 后端成功 code = 0
                            if (apiCode == 0) {

                                Toast.makeText(RegisterActivity.this,
                                        "注册成功！", Toast.LENGTH_LONG).show();
                                finish();
                            } else {
                                // 处理各种错误情况
                                if (msg.contains("账号已存在") || msg.contains("已存在") || msg.contains("手机号已注册")) {
                                    Toast.makeText(RegisterActivity.this,
                                            "该手机号已注册，请直接登录", Toast.LENGTH_LONG).show();
                                } else {
                                    Toast.makeText(RegisterActivity.this,
                                            "注册失败：" + msg, Toast.LENGTH_LONG).show();
                                }
                            }

                        } catch (Exception e) {
                            // 降级判断
                            if (result.contains("\"code\":0")) {
                                Toast.makeText(RegisterActivity.this,
                                        "注册成功！", Toast.LENGTH_LONG).show();
                                finish();
                            } else {
                                Toast.makeText(RegisterActivity.this,
                                        "注册失败：" + result, Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            });

        } catch (Exception e) {
            Toast.makeText(this, "请求构建失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}