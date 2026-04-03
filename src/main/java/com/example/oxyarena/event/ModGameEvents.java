package com.example.oxyarena.event;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.example.oxyarena.item.CobaltBowItem;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ModGameEvents {
    private static final int COBALT_ARROW_RAIN_ARROWS_PER_TICK = 3;
    private static final int COBALT_ARROW_RAIN_WAVES = 10;
    private static final double COBALT_ARROW_RAIN_RADIUS = 4.5D;
    private static final double COBALT_ARROW_RAIN_HEIGHT = 16.0D;
    private static final double COBALT_ARROW_RAIN_DAMAGE = 1.5D;
    private static final float COBALT_ARROW_RAIN_VELOCITY = 2.6F;
    private static final List<CobaltArrowRainWave> COBALT_ARROW_RAIN_WAVES_QUEUE = new ArrayList<>();

    private ModGameEvents() {
    }

    public static void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)
                || !event.getSource().is(DamageTypeTags.IS_FIRE)
                || !isHoldingFlamingScythe(player)) {
            return;
        }

        float fireDamage = event.getNewDamage();
        if (fireDamage <= 0.0F) {
            event.setNewDamage(0.0F);
            return;
        }

        event.setNewDamage(0.0F);
        if (!player.level().isClientSide) {
            player.heal(fireDamage);
        }
    }

    private static boolean isHoldingFlamingScythe(Player player) {
        return player.getMainHandItem().is(ModItems.FLAMING_SCYTHE.get())
                || player.getOffhandItem().is(ModItems.FLAMING_SCYTHE.get());
    }

    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow)
                || arrow.level().isClientSide()
                || !CobaltBowItem.hasArrowRain(arrow)
                || !(event.getRayTraceResult() instanceof EntityHitResult entityHitResult)
                || !(entityHitResult.getEntity() instanceof LivingEntity target)) {
            return;
        }

        spawnCobaltArrowRain((ServerLevel)arrow.level(), arrow.getOwner(), target);
    }

    private static void spawnCobaltArrowRain(ServerLevel level, Entity owner, LivingEntity target) {
        Vec3 targetCenter = target.getBoundingBox().getCenter();
        COBALT_ARROW_RAIN_WAVES_QUEUE.add(new CobaltArrowRainWave(
                level,
                owner,
                targetCenter.x,
                target.getY() + target.getBbHeight() + COBALT_ARROW_RAIN_HEIGHT,
                targetCenter.z,
                COBALT_ARROW_RAIN_WAVES));
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        if (COBALT_ARROW_RAIN_WAVES_QUEUE.isEmpty()) {
            return;
        }

        Iterator<CobaltArrowRainWave> iterator = COBALT_ARROW_RAIN_WAVES_QUEUE.iterator();
        while (iterator.hasNext()) {
            CobaltArrowRainWave arrowRainWave = iterator.next();
            if (arrowRainWave.level().getServer() != event.getServer()) {
                iterator.remove();
                continue;
            }

            arrowRainWave.spawnWave();
            if (arrowRainWave.isFinished()) {
                iterator.remove();
            }
        }
    }

    private static final class CobaltArrowRainWave {
        private final ServerLevel level;
        private final Entity owner;
        private final double centerX;
        private final double spawnY;
        private final double centerZ;
        private int remainingWaves;

        private CobaltArrowRainWave(
                ServerLevel level,
                Entity owner,
                double centerX,
                double spawnY,
                double centerZ,
                int remainingWaves) {
            this.level = level;
            this.owner = owner;
            this.centerX = centerX;
            this.spawnY = spawnY;
            this.centerZ = centerZ;
            this.remainingWaves = remainingWaves;
        }

        private ServerLevel level() {
            return this.level;
        }

        private void spawnWave() {
            RandomSource random = this.level.getRandom();

            for (int arrowIndex = 0; arrowIndex < COBALT_ARROW_RAIN_ARROWS_PER_TICK; arrowIndex++) {
                double angle = random.nextDouble() * Math.PI * 2.0D;
                double radius = COBALT_ARROW_RAIN_RADIUS * Math.sqrt(random.nextDouble());
                double spawnX = this.centerX + Math.cos(angle) * radius;
                double spawnZ = this.centerZ + Math.sin(angle) * radius;

                Arrow rainArrow = new Arrow(
                        this.level,
                        spawnX,
                        this.spawnY + random.nextDouble(),
                        spawnZ,
                        Items.ARROW.getDefaultInstance(),
                        null);
                rainArrow.setOwner(this.owner);
                rainArrow.pickup = AbstractArrow.Pickup.DISALLOWED;
                rainArrow.setBaseDamage(COBALT_ARROW_RAIN_DAMAGE);
                rainArrow.shoot(0.0D, -1.0D, 0.0D, COBALT_ARROW_RAIN_VELOCITY, 0.0F);

                this.level.addFreshEntity(rainArrow);
            }

            this.remainingWaves--;
        }

        private boolean isFinished() {
            return this.remainingWaves <= 0;
        }
    }
}
