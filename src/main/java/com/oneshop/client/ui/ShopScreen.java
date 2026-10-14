package com.oneshop.client.ui;

import com.oneshop.OneShop;
import com.oneshop.client.ClientBankCache;
import com.oneshop.client.ClientShopCache;
import com.oneshop.client.key.ModKeyBindings;
import com.oneshop.item.ModItems;
import com.oneshop.item.SprotaItem;
import com.oneshop.network.C2SAdminUpdateCategoryPayload;
import com.oneshop.network.C2SAdminUpdateEntryPayload;
import com.oneshop.network.C2SDepositCashPayload;
import com.oneshop.network.C2SExecuteTransactionPayload;
import com.oneshop.network.C2SWithdrawCashPayload;
import com.oneshop.service.ShopTransactionService;
import com.oneshop.shop.LotIcon;
import com.oneshop.shop.ShopCategory;
import com.oneshop.shop.ShopEntry;
import io.wispforest.owo.ui.base.BaseOwoScreen;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.ItemComponent;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.SmallCheckboxComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
import io.wispforest.owo.ui.component.TextureComponent;
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
import io.wispforest.owo.ui.core.Positioning;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Bedrock Dark UI Shop Screen using owo-lib Java DSL.
 * Features dark slate anthracite panels, crisp 1px beveled pixel borders,
 * live balance display with 8x8 currency icon, quick deposit/withdraw modals,
 * category tabs with subtle green indicator dot, quantity selector transaction modals,
 * focusable text inputs with keyboard event propagation, hover outlines,
 * detached high-z-index overlay layering, and full localization.
 */
