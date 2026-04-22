package com.example.myapplication;

import java.io.Serializable;
import java.util.List;
//AI辅助生成 DeepSeek-R1-0528 2025.12.6
public interface ActionStrategy extends Serializable {
    String getActionName();          // 深蹲 | 拳击
    String getCachePrefix();
    boolean hasCounter();
    List<String> getVersions();      // 仅保留用于缓存目录，可不改

    /* 返回官方视频列表 */
    List<OfficialVideo> getOfficialVideos();

    /* 根据动作名称获取特定视频 */
    OfficialVideo getOfficialVideoByName(String actionName);

    /* 内部类：描述一个官方视频 */
    class OfficialVideo implements Serializable {
        public final String displayName;   // 界面展示的文字
        public final String videoResName;  // raw 目录下 mp4 文件名（无扩展名）
        public final String cacheName;     // cache 目录下 bin 文件名（无扩展名）

        public OfficialVideo(String displayName, String videoResName, String cacheName) {
            this.displayName = displayName;
            this.videoResName = videoResName;
            this.cacheName   = cacheName;
        }
    }
}