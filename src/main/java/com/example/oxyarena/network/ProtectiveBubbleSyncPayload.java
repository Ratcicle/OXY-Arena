package com.example.oxyarena.network;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ProtectiveBubbleSyncPayload(UUID playerId, boolean active) implements CustomPacketPayload {
    public static final Type<ProtectiveBubbleSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "protective_bubble_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProtectiveBubbleSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC,
                    ProtectiveBubbleSyncPayload::playerId,
                    ByteBufCodecs.BOOL,
                    ProtectiveBubbleSyncPayload::active,
                    ProtectiveBubbleSyncPayload::new);

    private static Consumer<ProtectiveBubbleSyncPayload> clientReceiver = payload -> {
    };

    public static void setClientReceiver(Consumer<ProtectiveBubbleSyncPayload> receiver) {
        clientReceiver = Objects.requireNonNullElseGet(receiver, () -> payload -> {
        });
    }

    public static void handle(ProtectiveBubbleSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientReceiver.accept(payload));
    }

    @Override
    public Type<ProtectiveBubbleSyncPayload> type() {
        return TYPE;
    }
}
