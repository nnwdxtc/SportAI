package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import java.io.*;
import java.util.*;

/**
 * 可序列化的完整报告
 */
public class ReportData implements Serializable {
    private static final long serialVersionUID = 1L;

    public String actionType;          // 深蹲/俯卧撑...
    public long durationMs;            // 运动总时长
    public int   squatCount;           // 动作计数（实时模式）
    public float avgSimilarity;        // 平均相似度 0~1
    public int   totalFrames;          // 总帧数
    public int   validFrames;          // 有效帧数

    /* 新增：逐帧指标 */
    public List<Float> similarityList; // 每帧相似度
    public List<Float> kneeAngleList;  // 实时膝角度
    public List<Float> hipAngleList;   // 实时髋角度
    public List<Long>  timeStamps;     // 相对时间戳 ms


    public List<Float> stdKneeList;
    public List<Float> stdHipList;


    public transient Bitmap thumbBitmap; // 不序列化
    public String thumbFile;             // 在缓存目录保存 png 的路径

    public ReportData(String actionType, long durationMs, int squatCount,
                      float avgSimilarity, int totalFrames, int validFrames,
                      List<Float> similarityList,
                      List<Float> kneeAngleList, List<Float> hipAngleList,
                      List<Long> timeStamps,
                      List<Float> stdKneeList, List<Float> stdHipList,
                      Bitmap thumb) {
        this.actionType   = actionType;
        this.durationMs   = durationMs;
        this.squatCount   = squatCount;
        this.avgSimilarity= avgSimilarity;
        this.totalFrames  = totalFrames;
        this.validFrames  = validFrames;
        this.similarityList = similarityList;
        this.kneeAngleList  = kneeAngleList;
        this.hipAngleList   = hipAngleList;
        this.timeStamps     = timeStamps;
        this.stdKneeList    = stdKneeList;
        this.stdHipList     = stdHipList;
        this.thumbBitmap    = thumb;
    }

    /* 工具：将 Bitmap 保存到缓存并返回路径 */
    public void saveThumb(Context ctx) throws IOException {
        if (thumbBitmap == null) return;
        File f = new File(ctx.getCacheDir(), "report_thumb_" + System.currentTimeMillis() + ".png");
        try (FileOutputStream out = new FileOutputStream(f)) {
            thumbBitmap.compress(Bitmap.CompressFormat.PNG, 90, out);
        }
        thumbFile = f.getAbsolutePath();
    }
}