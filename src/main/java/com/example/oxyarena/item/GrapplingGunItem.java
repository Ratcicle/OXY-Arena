package com.example.oxyarena.item;

import com.example.oxyarena.entity.projectile.GrapplingHook;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class GrapplingGunItem extends Item {
    private static final float HOOK_VELOCITY = 2.75F;
    private static final float HOOK_INACCURACY = 0.25F;

    public GrapplingGunItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);

        if (!level.isClientSide) {
            if (GrapplingHook.discardOwnedHooks(player)) {
                level.playSound(
                        null,
                        player.getX(),
                        player.getY(),
                        player.getZ(),
                        SoundEvents.CHAIN_BREAK,
                        SoundSource.PLAYERS,
                        0.8F,
                        1.4F);
                player.awardStat(Stats.ITEM_USED.get(this));
                return InteractionResultHolder.success(itemStack);
            }

            GrapplingHook grapplingHook = new GrapplingHook(level, player);
            grapplingHook.shootFromRotation(
                    player,
                    player.getXRot(),
                    player.getYRot(),
                    0.0F,
                    HOOK_VELOCITY,
                    HOOK_INACCURACY);
            level.addFreshEntity(grapplingHook);
        }

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.CHAIN_PLACE,
                SoundSource.PLAYERS,
                0.8F,
                1.2F);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(itemStack, level.isClientSide());
    }
}
