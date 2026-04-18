package com.example.oxyarena.network;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.event.gameplay.NecromancerStaffEvents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record NecromancerStaffSelectSoulPayload(int direction) implements CustomPacketPayload {
    public static final Type<NecromancerStaffSelectSoulPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "necromancer_staff_select_soul"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NecromancerStaffSelectSoulPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    NecromancerStaffSelectSoulPayload::direction,
                    NecromancerStaffSelectSoulPayload::new);

    public static void handle(NecromancerStaffSelectSoulPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                NecromancerStaffEvents.cycleSelectedSoul(player, Integer.signum(payload.direction()));
            }
        });
    }

    @Override
    public Type<NecromancerStaffSelectSoulPayload> type() {
        return TYPE;
    }
}
