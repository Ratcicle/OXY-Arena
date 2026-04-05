package com.example.oxyarena;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    public static final ModConfigSpec COMMON_SPEC = new ModConfigSpec.Builder().build();

    private static final ModConfigSpec.Builder CLIENT_BUILDER = new ModConfigSpec.Builder();
    private static final Client CLIENT = new Client(CLIENT_BUILDER);
    public static final ModConfigSpec CLIENT_SPEC = CLIENT_BUILDER.build();

    private Config() {
    }

    public static boolean immersiveHudEnabled() {
        return CLIENT.immersiveHudEnabled.get();
    }

    public static int immersiveHudVisibleTicks() {
        return CLIENT.immersiveHudVisibleTicks.get();
    }

    public static int immersiveHudHotbarVisibleTicks() {
        return CLIENT.immersiveHudHotbarVisibleTicks.get();
    }

    public static int immersiveHudHealthVisibleTicks() {
        return CLIENT.immersiveHudHealthVisibleTicks.get();
    }

    public static boolean immersiveHudFadeEnabled() {
        return CLIENT.immersiveHudFadeEnabled.get();
    }

    public static int immersiveHudFadeTicks() {
        return CLIENT.immersiveHudFadeTicks.get();
    }

    public static boolean immersiveHudCrosshairOnBlocks() {
        return CLIENT.immersiveHudCrosshairOnBlocks.get();
    }

    public static boolean immersiveHudCrosshairOnEntities() {
        return CLIENT.immersiveHudCrosshairOnEntities.get();
    }

    public static boolean immersiveHudShowSelectedItemName() {
        return CLIENT.immersiveHudShowSelectedItemName.get();
    }

    private static final class Client {
        private final ModConfigSpec.BooleanValue immersiveHudEnabled;
        private final ModConfigSpec.IntValue immersiveHudVisibleTicks;
        private final ModConfigSpec.IntValue immersiveHudHotbarVisibleTicks;
        private final ModConfigSpec.IntValue immersiveHudHealthVisibleTicks;
        private final ModConfigSpec.BooleanValue immersiveHudFadeEnabled;
        private final ModConfigSpec.IntValue immersiveHudFadeTicks;
        private final ModConfigSpec.BooleanValue immersiveHudCrosshairOnBlocks;
        private final ModConfigSpec.BooleanValue immersiveHudCrosshairOnEntities;
        private final ModConfigSpec.BooleanValue immersiveHudShowSelectedItemName;

        private Client(ModConfigSpec.Builder builder) {
            builder.push("immersive_hud");

            immersiveHudEnabled = builder.comment("Hide the tracked HUD elements by default and reveal them when they change.")
                    .translation("config.oxyarena.immersive_hud.enabled")
                    .define("enabled", true);

            immersiveHudVisibleTicks = builder.comment("How long each HUD element stays visible after changing, in client ticks.")
                    .translation("config.oxyarena.immersive_hud.visible_ticks")
                    .defineInRange("visible_ticks", 60, 5, 400);

            immersiveHudHotbarVisibleTicks = builder.comment("How long the hotbar stays visible after changing, in client ticks.")
                    .translation("config.oxyarena.immersive_hud.hotbar_visible_ticks")
                    .defineInRange("hotbar_visible_ticks", 60, 5, 400);

            immersiveHudHealthVisibleTicks = builder.comment("How long the health bar stays visible after changing, in client ticks.")
                    .translation("config.oxyarena.immersive_hud.health_visible_ticks")
                    .defineInRange("health_visible_ticks", 60, 5, 400);

            immersiveHudFadeEnabled = builder.comment("Enable fade-in and fade-out for the immersive HUD.")
                    .translation("config.oxyarena.immersive_hud.fade_enabled")
                    .define("fade_enabled", true);

            immersiveHudFadeTicks = builder.comment("How long the fade-in and fade-out lasts for each HUD element, in client ticks.")
                    .translation("config.oxyarena.immersive_hud.fade_ticks")
                    .defineInRange("fade_ticks", 10, 0, 200);

            immersiveHudCrosshairOnBlocks = builder.comment("Show the crosshair while looking at a block within interaction range.")
                    .translation("config.oxyarena.immersive_hud.crosshair_on_blocks")
                    .define("crosshair_on_blocks", true);

            immersiveHudCrosshairOnEntities = builder.comment("Show the crosshair while looking at an entity within interaction range.")
                    .translation("config.oxyarena.immersive_hud.crosshair_on_entities")
                    .define("crosshair_on_entities", true);

            immersiveHudShowSelectedItemName = builder.comment("Show the selected item name together with the hotbar.")
                    .translation("config.oxyarena.immersive_hud.show_selected_item_name")
                    .define("show_selected_item_name", false);

            builder.pop();
        }
    }
}
