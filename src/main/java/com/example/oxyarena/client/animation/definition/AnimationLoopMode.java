package com.example.oxyarena.client.animation.definition;

import java.util.Locale;

public enum AnimationLoopMode {
    NONE("none"),
    LOOP("loop"),
    HOLD("hold");

    private final String wireName;

    AnimationLoopMode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return this.wireName;
    }

    public static AnimationLoopMode fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        for (AnimationLoopMode value : values()) {
            if (value.wireName.equals(normalized)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Unknown animation loop mode: " + wireName);
    }
}
