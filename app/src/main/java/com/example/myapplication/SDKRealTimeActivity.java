package com.example.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
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
import com.example.fitness.sdk.model.ErrorType;
import com.example.fitness.sdk.model.Keypoint;
import com.example.fitness.sdk.model.NormalizedLandmark;
import com.example.fitness.sdk.model.SkeletonFrame;
import com.example.fitness.sdk.ui.PoseOverlayView;
import com.example.poselandmarker.R;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mediapipe.tasks.core.BaseOptions;
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SDKRealTimeActivity extends AppCompatActivity implements FitnessSDKListener {

    private static final String TAG = "SDKRealTimeActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private static final int REQUEST_VIDEO_PICK = 1002;
    public static final String EXTRA_ACTION = "action_strategy";

    // ========== SDK 相关 ==========
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

    // ========== 标准动作数据 ==========
    private final List<FrameData> standardVideoData = new ArrayList<>();
    private float standardVideoFps = 30f;

    // ========== MediaPipe 姿态检测 ==========
    private PoseLandmarker poseLandmarker;
    private ExecutorService backgroundExecutor;

    // ========== UI组件 ==========
    private PreviewView viewFinder;
    private PixelPerfectOverlayView videoOverlayView;
    private ImageButton switchCameraButton;
    private Button startButton;
    private Button selectVideoButton;
    private TextView squatCountView;
    private TextView angleView;
    private LinearLayout staticHintLayout;
    private TextView countdownText;
    private PlayerView exoPlayerView;
    private LinearLayout videoControlPanel;
    private Button btnPlaybackSpeed;
    private PoseOverlayView poseOverlayView;  // SDK骨架视图
    private Handler videoUpdateHandler;
    private Runnable videoUpdateRunnable;
    private TextView tvSimilarity;

    // ========== 摄像头相关 ==========
    private ProcessCameraProvider cameraProvider;
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;
    private int currentRotation = 90;
    private int currentImageWidth = 640;
    private int currentImageHeight = 480;

    // ========== 视频播放器 ==========
    private SimpleExoPlayer player;

    // ========== 倒计时相关 ==========
    private Handler countdownHandler;
    private int countdownValue = 3;
    private boolean isWaitingForDetection = false;
    private int successfulDetectionCount = 0;
    private Handler detectionHandler;

    // ========== 其他 ==========
    private static final long DETECTION_TIMEOUT_MS = 30000;
    private static final int DETECTION_REQUIRED_COUNT = 30;
    private String[] REQUIRED_PERMISSIONS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        actionStrategy = (ActionStrategy) getIntent().getSerializableExtra(EXTRA_ACTION);
        if (actionStrategy == null) {
            actionStrategy = new SquatStrategy();
        }
        actionName = actionStrategy.getActionName();
        needCounting = actionStrategy.hasCounter();

        setContentView(R.layout.activity_realtime);

        backgroundExecutor = Executors.newSingleThreadExecutor();

        setupToolbar();
        initializeViews();
        initPermissions();

        countdownHandler = new Handler(Looper.getMainLooper());
        detectionHandler = new Handler(Looper.getMainLooper());
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
        videoOverlayView = findViewById(R.id.videoOverlayView);
        switchCameraButton = findViewById(R.id.switch_camera_button);
        startButton = findViewById(R.id.start_button);
        selectVideoButton = findViewById(R.id.select_video_button);
        squatCountView = findViewById(R.id.squatCount);
        angleView = findViewById(R.id.angleView);
        staticHintLayout = findViewById(R.id.staticHintLayout);
        countdownText = findViewById(R.id.countdownText);
        exoPlayerView = findViewById(R.id.exoPlayerView);
        videoControlPanel = findViewById(R.id.videoControlPanel);
        btnPlaybackSpeed = findViewById(R.id.btn_playback_speed);
        poseOverlayView = findViewById(R.id.poseOverlayView);
        tvSimilarity = findViewById(R.id.tvSimilarity);
        // 隐藏原有的 OverlayView
        View oldOverlay = findViewById(R.id.overlay);
        if (oldOverlay != null) {
            oldOverlay.setVisibility(View.GONE);
        }

        // 根据动作类型控制计数面板显示
        LinearLayout lytContainer = findViewById(R.id.lyt_container);
        if (lytContainer != null) {
            if ("拳击".equals(actionName)) {
                lytContainer.setVisibility(View.GONE);
            } else {
                lytContainer.setVisibility(View.VISIBLE);
            }
        }

        startButton.setOnClickListener(v -> toggleTracking());
        selectVideoButton.setOnClickListener(v -> showVideoOptionsDialog());
        switchCameraButton.setOnClickListener(v -> toggleCamera());

        if (btnPlaybackSpeed != null) {
            btnPlaybackSpeed.setOnClickListener(v -> showSpeedPickerDialog());
        }
        if (exoPlayerView != null) {
            exoPlayerView.setOnClickListener(v -> toggleControlPanel());
        }

        setupAngleDisplay();
        startButton.setEnabled(false);
        videoUpdateHandler = new Handler(Looper.getMainLooper());
        videoUpdateRunnable = () -> {
            if (player != null && isStandardDataReady) {
                updateVideoOverlay();
            }
            if (videoUpdateHandler != null) {
                videoUpdateHandler.postDelayed(videoUpdateRunnable, 33);
            }
        };
        REQUIRED_PERMISSIONS = getRequiredPermissions();
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
        showProgress("正在加载标准视频...", 0);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                List<FrameData> data = PoseCache.read(video.cacheName, this);
                if (data == null || data.isEmpty()) {
                    Toast.makeText(this, "官方标准数据缺失", Toast.LENGTH_LONG).show();
                    selectVideoButton.setEnabled(true);
                    hideProgress();
                    return;
                }

                standardVideoData.clear();
                standardVideoData.addAll(data);
                isStandardDataReady = true;
                standardVideoFps = 30f;

                int resId = getResources().getIdentifier(video.videoResName, "raw", getPackageName());
                if (resId != 0) {
                    Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + resId);
                    setupVideoPlayer(videoUri);
                }

                if (isSDKReady) {
                    loadActionToSDK();
                }

                selectVideoButton.setEnabled(true);
                startButton.setEnabled(true);
                hideProgress();
                Toast.makeText(this, "加载完成: " + data.size() + "帧", Toast.LENGTH_SHORT).show();

            } catch (Exception e) {
                Log.e(TAG, "加载失败: " + e.getMessage(), e);
                selectVideoButton.setEnabled(true);
                hideProgress();
                Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
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
                    Log.d(TAG, "视频准备就绪");
                    updateVideoDisplayRect();
                    updateVideoOverlay();
                    startVideoUpdateTimer();  // 启动定时器
                }
            }
            @Override
            public void onVideoSizeChanged(com.google.android.exoplayer2.video.VideoSize size) {
                updateVideoDisplayRect();
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                                Player.PositionInfo newPosition, int reason) {
                // 播放位置变化时更新骨架
                updateVideoOverlay();
            }
        });
    }
    private void updateVideoOverlay() {
        if (!isStandardDataReady || standardVideoData.isEmpty() || player == null) return;

        long positionMs = player.getCurrentPosition();
        int totalFrames = standardVideoData.size();
        // 根据播放位置计算当前帧
        int currentFrame = (int) ((positionMs * standardVideoFps) / 1000) % totalFrames;

        //Log.d(TAG, "updateVideoOverlay: position=" + positionMs + "ms, frame=" + currentFrame + "/" + totalFrames);

        if (currentFrame < standardVideoData.size()) {
            FrameData frameData = standardVideoData.get(currentFrame);
            if (frameData.landmarks != null && videoOverlayView != null) {
                videoOverlayView.setStandardLandmarks(frameData.landmarks);
            }
        }
    }
    private void updateVideoDisplayRect() {
        if (exoPlayerView == null || player == null) {
            Log.w(TAG, "updateVideoDisplayRect: exoPlayerView 或 player 为 null");
            return;
        }

        com.google.android.exoplayer2.video.VideoSize vs = player.getVideoSize();
        if (vs.width == 0 || vs.height == 0) {
            Log.w(TAG, "updateVideoDisplayRect: 视频尺寸为 0");
            return;
        }

        int vw = exoPlayerView.getWidth();
        int vh = exoPlayerView.getHeight();
        if (vw == 0 || vh == 0) {
            Log.w(TAG, "updateVideoDisplayRect: exoPlayerView 尺寸为 0");
            return;
        }

        float videoAspect = (float) vs.width / vs.height;
        float viewAspect = (float) vw / vh;

        RectF rect = new RectF();
        if (videoAspect > viewAspect) {
            // 视频更宽，上下留黑边
            float displayHeight = vw / videoAspect;
            float top = (vh - displayHeight) * 0.5f;
            rect.set(0, top, vw, top + displayHeight);
        } else {
            // 视频更高，左右留黑边
            float displayWidth = vh * videoAspect;
            float left = (vw - displayWidth) * 0.5f;
            rect.set(left, 0, left + displayWidth, vh);
        }

        Log.d(TAG, "视频显示区域: " + rect);

        if (videoOverlayView != null) {
            videoOverlayView.setVideoDisplayRect(rect);
        } else {
            Log.w(TAG, "videoOverlayView 为 null");
        }
    }
    private void showProgress(String message, int progress) {
        LinearLayout progressOverlay = findViewById(R.id.videoProgressOverlay);
        TextView progressText = findViewById(R.id.videoProgressText);
        ProgressBar progressBar = findViewById(R.id.videoProgressBar);
        TextView progressPercent = findViewById(R.id.videoProgressPercent);

        if (progressOverlay != null) {
            progressOverlay.setVisibility(View.VISIBLE);
            if (progressText != null) progressText.setText(message);
            if (progressBar != null) progressBar.setProgress(progress);
            if (progressPercent != null) progressPercent.setText(progress + "%");
        }
    }

    private void hideProgress() {
        LinearLayout progressOverlay = findViewById(R.id.videoProgressOverlay);
        if (progressOverlay != null) {
            progressOverlay.setVisibility(View.GONE);
        }
    }

    // ========== SDK 集成 ==========

    private void loadActionToSDK() {
        if (!isSDKReady || standardVideoData.isEmpty()) return;

        List<SkeletonFrame> frames = new ArrayList<>();
        for (FrameData data : standardVideoData) {
            frames.add(convertToSkeletonFrame(data));
        }

        // 打印角度范围，验证数据
        if (!frames.isEmpty()) {
            float minKnee = 180, maxKnee = 0;
            for (SkeletonFrame f : frames) {
                if (f.getKneeAngle() > 0) {
                    minKnee = Math.min(minKnee, f.getKneeAngle());
                    maxKnee = Math.max(maxKnee, f.getKneeAngle());
                }
            }
            Log.d(TAG, "标准动作膝角范围: " + minKnee + "° ~ " + maxKnee + "°");
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

    private SkeletonFrame convertToSkeletonFrame(FrameData frameData) {
        List<Keypoint> keypoints = new ArrayList<>();
        if (frameData.landmarks != null) {
            for (int i = 0; i < frameData.landmarks.size() && i < 33; i++) {
                com.google.mediapipe.tasks.components.containers.NormalizedLandmark lm = frameData.landmarks.get(i);
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

    private void initSDK() {
        Log.d(TAG, "初始化 FitnessSDK...");
        sdk = FitnessSDK.getInstance();

        SDKConfig config = SDKConfig.builder()
                .setCallbackFps(30)
                .setMinDetectionConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setActionTimeoutMs(30000)
                .build();

        sdk.init(this, config, this);
    }

    // ========== MediaPipe 姿态检测初始化 ==========

    private void initPoseLandmarker() {
        backgroundExecutor.execute(() -> {
            try {
                String modelPath = "pose_landmarker.task";
                BaseOptions baseOptions = BaseOptions.builder()
                        .setModelAssetPath(modelPath)
                        .setDelegate(Delegate.CPU)
                        .build();

                // VIDEO 模式，不需要 ResultListener
                PoseLandmarker.PoseLandmarkerOptions options =
                        PoseLandmarker.PoseLandmarkerOptions.builder()
                                .setBaseOptions(baseOptions)
                                .setRunningMode(RunningMode.VIDEO)
                                .build();

                poseLandmarker = PoseLandmarker.createFromOptions(this, options);
                Log.d(TAG, "MediaPipe 姿态检测器初始化成功 (VIDEO模式)");

            } catch (Exception e) {
                Log.e(TAG, "MediaPipe 初始化失败: " + e.getMessage(), e);
                runOnUiThread(() -> Toast.makeText(this, "姿态检测初始化失败", Toast.LENGTH_LONG).show());
            }
        });
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

        } catch (Exception e) {
            Log.e(TAG, "绑定相机失败: " + e.getMessage(), e);
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy image) {
        Log.d(TAG, "analyzeImage 被调用, isTracking=" + isTracking + ", poseLandmarker=" + poseLandmarker);

        if (!isTracking || poseLandmarker == null) {
            image.close();
            return;
        }

        try {
            Bitmap bitmap = imageProxyToBitmap(image);
            Log.d(TAG, "bitmap 转换结果: " + (bitmap != null));

            if (bitmap != null) {
                currentImageWidth = bitmap.getWidth();
                currentImageHeight = bitmap.getHeight();

                long timestampMs = System.currentTimeMillis();
                com.google.mediapipe.framework.image.MPImage mpImage = convertBitmapToMPImage(bitmap);
                PoseLandmarkerResult result = poseLandmarker.detectForVideo(mpImage, timestampMs);

                Log.d(TAG, "detectForVideo 完成, result=" + (result != null) + ", landmarks=" +
                        (result != null && !result.landmarks().isEmpty() ? result.landmarks().get(0).size() : 0));

                if (result != null && !result.landmarks().isEmpty()) {
                    processPoseResult(result);
                }

                bitmap.recycle();
            }
        } catch (Exception e) {
            Log.e(TAG, "分析图像失败: " + e.getMessage(), e);
        } finally {
            image.close();
        }
    }
    private void processPoseResult(PoseLandmarkerResult result) {
        Log.d(TAG, "processPoseResult 被调用, isSDKReady=" + isSDKReady + ", isTracking=" + isTracking);

        if (result == null || result.landmarks().isEmpty()) {
            Log.w(TAG, "result 为空或无 landmarks");
            return;
        }

        List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> mediapipeLandmarks = result.landmarks().get(0);
        Log.d(TAG, "mediapipeLandmarks 数量: " + mediapipeLandmarks.size());

        // 转换为 SDK 的 NormalizedLandmark
        List<com.example.fitness.sdk.model.NormalizedLandmark> sdkLandmarks = new ArrayList<>();
        for (com.google.mediapipe.tasks.components.containers.NormalizedLandmark lm : mediapipeLandmarks) {
            float visibility = lm.visibility().isPresent() ? lm.visibility().get() : 0f;
            float presence = lm.presence().isPresent() ? lm.presence().get() : 0f;
            sdkLandmarks.add(new com.example.fitness.sdk.model.NormalizedLandmark(
                    lm.x(), lm.y(), lm.z(), visibility, presence
            ));
        }

        // 调用 SDK
        if (isSDKReady && isTracking && sdk != null) {
            Log.d(TAG, "调用 sdk.updateLandmarks, 关键点数量=" + sdkLandmarks.size());
            sdk.updateLandmarks(sdkLandmarks, currentImageWidth, currentImageHeight);
        } else {
            Log.w(TAG, "跳过 SDK 调用: isSDKReady=" + isSDKReady + ", isTracking=" + isTracking + ", sdk=" + sdk);
        }
    }
    private com.google.mediapipe.framework.image.MPImage convertBitmapToMPImage(Bitmap bitmap) {
        return new com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build();
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
            startTrackingImmediately();
        }
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

        if (isSDKReady) {
            sdk.startSession();
        }

        // 从头播放标准视频
        if (player != null) {
            player.seekTo(0);
            player.setPlayWhenReady(true);
        }

        runOnUiThread(() -> {
            startButton.setText("停止运动");
            startButton.setTextColor(Color.WHITE);
            if (squatCountView != null && needCounting) {
                squatCountView.setVisibility(View.VISIBLE);
                squatCountView.setText(actionName + ": 0");
            }
        });

        Log.d(TAG, "开始跟踪，动作类型：" + actionName);
    }

    private void stopTracking() {
        isTracking = false;
        isWaitingForDetection = false;

        if (isSDKReady) {
            sdk.stopSession();
        }

        runOnUiThread(() -> {
            startButton.setText("开始运动");
            if (poseOverlayView != null) {
                poseOverlayView.clear();
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

    // ========== FitnessSDKListener 回调 ==========

    @Override
    public void onInitSuccess(String sdkVersion) {
        runOnUiThread(() -> {
            isSDKReady = true;
            Toast.makeText(this, "SDK初始化成功 v" + sdkVersion, Toast.LENGTH_SHORT).show();

            // 绑定 SDK 骨架视图
            sdk.attachOverlayView(poseOverlayView);

            // 初始化 MediaPipe 姿态检测
            initPoseLandmarker();

            // 启动摄像头
            startCamera();

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
        if (!isTracking) return;

        // 更新当前相似度
        currentSimilarity = similarity;

        // 更新 UI 显示
        updateSimilarityDisplay(currentSimilarity);

        if (angleView != null && skeletonFrame != null) {
            updateAngleDisplay(skeletonFrame);
        }
    }
    private void updateSimilarityDisplay(float similarity) {
        if (tvSimilarity != null) {
            tvSimilarity.setText(String.format("相似度: %.0f%%", similarity * 100));
            tvSimilarity.setTextColor(getSimilarityColor(similarity));
        }
    }
    private int getSimilarityColor(float similarity) {
        if (similarity >= 0.8f) {
            return Color.GREEN;
        } else if (similarity >= 0.6f) {
            return Color.YELLOW;
        } else {
            return Color.RED;
        }
    }
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

        runOnUiThread(() -> {
            updateSimilarityDisplay(currentSimilarity);
            if (squatCountView != null && needCounting) {
                squatCountView.setText(actionName + ": " + completedCount);
            }

        });
    }

    @Override
    public void onActionError(int score, ErrorType errorType, Bitmap errorFrame, Bitmap correctFrameRef) {
        runOnUiThread(() -> {
            Log.w(TAG, "动作错误: " + errorType.getDescription());
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
        if (poseLandmarker != null) {
            poseLandmarker.close();
        }
        if (player != null) {
            player.release();
        }
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
        if (backgroundExecutor != null) {
            backgroundExecutor.shutdown();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VIDEO_PICK && resultCode == RESULT_OK && data != null) {
            Toast.makeText(this, "自定义视频选择成功", Toast.LENGTH_SHORT).show();
        }
    }
}