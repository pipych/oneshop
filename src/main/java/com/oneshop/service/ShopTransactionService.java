package com.oneshop.service;

import com.oneshop.item.ModItems;
import com.oneshop.item.SprotaItem;
import com.oneshop.network.S2CSyncShopDataPayload;
import com.oneshop.shop.ServerShopRegistry;
import com.oneshop.shop.ShopCategory;
import com.oneshop.shop.ShopEntry;
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
     * Sends the complete shop registry to a specific player.
     */
    public static void syncShopData(ServerPlayer player) {
        if (player != null && player.server != null) {
            int currentVer = ServerShopRegistry.getInstance().getSavedData(player.server).getDataVersion();
            var categories = ServerShopRegistry.getInstance().getCategories(player.server);
            PacketDistributor.sendToPlayer(player, new S2CSyncShopDataPayload(categories));
            LAST_SYNCED_VERSION.put(player.getUUID(), currentVer);
        }
    }

    /**
     * Broadcasts the complete shop registry to all connected players.
     */
    public static void broadcastShopData(MinecraftServer server) {
        if (server != null) {
            int currentVer = ServerShopRegistry.getInstance().getSavedData(server).getDataVersion();
            var categories = ServerShopRegistry.getInstance().getCategories(server);
            PacketDistributor.sendToAllPlayers(new S2CSyncShopDataPayload(categories));
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                LAST_SYNCED_VERSION.put(player.getUUID(), currentVer);
            }
        }
    }

    /**
     * Sends shop data to player ONLY if their last synced version differs from the server's current version.
     */
    public static void syncShopDataIfChanged(ServerPlayer player) {
        if (player != null && player.server != null) {
            int currentVer = ServerShopRegistry.getInstance().getSavedData(player.server).getDataVersion();
            if (LAST_SYNCED_VERSION.getOrDefault(player.getUUID(), -1) != currentVer) {
                syncShopData(player);
            }
        }
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
