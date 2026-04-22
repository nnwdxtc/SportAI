package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;
import java.util.Map;
import java.util.HashMap;

import androidx.annotation.NonNull;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.InputStream;
import java.lang.reflect.Type;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

/**
 * 视频缓存管理器 - 处理视频秒传、分析结果缓存和获取
 */
public class VideoCacheManager {

    private static final String TAG = "VideoCacheManager";
    private static final String PREFS_NAME = "video_cache_prefs";
    private static final String KEY_AUTH_TOKEN = "auth_token";
    private static final String KEY_BASE_URL = "base_url";
    private static final String DEFAULT_BASE_URL = "http://114.55.105.76:8080";

    private final Context context;
    private final VideoApiService apiService;
    private final Executor executor;
    private final Gson gson;


    // 对应后端的 UploadCheckResult
    public static class UploadCheckResult {
        public String sha256;
        public boolean exist;
    }

    // 对应后端的 VideoCheckResult
    public static class VideoCheckResult {
        public VideoEntity video;
        public boolean hasAnalysis;
        public AnalysisEntity analysis;
    }

    // 对应后端的 VideoEntity
    public static class VideoEntity {
        public Long id;
        public Long userId;
        public String sha256;
        public Long sizeBytes;
        public String filePath;
        public Integer durationMs;
        public Float fps;
        public Integer frameCount;
        public Integer status;
    }

    // 对应后端的 AnalysisEntity
    public static class AnalysisEntity {
        public Long id;
        public Long videoId;
        public Long userId;
        public String frameLandmarksJson;
        public Integer status;
    }

    // 对应后端的 ApiResult<T>
    public static class ApiResult<T> {
        public int code;
        public String msg;
        public T data;
    }

    // 对应后端的 UploadResultDTO
    public static class UploadResultDTO {
        public String sha256;
        public List<FrameResultDTO> frameData;

        public static class FrameResultDTO {
            public long timestamp;
            public List<LandmarkDTO> landmarks;
        }

        public static class LandmarkDTO {
            public float x;
            public float y;
            public float visibility;
        }
    }

    // Retrofit接口定义
    public interface VideoApiService {
        @GET("/api/video/exist")
        Call<ApiResult<VideoCheckResult>> checkVideoExist(
                @Query("sha256") String sha256,
                @Header("Authorization") String token
        );

        @GET("/api/video/result")
        Call<ApiResult<AnalysisEntity>> getVideoResult(
                @Query("sha256") String sha256,
                @Header("Authorization") String token
        );

        @POST("/api/video/uploadResult")
        Call<ApiResult<Void>> uploadResult(
                @Body UploadResultDTO dto,
                @Header("Authorization") String token
        );

        @POST("/api/file/register")
        Call<ApiResult<UploadCheckResult>> registerVideo(
                @Body Map<String, String> dto,
                @Header("Authorization") String token
        );
    }

    // 回调接口
    public interface CacheCheckCallback {
        void onCacheFound(List<FrameData> cachedFrames, String sha256);
        void onCacheNotFound(String sha256);
        void onError(String error);
    }

    public interface UploadCallback {
        void onSuccess();
        void onError(String error);
    }

    // 单例模式
    private static VideoCacheManager instance;

    public static synchronized VideoCacheManager getInstance(Context context) {
        if (instance == null) {
            instance = new VideoCacheManager(context.getApplicationContext());
        }
        return instance;
    }

    private VideoCacheManager(Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
        this.executor = java.util.concurrent.Executors.newSingleThreadExecutor();

        String baseUrl = getBaseUrl();
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build();
        this.apiService = retrofit.create(VideoApiService.class);
    }

