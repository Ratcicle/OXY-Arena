package com.example.oxyarena.client.animation.definition;

import java.util.Locale;

public enum AnimationApplyMode {
    ADDITIVE("additive"),
    REPLACE("replace");

    private final String wireName;

    AnimationApplyMode(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return this.wireName;
    }

    public static AnimationApplyMode fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        for (AnimationApplyMode value : values()) {
            if (value.wireName.equals(normalized)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Unknown animation apply mode: " + wireName);
    }
}
