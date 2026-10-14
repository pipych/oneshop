package com.oneshop.shop;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Server-side singleton shop manager/registry.
 * Provides high-level APIs to query categories, look up entries by ID, update stock,
 * and mark {@link ShopSavedData} dirty upon modifications.
 */
public final class ServerShopRegistry {
    private static final ServerShopRegistry INSTANCE = new ServerShopRegistry();

    private MinecraftServer currentServer;
    private ShopSavedData customSavedData;

    private ServerShopRegistry() {}

    /**
     * Obtains the singleton instance of the server shop registry.
     */
    public static ServerShopRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * Alias for {@link #getInstance()}.
     */
    public static ServerShopRegistry get() {
        return INSTANCE;
    }

    /**
     * Sets the active {@link MinecraftServer} instance (typically called during server starting/stopping events).
     */
    public void setCurrentServer(MinecraftServer server) {
        this.currentServer = server;
    }

    /**
     * Sets a custom or mock {@link ShopSavedData} for testing or isolated environments.
     */
    public void setCustomSavedData(ShopSavedData customSavedData) {
        this.customSavedData = customSavedData;
    }

    /**
     * Gets the active {@link MinecraftServer}, checking the registered reference or falling back to NeoForge hooks.
     */
    public MinecraftServer getServer() {
        if (this.currentServer != null) {
            return this.currentServer;
        }
        return ServerLifecycleHooks.getCurrentServer();
    }

    /**
     * Resolves the {@link ShopSavedData} instance for the active server or custom data if configured.
     */
    public ShopSavedData getSavedData() {
        if (this.customSavedData != null) {
            return this.customSavedData;
        }
        MinecraftServer server = getServer();
        if (server == null) {
            throw new IllegalStateException("Cannot access ShopSavedData: No MinecraftServer is currently active.");
        }
        return ShopSavedData.get(server);
    }

    /**
     * Resolves the {@link ShopSavedData} instance for a specific {@link MinecraftServer}.
     */
    public ShopSavedData getSavedData(MinecraftServer server) {
        if (server != null) {
            return ShopSavedData.get(server);
        }
        return getSavedData();
    }

    /**
     * Resolves the {@link ShopSavedData} instance for a specific {@link ServerLevel}.
     */
    public ShopSavedData getSavedData(ServerLevel level) {
        Objects.requireNonNull(level, "ServerLevel cannot be null");
        return ShopSavedData.get(level);
    }

    /**
     * Resolves the {@link ShopSavedData} instance from a given {@link DimensionDataStorage}.
     */
    public ShopSavedData getSavedData(DimensionDataStorage storage) {
        Objects.requireNonNull(storage, "DimensionDataStorage cannot be null");
        return ShopSavedData.get(storage);
    }

    // Category Queries
    public List<ShopCategory> getCategories() {
        return getSavedData().getCategories();
    }

    public List<ShopCategory> getCategories(MinecraftServer server) {
        return getSavedData(server).getCategories();
    }

    public Optional<ShopCategory> getCategory(String categoryId) {
        return getSavedData().getCategory(categoryId);
    }

    public Optional<ShopCategory> getCategory(MinecraftServer server, String categoryId) {
        return getSavedData(server).getCategory(categoryId);
    }

    // Entry Queries
    public Optional<ShopEntry> getEntry(String entryId) {
        return getSavedData().getEntry(entryId);
    }

    public Optional<ShopEntry> getEntry(MinecraftServer server, String entryId) {
        return getSavedData(server).getEntry(entryId);
    }

    public Optional<ShopCategory> getCategoryForEntry(String entryId) {
        return getSavedData().getCategoryForEntry(entryId);
    }

    public Optional<ShopCategory> getCategoryForEntry(MinecraftServer server, String entryId) {
        return getSavedData(server).getCategoryForEntry(entryId);
    }

    // Stock Management (Marks dirty automatically)
    /**
     * Updates the stock of the specified shop entry and marks SavedData dirty.
     *
     * @param entryId  The shop entry ID
     * @param newStock The new stock quantity (-1 for unlimited)
     * @return {@code true} if updated, {@code false} if entry was not found
     */
    public boolean updateStock(String entryId, int newStock) {
        return getSavedData().updateStock(entryId, newStock);
    }

    public boolean updateStock(MinecraftServer server, String entryId, int newStock) {
        return getSavedData(server).updateStock(entryId, newStock);
    }

    /**
     * Alias for {@link #updateStock(String, int)}.
     */
    public boolean setStock(String entryId, int newStock) {
        return updateStock(entryId, newStock);
    }

    /**
     * Modifies the stock of the entry by a relative delta (positive to increase, negative to decrease).
     * If the entry is unlimited (-1), its stock remains unlimited.
     * Marks SavedData as dirty upon modification.
     */
    public boolean modifyStock(String entryId, int delta) {
        return getSavedData().modifyStock(entryId, delta);
    }

    public boolean modifyStock(MinecraftServer server, String entryId, int delta) {
        return getSavedData(server).modifyStock(entryId, delta);
    }

    /**
     * Consumes stock from an entry if available (or unlimited).
     * Marks SavedData as dirty upon consumption.
     */
    public boolean consumeStock(String entryId, int amount) {
        return getSavedData().consumeStock(entryId, amount);
    }

    public boolean consumeStock(MinecraftServer server, String entryId, int amount) {
        return getSavedData(server).consumeStock(entryId, amount);
    }

    /**
     * Checks if the entry has at least the required amount of stock.
     */
    public boolean hasStock(String entryId, int amount) {
        return getEntry(entryId).map(e -> e.hasStock(amount)).orElse(false);
    }

    public boolean hasStock(MinecraftServer server, String entryId, int amount) {
        return getEntry(server, entryId).map(e -> e.hasStock(amount)).orElse(false);
    }

    // Category & Entry Registration (Marks dirty)
    public void registerCategory(ShopCategory category) {
        getSavedData().setCategory(category);
    }

    public void registerCategory(MinecraftServer server, ShopCategory category) {
        getSavedData(server).setCategory(category);
    }

    public boolean unregisterCategory(String categoryId) {
        return getSavedData().removeCategory(categoryId);
    }

    public boolean unregisterCategory(MinecraftServer server, String categoryId) {
        return getSavedData(server).removeCategory(categoryId);
    }

    public boolean registerEntry(String categoryId, ShopEntry entry) {
        return getSavedData().addOrUpdateEntry(categoryId, entry);
    }

    public boolean registerEntry(MinecraftServer server, String categoryId, ShopEntry entry) {
        return getSavedData(server).addOrUpdateEntry(categoryId, entry);
    }

    public boolean unregisterEntry(String categoryId, String entryId) {
        return getSavedData().removeEntry(categoryId, entryId);
    }

    public boolean unregisterEntry(MinecraftServer server, String categoryId, String entryId) {
        return getSavedData(server).removeEntry(categoryId, entryId);
    }

    /**
     * Manually marks the active SavedData as dirty.
     */
    public void markDirty() {
        getSavedData().setDirty();
    }

    public void markDirty(MinecraftServer server) {
        getSavedData(server).setDirty();
    }
}
