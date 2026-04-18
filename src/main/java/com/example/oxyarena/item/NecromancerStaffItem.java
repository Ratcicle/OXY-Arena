package com.example.oxyarena.item;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import com.example.oxyarena.event.gameplay.NecromancerStaffEvents;
import com.example.oxyarena.registry.ModItems;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

public class NecromancerStaffItem extends SwordItem {
    public static final int MAX_SOULS = 8;
    public static final int MAX_ACTIVE_SUMMONS = 3;
    public static final int SUMMON_COOLDOWN_TICKS = 20;

    private static final String SOULS_TAG = "Souls";
    private static final String SELECTED_SOUL_INDEX_TAG = "SelectedSoulIndex";
    private static final String DISPLAY_NAME_TAG = "DisplayName";

    public NecromancerStaffItem(Tier tier, Properties properties) {
        super(tier, properties);
    }

    public static boolean isStaff(ItemStack stack) {
        return stack.is(ModItems.NECROMANCER_STAFF.get());
    }

    public static int getSoulCount(ItemStack stack) {
        return getSouls(stack).size();
    }

    public static Optional<String> getSelectedSoulName(ItemStack stack) {
        ListTag souls = getSouls(stack);
        if (souls.isEmpty()) {
            return Optional.empty();
        }

        int selectedIndex = getSelectedIndex(stack, souls.size());
        return Optional.of(souls.getCompound(selectedIndex).getString(DISPLAY_NAME_TAG));
    }

    public static boolean addSoul(ItemStack stack, CompoundTag soulTag) {
        if (!isStaff(stack) || soulTag.isEmpty()) {
            return false;
        }

        AtomicBoolean added = new AtomicBoolean(false);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            ListTag souls = copySouls(tag);
            if (souls.size() >= MAX_SOULS) {
                return;
            }

            souls.add(soulTag.copy());
            tag.put(SOULS_TAG, souls);
            tag.putInt(SELECTED_SOUL_INDEX_TAG, clampSelectedIndex(tag.getInt(SELECTED_SOUL_INDEX_TAG), souls.size()));
            added.set(true);
        });
        return added.get();
    }

    public static Optional<CompoundTag> removeSelectedSoul(ItemStack stack) {
        if (!isStaff(stack)) {
            return Optional.empty();
        }

        AtomicReference<CompoundTag> removedSoul = new AtomicReference<>();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            ListTag souls = copySouls(tag);
            if (souls.isEmpty()) {
                return;
            }

            int selectedIndex = clampSelectedIndex(tag.getInt(SELECTED_SOUL_INDEX_TAG), souls.size());
            removedSoul.set(souls.getCompound(selectedIndex).copy());
            souls.remove(selectedIndex);
            if (souls.isEmpty()) {
                tag.remove(SOULS_TAG);
                tag.putInt(SELECTED_SOUL_INDEX_TAG, 0);
            } else {
                tag.put(SOULS_TAG, souls);
                tag.putInt(SELECTED_SOUL_INDEX_TAG, clampSelectedIndex(selectedIndex, souls.size()));
            }
        });
        return Optional.ofNullable(removedSoul.get());
    }

    public static Optional<CompoundTag> getSelectedSoul(ItemStack stack) {
        if (!isStaff(stack)) {
            return Optional.empty();
        }

        ListTag souls = getSouls(stack);
        if (souls.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(souls.getCompound(getSelectedIndex(stack, souls.size())).copy());
    }

    public static void cycleSelectedSoul(ItemStack stack, int direction) {
        if (!isStaff(stack) || direction == 0) {
            return;
        }

        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            ListTag souls = copySouls(tag);
            if (souls.isEmpty()) {
                tag.putInt(SELECTED_SOUL_INDEX_TAG, 0);
                return;
            }

            int selectedIndex = clampSelectedIndex(tag.getInt(SELECTED_SOUL_INDEX_TAG), souls.size());
            int nextIndex = Math.floorMod(selectedIndex + direction, souls.size());
            tag.put(SOULS_TAG, souls);
            tag.putInt(SELECTED_SOUL_INDEX_TAG, nextIndex);
        });
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.isShiftKeyDown()) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide && !NecromancerStaffEvents.summonSelectedSoul(player, stack)) {
            return InteractionResultHolder.fail(stack);
        }

        player.swing(hand, true);
        player.getCooldowns().addCooldown(this, SUMMON_COOLDOWN_TICKS);
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            List<Component> tooltipComponents,
            TooltipFlag tooltipFlag) {
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.necromancer_staff.capture")
                .withStyle(ChatFormatting.DARK_PURPLE));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.necromancer_staff.summon")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
        tooltipComponents.add(Component.translatable("tooltip.oxyarena.necromancer_staff.storage", getSoulCount(stack), MAX_SOULS)
                .withStyle(ChatFormatting.GRAY));
    }

    private static ListTag getSouls(ItemStack stack) {
        if (!isStaff(stack)) {
            return new ListTag();
        }

        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getList(SOULS_TAG, Tag.TAG_COMPOUND);
    }

    private static ListTag copySouls(CompoundTag tag) {
        return (ListTag)tag.getList(SOULS_TAG, Tag.TAG_COMPOUND).copy();
    }

    private static int getSelectedIndex(ItemStack stack, int soulCount) {
        int selectedIndex = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY)
                .copyTag()
                .getInt(SELECTED_SOUL_INDEX_TAG);
        return clampSelectedIndex(selectedIndex, soulCount);
    }

    private static int clampSelectedIndex(int selectedIndex, int soulCount) {
        if (soulCount <= 0) {
            return 0;
        }

        return Math.max(0, Math.min(selectedIndex, soulCount - 1));
    }
}
