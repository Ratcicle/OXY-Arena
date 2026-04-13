package com.example.oxyarena.util;

import net.minecraft.util.Mth;

public final class OccultCamouflageTuning {
    public static final int ARMING_TICKS = 40;
    public static final int FADE_IN_TICKS = 12;
    public static final float PARTIAL_FLOOR_PROGRESS = 0.35F;
    public static final float PARTIAL_DECAY_PER_TICK = 0.08F;
    public static final double MOVEMENT_EPSILON_SQR = 1.0E-4D;
    public static final double MICRO_MOVEMENT_SQR = 0.009D;
    public static final float NETWORK_RECONCILE_THRESHOLD = 0.18F;
    public static final float LOCAL_SOFT_RECONCILE_FACTOR = 0.2F;
    public static final float LOCAL_HARD_RECONCILE_FACTOR = 0.55F;
    public static final float RENDER_LERP_IN_PER_TICK = 0.28F;
    public static final float RENDER_LERP_OUT_PER_TICK = 0.36F;
    public static final float CROSSFADE_EPSILON = 0.01F;
    public static final float INTERIOR_ALPHA_SCALE = 0.032F;
    public static final float EDGE_ALPHA_SCALE = 0.18F;
    public static final float SHIMMER_ALPHA_SCALE = 0.11F;
    public static final float EDGE_SHELL_SCALE = 1.006F;
    public static final float SHIMMER_U_SPEED = 0.0085F;
    public static final float SHIMMER_V_SPEED = -0.0065F;
    public static final float SHIMMER_UV_SCALE = 5.0F;
    public static final float SHIMMER_VIEW_SCALE = 0.08F;

    private OccultCamouflageTuning() {
    }

    public static float quantizedToProgress(int quantizedProgress) {
        return Mth.clamp(quantizedProgress, 0, 255) / 255.0F;
    }

    public static int progressToQuantized(float progress) {
        return Mth.clamp(Mth.floor(Mth.clamp(progress, 0.0F, 1.0F) * 255.0F + 0.5F), 0, 255);
    }

    public static float fadeInStep() {
        return 1.0F / FADE_IN_TICKS;
    }

    public static boolean isMicroMovement(double deltaMovementSqr, boolean crouching) {
        return crouching
                && deltaMovementSqr > MOVEMENT_EPSILON_SQR
                && deltaMovementSqr <= MICRO_MOVEMENT_SQR;
    }

    public static boolean isNormalMovement(double deltaMovementSqr, boolean crouching) {
        return deltaMovementSqr > MICRO_MOVEMENT_SQR || (deltaMovementSqr > MOVEMENT_EPSILON_SQR && !crouching);
    }
}
