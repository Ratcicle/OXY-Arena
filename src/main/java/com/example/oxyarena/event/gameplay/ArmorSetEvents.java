package com.example.oxyarena.event.gameplay;

import java.util.ArrayList;
import java.util.List;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public final class ArmorSetEvents {
    private static final int SET_PASSIVE_EFFECT_DURATION_TICKS = 10;
    private static final int DIAMOND_SET_FORTUNE_LEVEL = 3;
    private static final double COBALT_SET_MAX_HEALTH_BONUS = 4.0D;
    private static final double NETHERITE_SET_KNOCKBACK_RESISTANCE_BONUS = 0.6D;
    private static final double NETHERITE_SET_EXPLOSION_KNOCKBACK_RESISTANCE_BONUS = 1.0D;
    private static final float NETHERITE_SET_EXPLOSION_DAMAGE_MULTIPLIER = 0.65F;

    private static final ResourceLocation COBALT_SET_MAX_HEALTH_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "set_bonus.cobalt_max_health");
    private static final ResourceLocation NETHERITE_SET_KNOCKBACK_RESISTANCE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "set_bonus.netherite_knockback_resistance");
    private static final ResourceLocation NETHERITE_SET_EXPLOSION_KNOCKBACK_RESISTANCE_MODIFIER_ID = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "set_bonus.netherite_explosion_knockback_resistance");

    private ArmorSetEvents() {
    }

    public static void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        handleNetheriteSetExplosionResistance(event);
    }

    public static void onBlockDrops(BlockDropsEvent event) {
        handleDiamondSetFortune(event);
    }

    public static void onServerTickPost(ServerTickEvent.Post event) {
        for (Player player : event.getServer().getPlayerList().getPlayers()) {
            if (player.isSpectator()) {
                clearArmorSetAttributeModifiers(player);
                continue;
            }

            if (hasFullCitrineSet(player)) {
                applyRefreshingEffect(player, MobEffects.DIG_SPEED, 1);
            }

            if (hasFullIronSet(player)) {
                applyRefreshingEffect(player, MobEffects.DAMAGE_RESISTANCE, 0);
            }

            updateTransientAttributeModifier(
                    player,
                    Attributes.MAX_HEALTH,
                    COBALT_SET_MAX_HEALTH_MODIFIER_ID,
                    COBALT_SET_MAX_HEALTH_BONUS,
                    AttributeModifier.Operation.ADD_VALUE,
                    hasFullCobaltSet(player));
            updateTransientAttributeModifier(
                    player,
                    Attributes.KNOCKBACK_RESISTANCE,
                    NETHERITE_SET_KNOCKBACK_RESISTANCE_MODIFIER_ID,
                    NETHERITE_SET_KNOCKBACK_RESISTANCE_BONUS,
                    AttributeModifier.Operation.ADD_VALUE,
                    hasFullNetheriteSet(player));
            updateTransientAttributeModifier(
                    player,
                    Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,
                    NETHERITE_SET_EXPLOSION_KNOCKBACK_RESISTANCE_MODIFIER_ID,
                    NETHERITE_SET_EXPLOSION_KNOCKBACK_RESISTANCE_BONUS,
                    AttributeModifier.Operation.ADD_VALUE,
                    hasFullNetheriteSet(player));
        }
    }

    private static void clearArmorSetAttributeModifiers(Player player) {
        removeTransientAttributeModifier(player, Attributes.MAX_HEALTH, COBALT_SET_MAX_HEALTH_MODIFIER_ID);
        removeTransientAttributeModifier(player, Attributes.KNOCKBACK_RESISTANCE, NETHERITE_SET_KNOCKBACK_RESISTANCE_MODIFIER_ID);
        removeTransientAttributeModifier(
                player,
                Attributes.EXPLOSION_KNOCKBACK_RESISTANCE,
                NETHERITE_SET_EXPLOSION_KNOCKBACK_RESISTANCE_MODIFIER_ID);
    }

    private static void applyRefreshingEffect(Player player, Holder<MobEffect> effect, int amplifier) {
        MobEffectInstance currentEffect = player.getEffect(effect);
        if (currentEffect != null
                && currentEffect.getAmplifier() > amplifier
                && currentEffect.getDuration() > SET_PASSIVE_EFFECT_DURATION_TICKS / 2) {
            return;
        }

        if (currentEffect != null
                && currentEffect.getAmplifier() == amplifier
                && currentEffect.getDuration() > SET_PASSIVE_EFFECT_DURATION_TICKS / 2) {
            return;
        }

        player.addEffect(new MobEffectInstance(effect, SET_PASSIVE_EFFECT_DURATION_TICKS, amplifier, false, false, true));
    }

    private static void updateTransientAttributeModifier(
            Player player,
            Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation modifierId,
            double amount,
            AttributeModifier.Operation operation,
            boolean active) {
        AttributeInstance attributeInstance = player.getAttribute(attribute);
        if (attributeInstance == null) {
            return;
        }

        if (active) {
            attributeInstance.addOrUpdateTransientModifier(new AttributeModifier(modifierId, amount, operation));
            return;
        }

        boolean removed = attributeInstance.removeModifier(modifierId);
        if (removed && attribute.is(Attributes.MAX_HEALTH) && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static void removeTransientAttributeModifier(
            Player player,
            Holder<net.minecraft.world.entity.ai.attributes.Attribute> attribute,
            ResourceLocation modifierId) {
        AttributeInstance attributeInstance = player.getAttribute(attribute);
        if (attributeInstance == null) {
            return;
        }

        boolean removed = attributeInstance.removeModifier(modifierId);
        if (removed && attribute.is(Attributes.MAX_HEALTH) && player.getHealth() > player.getMaxHealth()) {
            player.setHealth(player.getMaxHealth());
        }
    }

    private static boolean hasFullCitrineSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.CITRINE_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.CITRINE_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.CITRINE_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.CITRINE_BOOTS.get());
    }

    private static boolean hasFullCobaltSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(ModItems.COBALT_HELMET.get())
                && player.getItemBySlot(EquipmentSlot.CHEST).is(ModItems.COBALT_CHESTPLATE.get())
                && player.getItemBySlot(EquipmentSlot.LEGS).is(ModItems.COBALT_LEGGINGS.get())
                && player.getItemBySlot(EquipmentSlot.FEET).is(ModItems.COBALT_BOOTS.get());
    }

    private static boolean hasFullIronSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET)
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE)
                && player.getItemBySlot(EquipmentSlot.LEGS).is(Items.IRON_LEGGINGS)
                && player.getItemBySlot(EquipmentSlot.FEET).is(Items.IRON_BOOTS);
    }

    private static boolean hasFullDiamondSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.DIAMOND_HELMET)
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.DIAMOND_CHESTPLATE)
                && player.getItemBySlot(EquipmentSlot.LEGS).is(Items.DIAMOND_LEGGINGS)
                && player.getItemBySlot(EquipmentSlot.FEET).is(Items.DIAMOND_BOOTS);
    }

    private static boolean hasFullNetheriteSet(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).is(Items.NETHERITE_HELMET)
                && player.getItemBySlot(EquipmentSlot.CHEST).is(Items.NETHERITE_CHESTPLATE)
                && player.getItemBySlot(EquipmentSlot.LEGS).is(Items.NETHERITE_LEGGINGS)
                && player.getItemBySlot(EquipmentSlot.FEET).is(Items.NETHERITE_BOOTS);
    }

    private static void handleNetheriteSetExplosionResistance(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !hasFullNetheriteSet(player)
                || !event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            return;
        }

        event.setAmount(event.getAmount() * NETHERITE_SET_EXPLOSION_DAMAGE_MULTIPLIER);
    }

    private static void handleDiamondSetFortune(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)
                || !hasFullDiamondSet(player)
                || event.isCanceled()) {
            return;
        }

        ItemStack tool = event.getTool();
        if (tool.isEmpty()) {
            return;
        }

        Holder<Enchantment> silkTouch = event.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);
        if (tool.getEnchantmentLevel(silkTouch) > 0) {
            return;
        }

        Holder<Enchantment> fortune = event.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FORTUNE);
        if (tool.getEnchantmentLevel(fortune) >= DIAMOND_SET_FORTUNE_LEVEL) {
            return;
        }

        ItemStack simulatedTool = tool.copy();
        simulatedTool.enchant(fortune, DIAMOND_SET_FORTUNE_LEVEL);

        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        BlockEntity blockEntity = event.getBlockEntity();
        List<ItemStack> recalculatedDrops = Block.getDrops(state, event.getLevel(), pos, blockEntity, player, simulatedTool);

        event.getDrops().clear();
        event.getDrops().addAll(createDropEntities(event.getLevel(), pos, recalculatedDrops));
        event.setDroppedExperience(EnchantmentHelper.processBlockExperience(
                event.getLevel(),
                simulatedTool,
                state.getExpDrop(event.getLevel(), pos, blockEntity, player, simulatedTool)));
    }

    private static List<ItemEntity> createDropEntities(ServerLevel level, BlockPos pos, List<ItemStack> drops) {
        List<ItemEntity> entities = new ArrayList<>();
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS)) {
            return entities;
        }

        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }

            double x = (double)pos.getX() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D);
            double y = (double)pos.getY() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D);
            double z = (double)pos.getZ() + 0.5D + Mth.nextDouble(level.random, -0.25D, 0.25D);
            ItemEntity entity = new ItemEntity(level, x, y, z, drop);
            entity.setDefaultPickUpDelay();
            entities.add(entity);
        }

        return entities;
    }
}
