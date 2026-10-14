package com.oneshop.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.Easing;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Universal Bedrock UI Screen engine providing animated blur/scrim,
 * layered modal stacking with smooth cubic animations, input focus isolation,
 * and standard Bedrock dialog layouts.
 */
public abstract class OneModUi extends BaseOwoScreen<StackLayout> {
    public static final int MODAL_SCRIM_ALPHA = 0x90;
    public static final int OPEN_DURATION_MS = 210;
    public static final int CLOSE_DURATION_MS = 170;

    public static class ModalEntry {
        public final FlowLayout modalBackdrop;
        public final FlowLayout dialog;
        public final AnimatableProperty<Float> alpha;
        public final AnimatedDialogWrapper wrapper;
        public boolean isClosing = false;

        public ModalEntry(FlowLayout modalBackdrop, FlowLayout dialog, AnimatableProperty<Float> alpha, AnimatedDialogWrapper wrapper) {
            this.modalBackdrop = modalBackdrop;
            this.dialog = dialog;
            this.alpha = alpha;
            this.wrapper = wrapper;
        }
    }

    protected StackLayout rootStack;
    protected final AnimatableProperty<Float> windowAlpha = AnimatableProperty.of(0.0f);
    protected boolean isClosing = false;
    protected ModalEntry currentModal = null;
    protected final Deque<ModalEntry> modalEntryStack = new ArrayDeque<>();

    protected io.wispforest.owo.ui.core.Component activeModal = null;
    protected final Deque<io.wispforest.owo.ui.core.Component> modalStack = new ArrayDeque<>();

    protected int currentMouseX;
    protected int currentMouseY;

    private static java.lang.reflect.Field blurEffectField = null;
    private static boolean blurEffectFieldLookupDone = false;

    protected OneModUi() {
        super();
    }

