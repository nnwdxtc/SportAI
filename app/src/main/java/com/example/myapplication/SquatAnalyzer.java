package com.example.myapplication;

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import java.util.List;

public class SquatAnalyzer {
    public enum State { STANDING, DESCENDING, BOTTOM, ASCENDING }

    private int squatCount = 0;
    private State state = State.STANDING;
    private int confirmFrames = 0;
    private String currentActionType = "深蹲";

    // 防抖 - 放宽阈值
    private double lastKneeAngle = -1;
    private double lastHipAngle = -1;
    private static final double ANGLE_CHANGE_THRESHOLD = 8.0; // 提高阈值，减少防抖影响

    // 深蹲阈值 - 放宽范围
    private static final double SQUAT_MIN_KNEE = 100; // 从90提高到100，更容易到达底部
    private static final double SQUAT_MAX_KNEE = 160; // 从170降到160，更容易识别站立

    // 高抬腿阈值 - 放宽范围
    private static final double HIGH_KNEE_MIN_KNEE = 110; // 从100提高到110
    private static final double HIGH_KNEE_MAX_KNEE = 160; // 从170降到160

    // 罗马尼亚硬拉阈值 - 放宽范围
    private static final double RDL_MIN_HIP = 100; // 从90提高到100
    private static final double RDL_MAX_HIP = 165; // 从175降到165
    private static final double RDL_MIN_KNEE = 150; // 提高，允许更多膝关节弯曲
    private static final double RDL_MAX_KNEE = 170;

    // 关节可见性检查 - 降低要求
    private static final int[] REQUIRED_JOINTS = {
            PoseLandmark.LEFT_HIP, PoseLandmark.RIGHT_HIP,
            PoseLandmark.LEFT_KNEE, PoseLandmark.RIGHT_KNEE,
            PoseLandmark.LEFT_ANKLE, PoseLandmark.RIGHT_ANKLE
    };
    private static final float MIN_VISIBILITY = 0.25f; // 从0.3降到0.25，更容易检测

    public void setActionType(String actionName) {
        this.currentActionType = actionName;
        resetCounter();
        android.util.Log.d("SquatAnalyzer", "设置动作类型: " + actionName);
    }

    public int getSquatCount() {
        return squatCount;
    }

    public void resetCounter() {
        squatCount = 0;
        state = State.STANDING;
        confirmFrames = 0;
        lastKneeAngle = -1;
        lastHipAngle = -1;
        android.util.Log.d("SquatAnalyzer", "计数器已重置");
    }

    private boolean areJointsVisible(List<NormalizedLandmark> landmarks) {
        if (landmarks == null || landmarks.size() < 33) return false;
        int visible = 0;
        for (int idx : REQUIRED_JOINTS) {
            if (idx < landmarks.size() && landmarks.get(idx).visibility().isPresent() &&
                    landmarks.get(idx).visibility().get() >= MIN_VISIBILITY) {
                visible++;
            }
        }
        return visible >= 3; // 从4降到3，降低关节可见性要求
    }

    private double calculateKneeAngle(List<NormalizedLandmark> landmarks) {
        try {
            double leftKnee = calculateAngle(
                    landmarks.get(PoseLandmark.LEFT_HIP),
                    landmarks.get(PoseLandmark.LEFT_KNEE),
                    landmarks.get(PoseLandmark.LEFT_ANKLE)
            );
            double rightKnee = calculateAngle(
                    landmarks.get(PoseLandmark.RIGHT_HIP),
                    landmarks.get(PoseLandmark.RIGHT_KNEE),
                    landmarks.get(PoseLandmark.RIGHT_ANKLE)
            );
            if (leftKnee > 0 && rightKnee > 0) return (leftKnee + rightKnee) / 2;
            if (leftKnee > 0) return leftKnee;
            return rightKnee;
        } catch (Exception e) {
            return 0;
        }
    }

    private double calculateHipAngle(List<NormalizedLandmark> landmarks) {
        try {
            double leftHip = calculateAngle(
                    landmarks.get(PoseLandmark.LEFT_SHOULDER),
                    landmarks.get(PoseLandmark.LEFT_HIP),
                    landmarks.get(PoseLandmark.LEFT_KNEE)
            );
            double rightHip = calculateAngle(
                    landmarks.get(PoseLandmark.RIGHT_SHOULDER),
                    landmarks.get(PoseLandmark.RIGHT_HIP),
                    landmarks.get(PoseLandmark.RIGHT_KNEE)
            );
            if (leftHip > 0 && rightHip > 0) return (leftHip + rightHip) / 2;
            if (leftHip > 0) return leftHip;
            return rightHip;
        } catch (Exception e) {
            return 0;
        }
    }

