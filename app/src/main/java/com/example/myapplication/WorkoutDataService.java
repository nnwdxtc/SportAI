// WorkoutDataService.java - 完整版（添加删除功能）

package com.example.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WorkoutDataService {
    private static final String TAG = "WorkoutDataService";
    private static final String BASE_URL = "http://114.55.105.76:8080";
    private static final String UPLOAD_REPORT_URL = BASE_URL + "/api/report/upload";
    private static final String MY_REPORTS_URL = BASE_URL + "/api/report/myList";
    private static final String DELETE_REPORT_URL = BASE_URL + "/api/report/";
    private static final String REPORT_DETAIL_URL = BASE_URL + "/api/report/";

    private final Context context;
    private final OkHttpClient client;

    public WorkoutDataService(Context context) {
        this.context = context;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    /**
     * 保存运动数据（支持游客模式和登录模式）
     */
    public void saveWorkoutData(WorkoutRecord record, Callback callback) {
        Log.d(TAG, "========== saveWorkoutData() 开始 ==========");
        Log.d(TAG, String.format("运动记录: 动作=%s, 时长=%dms, 相似度=%.2f%%, 次数=%d",
                record.getActionName(), record.getDurationMs(),
                record.getAvgSimilarity() * 100, record.getCount()));

        SharedPreferences sp = context.getSharedPreferences("user", Context.MODE_PRIVATE);
        String token = sp.getString("token", "");

        // 生成报告JSON文件（包含完整运动数据）
        String reportPath = generateFullReportFile(record);
        if (reportPath != null) {
            record.setReportFilePath(reportPath);
            Log.d(TAG, "报告文件已生成: " + reportPath);
        }

        if (token.isEmpty()) {
            Log.d(TAG, "游客模式: 保存到本地");
            saveToLocal(record);
            if (callback != null) {
                try {
                    callback.onResponse(null, null);
                } catch (IOException e) {
                    callback.onFailure(null, e);
                }
            }
        } else {
            Log.d(TAG, "登录模式: 上传到服务器, token长度=" + token.length());
            uploadToServer(record, token, callback);
        }
    }


    private String generateFullReportFile(WorkoutRecord record) {
        try {
            JSONObject reportJson = new JSONObject();
            reportJson.put("sportName", record.getActionName());
            reportJson.put("startTime", record.getStartTime());
            reportJson.put("durationMs", record.getDurationMs());
            reportJson.put("exerciseCount", record.getCount());
            reportJson.put("similarityAvg", record.getAvgSimilarity());


            JSONArray timestamps = new JSONArray();
            JSONArray similarities = new JSONArray();

            List<Long> recordTimestamps = record.getTimestamps();
            List<Float> recordSimilarities = record.getSimilarities();

            if (recordTimestamps != null && !recordTimestamps.isEmpty()) {
                for (int i = 0; i < recordTimestamps.size(); i++) {
                    timestamps.put(recordTimestamps.get(i));
                    similarities.put(recordSimilarities.get(i));
                }
                Log.d(TAG, String.format("保存时序数据: %d个数据点", recordTimestamps.size()));
            } else {

                generateDemoData(timestamps, similarities, record.getDurationMs(), record.getAvgSimilarity());
            }

            reportJson.put("timestamps", timestamps);
            reportJson.put("similarities", similarities);

            // 保存到文件
            String fileName = "report_" + record.getStartTime() + ".json";
            File reportDir = new File(context.getFilesDir(), "reports");
            if (!reportDir.exists()) {
                reportDir.mkdirs();
            }
            File reportFile = new File(reportDir, fileName);

            FileOutputStream fos = new FileOutputStream(reportFile);
            fos.write(reportJson.toString().getBytes());
            fos.close();

            Log.d(TAG, "报告文件已生成: " + reportFile.getAbsolutePath() + ", 大小=" + reportJson.toString().length());
            return reportFile.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "生成报告文件失败: " + e.getMessage(), e);
            return null;
        }
    }

    // 添加演示数据生成方法
    private void generateDemoData(JSONArray timestamps, JSONArray similarities, long durationMs, float avgSimilarity) throws Exception {
        int dataPoints = Math.min(30, (int)(durationMs / 500) + 1);
        if (dataPoints < 5) dataPoints = 10;

        for (int i = 0; i <= dataPoints; i++) {
            timestamps.put((long) i);
            double angle = (i * 2 * Math.PI / dataPoints);
            float similarity = (float) (avgSimilarity + 0.15 * Math.sin(angle));
            similarity = Math.max(0.3f, Math.min(0.95f, similarity));
            similarities.put(similarity);
        }
        Log.d(TAG, "生成演示数据: " + similarities.length() + "个数据点");
    }

    /**
     * 游客模式：保存到本地SharedPreferences
     */
    private void saveToLocal(WorkoutRecord record) {
        Log.d(TAG, "--- saveToLocal() ---");

        SharedPreferences sp = context.getSharedPreferences("workout_local", Context.MODE_PRIVATE);

        // 获取保存前的数据
        int beforeTotalCount = sp.getInt("total_count", 0);
        int beforeTotalDuration = sp.getInt("total_duration_ms", 0);
        Log.d(TAG, String.format("保存前: 总次数=%d, 总时长=%dms", beforeTotalCount, beforeTotalDuration));

        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        String lastDate = sp.getString("last_date", "");

        // 检查是否需要重置今日数据
        int todayCount = sp.getInt("today_count", 0);
        int todayDuration = sp.getInt("today_duration_ms", 0);
        if (!today.equals(lastDate)) {
            Log.d(TAG, "检测到新的一天，重置今日数据");
            todayCount = 0;
            todayDuration = 0;
        }

        // 更新今日数据
        todayCount++;
        todayDuration += record.getDurationMs();

        // 更新总数据
        int totalCount = beforeTotalCount + 1;
        int totalDuration = beforeTotalDuration + (int)record.getDurationMs();

        Log.d(TAG, String.format("更新后: 今日次数=%d, 今日时长=%dms, 总次数=%d, 总时长=%dms",
                todayCount, todayDuration, totalCount, totalDuration));

        // 保存记录列表
        String recordsJson = sp.getString("records_list", "[]");
        try {
            JSONArray recordsArray = new JSONArray(recordsJson);
            JSONObject recordJson = new JSONObject();
            recordJson.put("actionName", record.getActionName());
            recordJson.put("startTime", record.getStartTime());
            recordJson.put("durationMs", record.getDurationMs());
            recordJson.put("avgSimilarity", record.getAvgSimilarity());
            recordJson.put("count", record.getCount());
            recordJson.put("reportPath", record.getReportFilePath());
            recordJson.put("isSynced", false);
            recordJson.put("reportId", 0);

            // ========== 保存时序数据 ==========
            JSONArray timestampsArray = new JSONArray();
            JSONArray similaritiesArray = new JSONArray();
            List<Long> timestamps = record.getTimestamps();
            List<Float> similarities = record.getSimilarities();

            if (timestamps != null && similarities != null) {
                int size = Math.min(timestamps.size(), similarities.size());
                for (int i = 0; i < size; i++) {
                    timestampsArray.put(timestamps.get(i));
                    similaritiesArray.put(similarities.get(i));
                }
            }
            recordJson.put("timestamps", timestampsArray);
            recordJson.put("similarities", similaritiesArray);

            recordsArray.put(recordJson);

            sp.edit()
                    .putString("last_date", today)
                    .putInt("today_count", todayCount)
                    .putInt("today_duration_ms", todayDuration)
                    .putInt("total_count", totalCount)
                    .putInt("total_duration_ms", totalDuration)
                    .putString("records_list", recordsArray.toString())
                    .apply();

            Log.d(TAG, String.format("保存成功: 新记录数=%d, 时序数据点=%d",
                    recordsArray.length(), timestampsArray.length()));

        } catch (Exception e) {
            Log.e(TAG, "保存本地记录失败: " + e.getMessage(), e);
        }
    }

    /**
     * 登录模式：上传报告到服务器
     */
    private void uploadToServer(WorkoutRecord record, String token, Callback callback) {
        try {
            // 读取报告文件内容
            File reportFile = new File(record.getReportFilePath());
            if (!reportFile.exists()) {
                if (callback != null) {
                    callback.onFailure(null, new IOException("报告文件不存在"));
                }
                return;
            }

            // 读取JSON内容
            java.io.FileInputStream fis = new java.io.FileInputStream(reportFile);
            byte[] data = new byte[(int) reportFile.length()];
            fis.read(data);
            fis.close();
            String jsonContent = new String(data, "UTF-8");

            Log.d(TAG, "上传报告内容: " + jsonContent);

            RequestBody body = RequestBody.create(
                    jsonContent,
                    MediaType.parse("application/json; charset=utf-8")
            );

            Request request = new Request.Builder()
                    .url(UPLOAD_REPORT_URL)
                    .addHeader("Authorization", token)
                    .addHeader("Content-Type", "application/json")
                    .post(body)
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "上传报告失败: " + e.getMessage());
                    // 上传失败，保存到本地作为备份
                    saveToLocal(record);
                    if (callback != null) {
                        callback.onFailure(call, e);
                    }
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String result = response.body().string();
                    Log.d(TAG, "上传报告响应: " + result);

                    if (response.isSuccessful()) {
                        try {
                            JSONObject json = new JSONObject(result);
                            if (json.optInt("code") == 0) {
                                long reportId = json.optLong("data");
                                record.setReportId(reportId);
                                record.setSynced(true);
                                Log.d(TAG, "报告上传成功，ID: " + reportId);

                                // 上传成功后，从服务器同步运动统计
                                syncUserStatsFromServer(token);

                                if (callback != null) {
                                    callback.onResponse(call, response);
                                }
                            } else {
                                saveToLocal(record);
                                if (callback != null) {
                                    callback.onFailure(call, new IOException("上传失败: " + json.optString("msg")));
                                }
                            }
                        } catch (Exception e) {
                            saveToLocal(record);
                            if (callback != null) {
                                callback.onFailure(call, new IOException("解析失败: " + e.getMessage()));
                            }
                        }
                    } else {
                        saveToLocal(record);
                        if (callback != null) {
                            callback.onFailure(call, new IOException("HTTP错误: " + response.code()));
                        }
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "构建请求失败: " + e.getMessage());
            if (callback != null) {
                callback.onFailure(null, (IOException) e);
            }
        }
    }

    /**
     * 从服务器同步用户运动统计
     */
    private void syncUserStatsFromServer(String token) {
        Request request = new Request.Builder()
                .url(BASE_URL + "/api/user/stats")
                .addHeader("Authorization", token)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e(TAG, "同步用户统计失败: " + e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String result = response.body().string();
                    try {
                        JSONObject json = new JSONObject(result);
                        if (json.optInt("code") == 0) {
                            JSONObject data = json.optJSONObject("data");
                            if (data != null) {
                                SharedPreferences sp = context.getSharedPreferences("workout_local", Context.MODE_PRIVATE);
                                int totalCount = data.optInt("totalExerciseCount", 0);
                                int totalDuration = data.optInt("totalExerciseDurationMs", 0);
                                int todayCount = data.optInt("todayExerciseCount", 0);
                                int todayDuration = data.optInt("todayExerciseDurationMs", 0);

                                sp.edit()
                                        .putInt("total_count", totalCount)
                                        .putInt("total_duration_ms", totalDuration)
                                        .putInt("today_count", todayCount)
                                        .putInt("today_duration_ms", todayDuration)
                                        .apply();

                                Log.d(TAG, "同步用户统计成功: 总次数=" + totalCount + ", 总时长=" + totalDuration);
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "解析用户统计失败: " + e.getMessage());
                    }
                }
            }
        });
    }

    /**
     * 获取服务器报告列表（登录用户）
     */
    public void fetchServerReports(String token, ServerReportsCallback callback) {
        Request request = new Request.Builder()
                .url(MY_REPORTS_URL)
                .addHeader("Authorization", token)
                .get()
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onFailure(e.getMessage());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String result = response.body().string();
                    try {
                        JSONObject json = new JSONObject(result);
                        if (json.optInt("code") == 0) {
                            JSONArray dataArray = json.optJSONArray("data");
                            List<WorkoutRecord> records = new ArrayList<>();

                            if (dataArray != null) {
                                for (int i = 0; i < dataArray.length(); i++) {
                                    JSONObject obj = dataArray.getJSONObject(i);
                                    WorkoutRecord record = new WorkoutRecord();
                                    record.setReportId(obj.optLong("id"));
                                    record.setActionName(obj.optString("sportName"));
                                    record.setStartTime(parseTimeToMillis(obj.optString("startTime")));
                                    record.setDurationMs(obj.optLong("durationMs"));
                                    record.setAvgSimilarity((float) obj.optDouble("similarityAvg", 0));
                                    record.setCount(obj.optInt("exerciseCount"));
                                    record.setSynced(true);
                                    records.add(record);
                                }
                            }
                            callback.onSuccess(records);
                        } else {
                            callback.onFailure(json.optString("msg"));
                        }
                    } catch (Exception e) {
                        callback.onFailure("解析失败: " + e.getMessage());
                    }
                } else {
                    callback.onFailure("HTTP错误: " + response.code());
                }
            }
        });
    }

    /**
     * 删除服务器端报告（同步方法，供后台线程使用）
     */
    public boolean deleteServerReportSync(long reportId, String token) {
        try {
            String url = DELETE_REPORT_URL + reportId;
            Log.d(TAG, "删除服务器报告: " + url);

            Request request = new Request.Builder()
                    .url(url)
                    .delete()
                    .addHeader("Authorization", token)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                int code = response.code();
                Log.d(TAG, "HTTP状态码: " + code);

                if (code == 200 || code == 204) {
                    Log.d(TAG, "删除服务器报告成功: " + reportId);
                    deleteLocalReportById(reportId);
                    return true;
                } else if (code == 404) {
                    Log.d(TAG, "报告不存在: " + reportId);
                    deleteLocalReportById(reportId);
                    return true;
                } else {
                    Log.e(TAG, "删除失败 HTTP: " + code);
                    return false;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "删除服务器报告异常: " + e.getMessage(), e);
            return false;
        }
    }
    /**
     * 删除结果类
     */
    public static class DeleteResult {
        private final boolean success;
        private final String message;

        public DeleteResult(boolean success, String message) {
            this.success = success;
            this.message = message != null ? message : "";
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
    }
    /**
     * 删除本地报告（游客模式）
     * @param record 运动记录
     * @return 是否删除成功
     */
    public boolean deleteLocalReport(WorkoutRecord record) {
        boolean success = true;

        // 删除JSON报告文件
        if (record.getReportFilePath() != null && !record.getReportFilePath().isEmpty()) {
            File reportFile = new File(record.getReportFilePath());
            if (reportFile.exists()) {
                success = reportFile.delete() && success;
                Log.d(TAG, "删除报告文件: " + record.getReportFilePath() + ", 结果=" + success);
            }
        }

        // 从SharedPreferences中删除记录
        SharedPreferences prefs = context.getSharedPreferences("workout_local", Context.MODE_PRIVATE);
        String recordsJson = prefs.getString("records_list", "[]");
        try {
            JSONArray recordsArray = new JSONArray(recordsJson);
            JSONArray newArray = new JSONArray();

            for (int i = 0; i < recordsArray.length(); i++) {
                JSONObject obj = recordsArray.getJSONObject(i);
                long startTime = obj.getLong("startTime");
                if (startTime != record.getStartTime()) {
                    newArray.put(obj);
                }
            }

            // 更新统计数据
            int totalCount = prefs.getInt("total_count", 0);
            int totalDuration = prefs.getInt("total_duration_ms", 0);
            int todayCount = prefs.getInt("today_count", 0);
            int todayDuration = prefs.getInt("today_duration_ms", 0);

            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
            String recordDate = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date(record.getStartTime()));

            // 更新总统计
            totalCount = Math.max(0, totalCount - 1);
            totalDuration = Math.max(0, totalDuration - (int)record.getDurationMs());

            // 如果是今天的记录，更新今日统计
            if (today.equals(recordDate)) {
                todayCount = Math.max(0, todayCount - 1);
                todayDuration = Math.max(0, todayDuration - (int)record.getDurationMs());
            }

            prefs.edit()
                    .putString("records_list", newArray.toString())
                    .putInt("total_count", totalCount)
                    .putInt("total_duration_ms", totalDuration)
                    .putInt("today_count", todayCount)
                    .putInt("today_duration_ms", todayDuration)
                    .apply();

            Log.d(TAG, "删除本地记录成功: " + record.getStartTime());
        } catch (Exception e) {
            Log.e(TAG, "删除本地记录失败: " + e.getMessage());
            success = false;
        }

        return success;
    }

    /**
     * 根据报告ID删除本地缓存
     */
    private void deleteLocalReportById(long reportId) {
        SharedPreferences prefs = context.getSharedPreferences("workout_local", Context.MODE_PRIVATE);
        String recordsJson = prefs.getString("records_list", "[]");
        try {
            JSONArray recordsArray = new JSONArray(recordsJson);
            JSONArray newArray = new JSONArray();

            for (int i = 0; i < recordsArray.length(); i++) {
                JSONObject obj = recordsArray.getJSONObject(i);
                if (obj.has("reportId") && obj.getLong("reportId") != reportId) {
                    newArray.put(obj);
                } else if (!obj.has("reportId")) {
                    newArray.put(obj);
                }
            }

            prefs.edit().putString("records_list", newArray.toString()).apply();
            Log.d(TAG, "删除本地缓存成功: reportId=" + reportId);
        } catch (Exception e) {
            Log.e(TAG, "删除本地缓存失败: " + e.getMessage());
        }
    }

    /**
     * 解析时间字符串为毫秒时间戳
     */
    private long parseTimeToMillis(String timeStr) {
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            Date date = sdf.parse(timeStr);
            return date != null ? date.getTime() : System.currentTimeMillis();
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    /**
     * 获取本地运动记录列表
     */
    public List<WorkoutRecord> getLocalRecords() {
        List<WorkoutRecord> records = new ArrayList<>();
        SharedPreferences sp = context.getSharedPreferences("workout_local", Context.MODE_PRIVATE);
        String recordsJson = sp.getString("records_list", "[]");

        try {
            JSONArray array = new JSONArray(recordsJson);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                WorkoutRecord record = new WorkoutRecord();
                record.setActionName(obj.optString("actionName"));
                record.setStartTime(obj.optLong("startTime"));
                record.setDurationMs(obj.optLong("durationMs"));
                record.setAvgSimilarity((float) obj.optDouble("avgSimilarity", 0));
                record.setCount(obj.optInt("count"));
                record.setReportFilePath(obj.optString("reportPath"));
                record.setSynced(obj.optBoolean("isSynced", false));
                record.setReportId(obj.optLong("reportId", 0));

                // ========== 读取时序数据 ==========
                JSONArray timestampsArray = obj.optJSONArray("timestamps");
                JSONArray similaritiesArray = obj.optJSONArray("similarities");
                if (timestampsArray != null && similaritiesArray != null) {
                    int size = Math.min(timestampsArray.length(), similaritiesArray.length());
                    for (int j = 0; j < size; j++) {
                        record.addTimestamp(timestampsArray.getLong(j));
                        record.addSimilarity((float) similaritiesArray.getDouble(j));
                    }
                    Log.d(TAG, "读取本地记录: " + record.getActionName() + ", 时序数据点=" + size);
                }

                records.add(record);
            }
        } catch (Exception e) {
            Log.e(TAG, "解析本地记录失败: " + e.getMessage(), e);
        }

        // 按时间倒序排列
        java.util.Collections.sort(records, (a, b) -> Long.compare(b.getStartTime(), a.getStartTime()));
        return records;
    }

    /**
     * 获取本地总运动次数和时长
     */
    public int[] getLocalTotalStats() {
        SharedPreferences sp = context.getSharedPreferences("workout_local", Context.MODE_PRIVATE);
        int totalCount = sp.getInt("total_count", 0);
        int totalDuration = sp.getInt("total_duration_ms", 0);
        Log.d(TAG, "getLocalTotalStats: 总次数=" + totalCount + ", 总时长=" + totalDuration + "ms");
        return new int[]{totalCount, totalDuration};
    }

    /**
     * 获取今日运动次数和时长
     */
    public int[] getLocalTodayStats() {
        SharedPreferences sp = context.getSharedPreferences("workout_local", Context.MODE_PRIVATE);
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
        String lastDate = sp.getString("last_date", "");

        int todayCount = sp.getInt("today_count", 0);
        int todayDuration = sp.getInt("today_duration_ms", 0);

        if (!today.equals(lastDate)) {
            return new int[]{0, 0};
        }
        return new int[]{todayCount, todayDuration};
    }

    /**
     * 清空本地记录
     */
    public void clearLocalRecords() {
        SharedPreferences sp = context.getSharedPreferences("workout_local", Context.MODE_PRIVATE);
        sp.edit()
                .putInt("today_count", 0)
                .putInt("today_duration_ms", 0)
                .putInt("total_count", 0)
                .putInt("total_duration_ms", 0)
                .putString("records_list", "[]")
                .apply();
    }

    /**
     * 服务器报告回调接口
     */
    public interface ServerReportsCallback {
        void onSuccess(List<WorkoutRecord> records);
        void onFailure(String error);
    }
}