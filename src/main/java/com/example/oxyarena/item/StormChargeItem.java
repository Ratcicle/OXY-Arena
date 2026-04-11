package com.example.oxyarena.item;

import com.example.oxyarena.entity.projectile.StormCharge;
import com.example.oxyarena.registry.ModEntityTypes;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.phys.Vec3;

public class StormChargeItem extends Item implements ProjectileItem {
    private static final int COOLDOWN_TICKS = 10;

    public StormChargeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide()) {
            StormCharge stormCharge = new StormCharge(ModEntityTypes.STORM_CHARGE.get(), level);
            stormCharge.setOwner(player);
            stormCharge.setPos(player.position().x(), player.getEyePosition().y(), player.position().z());
            stormCharge.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.5F, 1.0F);
            level.addFreshEntity(stormCharge);
        }

        level.playSound(
                null,
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.WIND_CHARGE_THROW,
                SoundSource.NEUTRAL,
                0.5F,
                0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
        RandomSource random = level.getRandom();
        double x = random.triangle((double)direction.getStepX(), 0.11485000000000001D);
        double y = random.triangle((double)direction.getStepY(), 0.11485000000000001D);
        double z = random.triangle((double)direction.getStepZ(), 0.11485000000000001D);
        Vec3 movement = new Vec3(x, y, z);
        StormCharge stormCharge = new StormCharge(ModEntityTypes.STORM_CHARGE.get(), level);
        stormCharge.setPos(position.x(), position.y(), position.z());
        stormCharge.setDeltaMovement(movement);
        return stormCharge;
    }

    @Override
    public void shoot(Projectile projectile, double x, double y, double z, float power, float uncertainty) {
    }

    @Override
    public ProjectileItem.DispenseConfig createDispenseConfig() {
        return ProjectileItem.DispenseConfig.builder()
                .positionFunction((blockSource, itemStack) -> DispenserBlock.getDispensePosition(blockSource, 1.0D, Vec3.ZERO))
                .uncertainty(6.6666665F)
                .power(1.0F)
                .overrideDispenseEvent(1051)
                .build();
    }
}
