package com.example.oxyarena.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModPayloads {
    private static final String NETWORK_VERSION = "1";

    private ModPayloads() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NETWORK_VERSION);
        registrar.playToClient(
                ItemPickupNotificationPayload.TYPE,
                ItemPickupNotificationPayload.STREAM_CODEC,
                ItemPickupNotificationPayload::handle);
    }
}
