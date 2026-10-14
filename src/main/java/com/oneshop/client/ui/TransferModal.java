package com.oneshop.client.ui;

import com.oneshop.item.SprotaItem;
import com.oneshop.network.C2STransferMoneyPayload;
import com.oneshop.client.ClientBankCache;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.component.TextBoxComponent;
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
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Bedrock-style modal dialog for player-to-player money ($PR) transfers.
 * Stage 1: Online player search & selection list (excluding self).
 * Stage 2: Transfer amount input with stepper, presets (+10, +50, +100, "Все"), and confirmation.
 */
public final class TransferModal {
    private TransferModal() {}

    public static FlowLayout createOverlay(ShopScreen screen, Runnable onClose) {
        FlowLayout modalBackdrop = ShopScreen.createModalBackdrop();
        modalBackdrop.zIndex(500);

        FlowLayout dialog = Containers.verticalFlow(Sizing.fixed(250), Sizing.content());
        dialog.surface(BedrockTheme.PANEL);
        dialog.padding(Insets.of(8));
        dialog.gap(6);
        dialog.zIndex(501);

        LabelComponent title = Components.label(Component.translatable("oneshop.gui.transfer.title").withStyle(ChatFormatting.BOLD));
        title.color(Color.ofRgb(BedrockTheme.TEXT_GOLD));
        dialog.child(title);

        FlowLayout stageContent = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        stageContent.gap(6);
        dialog.child(stageContent);

        // Build Stage 1 initially
        buildStage1(screen, dialog, stageContent, onClose);

        modalBackdrop.child(dialog);
        return modalBackdrop;
    }

    private static void buildStage1(ShopScreen screen, FlowLayout dialog, FlowLayout stageContent, Runnable onClose) {
        stageContent.clearChildren();

        LabelComponent prompt = Components.label(Component.translatable("oneshop.gui.transfer.select_player"));
        prompt.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
        stageContent.child(prompt);

        // Search container
        HoverableFlowLayout searchFrame = new HoverableFlowLayout(Sizing.fill(100), Sizing.fixed(20), FlowLayout.Algorithm.HORIZONTAL);
        searchFrame.hoverCondition(() -> !screen.isModalOpen() || screen.isComponentInModal(searchFrame));
        searchFrame.padding(Insets.none());
        searchFrame.verticalAlignment(VerticalAlignment.CENTER);

        LabelComponent searchIcon = Components.label(Component.literal("🔍"));
        searchIcon.margins(Insets.of(0, 0, 4, 4));
        searchIcon.color(Color.ofRgb(BedrockTheme.TEXT_MUTED));

        BedrockTextBoxComponent searchBox = new BedrockTextBoxComponent(Sizing.fill(100), "");
        searchBox.setHint(Component.translatable("oneshop.gui.transfer.search_hint"));
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

        searchFrame.mouseDown().subscribe((mouseX, mouseY, button) -> {
            wrappedSearch.focus();
            return false;
        });
        wrappedSearch.mouseDown().subscribe((mouseX, mouseY, button) -> {
            wrappedSearch.focus();
            return false;
        });

        searchFrame.child(searchIcon);
        searchFrame.child(wrappedSearch);
        stageContent.child(searchFrame);

        // Player list container
        FlowLayout playersList = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        playersList.gap(2);

        ScrollContainer<FlowLayout> scroll = Containers.verticalScroll(Sizing.fill(100), Sizing.fixed(120), playersList);
        scroll.surface(BedrockTheme.INSET);
        scroll.padding(Insets.of(3));
        stageContent.child(scroll);

        // Populate players
        Runnable updatePlayers = () -> {
            playersList.clearChildren();
            String query = searchBox.getValue().trim().toLowerCase(Locale.ROOT);

            Minecraft mc = Minecraft.getInstance();
            UUID selfUuid = mc.player != null ? mc.player.getUUID() : null;
            Collection<PlayerInfo> allOnline = mc.getConnection() != null ? mc.getConnection().getOnlinePlayers() : List.of();

            List<PlayerInfo> filtered = allOnline.stream()
                    .filter(p -> selfUuid == null || !p.getProfile().getId().equals(selfUuid))
                    .filter(p -> query.isEmpty() || p.getProfile().getName().toLowerCase(Locale.ROOT).contains(query))
                    .sorted(Comparator.comparing(p -> p.getProfile().getName().toLowerCase(Locale.ROOT)))
                    .toList();

            if (filtered.isEmpty()) {
                Component msg = Component.translatable(allOnline.size() <= 1
                        ? "oneshop.gui.transfer.no_players_online"
                        : "oneshop.gui.transfer.no_matching_players");
                LabelComponent emptyLabel = Components.label(msg);
                emptyLabel.color(Color.ofRgb(BedrockTheme.TEXT_MUTED));
                emptyLabel.margins(Insets.of(4));
                playersList.child(emptyLabel);
                return;
            }

            for (PlayerInfo playerInfo : filtered) {
                HoverableFlowLayout playerRow = new HoverableFlowLayout(Sizing.fill(100), Sizing.fixed(22), FlowLayout.Algorithm.HORIZONTAL);
                playerRow.verticalAlignment(VerticalAlignment.CENTER);
                playerRow.padding(Insets.of(2, 2, 4, 4));
                playerRow.gap(6);
                playerRow.cursorStyle(CursorStyle.HAND);
                playerRow.hoverCondition(() -> !screen.isModalOpen() || screen.isComponentInModal(playerRow));

                Surface rowSurface = (context, comp) -> {
                    int x = comp.x(), y = comp.y(), w = comp.width(), h = comp.height();
                    int bg = playerRow.isHovered() ? BedrockTheme.BG_HOVER : BedrockTheme.BG_CARD;
                    BedrockTheme.drawBeveledBox(context, x, y, w, h, bg, BedrockTheme.BORDER_LIGHT, BedrockTheme.BORDER_DARK);
                };
                playerRow.surface(BedrockTheme.withHoverOutline(rowSurface, 0, BedrockTheme.OUTLINE_HOVER));

                // Player avatar (16x16 head render)
                var avatar = new TextureDisplayComponent(playerInfo.getSkin().texture(), 16, 16, 8f, 8f, 8, 8, 64, 64);
                playerRow.child(avatar);

                // Player Name
                LabelComponent nameLabel = Components.label(Component.literal(playerInfo.getProfile().getName()));
                nameLabel.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
                nameLabel.shadow(true);
                playerRow.child(nameLabel);

                playerRow.mouseDown().subscribe((mouseX, mouseY, button) -> {
                    if (button == 0) {
                        buildStage2(screen, dialog, stageContent, playerInfo, onClose);
                        return true;
                    }
                    return false;
                });

                playersList.child(playerRow);
            }
        };

        searchBox.onChanged().subscribe(s -> updatePlayers.run());
        updatePlayers.run();

        // Bottom Cancel button
        BedrockButton cancelBtn = new BedrockButton(Component.translatable("oneshop.gui.button.cancel"), Sizing.fill(100), Sizing.fixed(20), onClose);
        stageContent.child(cancelBtn);
    }

