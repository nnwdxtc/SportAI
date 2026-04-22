package com.example.myapplication;
import java.util.Arrays;
import java.util.List;

public class SquatStrategy implements ActionStrategy {
    public String getActionName() { return "深蹲"; }
    public String getCachePrefix() { return "squat"; }
    public boolean hasCounter() { return true; }
    public List<String> getVersions() { return Arrays.asList("standard"); }

    @Override
    public List<OfficialVideo> getOfficialVideos() {
        return Arrays.asList(
                new OfficialVideo("深蹲", "squat_standard", "squat_standard")
        );
    }

    @Override
    public OfficialVideo getOfficialVideoByName(String actionName) {
        // 深蹲只有一个动作，直接返回
        return getOfficialVideos().get(0);
    }
}