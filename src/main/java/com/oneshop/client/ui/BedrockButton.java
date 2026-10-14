package com.oneshop.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Bedrock Dark UI 1px beveled button implemented as a pure owo FlowLayout.
 * Features 1px beveled pixel borders, dynamic per-frame hover effects, hover outline, and click sounds without vanilla button artifacts.
 */
public class BedrockButton extends FlowLayout implements Hoverable {
    private boolean active = true;
    private boolean isHoveredState = false;
    private final LabelComponent label;
    private Consumer<BedrockButton> onPress;
    private BooleanSupplier hoverCondition;

    public BedrockButton(Component text, Sizing horizontalSizing, Sizing verticalSizing, Consumer<BedrockButton> onPress) {
        super(horizontalSizing, verticalSizing, Algorithm.HORIZONTAL);
        this.onPress = onPress;
        this.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        this.padding(io.wispforest.owo.ui.core.Insets.of(2, 3, 6, 6));
        this.cursorStyle(CursorStyle.HAND);

        this.label = Components.label(text);
        this.label.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
        this.label.shadow(true);
        this.child(this.label);

        this.surface(BedrockSurfaces.bedrockGrayButton());

        this.mouseEnter().subscribe(() -> {
            if (this.active) {
                this.isHoveredState = true;
            }
        });
        this.mouseLeave().subscribe(() -> this.isHoveredState = false);

        this.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0 && this.active) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (this.onPress != null) {
                    this.onPress.accept(this);
                }
                return true;
            }
            return false;
        });
    }

    public BedrockButton(Component text, Sizing horizontalSizing, Sizing verticalSizing, Runnable onClick) {
        this(text, horizontalSizing, verticalSizing, b -> onClick.run());
    }

    public BedrockButton(io.wispforest.owo.ui.core.Component icon, Sizing horizontalSizing, Sizing verticalSizing, Consumer<BedrockButton> onPress) {
        super(horizontalSizing, verticalSizing, Algorithm.HORIZONTAL);
        this.onPress = onPress;
        this.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        this.padding(io.wispforest.owo.ui.core.Insets.none());
        this.cursorStyle(CursorStyle.HAND);

        this.label = null;
        this.child(icon);

        this.surface(BedrockSurfaces.bedrockGrayButton());

        this.mouseEnter().subscribe(() -> {
            if (this.active) {
                this.isHoveredState = true;
            }
        });
        this.mouseLeave().subscribe(() -> this.isHoveredState = false);

        this.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0 && this.active) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                if (this.onPress != null) {
                    this.onPress.accept(this);
                }
                return true;
            }
            return false;
        });
    }

    public BedrockButton(io.wispforest.owo.ui.core.Component icon, Sizing horizontalSizing, Sizing verticalSizing, Runnable onClick) {
        this(icon, horizontalSizing, verticalSizing, b -> onClick.run());
    }

    public BedrockButton(Component text, Consumer<BedrockButton> onPress) {
        this(text, Sizing.content(6), Sizing.content(3), onPress);
    }

    public BedrockButton(Component text, Runnable onClick) {
        this(text, Sizing.content(6), Sizing.content(3), b -> onClick.run());
    }

    @Override
    public boolean canFocus(io.wispforest.owo.ui.core.Component.FocusSource source) {
        return false;
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        if (button == 0 && this.active) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            if (this.onPress != null) {
                this.onPress.accept(this);
            }
            return true;
        }
        return super.onMouseDown(mouseX, mouseY, button);
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
        boolean validHover = this.active && isMouseOver(mouseX, mouseY);
        if (this.isHoveredState != validHover) {
            this.isHoveredState = validHover;
            if (validHover) {
                this.mouseEnterEvents.sink().onMouseEnter();
            } else {
                this.mouseLeaveEvents.sink().onMouseLeave();
            }
        }
    }

    @Override
    protected void updateHoveredState(int mouseX, int mouseY, boolean nowHovered) {
        super.updateHoveredState(mouseX, mouseY, nowHovered);
        boolean validHover = nowHovered && this.active && isMouseOver(mouseX, mouseY);
        if (this.isHoveredState != validHover) {
            this.isHoveredState = validHover;
            if (validHover) {
                this.mouseEnterEvents.sink().onMouseEnter();
            } else {
                this.mouseLeaveEvents.sink().onMouseLeave();
            }
        }
    }

    @Override
    public boolean isHovered() {
        if (!this.active || !this.isHoveredState) {
            return false;
        }
        if (this.hoverCondition != null && !this.hoverCondition.getAsBoolean()) {
            return false;
        }
        return true;
    }

    public BedrockButton hoverCondition(BooleanSupplier condition) {
        this.hoverCondition = condition;
        return this;
    }

    public BedrockButton active(boolean active) {
        this.active = active;
        if (!active) {
            this.isHoveredState = false;
        }
        this.cursorStyle(active ? CursorStyle.HAND : CursorStyle.POINTER);
        if (this.label != null) {
            this.label.color(Color.ofRgb(active ? BedrockTheme.TEXT_WHITE : BedrockSurfaces.TEXT_DISABLED));
        }
        return this;
    }

    public boolean active() {
        return this.active;
    }

    public BedrockButton text(Component text) {
        if (this.label != null) {
            this.label.text(text);
        }
        return this;
    }

    public BedrockButton textColor(int rgb) {
        if (this.label != null) {
            this.label.color(Color.ofRgb(rgb));
        }
        return this;
    }
}
