package com.example.oxyarena.client.animation;

import java.util.LinkedHashMap;
import java.util.Map;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.client.animation.definition.AnimationDefinition;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

public final class PlayerAnimationDataManager
        extends SimplePreparableReloadListener<Map<ResourceLocation, AnimationDefinition>> {
    private static final String PLAYER_ANIMATIONS_DIRECTORY = "animations/player";
    private static final PlayerAnimationDataManager INSTANCE = new PlayerAnimationDataManager();
    private static volatile Map<ResourceLocation, AnimationDefinition> currentAnimations = Map.of();

    private PlayerAnimationDataManager() {
    }

    public static void onRegisterClientReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(INSTANCE);
    }

    public static AnimationDefinition get(ResourceLocation id) {
        return currentAnimations.get(id);
    }

    public static boolean contains(ResourceLocation id) {
        return currentAnimations.containsKey(id);
    }

    public static Map<ResourceLocation, AnimationDefinition> all() {
        return currentAnimations;
    }

    @Override
    protected Map<ResourceLocation, AnimationDefinition> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, AnimationDefinition> animations = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager
                .listResources(PLAYER_ANIMATIONS_DIRECTORY, path -> path.getPath().endsWith(".json"))
                .entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation animationId = toAnimationId(fileId);
            try (var reader = entry.getValue().openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                AnimationDefinition definition = AnimationDefinitionLoader.parse(animationId, json);
                animations.put(animationId, definition);
            } catch (Exception exception) {
                OXYArena.LOGGER.error("Failed to load player animation {}", fileId, exception);
            }
        }

        return Map.copyOf(animations);
    }

    @Override
    protected void apply(
            Map<ResourceLocation, AnimationDefinition> prepared,
            ResourceManager resourceManager,
            ProfilerFiller profiler) {
        currentAnimations = prepared;
        OXYArena.LOGGER.debug("Loaded {} player animation definitions", prepared.size());
    }

    private static ResourceLocation toAnimationId(ResourceLocation fileId) {
        String path = fileId.getPath();
        String relativePath = path.substring(
                PLAYER_ANIMATIONS_DIRECTORY.length() + 1,
                path.length() - ".json".length());
        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), relativePath);
    }
}
