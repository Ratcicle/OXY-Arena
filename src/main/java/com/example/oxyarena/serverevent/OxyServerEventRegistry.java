package com.example.oxyarena.serverevent;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import net.minecraft.util.RandomSource;

public final class OxyServerEventRegistry {
    private static final Map<String, OxyServerEventDefinition> EVENT_DEFINITIONS = new LinkedHashMap<>();

    static {
        register("apocalipse", ApocalypseServerEvent::new, 10, ServerEventGroup.MAP_ROTATION);
        register("airdrop", AirdropServerEvent::new, 10, ServerEventRotationRole.COOLDOWN_SPECIAL, ServerEventGroup.MAP_ROTATION);
        register("caca", PlayerHuntServerEvent::new, 10, ServerEventGroup.MAP_ROTATION);
        register("mineracao", MiningFeverServerEvent::new, 10, ServerEventGroup.MAP_ROTATION);
        register("miniboss", MinibossServerEvent::new, 10, ServerEventGroup.MAP_ROTATION);
        register("caravana_pillager", PillagerCaravanServerEvent::new, 10, ServerEventGroup.MAP_ROTATION);
        register("inundacao", InundacaoServerEvent::new, 10, ServerEventGroup.MAP_ROTATION);
        register("clones", CloneThiefServerEvent::new, 10, ServerEventGroup.MAP_ROTATION);
        register("mercador", MercadorServerEvent::new, 10, ServerEventGroup.MAP_ROTATION);
        register("nevoa", NevoaServerEvent::new, 10, ServerEventGroup.MAP_ROTATION);
        register("tnt", EruptionTntServerEvent::new, 10, ServerEventGroup.MAP_ROTATION);
    }

    private OxyServerEventRegistry() {
    }

    public static Collection<String> getRegisteredEventIds() {
        return EVENT_DEFINITIONS.keySet();
    }

    public static Collection<OxyServerEventDefinition> getRegisteredEventDefinitions() {
        return Collections.unmodifiableCollection(EVENT_DEFINITIONS.values());
    }

    public static List<OxyServerEventDefinition> getDefinitionsInGroup(ServerEventGroup group) {
        return EVENT_DEFINITIONS.values().stream()
                .filter(definition -> definition.isInGroup(group))
                .toList();
    }

    public static List<OxyServerEventDefinition> getDefinitionsInGroup(
            ServerEventGroup group,
            ServerEventRotationRole rotationRole) {
        return EVENT_DEFINITIONS.values().stream()
                .filter(definition -> definition.isInGroup(group))
                .filter(definition -> definition.rotationRole() == rotationRole)
                .toList();
    }

    public static Map<String, OxyServerEvent> createEventInstances() {
        Map<String, OxyServerEvent> events = new LinkedHashMap<>();
        EVENT_DEFINITIONS.forEach((id, definition) -> events.put(id, definition.createInstance()));
        return events;
    }

    public static Optional<OxyServerEventDefinition> getDefinition(String id) {
        return Optional.ofNullable(EVENT_DEFINITIONS.get(id));
    }

    public static Optional<OxyServerEventDefinition> pickRandomDefinition(
            ServerEventGroup group,
            RandomSource random,
            Collection<String> excludedEventIds) {
        List<OxyServerEventDefinition> candidates = EVENT_DEFINITIONS.values().stream()
                .filter(definition -> definition.isInGroup(group))
                .filter(definition -> !excludedEventIds.contains(definition.id()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int totalWeight = 0;
        for (OxyServerEventDefinition candidate : candidates) {
            totalWeight += candidate.selectionWeight();
        }

        int chosenWeight = random.nextInt(totalWeight);
        int cumulativeWeight = 0;
        for (OxyServerEventDefinition candidate : candidates) {
            cumulativeWeight += candidate.selectionWeight();
            if (chosenWeight < cumulativeWeight) {
                return Optional.of(candidate);
            }
        }

        return Optional.of(candidates.getLast());
    }

    private static void register(
            String id,
            Supplier<OxyServerEvent> factory,
            int selectionWeight,
            ServerEventGroup... groups) {
        register(id, factory, selectionWeight, ServerEventRotationRole.PRIMARY, groups);
    }

    private static void register(
            String id,
            Supplier<OxyServerEvent> factory,
            int selectionWeight,
            ServerEventRotationRole rotationRole,
            ServerEventGroup... groups) {
        OxyServerEventDefinition definition = new OxyServerEventDefinition(
                id,
                factory,
                groups.length == 0 ? EnumSet.noneOf(ServerEventGroup.class) : EnumSet.of(groups[0], groups),
                selectionWeight,
                rotationRole);
        if (EVENT_DEFINITIONS.putIfAbsent(id, definition) != null) {
            throw new IllegalStateException("Duplicate server event id: " + id);
        }
    }
}
