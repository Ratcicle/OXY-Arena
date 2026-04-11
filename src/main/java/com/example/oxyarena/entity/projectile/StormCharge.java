package com.example.oxyarena.entity.projectile;

import java.util.Optional;
import java.util.function.Function;

import com.example.oxyarena.event.ModGameEvents;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.windcharge.WindCharge;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.phys.Vec3;

public final class StormCharge extends WindCharge {
    private static final ExplosionDamageCalculator EXPLOSION_DAMAGE_CALCULATOR = new SimpleExplosionDamageCalculator(
            true,
            false,
            Optional.of(2.44F),
            BuiltInRegistries.BLOCK.getTag(BlockTags.BLOCKS_WIND_CHARGE_EXPLOSIONS).map(Function.identity()));
    private static final float EXPLOSION_RADIUS = 2.4F;

    public StormCharge(EntityType<? extends StormCharge> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void explode(Vec3 pos) {
        if (this.getOwner() instanceof Player player && !player.level().isClientSide) {
            ModGameEvents.grantStormChargeFallImmunity(player, pos);
        }

        this.level().explode(
                this,
                null,
                EXPLOSION_DAMAGE_CALCULATOR,
                pos.x(),
                pos.y(),
                pos.z(),
                EXPLOSION_RADIUS,
                false,
                Level.ExplosionInteraction.TRIGGER,
                ParticleTypes.GUST_EMITTER_SMALL,
                ParticleTypes.GUST_EMITTER_LARGE,
                SoundEvents.WIND_CHARGE_BURST);
    }

    @Override
    public ItemStack getItem() {
        return new ItemStack(ModItems.STORM_CHARGE.get());
    }
}
