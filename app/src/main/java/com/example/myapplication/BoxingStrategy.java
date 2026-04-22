package com.example.myapplication;
import java.util.Arrays;
import java.util.List;

public class BoxingStrategy implements ActionStrategy {
    public String getActionName() { return "拳击"; }
    public String getCachePrefix() { return "boxing"; }
    public boolean hasCounter() { return false; }
    public List<String> getVersions() { return Arrays.asList("jab","swing","hook"); }

    @Override
    public List<OfficialVideo> getOfficialVideos() {
        return Arrays.asList(
                new OfficialVideo("直拳", "boxing_jab", "boxing_jab"),
                new OfficialVideo("摆拳", "boxing_swing", "boxing_swing"),
                new OfficialVideo("勾拳", "boxing_hook", "boxing_hook")
               // new OfficialVideo("上勾拳", "boxing_uppercut", "boxing_uppercut")
        );
    }

    @Override
    public OfficialVideo getOfficialVideoByName(String actionName) {
        for (OfficialVideo video : getOfficialVideos()) {
            if (video.displayName.equals(actionName)) {
                return video;
            }
        }
        // 默认返回直拳
        return getOfficialVideos().get(0);
    }
}