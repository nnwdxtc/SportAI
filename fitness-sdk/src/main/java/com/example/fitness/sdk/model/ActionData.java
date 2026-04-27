package com.example.fitness.sdk.model;

import java.util.List;
import java.util.Map;

public class ActionData {
    private final String actionId;
    private final String actionName;
    private final List<SkeletonFrame> frames;
    private final float fps;
    private final SwitchMode switchMode;
    private final boolean needCounting;
    private final ActionConfig config;

    /**
     * 切换模式
     */
    public enum SwitchMode {
        IMMEDIATE,      // 立即切换
        AFTER_CURRENT   // 当前动作完成后切换
    }

    /**
     * 动作配置 - 所有可调整参数都在这里
     */
    public static class ActionConfig {
        // 关节权重 (key: 关节名称, value: 权重 0-1)
        private final Map<String, Float> weights;

        // 完成一次动作的最低平均相似度阈值 (0-1)
        private final float completionThreshold;

        // 节奏容差 (0-1)，例如 0.2 表示允许 ±20% 的时间误差
        private final float rhythmTolerance;

        // 最小帧匹配数（至少匹配多少帧才算一次动作）
        private final int minMatchedFrames;

        // 最低单帧相似度阈值（低于此值视为严重偏差）
        private final float minSimilarityThreshold;

        // 评分模式: "average" 平均分, "latest" 最新分, "best" 最高分
        private final String scoringMode;

        public ActionConfig(Builder builder) {
            this.weights = builder.weights;
            this.completionThreshold = builder.completionThreshold;
            this.rhythmTolerance = builder.rhythmTolerance;
            this.minMatchedFrames = builder.minMatchedFrames;
            this.minSimilarityThreshold = builder.minSimilarityThreshold;
            this.scoringMode = builder.scoringMode;
        }

        // Getters
        public Map<String, Float> getWeights() { return weights; }
        public float getCompletionThreshold() { return completionThreshold; }
        public float getRhythmTolerance() { return rhythmTolerance; }
        public int getMinMatchedFrames() { return minMatchedFrames; }
        public float getMinSimilarityThreshold() { return minSimilarityThreshold; }
        public String getScoringMode() { return scoringMode; }

        public static class Builder {
            private Map<String, Float> weights;
            private float completionThreshold = 0.6f;
            private float rhythmTolerance = 0.2f;
            private int minMatchedFrames = 10;
            private float minSimilarityThreshold = 0.3f;
            private String scoringMode = "average";

            public Builder setWeights(Map<String, Float> weights) {
                this.weights = weights;
                return this;
            }
            public Builder setCompletionThreshold(float threshold) {
                this.completionThreshold = threshold;
                return this;
            }
            public Builder setRhythmTolerance(float tolerance) {
                this.rhythmTolerance = tolerance;
                return this;
            }
            public Builder setMinMatchedFrames(int min) {
                this.minMatchedFrames = min;
                return this;
            }
            public Builder setMinSimilarityThreshold(float threshold) {
                this.minSimilarityThreshold = threshold;
                return this;
            }
            public Builder setScoringMode(String mode) {
                this.scoringMode = mode;
                return this;
            }
            public ActionConfig build() {
                return new ActionConfig(this);
            }
        }

        // 预定义配置（方便使用）
        public static ActionConfig squat() {
            return new Builder()
                    .setWeights(Map.of("knee", 0.5f, "hip", 0.5f))
                    .setCompletionThreshold(0.6f)
                    .setRhythmTolerance(0.2f)
                    .setMinMatchedFrames(50)
                    .setMinSimilarityThreshold(0.3f)
                    .build();
        }

        public static ActionConfig boxing() {
            return new Builder()
                    .setWeights(Map.of(
                            "leftElbow", 0.3f, "rightElbow", 0.3f,
                            "leftShoulder", 0.2f, "rightShoulder", 0.2f))
                    .setCompletionThreshold(0.55f)
                    .setRhythmTolerance(0.25f)
                    .setMinMatchedFrames(30)
                    .setMinSimilarityThreshold(0.25f)
                    .build();
        }

        public static ActionConfig highKnees() {
            return new Builder()
                    .setWeights(Map.of("knee", 0.7f, "hip", 0.3f))
                    .setCompletionThreshold(0.65f)
                    .setRhythmTolerance(0.15f)
                    .setMinMatchedFrames(40)
                    .setMinSimilarityThreshold(0.35f)
                    .build();
        }

        public static ActionConfig romanianDeadlift() {
            return new Builder()
                    .setWeights(Map.of("hip", 0.8f, "knee", 0.2f))
                    .setCompletionThreshold(0.6f)
                    .setRhythmTolerance(0.2f)
                    .setMinMatchedFrames(50)
                    .setMinSimilarityThreshold(0.3f)
                    .build();
        }
    }

    private ActionData(Builder builder) {
        this.actionId = builder.actionId;
        this.actionName = builder.actionName;
        this.frames = builder.frames;
        this.fps = builder.fps;
        this.switchMode = builder.switchMode;
        this.needCounting = builder.needCounting;
        this.config = builder.config != null ? builder.config : getDefaultConfig(actionName);
    }

    private ActionConfig getDefaultConfig(String actionName) {
        if (actionName.contains("拳击")) return ActionConfig.boxing();
        if (actionName.contains("高抬腿")) return ActionConfig.highKnees();
        if (actionName.contains("硬拉")) return ActionConfig.romanianDeadlift();
        return ActionConfig.squat();
    }

    // Getters
    public String getActionId() { return actionId; }
    public String getActionName() { return actionName; }
    public List<SkeletonFrame> getFrames() { return frames; }
    public float getFps() { return fps; }
    public SwitchMode getSwitchMode() { return switchMode; }
    public boolean isNeedCounting() { return needCounting; }
    public int getFrameCount() { return frames != null ? frames.size() : 0; }
    public ActionConfig getConfig() { return config; }

    public static class Builder {
        private String actionId;
        private String actionName;
        private List<SkeletonFrame> frames;
        private float fps = 30f;
        private SwitchMode switchMode = SwitchMode.IMMEDIATE;
        private boolean needCounting = true;
        private ActionConfig config;

        public Builder setActionId(String actionId) { this.actionId = actionId; return this; }
        public Builder setActionName(String actionName) { this.actionName = actionName; return this; }
        public Builder setFrames(List<SkeletonFrame> frames) { this.frames = frames; return this; }
        public Builder setFps(float fps) { this.fps = fps; return this; }
        public Builder setSwitchMode(SwitchMode switchMode) { this.switchMode = switchMode; return this; }
        public Builder setNeedCounting(boolean needCounting) { this.needCounting = needCounting; return this; }
        public Builder setConfig(ActionConfig config) { this.config = config; return this; }

        public ActionData build() {
            return new ActionData(this);
        }
    }
}