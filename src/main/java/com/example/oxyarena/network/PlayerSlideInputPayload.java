package com.example.oxyarena.network;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.event.gameplay.PlayerSlideEvents;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record PlayerSlideInputPayload(int slideKeyDown, int movementKeyDown) implements CustomPacketPayload {
    public static final Type<PlayerSlideInputPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "player_slide_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerSlideInputPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    PlayerSlideInputPayload::slideKeyDown,
                    ByteBufCodecs.INT,
                    PlayerSlideInputPayload::movementKeyDown,
                    PlayerSlideInputPayload::new);

    public PlayerSlideInputPayload(boolean slideKeyDown, boolean movementKeyDown) {
        this(slideKeyDown ? 1 : 0, movementKeyDown ? 1 : 0);
    }

    public static void handle(PlayerSlideInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                PlayerSlideEvents.handleInput(player, payload.slideKeyDown() != 0, payload.movementKeyDown() != 0);
            }
        });
    }

    @Override
    public Type<PlayerSlideInputPayload> type() {
        return TYPE;
    }
}
