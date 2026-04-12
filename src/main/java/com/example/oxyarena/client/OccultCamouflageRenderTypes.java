package com.example.oxyarena.client;

import java.io.IOException;
import java.util.Objects;

import org.jetbrains.annotations.Nullable;

import com.example.oxyarena.OXYArena;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;

import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class OccultCamouflageRenderTypes {
    public static final ResourceLocation MASK_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "textures/entity/occult_mask.png");
    public static final ResourceLocation NOISE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            OXYArena.MODID,
            "textures/entity/occult_noise.png");

    @Nullable
    private static ShaderInstance occultInteriorShader;
    @Nullable
    private static ShaderInstance occultEdgeShader;
    @Nullable
    private static ShaderInstance occultDistortionShader;

    private static final RenderStateShard.ShaderStateShard OCCULT_INTERIOR_SHADER_STATE =
            new RenderStateShard.ShaderStateShard(OccultCamouflageRenderTypes::getOccultInteriorShader);
    private static final RenderStateShard.ShaderStateShard OCCULT_EDGE_SHADER_STATE =
            new RenderStateShard.ShaderStateShard(OccultCamouflageRenderTypes::getOccultEdgeShader);
    private static final RenderStateShard.ShaderStateShard OCCULT_DISTORTION_SHADER_STATE =
            new RenderStateShard.ShaderStateShard(OccultCamouflageRenderTypes::getOccultDistortionShader);

    private static final RenderType OCCULT_INTERIOR = RenderType.create(
            "occult_interior",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(OCCULT_INTERIOR_SHADER_STATE)
                    .setTextureState(new RenderStateShard.TextureStateShard(MASK_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(true));
    private static final RenderType OCCULT_EDGE = RenderType.create(
            "occult_edge",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(OCCULT_EDGE_SHADER_STATE)
                    .setTextureState(new RenderStateShard.TextureStateShard(MASK_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true));
    private static final RenderType OCCULT_DISTORTION = RenderType.create(
            "occult_distortion",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            true,
            true,
            RenderType.CompositeState.builder()
                    .setShaderState(OCCULT_DISTORTION_SHADER_STATE)
                    .setTextureState(new RenderStateShard.TextureStateShard(NOISE_TEXTURE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(true));

    private OccultCamouflageRenderTypes() {
    }

    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "occult_interior"),
                        DefaultVertexFormat.NEW_ENTITY),
                shader -> occultInteriorShader = shader);
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "occult_edge"),
                        DefaultVertexFormat.NEW_ENTITY),
                shader -> occultEdgeShader = shader);
        event.registerShader(
                new ShaderInstance(
                        event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "occult_distortion"),
                        DefaultVertexFormat.NEW_ENTITY),
                shader -> occultDistortionShader = shader);
    }

    public static boolean areShadersReady() {
        return occultInteriorShader != null && occultEdgeShader != null && occultDistortionShader != null;
    }

    public static RenderType interior() {
        return OCCULT_INTERIOR;
    }

    public static RenderType edge() {
        return OCCULT_EDGE;
    }

    public static RenderType distortion() {
        return OCCULT_DISTORTION;
    }

    private static ShaderInstance getOccultInteriorShader() {
        return Objects.requireNonNull(occultInteriorShader, "Occult interior shader is not loaded");
    }

    private static ShaderInstance getOccultEdgeShader() {
        return Objects.requireNonNull(occultEdgeShader, "Occult edge shader is not loaded");
    }

    private static ShaderInstance getOccultDistortionShader() {
        return Objects.requireNonNull(occultDistortionShader, "Occult distortion shader is not loaded");
    }
}
