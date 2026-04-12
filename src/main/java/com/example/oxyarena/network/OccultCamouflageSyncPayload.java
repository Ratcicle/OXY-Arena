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

public record OccultCamouflageSyncPayload(UUID playerId, int quantizedProgress) implements CustomPacketPayload {
    public static final Type<OccultCamouflageSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "occult_camouflage_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OccultCamouflageSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC,
                    OccultCamouflageSyncPayload::playerId,
                    ByteBufCodecs.VAR_INT,
                    OccultCamouflageSyncPayload::quantizedProgress,
                    OccultCamouflageSyncPayload::new);

    private static Consumer<OccultCamouflageSyncPayload> clientReceiver = payload -> {
    };

    public static void setClientReceiver(Consumer<OccultCamouflageSyncPayload> receiver) {
        clientReceiver = Objects.requireNonNullElseGet(receiver, () -> payload -> {
        });
    }

    public static void handle(OccultCamouflageSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientReceiver.accept(payload));
    }

    @Override
    public Type<OccultCamouflageSyncPayload> type() {
        return TYPE;
    }
}
