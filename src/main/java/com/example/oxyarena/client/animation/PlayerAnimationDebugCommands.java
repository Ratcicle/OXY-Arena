package com.example.oxyarena.client.animation;

import java.util.Comparator;
import java.util.stream.Collectors;

import com.example.oxyarena.client.animation.definition.AnimationDefinition;
import com.example.oxyarena.client.animation.definition.AnimationVector;
import com.example.oxyarena.client.animation.definition.PlayerAnimationBone;
import com.example.oxyarena.client.animation.runtime.BoneTransform;
import com.example.oxyarena.client.animation.runtime.PlayerAnimationPose;
import com.example.oxyarena.client.animation.runtime.PlayerAnimationSampler;
import com.mojang.brigadier.arguments.DoubleArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class PlayerAnimationDebugCommands {
    private static final String ARG_ID = "id";
    private static final String ARG_SECONDS = "seconds";

    private PlayerAnimationDebugCommands() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(PlayerAnimationDebugCommands::onRegisterClientCommands);
    }

    private static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("oxyanim")
                        .then(Commands.literal("list")
                                .executes(context -> listAnimations(context.getSource())))
                        .then(Commands.literal("sample")
                                .then(Commands.argument(ARG_ID, ResourceLocationArgument.id())
                                        .then(Commands.argument(ARG_SECONDS, DoubleArgumentType.doubleArg(0.0D))
                                                .executes(context -> sampleAnimation(
                                                        context.getSource(),
                                                        ResourceLocationArgument.getId(context, ARG_ID),
                                                        DoubleArgumentType.getDouble(context, ARG_SECONDS)))))));
    }

    private static int listAnimations(CommandSourceStack source) {
        if (PlayerAnimationDataManager.all().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No OXY player animations loaded."), false);
            return 0;
        }

        String ids = PlayerAnimationDataManager.all().keySet().stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .map(ResourceLocation::toString)
                .collect(Collectors.joining(", "));
        source.sendSuccess(
                () -> Component.literal("Loaded OXY player animations: " + ids),
                false);
        return PlayerAnimationDataManager.all().size();
    }

    private static int sampleAnimation(CommandSourceStack source, ResourceLocation id, double seconds) {
        AnimationDefinition definition = PlayerAnimationDataManager.get(id);
        if (definition == null) {
            source.sendSuccess(() -> Component.literal("OXY player animation not loaded: " + id), false);
            return 0;
        }

        PlayerAnimationPose pose = PlayerAnimationSampler.sample(definition, seconds);
        if (pose.isEmpty()) {
            source.sendSuccess(
                    () -> Component.literal("Sample " + id + " at " + seconds + "s: no active transforms"),
                    false);
            return 1;
        }

        source.sendSuccess(
                () -> Component.literal("Sample " + id + " at " + seconds + "s: " + formatPose(pose)),
                false);
        return pose.transforms().size();
    }

    private static String formatPose(PlayerAnimationPose pose) {
        return pose.transforms().entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().wireName()))
                .map(entry -> formatTransform(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining("; "));
    }

    private static String formatTransform(PlayerAnimationBone bone, BoneTransform transform) {
        return bone.wireName()
                + " pos=" + formatVector(transform.position())
                + " rot=" + formatVector(transform.rotation())
                + " scale=" + formatVector(transform.scale());
    }

    private static String formatVector(AnimationVector vector) {
        return String.format(java.util.Locale.ROOT, "[%.3f, %.3f, %.3f]", vector.x(), vector.y(), vector.z());
    }
}
