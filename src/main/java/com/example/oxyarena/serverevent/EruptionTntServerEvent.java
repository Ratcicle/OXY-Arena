package com.example.oxyarena.serverevent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.entity.event.EruptionTntEntity;
import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.bossevents.CustomBossEvent;
import net.minecraft.server.bossevents.CustomBossEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class EruptionTntServerEvent implements OxyServerEvent {
    private static final int EVENT_DURATION_TICKS = 20 * 60 * 5;
    private static final int MIN_SPAWN_COOLDOWN_TICKS = 18;
    private static final int MAX_SPAWN_COOLDOWN_TICKS = 40;
    private static final int MIN_FUSE_TICKS = 28;
    private static final int MAX_FUSE_TICKS = 56;
    private static final double NEAR_MIN_SPAWN_DISTANCE = 4.0D;
    private static final double NEAR_MAX_SPAWN_DISTANCE = 10.0D;
    private static final double FAR_MIN_SPAWN_DISTANCE = 32.0D;
    private static final double FAR_MAX_SPAWN_DISTANCE = 64.0D;
    private static final double MIN_PLAYER_SAFETY_DISTANCE = 2.0D;
    private static final double MIN_TNT_SPACING = 6.0D;
    private static final double LOCAL_TNT_CAP_RADIUS = 12.0D;
    private static final int LOCAL_TNT_CAP = 2;
    private static final double FAR_TNT_MIN_RING = 24.0D;
    private static final double FAR_TNT_MAX_RING = 72.0D;
    private static final int FAR_TNT_CAP = 2;
    private static final float FAR_SPAWN_CHANCE = 0.55F;
    private static final int HARD_GLOBAL_TNT_CAP = 18;
    private static final int SPAWN_SEARCH_ATTEMPTS = 24;
    private static final double TNT_EMBED_OFFSET = 0.1D;
    private static final double MIN_VERTICAL_VELOCITY = 0.28D;
    private static final double MAX_VERTICAL_VELOCITY = 0.87D;
    private static final double MIN_HORIZONTAL_VELOCITY = 0.08D;
    private static final double MAX_HORIZONTAL_VELOCITY = 0.18D;
    private static final int[] SPAWN_Y_OFFSETS = {0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 8, -8, 10, -10};
    private static final ResourceLocation BOSSBAR_ID = ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "tnt");
    private static final String TNT_SURVIVOR_TAG = OXYArena.MODID + ".sobrevivente_erupcao_tnt";
    private static final int PERMANENT_EFFECT_REFRESH_TICKS = 20 * 15;

    @Nullable
    private CustomBossEvent bossBar;
    private boolean active;
    private boolean rewardsGranted;
    private int timeRemainingTicks;
    private final Map<UUID, Integer> spawnCooldowns = new HashMap<>();
    private final Set<UUID> tntVictims = new HashSet<>();

    @Override
    public String getId() {
        return "tnt";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.tnt");
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public boolean start(MinecraftServer server) {
        if (this.active) {
            return false;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return false;
        }

        List<ServerPlayer> eligiblePlayers = this.getEligiblePlayers(server);
        if (eligiblePlayers.isEmpty()) {
            return false;
        }

        this.cleanupStaleRuntimeArtifacts(server);
        this.active = true;
        this.rewardsGranted = false;
        this.timeRemainingTicks = EVENT_DURATION_TICKS;
        this.bossBar = this.createBossBar(server);
        this.updateBossBar(server);
        for (ServerPlayer player : eligiblePlayers) {
            this.spawnCooldowns.put(player.getUUID(), this.nextSpawnCooldown(overworld));
        }

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.tnt.started")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                false);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        if (!this.active && reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        this.active = false;
        if (reason == ServerEventStopReason.COMPLETED && !this.rewardsGranted) {
            this.rewardsGranted = true;
            this.rewardPlayers(server);
        }

        this.rewardsGranted = true;
        this.timeRemainingTicks = 0;
        this.spawnCooldowns.clear();
        this.tntVictims.clear();
        discardAllEruptionTnts(server);
        this.clearBossBar(server);
        if (reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("event.oxyarena.tnt.finished")
                            .withStyle(ChatFormatting.GRAY),
                    false);
        }
    }

    @Override
    public void tick(MinecraftServer server) {
        if (!this.active) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            this.stop(server, ServerEventStopReason.COMPLETED);
            return;
        }

        this.timeRemainingTicks--;
        if (this.timeRemainingTicks <= 0) {
            this.stop(server, ServerEventStopReason.COMPLETED);
            return;
        }

        this.tickSpawnScheduler(server, overworld);
        if (server.getTickCount() % 20 == 0) {
            this.updateBossBar(server);
        }
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getSource().getDirectEntity() instanceof EruptionTntEntity) {
            this.tntVictims.add(player.getUUID());
            player.sendSystemMessage(
                    Component.translatable("event.oxyarena.tnt.reward_fail")
                            .withStyle(ChatFormatting.RED));
        }
    }

    @Override
    public void onPlayerLoggedIn(MinecraftServer server, ServerPlayer player) {
        if (this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public void onPlayerChangedDimension(MinecraftServer server, ServerPlayer player) {
        if (this.bossBar != null) {
            this.bossBar.addPlayer(player);
        }
    }

    @Override
    public int getTimeRemainingTicks() {
        return this.timeRemainingTicks;
    }

    @Override
    public void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
        this.active = false;
        this.rewardsGranted = false;
        this.timeRemainingTicks = 0;
        this.spawnCooldowns.clear();
        this.tntVictims.clear();
        discardAllEruptionTnts(server);
        this.clearBossBar(server);
    }

    public static void tickPersistentPlayerEffects(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            refreshPersistentPlayerState(server, player);
        }
    }

    public static void refreshPersistentPlayerState(MinecraftServer server, ServerPlayer player) {
        if (player.getTags().contains(TNT_SURVIVOR_TAG)) {
            refreshSurvivorResistance(player);
        }
    }

    public static int discardAllEruptionTnts(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return 0;
        }

        List<EruptionTntEntity> tnts = getActiveTnts(overworld);
        tnts.forEach(EruptionTntEntity::discard);
        return tnts.size();
    }

    private void tickSpawnScheduler(MinecraftServer server, ServerLevel level) {
        List<ServerPlayer> eligiblePlayers = this.getEligiblePlayers(server);
        this.spawnCooldowns.keySet().removeIf(playerId -> eligiblePlayers.stream().noneMatch(player -> player.getUUID().equals(playerId)));
        ServerEventArea eventArea = this.getEventArea(server);
        int globalCap = this.getGlobalActiveTntCap(eligiblePlayers.size());
        for (ServerPlayer player : eligiblePlayers) {
            int cooldown = this.spawnCooldowns.getOrDefault(player.getUUID(), this.nextSpawnCooldown(level)) - 1;
            if (cooldown > 0) {
                this.spawnCooldowns.put(player.getUUID(), cooldown);
                continue;
            }

            this.spawnCooldowns.put(player.getUUID(), this.nextSpawnCooldown(level));
            this.trySpawnTntNearPlayer(level, player, eventArea, globalCap);
            this.trySpawnTntFarFromPlayer(level, player, eventArea, globalCap);
        }
    }

    private void trySpawnTntNearPlayer(ServerLevel level, ServerPlayer player, ServerEventArea eventArea, int globalCap) {
        if (getActiveTnts(level).size() >= globalCap || this.getLocalActiveTntCount(level, player.position(), LOCAL_TNT_CAP_RADIUS) >= LOCAL_TNT_CAP) {
            return;
        }

        BlockPos spawnCell = this.findSpawnCell(level, player, eventArea, NEAR_MIN_SPAWN_DISTANCE, NEAR_MAX_SPAWN_DISTANCE);
        if (spawnCell == null) {
            return;
        }

        if (!this.spawnEruptionTnt(level, player, spawnCell)) {
            return;
        }

    }

    private void trySpawnTntFarFromPlayer(ServerLevel level, ServerPlayer player, ServerEventArea eventArea, int globalCap) {
        if (level.random.nextFloat() > FAR_SPAWN_CHANCE
                || getActiveTnts(level).size() >= globalCap
                || this.getActiveTntCountInBand(level, player.position(), FAR_TNT_MIN_RING, FAR_TNT_MAX_RING) >= FAR_TNT_CAP) {
            return;
        }

        BlockPos spawnCell = this.findSpawnCell(level, player, eventArea, FAR_MIN_SPAWN_DISTANCE, FAR_MAX_SPAWN_DISTANCE);
        if (spawnCell == null) {
            return;
        }

        this.spawnEruptionTnt(level, player, spawnCell);
    }

    private boolean spawnEruptionTnt(ServerLevel level, ServerPlayer player, BlockPos spawnCell) {
        EruptionTntEntity tnt = new EruptionTntEntity(
                level,
                spawnCell.getX() + 0.5D,
                spawnCell.getY() - TNT_EMBED_OFFSET,
                spawnCell.getZ() + 0.5D,
                player);
        tnt.initializeFuseAndLaunch(
                level.random.nextIntBetweenInclusive(MIN_FUSE_TICKS, MAX_FUSE_TICKS),
                randomLaunchVelocity(level));

        if (!level.addFreshEntity(tnt)) {
            return false;
        }

        level.playSound(
                null,
                tnt.getX(),
                tnt.getY(),
                tnt.getZ(),
                SoundEvents.TNT_PRIMED,
                SoundSource.BLOCKS,
                1.0F,
                0.9F + level.random.nextFloat() * 0.2F);
        return true;
    }

    @Nullable
    private BlockPos findSpawnCell(ServerLevel level, ServerPlayer player, ServerEventArea eventArea, double minDistance, double maxDistance) {
        for (int attempt = 0; attempt < SPAWN_SEARCH_ATTEMPTS; attempt++) {
            double angle = level.random.nextDouble() * Mth.TWO_PI;
            double distance = Mth.lerp(level.random.nextDouble(), minDistance, maxDistance);
            double sampleX = player.getX() + Math.cos(angle) * distance;
            double sampleZ = player.getZ() + Math.sin(angle) * distance;
            if (!eventArea.contains(sampleX, sampleZ)) {
                continue;
            }

            int baseY = Mth.floor(player.getY());
            for (int yOffset : SPAWN_Y_OFFSETS) {
                BlockPos spawnCell = BlockPos.containing(sampleX, baseY + yOffset, sampleZ);
                if (!this.isValidSpawnCell(level, player, spawnCell)) {
                    continue;
                }

                return spawnCell;
            }
        }

        return null;
    }

    private boolean isValidSpawnCell(ServerLevel level, ServerPlayer player, BlockPos spawnCell) {
        BlockPos supportPos = spawnCell.below();
        if (!level.getWorldBorder().isWithinBounds(spawnCell)
                || !level.getWorldBorder().isWithinBounds(supportPos)
                || spawnCell.distSqr(player.blockPosition()) < MIN_PLAYER_SAFETY_DISTANCE * MIN_PLAYER_SAFETY_DISTANCE) {
            return false;
        }

        BlockState supportState = level.getBlockState(supportPos);
        if (!supportState.isFaceSturdy(level, supportPos, net.minecraft.core.Direction.UP)
                || !supportState.getFluidState().isEmpty()) {
            return false;
        }

        BlockState feetState = level.getBlockState(spawnCell);
        BlockState headState = level.getBlockState(spawnCell.above());
        if (!feetState.canBeReplaced() || !headState.canBeReplaced()) {
            return false;
        }

        if (!feetState.getFluidState().isEmpty() || !headState.getFluidState().isEmpty()) {
            return false;
        }

        Vec3 center = Vec3.atBottomCenterOf(spawnCell);
        return this.getLocalActiveTntCount(level, center, MIN_TNT_SPACING) == 0;
    }

    private int getLocalActiveTntCount(ServerLevel level, Vec3 center, double radius) {
        return level.getEntitiesOfClass(
                EruptionTntEntity.class,
                new AABB(center, center).inflate(radius),
                entity -> !entity.isRemoved()).size();
    }

    private int getActiveTntCountInBand(ServerLevel level, Vec3 center, double minRadius, double maxRadius) {
        double minRadiusSqr = minRadius * minRadius;
        double maxRadiusSqr = maxRadius * maxRadius;
        return level.getEntitiesOfClass(
                EruptionTntEntity.class,
                new AABB(center, center).inflate(maxRadius),
                entity -> {
                    if (entity.isRemoved()) {
                        return false;
                    }

                    double distanceSqr = entity.position().distanceToSqr(center);
                    return distanceSqr >= minRadiusSqr && distanceSqr <= maxRadiusSqr;
                }).size();
    }

    private int getGlobalActiveTntCap(int eligiblePlayers) {
        return Math.min(HARD_GLOBAL_TNT_CAP, Math.max(4, eligiblePlayers * 3));
    }

    private int nextSpawnCooldown(ServerLevel level) {
        return level.random.nextIntBetweenInclusive(MIN_SPAWN_COOLDOWN_TICKS, MAX_SPAWN_COOLDOWN_TICKS);
    }

    private static Vec3 randomLaunchVelocity(ServerLevel level) {
        double diagonalFactor = Mth.SQRT_OF_TWO / 2.0D;
        double xDirection = level.random.nextBoolean() ? 1.0D : -1.0D;
        double zDirection = level.random.nextBoolean() ? 1.0D : -1.0D;
        double horizontalSpeed = Mth.lerp(level.random.nextDouble(), MIN_HORIZONTAL_VELOCITY, MAX_HORIZONTAL_VELOCITY);
        double verticalSpeed = Mth.lerp(level.random.nextDouble(), MIN_VERTICAL_VELOCITY, MAX_VERTICAL_VELOCITY);
        return new Vec3(
                xDirection * diagonalFactor * horizontalSpeed,
                verticalSpeed,
                zDirection * diagonalFactor * horizontalSpeed);
    }

    private List<ServerPlayer> getEligiblePlayers(MinecraftServer server) {
        ServerEventArea eventArea = this.getEventArea(server);
        return server.getPlayerList().getPlayers().stream()
                .filter(player -> !player.isCreative())
                .filter(player -> !player.isSpectator())
                .filter(player -> player.serverLevel().dimension() == Level.OVERWORLD)
                .filter(player -> eventArea.contains(player.getX(), player.getZ()))
                .toList();
    }

    private ServerEventArea getEventArea(MinecraftServer server) {
        return ServerEventAreas.getArea(server, ServerEventGroup.MAP_ROTATION);
    }

    private void rewardPlayers(MinecraftServer server) {
        ServerEventArea eventArea = this.getEventArea(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.serverLevel().dimension() != Level.OVERWORLD
                    || !eventArea.contains(player.getX(), player.getZ())
                    || this.tntVictims.contains(player.getUUID())) {
                continue;
            }

            this.giveOrDrop(player, new ItemStack(ModItems.COBALT_SHIELD.get()));
            player.addTag(TNT_SURVIVOR_TAG);
            refreshSurvivorResistance(player);
            player.sendSystemMessage(
                    Component.translatable("event.oxyarena.tnt.reward_success")
                            .withStyle(ChatFormatting.GOLD));
        }
    }

    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }

    private static void refreshSurvivorResistance(ServerPlayer player) {
        MobEffectInstance resistanceEffect = player.getEffect(MobEffects.DAMAGE_RESISTANCE);
        if (resistanceEffect == null
                || resistanceEffect.getAmplifier() < 0
                || resistanceEffect.getDuration() <= 20) {
            player.addEffect(new MobEffectInstance(
                    MobEffects.DAMAGE_RESISTANCE,
                    PERMANENT_EFFECT_REFRESH_TICKS,
                    0,
                    false,
                    false,
                    false));
        }
    }

    private CustomBossEvent createBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent existingBossBar = bossEvents.get(BOSSBAR_ID);
        if (existingBossBar != null) {
            existingBossBar.removeAllPlayers();
            bossEvents.remove(existingBossBar);
        }

        CustomBossEvent tntBossBar = bossEvents.create(
                BOSSBAR_ID,
                Component.translatable("event.oxyarena.tnt.bossbar"));
        tntBossBar.setColor(BossEvent.BossBarColor.RED);
        tntBossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        tntBossBar.setMax(EVENT_DURATION_TICKS);
        tntBossBar.setValue(EVENT_DURATION_TICKS);
        tntBossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(tntBossBar::addPlayer);
        return tntBossBar;
    }

    private void clearBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent tntBossBar = this.bossBar != null ? this.bossBar : bossEvents.get(BOSSBAR_ID);
        if (tntBossBar != null) {
            tntBossBar.removeAllPlayers();
            bossEvents.remove(tntBossBar);
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

    private static List<EruptionTntEntity> getActiveTnts(ServerLevel level) {
        return level.getEntitiesOfClass(EruptionTntEntity.class, getLoadedWorldBounds(level), entity -> !entity.isRemoved());
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
}
