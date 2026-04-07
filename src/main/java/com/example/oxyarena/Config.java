package com.example.oxyarena;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class Config {
    private static final ModConfigSpec.Builder COMMON_BUILDER = new ModConfigSpec.Builder();
    private static final Common COMMON = new Common(COMMON_BUILDER);
    public static final ModConfigSpec COMMON_SPEC = COMMON_BUILDER.build();

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

    public static boolean pickupNotifierEnabled() {
        return CLIENT.pickupNotifierEnabled.get();
    }

    public static int pickupNotifierVisibleTicks() {
        return CLIENT.pickupNotifierVisibleTicks.get();
    }

    public static int pickupNotifierFadeTicks() {
        return CLIENT.pickupNotifierFadeTicks.get();
    }

    public static int pickupNotifierMaxEntries() {
        return CLIENT.pickupNotifierMaxEntries.get();
    }

    public static boolean inventoryQolSortingEnabled() {
        return CLIENT.inventoryQolSortingEnabled.get();
    }

    public static boolean inventoryQolWheelMoveEnabled() {
        return CLIENT.inventoryQolWheelMoveEnabled.get();
    }

    public static boolean inventoryQolMouseDragEnabled() {
        return CLIENT.inventoryQolMouseDragEnabled.get();
    }

    public static boolean pingEnabled() {
        return CLIENT.pingEnabled.get();
    }

    public static int pingDurationSeconds() {
        return CLIENT.pingDurationSeconds.get();
    }

    public static double pingVolume() {
        return CLIENT.pingVolume.get();
    }

    public static double pingMaxDistance() {
        return CLIENT.pingMaxDistance.get();
    }

    public static double pingRaycastDistance() {
        return CLIENT.pingRaycastDistance.get();
    }

    public static boolean pingShowAuthorName() {
        return CLIENT.pingShowAuthorName.get();
    }

    public static boolean pingShowDistance() {
        return CLIENT.pingShowDistance.get();
    }

    public static boolean rightClickHarvestEnabled() {
        return COMMON.rightClickHarvestEnabled.get();
    }

    public static boolean ametraOreGenerationEnabled() {
        return COMMON.ametraOreGenerationEnabled.get();
    }

    public static boolean fallingTreeEnabled() {
        return COMMON.fallingTreeEnabled.get();
    }

    public static int fallingTreeMaxLogs() {
        return COMMON.fallingTreeMaxLogs.get();
    }

    public static int fallingTreeBreakIntervalTicks() {
        return COMMON.fallingTreeBreakIntervalTicks.get();
    }

    public static boolean fallingTreeBranchDetectionEnabled() {
        return COMMON.fallingTreeBranchDetectionEnabled.get();
    }

    public static int fallingTreeBranchLeafRadius() {
        return COMMON.fallingTreeBranchLeafRadius.get();
    }

    public static int fallingTreeBranchMaxReach() {
        return COMMON.fallingTreeBranchMaxReach.get();
    }

    public static int ametraOrePrimaryCount() {
        return COMMON.ametraOrePrimaryCount.get();
    }

    public static int ametraOreMediumCount() {
        return COMMON.ametraOreMediumCount.get();
    }

    public static int ametraOreBuriedCount() {
        return COMMON.ametraOreBuriedCount.get();
    }

    public static int ametraOreMinY() {
        return COMMON.ametraOreMinY.get();
    }

    public static int ametraOreMaxY() {
        return COMMON.ametraOreMaxY.get();
    }

    public static int ametraOreDeepMaxY() {
        return COMMON.ametraOreDeepMaxY.get();
    }

    public static int ametraOreVeinSize() {
        return COMMON.ametraOreVeinSize.get();
    }

    public static double ametraOreExposedDiscardChance() {
        return COMMON.ametraOreExposedDiscardChance.get();
    }

    public static double ametraOreBuriedDiscardChance() {
        return COMMON.ametraOreBuriedDiscardChance.get();
    }

    private static final class Common {
        private final ModConfigSpec.BooleanValue rightClickHarvestEnabled;
        private final ModConfigSpec.BooleanValue fallingTreeEnabled;
        private final ModConfigSpec.IntValue fallingTreeMaxLogs;
        private final ModConfigSpec.IntValue fallingTreeBreakIntervalTicks;
        private final ModConfigSpec.BooleanValue fallingTreeBranchDetectionEnabled;
        private final ModConfigSpec.IntValue fallingTreeBranchLeafRadius;
        private final ModConfigSpec.IntValue fallingTreeBranchMaxReach;
        private final ModConfigSpec.BooleanValue ametraOreGenerationEnabled;
        private final ModConfigSpec.IntValue ametraOrePrimaryCount;
        private final ModConfigSpec.IntValue ametraOreMediumCount;
        private final ModConfigSpec.IntValue ametraOreBuriedCount;
        private final ModConfigSpec.IntValue ametraOreMinY;
        private final ModConfigSpec.IntValue ametraOreMaxY;
        private final ModConfigSpec.IntValue ametraOreDeepMaxY;
        private final ModConfigSpec.IntValue ametraOreVeinSize;
        private final ModConfigSpec.DoubleValue ametraOreExposedDiscardChance;
        private final ModConfigSpec.DoubleValue ametraOreBuriedDiscardChance;

        private Common(ModConfigSpec.Builder builder) {
            builder.push("right_click_harvest");

            rightClickHarvestEnabled = builder.comment("Allow mature crops to be harvested with right-click and replanted automatically.")
                    .translation("config.oxyarena.right_click_harvest.enabled")
                    .define("enabled", true);

            builder.pop();

            builder.push("falling_tree");

            fallingTreeEnabled = builder.comment("Break the whole tree when a supported overworld log is chopped with an axe.")
                    .translation("config.oxyarena.falling_tree.enabled")
                    .define("enabled", true);

            fallingTreeMaxLogs = builder.comment("Maximum number of connected logs that can be felled at once before the action aborts.")
                    .translation("config.oxyarena.falling_tree.max_logs")
                    .defineInRange("max_logs", 128, 1, 512);

            fallingTreeBreakIntervalTicks = builder.comment("Delay in ticks between each extra log broken by the falling tree effect.")
                    .translation("config.oxyarena.falling_tree.break_interval_ticks")
                    .defineInRange("break_interval_ticks", 5, 1, 20);

            fallingTreeBranchDetectionEnabled = builder.comment("Allow short natural branches near the canopy to be felled together with the main trunk.")
                    .translation("config.oxyarena.falling_tree.branch_detection_enabled")
                    .define("branch_detection_enabled", true);

            fallingTreeBranchLeafRadius = builder.comment("How far branch logs can search for nearby leaves to prove they belong to the tree.")
                    .translation("config.oxyarena.falling_tree.branch_leaf_radius")
                    .defineInRange("branch_leaf_radius", 2, 1, 6);

            fallingTreeBranchMaxReach = builder.comment("Maximum amount of short branch propagation away from the canopy trunk mass.")
                    .translation("config.oxyarena.falling_tree.branch_max_reach")
                    .defineInRange("branch_max_reach", 4, 1, 12);

            builder.pop();

            builder.push("ametra_ore_worldgen");

            ametraOreGenerationEnabled = builder.comment("Enable natural Ametra ore generation in new chunks.")
                    .translation("config.oxyarena.ametra_ore_worldgen.enabled")
                    .define("enabled", true);

            ametraOrePrimaryCount = builder.comment("Primary diamond-like Ametra ore attempts per chunk.")
                    .translation("config.oxyarena.ametra_ore_worldgen.primary_count")
                    .defineInRange("primary_count", 7, 0, 64);

            ametraOreMediumCount = builder.comment("Extra deep Ametra ore attempts per chunk.")
                    .translation("config.oxyarena.ametra_ore_worldgen.medium_count")
                    .defineInRange("medium_count", 2, 0, 64);

            ametraOreBuriedCount = builder.comment("Buried Ametra ore attempts per chunk.")
                    .translation("config.oxyarena.ametra_ore_worldgen.buried_count")
                    .defineInRange("buried_count", 4, 0, 64);

            ametraOreMinY = builder.comment("Minimum Y level for Ametra generation.")
                    .translation("config.oxyarena.ametra_ore_worldgen.min_y")
                    .defineInRange("min_y", -64, -64, 320);

            ametraOreMaxY = builder.comment("Maximum Y level for primary and buried Ametra generation.")
                    .translation("config.oxyarena.ametra_ore_worldgen.max_y")
                    .defineInRange("max_y", 15, -64, 320);

            ametraOreDeepMaxY = builder.comment("Maximum Y level for the extra deep Ametra generation.")
                    .translation("config.oxyarena.ametra_ore_worldgen.deep_max_y")
                    .defineInRange("deep_max_y", -4, -64, 320);

            ametraOreVeinSize = builder.comment("Maximum vein size for Ametra ore.")
                    .translation("config.oxyarena.ametra_ore_worldgen.vein_size")
                    .defineInRange("vein_size", 2, 1, 16);

            ametraOreExposedDiscardChance = builder.comment("Discard chance for primary Ametra ore exposed to air.")
                    .translation("config.oxyarena.ametra_ore_worldgen.exposed_discard_chance")
                    .defineInRange("exposed_discard_chance", 0.5D, 0.0D, 1.0D);

            ametraOreBuriedDiscardChance = builder.comment("Discard chance for buried Ametra ore exposed to air.")
                    .translation("config.oxyarena.ametra_ore_worldgen.buried_discard_chance")
                    .defineInRange("buried_discard_chance", 1.0D, 0.0D, 1.0D);

            builder.pop();
        }
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
        private final ModConfigSpec.BooleanValue pickupNotifierEnabled;
        private final ModConfigSpec.IntValue pickupNotifierVisibleTicks;
        private final ModConfigSpec.IntValue pickupNotifierFadeTicks;
        private final ModConfigSpec.IntValue pickupNotifierMaxEntries;
        private final ModConfigSpec.BooleanValue inventoryQolSortingEnabled;
        private final ModConfigSpec.BooleanValue inventoryQolWheelMoveEnabled;
        private final ModConfigSpec.BooleanValue inventoryQolMouseDragEnabled;
        private final ModConfigSpec.BooleanValue pingEnabled;
        private final ModConfigSpec.IntValue pingDurationSeconds;
        private final ModConfigSpec.DoubleValue pingVolume;
        private final ModConfigSpec.DoubleValue pingMaxDistance;
        private final ModConfigSpec.DoubleValue pingRaycastDistance;
        private final ModConfigSpec.BooleanValue pingShowAuthorName;
        private final ModConfigSpec.BooleanValue pingShowDistance;

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

            builder.push("pickup_notifier");

            pickupNotifierEnabled = builder.comment("Show a brief pickup notification with item name and amount.")
                    .translation("config.oxyarena.pickup_notifier.enabled")
                    .define("enabled", true);

            pickupNotifierVisibleTicks = builder.comment("How long pickup notifications stay visible, in client ticks.")
                    .translation("config.oxyarena.pickup_notifier.visible_ticks")
                    .defineInRange("visible_ticks", 60, 5, 400);

            pickupNotifierFadeTicks = builder.comment("How long pickup notifications fade in and out, in client ticks.")
                    .translation("config.oxyarena.pickup_notifier.fade_ticks")
                    .defineInRange("fade_ticks", 10, 0, 200);

            pickupNotifierMaxEntries = builder.comment("Maximum number of pickup notifications rendered at once.")
                    .translation("config.oxyarena.pickup_notifier.max_entries")
                    .defineInRange("max_entries", 4, 1, 10);

            builder.pop();

            builder.push("inventory_qol");

            inventoryQolSortingEnabled = builder.comment("Enable middle-click sorting for supported inventories and containers.")
                    .translation("config.oxyarena.inventory_qol.sorting_enabled")
                    .define("sorting_enabled", true);

            inventoryQolWheelMoveEnabled = builder.comment("Enable mouse wheel push/pull item movement between inventories.")
                    .translation("config.oxyarena.inventory_qol.wheel_move_enabled")
                    .define("wheel_move_enabled", true);

            inventoryQolMouseDragEnabled = builder.comment("Enable drag-based inventory tweaks such as RMB distribution and shift-drag quick move.")
                    .translation("config.oxyarena.inventory_qol.mouse_drag_enabled")
                    .define("mouse_drag_enabled", true);

            builder.pop();

            builder.push("ping");

            pingEnabled = builder.comment("Enable middle-mouse location pings in the world.")
                    .translation("config.oxyarena.ping.enabled")
                    .define("enabled", true);

            pingDurationSeconds = builder.comment("How long multiplayer pings stay visible, in seconds.")
                    .translation("config.oxyarena.ping.duration_seconds")
                    .defineInRange("duration_seconds", 10, 1, 60);

            pingVolume = builder.comment("Client-side sound volume for received pings.")
                    .translation("config.oxyarena.ping.volume")
                    .defineInRange("volume", 0.8D, 0.0D, 2.0D);

            pingMaxDistance = builder.comment("Maximum distance at which pings are rendered on the client.")
                    .translation("config.oxyarena.ping.max_distance")
                    .defineInRange("max_distance", 128.0D, 8.0D, 512.0D);

            pingRaycastDistance = builder.comment("Maximum distance used by the ping target raycast.")
                    .translation("config.oxyarena.ping.raycast_distance")
                    .defineInRange("raycast_distance", 218.0D, 8.0D, 512.0D);

            pingShowAuthorName = builder.comment("Show the ping author's name in the HUD overlay.")
                    .translation("config.oxyarena.ping.show_author_name")
                    .define("show_author_name", true);

            pingShowDistance = builder.comment("Show the ping distance in the HUD overlay.")
                    .translation("config.oxyarena.ping.show_distance")
                    .define("show_distance", true);

            builder.pop();
        }
    }
}
