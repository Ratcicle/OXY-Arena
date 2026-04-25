package com.example.oxyarena.network;

import java.util.Objects;
import java.util.function.Consumer;

import com.example.oxyarena.OXYArena;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AmetraGlaiveCooldownSyncPayload(int durationTicks) implements CustomPacketPayload {
    public static final Type<AmetraGlaiveCooldownSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "ametra_glaive_cooldown_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AmetraGlaiveCooldownSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT,
                    AmetraGlaiveCooldownSyncPayload::durationTicks,
                    AmetraGlaiveCooldownSyncPayload::new);

    private static Consumer<AmetraGlaiveCooldownSyncPayload> clientReceiver = payload -> {
    };

    public static void setClientReceiver(Consumer<AmetraGlaiveCooldownSyncPayload> receiver) {
        clientReceiver = Objects.requireNonNullElseGet(receiver, () -> payload -> {
        });
    }

    public static void handle(AmetraGlaiveCooldownSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientReceiver.accept(payload));
    }

    @Override
    public Type<AmetraGlaiveCooldownSyncPayload> type() {
        return TYPE;
    }
}
