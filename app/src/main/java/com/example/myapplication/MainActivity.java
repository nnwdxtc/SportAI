package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import com.example.poselandmarker.R;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_realtime);

        Toolbar toolbar = findViewById(R.id.back_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        initializeViews();
    }

    private void initializeViews() {
        // 实时分析按钮

        Button realtimeButton = findViewById(R.id.btn_realtime_compare);
        realtimeButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, RealtimeActivity.class);
            startActivity(intent);
        });

        // 非实时分析按钮
        Button nonRealtimeButton = findViewById(R.id.btn_non_realtime_compare);
        nonRealtimeButton.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, NonRealtimeActivity.class);
            startActivity(intent);
        });
    }
}