    private double calculateAngle(NormalizedLandmark a, NormalizedLandmark b, NormalizedLandmark c) {
        double ax = a.x() - b.x();
        double ay = a.y() - b.y();
        double bx = c.x() - b.x();
        double by = c.y() - b.y();
        double dot = ax * bx + ay * by;
        double magA = Math.sqrt(ax * ax + ay * ay);
        double magB = Math.sqrt(bx * bx + by * by);
        if (magA < 0.001 || magB < 0.001) return 0;
        return Math.toDegrees(Math.acos(dot / (magA * magB)));
    }

    public SquatAnalysis analyzeSquat(List<NormalizedLandmark> landmarks) {
        if (!areJointsVisible(landmarks)) {
            android.util.Log.d("SquatAnalyzer", "关节不可见");
            return new SquatAnalysis(squatCount, "检测中...", false, false);
        }

        double kneeAngle = calculateKneeAngle(landmarks);
        double hipAngle = calculateHipAngle(landmarks);

        android.util.Log.d("SquatAnalyzer", String.format("当前角度: 膝=%.1f°, 髋=%.1f°, 状态=%s, 计数=%d",
                kneeAngle, hipAngle, state.name(), squatCount));

        if (kneeAngle <= 0 || hipAngle <= 0) {
            android.util.Log.d("SquatAnalyzer", "角度计算无效");
            return new SquatAnalysis(squatCount, "角度计算中...", false, true);
        }

        // 防抖 - 放宽条件
        boolean significantChange = true;
        if (lastKneeAngle > 0) {
            significantChange = Math.abs(kneeAngle - lastKneeAngle) >= ANGLE_CHANGE_THRESHOLD;
        }
        lastKneeAngle = kneeAngle;
        lastHipAngle = hipAngle;

        // 根据动作类型更新状态机
        if ("高抬腿".equals(currentActionType)) {
            updateHighKneesStateMachine(kneeAngle);
        } else if ("罗马尼亚硬拉".equals(currentActionType)) {
            updateRDLStateMachine(hipAngle);
        } else {
            updateSquatStateMachine(kneeAngle);
        }

        String feedback = getStateText() + String.format(" (膝:%.0f° 髋:%.0f°)", kneeAngle, hipAngle);
        return new SquatAnalysis(squatCount, feedback, true, true);
    }

    /**
     * 深蹲状态机 - 宽松版本
     */
    private void updateSquatStateMachine(double kneeAngle) {
        android.util.Log.d("SquatAnalyzer", String.format("状态机更新: 状态=%s, 膝角=%.1f, 计数=%d",
                state.name(), kneeAngle, squatCount));

        switch (state) {
            case STANDING:
                // 站立 -> 下蹲：膝盖开始弯曲（角度变小）- 放宽条件
                if (kneeAngle < 160) { // 从150提高到160
                    confirmFrames++;
                    if (confirmFrames >= 2) { // 从3帧降到2帧
                        state = State.DESCENDING;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", "→ 开始下蹲, 膝角=" + kneeAngle);
                    }
                } else {
                    confirmFrames = 0;
                }
                break;

            case DESCENDING:
                // 下蹲 -> 底部：膝盖弯曲到最小角度 - 放宽条件
                if (kneeAngle < 120) { // 从100提高到120
                    confirmFrames++;
                    if (confirmFrames >= 2) { // 从3帧降到2帧
                        state = State.BOTTOM;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", "→ 到达底部, 膝角=" + kneeAngle);
                    }
                } else {
                    confirmFrames = 0;
                }
                break;

            case BOTTOM:
                // 底部 -> 上升：膝盖开始伸直 - 放宽条件
                if (kneeAngle > 110) { // 从100降到110（更宽松）
                    confirmFrames++;
                    if (confirmFrames >= 2) { // 从3帧降到2帧
                        state = State.ASCENDING;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", "→ 开始上升, 膝角=" + kneeAngle);
                    }
                } else {
                    confirmFrames = 0;
                }
                break;

            case ASCENDING:
                // 上升 -> 站立：膝盖接近伸直 - 放宽条件
                if (kneeAngle > 140) { // 从150降到140
                    confirmFrames++;
                    if (confirmFrames >= 2) { // 从3帧降到2帧
                        state = State.STANDING;
                        squatCount++;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", String.format("✓ 深蹲完成! 总次数=%d, 膝角=%.1f", squatCount, kneeAngle));
                    }
                } else {
                    confirmFrames = 0;
                }
                break;
        }
    }

