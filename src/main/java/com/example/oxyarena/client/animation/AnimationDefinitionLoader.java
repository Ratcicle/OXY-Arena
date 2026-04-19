package com.example.oxyarena.client.animation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.example.oxyarena.client.animation.definition.AnimationApplyBase;
import com.example.oxyarena.client.animation.definition.AnimationApplyMode;
import com.example.oxyarena.client.animation.definition.AnimationApplySettings;
import com.example.oxyarena.client.animation.definition.AnimationDefinition;
import com.example.oxyarena.client.animation.definition.AnimationInterpolation;
import com.example.oxyarena.client.animation.definition.AnimationKeyframe;
import com.example.oxyarena.client.animation.definition.AnimationLoopMode;
import com.example.oxyarena.client.animation.definition.AnimationTrack;
import com.example.oxyarena.client.animation.definition.AnimationTransformTarget;
import com.example.oxyarena.client.animation.definition.AnimationVector;
import com.example.oxyarena.client.animation.definition.PlayerAnimationBone;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public final class AnimationDefinitionLoader {
    private static final String FIELD_SCHEMA_VERSION = "schema_version";
    private static final String FIELD_TYPE = "type";
    private static final String FIELD_LENGTH = "length";
    private static final String FIELD_LOOP = "loop";
    private static final String FIELD_APPLY = "apply";
    private static final String FIELD_BASE = "base";
    private static final String FIELD_MODE = "mode";
    private static final String FIELD_BLEND_IN = "blend_in";
    private static final String FIELD_BLEND_OUT = "blend_out";
    private static final String FIELD_PRIORITY = "priority";
    private static final String FIELD_MASK = "mask";
    private static final String FIELD_ANIMATIONS = "animations";
    private static final String FIELD_BONE = "bone";
    private static final String FIELD_TARGET = "target";
    private static final String FIELD_KEYFRAMES = "keyframes";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final String FIELD_INTERPOLATION = "interpolation";

    private AnimationDefinitionLoader() {
    }

    public static AnimationDefinition parse(ResourceLocation id, JsonObject json) {
        if (json.has(FIELD_SCHEMA_VERSION)) {
            return parseOxyV1(id, json);
        }
        if (json.has(FIELD_LENGTH) && json.has(FIELD_ANIMATIONS)) {
            return parseRawBlockbench(id, json);
        }

        throw new IllegalArgumentException(
                "Animation JSON must contain schema_version or raw Blockbench length/animations fields");
    }

    private static AnimationDefinition parseOxyV1(ResourceLocation id, JsonObject json) {
        int schemaVersion = GsonHelper.getAsInt(json, FIELD_SCHEMA_VERSION);
        ResourceLocation type = ResourceLocation.parse(GsonHelper.getAsString(json, FIELD_TYPE));
        double lengthSeconds = GsonHelper.getAsDouble(json, FIELD_LENGTH);
        AnimationLoopMode loop = parseLoopMode(json);
        AnimationApplySettings apply = parseApplySettings(json);
        List<AnimationTrack> tracks = parseTracks(json);
        return new AnimationDefinition(id, schemaVersion, type, lengthSeconds, loop, apply, tracks);
    }

    private static AnimationDefinition parseRawBlockbench(ResourceLocation id, JsonObject json) {
        double lengthSeconds = GsonHelper.getAsDouble(json, FIELD_LENGTH);
        AnimationLoopMode loop = parseRawBlockbenchLoop(json);
        List<AnimationTrack> tracks = parseTracks(json);
        return new AnimationDefinition(id, lengthSeconds, loop, AnimationApplySettings.defaults(), tracks);
    }

    private static AnimationLoopMode parseLoopMode(JsonObject json) {
        if (!json.has(FIELD_LOOP)) {
            return AnimationLoopMode.NONE;
        }

        return AnimationLoopMode.fromWireName(GsonHelper.getAsString(json, FIELD_LOOP));
    }

    private static AnimationLoopMode parseRawBlockbenchLoop(JsonObject json) {
        if (!json.has(FIELD_LOOP)) {
            return AnimationLoopMode.NONE;
        }

        return GsonHelper.getAsBoolean(json, FIELD_LOOP) ? AnimationLoopMode.LOOP : AnimationLoopMode.NONE;
    }

    private static AnimationApplySettings parseApplySettings(JsonObject json) {
        if (!json.has(FIELD_APPLY)) {
            return AnimationApplySettings.defaults();
        }

        JsonObject apply = GsonHelper.getAsJsonObject(json, FIELD_APPLY);
        AnimationApplyBase base = apply.has(FIELD_BASE)
                ? AnimationApplyBase.fromWireName(GsonHelper.getAsString(apply, FIELD_BASE))
                : AnimationApplySettings.DEFAULT_BASE;
        AnimationApplyMode mode = apply.has(FIELD_MODE)
                ? AnimationApplyMode.fromWireName(GsonHelper.getAsString(apply, FIELD_MODE))
                : AnimationApplySettings.DEFAULT_MODE;
        double blendIn = GsonHelper.getAsDouble(apply, FIELD_BLEND_IN, AnimationApplySettings.DEFAULT_BLEND_IN_SECONDS);
        double blendOut = GsonHelper.getAsDouble(apply, FIELD_BLEND_OUT, AnimationApplySettings.DEFAULT_BLEND_OUT_SECONDS);
        int priority = GsonHelper.getAsInt(apply, FIELD_PRIORITY, AnimationApplySettings.DEFAULT_PRIORITY);
        Set<PlayerAnimationBone> mask = parseMask(apply);
        return new AnimationApplySettings(base, mode, blendIn, blendOut, priority, mask);
    }

    private static Set<PlayerAnimationBone> parseMask(JsonObject apply) {
        if (!apply.has(FIELD_MASK)) {
            return Set.of();
        }

        JsonArray maskArray = GsonHelper.getAsJsonArray(apply, FIELD_MASK);
        Set<PlayerAnimationBone> mask = new LinkedHashSet<>();
        for (JsonElement element : maskArray) {
            mask.add(PlayerAnimationBone.fromWireName(element.getAsString()));
        }
        return mask;
    }

    private static List<AnimationTrack> parseTracks(JsonObject json) {
        JsonArray animations = GsonHelper.getAsJsonArray(json, FIELD_ANIMATIONS);
        List<AnimationTrack> tracks = new ArrayList<>();
        for (JsonElement element : animations) {
            JsonObject track = GsonHelper.convertToJsonObject(element, "animation track");
            PlayerAnimationBone bone = PlayerAnimationBone.fromWireName(GsonHelper.getAsString(track, FIELD_BONE));
            AnimationTransformTarget target = AnimationTransformTarget.fromWireName(
                    GsonHelper.getAsString(track, FIELD_TARGET));
            tracks.add(new AnimationTrack(bone, target, parseKeyframes(track)));
        }
        return tracks;
    }

    private static List<AnimationKeyframe> parseKeyframes(JsonObject track) {
        JsonArray keyframes = GsonHelper.getAsJsonArray(track, FIELD_KEYFRAMES);
        List<AnimationKeyframe> parsed = new ArrayList<>();
        for (JsonElement element : keyframes) {
            JsonObject keyframe = GsonHelper.convertToJsonObject(element, "animation keyframe");
            double timestamp = GsonHelper.getAsDouble(keyframe, FIELD_TIMESTAMP);
            AnimationVector target = parseVector(keyframe);
            AnimationInterpolation interpolation = keyframe.has(FIELD_INTERPOLATION)
                    ? AnimationInterpolation.fromWireName(GsonHelper.getAsString(keyframe, FIELD_INTERPOLATION))
                    : AnimationInterpolation.LINEAR;
            parsed.add(new AnimationKeyframe(timestamp, target, interpolation));
        }
        return parsed;
    }

    private static AnimationVector parseVector(JsonObject keyframe) {
        JsonArray target = GsonHelper.getAsJsonArray(keyframe, FIELD_TARGET);
        if (target.size() != 3) {
            throw new IllegalArgumentException("Keyframe target must contain exactly 3 numeric values");
        }

        return new AnimationVector(
                target.get(0).getAsFloat(),
                target.get(1).getAsFloat(),
                target.get(2).getAsFloat());
    }
}
