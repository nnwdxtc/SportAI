package com.example.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.poselandmarker.R;

public class appcoveer extends AppCompatActivity {
    private Handler handler;
    private Runnable delayRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_appcoveer);

        // 初始化Handler和延迟任务
        handler = new Handler(Looper.getMainLooper());

        // 根据登录状态决定跳转目标
        delayRunnable = () -> jumpToNextActivity();

        // 启动3秒延迟跳转
        handler.postDelayed(delayRunnable, 3000);

        // 跳过按钮点击事件
        Button btnSkip = findViewById(R.id.btn_skip);
        btnSkip.setOnClickListener(v -> {
            handler.removeCallbacks(delayRunnable);
            jumpToNextActivity();
        });
    }

    /**
     * 判断登录状态并跳转到对应页面
     */
    private void jumpToNextActivity() {
        if (isLoggedIn()) {
            // 已登录，跳转到首页
            Intent intent = new Intent(this, Initial_activity.class);
            startActivity(intent);
            Toast.makeText(this, "欢迎回来", Toast.LENGTH_SHORT).show();
        } else {
            // 未登录，跳转到登录页
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
        }
        finish(); // 关闭启动页
    }

    /**
     * 判断是否已登录（检查Token是否存在）
     */
    private boolean isLoggedIn() {
        SharedPreferences sp = getSharedPreferences("user", MODE_PRIVATE);
        String token = sp.getString("token", "");
        return !token.isEmpty();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (handler != null) {
            handler.removeCallbacks(delayRunnable);
        }
    }
}