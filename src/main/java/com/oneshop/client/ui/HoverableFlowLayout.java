package com.oneshop.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.client.Minecraft;

import java.util.function.BooleanSupplier;

/**
 * A FlowLayout container that reliably and dynamically tracks its hover state,
 * accounting for child elements, ScrollContainer scissor boundaries, and active conditions.
 */
public class HoverableFlowLayout extends FlowLayout implements Hoverable {
    private boolean isHoveredState = false;
    private BooleanSupplier hoverCondition;

    public HoverableFlowLayout(Sizing horizontalSizing, Sizing verticalSizing, Algorithm algorithm) {
        super(horizontalSizing, verticalSizing, algorithm);
        this.mouseEnter().subscribe(() -> this.isHoveredState = true);
        this.mouseLeave().subscribe(() -> this.isHoveredState = false);
    }

    @Override
    public boolean canFocus(io.wispforest.owo.ui.core.Component.FocusSource source) {
        return false;
    }

    @Override
    public io.wispforest.owo.ui.util.FocusHandler focusHandler() {
        var handler = super.focusHandler();
        if (handler != null) return handler;
        ParentComponent p = this.parent();
        if (p != null && p.focusHandler() != null) {
            return p.focusHandler();
        }
        var root = this.root();
        if (root != null && root != this && root.focusHandler() != null) {
            return root.focusHandler();
        }
        var mc = Minecraft.getInstance();
        if (mc.screen instanceof OneModUi screen && screen.getFocusHandler() != null) {
            return screen.getFocusHandler();
        }
        if (mc.screen instanceof IconPickerModal picker && picker.getFocusHandler() != null) {
            return picker.getFocusHandler();
        }
        if (this.focusHandler == null) {
            this.focusHandler = new io.wispforest.owo.ui.util.FocusHandler(this);
        }
        return this.focusHandler;
    }

    @Override
    public void draw(io.wispforest.owo.ui.core.OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        super.draw(context, mouseX, mouseY, partialTicks, delta);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.isInBoundingBox(mouseX, mouseY)) {
            return false;
        }
        ParentComponent p = this.parent();
        while (p != null) {
            if (p instanceof ScrollContainer<?> scroll) {
                if (mouseX < scroll.x() || mouseX > scroll.x() + scroll.width()
                        || mouseY < scroll.y() || mouseY > scroll.y() + scroll.height()) {
                    return false;
                }
            }
            p = p.parent();
        }
        return true;
    }

    @Override
    protected void parentUpdate(float delta, int mouseX, int mouseY) {
        super.parentUpdate(delta, mouseX, mouseY);
        boolean valid = isMouseOver(mouseX, mouseY);
        if (this.isHoveredState != valid) {
            this.isHoveredState = valid;
            if (valid) {
                this.mouseEnterEvents.sink().onMouseEnter();
            } else {
                this.mouseLeaveEvents.sink().onMouseLeave();
            }
        }
    }

    @Override
    protected void updateHoveredState(int mouseX, int mouseY, boolean nowHovered) {
        super.updateHoveredState(mouseX, mouseY, nowHovered);
        boolean valid = nowHovered && isMouseOver(mouseX, mouseY);
        if (this.isHoveredState != valid) {
            this.isHoveredState = valid;
            if (valid) {
                this.mouseEnterEvents.sink().onMouseEnter();
            } else {
                this.mouseLeaveEvents.sink().onMouseLeave();
            }
        }
    }

    @Override
    public boolean isHovered() {
        if (!this.isHoveredState) {
            return false;
        }
        if (this.hoverCondition != null && !this.hoverCondition.getAsBoolean()) {
            return false;
        }
        return true;
    }

    public HoverableFlowLayout hoverCondition(BooleanSupplier condition) {
        this.hoverCondition = condition;
        return this;
    }

    public HoverableFlowLayout clipChildren(boolean clip) {
        this.allowOverflow(!clip);
        return this;
    }
}
