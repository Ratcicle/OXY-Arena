package com.example.oxyarena.client.animation.definition;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record AnimationTrack(
        PlayerAnimationBone bone,
        AnimationTransformTarget target,
        List<AnimationKeyframe> keyframes) {
    public AnimationTrack {
        bone = Objects.requireNonNull(bone, "bone");
        target = Objects.requireNonNull(target, "target");
        keyframes = normalizeKeyframes(keyframes);
    }

    private static List<AnimationKeyframe> normalizeKeyframes(List<AnimationKeyframe> keyframes) {
        List<AnimationKeyframe> copy = new ArrayList<>(Objects.requireNonNull(keyframes, "keyframes"));
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Animation track must contain at least one keyframe");
        }

        copy.sort(Comparator.comparingDouble(AnimationKeyframe::timestamp));
        for (int index = 1; index < copy.size(); index++) {
            double previous = copy.get(index - 1).timestamp();
            double current = copy.get(index).timestamp();
            if (Double.compare(previous, current) == 0) {
                throw new IllegalArgumentException("Duplicate keyframe timestamp in animation track: " + current);
            }
        }
        return List.copyOf(copy);
    }
}
