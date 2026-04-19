package com.example.oxyarena.client.animation.runtime;

import java.util.Map;

import com.example.oxyarena.client.animation.definition.AnimationApplyMode;
import com.example.oxyarena.client.animation.definition.AnimationApplySettings;
import com.example.oxyarena.client.animation.definition.AnimationVector;
import com.example.oxyarena.client.animation.definition.PlayerAnimationBone;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

public final class AnimationModelPatch {
    private static final float DEGREES_TO_RADIANS = (float)(Math.PI / 180.0D);

    private AnimationModelPatch() {
    }

    public static void apply(
            HumanoidModel<?> model,
            PlayerAnimationPose pose,
            AnimationApplySettings apply,
            float weight) {
        if (pose.isEmpty() || weight <= 0.0F) {
            return;
        }

        for (Map.Entry<PlayerAnimationBone, BoneTransform> entry : pose.transforms().entrySet()) {
            if (!apply.derivesMaskFromTracks() && !apply.mask().contains(entry.getKey())) {
                continue;
            }
            applyBone(model, entry.getKey(), entry.getValue(), apply.mode(), weight);
        }
    }

    private static void applyBone(
            HumanoidModel<?> model,
            PlayerAnimationBone bone,
            BoneTransform transform,
            AnimationApplyMode mode,
            float weight) {
        applyPart(basePart(model, bone), transform, mode, weight);
        if (model instanceof PlayerModel<?> playerModel) {
            ModelPart wearPart = wearPart(playerModel, bone);
            if (wearPart != null) {
                applyPart(wearPart, transform, mode, weight);
            }
        }
    }

    private static ModelPart basePart(HumanoidModel<?> model, PlayerAnimationBone bone) {
        return switch (bone) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_LEG -> model.rightLeg;
            case LEFT_LEG -> model.leftLeg;
        };
    }

    private static ModelPart wearPart(PlayerModel<?> model, PlayerAnimationBone bone) {
        return switch (bone) {
            case HEAD -> model.hat;
            case BODY -> model.jacket;
            case RIGHT_ARM -> model.rightSleeve;
            case LEFT_ARM -> model.leftSleeve;
            case RIGHT_LEG -> model.rightPants;
            case LEFT_LEG -> model.leftPants;
        };
    }

    private static void applyPart(ModelPart part, BoneTransform transform, AnimationApplyMode mode, float weight) {
        if (mode == AnimationApplyMode.REPLACE) {
            if (transform.hasPosition()) {
                replacePosition(part, transform.position(), weight);
            }
            if (transform.hasRotation()) {
                replaceRotation(part, transform.rotation(), weight);
            }
            if (transform.hasScale()) {
                replaceScale(part, transform.scale(), weight);
            }
            return;
        }

        if (transform.hasPosition()) {
            addPosition(part, transform.position(), weight);
        }
        if (transform.hasRotation()) {
            addRotation(part, transform.rotation(), weight);
        }
        if (transform.hasScale()) {
            addScale(part, transform.scale(), weight);
        }
    }

    private static void addPosition(ModelPart part, AnimationVector position, float weight) {
        AnimationVector convertedPosition = toModelPartPosition(position);
        part.x += convertedPosition.x() * weight;
        part.y += convertedPosition.y() * weight;
        part.z += convertedPosition.z() * weight;
    }

    private static void addRotation(ModelPart part, AnimationVector rotation, float weight) {
        AnimationVector convertedRotation = toModelPartRotation(rotation);
        part.xRot += convertedRotation.x() * weight;
        part.yRot += convertedRotation.y() * weight;
        part.zRot += convertedRotation.z() * weight;
    }

    private static void addScale(ModelPart part, AnimationVector scale, float weight) {
        part.xScale += (scale.x() - 1.0F) * weight;
        part.yScale += (scale.y() - 1.0F) * weight;
        part.zScale += (scale.z() - 1.0F) * weight;
    }

    private static void replacePosition(ModelPart part, AnimationVector position, float weight) {
        AnimationVector convertedPosition = toModelPartPosition(position);
        part.x = Mth.lerp(weight, part.x, convertedPosition.x());
        part.y = Mth.lerp(weight, part.y, convertedPosition.y());
        part.z = Mth.lerp(weight, part.z, convertedPosition.z());
    }

    private static void replaceRotation(ModelPart part, AnimationVector rotation, float weight) {
        AnimationVector convertedRotation = toModelPartRotation(rotation);
        part.xRot = Mth.lerp(weight, part.xRot, convertedRotation.x());
        part.yRot = Mth.lerp(weight, part.yRot, convertedRotation.y());
        part.zRot = Mth.lerp(weight, part.zRot, convertedRotation.z());
    }

    private static AnimationVector toModelPartPosition(AnimationVector position) {
        return new AnimationVector(position.x(), -position.y(), -position.z());
    }

    private static AnimationVector toModelPartRotation(AnimationVector rotation) {
        return new AnimationVector(
                rotation.x() * DEGREES_TO_RADIANS,
                -rotation.y() * DEGREES_TO_RADIANS,
                -rotation.z() * DEGREES_TO_RADIANS);
    }

    private static void replaceScale(ModelPart part, AnimationVector scale, float weight) {
        part.xScale = Mth.lerp(weight, part.xScale, scale.x());
        part.yScale = Mth.lerp(weight, part.yScale, scale.y());
        part.zScale = Mth.lerp(weight, part.zScale, scale.z());
    }
}
