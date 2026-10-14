package com.oneshop.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.base.BaseComponent;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders a ResourceLocation texture scaled to component dimensions with alpha blending.
 */
public class TextureDisplayComponent extends BaseComponent {
    private ResourceLocation texture;
    private final int defaultSize;
    private float u;
    private float v;
    private int regionWidth;
    private int regionHeight;
    private int textureWidth;
    private int textureHeight;

    public TextureDisplayComponent(ResourceLocation texture, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        this.texture = texture;
        this.defaultSize = Math.max(width, height);
        this.u = u;
        this.v = v;
        this.regionWidth = regionWidth;
        this.regionHeight = regionHeight;
        this.textureWidth = textureWidth;
        this.textureHeight = textureHeight;
        this.sizing(Sizing.fixed(width), Sizing.fixed(height));
    }

    public TextureDisplayComponent(ResourceLocation texture, int size) {
        this(texture, size, size, 0.0f, 0.0f, 1, 1, 1, 1);
    }

    public TextureDisplayComponent(ResourceLocation texture) {
        this(texture, 32);
    }

    public void setTexture(ResourceLocation texture) {
        this.texture = texture;
    }

    public ResourceLocation getTexture() {
        return texture;
    }

    @Override
    protected int determineHorizontalContentSize(Sizing sizing) {
        return defaultSize;
    }

    @Override
    protected int determineVerticalContentSize(Sizing sizing) {
        return defaultSize;
    }

    @Override
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        if (!io.wispforest.owo.ui.util.ScissorStack.isVisible(this, context.pose())) return;
        if (this.texture == null) return;
        float[] shaderColor = RenderSystem.getShaderColor();
        float prevR = shaderColor[0], prevG = shaderColor[1], prevB = shaderColor[2], prevA = shaderColor[3];
        if (prevA <= 0.003f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, prevA);
        context.blit(this.texture, this.x, this.y, this.width, this.height, this.u, this.v, this.regionWidth, this.regionHeight, this.textureWidth, this.textureHeight);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(prevR, prevG, prevB, prevA);
    }
}
