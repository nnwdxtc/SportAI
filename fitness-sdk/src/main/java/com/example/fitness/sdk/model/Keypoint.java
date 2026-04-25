package com.example.fitness.sdk.model;

/**
 * 骨骼关键点
 */
public class Keypoint {
    private final int id;           // 关键点索引 (0-32)
    private final float x;          // 归一化坐标 x (0-1)
    private final float y;          // 归一化坐标 y (0-1)
    private final float z;          // 归一化深度
    private final float visibility; // 可见性 (0-1)

    public Keypoint(int id, float x, float y, float z, float visibility) {
        this.id = id;
        this.x = x;
        this.y = y;
        this.z = z;
        this.visibility = visibility;
    }

    public int getId() { return id; }
    public float getX() { return x; }
    public float getY() { return y; }
    public float getZ() { return z; }
    public float getVisibility() { return visibility; }

    public boolean isValid() {
        return visibility >= 0.3f && x >= 0 && x <= 1 && y >= 0 && y <= 1;
    }
}