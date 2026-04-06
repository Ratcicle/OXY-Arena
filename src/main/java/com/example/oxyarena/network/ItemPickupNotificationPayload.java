package com.example.oxyarena.network;

import java.util.Objects;
import java.util.function.Consumer;

import com.example.oxyarena.OXYArena;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ItemPickupNotificationPayload(ItemStack stack) implements CustomPacketPayload {
    public static final Type<ItemPickupNotificationPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(OXYArena.MODID, "item_pickup_notification"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemPickupNotificationPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ItemStack.STREAM_CODEC,
                    ItemPickupNotificationPayload::stack,
                    ItemPickupNotificationPayload::new);

    private static Consumer<ItemStack> clientReceiver = stack -> {
    };

    public static void setClientReceiver(Consumer<ItemStack> receiver) {
        clientReceiver = Objects.requireNonNullElseGet(receiver, () -> stack -> {
        });
    }

    public static void handle(ItemPickupNotificationPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientReceiver.accept(payload.stack().copy()));
    }

    @Override
    public Type<ItemPickupNotificationPayload> type() {
        return TYPE;
    }
}
