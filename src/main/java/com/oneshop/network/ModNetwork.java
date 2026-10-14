package com.oneshop.network;

import com.oneshop.OneShop;
import com.oneshop.client.network.ClientPayloadHandler;
import com.oneshop.service.PlayerAccountService;
import com.oneshop.service.ShopTransactionService;
import com.oneshop.shop.ServerShopRegistry;
import com.oneshop.shop.ShopCategory;
import com.oneshop.shop.ShopEntry;
import com.oneshop.shop.ShopSavedData;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class ModNetwork {
    private ModNetwork() {}

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(OneShop.MODID);

        // Clientbound payloads
        registrar.playToClient(
                SyncBalancePayload.TYPE,
                SyncBalancePayload.STREAM_CODEC,
                ClientPayloadHandler::handleSyncBalance
        );

        registrar.playToClient(
                S2CSyncShopDataPayload.TYPE,
                S2CSyncShopDataPayload.STREAM_CODEC,
                ClientPayloadHandler::handleSyncShopData
        );

        registrar.playToClient(
                S2COpenShopScreenPayload.TYPE,
                S2COpenShopScreenPayload.STREAM_CODEC,
                ClientPayloadHandler::handleOpenShopScreen
        );

        // Serverbound payloads
        registrar.playToServer(
                C2SExecuteTransactionPayload.TYPE,
                C2SExecuteTransactionPayload.STREAM_CODEC,
                ModNetwork::handleExecuteTransactionOnServer
        );

        registrar.playToServer(
                C2SDepositCashPayload.TYPE,
                C2SDepositCashPayload.STREAM_CODEC,
                ModNetwork::handleDepositCashOnServer
        );

        registrar.playToServer(
                C2SWithdrawCashPayload.TYPE,
                C2SWithdrawCashPayload.STREAM_CODEC,
                ModNetwork::handleWithdrawCashOnServer
        );

        registrar.playToServer(
                C2STransferMoneyPayload.TYPE,
                C2STransferMoneyPayload.STREAM_CODEC,
                ModNetwork::handleTransferMoneyOnServer
        );

        registrar.playToServer(
                C2SOpenShopPayload.TYPE,
                C2SOpenShopPayload.STREAM_CODEC,
                ModNetwork::handleOpenShopOnServer
        );

        registrar.playToServer(
                C2SAdminUpdateEntryPayload.TYPE,
                C2SAdminUpdateEntryPayload.STREAM_CODEC,
                ModNetwork::handleAdminUpdateEntryOnServer
        );

        registrar.playToServer(
                C2SAdminUpdateCategoryPayload.TYPE,
                C2SAdminUpdateCategoryPayload.STREAM_CODEC,
                ModNetwork::handleAdminUpdateCategoryOnServer
        );
    }

    private static void handleOpenShopOnServer(C2SOpenShopPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                boolean isAdmin = payload.isAdmin();
                if (isAdmin && !player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("oneshop.admin.no_permission").withStyle(ChatFormatting.RED));
                    return;
                }
                PlayerAccountService.syncBalance(player);
                ShopTransactionService.syncShopDataIfChanged(player);
                PacketDistributor.sendToPlayer(player, new S2COpenShopScreenPayload(isAdmin));
            }
        });
    }

    private static void handleExecuteTransactionOnServer(C2SExecuteTransactionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ShopTransactionService.executeTransaction(
                        serverPlayer,
                        payload.categoryId(),
                        payload.entryId(),
                        payload.quantity(),
                        payload.isBuy()
                );
            }
        });
    }

    private static void handleDepositCashOnServer(C2SDepositCashPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ShopTransactionService.depositCash(serverPlayer, payload.amount());
            }
        });
    }

    private static void handleWithdrawCashOnServer(C2SWithdrawCashPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                ShopTransactionService.withdrawCash(serverPlayer, payload.amount());
            }
        });
    }

    private static void handleTransferMoneyOnServer(C2STransferMoneyPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                ShopTransactionService.transferMoney(sender, payload.targetPlayerUuid(), payload.amount());
            }
        });
    }

    private static void handleAdminUpdateEntryOnServer(C2SAdminUpdateEntryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("oneshop.admin.no_permission").withStyle(ChatFormatting.RED));
                    return;
                }
                MinecraftServer server = player.server;
                ShopSavedData savedData = ServerShopRegistry.getInstance().getSavedData(server);
                if (payload.delete()) {
                    savedData.removeEntry(payload.categoryId(), payload.entry().id());
                    player.sendSystemMessage(Component.translatable("oneshop.admin.entry_deleted", payload.entry().id()).withStyle(ChatFormatting.YELLOW));
                } else {
                    savedData.addOrUpdateEntry(payload.categoryId(), payload.entry());
                    player.sendSystemMessage(Component.translatable("oneshop.admin.entry_saved", payload.entry().id()).withStyle(ChatFormatting.GREEN));
                }
                savedData.setDirty();
                ShopTransactionService.broadcastShopData(server);
            }
        });
    }

    private static void handleAdminUpdateCategoryOnServer(C2SAdminUpdateCategoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (!player.hasPermissions(2)) {
                    player.sendSystemMessage(Component.translatable("oneshop.admin.no_permission").withStyle(ChatFormatting.RED));
                    return;
                }
                MinecraftServer server = player.server;
                ShopSavedData savedData = ServerShopRegistry.getInstance().getSavedData(server);
                if (payload.delete()) {
                    savedData.removeCategory(payload.category().id());
                    player.sendSystemMessage(Component.translatable("oneshop.admin.category_deleted", payload.category().id()).withStyle(ChatFormatting.YELLOW));
                } else {
                    var existingOpt = savedData.getCategory(payload.category().id());
                    ShopCategory toSave = payload.category();
                    if (existingOpt.isPresent() && toSave.entries().isEmpty() && !existingOpt.get().entries().isEmpty()) {
                        toSave = toSave.withEntries(existingOpt.get().entries());
                    }
                    savedData.setCategory(toSave);
                    player.sendSystemMessage(Component.translatable("oneshop.admin.category_saved", payload.category().id()).withStyle(ChatFormatting.GREEN));
                }
                savedData.setDirty();
                ShopTransactionService.broadcastShopData(server);
            }
        });
    }
}
