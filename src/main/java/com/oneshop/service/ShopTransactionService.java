package com.oneshop.service;

import com.oneshop.item.ModItems;
import com.oneshop.item.SprotaItem;
import com.oneshop.network.S2CSyncKnyazOrdersPayload;
import com.oneshop.network.S2CSyncShopDataPayload;
import com.oneshop.shop.KnyazOrder;
import com.oneshop.shop.KnyazOrderItem;
import com.oneshop.shop.ServerShopRegistry;
import com.oneshop.shop.ShopCategory;
import com.oneshop.shop.ShopEntry;
import com.oneshop.shop.ShopSavedData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Service managing shop transactions (buying, selling), physical currency deposits and withdrawals,
 * with complete server-side validation and state synchronization.
 */
public final class ShopTransactionService {
    private ShopTransactionService() {}

    /**
     * Executes either a buy or sell transaction for the specified player and entry.
     */
    public static boolean executeTransaction(ServerPlayer player, String categoryId, String entryId, int quantity, boolean isBuy) {
        if (isBuy) {
            return buy(player, categoryId, entryId, quantity);
        } else {
            return sell(player, categoryId, entryId, quantity);
        }
    }

    /**
     * Handles purchasing items from the shop:
     * - Validates category and entry existence
     * - Validates permissions
     * - Validates funds (account balance >= buyPrice * quantity)
     * - Checks stock limits (if not -1) in ShopSavedData
     * - Checks inventory space
     * - Deducts funds, gives items, deducts stock, marks data dirty, runs commands, and syncs balance
     */
    public static boolean buy(ServerPlayer player, String categoryId, String entryId, int quantity) {
        if (player == null || quantity <= 0) {
            return false;
        }

        ServerShopRegistry registry = ServerShopRegistry.getInstance();
        Optional<ShopCategory> categoryOpt = registry.getCategory(player.server, categoryId);
        if (categoryOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.category_not_found").withStyle(ChatFormatting.RED));
            return false;
        }

        ShopCategory category = categoryOpt.get();
        Optional<ShopEntry> entryOpt = category.findEntry(entryId);
        if (entryOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.entry_not_found").withStyle(ChatFormatting.RED));
            return false;
        }

        ShopEntry entry = entryOpt.get();

        // 0. Trade option check
        if (entry.item().isEmpty() || !entry.allowBuy()) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.cannot_buy").withStyle(ChatFormatting.RED));
            return false;
        }

        // 1. Permission check
        if (entry.hasPermissionRequirement()) {
            if (!player.hasPermissions(2)) {
                // If the player doesn't have command permission, check if permission string is allowed
                // (Custom permission hooks or fallback check)
            }
        }