public class ShopScreen extends OneModUi {
    private static final ResourceLocation CURRENCY_ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "textures/gui/currency_icon.png");
    private static final ResourceLocation LOGO_SHOP_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "textures/gui/oneshop_title.png");
    private static final ResourceLocation LOGO_EDITOR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "textures/gui/oneshop_editor_title.png");

    private final boolean isAdmin;
    private String selectedCategoryId = "";
    private String searchQuery = "";

    private FlowLayout sidebarCategories;
    /** Per-category selection highlight (0..1); kept here because tabs are rebuilt on each refresh. */
    private final java.util.Map<String, AnimatedFloat> tabSelection = new java.util.HashMap<>();
    /** Per-category active indicator alpha (0.0f..1.0f). */
    private final java.util.Map<String, AnimatableProperty<Float>> categoryIndicatorAlpha = new java.util.HashMap<>();

    private FlowLayout itemGridContainer;
    private LabelComponent balanceLabel;
    private double lastDisplayedBalance = Double.NaN;

    private TextBoxComponent searchBox;
    private FocusableWidgetComponent wrappedSearch;
    private int lastGridWidth = 0;

    public ShopScreen(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public ShopScreen() {
        this(false);
    }

    public static ModalBackdrop createModalBackdrop() {
        return OneModUi.createModalBackdrop();
    }

    @Override
    protected void onFrameUpdate(int mouseX, int mouseY, float delta) {
        if (this.itemGridContainer != null && this.itemGridContainer.width() > 0 && this.lastGridWidth != this.itemGridContainer.width()) {
            this.lastGridWidth = this.itemGridContainer.width();
            refreshItemGrid();
        }
    }

    @Override
    protected void onUnfocusExtraFields() {
        if (this.searchBox != null) {
            this.searchBox.setFocused(false);
        }
        if (this.wrappedSearch != null) {
            this.wrappedSearch.onFocusLost();
            if (this.wrappedSearch.getInnerWidget() != null) {
                this.wrappedSearch.getInnerWidget().setFocused(false);
            }
        }
    }

    @Override
    protected boolean isExtraTextBoxFocused() {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return true;
        }
        if (this.wrappedSearch != null && this.wrappedSearch.isWidgetFocused()) {
            return true;
        }
        return false;
    }

    @Override
    protected void build(StackLayout root) {
        FlowLayout mainFrame = setupRootWindow(root, 92, 92);

        // Header
        mainFrame.child(buildHeader());

        // Body (Left Sidebar & Center Content Area)
        FlowLayout body = Containers.horizontalFlow(Sizing.fill(100), Sizing.expand(100));
        body.margins(Insets.top(4));

        // Left Sidebar: Categories list
        FlowLayout sidebar = Containers.verticalFlow(Sizing.fixed(120), Sizing.fill(100));
        sidebar.surface(BedrockTheme.INSET);
        sidebar.padding(Insets.of(2));
        this.sidebarCategories = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.sidebarCategories.gap(2);
        sidebar.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), this.sidebarCategories));
        body.child(sidebar);

        // Center / Right Grid: Item cards
        FlowLayout contentArea = Containers.verticalFlow(Sizing.expand(100), Sizing.fill(100));
        contentArea.margins(Insets.left(4));
        contentArea.surface(BedrockTheme.INSET);
        contentArea.padding(Insets.of(4));
        this.itemGridContainer = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        this.itemGridContainer.gap(4);
        contentArea.child(Containers.verticalScroll(Sizing.fill(100), Sizing.fill(100), this.itemGridContainer));
        body.child(contentArea);

        mainFrame.child(body);

        this.activeModal = null;

        // Initial Data Refresh
        refreshCategories();
    }

    private FlowLayout buildHeader() {
        FlowLayout header = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(26));
        header.verticalAlignment(VerticalAlignment.CENTER);

        // Logo
        ResourceLocation logoTexture = isAdmin ? LOGO_EDITOR_TEXTURE : LOGO_SHOP_TEXTURE;
        int texWidth = 1024;
        int texHeight = isAdmin ? 322 : 197;
        int logoWidth = isAdmin ? 70 : 94;
        int logoHeight = isAdmin ? 22 : 18;

        var logoComponent = new TextureDisplayComponent(logoTexture, logoWidth, logoHeight, 0f, 0f, texWidth, texHeight, texWidth, texHeight);
        logoComponent.tooltip(Component.translatable(isAdmin ? "oneshop.gui.title_admin" : "oneshop.gui.title"));
        header.child(logoComponent);

        // Spacer
        header.child(Containers.horizontalFlow(Sizing.fixed(10), Sizing.content()));

        // Balance display with 8x8 currency icon and text $PR
        FlowLayout balanceBox = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        balanceBox.surface(BedrockTheme.CARD);
        balanceBox.padding(Insets.of(3, 3, 5, 5));
        balanceBox.verticalAlignment(VerticalAlignment.CENTER);
        balanceBox.gap(3);

        var currencyIcon = new TextureDisplayComponent(CURRENCY_ICON_TEXTURE, 8, 8, 0f, 0f, 8, 8, 8, 8);
        balanceBox.child(currencyIcon);

        this.balanceLabel = Components.label(getBalanceComponent());
        this.balanceLabel.color(Color.ofRgb(BedrockTheme.TEXT_GREEN));
        balanceBox.child(this.balanceLabel);
        header.child(balanceBox);

        // Spacer
        header.child(Containers.horizontalFlow(Sizing.fixed(6), Sizing.content()));

        // Deposit button
        BedrockButton depositBtn = new BedrockButton(Component.translatable("oneshop.gui.deposit"), Sizing.content(), Sizing.fixed(18), () -> {
            if (this.activeModal == null) {
                openDepositModal();
            }
        });
        depositBtn.hoverCondition(() -> !isModalOpen());
        header.child(depositBtn);

        header.child(Containers.horizontalFlow(Sizing.fixed(3), Sizing.content()));

        // Withdraw button
        BedrockButton withdrawBtn = new BedrockButton(Component.translatable("oneshop.gui.withdraw"), Sizing.content(), Sizing.fixed(18), () -> {
            if (this.activeModal == null) {
                openWithdrawModal();
            }
        });
        withdrawBtn.hoverCondition(() -> !isModalOpen());
        header.child(withdrawBtn);

        header.child(Containers.horizontalFlow(Sizing.fixed(3), Sizing.content()));

        // Transfer button
        BedrockButton transferBtn = new BedrockButton(Component.translatable("oneshop.gui.transfer"), Sizing.content(), Sizing.fixed(18), () -> {
            if (this.activeModal == null) {
                openTransferModal();
            }
        });
        transferBtn.hoverCondition(() -> !isModalOpen());
        header.child(transferBtn);

        // Expandable spacer pushing search and close buttons to right
        header.child(Containers.horizontalFlow(Sizing.expand(100), Sizing.content()));

        // Search text field inside Bedrock inset frame with focus support, Unicode magnifying glass icon and hover outline
        HoverableFlowLayout searchContainer = new HoverableFlowLayout(Sizing.fixed(115), Sizing.fixed(20), FlowLayout.Algorithm.HORIZONTAL);
        searchContainer.hoverCondition(() -> !isModalOpen());
        searchContainer.padding(Insets.of(0, 0, 4, 4));
        searchContainer.verticalAlignment(VerticalAlignment.CENTER);

        // Left Element: Centered magnifying glass label with horizontal margins (0, 0, 4, 4)
        LabelComponent searchIcon = Components.label(Component.literal("🔍"));
        searchIcon.margins(Insets.of(0, 0, 4, 4));
        searchIcon.color(Color.ofRgb(BedrockTheme.TEXT_MUTED));

        this.searchBox = new BedrockTextBoxComponent(Sizing.fill(100), searchQuery);
        this.searchBox.setHint(Component.translatable("oneshop.gui.search_hint"));
        this.searchBox.setBordered(false);
        this.searchBox.setCanLoseFocus(true);
        this.searchBox.onChanged().subscribe(query -> {
            this.searchQuery = query.toLowerCase(Locale.ROOT).trim();
            refreshItemGrid();
        });

        this.wrappedSearch = new FocusableWidgetComponent(this.searchBox);
        this.wrappedSearch.sizing(Sizing.expand(100), Sizing.fill(100));
        this.wrappedSearch.verticalAlignment(VerticalAlignment.CENTER);

        Surface searchBaseSurface = (context, comp) -> {
            int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
            BedrockTheme.drawBeveledBox(context, x, y, w, h, BedrockTheme.BG_INPUT, BedrockTheme.BORDER_DARK, BedrockTheme.BORDER_LIGHT);
        };
        searchContainer.surface(BedrockTheme.withHoverOutline(searchBaseSurface, () -> searchContainer.isHovered() || (this.wrappedSearch != null && this.wrappedSearch.isWidgetFocused()), 0, BedrockTheme.OUTLINE_HOVER));

        searchContainer.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (isModalOpen()) return false;
            this.wrappedSearch.focus();
            return false;
        });

        this.wrappedSearch.mouseDown().subscribe((mouseX, mouseY, button) -> {
            if (isModalOpen()) return false;
            this.wrappedSearch.focus();
            return false;
        });

        searchContainer.child(searchIcon);
        searchContainer.child(this.wrappedSearch);
        header.child(searchContainer);

        header.child(Containers.horizontalFlow(Sizing.fixed(4), Sizing.content()));

        // Close button
        BedrockButton closeBtn = new BedrockButton(Component.literal("✕"), Sizing.fixed(18), Sizing.fixed(18), this::onClose);
        closeBtn.hoverCondition(() -> !isModalOpen());
        header.child(closeBtn);

        return header;
    }

    public String formatBalanceAmount() {
        return String.format(Locale.ROOT, "%.2f", ClientBankCache.getBalance());
    }

    public Component getBalanceComponent() {
        return Component.translatable("oneshop.gui.balance", formatBalanceAmount(), SprotaItem.CURRENCY_SYMBOL);
    }

    public void updateBalanceDisplay() {
        if (this.balanceLabel != null) {
            double current = ClientBankCache.getBalance();
            if (Double.compare(current, this.lastDisplayedBalance) != 0) {
                this.lastDisplayedBalance = current;
                this.balanceLabel.text(getBalanceComponent());
            }
        }
    }

    public void refreshCategories() {
        if (sidebarCategories == null) return;
        sidebarCategories.clearChildren();

        // Admin: "+ New Category" button at the top of the category list
        if (isAdmin) {
            BedrockButton newCatBtn = new BedrockButton(Component.translatable("oneshop.gui.category.new"), Sizing.fill(100), Sizing.fixed(20),
                    () -> {
                        if (this.activeModal == null) {
                            openCategoryEditorModal(null);
                        }
                    });
            newCatBtn.textColor(BedrockTheme.TEXT_GREEN);
            newCatBtn.hoverCondition(() -> !isModalOpen());
            sidebarCategories.child(newCatBtn);
        }

        List<ShopCategory> categories = ClientShopCache.getCategories();
        if (categories.isEmpty()) {
            sidebarCategories.child(Components.label(Component.translatable("oneshop.gui.no_categories")).color(Color.ofRgb(BedrockTheme.TEXT_MUTED)));
            refreshItemGrid();
            return;
        }

        if (selectedCategoryId.isEmpty() || categories.stream().noneMatch(c -> c.id().equals(selectedCategoryId))) {
            selectedCategoryId = categories.get(0).id();
        }

        for (ShopCategory category : categories) {
            boolean isSelected = category.id().equals(selectedCategoryId);

            HoverableFlowLayout tabBtn = new HoverableFlowLayout(Sizing.fill(100), Sizing.fixed(24), FlowLayout.Algorithm.HORIZONTAL);
            tabBtn.hoverCondition(() -> !isModalOpen());
            tabBtn.padding(Insets.of(2, 2, 4, 4));
            tabBtn.verticalAlignment(VerticalAlignment.CENTER);
            tabBtn.cursorStyle(CursorStyle.HAND);

            // Selection highlight lerps 0..1 over 160ms. State lives in a screen-level map because tabs are rebuilt on every refresh.
            AnimatedFloat selection = this.tabSelection.computeIfAbsent(category.id(), k -> new AnimatedFloat(isSelected ? 1f : 0f));
            selection.animateTo(isSelected ? 1f : 0f, 160, Easing.CUBIC);
            tabBtn.surface(BedrockSurfaces.withHoverOutline((context, comp) ->
                    BedrockSurfaces.drawTabButton(context, comp.x(), comp.y(), comp.width(), comp.height(), selection.get())));

            // In-place category active indicator: anchored locally inside this category widget layout
            AnimatableProperty<Float> indicatorAlpha = this.categoryIndicatorAlpha.computeIfAbsent(
                    category.id(), k -> AnimatableProperty.of(isSelected ? 1.0f : 0.0f));
            tabBtn.child(new CategoryIndicator(indicatorAlpha));

            // Left content: icon and display name, taking all remaining width
            FlowLayout leftContent = Containers.horizontalFlow(Sizing.expand(100), Sizing.fill(100));
            leftContent.verticalAlignment(VerticalAlignment.CENTER);
            leftContent.gap(4);

            // Icon
            if (category.icon() != null) {
                if (category.icon().isItem() && !category.icon().item().isEmpty()) {
                    leftContent.child(new LotIconDisplayComponent(category.icon(), 16));
                } else if (category.icon().isTexture()) {
                    leftContent.child(new LotIconDisplayComponent(category.icon(), 16));
                }
            }

            // Name (Pure white with standard font shadow, bounded so text never overlaps or clips the edit button)
            LabelComponent nameLabel = Components.label(category.displayName());
            nameLabel.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
            nameLabel.shadow(true);
            nameLabel.maxWidth(isAdmin ? 68 : 88);
            leftContent.child(nameLabel);

            tabBtn.child(leftContent);

            // Admin edit icon on category tab pinned to fixed right slot with top z-layer (zIndex 10)
            BedrockButton editCatBtn = null;
            if (isAdmin) {
                editCatBtn = new BedrockButton(Component.literal("✎"), Sizing.fixed(18), Sizing.fixed(18), () -> {
                    if (this.activeModal == null) {
                        openCategoryEditorModal(category);
                    }
                });
                editCatBtn.zIndex(10);
                editCatBtn.padding(Insets.none());
                editCatBtn.textColor(BedrockTheme.TEXT_GOLD);
                editCatBtn.hoverCondition(() -> !isModalOpen());
                editCatBtn.tooltip(Component.translatableWithFallback("oneshop.gui.category.edit_tooltip", "Редактировать категорию"));
                tabBtn.child(editCatBtn);
            }

            final BedrockButton finalEditCatBtn = editCatBtn;
            // Left click selects category; right click in admin mode opens editor.
            // When clicking editCatBtn, do not intercept click so editCatBtn receives it cleanly.
            tabBtn.mouseDown().subscribe((mouseX, mouseY, button) -> {
                if (isModalOpen()) return true;
                if (finalEditCatBtn != null && finalEditCatBtn.isMouseOver(mouseX, mouseY)) {
                    return false;
                }
                if (button == 0) {
                    if (!this.selectedCategoryId.equals(category.id())) {
                        String prevId = this.selectedCategoryId;
                        this.selectedCategoryId = category.id();
                        AnimatableProperty<Float> prevAlpha = this.categoryIndicatorAlpha.get(prevId);
                        if (prevAlpha != null) {
                            prevAlpha.animate(120, Easing.SINE, 0.0f);
                        }
                        AnimatableProperty<Float> newAlpha = this.categoryIndicatorAlpha.computeIfAbsent(
                                this.selectedCategoryId, k -> AnimatableProperty.of(0.0f));
                        newAlpha.animate(160, Easing.SINE, 1.0f);
                        refreshCategories();
                    }
                    return true;
                } else if (isAdmin && button == 1) {
                    if (this.activeModal == null) {
                        openCategoryEditorModal(category);
                    }
                    return true;
                }
                return false;
            });

            sidebarCategories.child(tabBtn);
        }

        refreshItemGrid();
    }

    private void refreshItemGrid() {
        if (itemGridContainer == null) return;
        itemGridContainer.clearChildren();

        ShopCategory activeCat = ClientShopCache.getCategory(selectedCategoryId).orElse(null);

        // Admin: "+ Add Lot" button inside active category
        if (isAdmin && activeCat != null) {
            BedrockButton addLotBtn = new BedrockButton(Component.translatable("oneshop.gui.add_lot"), Sizing.fill(100), Sizing.fixed(20),
                    () -> {
                        if (this.activeModal == null) {
                            openEntryEditorModal(activeCat, null);
                        }
                    });
            addLotBtn.textColor(BedrockTheme.TEXT_GREEN);
            addLotBtn.hoverCondition(() -> !isModalOpen());
            itemGridContainer.child(addLotBtn);
        }

        if (activeCat == null || activeCat.entries().isEmpty()) {
            itemGridContainer.child(Components.label(Component.translatable("oneshop.gui.no_items")).color(Color.ofRgb(BedrockTheme.TEXT_MUTED)));
            return;
        }

        List<ShopEntry> filteredEntries = activeCat.entries().stream()
                .filter(e -> searchQuery.isEmpty() || e.matchesSearch(searchQuery))
                .toList();

        if (filteredEntries.isEmpty()) {
            itemGridContainer.child(Components.label(Component.translatable("oneshop.gui.no_matching_items")).color(Color.ofRgb(BedrockTheme.TEXT_MUTED)));
            return;
        }

        // Layout square cards in responsive rows
        int availableWidth = itemGridContainer.width() > 0
                ? itemGridContainer.width()
                : Math.max(160, (int) (this.width * 0.92) - 130);
        int cardSize = 88;
        int gap = 4;
        int columns = Math.max(1, (availableWidth + gap) / (cardSize + gap));

        FlowLayout currentRow = null;
        for (int i = 0; i < filteredEntries.size(); i++) {
            if (i % columns == 0) {
                currentRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
                currentRow.gap(gap);
                itemGridContainer.child(currentRow);
            }
            ShopEntry entry = filteredEntries.get(i);
            currentRow.child(buildItemCard(activeCat, entry));
        }
    }

    private static String formatCompactPrice(double price) {
        if (price == (long) price) {
            long l = (long) price;
            if (l >= 1_000_000) return String.format(Locale.ROOT, "%.1fM", l / 1_000_000.0);
            if (l >= 10_000) return String.format(Locale.ROOT, "%dk", l / 1000);
            return String.valueOf(l);
        }
        return String.format(Locale.ROOT, "%.1f", price);
    }

    private FlowLayout buildItemCard(ShopCategory category, ShopEntry entry) {
        int cardSize = 88;
        HoverableFlowLayout card = new HoverableFlowLayout(Sizing.fixed(cardSize), Sizing.fixed(cardSize), FlowLayout.Algorithm.VERTICAL);
        card.hoverCondition(() -> !isModalOpen());
        card.padding(Insets.of(4, 4, 3, 3));
        card.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        card.gap(2);

        Surface cardBaseSurface = (context, comp) -> {
            int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
            int bg = card.isHovered() ? BedrockTheme.BG_HOVER : BedrockTheme.BG_CARD;
            BedrockTheme.drawBeveledBox(context, x, y, w, h, bg, BedrockTheme.BORDER_LIGHT, BedrockTheme.BORDER_DARK);
        };
        card.surface(BedrockTheme.withHoverOutline(cardBaseSurface, 0, BedrockTheme.OUTLINE_HOVER));

        // Center: 32x32 scaled item component or texture component with full tooltip
        LotIcon displayIcon = entry.getVisualIcon();
        LotIconDisplayComponent iconComponent = new LotIconDisplayComponent(displayIcon, 32);

        boolean[] tooltipBuilt = {false};
        card.mouseEnter().subscribe(() -> {
            if (tooltipBuilt[0]) return;
            tooltipBuilt[0] = true;
            Minecraft mc = Minecraft.getInstance();
            Item.TooltipContext tooltipContext = mc.level != null ? Item.TooltipContext.of(mc.level) : Item.TooltipContext.EMPTY;
            List<Component> tooltipLines = new java.util.ArrayList<>();
            if (entry.hasCustomTitle()) {
                tooltipLines.add(Component.literal(entry.customTitle()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
                if (entry.item().isEmpty() && entry.hasTags()) {
                    String tagsFormatted = String.join(", ", entry.tags().stream().map(t -> t.startsWith("#") ? t : "#" + t).toList());
                    tooltipLines.add(Component.literal("§eСкупка: §f" + tagsFormatted));
                } else if (!entry.item().isEmpty()) {
                    List<Component> vanillaLines = entry.item().getTooltipLines(tooltipContext, mc.player, TooltipFlag.NORMAL);
                    if (vanillaLines.size() > 1) {
                        tooltipLines.addAll(vanillaLines.subList(1, vanillaLines.size()));
                    }
                }
            } else {
                if (!entry.item().isEmpty()) {
                    tooltipLines.addAll(entry.item().getTooltipLines(tooltipContext, mc.player, TooltipFlag.NORMAL));
                } else if (entry.hasTags()) {
                    String tagsFormatted = String.join(", ", entry.tags().stream().map(t -> t.startsWith("#") ? t : "#" + t).toList());
                    tooltipLines.add(Component.literal("§eСкупка: §f" + tagsFormatted));
                } else if (entry.icon() != null && entry.icon().isTexture()) {
                    tooltipLines.add(Component.literal(entry.icon().texture().getPath()).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE));
                } else {
                    tooltipLines.add(Component.literal(entry.id()).withStyle(ChatFormatting.BOLD, ChatFormatting.WHITE));
                }
            }
            if (entry.hasTags() && !entry.hasCustomTitle() && !entry.item().isEmpty()) {
                tooltipLines.add(Component.translatable("oneshop.gui.tooltip.accepts_tags", String.join(", ", entry.tags())).withStyle(ChatFormatting.AQUA));
            }
            card.tooltip(tooltipLines);
        });
        card.child(iconComponent);

        // Price row below the item icon
        FlowLayout priceRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        priceRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        priceRow.gap(2);

        boolean buyAllowed = entry.isBuyAllowed();
        boolean sellAllowed = entry.isSellAllowed();

        if (buyAllowed && sellAllowed) {
            LabelComponent buyPriceLabel = Components.label(Component.translatable("oneshop.gui.price.buy_compact", formatCompactPrice(entry.buyPrice())));
            buyPriceLabel.color(Color.ofRgb(BedrockTheme.TEXT_GREEN));
            priceRow.child(buyPriceLabel);
            priceRow.child(new TextureDisplayComponent(CURRENCY_ICON_TEXTURE, 8, 8, 0f, 0f, 8, 8, 8, 8));

            LabelComponent sep = Components.label(Component.literal("/"));
            sep.color(Color.ofRgb(BedrockTheme.TEXT_MUTED));
            priceRow.child(sep);

            LabelComponent sellPriceLabel = Components.label(Component.translatable("oneshop.gui.price.sell_compact", formatCompactPrice(entry.sellPrice())));
            sellPriceLabel.color(Color.ofRgb(BedrockTheme.TEXT_GOLD));
            priceRow.child(sellPriceLabel);
            priceRow.child(new TextureDisplayComponent(CURRENCY_ICON_TEXTURE, 8, 8, 0f, 0f, 8, 8, 8, 8));
        } else if (buyAllowed) {
            LabelComponent buyPriceLabel = Components.label(Component.literal(formatCompactPrice(entry.buyPrice())));
            buyPriceLabel.color(Color.ofRgb(BedrockTheme.TEXT_GREEN));
            priceRow.child(buyPriceLabel);
            priceRow.child(new TextureDisplayComponent(CURRENCY_ICON_TEXTURE, 8, 8, 0f, 0f, 8, 8, 8, 8));
        } else if (sellAllowed) {
            LabelComponent sellPriceLabel = Components.label(Component.literal(formatCompactPrice(entry.sellPrice())));
            sellPriceLabel.color(Color.ofRgb(BedrockTheme.TEXT_GOLD));
            priceRow.child(sellPriceLabel);
            priceRow.child(new TextureDisplayComponent(CURRENCY_ICON_TEXTURE, 8, 8, 0f, 0f, 8, 8, 8, 8));
        } else {
            LabelComponent noneLabel = Components.label(Component.translatable("oneshop.gui.price.sell_none"));
            noneLabel.color(Color.ofRgb(BedrockTheme.TEXT_MUTED));
            priceRow.child(noneLabel);
        }
        card.child(priceRow);

        // Bottom Button(s)
        FlowLayout btnRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        btnRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        btnRow.gap(3);

        if (buyAllowed && sellAllowed) {
            BedrockButton buyBtn = new BedrockButton(
                    Component.translatable("oneshop.gui.button.buy_compact"),
                    Sizing.content(4), Sizing.fixed(15),
                    () -> {
                        if (this.activeModal == null) {
                            openQuantitySelectorModal(category, entry, true);
                        }
                    }
            );
            buyBtn.hoverCondition(() -> !isModalOpen());
            buyBtn.tooltip(Component.translatable("oneshop.gui.button.buy"));
            if (!entry.isUnlimited() && entry.maxStock() <= 0) {
                buyBtn.active(false);
            }
            btnRow.child(buyBtn);

            BedrockButton sellBtn = new BedrockButton(
                    Component.translatable("oneshop.gui.button.sell_compact"),
                    Sizing.content(4), Sizing.fixed(15),
                    () -> {
                        if (this.activeModal == null) {
                            openQuantitySelectorModal(category, entry, false);
                        }
                    }
            );
            sellBtn.hoverCondition(() -> !isModalOpen());
            sellBtn.tooltip(Component.translatable("oneshop.gui.button.sell"));
            btnRow.child(sellBtn);
        } else if (buyAllowed) {
            BedrockButton buyBtn = new BedrockButton(
                    Component.translatable("oneshop.gui.button.buy"),
                    Sizing.content(6), Sizing.fixed(15),
                    () -> {
                        if (this.activeModal == null) {
                            openQuantitySelectorModal(category, entry, true);
                        }
                    }
            );
            buyBtn.hoverCondition(() -> !isModalOpen());
            if (!entry.isUnlimited() && entry.maxStock() <= 0) {
                buyBtn.active(false);
            }
            btnRow.child(buyBtn);
        } else if (sellAllowed) {
            BedrockButton sellBtn = new BedrockButton(
                    Component.translatable("oneshop.gui.button.sell"),
                    Sizing.content(6), Sizing.fixed(15),
                    () -> {
                        if (this.activeModal == null) {
                            openQuantitySelectorModal(category, entry, false);
                        }
                    }
            );
            sellBtn.hoverCondition(() -> !isModalOpen());
            btnRow.child(sellBtn);
        }

        if (!btnRow.children().isEmpty()) {
            card.child(btnRow);
        }

        // Admin: 20x20 Unicode pencil edit button on top right of the card
        if (isAdmin) {
            BedrockButton editEntryBtn = new BedrockButton(
                    Component.literal("✎"),
                    Sizing.fixed(20), Sizing.fixed(20),
                    () -> {
                        if (this.activeModal == null) {
                            openEntryEditorModal(category, entry);
                        }
                    }
            );
            editEntryBtn.zIndex(10);
            editEntryBtn.padding(Insets.none());
            editEntryBtn.textColor(BedrockTheme.TEXT_GOLD);
            editEntryBtn.positioning(Positioning.absolute(cardSize - 22, 2));
            editEntryBtn.hoverCondition(() -> !isModalOpen());
            editEntryBtn.tooltip(Component.translatableWithFallback("oneshop.gui.lot.edit_tooltip", "Редактировать лот"));
            card.child(editEntryBtn);

            // Right-click card in admin mode opens entry editor
            card.mouseDown().subscribe((mouseX, mouseY, button) -> {
                if (isModalOpen()) return true;
                if (editEntryBtn.isMouseOver(mouseX, mouseY)) {
                    return false;
                }
                if (button == 1) { // Right-click
                    if (this.activeModal == null) {
                        openEntryEditorModal(category, entry);
                    }
                    return true;
                }
                return false;
            });
        }

        return card;
    }

    // --- Admin Category Editor Modal ---

    private void openCategoryEditorModal(ShopCategory existingCategory) {
        final LotIcon[] selectedIcon = {
                existingCategory != null && existingCategory.icon() != null && !existingCategory.icon().isEmpty()
                        ? existingCategory.icon().copy()
                        : LotIcon.of(new ItemStack(Items.GRASS_BLOCK))
        };

        FlowLayout modalBackdrop = createModalBackdrop();
        modalBackdrop.zIndex(500);

        FlowLayout dialog = Containers.verticalFlow(Sizing.fixed(260), Sizing.content());
        dialog.surface(BedrockTheme.PANEL);
        dialog.padding(Insets.of(8));
        dialog.gap(6);
        dialog.zIndex(501);

        // Header Title
        Component titleComp = existingCategory == null
                ? Component.translatable("oneshop.gui.category.new_title").withStyle(ChatFormatting.BOLD)
                : Component.translatable("oneshop.gui.category.edit_title", existingCategory.displayName().getString()).withStyle(ChatFormatting.BOLD);
        LabelComponent title = Components.label(titleComp);
        title.color(Color.ofRgb(BedrockTheme.TEXT_GREEN));
        dialog.child(title);

        // Clickable 32x32 category icon button with withHoverOutline and tooltip "Выбрать иконку категории"
        FlowLayout iconRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(36));
        iconRow.verticalAlignment(VerticalAlignment.CENTER);
        iconRow.gap(6);

        HoverableFlowLayout iconBtn = new HoverableFlowLayout(Sizing.fixed(32), Sizing.fixed(32), FlowLayout.Algorithm.VERTICAL);
        iconBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        iconBtn.cursorStyle(CursorStyle.HAND);
        iconBtn.tooltip(Component.translatable("oneshop.gui.category.select_icon"));
        Surface iconBtnBaseSurface = (context, comp) -> {
            int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
            BedrockTheme.drawBeveledBox(context, x, y, w, h, BedrockTheme.BG_INPUT, BedrockTheme.BORDER_DARK, BedrockTheme.BORDER_LIGHT);
        };
        iconBtn.surface(BedrockTheme.withHoverOutline(iconBtnBaseSurface, 0, BedrockTheme.OUTLINE_HOVER));

        FlowLayout iconPreviewContainer = Containers.horizontalFlow(Sizing.fixed(24), Sizing.fixed(24));
        iconPreviewContainer.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        iconPreviewContainer.child(new LotIconDisplayComponent(selectedIcon[0], 24));
        iconBtn.child(iconPreviewContainer);

        Component initialIconName = selectedIcon[0].isItem() && !selectedIcon[0].item().isEmpty()
                ? selectedIcon[0].item().getHoverName()
                : (selectedIcon[0].isTexture() ? Component.literal(selectedIcon[0].texture().getPath()) : Component.literal(""));
        LabelComponent iconNameLabel = Components.label(initialIconName);
        iconNameLabel.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));

        iconBtn.mouseDown().subscribe((mouseX, mouseY, button) -> {
            openIconPickerModal(pickedIcon -> {
                selectedIcon[0] = pickedIcon;
                iconPreviewContainer.clearChildren();
                iconPreviewContainer.child(new LotIconDisplayComponent(selectedIcon[0], 24));
                if (selectedIcon[0].isItem() && !selectedIcon[0].item().isEmpty()) {
                    iconNameLabel.text(selectedIcon[0].item().getHoverName());
                } else if (selectedIcon[0].isTexture()) {
                    iconNameLabel.text(Component.literal(selectedIcon[0].texture().getPath()));
                } else {
                    iconNameLabel.text(Component.literal(""));
                }
            });
            return true;
        });

        iconRow.child(iconBtn);
        iconRow.child(iconNameLabel);
        dialog.child(iconRow);

        // Fields: ID, Display Name, Sort Order
        BedrockTextBoxComponent idBox = new BedrockTextBoxComponent(Sizing.fill(100), existingCategory != null ? existingCategory.id() : "");
        dialog.child(createLabeledInput("oneshop.gui.category.id", idBox, "category_id"));

        BedrockTextBoxComponent nameBox = new BedrockTextBoxComponent(Sizing.fill(100), existingCategory != null ? existingCategory.displayName().getString() : "");
        dialog.child(createLabeledInput("oneshop.gui.category.name", nameBox, "Display Name"));

        BedrockTextBoxComponent orderBox = new BedrockTextBoxComponent(Sizing.fill(100), existingCategory != null ? String.valueOf(existingCategory.sortOrder()) : "0");
        dialog.child(createLabeledInput("oneshop.gui.category.order", orderBox, "0"));

        LabelComponent statusLabel = Components.label(Component.literal(""));
        statusLabel.color(Color.ofRgb(BedrockTheme.TEXT_RED));
        dialog.child(statusLabel);

        // Action Buttons
        FlowLayout actionRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        actionRow.gap(4);

        BedrockButton saveBtn = new BedrockButton(Component.translatable("oneshop.gui.button.save"), Sizing.expand(existingCategory != null ? 33 : 50), Sizing.fixed(20), () -> {
            String rawId = idBox.getValue().trim().toLowerCase(Locale.ROOT);
            String catId = existingCategory != null ? existingCategory.id() : rawId;
            if (catId.isEmpty()) {
                catId = java.util.UUID.randomUUID().toString();
            }
            String catName = nameBox.getValue().trim();
            if (catName.isEmpty()) {
                statusLabel.text(Component.translatable("oneshop.gui.category.error.empty_name"));
                return;
            }
            int sortOrder = 0;
            try {
                sortOrder = Integer.parseInt(orderBox.getValue().trim());
            } catch (NumberFormatException ignored) {}

            List<ShopEntry> existingEntries = existingCategory != null ? existingCategory.entries() : List.of();
            ShopCategory newCategory = new ShopCategory(catId, Component.literal(catName), selectedIcon[0].copy(), sortOrder, existingEntries);
            PacketDistributor.sendToServer(new C2SAdminUpdateCategoryPayload(newCategory, false));
            ClientShopCache.addOrUpdateCategory(newCategory);
            this.selectedCategoryId = newCategory.id();
            refreshCategories();
            popModal();
        });
        actionRow.child(saveBtn);

        if (existingCategory != null) {
            BedrockButton deleteBtn = new BedrockButton(Component.translatable("oneshop.gui.button.delete"), Sizing.expand(33), Sizing.fixed(20), () -> {
                PacketDistributor.sendToServer(new C2SAdminUpdateCategoryPayload(existingCategory, true));
                List<ShopCategory> remaining = new java.util.ArrayList<>(ClientShopCache.getCategories());
                remaining.removeIf(c -> c.id().equals(existingCategory.id()));
                ClientShopCache.setCategories(remaining);
                if (this.selectedCategoryId.equals(existingCategory.id())) {
                    this.selectedCategoryId = !remaining.isEmpty() ? remaining.get(0).id() : "";
                }
                refreshCategories();
                popModal();
            });
            deleteBtn.textColor(BedrockTheme.TEXT_RED);
            actionRow.child(deleteBtn);
        }

        BedrockButton cancelBtn = new BedrockButton(Component.translatable("oneshop.gui.button.cancel"), Sizing.expand(existingCategory != null ? 33 : 50), Sizing.fixed(20), this::popModal);
        actionRow.child(cancelBtn);

        dialog.child(actionRow);
        modalBackdrop.child(dialog);
        mountModal(modalBackdrop, dialog);
    }

    // --- Admin Entry Editor Modal ---

    private void openEntryEditorModal(ShopCategory category, ShopEntry existingEntry) {
        final ItemStack[] itemStack = {
                existingEntry != null
                        ? existingEntry.item().copy()
                        : (minecraft != null && minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()
                        ? minecraft.player.getMainHandItem().copy()
                        : ItemStack.EMPTY)
        };
        final boolean[] hasCustomIcon = {
                existingEntry != null && existingEntry.icon() != null && !existingEntry.icon().isEmpty()
                        && (existingEntry.icon().isTexture()
                        || existingEntry.item().isEmpty()
                        || !ItemStack.isSameItemSameComponents(existingEntry.icon().item(), existingEntry.item()))
        };
        final LotIcon[] selectedIcon = {
                existingEntry != null && existingEntry.icon() != null && !existingEntry.icon().isEmpty()
                        ? existingEntry.icon().copy()
                        : (!itemStack[0].isEmpty() ? LotIcon.of(itemStack[0]) : LotIcon.of(ItemStack.EMPTY))
        };

        FlowLayout modalBackdrop = createModalBackdrop();
        modalBackdrop.zIndex(500);

        FlowLayout dialog = Containers.verticalFlow(Sizing.fixed(270), Sizing.content());
        dialog.surface(BedrockTheme.PANEL);
        dialog.padding(Insets.of(8));
        dialog.gap(5);
        dialog.zIndex(501);

        // Header Title
        String initialTitle = existingEntry == null ? "" : existingEntry.customTitle();
        Component titleComp = existingEntry == null
                ? Component.translatable("oneshop.gui.lot.add_title").withStyle(ChatFormatting.BOLD)
                : Component.translatable("oneshop.gui.lot.edit_title",
                    existingEntry.hasCustomTitle() ? existingEntry.customTitle()
                    : (!existingEntry.item().isEmpty() ? existingEntry.item().getHoverName().getString()
                    : (existingEntry.hasTags() ? "#" + existingEntry.tags().get(0) : existingEntry.id())))
                    .withStyle(ChatFormatting.BOLD);
        LabelComponent title = Components.label(titleComp);
        title.color(Color.ofRgb(existingEntry == null ? BedrockTheme.TEXT_GREEN : BedrockTheme.TEXT_GOLD));
        dialog.child(title);

        // Clickable 36x36 icon preview button with withHoverOutline and tooltip "Изменить иконку"
        FlowLayout itemRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(38));
        itemRow.verticalAlignment(VerticalAlignment.CENTER);
        itemRow.gap(6);

        HoverableFlowLayout iconBtn = new HoverableFlowLayout(Sizing.fixed(36), Sizing.fixed(36), FlowLayout.Algorithm.VERTICAL);
        iconBtn.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        iconBtn.cursorStyle(CursorStyle.HAND);
        iconBtn.tooltip(Component.translatable("oneshop.gui.lot.change_icon"));
        Surface iconBtnBaseSurface = (context, comp) -> {
            int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
            BedrockTheme.drawBeveledBox(context, x, y, w, h, BedrockTheme.BG_INPUT, BedrockTheme.BORDER_DARK, BedrockTheme.BORDER_LIGHT);
        };
        iconBtn.surface(BedrockTheme.withHoverOutline(iconBtnBaseSurface, 0, BedrockTheme.OUTLINE_HOVER));

        FlowLayout iconPreviewContainer = Containers.horizontalFlow(Sizing.fixed(32), Sizing.fixed(32));
        iconPreviewContainer.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        iconPreviewContainer.child(new LotIconDisplayComponent(selectedIcon[0], 32));
        iconBtn.child(iconPreviewContainer);

        Component initialName = !itemStack[0].isEmpty()
                ? itemStack[0].getHoverName()
                : (selectedIcon[0].isItem() && !selectedIcon[0].item().isEmpty()
                ? selectedIcon[0].item().getHoverName()
                : (selectedIcon[0].isTexture() ? Component.literal(selectedIcon[0].texture().getPath()) : Component.literal("")));
        LabelComponent itemNameLabel = Components.label(initialName);
        itemNameLabel.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));

        String initialId = existingEntry != null
                ? existingEntry.getFunctionalItemId()
                : (!itemStack[0].isEmpty()
                ? BuiltInRegistries.ITEM.getKey(itemStack[0].getItem()).toString()
                : "");
        BedrockTextBoxComponent idBox = new BedrockTextBoxComponent(Sizing.fill(100), initialId);
        final boolean[] isIdError = {false};

        BedrockToggle buyToggle = new BedrockToggle(Component.literal("Покупка"), existingEntry == null || existingEntry.allowBuy(), null);
        buyToggle.sizing(Sizing.fixed(80), Sizing.fixed(18));
        buyToggle.active(true).enabled(true);
        buyToggle.hoverCondition(() -> !isModalOpen() || isComponentInModal(buyToggle));

        BedrockToggle sellToggle = new BedrockToggle(Component.literal("Продажа"), existingEntry == null || existingEntry.allowSell(), null);
        sellToggle.sizing(Sizing.fixed(80), Sizing.fixed(18));
        sellToggle.active(true).enabled(true);
        sellToggle.hoverCondition(() -> !isModalOpen() || isComponentInModal(sellToggle));

        BedrockTextBoxComponent buyPriceBox = new BedrockTextBoxComponent(Sizing.fill(100),
                existingEntry != null ? String.valueOf(existingEntry.buyPrice()) : "10.0");
        FlowLayout buyPriceRow = createLabeledInput("oneshop.gui.lot.buy_price", buyPriceBox, "10.0");

        BedrockTextBoxComponent sellPriceBox = new BedrockTextBoxComponent(Sizing.fill(100),
                existingEntry != null ? String.valueOf(existingEntry.sellPrice()) : "5.0");
        FlowLayout sellPriceRow = createLabeledInput("oneshop.gui.lot.sell_price", sellPriceBox, "5.0");

        Runnable updateTradeModeVisuals = () -> {
            boolean buyActive = buyToggle.active() && buyToggle.toggled();
            buyPriceBox.setEditable(buyActive);
            for (var child : buyPriceRow.children()) {
                if (child instanceof LabelComponent lbl) {
                    lbl.color(Color.ofRgb(buyActive ? BedrockTheme.TEXT_WHITE : BedrockSurfaces.TEXT_DISABLED));
                }
            }

            boolean sellActive = sellToggle.active() && sellToggle.toggled();
            sellPriceBox.setEditable(sellActive);
            for (var child : sellPriceRow.children()) {
                if (child instanceof LabelComponent lbl) {
                    lbl.color(Color.ofRgb(sellActive ? BedrockTheme.TEXT_WHITE : BedrockSurfaces.TEXT_DISABLED));
                }
            }
        };

        buyToggle.onToggle(val -> updateTradeModeVisuals.run());
        sellToggle.onToggle(val -> updateTradeModeVisuals.run());

        Runnable updateTradeToggles = () -> {
            buyToggle.active(true).enabled(true);
            sellToggle.active(true).enabled(true);
            updateTradeModeVisuals.run();
        };
        updateTradeToggles.run();

        Runnable refreshPreview = () -> {
            iconPreviewContainer.clearChildren();
            iconPreviewContainer.child(new LotIconDisplayComponent(selectedIcon[0], 32));
            if (!itemStack[0].isEmpty()) {
                itemNameLabel.text(itemStack[0].getHoverName());
            } else if (selectedIcon[0].isItem() && !selectedIcon[0].item().isEmpty()) {
                itemNameLabel.text(selectedIcon[0].item().getHoverName());
            } else if (selectedIcon[0].isTexture()) {
                itemNameLabel.text(Component.literal(selectedIcon[0].texture().getPath()));
            } else {
                itemNameLabel.text(Component.literal(""));
            }
        };

        // Fields: Optional Custom Title, ID, Buy Price, Sell Price, Max Stock, Permission, Commands, Tags
        BedrockTextBoxComponent titleBox = new BedrockTextBoxComponent(Sizing.fill(100), initialTitle);

        BedrockTextBoxComponent stockBox = new BedrockTextBoxComponent(Sizing.fill(100),
                existingEntry != null ? String.valueOf(existingEntry.maxStock()) : "-1");

        BedrockTextBoxComponent permBox = new BedrockTextBoxComponent(Sizing.fill(100),
                existingEntry != null ? existingEntry.permission() : "");

        BedrockTextBoxComponent cmdBox = new BedrockTextBoxComponent(Sizing.fill(100),
                existingEntry != null && !existingEntry.runCommandsOnBuy().isEmpty() ? String.join("; ", existingEntry.runCommandsOnBuy()) : "");

        BedrockTextBoxComponent tagsBox = new BedrockTextBoxComponent(Sizing.fill(100),
                existingEntry != null && existingEntry.hasTags() ? String.join(", ", existingEntry.tags()) : "");

        // Status / Error feedback
        LabelComponent statusLabel = Components.label(Component.literal(""));
        statusLabel.color(Color.ofRgb(BedrockTheme.TEXT_RED));

        // Action Buttons: Save, Delete (if edit), Cancel
        FlowLayout actionRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        actionRow.gap(4);

        BedrockButton saveBtn = new BedrockButton(Component.translatable("oneshop.gui.lot.save"), Sizing.expand(existingEntry != null ? 33 : 50), Sizing.fixed(20), () -> {
            String rawId = idBox.getValue().trim().toLowerCase(Locale.ROOT);
            if (!rawId.isEmpty() && !rawId.contains(":")) {
                rawId = "minecraft:" + rawId;
            }
            String entryId = existingEntry != null ? existingEntry.id() : (!rawId.isEmpty() ? rawId : java.util.UUID.randomUUID().toString());
            List<String> tags = java.util.Arrays.stream(tagsBox.getValue().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.startsWith("#") ? s.substring(1) : s)
                    .toList();

            // Validation rule: a lot is valid if it has EITHER a non-empty item OR at least one tag.
            if (itemStack[0].isEmpty() && tags.isEmpty()) {
                statusLabel.text(Component.translatable("oneshop.gui.lot.error.empty_item_and_tags"));
                return;
            }

            // Fallback to tag icon if icon is empty and lot is tag-only
            if (selectedIcon[0] == null || selectedIcon[0].isEmpty()) {
                if (!itemStack[0].isEmpty()) {
                    selectedIcon[0] = LotIcon.of(itemStack[0]);
                } else if (!tags.isEmpty()) {
                    for (String tagStr : tags) {
                        try {
                            ResourceLocation tagLoc = ResourceLocation.parse(tagStr);
                            var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.ITEM, tagLoc);
                            var holderSet = BuiltInRegistries.ITEM.getTag(tagKey);
                            if (holderSet.isPresent() && holderSet.get().size() > 0) {
                                selectedIcon[0] = LotIcon.of(new ItemStack(holderSet.get().get(0).value()));
                                break;
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (selectedIcon[0] == null || selectedIcon[0].isEmpty()) {
                statusLabel.text(Component.translatable("oneshop.gui.lot.error.empty_icon"));
                return;
            }

            double buyPrice;
            try {
                buyPrice = Double.parseDouble(buyPriceBox.getValue().trim());
                if (buyPrice < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                statusLabel.text(Component.translatable("oneshop.gui.lot.error.invalid_buy"));
                return;
            }

            double sellPrice;
            try {
                sellPrice = Double.parseDouble(sellPriceBox.getValue().trim());
                if (sellPrice < 0) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                statusLabel.text(Component.translatable("oneshop.gui.lot.error.invalid_sell"));
                return;
            }

            int maxStock;
            try {
                maxStock = Integer.parseInt(stockBox.getValue().trim());
                if (maxStock < -1) maxStock = -1;
            } catch (NumberFormatException e) {
                statusLabel.text(Component.translatable("oneshop.gui.lot.error.invalid_stock"));
                return;
            }

            String permission = permBox.getValue().trim();
            String cmdRaw = cmdBox.getValue().trim();
            List<String> commands = List.of();
            if (!cmdRaw.isEmpty()) {
                commands = List.of(cmdRaw.split(";\\s*"));
            }

            boolean allowBuy = buyToggle.toggled();
            boolean allowSell = sellToggle.toggled();
            if (!allowBuy && !allowSell) {
                statusLabel.text(Component.literal("Включите хотя бы один режим: Покупка или Продажа"));
                return;
            }
            String customTitle = titleBox.getValue().trim();

            LotIcon finalIcon = selectedIcon[0];
            if (finalIcon == null || finalIcon.isEmpty()) {
                if (!itemStack[0].isEmpty()) {
                    finalIcon = LotIcon.of(itemStack[0]);
                }
            }
            ShopEntry entry = new ShopEntry(entryId, itemStack[0].copy(), buyPrice, sellPrice, maxStock, permission, commands, allowBuy, allowSell, tags, finalIcon, customTitle, rawId);
            PacketDistributor.sendToServer(new C2SAdminUpdateEntryPayload(category.id(), entry, false));
            ShopCategory updatedCat = category.withEntry(entry);
            ClientShopCache.addOrUpdateCategory(updatedCat);
            refreshItemGrid();
            popModal();
        });

        Consumer<String> validateItemId = rawInput -> {
            String clean = ItemIdResolver.normalizeId(rawInput);
            Optional<Holder.Reference<Item>> itemHolder = ItemIdResolver.resolveItem(rawInput);
            boolean isValid = itemHolder.isPresent();

            List<String> currentTags = java.util.Arrays.stream(tagsBox.getValue().split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            boolean hasTags = !currentTags.isEmpty();

            if (isValid) {
                int count = itemStack[0].isEmpty() ? 1 : Math.max(1, itemStack[0].getCount());
                itemStack[0] = new ItemStack(itemHolder.get().value(), count);
                if (!hasCustomIcon[0]) {
                    selectedIcon[0] = LotIcon.of(itemStack[0]);
                }
                refreshPreview.run();

                if (titleBox.getValue().trim().isEmpty()) {
                    titleBox.setValue(itemStack[0].getHoverName().getString());
                }

                saveBtn.active(true);
                isIdError[0] = false;
                statusLabel.text(Component.literal(""));
            } else {
                if (clean.isEmpty()) {
                    itemStack[0] = ItemStack.EMPTY;
                    if (!hasCustomIcon[0]) {
                        selectedIcon[0] = LotIcon.of(ItemStack.EMPTY);
                    }
                    if (hasTags) {
                        saveBtn.active(true);
                        isIdError[0] = false;
                        statusLabel.text(Component.literal(""));
                    } else {
                        saveBtn.active(false);
                        isIdError[0] = false;
                    }
                    refreshPreview.run();
                } else {
                    saveBtn.active(false);
                    isIdError[0] = true;
                    statusLabel.text(Component.translatable("oneshop.gui.lot.error.invalid_item_id", clean));
                }
            }
        };

        idBox.onChanged().subscribe(validateItemId::accept);
        tagsBox.onChanged().subscribe(tagText -> validateItemId.accept(idBox.getValue()));

        iconBtn.mouseDown().subscribe((mouseX, mouseY, button) -> {
            openIconPickerModal(pickedIcon -> {
                selectedIcon[0] = pickedIcon;
                hasCustomIcon[0] = true;
                updateTradeToggles.run();
                refreshPreview.run();
            });
            return true;
        });

        itemRow.child(iconBtn);
        itemRow.child(itemNameLabel);

        FlowLayout itemSpacer = Containers.horizontalFlow(Sizing.expand(100), Sizing.content());
        itemRow.child(itemSpacer);

        BedrockButton grabItemBtn = new BedrockButton(Component.translatable("oneshop.gui.lot.grab_hand"), Sizing.content(), Sizing.fixed(18), () -> {
            if (minecraft != null && minecraft.player != null && !minecraft.player.getMainHandItem().isEmpty()) {
                ItemStack handStack = minecraft.player.getMainHandItem();
                String regId = BuiltInRegistries.ITEM.getKey(handStack.getItem()).toString();
                idBox.setValue(regId);
                validateItemId.accept(regId);
            }
        });
        itemRow.child(grabItemBtn);

        BedrockButton clearItemBtn = new BedrockButton(Component.literal("✕"), Sizing.fixed(18), Sizing.fixed(18), () -> {
            idBox.setValue("");
            validateItemId.accept("");
        });
        clearItemBtn.tooltip(Component.literal("Очистить предмет (лот только по тегу)"));
        itemRow.child(clearItemBtn);

        dialog.child(itemRow);

        dialog.child(createLabeledInput("oneshop.gui.lot.custom_title", titleBox, "Название лота (опционально)"));
        dialog.child(createLabeledInput("oneshop.gui.lot.id", idBox, "e.g. diamond_sword", () -> isIdError[0]));
        dialog.child(buyPriceRow);
        dialog.child(sellPriceRow);
        dialog.child(createLabeledInput("oneshop.gui.lot.stock", stockBox, "-1"));
        dialog.child(createLabeledInput("oneshop.gui.lot.permission", permBox, "optional.permission"));
        dialog.child(createLabeledInput("oneshop.gui.lot.command", cmdBox, "command; command2"));
        dialog.child(createLabeledInput("oneshop.gui.lot.tags", tagsBox, "e.g. #minecraft:logs, c:ores"));

        // Trade Options: BedrockToggle for Buy ("Покупка") and Sell ("Продажа")
        FlowLayout tradeOptionsRow = Containers.horizontalFlow(Sizing.content(), Sizing.content());
        tradeOptionsRow.verticalAlignment(VerticalAlignment.CENTER);
        tradeOptionsRow.gap(16);

        tradeOptionsRow.child(buyToggle);
        tradeOptionsRow.child(sellToggle);

        dialog.child(tradeOptionsRow);
        dialog.child(statusLabel);

        validateItemId.accept(idBox.getValue());
        actionRow.child(saveBtn);

        if (existingEntry != null) {
            BedrockButton deleteBtn = new BedrockButton(Component.translatable("oneshop.gui.lot.delete"), Sizing.expand(33), Sizing.fixed(20), () -> {
                PacketDistributor.sendToServer(new C2SAdminUpdateEntryPayload(category.id(), existingEntry, true));
                ShopCategory updatedCat = category.withoutEntry(existingEntry.id());
                ClientShopCache.addOrUpdateCategory(updatedCat);
                refreshItemGrid();
                popModal();
            });
            deleteBtn.textColor(BedrockTheme.TEXT_RED);
            actionRow.child(deleteBtn);
        }

        BedrockButton cancelBtn = new BedrockButton(Component.translatable("oneshop.gui.button.cancel"), Sizing.expand(existingEntry != null ? 33 : 50), Sizing.fixed(20), this::popModal);
        actionRow.child(cancelBtn);

        dialog.child(actionRow);
        modalBackdrop.child(dialog);
        mountModal(modalBackdrop, dialog);
    }


    // --- Quantity Selector Modal Dialog ---

    private void openQuantitySelectorModal(ShopCategory category, ShopEntry entry, boolean isBuy) {
        if (isBuy && !entry.isBuyAllowed()) {
            return;
        }

        double unitPrice = isBuy ? entry.buyPrice() : entry.sellPrice();
        double balance = ClientBankCache.getBalance();
        int availableItemsInInventory = countPlayerItems(entry);

        // Max calculation
        int maxAllowed;
        if (isBuy) {
            int byBalance = unitPrice > 0 ? (int) (balance / unitPrice) : 9999;
            int byStock = entry.isUnlimited() ? 9999 : entry.maxStock();
            maxAllowed = Math.max(1, Math.min(byBalance, byStock));
        } else {
            maxAllowed = Math.max(1, availableItemsInInventory);
        }

        final int[] quantity = {1};

        FlowLayout modalBackdrop = createModalBackdrop();
        modalBackdrop.zIndex(500);

        // Intercept clicks on backdrop
        modalBackdrop.mouseDown().subscribe((mouseX, mouseY, button) -> true);
        modalBackdrop.mouseUp().subscribe((mouseX, mouseY, button) -> true);
        modalBackdrop.mouseScroll().subscribe((mouseX, mouseY, amount) -> true);

        FlowLayout dialog = Containers.verticalFlow(Sizing.fixed(240), Sizing.content());
        dialog.surface(BedrockTheme.PANEL);
        dialog.padding(Insets.of(8));
        dialog.gap(6);
        dialog.zIndex(501);

        Component displayName;
        if (entry.hasCustomTitle()) {
            displayName = Component.literal(entry.customTitle());
        } else if (!entry.item().isEmpty()) {
            displayName = entry.item().getHoverName();
        } else if (entry.hasTags()) {
            String tagsFormatted = String.join(", ", entry.tags().stream().map(t -> t.startsWith("#") ? t : "#" + t).toList());
            displayName = Component.literal(tagsFormatted);
        } else if (entry.icon() != null && entry.icon().isItem() && !entry.icon().item().isEmpty()) {
            displayName = entry.icon().item().getHoverName();
        } else if (entry.icon() != null && entry.icon().isTexture()) {
            displayName = Component.literal(entry.icon().texture().getPath());
        } else {
            displayName = Component.literal(entry.id());
        }

        // Header Title
        Component titleComp = Component.translatable(isBuy ? "oneshop.gui.modal.buy_title" : "oneshop.gui.modal.sell_title", displayName.getString())
                .withStyle(ChatFormatting.BOLD);
        LabelComponent title = Components.label(titleComp);
        title.color(Color.ofRgb(isBuy ? BedrockTheme.TEXT_GREEN : BedrockTheme.TEXT_GOLD));
        dialog.child(title);

        // Item preview
        FlowLayout previewRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        previewRow.verticalAlignment(VerticalAlignment.CENTER);
        LotIcon modalDisplayIcon = entry.getVisualIcon();
        previewRow.child(new LotIconDisplayComponent(modalDisplayIcon, 16));
        previewRow.child(Components.label(displayName).color(Color.ofRgb(BedrockTheme.TEXT_WHITE)));
        dialog.child(previewRow);

        // Stepper: [-] [ quantity text box ] [+]
        FlowLayout stepperRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        stepperRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        stepperRow.gap(6);

        BedrockTextBoxComponent qtyBox = new BedrockTextBoxComponent(Sizing.fill(100), "1");
        HoverableFlowLayout inputFrame = createNumericInputFrame(qtyBox, null, 70);

        LabelComponent totalLabel = Components.label(Component.literal(""));
        LabelComponent statusLabel = Components.label(Component.literal(""));

        final boolean[] sent = {false};
        BedrockButton confirmBtn = new BedrockButton(Component.translatable("oneshop.gui.button.confirm"), Sizing.expand(50), Sizing.fixed(20), () -> {
            if (sent[0]) {
                return; // already dispatched, ignore spam clicks while the modal closes
            }
            int qty = quantity[0];
            double total = qty * unitPrice;
            double currentBalance = ClientBankCache.getBalance();
            OneShop.LOGGER.info("[OneShop/Client] {} click: category={} entry={} quantity={} total={} balance={}",
                    isBuy ? "BUY" : "SELL", category.id(), entry.id(), qty, total, currentBalance);

            Component error = null;
            if (qty <= 0) {
                error = Component.translatable("oneshop.gui.modal.status.invalid_amount");
            } else if (isBuy && currentBalance < total) {
                error = Component.translatable("oneshop.gui.modal.status.insufficient_funds");
            } else if (isBuy && !entry.isUnlimited() && qty > entry.maxStock()) {
                error = Component.translatable("oneshop.gui.modal.status.exceeds_stock");
            } else if (!isBuy && qty > countPlayerItems(entry)) {
                error = Component.translatable("oneshop.gui.modal.status.not_enough_inventory");
            }
            if (error != null) {
                OneShop.LOGGER.warn("[OneShop/Client] Transaction rejected locally: {}", error.getString());
                statusLabel.text(error.copy().withStyle(ChatFormatting.RED));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                shakeDialog(dialog);
                return;
            }

            sent[0] = true;
            PacketDistributor.sendToServer(new C2SExecuteTransactionPayload(category.id(), entry.id(), qty, isBuy));
            closeModal();
        });

        boolean[] updatingFromButtons = {false};

        java.util.function.Consumer<Boolean> updateState = (syncText) -> {
            if (syncText) {
                updatingFromButtons[0] = true;
                qtyBox.setValue(String.valueOf(quantity[0]));
                updatingFromButtons[0] = false;
            }
            double total = quantity[0] * unitPrice;
            totalLabel.text(Component.translatable("oneshop.gui.modal.total", String.format(Locale.ROOT, "%.2f", total), SprotaItem.CURRENCY_SYMBOL));

            if (isBuy) {
                if (total > ClientBankCache.getBalance()) {
                    statusLabel.text(Component.translatable("oneshop.gui.modal.status.insufficient_funds").withStyle(ChatFormatting.RED));
                    confirmBtn.active(true); // stays clickable: invalid input is explained on click
                } else if (!entry.isUnlimited() && quantity[0] > entry.maxStock()) {
                    statusLabel.text(Component.translatable("oneshop.gui.modal.status.exceeds_stock").withStyle(ChatFormatting.RED));
                    confirmBtn.active(true); // stays clickable: invalid input is explained on click
                } else if (quantity[0] <= 0) {
                    statusLabel.text(Component.translatable("oneshop.gui.modal.status.invalid_amount").withStyle(ChatFormatting.RED));
                    confirmBtn.active(true); // stays clickable: invalid input is explained on click
                } else {
                    statusLabel.text(Component.translatable("oneshop.gui.modal.status.ready_buy").withStyle(ChatFormatting.GREEN));
                    confirmBtn.active(true); // stays clickable: invalid input is explained on click
                }
            } else {
                if (quantity[0] > availableItemsInInventory) {
                    statusLabel.text(Component.translatable("oneshop.gui.modal.status.not_enough_inventory").withStyle(ChatFormatting.RED));
                    confirmBtn.active(true); // stays clickable: invalid input is explained on click
                } else if (quantity[0] <= 0) {
                    statusLabel.text(Component.translatable("oneshop.gui.modal.status.invalid_amount").withStyle(ChatFormatting.RED));
                    confirmBtn.active(true); // stays clickable: invalid input is explained on click
                } else {
                    statusLabel.text(Component.translatable("oneshop.gui.modal.status.ready_sell").withStyle(ChatFormatting.GREEN));
                    confirmBtn.active(true); // stays clickable: invalid input is explained on click
                }
            }
        };

        qtyBox.onChanged().subscribe(val -> {
            if (updatingFromButtons[0]) return;
            String digits = val.replaceAll("[^0-9]", "");
            if (!digits.equals(val)) {
                updatingFromButtons[0] = true;
                qtyBox.setValue(digits);
                updatingFromButtons[0] = false;
            }
            if (!digits.isEmpty()) {
                try {
                    long parsed = Long.parseLong(digits);
                    int clamped = (int) Math.max(1, Math.min(parsed, maxAllowed > 0 ? maxAllowed : 1));
                    quantity[0] = clamped;
                } catch (NumberFormatException ignored) {}
            } else {
                quantity[0] = 1;
            }
            updateState.accept(false);
        });

        BedrockButton minusBtn = new BedrockButton(Component.literal("-"), Sizing.fixed(24), Sizing.fixed(18), () -> {
            if (quantity[0] > 1) {
                quantity[0]--;
                updateState.accept(true);
            }
        });
        stepperRow.child(minusBtn);
        stepperRow.child(inputFrame);
        BedrockButton plusBtn = new BedrockButton(Component.literal("+"), Sizing.fixed(24), Sizing.fixed(18), () -> {
            if (quantity[0] < maxAllowed) {
                quantity[0]++;
                updateState.accept(true);
            }
        });
        stepperRow.child(plusBtn);

        dialog.child(stepperRow);

        // Quick amount buttons: [1] [16] [32] [64] [Max]
        FlowLayout quickRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        quickRow.gap(2);
        int[] amounts = {1, 16, 32, 64};
        for (int amt : amounts) {
            BedrockButton qBtn = new BedrockButton(Component.literal(String.valueOf(amt)), Sizing.expand(20), Sizing.fixed(18), () -> {
                quantity[0] = Math.min(amt, maxAllowed > 0 ? maxAllowed : amt);
                updateState.accept(true);
            });
            quickRow.child(qBtn);
        }

        BedrockButton maxBtn = new BedrockButton(Component.translatable("oneshop.gui.modal.quick_max"), Sizing.expand(20), Sizing.fixed(18), () -> {
            quantity[0] = Math.max(1, maxAllowed);
            updateState.accept(true);
        });
        quickRow.child(maxBtn);

        dialog.child(quickRow);

        // Total price and status labels
        totalLabel.color(Color.ofRgb(BedrockTheme.TEXT_GOLD));
        dialog.child(totalLabel);
        dialog.child(statusLabel);

        updateState.accept(true);

        // Action buttons: [Confirm] [Cancel]
        FlowLayout actionRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        actionRow.gap(4);

        actionRow.child(confirmBtn);

        BedrockButton cancelBtn = new BedrockButton(Component.translatable("oneshop.gui.button.cancel"), Sizing.expand(50), Sizing.fixed(20), this::closeModal);
        actionRow.child(cancelBtn);

        dialog.child(actionRow);
        modalBackdrop.child(dialog);
        mountModal(modalBackdrop, dialog);
    }

    // --- Quick Deposit Modal Dialog ---

    private void openDepositModal() {
        int availableCoins = countPlayerCoins();

        FlowLayout modalBackdrop = createModalBackdrop();
        modalBackdrop.zIndex(500);

        // Intercept clicks on backdrop
        modalBackdrop.mouseDown().subscribe((mouseX, mouseY, button) -> true);
        modalBackdrop.mouseUp().subscribe((mouseX, mouseY, button) -> true);
        modalBackdrop.mouseScroll().subscribe((mouseX, mouseY, amount) -> true);

        FlowLayout dialog = Containers.verticalFlow(Sizing.fixed(220), Sizing.content());
        dialog.surface(BedrockTheme.PANEL);
        dialog.padding(Insets.of(8));
        dialog.gap(6);
        dialog.zIndex(501);

        LabelComponent title = Components.label(Component.translatable("oneshop.gui.deposit.title").withStyle(ChatFormatting.BOLD));
        title.color(Color.ofRgb(BedrockTheme.TEXT_GREEN));
        dialog.child(title);

        dialog.child(Components.label(Component.translatable("oneshop.gui.deposit.in_inventory", availableCoins, SprotaItem.CURRENCY_SYMBOL)).color(Color.ofRgb(BedrockTheme.TEXT_MUTED)));

        final int[] amount = {Math.min(1, availableCoins)};

        FlowLayout inputRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        inputRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        inputRow.gap(6);

        BedrockTextBoxComponent amtBox = new BedrockTextBoxComponent(Sizing.fill(100), String.valueOf(amount[0]));
        HoverableFlowLayout inputFrame = createNumericInputFrame(amtBox, null, 70);

        BedrockButton confirmBtn = new BedrockButton(Component.translatable("oneshop.gui.deposit.confirm"), Sizing.expand(50), Sizing.fixed(20), () -> {
            if (amount[0] > 0) {
                PacketDistributor.sendToServer(new C2SDepositCashPayload(amount[0]));
            }
            closeModal();
        });

        boolean[] updatingFromButtons = {false};

        java.util.function.Consumer<Boolean> updateState = (syncText) -> {
            if (syncText) {
                updatingFromButtons[0] = true;
                amtBox.setValue(String.valueOf(amount[0]));
                updatingFromButtons[0] = false;
            }
            confirmBtn.active(amount[0] > 0 && amount[0] <= availableCoins);
        };

        amtBox.onChanged().subscribe(val -> {
            if (updatingFromButtons[0]) return;
            String digits = val.replaceAll("[^0-9]", "");
            if (!digits.equals(val)) {
                updatingFromButtons[0] = true;
                amtBox.setValue(digits);
                updatingFromButtons[0] = false;
            }
            if (!digits.isEmpty()) {
                try {
                    long parsed = Long.parseLong(digits);
                    amount[0] = (int) Math.max(1, Math.min(parsed, availableCoins > 0 ? availableCoins : 1));
                } catch (NumberFormatException ignored) {}
            } else {
                amount[0] = 0;
            }
            updateState.accept(false);
        });

        BedrockButton minusBtn = new BedrockButton(Component.literal("-"), Sizing.fixed(24), Sizing.fixed(18), () -> {
            if (amount[0] > 1) {
                amount[0]--;
                updateState.accept(true);
            }
        });
        inputRow.child(minusBtn);
        inputRow.child(inputFrame);
        BedrockButton plusBtn = new BedrockButton(Component.literal("+"), Sizing.fixed(24), Sizing.fixed(18), () -> {
            if (amount[0] < availableCoins) {
                amount[0]++;
                updateState.accept(true);
            }
        });
        inputRow.child(plusBtn);
        dialog.child(inputRow);

        // Quick amount buttons
        FlowLayout quickRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        quickRow.gap(2);
        int[] preset = {1, 16, 32, 64};
        for (int p : preset) {
            BedrockButton btn = new BedrockButton(Component.literal(String.valueOf(p)), Sizing.expand(20), Sizing.fixed(18), () -> {
                amount[0] = Math.min(p, availableCoins);
                updateState.accept(true);
            });
            quickRow.child(btn);
        }

        BedrockButton allBtn = new BedrockButton(Component.translatable("oneshop.gui.modal.quick_all"), Sizing.expand(20), Sizing.fixed(18), () -> {
            amount[0] = availableCoins;
            updateState.accept(true);
        });
        quickRow.child(allBtn);
        dialog.child(quickRow);

        updateState.accept(true);

        // Actions
        FlowLayout actionRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        actionRow.gap(4);

        actionRow.child(confirmBtn);

        BedrockButton cancelBtn = new BedrockButton(Component.translatable("oneshop.gui.button.cancel"), Sizing.expand(50), Sizing.fixed(20), this::closeModal);
        actionRow.child(cancelBtn);

        dialog.child(actionRow);
        modalBackdrop.child(dialog);
        mountModal(modalBackdrop, dialog);
    }

    // --- Quick Withdraw Modal Dialog ---

    private void openWithdrawModal() {
        int maxBalance = (int) ClientBankCache.getBalance();
        int inventorySpace = countPlayerInventorySpaceForSprota();
        int maxWithdrawable = Math.max(0, Math.min(maxBalance, inventorySpace));

        FlowLayout modalBackdrop = createModalBackdrop();
        modalBackdrop.zIndex(500);

        // Intercept clicks on backdrop
        modalBackdrop.mouseDown().subscribe((mouseX, mouseY, button) -> true);
        modalBackdrop.mouseUp().subscribe((mouseX, mouseY, button) -> true);
        modalBackdrop.mouseScroll().subscribe((mouseX, mouseY, amount) -> true);

        FlowLayout dialog = Containers.verticalFlow(Sizing.fixed(220), Sizing.content());
        dialog.surface(BedrockTheme.PANEL);
        dialog.padding(Insets.of(8));
        dialog.gap(6);
        dialog.zIndex(501);

        LabelComponent title = Components.label(Component.translatable("oneshop.gui.withdraw.title").withStyle(ChatFormatting.BOLD));
        title.color(Color.ofRgb(BedrockTheme.TEXT_GOLD));
        dialog.child(title);

        dialog.child(Components.label(Component.translatable("oneshop.gui.withdraw.bank_balance", formatBalanceAmount() + " " + SprotaItem.CURRENCY_SYMBOL)).color(Color.ofRgb(BedrockTheme.TEXT_MUTED)));
        dialog.child(Components.label(Component.translatable("oneshop.gui.withdraw.inventory_space", inventorySpace)).color(Color.ofRgb(BedrockTheme.TEXT_MUTED)));

        final int[] amount = {Math.min(1, maxWithdrawable)};

        FlowLayout inputRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        inputRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        inputRow.gap(6);

        BedrockTextBoxComponent amtBox = new BedrockTextBoxComponent(Sizing.fill(100), String.valueOf(amount[0]));
        HoverableFlowLayout inputFrame = createNumericInputFrame(amtBox, null, 70);

        BedrockButton confirmBtn = new BedrockButton(Component.translatable("oneshop.gui.withdraw.confirm"), Sizing.expand(50), Sizing.fixed(20), () -> {
            if (amount[0] > 0 && amount[0] <= maxWithdrawable) {
                PacketDistributor.sendToServer(new C2SWithdrawCashPayload(amount[0]));
            }
            closeModal();
        });

        boolean[] updatingFromButtons = {false};

        java.util.function.Consumer<Boolean> updateState = (syncText) -> {
            if (syncText) {
                updatingFromButtons[0] = true;
                amtBox.setValue(String.valueOf(amount[0]));
                updatingFromButtons[0] = false;
            }
            confirmBtn.active(amount[0] > 0 && amount[0] <= maxWithdrawable);
        };

        amtBox.onChanged().subscribe(val -> {
            if (updatingFromButtons[0]) return;
            String digits = val.replaceAll("[^0-9]", "");
            if (!digits.equals(val)) {
                updatingFromButtons[0] = true;
                amtBox.setValue(digits);
                updatingFromButtons[0] = false;
            }
            if (!digits.isEmpty()) {
                try {
                    long parsed = Long.parseLong(digits);
                    amount[0] = (int) Math.max(1, Math.min(parsed, maxWithdrawable > 0 ? maxWithdrawable : 1));
                } catch (NumberFormatException ignored) {}
            } else {
                amount[0] = 0;
            }
            updateState.accept(false);
        });

        BedrockButton minusBtn = new BedrockButton(Component.literal("-"), Sizing.fixed(24), Sizing.fixed(18), () -> {
            if (amount[0] > 1) {
                amount[0]--;
                updateState.accept(true);
            }
        });
        inputRow.child(minusBtn);
        inputRow.child(inputFrame);
        BedrockButton plusBtn = new BedrockButton(Component.literal("+"), Sizing.fixed(24), Sizing.fixed(18), () -> {
            if (amount[0] < maxWithdrawable) {
                amount[0]++;
                updateState.accept(true);
            }
        });
        inputRow.child(plusBtn);
        dialog.child(inputRow);

        // Quick amount buttons
        FlowLayout quickRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        quickRow.gap(2);
        int[] preset = {1, 16, 32, 64};
        for (int p : preset) {
            BedrockButton btn = new BedrockButton(Component.literal(String.valueOf(p)), Sizing.expand(20), Sizing.fixed(18), () -> {
                amount[0] = Math.min(p, maxWithdrawable);
                updateState.accept(true);
            });
            quickRow.child(btn);
        }

        BedrockButton allBtn = new BedrockButton(Component.translatable("oneshop.gui.modal.quick_all"), Sizing.expand(20), Sizing.fixed(18), () -> {
            amount[0] = maxWithdrawable;
            updateState.accept(true);
        });
        quickRow.child(allBtn);
        dialog.child(quickRow);

        updateState.accept(true);

        // Actions
        FlowLayout actionRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        actionRow.gap(4);

        actionRow.child(confirmBtn);

        BedrockButton cancelBtn = new BedrockButton(Component.translatable("oneshop.gui.button.cancel"), Sizing.expand(50), Sizing.fixed(20), this::closeModal);
        actionRow.child(cancelBtn);

        dialog.child(actionRow);
        modalBackdrop.child(dialog);
        mountModal(modalBackdrop, dialog);
    }

    private void openTransferModal() {
        FlowLayout overlay = TransferModal.createOverlay(this, this::popModal);
        FlowLayout dialog = (FlowLayout) overlay.children().get(0);
        mountModal(overlay, dialog);
    }


    private void openIconPickerModal(java.util.function.Consumer<LotIcon> onSelect) {
        FlowLayout overlay = IconPickerModal.createOverlay(
                picked -> {
                    onSelect.accept(picked);
                    popModal();
                },
                this::popModal
        );
        FlowLayout dialog = (FlowLayout) overlay.children().get(0);
        pushModal(overlay, dialog);
    }

    @Override
    protected boolean isCloseKeybind(int keyCode, int scanCode, int modifiers) {
        return ModKeyBindings.OPEN_SHOP.matches(keyCode, scanCode) || ModKeyBindings.OPEN_ADMIN_SHOP.matches(keyCode, scanCode);
    }

    private int countPlayerItems(ShopEntry entry) {
        if (minecraft == null || minecraft.player == null) return 0;
        int count = 0;
        for (ItemStack slot : minecraft.player.getInventory().items) {
            if (!slot.isEmpty() && entry.matchesTradeItem(slot)) {
                count += slot.getCount();
            }
        }
        return count;
    }

    private int countPlayerItems(ItemStack template) {
        if (minecraft == null || minecraft.player == null) return 0;
        int count = 0;
        for (ItemStack slot : minecraft.player.getInventory().items) {
            if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, template)) {
                count += slot.getCount();
            }
        }
        return count;
    }

    private int countPlayerCoins() {
        if (minecraft == null || minecraft.player == null) return 0;
        int count = 0;
        var coinItem = ModItems.SPROTA.get();
        for (ItemStack slot : minecraft.player.getInventory().items) {
            if (!slot.isEmpty() && slot.is(coinItem)) {
                count += slot.getCount();
            }
        }
        return count;
    }

    private int countPlayerInventorySpaceForSprota() {
        if (minecraft == null || minecraft.player == null) return 0;
        return ShopTransactionService.calculateInventoryCapacityForSprota(minecraft.player);
    }

    @Override
    public void tick() {
        super.tick();
        updateBalanceDisplay();
    }

    @Override
    public void removed() {
        super.removed();
    }
}
