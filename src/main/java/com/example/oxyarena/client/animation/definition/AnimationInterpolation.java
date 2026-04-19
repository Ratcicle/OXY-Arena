package com.example.oxyarena.client.animation.definition;

import java.util.Locale;

public enum AnimationInterpolation {
    LINEAR("linear"),
    STEP("step"),
    BEZIER("bezier");

    private final String wireName;

    AnimationInterpolation(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return this.wireName;
    }

    public static AnimationInterpolation fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        for (AnimationInterpolation value : values()) {
            if (value.wireName.equals(normalized)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Unknown animation interpolation: " + wireName);
    }
}
