package com.example.oxyarena.client;

import com.example.oxyarena.Config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.common.NeoForge;

public final class ImmersiveHudController {
    private static final float FULL_ALPHA = 1.0F;
    private static final float HIDDEN_ALPHA = 0.0F;

    private static boolean registered;

    private int hotbarTicks;
    private int healthTicks;
    private int foodTicks;
    private int xpTicks;
    private int airTicks;
    private int armorTicks;

    private boolean snapshotInitialized;
    private float lastHealth = Float.NaN;
    private float lastAbsorption = Float.NaN;
    private int lastFoodLevel = Integer.MIN_VALUE;
    private float lastXpProgress = Float.NaN;
    private int lastXpLevel = Integer.MIN_VALUE;
    private int lastSelectedSlot = Integer.MIN_VALUE;
    private int lastAirSupply = Integer.MIN_VALUE;
    private int lastArmorValue = Integer.MIN_VALUE;
    private Object lastLevelIdentity;

    public static void register() {
        if (registered) {
            return;
        }

        ImmersiveHudController controller = new ImmersiveHudController();
        NeoForge.EVENT_BUS.addListener(controller::onClientTickPost);
        NeoForge.EVENT_BUS.addListener(controller::onRenderGuiLayerPre);
        NeoForge.EVENT_BUS.addListener(controller::onRenderGuiLayerPost);
        registered = true;
    }

    private void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (player == null) {
            resetSnapshot();
            return;
        }

        if (!Config.immersiveHudEnabled()) {
            resetSnapshot();
            return;
        }

        if (minecraft.isPaused()) {
            return;
        }

