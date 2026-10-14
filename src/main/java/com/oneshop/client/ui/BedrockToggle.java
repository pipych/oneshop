package com.oneshop.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Easing;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
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
 * Pixel-accurate Bedrock Edition toggle switch matching authentic Bedrock UI specifications.
 * Dimensions: 28x14px switch track with 3D knob and state indicators (○ when off, | when on).
 */
public class BedrockToggle extends FlowLayout implements Hoverable {
    public static final int SWITCH_WIDTH = 28;
    public static final int SWITCH_HEIGHT = 14;

    public static final int BORDER_BLACK = 0xFF121212;

    // Toggle Off Colors
    public static final int OFF_FILL = 0xFF4A4A4C;
    public static final int OFF_INDICATOR = 0xFF242426;

    // Toggle On Colors
    public static final int ON_FILL = 0xFF448827;
    public static final int ON_HIGHLIGHT = 0xFF62A838;
    public static final int ON_SHADOW = 0xFF204412;
    public static final int ON_INDICATOR = 0xFFFFFFFF;

    // Knob Colors
    public static final int KNOB_FILL = 0xFFE0E0E0;
    public static final int KNOB_HIGHLIGHT = 0xFFFFFFFF;
    public static final int KNOB_SHADOW = 0xFFA0A0A2;

    private static final int ANIMATION_MS = 140;
    private static final int LABEL_INACTIVE = 0xA0A0A0;

    private boolean toggled;
    private boolean active = true;
    private boolean isHoveredState = false;
    private final AnimatedFloat knobProgress;
    private int lastLabelRgb = -1;
    private final LabelComponent label;
    private final FlowLayout switchBox;
    private Consumer<Boolean> onToggle;
    private BooleanSupplier hoverCondition;

