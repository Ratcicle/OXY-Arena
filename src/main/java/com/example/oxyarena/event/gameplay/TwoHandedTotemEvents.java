package com.example.oxyarena.event.gameplay;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.EffectCures;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

public final class TwoHandedTotemEvents {
    private TwoHandedTotemEvents() {
    }

    public static boolean tryUseInventoryTotem(LivingDeathEvent event, ServerPlayer player) {
        if (event.isCanceled()
                || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !WeaponAttributesDataManager.isTwoHanded(player.getMainHandItem())) {
            return false;
        }

        ItemStack totem = findInventoryTotem(player);
        if (totem.isEmpty()) {
            return false;
        }

        ItemStack usedTotem = totem.copy();
        totem.shrink(1);
        player.getInventory().setChanged();

        player.awardStat(Stats.ITEM_USED.get(Items.TOTEM_OF_UNDYING), 1);
        CriteriaTriggers.USED_TOTEM.trigger(player, usedTotem);
        player.gameEvent(GameEvent.ITEM_INTERACT_FINISH);
        player.setHealth(1.0F);
        player.removeEffectsCuredBy(EffectCures.PROTECTED_BY_TOTEM);
        player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 900, 1));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 1));
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 800, 0));
        player.level().broadcastEntityEvent(player, (byte)35);
        event.setCanceled(true);
        return true;
    }

    private static ItemStack findInventoryTotem(ServerPlayer player) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(Items.TOTEM_OF_UNDYING)) {
                return stack;
            }
        }

        return ItemStack.EMPTY;
    }
}
