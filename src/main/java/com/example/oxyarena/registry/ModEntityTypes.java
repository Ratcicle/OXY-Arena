package com.example.oxyarena.registry;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.entity.event.AirdropCrateEntity;
import com.example.oxyarena.entity.event.BobBossEntity;
import com.example.oxyarena.entity.event.CloneThiefEntity;
import com.example.oxyarena.entity.event.EruptionTntEntity;
import com.example.oxyarena.entity.effect.SmokeCloud;
import com.example.oxyarena.entity.effect.GhostSaberEchoEntity;
import com.example.oxyarena.entity.effect.SpectralMarkEntity;
import com.example.oxyarena.entity.effect.ZenithOrbitSwordEntity;
import com.example.oxyarena.entity.projectile.BlackBladeProjectile;
import com.example.oxyarena.entity.projectile.CitrineThrowingDagger;
import com.example.oxyarena.entity.projectile.ElementalGauntletProjectile;
import com.example.oxyarena.entity.projectile.GrapplingHook;
import com.example.oxyarena.entity.projectile.IncandescentThrowingDagger;
import com.example.oxyarena.entity.projectile.SmokeBomb;
import com.example.oxyarena.entity.projectile.StormCharge;
import com.example.oxyarena.entity.projectile.ThrownZeusLightning;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntityTypes {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(
            Registries.ENTITY_TYPE,
            OXYArena.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<CitrineThrowingDagger>> CITRINE_THROWING_DAGGER =
            ENTITY_TYPES.register(
                    "citrine_throwing_dagger",
                    () -> EntityType.Builder.<CitrineThrowingDagger>of(CitrineThrowingDagger::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .eyeHeight(0.13F)
                            .clientTrackingRange(8)
                            .updateInterval(10)
                            .build("citrine_throwing_dagger"));
    public static final DeferredHolder<EntityType<?>, EntityType<IncandescentThrowingDagger>> INCANDESCENT_THROWING_DAGGER =
            ENTITY_TYPES.register(
                    "incandescent_throwing_dagger",
                    () -> EntityType.Builder.<IncandescentThrowingDagger>of(IncandescentThrowingDagger::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .eyeHeight(0.13F)
                            .clientTrackingRange(8)
                            .updateInterval(10)
                            .build("incandescent_throwing_dagger"));
    public static final DeferredHolder<EntityType<?>, EntityType<SmokeBomb>> SMOKE_BOMB = ENTITY_TYPES.register(
            "smoke_bomb",
            () -> EntityType.Builder.<SmokeBomb>of(SmokeBomb::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("smoke_bomb"));
    public static final DeferredHolder<EntityType<?>, EntityType<SmokeCloud>> SMOKE_CLOUD = ENTITY_TYPES.register(
            "smoke_cloud",
            () -> EntityType.Builder.<SmokeCloud>of(SmokeCloud::new, MobCategory.MISC)
                    .noSave()
                    .noSummon()
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(32)
                    .updateInterval(2)
                    .build("smoke_cloud"));
    public static final DeferredHolder<EntityType<?>, EntityType<StormCharge>> STORM_CHARGE = ENTITY_TYPES.register(
            "storm_charge",
            () -> EntityType.Builder.<StormCharge>of(StormCharge::new, MobCategory.MISC)
                    .sized(0.3125F, 0.3125F)
                    .eyeHeight(0.0F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("storm_charge"));
    public static final DeferredHolder<EntityType<?>, EntityType<ThrownZeusLightning>> ZEUS_LIGHTNING =
            ENTITY_TYPES.register(
                    "zeus_lightning",
                    () -> EntityType.Builder.<ThrownZeusLightning>of(ThrownZeusLightning::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .eyeHeight(0.13F)
                            .clientTrackingRange(8)
                            .updateInterval(10)
                            .build("zeus_lightning"));
    public static final DeferredHolder<EntityType<?>, EntityType<ElementalGauntletProjectile>> ELEMENTAL_GAUNTLET_PROJECTILE =
            ENTITY_TYPES.register(
                    "elemental_gauntlet_projectile",
                    () -> EntityType.Builder.<ElementalGauntletProjectile>of(
                            ElementalGauntletProjectile::new,
                            MobCategory.MISC)
                            .sized(0.75F, 0.75F)
                            .eyeHeight(0.0F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("elemental_gauntlet_projectile"));
    public static final DeferredHolder<EntityType<?>, EntityType<BlackBladeProjectile>> BLACK_BLADE_PROJECTILE =
            ENTITY_TYPES.register(
                    "black_blade_projectile",
                    () -> EntityType.Builder.<BlackBladeProjectile>of(BlackBladeProjectile::new, MobCategory.MISC)
                            .sized(0.5F, 0.5F)
                            .eyeHeight(0.0F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build("black_blade_projectile"));
    public static final DeferredHolder<EntityType<?>, EntityType<GrapplingHook>> GRAPPLING_HOOK =
            ENTITY_TYPES.register(
                    "grappling_hook",
                    () -> EntityType.Builder.<GrapplingHook>of(GrapplingHook::new, MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(64)
                            .updateInterval(2)
                            .build("grappling_hook"));
    public static final DeferredHolder<EntityType<?>, EntityType<AirdropCrateEntity>> AIRDROP_CRATE =
            ENTITY_TYPES.register(
                    "airdrop_crate",
                    () -> EntityType.Builder.<AirdropCrateEntity>of(AirdropCrateEntity::new, MobCategory.MISC)
                            .sized(1.0F, 1.0F)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("airdrop_crate"));
    public static final DeferredHolder<EntityType<?>, EntityType<BobBossEntity>> BOB_BOSS =
            ENTITY_TYPES.register(
                    "bob_boss",
                    () -> EntityType.Builder.<BobBossEntity>of(BobBossEntity::new, MobCategory.MONSTER)
                            .noSave()
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(4)
                            .build("bob_boss"));
    public static final DeferredHolder<EntityType<?>, EntityType<CloneThiefEntity>> CLONE_THIEF =
            ENTITY_TYPES.register(
                    "clone_thief",
                    () -> EntityType.Builder.<CloneThiefEntity>of(CloneThiefEntity::new, MobCategory.MONSTER)
                            .sized(0.6F, 1.8F)
                            .clientTrackingRange(8)
                            .build("clone_thief"));
    public static final DeferredHolder<EntityType<?>, EntityType<EruptionTntEntity>> ERUPTION_TNT =
            ENTITY_TYPES.register(
                    "eruption_tnt",
                    () -> EntityType.Builder.<EruptionTntEntity>of(EruptionTntEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.98F, 0.98F)
                            .clientTrackingRange(10)
                            .updateInterval(10)
                            .build("eruption_tnt"));
    public static final DeferredHolder<EntityType<?>, EntityType<ZenithOrbitSwordEntity>> ZENITH_ORBIT_SWORD =
            ENTITY_TYPES.register(
                    "zenith_orbit_sword",
                    () -> EntityType.Builder.<ZenithOrbitSwordEntity>of(ZenithOrbitSwordEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.5F, 0.5F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build("zenith_orbit_sword"));
    public static final DeferredHolder<EntityType<?>, EntityType<SpectralMarkEntity>> SPECTRAL_MARK =
            ENTITY_TYPES.register(
                    "spectral_mark",
                    () -> EntityType.Builder.<SpectralMarkEntity>of(SpectralMarkEntity::new, MobCategory.MISC)
                            .noSummon()
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(16)
                            .updateInterval(1)
                            .build("spectral_mark"));
    public static final DeferredHolder<EntityType<?>, EntityType<GhostSaberEchoEntity>> GHOST_SABER_ECHO =
            ENTITY_TYPES.register(
                    "ghost_saber_echo",
                    () -> EntityType.Builder.<GhostSaberEchoEntity>of(GhostSaberEchoEntity::new, MobCategory.MISC)
                            .noSave()
                            .noSummon()
                            .sized(0.6F, 1.9F)
                            .clientTrackingRange(32)
                            .updateInterval(1)
                            .build("ghost_saber_echo"));

    private ModEntityTypes() {
    }

    public static void register(IEventBus modEventBus) {
        ENTITY_TYPES.register(modEventBus);
        modEventBus.addListener(ModEntityTypes::registerAttributes);
    }

    private static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(BOB_BOSS.get(), BobBossEntity.createAttributes().build());
        event.put(CLONE_THIEF.get(), CloneThiefEntity.createAttributes().build());
    }
}
