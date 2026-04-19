package com.example.oxyarena.client.animation.runtime;

import java.util.Map;

import com.example.oxyarena.client.animation.definition.PlayerAnimationBone;

public record PlayerAnimationPose(Map<PlayerAnimationBone, BoneTransform> transforms) {
    public static final PlayerAnimationPose EMPTY = new PlayerAnimationPose(Map.of());

    public PlayerAnimationPose {
        transforms = Map.copyOf(transforms);
    }

    public boolean isEmpty() {
        return this.transforms.isEmpty();
    }

    public BoneTransform get(PlayerAnimationBone bone) {
        return this.transforms.getOrDefault(bone, BoneTransform.IDENTITY);
    }
}
