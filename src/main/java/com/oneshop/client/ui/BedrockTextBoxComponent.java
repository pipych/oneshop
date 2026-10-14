package com.oneshop.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.core.Sizing;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.lang.reflect.Field;
import java.util.function.BiFunction;

/**
 * Custom TextBoxComponent for Bedrock Dark UI that ensures the text,
 * blinking cursor, and selection box are always vertically centered
 * relative to the widget height:
 *   textY = this.getY() + (this.getHeight() - 9) / 2;
 */
public class BedrockTextBoxComponent extends TextBoxComponent {

    private static final WidgetSprites SPRITES = new WidgetSprites(
            ResourceLocation.withDefaultNamespace("widget/text_field"),
            ResourceLocation.withDefaultNamespace("widget/text_field_highlighted")
    );

    private static final Field DISPLAY_POS_FIELD;
    private static final Field FOCUSED_TIME_FIELD;
    private static final Field HIGHLIGHT_POS_FIELD;

    static {
        DISPLAY_POS_FIELD = findField(EditBox.class, "displayPos", "f_94089_");
        FOCUSED_TIME_FIELD = findField(EditBox.class, "focusedTime", "f_315802_");
        HIGHLIGHT_POS_FIELD = findField(EditBox.class, "highlightPos", "f_94091_");
    }

