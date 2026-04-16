package com.example.oxyarena.combatstatus;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.oxyarena.OXYArena;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

public final class CombatStatusDataManager extends SimplePreparableReloadListener<CombatStatusDataManager.CombatStatusData> {
    private static final Gson GSON = new Gson();
    private static final String COMBAT_STATUSES_DIRECTORY = "combat_statuses";
    private static final String ITEM_APPLICATIONS_DIRECTORY = "item_combat_status_applications";
    private static final CombatStatusDataManager INSTANCE = new CombatStatusDataManager();
    private static volatile CombatStatusData currentData = CombatStatusData.EMPTY;

    private CombatStatusDataManager() {
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public static CombatStatusDefinition getDefinition(ResourceLocation statusId) {
        return currentData.definitions().get(statusId);
    }

    public static List<CombatStatusApplication> getApplications(ItemStack stack) {
        if (stack.isEmpty()) {
            return List.of();
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return currentData.applicationsByItem().getOrDefault(itemId, List.of());
    }

    @Override
    protected CombatStatusData prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, CombatStatusDefinition> definitions = loadDefinitions(resourceManager);
        Map<ResourceLocation, List<CombatStatusApplication>> applicationsByItem = loadItemApplications(
                resourceManager,
                definitions);
        return new CombatStatusData(
                Map.copyOf(definitions),
                copyNestedLists(applicationsByItem));
    }

    @Override
    protected void apply(CombatStatusData prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        currentData = prepared;
        OXYArena.LOGGER.debug(
                "Loaded {} combat statuses and {} item combat status bindings",
                prepared.definitions().size(),
                prepared.applicationsByItem().size());
    }

    private static Map<ResourceLocation, CombatStatusDefinition> loadDefinitions(ResourceManager resourceManager) {
        Map<ResourceLocation, CombatStatusDefinition> definitions = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager
                .listResources(COMBAT_STATUSES_DIRECTORY, path -> path.getPath().endsWith(".json"))
                .entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation statusId = toDataId(fileId, COMBAT_STATUSES_DIRECTORY);
            try (var reader = entry.getValue().openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                CombatStatusDefinition definition = parseDefinition(statusId, json);
                definitions.put(statusId, definition);
            } catch (Exception exception) {
                OXYArena.LOGGER.error("Failed to load combat status definition {}", fileId, exception);
            }
        }

        return definitions;
    }

    private static Map<ResourceLocation, List<CombatStatusApplication>> loadItemApplications(
            ResourceManager resourceManager,
            Map<ResourceLocation, CombatStatusDefinition> definitions) {
        Map<ResourceLocation, List<CombatStatusApplication>> applicationsByItem = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager
                .listResources(ITEM_APPLICATIONS_DIRECTORY, path -> path.getPath().endsWith(".json"))
                .entrySet()) {
            ResourceLocation fileId = entry.getKey();
            try (var reader = entry.getValue().openAsReader()) {
                JsonObject json = GsonHelper.parse(reader);
                ResourceLocation itemId = ResourceLocation.parse(GsonHelper.getAsString(json, "item"));
                JsonArray applicationsArray = GsonHelper.getAsJsonArray(json, "applications");
                List<CombatStatusApplication> parsedApplications = new ArrayList<>();
                for (JsonElement element : applicationsArray) {
                    JsonObject applicationObject = element.getAsJsonObject();
                    ResourceLocation statusId = ResourceLocation.parse(
                            GsonHelper.getAsString(applicationObject, "status"));
                    if (!definitions.containsKey(statusId)) {
                        OXYArena.LOGGER.warn(
                                "Skipping combat status application {} -> {} because the status definition does not exist",
                                itemId,
                                statusId);
                        continue;
                    }

                    parsedApplications.add(new CombatStatusApplication(
                            statusId,
                            (float)GsonHelper.getAsDouble(applicationObject, "buildup_per_hit")));
                }

                if (!parsedApplications.isEmpty()) {
                    applicationsByItem.put(itemId, List.copyOf(parsedApplications));
                }
            } catch (Exception exception) {
                OXYArena.LOGGER.error("Failed to load item combat status applications {}", fileId, exception);
            }
        }

        return applicationsByItem;
    }

    private static CombatStatusDefinition parseDefinition(ResourceLocation statusId, JsonObject json) {
        return new CombatStatusDefinition(
                statusId,
                (float)GsonHelper.getAsDouble(json, "max_buildup"),
                GsonHelper.getAsInt(json, "decay_delay_ticks"),
                (float)GsonHelper.getAsDouble(json, "decay_per_tick"),
                (float)GsonHelper.getAsDouble(json, "proc_flat_damage"),
                (float)GsonHelper.getAsDouble(json, "proc_max_health_ratio"),
                GsonHelper.getAsBoolean(json, "reset_on_proc"),
                GsonHelper.getAsBoolean(json, "overflow_carry"),
                GsonHelper.getAsString(json, "damage_source"),
                ResourceLocation.parse(GsonHelper.getAsString(json, "hud_icon")),
                ResourceLocation.parse(GsonHelper.getAsString(json, "hud_bar")),
                GsonHelper.getAsString(json, "proc_particle_style"));
    }

    private static ResourceLocation toDataId(ResourceLocation fileId, String directory) {
        String path = fileId.getPath();
        String relativePath = path.substring(directory.length() + 1, path.length() - ".json".length());
        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), relativePath);
    }

    private static Map<ResourceLocation, List<CombatStatusApplication>> copyNestedLists(
            Map<ResourceLocation, List<CombatStatusApplication>> source) {
        Map<ResourceLocation, List<CombatStatusApplication>> copy = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, List<CombatStatusApplication>> entry : source.entrySet()) {
            copy.put(entry.getKey(), List.copyOf(entry.getValue()));
        }

        return Map.copyOf(copy);
    }

    protected record CombatStatusData(
            Map<ResourceLocation, CombatStatusDefinition> definitions,
            Map<ResourceLocation, List<CombatStatusApplication>> applicationsByItem) {
        private static final CombatStatusData EMPTY = new CombatStatusData(Map.of(), Map.of());
    }
}
