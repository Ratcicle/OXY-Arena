package com.example.oxyarena.serverevent;

import net.minecraft.server.MinecraftServer;

public final class ServerEventAreas {
    private static final ServerEventArea DEFAULT_MAP_ROTATION_AREA = new ServerEventArea(100, 400, 200, 500);

    private ServerEventAreas() {
    }

    public static ServerEventArea getArea(MinecraftServer server, ServerEventGroup group) {
        return ServerEventAreaSavedData.get(server).getArea(getAreaKey(group), getDefaultArea(group));
    }

    public static void setArea(MinecraftServer server, ServerEventGroup group, ServerEventArea area) {
        ServerEventAreaSavedData.get(server).setArea(getAreaKey(group), area);
    }

    public static void resetArea(MinecraftServer server, ServerEventGroup group) {
        ServerEventAreaSavedData.get(server).resetArea(getAreaKey(group));
    }

    public static ServerEventArea getDefaultArea(ServerEventGroup group) {
        return switch (group) {
            case MAP_ROTATION -> DEFAULT_MAP_ROTATION_AREA;
        };
    }

    private static String getAreaKey(ServerEventGroup group) {
        return group.getId();
    }
}
