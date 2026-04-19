package com.example.oxyarena.event.gameplay;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.example.oxyarena.OXYArena;
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

public final class WeaponAttributesDataManager extends SimplePreparableReloadListener<WeaponAttributesDataManager.WeaponAttributeData> {
    private static final String WEAPON_ATTRIBUTES_DIRECTORY = "weapon_attributes";
    private static final WeaponAttributesDataManager INSTANCE = new WeaponAttributesDataManager();
    private static volatile WeaponAttributeData currentData = WeaponAttributeData.EMPTY;

    private WeaponAttributesDataManager() {
    }

    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener(INSTANCE);
    }

    public static boolean isTwoHanded(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return currentData.twoHandedItems().contains(itemId);
    }

    @Override
    protected WeaponAttributeData prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, JsonObject> definitions = loadDefinitions(resourceManager);
        Set<ResourceLocation> twoHandedItems = resolveTwoHandedItems(definitions);
        return new WeaponAttributeData(Set.copyOf(twoHandedItems));
    }

    @Override
    protected void apply(WeaponAttributeData prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        currentData = prepared;
        OXYArena.LOGGER.debug("Loaded {} two-handed weapon attribute bindings", prepared.twoHandedItems().size());
    }

    private static Map<ResourceLocation, JsonObject> loadDefinitions(ResourceManager resourceManager) {
        Map<ResourceLocation, JsonObject> definitions = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> entry : resourceManager
                .listResources(WEAPON_ATTRIBUTES_DIRECTORY, path -> path.getPath().endsWith(".json"))
                .entrySet()) {
            ResourceLocation fileId = entry.getKey();
            ResourceLocation attributeId = toDataId(fileId);
            try (var reader = entry.getValue().openAsReader()) {
                definitions.put(attributeId, GsonHelper.parse(reader));
            } catch (Exception exception) {
                OXYArena.LOGGER.error("Failed to load weapon attributes {}", fileId, exception);
            }
        }

        return definitions;
    }

    private static Set<ResourceLocation> resolveTwoHandedItems(Map<ResourceLocation, JsonObject> definitions) {
        Set<ResourceLocation> twoHandedItems = new HashSet<>();
        Map<ResourceLocation, Boolean> resolved = new HashMap<>();
        for (ResourceLocation attributeId : definitions.keySet()) {
            if (resolveTwoHanded(attributeId, definitions, resolved, new HashSet<>())) {
                twoHandedItems.add(attributeId);
            }
        }

        return twoHandedItems;
    }

    private static boolean resolveTwoHanded(
            ResourceLocation attributeId,
            Map<ResourceLocation, JsonObject> definitions,
            Map<ResourceLocation, Boolean> resolved,
            Set<ResourceLocation> resolving) {
        Boolean cached = resolved.get(attributeId);
        if (cached != null) {
            return cached;
        }

        JsonObject json = definitions.get(attributeId);
        if (json == null) {
            resolved.put(attributeId, false);
            return false;
        }

        if (!resolving.add(attributeId)) {
            OXYArena.LOGGER.warn("Detected cyclic weapon_attributes parent chain at {}", attributeId);
            resolved.put(attributeId, false);
            return false;
        }

        Boolean explicitValue = getExplicitTwoHandedValue(attributeId, json);
        boolean twoHanded;
        if (explicitValue != null) {
            twoHanded = explicitValue;
        } else {
            ResourceLocation parentId = getParentId(attributeId, json);
            twoHanded = parentId != null && resolveTwoHanded(parentId, definitions, resolved, resolving);
        }

        resolving.remove(attributeId);
        resolved.put(attributeId, twoHanded);
        return twoHanded;
    }

    private static Boolean getExplicitTwoHandedValue(ResourceLocation attributeId, JsonObject json) {
        if (!json.has("attributes") || !json.get("attributes").isJsonObject()) {
            return null;
        }

        JsonObject attributes = json.getAsJsonObject("attributes");
        if (!attributes.has("two_handed")) {
            return null;
        }

        try {
            return attributes.get("two_handed").getAsBoolean();
        } catch (Exception exception) {
            OXYArena.LOGGER.warn("Ignoring invalid two_handed value in weapon attributes {}", attributeId, exception);
            return null;
        }
    }

    private static ResourceLocation getParentId(ResourceLocation attributeId, JsonObject json) {
        if (!json.has("parent")) {
            return null;
        }

        try {
            return ResourceLocation.parse(GsonHelper.getAsString(json, "parent"));
        } catch (Exception exception) {
            OXYArena.LOGGER.warn("Ignoring invalid parent in weapon attributes {}", attributeId, exception);
            return null;
        }
    }

    private static ResourceLocation toDataId(ResourceLocation fileId) {
        String path = fileId.getPath();
        String relativePath = path.substring(
                WEAPON_ATTRIBUTES_DIRECTORY.length() + 1,
                path.length() - ".json".length());
        return ResourceLocation.fromNamespaceAndPath(fileId.getNamespace(), relativePath);
    }

    protected record WeaponAttributeData(Set<ResourceLocation> twoHandedItems) {
        private static final WeaponAttributeData EMPTY = new WeaponAttributeData(Set.of());
    }
}
