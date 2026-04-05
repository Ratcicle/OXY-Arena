package com.example.oxyarena.serverevent;

import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;
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
import net.minecraft.tags.BlockTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class MercadorServerEvent implements OxyServerEvent {
    private static final int EVENT_DURATION_TICKS = 20 * 60 * 5;
    private static final int MAX_GROUND_SCAN_ATTEMPTS = 24;
    private static final ResourceLocation BOSSBAR_ID = ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "mercador");
    private static final String EVENT_VILLAGER_TAG = OXYArena.MODID + ".mercador_evento";

    @Nullable
    private CustomBossEvent bossBar;
    private boolean active;
    private int timeRemainingTicks;
    @Nullable
    private UUID villagerUuid;
    @Nullable
    private Integer forcedChunkX;
    @Nullable
    private Integer forcedChunkZ;

    @Override
    public String getId() {
        return "mercador";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.mercador");
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

        this.cleanupStaleRuntimeArtifacts(server);
        BlockPos spawnPos = this.findSpawnPos(overworld);
        if (spawnPos == null) {
            return false;
        }

        Villager jorginho = EntityType.VILLAGER.create(overworld);
        if (jorginho == null) {
            return false;
        }

        this.configureVillager(jorginho, spawnPos);
        if (!overworld.addFreshEntity(jorginho)) {
            return false;
        }

        this.active = true;
        this.timeRemainingTicks = EVENT_DURATION_TICKS;
        this.villagerUuid = jorginho.getUUID();
        this.setForcedChunk(overworld, spawnPos);
        this.bossBar = this.createBossBar(server);
        this.updateBossBar(server);
        this.broadcastStart(server, spawnPos);
        return true;
    }

    @Override
    public void stop(MinecraftServer server, ServerEventStopReason reason) {
        if (!this.active && reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            return;
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld != null) {
            Villager villager = this.getVillager(overworld);
            if (villager != null) {
                villager.discard();
            }

            this.releaseForcedChunk(overworld);
        }

        this.active = false;
        this.timeRemainingTicks = 0;
        this.villagerUuid = null;
        this.clearBossBar(server);

        if (reason != ServerEventStopReason.SERVER_SHUTDOWN) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("event.oxyarena.mercador.finished")
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
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
            this.stop(server, ServerEventStopReason.MANUAL);
            return;
        }

        this.timeRemainingTicks--;
        if (this.timeRemainingTicks <= 0) {
            this.stop(server, ServerEventStopReason.COMPLETED);
            return;
        }

        if (this.getVillager(overworld) == null) {
            this.stop(server, ServerEventStopReason.MANUAL);
            return;
        }

        if (server.getTickCount() % 20 == 0) {
            this.updateBossBar(server);
        }
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
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
        this.timeRemainingTicks = 0;
        this.villagerUuid = null;

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            this.forcedChunkX = null;
            this.forcedChunkZ = null;
            this.clearBossBar(server);
            return;
        }

        for (Villager villager : overworld.getEntitiesOfClass(
                Villager.class,
                this.getLoadedWorldBounds(overworld),
                entity -> entity.getTags().contains(EVENT_VILLAGER_TAG))) {
            villager.discard();
        }

        this.releaseForcedChunk(overworld);
        this.clearBossBar(server);
    }

    @Nullable
    private BlockPos findSpawnPos(ServerLevel level) {
        ServerEventArea eventArea = ServerEventAreas.getArea(level.getServer(), ServerEventGroup.MAP_ROTATION);
        int startY = level.getMaxBuildHeight() - 1;

        for (int attempt = 0; attempt < MAX_GROUND_SCAN_ATTEMPTS; attempt++) {
            int x = eventArea.randomX(level.random);
            int z = eventArea.randomZ(level.random);

            for (int y = startY; y > level.getMinBuildHeight(); y--) {
                BlockPos supportPos = new BlockPos(x, y, z);
                BlockState supportState = level.getBlockState(supportPos);
                if (!this.isValidSupport(supportState)) {
                    continue;
                }

                BlockPos spawnPos = supportPos.above();
                if (level.getBlockState(spawnPos).canBeReplaced() && level.getBlockState(spawnPos.above()).canBeReplaced()) {
                    return spawnPos.immutable();
                }

                break;
            }
        }

        return null;
    }

    private boolean isValidSupport(BlockState state) {
        if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) {
            return false;
        }

        FluidState fluidState = state.getFluidState();
        return fluidState.isEmpty();
    }

    private void configureVillager(Villager villager, BlockPos spawnPos) {
        villager.moveTo(
                spawnPos.getX() + 0.5D,
                spawnPos.getY(),
                spawnPos.getZ() + 0.5D,
                villager.level().random.nextFloat() * 360.0F,
                0.0F);
        villager.setNoAi(true);
        villager.setInvulnerable(true);
        villager.setCanPickUpLoot(false);
        villager.setPersistenceRequired();
        villager.addTag(EVENT_VILLAGER_TAG);
        villager.setCustomName(
                Component.translatable("event.oxyarena.mercador.name")
                        .copy()
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        villager.setCustomNameVisible(true);
        villager.setVillagerData(
                villager.getVillagerData()
                        .setType(VillagerType.PLAINS)
                        .setProfession(VillagerProfession.ARMORER)
                        .setLevel(5));
        villager.setVillagerXp(0);
        villager.setOffers(this.createOffers());
    }

    private MerchantOffers createOffers() {
        MerchantOffers offers = new MerchantOffers();
        offers.add(new MerchantOffer(new ItemCost(Items.COAL, 5), new ItemStack(Items.IRON_INGOT), 99999, 0, 0.0F));
        offers.add(new MerchantOffer(new ItemCost(Items.IRON_INGOT, 5), new ItemStack(Items.GOLD_INGOT, 2), 99999, 0, 0.0F));
        offers.add(new MerchantOffer(new ItemCost(Items.GOLD_INGOT, 5), new ItemStack(Items.DIAMOND), 99999, 0, 0.0F));
        offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, 1), new ItemStack(Items.DIAMOND), 99999, 0, 0.0F));
        offers.add(new MerchantOffer(
                new ItemCost(ModItems.CITRINE_GEM.get(), 10),
                new ItemStack(ModItems.COBALT_INGOT.get()),
                99999,
                0,
                0.0F));
        offers.add(new MerchantOffer(
                new ItemCost(ModItems.COBALT_INGOT.get(), 5),
                new ItemStack(ModItems.GRAPPLING_GUN.get()),
                99999,
                0,
                0.0F));
        offers.add(new MerchantOffer(
                new ItemCost(Items.DIAMOND, 3),
                new ItemStack(ModItems.GRAPPLING_GUN.get()),
                99999,
                0,
                0.0F));
        offers.add(new MerchantOffer(
                new ItemCost(Items.DIAMOND, 5),
                Optional.of(new ItemCost(Items.GOLD_INGOT, 5)),
                new ItemStack(Items.NETHERITE_INGOT),
                99999,
                0,
                0.0F));
        return offers;
    }

    private void broadcastStart(MinecraftServer server, BlockPos spawnPos) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.mercador.started",
                        spawnPos.getX(),
                        spawnPos.getY(),
                        spawnPos.getZ())
                        .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                false);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.mercador.warning")
                        .withStyle(ChatFormatting.GRAY),
                false);

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
        }
    }

    @Nullable
    private Villager getVillager(ServerLevel level) {
        if (this.villagerUuid == null) {
            return null;
        }

        Entity entity = level.getEntity(this.villagerUuid);
        return entity instanceof Villager villager ? villager : null;
    }

    private void setForcedChunk(ServerLevel level, BlockPos pos) {
        this.releaseForcedChunk(level);
        this.forcedChunkX = pos.getX() >> 4;
        this.forcedChunkZ = pos.getZ() >> 4;
        level.setChunkForced(this.forcedChunkX, this.forcedChunkZ, true);
    }

    private void releaseForcedChunk(ServerLevel level) {
        if (this.forcedChunkX != null && this.forcedChunkZ != null) {
            level.setChunkForced(this.forcedChunkX, this.forcedChunkZ, false);
        }

        this.forcedChunkX = null;
        this.forcedChunkZ = null;
    }

    private CustomBossEvent createBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent existingBossBar = bossEvents.get(BOSSBAR_ID);
        if (existingBossBar != null) {
            existingBossBar.removeAllPlayers();
            bossEvents.remove(existingBossBar);
        }

        CustomBossEvent mercadorBossBar = bossEvents.create(
                BOSSBAR_ID,
                Component.translatable("event.oxyarena.mercador.bossbar"));
        mercadorBossBar.setColor(BossEvent.BossBarColor.YELLOW);
        mercadorBossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        mercadorBossBar.setMax(EVENT_DURATION_TICKS);
        mercadorBossBar.setValue(EVENT_DURATION_TICKS);
        mercadorBossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(mercadorBossBar::addPlayer);
        return mercadorBossBar;
    }

    private void clearBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent mercadorBossBar = this.bossBar != null ? this.bossBar : bossEvents.get(BOSSBAR_ID);
        if (mercadorBossBar != null) {
            mercadorBossBar.removeAllPlayers();
            bossEvents.remove(mercadorBossBar);
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

    private AABB getLoadedWorldBounds(ServerLevel level) {
        return new AABB(
                level.getWorldBorder().getMinX(),
                level.getMinBuildHeight(),
                level.getWorldBorder().getMinZ(),
                level.getWorldBorder().getMaxX(),
                level.getMaxBuildHeight(),
                level.getWorldBorder().getMaxZ());
    }
}
