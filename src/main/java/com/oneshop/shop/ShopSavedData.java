package com.oneshop.shop;

import com.mojang.serialization.Codec;
import com.oneshop.OneShop;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * World-saved data storage for OneShop shop categories and entries.
 * Persists data to {@code saves/<world>/data/oneshop_shop.dat}.
 */
public class ShopSavedData extends SavedData {
    public static final String DATA_NAME = "oneshop_shop";
    public static final String FILE_NAME = DATA_NAME + ".dat";

    public static final Codec<List<ShopCategory>> CATEGORIES_CODEC = ShopCategory.CODEC.listOf();

    private final Map<String, ShopCategory> categories = new LinkedHashMap<>();
    private List<ShopCategory> cachedSortedCategories = null;
    private final Map<String, ShopEntry> entryLookupCache = new java.util.HashMap<>();
    private int dataVersion = 1;

    public ShopSavedData() {
    }

    /**
     * Factory instance used by {@link DimensionDataStorage}.
     * When the file does not exist, {@link #createDefault()} is invoked to populate default items.
     */
    public static SavedData.Factory<ShopSavedData> factory() {
        return new SavedData.Factory<>(
                ShopSavedData::createDefault,
                ShopSavedData::load,
                null
        );
    }

    /**
     * Obtains the {@link ShopSavedData} instance from the server's overworld data storage.
     */
    public static ShopSavedData get(MinecraftServer server) {
        Objects.requireNonNull(server, "MinecraftServer cannot be null");
        return get(server.overworld().getDataStorage());
    }

    /**
     * Obtains the {@link ShopSavedData} instance from a {@link ServerLevel}.
     */
    public static ShopSavedData get(ServerLevel level) {
        Objects.requireNonNull(level, "ServerLevel cannot be null");
        return get(level.getServer());
    }

    /**
     * Obtains the {@link ShopSavedData} instance from the given {@link DimensionDataStorage}.
     */
    public static ShopSavedData get(DimensionDataStorage storage) {
        Objects.requireNonNull(storage, "DimensionDataStorage cannot be null");
        return storage.computeIfAbsent(factory(), DATA_NAME);
    }

    /**
     * Creates a new instance populated with default categories and items, marked dirty.
     */
    public static ShopSavedData createDefault() {
        OneShop.LOGGER.info("Shop saved data not found. Populating default shop data...");
        ShopSavedData data = new ShopSavedData();
        data.populateDefaults();
        data.setDirty();
        return data;
    }

    /**
     * Populates the default "Blocks" category containing 4 vanilla items.
     */
    public synchronized void populateDefaults() {
        List<ShopEntry> blockEntries = List.of(
                new ShopEntry("stone", new ItemStack(Items.STONE), 10.0, 5.0, -1, "", List.of()),
                new ShopEntry("cobblestone", new ItemStack(Items.COBBLESTONE), 5.0, 2.5, -1, "", List.of()),
                new ShopEntry("oak_log", new ItemStack(Items.OAK_LOG), 15.0, 7.5, -1, "", List.of()),
                new ShopEntry("dirt", new ItemStack(Items.DIRT), 2.0, 1.0, -1, "", List.of())
        );

        ShopCategory defaultCategory = new ShopCategory(
                "blocks",
                Component.translatable("oneshop.gui.default_category.blocks"),
                LotIcon.of(new ItemStack(Items.GRASS_BLOCK)),
                0,
                blockEntries
        );

        this.categories.put(defaultCategory.id(), defaultCategory);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        List<ShopCategory> list = new ArrayList<>(this.categories.values());

        CATEGORIES_CODEC.encodeStart(ops, list)
                .ifSuccess(resultTag -> tag.put("categories", resultTag))
                .ifError(error -> OneShop.LOGGER.error("Failed to serialize shop categories: {}", error.message()));

        return tag;
    }

