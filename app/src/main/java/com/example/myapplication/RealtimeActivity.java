package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.fitness.sdk.FitnessSDK;
import com.example.fitness.sdk.config.SDKConfig;
import com.example.fitness.sdk.listener.FitnessSDKListener;
import com.example.fitness.sdk.model.ActionData;
import com.example.fitness.sdk.model.CameraFrame;
import com.example.fitness.sdk.model.ErrorType;
import com.example.fitness.sdk.model.Keypoint;
import com.example.fitness.sdk.model.SkeletonFrame;
import com.example.poselandmarker.R;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackException;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

public class RealtimeActivity extends AppCompatActivity implements FitnessSDKListener {

    private static final String TAG = "RealtimeActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private static final int REQUEST_VIDEO_PICK = 1002;
    public static final String EXTRA_ACTION = "action_strategy";

    // ========== SDK 核心变量 ==========
    private FitnessSDK sdk;
    private boolean isSDKReady = false;
    private ActionStrategy actionStrategy;
    private String actionName;
    private boolean needCounting = false;

    // ========== SDK 回调数据 ==========
    private float currentSimilarity = 0f;
    private int currentSDKCount = 0;

    // ========== 运动状态 ==========
    private boolean isTracking = false;
    private boolean isStandardDataReady = false;
    private long workoutStartTime = 0;

    // ========== 标准动作数据 ==========
    private final List<FrameData> standardVideoData = new ArrayList<>();
    private float standardVideoFps = 30f;

    // ========== UI组件 ==========
    private PreviewView viewFinder;
    private OverlayView overlayView;           // SDK 会更新这个视图
    private PixelPerfectOverlayView videoOverlayView;
    private ImageButton switchCameraButton;
    private Button startButton;
    private Button selectVideoButton;
    private TextView squatCountView;
    private TextView angleView;
    private LinearLayout staticHintLayout;
    private PlayerView exoPlayerView;
    private LinearLayout videoControlPanel;
    private Button btnPlaybackSpeed;
    private TextView countdownText;
    private Button generateReportButton;
    private LinearLayout videoProgressOverlay;
    private ProgressBar videoProgressBar;
    private TextView videoProgressText;
    private TextView videoProgressPercent;

    // ========== 摄像头相关 ==========
    private ProcessCameraProvider cameraProvider;
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private int currentRotation = 90;
    private Executor backgroundExecutor;

    // ========== 视频播放器 ==========
    private SimpleExoPlayer player;
    private Uri customStandardVideoUri = null;

    // ========== 倒计时相关 ==========
    private Handler countdownHandler;
    private int countdownValue = 3;
    private boolean isWaitingForDetection = false;
    private int successfulDetectionCount = 0;
    private Handler detectionHandler;

    // ========== 其他 ==========
    private WorkoutDataService workoutDataService;
    private VideoProcessor videoProcessor;
    private static final long DETECTION_TIMEOUT_MS = 30000;
    private static final int DETECTION_REQUIRED_COUNT = 30;
    private List<Float> similarityList = new ArrayList<>();

    // 权限
    private String[] REQUIRED_PERMISSIONS;

    // ========== 生命周期 ==========

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 获取动作策略
        actionStrategy = (ActionStrategy) getIntent().getSerializableExtra(EXTRA_ACTION);
        if (actionStrategy == null) {
            actionStrategy = new SquatStrategy();
        }
        actionName = actionStrategy.getActionName();
        needCounting = actionStrategy.hasCounter();

        setContentView(R.layout.activity_realtime);

        setupToolbar();
        initializeViews();
        initPermissions();

