package com.example.oxyarena.network;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlayerAnimationPlayPayload(UUID playerId, ResourceLocation animationId) implements CustomPacketPayload {
    public static final Type<PlayerAnimationPlayPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "player_animation_play"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerAnimationPlayPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC,
                    PlayerAnimationPlayPayload::playerId,
                    ResourceLocation.STREAM_CODEC,
                    PlayerAnimationPlayPayload::animationId,
                    PlayerAnimationPlayPayload::new);

    private static Consumer<PlayerAnimationPlayPayload> clientReceiver = payload -> {
    };

    public static void setClientReceiver(Consumer<PlayerAnimationPlayPayload> receiver) {
        clientReceiver = Objects.requireNonNullElseGet(receiver, () -> payload -> {
        });
    }

    public static void handle(PlayerAnimationPlayPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientReceiver.accept(payload));
    }

    @Override
    public Type<PlayerAnimationPlayPayload> type() {
        return TYPE;
    }
}
