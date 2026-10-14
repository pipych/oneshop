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

    public TextureDisplayComponent(ResourceLocation texture, int size) {
        this.texture = texture;
        this.defaultSize = size;
        this.sizing(Sizing.fixed(size), Sizing.fixed(size));
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
        if (this.texture == null) return;
        float[] prevShader = RenderSystem.getShaderColor().clone();
        float currentAlpha = prevShader[3];

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, currentAlpha);
        context.setColor(1.0f, 1.0f, 1.0f, currentAlpha);

        context.blit(this.texture, this.x, this.y, this.width, this.height, 0.0f, 0.0f, 1, 1, 1, 1);

        context.setColor(prevShader[0], prevShader[1], prevShader[2], prevShader[3]);
        RenderSystem.setShaderColor(prevShader[0], prevShader[1], prevShader[2], prevShader[3]);
    }
}
