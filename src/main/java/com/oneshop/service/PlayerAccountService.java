package com.oneshop.service;

import com.oneshop.attachment.ModAttachments;
import com.oneshop.attachment.PlayerAccountData;
import com.oneshop.network.SyncBalancePayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Service managing player accounts, balance updates, and network synchronization.
 */
public final class PlayerAccountService {
    private PlayerAccountService() {}

    public static PlayerAccountData getAccount(Player player) {
        return player.getData(ModAttachments.PLAYER_ACCOUNT.get());
    }

    public static double getBalance(Player player) {
        return getAccount(player).balance();
    }

    public static void setBalance(ServerPlayer player, double newBalance) {
        player.setData(ModAttachments.PLAYER_ACCOUNT.get(), new PlayerAccountData(newBalance));
        syncBalance(player);
    }

    public static void addBalance(ServerPlayer player, double amount) {
        setBalance(player, getBalance(player) + amount);
    }

    public static boolean withdraw(ServerPlayer player, double amount) {
        double current = getBalance(player);
        if (current >= amount) {
            setBalance(player, current - amount);
            return true;
        }
        return false;
    }

    public static void syncBalance(ServerPlayer player) {
        double balance = getBalance(player);
        PacketDistributor.sendToPlayer(player, new SyncBalancePayload(balance));
    }
}
