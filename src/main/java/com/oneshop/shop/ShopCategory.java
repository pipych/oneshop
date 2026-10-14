package com.oneshop.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Data model representing a category of shop entries.
 *
 * @param id          Unique category identifier
 * @param displayName Formatted display name for UI rendering
 * @param icon        LotIcon representing this category in menus (ItemStack or ResourceLocation texture)
 * @param sortOrder   Integer sorting weight (lower numbers appear first)
 * @param entries     List of shop entries belonging to this category
 */
public record ShopCategory(
        String id,
        Component displayName,
        LotIcon icon,
        int sortOrder,
        List<ShopEntry> entries
) {
    public static final Codec<ShopCategory> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(ShopCategory::id),
            ComponentSerialization.CODEC.fieldOf("displayName").forGetter(ShopCategory::displayName),
            LotIcon.CODEC.optionalFieldOf("icon").forGetter(cat -> Optional.ofNullable(cat.icon())),
            Codec.INT.optionalFieldOf("sortOrder", 0).forGetter(ShopCategory::sortOrder),
            ShopEntry.CODEC.listOf().optionalFieldOf("entries", List.of()).forGetter(ShopCategory::entries)
    ).apply(instance, (id, displayName, iconOpt, sortOrder, entries) ->
            new ShopCategory(id, displayName, iconOpt.orElseGet(() -> LotIcon.of(ItemStack.EMPTY)), sortOrder, entries)
    ));

    public ShopCategory {
        if (id == null || id.isBlank()) {
            id = java.util.UUID.randomUUID().toString();
        }
        displayName = displayName != null ? displayName : Component.literal(id);
        icon = icon != null ? icon : LotIcon.of(ItemStack.EMPTY);
        entries = entries != null ? List.copyOf(entries) : List.of();
    }

    public ShopCategory(String id, Component displayName, ItemStack icon, int sortOrder, List<ShopEntry> entries) {
        this(id, displayName, LotIcon.of(icon), sortOrder, entries);
    }

    public ShopCategory(String id, Component displayName, LotIcon icon, int sortOrder) {
        this(id, displayName, icon, sortOrder, List.of());
    }

    public ShopCategory(String id, Component displayName, ItemStack icon, int sortOrder) {
        this(id, displayName, LotIcon.of(icon), sortOrder, List.of());
    }

    public ShopCategory(String id, Component displayName, LotIcon icon) {
        this(id, displayName, icon, 0, List.of());
    }

    public ShopCategory(String id, Component displayName, ItemStack icon) {
        this(id, displayName, LotIcon.of(icon), 0, List.of());
    }

    // JavaBean getters
    public String getId() {
        return id();
    }

    public Component getDisplayName() {
        return displayName();
    }

    public LotIcon getIcon() {
        return icon();
    }

    public int getSortOrder() {
        return sortOrder();
    }

    public List<ShopEntry> getEntries() {
        return entries();
    }

    // Query Methods
    public Optional<ShopEntry> findEntry(String entryId) {
        return entries.stream().filter(e -> e.id().equals(entryId)).findFirst();
    }

    public boolean hasEntry(String entryId) {
        return entries.stream().anyMatch(e -> e.id().equals(entryId));
    }

    // Transformation Methods
    public ShopCategory withEntries(List<ShopEntry> newEntries) {
        return new ShopCategory(id, displayName, icon.copy(), sortOrder, newEntries);
    }

    public ShopCategory withEntry(ShopEntry newEntry) {
        List<ShopEntry> list = new ArrayList<>(entries);
        int existingIndex = -1;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id().equals(newEntry.id())) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex >= 0) {
            list.set(existingIndex, newEntry);
        } else {
            list.add(newEntry);
        }
        return withEntries(list);
    }

    public ShopCategory withoutEntry(String entryId) {
        List<ShopEntry> list = new ArrayList<>(entries);
        list.removeIf(e -> e.id().equals(entryId));
        return withEntries(list);
    }

    public ShopCategory withDisplayName(Component newDisplayName) {
        return new ShopCategory(id, newDisplayName, icon.copy(), sortOrder, entries);
    }

    public ShopCategory withIcon(LotIcon newIcon) {
        return new ShopCategory(id, displayName, newIcon != null ? newIcon : LotIcon.of(ItemStack.EMPTY), sortOrder, entries);
    }

    public ShopCategory withIcon(ItemStack newIcon) {
        return new ShopCategory(id, displayName, LotIcon.of(newIcon), sortOrder, entries);
    }

    public ShopCategory withSortOrder(int newSortOrder) {
        return new ShopCategory(id, displayName, icon.copy(), newSortOrder, entries);
    }

    public ShopCategory copy() {
        return new ShopCategory(id, displayName, icon.copy(), sortOrder, entries);
    }
}
