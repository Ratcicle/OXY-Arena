package com.example.oxyarena;

import com.example.oxyarena.client.ImmersiveHudController;
import com.example.oxyarena.client.InventoryInteractionController;
import com.example.oxyarena.client.NecromancerStaffHudController;
import com.example.oxyarena.client.OccultCamouflageController;
import com.example.oxyarena.client.OccultCamouflageRenderTypes;
import com.example.oxyarena.client.CombatStatusHudController;
import com.example.oxyarena.client.PingLocationController;
import com.example.oxyarena.client.PickupNotifierController;
import com.example.oxyarena.client.PlayerMantleController;
import com.example.oxyarena.client.PlayerSlideController;
import com.example.oxyarena.client.AmetraXrayController;
import com.example.oxyarena.client.BridgingAssistController;
import com.example.oxyarena.client.ToolTooltipStatsController;
import com.example.oxyarena.client.animation.OxyPlayerAnimatorBridge;
import com.example.oxyarena.client.particle.NevoaBorderParticle;
import com.example.oxyarena.client.renderer.entity.AirdropCrateRenderer;
import com.example.oxyarena.client.renderer.entity.CloneThiefRenderer;
import com.example.oxyarena.client.renderer.entity.CitrineThrowingDaggerRenderer;
import com.example.oxyarena.client.renderer.entity.ElementalGauntletProjectileRenderer;
import com.example.oxyarena.client.renderer.entity.EruptionTntRenderer;
import com.example.oxyarena.client.renderer.entity.GrapplingHookRenderer;
import com.example.oxyarena.client.renderer.entity.GhostSaberEchoRenderer;
import com.example.oxyarena.client.renderer.entity.IncandescentThrowingDaggerRenderer;
import com.example.oxyarena.client.renderer.entity.SpectralMarkRenderer;
import com.example.oxyarena.client.renderer.entity.StormChargeRenderer;
import com.example.oxyarena.client.renderer.entity.ThrownZeusLightningRenderer;
import com.example.oxyarena.client.renderer.entity.ZenithOrbitSwordRenderer;
import com.example.oxyarena.item.SoulReaperItem;
import com.example.oxyarena.registry.ModBlocks;
import com.example.oxyarena.network.CombatStatusSyncPayload;
import com.example.oxyarena.network.ItemPickupNotificationPayload;
import com.example.oxyarena.network.OccultCamouflageSyncPayload;
import com.example.oxyarena.network.PlayerAnimationPlayPayload;
import com.example.oxyarena.network.PlayerAnimationStopPayload;
import com.example.oxyarena.network.PingLocationSyncPayload;
import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.registry.ModMobEffects;
import com.example.oxyarena.registry.ModParticleTypes;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.entity.ZombieRenderer;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = OXYArena.MODID, dist = Dist.CLIENT)
public class OXYArenaClient {
    public OXYArenaClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::registerEntityRenderers);
        modEventBus.addListener(this::registerParticleProviders);
        modEventBus.addListener(this::registerKeyMappings);
        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(this::registerShaders);
        ImmersiveHudController.register();
        InventoryInteractionController.register();
        PingLocationController.register();
        PickupNotifierController.register();
        CombatStatusHudController.register();
        NecromancerStaffHudController.register();
        PlayerMantleController.register();
        PlayerSlideController.register();
        AmetraXrayController.register();
        BridgingAssistController.register();
        ToolTooltipStatsController.register();
        OccultCamouflageController.register();
        CombatStatusSyncPayload.setClientReceiver(CombatStatusHudController::handleSync);
        ItemPickupNotificationPayload.setClientReceiver(PickupNotifierController::handlePickup);
        PingLocationSyncPayload.setClientReceiver(PingLocationController::handlePing);
        OccultCamouflageSyncPayload.setClientReceiver(OccultCamouflageController::handleSync);
        PlayerAnimationPlayPayload.setClientReceiver(OxyPlayerAnimatorBridge::handlePlayPayload);
        PlayerAnimationStopPayload.setClientReceiver(OxyPlayerAnimatorBridge::handleStopPayload);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntityTypes.CITRINE_THROWING_DAGGER.get(),
                CitrineThrowingDaggerRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.INCANDESCENT_THROWING_DAGGER.get(),
                IncandescentThrowingDaggerRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SMOKE_BOMB.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SMOKE_CLOUD.get(), NoopRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.STORM_CHARGE.get(), StormChargeRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.ZEUS_LIGHTNING.get(),
                ThrownZeusLightningRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.ELEMENTAL_GAUNTLET_PROJECTILE.get(),
                ElementalGauntletProjectileRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.GRAPPLING_HOOK.get(),
                GrapplingHookRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.AIRDROP_CRATE.get(),
                AirdropCrateRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.BOB_BOSS.get(),
                ZombieRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.CLONE_THIEF.get(),
                CloneThiefRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.ERUPTION_TNT.get(),
                EruptionTntRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.ZENITH_ORBIT_SWORD.get(),
                ZenithOrbitSwordRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.SPECTRAL_MARK.get(),
                SpectralMarkRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.GHOST_SABER_ECHO.get(),
                GhostSaberEchoRenderer::new);
    }

    private void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                ModParticleTypes.NEVOA_BORDER.get(),
                NevoaBorderParticle.Provider::new);
    }

    private void registerKeyMappings(RegisterKeyMappingsEvent event) {
        PingLocationController.registerKeyMappings(event);
        AmetraXrayController.registerKeyMappings(event);
        PlayerSlideController.registerKeyMappings(event);
    }

    private void registerShaders(RegisterShadersEvent event) {
        try {
            OccultCamouflageRenderTypes.registerShaders(event);
        } catch (java.io.IOException exception) {
            throw new RuntimeException("Failed to register occult camouflage shaders", exception);
        }
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.SOUL_REAPER_FIRE.get(), RenderType.cutout());
            ItemBlockRenderTypes.setRenderLayer(ModBlocks.SOUL_REAPER_SOUL_FIRE.get(), RenderType.cutout());
            ItemProperties.register(
                    ModItems.AMETRA_SWORD.get(),
                    ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "altered"),
                    (stack, level, entity, seed) -> entity != null
                            && entity.hasEffect(ModMobEffects.AMETRA_AWAKENING)
                            && entity.getMainHandItem() == stack
                                    ? 1.0F
                                    : 0.0F);
            ItemProperties.register(
                    ModItems.SOUL_REAPER.get(),
                    ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "altered"),
                    (stack, level, entity, seed) -> SoulReaperItem.isAltered(stack) ? 1.0F : 0.0F);
            ItemProperties.register(
                    ModItems.COBALT_BOW.get(),
                    ResourceLocation.withDefaultNamespace("pull"),
                    (stack, level, entity, seed) -> {
                        if (entity == null || entity.getUseItem() != stack) {
                            return 0.0F;
                        }

                        return (float)(stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F;
                    });
            ItemProperties.register(
                    ModItems.COBALT_BOW.get(),
                    ResourceLocation.withDefaultNamespace("pulling"),
                    (stack, level, entity, seed) -> entity != null
                            && entity.isUsingItem()
                            && entity.getUseItem() == stack
                                    ? 1.0F
                                    : 0.0F);
            ItemProperties.register(
                    ModItems.COBALT_SHIELD.get(),
                    ResourceLocation.withDefaultNamespace("blocking"),
                    (stack, level, entity, seed) -> entity != null
                            && entity.isUsingItem()
                            && entity.getUseItem() == stack
                                    ? 1.0F
                                    : 0.0F);
            ItemProperties.register(
                    ModItems.KUSABIMARU.get(),
                    ResourceLocation.withDefaultNamespace("blocking"),
                    (stack, level, entity, seed) -> entity != null
                            && entity.isUsingItem()
                            && entity.getUseItem() == stack
                                    ? 1.0F
                                    : 0.0F);
        });
    }
}
