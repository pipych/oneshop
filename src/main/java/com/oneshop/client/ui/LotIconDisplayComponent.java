package com.oneshop.client.ui;

import com.oneshop.shop.LotIcon;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.world.item.ItemStack;

/**
 * Unified UI component that renders a {@link LotIcon}, supporting both 32x32 scaled items
 * and arbitrary textures.
 */
public class LotIconDisplayComponent extends FlowLayout {
    private LotIcon icon;
    private final int size;

    public LotIconDisplayComponent(LotIcon icon, int size) {
        super(Sizing.fixed(size), Sizing.fixed(size), Algorithm.VERTICAL);
        this.icon = icon;
        this.size = size;
        this.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        rebuild();
    }

    public LotIconDisplayComponent(LotIcon icon) {
        this(icon, 32);
    }

    public void setIcon(LotIcon newIcon) {
        this.icon = newIcon;
        rebuild();
    }

    public LotIcon getIcon() {
        return icon;
    }

    private void rebuild() {
        this.clearChildren();
        if (icon == null || icon.isItem()) {
            ItemStack stack = (icon != null) ? icon.item() : ItemStack.EMPTY;
            this.child(new ItemDisplayComponent(stack, size));
        } else {
            this.child(new TextureDisplayComponent(icon.texture(), size));
        }
    }

    @Override
    public boolean canFocus(io.wispforest.owo.ui.core.Component.FocusSource source) {
        return false;
    }

    @Override
    public io.wispforest.owo.ui.util.FocusHandler focusHandler() {
        var handler = super.focusHandler();
        if (handler != null) return handler;
        io.wispforest.owo.ui.core.ParentComponent p = this.parent();
        if (p != null && p.focusHandler() != null) return p.focusHandler();
        var r = this.root();
        if (r != null && r != this && r.focusHandler() != null) return r.focusHandler();
        if (this.focusHandler == null) {
            this.focusHandler = new io.wispforest.owo.ui.util.FocusHandler(this);
        }
        return this.focusHandler;
    }

    @Override
    public void draw(io.wispforest.owo.ui.core.OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        float[] shaderColor = com.mojang.blaze3d.systems.RenderSystem.getShaderColor();
        float prevR = shaderColor[0], prevG = shaderColor[1], prevB = shaderColor[2], prevA = shaderColor[3];

        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, prevA);
        context.setColor(1.0f, 1.0f, 1.0f, prevA);

        super.draw(context, mouseX, mouseY, partialTicks, delta);

        context.setColor(prevR, prevG, prevB, prevA);
        com.mojang.blaze3d.systems.RenderSystem.setShaderColor(prevR, prevG, prevB, prevA);
    }
}
