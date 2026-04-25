package com.example.oxyarena.item;

import java.util.List;

import com.example.oxyarena.entity.projectile.AntiHealPotionProjectile;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

public final class AntiHealPotionItem extends Item implements ProjectileItem {
    private static final float SHOOT_X_ROT_OFFSET = -20.0F;
    private static final float SHOOT_POWER = 0.5F;
    private static final float SHOOT_INACCURACY = 1.0F;

    public AntiHealPotionItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.SPLASH_POTION_THROW,
                SoundSource.PLAYERS,
                0.5F,
                0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!level.isClientSide) {
            AntiHealPotionProjectile projectile = new AntiHealPotionProjectile(level, player);
            projectile.setItem(itemStack);
            projectile.shootFromRotation(
                    player,
                    player.getXRot(),
                    player.getYRot(),
                    SHOOT_X_ROT_OFFSET,
                    SHOOT_POWER,
                    SHOOT_INACCURACY);
            level.addFreshEntity(projectile);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        itemStack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        AntiHealPotionProjectile projectile = new AntiHealPotionProjectile(level, pos.x(), pos.y(), pos.z());
        projectile.setItem(stack);
        return projectile;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.anti_heal_potion")
                .withStyle(ChatFormatting.DARK_PURPLE));
    }
}
