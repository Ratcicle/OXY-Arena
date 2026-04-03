package com.example.oxyarena.item;

import com.example.oxyarena.entity.projectile.ThrownZeusLightning;

import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.level.Level;

public class ZeusLightningItem extends Item implements ProjectileItem {
    private static final int THROW_THRESHOLD_TIME = 10;
    private static final float SHOOT_POWER = 2.5F;
    private static final double ATTACK_DAMAGE_BONUS = 4.0D;
    private static final double ATTACK_SPEED_BONUS = -2.0D;

    public ZeusLightningItem(Properties properties) {
        super(properties);
    }

    public static ItemAttributeModifiers createAttributes() {
        return ItemAttributeModifiers.builder()
                .add(
                        Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                BASE_ATTACK_DAMAGE_ID,
                                ATTACK_DAMAGE_BONUS,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(
                        Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                BASE_ATTACK_SPEED_ID,
                                ATTACK_SPEED_BONUS,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack itemStack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(itemStack);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity livingEntity, int timeLeft) {
        if (!(livingEntity instanceof Player player)) {
            return;
        }

        int useTicks = this.getUseDuration(stack, livingEntity) - timeLeft;
        if (useTicks < THROW_THRESHOLD_TIME) {
            return;
        }

        SoundEvent throwSound = SoundEvents.TRIDENT_THROW.value();
        if (!level.isClientSide) {
            ThrownZeusLightning zeusLightning = new ThrownZeusLightning(
                    level,
                    player,
                    stack,
                    livingEntity.getUsedItemHand());
            zeusLightning.shootFromRotation(
                    player,
                    player.getXRot(),
                    player.getYRot(),
                    0.0F,
                    SHOOT_POWER,
                    0.0F);
            if (player.hasInfiniteMaterials()) {
                zeusLightning.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            }

            level.addFreshEntity(zeusLightning);
            level.playSound(null, zeusLightning, throwSound, SoundSource.PLAYERS, 1.0F, 1.0F);
            if (!player.hasInfiniteMaterials()) {
                stack.consume(1, player);
            }
        }

        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    public Projectile asProjectile(Level level, Position pos, ItemStack stack, Direction direction) {
        ThrownZeusLightning zeusLightning = new ThrownZeusLightning(level, pos.x(), pos.y(), pos.z(), stack);
        zeusLightning.pickup = AbstractArrow.Pickup.ALLOWED;
        return zeusLightning;
    }
}
