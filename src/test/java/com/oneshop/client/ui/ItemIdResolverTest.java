package com.oneshop.client.ui;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ItemIdResolverTest {

    @Test
    public void testNormalizeIdAddsDefaultNamespace() {
        assertEquals("minecraft:stone", ItemIdResolver.normalizeId("stone"));
        assertEquals("minecraft:stone", ItemIdResolver.normalizeId("  stone  "));
        assertEquals("minecraft:diamond_sword", ItemIdResolver.normalizeId("DIAMOND_SWORD"));
        assertEquals("minecraft:golden_apple", ItemIdResolver.normalizeId("  Golden_Apple "));
    }

    @Test
    public void testNormalizeIdPreservesExistingNamespace() {
        assertEquals("minecraft:stone", ItemIdResolver.normalizeId("minecraft:stone"));
        assertEquals("c:iron_ingots", ItemIdResolver.normalizeId("C:iron_ingots"));
        assertEquals("neoforge:cheese", ItemIdResolver.normalizeId("  NeoForge:Cheese  "));
    }

    @Test
    public void testNormalizeIdHandlesEmptyOrNull() {
        assertEquals("", ItemIdResolver.normalizeId(""));
        assertEquals("", ItemIdResolver.normalizeId("   "));
        assertEquals("", ItemIdResolver.normalizeId(null));
    }

    @Test
    public void testParseLocation() {
        ResourceLocation loc = ItemIdResolver.parseLocation("minecraft:stone");
        assertNotNull(loc);
        assertEquals("minecraft", loc.getNamespace());
        assertEquals("stone", loc.getPath());

        assertNull(ItemIdResolver.parseLocation(""));
        assertNull(ItemIdResolver.parseLocation(null));
        assertNull(ItemIdResolver.parseLocation("invalid space in id"));
    }

    @Test
    public void testCalculateCenteredTextY() {
        // Standard Bedrock text box (height 18)
        int boxY = 100;
        int boxHeight = 18;
        int textY = ItemIdResolver.calculateCenteredTextY(boxY, boxHeight);
        assertEquals(104, textY);
        // Vertical spacing: top space = 104 - 100 = 4px; bottom space = (100 + 18) - (104 + 9) = 5px.
        assertEquals(4, textY - boxY);
        assertEquals(5, (boxY + boxHeight) - (textY + 9));

        // Compact text box (height 16)
        int textY16 = ItemIdResolver.calculateCenteredTextY(0, 16);
        assertEquals(3, textY16);
        assertEquals(3, textY16 - 0);
        assertEquals(4, 16 - (textY16 + 9));
    }

    @Test
    public void testCursorAndSelectionBounds() {
        int textY = 104;
        int topY = ItemIdResolver.getCursorTopY(textY);
        int bottomY = ItemIdResolver.getCursorBottomY(textY);

        assertEquals(103, topY);
        assertEquals(114, bottomY);
        // Total cursor/selection height is 11px (1px padding above text, 1px padding below 9px font)
        assertEquals(11, bottomY - topY);
    }

    @Test
    public void testBedrockTextBoxReflectionFields() throws Exception {
        java.lang.reflect.Field dpField = BedrockTextBoxComponent.class.getDeclaredField("DISPLAY_POS_FIELD");
        dpField.setAccessible(true);
        assertNotNull(dpField.get(null), "displayPos field should be found via reflection");

        java.lang.reflect.Field ftField = BedrockTextBoxComponent.class.getDeclaredField("FOCUSED_TIME_FIELD");
        ftField.setAccessible(true);
        assertNotNull(ftField.get(null), "focusedTime field should be found via reflection");

        java.lang.reflect.Field hpField = BedrockTextBoxComponent.class.getDeclaredField("HIGHLIGHT_POS_FIELD");
        hpField.setAccessible(true);
        assertNotNull(hpField.get(null), "highlightPos field should be found via reflection");
    }
}
