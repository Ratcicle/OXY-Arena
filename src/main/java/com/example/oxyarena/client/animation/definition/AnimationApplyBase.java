package com.example.oxyarena.client.animation.definition;

import java.util.Locale;

public enum AnimationApplyBase {
    VANILLA("vanilla");

    private final String wireName;

    AnimationApplyBase(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return this.wireName;
    }

    public static AnimationApplyBase fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        for (AnimationApplyBase value : values()) {
            if (value.wireName.equals(normalized)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Unknown animation apply base: " + wireName);
    }
}