    private static void buildStage2(ShopScreen screen, FlowLayout dialog, FlowLayout stageContent, PlayerInfo recipient, Runnable onClose) {
        stageContent.clearChildren();

        double currentBalance = ClientBankCache.getBalance();

        // Recipient header row: [<-] [avatar] [Recipient: Name]
        FlowLayout recipientRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        recipientRow.verticalAlignment(VerticalAlignment.CENTER);
        recipientRow.gap(4);

        BedrockButton backBtn = new BedrockButton(Component.literal("←"), Sizing.fixed(20), Sizing.fixed(18), () -> {
            buildStage1(screen, dialog, stageContent, onClose);
        });
        backBtn.tooltip(Component.translatable("oneshop.gui.transfer.change_player"));
        recipientRow.child(backBtn);

        var avatar = new TextureDisplayComponent(recipient.getSkin().texture(), 16, 16, 8f, 8f, 8, 8, 64, 64);
        recipientRow.child(avatar);

        LabelComponent recipientLabel = Components.label(Component.translatable("oneshop.gui.transfer.recipient", recipient.getProfile().getName()).withStyle(ChatFormatting.BOLD));
        recipientLabel.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
        recipientRow.child(recipientLabel);

        stageContent.child(recipientRow);

        // Your balance info
        LabelComponent balanceLabel = Components.label(Component.translatable("oneshop.gui.transfer.your_balance", screen.formatBalanceAmount() + " " + SprotaItem.CURRENCY_SYMBOL));
        balanceLabel.color(Color.ofRgb(BedrockTheme.TEXT_MUTED));
        stageContent.child(balanceLabel);

        // Transfer Amount Label
        LabelComponent amtPrompt = Components.label(Component.translatable("oneshop.gui.transfer.amount"));
        amtPrompt.color(Color.ofRgb(BedrockTheme.TEXT_WHITE));
        stageContent.child(amtPrompt);

        final double[] amount = {Math.min(10.0, currentBalance > 0 ? currentBalance : 0.0)};

        // Input row: [-] [ amount box ] [+]
        FlowLayout inputRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        inputRow.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        inputRow.gap(6);

        BedrockTextBoxComponent amtBox = new BedrockTextBoxComponent(Sizing.fill(100), amount[0] == (long) amount[0] ? String.valueOf((long) amount[0]) : String.format(Locale.ROOT, "%.2f", amount[0]));
        HoverableFlowLayout inputFrame = screen.createNumericInputFrame(amtBox, null, 80);

        LabelComponent statusLabel = Components.label(Component.literal(""));

        BedrockButton confirmBtn = new BedrockButton(Component.translatable("oneshop.gui.button.confirm"), Sizing.expand(50), Sizing.fixed(20), () -> {
            if (amount[0] > 0 && amount[0] <= currentBalance) {
                PacketDistributor.sendToServer(new C2STransferMoneyPayload(recipient.getProfile().getId(), amount[0]));
            }
            onClose.run();
        });

        boolean[] updatingFromButtons = {false};

        java.util.function.Consumer<Boolean> updateState = (syncText) -> {
            if (syncText) {
                updatingFromButtons[0] = true;
                String displayVal = (amount[0] == (long) amount[0]) ? String.valueOf((long) amount[0]) : String.format(Locale.ROOT, "%.2f", amount[0]);
                amtBox.setValue(displayVal);
                updatingFromButtons[0] = false;
            }

            if (amount[0] <= 0) {
                statusLabel.text(Component.translatable("oneshop.gui.modal.status.invalid_amount").withStyle(ChatFormatting.RED));
                confirmBtn.active(false);
            } else if (amount[0] > currentBalance) {
                statusLabel.text(Component.translatable("oneshop.gui.modal.status.insufficient_funds").withStyle(ChatFormatting.RED));
                confirmBtn.active(false);
            } else {
                statusLabel.text(Component.translatable("oneshop.gui.transfer.ready", String.format(Locale.ROOT, "%.2f", amount[0]), SprotaItem.CURRENCY_SYMBOL).withStyle(ChatFormatting.GREEN));
                confirmBtn.active(true);
            }
        };

        amtBox.onChanged().subscribe(val -> {
            if (updatingFromButtons[0]) return;
            String sanitized = val.replaceAll("[^0-9.]", "");
            int firstDot = sanitized.indexOf('.');
            if (firstDot != -1) {
                sanitized = sanitized.substring(0, firstDot + 1) + sanitized.substring(firstDot + 1).replace(".", "");
            }
            if (!sanitized.equals(val)) {
                updatingFromButtons[0] = true;
                amtBox.setValue(sanitized);
                updatingFromButtons[0] = false;
            }
            if (!sanitized.isEmpty() && !sanitized.equals(".")) {
                try {
                    double parsed = Double.parseDouble(sanitized);
                    amount[0] = Math.max(0, parsed);
                } catch (NumberFormatException ignored) {}
            } else {
                amount[0] = 0;
            }
            updateState.accept(false);
        });

        BedrockButton minusBtn = new BedrockButton(Component.literal("-"), Sizing.fixed(24), Sizing.fixed(18), () -> {
            if (amount[0] > 1) {
                amount[0] = Math.max(0, amount[0] - 1);
                updateState.accept(true);
            }
        });
        inputRow.child(minusBtn);
        inputRow.child(inputFrame);

        BedrockButton plusBtn = new BedrockButton(Component.literal("+"), Sizing.fixed(24), Sizing.fixed(18), () -> {
            if (amount[0] < currentBalance) {
                amount[0] = Math.min(amount[0] + 1, currentBalance);
                updateState.accept(true);
            }
        });
        inputRow.child(plusBtn);
        stageContent.child(inputRow);

        // Presets row: [+10], [+50], [+100], [Все]
        FlowLayout presetsRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(18));
        presetsRow.gap(2);

        int[] increments = {10, 50, 100};
        for (int inc : increments) {
            BedrockButton incBtn = new BedrockButton(Component.literal("+" + inc), Sizing.expand(25), Sizing.fixed(18), () -> {
                amount[0] = Math.min(amount[0] + inc, currentBalance);
                updateState.accept(true);
            });
            presetsRow.child(incBtn);
        }

        BedrockButton allBtn = new BedrockButton(Component.translatable("oneshop.gui.modal.quick_all"), Sizing.expand(25), Sizing.fixed(18), () -> {
            amount[0] = currentBalance;
            updateState.accept(true);
        });
        presetsRow.child(allBtn);
        stageContent.child(presetsRow);

        // Status Label
        stageContent.child(statusLabel);
        updateState.accept(true);

        // Action buttons: [Confirm] [Cancel]
        FlowLayout actionRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(20));
        actionRow.gap(4);
        actionRow.child(confirmBtn);

        BedrockButton cancelBtn = new BedrockButton(Component.translatable("oneshop.gui.button.cancel"), Sizing.expand(50), Sizing.fixed(20), onClose);
        actionRow.child(cancelBtn);
        stageContent.child(actionRow);
    }
}
