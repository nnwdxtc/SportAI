package com.example.fitness.sdk.model;
import com.example.fitness.sdk.model.NormalizedLandmark;
/**
 * 归一化关键点 - SDK 内部使用，替代 MediaPipe 的 NormalizedLandmark
 */
public class NormalizedLandmark {
    private final float x;
    private final float y;
    private final float z;
    private final float visibility;
    private final float presence;

    public NormalizedLandmark(float x, float y, float z, float visibility, float presence) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.visibility = visibility;
        this.presence = presence;
    }

    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public float getVisibility() { return visibility; }  // 返回 float，不是 Float
    public float getPresence() { return presence; }

    public boolean isValid() {
        return visibility >= 0.3f && x >= 0 && x <= 1 && y >= 0 && y <= 1;
    }
}