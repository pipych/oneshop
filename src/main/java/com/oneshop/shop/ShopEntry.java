package com.oneshop.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Data model representing an individual shop item entry.
 * Strictly separates functional traded item/tags from decoupled visual icon representations.
 *
 * @param id                Unique identifier for the shop entry
 * @param item              The optional item stack being sold/bought (serialized via ItemStack.OPTIONAL_CODEC)
 * @param buyPrice          Cost for a player to buy this item from the shop
 * @param sellPrice         Reward for a player selling this item to the shop
 * @param maxStock          Maximum available stock (-1 for unlimited stock)
 * @param permission        Optional permission node required to interact with this entry
 * @param runCommandsOnBuy  List of server commands to execute when a player buys this entry
 * @param allowBuy          Whether buying this entry is enabled (automatically disabled for tag-only lots)
 * @param allowSell         Whether selling this entry is enabled
 * @param tags              Optional list of Item tags (e.g. minecraft:logs, c:ores) for bulk tag-based selling
 * @param icon              Decoupled visual display icon (ItemStack or ResourceLocation texture)
 * @param customTitle       Optional custom display title overriding default item/tag name
 * @param targetItem        Explicit registry ID or tag (e.g. "minecraft:cobblestone", "#c:stones") for the functional trade item
 */
