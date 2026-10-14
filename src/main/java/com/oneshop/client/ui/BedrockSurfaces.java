package com.oneshop.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Surface;

import java.util.function.BooleanSupplier;

/**
 * Pixel-accurate Bedrock UI surfaces matching authentic Minecraft Bedrock Edition button styles.
 */
public final class BedrockSurfaces {
    // 1px Solid Black Outer Border
    public static final int BORDER_BLACK = 0xFF121212;

    // Standard Button / Inactive Category (Top Gray Button)
    public static final int GRAY_HIGHLIGHT = 0xFF5C5D5E; // 1px light slate highlight (top & sides)
    public static final int GRAY_FILL = 0xFF444446;      // Neutral dark gray main body
    public static final int GRAY_SHADOW = 0xFF242426;    // 2-3px dark inset shadow along bottom edge

    // Disabled Button Colors
    public static final int DISABLED_HIGHLIGHT = 0xFF38383A;
    public static final int DISABLED_FILL = 0xFF2A2A2C;
    public static final int DISABLED_SHADOW = 0xFF18181A;
    public static final int TEXT_DISABLED = 0xFF777777;

    // Active Selected Category (Bottom Green Button)
    public static final int GREEN_HIGHLIGHT = 0xFF62A838; // 1px light moss green (top & sides)
    public static final int GREEN_FILL = 0xFF448827;      // Classic Bedrock green main body
    public static final int GREEN_SHADOW = 0xFF204412;    // 2-3px deep forest green shadow along bottom edge
    public static final int GREEN_INDICATOR = 0xFFFFFFFF; // Centered horizontal white strip (height 2px, ~40% width)

    // Dynamic Hover Outline
    public static final int OUTLINE_HOVER = 0xFFFFFFFF;   // 1px solid white outer outline

    private BedrockSurfaces() {}

    /**
     * Draws the pixel-accurate Bedrock Gray Button background, bevel, shadow, and borders.
     */
    public static void drawGrayButton(OwoUIDrawContext context, int x, int y, int w, int h, boolean active) {
        if (w <= 0 || h <= 0) return;

        float alpha = RenderSystem.getShaderColor()[3];
        int borderBlack = AnimatedFloat.scaleAlpha(BORDER_BLACK, alpha);
        int fill = AnimatedFloat.scaleAlpha(active ? GRAY_FILL : DISABLED_FILL, alpha);
        int highlight = AnimatedFloat.scaleAlpha(active ? GRAY_HIGHLIGHT : DISABLED_HIGHLIGHT, alpha);
        int shadow = AnimatedFloat.scaleAlpha(active ? GRAY_SHADOW : DISABLED_SHADOW, alpha);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 1. Outer 1px solid black border (0xFF121212)
        context.fill(x, y, x + w, y + 1, borderBlack);
        context.fill(x, y + h - 1, x + w, y + h, borderBlack);
        context.fill(x, y, x + 1, y + h, borderBlack);
        context.fill(x + w - 1, y, x + w, y + h, borderBlack);

        if (w <= 2 || h <= 2) return;

        // 2. Main background fill (0xFF444446)
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);

        // 3. Bottom shadow bar: 2-3px dark inset shadow (0xFF242426) along the bottom edge
        int shadowHeight = h > 18 ? 3 : (h > 10 ? 2 : 1);
        int shadowY = y + h - 1 - shadowHeight;
        context.fill(x + 1, shadowY, x + w - 1, y + h - 1, shadow);

