package com.example.myapplication;

import java.util.Arrays;
import java.util.List;

public class RomanianDeadliftStrategy implements ActionStrategy {

    @Override
    public String getActionName() {
        return "罗马尼亚硬拉";
    }

    @Override
    public String getCachePrefix() {
        return "romanian_deadlift";
    }

    @Override
    public boolean hasCounter() {
        return true;  // 需要计数
    }

    @Override
    public List<String> getVersions() {
        return Arrays.asList("standard");
    }

    @Override
    public List<OfficialVideo> getOfficialVideos() {
        return Arrays.asList(
                new OfficialVideo("罗马尼亚硬拉标准动作", "romanian_deadlift_standard", "romanian_deadlift_standard")
        );
    }

    @Override
    public OfficialVideo getOfficialVideoByName(String actionName) {
        // 只有一个动作，直接返回
        return getOfficialVideos().get(0);
    }
}