package com.example.oxyarena.item;

import java.util.function.Consumer;

import com.example.oxyarena.client.renderer.item.CobaltShieldItemRenderer;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ShieldItem;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class CobaltShieldItem extends ShieldItem {
    public CobaltShieldItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return CobaltShieldItemRenderer.getInstance();
            }
        });
    }
}
