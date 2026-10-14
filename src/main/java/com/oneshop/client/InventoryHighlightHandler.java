package com.oneshop.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.oneshop.OneShop;
import net.minecraft.Util;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;

import java.util.OptionalDouble;

/**
 * Client event handler rendering smooth green slot border highlights on Shift-hold
 * in container inventory screens for items sellable in OneShop.
 */
@EventBusSubscriber(modid = OneShop.MODID, value = Dist.CLIENT)
public class InventoryHighlightHandler {
    private static float highlightAlpha = 0.0f;
    private static long lastFrameNanos = -1L;

    @SubscribeEvent
    public static void onRenderContainerForeground(ContainerScreenEvent.Render.Foreground event) {
        if (!Screen.hasShiftDown() && highlightAlpha <= 0.001f) {
            lastFrameNanos = Util.getNanos();
            return;
        }

        long now = Util.getNanos();
        float dt = (lastFrameNanos == -1L) ? 0.0f : (now - lastFrameNanos) / 1_000_000_000.0f;
        lastFrameNanos = now;
        if (dt > 0.1f) dt = 0.1f; // clamp lag spikes

        float target = Screen.hasShiftDown() ? 1.0f : 0.0f;
        if (highlightAlpha < target) {
            highlightAlpha = Math.min(1.0f, highlightAlpha + (dt / 0.15f));
        } else if (highlightAlpha > target) {
            highlightAlpha = Math.max(0.0f, highlightAlpha - (dt / 0.15f));
        }

        if (highlightAlpha <= 0.001f) {
            return;
        }

        if (event == null || event.getContainerScreen() == null || event.getContainerScreen().getMenu() == null) {
            return;
        }

        int alphaInt = (int) (highlightAlpha * 255.0f) & 0xFF;
        int borderColor = (alphaInt << 24) | 0x2ECC71;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        for (Slot slot : event.getContainerScreen().getMenu().slots) {
            if (slot == null || !slot.isActive() || !slot.hasItem()) {
                continue;
            }

            ItemStack stack = slot.getItem();
            OptionalDouble priceOpt = ClientShopCache.getSellPrice(stack);
            if (priceOpt.isPresent() && priceOpt.getAsDouble() > 0) {
                event.getGuiGraphics().renderOutline(slot.x, slot.y, 16, 16, borderColor);
            }
        }
    }

    public static float getHighlightAlpha() {
        return highlightAlpha;
    }

    public static void resetState() {
        highlightAlpha = 0.0f;
        lastFrameNanos = -1L;
    }
}
