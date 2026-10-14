package com.oneshop.client.ui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
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

    public static void renderItemWithAlpha(GuiGraphics context, ItemStack stack, int x, int y, float size, float alpha) {
        if (stack == null || stack.isEmpty() || alpha <= 0.005f) return;

        if (alpha >= 0.995f) {
            if (size != 16.0f && size > 0.0f) {
                context.pose().pushPose();
                context.pose().translate(x, y, 0);
                float s = size / 16.0f;
                context.pose().scale(s, s, 1.0f);
                context.renderItem(stack, 0, 0);
                context.pose().popPose();
            } else {
                context.renderItem(stack, x, y);
            }
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            return;
        }

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        BakedModel model = itemRenderer.getModel(stack, null, null, 0);

        context.pose().pushPose();
        context.pose().translate(x, y, 0);
        if (size != 16.0f && size > 0.0f) {
            float s = size / 16.0f;
            context.pose().scale(s, s, 1.0f);
        }
        context.pose().translate(8.0f, 8.0f, 150.0f);
        context.pose().scale(16.0f, -16.0f, 16.0f);

        boolean flat = !model.usesBlockLight();
        if (flat) {
            Lighting.setupForFlatItems();
        }

        MultiBufferSource bufferSource = type -> {
            if (type == Sheets.cutoutBlockSheet() || type == Sheets.solidBlockSheet()) {
                return context.bufferSource().getBuffer(Sheets.translucentCullBlockSheet());
            }
            return context.bufferSource().getBuffer(type);
        };

        itemRenderer.render(stack, ItemDisplayContext.GUI, false, context.pose(), bufferSource, 15728880, OverlayTexture.NO_OVERLAY, model);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);

        context.flush();

        if (flat) {
            Lighting.setupFor3DItems();
        }

        context.pose().popPose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
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
        if (!io.wispforest.owo.ui.util.ScissorStack.isVisible(this, context.pose())) return;
        if (this.stack == null || this.stack.isEmpty()) return;
        float[] shaderColor = RenderSystem.getShaderColor();
        float prevR = shaderColor[0], prevG = shaderColor[1], prevB = shaderColor[2], prevA = shaderColor[3];

        renderItemWithAlpha(context, this.stack, this.x, this.y, (float) this.size, prevA);

        if (this.stack.getCount() > 1 || this.stack.isDamaged()) {
            context.renderItemDecorations(Minecraft.getInstance().font, this.stack,
                    this.x + Math.max(0, this.width() - 16),
                    this.y + Math.max(0, this.height() - 16));
        }

        context.setColor(prevR, prevG, prevB, prevA);
        RenderSystem.setShaderColor(prevR, prevG, prevB, prevA);
    }
}