    public void setBaseUrl(String baseUrl) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_BASE_URL, baseUrl).apply();
    }

    private String getBaseUrl() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_BASE_URL, DEFAULT_BASE_URL);
    }

    public void setAuthToken(String token) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
    }

    /**
     * 获取Token - 优先从video_cache_prefs读取，如果没有则从user prefs读取
     * 并添加 Bearer 前缀
     */
    private String getAuthToken() {
        Log.d(TAG, "========== 开始获取 Token ==========");

        SharedPreferences cachePrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences userPrefs = context.getSharedPreferences("user", Context.MODE_PRIVATE);

        // 🔧 始终从 user prefs 获取最新 Token
        String token = userPrefs.getString("token", "");

        if (token != null && !token.isEmpty()) {
            // 同步到 cachePrefs
            cachePrefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
            Log.d(TAG, "✅ 从 user prefs 获取 Token: " + token.substring(0, Math.min(8, token.length())) + "...");
        } else {
            // 降级：从 cachePrefs 读取
            token = cachePrefs.getString(KEY_AUTH_TOKEN, "");
            Log.d(TAG, "⚠️ user prefs 无 Token，从 cachePrefs 读取: " +
                    (token.length() > 8 ? token.substring(0, 8) + "..." : "空"));
        }

        // 去除可能的 Bearer 前缀
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
            cachePrefs.edit().putString(KEY_AUTH_TOKEN, token).apply();
        }

        Log.d(TAG, "✅ 最终 Token: " + (token != null && token.length() > 8 ? token.substring(0, 8) + "..." : "空"));
        Log.d(TAG, "========== Token 获取结束 ==========");

        return token != null ? token : "";
    }


