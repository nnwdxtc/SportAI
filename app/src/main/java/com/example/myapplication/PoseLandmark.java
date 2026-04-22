// PoseLandmark.java
package com.example.myapplication;

public class PoseLandmark {
    // 身体关键点索引
    public static final int NOSE = 0;
    public static final int LEFT_EYE_INNER = 1;
    public static final int LEFT_EYE = 2;
    public static final int LEFT_EYE_OUTER = 3;
    public static final int RIGHT_EYE_INNER = 4;
    public static final int RIGHT_EYE = 5;
    public static final int RIGHT_EYE_OUTER = 6;
    public static final int LEFT_EAR = 7;
    public static final int RIGHT_EAR = 8;
    public static final int MOUTH_LEFT = 9;
    public static final int MOUTH_RIGHT = 10;
    public static final int LEFT_SHOULDER = 11;
    public static final int RIGHT_SHOULDER = 12;
    public static final int LEFT_ELBOW = 13;
    public static final int RIGHT_ELBOW = 14;
    public static final int LEFT_WRIST = 15;
    public static final int RIGHT_WRIST = 16;
    public static final int LEFT_PINKY = 17;
    public static final int RIGHT_PINKY = 18;
    public static final int LEFT_INDEX = 19;
    public static final int RIGHT_INDEX = 20;
    public static final int LEFT_THUMB = 21;
    public static final int RIGHT_THUMB = 22;
    public static final int LEFT_HIP = 23;
    public static final int RIGHT_HIP = 24;
    public static final int LEFT_KNEE = 25;
    public static final int RIGHT_KNEE = 26;
    public static final int LEFT_ANKLE = 27;
    public static final int RIGHT_ANKLE = 28;
    public static final int LEFT_HEEL = 29;
    public static final int RIGHT_HEEL = 30;
    public static final int LEFT_FOOT_INDEX = 31;
    public static final int RIGHT_FOOT_INDEX = 32;

    // 获取关键点名称
    public static String getLandmarkName(int index) {
        switch (index) {
            case NOSE: return "鼻子";
            case LEFT_EYE: return "左眼";
            case RIGHT_EYE: return "右眼";
            case LEFT_EAR: return "左耳";
            case RIGHT_EAR: return "右耳";
            case LEFT_SHOULDER: return "左肩";
            case RIGHT_SHOULDER: return "右肩";
            case LEFT_ELBOW: return "左肘";
            case RIGHT_ELBOW: return "右肘";
            case LEFT_WRIST: return "左腕";
            case RIGHT_WRIST: return "右腕";
            case LEFT_HIP: return "左髋";
            case RIGHT_HIP: return "右髋";
            case LEFT_KNEE: return "左膝";
            case RIGHT_KNEE: return "右膝";
            case LEFT_ANKLE: return "左踝";
            case RIGHT_ANKLE: return "右踝";
            case LEFT_HEEL: return "左跟";
            case RIGHT_HEEL: return "右跟";
            case LEFT_FOOT_INDEX: return "左脚尖";
            case RIGHT_FOOT_INDEX: return "右脚尖";
            default: return "点" + index;
        }
    }
}