package com.example.oxyarena.client.animation.definition;

import java.util.Objects;

public record AnimationKeyframe(
        double timestamp,
        AnimationVector target,
        AnimationInterpolation interpolation) {
    public AnimationKeyframe {
        target = Objects.requireNonNull(target, "target");
        interpolation = interpolation == null ? AnimationInterpolation.LINEAR : interpolation;
        if (timestamp < 0.0D) {
            throw new IllegalArgumentException("timestamp must be greater than or equal to 0");
        }
    }

    public AnimationKeyframe(double timestamp, AnimationVector target) {
        this(timestamp, target, AnimationInterpolation.LINEAR);
    }
}
