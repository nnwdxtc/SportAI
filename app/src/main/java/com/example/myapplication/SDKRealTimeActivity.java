package com.example.myapplication;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness.sdk.FitnessSDK;
import com.example.fitness.sdk.config.CameraConfig;
import com.example.fitness.sdk.config.SDKConfig;
import com.example.fitness.sdk.listener.FitnessSDKListener;
import com.example.fitness.sdk.model.ActionData;
import com.example.fitness.sdk.model.ActionData.ActionConfig;
import com.example.fitness.sdk.model.ErrorType;
import com.example.fitness.sdk.model.Keypoint;
import com.example.fitness.sdk.ui.PoseOverlayView;
import com.example.poselandmarker.R;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SDKRealTimeActivity extends AppCompatActivity implements FitnessSDKListener {

    private static final String TAG = "SDKRealTimeActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 1001;

    // SDK
    private FitnessSDK sdk;
    private boolean isSDKReady = false;
    private ActionStrategy actionStrategy;
    private String actionName;
    private boolean needCounting = false;

    // UI
    private PoseOverlayView poseOverlayView;
    private Button startButton;
    private Button selectVideoButton;
    private TextView squatCountView;
    private TextView angleView;
    private TextView tvSimilarity;
    private LinearLayout staticHintLayout;
    private PlayerView exoPlayerView;
    private SimpleExoPlayer player;
    private PixelPerfectOverlayView videoOverlayView;
    private Handler videoUpdateHandler;
    private Runnable videoUpdateRunnable;

    // 标准动作数据
    private final List<FrameData> standardVideoData = new ArrayList<>();

    // 状态
    private boolean isTracking = false;
    private boolean isStandardDataReady = false;
    private int currentCount = 0;
    private float currentSimilarity = 0f;

    // 权限
    private String[] REQUIRED_PERMISSIONS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        actionStrategy = (ActionStrategy) getIntent().getSerializableExtra("action_strategy");
        if (actionStrategy == null) {
            actionStrategy = new SquatStrategy();
        }
        actionName = actionStrategy.getActionName();
        needCounting = actionStrategy.hasCounter();

        setContentView(R.layout.activity_realtime);

        copyAssetsToCache();

        initViews();
        initPermissions();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.back_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        selectVideoButton = findViewById(R.id.select_video_button);
        startButton = findViewById(R.id.start_button);
        squatCountView = findViewById(R.id.squatCount);
        angleView = findViewById(R.id.angleView);
        tvSimilarity = findViewById(R.id.tvSimilarity);
        staticHintLayout = findViewById(R.id.staticHintLayout);
        exoPlayerView = findViewById(R.id.exoPlayerView);
        videoOverlayView = findViewById(R.id.videoOverlayView);
        poseOverlayView = findViewById(R.id.poseOverlayView);

        // 隐藏原有的 OverlayView
        View oldOverlay = findViewById(R.id.overlay);
        if (oldOverlay != null) {
            oldOverlay.setVisibility(View.GONE);
        }

        selectVideoButton.setOnClickListener(v -> showVideoOptionsDialog());
        startButton.setOnClickListener(v -> toggleTracking());

        setupAngleDisplay();

        videoUpdateHandler = new Handler(Looper.getMainLooper());
        videoUpdateRunnable = () -> {
            if (player != null && player.isPlaying() && isStandardDataReady) {
                updateVideoOverlay();
            }
            if (videoUpdateHandler != null) {
                videoUpdateHandler.postDelayed(videoUpdateRunnable, 33);
            }
        };
    }

    private void startVideoUpdateTimer() {
        if (videoUpdateHandler != null && videoUpdateRunnable != null) {
            videoUpdateHandler.removeCallbacks(videoUpdateRunnable);
            videoUpdateHandler.post(videoUpdateRunnable);
            Log.d(TAG, "视频帧更新定时器已启动");
        }
    }

    private void stopVideoUpdateTimer() {
        if (videoUpdateHandler != null && videoUpdateRunnable != null) {
            videoUpdateHandler.removeCallbacks(videoUpdateRunnable);
            Log.d(TAG, "视频帧更新定时器已停止");
        }
    }

    private void setupAngleDisplay() {
        if (angleView != null) {
            angleView.setBackgroundColor(Color.argb(128, 0, 0, 0));
            angleView.setTextColor(Color.WHITE);
            angleView.setTextSize(14);
            angleView.setPadding(12, 8, 12, 8);
            if ("拳击".equals(actionName)) {
                angleView.setText("左肘: --° | 右肘: --°\n左肩: --° | 右肩: --°");
            } else {
                angleView.setText("膝: --°\n髋: --°");
            }
        }
    }

    private void updateSimilarityDisplay(float similarity) {
        if (tvSimilarity != null) {
            tvSimilarity.setText(String.format("相似度: %.0f%%", similarity * 100));
            if (similarity >= 0.8f) {
                tvSimilarity.setTextColor(Color.GREEN);
            } else if (similarity >= 0.6f) {
                tvSimilarity.setTextColor(Color.YELLOW);
            } else {
                tvSimilarity.setTextColor(Color.RED);
            }
        }
    }

    private void showVideoOptionsDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_video_selector, null);
        androidx.appcompat.app.AlertDialog dialog = new androidx.appcompat.app.AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        RecyclerView rvOfficial = dialogView.findViewById(R.id.rv_official_videos);
        rvOfficial.setLayoutManager(new LinearLayoutManager(this));
        rvOfficial.setAdapter(new OfficialVideoAdapter(actionStrategy.getOfficialVideos(), video -> {
            dialog.dismiss();
            loadStandardVideo(video);
        }));

        dialogView.findViewById(R.id.tv_album).setOnClickListener(v -> {
            dialog.dismiss();
        });
        dialog.show();
    }

    private void loadStandardVideo(ActionStrategy.OfficialVideo video) {
        Toast.makeText(this, "加载: " + video.displayName, Toast.LENGTH_SHORT).show();
        selectVideoButton.setEnabled(false);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                List<FrameData> data = PoseCache.read(video.cacheName, this);
                if (data == null || data.isEmpty()) {
                    Toast.makeText(this, "数据缺失", Toast.LENGTH_LONG).show();
                    selectVideoButton.setEnabled(true);
                    return;
                }

                standardVideoData.clear();
                standardVideoData.addAll(data);
                isStandardDataReady = true;

                if (isSDKReady) {
                    loadActionToSDK();
                }

                int resId = getResources().getIdentifier(video.videoResName, "raw", getPackageName());
                if (resId != 0) {
                    setupVideoPlayer(Uri.parse("android.resource://" + getPackageName() + "/" + resId));
                }

                selectVideoButton.setEnabled(true);
                startButton.setEnabled(true);
                Toast.makeText(this, "加载完成: " + data.size() + "帧", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Log.e(TAG, "加载失败", e);
                selectVideoButton.setEnabled(true);
                Toast.makeText(this, "加载失败", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void copyAssetsToCache() {
        String[] files = {"squat_standard.bin", "boxing_jab.bin", "boxing_hook.bin",
                "boxing_swing.bin", "high_knees_standard.bin", "romanian_deadlift_standard.bin"};
        for (String fileName : files) {
            File cacheFile = new File(getCacheDir(), "pose_cache/" + fileName);
            if (!cacheFile.exists()) {
                try {
                    File cacheDir = new File(getCacheDir(), "pose_cache");
                    if (!cacheDir.exists()) {
                        cacheDir.mkdirs();
                    }
                    InputStream is = getAssets().open("pose/" + fileName);
                    FileOutputStream fos = new FileOutputStream(cacheFile);
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    is.close();
                    Log.d(TAG, "复制成功: " + fileName);
                } catch (IOException e) {
                    Log.e(TAG, "复制失败: " + fileName, e);
                }
            }
        }
    }

    private void setupVideoPlayer(Uri videoUri) {
        if (player != null) {
            player.release();
        }
        player = new SimpleExoPlayer.Builder(this).build();
        exoPlayerView.setPlayer(player);
        player.setVolume(0f);

        DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                Util.getUserAgent(this, getApplicationInfo().loadLabel(getPackageManager()).toString()));

        MediaItem mediaItem = MediaItem.fromUri(videoUri);
        ProgressiveMediaSource mediaSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(mediaItem);

        player.setMediaSource(mediaSource);
        player.prepare();
        player.setRepeatMode(Player.REPEAT_MODE_ALL);
        player.setPlayWhenReady(true);

        View placeholder = findViewById(R.id.videoPlaceholder);
        if (placeholder != null) {
            placeholder.setVisibility(View.GONE);
        }

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    Log.d(TAG, "视频准备就绪");
                    exoPlayerView.post(() -> {
                        updateVideoDisplayRect();
                        updateVideoOverlay();
                        startVideoUpdateTimer();
                    });
                }
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                                Player.PositionInfo newPosition, int reason) {
                updateVideoOverlay();
            }
        });
    }

    private void updateVideoOverlay() {
        if (!isStandardDataReady || standardVideoData.isEmpty() || player == null) return;
        long positionMs = player.getCurrentPosition();
        int currentFrame = (int) ((positionMs * 30) / 1000) % standardVideoData.size();
        if (currentFrame < standardVideoData.size()) {
            FrameData frameData = standardVideoData.get(currentFrame);
            if (frameData.landmarks != null && videoOverlayView != null) {
                videoOverlayView.setStandardLandmarks(frameData.landmarks);
            }
        }
    }

    private void updateVideoDisplayRect() {
        if (exoPlayerView == null || player == null) return;
        com.google.android.exoplayer2.video.VideoSize vs = player.getVideoSize();
        if (vs.width == 0 || vs.height == 0) return;

        int vw = exoPlayerView.getWidth();
        int vh = exoPlayerView.getHeight();
        float videoAspect = (float) vs.width / vs.height;
        float viewAspect = (float) vw / vh;

        RectF rect = new RectF();
        if (videoAspect > viewAspect) {
            float displayHeight = vw / videoAspect;
            float top = (vh - displayHeight) * 0.5f;
            rect.set(0, top, vw, top + displayHeight);
        } else {
            float displayWidth = vh * videoAspect;
            float left = (vw - displayWidth) * 0.5f;
            rect.set(left, 0, left + displayWidth, vh);
        }
        videoOverlayView.setVideoDisplayRect(rect);
    }

    private void loadActionToSDK() {
        if (!isSDKReady || standardVideoData.isEmpty()) return;

        List<com.example.fitness.sdk.model.SkeletonFrame> frames = new ArrayList<>();
        for (FrameData data : standardVideoData) {
            com.example.fitness.sdk.model.SkeletonFrame frame = new com.example.fitness.sdk.model.SkeletonFrame.Builder()
                    .setTimestampMs(data.timestamp)
                    .setHasValidPose(data.hasValidPose)
                    .setKneeAngle(data.kneeAngle)
                    .setHipAngle(data.hipAngle)
                    .setElbowAngle(data.elbowAngle)
                    .setShoulderAngle(data.shoulderAngle)
                    .setLeftElbowAngle(data.leftElbowAngle)
                    .setRightElbowAngle(data.rightElbowAngle)
                    .setLeftShoulderAngle(data.leftShoulderAngle)
                    .setRightShoulderAngle(data.rightShoulderAngle)
                    .build();
            frames.add(frame);
        }

        // 根据动作名称获取对应的配置
        ActionConfig config = getActionConfigForName(actionName);

        // 如果从标准视频数据中能获取到帧数信息，可以动态设置 minMatchedFrames
        int expectedFrameCount = frames.size();
        int minMatchedFrames = (int)(expectedFrameCount * 0.7f); // 至少匹配70%的帧

        // 创建带配置的 ActionData
        ActionData actionData = new ActionData.Builder()
                .setActionId(actionName.toLowerCase().replace(" ", "_"))
                .setActionName(actionName)
                .setFrames(frames)
                .setFps(30f)
                .setNeedCounting(needCounting)
                .setConfig(config)
                .build();

        sdk.loadStandardAction(actionData);
        Log.d(TAG, "已加载标准动作，帧数: " + frames.size() + ", 最小匹配帧数: " + minMatchedFrames);
    }

    /**
     * 根据动作名称获取对应的配置
     */
    private ActionConfig getActionConfigForName(String actionName) {
        if (actionName.contains("拳击")) {
            return new ActionConfig.Builder()
                    .setWeights(createBoxingWeights())
                    .setCompletionThreshold(0.55f)
                    .setRhythmTolerance(0.4f)
                    .setMinMatchedFrames(30)
                    .setMinSimilarityThreshold(0.25f)
                    .setScoringMode("average")
                    .build();
        } else if (actionName.contains("高抬腿")) {
            return new ActionConfig.Builder()
                    .setWeights(createHighKneesWeights())
                    .setCompletionThreshold(0.65f)
                    .setRhythmTolerance(0.25f)
                    .setMinMatchedFrames(40)
                    .setMinSimilarityThreshold(0.35f)
                    .setScoringMode("average")
                    .build();
        } else if (actionName.contains("硬拉") || actionName.contains("罗马尼亚")) {
            return new ActionConfig.Builder()
                    .setWeights(createDeadliftWeights())
                    .setCompletionThreshold(0.6f)
                    .setRhythmTolerance(0.6f)
                    .setMinMatchedFrames(50)
                    .setMinSimilarityThreshold(0.3f)
                    .setScoringMode("average")
                    .build();
        } else {
            // 默认深蹲配置
            return new ActionConfig.Builder()
                    .setWeights(createSquatWeights())
                    .setCompletionThreshold(0.5f)
                    .setRhythmTolerance(0.8f)
                    .setMinMatchedFrames(30)
                    .setMinSimilarityThreshold(0.4f)
                    .setScoringMode("average")
                    .build();
        }
    }

    private Map<String, Float> createSquatWeights() {
        Map<String, Float> weights = new HashMap<>();
        weights.put("knee", 0.5f);
        weights.put("hip", 0.5f);
        return weights;
    }

    private Map<String, Float> createBoxingWeights() {
        Map<String, Float> weights = new HashMap<>();
        weights.put("leftElbow", 0.3f);
        weights.put("rightElbow", 0.3f);
        weights.put("leftShoulder", 0.2f);
        weights.put("rightShoulder", 0.2f);
        return weights;
    }

    private Map<String, Float> createHighKneesWeights() {
        Map<String, Float> weights = new HashMap<>();
        weights.put("knee", 0.7f);
        weights.put("hip", 0.3f);
        return weights;
    }

    private Map<String, Float> createDeadliftWeights() {
        Map<String, Float> weights = new HashMap<>();
        weights.put("hip", 0.8f);
        weights.put("knee", 0.2f);
        return weights;
    }

    private void initPermissions() {
        REQUIRED_PERMISSIONS = new String[]{Manifest.permission.CAMERA};
        if (allPermissionsGranted()) {
            initSDK();
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
        }
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void initSDK() {
        sdk = FitnessSDK.getInstance();
        SDKConfig config = SDKConfig.builder()
                .setCallbackFps(30)
                .build();
        sdk.init(this, config, this);
    }

    private void toggleTracking() {
        if (isTracking) {
            stopTracking();
        } else {
            startTracking();
        }
    }

    private void startTracking() {
        if (!isStandardDataReady) {
            Toast.makeText(this, "请先选择标准视频", Toast.LENGTH_SHORT).show();
            return;
        }
        isTracking = true;
        currentCount = 0;
        currentSimilarity = 0f;

        // 从头播放标准视频
        if (player != null) {
            player.seekTo(0);
            player.setPlayWhenReady(true);
        }

        sdk.startSession();
        startButton.setText("停止运动");

    }

    private void stopTracking() {
        isTracking = false;

        sdk.stopSession();
        sdk.resetCounter();
        runOnUiThread(() -> {
            startButton.setText("开始运动");
            if (squatCountView != null && needCounting) {
                squatCountView.setText(actionName + ": 0");
            }
            currentCount = 0;
            currentSimilarity = 0f;
            updateSimilarityDisplay(0f);

            if (staticHintLayout != null) {
                staticHintLayout.setVisibility(View.GONE);
            }
        });

        Log.d(TAG, "停止跟踪，动作类型：" + actionName);
    }

    // ========== FitnessSDKListener 回调 ==========

    @Override
    public void onInitSuccess(String sdkVersion) {
        runOnUiThread(() -> {
            isSDKReady = true;
            Toast.makeText(this, "SDK初始化成功", Toast.LENGTH_SHORT).show();

            poseOverlayView = findViewById(R.id.poseOverlayView);
            sdk.setPoseOverlayView(poseOverlayView);

            // 开启相机
            CameraConfig cameraConfig = CameraConfig.builder()
                    .setLensFacing(CameraConfig.CAMERA_FACING_BACK)
                    .setTargetSize(640, 480)
                    .build();

            PreviewView viewFinder = findViewById(R.id.viewFinder);
            sdk.openCamera(this, viewFinder, cameraConfig);

            if (isStandardDataReady) {
                loadActionToSDK();
            }
        });
    }

    @Override
    public void onInitError(int code, String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, "SDK初始化失败: " + message, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    public void onSkeletonFrame(Bitmap frame, List<Keypoint> keypoints) {
        runOnUiThread(() -> {
            updateSimilarityDisplay(currentSimilarity);

            if (angleView != null && keypoints != null && isTracking) {
                updateAngleDisplay(keypoints);
            }
        });
    }

    private void updateAngleDisplay(List<Keypoint> keypoints) {
        // 从关键点中提取膝关节点计算角度
        float kneeAngle = 0;
        float hipAngle = 0;

        for (Keypoint kp : keypoints) {
            if (kp.getId() == 25) { // 左膝
                // 需要三个点才能准确计算，这里简化
                kneeAngle = kp.getY() * 180;
            }
            if (kp.getId() == 23) { // 左髋
                hipAngle = kp.getY() * 180;
            }
        }

        if ("拳击".equals(actionName)) {
            angleView.setText(String.format("肘: %.0f°\n肩: %.0f°", kneeAngle, hipAngle));
        } else {
            angleView.setText(String.format("膝: %.0f°\n髋: %.0f°", kneeAngle, hipAngle));
        }

        // 根据相似度设置角度显示颜色
        if (currentSimilarity >= 0.8f) {
            angleView.setTextColor(Color.GREEN);
        } else if (currentSimilarity >= 0.6f) {
            angleView.setTextColor(Color.YELLOW);
        } else {
            angleView.setTextColor(Color.RED);
        }
    }

    @Override
    public void onActionStart(String actionId) {
        runOnUiThread(() -> {
            if (staticHintLayout != null) {
                staticHintLayout.setVisibility(View.GONE);
            }
            Log.d(TAG, "动作开始: " + actionId);
        });
    }

    @Override
    public void onActionSuccess(int score, long durationMs, String actionId, int completedCount) {
        currentCount = completedCount;
        currentSimilarity = score / 100f;
        runOnUiThread(() -> {
            updateSimilarityDisplay(currentSimilarity);
            if (squatCountView != null && needCounting) {
                squatCountView.setText(actionName + ": " + completedCount);
            }
            Toast.makeText(this, "✅ 动作完成! 得分:" + score + " 次数:" + completedCount, Toast.LENGTH_SHORT).show();
            Log.d(TAG, String.format("动作完成! 得分:%d, 次数:%d, 耗时:%.1fs", score, completedCount, durationMs / 1000.0));
        });
    }

    @Override
    public void onActionError(int score, ErrorType errorType, List<Bitmap> errorKeyFrames, Bitmap correctFrameRef) {
        currentSimilarity = score / 100f;
        runOnUiThread(() -> {
            updateSimilarityDisplay(currentSimilarity);
            if (staticHintLayout != null) {
                staticHintLayout.setVisibility(View.VISIBLE);
                // 显示具体错误信息
                TextView hintDetail = staticHintLayout.findViewById(R.id.staticHintDetail);
                if (hintDetail != null) {
                    hintDetail.setText(errorType.getDescription());
                }
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    if (staticHintLayout != null) {
                        staticHintLayout.setVisibility(View.GONE);
                    }
                }, 2000);
            }
            Toast.makeText(this, "❌ 动作错误: " + errorType.getDescription() + " 得分:" + score, Toast.LENGTH_SHORT).show();
            Log.w(TAG, "动作错误: " + errorType.getDescription() + ", 得分:" + score);
        });
    }

    @Override
    public void onActionSwitched(String newActionId) {
        Log.d(TAG, "动作切换: " + newActionId);
    }

    @Override
    public void onActionTimeout(String actionId) {
        runOnUiThread(() -> {
            Toast.makeText(this, "动作超时", Toast.LENGTH_SHORT).show();
            stopTracking();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopVideoUpdateTimer();
        if (videoUpdateHandler != null) {
            videoUpdateHandler.removeCallbacksAndMessages(null);
        }
        if (sdk != null) {
            sdk.release();
        }
        if (player != null) {
            player.release();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                initSDK();
            } else {
                Toast.makeText(this, "需要相机权限", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}