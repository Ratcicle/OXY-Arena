package com.example.oxyarena.serverevent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.monster.Evoker;
import net.minecraft.world.entity.monster.PatrollingMonster;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Vex;
import net.minecraft.world.entity.monster.Vindicator;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class PillagerCaravanServerEvent implements OxyServerEvent {
    private static final int EVENT_DURATION_TICKS = 20 * 60 * 5;
    private static final int MAX_ACTIVE_CARAVANS = 5;
    private static final int SPAWN_INTERVAL_TICKS = 20 * 30;
    private static final int FIRST_SPAWN_DELAY_TICKS = 20 * 3;
    private static final int SPAWN_SEARCH_ATTEMPTS = 48;
    private static final int CAVE_SPAWN_SEARCH_ATTEMPTS = 72;
    private static final int CAVE_PATROL_SEARCH_ATTEMPTS = 32;
    private static final int CAVE_PLAYER_SURFACE_DEPTH = 8;
    private static final int CAVE_CARAVAN_MIN_DISTANCE = 12;
    private static final int CAVE_CARAVAN_MAX_DISTANCE = 36;
    private static final int CAVE_PATROL_RADIUS = 28;
    private static final int PATROL_RETARGET_TICKS = 20 * 8;
    private static final int FORMATION_TICK_INTERVAL = 20;
    private static final int EVOKER_DEATH_CASCADE_DELAY_TICKS = 20;
    private static final double VEX_TRACKING_RADIUS = 48.0D;
    private static final float CASCADE_DAMAGE = 10000.0F;
    private static final int[] SPAWN_Y_OFFSETS = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6};
    private static final ResourceLocation BOSSBAR_ID = ResourceLocation.fromNamespaceAndPath(OXYArena.MODID,
            "caravana_pillager");
    private static final String EVENT_MOB_TAG = OXYArena.MODID + ".pillager_caravan";
    private static final String CARAVAN_ID_TAG = "OxyPillagerCaravanId";
    private static final String FORMATION_INDEX_TAG = "OxyPillagerCaravanFormation";
    private static final String EVOKER_TAG = "OxyPillagerCaravanEvoker";

    private final Map<UUID, CaravanState> caravans = new HashMap<>();
    @Nullable
    private CustomBossEvent bossBar;
    private boolean active;
    private int timeRemainingTicks;
    private int spawnCooldownTicks;

    @Override
    public String getId() {
        return "caravana_pillager";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.caravana_pillager");
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public boolean start(MinecraftServer server) {
        if (this.active || server.getLevel(Level.OVERWORLD) == null) {
            return false;
        }

        this.cleanupStaleRuntimeArtifacts(server);
        this.active = true;
        this.timeRemainingTicks = EVENT_DURATION_TICKS;
        this.spawnCooldownTicks = FIRST_SPAWN_DELAY_TICKS;
        this.caravans.clear();
        this.bossBar = this.createBossBar(server);
        this.updateBossBar(server);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.caravana_pillager.started")
                        .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                false);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        if (!this.active && reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        this.active = false;
        this.timeRemainingTicks = 0;
        this.spawnCooldownTicks = 0;
        this.removeEventCaravans(server);
        this.caravans.clear();
        this.clearBossBar(server);

        if (reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("event.oxyarena.caravana_pillager.finished")
                            .withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD),
                    false);
        }
    }

    @Override
    public void tick(MinecraftServer server) {
        if (!this.active) {
            return;
        }

        this.timeRemainingTicks--;
        if (this.timeRemainingTicks <= 0) {
            this.stop(server, ServerEventStopReason.COMPLETED);
            return;
        }

        this.tickCaravans(server);
        if (--this.spawnCooldownTicks <= 0) {
            this.spawnCooldownTicks = SPAWN_INTERVAL_TICKS;
            this.trySpawnCaravan(server);
        }

        this.updateBossBar(server);
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
        if (!this.active || !(event.getEntity() instanceof Mob mob) || !mob.getTags().contains(EVENT_MOB_TAG)) {
            return;
        }

        UUID caravanId = getCaravanId(mob);
        if (caravanId == null) {
            return;
        }

        CaravanState caravan = this.caravans.get(caravanId);
        if (caravan == null) {
            return;
        }

        caravan.memberIds().remove(mob.getUUID());
        if (mob.getUUID().equals(caravan.evokerId()) || mob.getPersistentData().getBoolean(EVOKER_TAG)) {
            if (mob.level() instanceof ServerLevel level) {
                this.trackEvokerVexes(level, caravan, mob);
            }
            caravan.scheduleCascade(getPlayerKiller(event), EVOKER_DEATH_CASCADE_DELAY_TICKS);
        }
        if (caravan.memberIds().isEmpty()) {
            this.caravans.remove(caravanId);
        }
    }

    @Override
    public void onPlayerLoggedIn(MinecraftServer server, ServerPlayer player) {
        if (this.active && this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public void onPlayerChangedDimension(MinecraftServer server, ServerPlayer player) {
        if (this.active && this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public int getTimeRemainingTicks() {
        return this.timeRemainingTicks;
    }

    @Override
    public Component getStatusText() {
        return Component.translatable("event.oxyarena.caravana_pillager.status", this.caravans.size());
    }

    @Override
    public void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
        this.active = false;
        this.timeRemainingTicks = 0;
        this.spawnCooldownTicks = 0;
        this.caravans.clear();
        this.removeEventCaravans(server);
        this.clearBossBar(server);
    }

    private void trySpawnCaravan(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        this.pruneCaravans(server);
        if (this.caravans.size() < MAX_ACTIVE_CARAVANS) {
            this.spawnCaravan(overworld);
        }
    }

    private boolean spawnCaravan(ServerLevel level) {
        SpawnLocation spawnLocation = this.findRandomSpawnLocation(level);
        if (spawnLocation == null) {
            return false;
        }

        BlockPos center = spawnLocation.pos();
        UUID caravanId = UUID.randomUUID();
        float yaw = level.random.nextFloat() * 360.0F;
        CaravanState caravan = new CaravanState(spawnLocation.underground(), center);
        List<Mob> spawned = new ArrayList<>();

        Evoker evoker = this.createMob(level, EntityType.EVOKER, center, yaw, 0, caravanId, spawnLocation.underground());
        if (evoker == null) {
            return false;
        }
        this.prepareEvoker(level, evoker);
        caravan.setEvokerId(evoker.getUUID());
        spawned.add(evoker);

        int formationIndex = 1;
        for (int index = 0; index < 5; index++) {
            Pillager pillager = this.createMob(level, EntityType.PILLAGER, offset(center, yaw, formationIndex), yaw, formationIndex, caravanId, spawnLocation.underground());
            if (pillager != null) {
                spawned.add(pillager);
            }
            formationIndex++;
        }
        for (int index = 0; index < 2; index++) {
            Vindicator vindicator = this.createMob(level, EntityType.VINDICATOR, offset(center, yaw, formationIndex), yaw, formationIndex, caravanId, spawnLocation.underground());
            if (vindicator != null) {
                spawned.add(vindicator);
            }
            formationIndex++;
        }
        if (level.random.nextBoolean()) {
            Ravager ravager = this.createMob(level, EntityType.RAVAGER, offset(center, yaw, formationIndex), yaw, formationIndex, caravanId, spawnLocation.underground());
            if (ravager != null) {
                spawned.add(ravager);
            }
        }

        if (spawned.size() < 8) {
            spawned.forEach(Entity::discard);
            return false;
        }

        spawned.forEach(mob -> {
            caravan.memberIds().add(mob.getUUID());
            level.addFreshEntity(mob);
        });
        this.chooseNewPatrolTarget(level, caravan);
        this.caravans.put(caravanId, caravan);
        return true;
    }

    @Nullable
    private <T extends Mob> T createMob(
            ServerLevel level,
            EntityType<T> type,
            BlockPos spawnPos,
            float yaw,
            int formationIndex,
            UUID caravanId,
            boolean underground) {
        BlockPos adjustedSpawnPos = this.adjustSpawnCell(level, spawnPos, underground);
        if (adjustedSpawnPos == null) {
            return null;
        }

        T mob = type.create(level);
        if (mob == null) {
            return null;
        }

        mob.moveTo(adjustedSpawnPos.getX() + 0.5D, adjustedSpawnPos.getY(), adjustedSpawnPos.getZ() + 0.5D, yaw, 0.0F);
        mob.finalizeSpawn((ServerLevelAccessor)level, level.getCurrentDifficultyAt(adjustedSpawnPos), MobSpawnType.EVENT,
                (SpawnGroupData)null);
        mob.setPersistenceRequired();
        mob.addTag(EVENT_MOB_TAG);
        mob.getPersistentData().putUUID(CARAVAN_ID_TAG, caravanId);
        mob.getPersistentData().putInt(FORMATION_INDEX_TAG, formationIndex);
        if (mob instanceof PatrollingMonster patrollingMonster) {
            patrollingMonster.setPatrolLeader(formationIndex == 0);
            patrollingMonster.setPatrolTarget(adjustedSpawnPos.offset(level.random.nextIntBetweenInclusive(-8, 8), 0,
                    level.random.nextIntBetweenInclusive(-8, 8)));
        }
        return mob.checkSpawnObstruction(level) ? mob : null;
    }

    private void prepareEvoker(ServerLevel level, Evoker evoker) {
        evoker.getPersistentData().putBoolean(EVOKER_TAG, true);
        evoker.setItemSlot(
                EquipmentSlot.HEAD,
                Raid.getLeaderBannerInstance(level.registryAccess().lookupOrThrow(Registries.BANNER_PATTERN)));
        evoker.setDropChance(EquipmentSlot.HEAD, 2.0F);
    }

    private void tickCaravans(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        this.pruneCaravans(server);
        Iterator<CaravanState> iterator = this.caravans.values().iterator();
        while (iterator.hasNext()) {
            CaravanState caravan = iterator.next();
            if (caravan.memberIds().isEmpty()) {
                iterator.remove();
                continue;
            }

            if (caravan.cascadeTicksRemaining() > 0) {
                caravan.decrementCascade();
                if (caravan.cascadeTicksRemaining() <= 0) {
                    iterator.remove();
                    this.killRemainingMembers(overworld, caravan);
                    continue;
                }
            }

            Mob evoker = getMob(overworld, caravan.evokerId());
            if (evoker == null || !evoker.isAlive()) {
                if (caravan.cascadeTicksRemaining() <= 0) {
                    caravan.scheduleCascade(null, EVOKER_DEATH_CASCADE_DELAY_TICKS);
                }
                continue;
            }

            this.trackEvokerVexes(overworld, caravan, evoker);
            if (--caravan.retargetTicksRemaining <= 0
                    || caravan.patrolTarget() == null
                    || caravan.patrolTarget().closerToCenterThan(evoker.position(), 8.0D)) {
                this.chooseNewPatrolTarget(overworld, caravan);
            }
            this.driveFormation(overworld, caravan, evoker);
        }
    }

    private void driveFormation(ServerLevel level, CaravanState caravan, Mob evoker) {
        BlockPos patrolTarget = caravan.patrolTarget();
        if (patrolTarget != null && evoker.getTarget() == null) {
            evoker.getNavigation().moveTo(patrolTarget.getX() + 0.5D, patrolTarget.getY(), patrolTarget.getZ() + 0.5D, 0.85D);
            if (evoker instanceof PatrollingMonster patrollingMonster) {
                patrollingMonster.setPatrolTarget(patrolTarget);
            }
        }

        if (level.getGameTime() % FORMATION_TICK_INTERVAL != 0) {
            return;
        }

        float yaw = getFormationYaw(evoker, patrolTarget);
        for (UUID memberId : Set.copyOf(caravan.memberIds())) {
            if (memberId.equals(evoker.getUUID())) {
                continue;
            }

            Mob member = getMob(level, memberId);
            if (member == null || !member.isAlive() || member.getTarget() != null) {
                continue;
            }

            int formationIndex = member.getPersistentData().getInt(FORMATION_INDEX_TAG);
            BlockPos targetPos = offset(evoker.blockPosition(), yaw, formationIndex);
            member.getNavigation().moveTo(targetPos.getX() + 0.5D, evoker.getY(), targetPos.getZ() + 0.5D, 1.0D);
            if (member instanceof PatrollingMonster patrollingMonster) {
                patrollingMonster.setPatrolTarget(targetPos);
            }
        }
    }

    private void trackEvokerVexes(ServerLevel level, CaravanState caravan, Mob evoker) {
        UUID caravanId = getCaravanId(evoker);
        if (caravanId == null) {
            return;
        }

        for (Vex vex : level.getEntitiesOfClass(
                Vex.class,
                evoker.getBoundingBox().inflate(VEX_TRACKING_RADIUS),
                vex -> vex.isAlive() && isOwnedBy(vex, evoker))) {
            vex.addTag(EVENT_MOB_TAG);
            vex.getPersistentData().putUUID(CARAVAN_ID_TAG, caravanId);
            caravan.memberIds().add(vex.getUUID());
        }
    }

    private void killRemainingMembers(ServerLevel level, CaravanState caravan) {
        ServerPlayer killer = caravan.killerId() == null ? null : level.getServer().getPlayerList().getPlayer(caravan.killerId());
        for (UUID memberId : Set.copyOf(caravan.memberIds())) {
            if (memberId.equals(caravan.evokerId())) {
                continue;
            }

            Mob member = getMob(level, memberId);
            if (member == null || !member.isAlive()) {
                continue;
            }

            if (killer != null) {
                member.setLastHurtByPlayer(killer);
                member.hurt(killer.damageSources().playerAttack(killer), CASCADE_DAMAGE);
            } else {
                member.hurt(member.damageSources().magic(), CASCADE_DAMAGE);
            }
        }
    }

    private void pruneCaravans(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            this.caravans.clear();
            return;
        }

        Iterator<CaravanState> iterator = this.caravans.values().iterator();
        while (iterator.hasNext()) {
            CaravanState caravan = iterator.next();
            caravan.memberIds().removeIf(memberId -> {
                Mob mob = getMob(overworld, memberId);
                return mob == null || !mob.isAlive();
            });
            if (caravan.memberIds().isEmpty()) {
                iterator.remove();
            }
        }
    }

    private void chooseNewPatrolTarget(ServerLevel level, CaravanState caravan) {
        ServerEventArea eventArea = getEventArea(level);
        BlockPos patrolTarget = caravan.underground()
                ? this.findCavePatrolTarget(level, eventArea, caravan.patrolAnchor())
                : this.findSurfacePatrolTarget(level, eventArea);
        if (patrolTarget == null) {
            patrolTarget = caravan.patrolAnchor();
        }

        caravan.setPatrolTarget(patrolTarget);
    }

    @Nullable
    private SpawnLocation findRandomSpawnLocation(ServerLevel level) {
        ServerEventArea eventArea = getEventArea(level);
        boolean hasCavePlayers = this.hasCavePlayer(level, eventArea);
        if (hasCavePlayers && level.random.nextBoolean()) {
            BlockPos caveSpawn = this.findCaveSpawnNearPlayer(level, eventArea);
            if (caveSpawn != null) {
                return new SpawnLocation(caveSpawn, true);
            }
        }

        BlockPos surfaceSpawn = this.findSurfaceSpawnPos(level, eventArea);
        if (surfaceSpawn != null) {
            return new SpawnLocation(surfaceSpawn, false);
        }

        if (hasCavePlayers) {
            BlockPos caveSpawn = this.findCaveSpawnNearPlayer(level, eventArea);
            if (caveSpawn != null) {
                return new SpawnLocation(caveSpawn, true);
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findSurfaceSpawnPos(ServerLevel level, ServerEventArea eventArea) {
        for (int attempt = 0; attempt < SPAWN_SEARCH_ATTEMPTS; attempt++) {
            int sampleX = eventArea.randomX(level.random);
            int sampleZ = eventArea.randomZ(level.random);
            int topY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sampleX, sampleZ);
            int minY = level.getMinBuildHeight() + 1;
            if (topY < minY) {
                continue;
            }

            int baseY = attempt % 3 == 0 ? topY : level.random.nextIntBetweenInclusive(minY, topY);
            for (int yOffset : SPAWN_Y_OFFSETS) {
                BlockPos candidate = new BlockPos(sampleX, baseY + yOffset, sampleZ);
                if (this.isValidSpawnCell(level, candidate)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findSurfacePatrolTarget(ServerLevel level, ServerEventArea eventArea) {
        int x = eventArea.randomX(level.random);
        int z = eventArea.randomZ(level.random);
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    private boolean hasCavePlayer(ServerLevel level, ServerEventArea eventArea) {
        for (ServerPlayer player : level.players()) {
            if (this.isCavePlayer(level, eventArea, player)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCavePlayer(ServerLevel level, ServerEventArea eventArea, ServerPlayer player) {
        if (player.isSpectator() || !player.isAlive() || player.level() != level || !eventArea.contains(player.getX(), player.getZ())) {
            return false;
        }

        int surfaceY = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Mth.floor(player.getX()),
                Mth.floor(player.getZ()));
        return player.getY() <= surfaceY - CAVE_PLAYER_SURFACE_DEPTH;
    }

    @Nullable
    private BlockPos findCaveSpawnNearPlayer(ServerLevel level, ServerEventArea eventArea) {
        List<ServerPlayer> cavePlayers = level.players().stream()
                .filter(player -> this.isCavePlayer(level, eventArea, player))
                .toList();
        if (cavePlayers.isEmpty()) {
            return null;
        }

        for (int attempt = 0; attempt < CAVE_SPAWN_SEARCH_ATTEMPTS; attempt++) {
            ServerPlayer player = cavePlayers.get(level.random.nextInt(cavePlayers.size()));
            double angle = level.random.nextDouble() * Math.PI * 2.0D;
            int distance = level.random.nextIntBetweenInclusive(CAVE_CARAVAN_MIN_DISTANCE, CAVE_CARAVAN_MAX_DISTANCE);
            int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
            int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
            int baseY = player.blockPosition().getY() + level.random.nextIntBetweenInclusive(-8, 6);
            BlockPos candidate = this.findCaveSpawnCellNear(level, eventArea, new BlockPos(x, baseY, z), 8);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findCavePatrolTarget(ServerLevel level, ServerEventArea eventArea, BlockPos anchor) {
        for (int attempt = 0; attempt < CAVE_PATROL_SEARCH_ATTEMPTS; attempt++) {
            int x = anchor.getX() + level.random.nextIntBetweenInclusive(-CAVE_PATROL_RADIUS, CAVE_PATROL_RADIUS);
            int z = anchor.getZ() + level.random.nextIntBetweenInclusive(-CAVE_PATROL_RADIUS, CAVE_PATROL_RADIUS);
            int y = anchor.getY() + level.random.nextIntBetweenInclusive(-6, 4);
            BlockPos candidate = this.findCaveSpawnCellNear(level, eventArea, new BlockPos(x, y, z), 6);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findCaveSpawnCellNear(ServerLevel level, ServerEventArea eventArea, BlockPos origin, int verticalRange) {
        for (int offset = 0; offset <= verticalRange; offset++) {
            int up = origin.getY() + offset;
            BlockPos upCandidate = new BlockPos(origin.getX(), up, origin.getZ());
            if (this.isValidCaveSpawnCell(level, eventArea, upCandidate)) {
                return upCandidate;
            }

            if (offset > 0) {
                int down = origin.getY() - offset;
                BlockPos downCandidate = new BlockPos(origin.getX(), down, origin.getZ());
                if (this.isValidCaveSpawnCell(level, eventArea, downCandidate)) {
                    return downCandidate;
                }
            }
        }

        return null;
    }

    @Nullable
    private BlockPos adjustSpawnCell(ServerLevel level, BlockPos spawnPos, boolean underground) {
        for (int yOffset : SPAWN_Y_OFFSETS) {
            BlockPos candidate = spawnPos.offset(0, yOffset, 0);
            if (underground ? this.isValidCaveSpawnCell(level, getEventArea(level), candidate) : this.isValidSpawnCell(level, candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private boolean isValidSpawnCell(ServerLevel level, BlockPos spawnPos) {
        BlockPos supportPos = spawnPos.below();
        if (!level.getWorldBorder().isWithinBounds(spawnPos)
                || !level.getWorldBorder().isWithinBounds(supportPos)) {
            return false;
        }

        BlockState supportState = level.getBlockState(supportPos);
        FluidState supportFluid = supportState.getFluidState();
        if (!supportState.isFaceSturdy(level, supportPos, Direction.UP) || !supportFluid.isEmpty()) {
            return false;
        }

        BlockState feetState = level.getBlockState(spawnPos);
        BlockState headState = level.getBlockState(spawnPos.above());
        BlockState topState = level.getBlockState(spawnPos.above(2));
        return feetState.canBeReplaced()
                && headState.canBeReplaced()
                && topState.canBeReplaced()
                && feetState.getFluidState().isEmpty()
                && headState.getFluidState().isEmpty()
                && topState.getFluidState().isEmpty();
    }

    private boolean isValidCaveSpawnCell(ServerLevel level, ServerEventArea eventArea, BlockPos spawnPos) {
        if (!eventArea.contains(spawnPos.getX(), spawnPos.getZ()) || !this.isValidSpawnCell(level, spawnPos)) {
            return false;
        }

        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawnPos.getX(), spawnPos.getZ());
        return spawnPos.getY() <= surfaceY - CAVE_PLAYER_SURFACE_DEPTH && !level.canSeeSky(spawnPos);
    }

    private CustomBossEvent createBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent existingBossBar = bossEvents.get(BOSSBAR_ID);
        if (existingBossBar != null) {
            existingBossBar.removeAllPlayers();
            bossEvents.remove(existingBossBar);
        }

        CustomBossEvent caravanBossBar = bossEvents.create(BOSSBAR_ID, this.getDisplayName());
        caravanBossBar.setColor(BossEvent.BossBarColor.PURPLE);
        caravanBossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        caravanBossBar.setMax(EVENT_DURATION_TICKS);
        caravanBossBar.setValue(EVENT_DURATION_TICKS);
        caravanBossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(caravanBossBar::addPlayer);
        return caravanBossBar;
    }

    private void clearBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent caravanBossBar = this.bossBar != null ? this.bossBar : bossEvents.get(BOSSBAR_ID);
        if (caravanBossBar != null) {
            caravanBossBar.removeAllPlayers();
            bossEvents.remove(caravanBossBar);
        }
        this.bossBar = null;
    }

    private void updateBossBar(MinecraftServer server) {
        if (this.bossBar == null) {
            return;
        }

        this.bossBar.setMax(EVENT_DURATION_TICKS);
        this.bossBar.setValue(this.timeRemainingTicks);
        this.bossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(this.bossBar::addPlayer);
    }

    private void removeEventCaravans(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        for (Mob mob : overworld.getEntitiesOfClass(
                Mob.class,
                getLoadedWorldBounds(overworld),
                entity -> entity.getTags().contains(EVENT_MOB_TAG))) {
            mob.discard();
        }
    }

    @Nullable
    private static Mob getMob(ServerLevel level, UUID entityId) {
        Entity entity = level.getEntity(entityId);
        return entity instanceof Mob mob ? mob : null;
    }

    @Nullable
    private static UUID getCaravanId(Mob mob) {
        return mob.getPersistentData().hasUUID(CARAVAN_ID_TAG)
                ? mob.getPersistentData().getUUID(CARAVAN_ID_TAG)
                : null;
    }

    @Nullable
    private static UUID getPlayerKiller(LivingDeathEvent event) {
        Entity attacker = event.getSource().getEntity();
        return attacker instanceof ServerPlayer player ? player.getUUID() : null;
    }

    private static boolean isOwnedBy(Vex vex, Mob evoker) {
        Mob owner = vex.getOwner();
        return owner != null && owner.getUUID().equals(evoker.getUUID());
    }

    private static BlockPos offset(BlockPos center, float yaw, int formationIndex) {
        Vec3 offset = formationOffset(formationIndex);
        double radians = Math.toRadians(yaw);
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        int x = Mth.floor(offset.x * cos - offset.z * sin);
        int z = Mth.floor(offset.x * sin + offset.z * cos);
        return center.offset(x, 0, z);
    }

    private static Vec3 formationOffset(int formationIndex) {
        return switch (formationIndex) {
            case 1 -> new Vec3(-2.0D, 0.0D, -2.0D);
            case 2 -> new Vec3(2.0D, 0.0D, -2.0D);
            case 3 -> new Vec3(-3.0D, 0.0D, 1.0D);
            case 4 -> new Vec3(3.0D, 0.0D, 1.0D);
            case 5 -> new Vec3(0.0D, 0.0D, 3.0D);
            case 6 -> new Vec3(-1.5D, 0.0D, -3.5D);
            case 7 -> new Vec3(1.5D, 0.0D, -3.5D);
            case 8 -> new Vec3(0.0D, 0.0D, -5.0D);
            default -> Vec3.ZERO;
        };
    }

    private static float getFormationYaw(Mob evoker, @Nullable BlockPos patrolTarget) {
        if (patrolTarget == null) {
            return evoker.getYRot();
        }

        double dx = patrolTarget.getX() + 0.5D - evoker.getX();
        double dz = patrolTarget.getZ() + 0.5D - evoker.getZ();
        return (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
    }

    private static ServerEventArea getEventArea(ServerLevel level) {
        return ServerEventAreas.getArea(level.getServer(), ServerEventGroup.MAP_ROTATION);
    }

    private static AABB getLoadedWorldBounds(ServerLevel level) {
        return new AABB(
                level.getWorldBorder().getMinX(),
                level.getMinBuildHeight(),
                level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(),
                level.getMaxBuildHeight(),
                level.getWorldBorder().getMaxZ());
    }

    private record SpawnLocation(BlockPos pos, boolean underground) {
    }

    private static final class CaravanState {
        private final Set<UUID> memberIds = new HashSet<>();
        private final boolean underground;
        private BlockPos patrolAnchor;
        private UUID evokerId;
        @Nullable
        private BlockPos patrolTarget;
        private int retargetTicksRemaining;
        private int cascadeTicksRemaining;
        @Nullable
        private UUID killerId;

        private CaravanState(boolean underground, BlockPos patrolAnchor) {
            this.underground = underground;
            this.patrolAnchor = patrolAnchor;
        }

        private Set<UUID> memberIds() {
            return this.memberIds;
        }

        private UUID evokerId() {
            return this.evokerId;
        }

        private void setEvokerId(UUID evokerId) {
            this.evokerId = evokerId;
        }

        private boolean underground() {
            return this.underground;
        }

        private BlockPos patrolAnchor() {
            return this.patrolAnchor;
        }

        @Nullable
        private BlockPos patrolTarget() {
            return this.patrolTarget;
        }

        private void setPatrolTarget(BlockPos patrolTarget) {
            this.patrolTarget = patrolTarget;
            this.patrolAnchor = patrolTarget;
            this.retargetTicksRemaining = PATROL_RETARGET_TICKS;
        }

        private int cascadeTicksRemaining() {
            return this.cascadeTicksRemaining;
        }

        @Nullable
        private UUID killerId() {
            return this.killerId;
        }

        private void scheduleCascade(@Nullable UUID killerId, int ticks) {
            if (this.cascadeTicksRemaining > 0) {
                return;
            }

            this.killerId = killerId;
            this.cascadeTicksRemaining = ticks;
        }

        private void decrementCascade() {
            this.cascadeTicksRemaining = Math.max(0, this.cascadeTicksRemaining - 1);
        }

    }
}
