package com.example.oxyarena.event;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.example.oxyarena.registry.ModBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class SoulReaperFireHelper {
    private static final int MAX_RANGE = 8;
    private static final int COOLDOWN_TICKS = 100;
    private static final int FIRE_DURATION_TICKS = MAX_RANGE;
    private static final float VANILLA_FIRE_CONTACT_SECONDS = 8.0F;
    private static final float IMMEDIATE_DAMAGE = 4.0F;
    private static final float DOT_DAMAGE = 2.0F;
    private static final Direction[] STAR_DIRECTIONS = new Direction[] {
            Direction.NORTH,
            Direction.EAST,
            Direction.SOUTH,
            Direction.WEST
    };
    private static final int[][] DIAGONALS = new int[][] {
            { 1, -1 },
            { 1, 1 },
            { -1, 1 },
            { -1, -1 }
    };
    private static final List<FirePatternActivation> ACTIVE_PATTERNS = new ArrayList<>();
    private static final Map<FireCellKey, EnumMap<FireType, Integer>> ACTIVE_FIRE_COUNTS = new HashMap<>();
    private static final Map<UUID, BurningEntityState> BURNING_ENTITIES = new HashMap<>();

    private SoulReaperFireHelper() {
    }

    public static int cooldownTicks() {
        return COOLDOWN_TICKS;
    }

    public static void activate(Player player, boolean spawnSoulFire) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos origin = resolveOriginCell(serverLevel, player);
        if (origin == null) {
            return;
        }

        FireType fireType = spawnSoulFire ? FireType.SOUL : FireType.NORMAL;
        ACTIVE_PATTERNS.add(new FirePatternActivation(
                serverLevel.dimension(),
                player.getUUID(),
                fireType,
                computeLayers(serverLevel, origin),
                serverLevel.getServer().getTickCount()));
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (ACTIVE_PATTERNS.isEmpty() && BURNING_ENTITIES.isEmpty()) {
            return;
        }

        int currentTick = event.getServer().getTickCount();
        Iterator<FirePatternActivation> iterator = ACTIVE_PATTERNS.iterator();
        while (iterator.hasNext()) {
            FirePatternActivation activation = iterator.next();
            if (activation.tick(event.getServer(), currentTick)) {
                iterator.remove();
            }
        }

        cleanupBurningEntities(event.getServer(), currentTick);
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        clearServerState(event.getServer());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE_PATTERNS.clear();
        ACTIVE_FIRE_COUNTS.clear();
        BURNING_ENTITIES.clear();
    }

    private static void clearServerState(MinecraftServer server) {
        for (FireCellKey key : List.copyOf(ACTIVE_FIRE_COUNTS.keySet())) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                continue;
            }

            BlockState state = level.getBlockState(key.pos());
            if (isSoulReaperFireBlock(state)) {
                level.removeBlock(key.pos(), false);
            }
        }

        ACTIVE_PATTERNS.clear();
        ACTIVE_FIRE_COUNTS.clear();
        BURNING_ENTITIES.clear();
    }

    private static List<List<BlockPos>> computeLayers(ServerLevel level, BlockPos origin) {
        List<List<BlockPos>> layers = new ArrayList<>(MAX_RANGE);
        for (int index = 0; index < MAX_RANGE; index++) {
            layers.add(new ArrayList<>());
        }

        layers.get(0).add(origin.immutable());

        for (Direction direction : STAR_DIRECTIONS) {
            appendRay(level, origin, direction.getStepX(), direction.getStepZ(), layers);
        }

        for (int[] diagonal : DIAGONALS) {
            appendRay(level, origin, diagonal[0], diagonal[1], layers);
        }

        return layers;
    }

    private static void appendRay(ServerLevel level, BlockPos origin, int stepX, int stepZ, List<List<BlockPos>> layers) {
        BlockPos previous = origin;
        for (int layerIndex = 1; layerIndex < MAX_RANGE; layerIndex++) {
            BlockPos next = findNextFireCell(level, previous, stepX, stepZ);
            if (next == null) {
                return;
            }

            layers.get(layerIndex).add(next);
            previous = next;
        }
    }

    private static BlockPos resolveOriginCell(ServerLevel level, Player player) {
        BlockPos feet = player.blockPosition();
        if (canUseAsFireCell(level, feet)) {
            return feet;
        }
        if (canUseAsFireCell(level, feet.below())) {
            return feet.below();
        }
        if (canUseAsFireCell(level, feet.above())) {
            return feet.above();
        }
        return null;
    }

    private static BlockPos findNextFireCell(ServerLevel level, BlockPos previous, int stepX, int stepZ) {
        BlockPos target = previous.offset(stepX, 0, stepZ);
        BlockPos[] candidates = new BlockPos[] {
                target,
                target.above(),
                target.below()
        };

        for (BlockPos candidate : candidates) {
            if (canUseAsFireCell(level, candidate)) {
                return candidate.immutable();
            }
        }

        return null;
    }

    private static boolean canUseAsFireCell(ServerLevel level, BlockPos pos) {
        if (pos.getY() <= level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        if (!level.isLoaded(pos) || !level.isLoaded(pos.below())) {
            return false;
        }

        BlockPos supportPos = pos.below();
        BlockState supportState = level.getBlockState(supportPos);
        if (!supportState.isFaceSturdy(level, supportPos, Direction.UP)) {
            return false;
        }

        BlockState state = level.getBlockState(pos);
        if (isSoulReaperFireBlock(state) || state.isAir()) {
            return true;
        }

        return canBeClearedForSoulReaperFire(state);
    }

    private static boolean canBeClearedForSoulReaperFire(BlockState state) {
        if (!state.getFluidState().isEmpty()) {
            return false;
        }

        if (state.canBeReplaced()) {
            return true;
        }

        Block block = state.getBlock();
        return block instanceof BushBlock || block instanceof VineBlock;
    }

    private static void registerFireCell(ServerLevel level, BlockPos pos, FireType fireType) {
        FireCellKey key = new FireCellKey(level.dimension(), pos.immutable());
        EnumMap<FireType, Integer> counts = ACTIVE_FIRE_COUNTS.computeIfAbsent(key, ignored -> new EnumMap<>(FireType.class));
        counts.merge(fireType, 1, Integer::sum);
        syncBlockState(level, key, counts);
    }

    private static void unregisterFireCell(ServerLevel level, BlockPos pos, FireType fireType) {
        FireCellKey key = new FireCellKey(level.dimension(), pos.immutable());
        EnumMap<FireType, Integer> counts = ACTIVE_FIRE_COUNTS.get(key);
        if (counts == null) {
            return;
        }

        counts.computeIfPresent(fireType, (type, current) -> current > 1 ? current - 1 : null);
        if (counts.isEmpty()) {
            ACTIVE_FIRE_COUNTS.remove(key);
        }

        syncBlockState(level, key, counts);
    }

    private static void syncBlockState(ServerLevel level, FireCellKey key, EnumMap<FireType, Integer> counts) {
        BlockState currentState = level.getBlockState(key.pos());
        BlockState desiredState = null;
        if (counts != null && counts.getOrDefault(FireType.SOUL, 0) > 0) {
            desiredState = ModBlocks.SOUL_REAPER_SOUL_FIRE.get().defaultBlockState();
        } else if (counts != null && counts.getOrDefault(FireType.NORMAL, 0) > 0) {
            desiredState = ModBlocks.SOUL_REAPER_FIRE.get().defaultBlockState();
        }

        if (desiredState == null) {
            if (isSoulReaperFireBlock(currentState)) {
                level.removeBlock(key.pos(), false);
            }
            return;
        }

        if (!currentState.equals(desiredState)) {
            level.setBlockAndUpdate(key.pos(), desiredState);
        }
    }

    private static boolean isSoulReaperFireBlock(BlockState state) {
        return state.is(ModBlocks.SOUL_REAPER_FIRE.get()) || state.is(ModBlocks.SOUL_REAPER_SOUL_FIRE.get());
    }

    private static void damageEntitiesOnBlock(ServerLevel level, BlockPos pos, UUID ownerId, float damage) {
        AABB bounds = new AABB(pos).expandTowards(0.0D, 0.25D, 0.0D);
        for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, bounds)) {
            if (livingEntity.getUUID().equals(ownerId)) {
                continue;
            }

            livingEntity.invulnerableTime = 0;
            livingEntity.hurtTime = 0;
            livingEntity.hurt(level.damageSources().magic(), damage);
        }
    }

    private static void refreshBurningEntitiesOnBlock(ServerLevel level, BlockPos pos, UUID ownerId, int currentTick) {
        AABB bounds = new AABB(pos).expandTowards(0.0D, 0.25D, 0.0D);
        for (LivingEntity livingEntity : level.getEntitiesOfClass(LivingEntity.class, bounds)) {
            if (livingEntity.getUUID().equals(ownerId)) {
                continue;
            }

            if (!livingEntity.fireImmune()) {
                livingEntity.setRemainingFireTicks(livingEntity.getRemainingFireTicks() + 1);
                if (livingEntity.getRemainingFireTicks() == 0) {
                    livingEntity.igniteForSeconds(VANILLA_FIRE_CONTACT_SECONDS);
                }
            }

            int expiresAtTick = currentTick + Math.max(livingEntity.getRemainingFireTicks(), 1);
            BURNING_ENTITIES.compute(
                    livingEntity.getUUID(),
                    (uuid, existingState) -> existingState == null
                            ? new BurningEntityState(level.dimension(), livingEntity.getUUID(), expiresAtTick)
                            : existingState.refresh(level.dimension(), expiresAtTick));
        }
    }

    private static void cleanupBurningEntities(MinecraftServer server, int currentTick) {
        Iterator<BurningEntityState> iterator = BURNING_ENTITIES.values().iterator();
        while (iterator.hasNext()) {
            BurningEntityState state = iterator.next();
            ServerLevel level = server.getLevel(state.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }

            Entity entity = level.getEntity(state.entityId());
            if (!(entity instanceof LivingEntity livingEntity) || !livingEntity.isAlive()) {
                iterator.remove();
                continue;
            }

            if (currentTick > state.expiresAtTick() || !livingEntity.isOnFire()) {
                iterator.remove();
                continue;
            }
        }
    }

    public static void adjustFireTickDamage(LivingEntity livingEntity, int currentTick, float currentDamage, java.util.function.Consumer<Float> damageSetter) {
        BurningEntityState state = BURNING_ENTITIES.get(livingEntity.getUUID());
        if (state == null) {
            return;
        }

        if (currentTick > state.expiresAtTick()) {
            BURNING_ENTITIES.remove(livingEntity.getUUID());
            return;
        }

        damageSetter.accept(Math.max(currentDamage, DOT_DAMAGE));
    }

    private record FireCellKey(ResourceKey<Level> dimension, BlockPos pos) {
    }

    public enum FireType {
        NORMAL,
        SOUL
    }

    private static final class FirePatternActivation {
        private final ResourceKey<Level> dimension;
        private final UUID ownerId;
        private final FireType fireType;
        private final List<List<BlockPos>> layers;
        private final List<BlockPos> activeBlocks = new ArrayList<>();
        private final int activationTick;

        private FirePatternActivation(
                ResourceKey<Level> dimension,
                UUID ownerId,
                FireType fireType,
                List<List<BlockPos>> layers,
                int activationTick) {
            this.dimension = dimension;
            this.ownerId = ownerId;
            this.fireType = fireType;
            this.layers = layers;
            this.activationTick = activationTick;
        }

        private boolean tick(MinecraftServer server, int currentTick) {
            ServerLevel level = server.getLevel(this.dimension);
            if (level == null) {
                this.clearRemaining(server);
                return true;
            }

            int age = currentTick - this.activationTick;
            if (age >= 0 && age < this.layers.size()) {
                this.igniteLayer(level, age, currentTick);
            }

            int extinguishIndex = age - FIRE_DURATION_TICKS;
            if (extinguishIndex >= 0 && extinguishIndex < this.layers.size()) {
                this.extinguishLayer(level, extinguishIndex);
            }

            this.applyDamageOverTime(level, currentTick);
            return extinguishIndex >= this.layers.size() - 1 && this.activeBlocks.isEmpty();
        }

        private void igniteLayer(ServerLevel level, int layerIndex, int currentTick) {
            for (BlockPos pos : this.layers.get(layerIndex)) {
                registerFireCell(level, pos, this.fireType);
                if (!this.activeBlocks.contains(pos)) {
                    this.activeBlocks.add(pos);
                }
                damageEntitiesOnBlock(level, pos, this.ownerId, IMMEDIATE_DAMAGE);
            }
        }

        private void extinguishLayer(ServerLevel level, int layerIndex) {
            for (BlockPos pos : this.layers.get(layerIndex)) {
                unregisterFireCell(level, pos, this.fireType);
                this.activeBlocks.remove(pos);
            }
        }

        private void applyDamageOverTime(ServerLevel level, int currentTick) {
            Iterator<BlockPos> iterator = this.activeBlocks.iterator();
            while (iterator.hasNext()) {
                BlockPos pos = iterator.next();
                if (!level.isLoaded(pos)) {
                    continue;
                }

                if (!isSoulReaperFireBlock(level.getBlockState(pos))) {
                    unregisterFireCell(level, pos, this.fireType);
                    iterator.remove();
                    continue;
                }

                refreshBurningEntitiesOnBlock(level, pos, this.ownerId, currentTick);
            }
        }

        private void clearRemaining(MinecraftServer server) {
            ServerLevel level = server.getLevel(this.dimension);
            if (level == null) {
                this.activeBlocks.clear();
                return;
            }

            for (BlockPos pos : List.copyOf(this.activeBlocks)) {
                unregisterFireCell(level, pos, this.fireType);
            }
            this.activeBlocks.clear();
        }
    }

    private static final class BurningEntityState {
        private ResourceKey<Level> dimension;
        private final UUID entityId;
        private int expiresAtTick;

        private BurningEntityState(ResourceKey<Level> dimension, UUID entityId, int expiresAtTick) {
            this.dimension = dimension;
            this.entityId = entityId;
            this.expiresAtTick = expiresAtTick;
        }

        private ResourceKey<Level> dimension() {
            return this.dimension;
        }

        private UUID entityId() {
            return this.entityId;
        }

        private int expiresAtTick() {
            return this.expiresAtTick;
        }

        private BurningEntityState refresh(ResourceKey<Level> newDimension, int newExpiresAtTick) {
            this.dimension = newDimension;
            this.expiresAtTick = newExpiresAtTick;
            return this;
        }
    }
}