        countdownHandler = new Handler(Looper.getMainLooper());
        detectionHandler = new Handler(Looper.getMainLooper());
        backgroundExecutor = java.util.concurrent.Executors.newSingleThreadExecutor();
        workoutDataService = new WorkoutDataService(this);
        videoProcessor = new VideoProcessor(this);
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.back_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    private void initializeViews() {
        viewFinder = findViewById(R.id.viewFinder);
        overlayView = findViewById(R.id.overlay);
        videoOverlayView = findViewById(R.id.videoOverlayView);
        switchCameraButton = findViewById(R.id.switch_camera_button);
        startButton = findViewById(R.id.start_button);
        selectVideoButton = findViewById(R.id.select_video_button);
        squatCountView = findViewById(R.id.squatCount);
        angleView = findViewById(R.id.angleView);
        staticHintLayout = findViewById(R.id.staticHintLayout);
        exoPlayerView = findViewById(R.id.exoPlayerView);
        videoControlPanel = findViewById(R.id.videoControlPanel);
        btnPlaybackSpeed = findViewById(R.id.btn_playback_speed);
        countdownText = findViewById(R.id.countdownText);
        generateReportButton = findViewById(R.id.btn_generate_report);
        videoProgressOverlay = findViewById(R.id.videoProgressOverlay);
        videoProgressBar = findViewById(R.id.videoProgressBar);
        videoProgressText = findViewById(R.id.videoProgressText);
        videoProgressPercent = findViewById(R.id.videoProgressPercent);

        // 根据动作类型控制计数面板显示
        LinearLayout lytContainer = findViewById(R.id.lyt_container);
        if (lytContainer != null) {
            if ("拳击".equals(actionName)) {
                lytContainer.setVisibility(View.GONE);
            } else {
                lytContainer.setVisibility(View.VISIBLE);
            }
        }

        // 设置按钮监听
        startButton.setOnClickListener(v -> toggleTracking());
        selectVideoButton.setOnClickListener(v -> showVideoOptionsDialog());
        switchCameraButton.setOnClickListener(v -> toggleCamera());

        if (btnPlaybackSpeed != null) {
            btnPlaybackSpeed.setOnClickListener(v -> showSpeedPickerDialog());
        }
        if (exoPlayerView != null) {
            exoPlayerView.setOnClickListener(v -> toggleControlPanel());
        }
        if (generateReportButton != null) {
            generateReportButton.setOnClickListener(v -> generateReport());
        }

        setupAngleDisplay();
        initializeExoPlayer();

        REQUIRED_PERMISSIONS = getRequiredPermissions();
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

    private void toggleControlPanel() {
        if (videoControlPanel != null) {
            if (videoControlPanel.getVisibility() == View.VISIBLE) {
                videoControlPanel.setVisibility(View.GONE);
            } else {
                videoControlPanel.setVisibility(View.VISIBLE);
            }
        }
    }

    private void showSpeedPickerDialog() {
        if (player == null) return;
        String[] labels = {"0.5 倍速", "1 倍速", "2 倍速"};
        float[] speeds = {0.5f, 1.0f, 2.0f};
        new AlertDialog.Builder(this)
                .setTitle("选择播放速度")
                .setItems(labels, (dialog, which) -> {
                    float speed = speeds[which];
                    player.setPlaybackParameters(new PlaybackParameters(speed));
                    if (btnPlaybackSpeed != null) {
                        btnPlaybackSpeed.setText("倍速: " + speed + "x");
                    }
                })
                .show();
    }

    // ========== 视频选择与加载 ==========

    private void showVideoOptionsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_video_selector, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        RecyclerView rvOfficial = dialogView.findViewById(R.id.rv_official_videos);
        rvOfficial.setLayoutManager(new LinearLayoutManager(this));
        rvOfficial.setAdapter(new OfficialVideoAdapter(actionStrategy.getOfficialVideos(), video -> {
            dialog.dismiss();
            handleDefaultVideo(video);
        }));

        dialogView.findViewById(R.id.tv_album).setOnClickListener(v -> {
            dialog.dismiss();
            selectVideoFromAlbum();
        });
        dialog.show();
    }

    private void handleDefaultVideo(ActionStrategy.OfficialVideo video) {
        Toast.makeText(this, "加载官方视频: " + video.displayName, Toast.LENGTH_SHORT).show();
        selectVideoButton.setEnabled(false);
        showVideoProgress("正在加载标准视频...", 0);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                List<FrameData> data = PoseCache.read(video.cacheName, this);
                if (data == null || data.isEmpty()) {
                    Toast.makeText(this, "官方标准数据缺失", Toast.LENGTH_LONG).show();
                    selectVideoButton.setEnabled(true);
                    hideVideoProgress();
                    return;
                }

                standardVideoData.clear();
                standardVideoData.addAll(data);
                isStandardDataReady = true;
                standardVideoFps = 30f;

                // 加载视频文件
                int resId = getResources().getIdentifier(video.videoResName, "raw", getPackageName());
                if (resId != 0) {
                    Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + resId);
                    setupVideoPlayer(videoUri);
                }

