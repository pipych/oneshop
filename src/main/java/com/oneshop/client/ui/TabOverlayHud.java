package com.oneshop.client.ui;

import com.oneshop.OneShop;
import com.oneshop.client.ClientBankCache;
import com.oneshop.client.ClientTabCache;
import com.oneshop.network.C2SRequestTabDataPayload;
import io.wispforest.owo.ui.component.Components;
import io.wispforest.owo.ui.component.LabelComponent;
import io.wispforest.owo.ui.container.Containers;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.Color;
import io.wispforest.owo.ui.core.HorizontalAlignment;
import io.wispforest.owo.ui.core.Insets;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import io.wispforest.owo.ui.core.Sizing;
import io.wispforest.owo.ui.core.Surface;
import io.wispforest.owo.ui.core.VerticalAlignment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Custom Tab Menu overlay replacing VanillaGuiLayers.TAB_LIST with owo-lib Bedrock styling,
 * displaying economy & state metrics (Shpora, State Brands, Tax Rate, Tax Debt)
 * and active character names with player ping.
 */
public final class TabOverlayHud {
    public static final ResourceLocation CURRENCY_ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "textures/gui/currency_icon.png");
    public static final ResourceLocation STATE_BRAND_ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "textures/gui/state_brand.png");
    public static final ResourceLocation TAX_RATE_ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "textures/gui/tax_rate_percent.png");
    public static final ResourceLocation TAX_DEBT_ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "textures/gui/tax_debt_percent.png");
    public static final ResourceLocation CROWN_ICON_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "textures/gui/monarch_crown.png");

    private static final long SYNC_INTERVAL_MS = 2000L; // ~40 client ticks (2 seconds)

    private static OwoUIAdapter<FlowLayout> adapter = null;
    private static boolean wasTabDown = false;
    private static long lastSyncRequestMs = 0L;
    private static TabSnapshot lastSnapshot = null;

    private TabOverlayHud() {}

    private record PlayerEntry(UUID uuid, String characterName, int latency, ResourceLocation skinTexture, boolean isKnyaz) {}

    private record TabSnapshot(
            double balance,
            int stateBrands,
            long taxDebt,
            int taxRate,
            List<PlayerEntry> players
    ) {}

    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!VanillaGuiLayers.TAB_LIST.equals(event.getName())) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null || mc.options.hideGui || !mc.options.keyPlayerList.isDown() || mc.player == null || mc.getConnection() == null) {
            wasTabDown = false;
            return;
        }

        // Cancel vanilla TAB overlay rendering
        event.setCanceled(true);
        mc.gui.getTabList().setVisible(false);

        long now = System.currentTimeMillis();
        if (!wasTabDown || (now - lastSyncRequestMs >= SYNC_INTERVAL_MS)) {
            wasTabDown = true;
            lastSyncRequestMs = now;
            try {
                PacketDistributor.sendToServer(new C2SRequestTabDataPayload());
            } catch (Throwable ignored) {}
            ClientTabCache.syncFromLocalOneManagerIfPresent(mc.player.getUUID());
        }

        int guiWidth = event.getGuiGraphics().guiWidth();
        int guiHeight = event.getGuiGraphics().guiHeight();

        if (adapter == null) {
            adapter = OwoUIAdapter.createWithoutScreen(0, 0, guiWidth, guiHeight, Containers::verticalFlow);
            adapter.rootComponent.surface(Surface.BLANK);
            adapter.rootComponent.alignment(HorizontalAlignment.CENTER, VerticalAlignment.TOP);
        } else if (adapter.width() != guiWidth || adapter.height() != guiHeight) {
            adapter.moveAndResize(0, 0, guiWidth, guiHeight);
        }

        TabSnapshot currentSnapshot = captureSnapshot(mc);
        if (lastSnapshot == null || !lastSnapshot.equals(currentSnapshot)) {
            lastSnapshot = currentSnapshot;
            rebuildUi(currentSnapshot);
            adapter.inflateAndMount();
        }

        float partialTick = event.getPartialTick().getGameTimeDeltaPartialTick(true);
        adapter.render(event.getGuiGraphics(), -1000, -1000, partialTick);
    }

    private static TabSnapshot captureSnapshot(Minecraft mc) {
        double balance = ClientBankCache.getBalance();
        int stateBrands = ClientTabCache.getStateBrands();
        long taxDebt = ClientTabCache.getTaxDebt();
        int taxRate = ClientTabCache.getTaxRate();

        Collection<PlayerInfo> playerInfos = mc.getConnection() != null
                ? mc.getConnection().getListedOnlinePlayers()
                : List.of();
        if (playerInfos.isEmpty() && mc.getConnection() != null) {
            playerInfos = mc.getConnection().getOnlinePlayers();
        }

        List<PlayerEntry> players = new ArrayList<>();
        for (PlayerInfo info : playerInfos) {
            UUID uuid = info.getProfile().getId();
            String rawName = info.getProfile().getName();
            String charName = ClientTabCache.resolveCharacterName(uuid, rawName);
            ResourceLocation skin = info.getSkin().texture();
            int latency = Math.max(0, info.getLatency());
            boolean isKnyaz = ClientTabCache.isPlayerKnyaz(uuid, rawName, charName);
            players.add(new PlayerEntry(uuid, charName, latency, skin, isKnyaz));
        }

        players.sort(Comparator.comparing(p -> p.characterName().toLowerCase(Locale.ROOT)));

        return new TabSnapshot(balance, stateBrands, taxDebt, taxRate, players);
    }

    private static void rebuildUi(TabSnapshot snapshot) {
        if (adapter == null) return;
        FlowLayout root = adapter.rootComponent;
        root.clearChildren();

        FlowLayout mainPanel = Containers.verticalFlow(Sizing.fixed(236), Sizing.content());
        mainPanel.margins(Insets.top(8));
        mainPanel.padding(Insets.of(8));
        mainPanel.gap(5);
        mainPanel.surface(BedrockTheme.GRAY_TOOLTIP_SURFACE);

        // Section 1: 4 Economy / State Values in a single compact row
        FlowLayout metricsRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.content());
        metricsRow.gap(3);

        // 1. Shpora (10x10 cropped coin icon + balance number)
        String formattedBalance = (snapshot.balance() == (long) snapshot.balance())
                ? String.format(Locale.ROOT, "%,d", (long) snapshot.balance())
                : String.format(Locale.ROOT, "%,.2f", snapshot.balance());
        metricsRow.child(buildMetricBadge(
                new TextureDisplayComponent(CURRENCY_ICON_TEXTURE, 10, 10, 0f, 0f, 8, 8, 8, 8),
                formattedBalance,
                BedrockTheme.TEXT_GREEN
        ));

        // 2. State Brands (10x10 cropped brand icon + count number only)
        metricsRow.child(buildMetricBadge(
                new TextureDisplayComponent(STATE_BRAND_ICON_TEXTURE, 10, 10, 1f, 1f, 14, 14, 16, 16),
                String.valueOf(snapshot.stateBrands()),
                BedrockTheme.TEXT_GOLD
        ));

        // 3. Tax Rate (10x10 yellow percent icon + tax rate number)
        metricsRow.child(buildMetricBadge(
                new TextureDisplayComponent(TAX_RATE_ICON_TEXTURE, 10, 10, 0f, 0f, 8, 8, 8, 8),
                String.valueOf(snapshot.taxRate()),
                BedrockTheme.TEXT_GOLD
        ));

        // 4. Tax Debt (10x10 red percent icon + tax debt number)
        int debtColor = snapshot.taxDebt() > 0 ? BedrockTheme.TEXT_RED : BedrockTheme.TEXT_GREEN;
        metricsRow.child(buildMetricBadge(
                new TextureDisplayComponent(TAX_DEBT_ICON_TEXTURE, 10, 10, 0f, 0f, 8, 8, 8, 8),
                String.valueOf(snapshot.taxDebt()),
                debtColor
        ));

        mainPanel.child(metricsRow);

        // Section 2: Players List Header
        FlowLayout headerRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(12));
        headerRow.padding(Insets.horizontal(2));
        headerRow.verticalAlignment(VerticalAlignment.CENTER);

        LabelComponent playersHeader = Components.label(Component.translatable("oneshop.tab.players", snapshot.players().size()));
        playersHeader.color(Color.ofRgb(BedrockTheme.TEXT_MUTED));
        headerRow.child(playersHeader);

        headerRow.child(Containers.horizontalFlow(Sizing.expand(100), Sizing.fixed(1)));

        LabelComponent pingHeader = Components.label(Component.translatable("oneshop.tab.ping"));
        pingHeader.color(Color.ofRgb(BedrockTheme.TEXT_MUTED));
        headerRow.child(pingHeader);

        mainPanel.child(headerRow);

        // Section 2: Players List Rows
        FlowLayout playersContainer = Containers.verticalFlow(Sizing.fill(100), Sizing.content());
        playersContainer.surface(BedrockTheme.INSET_TRANSLUCENT);
        playersContainer.padding(Insets.of(3));
        playersContainer.gap(2);

        for (PlayerEntry player : snapshot.players()) {
            FlowLayout playerRow = Containers.horizontalFlow(Sizing.fill(100), Sizing.fixed(16));
            playerRow.surface(BedrockTheme.CARD_TRANSLUCENT);
            playerRow.padding(Insets.of(1, 1, 4, 4));
            playerRow.verticalAlignment(VerticalAlignment.CENTER);
            playerRow.gap(5);

            // Avatar
            playerRow.child(new TextureDisplayComponent(player.skinTexture(), 10, 10, 8f, 8f, 8, 8, 64, 64));

            if (player.isKnyaz()) {
                playerRow.child(new TextureDisplayComponent(CROWN_ICON_TEXTURE, 10, 10, 3f, 3f, 10, 10, 16, 16));
            }

            // Character Name (orange for Knyaz, white for others)
            LabelComponent nameLbl = Components.label(Component.literal(player.characterName()));
            nameLbl.color(Color.ofRgb(player.isKnyaz() ? BedrockTheme.TEXT_ORANGE : BedrockTheme.TEXT_WHITE));
            nameLbl.shadow(true);
            playerRow.child(nameLbl);

            // Spacer
            playerRow.child(Containers.horizontalFlow(Sizing.expand(100), Sizing.fixed(1)));

            // Latency / Ping
            int pingColor = player.latency() < 100
                    ? BedrockTheme.TEXT_GREEN
                    : (player.latency() < 200 ? BedrockTheme.TEXT_GOLD : BedrockTheme.TEXT_RED);
            LabelComponent pingLbl = Components.label(Component.translatable("oneshop.tab.ping_ms", player.latency()));
            pingLbl.color(Color.ofRgb(pingColor));
            pingLbl.shadow(true);
            playerRow.child(pingLbl);

            playersContainer.child(playerRow);
        }

        mainPanel.child(playersContainer);
        root.child(mainPanel);
    }

    private static FlowLayout buildMetricBadge(io.wispforest.owo.ui.core.Component icon, String valueText, int valueColor) {
        FlowLayout badge = Containers.horizontalFlow(Sizing.expand(25), Sizing.fixed(16));
        badge.surface(BedrockTheme.CARD_TRANSLUCENT);
        badge.padding(Insets.of(2, 2, 3, 3));
        badge.alignment(HorizontalAlignment.CENTER, VerticalAlignment.CENTER);
        badge.gap(3);

        badge.child(icon);

        LabelComponent valLbl = Components.label(Component.literal(valueText));
        valLbl.color(Color.ofRgb(valueColor));
        valLbl.shadow(true);
        badge.child(valLbl);

        return badge;
    }

    public static void reset() {
        wasTabDown = false;
        lastSyncRequestMs = 0L;
        lastSnapshot = null;
        if (adapter != null) {
            try {
                adapter.dispose();
            } catch (Throwable ignored) {}
            adapter = null;
        }
    }
}
