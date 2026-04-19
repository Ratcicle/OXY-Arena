package com.example.oxyarena.client.animation.runtime;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.example.oxyarena.client.animation.definition.AnimationDefinition;
import com.example.oxyarena.client.animation.definition.AnimationKeyframe;
import com.example.oxyarena.client.animation.definition.AnimationLoopMode;
import com.example.oxyarena.client.animation.definition.AnimationTrack;
import com.example.oxyarena.client.animation.definition.AnimationTransformTarget;
import com.example.oxyarena.client.animation.definition.AnimationVector;
import com.example.oxyarena.client.animation.definition.PlayerAnimationBone;

import net.minecraft.util.Mth;

public final class PlayerAnimationSampler {
    private PlayerAnimationSampler() {
    }

    public static PlayerAnimationPose sample(AnimationDefinition definition, double elapsedSeconds) {
        double sampleTime = normalizeSampleTime(definition, elapsedSeconds);
        if (sampleTime < 0.0D) {
            return PlayerAnimationPose.EMPTY;
        }

        Map<PlayerAnimationBone, BoneTransform> transforms = new EnumMap<>(PlayerAnimationBone.class);
        for (AnimationTrack track : definition.tracks()) {
            AnimationVector sampledTarget = sampleTrack(track, sampleTime);
            transforms.compute(
                    track.bone(),
                    (bone, current) -> applyTrackTarget(
                            current == null ? BoneTransform.IDENTITY : current,
                            track.target(),
                            sampledTarget));
        }
        return transforms.isEmpty() ? PlayerAnimationPose.EMPTY : new PlayerAnimationPose(transforms);
    }

    private static double normalizeSampleTime(AnimationDefinition definition, double elapsedSeconds) {
        double length = definition.lengthSeconds();
        double clampedElapsed = Math.max(0.0D, elapsedSeconds);
        if (definition.loop() == AnimationLoopMode.LOOP) {
            return length <= 0.0D ? 0.0D : clampedElapsed % length;
        }
        if (definition.loop() == AnimationLoopMode.NONE && clampedElapsed > length) {
            return -1.0D;
        }
        return Mth.clamp(clampedElapsed, 0.0D, length);
    }

    private static AnimationVector sampleTrack(AnimationTrack track, double sampleTime) {
        List<AnimationKeyframe> keyframes = track.keyframes();
        if (sampleTime <= keyframes.getFirst().timestamp()) {
            return keyframes.getFirst().target();
        }
        AnimationKeyframe lastKeyframe = keyframes.getLast();
        if (sampleTime >= lastKeyframe.timestamp()) {
            return lastKeyframe.target();
        }

        for (int index = 1; index < keyframes.size(); index++) {
            AnimationKeyframe previous = keyframes.get(index - 1);
            AnimationKeyframe next = keyframes.get(index);
            if (sampleTime <= next.timestamp()) {
                return interpolate(previous, next, sampleTime);
            }
        }
        return lastKeyframe.target();
    }

    private static AnimationVector interpolate(AnimationKeyframe previous, AnimationKeyframe next, double sampleTime) {
        double duration = next.timestamp() - previous.timestamp();
        if (duration <= 0.0D) {
            return next.target();
        }

        float progress = (float)((sampleTime - previous.timestamp()) / duration);
        return switch (previous.interpolation()) {
            case STEP -> previous.target();
            case LINEAR -> lerp(previous.target(), next.target(), progress);
            case BEZIER -> lerp(previous.target(), next.target(), smoothstep(progress));
        };
    }

    private static BoneTransform applyTrackTarget(
            BoneTransform transform,
            AnimationTransformTarget target,
            AnimationVector sampledTarget) {
        return switch (target) {
            case POSITION -> transform.withPosition(sampledTarget);
            case ROTATION -> transform.withRotation(sampledTarget);
            case SCALE -> transform.withScale(sampledTarget);
        };
    }

    private static AnimationVector lerp(AnimationVector from, AnimationVector to, float progress) {
        return new AnimationVector(
                Mth.lerp(progress, from.x(), to.x()),
                Mth.lerp(progress, from.y(), to.y()),
                Mth.lerp(progress, from.z(), to.z()));
    }

    private static float smoothstep(float progress) {
        float clamped = Mth.clamp(progress, 0.0F, 1.0F);
        return clamped * clamped * (3.0F - 2.0F * clamped);
    }
}
