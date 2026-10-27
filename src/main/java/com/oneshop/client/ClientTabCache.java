package com.oneshop.client;

import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Client-side cache for Tab Overlay HUD metrics:
 * player state brands, tax debt, tax rate, and player character names.
 */
public final class ClientTabCache {
    private static int stateBrands = 0;
    private static long taxDebt = 0L;
    private static int taxRate = 10;
    private static Map<UUID, String> characterNames = Map.of();
    private static @Nullable UUID knyazUuid = null;
    private static String knyazName = "";

    private ClientTabCache() {}

    public static synchronized void update(int brands, long debt, int rate, @Nullable Map<UUID, String> names) {
        update(brands, debt, rate, names, knyazUuid, knyazName);
    }

    public static synchronized void update(int brands, long debt, int rate, @Nullable Map<UUID, String> names, @Nullable UUID knyazId, @Nullable String knyaz) {
        stateBrands = Math.max(0, brands);
        taxDebt = Math.max(0L, debt);
        taxRate = Math.max(0, rate);
        if (names != null) {
            Map<UUID, String> merged = new HashMap<>(characterNames);
            merged.putAll(names);
            characterNames = Map.copyOf(merged);
        }
        knyazUuid = knyazId;
        knyazName = knyaz != null ? knyaz : "";
    }

    public static synchronized void clear() {
        stateBrands = 0;
        taxDebt = 0L;
        taxRate = 10;
        characterNames = Map.of();
        knyazUuid = null;
        knyazName = "";
    }

    public static synchronized int getStateBrands() {
        return stateBrands;
    }

    public static synchronized long getTaxDebt() {
        return taxDebt;
    }

    public static synchronized int getTaxRate() {
        return taxRate;
    }

    public static synchronized Map<UUID, String> getCharacterNames() {
        return characterNames;
    }

    public static synchronized @Nullable UUID getKnyazUuid() {
        return knyazUuid;
    }

    public static synchronized String getKnyazName() {
        return knyazName;
    }

    public static synchronized boolean isPlayerKnyaz(@Nullable UUID uuid, @Nullable String rawPlayerName, @Nullable String characterName) {
        if (uuid != null && knyazUuid != null && knyazUuid.equals(uuid)) {
            return true;
        }
        String activeKnyazName = !knyazName.isBlank() ? knyazName : ClientShopCache.getKnyazName();
        if (activeKnyazName != null && !activeKnyazName.isBlank()) {
            if (rawPlayerName != null && activeKnyazName.equalsIgnoreCase(rawPlayerName)) {
                return true;
            }
            if (characterName != null && activeKnyazName.equalsIgnoreCase(characterName)) {
                return true;
            }
        }
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null && uuid != null && uuid.equals(mc.player.getUUID())) {
            if (ClientShopCache.isClientKnyaz() || ClientShopCache.clientHasCrown() || com.oneshop.service.OneManagerBridge.hasCrownInInventory(mc.player)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized String resolveCharacterName(@Nullable UUID playerUuid, String fallbackName) {
        if (playerUuid != null) {
            String charName = characterNames.get(playerUuid);
            if (charName != null && !charName.isBlank()) {
                return charName;
            }
        }
        return fallbackName != null ? fallbackName : "";
    }

    /**
     * Tries to sync directly from local OneManager client state if present in the runtime JVM.
     */
    public static synchronized void syncFromLocalOneManagerIfPresent(@Nullable UUID selfUuid) {
        try {
            Class<?> handlerClass = Class.forName("com.onemanager.client.ClientPacketHandler");
            java.lang.reflect.Field field = handlerClass.getField("lastKnownWorldData");
            Object worldData = field.get(null);
            if (worldData != null) {
                try {
                    taxRate = (Integer) worldData.getClass().getMethod("getTaxRate").invoke(worldData);
                } catch (Throwable ignored) {}

                try {
                    UUID kId = (UUID) worldData.getClass().getMethod("getKnyazUuid").invoke(worldData);
                    if (kId != null) knyazUuid = kId;
                } catch (Throwable ignored) {}
                try {
                    String kName = (String) worldData.getClass().getMethod("getKnyazName").invoke(worldData);
                    if (kName != null && !kName.isBlank()) knyazName = kName;
                } catch (Throwable ignored) {}

                if (selfUuid != null) {
                    try {
                        Object pd = worldData.getClass().getMethod("getPlayerData", UUID.class).invoke(worldData, selfUuid);
                        if (pd != null) {
                            try {
                                stateBrands = (Integer) pd.getClass().getMethod("getStateBrands").invoke(pd);
                            } catch (Throwable ignored) {}
                            try {
                                taxDebt = (Long) pd.getClass().getMethod("getTaxDebt").invoke(pd);
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }

                try {
                    Map<?, ?> allPd = (Map<?, ?>) worldData.getClass().getMethod("getAllPlayerData").invoke(worldData);
                    if (allPd != null && !allPd.isEmpty()) {
                        Map<UUID, String> updated = new HashMap<>(characterNames);
                        for (Map.Entry<?, ?> entry : allPd.entrySet()) {
                            if (entry.getKey() instanceof UUID u && entry.getValue() != null) {
                                Object pd = entry.getValue();
                                Boolean hasActive = (Boolean) pd.getClass().getMethod("hasActiveCharacter").invoke(pd);
                                if (Boolean.TRUE.equals(hasActive)) {
                                    Object activeChar = pd.getClass().getMethod("getActiveCharacter").invoke(pd);
                                    if (activeChar != null) {
                                        String name = (String) activeChar.getClass().getMethod("getName").invoke(activeChar);
                                        if (name != null && !name.isBlank()) {
                                            updated.put(u, name.trim());
                                        }
                                    }
                                }
                            }
                        }
                        characterNames = Map.copyOf(updated);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {
            // OneManager client not loaded or reflection failed, ignore silently
        }
    }
}