        // 4. Inner top & side bevel: 1px light slate highlight (0xFF5C5D5E)
        context.fill(x + 1, y + 1, x + w - 1, y + 2, highlight);
        context.fill(x + 1, y + 1, x + 2, shadowY, highlight);
        context.fill(x + w - 2, y + 1, x + w - 1, shadowY, highlight);
    }

    public static void drawGrayButton(OwoUIDrawContext context, int x, int y, int w, int h) {
        drawGrayButton(context, x, y, w, h, true);
    }

    /**
     * Draws the pixel-accurate Bedrock Green Button (Active Category Tab) with bottom shadow and centered white indicator.
     */
    public static void drawGreenButton(OwoUIDrawContext context, int x, int y, int w, int h, boolean active) {
        if (w <= 0 || h <= 0) return;

        float alpha = RenderSystem.getShaderColor()[3];
        int borderBlack = AnimatedFloat.scaleAlpha(BORDER_BLACK, alpha);
        int fill = AnimatedFloat.scaleAlpha(GREEN_FILL, alpha);
        int shadow = AnimatedFloat.scaleAlpha(GREEN_SHADOW, alpha);
        int highlight = AnimatedFloat.scaleAlpha(GREEN_HIGHLIGHT, alpha);
        int indicator = AnimatedFloat.scaleAlpha(GREEN_INDICATOR, alpha);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 1. Outer 1px solid black border (0xFF121212)
        context.fill(x, y, x + w, y + 1, borderBlack);
        context.fill(x, y + h - 1, x + w, y + h, borderBlack);
        context.fill(x, y, x + 1, y + h, borderBlack);
        context.fill(x + w - 1, y, x + w, y + h, borderBlack);

        if (w <= 2 || h <= 2) return;

        // 2. Main background fill: Classic Bedrock green (0xFF448827)
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);

        // 3. Bottom shadow bar: 2-3px deep forest green shadow (0xFF204412) along the bottom edge
        int shadowHeight = h > 18 ? 3 : (h > 10 ? 2 : 1);
        int shadowY = y + h - 1 - shadowHeight;
        context.fill(x + 1, shadowY, x + w - 1, y + h - 1, shadow);

        // 4. Inner top & side bevel: 1px light moss green (0xFF62A838)
        context.fill(x + 1, y + 1, x + w - 1, y + 2, highlight);
        context.fill(x + 1, y + 1, x + 2, shadowY, highlight);
        context.fill(x + w - 2, y + 1, x + w - 1, shadowY, highlight);

        // 5. Bottom indicator bar: Centered horizontal white strip (0xFFFFFFFF, height: 2px, width: ~40% of button width) sitting directly above the bottom border
        int indicatorHeight = 2;
        int indicatorWidth = Math.max(12, (int) Math.round(w * 0.40));
        if (indicatorWidth > w - 4) {
            indicatorWidth = Math.max(4, w - 4);
        }
        int indicatorX = x + (w - indicatorWidth) / 2;
        int indicatorY = y + h - 1 - indicatorHeight;
        context.fill(indicatorX, indicatorY, indicatorX + indicatorWidth, indicatorY + indicatorHeight, indicator);
    }

    /**
     * Category tab body that cross-fades from the gray to the green Bedrock button by {@code t} (0..1).
     * The white strip is intentionally omitted: it is drawn by the in-place {@link CategoryIndicator}.
     */
    public static void drawTabButton(OwoUIDrawContext context, int x, int y, int w, int h, float t) {
        if (w <= 0 || h <= 0) return;

        float alpha = RenderSystem.getShaderColor()[3];
        int borderBlack = AnimatedFloat.scaleAlpha(BORDER_BLACK, alpha);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        context.fill(x, y, x + w, y + 1, borderBlack);
        context.fill(x, y + h - 1, x + w, y + h, borderBlack);
        context.fill(x, y, x + 1, y + h, borderBlack);
        context.fill(x + w - 1, y, x + w, y + h, borderBlack);

        if (w <= 2 || h <= 2) return;

        int fill = AnimatedFloat.scaleAlpha(AnimatedFloat.lerpArgb(GRAY_FILL, GREEN_FILL, t), alpha);
        int shadow = AnimatedFloat.scaleAlpha(AnimatedFloat.lerpArgb(GRAY_SHADOW, GREEN_SHADOW, t), alpha);
        int highlight = AnimatedFloat.scaleAlpha(AnimatedFloat.lerpArgb(GRAY_HIGHLIGHT, GREEN_HIGHLIGHT, t), alpha);

        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
        int shadowHeight = h > 18 ? 3 : (h > 10 ? 2 : 1);
        int shadowY = y + h - 1 - shadowHeight;
        context.fill(x + 1, shadowY, x + w - 1, y + h - 1, shadow);
        context.fill(x + 1, y + 1, x + w - 1, y + 2, highlight);
        context.fill(x + 1, y + 1, x + 2, shadowY, highlight);
        context.fill(x + w - 2, y + 1, x + w - 1, shadowY, highlight);
    }

    public static void drawGreenButton(OwoUIDrawContext context, int x, int y, int w, int h) {
        drawGreenButton(context, x, y, w, h, true);
    }

    /**
     * Checks whether a UI component is currently hovered.
     */
    public static boolean isHovered(Component component) {
        if (component instanceof Hoverable h) {
            return h.isHovered();
        } else if (component instanceof net.minecraft.client.gui.components.AbstractWidget w) {
            return w.isHovered();
        }
        return false;
    }

    /**
     * Retains dynamic hover outline (1px 0xFFFFFFFF) without shifting layout dimensions.
     */
    public static Surface withHoverOutline(Surface baseSurface) {
        return (context, component) -> {
            if (baseSurface != null) {
                baseSurface.draw(context, component);
            }
            if (isHovered(component)) {
                float alpha = RenderSystem.getShaderColor()[3];
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                context.renderOutline(component.x(), component.y(), component.width(), component.height(), AnimatedFloat.scaleAlpha(OUTLINE_HOVER, alpha));
            }
        };
    }

    /**
     * Dynamic hover outline with custom hover supplier.
     */
    public static Surface withHoverOutline(Surface baseSurface, BooleanSupplier isHoveredSupplier) {
        return (context, component) -> {
            if (baseSurface != null) {
                baseSurface.draw(context, component);
            }
            boolean hovered = isHoveredSupplier != null ? isHoveredSupplier.getAsBoolean() : isHovered(component);
            if (hovered) {
                float alpha = RenderSystem.getShaderColor()[3];
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                context.renderOutline(component.x(), component.y(), component.width(), component.height(), AnimatedFloat.scaleAlpha(OUTLINE_HOVER, alpha));
            }
        };
    }

    public static final Surface GRAY_BUTTON_BASE = (context, component) -> {
        boolean active = !(component instanceof BedrockButton b) || b.active();
        drawGrayButton(context, component.x(), component.y(), component.width(), component.height(), active);
    };

    public static final Surface GREEN_BUTTON_BASE = (context, component) -> {
        boolean active = !(component instanceof BedrockButton b) || b.active();
        drawGreenButton(context, component.x(), component.y(), component.width(), component.height(), active);
    };

    private static final Surface GRAY_BUTTON_HOVER = withHoverOutline(GRAY_BUTTON_BASE);
    private static final Surface GREEN_BUTTON_HOVER = withHoverOutline(GREEN_BUTTON_BASE);

    /**
     * Reusable Bedrock Gray Button Surface with dynamic 1px white hover outline.
     */
    public static Surface bedrockGrayButton() {
        return GRAY_BUTTON_HOVER;
    }

    public static Surface bedrockGrayButton(BooleanSupplier hoverCondition) {
        return withHoverOutline(GRAY_BUTTON_BASE, hoverCondition);
    }

    /**
     * Reusable Bedrock Green Button Surface with dynamic 1px white hover outline.
     */
    public static Surface bedrockGreenButton() {
        return GREEN_BUTTON_HOVER;
    }

    public static Surface bedrockGreenButton(BooleanSupplier hoverCondition) {
        return withHoverOutline(GREEN_BUTTON_BASE, hoverCondition);
    }
}
