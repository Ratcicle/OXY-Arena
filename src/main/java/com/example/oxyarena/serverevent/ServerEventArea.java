package com.example.oxyarena.serverevent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.AABB;

public record ServerEventArea(int minX, int maxX, int minZ, int maxZ) {
    public ServerEventArea {
        if (minX > maxX) {
            int swappedMinX = maxX;
            maxX = minX;
            minX = swappedMinX;
        }

        if (minZ > maxZ) {
            int swappedMinZ = maxZ;
            maxZ = minZ;
            minZ = swappedMinZ;
        }
    }

    public boolean contains(double x, double z) {
        return x >= this.minX && x <= this.maxX && z >= this.minZ && z <= this.maxZ;
    }

    public int randomX(RandomSource random) {
        return random.nextIntBetweenInclusive(this.minX, this.maxX);
    }

    public int randomZ(RandomSource random) {
        return random.nextIntBetweenInclusive(this.minZ, this.maxZ);
    }

    public int minChunkX() {
        return Math.floorDiv(this.minX, 16);
    }

    public int maxChunkX() {
        return Math.floorDiv(this.maxX, 16);
    }

    public int minChunkZ() {
        return Math.floorDiv(this.minZ, 16);
    }

    public int maxChunkZ() {
        return Math.floorDiv(this.maxZ, 16);
    }

    public AABB createAabb(ServerLevel level) {
        return new AABB(
                this.minX,
                level.getMinBuildHeight(),
                this.minZ,
                this.maxX + 1.0D,
                level.getMaxBuildHeight(),
                this.maxZ + 1.0D);
    }
}
