package com.example.oxyarena.serverevent;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.Supplier;

public record OxyServerEventDefinition(
        String id,
        Supplier<OxyServerEvent> factory,
        EnumSet<ServerEventGroup> groups,
        int selectionWeight,
        ServerEventRotationRole rotationRole) {
    public OxyServerEventDefinition {
        groups = groups.isEmpty() ? EnumSet.noneOf(ServerEventGroup.class) : EnumSet.copyOf(groups);
        if (selectionWeight <= 0) {
            throw new IllegalArgumentException("selectionWeight must be positive for event " + id);
        }
    }

    public OxyServerEvent createInstance() {
        return this.factory.get();
    }

    public boolean isInGroup(ServerEventGroup group) {
        return this.groups.contains(group);
    }

    public Set<ServerEventGroup> groupsView() {
        return Set.copyOf(this.groups);
    }
}