                // 如果 SDK 已就绪，加载动作到 SDK
                if (isSDKReady) {
                    loadActionToSDK();
                }

                selectVideoButton.setEnabled(true);
                hideVideoProgress();
                Toast.makeText(this, "加载完成: " + data.size() + "帧", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Log.e(TAG, "加载失败: " + e.getMessage(), e);
                selectVideoButton.setEnabled(true);
                hideVideoProgress();
                Toast.makeText(this, "加载失败", Toast.LENGTH_LONG).show();
            }
        });
    }

    private void selectVideoFromAlbum() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        startActivityForResult(intent, REQUEST_VIDEO_PICK);
    }

    private void setupVideoPlayer(Uri videoUri) {
        if (player != null) {
            player.release();
        }
        player = new SimpleExoPlayer.Builder(this).build();
        exoPlayerView.setPlayer(player);
        exoPlayerView.setUseController(false);
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

        LinearLayout videoPlaceholder = findViewById(R.id.videoPlaceholder);
        if (videoPlaceholder != null) {
            videoPlaceholder.setVisibility(View.GONE);
        }

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    updateVideoOverlay();
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
        int totalFrames = standardVideoData.size();
        int currentFrame = (int) ((positionMs * standardVideoFps) / 1000) % totalFrames;
        if (currentFrame < standardVideoData.size()) {
            FrameData frameData = standardVideoData.get(currentFrame);
            if (frameData.landmarks != null && videoOverlayView != null) {
                videoOverlayView.setStandardLandmarks(frameData.landmarks);
            }
        }
    }

    private void initializeExoPlayer() {
        // 初始化播放器，等待视频加载
        player = null;
    }

    private void showVideoProgress(String message, int progress) {
        runOnUiThread(() -> {
            if (videoProgressOverlay != null) {
                videoProgressOverlay.setVisibility(View.VISIBLE);
                if (videoProgressText != null) videoProgressText.setText(message);
                if (videoProgressBar != null) videoProgressBar.setProgress(progress);
                if (videoProgressPercent != null) videoProgressPercent.setText(progress + "%");
            }
        });
    }

    private void hideVideoProgress() {
        runOnUiThread(() -> {
            if (videoProgressOverlay != null) {
                videoProgressOverlay.setVisibility(View.GONE);
            }
        });
    }

    // ========== SDK 集成核心方法 ==========

    /**
     * 初始化 SDK
     */
    private void initSDK() {
        Log.d(TAG, "初始化 FitnessSDK...");
        sdk = FitnessSDK.getInstance();

        SDKConfig config = SDKConfig.builder()
                .setCallbackFps(30)           // 回调频率 30fps
                .setMinDetectionConfidence(0.5f)  // 最小检测置信度
                .setMinTrackingConfidence(0.5f)   // 最小跟踪置信度
                .setActionTimeoutMs(30000)        // 动作超时 30秒
                .build();

        sdk.init(this, config, this);
    }

    /**
     * 加载标准动作到 SDK
     */
    private void loadActionToSDK() {
        if (!isSDKReady || standardVideoData.isEmpty()) return;

        List<SkeletonFrame> frames = new ArrayList<>();
        for (FrameData data : standardVideoData) {
            frames.add(convertToSkeletonFrame(data));
        }

        ActionData actionData = new ActionData.Builder()
                .setActionId(actionName.toLowerCase().replace(" ", "_"))
                .setActionName(actionName)
                .setFrames(frames)
                .setFps(standardVideoFps)
                .setNeedCounting(needCounting)
                .setSwitchMode(ActionData.SwitchMode.IMMEDIATE)
                .build();

        sdk.loadStandardAction(actionData);
        Log.d(TAG, "已加载动作到SDK: " + actionName + ", 帧数: " + frames.size());
    }

    /**
     * 转换 FrameData 为 SkeletonFrame
     */
    private SkeletonFrame convertToSkeletonFrame(FrameData frameData) {
        List<Keypoint> keypoints = new ArrayList<>();
        if (frameData.landmarks != null) {
            for (int i = 0; i < frameData.landmarks.size() && i < 33; i++) {
                NormalizedLandmark lm = frameData.landmarks.get(i);
                float visibility = lm.visibility().isPresent() ? lm.visibility().get() : 0f;
                keypoints.add(new Keypoint(i, lm.x(), lm.y(), lm.z(), visibility));
            }
        }
        return new SkeletonFrame.Builder()
                .setTimestampMs(frameData.timestamp)
                .setKeypoints(keypoints)
                .setHasValidPose(frameData.hasValidPose)
                .setKneeAngle(frameData.kneeAngle)
                .setHipAngle(frameData.hipAngle)
                .setElbowAngle(frameData.elbowAngle)
                .setShoulderAngle(frameData.shoulderAngle)
                .setLeftElbowAngle(frameData.leftElbowAngle)
                .setRightElbowAngle(frameData.rightElbowAngle)
                .setLeftShoulderAngle(frameData.leftShoulderAngle)
                .setRightShoulderAngle(frameData.rightShoulderAngle)
                .build();
    }

    // ========== 权限管理 ==========

    private void initPermissions() {
        REQUIRED_PERMISSIONS = getRequiredPermissions();
        if (allPermissionsGranted()) {
            // 权限已授予，初始化 SDK
            new Handler().postDelayed(() -> initSDK(), 500);
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

    private String[] getRequiredPermissions() {
        List<String> permissions = new ArrayList<>();
        permissions.add(Manifest.permission.CAMERA);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO);
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        }
        return permissions.toArray(new String[0]);
    }

    // ========== 摄像头管理 ==========

    private void startCamera() {
        if (!allPermissionsGranted()) return;

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindCameraUseCases();
            } catch (ExecutionException | InterruptedException e) {
                Log.e(TAG, "摄像头初始化失败", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        try {
            cameraProvider.unbindAll();

            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(cameraFacing)
                    .build();

            Preview preview = new Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build();

            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();

            imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::analyzeImage);

            Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
            preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

            currentRotation = cameraFacing == CameraSelector.LENS_FACING_FRONT ? 270 : 90;
            if (overlayView != null) {
                overlayView.setRotationDegrees(currentRotation);
                overlayView.setFrontCamera(cameraFacing == CameraSelector.LENS_FACING_FRONT);
            }

        } catch (Exception e) {
            Log.e(TAG, "绑定相机失败: " + e.getMessage(), e);
        }
    }

    private int getCurrentCameraRotation() {
        return 90;  // 默认旋转90度
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy image) {
        // 核心：只有在运动跟踪状态且SDK就绪时才处理
        if (isTracking && isSDKReady) {
            try {
                Bitmap bitmap = imageProxyToBitmap(image);
                if (bitmap != null) {
                    // 创建 CameraFrame 并调用 SDK 的 updateFrame
                    CameraFrame cameraFrame = new CameraFrame(bitmap, System.currentTimeMillis(), getCurrentCameraRotation());
                    bitmap.recycle();
                }
            } catch (Exception e) {
                Log.e(TAG, "分析图像失败: " + e.getMessage());
            } finally {
                image.close();
            }
        } else {
            image.close();
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        try {
            ImageProxy.PlaneProxy[] planes = image.getPlanes();
            if (planes.length < 3) return null;

            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            byte[] nv21 = new byte[ySize + uSize + vSize];
            yBuffer.get(nv21, 0, ySize);
            vBuffer.get(nv21, ySize, vSize);
            uBuffer.get(nv21, ySize + vSize, uSize);

            YuvImage yuvImage = new YuvImage(nv21, android.graphics.ImageFormat.NV21,
                    image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 100, out);
            byte[] imageBytes = out.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        } catch (Exception e) {
            Log.e(TAG, "图像转换失败", e);
            return null;
        }
    }

    private void toggleCamera() {
        cameraFacing = (cameraFacing == CameraSelector.LENS_FACING_FRONT) ?
                CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
        startCamera();
    }

    // ========== 运动控制 ==========

    private void toggleTracking() {
        if (isTracking) {
            stopTracking();
        } else {
            startDetectionPeriod();
        }
    }

    private void startDetectionPeriod() {
        if (!isStandardDataReady) {
            Toast.makeText(this, "请先选择标准视频", Toast.LENGTH_SHORT).show();
            return;
        }

        isWaitingForDetection = true;
        successfulDetectionCount = 0;
        startButton.setText("检测中...");

        detectionHandler.postDelayed(() -> {
            if (isWaitingForDetection && !isTracking) {
                isWaitingForDetection = false;
                startButton.setText("开始运动");
                Toast.makeText(this, "未检测到有效姿势，请确保全身在画面中", Toast.LENGTH_LONG).show();
            }
        }, DETECTION_TIMEOUT_MS);
    }

    private void startCountdown() {
        isWaitingForDetection = false;
        countdownValue = 3;
        if (countdownText != null) {
            countdownText.setVisibility(View.VISIBLE);
            countdownText.setText(String.valueOf(countdownValue));
            performCountdown();
        } else {
            startTrackingImmediately();
        }
    }

    private void performCountdown() {
        countdownHandler.postDelayed(() -> {
            countdownValue--;
            if (countdownValue > 0) {
                countdownText.setText(String.valueOf(countdownValue));
                performCountdown();
            } else {
                countdownText.setText("开始!");
                countdownHandler.postDelayed(() -> {
                    if (countdownText != null) {
                        countdownText.setVisibility(View.GONE);
                    }
                    startTrackingImmediately();
                }, 500);
            }
        }, 1000);
    }

    private void startTrackingImmediately() {
        isTracking = true;
        currentSDKCount = 0;
        currentSimilarity = 0f;

        // 启动 SDK 会话
        if (isSDKReady) {
            sdk.startSession();
        }

        runOnUiThread(() -> {
            startButton.setText("停止运动");
            if (overlayView != null) {
                overlayView.setTrackingState(true);
            }
            if (squatCountView != null && needCounting) {
                squatCountView.setVisibility(View.VISIBLE);
                squatCountView.setText(actionName + ": 0");
            }
        });

        workoutStartTime = System.currentTimeMillis();
        similarityList.clear();
        Log.d(TAG, "开始跟踪，动作类型：" + actionName);
    }

    private void stopTracking() {
        isTracking = false;
        isWaitingForDetection = false;

        // 停止 SDK 会话
        if (isSDKReady) {
            sdk.stopSession();
        }

        runOnUiThread(() -> {
            startButton.setText("开始运动");
            if (overlayView != null) {
                overlayView.setTrackingState(false);
                overlayView.setDirectLandmarks(null);  // 清除骨架显示
            }
            if (countdownText != null) {
                countdownText.setVisibility(View.GONE);
            }
            if (staticHintLayout != null) {
                staticHintLayout.setVisibility(View.GONE);
            }
        });

        Log.d(TAG, "停止跟踪，动作类型：" + actionName);
    }

    // ========== 辅助方法 ==========

    private void showStaticHint() {
        if (staticHintLayout == null) return;
        runOnUiThread(() -> {
            staticHintLayout.setVisibility(View.VISIBLE);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (staticHintLayout != null) {
                    staticHintLayout.setVisibility(View.GONE);
                }
            }, 2000);
        });
    }

    private void generateReport() {
        if (similarityList.isEmpty()) {
            Toast.makeText(this, "暂无运动数据", Toast.LENGTH_SHORT).show();
            return;
        }
        long duration = System.currentTimeMillis() - workoutStartTime;
        float avgSimilarity = (float) similarityList.stream().mapToDouble(f -> f).average().orElse(0);

        Toast.makeText(this, String.format("运动完成! 时长:%d秒 平均相似度:%.1f%% 完成次数:%d",
                duration / 1000, avgSimilarity * 100, currentSDKCount), Toast.LENGTH_LONG).show();
    }

    private void copyAssetsToCache() {
        // 已在其他地方调用
    }

    // ========== FitnessSDKListener 回调实现 ==========

    @Override
    public void onInitSuccess(String sdkVersion) {
        runOnUiThread(() -> {
            isSDKReady = true;
            Toast.makeText(this, "SDK初始化成功 v" + sdkVersion, Toast.LENGTH_SHORT).show();

            // 启动摄像头
            startCamera();

            // 如果标准数据已加载，加载到 SDK
            if (isStandardDataReady && !standardVideoData.isEmpty()) {
                loadActionToSDK();
            }
        });
    }

    @Override
    public void onInitError(int code, String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, "SDK初始化失败: " + message, Toast.LENGTH_LONG).show();
            Log.e(TAG, "SDK初始化失败: code=" + code + ", msg=" + message);
        });
    }


    @Override
    public void onSkeletonFrame(Bitmap frame, SkeletonFrame skeletonFrame, float similarity) {
        // ...

    }

    /**
     * 更新 OverlayView 显示骨架
     */
    private void updateOverlayWithSkeleton(SkeletonFrame skeletonFrame) {
        if (overlayView == null || skeletonFrame == null) return;

        List<NormalizedLandmark> landmarks = new ArrayList<>();
        for (Keypoint kp : skeletonFrame.getKeypoints()) {
            landmarks.add(NormalizedLandmark.create(
                    kp.getX(), kp.getY(), kp.getZ(),
                    java.util.Optional.of(kp.getVisibility()),
                    java.util.Optional.of(1.0f)
            ));
        }
        overlayView.setDirectLandmarks(landmarks);
        overlayView.setSquatInfo(currentSDKCount, currentSimilarity);
    }

    /**
     * 更新角度显示
     */
    private void updateAngleDisplay(SkeletonFrame frame) {
        if (angleView == null) return;

        if ("拳击".equals(actionName)) {
            angleView.setText(String.format(
                    "左肘: %.0f° | 右肘: %.0f°\n左肩: %.0f° | 右肩: %.0f°",
                    frame.getLeftElbowAngle(), frame.getRightElbowAngle(),
                    frame.getLeftShoulderAngle(), frame.getRightShoulderAngle()));
        } else {
            angleView.setText(String.format(
                    "膝: %.0f°\n髋: %.0f°",
                    frame.getKneeAngle(), frame.getHipAngle()));
        }

        // 根据相似度设置颜色
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
            Log.d(TAG, "动作开始: " + actionId);
            if (staticHintLayout != null) {
                staticHintLayout.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void onActionSuccess(int score, long durationMs, String actionId, int completedCount) {
        currentSDKCount = completedCount;
        currentSimilarity = score / 100f;

        // 记录相似度数据
        similarityList.add(currentSimilarity);

        runOnUiThread(() -> {
            if (squatCountView != null && needCounting) {
                squatCountView.setText(actionName + ": " + completedCount);
            }
            if (overlayView != null) {
                overlayView.setSquatInfo(completedCount, currentSimilarity);
            }
            Log.d(TAG, String.format("动作完成! 得分:%d 次数:%d 相似度:%.1f%%",
                    score, completedCount, currentSimilarity * 100));
        });
    }

    @Override
    public void onActionError(int score, ErrorType errorType, Bitmap errorFrame, Bitmap correctFrameRef) {
        runOnUiThread(() -> {
            Log.w(TAG, "动作错误: " + errorType.getDescription() + " 得分:" + score);
            showStaticHint();
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

    // ========== 生命周期 ==========

    @Override
    protected void onResume() {
        super.onResume();
        if (isSDKReady && !isTracking) {
            startCamera();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            player.setPlayWhenReady(false);
        }
        if (isTracking) {
            stopTracking();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (sdk != null) {
            sdk.release();
        }
        if (player != null) {
            player.release();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (backgroundExecutor != null) {
            ((java.util.concurrent.ExecutorService) backgroundExecutor).shutdown();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                new Handler().postDelayed(() -> initSDK(), 500);
            } else {
                Toast.makeText(this, "需要相机权限", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VIDEO_PICK && resultCode == RESULT_OK && data != null) {
            customStandardVideoUri = data.getData();
            Toast.makeText(this, "自定义视频选择成功", Toast.LENGTH_SHORT).show();
            // TODO: 处理自定义视频
        }
    }
}