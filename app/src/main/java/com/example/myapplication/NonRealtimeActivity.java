package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
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
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.poselandmarker.R;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.ProgressiveMediaSource;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSource;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class NonRealtimeActivity extends AppCompatActivity {
    private List<Float> upKnee = new ArrayList<>(), upHip = new ArrayList<>();
    private List<Float> stKnee = new ArrayList<>(), stHip = new ArrayList<>();
    private List<Long> times = new ArrayList<>();

    private static final String TAG = "NonRealtimeActivity";
    private static final int REQUEST_UPLOAD_VIDEO_PICK = 1001;
    private static final int REQUEST_STANDARD_VIDEO_PICK = 1002;
    private static final String EXTRA_ACTION = "action_strategy";

    private static final float MIN_VISIBILITY_THRESHOLD = 0.3f;

    // 拳击专用权重
    private static final float BOXING_ELBOW_WEIGHT = 0.5f;
    private static final float BOXING_SHOULDER_WEIGHT = 0.35f;
    private static final float BOXING_KNEE_WEIGHT = 0.1f;
    private static final float BOXING_HIP_WEIGHT = 0.05f;

    // UI Components
    private Button uploadVideoButton;
    private Button selectStandardVideoButton;
    private Button startCompareButton;
    private PlayerView uploadPlayerView;
    private PlayerView standardPlayerView;
    private PixelPerfectOverlayView uploadOverlay;
    private PixelPerfectOverlayView standardOverlayView;
    private TextView similarityView;
    private TextView similarityBreakdown;
    private TextView uploadAngleView;
    private TextView standardAngleView;
    private FloatingActionButton btnGenerateReport;
    private FloatingActionButton btnReset;

    // Video Players
    private SimpleExoPlayer uploadPlayer;
    private SimpleExoPlayer standardPlayer;

    // Video Processing
    private VideoProcessor uploadVideoProcessor;
    private VideoProcessor standardVideoProcessor;
    private Executor backgroundExecutor;

    // Progress Views
    private LinearLayout uploadProgressOverlay;
    private ProgressBar uploadProgressBar;
    private TextView uploadProgressText;
    private TextView uploadProgressPercent;
    private LinearLayout standardProgressOverlay;
    private ProgressBar standardProgressBar;
    private TextView standardProgressText;
    private TextView standardProgressPercent;
    private LinearLayout uploadPlaceholder;
    private LinearLayout standardPlaceholder;
    // 在类的成员变量区域添加
    private List<Float> uploadLeftElbowAngles = new ArrayList<>();
    private List<Float> uploadRightElbowAngles = new ArrayList<>();
    private List<Float> uploadLeftShoulderAngles = new ArrayList<>();
    private List<Float> uploadRightShoulderAngles = new ArrayList<>();
    private List<Float> standardLeftElbowAngles = new ArrayList<>();
    private List<Float> standardRightElbowAngles = new ArrayList<>();
    private List<Float> standardLeftShoulderAngles = new ArrayList<>();
    private List<Float> standardRightShoulderAngles = new ArrayList<>();
    // Data
    private Uri uploadVideoUri = null;
    private Uri standardVideoUri = null;
    private final List<FrameData> uploadVideoData = new ArrayList<>();
    private final List<FrameData> standardVideoData = new ArrayList<>();
    private boolean isUploadVideoReady = false;
    private boolean isStandardVideoReady = false;
    private boolean isComparing = false;
    private boolean isProcessingUpload = false;
    private boolean isProcessingStandard = false;
    private ActionStrategy action;

    // Frame Rate Tracking
    private float uploadVideoFps = 30f;
    private float standardVideoFps = 30f;

    private long uploadVideoDurationMs = 0;
    private long standardVideoDurationMs = 0;

    // Analysis
    private float overallSimilarity = 0f;
    private int totalComparedFrames = 0;
    private int validComparedFrames = 0;
    private float averageKneeAngleDiff = 0f;
    private float averageHipAngleDiff = 0f;

    private VideoFeatures uploadFeatures;
    private VideoFeatures standardFeatures;
    // 添加成员变量
    private int[] optimalMatchingPath;  // 存储最优匹配路径
    private List<Float> frameSimilarities = new ArrayList<>();
    private List<Float> uploadKneeAngles = new ArrayList<>();
    private List<Float> uploadHipAngles = new ArrayList<>();
    private List<Float> uploadElbowAngles = new ArrayList<>();
    private List<Float> uploadShoulderAngles = new ArrayList<>();
    private List<Float> standardKneeAngles = new ArrayList<>();
    private List<Float> standardHipAngles = new ArrayList<>();
    private List<Float> standardElbowAngles = new ArrayList<>();
    private List<Float> standardShoulderAngles = new ArrayList<>();
    private List<Long> frameTimestamps = new ArrayList<>();

    private WorkoutDataService workoutDataService;
    private long compareStartTime = 0;
    private String actionType = "比对运动";

    private BroadcastReceiver workoutDataReceiver;
    private boolean hasGeneratedReport = false;

    private static final int[] REQUIRED_LANDMARK_INDICES = {
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
    };

    //  视频特征类
    private static class VideoFeatures {
        float minKnee = 180f, maxKnee = 0f, kneeRange = 0f;
        float minHip = 180f, maxHip = 0f, hipRange = 0f;
        float minElbow = 180f, maxElbow = 0f, elbowRange = 0f;
        float minShoulder = 180f, maxShoulder = 0f, shoulderRange = 0f;
        float avgKnee = 0f, avgHip = 0f, avgElbow = 0f, avgShoulder = 0f;
        int totalFrames = 0;
        int validFrames = 0;

        @Override
        public String toString() {
            return String.format("膝:%.1f°~%.1f°(Δ%.1f) 髋:%.1f°~%.1f°(Δ%.1f) 肘:%.1f°~%.1f°(Δ%.1f) 肩:%.1f°~%.1f°(Δ%.1f) 有效帧:%d/%d",
                    minKnee, maxKnee, kneeRange, minHip, maxHip, hipRange,
                    minElbow, maxElbow, elbowRange, minShoulder, maxShoulder, shoulderRange,
                    validFrames, totalFrames);
        }
    }

    private static class BestMatch {
        float similarity = 0f;
        int standardIndex = -1;
        FrameData standardFrame = null;
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        action = (ActionStrategy) getIntent().getSerializableExtra(EXTRA_ACTION);
        if (action == null) {
            action = new SquatStrategy();
        }
        actionType = action.getActionName();

        setContentView(R.layout.activity_non_realtime2);

        workoutDataService = new WorkoutDataService(this);
        copyAssetsToCache();
        setupToolbar();
        initializeViews();
        initializeProgressViews();

        backgroundExecutor = Executors.newSingleThreadExecutor();
        uploadVideoProcessor = new VideoProcessor(this);
        standardVideoProcessor = new VideoProcessor(this);

        setupClickListeners();
        registerWorkoutDataReceiver();
        updateUIState();
    }

    private void setupToolbar() {
        Toolbar toolbar = findViewById(R.id.back_toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerWorkoutDataReceiver() {
        workoutDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if ("ACTION_WORKOUT_DATA_UPDATED".equals(intent.getAction())) {
                    Log.d(TAG, "收到运动数据更新广播");
                }
            }
        };

        IntentFilter filter = new IntentFilter();
        filter.addAction("ACTION_WORKOUT_DATA_UPDATED");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(workoutDataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(workoutDataReceiver, filter);
        }
    }


    private void initializeViews() {
        uploadVideoButton = findViewById(R.id.upload_video_button);
        selectStandardVideoButton = findViewById(R.id.select_standard_video_button);
        startCompareButton = findViewById(R.id.start_compare_button);
        btnGenerateReport = findViewById(R.id.btn_generate_report);


        uploadPlayerView = findViewById(R.id.uploadPlayerView);
        standardPlayerView = findViewById(R.id.standardPlayerView);
        uploadOverlay = findViewById(R.id.uploadOverlay);
        standardOverlayView = findViewById(R.id.standardOverlayView);

        similarityView = findViewById(R.id.similarityView);
        similarityBreakdown = findViewById(R.id.similarityBreakdown);


        uploadPlaceholder = findViewById(R.id.uploadPlaceholder);
        standardPlaceholder = findViewById(R.id.standardPlaceholder);

        if (btnGenerateReport != null) {
            btnGenerateReport.setVisibility(View.GONE);
            btnGenerateReport.setOnClickListener(v -> generateAndSaveReport());
        }
        if (btnReset != null) {
            btnReset.setVisibility(View.GONE);
            btnReset.setOnClickListener(v -> resetComparison());
        }

        setupAngleDisplays();
    }

    private void initializeProgressViews() {
        uploadProgressOverlay = findViewById(R.id.uploadProgressOverlay);
        uploadProgressBar = findViewById(R.id.uploadProgressBar);
        uploadProgressText = findViewById(R.id.uploadProgressText);
        uploadProgressPercent = findViewById(R.id.uploadProgressPercent);

        standardProgressOverlay = findViewById(R.id.standardProgressOverlay);
        standardProgressBar = findViewById(R.id.standardProgressBar);
        standardProgressText = findViewById(R.id.standardProgressText);
        standardProgressPercent = findViewById(R.id.standardProgressPercent);
    }

    private void setupAngleDisplays() {
        if (uploadAngleView != null) {
            uploadAngleView.setBackgroundColor(Color.argb(128, 0, 0, 0));
            uploadAngleView.setTextColor(Color.WHITE);
            uploadAngleView.setTextSize(12);
            uploadAngleView.setPadding(8, 4, 8, 4);
            uploadAngleView.setText("上传视频\n膝: --°\n髋: --°");
        }

        if (standardAngleView != null) {
            standardAngleView.setBackgroundColor(Color.argb(128, 0, 0, 0));
            standardAngleView.setTextColor(Color.WHITE);
            standardAngleView.setTextSize(12);
            standardAngleView.setPadding(8, 4, 8, 4);
            standardAngleView.setText("标准视频\n膝: --°\n髋: --°");
        }
    }

    private void setupClickListeners() {
        uploadVideoButton.setOnClickListener(v -> selectUploadVideo());
        selectStandardVideoButton.setOnClickListener(v -> selectStandardVideo());
        startCompareButton.setOnClickListener(v -> startComparison());
    }


    private long getVideoDuration(Uri videoUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, videoUri);
            String durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            if (durationStr != null) {
                return Long.parseLong(durationStr);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取视频时长失败: " + e.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
            }
        }
        return 0;
    }

    private int getVideoRotation(Uri videoUri) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(this, videoUri);
            String rotationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
            if (rotationStr != null) {
                return Integer.parseInt(rotationStr);
            }
        } catch (Exception e) {
            Log.e(TAG, "获取视频旋转角度失败: " + e.getMessage());
        } finally {
            try {
                retriever.release();
            } catch (Exception e) {
            }
        }
        return 0;
    }


    private void selectUploadVideo() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        startActivityForResult(intent, REQUEST_UPLOAD_VIDEO_PICK);
    }

    private void selectStandardVideo() {
        showVideoOptionsDialog();
    }

    private void showVideoOptionsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_video_selector, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();

        dialog.setOnShowListener(d -> {
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.width = (int) (getResources().getDisplayMetrics().widthPixels * 0.85);
            params.gravity = Gravity.CENTER;
            dialog.getWindow().setAttributes(params);
        });

        RecyclerView rvOfficial = dialogView.findViewById(R.id.rv_official_videos);
        rvOfficial.setLayoutManager(new LinearLayoutManager(this));
        rvOfficial.setAdapter(new OfficialVideoAdapter(action.getOfficialVideos(), video -> {
            dialog.dismiss();
            handleDefaultVideo(video);
        }));

        dialogView.findViewById(R.id.tv_album).setOnClickListener(v -> {
            dialog.dismiss();
            selectVideoFromAlbum();
        });

        dialog.show();
    }

    private void selectVideoFromAlbum() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("video/*");
        startActivityForResult(intent, REQUEST_STANDARD_VIDEO_PICK);
    }

    private void handleDefaultVideo(ActionStrategy.OfficialVideo video) {
        showStandardProgress("正在加载官方标准视频...", 0);
        selectStandardVideoButton.setEnabled(false);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                List<FrameData> data = PoseCache.read(video.cacheName, this);
                if (data == null || data.isEmpty()) {
                    Toast.makeText(this, "官方标准数据缺失: " + video.cacheName, Toast.LENGTH_LONG).show();
                    selectStandardVideoButton.setEnabled(true);
                    hideStandardProgress();
                    return;
                }

                standardVideoData.clear();
                standardVideoData.addAll(data);
                standardVideoFps = 30f;
                isStandardVideoReady = true;
                standardFeatures = analyzeVideoFeatures(standardVideoData, "标准视频");

                int resId = getResources().getIdentifier(video.videoResName, "raw", getPackageName());
                if (resId != 0) {
                    Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + resId);
                    standardVideoUri = videoUri;
                    standardVideoDurationMs = getVideoDuration(videoUri);
                    Log.d(TAG, "标准视频时长: " + standardVideoDurationMs + "ms");

                    if (standardPlayer != null) {
                        standardPlayer.release();
                    }

                    standardPlayer = new SimpleExoPlayer.Builder(this).build();
                    standardPlayerView.setPlayer(standardPlayer);
                    standardPlayerView.setUseController(true);
                    standardPlayer.setVolume(0);

                    DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
                    ProgressiveMediaSource.Factory mediaSourceFactory = new ProgressiveMediaSource.Factory(dataSourceFactory);
                    MediaItem mediaItem = MediaItem.fromUri(videoUri);
                    ProgressiveMediaSource mediaSource = mediaSourceFactory.createMediaSource(mediaItem);

                    standardPlayer.setMediaSource(mediaSource);
                    standardPlayer.prepare();
                    standardPlayer.setPlayWhenReady(false);

                    if (standardPlaceholder != null) {
                        standardPlaceholder.setVisibility(View.GONE);
                    }

                    standardPlayer.addListener(new Player.Listener() {
                        @Override
                        public void onPlaybackStateChanged(int state) {
                            if (state == Player.STATE_READY) {
                                Log.d(TAG, "官方标准视频播放器准备就绪");
                                updateStandardVideoOverlay();
                            }
                        }

                        @Override
                        public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                                            Player.PositionInfo newPosition,
                                                            int reason) {
                            updateStandardVideoOverlay();
                        }
                    });

                    hideStandardProgress();
                    selectStandardVideoButton.setEnabled(true);
                    updateUIState();

                    Toast.makeText(this, video.displayName + " 加载完成 - " + data.size() + "帧数据", Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Log.e(TAG, "加载官方标准视频失败: " + e.getMessage(), e);
                hideStandardProgress();
                selectStandardVideoButton.setEnabled(true);
                Toast.makeText(this, "加载失败: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null) {
            if (requestCode == REQUEST_UPLOAD_VIDEO_PICK) {
                resetUploadVideoData();
                uploadVideoUri = data.getData();
                uploadVideoDurationMs = getVideoDuration(uploadVideoUri);
                Log.d(TAG, "上传视频时长: " + uploadVideoDurationMs + "ms");
                initializeUploadVideoPlayer();
                processUploadVideoWithCache();
            } else if (requestCode == REQUEST_STANDARD_VIDEO_PICK) {
                resetStandardVideoData();
                standardVideoUri = data.getData();
                standardVideoDurationMs = getVideoDuration(standardVideoUri);
                Log.d(TAG, "标准视频时长: " + standardVideoDurationMs + "ms");
                initializeStandardVideoPlayer();
                processStandardVideoWithCache();
            }
        }
    }

    private void resetUploadVideoData() {
        Log.d(TAG, "重置上传视频数据");
        uploadVideoData.clear();
        upKnee.clear();
        upHip.clear();
        times.clear();
        isUploadVideoReady = false;
        isProcessingUpload = false;
        uploadFeatures = null;
        hasGeneratedReport = false;

        runOnUiThread(() -> {
            similarityView.setText("相似度: --%");
            if (similarityBreakdown != null) {
                similarityBreakdown.setText("等待比对结果...");
            }
            if (uploadAngleView != null) {
                uploadAngleView.setText("上传视频\n膝: --°\n髋: --°");
            }
            uploadOverlay.setStandardLandmarks(null);
            if (btnGenerateReport != null) {
                btnGenerateReport.setVisibility(View.GONE);
            }
            if (btnReset != null) {
                btnReset.setVisibility(View.GONE);
            }
            updateUIState();
        });
    }

    private void resetStandardVideoData() {
        Log.d(TAG, "重置标准视频数据");
        standardVideoData.clear();
        stKnee.clear();
        stHip.clear();
        isStandardVideoReady = false;
        isProcessingStandard = false;
        standardFeatures = null;
        hasGeneratedReport = false;

        runOnUiThread(() -> {
            similarityView.setText("相似度: --%");
            if (similarityBreakdown != null) {
                similarityBreakdown.setText("等待比对结果...");
            }
            if (standardAngleView != null) {
                standardAngleView.setText("标准视频\n膝: --°\n髋: --°");
            }
            standardOverlayView.setStandardLandmarks(null);
            if (btnGenerateReport != null) {
                btnGenerateReport.setVisibility(View.GONE);
            }
            if (btnReset != null) {
                btnReset.setVisibility(View.GONE);
            }
            updateUIState();
        });
    }

    //  视频播放器初始化
    private void initializeUploadVideoPlayer() {
        if (uploadPlayer != null) {
            uploadPlayer.release();
        }

        uploadPlayer = new SimpleExoPlayer.Builder(this).build();
        uploadPlayerView.setPlayer(uploadPlayer);
        uploadPlayerView.setUseController(true);

        uploadPlayer.setVolume(0);

        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
        ProgressiveMediaSource.Factory mediaSourceFactory = new ProgressiveMediaSource.Factory(dataSourceFactory);
        MediaItem mediaItem = MediaItem.fromUri(uploadVideoUri);
        ProgressiveMediaSource mediaSource = mediaSourceFactory.createMediaSource(mediaItem);

        uploadPlayer.setMediaSource(mediaSource);
        uploadPlayer.prepare();
        uploadPlayer.setPlayWhenReady(false);

        if (uploadPlaceholder != null) {
            uploadPlaceholder.setVisibility(View.GONE);
        }

        uploadPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && isUploadVideoReady) {
                    updateUploadVideoOverlay();
                }
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                                Player.PositionInfo newPosition,
                                                int reason) {
                if (isUploadVideoReady) {
                    updateUploadVideoOverlay();
                }
            }
        });
    }

    private void initializeStandardVideoPlayer() {
        if (standardPlayer != null) {
            standardPlayer.release();
        }

        standardPlayer = new SimpleExoPlayer.Builder(this).build();
        standardPlayerView.setPlayer(standardPlayer);
        standardPlayerView.setUseController(true);
        standardPlayer.setVolume(0);
        DataSource.Factory dataSourceFactory = new DefaultDataSource.Factory(this);
        ProgressiveMediaSource.Factory mediaSourceFactory = new ProgressiveMediaSource.Factory(dataSourceFactory);
        MediaItem mediaItem = MediaItem.fromUri(standardVideoUri);
        ProgressiveMediaSource mediaSource = mediaSourceFactory.createMediaSource(mediaItem);

        standardPlayer.setMediaSource(mediaSource);
        standardPlayer.prepare();
        standardPlayer.setPlayWhenReady(false);

        if (standardPlaceholder != null) {
            standardPlaceholder.setVisibility(View.GONE);
        }

        standardPlayer.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY && isStandardVideoReady) {
                    updateStandardVideoOverlay();
                }
            }

            @Override
            public void onPositionDiscontinuity(Player.PositionInfo oldPosition,
                                                Player.PositionInfo newPosition,
                                                int reason) {
                if (isStandardVideoReady) {
                    updateStandardVideoOverlay();
                }
            }
        });
    }

    // 视频处理方法（带缓存检查）
    private void processUploadVideoWithCache() {
        if (uploadVideoProcessor == null) {
            uploadVideoProcessor = new VideoProcessor(this);
        }

        isProcessingUpload = true;
        runOnUiThread(() -> {
            uploadVideoButton.setEnabled(false);
            updateUIState();
        });

        showUploadProgress("正在检查服务器缓存...", 0);

        VideoCacheManager.getInstance(this).checkVideoCache(uploadVideoUri, new VideoCacheManager.CacheCheckCallback() {
            @Override
            public void onCacheFound(List<FrameData> cachedFrames, String sha256) {
                runOnUiThread(() -> {
                    hideUploadProgress();
                    isProcessingUpload = false;

                    uploadVideoData.clear();
                    uploadVideoData.addAll(cachedFrames);
                    uploadVideoFps = 30f;
                    isUploadVideoReady = true;

                    int rotation = getVideoRotation(uploadVideoUri);
                    uploadOverlay.setRotationDegrees(rotation);

                    uploadFeatures = analyzeVideoFeatures(uploadVideoData, "上传视频(缓存)");

                    int validFrames = 0;
                    for (FrameData frame : uploadVideoData) {
                        if (frame.hasValidPose) validFrames++;
                    }

                    updateUIState();
                    updateUploadVideoOverlay();

                    Toast.makeText(NonRealtimeActivity.this,
                            String.format("使用服务器缓存 - %d/%d帧有效", validFrames, uploadVideoData.size()),
                            Toast.LENGTH_SHORT).show();

                    Log.d(TAG, "使用服务器缓存数据，帧数: " + cachedFrames.size());
                });
            }

            @Override
            public void onCacheNotFound(String sha256) {
                runOnUiThread(() -> processUploadVideoInternal(sha256));
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "缓存检查失败: " + error);
                runOnUiThread(() -> processUploadVideoInternal(null));
            }
        });
    }

    private void processUploadVideoInternal(String sha256) {
        showUploadProgress("正在本地分析视频...", 0);

        uploadVideoProcessor.processVideo(uploadVideoUri, new VideoProcessor.VideoProcessingCallback() {
            @Override
            public void onProgress(int progress) {
                showUploadProgress("正在本地分析视频...", progress);
            }

            @Override
            public void onComplete(List<VideoProcessor.VideoFrameData> frameDataList, int rotation, float frameRate) {
                runOnUiThread(() -> {
                    hideUploadProgress();
                    isProcessingUpload = false;

                    uploadVideoData.clear();
                    for (VideoProcessor.VideoFrameData videoFrame : frameDataList) {
                        uploadVideoData.add(new FrameData(videoFrame));
                    }
                    uploadVideoFps = frameRate;
                    isUploadVideoReady = true;

                    uploadOverlay.setRotationDegrees(rotation);
                    uploadFeatures = analyzeVideoFeatures(uploadVideoData, "上传视频");

                    updateUIState();

                    int validFrames = 0;
                    for (FrameData frame : uploadVideoData) {
                        if (frame.hasValidPose) validFrames++;
                    }

                    Toast.makeText(NonRealtimeActivity.this,
                            String.format("本地分析完成 - %d/%d帧有效", validFrames, uploadVideoData.size()),
                            Toast.LENGTH_SHORT).show();

                    updateUploadVideoOverlay();

                    if (sha256 != null && !frameDataList.isEmpty()) {
                        uploadAnalysisResultToServer(sha256, frameDataList);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    hideUploadProgress();
                    isProcessingUpload = false;
                    uploadVideoButton.setEnabled(true);
                    updateUIState();
                    Toast.makeText(NonRealtimeActivity.this, "视频分析失败: " + error, Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "上传视频处理错误: " + error);
                });
            }
        });
    }

    private void processStandardVideoWithCache() {
        isProcessingStandard = true;
        runOnUiThread(() -> {
            selectStandardVideoButton.setEnabled(false);
            updateUIState();
        });

        showStandardProgress("正在检查服务器缓存...", 0);

        VideoCacheManager.getInstance(this).checkVideoCache(standardVideoUri, new VideoCacheManager.CacheCheckCallback() {
            @Override
            public void onCacheFound(List<FrameData> cachedFrames, String sha256) {
                runOnUiThread(() -> {
                    hideStandardProgress();
                    isProcessingStandard = false;

                    standardVideoData.clear();
                    standardVideoData.addAll(cachedFrames);
                    standardVideoFps = 30f;
                    isStandardVideoReady = true;

                    int rotation = getVideoRotation(standardVideoUri);
                    standardOverlayView.setRotationDegrees(rotation);

                    standardFeatures = analyzeVideoFeatures(standardVideoData, "标准视频(缓存)");

                    updateUIState();
                    updateStandardVideoOverlay();

                    Toast.makeText(NonRealtimeActivity.this,
                            "使用服务器缓存的标准视频数据", Toast.LENGTH_SHORT).show();

                    Log.d(TAG, "标准视频使用服务器缓存，帧数: " + cachedFrames.size());
                });
            }

            @Override
            public void onCacheNotFound(String sha256) {
                runOnUiThread(() -> processStandardVideoInternal(sha256));
            }

            @Override
            public void onError(String error) {
                Log.w(TAG, "标准视频缓存检查失败: " + error);
                runOnUiThread(() -> processStandardVideoInternal(null));
            }
        });
    }

    private void processStandardVideoInternal(String sha256) {
        showStandardProgress("正在本地分析视频...", 0);

        standardVideoProcessor.processVideo(standardVideoUri, new VideoProcessor.VideoProcessingCallback() {
            @Override
            public void onProgress(int progress) {
                showStandardProgress("正在本地分析视频...", progress);
            }

            @Override
            public void onComplete(List<VideoProcessor.VideoFrameData> frameDataList, int rotation, float frameRate) {
                runOnUiThread(() -> {
                    hideStandardProgress();
                    isProcessingStandard = false;

                    standardVideoData.clear();
                    for (VideoProcessor.VideoFrameData videoFrame : frameDataList) {
                        standardVideoData.add(new FrameData(videoFrame));
                    }
                    standardVideoFps = frameRate;
                    isStandardVideoReady = true;

                    standardOverlayView.setRotationDegrees(rotation);
                    standardFeatures = analyzeVideoFeatures(standardVideoData, "标准视频");

                    updateUIState();
                    updateStandardVideoOverlay();

                    if (sha256 != null && !frameDataList.isEmpty()) {
                        VideoCacheManager.getInstance(NonRealtimeActivity.this)
                                .uploadAnalysisResult(sha256, standardVideoUri, frameDataList, null);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    hideStandardProgress();
                    isProcessingStandard = false;
                    selectStandardVideoButton.setEnabled(true);
                    updateUIState();
                    Toast.makeText(NonRealtimeActivity.this, "标准视频处理失败: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void uploadAnalysisResultToServer(String sha256, List<VideoProcessor.VideoFrameData> frameDataList) {
        Log.d(TAG, "正在上传分析结果到服务器...");

        VideoCacheManager.getInstance(this).uploadAnalysisResult(sha256, uploadVideoUri, frameDataList,
                new VideoCacheManager.UploadCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "分析结果上传成功，下次可使用缓存");
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "分析结果上传失败: " + error);
                    }
                });
    }

    // 覆盖层更新方法
    private void updateUploadVideoOverlay() {
        if (!isUploadVideoReady || uploadVideoData.isEmpty() || uploadPlayer == null) return;

        long positionMs = uploadPlayer.getCurrentPosition();
        int totalFrames = uploadVideoData.size();
        int currentFrame = (int) ((positionMs * uploadVideoFps) / 1000) % totalFrames;

        if (currentFrame >= 0 && currentFrame < uploadVideoData.size()) {
            FrameData frameData = uploadVideoData.get(currentFrame);
            if (frameData.landmarks != null) {
                uploadOverlay.setStandardLandmarks(frameData.landmarks);
                updateUploadAngleDisplay(frameData);
            }
        }
    }

    private void updateStandardVideoOverlay() {
        if (!isStandardVideoReady || standardVideoData.isEmpty() || standardPlayer == null) return;

        long positionMs = standardPlayer.getCurrentPosition();
        int totalFrames = standardVideoData.size();
        int currentFrame = (int) ((positionMs * standardVideoFps) / 1000) % totalFrames;

        if (currentFrame >= 0 && currentFrame < standardVideoData.size()) {
            FrameData frameData = standardVideoData.get(currentFrame);
            if (frameData.landmarks != null) {
                standardOverlayView.setStandardLandmarks(frameData.landmarks);
                updateStandardAngleDisplay(frameData);
            }
        }
    }

    private void updateUploadAngleDisplay(FrameData frame) {
        if (uploadAngleView != null) {
            String kneeText = (frame.kneeAngle < 0 || Float.isNaN(frame.kneeAngle)) ? "--" : String.format("%.1f", frame.kneeAngle);
            String hipText = (frame.hipAngle < 0 || Float.isNaN(frame.hipAngle)) ? "--" : String.format("%.1f", frame.hipAngle);

            if (isBoxingAction()) {
                String elbowText = (frame.elbowAngle < 0 || Float.isNaN(frame.elbowAngle)) ? "--" : String.format("%.1f", frame.elbowAngle);
                String shoulderText = (frame.shoulderAngle < 0 || Float.isNaN(frame.shoulderAngle)) ? "--" : String.format("%.1f", frame.shoulderAngle);
                uploadAngleView.setText(String.format("上传视频\n肘: %s°\n肩: %s°", elbowText, shoulderText));
            } else {
                uploadAngleView.setText(String.format("上传视频\n膝: %s°\n髋: %s°", kneeText, hipText));
            }
        }
    }

    private void updateStandardAngleDisplay(FrameData frame) {
        if (standardAngleView != null) {
            String kneeText = (frame.kneeAngle < 0 || Float.isNaN(frame.kneeAngle)) ? "--" : String.format("%.1f", frame.kneeAngle);
            String hipText = (frame.hipAngle < 0 || Float.isNaN(frame.hipAngle)) ? "--" : String.format("%.1f", frame.hipAngle);

            if (isBoxingAction()) {
                String elbowText = (frame.elbowAngle < 0 || Float.isNaN(frame.elbowAngle)) ? "--" : String.format("%.1f", frame.elbowAngle);
                String shoulderText = (frame.shoulderAngle < 0 || Float.isNaN(frame.shoulderAngle)) ? "--" : String.format("%.1f", frame.shoulderAngle);
                standardAngleView.setText(String.format("标准视频\n肘: %s°\n肩: %s°", elbowText, shoulderText));
            } else {
                standardAngleView.setText(String.format("标准视频\n膝: %s°\n髋: %s°", kneeText, hipText));
            }
        }
    }

    private boolean isBoxingAction() {
        if (action == null) return false;
        String name = action.getActionName();
        return name.contains("拳击") || name.contains("直拳") || name.contains("摆拳") || name.contains("勾拳");
    }

    // 进度显示方法
    private void showUploadProgress(String message, int progress) {
        runOnUiThread(() -> {
            if (uploadProgressOverlay != null) {
                uploadProgressOverlay.setVisibility(View.VISIBLE);
                if (uploadProgressText != null) uploadProgressText.setText(message);
                if (uploadProgressBar != null) uploadProgressBar.setProgress(progress);
                if (uploadProgressPercent != null) uploadProgressPercent.setText(progress + "%");
            }
        });
    }

    private void hideUploadProgress() {
        runOnUiThread(() -> {
            if (uploadProgressOverlay != null) {
                uploadProgressOverlay.setVisibility(View.GONE);
            }
        });
    }

    private void showStandardProgress(String message, int progress) {
        runOnUiThread(() -> {
            if (standardProgressOverlay != null) {
                standardProgressOverlay.setVisibility(View.VISIBLE);
                if (standardProgressText != null) standardProgressText.setText(message);
                if (standardProgressBar != null) standardProgressBar.setProgress(progress);
                if (standardProgressPercent != null) standardProgressPercent.setText(progress + "%");
            }
        });
    }

    private void hideStandardProgress() {
        runOnUiThread(() -> {
            if (standardProgressOverlay != null) {
                standardProgressOverlay.setVisibility(View.GONE);
            }
        });
    }

    // 视频特征分析
    private VideoFeatures analyzeVideoFeatures(List<FrameData> videoData, String videoName) {
        VideoFeatures features = new VideoFeatures();
        features.totalFrames = videoData.size();

        List<Float> kneeAngles = new ArrayList<>();
        List<Float> hipAngles = new ArrayList<>();
        List<Float> elbowAngles = new ArrayList<>();
        List<Float> shoulderAngles = new ArrayList<>();

        for (FrameData frame : videoData) {
            if (frame.hasValidPose) {
                if (frame.kneeAngle > 0) {
                    kneeAngles.add(frame.kneeAngle);
                    features.validFrames++;
                }
                if (frame.hipAngle > 0) hipAngles.add(frame.hipAngle);
                if (frame.elbowAngle > 0) elbowAngles.add(frame.elbowAngle);
                if (frame.shoulderAngle > 0) shoulderAngles.add(frame.shoulderAngle);
            }
        }

        if (!kneeAngles.isEmpty()) {
            features.minKnee = Collections.min(kneeAngles);
            features.maxKnee = Collections.max(kneeAngles);
            features.kneeRange = features.maxKnee - features.minKnee;
            features.avgKnee = calculateAverage(kneeAngles);
        }

        if (!hipAngles.isEmpty()) {
            features.minHip = Collections.min(hipAngles);
            features.maxHip = Collections.max(hipAngles);
            features.hipRange = features.maxHip - features.minHip;
            features.avgHip = calculateAverage(hipAngles);
        }

        if (!elbowAngles.isEmpty()) {
            features.minElbow = Collections.min(elbowAngles);
            features.maxElbow = Collections.max(elbowAngles);
            features.elbowRange = features.maxElbow - features.minElbow;
            features.avgElbow = calculateAverage(elbowAngles);
        }

        if (!shoulderAngles.isEmpty()) {
            features.minShoulder = Collections.min(shoulderAngles);
            features.maxShoulder = Collections.max(shoulderAngles);
            features.shoulderRange = features.maxShoulder - features.minShoulder;
            features.avgShoulder = calculateAverage(shoulderAngles);
        }

        Log.d(TAG, String.format("%s特征分析 - %s", videoName, features.toString()));
        return features;
    }

    private float calculateAverage(List<Float> values) {
        if (values.isEmpty()) return 0f;
        float sum = 0f;
        for (float value : values) {
            sum += value;
        }
        return sum / values.size();
    }

    //  姿态验证方法
    private boolean areRequiredJointsVisible(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() < 33) return false;

        int visibleCount = 0;
        for (int index : REQUIRED_LANDMARK_INDICES) {
            if (index < landmarks.size()) {
                NormalizedLandmark landmark = landmarks.get(index);
                if (landmark != null && landmark.visibility().isPresent() &&
                        landmark.visibility().get() >= MIN_VISIBILITY_THRESHOLD) {
                    visibleCount++;
                }
            }
        }
        return visibleCount >= 6;
    }

    //UI状态更新
    private void updateUIState() {
        runOnUiThread(() -> {
            boolean bothVideosReady = isUploadVideoReady && isStandardVideoReady;
            startCompareButton.setEnabled(bothVideosReady && !isComparing);

            if (bothVideosReady) {
                startCompareButton.setText(isComparing ? "正在分析..." : "开始智能比对");
                startCompareButton.setBackgroundColor(isComparing ? Color.parseColor("#FF9800") : Color.parseColor("#4CAF50"));
                startCompareButton.setTextColor(Color.WHITE);
            } else {
                startCompareButton.setBackgroundColor(Color.parseColor("#CCCCCC"));
                startCompareButton.setTextColor(Color.BLACK);
                if (isUploadVideoReady && !isStandardVideoReady) {
                    startCompareButton.setText("等待标准视频");
                } else if (!isUploadVideoReady && isStandardVideoReady) {
                    startCompareButton.setText("等待上传视频");
                } else {
                    startCompareButton.setText("请选择视频");
                }
            }

            uploadVideoButton.setEnabled(!isProcessingUpload && !isComparing);
            selectStandardVideoButton.setEnabled(!isProcessingStandard && !isComparing);
        });
    }

    // 相似度计算方法
    /**
     * 根据动作类型获取各关节的权重
     * @return float[4] = [elbowWeight, shoulderWeight, kneeWeight, hipWeight]
     */
    private float[] getActionWeights() {


        String actionName = action.getActionName();

        // 拳击：肘关节和肩关节权重大
        if (isBoxingAction()) {
            return new float[]{0.4f, 0.1f, 0f, 0f};
        }

        // 罗马尼亚硬拉：髋关节主导
        if (actionName.contains("硬拉") || actionName.contains("罗马尼亚")) {
            return new float[]{0.0f, 0.0f, 0.3f, 0.7f};
        }

        // 高抬腿：髋膝并重
        if (actionName.contains("高抬腿")) {
            return new float[]{0.0f, 0.f, 0.7f, 0.3f};
        }

        // 深蹲/默认：膝关节稍重要
        return new float[]{0.0f, 0.0f, 0.6f, 0.4f};
    }

    private float calculateBoxingSimilarity(FrameData uploadFrame, FrameData standardFrame) {
        if (uploadFrame == null || standardFrame == null) return 0f;
        if (!uploadFrame.hasValidPose || !standardFrame.hasValidPose) return 0f;

        float[] weights = getActionWeights();
        float elbowWeight = weights[0];
        float shoulderWeight = weights[1];

        // 四个角度独立计算
        float leftElbowSim = calculateBoxingAngleSimilarity(
                Math.abs(uploadFrame.leftElbowAngle - standardFrame.leftElbowAngle));
        float rightElbowSim = calculateBoxingAngleSimilarity(
                Math.abs(uploadFrame.rightElbowAngle - standardFrame.rightElbowAngle));
        float leftShoulderSim = calculateBoxingAngleSimilarity(
                Math.abs(uploadFrame.leftShoulderAngle - standardFrame.leftShoulderAngle));
        float rightShoulderSim = calculateBoxingAngleSimilarity(
                Math.abs(uploadFrame.rightShoulderAngle - standardFrame.rightShoulderAngle));


        float totalSimilarity =
                weights[0] * leftElbowSim + weights[0] * rightElbowSim +
                        weights[1] * leftShoulderSim + weights[1] * rightShoulderSim;

        return Math.min(1.0f, Math.max(0f, totalSimilarity));
    }

    /**
     * 拳击角度相似度评分函数
     */
//    private float calculateBoxingAngleSimilarity(float angleDiff) {
//        if (angleDiff <= 8f) {
//            return 1.0f;
//        } else if (angleDiff <= 15f) {
//            return 0.95f - (angleDiff - 8f) * 0.007f;
//        } else if (angleDiff <= 25f) {
//            return 0.85f - (angleDiff - 15f) * 0.01f;
//        } else if (angleDiff <= 40f) {
//            return 0.7f - (angleDiff - 25f) * 0.012f;
//        } else {
//            return Math.max(0.1f, 0.5f - (angleDiff - 40f) * 0.008f);
//        }
//    }
    private float calculateBoxingAngleSimilarity(float angleDiff) {
        // 自相似度优化：极小差异直接给100%
        if (angleDiff <= 5f) {
            return 1.0f;
        }

        if (angleDiff <= 12f) {
            // 线性插值，0.5度到12度之间平滑过渡
            return 1.0f - (angleDiff - 5f) * (0.05f / 7f);  // 5-12度缓慢下降
        } else if (angleDiff <= 25f) {
            return 0.95f - (angleDiff - 12f) * 0.0077f;
        } else if (angleDiff <= 40f) {
            return 0.85f - (angleDiff - 25f) * 0.0133f;
        } else if (angleDiff <= 60f) {
            return 0.65f - (angleDiff - 40f) * 0.0125f;
        } else {
            return Math.max(0.1f, 0.40f - (angleDiff - 60f) * 0.01f);
        }
    }

    private float calculateEnhancedSimilarity(float uploadKnee, float uploadHip,
                                              float standardKnee, float standardHip) {
        if (uploadKnee < 0 || standardKnee < 0 || uploadHip < 0 || standardHip < 0) {
            return 0f;
        }

        float kneeDiff = Math.abs(uploadKnee - standardKnee);
        float hipDiff = Math.abs(uploadHip - standardHip);
        float kneeSimilarity = calculateRelaxedSimilarityScore(kneeDiff);
        float hipSimilarity = calculateRelaxedSimilarityScore(hipDiff);
        float[] weights = getActionWeights();
        float kneeWeight = weights[2];
        float hipWeight = weights[3];

        if (kneeDiff > 60f) {
            kneeWeight *= 0.7f;
            hipWeight = 1.0f - kneeWeight;
        }
        if (hipDiff > 60f) {
            hipWeight *= 0.7f;
            kneeWeight = 1.0f - hipWeight;
        }

        float basicSimilarity = kneeWeight * kneeSimilarity + hipWeight * hipSimilarity;
        return basicSimilarity ;
    }

    /**
     * 根据动作类型计算相似度
     */
    private float calculateSimilarityByAction(FrameData uploadFrame, FrameData standardFrame) {
        if (action == null) {
            return calculateEnhancedSimilarity(uploadFrame.kneeAngle, uploadFrame.hipAngle,
                    standardFrame.kneeAngle, standardFrame.hipAngle);
        }

        if (isBoxingAction()) {
            return calculateBoxingSimilarity(uploadFrame, standardFrame);
        }

        return calculateEnhancedSimilarity(uploadFrame.kneeAngle, uploadFrame.hipAngle,
                standardFrame.kneeAngle, standardFrame.hipAngle);
    }



    private float calculateRelaxedSimilarityScore(float angleDiff) {
        if (angleDiff <= 10f) {
            return 1.0f;
        } else if (angleDiff <= 20f) {
            return 0.95f - (angleDiff - 10f) * 0.005f;
        } else if (angleDiff <= 30f) {
            return 0.9f - (angleDiff - 20f) * 0.01f;
        } else if (angleDiff <= 45f) {
            return 0.8f - (angleDiff - 30f) * 0.013f;
        } else if (angleDiff <= 60f) {
            return 0.6f - (angleDiff - 45f) * 0.01f;
        } else {
            return Math.max(0.1f, 0.4f - (angleDiff - 60f) * 0.005f);
        }
    }

    //  比对方法
    private void startComparison() {
        if (!isUploadVideoReady || !isStandardVideoReady) {
            Toast.makeText(this, "请先选择并处理两个视频", Toast.LENGTH_SHORT).show();
            return;
        }
        // 完全重置所有比对相关数据
        resetComparisonData();
        hasGeneratedReport = false;

        runOnUiThread(() -> {
            if (btnGenerateReport != null) {
                btnGenerateReport.setVisibility(View.GONE);
            }
            if (btnReset != null) {
                btnReset.setVisibility(View.GONE);
            }
        });

        isComparing = true;
        compareStartTime = System.currentTimeMillis();
        startCompareButton.setEnabled(false);
        updateUIState();

        frameSimilarities.clear();
        uploadKneeAngles.clear();
        uploadHipAngles.clear();
        uploadElbowAngles.clear();
        uploadShoulderAngles.clear();
        standardKneeAngles.clear();
        standardHipAngles.clear();
        standardElbowAngles.clear();
        standardShoulderAngles.clear();
        frameTimestamps.clear();

        showUploadProgress("正在进行滑动窗口匹配分析...", 0);

        backgroundExecutor.execute(() -> {
            try {
                overallSimilarity = calculateSlidingWindowSimilarity();

                runOnUiThread(() -> {
                    hideUploadProgress();
                    isComparing = false;
                    startCompareButton.setEnabled(true);
                    updateUIState();
                    displayComparisonResults();

                    if (btnGenerateReport != null && validComparedFrames > 0) {
                        btnGenerateReport.setVisibility(View.VISIBLE);
                    }
                    if (btnReset != null) {
                        btnReset.setVisibility(View.VISIBLE);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    hideUploadProgress();
                    isComparing = false;
                    startCompareButton.setEnabled(true);
                    updateUIState();
                    Toast.makeText(NonRealtimeActivity.this, "分析过程中出现错误: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "改进分析错误: " + e.getMessage(), e);
                });
            }
        });
    }

    private void resetComparisonData() {
        // 重置计数变量
        totalComparedFrames = 0;
        validComparedFrames = 0;
        averageKneeAngleDiff = 0f;
        averageHipAngleDiff = 0f;
        overallSimilarity = 0f;

        // 清空所有列表
        frameSimilarities.clear();
        uploadKneeAngles.clear();
        uploadHipAngles.clear();
        uploadElbowAngles.clear();
        uploadShoulderAngles.clear();
        standardKneeAngles.clear();
        standardHipAngles.clear();
        standardElbowAngles.clear();
        standardShoulderAngles.clear();
        frameTimestamps.clear();

        hasGeneratedReport = false;
    }


    private float calculateIntelligentAlignedSimilarity() {
        if (uploadVideoData.isEmpty() || standardVideoData.isEmpty()) {
            return 0f;
        }

        // 1. 构建相似度矩阵
        float[][] similarityMatrix = new float[uploadVideoData.size()][standardVideoData.size()];

        for (int i = 0; i < uploadVideoData.size(); i++) {
            FrameData uploadFrame = uploadVideoData.get(i);
            if (!uploadFrame.hasValidPose) continue;

            for (int j = 0; j < standardVideoData.size(); j++) {
                FrameData standardFrame = standardVideoData.get(j);
                if (!standardFrame.hasValidPose) continue;

                similarityMatrix[i][j] = calculateSimilarityByAction(uploadFrame, standardFrame);
            }

            // 显示进度
            if (i % 50 == 0) {
                final int progress = (i * 100) / uploadVideoData.size();
                runOnUiThread(() -> showUploadProgress("计算相似度矩阵...", progress));
            }
        }

        // 2. 使用DTW找到全局最优路径
        optimalMatchingPath = findOptimalPath(similarityMatrix);

        // 3. 根据最优路径计算平均相似度
        float totalSimilarity = 0f;
        validComparedFrames = 0;

        for (int i = 0; i < uploadVideoData.size(); i++) {
            if (optimalMatchingPath[i] >= 0) {
                totalSimilarity += similarityMatrix[i][optimalMatchingPath[i]];
                validComparedFrames++;

                // 记录数据用于报告
                FrameData uploadFrame = uploadVideoData.get(i);
                FrameData standardFrame = standardVideoData.get(optimalMatchingPath[i]);

                frameSimilarities.add(similarityMatrix[i][optimalMatchingPath[i]]);
                uploadKneeAngles.add(uploadFrame.kneeAngle);
                uploadHipAngles.add(uploadFrame.hipAngle);
                standardKneeAngles.add(standardFrame.kneeAngle);
                standardHipAngles.add(standardFrame.hipAngle);
            }
        }

        totalComparedFrames = uploadVideoData.size();
        return validComparedFrames > 0 ? totalSimilarity / validComparedFrames : 0f;
    }

    // DTW寻找最优路径（允许时间伸缩）
    private int[] findOptimalPath(float[][] simMatrix) {
        int n = simMatrix.length;      // 上传视频帧数
        int m = n > 0 ? simMatrix[0].length : 0;  // 标准视频帧数

        if (n == 0 || m == 0) return new int[0];

        // 使用距离（1-相似度）进行DTW
        float[][] cost = new float[n][m];

        // 初始化
        cost[0][0] = 1 - simMatrix[0][0];
        for (int i = 1; i < n; i++) {
            cost[i][0] = cost[i-1][0] + (1 - simMatrix[i][0]);
        }
        for (int j = 1; j < m; j++) {
            cost[0][j] = cost[0][j-1] + (1 - simMatrix[0][j]);
        }

        // 动态规划找最小代价路径
        for (int i = 1; i < n; i++) {
            for (int j = 1; j < m; j++) {
                float minPrev = Math.min(cost[i-1][j],
                        Math.min(cost[i][j-1], cost[i-1][j-1]));
                cost[i][j] = (1 - simMatrix[i][j]) + minPrev;
            }
        }

        // 回溯找到最优路径
        int[] path = new int[n];
        Arrays.fill(path, -1);

        int i = n - 1, j = m - 1;
        while (i >= 0 && j >= 0) {
            path[i] = j;

            if (i == 0 && j == 0) break;

            if (i == 0) {
                j--;
            } else if (j == 0) {
                i--;
            } else {
                float minPrev = Math.min(cost[i-1][j],
                        Math.min(cost[i][j-1], cost[i-1][j-1]));
                if (minPrev == cost[i-1][j-1]) {
                    i--;
                    j--;
                } else if (minPrev == cost[i-1][j]) {
                    i--;
                } else {
                    j--;
                }
            }
        }

        return path;
    }

    private float calculateSlidingWindowSimilarity() {
        float totalSimilarity = 0f;
        int windowSize = Math.max(30, Math.min(standardVideoData.size() / 2, 150));

        for (int uploadIndex = 0; uploadIndex < uploadVideoData.size(); uploadIndex++) {
            FrameData uploadFrame = uploadVideoData.get(uploadIndex);

            if (!uploadFrame.hasValidPose || !areRequiredJointsVisible(uploadFrame.landmarks)) {
                continue;
            }

            totalComparedFrames++;

            BestMatch bestMatch = findBestMatchInWindow(uploadFrame, uploadIndex, windowSize);

            if (bestMatch != null && bestMatch.similarity > 0.1f) {
                totalSimilarity += bestMatch.similarity;
                validComparedFrames++;
                updateAngleDifferenceStats(uploadFrame, bestMatch.standardFrame);
                updateBoxingAngleStats(uploadFrame, bestMatch.standardFrame);

                frameSimilarities.add(bestMatch.similarity);
                uploadKneeAngles.add(uploadFrame.kneeAngle);
                uploadHipAngles.add(uploadFrame.hipAngle);
                uploadElbowAngles.add(uploadFrame.elbowAngle);
                uploadShoulderAngles.add(uploadFrame.shoulderAngle);
                standardKneeAngles.add(bestMatch.standardFrame.kneeAngle);
                standardHipAngles.add(bestMatch.standardFrame.hipAngle);
                standardElbowAngles.add(bestMatch.standardFrame.elbowAngle);
                standardShoulderAngles.add(bestMatch.standardFrame.shoulderAngle);
                frameTimestamps.add((long) uploadIndex * 33);
            }

            if (uploadIndex % 10 == 0) {
                final int progress = (uploadIndex * 100) / uploadVideoData.size();
                runOnUiThread(() -> showUploadProgress("正在进行滑动窗口匹配...", progress));
            }
        }

        return validComparedFrames > 0 ? totalSimilarity / validComparedFrames : 0f;
    }

    private void updateBoxingAngleStats(FrameData uploadFrame, FrameData standardFrame) {
        // 拳击角度统计已在 updateAngleDifferenceStats 中处理
    }

    private BestMatch findBestMatchInWindow(FrameData uploadFrame, int uploadIndex, int windowSize) {
        BestMatch bestMatch = new BestMatch();

        int expectedStandardIndex = (int) ((float) uploadIndex / uploadVideoData.size() * standardVideoData.size());
        int searchStart = Math.max(0, expectedStandardIndex - windowSize);
        int searchEnd = Math.min(standardVideoData.size() - 1, expectedStandardIndex + windowSize);

        for (int standardIndex = searchStart; standardIndex <= searchEnd; standardIndex++) {
            FrameData standardFrame = standardVideoData.get(standardIndex);

            if (!standardFrame.hasValidPose || !areRequiredJointsVisible(standardFrame.landmarks)) {
                continue;
            }

            float similarity = calculateSimilarityByAction(uploadFrame, standardFrame);

            if (similarity > bestMatch.similarity) {
                bestMatch.similarity = similarity;
                bestMatch.standardIndex = standardIndex;
                bestMatch.standardFrame = standardFrame;
            }
        }

        return bestMatch.similarity > 0 ? bestMatch : null;
    }

    private void updateAngleDifferenceStats(FrameData uploadFrame, FrameData standardFrame) {
        if (uploadFrame.kneeAngle >= 0 && standardFrame.kneeAngle >= 0) {
            averageKneeAngleDiff += Math.abs(uploadFrame.kneeAngle - standardFrame.kneeAngle);
        }
        if (uploadFrame.hipAngle >= 0 && standardFrame.hipAngle >= 0) {
            averageHipAngleDiff += Math.abs(uploadFrame.hipAngle - standardFrame.hipAngle);
        }
    }

    private void displayComparisonResults() {
        String similarityText;

        if (validComparedFrames == 0) {
            similarityText = "无法计算相似度 - 无有效比较帧";
        } else {
            similarityText = String.format("智能分析相似度: %.1f%%", overallSimilarity * 100);
            similarityText += String.format("\n有效帧: %d/%d (%.1f%%)",
                    validComparedFrames, totalComparedFrames,
                    (validComparedFrames * 100.0f / totalComparedFrames));


        }

        similarityView.setText(similarityText);

        if (similarityBreakdown != null && validComparedFrames > 0) {
            String breakdown;
            if (isBoxingAction()) {
                breakdown = String.format(
                        "肘关节相似度: %.1f%%\n肩关节相似度: %.1f%%",
                        calculateElbowSimilarity() * 100, calculateShoulderSimilarity() * 100);
            } else {
                breakdown = String.format(
                        "膝关节相似度: %.1f%%\n髋关节相似度: %.1f%%",
                        calculateKneeSimilarity() * 100, calculateHipSimilarity() * 100);
            }
//            similarityBreakdown.setText(breakdown);
        }

        if (uploadPlayer != null) uploadPlayer.setPlayWhenReady(true);
        if (standardPlayer != null) standardPlayer.setPlayWhenReady(true);

        showDetailedIntelligentAnalysisReport();
    }

    private float calculateKneeSimilarity() {
        if (uploadKneeAngles.isEmpty() || standardKneeAngles.isEmpty()) return 0f;
        float totalSim = 0f;
        int count = Math.min(uploadKneeAngles.size(), standardKneeAngles.size());
        for (int i = 0; i < count; i++) {
            float diff = Math.abs(uploadKneeAngles.get(i) - standardKneeAngles.get(i));
            totalSim += calculateRelaxedSimilarityScore(diff);
        }
        return count > 0 ? totalSim / count : 0f;
    }

    private float calculateHipSimilarity() {
        if (uploadHipAngles.isEmpty() || standardHipAngles.isEmpty()) return 0f;
        float totalSim = 0f;
        int count = Math.min(uploadHipAngles.size(), standardHipAngles.size());
        for (int i = 0; i < count; i++) {
            float diff = Math.abs(uploadHipAngles.get(i) - standardHipAngles.get(i));
            totalSim += calculateRelaxedSimilarityScore(diff);
        }
        return count > 0 ? totalSim / count : 0f;
    }

    private float calculateElbowSimilarity() {
        if (uploadLeftElbowAngles.isEmpty() || standardLeftElbowAngles.isEmpty()) return 0f;

        float totalSim = 0f;
        int count = Math.min(uploadLeftElbowAngles.size(), standardLeftElbowAngles.size());

        for (int i = 0; i < count; i++) {
            // 分别计算左右肘的角度差
            float leftDiff = Math.abs(uploadLeftElbowAngles.get(i) - standardLeftElbowAngles.get(i));
            float rightDiff = Math.abs(uploadRightElbowAngles.get(i) - standardRightElbowAngles.get(i));

            // 分别计算左右肘的相似度
            float leftSim = calculateBoxingAngleSimilarity(leftDiff);
            float rightSim = calculateBoxingAngleSimilarity(rightDiff);

            // 左右肘平均作为这一帧的肘相似度
            totalSim += (leftSim + rightSim) / 2f;
        }

        return count > 0 ? totalSim / count : 0f;
    }

    private float calculateShoulderSimilarity() {
        if (uploadShoulderAngles.isEmpty() || standardShoulderAngles.isEmpty()) return 0f;
        float totalSim = 0f;
        int count = Math.min(uploadShoulderAngles.size(), standardShoulderAngles.size());
        for (int i = 0; i < count; i++) {
            float diff = Math.abs(uploadShoulderAngles.get(i) - standardShoulderAngles.get(i));
            totalSim += calculateBoxingAngleSimilarity(diff);
        }
        return count > 0 ? totalSim / count : 0f;
    }

    private void showDetailedIntelligentAnalysisReport() {
        String summary;

        if (validComparedFrames == 0) {
            summary = "分析失败: 没有找到可比较的有效帧\n请确保视频中包含完整的人体姿态";
        } else {
            summary = String.format("智能分析完成!\n相似度: %.1f%%\n分析帧数: %d/%d (%.1f%%)",
                    overallSimilarity * 100, validComparedFrames, totalComparedFrames,
                    (validComparedFrames * 100.0f / totalComparedFrames));

            if (uploadFeatures != null && standardFeatures != null) {
                if (isBoxingAction()) {
                    summary += String.format("\n肘角度范围: %.1f° vs %.1f°",
                            uploadFeatures.elbowRange, standardFeatures.elbowRange);
                    summary += String.format("\n肩角度范围: %.1f° vs %.1f°",
                            uploadFeatures.shoulderRange, standardFeatures.shoulderRange);
                } else {
                    summary += String.format("\n膝角度范围: %.1f° vs %.1f°",
                            uploadFeatures.kneeRange, standardFeatures.kneeRange);
                    summary += String.format("\n髋角度范围: %.1f° vs %.1f°",
                            uploadFeatures.hipRange, standardFeatures.hipRange);
                }
            }

            if (averageKneeAngleDiff > 0 || averageHipAngleDiff > 0) {
                summary += String.format("\n角度差异 - 膝: %.1f° 髋: %.1f°",
                        averageKneeAngleDiff / validComparedFrames,
                        averageHipAngleDiff / validComparedFrames);
            }

            if (overallSimilarity >= 0.9f) {
                summary += "\n🎉 动作非常标准!";
            } else if (overallSimilarity >= 0.7f) {
                summary += "\n✅ 动作基本标准，有改进空间";
            } else if (overallSimilarity >= 0.5f) {
                summary += "\n⚠️ 动作需要改进";
            } else {
                summary += "\n❌ 动作差异较大，建议重新学习标准动作";
            }
        }


        Log.d(TAG, "详细分析报告:\n" + summary);
    }
    //AI辅助生成 DeepSeek-R1-0528 2025.12.14
    // 报告生成方法
    private void generateAndSaveReport() {
        if (hasGeneratedReport) {
            Toast.makeText(this, "报告已经生成过了，请勿重复生成", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!isUploadVideoReady || !isStandardVideoReady) {
            Toast.makeText(this, "请先完成视频处理再进行比对", Toast.LENGTH_SHORT).show();
            return;
        }

        if (frameSimilarities.isEmpty()) {
            Toast.makeText(this, "没有比对数据，请先进行比对", Toast.LENGTH_SHORT).show();
            return;
        }

        long durationMs = uploadVideoDurationMs;
        if (durationMs <= 0) {
            durationMs = frameSimilarities.size() * 1000L;
        }

        float avgSim = overallSimilarity;

        WorkoutRecord record = new WorkoutRecord(actionType, durationMs, avgSim, 0);

        for (int i = 0; i < frameSimilarities.size(); i++) {
            long timestampMs = (long) i * 1000;
            record.addTimestamp(timestampMs);
            record.addSimilarity(frameSimilarities.get(i));
        }

        saveDetailedReportData(record);

        workoutDataService.saveWorkoutData(record, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> {
                    Log.e(TAG, "保存运动数据失败: " + e.getMessage());
                    Toast.makeText(NonRealtimeActivity.this, "报告保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                runOnUiThread(() -> {
                    hasGeneratedReport = true;
                    Toast.makeText(NonRealtimeActivity.this, "运动报告已保存", Toast.LENGTH_SHORT).show();
                    sendWorkoutDataRefreshBroadcast();
                });
                if (response != null) {
                    response.close();
                }
            }
        });

        showReportSummaryDialog(avgSim, durationMs);
    }

    private void saveDetailedReportData(WorkoutRecord record) {
        try {
            JSONObject detailJson = new JSONObject();
            detailJson.put("actionName", actionType);
            detailJson.put("startTime", record.getStartTime());
            detailJson.put("durationMs", record.getDurationMs());
            detailJson.put("avgSimilarity", record.getAvgSimilarity());
            detailJson.put("totalFrames", frameSimilarities.size());
            detailJson.put("isNonRealtime", true);
            detailJson.put("uploadVideoDurationMs", uploadVideoDurationMs);
            detailJson.put("standardVideoDurationMs", standardVideoDurationMs);
            detailJson.put("isBoxing", isBoxingAction());

            JSONArray similarities = new JSONArray();
            JSONArray uploadKnee = new JSONArray();
            JSONArray uploadHip = new JSONArray();
            JSONArray uploadElbow = new JSONArray();
            JSONArray uploadShoulder = new JSONArray();
            JSONArray standardKnee = new JSONArray();
            JSONArray standardHip = new JSONArray();
            JSONArray standardElbow = new JSONArray();
            JSONArray standardShoulder = new JSONArray();

            for (int i = 0; i < frameSimilarities.size(); i++) {
                similarities.put(frameSimilarities.get(i));
                if (i < uploadKneeAngles.size()) uploadKnee.put(uploadKneeAngles.get(i));
                if (i < uploadHipAngles.size()) uploadHip.put(uploadHipAngles.get(i));
                if (i < uploadElbowAngles.size()) uploadElbow.put(uploadElbowAngles.get(i));
                if (i < uploadShoulderAngles.size()) uploadShoulder.put(uploadShoulderAngles.get(i));
                if (i < standardKneeAngles.size()) standardKnee.put(standardKneeAngles.get(i));
                if (i < standardHipAngles.size()) standardHip.put(standardHipAngles.get(i));
                if (i < standardElbowAngles.size()) standardElbow.put(standardElbowAngles.get(i));
                if (i < standardShoulderAngles.size()) standardShoulder.put(standardShoulderAngles.get(i));
            }

            detailJson.put("similarities", similarities);
            detailJson.put("uploadKneeAngles", uploadKnee);
            detailJson.put("uploadHipAngles", uploadHip);
            detailJson.put("uploadElbowAngles", uploadElbow);
            detailJson.put("uploadShoulderAngles", uploadShoulder);
            detailJson.put("standardKneeAngles", standardKnee);
            detailJson.put("standardHipAngles", standardHip);
            detailJson.put("standardElbowAngles", standardElbow);
            detailJson.put("standardShoulderAngles", standardShoulder);
            detailJson.put("kneeSimilarity", calculateKneeSimilarity());
            detailJson.put("hipSimilarity", calculateHipSimilarity());
            detailJson.put("elbowSimilarity", calculateElbowSimilarity());
            detailJson.put("shoulderSimilarity", calculateShoulderSimilarity());

            String fileName = "compare_detail_" + record.getStartTime() + ".json";
            File reportDir = new File(getFilesDir(), "reports");
            if (!reportDir.exists()) {
                reportDir.mkdirs();
            }
            File detailFile = new File(reportDir, fileName);

            FileOutputStream fos = new FileOutputStream(detailFile);
            fos.write(detailJson.toString().getBytes());
            fos.close();

            Log.d(TAG, "详细报告已保存: " + detailFile.getAbsolutePath());
        } catch (Exception e) {
            Log.e(TAG, "保存详细报告失败: " + e.getMessage(), e);
        }
    }

    private void showReportSummaryDialog(float avgSim, long durationMs) {
        int durationSec = (int) ((durationMs + 999) / 1000);
        int minutes = durationSec / 60;
        int seconds = durationSec % 60;
        String durationStr = minutes > 0 ?
                String.format("%d分钟%d秒", minutes, seconds) :
                String.format("%d秒", seconds);

        StringBuilder content = new StringBuilder();
        content.append("运动类型: ").append(actionType).append("\n");
        content.append("视频时长: ").append(durationStr).append("\n");
        content.append("分析帧数: ").append(frameSimilarities.size()).append("\n\n");
        content.append("=== 相似度分析 ===\n");
        content.append(String.format("整体相似度: %.1f%%\n", avgSim * 100));


        new AlertDialog.Builder(this)
                .setTitle("运动报告已保存")
                .setMessage(content.toString())
                .setPositiveButton("查看记录", (dialog, which) -> {
                    Intent intent = new Intent(this, RecordActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("确定", null)
                .show();
    }

    private void sendWorkoutDataRefreshBroadcast() {
        try {
            Intent intent = new Intent("ACTION_WORKOUT_DATA_UPDATED");
            intent.setPackage(getPackageName());
            intent.putExtra("action_name", actionType);
            intent.putExtra("duration_ms", uploadVideoDurationMs);
            intent.putExtra("similarity", overallSimilarity);
            intent.putExtra("is_non_realtime", true);
            sendBroadcast(intent);
            Log.d(TAG, "已发送运动数据更新广播");
        } catch (Exception e) {
            Log.e(TAG, "发送广播失败: " + e.getMessage());
        }
    }

    //重置方法
    private void resetComparison() {
        if (uploadPlayer != null) {
            uploadPlayer.setPlayWhenReady(false);
            uploadPlayer.seekTo(0);
        }
        if (standardPlayer != null) {
            standardPlayer.setPlayWhenReady(false);
            standardPlayer.seekTo(0);
        }

        frameSimilarities.clear();
        uploadKneeAngles.clear();
        uploadHipAngles.clear();
        uploadElbowAngles.clear();
        uploadShoulderAngles.clear();
        standardKneeAngles.clear();
        standardHipAngles.clear();
        standardElbowAngles.clear();
        standardShoulderAngles.clear();
        frameTimestamps.clear();

        totalComparedFrames = 0;
        validComparedFrames = 0;
        averageKneeAngleDiff = 0f;
        averageHipAngleDiff = 0f;
        overallSimilarity = 0f;
        hasGeneratedReport = false;

        runOnUiThread(() -> {
            similarityView.setText("相似度: --%");
            if (similarityBreakdown != null) {
                similarityBreakdown.setText("等待比对结果...");
            }

            if (btnGenerateReport != null) {
                btnGenerateReport.setVisibility(View.GONE);
            }
            if (btnReset != null) {
                btnReset.setVisibility(View.GONE);
            }

            if (uploadAngleView != null) {
                if (isBoxingAction()) {
                    uploadAngleView.setText("上传视频\n肘: --°\n肩: --°");
                } else {
                    uploadAngleView.setText("上传视频\n膝: --°\n髋: --°");
                }
            }
            if (standardAngleView != null) {
                if (isBoxingAction()) {
                    standardAngleView.setText("标准视频\n肘: --°\n肩: --°");
                } else {
                    standardAngleView.setText("标准视频\n膝: --°\n髋: --°");
                }
            }

            uploadOverlay.setStandardLandmarks(null);
            standardOverlayView.setStandardLandmarks(null);

            startCompareButton.setEnabled(true);
            startCompareButton.setText("开始比对");
            startCompareButton.setBackgroundColor(Color.parseColor("#FF5722"));

            updateUIState();
        });

        Toast.makeText(this, "已重置，可以重新比对", Toast.LENGTH_SHORT).show();
    }
    private void copyAssetsToCache() {
        String[] files = {"squat_left.bin", "squat_right.bin","boxing_jab.bin","squat_standard.bin","boxing_hook.bin","boxing_swing.bin","romanian_deadlift_standard.bin",
                "high_knees_standard.bin"};
        for (String fileName:files) {
            File cacheFile = new File(getCacheDir(), "pose_cache/" + fileName);
            if (!cacheFile.exists()) {
                try {
                    File cacheDir = new File(getCacheDir(), "pose_cache");
                    if (!cacheDir.exists()) cacheDir.mkdirs();

                    InputStream is = getAssets().open("pose/" + fileName);
                    FileOutputStream fos = new FileOutputStream(cacheFile);
                    byte[] buffer = new byte[1024];
                    int len;
                    while ((len = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                    is.close();

                } catch (IOException e) {
                    Log.e("copyAssets", "复制失败", e);
                }
            }
        }
    }
    //  生命周期销毁
    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (workoutDataReceiver != null) {
            try {
                unregisterReceiver(workoutDataReceiver);
            } catch (Exception e) {
                Log.e(TAG, "注销广播接收器失败: " + e.getMessage());
            }
        }

        if (uploadPlayer != null) {
            uploadPlayer.release();
            uploadPlayer = null;
        }
        if (standardPlayer != null) {
            standardPlayer.release();
            standardPlayer = null;
        }
        if (uploadVideoProcessor != null) {
            uploadVideoProcessor.release();
            uploadVideoProcessor = null;
        }
        if (standardVideoProcessor != null) {
            standardVideoProcessor.release();
            standardVideoProcessor = null;
        }
        if (backgroundExecutor instanceof java.util.concurrent.ExecutorService) {
            ((java.util.concurrent.ExecutorService) backgroundExecutor).shutdown();
        }
    }
}