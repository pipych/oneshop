package com.oneshop.client.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.oneshop.shop.LotIcon;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.StackLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.Easing;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.OwoUIDrawContext;
import io.wispforest.owo.ui.core.ParentComponent;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import io.wispforest.owo.ui.util.ScissorStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * In-game Icon Picker modal offering "Items" and "Icons" (textures) tabs
 * with dynamic case-insensitive search, pagination, full tooltips, and Bedrock UI styling.
 */
public class IconPickerModal extends BaseOwoScreen<StackLayout> {
    public enum Tab {
        ITEMS,
        TEXTURES
    }

    private static final int COLUMNS = 12;
    private static final int ROWS = 7;
    private static final int PAGE_SIZE = COLUMNS * ROWS; // 84 items/textures per page

    private record CachedItemEntry(ItemStack stack, String lowerName, String lowerId) {}
    private record CachedTextureEntry(ResourceLocation texture, String lowerFull, String lowerPath) {}

    private static List<ItemStack> CACHED_ITEMS = null;
    private static List<CachedItemEntry> CACHED_ITEM_ENTRIES = null;
    private static List<ResourceLocation> CACHED_TEXTURES = null;
    private static List<CachedTextureEntry> CACHED_TEXTURE_ENTRIES = null;

    private final Screen parentScreen;
    private final Consumer<LotIcon> onSelect;
    private final Runnable onCancel;
    private final AnimatableProperty<Float> windowAlpha = AnimatableProperty.of(0.0f);
    private boolean isClosing = false;

    public IconPickerModal(Screen parentScreen, Consumer<LotIcon> onSelect) {
        this(parentScreen, onSelect, null);
    }

    public IconPickerModal(Screen parentScreen, Consumer<LotIcon> onSelect, Runnable onCancel) {
        this.parentScreen = parentScreen;
        this.onSelect = onSelect;
        this.onCancel = onCancel;
    }

    private static List<CachedItemEntry> getCachedItemEntries() {
        if (CACHED_ITEM_ENTRIES == null) {
            List<CachedItemEntry> entries = new ArrayList<>();
            List<ItemStack> items = new ArrayList<>();
            for (Item item : BuiltInRegistries.ITEM) {
                if (item == Items.AIR) continue;
                ItemStack stack = new ItemStack(item);
                if (!stack.isEmpty()) {
                    items.add(stack);
                    String lowerName = stack.getHoverName().getString().toLowerCase(Locale.ROOT);
                    String lowerId = BuiltInRegistries.ITEM.getKey(item).toString().toLowerCase(Locale.ROOT);
                    entries.add(new CachedItemEntry(stack, lowerName, lowerId));
                }
            }
            CACHED_ITEMS = items;
            CACHED_ITEM_ENTRIES = entries;
        }
        return CACHED_ITEM_ENTRIES;
    }

    public static List<ItemStack> getCachedItems() {
        if (CACHED_ITEMS == null) {
            getCachedItemEntries();
        }
        return CACHED_ITEMS;
    }

    private static List<CachedTextureEntry> getCachedTextureEntries() {
        if (CACHED_TEXTURE_ENTRIES == null) {
            Minecraft mc = Minecraft.getInstance();
            List<String> prefixes = List.of("textures/item", "textures/gui", "textures/mob_effect");
            java.util.Map<ResourceLocation, Resource> combined = new java.util.HashMap<>();
            for (String prefix : prefixes) {
                Map<ResourceLocation, Resource> map = mc.getResourceManager().listResources(prefix, rl -> {
                    String path = rl.getPath();
                    return path.endsWith(".png") && !path.contains("/title/") && !path.contains("/container/");
                });
                combined.putAll(map);
            }
            List<ResourceLocation> list = new ArrayList<>(combined.keySet());
            list.sort(Comparator.comparing(ResourceLocation::toString));
            List<CachedTextureEntry> entries = new ArrayList<>(list.size());
            for (ResourceLocation rl : list) {
                entries.add(new CachedTextureEntry(rl, rl.toString().toLowerCase(Locale.ROOT), rl.getPath().toLowerCase(Locale.ROOT)));
            }
            CACHED_TEXTURES = list;
            CACHED_TEXTURE_ENTRIES = entries;
        }
        return CACHED_TEXTURE_ENTRIES;
    }