public record ShopEntry(
        String id,
        ItemStack item,
        double buyPrice,
        double sellPrice,
        int maxStock,
        String permission,
        List<String> runCommandsOnBuy,
        boolean allowBuy,
        boolean allowSell,
        List<String> tags,
        LotIcon icon,
        String customTitle,
        String targetItem
) {
    public enum Type {
        BUY,
        SELL,
        BOTH,
        NONE
    }

    public static final Codec<ShopEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(ShopEntry::id),
            ItemStack.OPTIONAL_CODEC.optionalFieldOf("item", ItemStack.EMPTY).forGetter(ShopEntry::item),
            Codec.DOUBLE.fieldOf("buyPrice").forGetter(ShopEntry::buyPrice),
            Codec.DOUBLE.fieldOf("sellPrice").forGetter(ShopEntry::sellPrice),
            Codec.INT.fieldOf("maxStock").forGetter(ShopEntry::maxStock),
            Codec.STRING.optionalFieldOf("permission", "").forGetter(ShopEntry::permission),
            Codec.STRING.listOf().optionalFieldOf("runCommandsOnBuy", List.of()).forGetter(ShopEntry::runCommandsOnBuy),
            Codec.BOOL.optionalFieldOf("allowBuy", true).forGetter(ShopEntry::allowBuy),
            Codec.BOOL.optionalFieldOf("allowSell", true).forGetter(ShopEntry::allowSell),
            Codec.STRING.listOf().optionalFieldOf("tags", List.of()).forGetter(ShopEntry::tags),
            LotIcon.CODEC.optionalFieldOf("icon").forGetter(entry -> Optional.ofNullable(entry.icon())),
            Codec.STRING.optionalFieldOf("customTitle", "").forGetter(ShopEntry::customTitle),
            Codec.STRING.optionalFieldOf("targetItem", "").forGetter(ShopEntry::getFunctionalItemId)
    ).apply(instance, (id, item, buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, allowBuy, allowSell, tags, iconOpt, customTitle, targetItem) ->
            new ShopEntry(id, item, buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy,
                    allowBuy, allowSell, tags,
                    iconOpt.orElse(null),
                    customTitle, targetItem)
    ));

    private static final java.util.Map<String, TagKey<Item>> TAG_KEY_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private static final TagKey<Item> INVALID_TAG = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("oneshop", "invalid_tag"));
    private static final java.util.Map<String, Item> ITEM_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public static TagKey<Item> parseTagKey(String tagStr) {
        if (tagStr == null || tagStr.isBlank()) return null;
        String clean = tagStr.trim();
        if (clean.startsWith("#")) clean = clean.substring(1).trim();
        if (clean.isEmpty()) return null;
        TagKey<Item> tag = TAG_KEY_CACHE.computeIfAbsent(clean, s -> {
            try {
                ResourceLocation loc = ResourceLocation.tryParse(s);
                return loc != null ? TagKey.create(Registries.ITEM, loc) : INVALID_TAG;
            } catch (Throwable t) {
                return INVALID_TAG;
            }
        });
        return tag == INVALID_TAG ? null : tag;
    }

    public static Item parseItem(String itemStr) {
        if (itemStr == null || itemStr.isBlank() || itemStr.startsWith("#")) return null;
        String clean = itemStr.trim();
        Item item = ITEM_CACHE.computeIfAbsent(clean, s -> {
            try {
                ResourceLocation loc = ResourceLocation.tryParse(s);
                if (loc != null) {
                    Item regItem = BuiltInRegistries.ITEM.get(loc);
                    return (regItem != null && regItem != Items.AIR) ? regItem : Items.AIR;
                }
            } catch (Throwable ignored) {}
            return Items.AIR;
        });
        return item == Items.AIR ? null : item;
    }

    private static final java.util.Map<ShopEntry, String> SEARCH_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public boolean matchesSearch(String lowerQuery) {
        if (lowerQuery == null || lowerQuery.isEmpty()) {
            return true;
        }
        String searchIndex = SEARCH_CACHE.computeIfAbsent(this, e -> {
            StringBuilder sb = new StringBuilder();
            if (e.id() != null) sb.append(e.id().toLowerCase(Locale.ROOT)).append(' ');
            if (e.customTitle() != null) sb.append(e.customTitle().toLowerCase(Locale.ROOT)).append(' ');
            if (e.targetItem() != null) sb.append(e.targetItem().toLowerCase(Locale.ROOT)).append(' ');
            if (!e.item().isEmpty()) {
                sb.append(e.item().getHoverName().getString().toLowerCase(Locale.ROOT)).append(' ');
                sb.append(BuiltInRegistries.ITEM.getKey(e.item().getItem()).toString().toLowerCase(Locale.ROOT)).append(' ');
            }
            if (e.icon() != null) {
                if (e.icon().isItem() && !e.icon().item().isEmpty()) {
                    sb.append(e.icon().item().getHoverName().getString().toLowerCase(Locale.ROOT)).append(' ');
                } else if (e.icon().isTexture() && e.icon().texture() != null) {
                    sb.append(e.icon().texture().toString().toLowerCase(Locale.ROOT)).append(' ');
                    sb.append(e.icon().texture().getPath().toLowerCase(Locale.ROOT)).append(' ');
                }
            }
            if (e.hasTags()) {
                for (String t : e.tags()) {
                    if (t != null) sb.append(t.toLowerCase(Locale.ROOT)).append(' ');
                }
            }
            return sb.toString();
        });
        return searchIndex.contains(lowerQuery);
    }

    public static void clearCaches() {
        TAG_KEY_CACHE.clear();
        ITEM_CACHE.clear();
        SEARCH_CACHE.clear();
    }

    public ShopEntry {
        if (id == null || id.isBlank()) {
            id = java.util.UUID.randomUUID().toString();
        }
        item = item != null ? item : ItemStack.EMPTY;
        customTitle = customTitle != null ? customTitle : "";
        permission = permission != null ? permission : "";
        runCommandsOnBuy = runCommandsOnBuy != null ? List.copyOf(runCommandsOnBuy) : List.of();
        tags = tags != null ? List.copyOf(tags) : List.of();

        if (targetItem == null || targetItem.isBlank()) {
            if (!item.isEmpty() && item.getItem() != Items.AIR) {
                targetItem = BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
            } else if (!tags.isEmpty()) {
                String firstTag = tags.get(0).trim();
                targetItem = firstTag.startsWith("#") ? firstTag : "#" + firstTag;
            } else {
                targetItem = "";
            }
        } else {
            targetItem = targetItem.trim();
        }

        // If item is empty but targetItem represents a direct item ID, resolve item
        if (item.isEmpty() && !targetItem.isBlank() && !targetItem.startsWith("#")) {
            try {
                ResourceLocation loc = ResourceLocation.tryParse(targetItem);
                if (loc != null) {
                    Item regItem = BuiltInRegistries.ITEM.get(loc);
                    if (regItem != null && regItem != Items.AIR) {
                        item = new ItemStack(regItem);
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (icon == null) {
            icon = !item.isEmpty() ? LotIcon.of(item) : LotIcon.of(ItemStack.EMPTY);
        }
    }

    public ShopEntry(String id, ItemStack item, double buyPrice, double sellPrice, int maxStock, String permission, List<String> runCommandsOnBuy, boolean allowBuy, boolean allowSell, List<String> tags, LotIcon icon, String customTitle) {
        this(id, item, buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, allowBuy, allowSell, tags, icon, customTitle, "");
    }

    public ShopEntry(String id, ItemStack item, double buyPrice, double sellPrice, int maxStock, String permission, List<String> runCommandsOnBuy, boolean allowBuy, boolean allowSell, List<String> tags, LotIcon icon) {
        this(id, item, buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, allowBuy, allowSell, tags, icon, "", "");
    }

    public ShopEntry(String id, ItemStack item, double buyPrice, double sellPrice, int maxStock, String permission, List<String> runCommandsOnBuy, boolean allowBuy, boolean allowSell, List<String> tags) {
        this(id, item, buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, allowBuy, allowSell, tags, LotIcon.of(item), "", "");
    }

    public ShopEntry(String id, ItemStack item, double buyPrice, double sellPrice, int maxStock, String permission, List<String> runCommandsOnBuy, boolean allowBuy, boolean allowSell) {
        this(id, item, buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, allowBuy, allowSell, List.of(), LotIcon.of(item), "", "");
    }

    public ShopEntry(String id, ItemStack item, double buyPrice, double sellPrice, int maxStock, String permission, List<String> runCommandsOnBuy) {
        this(id, item, buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, true, true, List.of(), LotIcon.of(item), "", "");
    }

    public ShopEntry(String id, ItemStack item, double buyPrice, double sellPrice, int maxStock) {
        this(id, item, buyPrice, sellPrice, maxStock, "", List.of(), true, true, List.of(), LotIcon.of(item), "", "");
    }

    public ShopEntry(String id, ItemStack item, double buyPrice, double sellPrice) {
        this(id, item, buyPrice, sellPrice, -1, "", List.of(), true, true);
    }

    // JavaBean getters
    public String getId() {
        return id();
    }

    public ItemStack getItem() {
        return item();
    }

    public double getBuyPrice() {
        return buyPrice();
    }

    public double getSellPrice() {
        return sellPrice();
    }

    public int getMaxStock() {
        return maxStock();
    }

    public String getPermission() {
        return permission();
    }

    public List<String> getRunCommandsOnBuy() {
        return runCommandsOnBuy();
    }

    public boolean isAllowBuy() {
        return isBuyAllowed();
    }

    public boolean isAllowSell() {
        return isSellAllowed();
    }

    public LotIcon getIcon() {
        return icon();
    }

    public String getCustomTitle() {
        return customTitle();
    }

    public boolean hasCustomTitle() {
        return customTitle != null && !customTitle.isBlank();
    }

    // --- Strict Separation: Functional Target Getters ---

    /**
     * Resolves the actual registry ID or tag being traded. Must NEVER return or read iconId.
     */
    public String getFunctionalItemId() {
        if (targetItem != null && !targetItem.isBlank()) {
            return targetItem;
        }
        if (item != null && !item.isEmpty() && item.getItem() != Items.AIR) {
            return BuiltInRegistries.ITEM.getKey(item.getItem()).toString();
        }
        if (hasTags()) {
            for (String tagStr : tags) {
                if (tagStr != null && !tagStr.isBlank()) {
                    String trimmed = tagStr.trim();
                    return trimmed.startsWith("#") ? trimmed : "#" + trimmed;
                }
            }
        }
        return "";
    }

    public String getItemId() {
        return getFunctionalItemId();
    }

    public String getTargetItem() {
        return getFunctionalItemId();
    }

    /**
     * Matches an inventory ItemStack strictly against the functional traded item or tags.
     * Must NEVER read or match against icon / iconId.
     */
    public boolean matchesTradeItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        if (item != null && !item.isEmpty() && item.getItem() != Items.AIR) {
            if (item.getComponentsPatch().isEmpty()) {
                return stack.is(item.getItem());
            }
            return ItemStack.isSameItemSameComponents(stack, item);
        }
        String fid = getFunctionalItemId();
        if (fid != null && fid.startsWith("#")) {
            TagKey<Item> tagKey = parseTagKey(fid);
            if (tagKey != null && stack.is(tagKey)) {
                return true;
            }
        }
        if (hasTags()) {
            for (String tagStr : tags) {
                TagKey<Item> tagKey = parseTagKey(tagStr);
                if (tagKey != null && stack.is(tagKey)) {
                    return true;
                }
            }
        }
        if (fid != null && !fid.isBlank() && !fid.startsWith("#")) {
            Item regItem = parseItem(fid);
            if (regItem != null) {
                return stack.is(regItem);
            }
        }
        return false;
    }

    // --- Strict Separation: Visual Icon Getters ---

    /**
     * Resolves the visual item icon for rendering the lot card or preview.
     * If visual icon is not an item or is empty, falls back to rendering from getFunctionalItemId().
     */
    public ItemStack getDisplayIconStack() {
        if (icon != null && icon.isItem() && !icon.item().isEmpty()) {
            return icon.item().copy();
        }
        if (icon != null && icon.isTexture()) {
            return ItemStack.EMPTY;
        }
        if (item != null && !item.isEmpty()) {
            return item.copy();
        }
        String fid = getFunctionalItemId();
        if (fid != null && !fid.isBlank()) {
            if (fid.startsWith("#")) {
                TagKey<Item> tagKey = parseTagKey(fid);
                if (tagKey != null) {
                    var holders = BuiltInRegistries.ITEM.getTag(tagKey);
                    if (holders.isPresent() && holders.get().size() > 0) {
                        return new ItemStack(holders.get().get(0).value());
                    }
                }
            } else {
                Item regItem = parseItem(fid);
                if (regItem != null) {
                    return new ItemStack(regItem);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * Resolves the visual texture path if a custom texture icon is used.
     */
    public ResourceLocation getCustomIconTexture() {
        if (icon != null && icon.isTexture() && icon.texture() != null) {
            return icon.texture();
        }
        return null;
    }

    /**
     * Returns the visual icon ID string (registry item ID or texture path).
     */
    public String getIconId() {
        if (icon != null) {
            if (icon.isItem() && !icon.item().isEmpty()) {
                return BuiltInRegistries.ITEM.getKey(icon.item().getItem()).toString();
            } else if (icon.isTexture() && icon.texture() != null) {
                return icon.texture().toString();
            }
        }
        return "";
    }

    public LotIcon getVisualIcon() {
        if (icon != null && !icon.isEmpty()) {
            return icon;
        }
        return LotIcon.of(getDisplayIconStack());
    }

    // Convenience & State Check Methods
    public boolean isValid() {
        return !item.isEmpty() || hasTags() || (targetItem != null && !targetItem.isBlank());
    }

    public boolean isTagOnly() {
        return item.isEmpty() && (hasTags() || (targetItem != null && targetItem.startsWith("#")));
    }

    public boolean isUnlimited() {
        return maxStock < 0;
    }

    public boolean hasStock(int amount) {
        return isUnlimited() || maxStock >= amount;
    }

    public boolean hasPermissionRequirement() {
        return permission != null && !permission.isBlank();
    }

    public boolean isBuyAllowed() {
        return (!item.isEmpty() || (!targetItem.isBlank() && !targetItem.startsWith("#"))) && allowBuy;
    }

    public boolean isSellAllowed() {
        return allowSell && sellPrice > 0 && isValid();
    }

    public boolean hasTags() {
        return (tags != null && !tags.isEmpty()) || (targetItem != null && targetItem.startsWith("#"));
    }

    public List<String> getTags() {
        return tags();
    }

    public ShopEntry withMaxStock(int newMaxStock) {
        return new ShopEntry(id, item.copy(), buyPrice, sellPrice, newMaxStock, permission, runCommandsOnBuy, allowBuy, allowSell, tags, icon, customTitle, targetItem);
    }

    public ShopEntry withPrices(double newBuyPrice, double newSellPrice) {
        return new ShopEntry(id, item.copy(), newBuyPrice, newSellPrice, maxStock, permission, runCommandsOnBuy, allowBuy, allowSell, tags, icon, customTitle, targetItem);
    }

    public ShopEntry withItem(ItemStack newItem) {
        LotIcon updatedIcon = icon;
        if (icon == null || (icon.isItem() && (icon.item().isEmpty() || ItemStack.isSameItemSameComponents(icon.item(), item)))) {
            updatedIcon = LotIcon.of(newItem);
        }
        String newTarget = (newItem != null && !newItem.isEmpty())
                ? BuiltInRegistries.ITEM.getKey(newItem.getItem()).toString()
                : targetItem;
        return new ShopEntry(id, newItem, buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, !newItem.isEmpty() && allowBuy, allowSell, tags, updatedIcon, customTitle, newTarget);
    }

    public Type type() {
        if (allowBuy && allowSell) return Type.BOTH;
        if (allowBuy) return Type.BUY;
        if (allowSell) return Type.SELL;
        return Type.NONE;
    }

    public ShopEntry withTradeOptions(boolean newAllowBuy, boolean newAllowSell) {
        return new ShopEntry(id, item.copy(), buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, newAllowBuy, newAllowSell, tags, icon, customTitle, targetItem);
    }

    public ShopEntry withType(Type newType) {
        boolean newBuy = newType == Type.BUY || newType == Type.BOTH;
        boolean newSell = newType == Type.SELL || newType == Type.BOTH;
        return withTradeOptions(newBuy, newSell);
    }

    public ShopEntry withTags(List<String> newTags) {
        return new ShopEntry(id, item.copy(), buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, allowBuy, allowSell, newTags, icon, customTitle, targetItem);
    }

    public ShopEntry withIcon(LotIcon newIcon) {
        return new ShopEntry(id, item.copy(), buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, allowBuy, allowSell, tags, newIcon, customTitle, targetItem);
    }

    public ShopEntry withCustomTitle(String newCustomTitle) {
        return new ShopEntry(id, item.copy(), buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, allowBuy, allowSell, tags, icon, newCustomTitle, targetItem);
    }

    public ShopEntry withTargetItem(String newTargetItem) {
        return new ShopEntry(id, item.copy(), buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, allowBuy, allowSell, tags, icon, customTitle, newTargetItem);
    }

    public ShopEntry copy() {
        return new ShopEntry(id, item.copy(), buyPrice, sellPrice, maxStock, permission, runCommandsOnBuy, allowBuy, allowSell, tags, icon != null ? icon.copy() : LotIcon.of(item), customTitle, targetItem);
    }
}
