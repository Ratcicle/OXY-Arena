package com.example.oxyarena.serverevent;

public enum ServerEventGroup {
    MAP_ROTATION("map_rotation");

    private final String id;

    ServerEventGroup(String id) {
        this.id = id;
    }

    public String getId() {
        return this.id;
    }
}
