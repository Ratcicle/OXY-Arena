package com.example.oxyarena.network;

import com.example.oxyarena.OXYArena;
import com.example.oxyarena.inventory.InventorySortHelper;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record InventorySortRequestPayload(int menuId, int clickedSlotIndex) implements CustomPacketPayload {
    public static final Type<InventorySortRequestPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "inventory_sort_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, InventorySortRequestPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,
                    InventorySortRequestPayload::menuId,
                    ByteBufCodecs.INT,
                    InventorySortRequestPayload::clickedSlotIndex,
                    InventorySortRequestPayload::new);

    public static void handle(InventorySortRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                InventorySortHelper.sortFromClickedSlot(player, payload.menuId(), payload.clickedSlotIndex());
            }
        });
    }

    @Override
    public Type<InventorySortRequestPayload> type() {
        return TYPE;
    }
}