    public BedrockToggle(Component labelText, boolean initialValue, Consumer<Boolean> onToggle) {
        super(Sizing.content(), Sizing.fixed(18), Algorithm.HORIZONTAL);
        this.toggled = initialValue;
        this.active = true;
        this.onToggle = onToggle;
        this.verticalAlignment(VerticalAlignment.CENTER);
        this.gap(6);
        this.cursorStyle(CursorStyle.HAND);

        // Switch Box: 28x14 px
        this.knobProgress = new AnimatedFloat(initialValue ? 1f : 0f);
        this.switchBox = new FlowLayout(Sizing.fixed(SWITCH_WIDTH), Sizing.fixed(SWITCH_HEIGHT), Algorithm.HORIZONTAL) {
            @Override
            public boolean canFocus(io.wispforest.owo.ui.core.Component.FocusSource source) {
                return false;
            }
        };
        Surface switchSurface = (context, comp) -> {
            int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
            float progress = this.knobProgress.get();
            drawSwitch(context, x, y, w, h, progress);
            if (this.active) {
                updateLabelColor(progress);
            }
            if (this.active && isHovered()) {
                context.renderOutline(x, y, w, h, BedrockSurfaces.OUTLINE_HOVER);
            }
        };
        this.switchBox.surface(switchSurface);
        this.child(this.switchBox);

        // Label
        this.label = Components.label(labelText);
        this.label.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
        this.label.shadow(true);
        this.child(this.label);

        // Explicit click consumption subscribers on root and children without triggering focus changes
        this.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0 && this.active) {
                toggle();
                return true;
            }
            return false;
        });
        this.switchBox.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0 && this.active) {
                toggle();
                return true;
            }
            return false;
        });
        this.label.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (button == 0 && this.active) {
                toggle();
                return true;
            }
            return false;
        });

        this.mouseEnter().subscribe(() -> {
            if (this.active) {
                this.isHoveredState = true;
            }
        });
        this.mouseLeave().subscribe(() -> this.isHoveredState = false);
    }

    @Override
    public boolean canFocus(io.wispforest.owo.ui.core.Component.FocusSource source) {
        return false;
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        if (button == 0 && this.active) {
            toggle();
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
    public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
        super.draw(context, mouseX, mouseY, partialTicks, delta);
    }

    /** Crossfades the label between inactive (dim) and active (bright) text styles. */
    private void updateLabelColor(float progress) {
        int rgb = AnimatedFloat.lerpArgb(0xFF000000 | LABEL_INACTIVE, 0xFF000000 | BedrockTheme.TEXT_WHITE, progress) & 0xFFFFFF;
        if (rgb != this.lastLabelRgb) {
            this.lastLabelRgb = rgb;
            this.label.color(Color.ofRgb(rgb));
        }
    }

    public static void drawSwitch(OwoUIDrawContext context, int x, int y, int w, int h, boolean on) {
        drawSwitch(context, x, y, w, h, on ? 1f : 0f);
    }

    /**
     * Draws the switch with the knob at normalized position {@code p} (0 = off/left, 1 = on/right).
     * Track colour and the off/on indicators cross-fade with the same progress.
     */
    public static void drawSwitch(OwoUIDrawContext context, int x, int y, int w, int h, float p) {
        if (w <= 0 || h <= 0) return;

        float alpha = RenderSystem.getShaderColor()[3];
        int borderBlack = AnimatedFloat.scaleAlpha(BORDER_BLACK, alpha);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // 1. Outer 1px black border
        context.fill(x, y, x + w, y + 1, borderBlack);
        context.fill(x, y + h - 1, x + w, y + h, borderBlack);
        context.fill(x, y, x + 1, y + h, borderBlack);
        context.fill(x + w - 1, y, x + w, y + h, borderBlack);

        // 2. Track fill: gray -> Bedrock green
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, AnimatedFloat.scaleAlpha(AnimatedFloat.lerpArgb(OFF_FILL, ON_FILL, p), alpha));
        if (p > 0f) {
            context.fill(x + 1, y + 1, x + w - 1, y + 2, AnimatedFloat.scaleAlpha(AnimatedFloat.scaleAlpha(ON_HIGHLIGHT, p), alpha));
            context.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, AnimatedFloat.scaleAlpha(AnimatedFloat.scaleAlpha(ON_SHADOW, p), alpha));
        }

        // 3. Right side "○" indicator fades out as the switch turns on
        if (p < 1f) {
            int offColor = AnimatedFloat.scaleAlpha(AnimatedFloat.scaleAlpha(OFF_INDICATOR, 1f - p), alpha);
            int circleX = x + 19;
            int circleY = y + 5;
            context.fill(circleX, circleY, circleX + 4, circleY + 1, offColor);         // Top
            context.fill(circleX, circleY + 3, circleX + 4, circleY + 4, offColor);     // Bottom
            context.fill(circleX, circleY + 1, circleX + 1, circleY + 3, offColor);     // Left
            context.fill(circleX + 3, circleY + 1, circleX + 4, circleY + 3, offColor); // Right
        }

        // 4. Left side "|" tick fades in as the switch turns on
        if (p > 0f) {
            context.fill(x + 6, y + 4, x + 7, y + 10, AnimatedFloat.scaleAlpha(AnimatedFloat.scaleAlpha(ON_INDICATOR, p), alpha));
        }

        // 5. Knob: 14x16 px extending 1px above and below the track, slides horizontally
        int knobX = x + Math.round(p * (w - 14));
        drawKnob(context, knobX, y - 1, 14, 16, alpha);
    }

    private static void drawKnob(OwoUIDrawContext context, int kx, int ky, int kw, int kh, float alpha) {
        int borderBlack = AnimatedFloat.scaleAlpha(BORDER_BLACK, alpha);
        // Outer 1px black border
        context.fill(kx, ky, kx + kw, ky + 1, borderBlack);
        context.fill(kx, ky + kh - 1, kx + kw, ky + kh, borderBlack);
        context.fill(kx, ky, kx + 1, ky + kh, borderBlack);
        context.fill(kx + kw - 1, ky, kx + kw, ky + kh, borderBlack);

        // Body fill (0xFFE0E0E0)
        context.fill(kx + 1, ky + 1, kx + kw - 1, ky + kh - 1, AnimatedFloat.scaleAlpha(KNOB_FILL, alpha));

        // Top highlight (0xFFFFFFFF)
        context.fill(kx + 1, ky + 1, kx + kw - 1, ky + 2, AnimatedFloat.scaleAlpha(KNOB_HIGHLIGHT, alpha));

        // Bottom shadow: 2px (0xFFA0A0A2)
        context.fill(kx + 1, ky + kh - 3, kx + kw - 1, ky + kh - 1, AnimatedFloat.scaleAlpha(KNOB_SHADOW, alpha));
    }

    public void toggle() {
        if (!this.active) return;
        this.toggled = !this.toggled;
        // Redirects smoothly from the current knob position if clicked mid-animation.
        this.knobProgress.animateTo(this.toggled ? 1f : 0f, ANIMATION_MS, Easing.CUBIC);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        if (this.onToggle != null) {
            this.onToggle.accept(this.toggled);
        }
    }

    public boolean toggled() {
        return this.toggled;
    }

    public BedrockToggle toggled(boolean toggled) {
        this.toggled = toggled;
        this.knobProgress.animateTo(toggled ? 1f : 0f, ANIMATION_MS, Easing.CUBIC);
        return this;
    }

    public BedrockToggle active(boolean active) {
        this.active = active;
        this.lastLabelRgb = -1;
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

    public BedrockToggle enabled(boolean enabled) {
        return active(enabled);
    }

    public boolean enabled() {
        return this.active;
    }

    public BedrockToggle onToggle(Consumer<Boolean> onToggle) {
        this.onToggle = onToggle;
        return this;
    }

    public BedrockToggle hoverCondition(BooleanSupplier condition) {
        this.hoverCondition = condition;
        return this;
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
}