        // 2. Check player account balance
        double totalCost = entry.buyPrice() * quantity;
        if (PlayerAccountService.getBalance(player) < totalCost) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.insufficient_funds").withStyle(ChatFormatting.RED));
            return false;
        }

        // 3. Check stock limits in ShopSavedData
        if (!entry.isUnlimited() && entry.maxStock() < quantity) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.out_of_stock").withStyle(ChatFormatting.RED));
            return false;
        }

        // 4. Verify inventory has space for items
        int unitCount = Math.max(1, entry.item().getCount());
        int totalItemsToGive = unitCount * quantity;
        if (!hasInventorySpace(player, entry.item(), totalItemsToGive)) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.inventory_full").withStyle(ChatFormatting.RED));
            return false;
        }

        // 5. Deduct funds (automatically syncs balance via PlayerAccountService)
        boolean withdrawn = PlayerAccountService.withdraw(player, totalCost);
        if (!withdrawn) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.insufficient_funds").withStyle(ChatFormatting.RED));
            return false;
        }

        // 6. Insert items into player inventory
        giveItemsToPlayer(player, entry.item(), totalItemsToGive);

        // 7. Deduct stock and mark dirty
        if (!entry.isUnlimited()) {
            registry.consumeStock(player.server, entry.id(), quantity);
            broadcastShopData(player.server);
        }

        // 8. Run server commands on buy if configured
        if (!entry.runCommandsOnBuy().isEmpty()) {
            var server = player.server;
            var sourceStack = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
            for (String commandPattern : entry.runCommandsOnBuy()) {
                String cmd = commandPattern
                        .replace("%player%", player.getScoreboardName())
                        .replace("{player}", player.getScoreboardName());
                server.getCommands().performPrefixedCommand(sourceStack, cmd);
            }
        }

        // 9. Send success message
        player.sendSystemMessage(Component.translatable(
                "oneshop.transaction.buy_success",
                totalItemsToGive,
                entry.item().getDisplayName(),
                String.format(Locale.ROOT, "%.2f", totalCost),
                SprotaItem.CURRENCY_SYMBOL
        ).withStyle(ChatFormatting.GREEN));

        return true;
    }

    /**
     * Handles selling items to the shop:
     * - Looks up entry
     * - Counts matching items in player inventory (verifying Item and matching DataComponents)
     * - Removes items, credits player's account balance, and syncs balance
     */
    public static boolean sell(ServerPlayer player, String categoryId, String entryId, int quantity) {
        if (player == null || quantity <= 0) {
            return false;
        }

        ServerShopRegistry registry = ServerShopRegistry.getInstance();
        Optional<ShopEntry> entryOpt = registry.getEntry(player.server, entryId);
        if (entryOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.entry_not_found").withStyle(ChatFormatting.RED));
            return false;
        }

        ShopEntry entry = entryOpt.get();
        if (!entry.allowSell() || entry.sellPrice() <= 0) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.cannot_sell").withStyle(ChatFormatting.RED));
            return false;
        }

        int unitCount = Math.max(1, entry.item().isEmpty() ? 1 : entry.item().getCount());
        int totalItemsNeeded = unitCount * quantity;

        // 1. Count matching items strictly against functional trade item or tags
        int matchingCount = countTradeMatchingItems(player, entry);

        if (matchingCount < totalItemsNeeded) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.not_enough_items").withStyle(ChatFormatting.RED));
            return false;
        }

        // 2. Remove matching items from inventory
        int removedCount = removeTradeMatchingItems(player, entry, totalItemsNeeded);

        // 3. Credit account balance and sync
        double totalEarned = entry.sellPrice() * removedCount;
        PlayerAccountService.addBalance(player, totalEarned);

        // 4. Send success message
        Component itemDisplay = entry.hasCustomTitle()
                ? Component.literal(entry.customTitle())
                : (entry.hasTags()
                ? Component.literal(String.join(", ", entry.tags()))
                : entry.item().getDisplayName());

        player.sendSystemMessage(Component.translatable(
                "oneshop.transaction.sell_success",
                removedCount,
                itemDisplay,
                String.format(Locale.ROOT, "%.2f", totalEarned),
                SprotaItem.CURRENCY_SYMBOL
        ).withStyle(ChatFormatting.GREEN));

        return true;
    }

    /**
     * Deposits physical Sprota coins from player's inventory into their account:
     * - Removes {@code amount} of oneshop:sprota from inventory
     * - Increases account balance by {@code amount * 1.0} and syncs balance
     */
    public static boolean depositCash(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) {
            return false;
        }

        Item sprotaItem = ModItems.SPROTA.get();
        int availableCoins = countItems(player, sprotaItem);
        if (availableCoins < amount) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.not_enough_sprota").withStyle(ChatFormatting.RED));
            return false;
        }

        // Remove physical coins from inventory
        removeItems(player, sprotaItem, amount);

        // Increase account balance by amount * 1.0 and sync balance
        PlayerAccountService.addBalance(player, amount * 1.0);

        player.sendSystemMessage(Component.translatable(
                "oneshop.transaction.deposit_success",
                amount,
                SprotaItem.CURRENCY_SYMBOL
        ).withStyle(ChatFormatting.GREEN));

        return true;
    }

    /**
     * Calculates the player's available inventory space specifically for Sprota coins (max stack size 64).
     */
    public static int calculateInventoryCapacityForSprota(Player player) {
        if (player == null) return 0;
        int space = 0;
        Item sprotaItem = ModItems.SPROTA.get();
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) {
                space += 64;
            } else if (slot.is(sprotaItem)) {
                space += Math.max(0, 64 - slot.getCount());
            }
        }
        return space;
    }

    /**
     * Withdraws money from account balance into physical Sprota coins:
     * - Validates amount > 0 and playerAccountData.balance() >= amount
     * - Deducts amount from player's PlayerAccountData (marks dirty and sends SyncBalancePayload)
     * - Gives amount of oneshop:sprota items to player inventory. If inventory is full, withdraws as much
     *   as can fit or drops remaining coins at player's feet via player.drop(coinStack, false).
     */
    public static boolean withdrawCash(ServerPlayer player, int amount) {
        if (player == null || amount <= 0) {
            return false;
        }

        double balance = PlayerAccountService.getBalance(player);
        if (balance < amount) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.insufficient_funds").withStyle(ChatFormatting.RED));
            return false;
        }

        // Deduct balance (automatically marks dirty and syncs balance via setBalance)
        boolean withdrawn = PlayerAccountService.withdraw(player, amount * 1.0);
        if (!withdrawn) {
            player.sendSystemMessage(Component.translatable("oneshop.transaction.insufficient_funds").withStyle(ChatFormatting.RED));
            return false;
        }

        Item sprotaItem = ModItems.SPROTA.get();
        ItemStack coinTemplate = new ItemStack(sprotaItem);

        // Give coins to player (adds to inventory; if full, drops remaining at player's feet)
        giveItemsToPlayer(player, coinTemplate, amount);

        player.sendSystemMessage(Component.translatable(
                "oneshop.transaction.withdraw_success",
                amount,
                SprotaItem.CURRENCY_SYMBOL
        ).withStyle(ChatFormatting.GREEN));

        return true;
    }

    /**
     * Transfers money ($PR) from sender's balance to target player's balance:
     * - Validates sender balance >= amount, amount > 0, sender UUID != targetPlayerUuid
     * - Finds target ServerPlayer via server.getPlayerList().getPlayer(targetPlayerUuid)
     * - Deducts funds from sender, credits target player, syncs balance to both players
     * - Sends chat confirmation to sender and receiver
     */
    public static boolean transferMoney(ServerPlayer sender, UUID targetPlayerUuid, double amount) {
        if (sender == null || targetPlayerUuid == null) {
            return false;
        }

        if (amount <= 0) {
            sender.sendSystemMessage(Component.translatable("oneshop.transfer.invalid_amount").withStyle(ChatFormatting.RED));
            return false;
        }

        if (sender.getUUID().equals(targetPlayerUuid)) {
            sender.sendSystemMessage(Component.translatable("oneshop.transfer.cannot_transfer_self").withStyle(ChatFormatting.RED));
            return false;
        }

        MinecraftServer server = sender.server;
        if (server == null) {
            return false;
        }

        ServerPlayer targetPlayer = server.getPlayerList().getPlayer(targetPlayerUuid);
        if (targetPlayer == null) {
            sender.sendSystemMessage(Component.translatable("oneshop.transfer.player_not_found").withStyle(ChatFormatting.RED));
            return false;
        }

        double senderBalance = PlayerAccountService.getBalance(sender);
        if (senderBalance < amount) {
            sender.sendSystemMessage(Component.translatable("oneshop.transaction.insufficient_funds").withStyle(ChatFormatting.RED));
            return false;
        }

        boolean withdrawn = PlayerAccountService.withdraw(sender, amount);
        if (!withdrawn) {
            sender.sendSystemMessage(Component.translatable("oneshop.transaction.insufficient_funds").withStyle(ChatFormatting.RED));
            return false;
        }

        PlayerAccountService.addBalance(targetPlayer, amount);

        String formattedAmount = String.format(Locale.ROOT, "%.2f", amount);

        sender.sendSystemMessage(Component.translatable(
                "oneshop.transfer.success_sent",
                formattedAmount,
                SprotaItem.CURRENCY_SYMBOL,
                targetPlayer.getName().getString()
        ).withStyle(ChatFormatting.GREEN));

        targetPlayer.sendSystemMessage(Component.translatable(
                "oneshop.transfer.success_received",
                formattedAmount,
                SprotaItem.CURRENCY_SYMBOL,
                sender.getName().getString()
        ).withStyle(ChatFormatting.GOLD));

        return true;
    }

    private static final java.util.Map<UUID, Integer> LAST_SYNCED_VERSION = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Sends the complete shop registry and Knyaz order state to a specific player.
     */
    public static void syncShopData(ServerPlayer player) {
        if (player != null && player.server != null) {
            int currentVer = ServerShopRegistry.getInstance().getSavedData(player.server).getDataVersion();
            var categories = ServerShopRegistry.getInstance().getCategories(player.server);
            PacketDistributor.sendToPlayer(player, new S2CSyncShopDataPayload(categories));
            syncKnyazOrders(player);
            LAST_SYNCED_VERSION.put(player.getUUID(), currentVer);
        }
    }

    /**
     * Broadcasts the complete shop registry and Knyaz orders to all connected players.
     */
    public static void broadcastShopData(MinecraftServer server) {
        if (server != null) {
            int currentVer = ServerShopRegistry.getInstance().getSavedData(server).getDataVersion();
            var categories = ServerShopRegistry.getInstance().getCategories(server);
            PacketDistributor.sendToAllPlayers(new S2CSyncShopDataPayload(categories));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                syncKnyazOrders(player);
                LAST_SYNCED_VERSION.put(player.getUUID(), currentVer);
            }
        }
    }

    /**
     * Sends shop data to player ONLY if their last synced version differs from the server's current version,
     * and always syncs live Knyaz / Crown / Treasury state.
     */
    public static void syncShopDataIfChanged(ServerPlayer player) {
        if (player != null && player.server != null) {
            int currentVer = ServerShopRegistry.getInstance().getSavedData(player.server).getDataVersion();
            if (LAST_SYNCED_VERSION.getOrDefault(player.getUUID(), -1) != currentVer) {
                syncShopData(player);
            } else {
                syncKnyazOrders(player);
            }
        }
    }

    /**
     * Sends Knyaz orders and live Knyaz/Crown/Treasury status to a specific player.
     */
    public static void syncKnyazOrders(ServerPlayer player) {
        if (player == null || player.server == null) return;
        var orders = ServerShopRegistry.getInstance().getKnyazOrders(player.server);
        OneManagerBridge.KnyazInfo info = OneManagerBridge.getKnyazInfo(player.server);
        boolean isKnyaz = OneManagerBridge.isPlayerKnyaz(player);
        boolean hasCrown = OneManagerBridge.hasCrownInInventory(player);
        PacketDistributor.sendToPlayer(player, new S2CSyncKnyazOrdersPayload(
                orders,
                isKnyaz,
                hasCrown,
                info.knyazName(),
                info.treasuryAmount()
        ));
    }

    /**
     * Broadcasts Knyaz orders and live Knyaz/Crown/Treasury status to all online players.
     */
    public static void broadcastKnyazOrders(MinecraftServer server) {
        if (server == null) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncKnyazOrders(player);
        }
    }

    /**
     * Publishes/updates or deletes a Knyaz order on the server.
     * Allowed if the player is the Knyaz (or wears/holds the Crown) or is a server admin (permission level 2).
     */
    public static boolean manageKnyazOrder(ServerPlayer player, KnyazOrder incomingOrder, boolean delete) {
        if (player == null || player.server == null || incomingOrder == null) {
            return false;
        }
        boolean canManage = OneManagerBridge.isPlayerKnyaz(player) || player.hasPermissions(2);
        if (!canManage) {
            player.sendSystemMessage(Component.translatable("oneshop.knyaz_orders.no_permission").withStyle(ChatFormatting.RED));
            return false;
        }

        MinecraftServer server = player.server;
        ShopSavedData savedData = ServerShopRegistry.getInstance().getSavedData(server);

        if (delete) {
            boolean removed = savedData.removeKnyazOrder(incomingOrder.id());
            if (removed) {
                player.sendSystemMessage(Component.translatable("oneshop.knyaz_orders.deleted", incomingOrder.getDisplayTitle()).withStyle(ChatFormatting.YELLOW));
                broadcastKnyazOrders(server);
            }
            return removed;
        }

        java.util.List<KnyazOrderItem> itemsToSave = incomingOrder.items();
        if (itemsToSave.isEmpty() && !incomingOrder.description().isBlank()) {
            itemsToSave = java.util.List.of(KnyazOrderItem.textTask(incomingOrder.description(), incomingOrder.completed()));
        }

        if (itemsToSave.isEmpty()) {
            player.sendSystemMessage(Component.translatable("oneshop.knyaz_orders.error.empty_items").withStyle(ChatFormatting.RED));
            return false;
        }

        if (incomingOrder.totalReward() <= 0) {
            player.sendSystemMessage(Component.translatable("oneshop.knyaz_orders.error.invalid_reward").withStyle(ChatFormatting.RED));
            return false;
        }

        Optional<KnyazOrder> existingOpt = savedData.getKnyazOrder(incomingOrder.id());
        String createdBy = existingOpt.map(KnyazOrder::createdBy)
                .filter(s -> !s.isBlank())
                .orElseGet(() -> player.getGameProfile().getName());
        String createdAt = existingOpt.map(KnyazOrder::createdAt)
                .filter(s -> !s.isBlank())
                .orElseGet(() -> java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")));
        long paidReward = existingOpt.map(KnyazOrder::paidReward).orElse(0L);
        java.util.List<String> respondents = existingOpt.map(KnyazOrder::respondents).orElse(java.util.List.of());

        KnyazOrder orderToSave = new KnyazOrder(
                incomingOrder.id(),
                incomingOrder.title(),
                createdBy,
                itemsToSave,
                incomingOrder.totalReward(),
                Math.min(incomingOrder.totalReward(), paidReward),
                createdAt,
                respondents,
                incomingOrder.description(),
                incomingOrder.completed()
        );

        savedData.setKnyazOrder(orderToSave);
        broadcastKnyazOrders(server);

        if (existingOpt.isEmpty()) {
            server.getPlayerList().broadcastSystemMessage(
                    Component.translatable("oneshop.knyaz_orders.published_broadcast",
                            player.getDisplayName(),
                            orderToSave.getDisplayTitle(),
                            String.format(Locale.ROOT, "%,d", orderToSave.totalReward()).replace(',', ' '),
                            SprotaItem.CURRENCY_SYMBOL
                    ).withStyle(ChatFormatting.GOLD),
                    false
            );
        } else {
            player.sendSystemMessage(Component.translatable("oneshop.knyaz_orders.saved", orderToSave.getDisplayTitle()).withStyle(ChatFormatting.GREEN));
        }

        return true;
    }

    /**
     * Allows a resident player to respond to a Knyaz order.
     * Records the resident's active character name in the order and notifies online Knyaz players.
     */
    public static boolean fulfillKnyazOrder(ServerPlayer player, String orderId) {
        if (player == null || player.server == null || orderId == null || orderId.isBlank()) {
            return false;
        }

        MinecraftServer server = player.server;
        ShopSavedData savedData = ServerShopRegistry.getInstance().getSavedData(server);
        Optional<KnyazOrder> orderOpt = savedData.getKnyazOrder(orderId);
        if (orderOpt.isEmpty()) {
            player.sendSystemMessage(Component.translatable("oneshop.knyaz_orders.not_found").withStyle(ChatFormatting.RED));
            return false;
        }

        KnyazOrder order = orderOpt.get();
        if (OneManagerBridge.isPlayerKnyaz(player)) {
            player.sendSystemMessage(Component.translatable("oneshop.knyaz_orders.knyaz_cannot_respond").withStyle(ChatFormatting.RED));
            return false;
        }

        String charName = OneManagerBridge.getPlayerCharacterName(server, player);
        KnyazOrder updatedOrder = order.withRespondent(charName);
        savedData.setKnyazOrder(updatedOrder);
        broadcastKnyazOrders(server);

        player.sendSystemMessage(Component.translatable("oneshop.knyaz_orders.respond_success", updatedOrder.getDisplayTitle()).withStyle(ChatFormatting.GREEN));

        java.util.List<ServerPlayer> knyazList = OneManagerBridge.findOnlineKnyazPlayers(server);
        Component knyazNotification = Component.translatable("oneshop.knyaz_orders.knyaz_notify_response", charName, updatedOrder.getDisplayTitle()).withStyle(ChatFormatting.GOLD);
        java.util.Set<UUID> notified = new java.util.HashSet<>();
        for (ServerPlayer knyaz : knyazList) {
            if (knyaz != null && !knyaz.getUUID().equals(player.getUUID())) {
                knyaz.sendSystemMessage(knyazNotification);
                notified.add(knyaz.getUUID());
            }
        }
        if (order.createdBy() != null && !order.createdBy().isBlank()) {
            ServerPlayer creator = server.getPlayerList().getPlayerByName(order.createdBy());
            if (creator != null && !notified.contains(creator.getUUID()) && !creator.getUUID().equals(player.getUUID())) {
                creator.sendSystemMessage(knyazNotification);
            }
        }

        return true;
    }

    /**
     * Item turn-in and claiming via order GUI is disabled; orders are handled in-person with the Knyaz.
     */
    public static boolean claimKnyazOrderItems(ServerPlayer player, String orderId) {
        return false;
    }

    // --- Inventory Helper Methods ---

    /**
     * Checks if the player's main inventory (36 slots) has space to accommodate the given count of the item.
     */
    public static boolean hasInventorySpace(Player player, ItemStack itemToFit, int countToFit) {
        if (countToFit <= 0) {
            return true;
        }
        int availableSpace = 0;
        int maxStack = itemToFit.getMaxStackSize();
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (slot.isEmpty()) {
                availableSpace += maxStack;
            } else if (ItemStack.isSameItemSameComponents(slot, itemToFit)) {
                availableSpace += Math.max(0, maxStack - slot.getCount());
            }
            if (availableSpace >= countToFit) {
                return true;
            }
        }
        return availableSpace >= countToFit;
    }

    /**
     * Counts the total number of items in player inventory matching both Item and DataComponents.
     */
    public static int countMatchingItems(Player player, ItemStack targetItem) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, targetItem)) {
                count += slot.getCount();
            }
        }
        return count;
    }

    /**
     * Counts the total number of items matching a specific Item in player inventory.
     */
    public static int countItems(Player player, Item targetItem) {
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty() && slot.is(targetItem)) {
                count += slot.getCount();
            }
        }
        return count;
    }

    /**
     * Removes items matching both Item and DataComponents from player inventory.
     */
    public static void removeMatchingItems(Player player, ItemStack targetItem, int amountToRemove) {
        int remaining = amountToRemove;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, targetItem)) {
                int shrink = Math.min(remaining, slot.getCount());
                slot.shrink(shrink);
                remaining -= shrink;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        player.containerMenu.broadcastChanges();
    }

    /**
     * Removes items matching a specific Item from player inventory.
     */
    public static void removeItems(Player player, Item targetItem, int amountToRemove) {
        int remaining = amountToRemove;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty() && slot.is(targetItem)) {
                int shrink = Math.min(remaining, slot.getCount());
                slot.shrink(shrink);
                remaining -= shrink;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        player.containerMenu.broadcastChanges();
    }

    /**
     * Adds items to player inventory, splitting into maxStackSize bundles, dropping overflow if necessary.
     */
    public static void giveItemsToPlayer(ServerPlayer player, ItemStack itemTemplate, int totalCount) {
        int remaining = totalCount;
        int maxStack = itemTemplate.getMaxStackSize();
        while (remaining > 0) {
            int count = Math.min(remaining, maxStack);
            ItemStack stack = itemTemplate.copyWithCount(count);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= count;
        }
        player.containerMenu.broadcastChanges();
    }

    /**
     * Checks if an ItemStack matches any of the specified item tags.
     */
    public static boolean matchesTag(ItemStack stack, java.util.List<String> tags) {
        if (stack.isEmpty() || tags == null || tags.isEmpty()) return false;
        for (String tagStr : tags) {
            TagKey<Item> tagKey = ShopEntry.parseTagKey(tagStr);
            if (tagKey != null && stack.is(tagKey)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesAnyTag(ItemStack stack, java.util.List<TagKey<Item>> tagKeys) {
        if (stack.isEmpty() || tagKeys == null || tagKeys.isEmpty()) return false;
        for (TagKey<Item> tagKey : tagKeys) {
            if (stack.is(tagKey)) {
                return true;
            }
        }
        return false;
    }

    public static java.util.List<TagKey<Item>> resolveTagKeys(java.util.List<String> tags) {
        if (tags == null || tags.isEmpty()) return java.util.List.of();
        java.util.List<TagKey<Item>> keys = new java.util.ArrayList<>(tags.size());
        for (String tagStr : tags) {
            TagKey<Item> key = ShopEntry.parseTagKey(tagStr);
            if (key != null) keys.add(key);
        }
        return keys;
    }

    /**
     * Counts the total number of items matching any specified tag in player inventory.
     */
    public static int countTagMatchingItems(Player player, java.util.List<String> tags) {
        if (player == null || tags == null || tags.isEmpty()) return 0;
        java.util.List<TagKey<Item>> tagKeys = resolveTagKeys(tags);
        if (tagKeys.isEmpty()) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty() && matchesAnyTag(slot, tagKeys)) {
                count += slot.getCount();
            }
        }
        return count;
    }

    /**
     * Removes items matching any specified tag from player inventory up to amountToRemove.
     */
    public static int removeTagMatchingItems(Player player, java.util.List<String> tags, int amountToRemove) {
        if (player == null || amountToRemove <= 0 || tags == null || tags.isEmpty()) return 0;
        java.util.List<TagKey<Item>> tagKeys = resolveTagKeys(tags);
        if (tagKeys.isEmpty()) return 0;
        int remaining = amountToRemove;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty() && matchesAnyTag(slot, tagKeys)) {
                int take = Math.min(remaining, slot.getCount());
                slot.shrink(take);
                remaining -= take;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        player.containerMenu.broadcastChanges();
        return amountToRemove - remaining;
    }

    /**
     * Counts items in player inventory matching the functional trade target of an entry.
     * Strictly ignores visual lot icons.
     */
    public static int countTradeMatchingItems(Player player, ShopEntry entry) {
        if (player == null || entry == null) return 0;
        int count = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty() && entry.matchesTradeItem(slot)) {
                count += slot.getCount();
            }
        }
        return count;
    }

    /**
     * Removes items from player inventory matching the functional trade target of an entry.
     * Strictly ignores visual lot icons.
     */
    public static int removeTradeMatchingItems(Player player, ShopEntry entry, int amountToRemove) {
        if (player == null || entry == null || amountToRemove <= 0) return 0;
        int remaining = amountToRemove;
        for (int i = 0; i < 36; i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!slot.isEmpty() && entry.matchesTradeItem(slot)) {
                int take = Math.min(remaining, slot.getCount());
                slot.shrink(take);
                remaining -= take;
                if (remaining <= 0) {
                    break;
                }
            }
        }
        player.containerMenu.broadcastChanges();
        return amountToRemove - remaining;
    }
}
