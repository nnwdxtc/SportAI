package com.example.myapplication;

import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.util.Log;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;

import java.io.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

public final class PoseCache {

    // 计算髋部高度
    private static float calculateHipHeight(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() <= Math.max(LEFT_HIP, RIGHT_HIP)) {
            return 0f;
        }

        NormalizedLandmark leftHip = landmarks.get(LEFT_HIP);
        NormalizedLandmark rightHip = landmarks.get(RIGHT_HIP);

        if (isLandmarkValid(leftHip) && isLandmarkValid(rightHip)) {
            float leftHipY = leftHip.y();
            float rightHipY = rightHip.y();
            float avgY = (leftHipY + rightHipY) / 2.0f;
            return avgY;
        }

        return 0f;
    }

    // 检查关键点可见性
    private static boolean isLandmarkValid(NormalizedLandmark landmark) {
        return landmark != null &&
                landmark.visibility().isPresent() &&
                landmark.visibility().get() >= 0.3f &&
                landmark.x() >= 0 && landmark.x() <= 1 &&
                landmark.y() >= 0 && landmark.y() <= 1;
    }

    public static List<FrameData> read(String key, Context ctx) throws IOException {
        File cacheFile = new File(ctx.getCacheDir(), "pose_cache/" + key + ".bin");

        // 获取视频分辨率
        int width = 1920;
        int height = 1080;

        // 尝试从视频文件获取实际分辨率
        File videoFile = new File(ctx.getExternalFilesDir(null), "videos/" + key + ".mp4");
        Log.d("PoseCache", "尝试读取: " + cacheFile.getAbsolutePath() + " 存在=" + cacheFile.exists());

        if (!videoFile.exists()) {
            videoFile = new File(ctx.getExternalFilesDir(null), key + ".mp4");
        }

        if (!videoFile.exists()) {
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
                        Log.d("PoseCache", "视频分辨率: " + width + "x" + height);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                retriever.release();
            }
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

            // 计算各种角度
            float leftElbowAngle = calculateLeftElbowAngle(lm, width, height);
            float rightElbowAngle = calculateRightElbowAngle(lm, width, height);
            float leftShoulderAngle = calculateLeftShoulderAngle(lm, width, height);
            float rightShoulderAngle = calculateRightShoulderAngle(lm, width, height);

            float kneeAngle = calculateKneeAngle(lm, width, height);
            float hipAngle = calculateHipAngle(lm, width, height);

            // 计算平均角度（用于通用运动）
            float elbowAngle = calculateAverageAngle(leftElbowAngle, rightElbowAngle);
            float shoulderAngle = calculateAverageAngle(leftShoulderAngle, rightShoulderAngle);

            float hipHeight = calculateHipHeight(lm);

            // 使用 FrameData 的拳击专用构造函数（构造函数2）
            FrameData frameData = new FrameData(
                    kneeAngle, hipAngle,
                    leftElbowAngle, rightElbowAngle,
                    leftShoulderAngle, rightShoulderAngle,
                    hipHeight, ts, lm, v
            );
            list.add(frameData);
        }

        Log.d("PoseCache", "读取完成，共 " + list.size() + " 帧");
        return list;
    }

    // ========== 角度计算方法 ==========

    private static float calculateAverageAngle(float left, float right) {
        int count = 0;
        float sum = 0;
        if (left > 0 && left <= 180) {
            sum += left;
            count++;
        }
        if (right > 0 && right <= 180) {
            sum += right;
            count++;
        }
        return count > 0 ? sum / count : 0f;
    }

    private static float calculateKneeAngle(List<NormalizedLandmark> landmarks, int width, int height) {
        // 尝试左膝
        if (landmarks.size() > LEFT_KNEE) {
            NormalizedLandmark hip = landmarks.get(LEFT_HIP);
            NormalizedLandmark knee = landmarks.get(LEFT_KNEE);
            NormalizedLandmark ankle = landmarks.get(LEFT_ANKLE);

            if (isLandmarkValid(hip) && isLandmarkValid(knee) && isLandmarkValid(ankle)) {
                return (float) calculateAngleWithSize(hip, knee, ankle, width, height);
            }
        }

        // 尝试右膝
        if (landmarks.size() > RIGHT_KNEE) {
            NormalizedLandmark hip = landmarks.get(RIGHT_HIP);
            NormalizedLandmark knee = landmarks.get(RIGHT_KNEE);
            NormalizedLandmark ankle = landmarks.get(RIGHT_ANKLE);

            if (isLandmarkValid(hip) && isLandmarkValid(knee) && isLandmarkValid(ankle)) {
                return (float) calculateAngleWithSize(hip, knee, ankle, width, height);
            }
        }

        return 0f;
    }

    private static float calculateHipAngle(List<NormalizedLandmark> landmarks, int width, int height) {
        // 尝试左髋
        if (landmarks.size() > LEFT_HIP) {
            NormalizedLandmark shoulder = landmarks.get(LEFT_SHOULDER);
            NormalizedLandmark hip = landmarks.get(LEFT_HIP);
            NormalizedLandmark knee = landmarks.get(LEFT_KNEE);

            if (isLandmarkValid(shoulder) && isLandmarkValid(hip) && isLandmarkValid(knee)) {
                return (float) calculateAngleWithSize(shoulder, hip, knee, width, height);
            }
        }

        // 尝试右髋
        if (landmarks.size() > RIGHT_HIP) {
            NormalizedLandmark shoulder = landmarks.get(RIGHT_SHOULDER);
            NormalizedLandmark hip = landmarks.get(RIGHT_HIP);
            NormalizedLandmark knee = landmarks.get(RIGHT_KNEE);

            if (isLandmarkValid(shoulder) && isLandmarkValid(hip) && isLandmarkValid(knee)) {
                return (float) calculateAngleWithSize(shoulder, hip, knee, width, height);
            }
        }

        return 0f;
    }

    private static float calculateLeftElbowAngle(List<NormalizedLandmark> landmarks, int width, int height) {
        if (landmarks.size() > LEFT_ELBOW) {
            NormalizedLandmark shoulder = landmarks.get(LEFT_SHOULDER);
            NormalizedLandmark elbow = landmarks.get(LEFT_ELBOW);
            NormalizedLandmark wrist = landmarks.get(LEFT_WRIST);

            if (isLandmarkValid(shoulder) && isLandmarkValid(elbow) && isLandmarkValid(wrist)) {
                return (float) calculateAngleWithSize(shoulder, elbow, wrist, width, height);
            }
        }
        return 0f;
    }

    private static float calculateRightElbowAngle(List<NormalizedLandmark> landmarks, int width, int height) {
        if (landmarks.size() > RIGHT_ELBOW) {
            NormalizedLandmark shoulder = landmarks.get(RIGHT_SHOULDER);
            NormalizedLandmark elbow = landmarks.get(RIGHT_ELBOW);
            NormalizedLandmark wrist = landmarks.get(RIGHT_WRIST);

            if (isLandmarkValid(shoulder) && isLandmarkValid(elbow) && isLandmarkValid(wrist)) {
                return (float) calculateAngleWithSize(shoulder, elbow, wrist, width, height);
            }
        }
        return 0f;
    }

    private static float calculateLeftShoulderAngle(List<NormalizedLandmark> landmarks, int width, int height) {
        if (landmarks.size() > LEFT_SHOULDER) {
            NormalizedLandmark hip = landmarks.get(LEFT_HIP);
            NormalizedLandmark shoulder = landmarks.get(LEFT_SHOULDER);
            NormalizedLandmark elbow = landmarks.get(LEFT_ELBOW);

            if (isLandmarkValid(hip) && isLandmarkValid(shoulder) && isLandmarkValid(elbow)) {
                return (float) calculateAngleWithSize(hip, shoulder, elbow, width, height);
            }
        }
        return 0f;
    }

    private static float calculateRightShoulderAngle(List<NormalizedLandmark> landmarks, int width, int height) {
        if (landmarks.size() > RIGHT_SHOULDER) {
            NormalizedLandmark hip = landmarks.get(RIGHT_HIP);
            NormalizedLandmark shoulder = landmarks.get(RIGHT_SHOULDER);
            NormalizedLandmark elbow = landmarks.get(RIGHT_ELBOW);

            if (isLandmarkValid(hip) && isLandmarkValid(shoulder) && isLandmarkValid(elbow)) {
                return (float) calculateAngleWithSize(hip, shoulder, elbow, width, height);
            }
        }
        return 0f;
    }

    private static double calculateAngleWithSize(NormalizedLandmark a, NormalizedLandmark b, NormalizedLandmark c,
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
            Log.e("PoseCache", "角度计算错误: " + e.getMessage());
            return 0.0;
        }
    }

    // 关键点索引常量
    private static final int LEFT_SHOULDER = 11;
    private static final int RIGHT_SHOULDER = 12;
    private static final int LEFT_ELBOW = 13;
    private static final int RIGHT_ELBOW = 14;
    private static final int LEFT_WRIST = 15;
    private static final int RIGHT_WRIST = 16;
    private static final int LEFT_HIP = 23;
    private static final int RIGHT_HIP = 24;
    private static final int LEFT_KNEE = 25;
    private static final int RIGHT_KNEE = 26;
    private static final int LEFT_ANKLE = 27;
    private static final int RIGHT_ANKLE = 28;
}