    public static List<ResourceLocation> getCachedTextures() {
        if (CACHED_TEXTURES == null) {
            getCachedTextureEntries();
        }
        return CACHED_TEXTURES;
    }

    public static void clearCaches() {
        CACHED_ITEMS = null;
        CACHED_ITEM_ENTRIES = null;
        CACHED_TEXTURES = null;
        CACHED_TEXTURE_ENTRIES = null;
    }

    @Override
    protected @NotNull OwoUIAdapter<StackLayout> createAdapter() {
        return OwoUIAdapter.create(this, Containers::stack);
    }

    @Override
    protected void build(StackLayout root) {
        this.isClosing = false;
        this.windowAlpha.set(0.0f);
        this.windowAlpha.animate(210, Easing.CUBIC, 1.0f);

        FlowLayout overlay = createOverlay(
                picked -> {
                    if (onSelect != null) onSelect.accept(picked);
                    closePicker();
                },
                this::closePicker
        );
        root.child(overlay);
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        float alpha = Math.max(0.0f, Math.min(1.0f, this.windowAlpha.get()));
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, alpha);
        context.setColor(1.0f, 1.0f, 1.0f, alpha);
        super.render(context, mouseX, mouseY, delta);
        context.flush();
        context.setColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    public io.wispforest.owo.ui.util.FocusHandler getFocusHandler() {
        if (this.uiAdapter == null || this.uiAdapter.rootComponent == null) {
            return null;
        }
        return this.uiAdapter.rootComponent.focusHandler();
    }

