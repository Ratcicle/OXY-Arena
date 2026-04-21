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

public record PlayerAnimationStopPayload(UUID playerId) implements CustomPacketPayload {
    public static final Type<PlayerAnimationStopPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "player_animation_stop"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerAnimationStopPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC,
                    PlayerAnimationStopPayload::playerId,
                    PlayerAnimationStopPayload::new);

    private static Consumer<PlayerAnimationStopPayload> clientReceiver = payload -> {
    };

    public static void setClientReceiver(Consumer<PlayerAnimationStopPayload> receiver) {
        clientReceiver = Objects.requireNonNullElseGet(receiver, () -> payload -> {
        });
    }

    public static void handle(PlayerAnimationStopPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientReceiver.accept(payload));
    }

    @Override
    public Type<PlayerAnimationStopPayload> type() {
        return TYPE;
    }
}
