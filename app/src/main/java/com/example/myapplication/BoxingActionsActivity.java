package com.example.myapplication;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.example.poselandmarker.R;
import android.content.Intent;
import androidx.cardview.widget.CardView;

public class BoxingActionsActivity extends AppCompatActivity {

    private BoxingStrategy boxingStrategy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_boxing_actions);

        // 初始化策略对象
        boxingStrategy = new BoxingStrategy();

        // 初始化返回按钮点击事件
        LinearLayout btnBack = findViewById(R.id.btn_back);
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        // 1. 直拳卡片点击事件
        CardView cardJab = findViewById(R.id.card_jab);
        cardJab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpToDetail("直拳");
            }
        });

        // 2. 摆拳卡片点击事件
        CardView cardHook = findViewById(R.id.card_hook);
        cardHook.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpToDetail("摆拳");
            }
        });

        // 3. 勾拳卡片点击事件
        CardView cardUpper = findViewById(R.id.card_upper);
        cardUpper.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                jumpToDetail("勾拳");
            }
        });
    }


    private void jumpToDetail(String actionName) {
        Intent intent = new Intent(BoxingActionsActivity.this, activity_action_detail.class);
        // 传递策略对象
        intent.putExtra("action_strategy", boxingStrategy);
        // 传递具体动作名称
        intent.putExtra("specific_action_name", actionName);
        startActivity(intent);
    }
}