package com.example.oxyarena.network;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.event.gameplay.PlayerMantleEvents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlayerMantleInputPayload(int action) implements CustomPacketPayload {
    public static final int ACTION_JUMP_PRESSED = 0;
    public static final int ACTION_SHIFT_PRESSED = 1;
    public static final Type<PlayerMantleInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "player_mantle_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerMantleInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    PlayerMantleInputPayload::action,
                    PlayerMantleInputPayload::new);

    public static void handle(PlayerMantleInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PlayerMantleEvents.handleInput(player, payload.action());
            }
        });
    }

    @Override
    public Type<PlayerMantleInputPayload> type() {
        return TYPE;
    }
}
