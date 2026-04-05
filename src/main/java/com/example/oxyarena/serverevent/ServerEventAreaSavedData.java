package com.example.oxyarena.serverevent;

import java.util.HashMap;
import java.util.Map;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class ServerEventAreaSavedData extends SavedData {
    private static final String DATA_NAME = OXYArena.MODID + "_server_event_areas";
    private static final String AREAS_TAG = "Areas";
    private static final String MIN_X_TAG = "MinX";
    private static final String MAX_X_TAG = "MaxX";
    private static final String MIN_Z_TAG = "MinZ";
    private static final String MAX_Z_TAG = "MaxZ";
    private static final Factory<ServerEventAreaSavedData> FACTORY = new Factory<>(
            ServerEventAreaSavedData::new,
            ServerEventAreaSavedData::load);

    private final Map<String, ServerEventArea> areas = new HashMap<>();

    public static ServerEventAreaSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            throw new IllegalStateException("Overworld is not available for server event area storage");
        }

        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static ServerEventAreaSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ServerEventAreaSavedData savedData = new ServerEventAreaSavedData();
        CompoundTag areasTag = tag.getCompound(AREAS_TAG);
        for (String areaId : areasTag.getAllKeys()) {
            CompoundTag areaTag = areasTag.getCompound(areaId);
            savedData.areas.put(
                    areaId,
                    new ServerEventArea(
                            areaTag.getInt(MIN_X_TAG),
                            areaTag.getInt(MAX_X_TAG),
                            areaTag.getInt(MIN_Z_TAG),
                            areaTag.getInt(MAX_Z_TAG)));
        }

        return savedData;
    }

    public ServerEventArea getArea(String areaId, ServerEventArea fallbackArea) {
        return this.areas.getOrDefault(areaId, fallbackArea);
    }

    public void setArea(String areaId, ServerEventArea area) {
        this.areas.put(areaId, area);
        this.setDirty();
    }

    public void resetArea(String areaId) {
        if (this.areas.remove(areaId) != null) {
            this.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag areasTag = new CompoundTag();
        this.areas.forEach((areaId, area) -> {
            CompoundTag areaTag = new CompoundTag();
            areaTag.putInt(MIN_X_TAG, area.minX());
            areaTag.putInt(MAX_X_TAG, area.maxX());
            areaTag.putInt(MIN_Z_TAG, area.minZ());
            areaTag.putInt(MAX_Z_TAG, area.maxZ());
            areasTag.put(areaId, areaTag);
        });
        tag.put(AREAS_TAG, areasTag);
        return tag;
    }
}
