package com.oneshop.client.ui;

import com.oneshop.client.ClientShopCache;
import com.oneshop.network.C2SFulfillKnyazOrderPayload;
import com.oneshop.network.C2SKnyazManageOrderPayload;
import com.oneshop.shop.KnyazOrder;
import com.oneshop.shop.KnyazOrderItem;
import com.oneshop.shop.LotIcon;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.container.ScrollContainer;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.CursorStyle;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Modals for Knyaz Orders ("Заказы Князя"):
 * 1. Order Details & Checklist Modal (displays resource items and text tasks as an interactive checklist,
 *    tracks inventory availability, allows resident to respond to the order, and allows Knyaz to mark tasks completed).
 * 2. Knyaz Order Creation / Editing Modal (allows adding resource items and text tasks,
 *    setting required quantities, setting description, setting informational monetary reward, and publishing).
 */
public final class KnyazOrderModals {
    private static final ResourceLocation CURRENCY_ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("oneshop", "textures/gui/currency_icon.png");

    private KnyazOrderModals() {}

    /**
     * Opens the Knyaz Order inspection & checklist window.
     */
    public static FlowLayout createOrderDetailOverlay(ShopScreen screen, KnyazOrder initialOrder, boolean canManageOrders, Runnable onClose) {
        FlowLayout modalBackdrop = ShopScreen.createModalBackdrop();
        modalBackdrop.zIndex(500);

        FlowLayout dialog = Containers.verticalFlow(Sizing.fixed(320), Sizing.content());
        dialog.surface(BedrockTheme.PANEL);
        dialog.padding(Insets.of(8));
        dialog.gap(6);
        dialog.zIndex(501);

        final KnyazOrder[] currentOrder = {initialOrder};

        // Header Row: Title + Knyaz author + Close button
        FlowLayout headerRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        headerRow.verticalAlignment(VerticalAlignment.CENTER);
        headerRow.gap(4);

        LabelComponent titleLabel = Components.label(Component.literal("👑 " + currentOrder[0].getDisplayTitle()).withStyle(ChatFormatting.BOLD));
        titleLabel.color(Color.ofRgb(BedrockTheme.TEXT_GOLD));
        headerRow.child(titleLabel);

        headerRow.child(Containers.horizontalFlow(Sizing.expand(100), Sizing.content()));

        if (canManageOrders) {
            BedrockButton editBtn = new BedrockButton(Component.literal("✎"), Sizing.fixed(18), Sizing.fixed(18), () -> {
                onClose.run();
                Minecraft.getInstance().tell(() -> screen.openKnyazOrderEditorModal(currentOrder[0]));
            });
            editBtn.textColor(BedrockTheme.TEXT_GOLD);
            editBtn.tooltip(Component.translatable("oneshop.knyaz_orders.edit_tooltip"));
            headerRow.child(editBtn);
        }

        BedrockButton closeBtn = new BedrockButton(Component.literal("✕"), Sizing.fixed(18), Sizing.fixed(18), onClose);
        headerRow.child(closeBtn);
        dialog.child(headerRow);

        // Info Row: Author, Informational Reward, Description, Status, IRL hint, Respondents, Checklist progress
        FlowLayout infoBox = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        infoBox.surface(BedrockTheme.CARD);
        infoBox.padding(Insets.of(5));
        infoBox.gap(3);

        if (!currentOrder[0].createdBy().isEmpty()) {
            infoBox.child(Components.label(Component.translatable("oneshop.knyaz_orders.created_by", currentOrder[0].createdBy()))
                    .color(Color.ofRgb(BedrockTheme.TEXT_MUTED)));
        }

        FlowLayout rewardRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        rewardRow.verticalAlignment(VerticalAlignment.CENTER);
        rewardRow.gap(3);
        rewardRow.child(Components.label(Component.translatable(
                "oneshop.knyaz_orders.reward_info_fixed",
                formatLong(currentOrder[0].totalReward())
        )).color(Color.ofRgb(BedrockTheme.TEXT_GOLD)));
        rewardRow.child(new TextureDisplayComponent(CURRENCY_ICON_TEXTURE, 8, 8, 0f, 0f, 8, 8, 8, 8));
        infoBox.child(rewardRow);

        if (!currentOrder[0].description().isBlank()) {
            LabelComponent descLbl = Components.label(Component.translatable("oneshop.knyaz_orders.description_info", currentOrder[0].description()));
            descLbl.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
            descLbl.maxWidth(295);
            infoBox.child(descLbl);
        }

        LabelComponent orderStatusLabel = Components.label(Component.translatable(
                currentOrder[0].isCompleted()
                        ? "oneshop.knyaz_orders.order_status_done"
                        : "oneshop.knyaz_orders.order_status_active"
        ));
        orderStatusLabel.color(Color.ofRgb(currentOrder[0].isCompleted() ? BedrockTheme.TEXT_GREEN : BedrockTheme.TEXT_GOLD));
        infoBox.child(orderStatusLabel);

        infoBox.child(Components.label(Component.translatable("oneshop.knyaz_orders.irl_hint"))
                .color(Color.ofRgb(BedrockTheme.TEXT_MUTED)));

        if (!currentOrder[0].respondents().isEmpty()) {
            infoBox.child(Components.label(Component.translatable(
                    "oneshop.knyaz_orders.respondents_list",
                    String.join(", ", currentOrder[0].respondents())
            )).color(Color.ofRgb(BedrockTheme.TEXT_GOLD)));
        }

        Map<Item, Integer> playerInvCounts = countPlayerInventoryByItem();
        boolean[] checkedState = new boolean[currentOrder[0].items().size()];
        Map<Item, Integer> initialAlloc = new HashMap<>(playerInvCounts);
        int initialCheckedCount = 0;
        for (int i = 0; i < currentOrder[0].items().size(); i++) {
            KnyazOrderItem item = currentOrder[0].items().get(i);
            if (item.isTextTask()) {
                checkedState[i] = item.isCompleted();
            } else {
                Item resolved = item.resolveItem();
                int avail = (resolved != Items.AIR) ? initialAlloc.getOrDefault(resolved, 0) : 0;
                if (item.isCompleted() || avail >= item.requiredCount()) {
                    checkedState[i] = true;
                    if (resolved != Items.AIR && !item.isCompleted()) {
                        initialAlloc.put(resolved, avail - item.requiredCount());
                    }
                } else {
                    checkedState[i] = false;
                }
            }
            if (checkedState[i]) initialCheckedCount++;
        }

        LabelComponent checklistProgressLabel = Components.label(Component.translatable(
                "oneshop.knyaz_orders.checklist_progress",
                initialCheckedCount,
                currentOrder[0].items().size()
        ));
        checklistProgressLabel.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
        infoBox.child(checklistProgressLabel);

        dialog.child(infoBox);

        // Subtitle: Checklist header
        LabelComponent listHeader = Components.label(Component.translatable("oneshop.knyaz_orders.items_header"));
        listHeader.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
        dialog.child(listHeader);

        // Checklist items list
        FlowLayout itemsList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        itemsList.gap(4);

        // Action Buttons Row (declared before refreshChecklist so markDoneBtn can update dynamically)
        FlowLayout actionRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        actionRow.gap(4);

        final Runnable[] refreshChecklist = new Runnable[1];
        refreshChecklist[0] = () -> {
            itemsList.clearChildren();
            int currentChecked = 0;
            for (boolean b : checkedState) {
                if (b) currentChecked++;
            }
            checklistProgressLabel.text(Component.translatable(
                    "oneshop.knyaz_orders.checklist_progress",
                    currentChecked,
                    currentOrder[0].items().size()
            ));

            boolean completedOrder = currentOrder[0].isCompleted();
            orderStatusLabel.text(Component.translatable(
                    completedOrder
                            ? "oneshop.knyaz_orders.order_status_done"
                            : "oneshop.knyaz_orders.order_status_active"
            ));
            orderStatusLabel.color(Color.ofRgb(completedOrder ? BedrockTheme.TEXT_GREEN : BedrockTheme.TEXT_GOLD));

            for (int i = 0; i < currentOrder[0].items().size(); i++) {
                final int idx = i;
                KnyazOrderItem orderItem = currentOrder[0].items().get(i);

                HoverableFlowLayout itemCard = new HoverableFlowLayout(Sizing.fill(100), Sizing.fixed(30), FlowLayout.Algorithm.HORIZONTAL);
                itemCard.verticalAlignment(VerticalAlignment.CENTER);
                itemCard.padding(Insets.of(3, 3, 5, 5));
                itemCard.gap(6);
                itemCard.cursorStyle(CursorStyle.HAND);

                if (orderItem.isTextTask()) {
                    boolean isDone = orderItem.isCompleted();
                    int frameColor = isDone ? BedrockTheme.TEXT_GREEN : BedrockTheme.TEXT_RED;

                    itemCard.surface((context, comp) -> {
                        int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
                        int bg = itemCard.isHovered() ? BedrockTheme.BG_HOVER : BedrockTheme.BG_CARD;
                        BedrockTheme.drawBeveledBox(context, x, y, w, h, bg, frameColor, frameColor);
                        BedrockTheme.drawOutline(context, x, y, w, h, frameColor);
                    });

                    // Checkbox badge: [✓] or [ ]
                    LabelComponent checkLbl = Components.label(Component.literal(isDone ? "[✓]" : "[ ]"));
                    checkLbl.color(Color.ofRgb(frameColor));
                    itemCard.child(checkLbl);

                    // Book icon slot (22x22)
                    FlowLayout iconSlot = Containers.horizontalFlow(Sizing.fixed(22), Sizing.fixed(22));
                    iconSlot.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                    iconSlot.surface((context, comp) -> {
                        BedrockTheme.drawBeveledBox(context, comp.x(), comp.y(), comp.width(), comp.height(), BedrockTheme.BG_INPUT, frameColor, frameColor);
                    });
                    iconSlot.child(new ItemDisplayComponent(new ItemStack(Items.WRITABLE_BOOK), 16));
                    itemCard.child(iconSlot);

                    // Text column
                    FlowLayout textCol = Containers.verticalFlow(Sizing.expand(100), Sizing.content());
                    textCol.gap(1);

                    LabelComponent taskLbl = Components.label(Component.literal(orderItem.taskText()));
                    taskLbl.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
                    taskLbl.shadow(true);
                    taskLbl.maxWidth(165);
                    textCol.child(taskLbl);

                    Component hintText = Component.translatable(
                            canManageOrders
                                    ? "oneshop.knyaz_orders.text_task_knyaz_hint"
                                    : "oneshop.knyaz_orders.text_task_resident_hint"
                    );
                    LabelComponent subLbl = Components.label(hintText);
                    subLbl.color(Color.ofRgb(isDone ? BedrockTheme.TEXT_GREEN : BedrockTheme.TEXT_MUTED));
                    textCol.child(subLbl);
                    itemCard.child(textCol);

                    // Right status badge: ✓ Выполнено / Не выполнено
                    Component badgeComp = Component.translatable(isDone
                            ? "oneshop.knyaz_orders.status_completed_knyaz"
                            : "oneshop.knyaz_orders.status_not_completed_knyaz");
                    LabelComponent badgeLbl = Components.label(badgeComp);
                    badgeLbl.color(Color.ofRgb(frameColor));
                    itemCard.child(badgeLbl);

                    itemCard.mouseDown().subscribe((mx, my, button) -> {
                        if (button == 0) {
                            if (canManageOrders) {
                                boolean nextState = !orderItem.isCompleted();
                                currentOrder[0] = currentOrder[0].withItemCompleted(idx, nextState);
                                checkedState[idx] = nextState;
                                PacketDistributor.sendToServer(new C2SKnyazManageOrderPayload(currentOrder[0], false));
                                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                                refreshChecklist[0].run();
                            } else {
                                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
                            }
                            return true;
                        }
                        return false;
                    });
                } else {
                    // Resource Item
                    Item item = orderItem.resolveItem();
                    ItemStack displayStack = orderItem.createDisplayStack();
                    boolean isDoneByKnyaz = orderItem.isCompleted();
                    boolean isChecked = isDoneByKnyaz || checkedState[idx];
                    int frameColor = isChecked ? BedrockTheme.TEXT_GREEN : BedrockTheme.TEXT_RED;

                    itemCard.surface((context, comp) -> {
                        int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
                        int bg = itemCard.isHovered() ? BedrockTheme.BG_HOVER : BedrockTheme.BG_CARD;
                        BedrockTheme.drawBeveledBox(context, x, y, w, h, bg, frameColor, frameColor);
                        BedrockTheme.drawOutline(context, x, y, w, h, frameColor);
                    });

                    // Checkbox badge: [✓] or [ ]
                    LabelComponent checkLbl = Components.label(Component.literal(isChecked ? "[✓]" : "[ ]"));
                    checkLbl.color(Color.ofRgb(frameColor));
                    itemCard.child(checkLbl);

                    // Item icon slot (22x22)
                    FlowLayout iconSlot = Containers.horizontalFlow(Sizing.fixed(22), Sizing.fixed(22));
                    iconSlot.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                    iconSlot.surface((context, comp) -> {
                        BedrockTheme.drawBeveledBox(context, comp.x(), comp.y(), comp.width(), comp.height(), BedrockTheme.BG_INPUT, frameColor, frameColor);
                    });
                    if (!displayStack.isEmpty()) {
                        ItemDisplayComponent itemComp = new ItemDisplayComponent(displayStack, 16);
                        Minecraft mc = Minecraft.getInstance();
                        Item.TooltipContext tc = mc.level != null ? Item.TooltipContext.of(mc.level) : Item.TooltipContext.EMPTY;
                        iconSlot.tooltip(displayStack.getTooltipLines(tc, mc.player, TooltipFlag.NORMAL));
                        iconSlot.child(itemComp);
                    }
                    itemCard.child(iconSlot);

                    // Item Name & Checklist quantity info
                    FlowLayout textCol = Containers.verticalFlow(Sizing.expand(100), Sizing.content());
                    textCol.gap(1);

                    Component itemName = !displayStack.isEmpty()
                            ? displayStack.getHoverName()
                            : Component.literal(orderItem.itemId());
                    LabelComponent nameLbl = Components.label(itemName);
                    nameLbl.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
                    nameLbl.shadow(true);
                    textCol.child(nameLbl);

                    int totalPlayerHasOfItem = (item != Items.AIR) ? playerInvCounts.getOrDefault(item, 0) : 0;
                    Component progressText = Component.translatable(
                            "oneshop.knyaz_orders.item_checklist_info",
                            orderItem.requiredCount(),
                            totalPlayerHasOfItem
                    );
                    LabelComponent progLbl = Components.label(progressText);
                    progLbl.color(Color.ofRgb(isChecked ? BedrockTheme.TEXT_GREEN : BedrockTheme.TEXT_MUTED));
                    textCol.child(progLbl);

                    itemCard.child(textCol);

                    // Right status badge
                    Component badgeComp;
                    if (isDoneByKnyaz) {
                        badgeComp = Component.translatable("oneshop.knyaz_orders.status_completed_knyaz");
                    } else {
                        badgeComp = Component.translatable(isChecked
                                ? "oneshop.knyaz_orders.status_ready"
                                : "oneshop.knyaz_orders.status_not_ready");
                    }
                    LabelComponent badgeLbl = Components.label(badgeComp);
                    badgeLbl.color(Color.ofRgb(frameColor));
                    itemCard.child(badgeLbl);

                    itemCard.mouseDown().subscribe((mx, my, button) -> {
                        if (button == 0) {
                            if (canManageOrders) {
                                boolean nextState = !orderItem.isCompleted();
                                currentOrder[0] = currentOrder[0].withItemCompleted(idx, nextState);
                                checkedState[idx] = nextState;
                                PacketDistributor.sendToServer(new C2SKnyazManageOrderPayload(currentOrder[0], false));
                                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                                refreshChecklist[0].run();
                            } else {
                                checkedState[idx] = !checkedState[idx];
                                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                                refreshChecklist[0].run();
                            }
                            return true;
                        }
                        return false;
                    });
                }

                itemsList.child(itemCard);
            }
        };

        refreshChecklist[0].run();

        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(125), itemsList);
        scroll.surface(BedrockTheme.INSET);
        scroll.padding(Insets.of(4));
        dialog.child(scroll);