    /**
     * 高抬腿状态机 - 宽松版本
     */
    private void updateHighKneesStateMachine(double kneeAngle) {
        switch (state) {
            case STANDING:
                // 站立状态：开始抬腿 - 放宽条件
                if (kneeAngle < HIGH_KNEE_MAX_KNEE - 20) { // 从15降到20
                    confirmFrames++;
                    if (confirmFrames >= 2) {
                        state = State.DESCENDING;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", "高抬腿: 开始抬腿");
                    }
                } else {
                    confirmFrames = 0;
                }
                break;
            case DESCENDING:
                // 抬腿到最高点 - 放宽条件
                if (kneeAngle <= HIGH_KNEE_MIN_KNEE + 20) { // 从15提高到20
                    confirmFrames++;
                    if (confirmFrames >= 2) {
                        state = State.BOTTOM;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", "高抬腿: 抬腿到位");
                    }
                } else {
                    confirmFrames = 0;
                }
                break;
            case BOTTOM:
                // 开始放下腿 - 放宽条件
                if (kneeAngle > HIGH_KNEE_MIN_KNEE + 15) {
                    confirmFrames++;
                    if (confirmFrames >= 2) {
                        state = State.ASCENDING;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", "高抬腿: 开始放下");
                    }
                } else {
                    confirmFrames = 0;
                }
                break;
            case ASCENDING:
                // 腿放下，回到站立位置 - 放宽条件
                if (kneeAngle >= HIGH_KNEE_MAX_KNEE - 15) { // 从10降到15
                    confirmFrames++;
                    if (confirmFrames >= 2) {
                        state = State.STANDING;
                        squatCount++;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", "高抬腿: 完成! 总次数=" + squatCount);
                    }
                } else {
                    confirmFrames = 0;
                }
                break;
        }
    }

    /**
     * 罗马尼亚硬拉状态机 - 宽松版本
     */
    private void updateRDLStateMachine(double hipAngle) {
        switch (state) {
            case STANDING:
                // 站立状态：开始下放 - 放宽条件
                if (hipAngle < RDL_MAX_HIP - 20) { // 从15降到20
                    confirmFrames++;
                    if (confirmFrames >= 2) {
                        state = State.DESCENDING;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", "罗马尼亚硬拉: 开始下放");
                    }
                } else {
                    confirmFrames = 0;
                }
                break;
            case DESCENDING:
                // 下放到最低点 - 放宽条件
                if (hipAngle <= RDL_MIN_HIP + 20) { // 从15提高到20
                    confirmFrames++;
                    if (confirmFrames >= 2) {
                        state = State.BOTTOM;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", "罗马尼亚硬拉: 到达底部");
                    }
                } else {
                    confirmFrames = 0;
                }
                break;
            case BOTTOM:
                // 开始上升 - 放宽条件
                if (hipAngle > RDL_MIN_HIP + 15) {
                    confirmFrames++;
                    if (confirmFrames >= 2) {
                        state = State.ASCENDING;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", "罗马尼亚硬拉: 开始上升");
                    }
                } else {
                    confirmFrames = 0;
                }
                break;
            case ASCENDING:
                // 上升到站立位置 - 放宽条件
                if (hipAngle >= RDL_MAX_HIP - 15) { // 从10降到15
                    confirmFrames++;
                    if (confirmFrames >= 2) {
                        state = State.STANDING;
                        squatCount++;
                        confirmFrames = 0;
                        android.util.Log.d("SquatAnalyzer", "罗马尼亚硬拉: 完成! 总次数=" + squatCount);
                    }
                } else {
                    confirmFrames = 0;
                }
                break;
        }
    }

    private String getStateText() {
        switch (state) {
            case STANDING: return "站立";
            case DESCENDING: return "下放中";
            case BOTTOM: return "底部";
            case ASCENDING: return "上升中";
            default: return "检测中";
        }
    }

    public static class SquatAnalysis {
        private final int count;
        private final String feedback;
        private final boolean isProperForm;
        private final boolean hasRequiredJoints;

        public SquatAnalysis(int count, String feedback, boolean isProperForm, boolean hasRequiredJoints) {
            this.count = count;
            this.feedback = feedback;
            this.isProperForm = isProperForm;
            this.hasRequiredJoints = hasRequiredJoints;
        }

        public int getSquatCount() { return count; }
        public String getFeedback() { return feedback; }
        public boolean isProperForm() { return isProperForm; }
        public boolean hasRequiredJoints() { return hasRequiredJoints; }
    }
}