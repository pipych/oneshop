package com.oneshop.client;

import com.mojang.datafixers.util.Either;
import com.oneshop.OneShop;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.neoforged.neoforge.client.event.RenderTooltipEvent;

import java.util.OptionalDouble;

/**
 * Global client-side item tooltip handler injecting OneShop sell status and price
 * with custom inline coin icon renderer for all items sellable in the shop.
 */
@EventBusSubscriber(modid = OneShop.MODID, value = Dist.CLIENT)
public class ShopTooltipHandler {

    public static final ResourceLocation COIN_ICON_TEXTURE = SellPriceClientTooltip.COIN_ICON_TEXTURE;
    public static final int COIN_ICON_SIZE = 8;

    public static void onRegisterTooltipComponentFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(SellPriceTooltip.class, SellPriceClientTooltip::new);
    }

    @SubscribeEvent
    public static void onGatherComponents(RenderTooltipEvent.GatherComponents event) {
        if (event == null || event.getTooltipElements() == null) {
            return;
        }

        ItemStack stack = event.getItemStack();
        if (stack == null || stack.isEmpty()) {
            return;
        }

        OptionalDouble priceOpt = ClientShopCache.getSellPrice(stack);
        if (priceOpt.isPresent() && priceOpt.getAsDouble() > 0) {
            double price = priceOpt.getAsDouble();
            event.getTooltipElements().add(Either.right(new SellPriceTooltip(price)));
        }
    }
}
