package com.oneshop.service;

import com.oneshop.OneShop;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Integration bridge for reading and interacting with OneManager's world data,
 * Knyaz status, Monarch Crown status, and Treasury (Казна).
 *
 * Works seamlessly both when OneManager is loaded at runtime (via direct SavedData reflection)
 * and when reading OneManager's world data files directly from saves/<world>/data/*.dat.
 */
public final class OneManagerBridge {
    public static final String KNYAZ_DATA_FILE = "monarchcrownmod_knyaz.dat";
    public static final String CHARACTER_DATA_FILE = "onemanager_character_data.dat";
    public static final ResourceLocation CROWN_ID = ResourceLocation.fromNamespaceAndPath("onemanager", "monarch_crown");
    public static final ResourceLocation LEGACY_CROWN_ID = ResourceLocation.fromNamespaceAndPath("monarchcrownmod", "monarch_crown");

    private OneManagerBridge() {}

    public record KnyazInfo(String knyazName, @Nullable UUID knyazUuid, long treasuryAmount, long treasuryCapacity) {}

    public record PlayerTabMetrics(
            int stateBrands,
            long taxDebt,
            int taxRate,
            java.util.Map<UUID, String> characterNames,
            @Nullable UUID knyazUuid,
            String knyazName
    ) {
        public PlayerTabMetrics(int stateBrands, long taxDebt, int taxRate, java.util.Map<UUID, String> characterNames) {
            this(stateBrands, taxDebt, taxRate, characterNames, null, "");
        }
    }

