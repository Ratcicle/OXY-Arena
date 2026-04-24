package com.example.oxyarena.serverevent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
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
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

public final class ForbiddenRelicServerEvent implements OxyServerEvent {
    private static final int EVENT_DURATION_TICKS = 20 * 60 * 4;
    private static final int POSITION_BROADCAST_INTERVAL_TICKS = 20 * 20;
    private static final int GLOW_REFRESH_DURATION_TICKS = 60;
    private static final int MAX_GROUND_SCAN_Y = 300;
    private static final int BORDER_DROP_INSET_BLOCKS = 2;
    private static final int FINAL_MILESTONE_SECONDS = 120;
    private static final int[] MILESTONE_SECONDS = { 15, 30, 45, 60, 75, 90, 105, 120 };
    private static final String CARRIER_TAG = "oxyarena_forbidden_relic_carrier";
    private static final ResourceLocation BOSSBAR_ID = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "reliquia_proibida");
    private static final List<Supplier<? extends Item>> RARE_REWARDS = List.of(
            ModItems.GRAPPLING_GUN,
            ModItems.COBALT_BOW,
            ModItems.BLACK_DIAMOND_SWORD,
            () -> Items.MACE);
    private static final List<Supplier<? extends Item>> LEGENDARY_REWARDS = List.of(
            ModItems.ZEUS_LIGHTNING,
            ModItems.FLAMING_SCYTHE,
            ModItems.LIFEHUNT_SCYTHE,
            ModItems.MURASAMA,
            ModItems.RIVERS_OF_BLOOD,
            ModItems.BLACK_BLADE,
            ModItems.KUSABIMARU,
            ModItems.SOUL_REAPER,
            ModItems.EARTHBREAKER,
            ModItems.ZERO_REVERSE,
            ModItems.NECROMANCER_STAFF,
            ModItems.FROZEN_NEEDLE,
            ModItems.GHOST_SABER,
            ModItems.ZENITH);

    @Nullable
    private CustomBossEvent bossBar;
    @Nullable
    private UUID carrierUuid;
    @Nullable
    private UUID relicEntityUuid;
    @Nullable
    private Vec3 lastKnownRelicPos;
    private final Map<UUID, Set<Integer>> claimedMilestones = new HashMap<>();
    private String carrierName = "";
    private boolean active;
    private boolean completedByLegendary;
    private int timeRemainingTicks;
    private int carriedTicks;

    @Override
    public String getId() {
        return "reliquia_proibida";
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("event.oxyarena.reliquia_proibida");
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

        BlockPos spawnPos = this.findRelicSpawnPos(overworld);
        if (spawnPos == null) {
            return false;
        }

        this.cleanupStaleRuntimeArtifacts(server);
        ItemEntity relic = this.spawnRelic(overworld, Vec3.atCenterOf(spawnPos));
        if (relic == null) {
            return false;
        }

        this.active = true;
        this.completedByLegendary = false;
        this.carrierUuid = null;
        this.relicEntityUuid = relic.getUUID();
        this.lastKnownRelicPos = relic.position();
        this.carrierName = "";
        this.timeRemainingTicks = EVENT_DURATION_TICKS;
        this.carriedTicks = 0;
        this.claimedMilestones.clear();
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

        boolean legendaryCompletion = this.completedByLegendary;
        this.clearCarrierGlow(server);
        this.removeAllForbiddenRelics(server);
        this.active = false;
        this.completedByLegendary = false;
        this.carrierUuid = null;
        this.relicEntityUuid = null;
        this.lastKnownRelicPos = null;
        this.carrierName = "";
        this.timeRemainingTicks = 0;
        this.carriedTicks = 0;
        this.claimedMilestones.clear();
        this.clearBossBar(server);

        if (reason != ServerEventStopReason.SERVER_SHUTDOWN && !legendaryCompletion) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("event.oxyarena.reliquia_proibida.finished_no_winner")
                            .withStyle(ChatFormatting.DARK_PURPLE),
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

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            this.stop(server, ServerEventStopReason.COMPLETED);
            return;
        }

        if (this.carrierUuid != null) {
            this.tickCarrier(server, overworld);
        } else {
            this.tickGroundRelic(overworld);
        }

        if (server.getTickCount() % 20 == 0) {
            this.removeStrayPlayerRelics(server);
            this.removeDuplicateRelicEntities(overworld);
            this.updateBossBar(server);
        }
    }

    @Override
    public void onLivingDeath(MinecraftServer server, LivingDeathEvent event) {
        if (!this.active || !(event.getEntity() instanceof ServerPlayer player) || !this.isCarrier(player)) {
            return;
        }

        Vec3 dropPos = player.position();
        this.clearCarrierState(player, true);
        this.spawnRelicAt(server, dropPos);
        this.broadcastCarrierLost(server, "event.oxyarena.reliquia_proibida.carrier_died", player.getGameProfile().getName());
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

        if (!this.active || !this.isCarrier(player)) {
            return;
        }

        Vec3 dropPos = this.getLastKnownOrPlayerPos(player);
        this.clearCarrierState(player, true);
        this.spawnRelicAt(server, dropPos);
        this.broadcastCarrierLost(server, "event.oxyarena.reliquia_proibida.carrier_left_area", player.getGameProfile().getName());
    }

    @Override
    public void onPlayerLoggedOut(MinecraftServer server, ServerPlayer player) {
        if (!this.active || !this.isCarrier(player)) {
            return;
        }

        Vec3 dropPos = this.getLastKnownOrPlayerPos(player);
        this.clearCarrierState(player, true);
        this.spawnRelicAt(server, dropPos);
        this.broadcastCarrierLost(server, "event.oxyarena.reliquia_proibida.carrier_logged_out", player.getGameProfile().getName());
    }

    @Override
    public void onItemEntityPickup(MinecraftServer server, ItemEntityPickupEvent.Post event) {
        if (!this.active || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        ItemStack originalStack = event.getOriginalStack();
        int pickedUpCount = originalStack.getCount() - event.getCurrentStack().getCount();
        if (pickedUpCount <= 0 || !isForbiddenRelic(originalStack)) {
            return;
        }

        if (player.serverLevel().dimension() != Level.OVERWORLD
                || !this.getEventArea(server).contains(player.getX(), player.getZ())) {
            this.removeRelicsFromInventory(player, false);
            if (this.carrierUuid == null) {
                this.spawnRelicAt(server, this.findBoundaryDropPos(server, player.position()));
            }
            return;
        }

        if (this.carrierUuid != null && !this.isCarrier(player)) {
            this.removeRelicsFromInventory(player, false);
            return;
        }

        this.setCarrier(server, player);
    }

    @Override
    public void onItemToss(MinecraftServer server, ItemTossEvent event) {
        if (!this.active || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        ItemEntity tossedItem = event.getEntity();
        if (!isForbiddenRelic(tossedItem.getItem())) {
            return;
        }

        if (!this.isCarrier(player)) {
            tossedItem.discard();
            return;
        }

        this.prepareRelicEntity(tossedItem);
        this.relicEntityUuid = tossedItem.getUUID();
        this.lastKnownRelicPos = tossedItem.position();
        this.clearCarrierState(player, true);
        this.broadcastCarrierLost(server, "event.oxyarena.reliquia_proibida.carrier_dropped", player.getGameProfile().getName());
    }

    @Override
    public int getTimeRemainingTicks() {
        return this.timeRemainingTicks;
    }

    @Override
    public Component getStatusText() {
        if (!this.active) {
            return null;
        }

        if (this.carrierUuid != null) {
            return Component.translatable(
                    "event.oxyarena.reliquia_proibida.status.carried",
                    this.carrierName,
                    this.carriedTicks / 20,
                    this.formatTicks(this.timeRemainingTicks));
        }

        return Component.translatable(
                "event.oxyarena.reliquia_proibida.status.waiting",
                this.formatTicks(this.timeRemainingTicks));
    }

    @Override
    public void cleanupStaleRuntimeArtifacts(MinecraftServer server) {
        this.clearCarrierGlow(server);
        this.removeAllForbiddenRelics(server);
        this.active = false;
        this.completedByLegendary = false;
        this.carrierUuid = null;
        this.relicEntityUuid = null;
        this.lastKnownRelicPos = null;
        this.carrierName = "";
        this.timeRemainingTicks = 0;
        this.carriedTicks = 0;
        this.claimedMilestones.clear();
        this.clearBossBar(server);
    }

    private void tickCarrier(MinecraftServer server, ServerLevel overworld) {
        ServerPlayer carrier = this.getCarrierPlayer(server);
        if (carrier == null) {
            String lostCarrierName = this.carrierName;
            this.spawnRelicAt(server, this.lastKnownRelicPos);
            this.clearCarrierState(null, false);
            this.broadcastCarrierLost(server, "event.oxyarena.reliquia_proibida.carrier_lost", lostCarrierName);
            return;
        }

        if (!carrier.isAlive()
                || carrier.serverLevel().dimension() != Level.OVERWORLD
                || !this.getEventArea(server).contains(carrier.getX(), carrier.getZ())) {
            Vec3 dropPos = this.findBoundaryDropPos(server, carrier.position());
            this.clearCarrierState(carrier, true);
            this.spawnRelicAt(server, dropPos);
            this.broadcastCarrierLost(server, "event.oxyarena.reliquia_proibida.carrier_left_area", carrier.getGameProfile().getName());
            return;
        }

        int relicCount = this.removeRelicsFromInventory(carrier, true);
        if (relicCount <= 0) {
            Vec3 dropPos = this.getLastKnownOrPlayerPos(carrier);
            this.clearCarrierState(carrier, false);
            this.spawnRelicAt(server, dropPos);
            this.broadcastCarrierLost(server, "event.oxyarena.reliquia_proibida.carrier_lost", carrier.getGameProfile().getName());
            return;
        }

        this.lastKnownRelicPos = carrier.position();
        this.refreshCarrierGlow(carrier);
        this.carriedTicks++;
        this.checkRewardMilestones(server, carrier);

        if (this.active && this.carriedTicks > 0 && this.carriedTicks % POSITION_BROADCAST_INTERVAL_TICKS == 0) {
            this.broadcastCarrierPosition(server, carrier);
        }
    }

    private void tickGroundRelic(ServerLevel overworld) {
        ItemEntity relic = this.getTrackedRelicEntity(overworld);
        if (relic != null && relic.isAlive()) {
            this.prepareRelicEntity(relic);
            this.lastKnownRelicPos = relic.position();
            return;
        }

        this.spawnRelicAt(overworld.getServer(), this.lastKnownRelicPos);
    }

    private void setCarrier(MinecraftServer server, ServerPlayer player) {
        this.clearCarrierGlow(server);
        this.carrierUuid = player.getUUID();
        this.carrierName = player.getGameProfile().getName();
        this.relicEntityUuid = null;
        this.carriedTicks = 0;
        this.lastKnownRelicPos = player.position();
        this.removeRelicsFromInventory(player, true);
        this.refreshCarrierGlow(player);
        this.removeDuplicateRelicEntities(player.serverLevel());
        this.updateBossBar(server);

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.reliquia_proibida.picked", this.carrierName)
                        .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                false);
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 0.8F);
    }

    private void clearCarrierState(@Nullable ServerPlayer carrier, boolean removeInventoryRelics) {
        if (carrier != null) {
            if (removeInventoryRelics) {
                this.removeRelicsFromInventory(carrier, false);
            }
            carrier.removeTag(CARRIER_TAG);
            carrier.removeEffect(MobEffects.GLOWING);
        }

        this.carrierUuid = null;
        this.carrierName = "";
        this.carriedTicks = 0;
    }

    private void checkRewardMilestones(MinecraftServer server, ServerPlayer carrier) {
        int heldSeconds = this.carriedTicks / 20;
        Set<Integer> claimed = this.claimedMilestones.computeIfAbsent(carrier.getUUID(), ignored -> new HashSet<>());
        for (int milestoneSeconds : MILESTONE_SECONDS) {
            if (heldSeconds < milestoneSeconds || claimed.contains(milestoneSeconds)) {
                continue;
            }

            claimed.add(milestoneSeconds);
            List<ItemStack> rewards = this.createRewards(milestoneSeconds, carrier.serverLevel().random);
            rewards.forEach(stack -> this.giveOrDrop(carrier, stack));
            this.broadcastReward(server, carrier, milestoneSeconds, rewards);

            if (milestoneSeconds >= FINAL_MILESTONE_SECONDS) {
                this.completedByLegendary = true;
                server.getPlayerList().broadcastSystemMessage(
                        Component.translatable("event.oxyarena.reliquia_proibida.finished_winner", carrier.getGameProfile().getName())
                                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD),
                        false);
                this.stop(server, ServerEventStopReason.COMPLETED);
                return;
            }
        }
    }

    private List<ItemStack> createRewards(int milestoneSeconds, RandomSource random) {
        return switch (milestoneSeconds) {
            case 15 -> List.of(new ItemStack(Items.COOKED_BEEF, 12), new ItemStack(Items.ARROW, 16));
            case 30 -> List.of(new ItemStack(Items.ENDER_PEARL, 2), new ItemStack(ModItems.SMOKE_BOMB.get(), 2));
            case 45 -> List.of(new ItemStack(Items.DIAMOND, 3), new ItemStack(Items.GOLD_INGOT, 8));
            case 60 -> List.of(
                    new ItemStack(Items.GOLDEN_APPLE),
                    new ItemStack(Items.GOLDEN_CARROT, 6),
                    new ItemStack(ModItems.ESTUS_FLASK.get(), 2));
            case 75 -> List.of(new ItemStack(ModItems.COBALT_INGOT.get(), 4), new ItemStack(ModItems.STORM_CHARGE.get(), 3));
            case 90 -> List.of(new ItemStack(this.pickRandomItem(RARE_REWARDS, random)));
            case 105 -> List.of(new ItemStack(Items.NETHERITE_INGOT, 2), new ItemStack(Items.ANCIENT_DEBRIS, 4));
            case 120 -> List.of(new ItemStack(this.pickRandomItem(LEGENDARY_REWARDS, random)));
            default -> List.of();
        };
    }

    private Item pickRandomItem(List<Supplier<? extends Item>> itemSuppliers, RandomSource random) {
        return itemSuppliers.get(random.nextInt(itemSuppliers.size())).get();
    }

    @Nullable
    private BlockPos findRelicSpawnPos(ServerLevel level) {
        ServerEventArea eventArea = this.getEventArea(level.getServer());

        for (int attempt = 0; attempt < 48; attempt++) {
            int x = eventArea.randomX(level.random);
            int z = eventArea.randomZ(level.random);
            BlockPos relicPos = this.findRelicSpawnPosAt(level, x, z);
            if (relicPos != null) {
                return relicPos;
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findRelicSpawnPosNear(ServerLevel level, int targetX, int targetZ) {
        ServerEventArea eventArea = this.getEventArea(level.getServer());
        int clampedTargetX = Mth.clamp(targetX, eventArea.minX(), eventArea.maxX());
        int clampedTargetZ = Mth.clamp(targetZ, eventArea.minZ(), eventArea.maxZ());

        for (int radius = 0; radius <= 3; radius++) {
            for (int x = clampedTargetX - radius; x <= clampedTargetX + radius; x++) {
                int clampedX = Mth.clamp(x, eventArea.minX(), eventArea.maxX());
                for (int z = clampedTargetZ - radius; z <= clampedTargetZ + radius; z++) {
                    int clampedZ = Mth.clamp(z, eventArea.minZ(), eventArea.maxZ());
                    BlockPos relicPos = this.findRelicSpawnPosAt(level, clampedX, clampedZ);
                    if (relicPos != null) {
                        return relicPos;
                    }
                }
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findRelicSpawnPosAt(ServerLevel level, int x, int z) {
        int startY = Math.min(MAX_GROUND_SCAN_Y, level.getMaxBuildHeight() - 1);
        for (int y = startY; y > level.getMinBuildHeight(); y--) {
            BlockPos groundPos = new BlockPos(x, y, z);
            BlockState groundState = level.getBlockState(groundPos);
            if (!this.isValidRelicSupport(groundState)) {
                continue;
            }

            BlockPos relicPos = groundPos.above();
            if (level.getBlockState(relicPos).canBeReplaced()) {
                return relicPos;
            }

            break;
        }

        return null;
    }

    private boolean isValidRelicSupport(BlockState blockState) {
        if (blockState.isAir() || blockState.is(BlockTags.LEAVES) || blockState.is(BlockTags.LOGS)) {
            return false;
        }

        FluidState fluidState = blockState.getFluidState();
        return fluidState.isEmpty();
    }

    @Nullable
    private ItemEntity spawnRelicAt(MinecraftServer server, @Nullable Vec3 preferredPos) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return null;
        }

        Vec3 spawnPos = null;
        if (preferredPos != null) {
            BlockPos groundedPos = this.findRelicSpawnPosNear(
                    overworld,
                    Mth.floor(preferredPos.x),
                    Mth.floor(preferredPos.z));
            if (groundedPos != null) {
                spawnPos = Vec3.atCenterOf(groundedPos);
            }
        }

        if (spawnPos == null) {
            BlockPos fallbackPos = this.findRelicSpawnPos(overworld);
            if (fallbackPos == null) {
                return null;
            }
            spawnPos = Vec3.atCenterOf(fallbackPos);
        }

        ItemEntity relic = this.spawnRelic(overworld, spawnPos);
        if (relic != null) {
            this.relicEntityUuid = relic.getUUID();
            this.lastKnownRelicPos = relic.position();
        }

        return relic;
    }

    @Nullable
    private ItemEntity spawnRelic(ServerLevel level, Vec3 pos) {
        ItemEntity relic = new ItemEntity(level, pos.x, pos.y, pos.z, this.createRelicStack());
        this.prepareRelicEntity(relic);
        if (!level.addFreshEntity(relic)) {
            return null;
        }

        level.playSound(null, relic.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.75F);
        return relic;
    }

    private void prepareRelicEntity(ItemEntity relic) {
        relic.setNoPickUpDelay();
        relic.setUnlimitedLifetime();
        relic.setGlowingTag(true);
    }

    private ItemStack createRelicStack() {
        return new ItemStack(ModItems.FORBIDDEN_RELIC.get());
    }

    @Nullable
    private ItemEntity getTrackedRelicEntity(ServerLevel level) {
        if (this.relicEntityUuid == null) {
            return null;
        }

        Entity entity = level.getEntity(this.relicEntityUuid);
        if (entity instanceof ItemEntity itemEntity && isForbiddenRelic(itemEntity.getItem())) {
            return itemEntity;
        }

        return null;
    }

    private void removeDuplicateRelicEntities(ServerLevel level) {
        int kept = 0;
        for (ItemEntity itemEntity : level.getEntitiesOfClass(
                ItemEntity.class,
                getLoadedWorldBounds(level),
                entity -> isForbiddenRelic(entity.getItem()))) {
            if (this.carrierUuid != null) {
                itemEntity.discard();
                continue;
            }

            if (this.relicEntityUuid != null && itemEntity.getUUID().equals(this.relicEntityUuid)) {
                kept++;
                this.prepareRelicEntity(itemEntity);
                continue;
            }

            if (this.relicEntityUuid == null && kept == 0) {
                kept++;
                this.relicEntityUuid = itemEntity.getUUID();
                this.lastKnownRelicPos = itemEntity.position();
                this.prepareRelicEntity(itemEntity);
                continue;
            }

            itemEntity.discard();
        }
    }

    private void removeAllForbiddenRelics(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            this.removeRelicsFromInventory(player, false);
            player.removeTag(CARRIER_TAG);
            player.removeEffect(MobEffects.GLOWING);
        }

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        for (ItemEntity itemEntity : overworld.getEntitiesOfClass(
                ItemEntity.class,
                getLoadedWorldBounds(overworld),
                entity -> isForbiddenRelic(entity.getItem()))) {
            itemEntity.discard();
        }
    }

    private void removeStrayPlayerRelics(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (this.carrierUuid != null && player.getUUID().equals(this.carrierUuid)) {
                continue;
            }

            this.removeRelicsFromInventory(player, false);
        }
    }

    private int removeRelicsFromInventory(ServerPlayer player, boolean keepOne) {
        Inventory inventory = player.getInventory();
        boolean keptRelic = false;
        int foundRelics = 0;
        for (NonNullList<ItemStack> itemList : List.of(inventory.items, inventory.armor, inventory.offhand)) {
            for (int slot = 0; slot < itemList.size(); slot++) {
                ItemStack stack = itemList.get(slot);
                if (!isForbiddenRelic(stack)) {
                    continue;
                }

                foundRelics += stack.getCount();
                if (keepOne && !keptRelic) {
                    stack.setCount(1);
                    keptRelic = true;
                } else {
                    itemList.set(slot, ItemStack.EMPTY);
                }
            }
        }

        if (foundRelics > 0) {
            inventory.setChanged();
        }

        return foundRelics;
    }

    private boolean isCarrier(ServerPlayer player) {
        return this.carrierUuid != null && this.carrierUuid.equals(player.getUUID());
    }

    @Nullable
    private ServerPlayer getCarrierPlayer(MinecraftServer server) {
        return this.carrierUuid != null ? server.getPlayerList().getPlayer(this.carrierUuid) : null;
    }

    private void refreshCarrierGlow(ServerPlayer carrier) {
        carrier.addTag(CARRIER_TAG);
        MobEffectInstance glowingEffect = carrier.getEffect(MobEffects.GLOWING);
        if (glowingEffect == null || glowingEffect.getDuration() <= 20) {
            carrier.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_REFRESH_DURATION_TICKS, 0, false, false, false));
        }
    }

    private void clearCarrierGlow(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.getTags().contains(CARRIER_TAG)) {
                player.removeTag(CARRIER_TAG);
                player.removeEffect(MobEffects.GLOWING);
            }
        }
    }

    private Vec3 getLastKnownOrPlayerPos(ServerPlayer player) {
        return this.lastKnownRelicPos != null ? this.lastKnownRelicPos : player.position();
    }

    @Nullable
    private Vec3 findBoundaryDropPos(MinecraftServer server, Vec3 sourcePos) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return this.lastKnownRelicPos;
        }

        ServerEventArea eventArea = this.getEventArea(server);
        int minX = Math.min(eventArea.minX() + BORDER_DROP_INSET_BLOCKS, eventArea.maxX());
        int maxX = Math.max(eventArea.maxX() - BORDER_DROP_INSET_BLOCKS, eventArea.minX());
        int minZ = Math.min(eventArea.minZ() + BORDER_DROP_INSET_BLOCKS, eventArea.maxZ());
        int maxZ = Math.max(eventArea.maxZ() - BORDER_DROP_INSET_BLOCKS, eventArea.minZ());

        int targetX = Mth.clamp(Mth.floor(sourcePos.x), minX, maxX);
        int targetZ = Mth.clamp(Mth.floor(sourcePos.z), minZ, maxZ);

        if (sourcePos.x < eventArea.minX()) {
            targetX = minX;
        } else if (sourcePos.x > eventArea.maxX()) {
            targetX = maxX;
        }

        if (sourcePos.z < eventArea.minZ()) {
            targetZ = minZ;
        } else if (sourcePos.z > eventArea.maxZ()) {
            targetZ = maxZ;
        }

        BlockPos groundedPos = this.findRelicSpawnPosNear(overworld, targetX, targetZ);
        return groundedPos != null ? Vec3.atCenterOf(groundedPos) : this.lastKnownRelicPos;
    }

    private ServerEventArea getEventArea(MinecraftServer server) {
        return ServerEventAreas.getArea(server, ServerEventGroup.MAP_ROTATION);
    }

    private void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (!player.getInventory().add(stack.copy())) {
            player.drop(stack.copy(), false);
        }
    }

    private void broadcastStart(MinecraftServer server, BlockPos pos) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.reliquia_proibida.started",
                        pos.getX(),
                        pos.getY(),
                        pos.getZ()).withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                false);
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("event.oxyarena.reliquia_proibida.hint")
                        .withStyle(ChatFormatting.LIGHT_PURPLE),
                false);
    }

    private void broadcastCarrierPosition(MinecraftServer server, ServerPlayer carrier) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(
                        "event.oxyarena.reliquia_proibida.position",
                        carrier.getGameProfile().getName(),
                        (int)Math.floor(carrier.getX()),
                        (int)Math.floor(carrier.getZ()))
                        .withStyle(ChatFormatting.RED),
                false);
    }

    private void broadcastCarrierLost(MinecraftServer server, String translationKey, String playerName) {
        server.getPlayerList().broadcastSystemMessage(
                Component.translatable(translationKey, playerName)
                        .withStyle(ChatFormatting.YELLOW),
                false);
        this.updateBossBar(server);
    }

    private void broadcastReward(MinecraftServer server, ServerPlayer carrier, int milestoneSeconds, List<ItemStack> rewards) {
        String rewardText = rewards.stream()
                .map(this::formatRewardStack)
                .collect(Collectors.joining(", "));
        String translationKey = milestoneSeconds >= FINAL_MILESTONE_SECONDS
                ? "event.oxyarena.reliquia_proibida.reward_final"
                : "event.oxyarena.reliquia_proibida.reward";
        Component message = Component.translatable(translationKey, carrier.getGameProfile().getName(), milestoneSeconds, rewardText);
        server.getPlayerList().broadcastSystemMessage(
                milestoneSeconds >= FINAL_MILESTONE_SECONDS
                        ? message.copy().withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD)
                        : message.copy().withStyle(ChatFormatting.LIGHT_PURPLE),
                false);
        carrier.serverLevel().playSound(null, carrier.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.8F, 1.25F);
    }

    private String formatRewardStack(ItemStack stack) {
        return "%sx %s".formatted(stack.getCount(), stack.getHoverName().getString());
    }

    private CustomBossEvent createBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent existingBossBar = bossEvents.get(BOSSBAR_ID);
        if (existingBossBar != null) {
            existingBossBar.removeAllPlayers();
            bossEvents.remove(existingBossBar);
        }

        CustomBossEvent relicBossBar = bossEvents.create(
                BOSSBAR_ID,
                Component.translatable("event.oxyarena.reliquia_proibida.bossbar.waiting"));
        relicBossBar.setColor(BossEvent.BossBarColor.PURPLE);
        relicBossBar.setOverlay(BossEvent.BossBarOverlay.PROGRESS);
        relicBossBar.setMax(EVENT_DURATION_TICKS);
        relicBossBar.setValue(EVENT_DURATION_TICKS);
        relicBossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(relicBossBar::addPlayer);
        return relicBossBar;
    }

    private void clearBossBar(MinecraftServer server) {
        CustomBossEvents bossEvents = server.getCustomBossEvents();
        CustomBossEvent relicBossBar = this.bossBar != null ? this.bossBar : bossEvents.get(BOSSBAR_ID);
        if (relicBossBar != null) {
            relicBossBar.removeAllPlayers();
            bossEvents.remove(relicBossBar);
        }

        this.bossBar = null;
    }

    private void updateBossBar(MinecraftServer server) {
        if (this.bossBar == null) {
            return;
        }

        if (this.carrierUuid == null) {
            this.bossBar.setName(Component.translatable("event.oxyarena.reliquia_proibida.bossbar.waiting"));
            this.bossBar.setMax(EVENT_DURATION_TICKS);
            this.bossBar.setValue(Math.max(0, this.timeRemainingTicks));
        } else {
            int nextMilestoneSeconds = this.getNextUnclaimedMilestoneSeconds();
            this.bossBar.setName(Component.translatable(
                    "event.oxyarena.reliquia_proibida.bossbar.carried",
                    this.carrierName,
                    nextMilestoneSeconds));
            this.bossBar.setMax(nextMilestoneSeconds * 20);
            this.bossBar.setValue(Math.min(this.carriedTicks, nextMilestoneSeconds * 20));
        }

        this.bossBar.setVisible(true);
        server.getPlayerList().getPlayers().forEach(this.bossBar::addPlayer);
    }

    private int getNextUnclaimedMilestoneSeconds() {
        if (this.carrierUuid == null) {
            return MILESTONE_SECONDS[0];
        }

        Set<Integer> claimed = this.claimedMilestones.getOrDefault(this.carrierUuid, Set.of());
        for (int milestoneSeconds : MILESTONE_SECONDS) {
            if (!claimed.contains(milestoneSeconds)) {
                return milestoneSeconds;
            }
        }

        return FINAL_MILESTONE_SECONDS;
    }

    private String formatTicks(int ticks) {
        int totalSeconds = Math.max(0, ticks / 20);
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return "%d:%02d".formatted(minutes, seconds);
    }

    private static boolean isForbiddenRelic(ItemStack stack) {
        return !stack.isEmpty() && stack.is(ModItems.FORBIDDEN_RELIC.get());
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
