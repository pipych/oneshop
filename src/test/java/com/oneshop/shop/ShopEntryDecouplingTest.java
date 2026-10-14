package com.oneshop.shop;

import com.oneshop.client.ClientShopCache;
import com.oneshop.service.ShopTransactionService;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

public class ShopEntryDecouplingTest {

    @BeforeAll
    public static void initMinecraft() {
        try {
            java.lang.reflect.Field instanceField = net.neoforged.fml.loading.LoadingModList.class.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            if (instanceField.get(null) == null) {
                java.lang.reflect.Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
                unsafeField.setAccessible(true);
                sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
                Object mockList = unsafe.allocateInstance(net.neoforged.fml.loading.LoadingModList.class);

                java.lang.reflect.Field modFilesField = net.neoforged.fml.loading.LoadingModList.class.getDeclaredField("modFiles");
                modFilesField.setAccessible(true);
                modFilesField.set(mockList, java.util.List.of());

                instanceField.set(null, mockList);
            }
            net.minecraft.SharedConstants.tryDetectVersion();
            net.minecraft.server.Bootstrap.bootStrap();
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @BeforeEach
    public void setUp() {
        ClientShopCache.clear();
    }

    @Test
    public void testStrictDecouplingOfFunctionalItemAndVisualIcon() {
        // Lot selling cobblestone, but visual icon is set to diamond
        ShopEntry entry = new ShopEntry(
                "custom-lot-1",
                new ItemStack(Items.COBBLESTONE),
                20.0,
                10.0,
                -1,
                "",
                List.of(),
                true,
                true,
                List.of(),
                LotIcon.of(new ItemStack(Items.DIAMOND)),
                "Special Cobblestone",
                "minecraft:cobblestone"
        );

        // 1. Functional item check: Must resolve cobblestone, NEVER diamond
        assertEquals("minecraft:cobblestone", entry.getFunctionalItemId());
        assertEquals("minecraft:cobblestone", entry.getItemId());
        assertEquals("minecraft:cobblestone", entry.getTargetItem());

        assertTrue(entry.matchesTradeItem(new ItemStack(Items.COBBLESTONE)));
        assertFalse(entry.matchesTradeItem(new ItemStack(Items.DIAMOND)), "Visual icon must NEVER match trade item");
        assertFalse(entry.matchesTradeItem(new ItemStack(Items.DIRT)));

        // 2. Display icon check: Must render diamond for 32x32 visual display
        ItemStack displayStack = entry.getDisplayIconStack();
        assertFalse(displayStack.isEmpty());
        assertEquals(Items.DIAMOND, displayStack.getItem());
        assertNull(entry.getCustomIconTexture());
        assertEquals("minecraft:diamond", entry.getIconId());
    }

    @Test
    public void testVisualIconFallbackToFunctionalItem() {
        // Lot selling stone with no custom icon specified (null or empty)
        ShopEntry entry = new ShopEntry(
                "plain-stone",
                new ItemStack(Items.STONE),
                10.0,
                5.0,
                -1,
                "",
                List.of(),
                true,
                true,
                List.of(),
                LotIcon.of(ItemStack.EMPTY),
                "",
                "minecraft:stone"
        );

        assertEquals("minecraft:stone", entry.getFunctionalItemId());
        assertTrue(entry.matchesTradeItem(new ItemStack(Items.STONE)));

        // Fallback: Display stack should fall back to functional item (stone)
        ItemStack displayStack = entry.getDisplayIconStack();
        assertFalse(displayStack.isEmpty());
        assertEquals(Items.STONE, displayStack.getItem());
    }

    @Test
    public void testCustomTextureVisualIcon() {
        ResourceLocation customTex = ResourceLocation.fromNamespaceAndPath("oneshop", "textures/gui/currency_icon.png");
        ShopEntry entry = new ShopEntry(
                "textured-lot",
                new ItemStack(Items.OAK_LOG),
                15.0,
                7.5,
                -1,
                "",
                List.of(),
                true,
                true,
                List.of(),
                LotIcon.of(customTex),
                "Log Lot",
                "minecraft:oak_log"
        );

        assertEquals("minecraft:oak_log", entry.getFunctionalItemId());
        assertTrue(entry.matchesTradeItem(new ItemStack(Items.OAK_LOG)));
        assertEquals(customTex, entry.getCustomIconTexture());
        assertTrue(entry.getDisplayIconStack().isEmpty(), "Display icon stack is empty when custom texture is used");
        assertEquals(customTex.toString(), entry.getIconId());
    }

    @Test
    public void testClientSellCacheRecognizesCustomIconItemsProperly() {
        // Register lot: sells cobblestone (sellPrice = 10.0), but icon is diamond
        ShopEntry entry = new ShopEntry(
                "lot-cobble-with-diamond-icon",
                new ItemStack(Items.COBBLESTONE),
                20.0,
                10.0,
                -1,
                "",
                List.of(),
                true,
                true,
                List.of(),
                LotIcon.of(new ItemStack(Items.DIAMOND)),
                "Cobble lot",
                "minecraft:cobblestone"
        );

        ShopCategory category = new ShopCategory(
                "building",
                Component.literal("Building"),
                LotIcon.of(new ItemStack(Items.BRICKS)),
                0,
                List.of(entry)
        );

        ClientShopCache.setCategories(List.of(category));

        // In inventory: cobblestone MUST be sellable at 10.0
        assertTrue(ClientShopCache.canSell(Items.COBBLESTONE), "Functional item cobblestone must be recognized as sellable");
        assertTrue(ClientShopCache.canSell(new ItemStack(Items.COBBLESTONE)));
        OptionalDouble cobblePrice = ClientShopCache.getSellPrice(new ItemStack(Items.COBBLESTONE));
        assertTrue(cobblePrice.isPresent());
        assertEquals(10.0, cobblePrice.getAsDouble(), 0.001);

        // In inventory: diamond (the visual icon) must NOT be sellable!
        assertFalse(ClientShopCache.canSell(Items.DIAMOND), "Visual icon diamond must NOT be recognized as sellable");
        assertFalse(ClientShopCache.canSell(new ItemStack(Items.DIAMOND)));
        assertTrue(ClientShopCache.getSellPrice(new ItemStack(Items.DIAMOND)).isEmpty());
    }

    @Test
    public void testSerializationRoundtripIndependence() {
        ResourceLocation customTex = ResourceLocation.fromNamespaceAndPath("oneshop", "textures/gui/currency_icon.png");
        ShopEntry original = new ShopEntry(
                "roundtrip-1",
                new ItemStack(Items.IRON_INGOT),
                30.0,
                15.0,
                100,
                "perm.iron",
                List.of("say buy"),
                true,
                true,
                List.of("c:ingots/iron"),
                LotIcon.of(customTex),
                "Iron Custom",
                "minecraft:iron_ingot"
        );

        var registries = net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);

        var encodedTag = ShopEntry.CODEC.encodeStart(ops, original).getOrThrow();
        assertInstanceOf(CompoundTag.class, encodedTag);
        CompoundTag tag = (CompoundTag) encodedTag;

        assertTrue(tag.contains("item"));
        assertTrue(tag.contains("icon"));
        assertTrue(tag.contains("targetItem"));
        assertEquals("minecraft:iron_ingot", tag.getString("targetItem"));

        var decoded = ShopEntry.CODEC.parse(ops, tag).getOrThrow();
        assertEquals("minecraft:iron_ingot", decoded.getFunctionalItemId());
        assertTrue(decoded.matchesTradeItem(new ItemStack(Items.IRON_INGOT)));
        assertFalse(decoded.matchesTradeItem(new ItemStack(Items.GOLD_INGOT)));
        assertEquals(customTex, decoded.getCustomIconTexture());
        assertEquals(15.0, decoded.sellPrice(), 0.001);
    }
}
