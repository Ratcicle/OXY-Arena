package com.example.oxyarena;

import com.example.oxyarena.client.ImmersiveHudController;
import com.example.oxyarena.client.particle.NevoaBorderParticle;
import com.example.oxyarena.client.renderer.entity.AirdropCrateRenderer;
import com.example.oxyarena.client.renderer.entity.CitrineThrowingDaggerRenderer;
import com.example.oxyarena.client.renderer.entity.GrapplingHookRenderer;
import com.example.oxyarena.client.renderer.entity.ThrownZeusLightningRenderer;
import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.registry.ModMobEffects;
import com.example.oxyarena.registry.ModParticleTypes;

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
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

@Mod(value = OXYArena.MODID, dist = Dist.CLIENT)
public class OXYArenaClient {
    public OXYArenaClient(IEventBus modEventBus, ModContainer container) {
        modEventBus.addListener(this::registerEntityRenderers);
        modEventBus.addListener(this::registerParticleProviders);
        modEventBus.addListener(this::onClientSetup);
        ImmersiveHudController.register();
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }

    private void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(
                ModEntityTypes.CITRINE_THROWING_DAGGER.get(),
                CitrineThrowingDaggerRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SMOKE_BOMB.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntityTypes.SMOKE_CLOUD.get(), NoopRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.ZEUS_LIGHTNING.get(),
                ThrownZeusLightningRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.GRAPPLING_HOOK.get(),
                GrapplingHookRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.AIRDROP_CRATE.get(),
                AirdropCrateRenderer::new);
        event.registerEntityRenderer(
                ModEntityTypes.BOB_BOSS.get(),
                ZombieRenderer::new);
    }

    private void registerParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(
                ModParticleTypes.NEVOA_BORDER.get(),
                NevoaBorderParticle.Provider::new);
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    ModItems.AMETRA_SWORD.get(),
                    ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "altered"),
                    (stack, level, entity, seed) -> entity != null
                            && entity.hasEffect(ModMobEffects.AMETRA_AWAKENING)
                            && entity.getMainHandItem() == stack
                                    ? 1.0F
                                    : 0.0F);
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
        });
    }
}
