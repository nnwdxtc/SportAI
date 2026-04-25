package com.example.fitness.sdk.model;

/**
 * 动作错误类型
 */
public enum ErrorType {
    // 角度相关错误
    ANGLE_TOO_LARGE("角度过大", 1001),
    ANGLE_TOO_SMALL("角度过小", 1002),
    ANGLE_DEVIATION("角度偏差过大", 1003),

    // 节奏相关错误
    RHYTHM_TOO_FAST("动作过快", 2001),
    RHYTHM_TOO_SLOW("动作过慢", 2002),
    RHYTHM_UNSTABLE("节奏不稳定", 2003),

    // 姿态相关错误
    MISSING_KEYPOINT("关键点缺失", 3001),
    POSTURE_DEVIATION("姿态偏差", 3002),
    IMBALANCE("左右不平衡", 3003),

    // 超时错误
    TIMEOUT("超时未完成", 4001);

    private final String description;
    private final int code;

    ErrorType(String description, int code) {
        this.description = description;
        this.code = code;
    }

    public String getDescription() { return description; }
    public int getCode() { return code; }
}