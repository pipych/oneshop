package com.oneshop.shop;

import com.mojang.serialization.DataResult;
import com.oneshop.service.OneManagerBridge;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class KnyazOrderTest {

    @Test
    void testKnyazOrderCodecRoundTrip() {
        List<KnyazOrderItem> items = List.of(
                new KnyazOrderItem("minecraft:iron_ingot", 128, 32, 16),
                new KnyazOrderItem("minecraft:oak_log", 256, 256, 256),
                new KnyazOrderItem("minecraft:diamond", 16, 0, 0)
        );
        KnyazOrder original = new KnyazOrder(
                "order-1",
                "Поставка ресурсов для крепости",
                "KnyazVladimir",
                items,
                1000L,
                250L,
                "2026-10-26 19:00",
                List.of("Добрыня", "Алёша Попович")
        );

        DataResult<Tag> encoded = KnyazOrder.CODEC.encodeStart(NbtOps.INSTANCE, original);
        assertTrue(encoded.result().isPresent(), "Encoding should succeed");

        DataResult<KnyazOrder> decoded = KnyazOrder.CODEC.parse(NbtOps.INSTANCE, encoded.result().get());
        assertTrue(decoded.result().isPresent(), "Decoding should succeed");

        KnyazOrder restored = decoded.result().get();
        assertEquals(original.id(), restored.id());
        assertEquals(original.title(), restored.title());
        assertEquals(original.createdBy(), restored.createdBy());
        assertEquals(original.totalReward(), restored.totalReward());
        assertEquals(original.paidReward(), restored.paidReward());
        assertEquals(3, restored.items().size());
        assertEquals(400, restored.totalRequiredCount());
        assertEquals(288, restored.totalDeliveredCount());
        assertEquals(112, restored.totalRemainingCount());
        assertEquals(16, restored.totalUnclaimedCount());
        assertEquals(List.of("Добрыня", "Алёша Попович"), restored.respondents());
        assertFalse(restored.isCompleted());
    }

    @Test
    void testOrderRespondentImmutabilityAndDeduplication() {
        KnyazOrder initial = new KnyazOrder(
                "order-resp",
                "Заказ на лес",
                "Knyaz",
                List.of(new KnyazOrderItem("minecraft:oak_log", 64, 0, 0)),
                500L,
                0L,
                "2026-10-27"
        );

        assertTrue(initial.respondents().isEmpty());

        KnyazOrder withFirst = initial.withRespondent("Добрыня Никитич");
        assertNotSame(initial, withFirst);
        assertTrue(initial.respondents().isEmpty(), "Initial order must remain immutable");
        assertEquals(List.of("Добрыня Никитич"), withFirst.respondents());

        // Duplicate addition should be ignored
        KnyazOrder duplicate = withFirst.withRespondent("Добрыня Никитич");
        assertEquals(List.of("Добрыня Никитич"), duplicate.respondents());

        // Trimming whitespace
        KnyazOrder trimmed = duplicate.withRespondent("  Добрыня Никитич  ");
        assertEquals(List.of("Добрыня Никитич"), trimmed.respondents());

        // Blank/null ignored
        KnyazOrder blank = trimmed.withRespondent("");
        assertEquals(List.of("Добрыня Никитич"), blank.respondents());
        assertSame(trimmed, blank);

        KnyazOrder nullResp = trimmed.withRespondent(null);
        assertSame(trimmed, nullResp);

        // Second respondent
        KnyazOrder withSecond = trimmed.withRespondent("Илья Муромец");
        assertEquals(List.of("Добрыня Никитич", "Илья Муромец"), withSecond.respondents());
    }

    @Test
    void testOneManagerCharacterNameResolution(@TempDir Path tempDataDir) throws Exception {
        UUID playerUuid = UUID.randomUUID();

        // Write onemanager_character_data.dat with player and activeCharacter name
        CompoundTag charData = new CompoundTag();
        ListTag playersList = new ListTag();

        CompoundTag playerTag = new CompoundTag();
        playerTag.putUUID("uuid", playerUuid);

        CompoundTag activeCharTag = new CompoundTag();
        activeCharTag.putString("name", "Алёша Попович");
        playerTag.put("activeCharacter", activeCharTag);

        playersList.add(playerTag);
        charData.put("players", playersList);

        CompoundTag root = new CompoundTag();
        root.put("data", charData);
        NbtIo.writeCompressed(root, tempDataDir.resolve("onemanager_character_data.dat"));

        // Assert character name resolved correctly
        String resolvedName = OneManagerBridge.getPlayerCharacterNameFromDataDir(tempDataDir, playerUuid, "FallbackNick");
        assertEquals("Алёша Попович", resolvedName);

        // Fallback for unknown player
        UUID unknownUuid = UUID.randomUUID();
        String fallbackResult = OneManagerBridge.getPlayerCharacterNameFromDataDir(tempDataDir, unknownUuid, "FallbackNick");
        assertEquals("FallbackNick", fallbackResult);
    }

    @Test
    void testProportionalPayoutCalculation() {
        List<KnyazOrderItem> items = List.of(
                new KnyazOrderItem("minecraft:iron_ingot", 50, 0, 0),
                new KnyazOrderItem("minecraft:coal", 50, 0, 0)
        );
        KnyazOrder order = new KnyazOrder("order-2", "Руда", "Knyaz", items, 100L, 0L, "");

        // First resident delivers 25 items out of 100 -> 25 $PR
        long firstPayout = order.calculatePayoutForDelivery(25);
        assertEquals(25L, firstPayout);

        KnyazOrder afterFirst = order.withItemsAndPaidReward(
                List.of(
                        items.get(0).withAddedDelivery(25),
                        items.get(1)
                ),
                order.paidReward() + firstPayout
        );

        assertEquals(75L, afterFirst.remainingReward());
        assertEquals(75, afterFirst.totalRemainingCount());

        // Second resident delivers remaining 75 items -> gets remaining 75 $PR
        long secondPayout = afterFirst.calculatePayoutForDelivery(75);
        assertEquals(75L, secondPayout);

        KnyazOrder completed = afterFirst.withItemsAndPaidReward(
                List.of(
                        afterFirst.items().get(0).withAddedDelivery(25),
                        afterFirst.items().get(1).withAddedDelivery(50)
                ),
                afterFirst.paidReward() + secondPayout
        );

        assertTrue(completed.isCompleted());
        assertEquals(0L, completed.remainingReward());
        assertEquals(100L, completed.paidReward());
    }

    @Test
    void testOneManagerFileBridgeKnyazAndTreasury(@TempDir Path tempDataDir) throws Exception {
        UUID knyazUuid = UUID.randomUUID();

        // 1. Write monarchcrownmod_knyaz.dat
        CompoundTag knyazData = new CompoundTag();
        knyazData.putString("KnyazName", "Rurik");
        knyazData.putUUID("KnyazUUID", knyazUuid);
        knyazData.putBoolean("PendingReturn", false);

        CompoundTag knyazRoot = new CompoundTag();
        knyazRoot.put("data", knyazData);
        NbtIo.writeCompressed(knyazRoot, tempDataDir.resolve("monarchcrownmod_knyaz.dat"));

        // 2. Write onemanager_character_data.dat with 2 treasury chests
        CompoundTag charData = new CompoundTag();
        charData.putString("knyaz_name", "Rurik");
        charData.putString("knyaz_uuid", knyazUuid.toString());
        charData.putLong("treasury", 900L);

        ListTag chests = new ListTag();
        CompoundTag chest1 = new CompoundTag();
        chest1.putString("id", "minecraft:overworld:0,64,0");
        chest1.putString("dimension", "minecraft:overworld");
        chest1.putInt("x", 0);
        chest1.putInt("y", 64);
        chest1.putInt("z", 0);
        chest1.putBoolean("large", false);
        chest1.putLong("amount", 500L);
        chests.add(chest1);

        CompoundTag chest2 = new CompoundTag();
        chest2.putString("id", "minecraft:overworld:2,64,0");
        chest2.putString("dimension", "minecraft:overworld");
        chest2.putInt("x", 2);
        chest2.putInt("y", 64);
        chest2.putInt("z", 0);
        chest2.putBoolean("large", true);
        chest2.putLong("amount", 400L);
        chests.add(chest2);

        charData.put("treasury_chests", chests);
        charData.put("treasury_log", new ListTag());

        CompoundTag charRoot = new CompoundTag();
        charRoot.put("data", charData);
        NbtIo.writeCompressed(charRoot, tempDataDir.resolve("onemanager_character_data.dat"));

        // Verify reading Knyaz Info and Treasury balance (sum of chests = 900, capacity = 500 + 1250 = 1750)
        OneManagerBridge.KnyazInfo info = OneManagerBridge.readKnyazInfoFromDataDir(tempDataDir);
        assertEquals("Rurik", info.knyazName());
        assertEquals(knyazUuid, info.knyazUuid());
        assertEquals(900L, info.treasuryAmount());
        assertEquals(1750L, info.treasuryCapacity());

        // Withdraw 600 $PR from Treasury (should take 400 from chest2 and 200 from chest1 -> 300 remaining)
        boolean ok = OneManagerBridge.withdrawFromDiskTreasury(null, tempDataDir, "Ivan", 600L, "Заказ Князя: Бревна");
        assertTrue(ok);
        assertEquals(300L, OneManagerBridge.readKnyazInfoFromDataDir(tempDataDir).treasuryAmount());

        // Attempting to withdraw more than remaining (400 > 300) should fail and leave balance intact
        boolean fail = OneManagerBridge.withdrawFromDiskTreasury(null, tempDataDir, "Ivan", 400L, "Заказ Князя: Железо");
        assertFalse(fail);
        assertEquals(300L, OneManagerBridge.readKnyazInfoFromDataDir(tempDataDir).treasuryAmount());
    }

    @Test
    void testTextOrderAndKnyazCompletionToggle() {
        KnyazOrderItem task1 = KnyazOrderItem.textTask("Построить мост через реку", false);
        KnyazOrderItem task2 = KnyazOrderItem.textTask("Возвести дозорную башню", false);
        assertTrue(task1.isTextTask());
        assertFalse(task1.isCompleted());
        assertEquals("Построить мост через реку", task1.taskText());

        KnyazOrder order = new KnyazOrder(
                "order-text-1",
                "Строительство на севере",
                "KnyazYaroslav",
                List.of(task1, task2),
                5000L,
                0L,
                "2026-10-27 20:00",
                List.of("Добрыня"),
                "Проект развития северных границ княжества",
                false
        );

        assertFalse(order.isCompleted());
        assertEquals("Строительство на севере", order.getDisplayTitle());
        assertTrue(order.matchesSearch("мост"));
        assertTrue(order.matchesSearch("северных"));

        // Round-trip serialization via CODEC
        DataResult<Tag> encoded = KnyazOrder.CODEC.encodeStart(NbtOps.INSTANCE, order);
        assertTrue(encoded.result().isPresent(), "Encoding should succeed");
        DataResult<KnyazOrder> decoded = KnyazOrder.CODEC.parse(NbtOps.INSTANCE, encoded.result().get());
        assertTrue(decoded.result().isPresent(), "Decoding should succeed");

        KnyazOrder restored = decoded.result().get();
        assertEquals(order.id(), restored.id());
        assertEquals(order.description(), restored.description());
        assertEquals(order.completed(), restored.completed());
        assertEquals(2, restored.items().size());
        assertTrue(restored.items().get(0).isTextTask());
        assertEquals("Построить мост через реку", restored.items().get(0).taskText());
        assertFalse(restored.items().get(0).isCompleted());

        // Knyaz marks task 0 as completed
        KnyazOrder afterTask0 = order.withItemCompleted(0, true);
        assertTrue(afterTask0.items().get(0).isCompleted());
        assertFalse(afterTask0.items().get(1).isCompleted());
        assertFalse(afterTask0.isCompleted()); // only 1 of 2 tasks done

        // Knyaz marks task 1 as completed -> all done -> order becomes completed
        KnyazOrder afterTask1 = afterTask0.withItemCompleted(1, true);
        assertTrue(afterTask1.items().get(1).isCompleted());
        assertTrue(afterTask1.isCompleted());

        // Knyaz can uncheck task 0
        KnyazOrder unchecked = afterTask1.withItemCompleted(0, false);
        assertFalse(unchecked.items().get(0).isCompleted());
        assertFalse(unchecked.isCompleted());

        // Toggle entire order completion
        KnyazOrder forceCompleted = order.withCompleted(true);
        assertTrue(forceCompleted.isCompleted());
        assertTrue(forceCompleted.items().get(0).isCompleted());
        assertTrue(forceCompleted.items().get(1).isCompleted());

        KnyazOrder forceUncompleted = forceCompleted.withCompleted(false);
        assertFalse(forceUncompleted.isCompleted());
        assertFalse(forceUncompleted.items().get(0).isCompleted());
        assertFalse(forceUncompleted.items().get(1).isCompleted());
    }
}
