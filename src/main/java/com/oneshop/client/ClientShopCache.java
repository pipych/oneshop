package com.oneshop.client;

import com.oneshop.shop.ShopCategory;
import com.oneshop.shop.ShopEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Client-side cache holding synced shop categories and entries for client GUI rendering,
 * as well as a pre-computed O(1) item-to-sell-price mapping for fast tooltip inspection and Shift-highlighting.
 * Strictly uses functional trade items and tags, never visual display icons.
 */
public final class ClientShopCache {
    private record TagPriceEntry(TagKey<Item> tagKey, double price) {}

    private static List<ShopCategory> categories = List.of();
    private static Map<Item, Double> itemSellPrices = Map.of();
    private static List<TagPriceEntry> tagSellPrices = List.of();

    private ClientShopCache() {}

    public static List<ShopCategory> getCategories() {
        return categories;
    }

    public static void setCategories(List<ShopCategory> newCategories) {
        categories = newCategories != null ? List.copyOf(newCategories) : List.of();
        rebuildItemSellPrices();
    }

    public static Optional<ShopCategory> getCategory(String categoryId) {
        return categories.stream().filter(c -> c.id().equals(categoryId)).findFirst();
    }

    public static void addOrUpdateCategory(ShopCategory newCategory) {
        List<ShopCategory> list = new java.util.ArrayList<>(categories);
        int idx = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(newCategory.id())) {
                idx = i;
                break;
            }
        }
        if (idx != -1) {
            list.set(idx, newCategory);
        } else {
            list.add(newCategory);
        }
        setCategories(list);
    }

    public static Optional<ShopEntry> getEntry(String entryId) {
        for (ShopCategory cat : categories) {
            Optional<ShopEntry> entry = cat.findEntry(entryId);
            if (entry.isPresent()) {
                return entry;
            }
        }
        return Optional.empty();
    }

    /**
     * Resolves the maximum unit sell price for the given Item in the client shop cache.
     * Lookup is O(1) backed by an unmodifiable HashMap.
     */
    public static OptionalDouble getSellPrice(Item item) {
        if (item == null || item == Items.AIR) {
            return OptionalDouble.empty();
        }
        Double price = itemSellPrices.get(item);
        if (price != null && price > 0) {
            return OptionalDouble.of(price);
        }
        return OptionalDouble.empty();
    }

    /**
     * Resolves the maximum unit sell price for the given ItemStack in the client shop cache.
     * Evaluates O(1) item cache first, then checks dynamic item tags (stack.is(tagKey)).
     * Strictly uses functional trade targets, never visual display icons.
     */
    public static OptionalDouble getSellPrice(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return OptionalDouble.empty();
        }
        Double price = itemSellPrices.get(stack.getItem());
        if (price != null && price > 0) {
            return OptionalDouble.of(price);
        }

        // Dynamic tag fallback lookup: iterate only over pre-computed tag sell prices
        double maxPrice = -1.0;
        for (TagPriceEntry tp : tagSellPrices) {
            if (stack.is(tp.tagKey())) {
                maxPrice = Math.max(maxPrice, tp.price());
            }
        }
        if (maxPrice > 0) {
            return OptionalDouble.of(maxPrice);
        }
        return OptionalDouble.empty();
    }

    /**
     * Checks if the item can be sold in the shop.
     */
    public static boolean canSell(Item item) {
        return getSellPrice(item).isPresent();
    }

    /**
     * Checks if the item stack can be sold in the shop.
     */
    public static boolean canSell(ItemStack stack) {
        return getSellPrice(stack).isPresent();
    }

    /**
     * Returns an unmodifiable view of the pre-computed item-to-sell-price mapping.
     */
    public static Map<Item, Double> getItemSellPrices() {
        return itemSellPrices;
    }

    /**
     * Clears all cached categories and item prices.
     */
    public static void clear() {
        setCategories(List.of());
    }

    /**
     * Rebuilds the O(1) item-to-sell-price cache strictly from entry.getFunctionalItemId()
     * and functional item tags for all entries where selling is enabled.
     * NEVER indexes or matches against visual icon / iconId.
     */
    private static void rebuildItemSellPrices() {
        Map<Item, Double> prices = new HashMap<>();
        List<TagPriceEntry> tagList = new java.util.ArrayList<>();
        for (ShopCategory category : categories) {
            for (ShopEntry entry : category.entries()) {
                if (!entry.isSellAllowed()) {
                    continue;
                }
                double price = entry.sellPrice();
                String functionalId = entry.getFunctionalItemId();
                if (functionalId != null && !functionalId.isBlank()) {
                    if (functionalId.startsWith("#")) {
                        String cleanTag = functionalId.substring(1).trim();
                        try {
                            ResourceLocation tagLoc = ResourceLocation.tryParse(cleanTag);
                            if (tagLoc != null) {
                                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);
                                tagList.add(new TagPriceEntry(tagKey, price));
                                var tagOptional = BuiltInRegistries.ITEM.getTag(tagKey);
                                if (tagOptional.isPresent()) {
                                    for (Holder<Item> holder : tagOptional.get()) {
                                        prices.merge(holder.value(), price, Math::max);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    } else {
                        try {
                            ResourceLocation loc = ResourceLocation.tryParse(functionalId.trim());
                            if (loc != null) {
                                Item item = BuiltInRegistries.ITEM.get(loc);
                                if (item != null && item != Items.AIR) {
                                    prices.merge(item, price, Math::max);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }

                // Also index directly from functional item stack if present
                if (!entry.item().isEmpty()) {
                    Item item = entry.item().getItem();
                    if (item != Items.AIR) {
                        prices.merge(item, price, Math::max);
                    }
                }

                // Index all tags from entry.tags()
                if (entry.hasTags()) {
                    for (String tagStr : entry.tags()) {
                        if (tagStr == null || tagStr.isBlank()) {
                            continue;
                        }
                        try {
                            String cleanTag = tagStr.trim();
                            if (cleanTag.startsWith("#")) {
                                cleanTag = cleanTag.substring(1);
                            }
                            ResourceLocation tagLoc = ResourceLocation.tryParse(cleanTag);
                            if (tagLoc != null) {
                                TagKey<Item> tagKey = TagKey.create(Registries.ITEM, tagLoc);
                                tagList.add(new TagPriceEntry(tagKey, price));
                                var tagOptional = BuiltInRegistries.ITEM.getTag(tagKey);
                                if (tagOptional.isPresent()) {
                                    for (Holder<Item> holder : tagOptional.get()) {
                                        prices.merge(holder.value(), price, Math::max);
                                    }
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
            }
        }
        itemSellPrices = Collections.unmodifiableMap(prices);
        tagSellPrices = List.copyOf(tagList);
    }
}