    private void unfocusActiveFields() {
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
                } else if (focused instanceof net.minecraft.client.gui.components.EditBox e) {
                    e.setFocused(false);
                }
                focusHandler.focus(null, io.wispforest.owo.ui.core.Component.FocusSource.MOUSE_CLICK);
            }
        } catch (Throwable ignored) {}
        if (this.uiAdapter.rootComponent != null) {
            try {
                var descendants = new java.util.ArrayList<io.wispforest.owo.ui.core.Component>();
                this.uiAdapter.rootComponent.collectDescendants(descendants);
                for (var c : descendants) {
                    if (c instanceof FocusableWidgetComponent f) {
                        f.onFocusLost();
                        if (f.getInnerWidget() != null) {
                            f.getInnerWidget().setFocused(false);
                        }
                    } else if (c instanceof net.minecraft.client.gui.components.EditBox e) {
                        e.setFocused(false);
                    }
                }
            } catch (Throwable ignored) {}
        }
    }

    private void closePicker() {
        if (this.isClosing) {
            return;
        }
        unfocusActiveFields();
        this.isClosing = true;
        this.windowAlpha.animate(170, Easing.CUBIC, 0.0f).onComplete(() -> {
            if (this.onCancel != null) {
                this.onCancel.run();
            } else if (this.minecraft != null) {
                this.minecraft.setScreen(parentScreen);
            }
        });
    }

    @Override
    public void onClose() {
        closePicker();
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (this.isClosing) return;
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.isClosing) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (this.isClosing) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.isClosing) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isClosing) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.isClosing) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.isClosing) return true;
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closePicker();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Creates the modal overlay container to mount onto a parent BaseOwoScreen root stack.
     *
     * @param onSelect Callback invoked when user selects an icon
     * @param onClose  Callback invoked when user cancels or closes the picker
     * @return The full-screen darkened backdrop FlowLayout containing the picker dialog
     */
    public static FlowLayout createOverlay(Consumer<LotIcon> onSelect, Runnable onClose) {
        final Tab[] activeTab = {Tab.ITEMS};
        final int[] currentPage = {0};
        final String[] searchQuery = {""};

        FlowLayout modalBackdrop = OneModUi.createModalBackdrop();
        // Dialog container (Bedrock Panel)
        FlowLayout dialog = Containers.verticalFlow(Sizing.fixed(320), Sizing.content());
        dialog.surface(BedrockTheme.PANEL);
        dialog.padding(Insets.of(8));
        dialog.gap(6);
        dialog.zIndex(601);

        // Header: Tabs on left, Search box and Close button on right
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
        header.verticalAlignment(VerticalAlignment.CENTER);
        header.gap(4);

        HoverableFlowLayout itemsTab = new HoverableFlowLayout(Sizing.content(), Sizing.fixed(20), FlowLayout.Algorithm.HORIZONTAL);
        itemsTab.padding(Insets.of(2, 2, 8, 8));
        itemsTab.verticalAlignment(VerticalAlignment.CENTER);
        itemsTab.cursorStyle(CursorStyle.HAND);
        LabelComponent itemsLabel = Components.label(Component.translatable("oneshop.gui.icon_picker.tab_items"));
        itemsLabel.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
        itemsLabel.shadow(true);
        itemsTab.child(itemsLabel);

        HoverableFlowLayout iconsTab = new HoverableFlowLayout(Sizing.content(), Sizing.fixed(20), FlowLayout.Algorithm.HORIZONTAL);
        iconsTab.padding(Insets.of(2, 2, 8, 8));
        iconsTab.verticalAlignment(VerticalAlignment.CENTER);
        iconsTab.cursorStyle(CursorStyle.HAND);
        LabelComponent iconsLabel = Components.label(Component.translatable("oneshop.gui.icon_picker.tab_icons"));
        iconsLabel.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
        iconsLabel.shadow(true);
        iconsTab.child(iconsLabel);

        Runnable updateTabSurfaces = () -> {
            itemsTab.surface(activeTab[0] == Tab.ITEMS ? BedrockSurfaces.bedrockGreenButton() : BedrockSurfaces.bedrockGrayButton());
            iconsTab.surface(activeTab[0] == Tab.TEXTURES ? BedrockSurfaces.bedrockGreenButton() : BedrockSurfaces.bedrockGrayButton());
        };
        updateTabSurfaces.run();

        header.child(itemsTab);
        header.child(iconsTab);

        // Spacer pushing search and close to right
        header.child(Containers.horizontalFlow(Sizing.expand(100), Sizing.content()));

        // Search box with Unicode 🔍 icon inside Bedrock input frame
        HoverableFlowLayout searchFrame = new HoverableFlowLayout(Sizing.fixed(120), Sizing.fixed(20), FlowLayout.Algorithm.HORIZONTAL);
        searchFrame.padding(Insets.of(0, 0, 4, 4));
        searchFrame.verticalAlignment(VerticalAlignment.CENTER);

        LabelComponent searchIcon = Components.label(Component.literal("🔍"));
        searchIcon.margins(Insets.of(0, 0, 2, 2));
        searchIcon.color(Color.ofRgb(BedrockTheme.TEXT_MUTED));
        searchFrame.child(searchIcon);

        BedrockTextBoxComponent searchBox = new BedrockTextBoxComponent(Sizing.fill(100), "");
        searchBox.setHint(Component.translatable("oneshop.gui.icon_picker.search_hint"));
        searchBox.setBordered(false);
        searchBox.setCanLoseFocus(true);

        FocusableWidgetComponent wrappedSearch = new FocusableWidgetComponent(searchBox);
        wrappedSearch.sizing(Sizing.expand(100), Sizing.fill(100));
        wrappedSearch.verticalAlignment(VerticalAlignment.CENTER);

        Surface searchBaseSurface = (context, comp) -> {
            int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
            BedrockTheme.drawBeveledBox(context, x, y, w, h, BedrockTheme.BG_INPUT, BedrockTheme.BORDER_DARK, BedrockTheme.BORDER_LIGHT);
        };
        searchFrame.surface(BedrockTheme.withHoverOutline(searchBaseSurface, () -> searchFrame.isHovered() || wrappedSearch.isWidgetFocused(), 0, BedrockTheme.OUTLINE_HOVER));

        searchFrame.mouseDown().subscribe((mx, my, btn) -> {
            wrappedSearch.focus();
            return false;
        });
        wrappedSearch.mouseDown().subscribe((mx, my, btn) -> {
            wrappedSearch.focus();
            return false;
        });

        searchFrame.child(wrappedSearch);
        header.child(searchFrame);

        // Close button
        BedrockButton closeBtn = new BedrockButton(Component.literal("✕"), Sizing.fixed(18), Sizing.fixed(18), onClose::run);
        header.child(closeBtn);

        dialog.child(header);

        // Grid container (Inset background, centered)
        FlowLayout gridArea = Containers.verticalFlow(Sizing.fill(100), Sizing.fixed(144));
        gridArea.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        gridArea.padding(Insets.of(2));
        gridArea.surface(BedrockTheme.INSET);
        gridArea.allowOverflow(false);
        gridArea.gap(2);
        dialog.child(gridArea);

        ItemGridPanel itemGridPanel = new ItemGridPanel(Sizing.fill(100), Sizing.fill(100), FlowLayout.Algorithm.VERTICAL);
        itemGridPanel.surface(Surface.BLANK).margins(Insets.none());
        itemGridPanel.clipChildren(true);
        itemGridPanel.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        itemGridPanel.gap(2);
        gridArea.child(itemGridPanel);

        // Footer: Pagination [◀] Page X / Y (Total: N) [▶] and [Cancel]
        FlowLayout footer = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        footer.verticalAlignment(VerticalAlignment.CENTER);
        footer.gap(4);

        LabelComponent pageLabel = Components.label(Component.literal(""));
        pageLabel.color(Color.ofRgb(BedrockTheme.TEXT_MUTED));

        final BedrockButton[] prevBtnHolder = new BedrockButton[1];
        final BedrockButton[] nextBtnHolder = new BedrockButton[1];

        // Grid refresh logic
        Runnable refreshGrid = () -> {
            itemGridPanel.clearChildren();
            String query = searchQuery[0];

            if (activeTab[0] == Tab.ITEMS) {
                List<CachedItemEntry> allEntries = getCachedItemEntries();
                List<CachedItemEntry> filtered = allEntries.stream().filter(entry -> {
                    if (query.isEmpty()) return true;
                    return entry.lowerName().contains(query) || entry.lowerId().contains(query);
                }).toList();

                int totalPages = Math.max(1, (int) Math.ceil((double) filtered.size() / PAGE_SIZE));
                if (currentPage[0] >= totalPages) currentPage[0] = totalPages - 1;
                if (currentPage[0] < 0) currentPage[0] = 0;

                pageLabel.text(Component.translatable("oneshop.gui.icon_picker.page", currentPage[0] + 1, totalPages, filtered.size()));
                if (prevBtnHolder[0] != null) prevBtnHolder[0].active(currentPage[0] > 0);
                if (nextBtnHolder[0] != null) nextBtnHolder[0].active(currentPage[0] < totalPages - 1);

                if (filtered.isEmpty()) {
                    itemGridPanel.child(Components.label(Component.translatable("oneshop.gui.icon_picker.no_results")).color(Color.ofRgb(BedrockTheme.TEXT_MUTED)));
                    return;
                }

                int start = currentPage[0] * PAGE_SIZE;
                int end = Math.min(start + PAGE_SIZE, filtered.size());
                List<CachedItemEntry> pageItems = filtered.subList(start, end);

                FlowLayout currentRow = null;
                for (int i = 0; i < pageItems.size(); i++) {
                    if (i % COLUMNS == 0) {
                        currentRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
                        currentRow.gap(2);
                        itemGridPanel.child(currentRow);
                    }
                    ItemStack stack = pageItems.get(i).stack();
                    GridItemCellWidget cell = new GridItemCellWidget(stack, clickedStack -> {
                        onSelect.accept(LotIcon.of(clickedStack));
                    });
                    if (currentRow != null) {
                        currentRow.child(cell);
                    }
                }
            } else {
                List<CachedTextureEntry> allEntries = getCachedTextureEntries();
                List<CachedTextureEntry> filtered = allEntries.stream().filter(entry -> {
                    if (query.isEmpty()) return true;
                    return entry.lowerFull().contains(query) || entry.lowerPath().contains(query);
                }).toList();

                int totalPages = Math.max(1, (int) Math.ceil((double) filtered.size() / PAGE_SIZE));
                if (currentPage[0] >= totalPages) currentPage[0] = totalPages - 1;
                if (currentPage[0] < 0) currentPage[0] = 0;

                pageLabel.text(Component.translatable("oneshop.gui.icon_picker.page", currentPage[0] + 1, totalPages, filtered.size()));
                if (prevBtnHolder[0] != null) prevBtnHolder[0].active(currentPage[0] > 0);
                if (nextBtnHolder[0] != null) nextBtnHolder[0].active(currentPage[0] < totalPages - 1);

                if (filtered.isEmpty()) {
                    itemGridPanel.child(Components.label(Component.translatable("oneshop.gui.icon_picker.no_results")).color(Color.ofRgb(BedrockTheme.TEXT_MUTED)));
                    return;
                }

                int start = currentPage[0] * PAGE_SIZE;
                int end = Math.min(start + PAGE_SIZE, filtered.size());
                List<CachedTextureEntry> pageTextures = filtered.subList(start, end);

                FlowLayout currentRow = null;
                for (int i = 0; i < pageTextures.size(); i++) {
                    if (i % COLUMNS == 0) {
                        currentRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
                        currentRow.gap(2);
                        itemGridPanel.child(currentRow);
                    }
                    ResourceLocation rl = pageTextures.get(i).texture();
                    GridTextureCellWidget cell = new GridTextureCellWidget(rl, clickedRl -> {
                        onSelect.accept(LotIcon.of(clickedRl));
                    });
                    if (currentRow != null) {
                        currentRow.child(cell);
                    }
                }
            }
        };

        // Tab click listeners
        itemsTab.mouseDown().subscribe((mx, my, btn) -> {
            if (activeTab[0] != Tab.ITEMS) {
                activeTab[0] = Tab.ITEMS;
                currentPage[0] = 0;
                updateTabSurfaces.run();
                refreshGrid.run();
            }
            return true;
        });

        iconsTab.mouseDown().subscribe((mx, my, btn) -> {
            if (activeTab[0] != Tab.TEXTURES) {
                activeTab[0] = Tab.TEXTURES;
                currentPage[0] = 0;
                updateTabSurfaces.run();
                refreshGrid.run();
            }
            return true;
        });

        // Search onChanged
        searchBox.onChanged().subscribe(query -> {
            searchQuery[0] = query.trim().toLowerCase(Locale.ROOT);
            currentPage[0] = 0;
            refreshGrid.run();
        });

        // Footer buttons
        BedrockButton prevBtn = new BedrockButton(Component.literal("◀"), Sizing.fixed(24), Sizing.fixed(18), () -> {
            if (currentPage[0] > 0) {
                currentPage[0]--;
                refreshGrid.run();
            }
        });
        prevBtnHolder[0] = prevBtn;
        footer.child(prevBtn);

        footer.child(pageLabel);

        BedrockButton nextBtn = new BedrockButton(Component.literal("▶"), Sizing.fixed(24), Sizing.fixed(18), () -> {
            currentPage[0]++;
            refreshGrid.run();
        });
        nextBtnHolder[0] = nextBtn;
        footer.child(nextBtn);

        footer.child(Containers.horizontalFlow(Sizing.expand(100), Sizing.content()));

        BedrockButton cancelBtn = new BedrockButton(Component.translatable("oneshop.gui.button.cancel"), Sizing.content(8), Sizing.fixed(18), onClose::run);
        footer.child(cancelBtn);

        dialog.child(footer);

        modalBackdrop.child(dialog);

        // Initial render
        refreshGrid.run();

        return modalBackdrop;
    }

    public static class ItemGridPanel extends FlowLayout {
        private boolean clipChildren = true;

        public ItemGridPanel(Sizing horizontalSizing, Sizing verticalSizing, Algorithm algorithm) {
            super(horizontalSizing, verticalSizing, algorithm);
            this.allowOverflow(false);
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

        public ItemGridPanel clipChildren(boolean clip) {
            this.clipChildren = clip;
            this.allowOverflow(!clip);
            return this;
        }

        public boolean clipChildren() {
            return this.clipChildren;
        }

        @Override
        public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
            if (this.clipChildren) {
                var p = this.padding.get();
                ScissorStack.push(this.x + p.left(), this.y + p.top(), Math.max(0, this.width - p.horizontal()), Math.max(0, this.height - p.vertical()), context.pose());
            }
            try {
                super.draw(context, mouseX, mouseY, partialTicks, delta);
                context.flush();
            } finally {
                if (this.clipChildren) {
                    ScissorStack.pop();
                }
            }
        }
    }

    public static class GridItemCellWidget extends HoverableFlowLayout {
        private final ItemStack stack;
        private boolean tooltipInitialized = false;

        public GridItemCellWidget(ItemStack stack, Consumer<ItemStack> onClick) {
            super(Sizing.fixed(18), Sizing.fixed(18), Algorithm.VERTICAL);
            this.stack = stack;
            this.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            this.cursorStyle(CursorStyle.HAND);

            Surface cellBase = (ctx, comp) -> {
                int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
                int bg = this.isHovered() ? BedrockTheme.BG_HOVER : BedrockTheme.BG_CARD;
                BedrockTheme.drawBeveledBox(ctx, x, y, w, h, bg, BedrockTheme.BORDER_LIGHT, BedrockTheme.BORDER_DARK);
            };
            this.surface(BedrockTheme.withHoverOutline(cellBase, 0, BedrockTheme.OUTLINE_HOVER));

            this.mouseEnter().subscribe(() -> {
                if (!this.tooltipInitialized) {
                    this.tooltipInitialized = true;
                    Minecraft mc = Minecraft.getInstance();
                    Item.TooltipContext tc = mc.level != null ? Item.TooltipContext.of(mc.level) : Item.TooltipContext.EMPTY;
                    this.tooltip(this.stack.getTooltipLines(tc, mc.player, TooltipFlag.NORMAL));
                }
            });

            Minecraft mc = Minecraft.getInstance();
            this.mouseDown().subscribe((mx, my, btn) -> {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                onClick.accept(stack);
                return true;
            });
        }

        @Override
        public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
            super.draw(context, mouseX, mouseY, partialTicks, delta);

            if (this.stack == null || this.stack.isEmpty()) return;

            int itemX = this.x + (this.width - 16) / 2;
            int itemY = this.y + (this.height - 16) / 2;

            float[] shaderColor = RenderSystem.getShaderColor();
            float prevR = shaderColor[0], prevG = shaderColor[1], prevB = shaderColor[2], prevA = shaderColor[3];

            ItemDisplayComponent.renderItemWithAlpha(context, this.stack, itemX, itemY, 16.0f, prevA);
            context.renderItemDecorations(Minecraft.getInstance().font, this.stack, itemX, itemY);

            context.setColor(prevR, prevG, prevB, prevA);
            RenderSystem.setShaderColor(prevR, prevG, prevB, prevA);
        }
    }

    public static class GridTextureCellWidget extends HoverableFlowLayout {
        private final ResourceLocation texture;

        public GridTextureCellWidget(ResourceLocation texture, Consumer<ResourceLocation> onClick) {
            super(Sizing.fixed(18), Sizing.fixed(18), Algorithm.VERTICAL);
            this.texture = texture;
            this.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
            this.cursorStyle(CursorStyle.HAND);

            Surface cellBase = (ctx, comp) -> {
                int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
                int bg = this.isHovered() ? BedrockTheme.BG_HOVER : BedrockTheme.BG_CARD;
                BedrockTheme.drawBeveledBox(ctx, x, y, w, h, bg, BedrockTheme.BORDER_LIGHT, BedrockTheme.BORDER_DARK);
            };
            this.surface(BedrockTheme.withHoverOutline(cellBase, 0, BedrockTheme.OUTLINE_HOVER));
            this.tooltip(Component.literal(texture.toString()).withStyle(ChatFormatting.AQUA));

            Minecraft mc = Minecraft.getInstance();
            this.mouseDown().subscribe((mx, my, btn) -> {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                onClick.accept(texture);
                return true;
            });
        }

        @Override
        public void draw(OwoUIDrawContext context, int mouseX, int mouseY, float partialTicks, float delta) {
            super.draw(context, mouseX, mouseY, partialTicks, delta);

            if (this.texture == null) return;

            int iconSize = 16;
            int iconX = this.x + (this.width - iconSize) / 2;
            int iconY = this.y + (this.height - iconSize) / 2;

            float[] shaderColor = RenderSystem.getShaderColor();
            float prevR = shaderColor[0], prevG = shaderColor[1], prevB = shaderColor[2], prevA = shaderColor[3];

            context.pose().pushPose();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, prevA);
            context.setColor(1.0f, 1.0f, 1.0f, prevA);

            context.blit(this.texture, iconX, iconY, iconSize, iconSize, 0.0f, 0.0f, 1, 1, 1, 1);

            context.setColor(prevR, prevG, prevB, prevA);
            RenderSystem.setShaderColor(prevR, prevG, prevB, prevA);
            context.pose().popPose();
        }
    }
}