    public static ShopSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        ShopSavedData data = new ShopSavedData();
        if (tag.contains("categories")) {
            var ops = registries.createSerializationContext(NbtOps.INSTANCE);
            CATEGORIES_CODEC.parse(ops, tag.get("categories"))
                    .ifSuccess(list -> {
                        for (ShopCategory cat : list) {
                            data.categories.put(cat.id(), cat);
                        }
                    })
                    .ifError(error -> OneShop.LOGGER.error("Failed to deserialize shop categories: {}", error.message()));
        } else {
            data.populateDefaults();
            data.setDirty();
        }
        return data;
    }

    @Override
    public synchronized void setDirty() {
        this.dataVersion++;
        this.cachedSortedCategories = null;
        this.entryLookupCache.clear();
        super.setDirty();
    }

    public synchronized int getDataVersion() {
        return this.dataVersion;
    }

    // Category Accessors & Mutators
    public synchronized List<ShopCategory> getCategories() {
        if (this.cachedSortedCategories != null) {
            return this.cachedSortedCategories;
        }
        List<ShopCategory> list = new ArrayList<>(this.categories.values());
        list.sort(Comparator.comparingInt(ShopCategory::sortOrder).thenComparing(ShopCategory::id));
        this.cachedSortedCategories = Collections.unmodifiableList(list);
        return this.cachedSortedCategories;
    }

    public synchronized Optional<ShopCategory> getCategory(String categoryId) {
        return Optional.ofNullable(this.categories.get(categoryId));
    }

    public synchronized void setCategory(ShopCategory category) {
        Objects.requireNonNull(category, "Category cannot be null");
        this.categories.put(category.id(), category);
        setDirty();
    }

    public synchronized boolean removeCategory(String categoryId) {
        boolean removed = this.categories.remove(categoryId) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // Entry Accessors & Mutators
    public synchronized Optional<ShopEntry> getEntry(String entryId) {
        if (entryId == null) return Optional.empty();
        ShopEntry cached = this.entryLookupCache.get(entryId);
        if (cached != null) {
            return Optional.of(cached);
        }
        for (ShopCategory category : this.categories.values()) {
            Optional<ShopEntry> entry = category.findEntry(entryId);
            if (entry.isPresent()) {
                this.entryLookupCache.put(entryId, entry.get());
                return entry;
            }
        }
        return Optional.empty();
    }

    public synchronized Optional<ShopCategory> getCategoryForEntry(String entryId) {
        for (ShopCategory category : this.categories.values()) {
            if (category.hasEntry(entryId)) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    public synchronized boolean addOrUpdateEntry(String categoryId, ShopEntry entry) {
        ShopCategory category = this.categories.get(categoryId);
        if (category == null) {
            return false;
        }
        ShopCategory updatedCategory = category.withEntry(entry);
        this.categories.put(categoryId, updatedCategory);
        setDirty();
        return true;
    }

    public synchronized boolean removeEntry(String categoryId, String entryId) {
        ShopCategory category = this.categories.get(categoryId);
        if (category == null || !category.hasEntry(entryId)) {
            return false;
        }
        ShopCategory updatedCategory = category.withoutEntry(entryId);
        this.categories.put(categoryId, updatedCategory);
        setDirty();
        return true;
    }

    /**
     * Updates the maxStock (available stock) of the entry matching the given ID.
     * Marks SavedData as dirty upon modification.
     *
     * @param entryId  The ID of the entry to update
     * @param newStock The new stock value (-1 for unlimited)
     * @return {@code true} if an entry was found and updated, {@code false} otherwise
     */
    public synchronized boolean updateStock(String entryId, int newStock) {
        for (Map.Entry<String, ShopCategory> catEntry : this.categories.entrySet()) {
            ShopCategory category = catEntry.getValue();
            Optional<ShopEntry> opt = category.findEntry(entryId);
            if (opt.isPresent()) {
                ShopEntry updatedEntry = opt.get().withMaxStock(newStock);
                ShopCategory updatedCat = category.withEntry(updatedEntry);
                this.categories.put(catEntry.getKey(), updatedCat);
                setDirty();
                return true;
            }
        }
        return false;
    }

    /**
     * Modifies the stock of the entry by a relative delta (positive to add, negative to decrement).
     * If the entry is unlimited (-1), its stock remains unlimited.
     * Marks SavedData as dirty upon modification.
     *
     * @param entryId The ID of the entry to update
     * @param delta   Amount to adjust stock by
     * @return {@code true} if an entry was found and modified, {@code false} otherwise
     */
    public synchronized boolean modifyStock(String entryId, int delta) {
        for (Map.Entry<String, ShopCategory> catEntry : this.categories.entrySet()) {
            ShopCategory category = catEntry.getValue();
            Optional<ShopEntry> opt = category.findEntry(entryId);
            if (opt.isPresent()) {
                ShopEntry entry = opt.get();
                if (entry.isUnlimited()) {
                    return true;
                }
                int newStock = Math.max(0, entry.maxStock() + delta);
                ShopEntry updatedEntry = entry.withMaxStock(newStock);
                ShopCategory updatedCat = category.withEntry(updatedEntry);
                this.categories.put(catEntry.getKey(), updatedCat);
                setDirty();
                return true;
            }
        }
        return false;
    }

    /**
     * Attempts to consume a specified amount of stock from an entry.
     * Succeeds unconditionally if the entry has unlimited stock (-1).
     * Marks SavedData as dirty upon consumption.
     *
     * @param entryId The ID of the entry
     * @param amount  The amount of stock to consume
     * @return {@code true} if sufficient stock was available and consumed, {@code false} otherwise
     */
    public synchronized boolean consumeStock(String entryId, int amount) {
        Optional<ShopEntry> opt = getEntry(entryId);
        if (opt.isEmpty()) {
            return false;
        }
        ShopEntry entry = opt.get();
        if (entry.isUnlimited()) {
            return true;
        }
        if (entry.maxStock() < amount) {
            return false;
        }
        return modifyStock(entryId, -amount);
    }

    public synchronized void clear() {
        this.categories.clear();
        setDirty();
    }
}
