package com.example.oxyarena.client.animation.definition;

import java.util.Locale;

public enum PlayerAnimationBone {
    HEAD("head"),
    BODY("body"),
    RIGHT_ARM("rightArm"),
    LEFT_ARM("leftArm"),
    RIGHT_LEG("rightLeg"),
    LEFT_LEG("leftLeg");

    private final String wireName;

    PlayerAnimationBone(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return this.wireName;
    }

    public static PlayerAnimationBone fromWireName(String wireName) {
        String normalized = wireName.toLowerCase(Locale.ROOT);
        for (PlayerAnimationBone value : values()) {
            if (value.wireName.toLowerCase(Locale.ROOT).equals(normalized)) {
                return value;
            }
        }

        throw new IllegalArgumentException("Unknown player animation bone: " + wireName);
    }
}
