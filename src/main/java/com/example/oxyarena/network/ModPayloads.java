package com.example.oxyarena.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {
    private static final String NETWORK_VERSION = "4";

    private ModPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(
                CombatStatusSyncPayload.TYPE,
                CombatStatusSyncPayload.STREAM_CODEC,
                CombatStatusSyncPayload::handle);
        registrar.playToClient(
                ItemPickupNotificationPayload.TYPE,
                ItemPickupNotificationPayload.STREAM_CODEC,
                ItemPickupNotificationPayload::handle);
        registrar.playToClient(
                PingLocationSyncPayload.TYPE,
                PingLocationSyncPayload.STREAM_CODEC,
                PingLocationSyncPayload::handle);
        registrar.playToClient(
                OccultCamouflageSyncPayload.TYPE,
                OccultCamouflageSyncPayload.STREAM_CODEC,
                OccultCamouflageSyncPayload::handle);
        registrar.playToClient(
                PlayerAnimationPlayPayload.TYPE,
                PlayerAnimationPlayPayload.STREAM_CODEC,
                PlayerAnimationPlayPayload::handle);
        registrar.playToServer(
                InventorySortRequestPayload.TYPE,
                InventorySortRequestPayload.STREAM_CODEC,
                InventorySortRequestPayload::handle);
        registrar.playToServer(
                PingLocationRequestPayload.TYPE,
                PingLocationRequestPayload.STREAM_CODEC,
                PingLocationRequestPayload::handle);
        registrar.playToServer(
                NecromancerStaffSelectSoulPayload.TYPE,
                NecromancerStaffSelectSoulPayload.STREAM_CODEC,
                NecromancerStaffSelectSoulPayload::handle);
        registrar.playToServer(
                PlayerSlideInputPayload.TYPE,
                PlayerSlideInputPayload.STREAM_CODEC,
                PlayerSlideInputPayload::handle);
        registrar.playToServer(
                PlayerMantleInputPayload.TYPE,
                PlayerMantleInputPayload.STREAM_CODEC,
                PlayerMantleInputPayload::handle);
    }
}
