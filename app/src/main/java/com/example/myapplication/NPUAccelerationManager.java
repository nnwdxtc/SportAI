// NPUAccelerationManager.java
package com.example.myapplication;

import android.content.Context;
import android.os.Build;
import android.util.Log;
import java.util.concurrent.atomic.AtomicBoolean;
//辅助AI生成：Kimi K2.5 2025.12.2
public class NPUAccelerationManager {
    private static final String TAG = "NPUAccelerationManager";
    private static NPUAccelerationManager instance;

    private final Context context;
    private final AtomicBoolean isNPUSupported = new AtomicBoolean(false);
    private final AtomicBoolean isNPUEnabled = new AtomicBoolean(false);

    // NPU支持的设备列表
    private static final String[] NPU_SUPPORTED_DEVICES = {
            "kirin", "hi", // 华为麒麟系列
            "snapdragon", "qcom", // 高通系列
            "exynos", // 三星Exynos系列
            "tensor", // Google Tensor系列
            "dimensity" // 联发科天玑系列
    };

    public static synchronized NPUAccelerationManager getInstance(Context context) {
        if (instance == null) {
            instance = new NPUAccelerationManager(context);
        }
        return instance;
    }

    private NPUAccelerationManager(Context context) {
        this.context = context.getApplicationContext();
        checkNPUSupport();
    }

    private void checkNPUSupport() {
        try {
            String hardware = Build.HARDWARE.toLowerCase();
            String board = Build.BOARD.toLowerCase();
            String manufacturer = Build.MANUFACTURER.toLowerCase();
            String model = Build.MODEL.toLowerCase();

            Log.d(TAG, String.format("设备信息 - 硬件: %s, 主板: %s, 制造商: %s, 型号: %s",
                    hardware, board, manufacturer, model));

            // 检查设备是否支持NPU
            boolean supported = false;
            for (String device : NPU_SUPPORTED_DEVICES) {
                if (hardware.contains(device) || board.contains(device) ||
                        manufacturer.contains(device) || model.contains(device)) {
                    supported = true;
                    break;
                }
            }

            // 特殊设备检查
            if (isHuaweiNPUSupported() || isQualcommNPUSupported()) {
                supported = true;
            }

            isNPUSupported.set(supported);
            Log.d(TAG, "NPU支持状态: " + (supported ? "支持" : "不支持"));

        } catch (Exception e) {
            Log.e(TAG, "检查NPU支持失败: " + e.getMessage());
            isNPUSupported.set(false);
        }
    }

    private boolean isHuaweiNPUSupported() {
        try {
            // 华为NPU检查
            return Build.MANUFACTURER.toLowerCase().contains("huawei") &&
                    (Build.HARDWARE.toLowerCase().contains("kirin") ||
                            Build.MODEL.toLowerCase().contains("mate") ||
                            Build.MODEL.toLowerCase().contains("p"));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isQualcommNPUSupported() {
        try {
            // 高通NPU检查
            return Build.HARDWARE.toLowerCase().contains("qcom") &&
                    Build.MODEL.toLowerCase().contains("snapdragon");
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isNPUSupported() {
        return isNPUSupported.get();
    }

    public boolean enableNPUAcceleration() {
        if (!isNPUSupported()) {
            Log.w(TAG, "设备不支持NPU加速");
            return false;
        }

        try {
            isNPUEnabled.set(true);
            Log.i(TAG, "NPU加速已启用");
            return true;
        } catch (Exception e) {
            Log.e(TAG, "启用NPU加速失败: " + e.getMessage());
            isNPUEnabled.set(false);
            return false;
        }
    }

    public void disableNPUAcceleration() {
        isNPUEnabled.set(false);
        Log.i(TAG, "NPU加速已禁用");
    }

    public boolean isNPUEnabled() {
        return isNPUEnabled.get();
    }

    // 获取性能优化建议
    public String getOptimizationTips() {
        if (!isNPUSupported()) {
            return "当前设备不支持NPU加速，使用CPU/GPU处理";
        }

        if (isNPUEnabled()) {
            return "NPU加速已启用，享受快速姿态检测";
        } else {
            return "NPU支持但未启用，建议在设置中开启";
        }
    }
}