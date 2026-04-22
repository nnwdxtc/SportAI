package com.example.myapplication;

import android.content.Context;
import android.util.Log;

public class PerformanceOptimizer {
    private static final String TAG = "PerformanceOptimizer";

    public static class OptimizationConfig {
        public boolean useONNX = true;
        public boolean enableNPU = true;
        public int imageQuality = 75; // 0-100
        public int processingThreads = 2;
    }

    public static OptimizationConfig getOptimalConfig(Context context) {
        OptimizationConfig config = new OptimizationConfig();

        try {
            // 根据设备性能调整配置
            int cores = Runtime.getRuntime().availableProcessors();
            long maxMemory = Runtime.getRuntime().maxMemory() / (1024 * 1024); // MB

            Log.d(TAG, String.format("设备信息 - 核心数: %d, 最大内存: %dMB", cores, maxMemory));

            // 高性能设备配置
            if (cores >= 8 && maxMemory >= 4000) {
                config.processingThreads = 4;
                config.imageQuality = 85;
            }
            // 中性能设备配置
            else if (cores >= 4 && maxMemory >= 2000) {
                config.processingThreads = 2;
                config.imageQuality = 75;
            }
            // 低性能设备配置
            else {
                config.processingThreads = 1;
                config.imageQuality = 60;
                config.useONNX = true; // 低性能设备优先使用ONNX
            }

            // 检查NPU支持
            config.enableNPU = checkNPUSupport();

            Log.d(TAG, String.format("优化配置 - ONNX: %s, NPU: %s, 线程: %d, 质量: %d",
                    config.useONNX, config.enableNPU, config.processingThreads, config.imageQuality));

        } catch (Exception e) {
            Log.e(TAG, "获取优化配置失败: " + e.getMessage());
        }

        return config;
    }

    private static boolean checkNPUSupport() {
        try {
            String hardware = android.os.Build.HARDWARE.toLowerCase();
            String board = android.os.Build.BOARD.toLowerCase();
            String manufacturer = android.os.Build.MANUFACTURER.toLowerCase();

            return hardware.contains("kirin") || hardware.contains("qcom") ||
                    board.contains("kirin") || board.contains("snapdragon") ||
                    manufacturer.contains("huawei") || manufacturer.contains("qualcomm");
        } catch (Exception e) {
            return false;
        }
    }
}