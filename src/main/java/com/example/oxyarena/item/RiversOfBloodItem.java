package com.example.oxyarena.item;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.util.BetterCombatCompat;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public class RiversOfBloodItem extends SwordItem {
    private static final int CORPSE_PILER_INPUT_COOLDOWN_TICKS = 1;
    private static final long CORPSE_PILER_PRESET_WINDOW_TICKS = 10L;
    private static final ResourceLocation CORPSE_PILER_PRESET_ID = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "corpse_piler");
    private static final Map<UUID, Long> ACTIVE_CORPSE_PILER_UNTIL = new HashMap<>();
    private static final Set<UUID> PENDING_CLIENT_ATTACKS = new HashSet<>();

    public RiversOfBloodItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(itemStack);
        }

        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(itemStack);
        }

        activateCorpsePiler(player, itemStack, level.getGameTime(), level.isClientSide);
        player.getCooldowns().addCooldown(this, CORPSE_PILER_INPUT_COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.rivers_of_blood.ability")
                .withStyle(ChatFormatting.DARK_RED));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.rivers_of_blood.detail")
                .withStyle(ChatFormatting.GRAY));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.rivers_of_blood.combo")
                .withStyle(ChatFormatting.GRAY));
    }

    public static void tickCorpsePilerState(Player player, long gameTime) {
        Long activeUntil = ACTIVE_CORPSE_PILER_UNTIL.get(player.getUUID());
        if (activeUntil == null || activeUntil > gameTime) {
            return;
        }

        clearCorpsePilerState(player);
    }

    public static void clearCorpsePilerState(Player player) {
        ACTIVE_CORPSE_PILER_UNTIL.remove(player.getUUID());
        PENDING_CLIENT_ATTACKS.remove(player.getUUID());
        forEachRiversOfBloodStack(player, RiversOfBloodItem::clearCorpsePilerState);
    }

    public static boolean consumePendingClientAttack(Player player, ItemStack stack) {
        if (!stack.is(ModItems.RIVERS_OF_BLOOD.get()) || !PENDING_CLIENT_ATTACKS.remove(player.getUUID())) {
            return false;
        }

        return true;
    }

    private static void activateCorpsePiler(Player player, ItemStack stack, long gameTime, boolean queueClientAttack) {
        if (!stack.is(ModItems.RIVERS_OF_BLOOD.get()) || !BetterCombatCompat.canOverrideWeaponPreset()) {
            return;
        }

        BetterCombatCompat.setWeaponPreset(stack, CORPSE_PILER_PRESET_ID);
        ACTIVE_CORPSE_PILER_UNTIL.put(player.getUUID(), gameTime + CORPSE_PILER_PRESET_WINDOW_TICKS);
        if (queueClientAttack) {
            PENDING_CLIENT_ATTACKS.add(player.getUUID());
        }
    }

    private static void clearCorpsePilerState(ItemStack stack) {
        if (!stack.is(ModItems.RIVERS_OF_BLOOD.get())) {
            return;
        }

        BetterCombatCompat.clearWeaponPreset(stack);
    }

    private static void forEachRiversOfBloodStack(Player player, java.util.function.Consumer<ItemStack> consumer) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.RIVERS_OF_BLOOD.get())) {
                consumer.accept(stack);
            }
        }
    }
}
