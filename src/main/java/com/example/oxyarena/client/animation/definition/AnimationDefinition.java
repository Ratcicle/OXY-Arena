package com.example.oxyarena.client.animation.definition;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import com.example.oxyarena.OXYArena;

import net.minecraft.resources.ResourceLocation;

public record AnimationDefinition(
        ResourceLocation id,
        int schemaVersion,
        ResourceLocation type,
        double lengthSeconds,
        AnimationLoopMode loop,
        AnimationApplySettings apply,
        List<AnimationTrack> tracks) {
    public static final int SCHEMA_VERSION = 1;
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "player_animation");

    public AnimationDefinition {
        id = Objects.requireNonNull(id, "id");
        type = Objects.requireNonNull(type, "type");
        loop = Objects.requireNonNull(loop, "loop");
        apply = Objects.requireNonNull(apply, "apply");
        tracks = List.copyOf(Objects.requireNonNull(tracks, "tracks"));
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported animation schema version: " + schemaVersion);
        }
        if (!TYPE.equals(type)) {
            throw new IllegalArgumentException("Unsupported animation type: " + type);
        }
        if (lengthSeconds <= 0.0D) {
            throw new IllegalArgumentException("lengthSeconds must be greater than 0 for animation " + id);
        }
        if (tracks.isEmpty()) {
            throw new IllegalArgumentException("Animation must contain at least one track: " + id);
        }

        for (AnimationTrack track : tracks) {
            validateTrack(id, lengthSeconds, track);
        }
    }

    public AnimationDefinition(
            ResourceLocation id,
            double lengthSeconds,
            AnimationLoopMode loop,
            AnimationApplySettings apply,
            List<AnimationTrack> tracks) {
        this(id, SCHEMA_VERSION, TYPE, lengthSeconds, loop, apply, tracks);
    }

    public Set<PlayerAnimationBone> resolvedMask() {
        if (!this.apply.derivesMaskFromTracks()) {
            return this.apply.mask();
        }

        EnumSet<PlayerAnimationBone> derived = EnumSet.noneOf(PlayerAnimationBone.class);
        for (AnimationTrack track : this.tracks) {
            derived.add(track.bone());
        }
        return Set.copyOf(derived);
    }

    private static void validateTrack(ResourceLocation id, double lengthSeconds, AnimationTrack track) {
        for (AnimationKeyframe keyframe : track.keyframes()) {
            if (keyframe.timestamp() > lengthSeconds) {
                throw new IllegalArgumentException(
                        "Keyframe timestamp exceeds animation length in " + id + ": " + keyframe.timestamp());
            }
        }
    }
}