        // Populate Action Buttons Row
        final boolean isResident = !ClientShopCache.isClientKnyaz();
        final boolean[] responded = {false};

        if (canManageOrders) {
            BedrockButton markBtn = new BedrockButton(
                    Component.translatable(currentOrder[0].isCompleted()
                            ? "oneshop.knyaz_orders.button.mark_uncompleted"
                            : "oneshop.knyaz_orders.button.mark_completed"),
                    Sizing.expand(60), Sizing.fixed(20),
                    () -> {
                        boolean nextDone = !currentOrder[0].isCompleted();
                        currentOrder[0] = currentOrder[0].withCompleted(nextDone);
                        for (int k = 0; k < checkedState.length; k++) {
                            checkedState[k] = nextDone;
                        }
                        PacketDistributor.sendToServer(new C2SKnyazManageOrderPayload(currentOrder[0], false));
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
                        refreshChecklist[0].run();
                    }
            );
            markBtn.textColor(currentOrder[0].isCompleted() ? BedrockTheme.TEXT_RED : BedrockTheme.TEXT_GREEN);
            actionRow.child(markBtn);

            BedrockButton cancelBtn = new BedrockButton(
                    Component.translatable("oneshop.gui.button.cancel"),
                    Sizing.expand(40),
                    Sizing.fixed(20),
                    onClose
            );
            actionRow.child(cancelBtn);
        } else {
            if (isResident) {
                BedrockButton respondBtn = new BedrockButton(
                        Component.translatable("oneshop.knyaz_orders.button.respond"),
                        Sizing.expand(50), Sizing.fixed(20),
                        () -> {
                            if (responded[0]) return;
                            responded[0] = true;
                            PacketDistributor.sendToServer(new C2SFulfillKnyazOrderPayload(currentOrder[0].id()));
                            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F));
                            onClose.run();
                        }
                );
                respondBtn.textColor(BedrockTheme.TEXT_GREEN);
                actionRow.child(respondBtn);
            }

            BedrockButton cancelBtn = new BedrockButton(
                    Component.translatable("oneshop.gui.button.cancel"),
                    Sizing.expand(isResident ? 50 : 100),
                    Sizing.fixed(20),
                    onClose
            );
            actionRow.child(cancelBtn);
        }

        dialog.child(actionRow);
        modalBackdrop.child(dialog);
        return modalBackdrop;
    }

    /**
     * Opens the Knyaz Order Creation / Editing Modal where the Knyaz can add resource items and text tasks,
     * configure their quantities, set the description, set monetary reward, and publish or delete the order.
     */
    public static FlowLayout createOrderEditorOverlay(ShopScreen screen, KnyazOrder existingOrder, Runnable onClose) {
        FlowLayout modalBackdrop = ShopScreen.createModalBackdrop();
        modalBackdrop.zIndex(500);

        FlowLayout dialog = Containers.verticalFlow(Sizing.fixed(330), Sizing.content());
        dialog.surface(BedrockTheme.PANEL);
        dialog.padding(Insets.of(8));
        dialog.gap(5);
        dialog.zIndex(501);

        // Header
        Component titleComp = existingOrder == null
                ? Component.translatable("oneshop.knyaz_orders.editor.new_title").withStyle(ChatFormatting.BOLD)
                : Component.translatable("oneshop.knyaz_orders.editor.edit_title", existingOrder.getDisplayTitle()).withStyle(ChatFormatting.BOLD);
        LabelComponent titleLabel = Components.label(titleComp);
        titleLabel.color(Color.ofRgb(BedrockTheme.TEXT_GOLD));
        dialog.child(titleLabel);

        // Order Title field
        BedrockTextBoxComponent titleBox = new BedrockTextBoxComponent(
                Sizing.fill(100),
                existingOrder != null ? existingOrder.title() : ""
        );
        titleBox.setMaxLength(128);
        dialog.child(screen.createLabeledInput("oneshop.knyaz_orders.editor.title_field", titleBox, "Например: Постройка крепости и моста"));

        // Description / Project Task field
        BedrockTextBoxComponent descriptionBox = new BedrockTextBoxComponent(
                Sizing.fill(100),
                existingOrder != null ? existingOrder.description() : ""
        );
        descriptionBox.setMaxLength(256);
        dialog.child(screen.createLabeledInput("oneshop.knyaz_orders.editor.description_field", descriptionBox, "Например: Построить мельницу у реки"));

        // Total Monetary Reward field
        BedrockTextBoxComponent rewardBox = new BedrockTextBoxComponent(
                Sizing.fill(100),
                existingOrder != null ? String.valueOf(existingOrder.totalReward()) : "100"
        );
        rewardBox.setMaxLength(20);
        FlowLayout rewardInputRow = screen.createLabeledInput("oneshop.knyaz_orders.editor.reward_field", rewardBox, "100");
        rewardInputRow.child(1, new TextureDisplayComponent(CURRENCY_ICON_TEXTURE, 8, 8, 0f, 0f, 8, 8, 8, 8));
        dialog.child(rewardInputRow);

        // Mutable drafts for items and text tasks
        final class DraftItem {
            String itemId;
            int requiredCount;
            int deliveredCount;
            int claimedCount;
            String taskText;
            boolean isTextTask;

            DraftItem(String itemId, int requiredCount, int deliveredCount, int claimedCount, String taskText, boolean isTextTask) {
                this.itemId = itemId != null ? itemId : "";
                this.requiredCount = requiredCount;
                this.deliveredCount = deliveredCount;
                this.claimedCount = claimedCount;
                this.taskText = taskText != null ? taskText : "";
                this.isTextTask = isTextTask;
            }
        }

        List<DraftItem> draftItems = new ArrayList<>();
        if (existingOrder != null && !existingOrder.items().isEmpty()) {
            for (KnyazOrderItem it : existingOrder.items()) {
                draftItems.add(new DraftItem(it.itemId(), it.requiredCount(), it.deliveredCount(), it.claimedCount(), it.taskText(), it.isTextTask()));
            }
        }

        // Toolbar to add tasks/items: [+ Задача] [+ Предмет] [+ Из руки]
        FlowLayout addBar = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        addBar.verticalAlignment(VerticalAlignment.CENTER);
        addBar.gap(3);

        LabelComponent itemsHeader = Components.label(Component.translatable("oneshop.knyaz_orders.editor.resources_label"));
        itemsHeader.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
        addBar.child(itemsHeader);

        addBar.child(Containers.horizontalFlow(Sizing.expand(100), Sizing.content()));

        FlowLayout rowsContainer = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        rowsContainer.gap(3);

        final Runnable[] rebuildRows = new Runnable[1];

        BedrockButton addTaskBtn = new BedrockButton(
                Component.translatable("oneshop.knyaz_orders.editor.add_text_task"),
                Sizing.content(4), Sizing.fixed(18),
                () -> {
                    draftItems.add(new DraftItem("", 1, 0, 0, "", true));
                    rebuildRows[0].run();
                }
        );
        addTaskBtn.textColor(BedrockTheme.TEXT_GOLD);
        addBar.child(addTaskBtn);

        BedrockButton addPickerBtn = new BedrockButton(
                Component.translatable("oneshop.knyaz_orders.editor.add_item"),
                Sizing.content(4), Sizing.fixed(18),
                () -> {
                    screen.openItemOnlyPickerModal(pickedIcon -> {
                        if (pickedIcon != null && pickedIcon.isItem() && !pickedIcon.item().isEmpty()) {
                            String regId = BuiltInRegistries.ITEM.getKey(pickedIcon.item().getItem()).toString();
                            draftItems.add(new DraftItem(regId, 64, 0, 0, "", false));
                            rebuildRows[0].run();
                        }
                    });
                }
        );
        addPickerBtn.textColor(BedrockTheme.TEXT_GREEN);
        addBar.child(addPickerBtn);

        BedrockButton addHandBtn = new BedrockButton(
                Component.translatable("oneshop.gui.lot.grab_hand"),
                Sizing.content(4), Sizing.fixed(18),
                () -> {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.player != null && !mc.player.getMainHandItem().isEmpty()) {
                        ItemStack hand = mc.player.getMainHandItem();
                        String regId = BuiltInRegistries.ITEM.getKey(hand.getItem()).toString();
                        draftItems.add(new DraftItem(regId, Math.max(1, hand.getCount()), 0, 0, "", false));
                        rebuildRows[0].run();
                    }
                }
        );
        addBar.child(addHandBtn);
        dialog.child(addBar);

        // Rebuild draft item rows
        rebuildRows[0] = () -> {
            rowsContainer.clearChildren();
            if (draftItems.isEmpty()) {
                LabelComponent emptyLbl = Components.label(Component.translatable("oneshop.knyaz_orders.editor.no_items"));
                emptyLbl.color(Color.ofRgb(BedrockTheme.TEXT_MUTED));
                rowsContainer.child(emptyLbl);
                return;
            }

            for (int i = 0; i < draftItems.size(); i++) {
                final int index = i;
                DraftItem draft = draftItems.get(index);

                FlowLayout row = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(24));
                row.verticalAlignment(VerticalAlignment.CENTER);
                row.gap(4);
                row.surface(BedrockTheme.CARD);
                row.padding(Insets.of(2, 2, 4, 4));

                if (draft.isTextTask) {
                    // Book icon
                    FlowLayout iconSlot = Containers.horizontalFlow(Sizing.fixed(20), Sizing.fixed(20));
                    iconSlot.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                    iconSlot.surface(BedrockTheme.INSET);
                    iconSlot.child(new ItemDisplayComponent(new ItemStack(Items.WRITABLE_BOOK), 16));
                    row.child(iconSlot);

                    // Task text input
                    BedrockTextBoxComponent taskBox = new BedrockTextBoxComponent(Sizing.fill(100), draft.taskText);
                    taskBox.setMaxLength(160);
                    taskBox.setBordered(false);
                    taskBox.setCanLoseFocus(true);
                    taskBox.onChanged().subscribe(val -> draft.taskText = val);

                    HoverableFlowLayout inputFrame = new HoverableFlowLayout(Sizing.expand(100), Sizing.fixed(18), FlowLayout.Algorithm.HORIZONTAL);
                    inputFrame.padding(Insets.of(0, 0, 4, 4));
                    inputFrame.verticalAlignment(VerticalAlignment.CENTER);
                    FocusableWidgetComponent wrapped = new FocusableWidgetComponent(taskBox);
                    wrapped.sizing(Sizing.fill(100), Sizing.fill(100));
                    wrapped.verticalAlignment(VerticalAlignment.CENTER);
                    Surface baseSurface = (context, comp) -> {
                        int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
                        BedrockTheme.drawBeveledBox(context, x, y, w, h, BedrockTheme.BG_INPUT, BedrockTheme.BORDER_DARK, BedrockTheme.BORDER_LIGHT);
                    };
                    inputFrame.surface(BedrockTheme.withHoverOutline(baseSurface, () -> inputFrame.isHovered() || wrapped.isWidgetFocused(), 0, BedrockTheme.OUTLINE_HOVER));
                    inputFrame.mouseDown().subscribe((mx, my, b) -> {
                        wrapped.focus();
                        return false;
                    });
                    wrapped.mouseDown().subscribe((mx, my, b) -> {
                        wrapped.focus();
                        return false;
                    });
                    inputFrame.child(wrapped);
                    row.child(inputFrame);

                    // Remove item button [✕]
                    BedrockButton removeBtn = new BedrockButton(Component.literal("✕"), Sizing.fixed(16), Sizing.fixed(16), () -> {
                        draftItems.remove(index);
                        rebuildRows[0].run();
                    });
                    removeBtn.textColor(BedrockTheme.TEXT_RED);
                    row.child(removeBtn);
                } else {
                    // Resource item icon
                    ResourceLocation itemLoc = ResourceLocation.tryParse(draft.itemId);
                    ItemStack displayStack = (itemLoc != null && BuiltInRegistries.ITEM.containsKey(itemLoc))
                            ? new ItemStack(BuiltInRegistries.ITEM.get(itemLoc))
                            : new ItemStack(Items.BARRIER);

                    HoverableFlowLayout iconSlot = new HoverableFlowLayout(Sizing.fixed(20), Sizing.fixed(20), FlowLayout.Algorithm.HORIZONTAL);
                    iconSlot.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
                    iconSlot.surface(BedrockTheme.INSET);
                    iconSlot.cursorStyle(CursorStyle.HAND);
                    iconSlot.tooltip(Component.translatable("oneshop.gui.lot.change_icon"));
                    iconSlot.child(new ItemDisplayComponent(displayStack, 16));
                    iconSlot.mouseDown().subscribe((mx, my, button) -> {
                        if (button == 0) {
                            screen.openItemOnlyPickerModal(pickedIcon -> {
                                if (pickedIcon != null && pickedIcon.isItem() && !pickedIcon.item().isEmpty()) {
                                    draft.itemId = BuiltInRegistries.ITEM.getKey(pickedIcon.item().getItem()).toString();
                                    rebuildRows[0].run();
                                }
                            });
                            return true;
                        }
                        return false;
                    });
                    row.child(iconSlot);

                    // Item Name
                    LabelComponent nameLbl = Components.label(displayStack.getHoverName());
                    nameLbl.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
                    nameLbl.maxWidth(80);
                    row.child(nameLbl);

                    row.child(Containers.horizontalFlow(Sizing.expand(100), Sizing.content()));

                    // Required Count Input
                    BedrockTextBoxComponent qtyBox = new BedrockTextBoxComponent(Sizing.fixed(45), String.valueOf(draft.requiredCount));
                    qtyBox.setMaxLength(6);
                    qtyBox.onChanged().subscribe(val -> {
                        String digits = val.replaceAll("[^0-9]", "");
                        if (!digits.isEmpty()) {
                            try {
                                draft.requiredCount = Math.max(1, Integer.parseInt(digits));
                            } catch (NumberFormatException ignored) {}
                        }
                    });
                    row.child(screen.createNumericInputFrame(qtyBox, null, 45));

                    // Remove item button [✕]
                    BedrockButton removeBtn = new BedrockButton(Component.literal("✕"), Sizing.fixed(16), Sizing.fixed(16), () -> {
                        draftItems.remove(index);
                        rebuildRows[0].run();
                    });
                    removeBtn.textColor(BedrockTheme.TEXT_RED);
                    row.child(removeBtn);
                }

                rowsContainer.child(row);
            }
        };

        rebuildRows[0].run();

        ScrollContainer<FlowLayout> rowsScroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(110), rowsContainer);
        rowsScroll.surface(BedrockTheme.INSET);
        rowsScroll.padding(Insets.of(4));
        dialog.child(rowsScroll);

        // Status Label for validation errors
        LabelComponent statusLabel = Components.label(Component.empty());
        statusLabel.color(Color.ofRgb(BedrockTheme.TEXT_RED));
        dialog.child(statusLabel);

        // Action Buttons Row: [Опубликовать] [Удалить] [Отмена]
        FlowLayout actionRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        actionRow.gap(4);

        BedrockButton publishBtn = new BedrockButton(
                Component.translatable("oneshop.knyaz_orders.editor.publish"),
                Sizing.expand(existingOrder != null ? 38 : 50),
                Sizing.fixed(20),
                () -> {
                    long reward;
                    try {
                        reward = Long.parseLong(rewardBox.getValue().trim());
                        if (reward <= 0) throw new NumberFormatException();
                    } catch (NumberFormatException ex) {
                        statusLabel.text(Component.translatable("oneshop.knyaz_orders.error.invalid_reward"));
                        return;
                    }

                    List<KnyazOrderItem> builtItems = new ArrayList<>(draftItems.size());
                    for (DraftItem d : draftItems) {
                        if (d.isTextTask) {
                            if (d.taskText != null && !d.taskText.trim().isEmpty()) {
                                builtItems.add(new KnyazOrderItem("", 1, d.deliveredCount, 0, d.taskText.trim()));
                            }
                        } else {
                            if (d.itemId != null && !d.itemId.isBlank()) {
                                builtItems.add(new KnyazOrderItem(
                                        d.itemId,
                                        Math.max(1, d.requiredCount),
                                        Math.min(d.requiredCount, d.deliveredCount),
                                        Math.min(d.requiredCount, d.claimedCount),
                                        ""
                                ));
                            }
                        }
                    }

                    String desc = descriptionBox.getValue().trim();
                    if (builtItems.isEmpty() && !desc.isEmpty()) {
                        builtItems.add(KnyazOrderItem.textTask(desc, existingOrder != null && existingOrder.isCompleted()));
                    }

                    if (builtItems.isEmpty()) {
                        statusLabel.text(Component.translatable("oneshop.knyaz_orders.error.empty_items"));
                        return;
                    }

                    String orderId = existingOrder != null ? existingOrder.id() : UUID.randomUUID().toString();
                    String title = titleBox.getValue().trim();
                    String author = existingOrder != null ? existingOrder.createdBy() : "";
                    long paid = existingOrder != null ? Math.min(reward, existingOrder.paidReward()) : 0L;
                    String createdAt = existingOrder != null ? existingOrder.createdAt() : "";
                    List<String> respondents = existingOrder != null ? existingOrder.respondents() : List.of();
                    boolean orderCompleted = existingOrder != null && existingOrder.completed();

                    KnyazOrder newOrder = new KnyazOrder(orderId, title, author, builtItems, reward, paid, createdAt, respondents, desc, orderCompleted);
                    PacketDistributor.sendToServer(new C2SKnyazManageOrderPayload(newOrder, false));
                    onClose.run();
                }
        );
        publishBtn.textColor(BedrockTheme.TEXT_GREEN);
        actionRow.child(publishBtn);

        if (existingOrder != null) {
            BedrockButton deleteBtn = new BedrockButton(
                    Component.translatable("oneshop.gui.button.delete"),
                    Sizing.expand(31),
                    Sizing.fixed(20),
                    () -> {
                        PacketDistributor.sendToServer(new C2SKnyazManageOrderPayload(existingOrder, true));
                        onClose.run();
                    }
            );
            deleteBtn.textColor(BedrockTheme.TEXT_RED);
            actionRow.child(deleteBtn);
        }

        BedrockButton cancelBtn = new BedrockButton(
                Component.translatable("oneshop.gui.button.cancel"),
                Sizing.expand(existingOrder != null ? 31 : 50),
                Sizing.fixed(20),
                onClose
        );
        actionRow.child(cancelBtn);

        dialog.child(actionRow);
        modalBackdrop.child(dialog);
        return modalBackdrop;
    }

    public static Map<Item, Integer> countPlayerInventoryByItem() {
        Map<Item, Integer> map = new HashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return map;
        for (ItemStack slot : mc.player.getInventory().items) {
            if (!slot.isEmpty()) {
                map.merge(slot.getItem(), slot.getCount(), Integer::sum);
            }
        }
        return map;
    }

    /**
     * Counts how many item or task requirements in the order are satisfied/completed.
     */
    public static int countReadyChecklistItems(KnyazOrder order, Map<Item, Integer> playerInv) {
        if (order == null || order.items().isEmpty()) return 0;
        if (order.isCompleted()) return order.items().size();
        Map<Item, Integer> copy = new HashMap<>(playerInv);
        int count = 0;
        for (KnyazOrderItem item : order.items()) {
            if (item.isCompleted()) {
                count++;
            } else if (!item.isTextTask()) {
                Item resolved = item.resolveItem();
                int avail = (resolved != Items.AIR) ? copy.getOrDefault(resolved, 0) : 0;
                if (avail >= item.requiredCount()) {
                    count++;
                    if (resolved != Items.AIR) {
                        copy.put(resolved, avail - item.requiredCount());
                    }
                }
            }
        }
        return count;
    }

    public static boolean isOrderAllReadyForPlayer(KnyazOrder order, Map<Item, Integer> playerInv) {
        if (order == null || order.items().isEmpty()) return false;
        if (order.isCompleted()) return true;
        Map<Item, Integer> copy = new HashMap<>(playerInv);
        for (KnyazOrderItem item : order.items()) {
            if (item.isCompleted()) continue;
            if (item.isTextTask()) return false;
            Item resolved = item.resolveItem();
            int avail = (resolved != Items.AIR) ? copy.getOrDefault(resolved, 0) : 0;
            if (avail < item.requiredCount()) {
                return false;
            }
            if (resolved != Items.AIR) {
                copy.put(resolved, avail - item.requiredCount());
            }
        }
        return true;
    }

    public static boolean canPlayerContributeAny(KnyazOrder order, Map<Item, Integer> playerInv) {
        if (order == null || order.isCompleted()) return false;
        for (KnyazOrderItem item : order.items()) {
            if (!item.isCompleted() && !item.isTextTask()) {
                Item resolved = item.resolveItem();
                if (resolved != Items.AIR && playerInv.getOrDefault(resolved, 0) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String formatLong(long val) {
        return String.format(Locale.ROOT, "%,d", val).replace(',', ' ');
    }
}
