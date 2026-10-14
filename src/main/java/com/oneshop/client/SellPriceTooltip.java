package com.oneshop.client;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

public record SellPriceTooltip(long price) implements TooltipComponent {}
