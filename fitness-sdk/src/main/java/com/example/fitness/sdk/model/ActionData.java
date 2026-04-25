package com.example.fitness.sdk.model;

import java.util.List;

/**
 * 标准动作数据
 */
public class ActionData {
    private final String actionId;           // 动作唯一标识
    private final String actionName;         // 动作名称（深蹲/拳击/高抬腿等）
    private final List<SkeletonFrame> frames; // 标准关键帧序列
    private final float fps;                  // 帧率
    private final SwitchMode switchMode;      // 切换模式
    private final boolean needCounting;       // 是否需要计数

    public enum SwitchMode {
        IMMEDIATE,      // 立即切换
        AFTER_CURRENT   // 当前动作完成后切换
    }

    private ActionData(Builder builder) {
        this.actionId = builder.actionId;
        this.actionName = builder.actionName;
        this.frames = builder.frames;
        this.fps = builder.fps;
        this.switchMode = builder.switchMode;
        this.needCounting = builder.needCounting;
    }

    // Getters
    public String getActionId() { return actionId; }
    public String getActionName() { return actionName; }
    public List<SkeletonFrame> getFrames() { return frames; }
    public float getFps() { return fps; }
    public SwitchMode getSwitchMode() { return switchMode; }
    public boolean isNeedCounting() { return needCounting; }
    public int getFrameCount() { return frames != null ? frames.size() : 0; }

    public static class Builder {
        private String actionId;
        private String actionName;
        private List<SkeletonFrame> frames;
        private float fps = 30f;
        private SwitchMode switchMode = SwitchMode.IMMEDIATE;
        private boolean needCounting = true;

        public Builder setActionId(String actionId) { this.actionId = actionId; return this; }
        public Builder setActionName(String actionName) { this.actionName = actionName; return this; }
        public Builder setFrames(List<SkeletonFrame> frames) { this.frames = frames; return this; }
        public Builder setFps(float fps) { this.fps = fps; return this; }
        public Builder setSwitchMode(SwitchMode switchMode) { this.switchMode = switchMode; return this; }
        public Builder setNeedCounting(boolean needCounting) { this.needCounting = needCounting; return this; }

        public ActionData build() {
            return new ActionData(this);
        }
    }
}