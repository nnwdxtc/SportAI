package com.example.myapplication;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
//AI辅助生成 DeepSeek-R1-0528 2026.3.1
public class DoubaoApiClient {
    private static final String TAG = "DoubaoApiClient";
    private static final String BASE_URL = "https://ark.cn-beijing.volces.com/api/v3/chat/completions";
    private static final String API_KEY = "209d05c1-6eea-4fc0-a164-3863fa11abd8";
    private static final String MODEL = "doubao-seed-1-8-251228";

    private final OkHttpClient client;
    private final Handler mainHandler;

    // 回调接口
    public interface ChatCallback {
        void onSuccess(String response);
        void onError(String error);
    }

    public DoubaoApiClient() {
        // 配置 OkHttpClient，设置超时时间
        client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();

        // 主线程 Handler，用于回调更新 UI
        mainHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * 发送普通文本消息到豆包大模型
     */
    public void sendMessage(String userMessage, ChatCallback callback) {
        try {
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", MODEL);
            requestBody.put("stream", false);

            // 构建消息数组
            JSONArray messages = new JSONArray();

            // System 消息 - 设置 AI 角色为健身教练
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是专业的运动健身教练，擅长深蹲、拳击、罗马尼亚硬拉、高抬腿等动作的指导。请用简洁专业的语言回答用户关于运动的问题，给出具体的动作要领和注意事项。");
            messages.put(systemMsg);

            // User 消息
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.put(userMsg);

            requestBody.put("messages", messages);

            // 创建 HTTP 请求
            Request request = new Request.Builder()
                    .url(BASE_URL)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(
                            requestBody.toString(),
                            MediaType.parse("application/json")
                    ))
                    .build();

            // 异步发送请求
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "请求失败: " + e.getMessage(), e);
                    mainHandler.post(() -> callback.onError("网络错误: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "未知错误";
                        Log.e(TAG, "响应错误: " + response.code() + ", " + errorBody);
                        mainHandler.post(() -> callback.onError("请求失败: " + response.code()));
                        return;
                    }

                    String responseBody = response.body().string();
                    Log.d(TAG, "响应内容: " + responseBody);

                    try {
                        // 解析 JSON 响应
                        JSONObject jsonResponse = new JSONObject(responseBody);
                        String content = jsonResponse
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");

                        // 回到主线程回调
                        mainHandler.post(() -> callback.onSuccess(content));

                    } catch (JSONException e) {
                        Log.e(TAG, "JSON 解析错误", e);
                        mainHandler.post(() -> callback.onError("解析响应失败"));
                    }
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "构建请求体错误", e);
            callback.onError("请求构建失败");
        }
    }

    // ========== 流式回调接口 ==========
    public interface StreamCallback {
        void onStart();                    // 开始生成
        void onChunk(String chunk);        // 收到一块内容
        void onComplete();                 // 生成完成
        void onError(String error);        // 发生错误
    }

    /**
     * 流式发送消息（一边生成一边返回）
     */
    public void sendMessageStream(String userMessage, StreamCallback callback) {
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", MODEL);
            requestBody.put("stream", true);  // 开启流式

            JSONArray messages = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是专业的运动健身教练，擅长深蹲、拳击、罗马尼亚硬拉、高抬腿等动作的指导。请用简洁专业的语言回答用户关于运动的问题，给出具体的动作要领和注意事项。");
            messages.put(systemMsg);

            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);
            messages.put(userMsg);

            requestBody.put("messages", messages);

            Request request = new Request.Builder()
                    .url(BASE_URL)
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(
                            requestBody.toString(),
                            MediaType.parse("application/json")
                    ))
                    .build();

            // 回到主线程通知开始
            mainHandler.post(callback::onStart);

            // 使用自定义的回调处理流式响应
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "流式请求失败: " + e.getMessage(), e);
                    mainHandler.post(() -> callback.onError("网络错误: " + e.getMessage()));
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null ? response.body().string() : "未知错误";
                        Log.e(TAG, "流式响应错误: " + response.code() + ", " + errorBody);
                        mainHandler.post(() -> callback.onError("请求失败: " + response.code()));
                        return;
                    }

                    // 读取流式响应
                    try (okio.BufferedSource source = response.body().source()) {
                        StringBuilder fullContent = new StringBuilder();

                        while (!source.exhausted()) {
                            String line = source.readUtf8Line();
                            if (line == null) break;

                            // SSE 格式：data: {...}
                            if (line.startsWith("data: ")) {
                                String jsonData = line.substring(6);

                                // 结束标记
                                if ("[DONE]".equals(jsonData.trim())) {
                                    break;
                                }

                                try {
                                    JSONObject jsonResponse = new JSONObject(jsonData);
                                    JSONArray choices = jsonResponse.optJSONArray("choices");
                                    if (choices != null && choices.length() > 0) {
                                        JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                                        if (delta != null) {
                                            String content = delta.optString("content", "");
                                            if (!content.isEmpty()) {
                                                fullContent.append(content);
                                                // 每收到一块就回调
                                                final String currentText = fullContent.toString();
                                                mainHandler.post(() -> callback.onChunk(currentText));
                                            }
                                        }
                                    }
                                } catch (JSONException e) {
                                    Log.e(TAG, "解析流式数据错误: " + jsonData, e);
                                }
                            }
                        }

                        mainHandler.post(callback::onComplete);

                    } catch (Exception e) {
                        Log.e(TAG, "读取流式响应错误", e);
                        mainHandler.post(() -> callback.onError("读取响应失败: " + e.getMessage()));
                    }
                }
            });

        } catch (JSONException e) {
            Log.e(TAG, "构建流式请求体错误", e);
            callback.onError("请求构建失败");
        }
    }
}