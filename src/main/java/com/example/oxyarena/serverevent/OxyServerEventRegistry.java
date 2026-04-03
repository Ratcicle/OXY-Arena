package com.example.oxyarena.serverevent;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class OxyServerEventRegistry {
    private static final Map<String, Supplier<OxyServerEvent>> EVENT_FACTORIES = new LinkedHashMap<>();

    static {
        register("apocalipse", ApocalypseServerEvent::new);
        register("airdrop", AirdropServerEvent::new);
    }

    private OxyServerEventRegistry() {
    }

    public static Collection<String> getRegisteredEventIds() {
        return EVENT_FACTORIES.keySet();
    }

    public static Map<String, OxyServerEvent> createEventInstances() {
        Map<String, OxyServerEvent> events = new LinkedHashMap<>();
        EVENT_FACTORIES.forEach((id, factory) -> events.put(id, factory.get()));
        return events;
    }

    private static void register(String id, Supplier<OxyServerEvent> factory) {
        if (EVENT_FACTORIES.putIfAbsent(id, factory) != null) {
            throw new IllegalStateException("Duplicate server event id: " + id);
        }
    }
}
