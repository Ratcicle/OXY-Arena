package com.example.oxyarena.serverevent;

import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

public final class InundacaoRuntimeSavedData extends SavedData {
    private static final String DATA_NAME = OXYArena.MODID + "_inundacao_runtime";
    private static final String AREA_TAG = "Area";
    private static final String MIN_X_TAG = "MinX";
    private static final String MAX_X_TAG = "MaxX";
    private static final String MIN_Z_TAG = "MinZ";
    private static final String MAX_Z_TAG = "MaxZ";
    private static final String FORCED_CHUNKS_TAG = "ForcedChunks";
    private static final String WATER_LAYERS_TAG = "WaterLayers";
    private static final Factory<InundacaoRuntimeSavedData> FACTORY = new Factory<>(
            InundacaoRuntimeSavedData::new,
            InundacaoRuntimeSavedData::load);

    @Nullable
    private ServerEventArea trackedArea;
    private final Map<Integer, BitSet> waterLayers = new HashMap<>();
    private final Set<Long> forcedChunks = new HashSet<>();

    public static InundacaoRuntimeSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        if (overworld == null) {
            throw new IllegalStateException("Overworld is not available for inundacao runtime storage");
        }

        return overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    private static InundacaoRuntimeSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        InundacaoRuntimeSavedData savedData = new InundacaoRuntimeSavedData();
        if (tag.contains(AREA_TAG)) {
            CompoundTag areaTag = tag.getCompound(AREA_TAG);
            savedData.trackedArea = new ServerEventArea(
                    areaTag.getInt(MIN_X_TAG),
                    areaTag.getInt(MAX_X_TAG),
                    areaTag.getInt(MIN_Z_TAG),
                    areaTag.getInt(MAX_Z_TAG));
        }

        for (long chunkLong : tag.getLongArray(FORCED_CHUNKS_TAG)) {
            savedData.forcedChunks.add(chunkLong);
        }

        CompoundTag layersTag = tag.getCompound(WATER_LAYERS_TAG);
        for (String yKey : layersTag.getAllKeys()) {
            savedData.waterLayers.put(Integer.parseInt(yKey), BitSet.valueOf(layersTag.getLongArray(yKey)));
        }

        return savedData;
    }

    @Nullable
    public ServerEventArea getTrackedArea() {
        return this.trackedArea;
    }

    public void beginTracking(ServerEventArea area) {
        this.trackedArea = area;
        this.setDirty();
    }

    public boolean hasTrackedState() {
        return this.trackedArea != null || !this.waterLayers.isEmpty() || !this.forcedChunks.isEmpty();
    }

    public boolean hasTrackedWater() {
        return !this.waterLayers.isEmpty();
    }

    public int getHighestTrackedWaterY() {
        return this.waterLayers.keySet().stream().mapToInt(Integer::intValue).max().orElse(Integer.MIN_VALUE);
    }

    public Map<Integer, BitSet> copyWaterLayers() {
        Map<Integer, BitSet> copy = new HashMap<>();
        this.waterLayers.forEach((y, mask) -> copy.put(y, (BitSet)mask.clone()));
        return copy;
    }

    public Set<Long> copyForcedChunks() {
        return new HashSet<>(this.forcedChunks);
    }

    public BitSet getLayerMask(int y) {
        return this.waterLayers.computeIfAbsent(y, ignored -> new BitSet());
    }

    public BitSet getExistingLayerMask(int y) {
        return this.waterLayers.get(y);
    }

    public boolean markWaterPlaced(int y, int index) {
        BitSet mask = this.getLayerMask(y);
        if (mask.get(index)) {
            return false;
        }

        mask.set(index);
        this.setDirty();
        return true;
    }

    public boolean clearTrackedWater(int y, int index) {
        BitSet mask = this.waterLayers.get(y);
        if (mask == null || !mask.get(index)) {
            return false;
        }

        mask.clear(index);
        if (mask.isEmpty()) {
            this.waterLayers.remove(y);
        }
        this.setDirty();
        return true;
    }

    public void removeLayer(int y) {
        if (this.waterLayers.remove(y) != null) {
            this.setDirty();
        }
    }

    public boolean rememberForcedChunk(long chunkLong) {
        if (!this.forcedChunks.add(chunkLong)) {
            return false;
        }

        this.setDirty();
        return true;
    }

    public boolean forgetForcedChunk(long chunkLong) {
        if (!this.forcedChunks.remove(chunkLong)) {
            return false;
        }

        this.setDirty();
        return true;
    }

    public void clearAll() {
        this.trackedArea = null;
        this.waterLayers.clear();
        this.forcedChunks.clear();
        this.setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (this.trackedArea != null) {
            CompoundTag areaTag = new CompoundTag();
            areaTag.putInt(MIN_X_TAG, this.trackedArea.minX());
            areaTag.putInt(MAX_X_TAG, this.trackedArea.maxX());
            areaTag.putInt(MIN_Z_TAG, this.trackedArea.minZ());
            areaTag.putInt(MAX_Z_TAG, this.trackedArea.maxZ());
            tag.put(AREA_TAG, areaTag);
        }

        long[] forcedChunkArray = this.forcedChunks.stream().mapToLong(Long::longValue).toArray();
        tag.putLongArray(FORCED_CHUNKS_TAG, forcedChunkArray);

        CompoundTag layersTag = new CompoundTag();
        this.waterLayers.forEach((y, mask) -> layersTag.putLongArray(Integer.toString(y), mask.toLongArray()));
        tag.put(WATER_LAYERS_TAG, layersTag);
        return tag;
    }
}
