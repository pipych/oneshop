package com.oneshop.client;

import com.oneshop.shop.LotIcon;
import com.oneshop.shop.ShopCategory;
import com.oneshop.shop.ShopEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalDouble;

import static org.junit.jupiter.api.Assertions.*;

public class ClientShopCacheTest {

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
    public void testEmptyCacheReturnsEmpty() {
        assertFalse(ClientShopCache.canSell(Items.DIAMOND));
        assertTrue(ClientShopCache.getSellPrice(Items.DIAMOND).isEmpty());
        assertTrue(ClientShopCache.getSellPrice(new ItemStack(Items.DIAMOND)).isEmpty());
        assertTrue(ClientShopCache.getSellPrice(Items.AIR).isEmpty());
        assertTrue(ClientShopCache.getSellPrice((ItemStack) null).isEmpty());
    }

    @Test
    public void testSetCategoriesPopulatesSellPrices() {
        ShopEntry diamondEntry = new ShopEntry(
                "diamond-1",
                new ItemStack(Items.DIAMOND),
                100.0,
                50.0,
                -1,
                "",
                List.of(),
                true,
                true,
                List.of(),
                LotIcon.of(new ItemStack(Items.DIAMOND))
        );

        ShopCategory category = new ShopCategory(
                "minerals",
                Component.literal("Minerals"),
                LotIcon.of(new ItemStack(Items.DIAMOND)),
                0,
                List.of(diamondEntry)
        );

        ClientShopCache.setCategories(List.of(category));

        assertTrue(ClientShopCache.canSell(Items.DIAMOND));
        OptionalDouble priceOpt = ClientShopCache.getSellPrice(Items.DIAMOND);
        assertTrue(priceOpt.isPresent());
        assertEquals(50.0, priceOpt.getAsDouble(), 0.001);

        // Gold is not in the shop
        assertFalse(ClientShopCache.canSell(Items.GOLD_INGOT));
        assertTrue(ClientShopCache.getSellPrice(Items.GOLD_INGOT).isEmpty());
    }

    @Test
    public void testDisallowedSellNotCached() {
        ShopEntry buyOnlyEntry = new ShopEntry(
                "diamond-buy-only",
                new ItemStack(Items.DIAMOND),
                100.0,
                50.0,
                -1,
                "",
                List.of(),
                true,
                false, // allowSell = false
                List.of(),
                LotIcon.of(new ItemStack(Items.DIAMOND))
        );

        ShopEntry zeroSellPriceEntry = new ShopEntry(
                "emerald-zero-sell",
                new ItemStack(Items.EMERALD),
                100.0,
                0.0, // sellPrice = 0
                -1,
                "",
                List.of(),
                true,
                true,
                List.of(),
                LotIcon.of(new ItemStack(Items.EMERALD))
        );

        ShopCategory category = new ShopCategory(
                "test",
                Component.literal("Test"),
                LotIcon.of(new ItemStack(Items.DIAMOND)),
                0,
                List.of(buyOnlyEntry, zeroSellPriceEntry)
        );

        ClientShopCache.setCategories(List.of(category));

        assertFalse(ClientShopCache.canSell(Items.DIAMOND));
        assertFalse(ClientShopCache.canSell(Items.EMERALD));
    }

    @Test
    public void testMultipleEntriesTakesHighestSellPrice() {
        ShopEntry lowDiamond = new ShopEntry(
                "diamond-low",
                new ItemStack(Items.DIAMOND),
                50.0,
                20.0,
                -1,
                "",
                List.of(),
                true,
                true,
                List.of(),
                LotIcon.of(new ItemStack(Items.DIAMOND))
        );

        ShopEntry highDiamond = new ShopEntry(
                "diamond-high",
                new ItemStack(Items.DIAMOND),
                150.0,
                75.0,
                -1,
                "",
                List.of(),
                true,
                true,
                List.of(),
                LotIcon.of(new ItemStack(Items.DIAMOND))
        );

        ShopCategory cat1 = new ShopCategory("cat1", Component.literal("Cat 1"), LotIcon.of(new ItemStack(Items.DIAMOND)), 0, List.of(lowDiamond));
        ShopCategory cat2 = new ShopCategory("cat2", Component.literal("Cat 2"), LotIcon.of(new ItemStack(Items.DIAMOND)), 1, List.of(highDiamond));

        ClientShopCache.setCategories(List.of(cat1, cat2));

        OptionalDouble priceOpt = ClientShopCache.getSellPrice(Items.DIAMOND);
        assertTrue(priceOpt.isPresent());
        assertEquals(75.0, priceOpt.getAsDouble(), 0.001);
    }

