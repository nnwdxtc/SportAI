package com.example.myapplication;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Log;
import android.widget.Toast;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public final class PoseCache {

    // 添加一个静态方法来计算髋部高度
    private static float calculateHipHeight(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() <= Math.max(PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP)) {
            return 0f;
        }

        NormalizedLandmark leftHip = landmarks.get(PoseLandmark.LEFT_HIP);
        NormalizedLandmark rightHip = landmarks.get(PoseLandmark.RIGHT_HIP);

        if (isLandmarkValid(leftHip) && isLandmarkValid(rightHip)) {
            float leftHipY = leftHip.y();
            float rightHipY = rightHip.y();
            float avgY = (leftHipY + rightHipY) / 2.0f;

            // 归一化高度，注意y坐标0是顶部，1是底部
            return avgY;
        }

        return 0f;
    }

    // 添加关键点可见性检查方法
    private static boolean isLandmarkValid(NormalizedLandmark landmark) {
        return landmark != null &&
                landmark.visibility().isPresent() &&
                landmark.visibility().get() >= 0.3f &&
                landmark.x() >= 0 && landmark.x() <= 1 &&
                landmark.y() >= 0 && landmark.y() <= 1;
    }

    public static List<FrameData> read(String key, Context ctx) throws IOException {
        File cacheFile = new File(ctx.getCacheDir(), "pose_cache/" + key + ".bin");

        // 1. 尝试从视频文件获取实际分辨率
        int width = 2046;
        int height = 1080;

        // 假设视频文件位于外部存储的特定目录
        // 你可以根据实际情况调整视频文件路径
        File videoFile = new File(ctx.getExternalFilesDir(null), "videos/" + key + ".mp4");
        File file = new File(ctx.getCacheDir(), "pose_cache/" + key + ".bin");
        Log.d("PoseCache", "尝试读取: " + file.getAbsolutePath() + " 存在=" + file.exists());
        // 如果默认路径不存在，尝试其他可能的位置
        if (!videoFile.exists()) {
            // 尝试从缓存目录的父目录查找
            videoFile = new File(ctx.getExternalFilesDir(null), key + ".mp4");
        }

        if (!videoFile.exists()) {
            // 尝试从内部存储的视频目录查找
            videoFile = new File(ctx.getFilesDir(), "videos/" + key + ".mp4");
        }

        if (videoFile.exists()) {
            MediaMetadataRetriever retriever = new MediaMetadataRetriever();
            try {
                retriever.setDataSource(videoFile.getAbsolutePath());
                String w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                String h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);

                if (w != null && h != null) {
                    try {
                        width = Integer.parseInt(w);
                        height = Integer.parseInt(h);
                        // 可以在这里添加日志输出，确认获取到的分辨率
                        // Log.d("PoseCache", "Video resolution: " + width + "x" + height);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                // 解析失败时使用默认值
            } finally {
                retriever.release();
            }
        } else {
            // 如果找不到视频文件，可以记录日志或使用默认值
            // Log.w("PoseCache", "Video file not found for key: " + key);
        }

        MappedByteBuffer buf = new RandomAccessFile(cacheFile, "r")
                .getChannel().map(FileChannel.MapMode.READ_ONLY, 0, cacheFile.length());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int n = buf.getInt();

        List<FrameData> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {

            long ts = buf.getLong();
            boolean v = buf.get() == 1;
            List<NormalizedLandmark> lm = new ArrayList<>(33);
            for (int j = 0; j < 33; j++) {
                float x = buf.getFloat();
                float y = buf.getFloat();
                float z = buf.getFloat();
                float vis = buf.getFloat();
                float pres = buf.getFloat();
                lm.add(NormalizedLandmark.create(x, y, z,
                        java.util.Optional.of(vis),
                        java.util.Optional.of(pres)));
            }

            // 使用动态获取的视频分辨率计算角度
            double[] angles = RealtimeActivity.calculateKeyAnglesWithSize(lm, width, height);
            float kneeAngle = (float) angles[0];
            float hipAngle  = (float) angles[1];
            float elbowAngle = (float) angles[2];
            float shoulderAngle = (float) angles[3];

            // 使用本地方法计算髋部高度
            float hipHeight = calculateHipHeight(lm);

            list.add(new FrameData(kneeAngle, hipAngle, elbowAngle, shoulderAngle, hipHeight, ts, lm, v));
        }

        return list;
    }

    // 如果需要，添加 PoseLandmark 常量定义
    private static class PoseLandmark {
        public static final int LEFT_HIP = 23;
        public static final int RIGHT_HIP = 24;
        // 可以添加其他需要的常量
    }
}