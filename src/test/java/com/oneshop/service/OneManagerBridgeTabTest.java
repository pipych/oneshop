package com.oneshop.service;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class OneManagerBridgeTabTest {

    @Test
    void testReadPlayerTabMetricsFromDataDir(@TempDir Path tempDir) throws IOException {
        UUID player1Uuid = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID player2Uuid = UUID.fromString("22222222-2222-2222-2222-222222222222");

        CompoundTag charData = new CompoundTag();
        charData.putInt("tax_rate", 15);

        ListTag playersList = new ListTag();

        // Player 1: stateBrands = 7, taxStatus = -45 (debt = 45), activeCharacter name = "Николай"
        CompoundTag player1Tag = new CompoundTag();
        player1Tag.putUUID("uuid", player1Uuid);
        player1Tag.putInt("stateBrands", 7);
        player1Tag.putLong("taxStatus", -45L);
        CompoundTag activeChar1 = new CompoundTag();
        activeChar1.putString("name", "Николай");
        player1Tag.put("activeCharacter", activeChar1);
        playersList.add(player1Tag);

        // Player 2: activeCharacter name = "Дмитрий"
        CompoundTag player2Tag = new CompoundTag();
        player2Tag.putUUID("uuid", player2Uuid);
        player2Tag.putInt("stateBrands", 2);
        player2Tag.putLong("taxStatus", 100L); // Overpaid, debt = 0
        CompoundTag activeChar2 = new CompoundTag();
        activeChar2.putString("name", "Дмитрий");
        player2Tag.put("activeCharacter", activeChar2);
        playersList.add(player2Tag);

        charData.put("players", playersList);

        CompoundTag root = new CompoundTag();
        root.put("data", charData);

        Path charFile = tempDir.resolve(OneManagerBridge.CHARACTER_DATA_FILE);
        NbtIo.writeCompressed(root, charFile);

        // Read metrics for player 1
        OneManagerBridge.PlayerTabMetrics metrics = OneManagerBridge.readPlayerTabMetricsFromDataDir(tempDir, player1Uuid);

        assertEquals(7, metrics.stateBrands(), "State brands should match player 1 data");
        assertEquals(45L, metrics.taxDebt(), "Tax debt should be 45 (from taxStatus = -45)");
        assertEquals(15, metrics.taxRate(), "Tax rate should match world data tax_rate");
        assertNotNull(metrics.characterNames(), "Character names map must not be null");
        assertEquals("Николай", metrics.characterNames().get(player1Uuid));
        assertEquals("Дмитрий", metrics.characterNames().get(player2Uuid));
        assertNull(metrics.knyazUuid());
        assertEquals("", metrics.knyazName());

        // Read metrics for player 2
        OneManagerBridge.PlayerTabMetrics metrics2 = OneManagerBridge.readPlayerTabMetricsFromDataDir(tempDir, player2Uuid);
        assertEquals(2, metrics2.stateBrands());
        assertEquals(0L, metrics2.taxDebt(), "Overpaid taxes should result in 0 tax debt");
        assertEquals(15, metrics2.taxRate());
    }

    @Test
    void testReadPlayerTabMetricsWithKnyaz(@TempDir Path tempDir) throws IOException {
        UUID knyazUuid = UUID.fromString("33333333-3333-3333-3333-333333333333");
        CompoundTag knyazData = new CompoundTag();
        knyazData.putString("KnyazName", "Великий Князь");
        knyazData.putUUID("KnyazUUID", knyazUuid);

        CompoundTag root = new CompoundTag();
        root.put("data", knyazData);

        Path knyazFile = tempDir.resolve(OneManagerBridge.KNYAZ_DATA_FILE);
        NbtIo.writeCompressed(root, knyazFile);

        OneManagerBridge.PlayerTabMetrics metrics = OneManagerBridge.readPlayerTabMetricsFromDataDir(tempDir, null);
        assertEquals(knyazUuid, metrics.knyazUuid());
        assertEquals("Великий Князь", metrics.knyazName());
    }
}
