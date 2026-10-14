package com.oneshop.client;

import com.oneshop.client.key.ClientKeyHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(RegisterKeyMappingsEvent.class, ClientKeyHandler::onRegisterKeyMappings);
        modEventBus.addListener(RegisterClientTooltipComponentFactoriesEvent.class, ShopTooltipHandler::onRegisterTooltipComponentFactories);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, ClientKeyHandler::onClientTick);
    }
}
