package com.example.oxyarena.network;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PingLocationSyncPayload(
        UUID authorId,
        int sequence,
        Vec3 position,
        Optional<UUID> entityId,
        ResourceLocation dimensionId) implements CustomPacketPayload {
    private static final StreamCodec<RegistryFriendlyByteBuf, Vec3> VEC3_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            Vec3::x,
            ByteBufCodecs.DOUBLE,
            Vec3::y,
            ByteBufCodecs.DOUBLE,
            Vec3::z,
            Vec3::new);

    public static final Type<PingLocationSyncPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "ping_location_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PingLocationSyncPayload> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC,
                    PingLocationSyncPayload::authorId,
                    ByteBufCodecs.INT,
                    PingLocationSyncPayload::sequence,
                    VEC3_STREAM_CODEC,
                    PingLocationSyncPayload::position,
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
                    PingLocationSyncPayload::entityId,
                    ResourceLocation.STREAM_CODEC,
                    PingLocationSyncPayload::dimensionId,
                    PingLocationSyncPayload::new);

    private static Consumer<PingLocationSyncPayload> clientReceiver = payload -> {
    };

    public static void setClientReceiver(Consumer<PingLocationSyncPayload> receiver) {
        clientReceiver = Objects.requireNonNullElseGet(receiver, () -> payload -> {
        });
    }

    public static void handle(PingLocationSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientReceiver.accept(payload));
    }

    @Override
    public Type<PingLocationSyncPayload> type() {
        return TYPE;
    }
}
