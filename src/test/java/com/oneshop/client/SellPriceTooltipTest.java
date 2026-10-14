package com.oneshop.client;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SellPriceTooltipTest {

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

    @Test
    public void testRecordProperties() {
        SellPriceTooltip tooltip = new SellPriceTooltip(150L);
        assertEquals(150L, tooltip.price());
    }

    @Test
    public void testClientTooltipConstructionAndDimensions() {
        SellPriceTooltip data = new SellPriceTooltip(250L);
        SellPriceClientTooltip clientTooltip = new SellPriceClientTooltip(data);

        assertEquals(10, clientTooltip.getHeight());

        Font dummyFont = new Font(id -> null, false) {
            @Override
            public int width(FormattedText text) {
                return 60;
            }
        };

        assertEquals(60 + 2 + 8, clientTooltip.getWidth(dummyFont));

        Component text = clientTooltip.getText();
        assertNotNull(text);
        String textString = text.getString();
        assertTrue(textString.contains("Можно продать:"));
        assertTrue(textString.contains("250"));
    }

    @Test
    public void testTextureConstantExistsAndPointsToCurrencyIcon() {
        assertNotNull(SellPriceClientTooltip.COIN_ICON_TEXTURE);
        assertEquals("oneshop", SellPriceClientTooltip.COIN_ICON_TEXTURE.getNamespace());
        assertEquals("textures/gui/currency_icon.png", SellPriceClientTooltip.COIN_ICON_TEXTURE.getPath());
        assertEquals(8, SellPriceClientTooltip.COIN_ICON_WIDTH);
        assertEquals(8, SellPriceClientTooltip.COIN_ICON_HEIGHT);
        assertSame(SellPriceClientTooltip.COIN_ICON_TEXTURE, ShopTooltipHandler.COIN_ICON_TEXTURE);
    }
}
