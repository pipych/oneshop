package com.oneshop.client;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record SellPriceTooltip(double price) implements TooltipComponent {
    public SellPriceTooltip(long price) {
        this((double) price);
    }
}