    /**
     * Checks whether the given ItemStack is a Monarch Crown from OneManager / MonarchCrownMod.
     */
    public static boolean isCrownStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (CROWN_ID.equals(itemId) || LEGACY_CROWN_ID.equals(itemId)) {
            return true;
        }
        String className = stack.getItem().getClass().getName();
        return className.endsWith(".CrownItem");
    }

    /**
     * Checks whether the player is currently wearing the Monarch Crown on their head.
     */
    public static boolean isWearingCrown(Player player) {
        if (player == null) return false;
        return isCrownStack(player.getItemBySlot(EquipmentSlot.HEAD));
    }

    /**
     * Checks whether the player possesses the Monarch Crown anywhere in their inventory (head, armor, main inventory, offhand).
     */
    public static boolean hasCrownInInventory(Player player) {
        if (player == null) return false;
        if (isWearingCrown(player)) return true;
        for (ItemStack stack : player.getInventory().armor) {
            if (isCrownStack(stack)) return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (isCrownStack(stack)) return true;
        }
        for (ItemStack stack : player.getInventory().offhand) {
            if (isCrownStack(stack)) return true;
        }
        return false;
    }

    /**
     * Checks whether the player is recognized as the Knyaz (either via OneManager's KnyazSavedData / CharacterWorldData
     * or by possessing/wearing the Monarch Crown).
     */
    public static boolean isPlayerKnyaz(ServerPlayer player) {
        if (player == null) return false;
        if (hasCrownInInventory(player)) {
            return true;
        }
        if (player.server == null) return false;
        KnyazInfo info = getKnyazInfo(player.server);
        if (info.knyazUuid() != null && info.knyazUuid().equals(player.getUUID())) {
            return true;
        }
        if (info.knyazName() != null && !info.knyazName().isEmpty()
                && info.knyazName().equalsIgnoreCase(player.getGameProfile().getName())) {
            return true;
        }
        return false;
    }

    /**
     * Finds all online players who are currently recognized as Knyaz.
     */
    public static java.util.List<ServerPlayer> findOnlineKnyazPlayers(@Nullable MinecraftServer server) {
        if (server == null || server.getPlayerList() == null) {
            return java.util.List.of();
        }
        java.util.List<ServerPlayer> list = new java.util.ArrayList<>();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (isPlayerKnyaz(p)) {
                list.add(p);
            }
        }
        return list;
    }

    /**
     * Resolves the active character name of the player from OneManager.
     * Checks live CharacterWorldData reflection first, then falls back to NBT data on disk,
     * and finally falls back to the player's GameProfile name.
     */
    public static String getPlayerCharacterName(@Nullable MinecraftServer server, @Nullable ServerPlayer player) {
        if (player == null) {
            return "";
        }
        String fallback = player.getGameProfile() != null && player.getGameProfile().getName() != null
                ? player.getGameProfile().getName()
                : "";

        if (server != null) {
            // 1. Try live OneManager classes via reflection
            try {
                Class<?> charWorldDataClass = Class.forName("com.onemanager.data.CharacterWorldData");
                Object worldData = charWorldDataClass.getMethod("get", MinecraftServer.class).invoke(null, server);
                if (worldData != null) {
                    Object pd = charWorldDataClass.getMethod("getPlayerData", UUID.class).invoke(worldData, player.getUUID());
                    if (pd != null) {
                        Boolean hasActive = (Boolean) pd.getClass().getMethod("hasActiveCharacter").invoke(pd);
                        if (Boolean.TRUE.equals(hasActive)) {
                            Object activeChar = pd.getClass().getMethod("getActiveCharacter").invoke(pd);
                            if (activeChar != null) {
                                String name = (String) activeChar.getClass().getMethod("getName").invoke(activeChar);
                                if (name != null && !name.isBlank()) {
                                    return name.trim();
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 2. Try disk data
            try {
                Path dataDir = getOverworldDataDir(server);
                String diskName = getPlayerCharacterNameFromDataDir(dataDir, player.getUUID(), fallback);
                if (diskName != null && !diskName.isBlank()) {
                    return diskName.trim();
                }
            } catch (Throwable ignored) {}
        }

        return fallback;
    }

    /**
     * Resolves character name from the world data directory NBT file.
     */
    public static String getPlayerCharacterNameFromDataDir(Path dataDir, UUID playerUuid, String fallbackName) {
        if (dataDir == null || playerUuid == null) {
            return fallbackName;
        }
        try {
            PlayerTabMetrics metrics = readPlayerTabMetricsFromDataDir(dataDir, playerUuid);
            String name = metrics.characterNames().get(playerUuid);
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        } catch (Throwable ignored) {}
        return fallbackName;
    }

    /**
     * Reads Knyaz identity and Treasury state from OneManager (via live SavedData if loaded, or directly from world data files).
     */
    public static KnyazInfo getKnyazInfo(MinecraftServer server) {
        if (server == null) {
            return new KnyazInfo("", null, 0L, 0L);
        }

        // 1. Try live OneManager classes via reflection first
        try {
            Class<?> charWorldDataClass = Class.forName("com.onemanager.data.CharacterWorldData");
            Object worldData = charWorldDataClass.getMethod("get", MinecraftServer.class).invoke(null, server);
            if (worldData != null) {
                String knyazName = (String) charWorldDataClass.getMethod("getKnyazName").invoke(worldData);
                UUID knyazUuid = (UUID) charWorldDataClass.getMethod("getKnyazUuid").invoke(worldData);
                long treasury = (Long) charWorldDataClass.getMethod("getTreasury").invoke(worldData);
                long capacity = (Long) charWorldDataClass.getMethod("getTreasuryCapacity").invoke(worldData);

                if (knyazUuid == null && (knyazName == null || knyazName.isEmpty())) {
                    try {
                        Class<?> knyazSavedDataClass = Class.forName("com.onemanager.data.KnyazSavedData");
                        Object knyazData = knyazSavedDataClass.getMethod("get", MinecraftServer.class).invoke(null, server);
                        if (knyazData != null) {
                            knyazName = (String) knyazSavedDataClass.getMethod("getKnyazName").invoke(knyazData);
                            knyazUuid = (UUID) knyazSavedDataClass.getMethod("getKnyazUUID").invoke(knyazData);
                        }
                    } catch (Throwable ignored) {}
                }

                return new KnyazInfo(knyazName != null ? knyazName : "", knyazUuid, treasury, capacity);
            }
        } catch (ClassNotFoundException ignored) {
            // OneManager not loaded in JVM — fall back to reading .dat files from disk
        } catch (Throwable t) {
            OneShop.LOGGER.debug("Reflection read of OneManager CharacterWorldData failed, falling back to NBT file read", t);
        }

        // 2. Fallback: read directly from world data .dat files on disk
        return readKnyazInfoFromDisk(server);
    }

    private static Path getOverworldDataDir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("data");
    }

    public static KnyazInfo readKnyazInfoFromDisk(MinecraftServer server) {
        Path dataDir = getOverworldDataDir(server);
        return readKnyazInfoFromDataDir(dataDir);
    }

    public static KnyazInfo readKnyazInfoFromDataDir(Path dataDir) {
        String knyazName = "";
        UUID knyazUuid = null;
        long treasury = 0L;
        long capacity = 0L;

        if (dataDir == null || !Files.exists(dataDir)) {
            return new KnyazInfo("", null, 0L, 0L);
        }

        // Read monarchcrownmod_knyaz.dat
        Path knyazFile = dataDir.resolve(KNYAZ_DATA_FILE);
        if (Files.exists(knyazFile)) {
            try {
                CompoundTag root = NbtIo.readCompressed(knyazFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                CompoundTag data = root.contains("data", Tag.TAG_COMPOUND) ? root.getCompound("data") : root;
                if (data.contains("KnyazName", Tag.TAG_STRING)) {
                    knyazName = data.getString("KnyazName");
                }
                if (data.hasUUID("KnyazUUID")) {
                    knyazUuid = data.getUUID("KnyazUUID");
                }
            } catch (Throwable e) {
                OneShop.LOGGER.error("Failed to read OneManager knyaz file: {}", knyazFile, e);
            }
        }

        // Read onemanager_character_data.dat
        Path charFile = dataDir.resolve(CHARACTER_DATA_FILE);
        if (Files.exists(charFile)) {
            try {
                CompoundTag root = NbtIo.readCompressed(charFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                CompoundTag data = root.contains("data", Tag.TAG_COMPOUND) ? root.getCompound("data") : root;
                if (knyazName.isEmpty() && data.contains("knyaz_name", Tag.TAG_STRING)) {
                    knyazName = data.getString("knyaz_name");
                }
                if (knyazUuid == null && data.hasUUID("knyaz_uuid")) {
                    knyazUuid = data.getUUID("knyaz_uuid");
                }
                if (data.contains("treasury", Tag.TAG_ANY_NUMERIC)) {
                    treasury = data.getLong("treasury");
                }
                if (data.contains("treasury_chests", Tag.TAG_LIST)) {
                    ListTag chests = data.getList("treasury_chests", Tag.TAG_COMPOUND);
                    long sumAmount = 0L;
                    long sumCap = 0L;
                    for (int i = 0; i < chests.size(); i++) {
                        CompoundTag chest = chests.getCompound(i);
                        boolean large = chest.getBoolean("large");
                        long amt = chest.getLong("amount");
                        sumAmount += amt;
                        sumCap += large ? 1250L : 500L;
                    }
                    if (!chests.isEmpty()) {
                        treasury = sumAmount;
                        capacity = sumCap;
                    }
                }
            } catch (Throwable e) {
                OneShop.LOGGER.error("Failed to read OneManager character world data file: {}", charFile, e);
            }
        }

        return new KnyazInfo(knyazName, knyazUuid, treasury, capacity);
    }

    /**
     * Reads player tab metrics (state brands, tax debt, current tax rate, and online character names map)
     * from OneManager (via live CharacterWorldData reflection or direct disk read).
     */
    public static PlayerTabMetrics getPlayerTabMetrics(MinecraftServer server, @Nullable UUID playerUuid) {
        if (server == null) {
            return new PlayerTabMetrics(0, 0L, 10, java.util.Map.of(), null, "");
        }

        KnyazInfo knyazInfo = getKnyazInfo(server);
        UUID knyazUuid = knyazInfo.knyazUuid();
        String knyazName = knyazInfo.knyazName();
        if (knyazUuid == null && server.getPlayerList() != null) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (hasCrownInInventory(p)) {
                    knyazUuid = p.getUUID();
                    knyazName = p.getGameProfile().getName();
                    break;
                }
            }
        }
        if (knyazName == null) {
            knyazName = "";
        }

        // 1. Try live OneManager classes via reflection first
        try {
            Class<?> charWorldDataClass = Class.forName("com.onemanager.data.CharacterWorldData");
            Object worldData = charWorldDataClass.getMethod("get", MinecraftServer.class).invoke(null, server);
            if (worldData != null) {
                int taxRate = (Integer) charWorldDataClass.getMethod("getTaxRate").invoke(worldData);
                int stateBrands = 0;
                long taxDebt = 0L;
                if (playerUuid != null) {
                    Object myPd = charWorldDataClass.getMethod("getPlayerData", UUID.class).invoke(worldData, playerUuid);
                    if (myPd != null) {
                        try {
                            stateBrands = (Integer) myPd.getClass().getMethod("getStateBrands").invoke(myPd);
                        } catch (Throwable ignored) {}
                        try {
                            taxDebt = (Long) myPd.getClass().getMethod("getTaxDebt").invoke(myPd);
                        } catch (Throwable ignored) {}
                    }
                }
                java.util.Map<UUID, String> charNames = new java.util.HashMap<>();
                try {
                    java.util.Map<?, ?> allPlayers = (java.util.Map<?, ?>) charWorldDataClass.getMethod("getAllPlayerData").invoke(worldData);
                    if (allPlayers != null) {
                        for (java.util.Map.Entry<?, ?> entry : allPlayers.entrySet()) {
                            if (entry.getKey() instanceof UUID u && entry.getValue() != null) {
                                Object pd = entry.getValue();
                                Boolean hasActive = (Boolean) pd.getClass().getMethod("hasActiveCharacter").invoke(pd);
                                if (Boolean.TRUE.equals(hasActive)) {
                                    Object activeChar = pd.getClass().getMethod("getActiveCharacter").invoke(pd);
                                    if (activeChar != null) {
                                        String cName = (String) activeChar.getClass().getMethod("getName").invoke(activeChar);
                                        if (cName != null && !cName.isBlank()) {
                                            charNames.put(u, cName.trim());
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
                return new PlayerTabMetrics(Math.max(0, stateBrands), Math.max(0L, taxDebt), Math.max(0, taxRate), java.util.Collections.unmodifiableMap(charNames), knyazUuid, knyazName);
            }
        } catch (ClassNotFoundException ignored) {
            // OneManager not loaded in JVM — fall back to reading .dat files from disk
        } catch (Throwable t) {
            OneShop.LOGGER.debug("Reflection read of OneManager CharacterWorldData for tab metrics failed, falling back to NBT file read", t);
        }

        // 2. Fallback: read directly from world data .dat files on disk
        PlayerTabMetrics diskMetrics = readPlayerTabMetricsFromDataDir(getOverworldDataDir(server), playerUuid);
        if (knyazUuid != null || !knyazName.isBlank()) {
            return new PlayerTabMetrics(
                    diskMetrics.stateBrands(),
                    diskMetrics.taxDebt(),
                    diskMetrics.taxRate(),
                    diskMetrics.characterNames(),
                    knyazUuid != null ? knyazUuid : diskMetrics.knyazUuid(),
                    !knyazName.isBlank() ? knyazName : diskMetrics.knyazName()
            );
        }
        return diskMetrics;
    }

    public static PlayerTabMetrics readPlayerTabMetricsFromDataDir(Path dataDir, @Nullable UUID playerUuid) {
        int stateBrands = 0;
        long taxDebt = 0L;
        int taxRate = 10;
        java.util.Map<UUID, String> charNames = new java.util.HashMap<>();

        if (dataDir == null || !Files.exists(dataDir)) {
            return new PlayerTabMetrics(stateBrands, taxDebt, taxRate, java.util.Collections.unmodifiableMap(charNames), null, "");
        }

        KnyazInfo knyazInfo = readKnyazInfoFromDataDir(dataDir);

        Path charFile = dataDir.resolve(CHARACTER_DATA_FILE);
        if (Files.exists(charFile)) {
            try {
                CompoundTag root = NbtIo.readCompressed(charFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
                CompoundTag data = root.contains("data", Tag.TAG_COMPOUND) ? root.getCompound("data") : root;
                if (data.contains("tax_rate", Tag.TAG_ANY_NUMERIC)) {
                    taxRate = Math.max(0, data.getInt("tax_rate"));
                }
                if (data.contains("players", Tag.TAG_LIST)) {
                    ListTag playersList = data.getList("players", Tag.TAG_COMPOUND);
                    for (int i = 0; i < playersList.size(); i++) {
                        CompoundTag pTag = playersList.getCompound(i);
                        UUID u = null;
                        if (pTag.hasUUID("uuid")) {
                            u = pTag.getUUID("uuid");
                        } else if (pTag.contains("uuid", Tag.TAG_STRING)) {
                            try {
                                u = UUID.fromString(pTag.getString("uuid"));
                            } catch (Exception ignored) {}
                        }
                        if (u == null) continue;

                        if (playerUuid != null && u.equals(playerUuid)) {
                            if (pTag.contains("stateBrands", Tag.TAG_ANY_NUMERIC)) {
                                stateBrands = Math.max(0, pTag.getInt("stateBrands"));
                            }
                            long taxStatus = 0L;
                            if (pTag.contains("taxStatus", Tag.TAG_ANY_NUMERIC)) {
                                taxStatus = pTag.getLong("taxStatus");
                            } else if (pTag.contains("taxDebt", Tag.TAG_ANY_NUMERIC)) {
                                taxStatus = -pTag.getLong("taxDebt");
                            }
                            taxDebt = Math.max(0L, -taxStatus);
                        }

                        if (pTag.contains("activeCharacter", Tag.TAG_COMPOUND)) {
                            CompoundTag activeTag = pTag.getCompound("activeCharacter");
                            if (activeTag.contains("name", Tag.TAG_STRING)) {
                                String cName = activeTag.getString("name");
                                if (cName != null && !cName.isBlank()) {
                                    charNames.put(u, cName.trim());
                                }
                            }
                        }
                    }
                }
            } catch (Throwable e) {
                OneShop.LOGGER.error("Failed to read OneManager character world data for tab metrics: {}", charFile, e);
            }
        }

        return new PlayerTabMetrics(stateBrands, taxDebt, taxRate, java.util.Collections.unmodifiableMap(charNames), knyazInfo.knyazUuid(), knyazInfo.knyazName() != null ? knyazInfo.knyazName() : "");
    }

    /**
     * Gets the current treasury balance in $PR from OneManager.
     */
    public static long getTreasuryBalance(MinecraftServer server) {
        return getKnyazInfo(server).treasuryAmount();
    }

    /**
     * Withdraws {@code amount} $PR from OneManager's Treasury (physical treasury chests + world data)
     * and logs the transaction in OneManager's treasury log.
     *
     * @param server      Active MinecraftServer
     * @param player      Player fulfilling the order (for audit log)
     * @param amount      Amount of $PR to withdraw from the treasury
     * @param orderTitle  Order title for the treasury transaction log
     * @return {@code true} if the treasury had sufficient funds and the withdrawal succeeded
     */
    public static boolean withdrawFromTreasury(MinecraftServer server, @Nullable ServerPlayer player, long amount, String orderTitle) {
        if (amount <= 0) {
            return true;
        }
        if (server == null) {
            return false;
        }

        String playerName = player != null ? player.getScoreboardName() : "Система";
        String actionDesc = "-" + String.format("%,d", amount).replace(',', ' ') + " $PR (Оплата заказа Князя: " + orderTitle + ")";

        // 1. Try live OneManager CharacterWorldData via reflection
        try {
            Class<?> charWorldDataClass = Class.forName("com.onemanager.data.CharacterWorldData");
            Object worldData = charWorldDataClass.getMethod("get", MinecraftServer.class).invoke(null, server);
            if (worldData != null) {
                long currentTreasury = (Long) charWorldDataClass.getMethod("getTreasury").invoke(worldData);
                if (currentTreasury < amount) {
                    return false;
                }

                // Check if treasury is backed by physical chests or raw treasury value
                java.util.List<?> chests = (java.util.List<?>) charWorldDataClass.getMethod("getTreasuryChests").invoke(worldData);
                if (chests != null && !chests.isEmpty()) {
                    long withdrawn = (Long) charWorldDataClass.getMethod("withdrawFromChests", MinecraftServer.class, long.class)
                            .invoke(worldData, server, amount);
                    if (withdrawn < amount) {
                        return false;
                    }
                } else {
                    charWorldDataClass.getMethod("removeTreasury", long.class).invoke(worldData, amount);
                }

                // Resolve active character name if available
                String charName = playerName;
                if (player != null) {
                    try {
                        Object pd = charWorldDataClass.getMethod("getPlayerData", UUID.class).invoke(worldData, player.getUUID());
                        if (pd != null) {
                            Boolean hasActive = (Boolean) pd.getClass().getMethod("hasActiveCharacter").invoke(pd);
                            if (Boolean.TRUE.equals(hasActive)) {
                                Object activeChar = pd.getClass().getMethod("getActiveCharacter").invoke(pd);
                                if (activeChar != null) {
                                    String name = (String) activeChar.getClass().getMethod("getName").invoke(activeChar);
                                    if (name != null && !name.isEmpty()) {
                                        charName = name;
                                    }
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                long treasuryAfter = (Long) charWorldDataClass.getMethod("getTreasury").invoke(worldData);
                charWorldDataClass.getMethod("logTransaction", String.class, String.class, String.class, long.class)
                        .invoke(worldData, charName, playerName, actionDesc, treasuryAfter);
                charWorldDataClass.getMethod("setDirty").invoke(worldData);

                // Broadcast SyncAllCharactersPacket to keep OneManager clients synced
                try {
                    CompoundTag savedTag = (CompoundTag) charWorldDataClass.getMethod("save", CompoundTag.class)
                            .invoke(worldData, new CompoundTag());
                    Class<?> packetClass = Class.forName("com.onemanager.network.SyncAllCharactersPacket");
                    Object packet = packetClass.getConstructor(CompoundTag.class, boolean.class).newInstance(savedTag, false);
                    Class<?> netHandlerClass = Class.forName("com.onemanager.network.NetworkHandler");
                    Class<?> customPayloadClass = Class.forName("net.minecraft.network.protocol.common.custom.CustomPacketPayload");
                    netHandlerClass.getMethod("broadcastToAll", customPayloadClass).invoke(null, packet);
                } catch (Throwable ignored) {}

                return true;
            }
        } catch (ClassNotFoundException ignored) {
            // Fall back to direct file update below
        } catch (Throwable t) {
            OneShop.LOGGER.error("Error withdrawing from OneManager treasury via reflection", t);
            return false;
        }

        // 2. Fallback: update onemanager_character_data.dat directly on disk
        return withdrawFromDiskTreasury(server, getOverworldDataDir(server), playerName, amount, actionDesc);
    }

    public static boolean withdrawFromDiskTreasury(@Nullable MinecraftServer server, Path dataDir, String playerName, long amount, String actionDesc) {
        if (amount <= 0) return true;
        if (dataDir == null) return false;
        Path charFile = dataDir.resolve(CHARACTER_DATA_FILE);
        if (!Files.exists(charFile)) {
            return false;
        }

        try {
            CompoundTag root = NbtIo.readCompressed(charFile, net.minecraft.nbt.NbtAccounter.unlimitedHeap());
            CompoundTag data = root.contains("data", Tag.TAG_COMPOUND) ? root.getCompound("data") : root;

            long currentTreasury = data.getLong("treasury");
            ListTag chests = data.contains("treasury_chests", Tag.TAG_LIST)
                    ? data.getList("treasury_chests", Tag.TAG_COMPOUND)
                    : new ListTag();

            if (!chests.isEmpty()) {
                long sum = 0L;
                for (int i = 0; i < chests.size(); i++) {
                    sum += chests.getCompound(i).getLong("amount");
                }
                currentTreasury = sum;
            }

            if (currentTreasury < amount) {
                return false;
            }

            if (!chests.isEmpty()) {
                long remaining = amount;
                for (int i = chests.size() - 1; i >= 0 && remaining > 0; i--) {
                    CompoundTag chest = chests.getCompound(i);
                    long stored = chest.getLong("amount");
                    if (stored > 0) {
                        long take = Math.min(stored, remaining);
                        long newStored = stored - take;
                        chest.putLong("amount", newStored);
                        remaining -= take;
                        syncLoadedChestEntity(server, chest, (int) newStored);
                    }
                }
            }

            long newTreasury = currentTreasury - amount;
            data.putLong("treasury", newTreasury);

            // Append log entry
            ListTag logList = data.contains("treasury_log", Tag.TAG_LIST)
                    ? data.getList("treasury_log", Tag.TAG_COMPOUND)
                    : new ListTag();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"));
            CompoundTag logEntry = new CompoundTag();
            logEntry.putString("characterName", playerName);
            logEntry.putString("playerName", playerName);
            logEntry.putString("action", actionDesc);
            logEntry.putString("timestamp", timestamp);
            logEntry.putLong("treasuryAfter", newTreasury);
            logEntry.putLong("gameTick", 0L);
            logList.add(0, logEntry);
            data.put("treasury_log", logList);

            NbtIo.writeCompressed(root, charFile);
            return true;
        } catch (Throwable e) {
            OneShop.LOGGER.error("Failed to withdraw from disk treasury file: {}", charFile, e);
            return false;
        }
    }

    private static void syncLoadedChestEntity(@Nullable MinecraftServer server, CompoundTag chestTag, int newStoredAmount) {
        if (server == null) return;
        try {
            String dim = chestTag.getString("dimension");
            BlockPos pos = new BlockPos(chestTag.getInt("x"), chestTag.getInt("y"), chestTag.getInt("z"));
            ResourceLocation dimLoc = ResourceLocation.tryParse(dim);
            ServerLevel level = dimLoc != null
                    ? server.getLevel(ResourceKey.create(Registries.DIMENSION, dimLoc))
                    : server.overworld();
            if (level == null) level = server.overworld();
            if (level != null && level.isLoaded(pos)) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be != null && be.getClass().getName().endsWith("TreasuryChestBlockEntity")) {
                    be.getClass().getMethod("setStoredAmountFromWorldData", int.class).invoke(be, newStoredAmount);
                }
            }
        } catch (Throwable ignored) {}
    }
}
