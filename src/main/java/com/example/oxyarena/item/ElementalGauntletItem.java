package com.example.oxyarena.item;

import java.util.List;

import com.example.oxyarena.entity.projectile.ElementalGauntletProjectile;
import com.example.oxyarena.registry.ModEntityTypes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public final class ElementalGauntletItem extends Item {
    private static final String SHOT_INDEX_TAG = "ElementalGauntletShotIndex";
    private static final int USE_DURATION_TICKS = 72000;
    private static final int FIRE_INTERVAL_TICKS = 10;
    private static final int SHOT_PATTERN_LENGTH = 20;
    private static final float SHOOT_POWER = 2.8F;
    private static final float SHOOT_INACCURACY = 0.05F;

    public ElementalGauntletItem(Properties properties) {
        super(properties);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_DURATION_TICKS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }

        player.startUsingItem(hand);
        if (!level.isClientSide) {
            boolean canContinue = fireProjectile(level, player, stack);
            if (!canContinue) {
                player.stopUsingItem();
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity livingEntity, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide
                || !(livingEntity instanceof Player player)
                || player.getUsedItemHand() != InteractionHand.MAIN_HAND
                || player.getUseItem() != stack) {
            return;
        }

        int elapsedTicks = this.getUseDuration(stack, livingEntity) - remainingUseDuration;
        if (elapsedTicks <= 0 || elapsedTicks % FIRE_INTERVAL_TICKS != 0) {
            return;
        }

        if (!fireProjectile(level, player, stack)) {
            player.stopUsingItem();
        }
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.elemental_gauntlet.passive")
                .withStyle(ChatFormatting.GOLD));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.elemental_gauntlet.detail")
                .withStyle(ChatFormatting.AQUA));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.elemental_gauntlet.knockback")
                .withStyle(ChatFormatting.GRAY));
    }

    private static boolean fireProjectile(Level level, Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        FirePattern firePattern = getNextFirePattern(stack);
        ElementalGauntletProjectile projectile = new ElementalGauntletProjectile(
                ModEntityTypes.ELEMENTAL_GAUNTLET_PROJECTILE.get(),
                level,
                player,
                stack,
                firePattern.variant(),
                firePattern.knockbackBurst());
        projectile.shootFromRotation(
                player,
                player.getXRot(),
                player.getYRot(),
                0.0F,
                SHOOT_POWER,
                SHOOT_INACCURACY);
        level.addFreshEntity(projectile);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.BLAZE_SHOOT,
                SoundSource.PLAYERS,
                0.7F,
                1.15F + level.getRandom().nextFloat() * 0.15F);

        if (!player.hasInfiniteMaterials()) {
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }

        return !stack.isEmpty();
    }

    private static FirePattern getNextFirePattern(ItemStack stack) {
        int shotIndex = Math.floorMod(
                stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                        .copyTag()
                        .getInt(SHOT_INDEX_TAG),
                SHOT_PATTERN_LENGTH);
        int variant = shotIndex % 4;
        boolean knockbackBurst = shotIndex % 5 == 4;
        int nextShotIndex = (shotIndex + 1) % SHOT_PATTERN_LENGTH;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putInt(SHOT_INDEX_TAG, nextShotIndex));
        return new FirePattern(variant, knockbackBurst);
    }

    private record FirePattern(int variant, boolean knockbackBurst) {
    }
}