    private static Field findField(Class<?> clazz, String... names) {
        for (String name : names) {
            try {
                Field f = clazz.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private Component customHint;
    private String customSuggestion;
    private int customMaxLength = 32;
    private int customTextColor = 14737632;
    private int customTextColorUneditable = 7368816;
    private boolean customIsEditable = true;
    private long localFocusedTime = Util.getMillis();
    private BiFunction<String, Integer, FormattedCharSequence> customFormatter =
            (s, i) -> FormattedCharSequence.forward(s, Style.EMPTY);

    public BedrockTextBoxComponent(Sizing horizontalSizing) {
        super(horizontalSizing);
        this.setBordered(false);
        this.setCanLoseFocus(true);
    }

    public BedrockTextBoxComponent(Sizing horizontalSizing, String text) {
        this(horizontalSizing);
        if (text != null && !text.isEmpty()) {
            this.setValue(text);
            this.moveCursorToStart(false);
        }
    }

    @Override
    public void setHint(Component hint) {
        super.setHint(hint);
        this.customHint = hint;
    }

    @Override
    public void setSuggestion(String suggestion) {
        super.setSuggestion(suggestion);
        this.customSuggestion = suggestion;
    }

    @Override
    public void setMaxLength(int length) {
        super.setMaxLength(length);
        this.customMaxLength = length;
    }

    @Override
    public void setTextColor(int color) {
        super.setTextColor(color);
        this.customTextColor = color;
    }

    @Override
    public void setTextColorUneditable(int color) {
        super.setTextColorUneditable(color);
        this.customTextColorUneditable = color;
    }

    @Override
    public void setEditable(boolean editable) {
        super.setEditable(editable);
        this.customIsEditable = editable;
    }

    @Override
    public void setFormatter(BiFunction<String, Integer, FormattedCharSequence> formatter) {
        super.setFormatter(formatter);
        this.customFormatter = formatter;
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            this.localFocusedTime = Util.getMillis();
        }
    }

    protected int getDisplayPos() {
        if (DISPLAY_POS_FIELD != null) {
            try {
                return DISPLAY_POS_FIELD.getInt(this);
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    protected long getFocusedTime() {
        if (FOCUSED_TIME_FIELD != null) {
            try {
                return FOCUSED_TIME_FIELD.getLong(this);
            } catch (Throwable ignored) {}
        }
        return this.localFocusedTime;
    }

    protected int getHighlightPos() {
        if (HIGHLIGHT_POS_FIELD != null) {
            try {
                return HIGHLIGHT_POS_FIELD.getInt(this);
            } catch (Throwable ignored) {}
        }
        return this.getCursorPosition();
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.isVisible()) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        if (this.isBordered()) {
            ResourceLocation resourcelocation = SPRITES.get(this.isActive(), this.isFocused());
            guiGraphics.blitSprite(resourcelocation, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        }

        var font = Minecraft.getInstance().font;
        int color = this.customIsEditable ? this.customTextColor : this.customTextColorUneditable;
        int displayPos = this.getDisplayPos();
        int cursorPos = this.getCursorPosition();
        int relCursorPos = cursorPos - displayPos;
        String val = this.getValue();
        String visibleText = font.plainSubstrByWidth(val.substring(displayPos), this.getInnerWidth());
        boolean cursorWithinVisible = relCursorPos >= 0 && relCursorPos <= visibleText.length();
        boolean blinkOn = this.isFocused() && (Util.getMillis() - this.getFocusedTime()) / 300L % 2L == 0L && cursorWithinVisible;

        int textX = this.isBordered() ? this.getX() + 4 : this.getX();
        int textY = ItemIdResolver.calculateCenteredTextY(this.getY(), this.getHeight());

        int renderX = textX;
        int highlightPos = this.getHighlightPos();
        int relHighlightPos = Mth.clamp(highlightPos - displayPos, 0, visibleText.length());

        if (!visibleText.isEmpty()) {
            String beforeCursor = cursorWithinVisible ? visibleText.substring(0, relCursorPos) : visibleText;
            renderX = guiGraphics.drawString(font, this.customFormatter.apply(beforeCursor, displayPos), textX, textY, color, this.getTextShadow());
        }

        boolean cursorNotAtEnd = cursorPos < val.length() || val.length() >= this.customMaxLength;
        int cursorBarX = renderX;
        if (!cursorWithinVisible) {
            cursorBarX = relCursorPos > 0 ? textX + this.width : textX;
        } else if (cursorNotAtEnd) {
            cursorBarX = renderX - 1;
            renderX--;
        }

        if (!visibleText.isEmpty() && cursorWithinVisible && relCursorPos < visibleText.length()) {
            guiGraphics.drawString(font, this.customFormatter.apply(visibleText.substring(relCursorPos), cursorPos), renderX, textY, color, this.getTextShadow());
        }

        if (this.customHint != null && visibleText.isEmpty() && !this.isFocused()) {
            guiGraphics.drawString(font, this.customHint, renderX, textY, color, this.getTextShadow());
        }

        if (!cursorNotAtEnd && this.customSuggestion != null) {
            guiGraphics.drawString(font, this.customSuggestion, cursorBarX - 1, textY, -8355712, this.getTextShadow());
        }

        // Blinking cursor
        if (blinkOn) {
            if (cursorNotAtEnd) {
                // Vertical bar cursor centered vertically with textY
                float alpha = RenderSystem.getShaderColor()[3];
                guiGraphics.fill(RenderType.guiOverlay(), cursorBarX, ItemIdResolver.getCursorTopY(textY), cursorBarX + 1, ItemIdResolver.getCursorBottomY(textY), AnimatedFloat.scaleAlpha(-3092272, alpha));
            } else {
                // Underscore cursor
                guiGraphics.drawString(font, "_", cursorBarX, textY, color, this.getTextShadow());
            }
        }

        // Text selection box synchronized with textY
        if (relHighlightPos != relCursorPos) {
            int highlightEndX = textX + font.width(visibleText.substring(0, relHighlightPos));
            renderHighlight(guiGraphics, cursorBarX, ItemIdResolver.getCursorTopY(textY), highlightEndX - 1, ItemIdResolver.getCursorBottomY(textY));
        }
    }

    private void renderHighlight(GuiGraphics guiGraphics, int minX, int minY, int maxX, int maxY) {
        if (minX < maxX) {
            int temp = minX;
            minX = maxX;
            maxX = temp;
        }

        if (minY < maxY) {
            int temp = minY;
            minY = maxY;
            maxY = temp;
        }

        if (maxX > this.getX() + this.width) {
            maxX = this.getX() + this.width;
        }

        if (minX > this.getX() + this.width) {
            minX = this.getX() + this.width;
        }

        float alpha = RenderSystem.getShaderColor()[3];
        guiGraphics.fill(RenderType.guiTextHighlight(), minX, minY, maxX, maxY, AnimatedFloat.scaleAlpha(-16776961, alpha));
    }
}
