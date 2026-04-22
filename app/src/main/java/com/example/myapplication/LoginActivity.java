package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.example.poselandmarker.R;

import org.json.JSONObject;

import java.io.IOException;
import java.util.Random;
import okhttp3.*;

import javax.net.ssl.*;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class LoginActivity extends AppCompatActivity {

    private EditText etUsername, etPassword, etCaptcha;
    private ImageView ivEye, ivCaptcha;
    private Button btnLogin;
    private TextView tvRegister, tvGuest;
    private boolean isPasswordVisible = false;

    private String localCaptchaCode;
    private static final int CAPTCHA_WIDTH = 100;
    private static final int CAPTCHA_HEIGHT = 40;

    // 后端地址
    private static final String BASE_URL = "http://114.55.105.76:8080";
    private static final String LOGIN_URL = BASE_URL + "/api/auth/login";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        etUsername = findViewById(R.id.et_username);
        etPassword = findViewById(R.id.et_password);
        etCaptcha = findViewById(R.id.et_captcha);
        ivEye = findViewById(R.id.iv_eye);
        ivCaptcha = findViewById(R.id.iv_captcha);
        btnLogin = findViewById(R.id.btn_login);
        tvRegister = findViewById(R.id.tv_register);
        tvGuest = findViewById(R.id.tv_guest);

        generateLocalCaptcha();

        ivCaptcha.setOnClickListener(v -> generateLocalCaptcha());

        ivEye.setOnClickListener(v -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                etPassword.setInputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
                ivEye.setImageResource(R.drawable.ic_eye_open);
            } else {
                etPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
                ivEye.setImageResource(R.drawable.ic_eye_closed);
            }
            etPassword.setSelection(etPassword.getText().length());
        });

        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            String inputCaptcha = etCaptcha.getText().toString().trim().toUpperCase();

            if (username.isEmpty() || password.isEmpty() || inputCaptcha.isEmpty()) {
                Toast.makeText(this, "账号、密码或验证码不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!inputCaptcha.equals(localCaptchaCode)) {
                Toast.makeText(this, "验证码错误", Toast.LENGTH_SHORT).show();
                generateLocalCaptcha();
                return;
            }

            loginToServer(username, password);
        });

        tvRegister.setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, RegisterActivity.class);
            startActivity(intent);
        });

        tvGuest.setOnClickListener(v -> {
            Toast.makeText(this, "进入游客模式", Toast.LENGTH_SHORT).show();
            Intent intent = new Intent(LoginActivity.this, Initial_activity.class);
            startActivity(intent);
            finish();
        });
    }

    private void generateLocalCaptcha() {
        localCaptchaCode = generateRandomCaptcha(4);
        Bitmap captchaBitmap = createCaptchaBitmap(localCaptchaCode);
        ivCaptcha.setImageBitmap(captchaBitmap);
    }

    private String generateRandomCaptcha(int length) {
        String chars = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private Bitmap createCaptchaBitmap(String code) {
        Bitmap bitmap = Bitmap.createBitmap(CAPTCHA_WIDTH, CAPTCHA_HEIGHT, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Random random = new Random();

        canvas.drawColor(Color.parseColor("#E5E6EB"));

        Paint linePaint = new Paint();
        linePaint.setStrokeWidth(1);
        for (int i = 0; i < random.nextInt(3) + 2; i++) {
            linePaint.setColor(Color.rgb(random.nextInt(200), random.nextInt(200), random.nextInt(200)));
            int startX = random.nextInt(CAPTCHA_WIDTH);
            int startY = random.nextInt(CAPTCHA_HEIGHT);
            int endX = random.nextInt(CAPTCHA_WIDTH);
            int endY = random.nextInt(CAPTCHA_HEIGHT);
            canvas.drawLine(startX, startY, endX, endY, linePaint);
        }

        Paint textPaint = new Paint();
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(24);
        textPaint.setStrokeWidth(2);

        Rect textBounds = new Rect();
        textPaint.getTextBounds(code, 0, code.length(), textBounds);
        int textX = (CAPTCHA_WIDTH - textBounds.width()) / 2;
        int textY = (CAPTCHA_HEIGHT + textBounds.height()) / 2;

        for (int i = 0; i < code.length(); i++) {
            textPaint.setColor(Color.rgb(random.nextInt(100), random.nextInt(100), random.nextInt(100)));
            float offsetX = textX + i * 20 + random.nextInt(4) - 2;
            float offsetY = textY + random.nextInt(6) - 3;
            canvas.drawText(String.valueOf(code.charAt(i)), offsetX, offsetY, textPaint);
        }

        Paint pointPaint = new Paint();
        for (int i = 0; i < 50; i++) {
            pointPaint.setColor(Color.rgb(random.nextInt(150), random.nextInt(150), random.nextInt(150)));
            int x = random.nextInt(CAPTCHA_WIDTH);
            int y = random.nextInt(CAPTCHA_HEIGHT);
            canvas.drawPoint(x, y, pointPaint);
        }

        canvas.save();
        return bitmap;
    }

    /**
     * 忽略 HTTPS 自签名证书错误，允许正常连接后端
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
     * 使用支持 HTTPS 自签名的 OkHttp
     */
    private void loginToServer(String username, String password) {
        // 使用支持自签名证书的客户端
        OkHttpClient client = getUnsafeOkHttpClient();
        String json = "{\"account\":\"" + username + "\",\"password\":\"" + password + "\"}";
        RequestBody body = RequestBody.create(
                json,
                MediaType.parse("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(LOGIN_URL)  // 确保是 /api/auth/login
                .post(body)
                .header("Content-Type", "application/json")
                .build();


        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> Toast.makeText(LoginActivity.this,
                        "网络连接失败: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String result = response.body().string();
                runOnUiThread(() -> {
                    try {
                        JSONObject json = new JSONObject(result);
                        int code = json.optInt("code", -1);

                        if (code == 0) {
                            // 可以顺便保存 token
                            String token = json.getJSONObject("data").optString("token");
                            // 登录成功后添加
                            SharedPreferences sp = getSharedPreferences("user", MODE_PRIVATE);
                            sp.edit().putString("token", token).putString("account", username).apply();

                            Toast.makeText(LoginActivity.this, "登录成功", Toast.LENGTH_SHORT).show();
                            Intent intent = new Intent(LoginActivity.this, Initial_activity.class);
                            startActivity(intent);
                            finish();
                        } else {
                            String msg = json.optString("msg", "登录失败");
                            Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_SHORT).show();
                        }
                    } catch (Exception e) {
                        Toast.makeText(LoginActivity.this,
                                "解析错误", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
    }
}