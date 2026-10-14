package com.oneshop.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/**
 * 2x scaled item component for ShopEntry cards, rendering 32x32 resolution item models
 * centered with decorations rendered at the bottom-right corner.
 */
public class ItemDisplayComponent extends ItemComponent {
    private final int size;

    public ItemDisplayComponent(ItemStack stack, int size) {
        super(stack);
        this.size = size;
        this.sizing(Sizing.fixed(size), Sizing.fixed(size));
        this.showOverlay(false);
    }

    public ItemDisplayComponent(ItemStack stack) {
        this(stack, 32);
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return size;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return size;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        float[] prevShader = RenderSystem.getShaderColor().clone();
        float currentAlpha = prevShader[3];

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, currentAlpha);
        context.setColor(1.0f, 1.0f, 1.0f, currentAlpha);

        super.draw(context, mouseX, mouseY, partialTicks, delta);
        if (!this.stack.isEmpty() && (this.stack.getCount() > 1 || this.stack.isDamaged())) {
            context.renderItemDecorations(Minecraft.getInstance().font, this.stack,
                    this.x + Math.max(0, this.width() - 16),
                    this.y + Math.max(0, this.height() - 16));
        }

        context.setColor(prevShader[0], prevShader[1], prevShader[2], prevShader[3]);
        RenderSystem.setShaderColor(prevShader[0], prevShader[1], prevShader[2], prevShader[3]);
    }
}
