// ActionCounter.java
package com.example.myapplication;

public interface ActionCounter {
    /**
     * 分析动作并更新计数
     * @param kneeAngle 膝关节角度
     * @param hipAngle 髋关节角度
     * @return 当前总次数
     */
    int analyze(float kneeAngle, float hipAngle);

    void reset();

    int getCount();
}