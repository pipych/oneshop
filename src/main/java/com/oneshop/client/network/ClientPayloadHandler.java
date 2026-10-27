package com.oneshop.client.network;

import com.oneshop.attachment.ModAttachments;
import com.oneshop.attachment.PlayerAccountData;
import com.oneshop.client.ClientBankCache;
import com.oneshop.client.ClientShopCache;
import com.oneshop.client.ClientTabCache;
import com.oneshop.client.ui.ShopScreen;
import com.oneshop.network.S2COpenShopScreenPayload;
import com.oneshop.network.S2CSyncKnyazOrdersPayload;
import com.oneshop.network.S2CSyncShopDataPayload;
import com.oneshop.network.S2CSyncTabDataPayload;
import com.oneshop.network.SyncBalancePayload;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}

    public static void handleSyncBalance(SyncBalancePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientBankCache.setBalance(payload.balance());
            if (context.player() != null) {
                context.player().setData(ModAttachments.PLAYER_ACCOUNT.get(), new PlayerAccountData(payload.balance()));
            }
            if (Minecraft.getInstance().screen instanceof ShopScreen shopScreen) {
                shopScreen.updateBalanceDisplay();
            }
        });
    }

    public static void handleSyncShopData(S2CSyncShopDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientShopCache.setCategories(payload.categories());
            if (Minecraft.getInstance().screen instanceof ShopScreen shopScreen) {
                shopScreen.refreshCategories();
            }
        });
    }

    public static void handleSyncKnyazOrders(S2CSyncKnyazOrdersPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientShopCache.setKnyazOrders(payload.orders());
            ClientShopCache.setKnyazState(
                    payload.isKnyaz(),
                    payload.hasCrown(),
                    payload.knyazName(),
                    payload.treasuryBalance()
            );
            if (Minecraft.getInstance().screen instanceof ShopScreen shopScreen) {
                shopScreen.onKnyazOrdersSynced();
            }
        });
    }

    public static void handleOpenShopScreen(S2COpenShopScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new ShopScreen(payload.isAdmin()));
        });
    }

    public static void handleSyncTabData(S2CSyncTabDataPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientTabCache.update(
                    payload.stateBrands(),
                    payload.taxDebt(),
                    payload.taxRate(),
                    payload.characterNames(),
                    payload.knyazUuid(),
                    payload.knyazName()
            );
        });
    }
}
