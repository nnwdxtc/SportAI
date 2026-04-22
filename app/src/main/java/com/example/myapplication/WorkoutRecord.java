// WorkoutRecord.java
package com.example.myapplication;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class WorkoutRecord implements Serializable {
    private String actionName;      // 动作名称
    private long startTime;         // 开始时间戳（毫秒）
    private long durationMs;        // 时长(毫秒) - 整型
    private float avgSimilarity;    // 平均相似度
    private int count;              // 次数（深蹲计数）
    private String reportFilePath;  // 报告文件路径
    private long reportId;          // 服务器报告ID（登录用户）
    private boolean isSynced;       // 是否已同步到服务器

    // 时序数据
    private List<Long> timestamps = new ArrayList<>();      // 时间戳（毫秒，相对于开始时间）
    private List<Float> similarities = new ArrayList<>();

    public WorkoutRecord() {}

    public WorkoutRecord(String actionName, long durationMs, float avgSimilarity, int count) {
        this.actionName = actionName;
        this.startTime = System.currentTimeMillis();
        this.durationMs = ceilToSeconds(durationMs);  // 向上取整到秒
        this.avgSimilarity = avgSimilarity;
        this.count = count;
        this.isSynced = false;
        this.timestamps = new ArrayList<>();
        this.similarities = new ArrayList<>();
    }

    /**
     * 将毫秒向上取整到秒（例如：1500ms -> 2000ms, 1000ms -> 1000ms）
     */
    private long ceilToSeconds(long ms) {
        if (ms <= 0) return 0;
        long seconds = (ms + 999) / 1000;  // 向上取整到秒
        return seconds * 1000;
    }

    /**
     * 获取向上取整后的时长（秒）
     */
    public int getDurationSecondsCeil() {
        return (int)((durationMs + 999) / 1000);
    }

    // Getters and Setters
    public String getActionName() { return actionName; }
    public void setActionName(String actionName) { this.actionName = actionName; }

    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }

    public long getDurationMs() { return durationMs; }
    public void setDurationMs(long durationMs) {
        this.durationMs = ceilToSeconds(durationMs);
    }

    public float getAvgSimilarity() { return avgSimilarity; }
    public void setAvgSimilarity(float avgSimilarity) { this.avgSimilarity = avgSimilarity; }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = count; }

    public String getReportFilePath() { return reportFilePath; }
    public void setReportFilePath(String reportFilePath) { this.reportFilePath = reportFilePath; }

    public long getReportId() { return reportId; }
    public void setReportId(long reportId) { this.reportId = reportId; }

    public boolean isSynced() { return isSynced; }
    public void setSynced(boolean synced) { isSynced = synced; }

    public List<Long> getTimestamps() { return timestamps; }
    public void setTimestamps(List<Long> timestamps) { this.timestamps = timestamps; }

    public List<Float> getSimilarities() { return similarities; }
    public void setSimilarities(List<Float> similarities) { this.similarities = similarities; }

    /**
     * 添加时序数据（时间向上取整到秒）
     */
    public void addTimestamp(long timestampMs) {
        if (this.timestamps == null) this.timestamps = new ArrayList<>();
        // 向上取整到秒
        long roundedTimestamp = ((timestampMs + 999) / 1000) * 1000;
        this.timestamps.add(roundedTimestamp);
    }

    public void addSimilarity(float similarity) {
        if (this.similarities == null) this.similarities = new ArrayList<>();
        this.similarities.add(similarity);
    }

    public void addTimeSeriesPoint(long timestampMs, float similarity) {
        addTimestamp(timestampMs);
        addSimilarity(similarity);
    }

    public void clearTimeSeries() {
        if (timestamps != null) timestamps.clear();
        if (similarities != null) similarities.clear();
    }

    public int getTimeSeriesSize() {
        if (timestamps == null || similarities == null) return 0;
        return Math.min(timestamps.size(), similarities.size());
    }

    /**
     * 获取格式化的日期时间
     */
    public String getFormattedDateTime() {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA);
        return sdf.format(new java.util.Date(startTime));
    }

    /**
     * 获取格式化的时长（秒为单位，向上取整）
     */
    public String getFormattedDuration() {
        long seconds = (durationMs + 999) / 1000;  // 向上取整到秒
        long minutes = seconds / 60;
        long remainingSeconds = seconds % 60;
        if (minutes > 0) {
            return minutes + "分钟" + (remainingSeconds > 0 ? remainingSeconds + "秒" : "");
        }
        return seconds + "秒";
    }

    /**
     * 获取格式化的时长（仅秒）
     */
    public String getFormattedDurationSeconds() {
        long seconds = (durationMs + 999) / 1000;
        return seconds + "秒";
    }

    public int getDurationSeconds() {
        return (int)((durationMs + 999) / 1000);
    }

    public float getMaxSimilarity() {
        if (similarities == null || similarities.isEmpty()) return 0f;
        float max = 0f;
        for (float s : similarities) {
            if (s > max) max = s;
        }
        return max;
    }

    public float getMinSimilarity() {
        if (similarities == null || similarities.isEmpty()) return 0f;
        float min = 1f;
        for (float s : similarities) {
            if (s < min) min = s;
        }
        return min;
    }

    @Override
    public String toString() {
        return "WorkoutRecord{" +
                "actionName='" + actionName + '\'' +
                ", startTime=" + startTime +
                ", durationMs=" + durationMs +
                ", durationSec=" + getDurationSeconds() +
                ", avgSimilarity=" + avgSimilarity +
                ", count=" + count +
                ", isSynced=" + isSynced +
                ", timeSeriesSize=" + getTimeSeriesSize() +
                '}';
    }
}