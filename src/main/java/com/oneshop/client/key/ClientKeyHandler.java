package com.oneshop.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import com.oneshop.network.C2SOpenShopPayload;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.social.SocialInteractionsScreen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientKeyHandler {
    private ClientKeyHandler() {}

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.OPEN_SHOP);
        event.register(ModKeyBindings.OPEN_ADMIN_SHOP);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options != null && mc.options.keySocialInteractions != null) {
            if (mc.options.keySocialInteractions.same(ModKeyBindings.OPEN_SHOP)) {
                mc.options.keySocialInteractions.setKey(InputConstants.UNKNOWN);
                KeyMapping.resetMapping();
            }
            while (mc.options.keySocialInteractions.consumeClick()) {
                // Drain click to prevent vanilla SocialInteractionsScreen / toast
            }
        }

        if (mc.screen == null && mc.player != null) {
            while (ModKeyBindings.OPEN_SHOP.consumeClick()) {
                PacketDistributor.sendToServer(new C2SOpenShopPayload(false));
            }
            while (ModKeyBindings.OPEN_ADMIN_SHOP.consumeClick()) {
                PacketDistributor.sendToServer(new C2SOpenShopPayload(true));
            }
        }
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        if (event.getNewScreen() instanceof SocialInteractionsScreen) {
            event.setCanceled(true);
        }
    }
}
