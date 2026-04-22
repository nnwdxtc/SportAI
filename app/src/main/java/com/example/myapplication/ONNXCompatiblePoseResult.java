package com.example.myapplication;

import com.google.mediapipe.framework.image.MPImage;
import com.google.mediapipe.tasks.components.containers.Landmark;
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark;
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;


public class ONNXCompatiblePoseResult extends PoseLandmarkerResult {
    private final List<List<NormalizedLandmark>> landmarks;
    private final List<List<Landmark>> worldLandmarks;
    private final Optional<List<MPImage>> segmentationMasks;
    private final long timestampMs;

    public ONNXCompatiblePoseResult(List<NormalizedLandmark> landmarks, long timestampMs) {
        this.landmarks = new ArrayList<>();
        this.worldLandmarks = new ArrayList<>();

        if (landmarks != null && !landmarks.isEmpty()) {
            this.landmarks.add(new ArrayList<>(landmarks));


            List<Landmark> worldLandmarkList = new ArrayList<>();
            for (NormalizedLandmark landmark : landmarks) {
                worldLandmarkList.add(createWorldLandmark(landmark));
            }
            this.worldLandmarks.add(worldLandmarkList);
        }

        this.segmentationMasks = Optional.empty();
        this.timestampMs = timestampMs;
    }

    private Landmark createWorldLandmark(NormalizedLandmark normalizedLandmark) {
        return new Landmark() {
            @Override
            public float x() {
                return normalizedLandmark.x() * 1000; // 缩放因子
            }

            @Override
            public float y() {
                return normalizedLandmark.y() * 1000;
            }

            @Override
            public float z() {
                return normalizedLandmark.z() * 1000;
            }

            @Override
            public Optional<Float> visibility() {
                return normalizedLandmark.visibility();
            }

            @Override
            public Optional<Float> presence() {
                return normalizedLandmark.presence();
            }
        };
    }

    @Override
    public List<List<NormalizedLandmark>> landmarks() {
        return Collections.unmodifiableList(landmarks);
    }

    @Override
    public List<List<Landmark>> worldLandmarks() {
        return Collections.unmodifiableList(worldLandmarks);
    }

    @Override
    public Optional<List<MPImage>> segmentationMasks() {
        return segmentationMasks;
    }

    @Override
    public long timestampMs() {
        return timestampMs;
    }
}