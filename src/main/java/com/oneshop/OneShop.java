package com.oneshop;

import com.mojang.logging.LogUtils;
import com.oneshop.attachment.ModAttachments;
import com.oneshop.command.OneShopCommand;
import com.oneshop.item.ModCreativeTabs;
import com.oneshop.item.ModItems;
import com.oneshop.network.ModNetwork;
import com.oneshop.service.PlayerAccountService;
import com.oneshop.service.ShopTransactionService;
import com.oneshop.shop.ServerShopRegistry;
import com.oneshop.shop.ShopSavedData;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.slf4j.Logger;

@Mod(OneShop.MODID)
public class OneShop {
    public static final String MODID = "oneshop";
    public static final Logger LOGGER = LogUtils.getLogger();

    public OneShop(IEventBus modEventBus) {
        LOGGER.info("Initializing OneShop mod...");

        // Register Deferred Registers to the mod event bus
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        // Register network payload handler
        modEventBus.addListener(ModNetwork::registerPayloads);

        // Initialize client setup if on client physical side
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            com.oneshop.client.ClientSetup.init(modEventBus);
        }

        // Register event listener to NeoForge gameplay event bus
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        OneShopCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            LOGGER.debug("Syncing account balance and shop data for logged in player: {}", serverPlayer.getScoreboardName());
            PlayerAccountService.syncBalance(serverPlayer);
            ShopTransactionService.syncShopData(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            LOGGER.debug("Syncing account balance and shop data for respawned player: {}", serverPlayer.getScoreboardName());
            PlayerAccountService.syncBalance(serverPlayer);
            ShopTransactionService.syncShopData(serverPlayer);
        }
    }

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        LOGGER.info("Server started, initializing OneShop ServerShopRegistry...");
        ServerShopRegistry.getInstance().setCurrentServer(event.getServer());
        // Eagerly ensure shop data is loaded / initialized with default categories if missing
        ShopSavedData.get(event.getServer());
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        LOGGER.info("Server stopped, clearing OneShop ServerShopRegistry server reference...");
        ServerShopRegistry.getInstance().setCurrentServer(null);
    }
}
