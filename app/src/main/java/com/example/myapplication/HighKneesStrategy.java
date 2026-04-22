package com.example.myapplication;

import java.util.Arrays;
import java.util.List;

public class HighKneesStrategy implements ActionStrategy {

    @Override
    public String getActionName() {
        return "高抬腿";
    }

    @Override
    public String getCachePrefix() {
        return "high_knees";
    }

    @Override
    public boolean hasCounter() {
        return true;
    }

    @Override
    public List<String> getVersions() {
        return Arrays.asList("standard");
    }

    @Override
    public List<OfficialVideo> getOfficialVideos() {
        return Arrays.asList(
                new OfficialVideo("高抬腿标准动作", "high_knees_standard", "high_knees_standard")
        );
    }

    @Override
    public OfficialVideo getOfficialVideoByName(String actionName) {
        // 只有一个动作，直接返回
        return getOfficialVideos().get(0);
    }
}