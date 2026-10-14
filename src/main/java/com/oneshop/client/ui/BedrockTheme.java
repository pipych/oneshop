package com.oneshop.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.component.ButtonComponent;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Surface;

import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Bedrock Dark UI styling constants, colors, and surfaces.
 * Features dark slate/anthracite backgrounds and crisp 1px beveled pixel borders without blurriness.
 */
public final class BedrockTheme {
    public static final int BG_DARK = 0xFF242424;
    public static final int BG_SLATE = 0xFF313233;
    public static final int BG_CARD = 0xFF282828;
    public static final int BG_HOVER = 0xFF3C3D3E;
    public static final int BG_INPUT = 0xFF1E1E1E;

    public static final int BORDER_LIGHT = 0xFF505050;
    public static final int BORDER_DARK = 0xFF1E1E1E;
    public static final int BORDER_ACTIVE = 0xFF55FF55;

    public static final int ACCENT_GREEN = 0xFF55FF55;
    public static final int OUTLINE_HOVER = 0xFFFFFFFF; // Pure white 1px outline for interactive hover
    public static final int TEXT_WHITE = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFFAAAAAA;
    public static final int TEXT_GOLD = 0xFFFFAA00;
    public static final int TEXT_RED = 0xFFFF5555;
    public static final int TEXT_GREEN = 0xFF55FF55;

    private BedrockTheme() {}

    /**
     * Primary window/panel surface: #313233 background with crisp 1px beveled border.
     */
    public static final Surface PANEL = (context, component) -> {
        drawBeveledBox(context, component.x(), component.y(), component.width(), component.height(), BG_SLATE, BORDER_LIGHT, BORDER_DARK);
    };

    /**
     * Card surface: #242424 background with crisp 1px beveled border.
     */
    public static final Surface CARD = (context, component) -> {
        drawBeveledBox(context, component.x(), component.y(), component.width(), component.height(), BG_CARD, BORDER_LIGHT, BORDER_DARK);
    };

    /**
     * Inactive category tab.
     */
    public static final Surface TAB_INACTIVE = BedrockSurfaces.bedrockGrayButton();

    /**
     * Active category tab.
     */
    public static final Surface TAB_ACTIVE = BedrockSurfaces.bedrockGreenButton();

    /**
     * Inset surface for text fields or slot backgrounds.
     */
    public static final Surface INSET = (context, component) -> {
        drawBeveledBox(context, component.x(), component.y(), component.width(), component.height(), BG_INPUT, BORDER_DARK, BORDER_LIGHT);
    };

