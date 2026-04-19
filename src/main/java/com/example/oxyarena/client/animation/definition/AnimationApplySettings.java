package com.example.oxyarena.client.animation.definition;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

public record AnimationApplySettings(
        AnimationApplyBase base,
        AnimationApplyMode mode,
        double blendInSeconds,
        double blendOutSeconds,
        int priority,
        Set<PlayerAnimationBone> mask) {
    public static final AnimationApplyBase DEFAULT_BASE = AnimationApplyBase.VANILLA;
    public static final AnimationApplyMode DEFAULT_MODE = AnimationApplyMode.ADDITIVE;
    public static final double DEFAULT_BLEND_IN_SECONDS = 0.05D;
    public static final double DEFAULT_BLEND_OUT_SECONDS = 0.12D;
    public static final int DEFAULT_PRIORITY = 100;

    public AnimationApplySettings {
        base = Objects.requireNonNull(base, "base");
        mode = Objects.requireNonNull(mode, "mode");
        mask = copyMask(mask);
        if (blendInSeconds < 0.0D) {
            throw new IllegalArgumentException("blendInSeconds must be greater than or equal to 0");
        }
        if (blendOutSeconds < 0.0D) {
            throw new IllegalArgumentException("blendOutSeconds must be greater than or equal to 0");
        }
    }

    public static AnimationApplySettings defaults() {
        return new AnimationApplySettings(
                DEFAULT_BASE,
                DEFAULT_MODE,
                DEFAULT_BLEND_IN_SECONDS,
                DEFAULT_BLEND_OUT_SECONDS,
                DEFAULT_PRIORITY,
                Set.of());
    }

    public boolean derivesMaskFromTracks() {
        return this.mask.isEmpty();
    }

    private static Set<PlayerAnimationBone> copyMask(Set<PlayerAnimationBone> mask) {
        if (mask == null || mask.isEmpty()) {
            return Set.of();
        }

        EnumSet<PlayerAnimationBone> copy = EnumSet.noneOf(PlayerAnimationBone.class);
        copy.addAll(mask);
        return Set.copyOf(copy);
    }
}
