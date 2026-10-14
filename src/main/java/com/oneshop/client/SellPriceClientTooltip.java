package com.oneshop.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.oneshop.OneShop;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class SellPriceClientTooltip implements ClientTooltipComponent {
    public static final ResourceLocation COIN_ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "textures/gui/currency_icon.png");
    public static final int COIN_ICON_WIDTH = 8;
    public static final int COIN_ICON_HEIGHT = 8;

    private static double cachedPrice = Double.NaN;
    private static Component cachedText = null;

    private final Component text;

    public SellPriceClientTooltip(SellPriceTooltip data) {
        double price = data.price();
        if (Double.compare(price, cachedPrice) == 0 && cachedText != null) {
            this.text = cachedText;
        } else {
            cachedPrice = price;
            cachedText = Component.literal("Можно продать: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(formatPrice(price)).withStyle(ChatFormatting.GREEN));
            this.text = cachedText;
        }
    }

    private static String formatPrice(double price) {
        if (price == (long) price) {
            return Long.toString((long) price);
        }
        double rounded = Math.round(price * 100.0) / 100.0;
        return Double.toString(rounded);
    }

    @Override
    public int getHeight() {
        return 10;
    }

    @Override
    public int getWidth(Font font) {
        return font.width(this.text) + 2 + 8;
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix, MultiBufferSource.BufferSource bufferSource) {
        font.drawInBatch(this.text, (float) x, (float) y, -1, true, matrix, bufferSource, Font.DisplayMode.NORMAL, 0, 15728880);
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        int iconX = x + font.width(this.text) + 2;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        guiGraphics.blit(COIN_ICON_TEXTURE, iconX, y, 0, 0, 8, 8, 8, 8);
    }

    public Component getText() {
        return this.text;
    }
}