//AI辅助生成 Kimi k2.5 2025.12.6
    /**
     * 检查视频缓存
     */
    public void checkVideoCache(@NonNull Uri videoUri, @NonNull CacheCheckCallback callback) {
        executor.execute(() -> {
            try {
                String sha256 = calculateFileSha256(videoUri);
                Log.d(TAG, "视频SHA256: " + sha256);

                Call<ApiResult<VideoCheckResult>> existCall = apiService.checkVideoExist(sha256, getAuthToken());
                Response<ApiResult<VideoCheckResult>> existResponse = existCall.execute();

                if (!existResponse.isSuccessful() || existResponse.body() == null) {
                    callback.onError("查询视频存在性失败: HTTP " + existResponse.code());
                    return;
                }

                ApiResult<VideoCheckResult> existResult = existResponse.body();

                if (existResult.code != 0 || existResult.data == null) {
                    Log.d(TAG, "视频不存在于服务器，需要分析: " + sha256);
                    callback.onCacheNotFound(sha256);
                    return;
                }

                if (!existResult.data.hasAnalysis) {
                    Log.d(TAG, "视频存在但无分析结果，需要分析: " + sha256);
                    callback.onCacheNotFound(sha256);
                    return;
                }

                if (existResult.data.analysis != null && existResult.data.analysis.frameLandmarksJson != null) {
                    List<FrameData> cachedFrames = parseAnalysisJson(existResult.data.analysis.frameLandmarksJson);

                    if (cachedFrames.isEmpty()) {
                        Log.w(TAG, "解析缓存数据为空: " + sha256);
                        callback.onCacheNotFound(sha256);
                        return;
                    }

                    Log.d(TAG, "成功获取缓存，帧数: " + cachedFrames.size());
                    callback.onCacheFound(cachedFrames, sha256);
                } else {
                    Log.w(TAG, "分析结果为空: " + sha256);
                    callback.onCacheNotFound(sha256);
                }

            } catch (Exception e) {
                Log.e(TAG, "检查缓存异常", e);
                callback.onError("检查缓存异常: " + e.getMessage());
            }
        });
    }

    /**
     * 上传分析结果到服务器
     */
    public void uploadAnalysisResult(String sha256, Uri videoUri,
                                     List<VideoProcessor.VideoFrameData> frameDataList,
                                     UploadCallback callback) {
        if (sha256 == null || frameDataList == null || frameDataList.isEmpty()) {
            if (callback != null) callback.onError("参数为空");
            return;
        }

        executor.execute(() -> {
            try {
                Map<String, String> registerMap = new HashMap<>();
                registerMap.put("sha256", sha256);

                Call<ApiResult<UploadCheckResult>> registerCall = apiService.registerVideo(registerMap, getAuthToken());
                Response<ApiResult<UploadCheckResult>> registerResponse = registerCall.execute();

                if (!registerResponse.isSuccessful() || registerResponse.body() == null) {
                    String errorMsg = "注册视频失败: HTTP " + registerResponse.code();
                    Log.w(TAG, errorMsg);
                    if (callback != null) callback.onError(errorMsg);
                    return;
                }

                ApiResult<UploadCheckResult> registerResult = registerResponse.body();
                if (registerResult.code != 0) {
                    String errorMsg = "注册视频失败: " + registerResult.msg;
                    Log.w(TAG, errorMsg);
                    if (callback != null) callback.onError(errorMsg);
                    return;
                }

                Log.d(TAG, "视频注册成功，exist=" + registerResult.data.exist);

                if (registerResult.data.exist) {
                    Log.d(TAG, "视频已有分析结果，跳过上传");
                    if (callback != null) callback.onSuccess();
                    return;
                }

                UploadResultDTO dto = buildUploadDTO(sha256, frameDataList);

                Call<ApiResult<Void>> call = apiService.uploadResult(dto, getAuthToken());
                Response<ApiResult<Void>> response = call.execute();

                if (response.isSuccessful() && response.body() != null && response.body().code == 0) {
                    Log.d(TAG, "分析结果上传成功: " + sha256);
                    if (callback != null) callback.onSuccess();
                } else {
                    String msg = response.body() != null ? response.body().msg : "unknown";
                    Log.w(TAG, "分析结果上传失败: " + msg);
                    if (callback != null) callback.onError(msg);
                }
            } catch (Exception e) {
                Log.e(TAG, "上传分析结果异常", e);
                if (callback != null) callback.onError(e.getMessage());
            }
        });
    }



    private String calculateFileSha256(Uri uri) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = context.getContentResolver().openInputStream(uri)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : digest.digest()) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private List<FrameData> parseAnalysisJson(String json) {
        List<FrameData> frames = new ArrayList<>();
        try {
            Type listType = new TypeToken<List<UploadResultDTO.FrameResultDTO>>(){}.getType();
            List<UploadResultDTO.FrameResultDTO> frameResults = gson.fromJson(json, listType);

            if (frameResults == null) {
                Log.e(TAG, "Gson 解析结果为 null");
                return frames;
            }

            Log.d(TAG, "解析出 " + frameResults.size() + " 帧");

            for (int i = 0; i < frameResults.size(); i++) {
                UploadResultDTO.FrameResultDTO frameResult = frameResults.get(i);
                FrameData frameData = new FrameData();
                frameData.timestamp = frameResult.timestamp;

                if (frameResult.landmarks != null && !frameResult.landmarks.isEmpty()) {
                    Log.d(TAG, "帧 " + i + " 有 " + frameResult.landmarks.size() + " 个 landmarks");

                    List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks = new ArrayList<>();

                    for (int j = 0; j < frameResult.landmarks.size(); j++) {
                        UploadResultDTO.LandmarkDTO lmDto = frameResult.landmarks.get(j);

                        if (lmDto == null) continue;

                        com.google.mediapipe.tasks.components.containers.NormalizedLandmark landmark =
                                createNormalizedLandmark(lmDto.x, lmDto.y, lmDto.visibility);

                        if (landmark != null) {
                            landmarks.add(landmark);
                        } else {
                            Log.w(TAG, "landmark " + j + " 创建失败");
                        }
                    }

                    frameData.landmarks = landmarks;
                    frameData.hasValidPose = !landmarks.isEmpty();

                    // 计算角度
                    if (frameData.hasValidPose) {
                        // 深蹲/硬拉角度
                        frameData.kneeAngle = calculateKneeAngle(landmarks);
                        frameData.hipAngle = calculateHipAngle(landmarks);

                        // 拳击专用角度（左右分离）
                        frameData.leftElbowAngle = calculateElbowAngle(landmarks, true);
                        frameData.rightElbowAngle = calculateElbowAngle(landmarks, false);
                        frameData.leftShoulderAngle = calculateShoulderAngle(landmarks, true);
                        frameData.rightShoulderAngle = calculateShoulderAngle(landmarks, false);

                        Log.d(TAG, String.format("帧 %d: 膝=%.1f, 髋=%.1f, 左肘=%.1f, 右肘=%.1f, 左肩=%.1f, 右肩=%.1f",
                                i, frameData.kneeAngle, frameData.hipAngle,
                                frameData.leftElbowAngle, frameData.rightElbowAngle,
                                frameData.leftShoulderAngle, frameData.rightShoulderAngle));
                    }
                } else {
                    Log.w(TAG, "帧 " + i + " 无 landmarks");
                    frameData.hasValidPose = false;
                }

                frames.add(frameData);
            }

            // 统计
            int validFrames = 0;
            for (FrameData f : frames) {
                if (f.hasValidPose) validFrames++;
            }
            Log.d(TAG, "解析完成: 总帧=" + frames.size() + ", 有效帧=" + validFrames);

        } catch (Exception e) {
            Log.e(TAG, "解析 JSON 异常", e);
        }
        return frames;
    }


    private com.google.mediapipe.tasks.components.containers.NormalizedLandmark createNormalizedLandmark(
            float x, float y, float visibility) {
        try {
            return com.google.mediapipe.tasks.components.containers.NormalizedLandmark.create(
                    x,
                    y,
                    0f,
                    java.util.Optional.of(visibility),
                    java.util.Optional.of(1.0f)
            );
        } catch (Exception e) {
            Log.e(TAG, "createNormalizedLandmark 失败: x=" + x + ", y=" + y, e);
            return null;
        }
    }

    private UploadResultDTO buildUploadDTO(String sha256,
                                           List<VideoProcessor.VideoFrameData> frameDataList) {
        UploadResultDTO dto = new UploadResultDTO();
        dto.sha256 = sha256;
        dto.frameData = new ArrayList<>();

        for (VideoProcessor.VideoFrameData frame : frameDataList) {
            UploadResultDTO.FrameResultDTO frameDto = new UploadResultDTO.FrameResultDTO();
            frameDto.timestamp = frame.timestamp;
            frameDto.landmarks = new ArrayList<>();

            if (frame.landmarks != null) {
                for (com.google.mediapipe.tasks.components.containers.NormalizedLandmark lm : frame.landmarks) {
                    UploadResultDTO.LandmarkDTO landmarkDto = new UploadResultDTO.LandmarkDTO();
                    landmarkDto.x = lm.x();
                    landmarkDto.y = lm.y();

                    if (lm.visibility().isPresent()) {
                        landmarkDto.visibility = lm.visibility().get();
                    } else {
                        landmarkDto.visibility = 1.0f;
                    }

                    frameDto.landmarks.add(landmarkDto);
                }
            }

            dto.frameData.add(frameDto);
        }

        return dto;
    }

    private float calculateKneeAngle(List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() < 33) return -1f;

        try {
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark leftHip = landmarks.get(PoseLandmark.LEFT_HIP);
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark leftKnee = landmarks.get(PoseLandmark.LEFT_KNEE);
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark leftAnkle = landmarks.get(PoseLandmark.LEFT_ANKLE);

            if (leftHip == null || leftKnee == null || leftAnkle == null) return -1f;

            return calculateAngle(leftHip, leftKnee, leftAnkle);
        } catch (Exception e) {
            return -1f;
        }
    }

    private float calculateHipAngle(List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() < 33) return -1f;

        try {
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark leftShoulder = landmarks.get(PoseLandmark.LEFT_SHOULDER);
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark leftHip = landmarks.get(PoseLandmark.LEFT_HIP);
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark leftKnee = landmarks.get(PoseLandmark.LEFT_KNEE);

            if (leftShoulder == null || leftHip == null || leftKnee == null) return -1f;

            return calculateAngle(leftShoulder, leftHip, leftKnee);
        } catch (Exception e) {
            return -1f;
        }
    }

    /**
     * 计算肘关节角度（肩-肘-腕）
     * @param landmarks 关键点列表
     * @param isLeft true=左臂, false=右臂
     * @return 角度值，无效返回-1
     */
    private float calculateElbowAngle(List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks, boolean isLeft) {
        if (landmarks == null || landmarks.size() < 33) return -1f;

        try {
            int shoulderIdx = isLeft ? PoseLandmark.LEFT_SHOULDER : PoseLandmark.RIGHT_SHOULDER;
            int elbowIdx = isLeft ? PoseLandmark.LEFT_ELBOW : PoseLandmark.RIGHT_ELBOW;
            int wristIdx = isLeft ? PoseLandmark.LEFT_WRIST : PoseLandmark.RIGHT_WRIST;

            com.google.mediapipe.tasks.components.containers.NormalizedLandmark shoulder = landmarks.get(shoulderIdx);
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark elbow = landmarks.get(elbowIdx);
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark wrist = landmarks.get(wristIdx);

            if (shoulder == null || elbow == null || wrist == null) return -1f;

            // 检查可见性（可选，如果可见性太低可能不准确）
            if (shoulder.visibility().isPresent() && shoulder.visibility().get() < 0.3f) return -1f;
            if (elbow.visibility().isPresent() && elbow.visibility().get() < 0.3f) return -1f;
            if (wrist.visibility().isPresent() && wrist.visibility().get() < 0.3f) return -1f;

            return calculateAngle(shoulder, elbow, wrist);
        } catch (Exception e) {
            return -1f;
        }
    }

    /**
     * 计算肩关节角度（髋-肩-肘）
     * @param landmarks 关键点列表
     * @param isLeft true=左臂, false=右臂
     * @return 角度值，无效返回-1
     */
    private float calculateShoulderAngle(List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark> landmarks, boolean isLeft) {
        if (landmarks == null || landmarks.size() < 33) return -1f;

        try {
            int hipIdx = isLeft ? PoseLandmark.LEFT_HIP : PoseLandmark.RIGHT_HIP;
            int shoulderIdx = isLeft ? PoseLandmark.LEFT_SHOULDER : PoseLandmark.RIGHT_SHOULDER;
            int elbowIdx = isLeft ? PoseLandmark.LEFT_ELBOW : PoseLandmark.RIGHT_ELBOW;

            com.google.mediapipe.tasks.components.containers.NormalizedLandmark hip = landmarks.get(hipIdx);
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark shoulder = landmarks.get(shoulderIdx);
            com.google.mediapipe.tasks.components.containers.NormalizedLandmark elbow = landmarks.get(elbowIdx);

            if (hip == null || shoulder == null || elbow == null) return -1f;

            // 检查可见性
            if (hip.visibility().isPresent() && hip.visibility().get() < 0.3f) return -1f;
            if (shoulder.visibility().isPresent() && shoulder.visibility().get() < 0.3f) return -1f;
            if (elbow.visibility().isPresent() && elbow.visibility().get() < 0.3f) return -1f;

            return calculateAngle(hip, shoulder, elbow);
        } catch (Exception e) {
            return -1f;
        }
    }

    private float calculateAngle(com.google.mediapipe.tasks.components.containers.NormalizedLandmark a,
                                 com.google.mediapipe.tasks.components.containers.NormalizedLandmark b,
                                 com.google.mediapipe.tasks.components.containers.NormalizedLandmark c) {
        double baX = a.x() - b.x();
        double baY = a.y() - b.y();
        double bcX = c.x() - b.x();
        double bcY = c.y() - b.y();

        double dotProduct = baX * bcX + baY * bcY;
        double baLength = Math.sqrt(baX * baX + baY * baY);
        double bcLength = Math.sqrt(bcX * bcX + bcY * bcY);

        if (baLength == 0 || bcLength == 0) return -1f;

        double cosine = dotProduct / (baLength * bcLength);
        cosine = Math.max(-1.0, Math.min(1.0, cosine));
        double angleRad = Math.acos(cosine);

        return (float) Math.toDegrees(angleRad);
    }

    public void shutdown() {
        if (executor instanceof java.util.concurrent.ExecutorService) {
            ((java.util.concurrent.ExecutorService) executor).shutdown();
        }
        instance = null;
    }
}