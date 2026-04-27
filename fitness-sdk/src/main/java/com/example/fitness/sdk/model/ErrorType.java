package com.example.fitness.sdk.model;

/**
 * 动作错误类型
 */
public enum ErrorType {
    // 角度相关
    ANGLE_DEVIATION("角度偏差", 1001),      // 与标准序列角度不匹配

    // 节奏相关
    RHYTHM_DEVIATION("节奏偏差", 2001),    // 动作过快或过慢

    // 姿态相关
    MISSING_KEYPOINT("关键点缺失", 3001),
    POSTURE_DEVIATION("姿态偏差", 3002),

    // 超时
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