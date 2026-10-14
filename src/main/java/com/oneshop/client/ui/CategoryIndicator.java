package com.oneshop.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;

/**
 * In-place category active indicator bar anchored locally inside each category widget layout.
 * Controlled via per-category {@link AnimatableProperty<Float>} alpha (0.0f to 1.0f) with dynamic alpha blending.
 */
public final class CategoryIndicator extends BaseComponent {
    public static final int HEIGHT = 2;
    private final AnimatableProperty<Float> alpha;
    private int lastWidth = -1, lastX = Integer.MIN_VALUE, lastY = Integer.MIN_VALUE;

    public CategoryIndicator(AnimatableProperty<Float> alpha) {
        this.alpha = alpha;
        this.horizontalSizing(Sizing.fixed(12));
        this.verticalSizing(Sizing.fixed(HEIGHT));
        this.zIndex(5);
    }

    @Override
    public void update(float delta, int mouseX, int mouseY) {
        super.update(delta, mouseX, mouseY);
        if (this.parent == null || this.parent.width() <= 0 || this.parent.height() <= 0) {
            return;
        }

        int parentW = this.parent.width();
        int parentH = this.parent.height();
        int stripWidth = Math.max(12, (int) Math.round(parentW * 0.40));
        stripWidth = Math.min(stripWidth, Math.max(4, parentW - 4));
        int targetX = (parentW - stripWidth) / 2;
        int targetY = parentH - 1 - HEIGHT;

        if (stripWidth != this.lastWidth) {
            this.lastWidth = stripWidth;
            this.horizontalSizing(Sizing.fixed(stripWidth));
        }

        if (targetX != this.lastX || targetY != this.lastY) {
            this.lastX = targetX;
            this.lastY = targetY;
            this.positioning(Positioning.absolute(targetX, targetY));
        }
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        float a = this.alpha != null ? this.alpha.get() : 0.0f;
        float currentAlpha = RenderSystem.getShaderColor()[3];
        float effectiveA = a * currentAlpha;
        if (effectiveA <= 0.005f || this.width() <= 0) {
            return;
        }

        int alphaInt = Math.max(0, Math.min(255, Math.round(effectiveA * 255.0f)));
        int color = (alphaInt << 24) | 0xFFFFFF;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.fill(this.x(), this.y(), this.x() + this.width(), this.y() + this.height(), color);
    }
}
