package com.oneshop.client.ui;

import io.wispforest.owo.ui.component.VanillaWidgetComponent;
import io.wispforest.owo.ui.core.Component;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.inject.GreedyInputComponent;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

/**
 * Focus-enabled wrapper around vanilla AbstractWidget (such as EditBox/TextBoxComponent).
 * Overrides canFocus(FocusSource) to return true so owo-lib's FocusHandler will focus it on mouse click,
 * implements GreedyInputComponent so parent screens know it consumes input,
 * and maintains the widget's internal focus state (displaying blinking text cursor and accepting key events).
 */
public class FocusableWidgetComponent extends VanillaWidgetComponent implements GreedyInputComponent {
    private final AbstractWidget innerWidget;

    public FocusableWidgetComponent(AbstractWidget widget) {
        super(widget);
        this.innerWidget = widget;
        this.cursorStyle(CursorStyle.TEXT);
        if (widget instanceof EditBox) {
            this.margins(Insets.none());
        }
    }

    @Override
    public io.wispforest.owo.ui.base.BaseComponent margins(Insets margins) {
        if (this.innerWidget instanceof EditBox) {
            return super.margins(margins.add(-1, -1, -1, -1));
        } else {
            return super.margins(margins);
        }
    }

    @Override
    public boolean canFocus(Component.FocusSource source) {
        var handler = this.focusHandler();
        if (handler != null) return true;
        var root = this.root();
        return root != null && root != this && root.focusHandler() != null;
    }

    @Override
    public void onFocusGained(Component.FocusSource source) {
        super.onFocusGained(source);
        if (this.innerWidget != null) {
            this.innerWidget.setFocused(true);
        }
    }

    @Override
    public void onFocusLost() {
        super.onFocusLost();
        if (this.innerWidget != null) {
            this.innerWidget.setFocused(false);
        }
    }

    @Override
    public boolean onMouseDown(double mouseX, double mouseY, int button) {
        focus();
        return super.onMouseDown(mouseX, mouseY, button);
    }

    public void focus() {
        if (this.innerWidget != null) {
            this.innerWidget.setFocused(true);
        }
        var handler = this.focusHandler();
        if (handler == null) {
            var root = this.root();
            if (root != null && root != this) {
                handler = root.focusHandler();
            }
        }
        if (handler != null) {
            try {
                handler.focus(this, Component.FocusSource.MOUSE_CLICK);
            } catch (Throwable ignored) {}
        }
    }

    public void clearFocus() {
        if (this.innerWidget != null) {
            this.innerWidget.setFocused(false);
        }
        var handler = this.focusHandler();
        if (handler == null) {
            var root = this.root();
            if (root != null && root != this) {
                handler = root.focusHandler();
            }
        }
        if (handler != null) {
            try {
                if (handler.focused() == this) {
                    handler.focus(null, Component.FocusSource.MOUSE_CLICK);
                }
            } catch (Throwable ignored) {}
        }
    }

    public boolean isFocused() {
        var handler = this.focusHandler();
        if (handler != null && handler.focused() == this) {
            return true;
        }
        return this.innerWidget != null && this.innerWidget.isFocused();
    }

    public boolean isWidgetFocused() {
        return isFocused();
    }

    public AbstractWidget getInnerWidget() {
        return this.innerWidget;
    }

    public FocusableWidgetComponent verticalAlignment(io.wispforest.owo.ui.core.VerticalAlignment alignment) {
        return this;
    }
}
