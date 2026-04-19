package com.example.oxyarena.client.animation.definition;

import java.util.Locale;

public enum AnimationTransformTarget {
    ROTATION("rotation"),
    POSITION("position"),
    SCALE("scale");

    private final String wireName;

    AnimationTransformTarget(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return this.wireName;
    }

    public static AnimationTransformTarget fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        for (AnimationTransformTarget value : values()) {
            if (value.wireName.equals(normalized)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Unknown animation transform target: " + wireName);
    }
}
