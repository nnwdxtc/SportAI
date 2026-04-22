// CyclicActionCounter.java
package com.example.myapplication;

import android.util.Log;

public class CyclicActionCounter implements ActionCounter {
    private static final String TAG = "CyclicActionCounter";

    // 状态机状态
    private static final int STATE_TOP = 0;      // 顶部位置（伸展）
    private static final int STATE_BOTTOM = 1;   // 底部位置（弯曲）

    private int currentState = STATE_TOP;
    private int count = 0;

    // 角度阈值（可根据动作类型配置）
    private final float minKneeAngle;   // 最小膝关节角度（完全伸展）
    private final float maxKneeAngle;   // 最大膝关节角度（深蹲底部）
    private final float minHipAngle;    // 最小髋关节角度
    private final float maxHipAngle;    // 最大髋关节角度

    // 防抖阈值
    private static final float ANGLE_HYSTERESIS = 10.0f;  // 滞后阈值
    private float lastValidKneeAngle = -1;
    private float lastValidHipAngle = -1;

    /**
     * 构造函数
     * @param minKneeAngle 伸展状态膝关节角度（通常150-180度）
     * @param maxKneeAngle 弯曲状态膝关节角度（深蹲底部60-90度，高抬腿120-140度）
     * @param minHipAngle 伸展状态髋关节角度（通常160-180度）
     * @param maxHipAngle 弯曲状态髋关节角度
     */
    public CyclicActionCounter(float minKneeAngle, float maxKneeAngle,
                               float minHipAngle, float maxHipAngle) {
        this.minKneeAngle = minKneeAngle;
        this.maxKneeAngle = maxKneeAngle;
        this.minHipAngle = minHipAngle;
        this.maxHipAngle = maxHipAngle;
    }

    /**
     * 使用默认阈值的计数器（适用于深蹲）
     */
    public static CyclicActionCounter forSquat() {
        return new CyclicActionCounter(160f, 70f, 170f, 80f);
    }

    /**
     * 使用默认阈值的计数器（适用于高抬腿）
     * 高抬腿时膝关节弯曲约90-120度，髋关节弯曲约70-90度
     */
    public static CyclicActionCounter forHighKnees() {
        return new CyclicActionCounter(170f, 100f, 175f, 85f);
    }

    /**
     * 使用默认阈值的计数器（适用于罗马尼亚硬拉）
     * 罗马尼亚硬拉时膝关节微屈（约150-160度），髋关节弯曲较大（约90-120度）
     */
    public static CyclicActionCounter forRomanianDeadlift() {
        return new CyclicActionCounter(170f, 150f, 175f, 90f);
    }

    @Override
    public int analyze(float kneeAngle, float hipAngle) {
        // 无效角度检查
        if (!isValidAngle(kneeAngle) || !isValidAngle(hipAngle)) {
            return count;
        }

        // 防抖：检查角度变化是否显著
        if (lastValidKneeAngle > 0 && Math.abs(kneeAngle - lastValidKneeAngle) < 5.0f) {
            return count;
        }
        lastValidKneeAngle = kneeAngle;
        lastValidHipAngle = hipAngle;

        boolean isAtTop = isAtTopPosition(kneeAngle, hipAngle);
        boolean isAtBottom = isAtBottomPosition(kneeAngle, hipAngle);

        switch (currentState) {
            case STATE_TOP:
                if (isAtBottom) {
                    currentState = STATE_BOTTOM;
                    Log.d(TAG, "进入底部位置: 膝=" + kneeAngle + "°, 髋=" + hipAngle + "°");
                }
                break;

            case STATE_BOTTOM:
                if (isAtTop) {
                    currentState = STATE_TOP;
                    count++;
                    Log.d(TAG, "完成一次动作! 总次数: " + count);
                }
                break;
        }

        return count;
    }

    /**
     * 判断是否处于顶部（伸展）位置
     */
    private boolean isAtTopPosition(float kneeAngle, float hipAngle) {
        return kneeAngle >= minKneeAngle - ANGLE_HYSTERESIS &&
                hipAngle >= minHipAngle - ANGLE_HYSTERESIS;
    }

    /**
     * 判断是否处于底部（弯曲）位置
     */
    private boolean isAtBottomPosition(float kneeAngle, float hipAngle) {
        return kneeAngle <= maxKneeAngle + ANGLE_HYSTERESIS &&
                hipAngle <= maxHipAngle + ANGLE_HYSTERESIS;
    }

    private boolean isValidAngle(float angle) {
        return !Float.isNaN(angle) && !Float.isInfinite(angle) && angle > 0 && angle < 180;
    }

    @Override
    public void reset() {
        currentState = STATE_TOP;
        count = 0;
        lastValidKneeAngle = -1;
        lastValidHipAngle = -1;
        Log.d(TAG, "计数器已重置");
    }

    @Override
    public int getCount() {
        return count;
    }
}