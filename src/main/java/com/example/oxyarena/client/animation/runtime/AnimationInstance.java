package com.example.oxyarena.client.animation.runtime;

import com.example.oxyarena.client.animation.definition.AnimationDefinition;
import com.example.oxyarena.client.animation.definition.AnimationLoopMode;

import net.minecraft.util.Mth;

public record AnimationInstance(AnimationDefinition definition, long startGameTime) {
    private static final double SECONDS_PER_TICK = 1.0D / 20.0D;

    public double elapsedSeconds(long gameTime, float partialTick) {
        return Math.max(0.0D, (gameTime - this.startGameTime + partialTick) * SECONDS_PER_TICK);
    }

    public PlayerAnimationPose sample(long gameTime, float partialTick) {
        double elapsedSeconds = this.elapsedSeconds(gameTime, partialTick);
        if (this.definition.loop() == AnimationLoopMode.NONE && elapsedSeconds > this.definition.lengthSeconds()) {
            elapsedSeconds = this.definition.lengthSeconds();
        }

        return PlayerAnimationSampler.sample(this.definition, elapsedSeconds);
    }

    public float blendWeight(long gameTime, float partialTick) {
        double elapsedSeconds = this.elapsedSeconds(gameTime, partialTick);
        double weight = 1.0D;
        double blendInSeconds = this.definition.apply().blendInSeconds();
        if (blendInSeconds > 0.0D && elapsedSeconds < blendInSeconds) {
            weight = elapsedSeconds / blendInSeconds;
        }

        if (this.definition.loop() == AnimationLoopMode.NONE && elapsedSeconds >= this.definition.lengthSeconds()) {
            double blendOutSeconds = this.definition.apply().blendOutSeconds();
            if (blendOutSeconds <= 0.0D) {
                return 0.0F;
            }

            double blendOutProgress = (elapsedSeconds - this.definition.lengthSeconds()) / blendOutSeconds;
            weight = Math.min(weight, 1.0D - blendOutProgress);
        }

        return (float)Mth.clamp(weight, 0.0D, 1.0D);
    }

    public boolean isExpired(long gameTime) {
        if (this.definition.loop() != AnimationLoopMode.NONE) {
            return false;
        }

        double elapsedSeconds = this.elapsedSeconds(gameTime, 0.0F);
        return elapsedSeconds > this.definition.lengthSeconds() + this.definition.apply().blendOutSeconds();
    }
}
