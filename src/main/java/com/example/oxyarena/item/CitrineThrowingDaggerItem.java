package com.example.oxyarena.item;

import com.example.oxyarena.entity.projectile.CitrineThrowingDagger;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

public class CitrineThrowingDaggerItem extends Item implements ProjectileItem {
    private static final int COOLDOWN_TICKS = 15;
    private static final float SHOOT_POWER = 2.0F;
    private static final float SHOOT_INACCURACY = 0.5F;

    public CitrineThrowingDaggerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(itemStack);
        }

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.TRIDENT_THROW,
                SoundSource.PLAYERS,
                0.8F,
                1.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!level.isClientSide) {
            CitrineThrowingDagger dagger = new CitrineThrowingDagger(level, player, itemStack);
            dagger.shootFromRotation(
                    player,
                    player.getXRot(),
                    player.getYRot(),
                    0.0F,
                    SHOOT_POWER,
                    SHOOT_INACCURACY);
            if (player.hasInfiniteMaterials()) {
                dagger.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }

            level.addFreshEntity(dagger);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        itemStack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        CitrineThrowingDagger dagger = new CitrineThrowingDagger(level, pos.x(), pos.y(), pos.z(), stack);
        dagger.pickup = AbstractArrow.Pickup.ALLOWED;
        return dagger;
    }
}
