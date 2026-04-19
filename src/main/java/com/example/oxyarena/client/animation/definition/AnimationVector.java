package com.example.oxyarena.client.animation.definition;

public record AnimationVector(float x, float y, float z) {
    public static final AnimationVector ZERO = new AnimationVector(0.0F, 0.0F, 0.0F);
    public static final AnimationVector ONE = new AnimationVector(1.0F, 1.0F, 1.0F);
}
