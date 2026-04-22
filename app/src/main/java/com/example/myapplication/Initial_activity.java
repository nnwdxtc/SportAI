package com.example.myapplication;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.poselandmarker.R;

import android.view.*;
import io.noties.markwon.Markwon;

public class Initial_activity extends AppCompatActivity {

    private ImageView ivHome, ivHistory, ivProfile;
    private TextView tvHome, tvHistory, tvProfile;
    private EditText etSearch;
    private FrameLayout btnSquat;
    private FrameLayout btnBoxing;
    private FrameLayout btnRomanianDeadlift;
    private FrameLayout btnHighKnees;
    private View rootView;

    //  AI 助手相关
    private FrameLayout aiFabContainer;
    private Dialog aiDialog;
    private DoubaoApiClient doubaoApiClient;
    private LinearLayout chatContainer;
    private ScrollView scrollChat;
    private EditText etAiInput;
    private ImageButton btnSend;
    private ProgressBar progressLoading;
    private Markwon markwon;  // 添加成员变量

    // 拖拽相关变量
    private float dX, dY;  // 触摸点与视图左上角的偏移量
    private int screenWidth, screenHeight;
    private static final int MARGIN_BOTTOM = 100;  // 距离底部距离（dp）
    private static final int MARGIN_RIGHT = 16;    // 距离右边距离（dp）

