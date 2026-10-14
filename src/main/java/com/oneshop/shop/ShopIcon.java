package com.oneshop.shop;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Decoupled icon utility and alias for {@link LotIcon}.
 */
public final class ShopIcon {
    private ShopIcon() {}

    public static LotIcon of(ItemStack item) {
        return LotIcon.of(item);
    }

    public static LotIcon of(ResourceLocation texture) {
        return LotIcon.of(texture);
    }

    public static LotIcon ofItem(ItemStack item) {
        return LotIcon.ofItem(item);
    }

    public static LotIcon ofTexture(ResourceLocation texture) {
        return LotIcon.ofTexture(texture);
    }
}
