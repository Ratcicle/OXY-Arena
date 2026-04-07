package com.example.oxyarena.network;

import java.util.Optional;
import java.util.UUID;

import com.example.oxyarena.OXYArena;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PingLocationRequestPayload(
        Vec3 position,
        Optional<UUID> entityId,
        ResourceLocation dimensionId,
        int sequence) implements CustomPacketPayload {
    private static final double MAX_SERVER_TARGET_DISTANCE = 256.0D;
    private static final StreamCodec<RegistryFriendlyByteBuf, Vec3> VEC3_STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            Vec3::x,
            ByteBufCodecs.DOUBLE,
            Vec3::y,
            ByteBufCodecs.DOUBLE,
            Vec3::z,
            Vec3::new);

    public static final Type<PingLocationRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "ping_location_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PingLocationRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    VEC3_STREAM_CODEC,
                    PingLocationRequestPayload::position,
                    ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
                    PingLocationRequestPayload::entityId,
                    ResourceLocation.STREAM_CODEC,
                    PingLocationRequestPayload::dimensionId,
                    ByteBufCodecs.INT,
                    PingLocationRequestPayload::sequence,
                    PingLocationRequestPayload::new);

    public static void handle(PingLocationRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
                return;
            }

            if (!payload.dimensionId().equals(level.dimension().location())) {
                return;
            }

            Vec3 requestedPos = payload.position();
            if (player.position().distanceToSqr(requestedPos) > MAX_SERVER_TARGET_DISTANCE * MAX_SERVER_TARGET_DISTANCE) {
                return;
            }

            Optional<UUID> entityId = Optional.empty();
            Vec3 resolvedPos = requestedPos;
            if (payload.entityId().isPresent()) {
                Entity entity = level.getEntity(payload.entityId().get());
                if (entity == null || entity.level() != level) {
                    return;
                }

                Vec3 entityPos = entity.getBoundingBox().getCenter();
                if (player.position().distanceToSqr(entityPos) > MAX_SERVER_TARGET_DISTANCE * MAX_SERVER_TARGET_DISTANCE) {
                    return;
                }

                entityId = Optional.of(entity.getUUID());
                resolvedPos = entityPos;
            }

            PacketDistributor.sendToPlayersInDimension(
                    level,
                    new PingLocationSyncPayload(
                            player.getUUID(),
                            payload.sequence(),
                            resolvedPos,
                            entityId,
                            level.dimension().location()));
        });
    }

    @Override
    public Type<PingLocationRequestPayload> type() {
        return TYPE;
    }
}
