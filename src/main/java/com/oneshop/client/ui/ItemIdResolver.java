package com.oneshop.client.ui;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.Locale;
import java.util.Optional;

/**
 * Resilient resolver and layout math for Slot Editor item ID normalization,
 * registry validation, and vertical text centering.
 */
public final class ItemIdResolver {

    private ItemIdResolver() {}

    /**
     * Sanitizes and normalizes raw item ID input:
     * - Trims whitespace
     * - Converts to lower-case using Locale.ROOT
     * - Automatically prepends the "minecraft:" namespace if missing
     */
    public static String normalizeId(String rawInput) {
        if (rawInput == null) {
            return "";
        }
        String clean = rawInput.trim().toLowerCase(Locale.ROOT);
        if (!clean.isEmpty() && !clean.contains(":")) {
            clean = "minecraft:" + clean;
        }
        return clean;
    }

    /**
     * Safely parses ResourceLocation from a normalized string without throwing exceptions.
     */
    public static ResourceLocation parseLocation(String clean) {
        if (clean == null || clean.isEmpty()) {
            return null;
        }
        return ResourceLocation.tryParse(clean);
    }

    /**
     * Validates and resolves an item Holder from raw user input.
     * In Minecraft 1.21.1, missing items resolve to Items.AIR rather than null,
     * so this explicitly ensures the holder exists and is not Items.AIR.
     */
    public static Optional<Holder.Reference<Item>> resolveItem(String rawInput) {
        String clean = normalizeId(rawInput);
        ResourceLocation loc = parseLocation(clean);
        if (loc == null) {
            return Optional.empty();
        }
        Optional<Holder.Reference<Item>> itemHolder = BuiltInRegistries.ITEM.getHolder(loc);
        if (itemHolder.isPresent() && itemHolder.get().value() != Items.AIR) {
            return itemHolder;
        }
        return Optional.empty();
    }

    /**
     * Calculates vertical text render offset ensuring Minecraft font (9px height)
     * is centered vertically within container:
     *   int textY = boxY + (boxHeight - 9) / 2;
     */
    public static int calculateCenteredTextY(int boxY, int boxHeight) {
        return boxY + (boxHeight - 9) / 2;
    }

    /**
     * Top Y coordinate for the blinking cursor bar and selection highlight box.
     */
    public static int getCursorTopY(int textY) {
        return textY - 1;
    }

    /**
     * Bottom Y coordinate for the blinking cursor bar and selection highlight box.
     */
    public static int getCursorBottomY(int textY) {
        return textY + 1 + 9;
    }
}
