package com.oneshop.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.oneshop.client.key.ClientKeyHandler;
import com.oneshop.client.key.ModKeyBindings;
import com.oneshop.client.ui.IconPickerModal;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(RegisterKeyMappingsEvent.class, ClientKeyHandler::onRegisterKeyMappings);
        modEventBus.addListener(RegisterClientTooltipComponentFactoriesEvent.class, ShopTooltipHandler::onRegisterTooltipComponentFactories);
        modEventBus.addListener(FMLClientSetupEvent.class, ClientSetup::onClientSetup);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ClientKeyHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Opening.class, ClientKeyHandler::onScreenOpening);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            ClientShopCache.clear();
            ClientBankCache.setBalance(0.0);
            InventoryHighlightHandler.resetState();
            IconPickerModal.clearCaches();
            com.oneshop.shop.ShopEntry.clearCaches();
        });
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            Options options = Minecraft.getInstance().options;
            if (options != null && options.keySocialInteractions != null) {
                KeyMapping socialKey = options.keySocialInteractions;
                if (!socialKey.isUnbound() && (socialKey.getKey().getValue() == InputConstants.KEY_P || socialKey.same(ModKeyBindings.OPEN_SHOP))) {
                    socialKey.setKey(InputConstants.UNKNOWN);
                    KeyMapping.resetMapping();
                    options.save();
                }
            }
        });
    }
}