    protected OneModUi(Component title) {
        super(title);
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::stack);
    }

    /**
     * Initializes the root stack with entrance animation and mounts a standard centered Bedrock panel.
     */
    protected FlowLayout setupRootWindow(StackLayout root, int fillWidthPercent, int fillHeightPercent) {
        this.rootStack = root;
        root.surface(Surface.BLANK);

        this.isClosing = false;
        this.windowAlpha.set(0.0f);
        this.windowAlpha.animate(OPEN_DURATION_MS, Easing.CUBIC, 1.0f);

        FlowLayout mainFrame = Containers.verticalFlow(Sizing.fill(fillWidthPercent), Sizing.fill(fillHeightPercent));
        mainFrame.surface(BedrockTheme.PANEL);
        mainFrame.padding(Insets.of(6));
        mainFrame.positioning(Positioning.relative(50, 50));
        root.child(mainFrame);
        return mainFrame;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        float alpha = Math.max(0.0f, Math.min(1.0f, this.windowAlpha.get()));
        if (this.minecraft != null && alpha > 0.001f) {
            renderAnimatedBlur(delta, alpha);
        }
        int scrimAlpha = Math.round(0x80 * alpha);
        if (scrimAlpha > 0) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            context.fill(0, 0, this.width, this.height, (scrimAlpha << 24) | 0x101010);
        }

        this.currentMouseX = mouseX;
        this.currentMouseY = mouseY;
        onFrameUpdate(mouseX, mouseY, delta);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        context.setColor(1.0f, 1.0f, 1.0f, alpha);
        super.render(context, mouseX, mouseY, delta);
        context.flush();
        context.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * Optional frame update hook called during render.
     */
    protected void onFrameUpdate(int mouseX, int mouseY, float delta) {}

    protected void renderAnimatedBlur(float delta, float progress) {
        if (this.minecraft == null || this.minecraft.gameRenderer == null) return;
        int blurriness = this.minecraft.options.getMenuBackgroundBlurriness();
        if (blurriness < 1) return;

        try {
            RenderSystem.disableDepthTest();
            boolean customApplied = false;
            if (!blurEffectFieldLookupDone) {
                blurEffectFieldLookupDone = true;
                try {
                    for (java.lang.reflect.Field f : net.minecraft.client.renderer.GameRenderer.class.getDeclaredFields()) {
                        if (net.minecraft.client.renderer.PostChain.class.isAssignableFrom(f.getType())) {
                            f.setAccessible(true);
                            if ("blurEffect".equals(f.getName()) || f.getName().toLowerCase(Locale.ROOT).contains("blur")) {
                                blurEffectField = f;
                                break;
                            } else if (blurEffectField == null) {
                                blurEffectField = f;
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (blurEffectField != null) {
                net.minecraft.client.renderer.PostChain postChain =
                        (net.minecraft.client.renderer.PostChain) blurEffectField.get(this.minecraft.gameRenderer);
                if (postChain != null) {
                    postChain.setUniform("Radius", Math.max(0.01f, blurriness * progress));
                    postChain.process(delta);
                    customApplied = true;
                }
            }
            if (!customApplied) {
                this.minecraft.gameRenderer.processBlurEffect(delta * progress);
            }
            this.minecraft.getMainRenderTarget().bindWrite(false);
        } catch (Throwable e) {
            this.renderBlurredBackground(delta);
        }
    }

    public io.wispforest.owo.ui.util.FocusHandler getFocusHandler() {
        if (this.uiAdapter == null || this.uiAdapter.rootComponent == null) {
            return null;
        }
        return this.uiAdapter.rootComponent.focusHandler();
    }

    public void unfocusActiveTextFields() {
        if (this.uiAdapter == null) return;
        try {
            var focusHandler = getFocusHandler();
            if (focusHandler != null) {
                var focused = focusHandler.focused();
                if (focused instanceof FocusableWidgetComponent f) {
                    f.onFocusLost();
                    if (f.getInnerWidget() != null) {
                        f.getInnerWidget().setFocused(false);
                    }
                } else if (focused instanceof EditBox e) {
                    e.setFocused(false);
                }
                focusHandler.focus(null, io.wispforest.owo.ui.core.Component.FocusSource.MOUSE_CLICK);
            }
        } catch (Throwable ignored) {}

        onUnfocusExtraFields();

        StackLayout root = this.uiAdapter != null && this.uiAdapter.rootComponent != null
                ? this.uiAdapter.rootComponent
                : this.rootStack;
        if (root != null) {
            try {
                var descendants = new ArrayList<io.wispforest.owo.ui.core.Component>();
                root.collectDescendants(descendants);
                for (var c : descendants) {
                    if (c instanceof FocusableWidgetComponent f) {
                        f.onFocusLost();
                        if (f.getInnerWidget() != null) {
                            f.getInnerWidget().setFocused(false);
                        }
                    } else if (c instanceof EditBox e) {
                        e.setFocused(false);
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (this.currentModal != null && this.currentModal.modalBackdrop != null) {
            try {
                var descendants = new ArrayList<io.wispforest.owo.ui.core.Component>();
                this.currentModal.modalBackdrop.collectDescendants(descendants);
                for (var c : descendants) {
                    if (c instanceof FocusableWidgetComponent f) {
                        f.onFocusLost();
                        if (f.getInnerWidget() != null) {
                            f.getInnerWidget().setFocused(false);
                        }
                    } else if (c instanceof EditBox e) {
                        e.setFocused(false);
                    }
                }
            } catch (Throwable ignored) {}
        }
        for (ModalEntry entry : this.modalEntryStack) {
            if (entry != null && entry.modalBackdrop != null) {
                try {
                    var descendants = new ArrayList<io.wispforest.owo.ui.core.Component>();
                    entry.modalBackdrop.collectDescendants(descendants);
                    for (var c : descendants) {
                        if (c instanceof FocusableWidgetComponent f) {
                            f.onFocusLost();
                            if (f.getInnerWidget() != null) {
                                f.getInnerWidget().setFocused(false);
                            }
                        } else if (c instanceof EditBox e) {
                            e.setFocused(false);
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    /** Hook for subclasses to unfocus screen-specific fields. */
    protected void onUnfocusExtraFields() {}

    public boolean isAnyTextBoxFocused() {
        if (this.isClosing) return false;
        if (isExtraTextBoxFocused()) return true;
        var focusHandler = getFocusHandler();
        if (focusHandler != null) {
            io.wispforest.owo.ui.core.Component focused = focusHandler.focused();
            if (focused instanceof FocusableWidgetComponent f && f.isWidgetFocused()) {
                return true;
            }
            if (focused instanceof EditBox e && e.isFocused()) {
                return true;
            }
        }
        return false;
    }

    /** Hook for subclasses to check if screen-specific text fields are focused. */
    protected boolean isExtraTextBoxFocused() {
        return false;
    }

    public boolean isComponentHovered(io.wispforest.owo.ui.core.Component comp) {
        if (isModalOpen() && !isComponentInModal(comp)) {
            return false;
        }
        if (!comp.isInBoundingBox(currentMouseX, currentMouseY)) {
            return false;
        }
        ParentComponent parent = comp.parent();
        while (parent != null) {
            if (!parent.isInBoundingBox(currentMouseX, currentMouseY)) {
                return false;
            }
            parent = parent.parent();
        }
        return true;
    }

    public boolean isComponentInModal(io.wispforest.owo.ui.core.Component c) {
        if (!isModalOpen()) return false;
        ParentComponent parent = c instanceof ParentComponent p ? p : c.parent();
        while (parent != null) {
            if (this.currentModal != null && parent == this.currentModal.modalBackdrop) return true;
            if (parent == this.activeModal) return true;
            for (ModalEntry stacked : this.modalEntryStack) {
                if (parent == stacked.modalBackdrop) return true;
            }
            for (io.wispforest.owo.ui.core.Component stacked : this.modalStack) {
                if (parent == stacked) return true;
            }
            parent = parent.parent();
        }
        return false;
    }

    public FlowLayout createLabeledInput(String labelTranslationKey, TextBoxComponent textBox, String hint) {
        return createLabeledInput(labelTranslationKey, textBox, hint, null);
    }

    public FlowLayout createLabeledInput(String labelTranslationKey, TextBoxComponent textBox, String hint, BooleanSupplier isErrorSupplier) {
        FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        row.verticalAlignment(VerticalAlignment.CENTER);
        row.gap(4);

        LabelComponent label = Components.label(Component.translatable(labelTranslationKey));
        label.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
        label.sizing(Sizing.fixed(80), Sizing.content());
        row.child(label);

        HoverableFlowLayout inputFrame = new HoverableFlowLayout(Sizing.expand(100), Sizing.fixed(16), FlowLayout.Algorithm.HORIZONTAL);
        inputFrame.hoverCondition(() -> !isModalOpen() || isComponentInModal(inputFrame));
        inputFrame.padding(Insets.of(0, 0, 4, 4));
        inputFrame.verticalAlignment(VerticalAlignment.CENTER);

        textBox.setHint(Component.translatable(hint));
        textBox.setBordered(false);
        textBox.setCanLoseFocus(true);

        FocusableWidgetComponent wrapped = new FocusableWidgetComponent(textBox);
        wrapped.sizing(Sizing.fill(100), Sizing.fill(100));
        wrapped.verticalAlignment(VerticalAlignment.CENTER);

        Surface baseSurface = (context, comp) -> {
            int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
            BedrockTheme.drawBeveledBox(context, x, y, w, h, BedrockTheme.BG_INPUT, BedrockTheme.BORDER_DARK, BedrockTheme.BORDER_LIGHT);
            if (isErrorSupplier != null && isErrorSupplier.getAsBoolean()) {
                BedrockTheme.drawOutline(context, x, y, w, h, BedrockTheme.TEXT_RED);
            }
        };
        inputFrame.surface(BedrockTheme.withHoverOutline(baseSurface, () -> (inputFrame.isHovered() || wrapped.isWidgetFocused()) && !(isErrorSupplier != null && isErrorSupplier.getAsBoolean()), 0, BedrockTheme.OUTLINE_HOVER));

        inputFrame.mouseDown().subscribe((mouseX, mouseY, button) -> {
            wrapped.focus();
            return false;
        });

        wrapped.mouseDown().subscribe((mouseX, mouseY, button) -> {
            wrapped.focus();
            return false;
        });

        inputFrame.child(wrapped);
        row.child(inputFrame);
        return row;
    }

    public HoverableFlowLayout createNumericInputFrame(TextBoxComponent textBox, FocusableWidgetComponent[] outWrapped, int width) {
        textBox.setBordered(false);
        textBox.setCanLoseFocus(true);

        FocusableWidgetComponent wrapped = new FocusableWidgetComponent(textBox);
        wrapped.sizing(Sizing.fill(100), Sizing.fill(100));
        wrapped.verticalAlignment(VerticalAlignment.CENTER);
        if (outWrapped != null && outWrapped.length > 0) {
            outWrapped[0] = wrapped;
        }

        HoverableFlowLayout inputFrame = new HoverableFlowLayout(Sizing.fixed(width), Sizing.fixed(18), FlowLayout.Algorithm.HORIZONTAL);
        inputFrame.hoverCondition(() -> !isModalOpen() || isComponentInModal(inputFrame));
        inputFrame.padding(Insets.of(0, 0, 4, 4));
        inputFrame.verticalAlignment(VerticalAlignment.CENTER);

        Surface baseSurface = (context, comp) -> {
            int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
            BedrockTheme.drawBeveledBox(context, x, y, w, h, BedrockTheme.BG_INPUT, BedrockTheme.BORDER_DARK, BedrockTheme.BORDER_LIGHT);
        };
        inputFrame.surface(BedrockTheme.withHoverOutline(baseSurface, () -> inputFrame.isHovered() || wrapped.isWidgetFocused(), 0, BedrockTheme.OUTLINE_HOVER));

        inputFrame.mouseDown().subscribe((mouseX, mouseY, button) -> {
            wrapped.focus();
            return false;
        });
        wrapped.mouseDown().subscribe((mouseX, mouseY, button) -> {
            wrapped.focus();
            return false;
        });

        inputFrame.child(wrapped);
        return inputFrame;
    }

    public boolean isModalOpen() {
        return this.currentModal != null && !this.currentModal.isClosing;
    }

    public void pushModal(FlowLayout modalBackdrop, FlowLayout dialog) {
        unfocusActiveTextFields();
        if (this.currentModal != null) {
            this.modalEntryStack.push(this.currentModal);
        }
        int z = 500 + this.modalEntryStack.size() * 50;
        modalBackdrop.zIndex(z);

        AnimatableProperty<Float> modalAlpha = AnimatableProperty.of(0.0f);
        modalAlpha.animate(OPEN_DURATION_MS, Easing.CUBIC, 1.0f);

        modalBackdrop.surface((context, component) -> {
            float a = Math.max(0.0f, Math.min(1.0f, modalAlpha.get()));
            float currentAlpha = RenderSystem.getShaderColor()[3];
            int alpha = Math.round(MODAL_SCRIM_ALPHA * a * currentAlpha);
            if (alpha > 0) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                context.fill(component.x(), component.y(), component.x() + component.width(), component.y() + component.height(), alpha << 24);
            }
        });

        AnimatedDialogWrapper wrapper = null;
        if (dialog != null) {
            if (modalBackdrop.children().contains(dialog)) {
                modalBackdrop.removeChild(dialog);
            }
            final ModalEntry[] entryRef = new ModalEntry[1];
            wrapper = new AnimatedDialogWrapper(modalAlpha, () -> entryRef[0] != null && entryRef[0].isClosing);
            wrapper.zIndex(z + 1);
            wrapper.child(dialog);
            modalBackdrop.child(wrapper);
            final ModalEntry entry = new ModalEntry(modalBackdrop, dialog, modalAlpha, wrapper);
            entryRef[0] = entry;
            this.currentModal = entry;
        } else {
            this.currentModal = new ModalEntry(modalBackdrop, null, modalAlpha, null);
        }

        final ModalEntry current = this.currentModal;
        this.activeModal = modalBackdrop;

        modalBackdrop.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (current.isClosing) {
                return true;
            }
            if (dialog != null && !dialog.isInBoundingBox(mouseX, mouseY)) {
                popModal();
                return true;
            }
            return true;
        });
        modalBackdrop.mouseUp().subscribe((mouseX, mouseY, button) -> true);
        modalBackdrop.mouseScroll().subscribe((mouseX, mouseY, amount) -> true);

        StackLayout root = this.uiAdapter != null && this.uiAdapter.rootComponent != null
                ? this.uiAdapter.rootComponent
                : this.rootStack;
        if (root != null && !root.children().contains(modalBackdrop)) {
            root.child(modalBackdrop);
        }
    }

    public void mountModal(FlowLayout modalBackdrop, FlowLayout dialog) {
        unfocusActiveTextFields();
        StackLayout root = this.uiAdapter != null && this.uiAdapter.rootComponent != null
                ? this.uiAdapter.rootComponent
                : this.rootStack;
        if (this.currentModal != null) {
            if (root != null) {
                root.removeChild(this.currentModal.modalBackdrop);
            }
            this.currentModal = null;
            this.activeModal = null;
        }
        while (!this.modalEntryStack.isEmpty()) {
            ModalEntry under = this.modalEntryStack.pop();
            if (root != null) {
                root.removeChild(under.modalBackdrop);
            }
        }
        pushModal(modalBackdrop, dialog);
    }

    public void popModal() {
        if (this.currentModal == null || this.currentModal.isClosing) {
            return;
        }
        unfocusActiveTextFields();
        final ModalEntry closingModal = this.currentModal;
        closingModal.isClosing = true;

        closingModal.alpha.animate(CLOSE_DURATION_MS, Easing.CUBIC, 0.0f).onComplete(() -> {
            StackLayout root = this.uiAdapter != null && this.uiAdapter.rootComponent != null
                    ? this.uiAdapter.rootComponent
                    : this.rootStack;
            if (root != null) {
                root.removeChild(closingModal.modalBackdrop);
            }
            if (!this.modalEntryStack.isEmpty()) {
                this.currentModal = this.modalEntryStack.pop();
                this.activeModal = this.currentModal.modalBackdrop;
                if (root != null && !root.children().contains(this.currentModal.modalBackdrop)) {
                    root.child(this.currentModal.modalBackdrop);
                }
            } else {
                this.currentModal = null;
                this.activeModal = null;
            }
        });
    }

    public void closeModal() {
        if (this.currentModal == null || this.currentModal.isClosing) {
            return;
        }
        unfocusActiveTextFields();
        final ModalEntry closingModal = this.currentModal;
        closingModal.isClosing = true;

        closingModal.alpha.animate(CLOSE_DURATION_MS, Easing.CUBIC, 0.0f).onComplete(() -> {
            StackLayout root = this.uiAdapter != null && this.uiAdapter.rootComponent != null
                    ? this.uiAdapter.rootComponent
                    : this.rootStack;
            if (root != null) {
                root.removeChild(closingModal.modalBackdrop);
                while (!this.modalEntryStack.isEmpty()) {
                    ModalEntry under = this.modalEntryStack.pop();
                    root.removeChild(under.modalBackdrop);
                }
            } else {
                this.modalEntryStack.clear();
            }
            if (this.currentModal == closingModal) {
                this.currentModal = null;
                this.activeModal = null;
            }
        });
    }

    public void shakeDialog(FlowLayout dialog) {
        if (dialog == null) return;
        Insets rest = dialog.margins().get();
        dialog.margins(rest.withLeft(rest.left() - 4));
        dialog.margins().animate(50, Easing.CUBIC, rest.withLeft(rest.left() + 4)).forwards();
        dialog.margins().animate(100, Easing.CUBIC, rest).forwards();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isClosing || (this.currentModal != null && this.currentModal.isClosing)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isClosing || (this.currentModal != null && this.currentModal.isClosing)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isClosing || (this.currentModal != null && this.currentModal.isClosing)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isClosing || (this.currentModal != null && this.currentModal.isClosing)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (this.isClosing || (this.currentModal != null && this.currentModal.isClosing)) return;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.isClosing || (this.currentModal != null && this.currentModal.isClosing)) {
            return true;
        }
        if (this.uiAdapter != null && this.uiAdapter.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (isAnyTextBoxFocused()) {
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.isClosing || (this.currentModal != null && this.currentModal.isClosing)) {
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE && isModalOpen()) {
            popModal();
            return true;
        }

        if (this.uiAdapter != null && this.uiAdapter.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (isAnyTextBoxFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.onClose();
                return true;
            }
            return true;
        }

        if (isCloseKeybind(keyCode, scanCode, modifiers)) {
            this.onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** Hook to specify additional keybinds (e.g. shop hotkeys) that close this screen. */
    protected boolean isCloseKeybind(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    @Override
    public void onClose() {
        if (this.isClosing) {
            return;
        }
        unfocusActiveTextFields();
        this.isClosing = true;
        this.windowAlpha.animate(CLOSE_DURATION_MS, Easing.CUBIC, 0.0f).onComplete(super::onClose);
    }

    public static class ModalBackdrop extends FlowLayout {
        public ModalBackdrop() {
            super(Sizing.fill(100), Sizing.fill(100), Algorithm.VERTICAL);
            this.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
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
    }

    public static class AnimatedDialogWrapper extends FlowLayout {
        private final AnimatableProperty<Float> alpha;
        private final java.util.function.BooleanSupplier isClosingSupplier;

        public AnimatedDialogWrapper(AnimatableProperty<Float> alpha, java.util.function.BooleanSupplier isClosingSupplier) {
            super(Sizing.content(), Sizing.content(), Algorithm.VERTICAL);
            this.alpha = alpha;
            this.isClosingSupplier = isClosingSupplier;
            this.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        }

        @Override
        public boolean canFocus(FocusSource source) {
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
            if (this.focusHandler == null) {
                this.focusHandler = new io.wispforest.owo.ui.util.FocusHandler(this);
            }
            return this.focusHandler;
        }

        @Override
        public boolean onMouseDown(double mouseX, double mouseY, int button) {
            if (this.isClosingSupplier != null && this.isClosingSupplier.getAsBoolean()) {
                return true;
            }
            return super.onMouseDown(mouseX, mouseY, button);
        }

        @Override
        public boolean onMouseUp(double mouseX, double mouseY, int button) {
            if (this.isClosingSupplier != null && this.isClosingSupplier.getAsBoolean()) {
                return true;
            }
            return super.onMouseUp(mouseX, mouseY, button);
        }

        @Override
        public boolean onMouseScroll(double mouseX, double mouseY, double amount) {
            if (this.isClosingSupplier != null && this.isClosingSupplier.getAsBoolean()) {
                return true;
            }
            return super.onMouseScroll(mouseX, mouseY, amount);
        }

        @Override
        public boolean onKeyPress(int keyCode, int scanCode, int modifiers) {
            if (this.isClosingSupplier != null && this.isClosingSupplier.getAsBoolean()) {
                return true;
            }
            return super.onKeyPress(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean onCharTyped(char chr, int modifiers) {
            if (this.isClosingSupplier != null && this.isClosingSupplier.getAsBoolean()) {
                return true;
            }
            return super.onCharTyped(chr, modifiers);
        }

        @Override
        public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
            float a = Math.max(0.0f, Math.min(1.0f, this.alpha.get()));
            if (a < 0.001f) {
                return;
            }
            float[] shaderColor = RenderSystem.getShaderColor();
            float prevR = shaderColor[0], prevG = shaderColor[1], prevB = shaderColor[2], prevA = shaderColor[3];
            float effectiveAlpha = a * prevA;
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, effectiveAlpha);
            context.setColor(1.0f, 1.0f, 1.0f, effectiveAlpha);
            super.draw(context, mouseX, mouseY, partialTicks, delta);
            context.flush();
            context.setColor(prevR, prevG, prevB, prevA);
            RenderSystem.setShaderColor(prevR, prevG, prevB, prevA);
        }
    }

    public static ModalBackdrop createModalBackdrop() {
        return new ModalBackdrop();
    }
}