    @Test
    public void testAddOrUpdateCategoryRefreshesCache() {
        ShopEntry stone = new ShopEntry(
                "stone",
                new ItemStack(Items.STONE),
                10.0,
                5.0,
                -1,
                "",
                List.of(),
                true,
                true,
                List.of(),
                LotIcon.of(new ItemStack(Items.STONE))
        );

        ShopCategory cat = new ShopCategory("blocks", Component.literal("Blocks"), LotIcon.of(new ItemStack(Items.STONE)), 0, List.of(stone));
        ClientShopCache.addOrUpdateCategory(cat);

        assertTrue(ClientShopCache.canSell(Items.STONE));
        assertEquals(5.0, ClientShopCache.getSellPrice(Items.STONE).getAsDouble(), 0.001);

        // Update category with updated price
        ShopEntry updatedStone = stone.withPrices(10.0, 8.0);
        ShopCategory updatedCat = new ShopCategory("blocks", Component.literal("Blocks"), LotIcon.of(new ItemStack(Items.STONE)), 0, List.of(updatedStone));
        ClientShopCache.addOrUpdateCategory(updatedCat);

        assertEquals(8.0, ClientShopCache.getSellPrice(Items.STONE).getAsDouble(), 0.001);
    }

    @Test
    public void testClearResetsPrices() {
        ShopEntry coal = new ShopEntry(
                "coal",
                new ItemStack(Items.COAL),
                5.0,
                2.0,
                -1,
                "",
                List.of(),
                true,
                true,
                List.of(),
                LotIcon.of(new ItemStack(Items.COAL))
        );
        ShopCategory cat = new ShopCategory("fuel", Component.literal("Fuel"), LotIcon.of(new ItemStack(Items.COAL)), 0, List.of(coal));
        ClientShopCache.setCategories(List.of(cat));
        assertTrue(ClientShopCache.canSell(Items.COAL));

        ClientShopCache.clear();
        assertFalse(ClientShopCache.canSell(Items.COAL));
        assertTrue(ClientShopCache.getSellPrice(Items.COAL).isEmpty());
    }

    @Test
    public void testShopTooltipHandlerAddsTooltipForSellableItem() {
        ShopEntry diamondEntry = new ShopEntry(
                "diamond-1",
                new ItemStack(Items.DIAMOND),
                100.0,
                50.0,
                -1,
                "",
                List.of(),
                true,
                true,
                List.of(),
                LotIcon.of(new ItemStack(Items.DIAMOND))
        );
        ShopCategory category = new ShopCategory(
                "minerals",
                Component.literal("Minerals"),
                LotIcon.of(new ItemStack(Items.DIAMOND)),
                0,
                List.of(diamondEntry)
        );
        ClientShopCache.setCategories(List.of(category));

        List<com.mojang.datafixers.util.Either<net.minecraft.network.chat.FormattedText, net.minecraft.world.inventory.tooltip.TooltipComponent>> elements = new java.util.ArrayList<>();
        elements.add(com.mojang.datafixers.util.Either.left(Component.literal("Diamond")));
        net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents event =
                new net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents(
                        new ItemStack(Items.DIAMOND),
                        0,
                        0,
                        elements,
                        -1
                );

        ShopTooltipHandler.onGatherComponents(event);

        assertEquals(2, elements.size(), "Should have added one shop tooltip component");
        assertTrue(elements.get(1).right().isPresent());
        net.minecraft.world.inventory.tooltip.TooltipComponent comp = elements.get(1).right().get();
        assertInstanceOf(SellPriceTooltip.class, comp);
        assertEquals(50L, ((SellPriceTooltip) comp).price());

        // Test item not in shop
        List<com.mojang.datafixers.util.Either<net.minecraft.network.chat.FormattedText, net.minecraft.world.inventory.tooltip.TooltipComponent>> goldElements = new java.util.ArrayList<>();
        goldElements.add(com.mojang.datafixers.util.Either.left(Component.literal("Gold")));
        net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents goldEvent =
                new net.neoforged.neoforge.client.event.RenderTooltipEvent.GatherComponents(
                        new ItemStack(Items.GOLD_INGOT),
                        0,
                        0,
                        goldElements,
                        -1
                );

        ShopTooltipHandler.onGatherComponents(goldEvent);

        assertEquals(1, goldElements.size(), "Non-shop items should not have tooltip modified");
    }
}
