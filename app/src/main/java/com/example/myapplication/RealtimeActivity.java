package com.example.myapplication;

import com.example.myapplication.FrameData;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.graphics.drawable.ColorDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
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
import android.view.WindowManager;
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
import androidx.camera.core.ExperimentalLensFacing;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.view.PreviewView;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.media.MediaMetadataRetriever;
import com.example.myapplication.FrameAnalysisData;
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
import com.google.mediapipe.tasks.core.Delegate;
import com.google.mediapipe.tasks.vision.core.RunningMode;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class RealtimeActivity extends AppCompatActivity implements PoseLandmarkerHelper.PoseLandmarkerListener {

    private static final String TAG = "RealtimeActivity";
    private static final int REQUEST_CODE_PERMISSIONS = 1001;
    private static final int REQUEST_VIDEO_PICK = 1002;
    private static final String ACTION_USB_PERMISSION = "com.example.myapplication.USB_PERMISSION";
    public static final String EXTRA_ACTION = "action_strategy";

    // 配置常量
    private static final int WINDOW_SIZE = 10;
    private static final int MIN_WINDOW_FOR_DTW = 5;
    private static final long POSE_TIMEOUT_MS = 1000;
    private static final float MIN_VISIBILITY_THRESHOLD = 0.2f;

    // 对齐机制常量
    private static final long ALIGNMENT_INTERVAL_MS = 10000; // 10秒对齐间隔
    private static final float ALIGNMENT_SIMILARITY_THRESHOLD = 0.6f; // 对齐相似度阈值60%
    private long lastAlignmentTime = 0;
    private boolean isInAlignmentPeriod = false;
    private Handler alignmentHandler;
    private int alignmentSearchRadius = 0;
    // 1. 添加成员变量
    private WorkoutDataService workoutDataService;
    private long workoutStartTime = 0;
    private float totalSimilaritySum = 0;
    private int similarityCount = 0;

    // 优化的DTW参数
    private static final int OPTIMIZED_WINDOW_SIZE = 8;
    private static final int DTW_BANDWIDTH = 3;
    private static final int SMOOTHING_WINDOW = 3;
    private static final int AUTO_ALIGN_WINDOW_RATIO = 4;
    private static final int FRAMES_PER_BATCH = 10;

    // 动作策略相关
    private ActionStrategy action;
    private String actionName;
    private boolean needCounting = false;

    //  连续性约束相关变量
    private LinkedList<Integer> matchOffsetHistory = new LinkedList<>();
    private static final int MATCH_HISTORY_SIZE = 5;
    private static final int MAX_OFFSET_VARIATION = 10;
    private float continuityWeight = 0.3f;

    //  帧索引管理
    private int dataFrameCounter = 0;
    private int currentFrameIndex = 0;

    //图像尺寸管理
    private int currentImageWidth = 1920;
    private int currentImageHeight = 1080;

    // 关键点索引
    private static final int[] REQUIRED_LANDMARK_INDICES = {
            PoseLandmark.LEFT_SHOULDER, PoseLandmark.RIGHT_SHOULDER,
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE,
            PoseLandmark.LEFT_ELBOW, PoseLandmark.RIGHT_ELBOW,
            PoseLandmark.LEFT_WRIST, PoseLandmark.RIGHT_WRIST
    };

    // 权限管理
    private String[] REQUIRED_PERMISSIONS;
    private List<Float> rtKneeAngles = new ArrayList<>();
    private List<Float> rtHipAngles = new ArrayList<>();
    private List<Long> rtTimes = new ArrayList<>();
    private List<Float> stdKnees = new ArrayList<>();
    private List<Float> stdHips = new ArrayList<>();
    private List<Float> rtElbowAngles = new ArrayList<>();
    private List<Float> rtShoulderAngles = new ArrayList<>();
    private List<Float> stdElbows = new ArrayList<>();
    private List<Float> stdShoulders = new ArrayList<>();

    // 拳击专用：左右关节数据
    private List<Float> rtLeftElbowAngles = new ArrayList<>();
    private List<Float> rtRightElbowAngles = new ArrayList<>();
    private List<Float> rtLeftShoulderAngles = new ArrayList<>();
    private List<Float> rtRightShoulderAngles = new ArrayList<>();
    private List<Float> stdLeftElbows = new ArrayList<>();
    private List<Float> stdRightElbows = new ArrayList<>();
    private List<Float> stdLeftShoulders = new ArrayList<>();
    private List<Float> stdRightShoulders = new ArrayList<>();

    // 拳击专用匹配历史
    private LinkedList<Integer> boxingMatchOffsetHistory = new LinkedList<>();
    private static final int BOXING_MATCH_HISTORY_SIZE = 10;
    private static final int BOXING_MAX_OFFSET_VARIATION = 3;

    // 数据记录相关
    private List<Float> realTimeAnglesHistory = new ArrayList<>();
    private List<Float> standardAnglesHistory = new ArrayList<>();
    private List<Float> similarityHistory = new ArrayList<>();
    private List<Long> frameTimestamps = new ArrayList<>();
    private int processedFrameCount = 0;
    private float similaritySmoothingFactor = 0.7f;
    private float lastSmoothedSimilarity = 0f;

    // 当前角度记录
    private float currentRealTimeKnee = 0f;
    private float currentRealTimeHip = 0f;
    private float currentRealTimeElbow = 0f;
    private float currentRealTimeShoulder = 0f;

    // 拳击专用：当前左右关节角度
    private float currentRealTimeLeftElbow = 0f;
    private float currentRealTimeRightElbow = 0f;
    private float currentRealTimeLeftShoulder = 0f;
    private float currentRealTimeRightShoulder = 0f;

    // 匹配信息
    private int matchedStandardFrameIndex = -1;
    private float matchedStandardKnee = -1f;
    private float matchedStandardHip = -1f;
    private float currentMatchSimilarity = 0f;

    // 拳击专用：匹配的左右关节角度
    private float matchedStandardLeftElbow = -1f;
    private float matchedStandardRightElbow = -1f;
    private float matchedStandardLeftShoulder = -1f;
    private float matchedStandardRightShoulder = -1f;

    //  静止状态检测相关变量
    private boolean isStaticState = false;
    private static final float STATIC_ANGLE_THRESHOLD = 10.0f;
    private static final int STATIC_FRAME_COUNT = 10;
    private int staticFrameCounter = 0;
    private float previousKneeAngle = -1f;
    private float previousHipAngle = -1f;
    private float previousElbowAngle = -1f;
    private float previousShoulderAngle = -1f;

    // 拳击专用：静止状态检测
    private float previousLeftElbowAngle = -1f;
    private float previousRightElbowAngle = -1f;
    private float previousLeftShoulderAngle = -1f;
    private float previousRightShoulderAngle = -1f;

    private static final long STATIC_TIME_THRESHOLD = 2000;
    private long staticStartTime = 0;
    private boolean hasShownStaticHint = false;

    // UI组件
    private LinearLayout staticHintLayout;
    private TextView staticHintView;
    private TextView staticHintDetail;
    private PreviewView viewFinder;
    private OverlayView overlayView;
    private ImageButton switchCameraButton;
    private Button startButton;
    private TextView squatCountView;
    private TextView angleView;
    private PlayerView exoPlayerView;
    private Button selectVideoButton;
    private PixelPerfectOverlayView videoOverlayView;

    // 可视化相关组件
    private Button toggleVisualizationButton;
    private Button toggleFrameAnalysisButton;
    private Button validateButton;
    private boolean isVisualizationEnabled = false;
    private boolean isFrameVisualizationEnabled = false;
    private List<Float[]> comparisonHistory = new ArrayList<>();

    // 帧级分析组件
    private List<FrameAnalysisData> frameAnalysisList = new ArrayList<>();
    private List<FrameAnalysisData> currentBatchData = new ArrayList<>();
    private FrameAnalysisAdapter frameAnalysisAdapter;
    private int currentDisplayFrameIndex = -1;
    private int currentFrameBatch = 0;

    // 摄像头相关
    private ProcessCameraProvider cameraProvider;
    private Executor backgroundExecutor;
    private int cameraFacing = CameraSelector.LENS_FACING_BACK;

    // USB摄像头支持
    private UsbManager usbManager;
    private boolean useUsbCamera = false;
    private boolean usbCameraAvailable = false;
    private boolean usbCameraPermissionGranted = false;
    private UsbDevice currentUsbDevice = null;

    // 倒计时相关
    private TextView countdownText;
    private Handler countdownHandler;
    private int countdownValue = 3;

    // 视频处理
    private SimpleExoPlayer player;
    private Uri customStandardVideoUri = null;
    private VideoProcessor videoProcessor;
    private float standardVideoFps = 30f;

    // 姿态检测
    private PoseLandmarkerHelper poseLandmarkerHelper;
    private SquatAnalyzer squatAnalyzer;

    // 状态管理
    private boolean isTracking = false;
    private boolean shouldCalculateAngles = false;
    private boolean isVideoProcessing = false;
    private boolean isStandardDataReady = false;
    private boolean hasRequiredJoints = false;

    // 数据存储
    private final List<FrameData> standardVideoData = new ArrayList<>();
    private final LinkedList<FrameData> poseWindow = new LinkedList<>();
    private int standardFramePointer = 0;

    // 运动分析
    private float similarityScore = 0f;
    private long trackingStartTime = 0;
    private long lastValidPoseTime = 0;

    // USB摄像头旋转角度
    private int usbCameraRotation = 0;

    // 进度显示控制
    private LinearLayout videoProgressOverlay;
    private ProgressBar videoProgressBar;
    private TextView videoProgressText;
    private TextView videoProgressPercent;

    // 30秒等待检测变量
    private static final long DETECTION_TIMEOUT_MS = 30000;
    private static final int DETECTION_REQUIRED_COUNT = 30;
    private long detectionStartTime = 0;
    private int successfulDetectionCount = 0;
    private boolean isWaitingForDetection = false;
    private Handler detectionHandler;

    // 运动数据相关
    private boolean hasMotionData = false;
    private int totalFramesAnalyzed = 0;
    private float averageSimilarity = 0f;

    // 相似度列表
    private List<Float> similarityList = new ArrayList<>();

    // 自动对齐相关
    private boolean autoAlignmentPerformed = false;
    private int bestAlignmentFrameIndex = -1;
    private float bestAlignmentSimilarity = 0f;

    // 标志位控制提示显示
    private boolean hasShownDetectionHint = false;
    private boolean hasShownPoseLostHint = false;

    // 倍速控制
    private LinearLayout videoControlPanel;
    private Button btnPlaybackSpeed;
//AI辅助生成 Kimi k2.5 2025.12.26
    // USB广播接收器
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.d(TAG, "收到USB广播: " + action);

            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null && isUsbCamera(device)) {
                            Log.d(TAG, "USB摄像头权限已获得: " + device.getDeviceName());
                            usbCameraPermissionGranted = true;
                            currentUsbDevice = device;
                            useUsbCamera = true;
                            usbCameraAvailable = true;
                            setUsbCameraRotation(device);
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                restartCamera();
                                Toast.makeText(RealtimeActivity.this, "USB摄像头已连接", Toast.LENGTH_SHORT).show();
                            }, 1000);
                        }
                    } else {
                        Log.d(TAG, "USB摄像头权限被拒绝");
                        usbCameraPermissionGranted = false;
                        useUsbCamera = false;
                        runOnUiThread(() ->
                                Toast.makeText(RealtimeActivity.this, "USB摄像头权限被拒绝，将使用手机摄像头", Toast.LENGTH_SHORT).show()
                        );
                        restartCamera();
                    }
                }
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && isUsbCamera(device)) {
                    Log.d(TAG, "USB摄像头已连接: " + device.getDeviceName());
                    currentUsbDevice = device;
                    setUsbCameraRotation(device);
                    new Handler(Looper.getMainLooper()).postDelayed(() -> checkUsbCamera(), 1500);
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null && currentUsbDevice != null &&
                        device.getDeviceName().equals(currentUsbDevice.getDeviceName())) {
                    Log.d(TAG, "USB摄像头已断开");
                    usbCameraPermissionGranted = false;
                    useUsbCamera = false;
                    usbCameraAvailable = false;
                    currentUsbDevice = null;
                    usbCameraRotation = 0;
                    runOnUiThread(() -> {
                        restartCamera();
                        Toast.makeText(RealtimeActivity.this, "USB摄像头已断开", Toast.LENGTH_SHORT).show();
                    });
                }
            }
        }
    };


    class CandidateMatch {
        int frameIndex;
        float stdKnee;
        float stdHip;
        // 拳击专用字段
        float stdLeftElbow;
        float stdRightElbow;
        float stdLeftShoulder;
        float stdRightShoulder;
        float similarity;
        float combinedScore;
        int offset;
        float continuityScore;

        CandidateMatch(int frameIndex, float stdKnee, float stdHip,
                       float stdLeftElbow, float stdRightElbow,
                       float stdLeftShoulder, float stdRightShoulder,
                       float similarity, float combinedScore, int offset, float continuityScore) {
            this.frameIndex = frameIndex;
            this.stdKnee = stdKnee;
            this.stdHip = stdHip;
            this.stdLeftElbow = stdLeftElbow;
            this.stdRightElbow = stdRightElbow;
            this.stdLeftShoulder = stdLeftShoulder;
            this.stdRightShoulder = stdRightShoulder;
            this.similarity = similarity;
            this.combinedScore = combinedScore;
            this.offset = offset;
            this.continuityScore = continuityScore;
        }
    }

    class MatchResult {
        int matchedFrameIndex;
        float similarity;
        float matchedKneeAngle;
        float matchedHipAngle;
        float matchedElbowAngle;
        float matchedShoulderAngle;
        // 拳击专用字段
        float matchedLeftElbowAngle;
        float matchedRightElbowAngle;
        float matchedLeftShoulderAngle;
        float matchedRightShoulderAngle;
        int searchStart;
        int searchEnd;

        MatchResult(int matchedFrameIndex, float similarity,
                    float matchedKneeAngle, float matchedHipAngle,
                    float matchedElbowAngle, float matchedShoulderAngle,
                    float matchedLeftElbowAngle, float matchedRightElbowAngle,
                    float matchedLeftShoulderAngle, float matchedRightShoulderAngle,
                    int searchStart, int searchEnd) {
            this.matchedFrameIndex = matchedFrameIndex;
            this.similarity = similarity;
            this.matchedKneeAngle = matchedKneeAngle;
            this.matchedHipAngle = matchedHipAngle;
            this.matchedElbowAngle = matchedElbowAngle;
            this.matchedShoulderAngle = matchedShoulderAngle;
            this.matchedLeftElbowAngle = matchedLeftElbowAngle;
            this.matchedRightElbowAngle = matchedRightElbowAngle;
            this.matchedLeftShoulderAngle = matchedLeftShoulderAngle;
            this.matchedRightShoulderAngle = matchedRightShoulderAngle;
            this.searchStart = searchStart;
            this.searchEnd = searchEnd;
        }
    }


    class FrameAnalysisAdapter extends BaseAdapter {
        private Context context;
        private List<FrameAnalysisData> dataList;

        FrameAnalysisAdapter(Context context, List<FrameAnalysisData> dataList) {
            this.context = context;
            this.dataList = dataList;
        }

        @Override
        public int getCount() {
            return dataList.size();
        }

        @Override
        public FrameAnalysisData getItem(int position) {
            return dataList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder;
            boolean isBoxing = action != null && "拳击".equals(action.getActionName());

            int layoutRes = isBoxing ? R.layout.frame_analysis_item_boxing : R.layout.frame_analysis_item;

            if (convertView == null ||
                    (convertView.getTag() != null && ((ViewHolder)convertView.getTag()).layoutType != (isBoxing ? 1 : 0))) {
                convertView = LayoutInflater.from(context).inflate(layoutRes, parent, false);
                holder = new ViewHolder();
                holder.layoutType = isBoxing ? 1 : 0;

                // 公共视图
                holder.tvFrameIndex = convertView.findViewById(R.id.tvFrameIndex);
                holder.tvIsSelected = convertView.findViewById(R.id.tvIsSelected);
                holder.tvMatchInfo = convertView.findViewById(R.id.tvMatchInfo);
                holder.tvSimilarity = convertView.findViewById(R.id.tvSimilarity);
                holder.tvSelectionReason = convertView.findViewById(R.id.tvSelectionReason);

                if (isBoxing) {
                    holder.tvLeftElbow = convertView.findViewById(R.id.tvLeftElbow);
                    holder.tvRightElbow = convertView.findViewById(R.id.tvRightElbow);
                    holder.tvLeftShoulder = convertView.findViewById(R.id.tvLeftShoulder);
                    holder.tvRightShoulder = convertView.findViewById(R.id.tvRightShoulder);
                    holder.pbSimilarity = convertView.findViewById(R.id.pbSimilarity);
                    holder.tvSimilarityDetails = convertView.findViewById(R.id.tvSimilarityDetails);
                } else {
                    holder.tvKneeInfo = convertView.findViewById(R.id.tvKneeInfo);
                    holder.tvHipInfo = convertView.findViewById(R.id.tvHipInfo);
                    holder.pbSimilarity = convertView.findViewById(R.id.pbSimilarity);
                    holder.tvSimilarityDetails = convertView.findViewById(R.id.tvSimilarityDetails);
                }

                convertView.setTag(holder);
            } else {
                holder = (ViewHolder) convertView.getTag();
            }

            FrameAnalysisData data = getItem(position);

            // 公共设置
            holder.tvFrameIndex.setText("实时帧 #" + data.frameIndex);

            if (data.isSelectedFrame) {
                holder.tvIsSelected.setVisibility(View.VISIBLE);
                holder.tvFrameIndex.setTextColor(Color.BLUE);
            } else {
                holder.tvIsSelected.setVisibility(View.GONE);
                holder.tvFrameIndex.setTextColor(Color.BLACK);
            }

            if (data.isSelectedFrame && data.standardFrameIndex >= 0) {
                String matchInfo = String.format("匹配标准帧#%d", data.standardFrameIndex);
                holder.tvMatchInfo.setText(matchInfo);
                holder.tvMatchInfo.setVisibility(View.VISIBLE);
            } else {
                holder.tvMatchInfo.setVisibility(View.GONE);
            }

            // 根据动作类型设置内容
            if (isBoxing) {
                holder.tvLeftElbow.setText(String.format("实时: %.1f° | 标准: %.1f°",
                        data.realTimeLeftElbowAngle, data.standardLeftElbowAngle));
                holder.tvRightElbow.setText(String.format("实时: %.1f° | 标准: %.1f°",
                        data.realTimeRightElbowAngle, data.standardRightElbowAngle));
                holder.tvLeftShoulder.setText(String.format("实时: %.1f° | 标准: %.1f°",
                        data.realTimeLeftShoulderAngle, data.standardLeftShoulderAngle));
                holder.tvRightShoulder.setText(String.format("实时: %.1f° | 标准: %.1f°",
                        data.realTimeRightShoulderAngle, data.standardRightShoulderAngle));

                holder.tvSimilarityDetails.setText(String.format(
                        "左肘:%.1f%% 右肘:%.1f%% 左肩:%.1f%% 右肩:%.1f%%",
                        data.leftElbowSimilarity * 100, data.rightElbowSimilarity * 100,
                        data.leftShoulderSimilarity * 100, data.rightShoulderSimilarity * 100));
            } else {
                holder.tvKneeInfo.setText(String.format("膝: %.1f° | 标准: %.1f°",
                        data.realTimeKneeAngle, data.standardKneeAngle));
                holder.tvHipInfo.setText(String.format("髋: %.1f° | 标准: %.1f°",
                        data.realTimeHipAngle, data.standardHipAngle));

                holder.tvSimilarityDetails.setText(String.format("膝:%.1f%% 髋:%.1f%%",
                        data.kneeSimilarity * 100, data.hipSimilarity * 100));
            }

            // 相似度设置
            String similarityText = String.format("相似度: %.1f%%", data.totalSimilarity * 100);
            holder.tvSimilarity.setText(similarityText);
            if (holder.pbSimilarity != null) {
                holder.pbSimilarity.setProgress((int)(data.totalSimilarity * 100));
            }

            // 匹配原因
            if (data.isSelectedFrame && !data.selectionReason.isEmpty()) {
                holder.tvSelectionReason.setText("匹配原因: " + data.selectionReason);
            } else {
                holder.tvSelectionReason.setText("");
            }

            // 根据相似度设置背景色
            if (data.totalSimilarity >= 0.8) {
                convertView.setBackgroundColor(Color.parseColor("#E8F5E8"));
            } else if (data.totalSimilarity >= 0.6) {
                convertView.setBackgroundColor(Color.parseColor("#FFF9E8"));
            } else if (data.totalSimilarity > 0) {
                convertView.setBackgroundColor(Color.parseColor("#FFEBEE"));
            } else {
                convertView.setBackgroundColor(Color.WHITE);
            }

            return convertView;
        }

        class ViewHolder {
            int layoutType;

            // 公共视图
            TextView tvFrameIndex;
            TextView tvIsSelected;
            TextView tvMatchInfo;
            TextView tvSimilarity;
            TextView tvSelectionReason;
            ProgressBar pbSimilarity;
            TextView tvSimilarityDetails;

            // 深蹲专用
            TextView tvKneeInfo;
            TextView tvHipInfo;

            // 拳击专用
            TextView tvLeftElbow;
            TextView tvRightElbow;
            TextView tvLeftShoulder;
            TextView tvRightShoulder;
        }
    }

    //  图像尺寸管理方法
    private void setCurrentImageSize(int width, int height) {
        this.currentImageWidth = width;
        this.currentImageHeight = height;
        Log.d(TAG, "当前图像尺寸更新: " + width + "×" + height);
    }

    private int getCurrentImageWidth() {
        return currentImageWidth;
    }

    private int getCurrentImageHeight() {
        return currentImageHeight;
    }

    // 拳击专用函数
    private float calculateBoxingSimilarity(float rtLeftElbow, float rtRightElbow,
                                            float rtLeftShoulder, float rtRightShoulder,
                                            float stdLeftElbow, float stdRightElbow,
                                            float stdLeftShoulder, float stdRightShoulder) {
        try {
            if (!isValidAngle(rtLeftElbow) || !isValidAngle(rtRightElbow) ||
                    !isValidAngle(rtLeftShoulder) || !isValidAngle(rtRightShoulder) ||
                    !isValidAngle(stdLeftElbow) || !isValidAngle(stdRightElbow) ||
                    !isValidAngle(stdLeftShoulder) || !isValidAngle(stdRightShoulder)) {
                return 0f;
            }

            float leftElbowSim = calculateSingleAngleSimilarity(Math.abs(rtLeftElbow - stdLeftElbow));
            float rightElbowSim = calculateSingleAngleSimilarity(Math.abs(rtRightElbow - stdRightElbow));
            float leftShoulderSim = calculateSingleAngleSimilarity(Math.abs(rtLeftShoulder - stdLeftShoulder));
            float rightShoulderSim = calculateSingleAngleSimilarity(Math.abs(rtRightShoulder - stdRightShoulder));

            return (leftElbowSim * 0.3f + rightElbowSim * 0.3f +
                    leftShoulderSim * 0.2f + rightShoulderSim * 0.2f);

        } catch (Exception e) {
            Log.e(TAG, "拳击相似度计算错误: " + e.getMessage());
            return 0f;
        }
    }

    private float calculateLeftElbowAngle(List<NormalizedLandmark> landmarks, int imageWidth, int imageHeight) {
        if (!areLandmarksValidForAngleCalculation(landmarks,
                PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)) {
            return 0f;
        }
        double angle = calculateAngleWithSize(
                landmarks.get(PoseLandmark.LEFT_SHOULDER),
                landmarks.get(PoseLandmark.LEFT_ELBOW),
                landmarks.get(PoseLandmark.LEFT_WRIST),
                imageWidth, imageHeight);
        return (float) angle;
    }

    private float calculateRightElbowAngle(List<NormalizedLandmark> landmarks, int imageWidth, int imageHeight) {
        if (!areLandmarksValidForAngleCalculation(landmarks,
                PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)) {
            return 0f;
        }
        double angle = calculateAngleWithSize(
                landmarks.get(PoseLandmark.RIGHT_SHOULDER),
                landmarks.get(PoseLandmark.RIGHT_ELBOW),
                landmarks.get(PoseLandmark.RIGHT_WRIST),
                imageWidth, imageHeight);
        return (float) angle;
    }

    private float calculateLeftShoulderAngle(List<NormalizedLandmark> landmarks, int imageWidth, int imageHeight) {
        if (!areLandmarksValidForAngleCalculation(landmarks,
                PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW)) {
            return 0f;
        }
        double angle = calculateAngleWithSize(
                landmarks.get(PoseLandmark.LEFT_HIP),
                landmarks.get(PoseLandmark.LEFT_SHOULDER),
                landmarks.get(PoseLandmark.LEFT_ELBOW),
                imageWidth, imageHeight);
        return (float) angle;
    }

    private float calculateRightShoulderAngle(List<NormalizedLandmark> landmarks, int imageWidth, int imageHeight) {
        if (!areLandmarksValidForAngleCalculation(landmarks,
                PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW)) {
            return 0f;
        }
        double angle = calculateAngleWithSize(
                landmarks.get(PoseLandmark.RIGHT_HIP),
                landmarks.get(PoseLandmark.RIGHT_SHOULDER),
                landmarks.get(PoseLandmark.RIGHT_ELBOW),
                imageWidth, imageHeight);
        return (float) angle;
    }

    private boolean detectStaticStateForBoxing(float leftElbowAngle, float rightElbowAngle,
                                               float leftShoulderAngle, float rightShoulderAngle) {
        if (previousLeftElbowAngle < 0 || previousRightElbowAngle < 0) {
            previousLeftElbowAngle = leftElbowAngle;
            previousRightElbowAngle = rightElbowAngle;
            previousLeftShoulderAngle = leftShoulderAngle;
            previousRightShoulderAngle = rightShoulderAngle;
            return false;
        }

        float leftElbowChange = Math.abs(leftElbowAngle - previousLeftElbowAngle);
        float rightElbowChange = Math.abs(rightElbowAngle - previousRightElbowAngle);
        float leftShoulderChange = Math.abs(leftShoulderAngle - previousLeftShoulderAngle);
        float rightShoulderChange = Math.abs(rightShoulderAngle - previousRightShoulderAngle);

        previousLeftElbowAngle = leftElbowAngle;
        previousRightElbowAngle = rightElbowAngle;
        previousLeftShoulderAngle = leftShoulderAngle;
        previousRightShoulderAngle = rightShoulderAngle;

        boolean isCurrentStatic = leftElbowChange < STATIC_ANGLE_THRESHOLD &&
                rightElbowChange < STATIC_ANGLE_THRESHOLD &&
                leftShoulderChange < STATIC_ANGLE_THRESHOLD &&
                rightShoulderChange < STATIC_ANGLE_THRESHOLD;

        if (isCurrentStatic) {
            staticFrameCounter++;
            if (staticFrameCounter == 1) {
                staticStartTime = System.currentTimeMillis();
            }
            if (staticFrameCounter >= STATIC_FRAME_COUNT) {
                long staticDuration = System.currentTimeMillis() - staticStartTime;
                if (staticDuration >= STATIC_TIME_THRESHOLD) {
                    logStaticDetection(0, 0, leftElbowAngle, rightElbowAngle, leftShoulderAngle, rightShoulderAngle, true);
                    return true;
                }
            }
        } else {
            resetStaticDetection();
        }
        return false;
    }

    // 通用函数
    private boolean detectStaticState(float kneeAngle, float hipAngle,
                                      float elbowAngle, float shoulderAngle) {
        if (previousKneeAngle < 0 || previousHipAngle < 0) {
            previousKneeAngle = kneeAngle;
            previousHipAngle = hipAngle;
            previousElbowAngle = elbowAngle;
            previousShoulderAngle = shoulderAngle;
            return false;
        }

        float kneeChange = Math.abs(kneeAngle - previousKneeAngle);
        float hipChange = Math.abs(hipAngle - previousHipAngle);
        float elbowChange = Math.abs(elbowAngle - previousElbowAngle);
        float shoulderChange = Math.abs(shoulderAngle - previousShoulderAngle);

        previousKneeAngle = kneeAngle;
        previousHipAngle = hipAngle;
        previousElbowAngle = elbowAngle;
        previousShoulderAngle = shoulderAngle;

        boolean isCurrentStatic = kneeChange < STATIC_ANGLE_THRESHOLD &&
                hipChange < STATIC_ANGLE_THRESHOLD &&
                elbowChange < STATIC_ANGLE_THRESHOLD &&
                shoulderChange < STATIC_ANGLE_THRESHOLD;

        if (isCurrentStatic) {
            staticFrameCounter++;
            if (staticFrameCounter == 1) {
                staticStartTime = System.currentTimeMillis();
            }
            if (staticFrameCounter >= STATIC_FRAME_COUNT) {
                long staticDuration = System.currentTimeMillis() - staticStartTime;
                if (staticDuration >= STATIC_TIME_THRESHOLD) {
                    logStaticDetection(kneeAngle, hipAngle, elbowAngle, shoulderAngle, true);
                    return true;
                }
            }
        } else {
            resetStaticDetection();
        }
        return false;
    }

    private void resetStaticDetection() {
        staticFrameCounter = 0;
        staticStartTime = 0;
        hasShownStaticHint = false;
        isStaticState = false;
        runOnUiThread(() -> {
            if (staticHintLayout != null) {
                staticHintLayout.setVisibility(View.GONE);
            }
        });
    }

    private void logStaticDetection(float kneeAngle, float hipAngle,
                                    float elbowAngle, float shoulderAngle,
                                    boolean isStatic) {
        if (isStatic) {
            Log.d(TAG, String.format(
                    "静止状态检测: (膝%.1f° 髋%.1f° 肘%.1f° 肩%.1f°), 连续静止帧数: %d/%d",
                    kneeAngle, hipAngle, elbowAngle, shoulderAngle,
                    staticFrameCounter, STATIC_FRAME_COUNT
            ));
        }
    }

    private void logStaticDetection(float kneeAngle, float hipAngle,
                                    float leftElbowAngle, float rightElbowAngle,
                                    float leftShoulderAngle, float rightShoulderAngle,
                                    boolean isStatic) {
        if (isStatic) {
            Log.d(TAG, String.format(
                    "拳击静止状态检测: (左肘%.1f° 右肘%.1f° 左肩%.1f° 右肩%.1f°), 连续静止帧数: %d/%d",
                    leftElbowAngle, rightElbowAngle, leftShoulderAngle, rightShoulderAngle,
                    staticFrameCounter, STATIC_FRAME_COUNT
            ));
        }
    }

    private void showStaticHint() {
        runOnUiThread(() -> {
            try {
                if (staticHintLayout != null) {
                    staticHintLayout.setVisibility(View.VISIBLE);
                    staticHintLayout.setAlpha(0f);
                    staticHintLayout.animate().alpha(1f).setDuration(500).start();
                }
                if (angleView != null) {
                    angleView.setText("实时: 静止状态\n匹配: 未检测到运动\n相似度: --%%");
                    angleView.setTextColor(Color.GRAY);
                }
            } catch (Exception e) {
                Log.e(TAG, "显示静止提示失败: " + e.getMessage());
            }
        });
    }

    private void hideStaticHint() {
        runOnUiThread(() -> {
            if (staticHintLayout != null) {
                staticHintLayout.setVisibility(View.GONE);
            }
        });
    }
    //  核心相似度计算函数
    private float calculateSingleAngleSimilarity(float angleDiff) {
        angleDiff = Math.abs(angleDiff);
        float radians = (float) Math.toRadians(angleDiff);
        float cosTheta = (float) Math.cos(radians);
        float similarity = (cosTheta + 1.0f) / 2.0f;
        return Math.max(0.0f, Math.min(1.0f, similarity));
    }

    private float calculateAngleSimilarity(float rtKnee, float rtHip, float rtElbow, float rtShoulder,
                                           float stdKnee, float stdHip, float stdElbow, float stdShoulder) {
        try {
            if (!isValidAngle(rtKnee) || !isValidAngle(rtHip) || !isValidAngle(rtElbow) || !isValidAngle(rtShoulder) ||
                    !isValidAngle(stdKnee) || !isValidAngle(stdHip) || !isValidAngle(stdElbow) || !isValidAngle(stdShoulder)) {
                return 0f;
            }

            float kneeDiff = Math.abs(rtKnee - stdKnee);
            float hipDiff = Math.abs(rtHip - stdHip);
            float elbowDiff = Math.abs(rtElbow - stdElbow);
            float shoulderDiff = Math.abs(rtShoulder - stdShoulder);

            float kneeSimilarity = calculateSingleAngleSimilarity(kneeDiff);
            float hipSimilarity = calculateSingleAngleSimilarity(hipDiff);
            float elbowSimilarity = calculateSingleAngleSimilarity(elbowDiff);
            float shoulderSimilarity = calculateSingleAngleSimilarity(shoulderDiff);

            float similarity;

            // 根据动作类型分配权重
            if (action == null) {
                // 默认深蹲权重
                similarity = (kneeSimilarity * 0.5f + hipSimilarity * 0.5f);
            } else {
                String actionName = action.getActionName();

                switch (actionName) {
                    case "拳击":
                        // 拳击：肘肩为主，膝髋辅助
                        similarity = (kneeSimilarity * 0.1f + hipSimilarity * 0.1f +
                                elbowSimilarity * 0.4f + shoulderSimilarity * 0.4f);
                        break;

                    case "高抬腿":
                        // 高抬腿：膝为主，髋辅助
                        similarity = (kneeSimilarity * 0.7f + hipSimilarity * 0.3f);
                        break;

                    case "罗马尼亚硬拉":
                        // 罗马尼亚硬拉：髋为主，膝辅助
                        similarity = (kneeSimilarity * 0.2f + hipSimilarity * 0.8f);
                        break;

                    case "深蹲":
                    default:
                        // 深蹲：膝髋各半
                        similarity = (kneeSimilarity * 0.5f + hipSimilarity * 0.5f);
                        break;
                }
            }

            return Math.max(0f, Math.min(1f, similarity));

        } catch (Exception e) {
            Log.e(TAG, "角度相似度计算错误: " + e.getMessage());
            return 0f;
        }
    }

    private float applyRealTimeSmoothing(float currentSimilarity) {
        if (lastSmoothedSimilarity == 0f) {
            lastSmoothedSimilarity = currentSimilarity;
            return currentSimilarity;
        }
        float smoothed = (similaritySmoothingFactor * lastSmoothedSimilarity) +
                ((1 - similaritySmoothingFactor) * currentSimilarity);
        lastSmoothedSimilarity = smoothed;
        return smoothed;
    }

    private boolean isValidAngle(float angle) {
        return !Float.isNaN(angle) && !Float.isInfinite(angle) && angle >= 0f && angle <= 180f;
    }

    private void recordAngleData(float rtKnee, float rtHip, float rtElbow, float rtShoulder,
                                 float stdKnee, float stdHip, float stdElbow, float stdShoulder,
                                 float similarity) {
        try {
            synchronized (this) {
                realTimeAnglesHistory.add(rtKnee);
                realTimeAnglesHistory.add(rtHip);
                standardAnglesHistory.add(stdKnee);
                standardAnglesHistory.add(stdHip);
                similarityHistory.add(similarity);
                frameTimestamps.add(System.currentTimeMillis());
                dataFrameCounter++;
            }
            if (dataFrameCounter % 30 == 0) {
                Log.d(TAG, String.format("已处理%d帧，当前相似度: %.1f%%", dataFrameCounter, similarity * 100));
            }
        } catch (Exception e) {
            Log.e(TAG, "记录角度数据失败: " + e.getMessage());
        }
    }

    private void resetDataCollection() {
        synchronized (this) {
            processedFrameCount = 0;
            dataFrameCounter = 0;
            currentFrameIndex = 0;
            realTimeAnglesHistory.clear();
            standardAnglesHistory.clear();
            similarityHistory.clear();
            frameTimestamps.clear();
            lastSmoothedSimilarity = 0f;
            matchedStandardFrameIndex = -1;
            matchedStandardKnee = -1f;
            matchedStandardHip = -1f;
            currentMatchSimilarity = 0f;

            matchedStandardLeftElbow = -1f;
            matchedStandardRightElbow = -1f;
            matchedStandardLeftShoulder = -1f;
            matchedStandardRightShoulder = -1f;
        }
    }

    private void updateAngleDisplayStatic(float realTimeKnee, float realTimeHip,
                                          float realTimeElbow, float realTimeShoulder) {
        try {
            if (angleView != null) {
                String staticText = String.format(
                        "匹配: 未检测到运动\n" +
                                "相似度: --%%\n" +
                                "状态: 静止状态"
                );
                angleView.setText(staticText);
                angleView.setTextColor(Color.GRAY);
            }
        } catch (Exception e) {
            Log.e(TAG, "深蹲静止状态显示失败: " + e.getMessage());
        }
    }

    private void updateAngleDisplayStaticForBoxing(float rtLeftElbow, float rtRightElbow,
                                                   float rtLeftShoulder, float rtRightShoulder) {
        try {
            if (angleView != null) {
                String staticText = String.format(
                        "匹配: 未检测到运动\n" +
                                "相似度: --%%\n" +
                                "状态: 静止状态"
                );
                angleView.setText(staticText);
                angleView.setTextColor(Color.GRAY);
            }
        } catch (Exception e) {
            Log.e(TAG, "拳击静止状态显示失败: " + e.getMessage());
        }
    }

    private void updateAngleDisplay(float realTimeKnee, float realTimeHip, float realTimeElbow, float realTimeShoulder,
                                    float matchedKnee, float matchedHip, float matchedElbow, float matchedShoulder,
                                    int matchedFrameIndex, float similarity) {
        try {
            currentRealTimeKnee = realTimeKnee;
            currentRealTimeHip = realTimeHip;
            currentRealTimeElbow = realTimeElbow;
            currentRealTimeShoulder = realTimeShoulder;

            String realTimeKneeText = formatAngle(realTimeKnee);
            String realTimeHipText = formatAngle(realTimeHip);
            String realTimeElbowText = formatAngle(realTimeElbow);
            String realTimeShoulderText = formatAngle(realTimeShoulder);
            String matchedKneeText = formatAngle(matchedKnee);
            String matchedHipText = formatAngle(matchedHip);
            String matchedElbowText = formatAngle(matchedElbow);
            String matchedShoulderText = formatAngle(matchedShoulder);

            String angleText;
            if (action != null && "拳击".equals(action.getActionName())) {
                if (matchedFrameIndex >= 0) {
                    angleText = String.format(
                            "实时: 肘%s° 肩%s°\n" +
                                    "匹配: 肘%s° 肩%s° (标准帧#%d)\n" +
                                    "相似度: %.1f%%",
                            realTimeElbowText, realTimeShoulderText,
                            matchedElbowText, matchedShoulderText,
                            matchedFrameIndex, similarity * 100
                    );
                } else {
                    angleText = String.format(
                            "实时: 肘%s° 肩%s°\n" +
                                    "匹配: 无有效匹配\n" +
                                    "相似度: --%%",
                            realTimeElbowText, realTimeShoulderText
                    );
                }
            } else {
                if (matchedFrameIndex >= 0) {
                    angleText = String.format(
                            "实时: 膝%s° 髋%s°\n" +
                                    "匹配: 膝%s° 髋%s° (标准帧#%d)\n" +
                                    "相似度: %.1f%%",
                            realTimeKneeText, realTimeHipText,
                            matchedKneeText, matchedHipText,
                            matchedFrameIndex, similarity * 100
                    );
                } else {
                    angleText = String.format(
                            "实时: 膝%s° 髋%s°\n" +
                                    "匹配: 无有效匹配\n" +
                                    "相似度: --%%",
                            realTimeKneeText, realTimeHipText
                    );
                }
            }

            if (angleView != null) {
                angleView.setText(angleText);
                if (similarity >= 0.8f) {
                    angleView.setTextColor(Color.GREEN);
                } else if (similarity >= 0.6f) {
                    angleView.setTextColor(Color.YELLOW);
                } else {
                    angleView.setTextColor(Color.RED);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "更新角度显示失败: " + e.getMessage());
        }
    }

    private void updateAngleDisplayForBoxing(float rtLeftElbow, float rtRightElbow,
                                             float rtLeftShoulder, float rtRightShoulder,
                                             float stdLeftElbow, float stdRightElbow,
                                             float stdLeftShoulder, float stdRightShoulder,
                                             int matchedFrameIndex, float similarity) {
        try {
            String angleText;
            if (matchedFrameIndex >= 0) {
                angleText = String.format(
                        "左肘: %s° | 右肘: %s°\n" +
                                "左肩: %s° | 右肩: %s°\n" +
                                "匹配帧#%d 相似度: %.1f%%\n" +
                                "标准: 左肘%s° 右肘%s° 左肩%s° 右肩%s°",
                        formatAngle(rtLeftElbow), formatAngle(rtRightElbow),
                        formatAngle(rtLeftShoulder), formatAngle(rtRightShoulder),
                        matchedFrameIndex, similarity * 100,
                        formatAngle(stdLeftElbow), formatAngle(stdRightElbow),
                        formatAngle(stdLeftShoulder), formatAngle(stdRightShoulder)
                );
            } else {
                angleText = String.format(
                        "左肘: %s° | 右肘: %s°\n" +
                                "左肩: %s° | 右肩: %s°\n" +
                                "匹配: 无有效匹配\n" +
                                "相似度: --%%",
                        formatAngle(rtLeftElbow), formatAngle(rtRightElbow),
                        formatAngle(rtLeftShoulder), formatAngle(rtRightShoulder)
                );
            }

            if (angleView != null) {
                angleView.setText(angleText);
                if (similarity >= 0.8f) {
                    angleView.setTextColor(Color.GREEN);
                } else if (similarity >= 0.6f) {
                    angleView.setTextColor(Color.YELLOW);
                } else {
                    angleView.setTextColor(Color.RED);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "拳击更新角度显示失败: " + e.getMessage());
        }
    }

    private String formatAngle(float angle) {
        return (angle <= 0 || Float.isNaN(angle)) ? "--" : String.format("%.1f", angle);
    }

    private void refreshSquatCount() {
        if (squatCountView == null) return;
        if (action != null && action.hasCounter() && isTracking && hasRequiredJoints) {
            int count = squatAnalyzer.getSquatCount();
            squatCountView.setVisibility(View.VISIBLE);
            squatCountView.setText(actionName + ": " + count);
        } else {
            squatCountView.setVisibility(View.GONE);
        }
    }


    private void performPeriodicAlignment() {
        if (!isTracking || !isStandardDataReady || standardVideoData.isEmpty()) {
            return;
        }

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastAlignmentTime >= ALIGNMENT_INTERVAL_MS) {
            Log.d(TAG, "执行周期性对齐（每10秒）");
            lastAlignmentTime = currentTime;

            isInAlignmentPeriod = true;

            int totalFrames = standardVideoData.size();
            alignmentSearchRadius = totalFrames / 2;

            findAndUpdateBestAlignment();

            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                isInAlignmentPeriod = false;
                Log.d(TAG, "对齐完成，恢复同步检测");
            }, 1000);
        }
    }

    /**
     * 查找并更新最佳对齐帧
     */
    private void findAndUpdateBestAlignment() {
        if (!isTracking || !hasRequiredJoints) {
            return;
        }

        try {
            float realTimeKnee = currentRealTimeKnee;
            float realTimeHip = currentRealTimeHip;
            float realTimeElbow = currentRealTimeElbow;
            float realTimeShoulder = currentRealTimeShoulder;

            float realTimeLeftElbow = currentRealTimeLeftElbow;
            float realTimeRightElbow = currentRealTimeRightElbow;
            float realTimeLeftShoulder = currentRealTimeLeftShoulder;
            float realTimeRightShoulder = currentRealTimeRightShoulder;

            boolean isBoxing = action != null && "拳击".equals(action.getActionName());

            int bestMatchIndex = -1;
            float bestSimilarity = 0f;

            int searchStart = Math.max(0, standardFramePointer - alignmentSearchRadius);
            int searchEnd = Math.min(standardVideoData.size() - 1,
                    standardFramePointer + alignmentSearchRadius);

            Log.d(TAG, String.format("对齐搜索：范围[%d-%d]，半径%d，当前指针%d",
                    searchStart, searchEnd, alignmentSearchRadius, standardFramePointer));

            for (int i = searchStart; i <= searchEnd; i++) {
                FrameData standardFrame = standardVideoData.get(i);
                if (standardFrame == null || !standardFrame.hasValidPose) {
                    continue;
                }

                float similarity;
                if (isBoxing) {
                    similarity = calculateBoxingSimilarity(
                            realTimeLeftElbow, realTimeRightElbow,
                            realTimeLeftShoulder, realTimeRightShoulder,
                            standardFrame.leftElbowAngle, standardFrame.rightElbowAngle,
                            standardFrame.leftShoulderAngle, standardFrame.rightShoulderAngle
                    );
                } else {
                    similarity = calculateAngleSimilarity(
                            realTimeKnee, realTimeHip, realTimeElbow, realTimeShoulder,
                            standardFrame.kneeAngle, standardFrame.hipAngle,
                            standardFrame.elbowAngle, standardFrame.shoulderAngle
                    );
                }

                if (similarity > bestSimilarity) {
                    bestSimilarity = similarity;
                    bestMatchIndex = i;
                }
            }

            if (bestMatchIndex >= 0 && bestSimilarity > ALIGNMENT_SIMILARITY_THRESHOLD) {
                int oldPointer = standardFramePointer;
                standardFramePointer = bestMatchIndex;

                Log.d(TAG, String.format("对齐成功：从帧#%d对齐到帧#%d，相似度%.1f%%",
                        oldPointer, bestMatchIndex, bestSimilarity * 100));

                if (player != null && player.isPlaying()) {
                    long newPositionMs = (long)((bestMatchIndex * 1000.0) / standardVideoFps);
                    player.seekTo(newPositionMs);
                }
            } else {
                Log.d(TAG, "对齐失败：未找到足够相似的帧，最高相似度" +
                        (bestSimilarity * 100) + "%");
            }

        } catch (Exception e) {
            Log.e(TAG, "对齐搜索失败: " + e.getMessage());
        }
    }


    /**
     * 实时相似度计算
     * 1. 正常情况下：实时帧 vs 标准视频当前帧
     * 2. 每10秒：执行一次对齐搜索
     */
    private void calculateRealTimeSimilarity(List<NormalizedLandmark> landmarks) {
        if (!isTracking || !shouldCalculateAngles || !hasRequiredJoints) {
            return;
        }

        try {
            int currentWidth = getCurrentImageWidth();
            int currentHeight = getCurrentImageHeight();

            double[] realTimeAngles = calculateKeyAnglesWithSize(landmarks, currentWidth, currentHeight);
            float realTimeKnee = (float) realTimeAngles[0];
            float realTimeHip = (float) realTimeAngles[1];
            float realTimeElbow = (float) realTimeAngles[2];
            float realTimeShoulder = (float) realTimeAngles[3];

            float realTimeLeftElbow = calculateLeftElbowAngle(landmarks, currentWidth, currentHeight);
            float realTimeRightElbow = calculateRightElbowAngle(landmarks, currentWidth, currentHeight);
            float realTimeLeftShoulder = calculateLeftShoulderAngle(landmarks, currentWidth, currentHeight);
            float realTimeRightShoulder = calculateRightShoulderAngle(landmarks, currentWidth, currentHeight);

            currentRealTimeKnee = realTimeKnee;
            currentRealTimeHip = realTimeHip;
            currentRealTimeElbow = realTimeElbow;
            currentRealTimeShoulder = realTimeShoulder;
            currentRealTimeLeftElbow = realTimeLeftElbow;
            currentRealTimeRightElbow = realTimeRightElbow;
            currentRealTimeLeftShoulder = realTimeLeftShoulder;
            currentRealTimeRightShoulder = realTimeRightShoulder;

            performPeriodicAlignment();

            FrameData currentStandardFrame = null;
            if (standardFramePointer < standardVideoData.size()) {
                currentStandardFrame = standardVideoData.get(standardFramePointer);
            }

            float similarity;
            float matchedKnee = -1f;
            float matchedHip = -1f;
            float matchedElbow = -1f;
            float matchedShoulder = -1f;
            float matchedLeftElbow = -1f;
            float matchedRightElbow = -1f;
            float matchedLeftShoulder = -1f;
            float matchedRightShoulder = -1f;

            boolean isBoxing = action != null && "拳击".equals(action.getActionName());

            if (currentStandardFrame != null && currentStandardFrame.hasValidPose) {
                if (isBoxing) {
                    matchedLeftElbow = currentStandardFrame.leftElbowAngle;
                    matchedRightElbow = currentStandardFrame.rightElbowAngle;
                    matchedLeftShoulder = currentStandardFrame.leftShoulderAngle;
                    matchedRightShoulder = currentStandardFrame.rightShoulderAngle;

                    similarity = calculateBoxingSimilarity(
                            realTimeLeftElbow, realTimeRightElbow,
                            realTimeLeftShoulder, realTimeRightShoulder,
                            matchedLeftElbow, matchedRightElbow,
                            matchedLeftShoulder, matchedRightShoulder
                    );

                    updateAngleDisplayForBoxing(
                            realTimeLeftElbow, realTimeRightElbow,
                            realTimeLeftShoulder, realTimeRightShoulder,
                            matchedLeftElbow, matchedRightElbow,
                            matchedLeftShoulder, matchedRightShoulder,
                            standardFramePointer, similarity
                    );
                } else {
                    matchedKnee = currentStandardFrame.kneeAngle;
                    matchedHip = currentStandardFrame.hipAngle;
                    matchedElbow = currentStandardFrame.elbowAngle;
                    matchedShoulder = currentStandardFrame.shoulderAngle;

                    similarity = calculateAngleSimilarity(
                            realTimeKnee, realTimeHip, realTimeElbow, realTimeShoulder,
                            matchedKnee, matchedHip, matchedElbow, matchedShoulder
                    );

                    updateAngleDisplay(
                            realTimeKnee, realTimeHip, realTimeElbow, realTimeShoulder,
                            matchedKnee, matchedHip, matchedElbow, matchedShoulder,
                            standardFramePointer, similarity
                    );
                }


                runOnUiThread(() -> {
                    if (overlayView != null) {
                        overlayView.setSquatInfo(
                                (action != null && action.hasCounter()) ? squatAnalyzer.getSquatCount() : 0,
                                similarity
                        );
                    }
                });
                // 在计算出 similarity 后，累积相似度
                if (isTracking && similarity > 0) {
                    totalSimilaritySum += similarity;
                    similarityCount++;


                    long elapsedMs = System.currentTimeMillis() - workoutStartTime;
                    // 向上取整到秒用于存储
                    long roundedElapsedMs = ((elapsedMs + 999) / 1000) * 1000;

                    similarityList.add(similarity);
                    frameTimestamps.add(roundedElapsedMs);

                    if (similarityCount % 30 == 0) {
                        Log.d(TAG, String.format("已记录%d帧, 当前已过时间=%dms (取整=%dms), 相似度=%.1f%%",
                                similarityCount, elapsedMs, roundedElapsedMs, similarity * 100));
                    }
                }
                FrameAnalysisData frameData;
                if (isBoxing) {
                    frameData = new FrameAnalysisData(
                            ++currentFrameIndex,
                            true,
                            realTimeLeftElbow, realTimeRightElbow,
                            realTimeLeftShoulder, realTimeRightShoulder,
                            matchedLeftElbow, matchedRightElbow,
                            matchedLeftShoulder, matchedRightShoulder,
                            calculateSingleAngleSimilarity(Math.abs(realTimeLeftElbow - matchedLeftElbow)),
                            calculateSingleAngleSimilarity(Math.abs(realTimeRightElbow - matchedRightElbow)),
                            calculateSingleAngleSimilarity(Math.abs(realTimeLeftShoulder - matchedLeftShoulder)),
                            calculateSingleAngleSimilarity(Math.abs(realTimeRightShoulder - matchedRightShoulder))
                    );
                } else {
                    frameData = new FrameAnalysisData(
                            ++currentFrameIndex,
                            realTimeKnee, realTimeHip,
                            realTimeElbow, realTimeShoulder,
                            matchedKnee, matchedHip,
                            matchedElbow, matchedShoulder,
                            calculateSingleAngleSimilarity(Math.abs(realTimeKnee - matchedKnee)),
                            calculateSingleAngleSimilarity(Math.abs(realTimeHip - matchedHip)),
                            calculateSingleAngleSimilarity(Math.abs(realTimeElbow - matchedElbow)),
                            calculateSingleAngleSimilarity(Math.abs(realTimeShoulder - matchedShoulder))
                    );
                }

                frameData.isSelectedFrame = true;
                frameData.standardFrameIndex = standardFramePointer;
                frameData.totalSimilarity = similarity;
                frameData.selectionReason = isInAlignmentPeriod ?
                        "周期性对齐" : "实时同步检测";

                frameAnalysisList.add(frameData);

                storeAngleData(isBoxing,
                        realTimeKnee, realTimeHip, realTimeElbow, realTimeShoulder,
                        realTimeLeftElbow, realTimeRightElbow, realTimeLeftShoulder, realTimeRightShoulder,
                        matchedKnee, matchedHip, matchedElbow, matchedShoulder,
                        matchedLeftElbow, matchedRightElbow, matchedLeftShoulder, matchedRightShoulder);

            } else {
                similarity = 0f;
                Log.w(TAG, "没有有效的标准帧数据，当前指针: " + standardFramePointer);
            }

            if (!isInAlignmentPeriod && player != null && player.isPlaying()) {
                long currentTime = System.currentTimeMillis();
                if (trackingStartTime > 0) {
                    long elapsedTime = currentTime - trackingStartTime;
                    int expectedFrame = (int)((elapsedTime * standardVideoFps) / 1000) % standardVideoData.size();

                    if (Math.abs(expectedFrame - standardFramePointer) > 1) {
                        standardFramePointer = expectedFrame;
                    }
                }
            }

        } catch (Exception e) {
            Log.e(TAG, "实时相似度计算失败: " + e.getMessage());
        }
    }

    private void storeAngleData(boolean isBoxing,
                                float rtKnee, float rtHip, float rtElbow, float rtShoulder,
                                float rtLeftElbow, float rtRightElbow,
                                float rtLeftShoulder, float rtRightShoulder,
                                float stdKnee, float stdHip, float stdElbow, float stdShoulder,
                                float stdLeftElbow, float stdRightElbow,
                                float stdLeftShoulder, float stdRightShoulder) {

        if (isBoxing) {
            rtLeftElbowAngles.add(rtLeftElbow);
            rtRightElbowAngles.add(rtRightElbow);
            rtLeftShoulderAngles.add(rtLeftShoulder);
            rtRightShoulderAngles.add(rtRightShoulder);

            stdLeftElbows.add(stdLeftElbow);
            stdRightElbows.add(stdRightElbow);
            stdLeftShoulders.add(stdLeftShoulder);
            stdRightShoulders.add(stdRightShoulder);
        } else {
            rtKneeAngles.add(rtKnee);
            rtHipAngles.add(rtHip);
            rtElbowAngles.add(rtElbow);
            rtShoulderAngles.add(rtShoulder);
            rtTimes.add(System.currentTimeMillis() - trackingStartTime);

            stdKnees.add(stdKnee);
            stdHips.add(stdHip);
            stdElbows.add(stdElbow);
            stdShoulders.add(stdShoulder);
        }
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        action = (ActionStrategy) getIntent().getSerializableExtra(EXTRA_ACTION);

        if (action == null) {
            Log.e(TAG, "错误：没有接收到ActionStrategy！使用默认深蹲");
            Toast.makeText(this, "错误：未接收到动作类型，默认使用深蹲", Toast.LENGTH_LONG).show();
            action = new SquatStrategy();
        } else {
            Log.d(TAG, "成功接收到动作类型: " + action.getActionName() +
                    ", hasCounter=" + action.hasCounter());
            Toast.makeText(this, "当前动作: " + action.getActionName(), Toast.LENGTH_SHORT).show();
        }

        actionName = action.getActionName();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            Log.e(TAG, "未捕获的异常: " + throwable.getMessage(), throwable);
            runOnUiThread(() -> Toast.makeText(this, "应用遇到问题，请重启应用", Toast.LENGTH_LONG).show());
        });

        try {
            Log.d(TAG, "开始创建 RealtimeActivity, 动作类型: " + actionName);
            setContentView(R.layout.activity_realtime);
            Log.d(TAG, "布局设置成功");

            Toolbar toolbar = findViewById(R.id.back_toolbar);
            if (toolbar == null) throw new RuntimeException("Toolbar 未找到");
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayShowTitleEnabled(false);
            }
            toolbar.setNavigationOnClickListener(v -> finish());

            copyAssetsToCache();
            initializeComponents();
            registerUsbReceiver();
            countdownHandler = new Handler(Looper.getMainLooper());

            // ========== 新增：根据动作类型控制信息面板显示 ==========
            LinearLayout lytContainer = findViewById(R.id.lyt_container);
            if (lytContainer != null) {
                if (action != null && "拳击".equals(action.getActionName())) {
                    lytContainer.setVisibility(View.GONE);
                    Log.d(TAG, "拳击模式：隐藏计数面板");
                } else {
                    lytContainer.setVisibility(View.VISIBLE);
                    Log.d(TAG, "深蹲模式：显示计数面板");
                }
            }

            if (allPermissionsGranted()) {
                new Handler().postDelayed(() -> {
                    checkUsbCamera();
                    startCamera();
                }, 500);
            } else {
                ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS);
            }

            Log.d(TAG, "RealtimeActivity初始化完成");

        } catch (Exception e) {
            Log.e(TAG, "RealtimeActivity初始化失败: " + e.getMessage(), e);
            Toast.makeText(this, "应用初始化失败，请重启应用", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void initializeComponents() {

        try {
            REQUIRED_PERMISSIONS = getRequiredPermissions();
            usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            initializeViews();
            initializeProgressViews();
            // 初始化运动数据服务
            workoutDataService = new WorkoutDataService(this);
            if (videoProgressOverlay == null) {
                Log.e(TAG, "进度视图初始化失败，重新查找");
                videoProgressOverlay = findViewById(R.id.videoProgressOverlay);
                videoProgressBar = findViewById(R.id.videoProgressBar);
                videoProgressText = findViewById(R.id.videoProgressText);
                videoProgressPercent = findViewById(R.id.videoProgressPercent);
            }

            backgroundExecutor = Executors.newSingleThreadExecutor();
            squatAnalyzer = new SquatAnalyzer();
            videoProcessor = new VideoProcessor(this);

            toggleVisualizationButton = findViewById(R.id.toggleVisualizationButton);
            toggleFrameAnalysisButton = findViewById(R.id.toggleFrameAnalysisButton);
            validateButton = findViewById(R.id.validateButton);
            videoControlPanel = findViewById(R.id.videoControlPanel);
            btnPlaybackSpeed = findViewById(R.id.btn_playback_speed);

            if (videoControlPanel != null) {
                videoControlPanel.setVisibility(View.GONE);
            }

            if (exoPlayerView != null) {
                exoPlayerView.setOnClickListener(v -> toggleControlPanel());
            }

            if (videoControlPanel != null) {
                videoControlPanel.setOnClickListener(v -> { });
            }

            if (btnPlaybackSpeed != null) {
                btnPlaybackSpeed.setOnClickListener(v -> showSpeedPickerDialog());
            }

            if (toggleVisualizationButton != null) {
                toggleVisualizationButton.setEnabled(false);
                toggleVisualizationButton.setAlpha(0.5f);
                toggleVisualizationButton.setOnClickListener(v -> {
                    if (hasMotionData) {
                        toggleVisualization();
                    } else {
                        Toast.makeText(this, "请先开始运动获取数据", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            if (toggleFrameAnalysisButton != null) {
                toggleFrameAnalysisButton.setEnabled(false);
                toggleFrameAnalysisButton.setAlpha(0.5f);
                toggleFrameAnalysisButton.setOnClickListener(v -> {
                    if (hasMotionData) {
                        toggleFrameAnalysis();
                    } else {
                        Toast.makeText(this, "请先开始运动获取数据", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            if (validateButton != null) {
                validateButton.setEnabled(false);
                validateButton.setAlpha(0.5f);
                validateButton.setOnClickListener(v -> {
                    if (hasMotionData) {
                        validateComparison();
                    } else {
                        Toast.makeText(this, "请先开始运动获取数据", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            new Handler().postDelayed(() -> {
                initializeExoPlayer();
                initPoseLandmarker();
            }, 1000);

        } catch (Exception e) {
            Log.e(TAG, "组件初始化失败: " + e.getMessage(), e);
            showErrorDialog("组件初始化失败", e.getMessage());
        }
    }

    private void toggleControlPanel() {
        if (videoControlPanel == null) return;
        if (videoControlPanel.getVisibility() == View.VISIBLE) {
            videoControlPanel.setVisibility(View.GONE);
        } else {
            videoControlPanel.setVisibility(View.VISIBLE);
        }
    }

    private void showSpeedPickerDialog() {
        if (player == null) {
            Toast.makeText(this, "视频未加载", Toast.LENGTH_SHORT).show();
            return;
        }

        String[] labels = {"0.5 倍速", "1 倍速", "2 倍速"};
        float[] speeds = {0.5f, 1.0f, 2.0f};

        new AlertDialog.Builder(this)
                .setTitle("选择播放速度")
                .setItems(labels, (dialog, which) -> {
                    float speed = speeds[which];
                    player.setPlaybackParameters(new PlaybackParameters(speed));
                    btnPlaybackSpeed.setText("倍速: " + speed + "x");
                })
                .show();
    }

    private void initializeProgressViews() {
        try {
            videoProgressOverlay = findViewById(R.id.videoProgressOverlay);
            videoProgressBar = findViewById(R.id.videoProgressBar);
            videoProgressText = findViewById(R.id.videoProgressText);
            videoProgressPercent = findViewById(R.id.videoProgressPercent);

            if (videoProgressBar != null) {
                videoProgressBar.setMax(100);
                videoProgressBar.setProgress(0);
            }
        } catch (Exception e) {
            Log.e(TAG, "进度视图初始化失败: " + e.getMessage(), e);
        }
    }

    private void showVideoProgress(String message, int progress) {
        runOnUiThread(() -> {
            try {
                if (videoProgressOverlay == null) {
                    initializeProgressViews();
                }
                if (videoProgressOverlay != null) {
                    videoProgressOverlay.bringToFront();
                    videoProgressOverlay.setVisibility(View.VISIBLE);
                    if (videoProgressText != null) videoProgressText.setText(message);
                    if (videoProgressBar != null) videoProgressBar.setProgress(progress);
                    if (videoProgressPercent != null) videoProgressPercent.setText(progress + "%");
                    videoProgressOverlay.invalidate();
                    videoProgressOverlay.requestLayout();
                }
            } catch (Exception e) {
                Log.e(TAG, "显示进度异常: " + e.getMessage());
            }
        });
    }

    private void hideVideoProgress() {
        runOnUiThread(() -> {
            try {
                if (videoProgressOverlay != null) {
                    videoProgressOverlay.setVisibility(View.GONE);
                }
            } catch (Exception e) {
                Log.e(TAG, "隐藏进度失败: " + e.getMessage());
            }
        });
    }

    private void initializeViews() {
        try {
            viewFinder = findViewById(R.id.viewFinder);
            if (viewFinder == null) {
                throw new RuntimeException("viewFinder 未找到");
            }

            overlayView = findViewById(R.id.overlay);
            if (overlayView == null) {
                throw new RuntimeException("overlay 未找到");
            }

            staticHintLayout = findViewById(R.id.staticHintLayout);
            staticHintView = findViewById(R.id.staticHintView);
            staticHintDetail = findViewById(R.id.staticHintDetail);
            switchCameraButton = findViewById(R.id.switch_camera_button);
            startButton = findViewById(R.id.start_button);
            squatCountView = findViewById(R.id.squatCount);
            angleView = findViewById(R.id.angleView);
            exoPlayerView = findViewById(R.id.exoPlayerView);
            selectVideoButton = findViewById(R.id.select_video_button);
            videoOverlayView = findViewById(R.id.videoOverlayView);
            countdownText = findViewById(R.id.countdownText);

            if (switchCameraButton != null) {
                switchCameraButton.setOnClickListener(v -> toggleCamera());
            }

            startButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (isTracking || isWaitingForDetection) {
                        stopTracking();
                    } else {
                        if (countdownText != null && countdownText.getVisibility() == View.VISIBLE) {
                            stopTracking();
                        }
                        if (!isStandardDataReady || standardVideoData.isEmpty()) {
                            Toast.makeText(RealtimeActivity.this, "请先选择标准视频", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        isTracking = false;
                        isWaitingForDetection = false;

                        resetDetectionState();
                        startDetectionPeriod();
                        startButton.setText("停止运动");
                    }
                }
            });

            if (selectVideoButton != null) {
                selectVideoButton.setOnClickListener(v -> showVideoOptionsDialog());
            }

            Button generateReportButton = findViewById(R.id.btn_generate_report);
            if (generateReportButton != null) {
                generateReportButton.setOnClickListener(v -> generateReport());
            }

            setupAngleDisplay();
            adjustCameraButtonPosition();

        } catch (Exception e) {
            Log.e(TAG, "视图初始化失败: " + e.getMessage(), e);
            throw new RuntimeException("UI初始化失败", e);
        }
    }

    private void showVideoOptionsDialog() {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_video_selector, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .setCancelable(true)
                .create();
        dialog.setOnShowListener(d -> { /* 宽度 85% & 居中 代码不变 */ });

        RecyclerView rvOfficial = dialogView.findViewById(R.id.rv_official_videos);
        rvOfficial.setLayoutManager(new LinearLayoutManager(this));
        rvOfficial.setAdapter(new OfficialVideoAdapter(action.getOfficialVideos(), video -> {
            dialog.dismiss();
            handleDefaultVideo(video);
        }));

        dialogView.findViewById(R.id.tv_album).setOnClickListener(v -> {
            dialog.dismiss();
            selectVideo();
        });
        dialog.show();
    }

    private void handleDefaultVideo(ActionStrategy.OfficialVideo video) {
        showVideoProgress("正在加载官方标准视频...", 0);
        selectVideoButton.setEnabled(false);
        startButton.setEnabled(false);

        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                List<FrameData> data = PoseCache.read(video.cacheName, this);
                if (data == null || data.isEmpty()) {
                    Toast.makeText(this, "官方标准数据缺失:\n" + video.cacheName, Toast.LENGTH_LONG).show();
                    selectVideoButton.setEnabled(true);
                    hideVideoProgress();
                    return;
                }

                synchronized (standardVideoData) {
                    standardVideoData.clear();
                    standardVideoData.addAll(data);
                    isStandardDataReady = true;
                    standardVideoFps = 30f;
                }

                // 加载对应的视频文件
                int resId = getResources().getIdentifier(
                        video.videoResName, "raw", getPackageName());

                if (resId != 0) {
                    Uri videoUri = Uri.parse("android.resource://" + getPackageName() + "/" + resId);
                    customStandardVideoUri = videoUri;

                    // 初始化视频播放器
                    if (player != null) {
                        player.release();
                    }

                    player = new SimpleExoPlayer.Builder(this).build();
                    exoPlayerView.setPlayer(player);
                    exoPlayerView.setUseController(false);
                    player.setVolume(0f);
                    DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                            Util.getUserAgent(this, getApplicationInfo().loadLabel(getPackageManager()).toString()));

                    MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                            .createMediaSource(MediaItem.fromUri(videoUri));

                    player.setMediaSource(videoSource);
                    player.prepare();
                    player.setRepeatMode(Player.REPEAT_MODE_ALL);
                    player.setPlayWhenReady(true);

                    // 隐藏占位符
                    LinearLayout videoPlaceholder = findViewById(R.id.videoPlaceholder);
                    if (videoPlaceholder != null) {
                        videoPlaceholder.setVisibility(View.GONE);
                    }

                    player.addListener(new Player.Listener() {
                        @Override
                        public void onPlaybackStateChanged(int state) {
                            if (state == Player.STATE_READY) {
                                Log.d(TAG, "官方视频播放器准备就绪");
                                updateVideoDisplayRect();
                                updateStandardVideoOverlay();
                            }
                        }

                        @Override
                        public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                                            @NonNull Player.PositionInfo newPosition,
                                                            int reason) {
                            updateStandardFramePointer();
                            updateStandardVideoOverlay();
                        }
                    });

                    Log.d(TAG, "官方视频加载成功: " + video.displayName + ", 数据帧数: " + data.size());

                    hideVideoProgress();
                    selectVideoButton.setEnabled(true);
                    startButton.setEnabled(true);

                    Toast.makeText(this,
                            video.displayName + " 加载完成 - " + data.size() + "帧数据",
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "视频资源不存在: " + video.videoResName, Toast.LENGTH_LONG).show();
                    selectVideoButton.setEnabled(true);
                    hideVideoProgress();
                }

            } catch (Exception e) {
                Log.e(TAG, "加载官方视频失败: " + e.getMessage(), e);
                hideVideoProgress();
                selectVideoButton.setEnabled(true);
                Toast.makeText(this, "加载失败:\n" + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void generateReport() {
        try {
            if (!isTracking && similarityList.isEmpty()) {
                Toast.makeText(this, "暂无运动数据", Toast.LENGTH_SHORT).show();
                return;
            }
            long duration = System.currentTimeMillis() - trackingStartTime;
            int valid = (int) similarityList.stream().filter(f -> f > 0.5f).count();
            Bitmap thumb = captureScreen();

            String sportName = action != null ? action.getActionName() : "运动";
            int count = (action != null && action.hasCounter()) ? squatAnalyzer.getSquatCount() : 0;

            ReportData report = new ReportData(
                    sportName, duration, count,
                    similarityList.isEmpty() ? 0 : (float) similarityList.stream().mapToDouble(f -> f).average().orElse(0),
                    similarityList.size(), valid,
                    new ArrayList<>(similarityList),
                    new ArrayList<>(rtKneeAngles),
                    new ArrayList<>(rtHipAngles),
                    new ArrayList<>(rtTimes),
                    new ArrayList<>(stdKnees),
                    new ArrayList<>(stdHips),
                    thumb);
            try {
                report.saveThumb(this);
            } catch (Exception ignore) {
            }
            Intent i = new Intent(this, ReportActivity.class);
            i.putExtra("REPORT_DATA", report);
            startActivity(i);
        } catch (Exception e) {
            Log.e(TAG, "生成报告失败: " + e.getMessage());
            Toast.makeText(this, "生成报告失败", Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap captureScreen() {
        try {
            View root = getWindow().getDecorView();
            root.setDrawingCacheEnabled(true);
            Bitmap bmp = Bitmap.createBitmap(root.getDrawingCache());
            root.setDrawingCacheEnabled(false);
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "截屏失败: " + e.getMessage());
            return null;
        }
    }

    private void setupAngleDisplay() {
        try {
            if (angleView != null) {
                angleView.setBackgroundColor(Color.argb(128, 0, 0, 0));
                angleView.setTextColor(Color.WHITE);
                angleView.setTextSize(14);
                angleView.setPadding(12, 8, 12, 8);
                if (action != null && "拳击".equals(action.getActionName())) {
                    angleView.setText("左肘: --° | 右肘: --°\n左肩: --° | 右肩: --°");
                } else {
                    angleView.setText("膝: --° / --°\n髋: --° / --°");
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "设置角度显示失败: " + e.getMessage());
        }
    }

    private void adjustCameraButtonPosition() {
        try {
            if (switchCameraButton != null) {
                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) switchCameraButton.getLayoutParams();
                if (params != null) {
                    params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
                    params.addRule(RelativeLayout.ALIGN_PARENT_END);
                    params.topMargin = 20;
                    params.setMarginEnd(20);
                    switchCameraButton.setLayoutParams(params);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "调整摄像头按钮位置失败: " + e.getMessage());
        }
    }

    private void setUsbCameraRotation(UsbDevice device) {
        try {
            if (device == null) {
                usbCameraRotation = 0;
                return;
            }
            Log.d(TAG, "设置USB摄像头旋转角度 - 设备: " + device.getDeviceName());
            usbCameraRotation = 0;
        } catch (Exception e) {
            Log.e(TAG, "设置USB摄像头旋转失败: " + e.getMessage());
            usbCameraRotation = 0;
        }
    }

    private int getCurrentCameraRotation() {
        if (useUsbCamera) {
            return usbCameraRotation;
        } else {
            return 90;
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerUsbReceiver() {
        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(ACTION_USB_PERMISSION);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(usbReceiver, filter, Context.RECEIVER_EXPORTED);
            } else {
                registerReceiver(usbReceiver, filter);
            }
            Log.d(TAG, "USB广播接收器注册成功");
        } catch (Exception e) {
            Log.e(TAG, "USB广播接收器注册失败: " + e.getMessage());
        }
    }

    private void checkUsbCamera() {
        try {
            HashMap<String, UsbDevice> deviceList = usbManager.getDeviceList();
            usbCameraAvailable = false;
            useUsbCamera = false;
            usbCameraPermissionGranted = false;

            for (UsbDevice device : deviceList.values()) {
                if (isUsbCamera(device)) {
                    usbCameraAvailable = true;
                    currentUsbDevice = device;
                    setUsbCameraRotation(device);

                    if (usbManager.hasPermission(device)) {
                        usbCameraPermissionGranted = true;
                        useUsbCamera = true;
                        new Handler(Looper.getMainLooper()).postDelayed(() -> restartCamera(), 500);
                    } else {
                        int flags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ?
                                PendingIntent.FLAG_MUTABLE : 0;
                        PendingIntent usbPermissionIntent = PendingIntent.getBroadcast(this, 0,
                                new Intent(ACTION_USB_PERMISSION), flags);
                        usbManager.requestPermission(device, usbPermissionIntent);
                    }
                    break;
                }
            }
            if (!usbCameraAvailable) {
                restartCamera();
            }
        } catch (Exception e) {
            restartCamera();
        }
    }

    private boolean isUsbCamera(UsbDevice device) {
        try {
            for (int i = 0; i < device.getInterfaceCount(); i++) {
                android.hardware.usb.UsbInterface usbInterface = device.getInterface(i);
                if (usbInterface.getInterfaceClass() == 14 || usbInterface.getInterfaceSubclass() == 2) {
                    return true;
                }
            }
            String deviceName = device.getDeviceName().toLowerCase();
            if (deviceName.contains("camera") || deviceName.contains("video") ||
                    deviceName.contains("uvc") || deviceName.contains("usb")) {
                return true;
            }
            int vendorId = device.getVendorId();
            if (vendorId == 0x046d || vendorId == 0x04f2 || vendorId == 0x0c45 ||
                    vendorId == 0x1bcf || vendorId == 0x1e4e || vendorId == 0x5986 ||
                    vendorId == 0x18ec || vendorId == 0x05a3 || vendorId == 0x1908) {
                return true;
            }
        } catch (Exception e) {
            Log.e(TAG, "判断USB摄像头失败: " + e.getMessage());
        }
        return false;
    }


    private void startCamera() {
        if (!allPermissionsGranted()) return;
        try {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            cameraProviderFuture.addListener(() -> {
                try {
                    cameraProvider = cameraProviderFuture.get();
                    if (cameraProvider != null) {
                        bindCameraUseCases();
                    }
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "摄像头初始化失败", e);
                }
            }, ContextCompat.getMainExecutor(this));
        } catch (Exception e) {
            Log.e(TAG, "启动摄像头异常: " + e.getMessage(), e);
        }
    }

    @OptIn(markerClass = ExperimentalLensFacing.class)
    private void bindCameraUseCases() {
        if (cameraProvider == null) return;

        try {
            cameraProvider.unbindAll();
            CameraSelector cameraSelector;
            String cameraType;

            if (useUsbCamera && usbCameraAvailable && usbCameraPermissionGranted) {
                try {
                    cameraSelector = new CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_EXTERNAL)
                            .build();
                    if (isCameraAvailable(cameraSelector)) {
                        cameraType = "USB摄像头(外部)";
                    } else {
                        cameraSelector = createFallbackCameraSelector();
                        cameraType = "USB摄像头(备用方案)";
                    }
                } catch (Exception e) {
                    useUsbCamera = false;
                    switchToBackupCamera();
                    return;
                }
            } else {
                cameraSelector = createFallbackCameraSelector();
                cameraType = (cameraFacing == CameraSelector.LENS_FACING_FRONT) ? "前置摄像头" : "后置摄像头";
            }

            bindCameraWithSelector(cameraSelector, cameraType);
        } catch (Exception e) {
            switchToBackupCamera();
        }
    }

    private CameraSelector createFallbackCameraSelector() {
        try {
            CameraSelector selector = new CameraSelector.Builder()
                    .requireLensFacing(cameraFacing)
                    .build();
            if (isCameraAvailable(selector)) {
                return selector;
            }
        } catch (Exception e) {
            Log.w(TAG, "首选摄像头方向不可用");
        }

        if (cameraFacing == CameraSelector.LENS_FACING_BACK) {
            try {
                CameraSelector wideSelector = selectWidestBackCamera();
                if (isCameraAvailable(wideSelector)) {
                    return wideSelector;
                }
            } catch (Exception e) {
                Log.w(TAG, "广角后置摄像头不可用");
            }
        }

        try {
            CameraSelector selector = CameraSelector.DEFAULT_BACK_CAMERA;
            if (isCameraAvailable(selector)) {
                cameraFacing = CameraSelector.LENS_FACING_BACK;
                return selector;
            }
        } catch (Exception e) {
        }

        try {
            CameraSelector selector = CameraSelector.DEFAULT_FRONT_CAMERA;
            if (isCameraAvailable(selector)) {
                cameraFacing = CameraSelector.LENS_FACING_FRONT;
                return selector;
            }
        } catch (Exception e) {
        }

        throw new RuntimeException("没有可用的摄像头");
    }

    private boolean isCameraAvailable(CameraSelector selector) {
        try {
            if (cameraProvider == null) return false;
            return cameraProvider.hasCamera(selector);
        } catch (Exception e) {
            return false;
        }
    }

    private void bindCameraWithSelector(CameraSelector cameraSelector, String cameraType) {
        try {
            cameraProvider.unbindAll();

            Preview preview = new Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build();

            ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build();

            imageAnalysis.setAnalyzer(backgroundExecutor, this::analyzeImage);

            Camera camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

            // 超出部分自动裁剪
            viewFinder.setScaleType(PreviewView.ScaleType.FILL_CENTER);
            viewFinder.setImplementationMode(PreviewView.ImplementationMode.COMPATIBLE);

            // 确保布局参数正确
            viewFinder.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER
            ));

            if (videoOverlayView != null) {
                videoOverlayView.setFrontCamera(cameraFacing == CameraSelector.LENS_FACING_FRONT);
            }

        } catch (Exception e) {
            Log.e(TAG, "绑定相机失败: " + e.getMessage(), e);
            throw e;
        }
    }

    private void toggleCamera() {
        if (useUsbCamera) {
            useUsbCamera = false;
            cameraFacing = CameraSelector.LENS_FACING_BACK;
        } else {
            checkUsbCamera();
            if (usbCameraAvailable && usbCameraPermissionGranted) {
                useUsbCamera = true;
            } else {
                cameraFacing = (cameraFacing == CameraSelector.LENS_FACING_FRONT) ?
                        CameraSelector.LENS_FACING_BACK : CameraSelector.LENS_FACING_FRONT;
                useUsbCamera = false;
            }
        }
        restartCamera();
        if (videoOverlayView != null) {
            videoOverlayView.setFrontCamera(cameraFacing == CameraSelector.LENS_FACING_FRONT);
        }
    }

    private void restartCamera() {
        if (cameraProvider != null) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    bindCameraUseCases();
                } catch (Exception e) {
                    Log.e(TAG, "重启摄像头失败: " + e.getMessage());
                }
            }, 300);
        }
    }

    private void switchToBackupCamera() {
        useUsbCamera = false;
        usbCameraAvailable = false;
        usbCameraPermissionGranted = false;
        cameraFacing = CameraSelector.LENS_FACING_BACK;
        runOnUiThread(() -> Toast.makeText(this, "USB摄像头不可用，已切换到手机摄像头", Toast.LENGTH_LONG).show());
        new Handler(Looper.getMainLooper()).postDelayed(() -> restartCamera(), 500);
    }

    @NonNull
    private CameraSelector selectWidestBackCamera() {
        if (cameraProvider == null) {
            return CameraSelector.DEFAULT_BACK_CAMERA;
        }
        List<CameraInfo> backs = new ArrayList<>();
        for (CameraInfo info : cameraProvider.getAvailableCameraInfos()) {
            if (info.getLensFacing() == CameraSelector.LENS_FACING_BACK) {
                backs.add(info);
            }
        }
        if (backs.size() <= 1) {
            return CameraSelector.DEFAULT_BACK_CAMERA;
        }
        final CameraInfo wideCandidate = backs.get(1);
        return new CameraSelector.Builder()
                .addCameraFilter(cameraInfos -> Collections.singletonList(wideCandidate))
                .build();
    }

    // 图像分析
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(ImageProxy image) {
        if (poseLandmarkerHelper == null || poseLandmarkerHelper.isClose() || isFinishing()) {
            image.close();
            return;
        }
        try {
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            setCurrentImageSize(imageWidth, imageHeight);

            Bitmap bitmap = imageProxyToBitmap(image);
            if (bitmap != null && poseLandmarkerHelper != null) {
                poseLandmarkerHelper.detectLiveStreamFrame(bitmap);
                if (!bitmap.isRecycled()) {
                    bitmap.recycle();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "图像分析失败: " + e.getMessage());
        } finally {
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

    // 视频处理
    private void selectVideo() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("video/*");
            startActivityForResult(intent, REQUEST_VIDEO_PICK);
        } catch (Exception e) {
            Log.e(TAG, "选择视频失败: " + e.getMessage());
            Toast.makeText(this, "无法打开文件选择器", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_VIDEO_PICK && resultCode == RESULT_OK && data != null) {
            runOnUiThread(() -> {
                showVideoProgress("正在准备处理视频...", 0);
                selectVideoButton.setEnabled(false);
                startButton.setEnabled(false);

                // 隐藏占位符
                LinearLayout videoPlaceholder = findViewById(R.id.videoPlaceholder);
                if (videoPlaceholder != null) {
                    videoPlaceholder.setVisibility(View.GONE);
                }
            });

            try {
                resetForNewVideo();
                customStandardVideoUri = data.getData();

                // 立即显示选择的视频
                if (player != null) {
                    player.release();
                }

                player = new SimpleExoPlayer.Builder(this).build();
                exoPlayerView.setPlayer(player);
                exoPlayerView.setUseController(true);

                DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                        Util.getUserAgent(this, getApplicationInfo().loadLabel(getPackageManager()).toString()));

                MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(customStandardVideoUri));

                player.setMediaSource(videoSource);
                player.prepare();
                player.setRepeatMode(Player.REPEAT_MODE_ALL);
                player.setPlayWhenReady(true);

                player.addListener(new Player.Listener() {
                    @Override
                    public void onPlaybackStateChanged(int state) {
                        if (state == Player.STATE_READY) {
                            Log.d(TAG, "自定义视频播放器准备就绪");
                        }
                    }

                    @Override
                    public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                                        @NonNull Player.PositionInfo newPosition,
                                                        int reason) {
                        updateStandardFramePointer();
                        updateStandardVideoOverlay();
                    }
                });

                // 处理视频姿态数据
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    processCustomVideoForPoseData();
                }, 500);

            } catch (Exception e) {
                Log.e(TAG, "处理视频失败: " + e.getMessage(), e);
                hideVideoProgress();
                selectVideoButton.setEnabled(true);
                Toast.makeText(this, "处理视频失败", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void initializeExoPlayer() {
        try {
            player = new SimpleExoPlayer.Builder(this).build();
            exoPlayerView.setPlayer(player);
            exoPlayerView.setUseController(false);
            player.setVolume(0f);
            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                    Util.getUserAgent(this, getApplicationInfo().loadLabel(getPackageManager()).toString()));

            MediaSource videoSource;
            try {
                videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.parse("asset:///squat_standard.mp4")));
            } catch (Exception e) {
                videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                        .createMediaSource(MediaItem.fromUri(Uri.EMPTY));
            }

            player.setMediaSource(videoSource);
            player.prepare();
            player.setRepeatMode(Player.REPEAT_MODE_ALL);
            player.setPlayWhenReady(false);

            player.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.e(TAG, "播放器错误: " + error.getMessage());
                }

                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        Log.d(TAG, "ExoPlayer准备就绪");
                    }
                }

                @Override
                public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                                    @NonNull Player.PositionInfo newPosition, int reason) {
                    updateStandardFramePointer();
                    updateStandardVideoOverlay();
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "ExoPlayer初始化失败: " + e.getMessage());
        }
    }

    private void initializeExoPlayerWithCustomVideo() {
        try {
            if (player != null) {
                player.release();
            }

            player = new SimpleExoPlayer.Builder(this)
                    .setSeekBackIncrementMs(5000)
                    .setSeekForwardIncrementMs(5000)
                    .build();

            exoPlayerView.setPlayer(player);
            exoPlayerView.setUseController(false);
            player.setVolume(0f);

            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(this,
                    Util.getUserAgent(this, getApplicationInfo().loadLabel(getPackageManager()).toString()));

            MediaSource videoSource = new ProgressiveMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(customStandardVideoUri));

            player.setMediaSource(videoSource);
            player.prepare();
            player.setRepeatMode(Player.REPEAT_MODE_ALL);  // 确保循环播放
            player.setPlayWhenReady(true);
            player.setPlaybackParameters(new PlaybackParameters(1.0f));

            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    switch (state) {
                        case Player.STATE_READY:
                            player.setPlayWhenReady(true);
                            break;
                        case Player.STATE_ENDED:
                            // 视频结束时自动从头播放（配合 REPEAT_MODE_ALL 应该已经自动处理）
                            Log.d(TAG, "视频播放结束，自动循环");
                            break;
                    }
                }

                @Override
                public void onPlayerError(PlaybackException error) {
                    new Handler().postDelayed(() -> {
                        if (customStandardVideoUri != null) {
                            initializeExoPlayerWithCustomVideo();
                        }
                    }, 1000);
                }

                @Override
                public void onVideoSizeChanged(com.google.android.exoplayer2.video.VideoSize size) {
                    updateVideoDisplayRect();
                }

                @Override
                public void onPositionDiscontinuity(@NonNull Player.PositionInfo oldPosition,
                                                    @NonNull Player.PositionInfo newPosition, int reason) {
                    updateStandardFramePointer();
                    updateStandardVideoOverlay();
                }

                @Override
                public void onIsPlayingChanged(boolean isPlaying) {
                    if (isPlaying) {
                        updateStandardVideoOverlay();
                    }
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "自定义视频播放器初始化失败: " + e.getMessage());
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

   //AI辅助生成 Kimi k2.5 2025.12.10

    /**
     * 处理自定义视频 - 带缓存检查
     */
    private void processCustomVideoForPoseData() {
        Log.d(TAG, "processCustomVideoForPoseData 被调用（带缓存检查）");

        // 检查 videoProcessor
        if (videoProcessor == null) {
            Log.e(TAG, "videoProcessor 为 null，尝试重新初始化");
            try {
                videoProcessor = new VideoProcessor(this);
                if (videoProcessor == null) {
                    hideVideoProgress();
                    selectVideoButton.setEnabled(true);
                    Toast.makeText(this, "视频处理器初始化失败", Toast.LENGTH_SHORT).show();
                    return;
                }
                Log.d(TAG, "VideoProcessor 重新初始化成功");
            } catch (Exception e) {
                Log.e(TAG, "重新初始化失败: " + e.getMessage());
                hideVideoProgress();
                selectVideoButton.setEnabled(true);
                Toast.makeText(this, "视频处理器初始化失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (customStandardVideoUri == null) {
            Log.e(TAG, "customStandardVideoUri 为 null");
            hideVideoProgress();
            selectVideoButton.setEnabled(true);
            Toast.makeText(this, "视频 URI 无效", Toast.LENGTH_SHORT).show();
            return;
        }

        isVideoProcessing = true;
        showVideoProgress("正在检查服务器缓存...", 0);
        selectVideoButton.setEnabled(false);
        startButton.setEnabled(false);

        // 先检查缓存
        VideoCacheManager.getInstance(this).checkVideoCache(customStandardVideoUri, new VideoCacheManager.CacheCheckCallback() {
            @Override
            public void onCacheFound(List<FrameData> cachedFrames, String sha256) {
                // 使用缓存数据 - 秒解析成功
                runOnUiThread(() -> {
                    Log.d(TAG, "使用服务器缓存数据，帧数: " + cachedFrames.size());
                    hideVideoProgress();
                    isVideoProcessing = false;

                    synchronized (standardVideoData) {
                        standardVideoData.clear();
                        standardVideoData.addAll(cachedFrames);
                        standardVideoFps = 30f; // 缓存数据使用默认FPS
                        isStandardDataReady = true;
                    }

                    // 获取视频旋转信息
                    int rotation = getVideoRotation(customStandardVideoUri);
                    if (videoOverlayView != null) {
                        videoOverlayView.setRotationDegrees(rotation);
                        videoOverlayView.setFrontCamera(cameraFacing == CameraSelector.LENS_FACING_FRONT);
                        updateVideoDisplayRect();
                    }

                    startButton.setEnabled(true);
                    selectVideoButton.setEnabled(true);

                    int validFrames = 0;
                    for (FrameData frame : standardVideoData) {
                        if (frame.hasValidPose) validFrames++;
                    }

                    Toast.makeText(RealtimeActivity.this,
                            String.format("秒解析成功！使用服务器缓存 - %d/%d帧有效", validFrames, standardVideoData.size()),
                            Toast.LENGTH_SHORT).show();

                    updateStandardVideoOverlay();
                    if (videoOverlayView != null) {
                        videoOverlayView.invalidate();
                    }

                    // 确保视频自动循环播放
                    setupVideoAutoPlay();
                });
            }

            @Override
            public void onCacheNotFound(String sha256) {
                // 没有缓存，进行本地分析
                runOnUiThread(() -> {
                    Log.d(TAG, "缓存未找到，进行本地分析");
                    processCustomVideoInternal(sha256);
                });
            }

            @Override
            public void onError(String error) {
                // 出错时降级处理
                Log.w(TAG, "缓存检查失败: " + error);
                runOnUiThread(() -> {
                    processCustomVideoInternal(null);
                });
            }
        });
    }

    /**
     * 实际处理自定义视频（本地分析）
     */
    private void processCustomVideoInternal(String sha256) {
        showVideoProgress("正在解析视频...", 0);

        videoProcessor.processVideo(customStandardVideoUri, new VideoProcessor.VideoProcessingCallback() {
            @Override
            public void onProgress(int progress) {
                runOnUiThread(() -> showVideoProgress("正在解析视频...", progress));
            }

            @Override
            public void onComplete(List<VideoProcessor.VideoFrameData> frameDataList, int rotation, float frameRate) {
                runOnUiThread(() -> {
                    Log.d(TAG, "视频处理完成，帧数: " + frameDataList.size());
                    hideVideoProgress();

                    synchronized (standardVideoData) {
                        standardVideoData.clear();
                        for (VideoProcessor.VideoFrameData videoFrame : frameDataList) {
                            standardVideoData.add(new FrameData(videoFrame));
                        }
                        standardVideoFps = frameRate;
                        isStandardDataReady = true;
                    }

                    if (videoOverlayView != null) {
                        videoOverlayView.setRotationDegrees(rotation);
                        videoOverlayView.setFrontCamera(cameraFacing == CameraSelector.LENS_FACING_FRONT);
                        updateVideoDisplayRect();
                    }

                    startButton.setEnabled(true);
                    selectVideoButton.setEnabled(true);
                    isVideoProcessing = false;

                    int validFrames = 0;
                    for (FrameData frame : standardVideoData) {
                        if (frame.hasValidPose) validFrames++;
                    }

                    Toast.makeText(RealtimeActivity.this,
                            String.format("本地分析完成 - %d/%d帧有效", validFrames, standardVideoData.size()),
                            Toast.LENGTH_LONG).show();

                    updateStandardVideoOverlay();
                    if (videoOverlayView != null) {
                        videoOverlayView.invalidate();
                    }

                    // 上传到服务器缓存（如果sha256不为空）
                    if (sha256 != null && !frameDataList.isEmpty()) {
                        uploadAnalysisResultToServer(sha256, frameDataList);
                    }

                    // 确保视频自动循环播放
                    setupVideoAutoPlay();
                    // 添加静音设置
                    if (player != null) {
                        player.setVolume(0);
                    }
                });
            }

            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Log.e(TAG, "视频处理错误: " + error);
                    hideVideoProgress();
                    selectVideoButton.setEnabled(true);
                    startButton.setEnabled(false);
                    isVideoProcessing = false;
                    Toast.makeText(RealtimeActivity.this, "视频处理失败: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    /**
     * 上传分析结果到服务器
     */
    private void uploadAnalysisResultToServer(String sha256, List<VideoProcessor.VideoFrameData> frameDataList) {
        Log.d(TAG, "正在上传分析结果到服务器...");

        VideoCacheManager.getInstance(this).uploadAnalysisResult(sha256, customStandardVideoUri, frameDataList,
                new VideoCacheManager.UploadCallback() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "分析结果上传成功，下次可使用秒解析");
                    }

                    @Override
                    public void onError(String error) {
                        Log.w(TAG, "分析结果上传失败: " + error);
                    }
                });
    }

    /**
     * 获取视频旋转角度
     */
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

    /**
     * 设置视频自动循环播放
     * 确保视频准备好后自动开始播放并循环
     */
    private void setupVideoAutoPlay() {
        if (player == null) {
            Log.w(TAG, "播放器为空，无法设置自动播放");
            return;
        }
        player.setVolume(0f);
        // 确保循环模式已设置
        player.setRepeatMode(Player.REPEAT_MODE_ALL);

        // 如果播放器还未准备就绪，等待准备完成后再开始播放
        int playbackState = player.getPlaybackState();
        if (playbackState == Player.STATE_READY) {
            player.setPlayWhenReady(true);
            Log.d(TAG, "视频已就绪，开始自动循环播放");
        } else if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_BUFFERING) {
            // 添加监听器等待播放器准备就绪
            player.addListener(new Player.Listener() {
                @Override
                public void onPlaybackStateChanged(int state) {
                    if (state == Player.STATE_READY) {
                        player.setPlayWhenReady(true);
                        player.removeListener(this);
                        Log.d(TAG, "视频准备就绪，开始自动循环播放");
                    }
                }
            });
        } else if (playbackState == Player.STATE_ENDED) {
            // 如果已结束，从头开始播放
            player.seekTo(0);
            player.setPlayWhenReady(true);
            Log.d(TAG, "视频已结束，重新开始循环播放");
        }
    }


    @Override
    public void onError(String error, int errorCode) {
        runOnUiThread(() -> {
            Log.e(TAG, "姿态检测错误: " + error);
        });
    }

    @Override
    public void onResults(PoseLandmarkerResult result, long inferenceTime) {
        runOnUiThread(() -> {
            try {
                int currentRotation = getCurrentCameraRotation();
                if (overlayView != null) {
                    overlayView.setRotationDegrees(currentRotation);
                    overlayView.setFrontCamera(cameraFacing == CameraSelector.LENS_FACING_FRONT);
                    overlayView.setPoseResults(result, inferenceTime);
                }

                updateStandardVideoOverlay();
                updateStandardFramePointer();

                // 30秒检测期逻辑
                if (isWaitingForDetection && !isTracking) {
                    if (result != null && !result.landmarks().isEmpty()) {
                        List<NormalizedLandmark> landmarks = result.landmarks().get(0);
                        boolean currentPoseValid = areRequiredJointsVisible(landmarks);

                        if (currentPoseValid) {
                            successfulDetectionCount++;
//                            Log.d(TAG, "有效姿势检测计数: " + successfulDetectionCount + "/" + DETECTION_REQUIRED_COUNT);

                            if (squatCountView != null) {
                                if (action != null && action.hasCounter()) {
                                    squatCountView.setVisibility(View.VISIBLE);
//                                    squatCountView.setText(actionName + "检测: " + successfulDetectionCount + "/" + DETECTION_REQUIRED_COUNT);
                                } else {
                                    squatCountView.setVisibility(View.GONE);
                                }
                            }

                            if (successfulDetectionCount >= DETECTION_REQUIRED_COUNT) {
                                isWaitingForDetection = false;
                                startCountdown();
                            }
                        } else {
                            successfulDetectionCount = 0;
                        }
                        return;
                    } else {
                        successfulDetectionCount = 0;
                    }
                }

                // 获取标准帧角度数据
                float standardKneeAngle = -1f;
                float standardHipAngle = -1f;
                float standardElbowAngle = -1f;
                float standardShoulderAngle = -1f;
                float standardLeftElbowAngle = -1f;
                float standardRightElbowAngle = -1f;
                float standardLeftShoulderAngle = -1f;
                float standardRightShoulderAngle = -1f;

                if (shouldCalculateAngles && isStandardDataReady && !standardVideoData.isEmpty() &&
                        standardFramePointer < standardVideoData.size()) {
                    FrameData currentStandardFrame = standardVideoData.get(standardFramePointer);
                    standardKneeAngle = currentStandardFrame.kneeAngle;
                    standardHipAngle = currentStandardFrame.hipAngle;
                    standardElbowAngle = currentStandardFrame.elbowAngle;
                    standardShoulderAngle = currentStandardFrame.shoulderAngle;
                    standardLeftElbowAngle = currentStandardFrame.leftElbowAngle;
                    standardRightElbowAngle = currentStandardFrame.rightElbowAngle;
                    standardLeftShoulderAngle = currentStandardFrame.leftShoulderAngle;
                    standardRightShoulderAngle = currentStandardFrame.rightShoulderAngle;

                    synchronized (this) {
                        if (stdKnees.size() <= standardFramePointer) stdKnees.add(standardKneeAngle);
                        if (stdHips.size() <= standardFramePointer) stdHips.add(standardHipAngle);
                        if (stdElbows.size() <= standardFramePointer) stdElbows.add(standardElbowAngle);
                        if (stdShoulders.size() <= standardFramePointer) stdShoulders.add(standardShoulderAngle);
                        if (stdLeftElbows.size() <= standardFramePointer) stdLeftElbows.add(standardLeftElbowAngle);
                        if (stdRightElbows.size() <= standardFramePointer) stdRightElbows.add(standardRightElbowAngle);
                        if (stdLeftShoulders.size() <= standardFramePointer) stdLeftShoulders.add(standardLeftShoulderAngle);
                        if (stdRightShoulders.size() <= standardFramePointer) stdRightShoulders.add(standardRightShoulderAngle);
                    }
                }

                if (result != null && !result.landmarks().isEmpty()) {
                    List<NormalizedLandmark> landmarks = result.landmarks().get(0);
                    boolean currentPoseValid = areRequiredJointsVisible(landmarks);
                    long currentTime = System.currentTimeMillis();

                    if (currentPoseValid) {
                        lastValidPoseTime = currentTime;
                        hasRequiredJoints = true;
                        if (hasShownPoseLostHint) hasShownPoseLostHint = false;
                    } else {
                        if (currentTime - lastValidPoseTime > POSE_TIMEOUT_MS) {
                            hasRequiredJoints = false;
                            if (isTracking && !hasShownPoseLostHint) {
                                hasShownPoseLostHint = true;
                                Toast.makeText(this, "姿态丢失", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }

                    if (shouldCalculateAngles && hasRequiredJoints) {
                        int currentWidth = getCurrentImageWidth();
                        int currentHeight = getCurrentImageHeight();

                        double[] realTimeAngles = calculateKeyAnglesWithSize(landmarks, currentWidth, currentHeight);
                        float realTimeKneeAngle = (float) realTimeAngles[0];
                        float realTimeHipAngle = (float) realTimeAngles[1];
                        float realTimeElbowAngle = (float) realTimeAngles[2];
                        float realTimeShoulderAngle = (float) realTimeAngles[3];

                        float realTimeLeftElbowAngle = calculateLeftElbowAngle(landmarks, currentWidth, currentHeight);
                        float realTimeRightElbowAngle = calculateRightElbowAngle(landmarks, currentWidth, currentHeight);
                        float realTimeLeftShoulderAngle = calculateLeftShoulderAngle(landmarks, currentWidth, currentHeight);
                        float realTimeRightShoulderAngle = calculateRightShoulderAngle(landmarks, currentWidth, currentHeight);

                        boolean isBoxing = action != null && "拳击".equals(action.getActionName());

                        if (isBoxing) {
                            boolean isStatic = detectStaticStateForBoxing(
                                    realTimeLeftElbowAngle, realTimeRightElbowAngle,
                                    realTimeLeftShoulderAngle, realTimeRightShoulderAngle);

                            if (isStatic) {
                                isStaticState = true;
                                if (!hasShownStaticHint) {
                                    hasShownStaticHint = true;
                                    showStaticHint();
                                }
                                updateAngleDisplayStaticForBoxing(
                                        realTimeLeftElbowAngle, realTimeRightElbowAngle,
                                        realTimeLeftShoulderAngle, realTimeRightShoulderAngle);
                                squatCountView.setVisibility(View.GONE);
                                overlayView.setSquatInfo(0, 0f);
                                return;
                            } else {
                                if (isStaticState) {
                                    isStaticState = false;
                                    resetStaticDetection();
                                    hideStaticHint();
                                }
                            }
                        } else {
                            boolean isStatic = detectStaticState(realTimeKneeAngle, realTimeHipAngle,
                                    realTimeElbowAngle, realTimeShoulderAngle);

                            if (isStatic) {
                                isStaticState = true;
                                if (!hasShownStaticHint) {
                                    hasShownStaticHint = true;
                                    showStaticHint();
                                }
                                updateAngleDisplayStatic(realTimeKneeAngle, realTimeHipAngle,
                                        realTimeElbowAngle, realTimeShoulderAngle);


                                return;
                            } else {
                                if (isStaticState) {
                                    isStaticState = false;
                                    resetStaticDetection();
                                    hideStaticHint();
                                }
                            }
                        }

                        // 使用新的实时相似度计算方法
                        calculateRealTimeSimilarity(landmarks);

                        // 动作计数（仅深蹲）
                        if (action != null && action.hasCounter()) {
                            SquatAnalyzer.SquatAnalysis analysis = squatAnalyzer.analyzeSquat(landmarks);
                            squatCountView.setVisibility(View.VISIBLE);
                            squatCountView.setText(actionName + ": " + analysis.getSquatCount());
                        } else {
                            squatCountView.setVisibility(View.GONE);
                        }

                    } else if (isTracking && !hasRequiredJoints) {
                        if (action != null && action.hasCounter()) {
                            squatCountView.setVisibility(View.VISIBLE);
                            squatCountView.setText(actionName + ": 等待完整姿态");
                        } else {
                            squatCountView.setVisibility(View.GONE);
                        }
                        overlayView.setSquatInfo(0, 0f);
                    }

                } else {
                    updateAngleDisplay(-1f, -1f, -1f, -1f, -1f, -1f, -1f, -1f, -1, 0f);
                    hasRequiredJoints = false;
                    if (isTracking) {
                        if (action != null && action.hasCounter()) {
                            squatCountView.setVisibility(View.VISIBLE);
                            squatCountView.setText(actionName + ": 未检测到姿态");
                        } else {
                            squatCountView.setVisibility(View.GONE);
                        }
                        overlayView.setSquatInfo(0, 0f);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "姿态结果处理失败: " + e.getMessage());
            }
        });
    }

    private boolean areRequiredJointsVisible(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() < 33) {
            return false;
        }

        int visibleCount = 0;
        for (int index : REQUIRED_LANDMARK_INDICES) {
            if (index < landmarks.size()) {
                NormalizedLandmark landmark = landmarks.get(index);
                if (landmark != null && landmark.visibility().isPresent() &&
                        landmark.visibility().get() >= MIN_VISIBILITY_THRESHOLD &&
                        landmark.x() >= 0 && landmark.x() <= 1 &&
                        landmark.y() >= 0 && landmark.y() <= 1) {
                    visibleCount++;
                }
            }
        }

        return visibleCount >= 9;
    }


    private static boolean areLandmarksValidForAngleCalculation(List<NormalizedLandmark> landmarks, int... indices) {
        if (landmarks == null || landmarks.size() < 33) {
            return false;
        }

        for (int index : indices) {
            if (index >= landmarks.size()) {
                return false;
            }
            NormalizedLandmark landmark = landmarks.get(index);
            if (landmark == null || !landmark.visibility().isPresent() ||
                    landmark.visibility().get() < MIN_VISIBILITY_THRESHOLD) {
                return false;
            }
        }
        return true;
    }

    public static double calculateAngleWithSize(NormalizedLandmark a, NormalizedLandmark b, NormalizedLandmark c,
                                                int imageWidth, int imageHeight) {
        try {
            double aX = a.x() * imageWidth;
            double aY = a.y() * imageHeight;
            double bX = b.x() * imageWidth;
            double bY = b.y() * imageHeight;
            double cX = c.x() * imageWidth;
            double cY = c.y() * imageHeight;

            double baX = aX - bX;
            double baY = aY - bY;
            double bcX = cX - bX;
            double bcY = cY - bY;

            double dotProduct = (baX * bcX) + (baY * bcY);
            double magnitudeBA = Math.sqrt(baX * baX + baY * baY);
            double magnitudeBC = Math.sqrt(bcX * bcX + bcY * bcY);

            if (magnitudeBA < 0.001 || magnitudeBC < 0.001) {
                return 0.0;
            }

            double cosAngle = dotProduct / (magnitudeBA * magnitudeBC);
            cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));

            return Math.toDegrees(Math.acos(cosAngle));

        } catch (Exception e) {
            Log.e(TAG, "角度计算错误: " + e.getMessage());
            return 0.0;
        }
    }

    public static double calculateAngle(NormalizedLandmark a, NormalizedLandmark b, NormalizedLandmark c) {
        try {
            double baX = a.x() - b.x();
            double baY = a.y() - b.y();
            double bcX = c.x() - b.x();
            double bcY = c.y() - b.y();

            double dotProduct = (baX * bcX) + (baY * bcY);
            double magnitudeBA = Math.sqrt(baX * baX + baY * baY);
            double magnitudeBC = Math.sqrt(bcX * bcX + bcY * bcY);

            if (magnitudeBA < 0.001 || magnitudeBC < 0.001) {
                return 0.0;
            }

            double cosAngle = dotProduct / (magnitudeBA * magnitudeBC);
            cosAngle = Math.max(-1.0, Math.min(1.0, cosAngle));
            return Math.toDegrees(Math.acos(cosAngle));

        } catch (Exception e) {
            Log.e(TAG, "角度计算错误: " + e.getMessage());
            return 0.0;
        }
    }

    public static double[] calculateKeyAnglesWithSize(List<NormalizedLandmark> landmarks, int imageWidth, int imageHeight) {
        double kneeAngle = 0, hipAngle = 0, elbowAngle = 0, shoulderAngle = 0;

        try {
            // 膝关节
            double leftKneeAngle = 0, rightKneeAngle = 0;
            boolean leftKneeValid = false, rightKneeValid = false;

            if (landmarks.size() > PoseLandmark.LEFT_ANKLE &&
                    areLandmarksValidForAngleCalculation(landmarks,
                            PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE, PoseLandmark.LEFT_ANKLE)) {
                leftKneeAngle = calculateAngleWithSize(
                        landmarks.get(PoseLandmark.LEFT_HIP),
                        landmarks.get(PoseLandmark.LEFT_KNEE),
                        landmarks.get(PoseLandmark.LEFT_ANKLE),
                        imageWidth, imageHeight);
                leftKneeValid = true;
            }

            if (landmarks.size() > PoseLandmark.RIGHT_ANKLE &&
                    areLandmarksValidForAngleCalculation(landmarks,
                            PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE, PoseLandmark.RIGHT_ANKLE)) {
                rightKneeAngle = calculateAngleWithSize(
                        landmarks.get(PoseLandmark.RIGHT_HIP),
                        landmarks.get(PoseLandmark.RIGHT_KNEE),
                        landmarks.get(PoseLandmark.RIGHT_ANKLE),
                        imageWidth, imageHeight);
                rightKneeValid = true;
            }

            if (leftKneeValid && rightKneeValid) {
                kneeAngle = (leftKneeAngle + rightKneeAngle) / 2.0;
            } else if (leftKneeValid) {
                kneeAngle = leftKneeAngle;
            } else if (rightKneeValid) {
                kneeAngle = rightKneeAngle;
            }

            // 髋关节
            double leftHipAngle = 0, rightHipAngle = 0;
            boolean leftHipValid = false, rightHipValid = false;

            if (landmarks.size() > PoseLandmark.LEFT_KNEE &&
                    areLandmarksValidForAngleCalculation(landmarks,
                            PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_KNEE)) {
                leftHipAngle = calculateAngleWithSize(
                        landmarks.get(PoseLandmark.LEFT_SHOULDER),
                        landmarks.get(PoseLandmark.LEFT_HIP),
                        landmarks.get(PoseLandmark.LEFT_KNEE),
                        imageWidth, imageHeight);
                leftHipValid = true;
            }

            if (landmarks.size() > PoseLandmark.RIGHT_KNEE &&
                    areLandmarksValidForAngleCalculation(landmarks,
                            PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_KNEE)) {
                rightHipAngle = calculateAngleWithSize(
                        landmarks.get(PoseLandmark.RIGHT_SHOULDER),
                        landmarks.get(PoseLandmark.RIGHT_HIP),
                        landmarks.get(PoseLandmark.RIGHT_KNEE),
                        imageWidth, imageHeight);
                rightHipValid = true;
            }

            if (leftHipValid && rightHipValid) {
                hipAngle = (leftHipAngle + rightHipAngle) / 2.0;
            } else if (leftHipValid) {
                hipAngle = leftHipAngle;
            } else if (rightHipValid) {
                hipAngle = rightHipAngle;
            }

            // 肘关节（平均）
            double leftElbowAngle = 0, rightElbowAngle = 0;
            boolean leftElbowValid = false, rightElbowValid = false;

            if (landmarks.size() > PoseLandmark.LEFT_WRIST &&
                    areLandmarksValidForAngleCalculation(landmarks,
                            PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW, PoseLandmark.LEFT_WRIST)) {
                leftElbowAngle = calculateAngleWithSize(
                        landmarks.get(PoseLandmark.LEFT_SHOULDER),
                        landmarks.get(PoseLandmark.LEFT_ELBOW),
                        landmarks.get(PoseLandmark.LEFT_WRIST),
                        imageWidth, imageHeight);
                leftElbowValid = true;
            }

            if (landmarks.size() > PoseLandmark.RIGHT_WRIST &&
                    areLandmarksValidForAngleCalculation(landmarks,
                            PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW, PoseLandmark.RIGHT_WRIST)) {
                rightElbowAngle = calculateAngleWithSize(
                        landmarks.get(PoseLandmark.RIGHT_SHOULDER),
                        landmarks.get(PoseLandmark.RIGHT_ELBOW),
                        landmarks.get(PoseLandmark.RIGHT_WRIST),
                        imageWidth, imageHeight);
                rightElbowValid = true;
            }

            if (leftElbowValid && rightElbowValid) {
                elbowAngle = (leftElbowAngle + rightElbowAngle) / 2.0;
            } else if (leftElbowValid) {
                elbowAngle = leftElbowAngle;
            } else if (rightElbowValid) {
                elbowAngle = rightElbowAngle;
            }

            // 肩关节（平均）
            double leftShoulderAngle = 0, rightShoulderAngle = 0;
            boolean leftShoulderValid = false, rightShoulderValid = false;

            if (landmarks.size() > PoseLandmark.LEFT_ELBOW &&
                    areLandmarksValidForAngleCalculation(landmarks,
                            PoseLandmark.LEFT_HIP, PoseLandmark.LEFT_SHOULDER, PoseLandmark.LEFT_ELBOW)) {
                leftShoulderAngle = calculateAngleWithSize(
                        landmarks.get(PoseLandmark.LEFT_HIP),
                        landmarks.get(PoseLandmark.LEFT_SHOULDER),
                        landmarks.get(PoseLandmark.LEFT_ELBOW),
                        imageWidth, imageHeight);
                leftShoulderValid = true;
            }

            if (landmarks.size() > PoseLandmark.RIGHT_ELBOW &&
                    areLandmarksValidForAngleCalculation(landmarks,
                            PoseLandmark.RIGHT_HIP, PoseLandmark.RIGHT_SHOULDER, PoseLandmark.RIGHT_ELBOW)) {
                rightShoulderAngle = calculateAngleWithSize(
                        landmarks.get(PoseLandmark.RIGHT_HIP),
                        landmarks.get(PoseLandmark.RIGHT_SHOULDER),
                        landmarks.get(PoseLandmark.RIGHT_ELBOW),
                        imageWidth, imageHeight);
                rightShoulderValid = true;
            }

            if (leftShoulderValid && rightShoulderValid) {
                shoulderAngle = (leftShoulderAngle + rightShoulderAngle) / 2.0;
            } else if (leftShoulderValid) {
                shoulderAngle = leftShoulderAngle;
            } else if (rightShoulderValid) {
                shoulderAngle = rightShoulderAngle;
            }

        } catch (Exception e) {
            Log.e(TAG, "实时角度计算错误: " + e.getMessage());
            return new double[]{0, 0, 0, 0};
        }

        return new double[]{kneeAngle, hipAngle, elbowAngle, shoulderAngle};
    }


    private void toggleTracking() {
        if (isTracking) {
            stopTracking();
            return;
        }

        if (isWaitingForDetection) {
            stopTracking();
            return;
        }

        if (countdownText != null && countdownText.getVisibility() == View.VISIBLE) {
            stopTracking();
            return;
        }

        if (!isStandardDataReady || standardVideoData.isEmpty()) {
            Toast.makeText(this, "请先选择标准视频", Toast.LENGTH_SHORT).show();
            return;
        }

        resetDetectionState();
        startDetectionPeriod();
    }

    private void stopCountdown() {
        if (countdownHandler != null) {
            countdownHandler.removeCallbacksAndMessages(null);
        }
        runOnUiThread(() -> {
            if (countdownText != null) {
                countdownText.setVisibility(View.GONE);
            }
        });
    }

    private void saveWorkoutData(WorkoutRecord record) {
        long durationMs = record.getDurationMs();
        if (durationMs < 1000) {
            Log.d(TAG, "运动时长太短（不足1秒），不保存");
            return;
        }


        if (record.getSimilarities().isEmpty() && !similarityList.isEmpty()) {
            for (int i = 0; i < similarityList.size(); i++) {
                long timestampMs = ((long)i * 1000);  // 每帧按1秒间隔
                record.addTimestamp(timestampMs);
                record.addSimilarity(similarityList.get(i));
            }
            // 确保最后一条记录的时间等于总时长
            if (record.getTimeSeriesSize() > 0) {
                record.getTimestamps().set(record.getTimeSeriesSize() - 1, durationMs);
            }
            Log.d(TAG, "从全局列表补充时序数据: " + similarityList.size() + "个点, 总时长=" + durationMs + "ms");
        }

        Log.d(TAG, String.format("保存运动数据: 动作=%s, 时长=%dms (%d秒), 相似度=%.2f%%, 次数=%d, 数据点=%d",
                record.getActionName(), record.getDurationMs(), record.getDurationSeconds(),
                record.getAvgSimilarity() * 100, record.getCount(),
                record.getTimeSeriesSize()));

        workoutDataService.saveWorkoutData(record, new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                Log.e(TAG, "保存运动数据失败: " + e.getMessage());
                runOnUiThread(() -> Toast.makeText(RealtimeActivity.this,
                        "运动数据保存失败，请检查网络", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(okhttp3.Call call, okhttp3.Response response) throws IOException {
                runOnUiThread(() -> Toast.makeText(RealtimeActivity.this,
                        "运动记录已保存", Toast.LENGTH_SHORT).show());
                if (response != null) {
                    response.close();
                }
            }
        });

        // 重置统计数据
        workoutStartTime = 0;
        totalSimilaritySum = 0;
        similarityCount = 0;
    }
    private void stopTracking() {

        runOnUiThread(() -> {
            if (overlayView != null) {
                overlayView.setTrackingState(false);

            }
        });
        if (isTracking && workoutStartTime > 0) {

            int count = (action != null && action.hasCounter()) ? squatAnalyzer.getSquatCount() : 0;
            long currentTime = System.currentTimeMillis();
            long rawDurationMs = currentTime - workoutStartTime;

            // 向上取整到秒（例如：1500ms -> 2000ms, 1000ms -> 1000ms）
            long durationMs = ((rawDurationMs + 999) / 1000) * 1000;
            float avgSimilarity = similarityCount > 0 ? totalSimilaritySum / similarityCount : 0f;

            WorkoutRecord record = new WorkoutRecord(actionName, durationMs, avgSimilarity, count);

            // 添加时序数据（时间向上取整到秒）
            for (int i = 0; i < similarityList.size(); i++) {
                // 时间戳向上取整到秒
                long timestampMs = ((long)i * 1000);  // 每帧按1秒间隔
                record.addTimestamp(timestampMs);
                record.addSimilarity(similarityList.get(i));
            }

            // 确保最后一条记录的时间等于总时长
            if (record.getTimeSeriesSize() > 0) {
                record.getTimestamps().set(record.getTimeSeriesSize() - 1, durationMs);
            }

            Log.d(TAG, String.format("停止运动: 原始时长=%dms, 向上取整后=%dms (%d秒), 数据点=%d",
                    rawDurationMs, durationMs, durationMs / 1000, similarityList.size()));

            // 保存运动数据
            saveWorkoutData(record);
            sendWorkoutDataRefreshBroadcast();
        }


        // 计数器只在 startTrackingImmediately() 中重置，停止时保持计数显示

        isTracking = false;
        shouldCalculateAngles = false;
        needCounting = false;

        isWaitingForDetection = false;
        successfulDetectionCount = 0;
        hasShownDetectionHint = false;
        hasShownPoseLostHint = false;

        resetStaticDetection();
        hideStaticHint();

        runOnUiThread(() -> {
            startButton.setText("开始运动");
            startButton.setEnabled(true);

            if (overlayView != null) {
                overlayView.setSquatInfo(0, 0f);
            }

            // 停止运动时：显示计数但不显示相似度
            if (action != null && action.hasCounter()) {
                squatCountView.setVisibility(View.VISIBLE);
                // 保持当前计数，不清零
                int currentCount = squatAnalyzer.getSquatCount();
                squatCountView.setText(actionName + ": " + currentCount);
            } else {
                squatCountView.setVisibility(View.GONE);
            }

            if (countdownText != null) {
                countdownText.setVisibility(View.GONE);
            }
        });



        if (detectionHandler != null) {
            detectionHandler.removeCallbacksAndMessages(null);
        }
        if (countdownHandler != null) {
            countdownHandler.removeCallbacksAndMessages(null);
        }
        if (alignmentHandler != null) {
            alignmentHandler.removeCallbacksAndMessages(null);
        }

        Log.d(TAG, "停止跟踪，动作类型：" + actionName);
    }

    /**
     * 发送广播通知运动数据已更新
     */
    private void sendWorkoutDataRefreshBroadcast() {
        try {
            Intent intent = new Intent("ACTION_WORKOUT_DATA_UPDATED");
            intent.putExtra("action_name", actionName);
            intent.putExtra("duration_ms", System.currentTimeMillis() - workoutStartTime);
            intent.putExtra("similarity", similarityCount > 0 ? totalSimilaritySum / similarityCount : 0f);
            intent.putExtra("count", (action != null && action.hasCounter()) ? squatAnalyzer.getSquatCount() : 0);
            sendBroadcast(intent);
            Log.d(TAG, "已发送运动数据更新广播: actionName=" + actionName);
        } catch (Exception e) {
            Log.e(TAG, "发送广播失败: " + e.getMessage());
        }
    }
    private void resetDetectionState() {
        detectionStartTime = System.currentTimeMillis();
        successfulDetectionCount = 0;
        isWaitingForDetection = true;
        hasShownDetectionHint = false;
        hasShownPoseLostHint = false;

        runOnUiThread(() -> {
            if (startButton != null) {
                startButton.setText("停止运动");
                startButton.setEnabled(true);
            }
            if (squatCountView != null) {
                if (action != null && action.hasCounter()) {
                    squatCountView.setVisibility(View.VISIBLE);
                    squatCountView.setText(actionName + "检测中...");
                } else {
                    squatCountView.setVisibility(View.GONE);
                }
            }
        });
    }

    private void startDetectionPeriod() {
        if (!hasShownDetectionHint) {
            runOnUiThread(() -> {
                Toast.makeText(this, "请在30秒内站好姿势", Toast.LENGTH_LONG).show();
                hasShownDetectionHint = true;
            });
        }

        if (detectionHandler == null) {
            detectionHandler = new Handler(Looper.getMainLooper());
        }

        detectionHandler.postDelayed(() -> {
            if (isWaitingForDetection && !isTracking) {
                runOnUiThread(() -> {
                    isWaitingForDetection = false;
                    hasShownDetectionHint = false;
                    startButton.setText("开始运动");
                    startButton.setEnabled(true);
                    if (action != null && action.hasCounter()) {
                        squatCountView.setText(actionName + ": 0");
                    }
                    Toast.makeText(this, "未检测到有效姿势，请确保全身在画面中", Toast.LENGTH_LONG).show();
                });
            }
        }, DETECTION_TIMEOUT_MS);
    }

    private void startTrackingImmediately() {

        if (squatAnalyzer != null) {
            squatAnalyzer.resetCounter();  // 重置计数器为0
            Log.d(TAG, "开始运动，计数器已重置");
        }

        // 设置计数器类型（根据当前动作）
        if (squatAnalyzer != null && action != null) {
            squatAnalyzer.setActionType(action.getActionName());
            Log.d(TAG, "设置计数器类型: " + action.getActionName());
        }

        isTracking = true;
        shouldCalculateAngles = true;
        needCounting = (action != null && action.hasCounter());
        hasMotionData = true;


        runOnUiThread(() -> {
            if (overlayView != null) {
                overlayView.setTrackingState(true);
            }
        });

        // 记录开始时间（毫秒级）
        workoutStartTime = System.currentTimeMillis();
        totalSimilaritySum = 0;
        similarityCount = 0;

        // 重置时序数据列表
        similarityList.clear();
        frameTimestamps.clear();

        // 重置对齐时间
        lastAlignmentTime = System.currentTimeMillis();
        isInAlignmentPeriod = false;

        // 初始化对齐处理器
        if (alignmentHandler == null) {
            alignmentHandler = new Handler(Looper.getMainLooper());
        }

        resetDataCollection();
        resetStaticDetection();
        hideStaticHint();

        frameAnalysisList.clear();
        currentBatchData.clear();
        currentFrameIndex = 0;
        dataFrameCounter = 0;
        currentFrameBatch = 0;

        rtKneeAngles.clear();
        rtHipAngles.clear();
        rtElbowAngles.clear();
        rtShoulderAngles.clear();
        rtLeftElbowAngles.clear();
        rtRightElbowAngles.clear();
        rtLeftShoulderAngles.clear();
        rtRightShoulderAngles.clear();
        rtTimes.clear();
        stdKnees.clear();
        stdHips.clear();
        stdElbows.clear();
        stdShoulders.clear();
        stdLeftElbows.clear();
        stdRightElbows.clear();
        stdLeftShoulders.clear();
        stdRightShoulders.clear();

        poseWindow.clear();

        standardFramePointer = 0;
        trackingStartTime = System.currentTimeMillis();

        autoAlignmentPerformed = false;
        bestAlignmentFrameIndex = -1;
        bestAlignmentSimilarity = 0f;

        runOnUiThread(() -> {
            startButton.setText("停止运动");

            if (toggleVisualizationButton != null) {
                toggleVisualizationButton.setEnabled(true);
                toggleVisualizationButton.setAlpha(1.0f);
            }
            if (toggleFrameAnalysisButton != null) {
                toggleFrameAnalysisButton.setEnabled(true);
                toggleFrameAnalysisButton.setAlpha(1.0f);
            }
            if (validateButton != null) {
                validateButton.setEnabled(true);
                validateButton.setAlpha(1.0f);
            }

            // 开始运动时显示计数（此时为0）
            if (action != null && action.hasCounter()) {
                squatCountView.setVisibility(View.VISIBLE);
                squatCountView.setText(actionName + ": 0");
            } else {
                squatCountView.setVisibility(View.GONE);
            }
        });

        if (player != null) {
            player.seekTo(0);
        }

        Log.d(TAG, String.format("开始跟踪，开始时间=%d, 动作类型=%s", workoutStartTime, actionName));
    }
    private void startCountdown() {
        if (countdownText == null) {
            startTrackingImmediately();
            return;
        }

        countdownText.setText(String.valueOf(countdownValue));
        countdownText.animate()
                .scaleX(1.5f)
                .scaleY(1.5f)
                .setDuration(500)
                .withEndAction(() -> {
                    countdownText.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .setDuration(500)
                            .start();
                })
                .start();

        countdownHandler.postDelayed(() -> {
            if (countdownValue > 1) {
                countdownValue--;
                startCountdown();
            } else {
                countdownText.setText("开始!");
                countdownHandler.postDelayed(() -> {
                    if (countdownText != null) {
                        countdownText.setVisibility(View.GONE);
                    }
                    startTrackingImmediately();
                }, 1000);
            }
        }, 1000);
    }

    private void initPoseLandmarker() {
        if (backgroundExecutor == null) {
            backgroundExecutor = Executors.newSingleThreadExecutor();
        }

        backgroundExecutor.execute(() -> {
            try {
                Thread.sleep(2000);

                poseLandmarkerHelper = new PoseLandmarkerHelper(
                        0.5f, 0.5f, 0.5f,
                        RunningMode.LIVE_STREAM,
                        Delegate.GPU,
                        this,
                        this
                );

                if (poseLandmarkerHelper == null || poseLandmarkerHelper.isClose()) {
                    throw new Exception("GPU初始化失败");
                }
            } catch (Exception e) {
                try {
                    poseLandmarkerHelper = new PoseLandmarkerHelper(
                            0.5f, 0.5f, 0.5f,
                            RunningMode.LIVE_STREAM,
                            Delegate.CPU,
                            this,
                            this
                    );
                } catch (Exception ex) {
                    runOnUiThread(() ->
                            Toast.makeText(RealtimeActivity.this, "姿态检测初始化失败", Toast.LENGTH_LONG).show()
                    );
                }
            }
        });
    }


    private void updateStandardVideoOverlay() {
        if (!isStandardDataReady || standardVideoData.isEmpty() || player == null) return;

        try {
            long positionMs = player.getCurrentPosition();
            int totalFrames = standardVideoData.size();
            int currentFrame = (int) ((positionMs * standardVideoFps) / 1000) % totalFrames;

            if (currentFrame < standardVideoData.size()) {
                FrameData frameData = standardVideoData.get(currentFrame);
                if (frameData.landmarks != null && videoOverlayView != null) {
                    videoOverlayView.setStandardLandmarks(frameData.landmarks);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "更新标准视频覆盖失败: " + e.getMessage());
        }
    }

    private void updateStandardFramePointer() {
        if (player == null || !isStandardDataReady || standardVideoData.isEmpty()) return;

        try {
            long positionMs = player.getCurrentPosition();
            int totalFrames = standardVideoData.size();
            int newPointer = (int) ((positionMs * standardVideoFps) / 1000) % totalFrames;

            int maxJump = (int) (standardVideoFps * 0.5f);
            int diff = newPointer - standardFramePointer;

            if (Math.abs(diff) > maxJump) {
                standardFramePointer = newPointer;
            } else if (diff > 0) {
                standardFramePointer = newPointer;
            }
        } catch (Exception e) {
            Log.e(TAG, "更新标准帧指针失败: " + e.getMessage());
        }
    }

    private void updatePoseWindow(List<NormalizedLandmark> landmarks) {
        if (!shouldCalculateAngles || !isStandardDataReady) {
            return;
        }

        try {
            long elapsedMs = System.currentTimeMillis() - trackingStartTime;

            if (landmarks != null && !landmarks.isEmpty() && hasRequiredJoints) {
                int currentWidth = getCurrentImageWidth();
                int currentHeight = getCurrentImageHeight();

                double[] allAngles = calculateKeyAnglesWithSize(landmarks, currentWidth, currentHeight);
                float kneeAngle = (float) allAngles[0];
                float hipAngle = (float) allAngles[1];
                float elbowAngle = (float) allAngles[2];
                float shoulderAngle = (float) allAngles[3];

                float leftElbowAngle = calculateLeftElbowAngle(landmarks, currentWidth, currentHeight);
                float rightElbowAngle = calculateRightElbowAngle(landmarks, currentWidth, currentHeight);
                float leftShoulderAngle = calculateLeftShoulderAngle(landmarks, currentWidth, currentHeight);
                float rightShoulderAngle = calculateRightShoulderAngle(landmarks, currentWidth, currentHeight);

                float hipHeight = 0f;
                if (areLandmarksValidForAngleCalculation(landmarks, PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)) {
                    hipHeight = 1f - ((landmarks.get(PoseLandmark.LEFT_HIP).y() +
                            landmarks.get(PoseLandmark.RIGHT_HIP).y()) / 2f);
                }

                if (poseWindow.size() >= OPTIMIZED_WINDOW_SIZE) {
                    poseWindow.removeFirst();
                }

                FrameData newFrame = new FrameData(kneeAngle, hipAngle,
                        leftElbowAngle, rightElbowAngle,
                        leftShoulderAngle, rightShoulderAngle,
                        hipHeight, System.currentTimeMillis(),
                        new ArrayList<>(landmarks), true);
                poseWindow.add(newFrame);

                runOnUiThread(() -> {
                    boolean isBoxing = action != null && "拳击".equals(action.getActionName());

                    if (isBoxing) {
                        rtLeftElbowAngles.add(leftElbowAngle);
                        rtRightElbowAngles.add(rightElbowAngle);
                        rtLeftShoulderAngles.add(leftShoulderAngle);
                        rtRightShoulderAngles.add(rightShoulderAngle);

                        if (!standardVideoData.isEmpty() && standardFramePointer < standardVideoData.size()) {
                            FrameData f = standardVideoData.get(standardFramePointer);
                            stdLeftElbows.add(f.leftElbowAngle);
                            stdRightElbows.add(f.rightElbowAngle);
                            stdLeftShoulders.add(f.leftShoulderAngle);
                            stdRightShoulders.add(f.rightShoulderAngle);
                        }
                    } else {
                        rtKneeAngles.add(kneeAngle);
                        rtHipAngles.add(hipAngle);
                        rtElbowAngles.add(elbowAngle);
                        rtShoulderAngles.add(shoulderAngle);
                        rtTimes.add(elapsedMs);

                        if (!standardVideoData.isEmpty() && standardFramePointer < standardVideoData.size()) {
                            FrameData f = standardVideoData.get(standardFramePointer);
                            stdKnees.add(f.kneeAngle);
                            stdHips.add(f.hipAngle);
                            stdElbows.add(f.elbowAngle);
                            stdShoulders.add(f.shoulderAngle);
                        }
                    }
                });
            } else {
                if (!poseWindow.isEmpty()) {
                    poseWindow.removeFirst();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "更新姿态窗口失败: " + e.getMessage());
        }
    }


    private void toggleVisualization() {
        FrameLayout container = findViewById(R.id.visualizationContainer);
        if (container == null) return;

        isVisualizationEnabled = !isVisualizationEnabled;
        if (isVisualizationEnabled) {
            View visualizationView = LayoutInflater.from(this).inflate(R.layout.visualization_overlay, container, false);
            container.removeAllViews();
            container.addView(visualizationView);
            container.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            Button btnRefresh = visualizationView.findViewById(R.id.btnRefreshChart);
            Button btnClose = visualizationView.findViewById(R.id.btnCloseVisualization);

            if (btnRefresh != null) {
                btnRefresh.setOnClickListener(v -> refreshVisualization());
            }
            if (btnClose != null) {
                btnClose.setOnClickListener(v -> toggleVisualization());
            }

            toggleVisualizationButton.setText("隐藏比对图");
            generateRealVisualizationData();
            refreshVisualization();
        } else {
            container.removeAllViews();
            container.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0
            ));
            toggleVisualizationButton.setText("显示比对图");
        }
    }

    private void generateRealVisualizationData() {
        comparisonHistory.clear();
        if (!hasMotionData) return;

        try {
            boolean isBoxing = action != null && "拳击".equals(action.getActionName());
            int dataPoints;

            if (isBoxing) {
                dataPoints = Math.min(50, rtLeftElbowAngles.size());
                for (int i = 0; i < dataPoints; i++) {
                    Float[] dataPoint = new Float[6];
                    dataPoint[0] = (float) i;
                    dataPoint[1] = rtLeftElbowAngles.get(i);
                    dataPoint[2] = (i < rtRightElbowAngles.size()) ? rtRightElbowAngles.get(i) : 0f;
                    dataPoint[3] = (i < stdLeftElbows.size()) ? stdLeftElbows.get(i) : 0f;
                    dataPoint[4] = (i < stdRightElbows.size()) ? stdRightElbows.get(i) : 0f;
                    dataPoint[5] = (i < similarityList.size()) ? similarityList.get(i) : 0f;
                    comparisonHistory.add(dataPoint);
                }
            } else {
                dataPoints = Math.min(50, rtKneeAngles.size());
                for (int i = 0; i < dataPoints; i++) {
                    Float[] dataPoint = new Float[6];
                    dataPoint[0] = (float) i;
                    dataPoint[1] = rtKneeAngles.get(i);
                    dataPoint[2] = (i < rtHipAngles.size()) ? rtHipAngles.get(i) : 0f;
                    dataPoint[3] = (i < stdKnees.size()) ? stdKnees.get(i) : 0f;
                    dataPoint[4] = (i < stdHips.size()) ? stdHips.get(i) : 0f;
                    dataPoint[5] = (i < similarityList.size()) ? similarityList.get(i) : 0f;
                    comparisonHistory.add(dataPoint);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "生成可视化数据失败: " + e.getMessage());
        }
    }

    private Bitmap generateRealComparisonGraph() {
        try {
            int width = 800;
            int height = 300;
            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            Paint backgroundPaint = new Paint();
            backgroundPaint.setColor(Color.WHITE);
            canvas.drawRect(0, 0, width, height, backgroundPaint);

            if (!hasMotionData || comparisonHistory.isEmpty()) {
                Paint textPaint = new Paint();
                textPaint.setColor(Color.RED);
                textPaint.setTextSize(16);
                textPaint.setTextAlign(Paint.Align.CENTER);
                canvas.drawText("暂无运动数据", width / 2f, height / 2f, textPaint);
                return bitmap;
            }

            int dataPoints = Math.min(50, comparisonHistory.size());
            float[] realTimeAngles = new float[dataPoints];
            float[] standardAngles = new float[dataPoints];
            float[] similarities = new float[dataPoints];

            for (int i = 0; i < dataPoints; i++) {
                Float[] point = comparisonHistory.get(i);
                if (point.length >= 6) {
                    realTimeAngles[i] = point[1];
                    standardAngles[i] = point[3];
                    similarities[i] = point[5];
                }
            }

            Paint gridPaint = new Paint();
            gridPaint.setColor(Color.LTGRAY);
            gridPaint.setStrokeWidth(1);

            for (int i = 0; i <= 10; i++) {
                float y = 50 + i * (height - 100) / 10f;
                canvas.drawLine(0, y, width, y, gridPaint);
            }

            Paint realTimePaint = new Paint();
            realTimePaint.setColor(Color.RED);
            realTimePaint.setStrokeWidth(2);
            realTimePaint.setStyle(Paint.Style.STROKE);

            Paint standardPaint = new Paint();
            standardPaint.setColor(Color.BLUE);
            standardPaint.setStrokeWidth(2);
            standardPaint.setStyle(Paint.Style.STROKE);

            float xStep = (width - 100) / (float) dataPoints;

            if (hasValidAngles(realTimeAngles)) {
                Path path = new Path();
                path.moveTo(50, 50 + (height - 100) * (1 - normalizeAngleForGraph(realTimeAngles[0])));
                for (int i = 1; i < dataPoints; i++) {
                    float x = 50 + i * xStep;
                    float y = 50 + (height - 100) * (1 - normalizeAngleForGraph(realTimeAngles[i]));
                    path.lineTo(x, y);
                }
                canvas.drawPath(path, realTimePaint);
            }

            if (hasValidAngles(standardAngles)) {
                Path path = new Path();
                path.moveTo(50, 50 + (height - 100) * (1 - normalizeAngleForGraph(standardAngles[0])));
                for (int i = 1; i < dataPoints; i++) {
                    float x = 50 + i * xStep;
                    float y = 50 + (height - 100) * (1 - normalizeAngleForGraph(standardAngles[i]));
                    path.lineTo(x, y);
                }
                canvas.drawPath(path, standardPaint);
            }

            drawChartLegend(canvas, width, height);
            return bitmap;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasValidAngles(float[] angles) {
        if (angles == null || angles.length == 0) return false;
        for (float angle : angles) {
            if (angle > 0) return true;
        }
        return false;
    }

    private float normalizeAngleForGraph(float angle) {
        if (angle <= 0) return 0f;
        return Math.max(0, Math.min(1, angle / 180f));
    }

    private String generateComparisonDetails() {
        if (!hasMotionData || comparisonHistory.isEmpty()) {
            return "暂无运动数据";
        }

        try {
            int totalFrames = comparisonHistory.size();
            float sumSimilarity = 0f;
            int validCount = 0;

            for (Float[] point : comparisonHistory) {
                if (point.length >= 6 && point[5] > 0) {
                    sumSimilarity += point[5];
                    validCount++;
                }
            }

            float avgSimilarity = (validCount > 0) ? (sumSimilarity / validCount) * 100 : 0f;
            int count = (action != null && action.hasCounter()) ? squatAnalyzer.getSquatCount() : 0;

            return String.format(
                    "运动数据分析:\n总帧数: %d\n平均相似度: %.1f%%\n%s: %d",
                    totalFrames, avgSimilarity,
                    action != null ? action.getActionName() : "动作次数", count
            );
        } catch (Exception e) {
            return "数据计算错误";
        }
    }

    private void refreshVisualization() {
        if (!isVisualizationEnabled) return;
        generateRealVisualizationData();
        Bitmap chartBitmap = generateRealComparisonGraph();
        FrameLayout container = findViewById(R.id.visualizationContainer);
        if (container != null && chartBitmap != null) {
            ImageView comparisonGraph = container.findViewById(R.id.comparisonGraph);
            if (comparisonGraph != null) {
                comparisonGraph.setImageBitmap(chartBitmap);
            }
            TextView comparisonDetails = container.findViewById(R.id.comparisonDetails);
            if (comparisonDetails != null) {
                comparisonDetails.setText(generateComparisonDetails());
            }
        }
    }

    private void drawChartLegend(Canvas canvas, int width, int height) {
        Paint textPaint = new Paint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(12);
        Paint linePaint = new Paint();
        linePaint.setStrokeWidth(3);

        linePaint.setColor(Color.RED);
        canvas.drawLine(width - 200, height - 80, width - 170, height - 80, linePaint);
        canvas.drawText("实时角度", width - 160, height - 75, textPaint);

        linePaint.setColor(Color.BLUE);
        canvas.drawLine(width - 200, height - 60, width - 170, height - 60, linePaint);
        canvas.drawText("标准角度", width - 160, height - 55, textPaint);
    }

    //  帧分析核心方法
    private void toggleFrameAnalysis() {
        FrameLayout container = findViewById(R.id.visualizationContainer);
        if (container == null) return;

        isFrameVisualizationEnabled = !isFrameVisualizationEnabled;
        if (isFrameVisualizationEnabled) {
            View frameAnalysisView = LayoutInflater.from(this).inflate(R.layout.frame_analysis_overlay, container, false);
            container.removeAllViews();
            container.addView(frameAnalysisView);
            container.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
            ));
            container.setBackgroundColor(Color.WHITE);

            initializeFrameAnalysisView(frameAnalysisView);
            toggleFrameAnalysisButton.setText("隐藏帧分析");
            container.requestLayout();
        } else {
            container.removeAllViews();
            container.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0
            ));
            container.setBackgroundColor(Color.TRANSPARENT);
            toggleFrameAnalysisButton.setText("显示帧分析");
        }
    }

    private void initializeFrameAnalysisView(View frameAnalysisView) {
        try {
            ListView frameListView = frameAnalysisView.findViewById(R.id.frameListView);
            TextView frameListTitle = frameAnalysisView.findViewById(R.id.frameListTitle);

            if (frameListTitle != null) {
                frameListTitle.setText(action != null ? action.getActionName() + "帧分析" : "帧分析");
            }

            currentFrameBatch = 0;
            showFrameBatch(currentFrameBatch);

            if (frameListView != null) {
                frameListView.setOnItemClickListener((parent, view, position, id) -> {
                    if (currentBatchData != null && position < currentBatchData.size()) {
                        FrameAnalysisData data = currentBatchData.get(position);
                        showFrameDetails(data);
                    }
                });
            }

            Button btnPrevFrames = frameAnalysisView.findViewById(R.id.btnPrevFrames);
            Button btnShowFirst10 = frameAnalysisView.findViewById(R.id.btnShowFirst10);
            Button btnNextFrames = frameAnalysisView.findViewById(R.id.btnNextFrames);
            Button btnCloseFrameAnalysis = frameAnalysisView.findViewById(R.id.btnCloseFrameAnalysis);

            if (btnPrevFrames != null) {
                btnPrevFrames.setOnClickListener(v -> {
                    if (currentFrameBatch > 0) {
                        currentFrameBatch--;
                        showFrameBatch(currentFrameBatch);
                        updateFrameBatchButtons();
                    }
                });
            }

            if (btnShowFirst10 != null) {
                btnShowFirst10.setOnClickListener(v -> {
                    currentFrameBatch = 0;
                    showFrameBatch(currentFrameBatch);
                    updateFrameBatchButtons();
                });
            }

            if (btnNextFrames != null) {
                btnNextFrames.setOnClickListener(v -> {
                    int totalBatches = getTotalBatches();
                    if (currentFrameBatch < totalBatches - 1) {
                        currentFrameBatch++;
                        showFrameBatch(currentFrameBatch);
                        updateFrameBatchButtons();
                    }
                });
            }

            if (btnCloseFrameAnalysis != null) {
                btnCloseFrameAnalysis.setOnClickListener(v -> toggleFrameAnalysis());
            }

            updateFrameBatchButtons();
            updateFrameAnalysisStats(frameAnalysisView);

        } catch (Exception e) {
            Log.e(TAG, "初始化帧分析视图失败: " + e.getMessage());
        }
    }

    private int getTotalBatches() {
        if (frameAnalysisList.isEmpty()) return 0;
        return (int) Math.ceil(frameAnalysisList.size() / (double) FRAMES_PER_BATCH);
    }

    private void showFrameBatch(int batchIndex) {
        if (frameAnalysisList.isEmpty()) return;

        int startIndex = batchIndex * FRAMES_PER_BATCH;
        int endIndex = Math.min(startIndex + FRAMES_PER_BATCH, frameAnalysisList.size());

        currentBatchData.clear();
        currentBatchData.addAll(frameAnalysisList.subList(startIndex, endIndex));

        frameAnalysisAdapter = new FrameAnalysisAdapter(this, currentBatchData);
        FrameLayout container = findViewById(R.id.visualizationContainer);
        if (container != null) {
            ListView frameListView = container.findViewById(R.id.frameListView);
            if (frameListView != null) {
                frameListView.setAdapter(frameAnalysisAdapter);
            }

            TextView frameListTitle = container.findViewById(R.id.frameListTitle);
            if (frameListTitle != null) {
                int totalFrames = frameAnalysisList.size();
                int totalBatches = getTotalBatches();
                frameListTitle.setText(String.format("%s帧分析 (%d-%d/%d, 批次%d/%d)",
                        action != null ? action.getActionName() : "动作",
                        startIndex + 1, endIndex, totalFrames, batchIndex + 1, totalBatches));
            }
        }
    }

    private void updateFrameBatchButtons() {
        FrameLayout container = findViewById(R.id.visualizationContainer);
        if (container == null) return;

        Button btnPrevFrames = container.findViewById(R.id.btnPrevFrames);
        Button btnNextFrames = container.findViewById(R.id.btnNextFrames);
        Button btnShowFirst10 = container.findViewById(R.id.btnShowFirst10);

        int totalBatches = getTotalBatches();

        if (btnPrevFrames != null) {
            btnPrevFrames.setEnabled(currentFrameBatch > 0);
            btnPrevFrames.setAlpha(currentFrameBatch > 0 ? 1.0f : 0.5f);
        }

        if (btnNextFrames != null) {
            btnNextFrames.setEnabled(currentFrameBatch < totalBatches - 1);
            btnNextFrames.setAlpha(currentFrameBatch < totalBatches - 1 ? 1.0f : 0.5f);
        }

        if (btnShowFirst10 != null) {
            btnShowFirst10.setEnabled(currentFrameBatch > 0);
            btnShowFirst10.setAlpha(currentFrameBatch > 0 ? 1.0f : 0.5f);
        }
    }

    private void updateFrameAnalysisStats(View frameAnalysisView) {
        TextView selectedFrameInfo = frameAnalysisView.findViewById(R.id.selectedFrameInfo);
        if (selectedFrameInfo == null) return;

        if (frameAnalysisList.isEmpty()) {
            selectedFrameInfo.setText("暂无帧数据");
            return;
        }

        int totalFrames = frameAnalysisList.size();
        int matchedFrames = 0;
        float totalSimilarity = 0f;
        float maxSimilarity = 0f;

        for (FrameAnalysisData data : frameAnalysisList) {
            if (data.isSelectedFrame) {
                matchedFrames++;
                totalSimilarity += data.totalSimilarity;
                if (data.totalSimilarity > maxSimilarity) {
                    maxSimilarity = data.totalSimilarity;
                }
            }
        }

        float avgSimilarity = matchedFrames > 0 ? totalSimilarity / matchedFrames : 0f;
        int startFrame = currentFrameBatch * FRAMES_PER_BATCH + 1;
        int endFrame = Math.min((currentFrameBatch + 1) * FRAMES_PER_BATCH, totalFrames);

        String stats = String.format(
                "总帧数: %d | 匹配帧: %d (%.1f%%)\n" +
                        "平均相似度: %.1f%% | 最高: %.1f%%\n" +
                        "当前显示: 帧%d-%d (批次%d/%d)",
                totalFrames,
                matchedFrames,
                matchedFrames * 100.0f / totalFrames,
                avgSimilarity * 100,
                maxSimilarity * 100,
                startFrame, endFrame,
                currentFrameBatch + 1, getTotalBatches()
        );

        selectedFrameInfo.setText(stats);
    }

    private void showFrameDetails(FrameAnalysisData data) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("帧详细信息 - #" + data.frameIndex);

        StringBuilder details = new StringBuilder();
        details.append(String.format("=== 基本信息 ===\n"));
        details.append(String.format("帧索引: #%d\n", data.frameIndex));
        details.append(String.format("时间戳: %dms\n", data.timestamp));
        details.append(String.format("是否有效: %s\n\n", data.hasValidPose ? "是" : "否"));

        if ("拳击".equals(data.actionType)) {
            details.append(String.format("=== 拳击角度 ===\n"));
            details.append(String.format("左肘角度: %.1f° | 标准: %.1f°\n", data.realTimeLeftElbowAngle, data.standardLeftElbowAngle));
            details.append(String.format("右肘角度: %.1f° | 标准: %.1f°\n", data.realTimeRightElbowAngle, data.standardRightElbowAngle));
            details.append(String.format("左肩角度: %.1f° | 标准: %.1f°\n", data.realTimeLeftShoulderAngle, data.standardLeftShoulderAngle));
            details.append(String.format("右肩角度: %.1f° | 标准: %.1f°\n\n", data.realTimeRightShoulderAngle, data.standardRightShoulderAngle));
            details.append(String.format("=== 相似度 ===\n"));
            details.append(String.format("左肘: %.1f%% | 右肘: %.1f%%\n", data.leftElbowSimilarity * 100, data.rightElbowSimilarity * 100));
            details.append(String.format("左肩: %.1f%% | 右肩: %.1f%%\n", data.leftShoulderSimilarity * 100, data.rightShoulderSimilarity * 100));
        } else {
            details.append(String.format("=== 深蹲角度 ===\n"));
            details.append(String.format("膝角度: %.1f° | 标准: %.1f°\n", data.realTimeKneeAngle, data.standardKneeAngle));
            details.append(String.format("髋角度: %.1f° | 标准: %.1f°\n\n", data.realTimeHipAngle, data.standardHipAngle));
            details.append(String.format("=== 相似度 ===\n"));
            details.append(String.format("膝: %.1f%% | 髋: %.1f%%", data.kneeSimilarity * 100, data.hipSimilarity * 100));
        }

        details.append(String.format("\n\n=== 匹配信息 ===\n"));
        details.append(String.format("匹配标准帧: #%d\n", data.standardFrameIndex));
        details.append(String.format("总相似度: %.1f%%", data.totalSimilarity * 100));

        builder.setMessage(details.toString());
        builder.setPositiveButton("确定", null);
        builder.show();
    }

    // 验证比对方法
    private void validateComparison() {
        try {
            if (!hasMotionData || frameAnalysisList.isEmpty()) {
                Toast.makeText(this, "请先开始运动获取数据", Toast.LENGTH_SHORT).show();
                return;
            }

            float totalKneeDiff = 0f, totalHipDiff = 0f, totalElbowDiff = 0f, totalShoulderDiff = 0f;
            float totalSimilarity = 0f;
            int validPoints = 0;

            for (FrameAnalysisData data : frameAnalysisList) {
                if (data.realTimeKneeAngle > 0 && data.standardKneeAngle > 0) {
                    totalKneeDiff += Math.abs(data.realTimeKneeAngle - data.standardKneeAngle);
                    validPoints++;
                }
                if (data.realTimeHipAngle > 0 && data.standardHipAngle > 0) {
                    totalHipDiff += Math.abs(data.realTimeHipAngle - data.standardHipAngle);
                }
                totalSimilarity += data.totalSimilarity;
            }

            if (validPoints > 0) {
                float avgSimilarity = totalSimilarity / frameAnalysisList.size();
                String result = String.format(
                        "验证结果:\n分析帧数: %d\n平均相似度: %.1f%%",
                        frameAnalysisList.size(), avgSimilarity * 100
                );

                new AlertDialog.Builder(this)
                        .setTitle("数据验证")
                        .setMessage(result)
                        .setPositiveButton("确定", null)
                        .show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "验证失败", Toast.LENGTH_SHORT).show();
        }
    }


    private void showErrorDialog(String title, String message) {
        runOnUiThread(() -> {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(title)
                    .setMessage(message)
                    .setPositiveButton("重试", (dialog, which) -> startCamera())
                    .setNegativeButton("返回", (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
        });
    }

    private void resetForNewVideo() {
        try {
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
            if (exoPlayerView != null) {
                exoPlayerView.setPlayer(null);
            }
            if (videoOverlayView != null) {
                videoOverlayView.setStandardLandmarks(Collections.emptyList());
            }

            synchronized (standardVideoData) {
                standardVideoData.clear();
                isStandardDataReady = false;
            }
            standardFramePointer = 0;

            if (videoProcessor != null) {
                videoProcessor.release();
                videoProcessor = new VideoProcessor(this);
            }

            resetDataCollection();

            rtKneeAngles.clear();
            rtHipAngles.clear();
            rtElbowAngles.clear();
            rtShoulderAngles.clear();
            rtLeftElbowAngles.clear();
            rtRightElbowAngles.clear();
            rtLeftShoulderAngles.clear();
            rtRightShoulderAngles.clear();
            rtTimes.clear();
            stdKnees.clear();
            stdHips.clear();
            stdElbows.clear();
            stdShoulders.clear();
            stdLeftElbows.clear();
            stdRightElbows.clear();
            stdLeftShoulders.clear();
            stdRightShoulders.clear();
            similarityList.clear();
            frameAnalysisList.clear();
            currentBatchData.clear();
            poseWindow.clear();

            isTracking = false;
            shouldCalculateAngles = false;
            hasRequiredJoints = false;
            hasMotionData = false;
            processedFrameCount = 0;
            currentFrameBatch = 0;

            autoAlignmentPerformed = false;
            bestAlignmentFrameIndex = -1;
            bestAlignmentSimilarity = 0f;

            resetStaticDetection();
            hideStaticHint();

            runOnUiThread(() -> {
                if (startButton != null) {
                    startButton.setText("开始运动");
                    startButton.setEnabled(false);
                }

                if (angleView != null) {
                    if (action != null && "拳击".equals(action.getActionName())) {
                        angleView.setText("左肘: --° | 右肘: --°\n左肩: --° | 右肩: --°");
                    } else {
                        angleView.setText("膝: --° / --°\n髋: --° / --°");
                    }
                }

                if (overlayView != null) {
                    overlayView.clear();
                }

                if (toggleVisualizationButton != null) {
                    toggleVisualizationButton.setEnabled(false);
                    toggleVisualizationButton.setAlpha(0.5f);
                }
                if (toggleFrameAnalysisButton != null) {
                    toggleFrameAnalysisButton.setEnabled(false);
                    toggleFrameAnalysisButton.setAlpha(0.5f);
                }
                if (validateButton != null) {
                    validateButton.setEnabled(false);
                    validateButton.setAlpha(0.5f);
                }

                if (squatCountView != null) {
                    squatCountView.setVisibility(View.GONE);
                }
            });

            Log.d(TAG, "为新视频重置完成，动作类型：" + (action != null ? action.getActionName() : "未知"));

        } catch (Exception e) {
            Log.e(TAG, "重置新视频失败: " + e.getMessage());
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                new Handler().postDelayed(() -> {
                    checkUsbCamera();
                    startCamera();
                }, 500);
            } else {
                Toast.makeText(this, "需要摄像头和存储权限", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            checkUsbCamera();
            if (player != null) {
                player.setPlayWhenReady(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "onResume失败: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (player != null) {
                player.setPlayWhenReady(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "onPause失败: " + e.getMessage());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (player != null) {
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(usbReceiver);
        } catch (Exception e) {
            Log.e(TAG, "注销USB接收器失败: " + e.getMessage());
        }

        if (staticHintLayout != null) {
            staticHintLayout.setVisibility(View.GONE);
        }

        if (countdownHandler != null) {
            countdownHandler.removeCallbacksAndMessages(null);
        }
        if (alignmentHandler != null) {
            alignmentHandler.removeCallbacksAndMessages(null);
        }
        if (player != null) {
            player.release();
        }
        if (poseLandmarkerHelper != null) {
            poseLandmarkerHelper.clearPoseLandmarker();
        }
        if (videoProcessor != null) {
            videoProcessor.release();
        }
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
}