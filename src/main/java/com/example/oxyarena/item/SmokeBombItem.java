package com.example.oxyarena.item;

import com.example.oxyarena.entity.projectile.SmokeBomb;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
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
import net.minecraft.world.level.Level;

public class SmokeBombItem extends Item implements ProjectileItem {
    private static final float SHOOT_POWER = 1.35F;
    private static final float SHOOT_INACCURACY = 0.8F;

    public SmokeBombItem(Properties properties) {
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
                SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS,
                0.6F,
                0.75F / (level.getRandom().nextFloat() * 0.4F + 0.8F));

        if (!level.isClientSide) {
            SmokeBomb smokeBomb = new SmokeBomb(level, player);
            smokeBomb.setItem(itemStack);
            smokeBomb.shootFromRotation(
                    player,
                    player.getXRot(),
                    player.getYRot(),
                    0.0F,
                    SHOOT_POWER,
                    SHOOT_INACCURACY);
            level.addFreshEntity(smokeBomb);
        }

        player.awardStat(Stats.ITEM_USED.get(this));
        itemStack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        SmokeBomb smokeBomb = new SmokeBomb(level, pos.x(), pos.y(), pos.z());
        smokeBomb.setItem(stack);
        return smokeBomb;
    }
}