        tickTimersDown();
        updateSnapshot(player);
    }

    private void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!shouldManageHud(minecraft)) {
            return;
        }

        if (!isManagedLayer(event.getName())) {
            return;
        }

        float alpha = getLayerAlpha(event.getName());
        if (alpha <= HIDDEN_ALPHA) {
            event.setCanceled(true);
            return;
        }

        event.getGuiGraphics().setColor(FULL_ALPHA, FULL_ALPHA, FULL_ALPHA, alpha);
    }

    private void onRenderGuiLayerPost(RenderGuiLayerEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!shouldManageHud(minecraft)) {
            return;
        }

        if (isManagedLayer(event.getName())) {
            event.getGuiGraphics().setColor(FULL_ALPHA, FULL_ALPHA, FULL_ALPHA, FULL_ALPHA);
        }
    }

    private boolean shouldManageHud(Minecraft minecraft) {
        return minecraft.player != null
                && Config.immersiveHudEnabled()
                && minecraft.screen == null
                && !minecraft.options.hideGui
                && !minecraft.getDebugOverlay().showDebugScreen()
                && !minecraft.player.isSpectator();
    }

    private boolean isManagedLayer(ResourceLocation layerName) {
        if (layerName.equals(VanillaGuiLayers.CROSSHAIR)) {
            return true;
        }

        if (layerName.equals(VanillaGuiLayers.HOTBAR)) {
            return true;
        }

        if (layerName.equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            return true;
        }

        if (layerName.equals(VanillaGuiLayers.FOOD_LEVEL)) {
            return true;
        }

        if (layerName.equals(VanillaGuiLayers.EXPERIENCE_BAR) || layerName.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
            return true;
        }

        if (layerName.equals(VanillaGuiLayers.AIR_LEVEL)) {
            return true;
        }

        if (layerName.equals(VanillaGuiLayers.ARMOR_LEVEL)) {
            return true;
        }

        return layerName.equals(VanillaGuiLayers.SELECTED_ITEM_NAME);
    }

    private float getLayerAlpha(ResourceLocation layerName) {
        if (layerName.equals(VanillaGuiLayers.CROSSHAIR)) {
            return shouldShowCrosshair(Minecraft.getInstance()) ? FULL_ALPHA : HIDDEN_ALPHA;
        }

        if (layerName.equals(VanillaGuiLayers.HOTBAR)) {
            return getFadeAlpha(hotbarTicks, Config.immersiveHudHotbarVisibleTicks());
        }

        if (layerName.equals(VanillaGuiLayers.SELECTED_ITEM_NAME)) {
            if (!Config.immersiveHudShowSelectedItemName()) {
                return HIDDEN_ALPHA;
            }

            return getFadeAlpha(hotbarTicks, Config.immersiveHudHotbarVisibleTicks());
        }

        if (layerName.equals(VanillaGuiLayers.PLAYER_HEALTH)) {
            return getFadeAlpha(healthTicks, Config.immersiveHudHealthVisibleTicks());
        }

        if (layerName.equals(VanillaGuiLayers.FOOD_LEVEL)) {
            return getFadeAlpha(foodTicks, Config.immersiveHudVisibleTicks());
        }

        if (layerName.equals(VanillaGuiLayers.EXPERIENCE_BAR) || layerName.equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) {
            return getFadeAlpha(xpTicks, Config.immersiveHudVisibleTicks());
        }

        if (layerName.equals(VanillaGuiLayers.AIR_LEVEL)) {
            return getFadeAlpha(airTicks, Config.immersiveHudVisibleTicks());
        }

        if (layerName.equals(VanillaGuiLayers.ARMOR_LEVEL)) {
            return getFadeAlpha(armorTicks, Config.immersiveHudVisibleTicks());
        }

        return HIDDEN_ALPHA;
    }

    private boolean shouldShowCrosshair(Minecraft minecraft) {
        if (minecraft.player == null || minecraft.level == null || minecraft.hitResult == null) {
            return false;
        }

        if (minecraft.hitResult.getType() == HitResult.Type.ENTITY) {
            return Config.immersiveHudCrosshairOnEntities();
        }

        if (minecraft.hitResult.getType() == HitResult.Type.BLOCK) {
            if (!Config.immersiveHudCrosshairOnBlocks()) {
                return false;
            }

            BlockHitResult blockHitResult = (BlockHitResult)minecraft.hitResult;
            return !minecraft.level.getBlockState(blockHitResult.getBlockPos()).isAir();
        }

        return false;
    }

    private float getFadeAlpha(int remainingTicks, int configuredShowTicks) {
        if (remainingTicks <= 0) {
            return HIDDEN_ALPHA;
        }

        int showTicks = getConfiguredShowTicks(configuredShowTicks);
        if (!Config.immersiveHudFadeEnabled()) {
            return FULL_ALPHA;
        }

        int fadeTicks = getConfiguredFadeTicks(showTicks);
        if (fadeTicks <= 0) {
            return FULL_ALPHA;
        }

        int elapsedTicks = showTicks - remainingTicks;
        float fadeInAlpha = Math.min(FULL_ALPHA, (elapsedTicks + 1.0F) / fadeTicks);
        float fadeOutAlpha = Math.min(FULL_ALPHA, remainingTicks / (float)fadeTicks);
        return Math.min(fadeInAlpha, fadeOutAlpha);
    }

    private void tickTimersDown() {
        hotbarTicks = Math.max(0, hotbarTicks - 1);
        healthTicks = Math.max(0, healthTicks - 1);
        foodTicks = Math.max(0, foodTicks - 1);
        xpTicks = Math.max(0, xpTicks - 1);
        airTicks = Math.max(0, airTicks - 1);
        armorTicks = Math.max(0, armorTicks - 1);
    }

    private void updateSnapshot(LocalPlayer player) {
        Object currentLevelIdentity = player.level();
        float currentHealth = player.getHealth();
        float currentAbsorption = player.getAbsorptionAmount();
        int currentFoodLevel = player.getFoodData().getFoodLevel();
        float currentXpProgress = player.experienceProgress;
        int currentXpLevel = player.experienceLevel;
        int currentSelectedSlot = player.getInventory().selected;
        int currentAirSupply = player.getAirSupply();
        int currentArmorValue = player.getArmorValue();

        if (!snapshotInitialized || lastLevelIdentity != currentLevelIdentity) {
            snapshotInitialized = true;
            storeSnapshot(
                    currentLevelIdentity,
                    currentHealth,
                    currentAbsorption,
                    currentFoodLevel,
                    currentXpProgress,
                    currentXpLevel,
                    currentSelectedSlot,
                    currentAirSupply,
                    currentArmorValue);
            return;
        }

        if (Float.compare(lastHealth, currentHealth) != 0 || Float.compare(lastAbsorption, currentAbsorption) != 0) {
            healthTicks = refreshLayerTimer(healthTicks, Config.immersiveHudHealthVisibleTicks());
        }

        if (lastFoodLevel != currentFoodLevel) {
            foodTicks = refreshLayerTimer(foodTicks, Config.immersiveHudVisibleTicks());
        }

        if (Float.compare(lastXpProgress, currentXpProgress) != 0 || lastXpLevel != currentXpLevel) {
            xpTicks = refreshLayerTimer(xpTicks, Config.immersiveHudVisibleTicks());
        }

        if (lastSelectedSlot != currentSelectedSlot) {
            hotbarTicks = refreshLayerTimer(hotbarTicks, Config.immersiveHudHotbarVisibleTicks());
        }

        if (lastAirSupply != currentAirSupply) {
            airTicks = refreshLayerTimer(airTicks, Config.immersiveHudVisibleTicks());
        }

        if (lastArmorValue != currentArmorValue) {
            armorTicks = refreshLayerTimer(armorTicks, Config.immersiveHudVisibleTicks());
        }

        storeSnapshot(
                currentLevelIdentity,
                currentHealth,
                currentAbsorption,
                currentFoodLevel,
                currentXpProgress,
                currentXpLevel,
                currentSelectedSlot,
                currentAirSupply,
                currentArmorValue);
    }

    private void storeSnapshot(
            Object currentLevelIdentity,
            float currentHealth,
            float currentAbsorption,
            int currentFoodLevel,
            float currentXpProgress,
            int currentXpLevel,
            int currentSelectedSlot,
            int currentAirSupply,
            int currentArmorValue) {
        lastLevelIdentity = currentLevelIdentity;
        lastHealth = currentHealth;
        lastAbsorption = currentAbsorption;
        lastFoodLevel = currentFoodLevel;
        lastXpProgress = currentXpProgress;
        lastXpLevel = currentXpLevel;
        lastSelectedSlot = currentSelectedSlot;
        lastAirSupply = currentAirSupply;
        lastArmorValue = currentArmorValue;
    }

    private int refreshLayerTimer(int currentTicks, int configuredShowTicks) {
        int showTicks = getConfiguredShowTicks(configuredShowTicks);
        if (currentTicks <= 0) {
            return showTicks;
        }

        return Math.max(currentTicks, getVisibleExtensionTicks(showTicks));
    }

    private int getConfiguredShowTicks(int configuredShowTicks) {
        return Math.max(1, configuredShowTicks);
    }

    private int getConfiguredFadeTicks(int showTicks) {
        return Math.max(0, Math.min(Config.immersiveHudFadeTicks(), showTicks));
    }

    private int getVisibleExtensionTicks(int showTicks) {
        if (!Config.immersiveHudFadeEnabled()) {
            return showTicks;
        }

        int fadeTicks = getConfiguredFadeTicks(showTicks);
        return Math.max(1, showTicks - fadeTicks);
    }

    private void resetSnapshot() {
        hotbarTicks = 0;
        healthTicks = 0;
        foodTicks = 0;
        xpTicks = 0;
        airTicks = 0;
        armorTicks = 0;
        snapshotInitialized = false;
        lastHealth = Float.NaN;
        lastAbsorption = Float.NaN;
        lastFoodLevel = Integer.MIN_VALUE;
        lastXpProgress = Float.NaN;
        lastXpLevel = Integer.MIN_VALUE;
        lastSelectedSlot = Integer.MIN_VALUE;
        lastAirSupply = Integer.MIN_VALUE;
        lastArmorValue = Integer.MIN_VALUE;
        lastLevelIdentity = null;
    }
}