    // 统一颜色
    private static final int COLOR_PRIMARY = 0xFF9933FF;
    private static final int COLOR_DEFAULT = 0xFF999999;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_initial);

        doubaoApiClient = new DoubaoApiClient();
        markwon = Markwon.create(this);

        rootView = findViewById(R.id.main); // 获取根布局

        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        initViews();
        initAiAssistant();
        setBottomNavListener();
        setButtonListeners();
        setSearchFunction();
        setCancelHighlightListener(); // 添加点击空白处取消高亮
    }

    private void initAiAssistant() {
        createAiFab();
        createAiDialog();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从其他页面返回时恢复按钮状态
        resetButtonStyle();
        // 清空搜索框
        etSearch.setText("");
    }

    private void initViews() {
        ivHome = findViewById(R.id.iv_home);
        ivHistory = findViewById(R.id.iv_history);
        ivProfile = findViewById(R.id.iv_profile);
        tvHome = findViewById(R.id.tv_home);
        tvHistory = findViewById(R.id.tv_history);
        tvProfile = findViewById(R.id.tv_profile);
        etSearch = findViewById(R.id.et_search);
        btnSquat = findViewById(R.id.btn_squat);
        btnBoxing = findViewById(R.id.btn_boxing);
        btnRomanianDeadlift = findViewById(R.id.btn_romanian_deadlift);
        btnHighKnees = findViewById(R.id.btn_high_knees);
    }

    /**
     * 设置点击空白区域取消高亮
     */
    private void setCancelHighlightListener() {
        // 点击根布局取消高亮（排除按钮和搜索框区域）
        rootView.setOnClickListener(v -> {
            resetButtonStyle();
            etSearch.clearFocus(); // 清除搜索框焦点
        });

        // 点击按钮时不触发根布局的点击事件
        View.OnClickListener buttonClickListener = v -> {
            // 阻止事件传递到根布局
        };
        btnSquat.setOnClickListener(buttonClickListener);
        btnBoxing.setOnClickListener(buttonClickListener);
        btnRomanianDeadlift.setOnClickListener(buttonClickListener);
        btnHighKnees.setOnClickListener(buttonClickListener);


        // 重新设置按钮的实际点击逻辑
        btnSquat.post(() -> {
            btnSquat.setOnClickListener(v -> {
                Intent i = new Intent(this, activity_action_detail.class);
                i.putExtra("action_strategy", new SquatStrategy());
                startActivity(i);
            });
        });

        btnBoxing.post(() -> {
            btnBoxing.setOnClickListener(v -> {
                Intent i = new Intent(this, BoxingActionsActivity.class);
                i.putExtra("action_strategy", new BoxingStrategy());
                startActivity(i);
            });
        });
        btnRomanianDeadlift.post(() -> {
            btnRomanianDeadlift.setOnClickListener(v -> {
                Intent i = new Intent(this, activity_action_detail.class);
                i.putExtra("action_strategy", new RomanianDeadliftStrategy());
                startActivity(i);
            });
        });


        btnHighKnees.post(() -> {
            btnHighKnees.setOnClickListener(v -> {
                Intent i = new Intent(this, activity_action_detail.class);
                i.putExtra("action_strategy", new HighKneesStrategy());
                startActivity(i);
            });
        });
    }

    /**
     * 底部导航栏
     */
    private void setBottomNavListener() {
        // 首页（当前页）
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            Toast.makeText(this, "当前在首页", Toast.LENGTH_SHORT).show();
        });

        // 记录页 - 从右往左动画
        findViewById(R.id.nav_history).setOnClickListener(v -> {
            resetButtonStyle(); // 跳转前重置状态
            Intent intent = new Intent(this, RecordActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            finish();
        });

        // 我的页 - 从右往左动画
        findViewById(R.id.nav_profile).setOnClickListener(v -> {
            resetButtonStyle(); // 跳转前重置状态
            Intent intent = new Intent(this, WodeActivity.class);
            startActivity(intent);
            overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
            finish();
        });
        btnRomanianDeadlift.setOnClickListener(v -> {
            Intent i = new Intent(this, activity_action_detail.class);
            i.putExtra("action_strategy", new RomanianDeadliftStrategy());
            startActivity(i);
        });


        btnHighKnees.setOnClickListener(v -> {
            Intent i = new Intent(this, activity_action_detail.class);
            i.putExtra("action_strategy", new HighKneesStrategy());
            startActivity(i);
        });
    }

    private void setButtonListeners() {
        btnSquat.setOnClickListener(v -> {
            Intent i = new Intent(this, activity_action_detail.class);
            i.putExtra("action_strategy", new SquatStrategy());
            startActivity(i);
        });

        btnBoxing.setOnClickListener(v -> {
            Intent i = new Intent(this, BoxingActionsActivity.class);
            i.putExtra("action_strategy", new BoxingStrategy());
            startActivity(i);
        });
        btnRomanianDeadlift.setOnClickListener(v -> {
            Intent i = new Intent(this, BoxingActionsActivity.class);
            i.putExtra("action_strategy", new BoxingStrategy());
            startActivity(i);
        });
        btnHighKnees.setOnClickListener(v -> {
            Intent i = new Intent(this, BoxingActionsActivity.class);
            i.putExtra("action_strategy", new BoxingStrategy());
            startActivity(i);
        });
    }

    private void setSearchFunction() {
        etSearch.setOnEditorActionListener((v, actionId, event) -> {
            String search = etSearch.getText().toString().trim();
            resetButtonStyle();

            if (search.isEmpty()) {
                Toast.makeText(this, "请输入搜索内容", Toast.LENGTH_SHORT).show();
            } else if (search.contains("深蹲") || search.equalsIgnoreCase("squat")) {
                highlightView(btnSquat);
                Toast.makeText(this, "找到：深蹲", Toast.LENGTH_SHORT).show();
            } else if (search.contains("拳击") || search.equalsIgnoreCase("boxing")) {
                highlightView(btnBoxing);
                Toast.makeText(this, "找到：拳击", Toast.LENGTH_SHORT).show();
            } else if (search.contains("罗马尼亚硬拉") || search.contains("硬拉") || search.equalsIgnoreCase("romanian deadlift")) {
                highlightView(btnRomanianDeadlift);
                Toast.makeText(this, "找到：罗马尼亚硬拉", Toast.LENGTH_SHORT).show();
            } else if (search.contains("高抬腿") || search.equalsIgnoreCase("high knees")) {
                highlightView(btnHighKnees);
                Toast.makeText(this, "找到：高抬腿", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "未找到相关运动", Toast.LENGTH_SHORT).show();
            }
            return true;
        });
//AI辅助生成 DeepSeek-R1-0528 2025.12.13
        // 点击搜索框时不取消高亮
        etSearch.setOnClickListener(v -> {
            // 阻止事件传递到根布局
        });


        etSearch.setOnFocusChangeListener((v, hasFocus) -> {

        });
    }

    /**
     * 高亮任意 View
     */
    private void highlightView(View view) {
        view.setBackgroundColor(0xFFFFE5E5); // 淡红色背景
        ScaleAnimation anim = new ScaleAnimation(1f, 1.02f, 1f, 1.02f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f);
        anim.setDuration(300);
        anim.setRepeatMode(Animation.REVERSE);
        anim.setRepeatCount(1);
        view.startAnimation(anim);
    }

    /**
     * 恢复按钮原始样式
     */
    private void resetButtonStyle() {
        // 恢复深蹲按钮
        btnSquat.setBackgroundResource(R.drawable.selector_squat_bar);
        btnSquat.clearAnimation();

        // 恢复拳击按钮
        btnBoxing.setBackgroundResource(R.drawable.selector_squat_bar);
        btnBoxing.clearAnimation();

        btnRomanianDeadlift.setBackgroundResource(R.drawable.selector_squat_bar);
        btnRomanianDeadlift.clearAnimation();


        btnHighKnees.setBackgroundResource(R.drawable.selector_squat_bar);
        btnHighKnees.clearAnimation();
    }

    /**
     * 创建 AI 悬浮按钮
     */
    private void createAiFab() {
        aiFabContainer = new FrameLayout(this);
        aiFabContainer.setId(View.generateViewId());

        // 设置按钮大小
        int size = dpToPx(56);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(size, size);
        aiFabContainer.setLayoutParams(params);

        //  初始位置设置：右下方
        params.gravity = Gravity.BOTTOM | Gravity.END;
        params.bottomMargin = dpToPx(MARGIN_BOTTOM);  // 距离底部 100dp
        params.rightMargin = dpToPx(MARGIN_RIGHT);    // 距离右边 16dp

        aiFabContainer.setLayoutParams(params);



        aiFabContainer.setElevation(dpToPx(8));
        aiFabContainer.setClickable(true);
        aiFabContainer.setFocusable(true);

        // 添加 AI 图标
        ImageView aiIcon = new ImageView(this);
        aiIcon.setImageResource(R.drawable.ic_ai_assistant);
        FrameLayout.LayoutParams iconParams = new FrameLayout.LayoutParams(
                dpToPx(28), dpToPx(28));
        iconParams.gravity = Gravity.CENTER;
        aiIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        aiFabContainer.addView(aiIcon);

        // 添加到根布局
        if (rootView instanceof ViewGroup) {
            ((ViewGroup) rootView).addView(aiFabContainer);
        }
//AI辅助生成 DeepSeek-R1-0528 2026.3.3
        // 设置拖拽监听
        aiFabContainer.post(() -> {
            ViewGroup parent = (ViewGroup) aiFabContainer.getParent();
            // 设置到右下角
            float initialX = parent.getWidth() - aiFabContainer.getWidth() - dpToPx(MARGIN_RIGHT);
            float initialY = parent.getHeight() - aiFabContainer.getHeight() - dpToPx(MARGIN_BOTTOM);

            aiFabContainer.setX(initialX);
            aiFabContainer.setY(initialY);
        });
        setupDragListener();

        // 点击监听
        aiFabContainer.setOnClickListener(v -> {
            showAiDialog();
            ScaleAnimation anim = new ScaleAnimation(1f, 0.9f, 1f, 0.9f,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            anim.setDuration(100);
            anim.setRepeatMode(Animation.REVERSE);
            anim.setRepeatCount(1);
            v.startAnimation(anim);
        });
    }

    /**
     * 设置拖拽监听
     */
    private void setupDragListener() {
        aiFabContainer.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;      // 按下时的坐标
            private float viewStartX, viewStartY;  // 视图按下时的位置
            private boolean isDragging = false;
            private static final int CLICK_THRESHOLD = 10;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        startY = event.getRawY();
                        viewStartX = v.getX();
                        viewStartY = v.getY();
                        isDragging = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startX;
                        float dy = event.getRawY() - startY;

                        if (Math.abs(dx) > CLICK_THRESHOLD || Math.abs(dy) > CLICK_THRESHOLD) {
                            isDragging = true;
                        }

                        if (isDragging) {
                            // 直接设置视图位置
                            float newX = viewStartX + dx;
                            float newY = viewStartY + dy;

                            // 边界检测
                            ViewGroup parent = (ViewGroup) v.getParent();
                            int maxX = parent.getWidth() - v.getWidth();
                            int maxY = parent.getHeight() - v.getHeight();

                            newX = Math.max(0, Math.min(newX, maxX));
                            newY = Math.max(0, Math.min(newY, maxY));

                            v.setX(newX);
                            v.setY(newY);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                        if (!isDragging) {
                            v.performClick();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    /**
     * 获取状态栏高度
     */
    private int getStatusBarHeight() {
        int result = 0;
        int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            result = getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * 创建 AI 对话框
     */
    private void createAiDialog() {
        aiDialog = new Dialog(this, R.style.AiDialogTheme);
        aiDialog.setContentView(R.layout.ai_dialog_layout);

        Window window = aiDialog.getWindow();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }

        chatContainer = aiDialog.findViewById(R.id.chat_container);
        scrollChat = aiDialog.findViewById(R.id.scroll_chat);
        etAiInput = aiDialog.findViewById(R.id.et_ai_input);
        btnSend = aiDialog.findViewById(R.id.btn_send);
        progressLoading = aiDialog.findViewById(R.id.progress_loading);

        ImageButton btnClose = aiDialog.findViewById(R.id.btn_close_dialog);
        btnClose.setOnClickListener(v -> aiDialog.dismiss());

        // 快捷问题芯片
        com.google.android.material.chip.Chip chipSquat = aiDialog.findViewById(R.id.chip_squat);
        com.google.android.material.chip.Chip chipBoxing = aiDialog.findViewById(R.id.chip_boxing);
        com.google.android.material.chip.Chip chipPlan = aiDialog.findViewById(R.id.chip_plan);

        chipSquat.setOnClickListener(v -> {
            etAiInput.setText("深蹲的标准动作要领是什么？");
            sendAiMessage();
        });

        chipBoxing.setOnClickListener(v -> {
            etAiInput.setText("拳击的基本技巧有哪些？");
            sendAiMessage();
        });

        chipPlan.setOnClickListener(v -> {
            etAiInput.setText("请为我制定一个适合新手的训练计划");
            sendAiMessage();
        });

        btnSend.setOnClickListener(v -> sendAiMessage());
    }

    private void showAiDialog() {
        if (aiDialog != null && !aiDialog.isShowing()) {
            aiDialog.show();
            etAiInput.setText("");
            etAiInput.requestFocus();
        }
    }

    private void sendAiMessage() {
        String message = etAiInput.getText().toString().trim();
        if (message.isEmpty()) {
            Toast.makeText(this, "请输入问题", Toast.LENGTH_SHORT).show();
            return;
        }

        addUserMessage(message);
        etAiInput.setText("");

        progressLoading.setVisibility(View.VISIBLE);
        btnSend.setEnabled(false);

        // 使用数组包装，让内部类可以修改
        final TextView[] aiMessageViewHolder = new TextView[1];

        // 使用流式发送
        doubaoApiClient.sendMessageStream(message, new DoubaoApiClient.StreamCallback() {

            @Override
            public void onStart() {
                // 创建空的AI消息视图
                aiMessageViewHolder[0] = createAiMessageView("");
                chatContainer.addView(aiMessageViewHolder[0]);
                scrollToBottom();
            }

            @Override
            public void onChunk(String chunk) {
                // 实时更新Markdown渲染
                if (aiMessageViewHolder[0] != null) {
                    setMarkdownText(aiMessageViewHolder[0], chunk);
                    scrollToBottom();
                }
            }

            @Override
            public void onComplete() {
                progressLoading.setVisibility(View.GONE);
                btnSend.setEnabled(true);
            }

            @Override
            public void onError(String error) {
                progressLoading.setVisibility(View.GONE);
                btnSend.setEnabled(true);
                if (aiMessageViewHolder[0] != null) {
                    aiMessageViewHolder[0].setText("抱歉，我遇到了一些问题：" + error + "\n请检查网络连接后重试。");
                }
            }
        });
    }

    private void addUserMessage(String message) {
        TextView userMsg = new TextView(this);
        userMsg.setText(message);
        userMsg.setTextSize(14);
        userMsg.setTextColor(getResources().getColor(android.R.color.white));
        userMsg.setBackgroundResource(R.drawable.user_message_background);
        userMsg.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.END;
        params.bottomMargin = dpToPx(8);
        userMsg.setLayoutParams(params);

        chatContainer.addView(userMsg);
        scrollToBottom();
    }

    /**
     * 创建AI消息视图（不添加到容器，用于流式更新）
     */
    private TextView createAiMessageView(String initialText) {
        TextView aiMsg = new TextView(this);
        aiMsg.setText(initialText);
        aiMsg.setTextSize(14);
        aiMsg.setTextColor(getResources().getColor(android.R.color.black));
        aiMsg.setBackgroundResource(R.drawable.ai_message_background);
        aiMsg.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.START;
        params.bottomMargin = dpToPx(8);
        aiMsg.setLayoutParams(params);

        return aiMsg;
    }

    /**
     * 设置Markdown格式的文本
     */
    private void setMarkdownText(TextView textView, String markdown) {
        if (markwon != null && markdown != null && !markdown.isEmpty()) {
            markwon.setMarkdown(textView, markdown);
        } else {
            textView.setText(markdown);
        }
    }

    private void addAiMessage(String message) {
        TextView aiMsg = new TextView(this);
        aiMsg.setText(message);
        aiMsg.setTextSize(14);
        aiMsg.setTextColor(getResources().getColor(android.R.color.black));
        aiMsg.setBackgroundResource(R.drawable.ai_message_background);
        aiMsg.setPadding(dpToPx(12), dpToPx(12), dpToPx(12), dpToPx(12));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.START;
        params.bottomMargin = dpToPx(8);
        aiMsg.setLayoutParams(params);

        chatContainer.addView(aiMsg);
        scrollToBottom();
    }

    private void scrollToBottom() {
        scrollChat.post(() -> scrollChat.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private int dpToPx(int dp) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (aiDialog != null && aiDialog.isShowing()) {
            aiDialog.dismiss();
        }
    }
}