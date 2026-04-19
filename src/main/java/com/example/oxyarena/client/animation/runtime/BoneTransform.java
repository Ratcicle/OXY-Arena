package com.example.oxyarena.client.animation.runtime;

import com.example.oxyarena.client.animation.definition.AnimationVector;

public record BoneTransform(
        AnimationVector position,
        AnimationVector rotation,
        AnimationVector scale,
        boolean hasPosition,
        boolean hasRotation,
        boolean hasScale) {
    public static final BoneTransform IDENTITY = new BoneTransform(
            AnimationVector.ZERO,
            AnimationVector.ZERO,
            AnimationVector.ONE,
            false,
            false,
            false);

    public BoneTransform withPosition(AnimationVector position) {
        return new BoneTransform(position, this.rotation, this.scale, true, this.hasRotation, this.hasScale);
    }

    public BoneTransform withRotation(AnimationVector rotation) {
        return new BoneTransform(this.position, rotation, this.scale, this.hasPosition, true, this.hasScale);
    }

    public BoneTransform withScale(AnimationVector scale) {
        return new BoneTransform(this.position, this.rotation, scale, this.hasPosition, this.hasRotation, true);
    }
}
