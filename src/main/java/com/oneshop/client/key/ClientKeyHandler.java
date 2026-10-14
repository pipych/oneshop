package com.oneshop.client.key;

import com.oneshop.network.C2SOpenShopPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientKeyHandler {
    private ClientKeyHandler() {}

    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.OPEN_SHOP);
        event.register(ModKeyBindings.OPEN_ADMIN_SHOP);
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null && minecraft.player != null) {
            while (ModKeyBindings.OPEN_SHOP.consumeClick()) {
                PacketDistributor.sendToServer(new C2SOpenShopPayload(false));
            }
            while (ModKeyBindings.OPEN_ADMIN_SHOP.consumeClick()) {
                PacketDistributor.sendToServer(new C2SOpenShopPayload(true));
            }
        }
    }
}
