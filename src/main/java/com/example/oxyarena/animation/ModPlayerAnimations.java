package com.example.oxyarena.animation;

import com.example.oxyarena.OXYArena;

import net.minecraft.resources.ResourceLocation;

public final class ModPlayerAnimations {
    public static final String PLAYER_ANIMATOR_RESOURCE_DIRECTORY = "player_animations";
    public static final ResourceLocation GHOST_SABER_PHANTOM_SABER_SLASH = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "phantom_saber_slash");
    public static final ResourceLocation AMETRA_WARPED_GLAIVE_RIFT_CUT = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "rift_cut");
    public static final ResourceLocation AMETRA_WARPED_GLAIVE_CIRCULAR_SLASH = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "circular_slash");
    public static final ResourceLocation PLAYER_MANTLE_CLIMB = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "climb");
    public static final ResourceLocation PLAYER_SLIDE = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "slide");
    public static final ResourceLocation PLAYER_CRAWL = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "crawl");

    private ModPlayerAnimations() {
    }
}
