package com.example.oxyarena;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;
import com.example.oxyarena.command.ModCommands;
import com.example.oxyarena.combatstatus.CombatStatusDataManager;
import com.example.oxyarena.event.ModGameEvents;
import com.example.oxyarena.event.ModServerEventHooks;
import com.example.oxyarena.event.ModTooltipEvents;
import com.example.oxyarena.event.gameplay.WeaponAttributesDataManager;
import com.example.oxyarena.network.ModPayloads;
import com.example.oxyarena.registry.ModArmorMaterials;
import com.example.oxyarena.registry.ModBlockEntityTypes;
import com.example.oxyarena.registry.ModBiomeModifiers;
import com.example.oxyarena.registry.ModBlocks;
import com.example.oxyarena.registry.ModCreativeModeTabs;
import com.example.oxyarena.registry.ModEntityTypes;
import com.example.oxyarena.registry.ModItems;
import com.example.oxyarena.registry.ModLootModifiers;
import com.example.oxyarena.registry.ModMobEffects;
import com.example.oxyarena.registry.ModParticleTypes;
import com.example.oxyarena.registry.ModSoundEvents;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.common.NeoForge;

@Mod(OXYArena.MODID)
public class OXYArena {
    public static final String MODID = "oxyarena";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OXYArena(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.debug("Initializing {}", MODID);

        ModArmorMaterials.register(modEventBus);
        ModBlockEntityTypes.register(modEventBus);
        ModBiomeModifiers.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModEntityTypes.register(modEventBus);
        ModMobEffects.register(modEventBus);
        ModItems.register(modEventBus);
        ModLootModifiers.register(modEventBus);
        ModParticleTypes.register(modEventBus);
        ModSoundEvents.register(modEventBus);
        ModCreativeModeTabs.register(modEventBus);
        modEventBus.addListener(ModPayloads::register);

        NeoForge.EVENT_BUS.addListener(ModCommands::register);
        NeoForge.EVENT_BUS.addListener(CombatStatusDataManager::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(WeaponAttributesDataManager::onAddReloadListeners);
        NeoForge.EVENT_BUS.addListener(ModGameEvents::onLivingIncomingDamage);
        NeoForge.EVENT_BUS.addListener(ModGameEvents::onLivingChangeTarget);
        NeoForge.EVENT_BUS.addListener(ModGameEvents::onLivingDamagePre);
        NeoForge.EVENT_BUS.addListener(ModGameEvents::onLivingDamagePost);
        NeoForge.EVENT_BUS.addListener(ModGameEvents::onLivingHeal);
        NeoForge.EVENT_BUS.addListener(ModGameEvents::onProjectileImpact);
        NeoForge.EVENT_BUS.addListener(ModGameEvents::onSweepAttack);
        NeoForge.EVENT_BUS.addListener(ModGameEvents::onBlockDrops);
        NeoForge.EVENT_BUS.addListener(ModGameEvents::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onServerTickPost);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onItemEntityPickup);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onItemToss);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onRightClickItem);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onEntityInteract);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onLeftClickBlock);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onAttackEntity);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onArrowNock);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onLivingGetProjectile);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onBlockBreak);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onStartTracking);
        NeoForge.EVENT_BUS.addListener(ModTooltipEvents::onItemTooltip);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onServerStopping);
        NeoForge.EVENT_BUS.addListener(ModServerEventHooks::onServerStopped);

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC);
        modContainer.registerConfig(ModConfig.Type.CLIENT, Config.CLIENT_SPEC);
    }
}