    /**
     * Bedrock 1px beveled button renderer.
     */
    public static final ButtonComponent.Renderer BUTTON_RENDERER = (context, button, delta) -> {
        BedrockSurfaces.drawGrayButton(context, button.getX(), button.getY(), button.getWidth(), button.getHeight(), button.active);
        if (button.active && button.isHovered()) {
            float alpha = RenderSystem.getShaderColor()[3];
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.renderOutline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), AnimatedFloat.scaleAlpha(OUTLINE_HOVER, alpha));
        }
    };

    /**
     * Active state button renderer with green highlight.
     */
    public static final ButtonComponent.Renderer ACTIVE_BUTTON_RENDERER = (context, button, delta) -> {
        BedrockSurfaces.drawGreenButton(context, button.getX(), button.getY(), button.getWidth(), button.getHeight(), button.active);
        if (button.active && button.isHovered()) {
            float alpha = RenderSystem.getShaderColor()[3];
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.renderOutline(button.getX(), button.getY(), button.getWidth(), button.getHeight(), AnimatedFloat.scaleAlpha(OUTLINE_HOVER, alpha));
        }
    };

    /**
     * Draws a crisp 1-pixel beveled pixel border rectangle.
     */
    public static void drawBeveledBox(OwoUIDrawContext context, int x, int y, int w, int h, int bgColor, int lightBorder, int darkBorder) {
        float alpha = RenderSystem.getShaderColor()[3];
        if (alpha < 0.999f) {
            bgColor = AnimatedFloat.scaleAlpha(bgColor, alpha);
            lightBorder = AnimatedFloat.scaleAlpha(lightBorder, alpha);
            darkBorder = AnimatedFloat.scaleAlpha(darkBorder, alpha);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.fill(x, y, x + w, y + h, bgColor);
        context.fill(x, y, x + w, y + 1, lightBorder);
        context.fill(x, y, x + 1, y + h, lightBorder);
        context.fill(x, y + h - 1, x + w, y + h, darkBorder);
        context.fill(x + w - 1, y, x + w, y + h, darkBorder);
    }

    /**
     * Draws a crisp 1-pixel solid outline along the outer bounding box for hover feedback.
     */
    public static void drawOutline(OwoUIDrawContext context, int x, int y, int w, int h, int outlineColor) {
        float alpha = RenderSystem.getShaderColor()[3];
        if (alpha < 0.999f) {
            outlineColor = AnimatedFloat.scaleAlpha(outlineColor, alpha);
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.fill(x, y, x + w, y + 1, outlineColor);
        context.fill(x, y, x + 1, y + h, outlineColor);
        context.fill(x, y + h - 1, x + w, y + h, outlineColor);
        context.fill(x + w - 1, y, x + w, y + h, outlineColor);
    }

    /** Per-component hover fade state, with fast-path for the single-component case. */
    private static final class HoverFade {
        private Object lastComponent;
        private AnimatedFloat lastFade;
        private java.util.Map<Object, AnimatedFloat> states;

        /**
         * Retargets the fade when the hover state flips (equivalent to hooking mouseEnter/mouseLeave, but also
         * honours hover conditions such as "a modal is open") and returns the current 0..1 alpha factor.
         */
        float alpha(Object component, boolean hovered) {
            AnimatedFloat fade;
            if (component == this.lastComponent && this.lastFade != null) {
                fade = this.lastFade;
            } else if (this.lastComponent == null) {
                this.lastComponent = component;
                fade = this.lastFade = new AnimatedFloat(0f);
            } else {
                if (this.states == null) {
                    this.states = new java.util.WeakHashMap<>();
                    this.states.put(this.lastComponent, this.lastFade);
                }
                fade = this.states.computeIfAbsent(component, k -> new AnimatedFloat(0f));
                this.lastComponent = component;
                this.lastFade = fade;
            }
            fade.animateTo(hovered ? 1f : 0f, hovered ? 100 : 120, io.wispforest.owo.ui.core.Easing.SINE);
            return fade.get();
        }
    }

    private static void drawFadedOutline(OwoUIDrawContext graphics, io.wispforest.owo.ui.core.ParentComponent component, int normalBorder, int hoverBorder, float t) {
        int color;
        if (normalBorder == 0) {
            color = AnimatedFloat.scaleAlpha(hoverBorder, t);
        } else {
            color = AnimatedFloat.lerpArgb(normalBorder, hoverBorder, t);
        }
        float alpha = RenderSystem.getShaderColor()[3];
        if (alpha < 0.999f) {
            color = AnimatedFloat.scaleAlpha(color, alpha);
        }
        if ((color >>> 24) != 0) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            graphics.renderOutline(component.x(), component.y(), component.width(), component.height(), color);
        }
    }

    /**
     * Reusable dynamic Surface wrapper for per-frame hover outline rendering.
     * Renders baseSurface, then the outline, whose alpha fades smoothly in/out with the hover state.
     */
    public static Surface withHoverOutline(Surface baseSurface, int normalBorder, int hoverBorder) {
        HoverFade fade = new HoverFade();
        return (graphics, component) -> {
            if (baseSurface != null) {
                baseSurface.draw(graphics, component);
            }
            boolean hovered = false;
            if (component instanceof Hoverable h) {
                hovered = h.isHovered();
            } else if (component instanceof net.minecraft.client.gui.components.AbstractWidget w) {
                hovered = w.isHovered();
            }
            drawFadedOutline(graphics, component, normalBorder, hoverBorder, fade.alpha(component, hovered));
        };
    }

    /**
     * Dynamic per-frame hover outline Surface wrapper with custom hover supplier.
     */
    public static Surface withHoverOutline(Surface baseSurface, BooleanSupplier isHoveredSupplier, int normalBorder, int hoverBorder) {
        HoverFade fade = new HoverFade();
        return (graphics, component) -> {
            if (baseSurface != null) {
                baseSurface.draw(graphics, component);
            }
            boolean hovered = isHoveredSupplier != null
                    ? isHoveredSupplier.getAsBoolean()
                    : (component instanceof Hoverable h && h.isHovered());
            drawFadedOutline(graphics, component, normalBorder, hoverBorder, fade.alpha(component, hovered));
        };
    }

    /**
     * Convenience method wrapping baseSurface with 1px #E0E0E0 hover outline.
     */
    public static Surface withHoverOutline(Surface baseSurface) {
        return withHoverOutline(baseSurface, 0, OUTLINE_HOVER);
    }

    public static Surface outlineOnHover(Surface baseSurface, BooleanSupplier isHoveredSupplier) {
        return withHoverOutline(baseSurface, isHoveredSupplier, 0, OUTLINE_HOVER);
    }

    public static Surface outlineOnHover(Surface baseSurface, Predicate<Component> hoverPredicate) {
        return (graphics, component) -> {
            if (baseSurface != null) {
                baseSurface.draw(graphics, component);
            }
            int border = (hoverPredicate != null && hoverPredicate.test(component)) ? OUTLINE_HOVER : 0;
            if (border != 0) {
                float alpha = RenderSystem.getShaderColor()[3];
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                graphics.renderOutline(component.x(), component.y(), component.width(), component.height(), AnimatedFloat.scaleAlpha(border, alpha));
            }
        };
    }
}
