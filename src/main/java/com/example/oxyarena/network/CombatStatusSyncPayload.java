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

public record CombatStatusSyncPayload(ResourceLocation statusId, int quantizedProgress) implements CustomPacketPayload {
    public static final Type<CombatStatusSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "combat_status_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CombatStatusSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    CombatStatusSyncPayload::statusId,
                    ByteBufCodecs.VAR_INT,
                    CombatStatusSyncPayload::quantizedProgress,
                    CombatStatusSyncPayload::new);

    private static Consumer<CombatStatusSyncPayload> clientReceiver = payload -> {
    };

    public static void setClientReceiver(Consumer<CombatStatusSyncPayload> receiver) {
        clientReceiver = Objects.requireNonNullElseGet(receiver, () -> payload -> {
        });
    }

    public static void handle(CombatStatusSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientReceiver.accept(payload));
    }

    @Override
    public Type<CombatStatusSyncPayload> type() {
        return TYPE;
    }
}
