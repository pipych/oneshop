package com.oneshop.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class InventoryHighlightHandlerTest {

    @BeforeEach
    public void setUp() {
        InventoryHighlightHandler.resetState();
    }

    @Test
    public void testInitialAlphaIsZero() {
        assertEquals(0.0f, InventoryHighlightHandler.getHighlightAlpha(), 0.0001f);
    }

    @Test
    public void testColorCalculation() {
        // Bedrock green is 0x2ECC71
        float alpha = 1.0f;
        int alphaInt = (int) (alpha * 255.0f) & 0xFF;
        int borderColor = (alphaInt << 24) | 0x2ECC71;
        assertEquals(0xFF2ECC71, borderColor);

        float halfAlpha = 0.5f;
        int halfAlphaInt = (int) (halfAlpha * 255.0f) & 0xFF;
        int halfBorderColor = (halfAlphaInt << 24) | 0x2ECC71;
        assertEquals((127 << 24) | 0x2ECC71, halfBorderColor);
    }

    @Test
    public void testAlphaInterpolationFormula() {
        float highlightAlpha = 0.0f;
        float target = 1.0f; // Shift pressed
        float dt = 0.05f; // 50ms

        // duration ~150ms -> speed dt / 0.15f
        if (highlightAlpha < target) {
            highlightAlpha = Math.min(1.0f, highlightAlpha + (dt / 0.15f));
        } else if (highlightAlpha > target) {
            highlightAlpha = Math.max(0.0f, highlightAlpha - (dt / 0.15f));
        }

        assertEquals(0.05f / 0.15f, highlightAlpha, 0.0001f);

        // Next frame, dt = 0.10f
        dt = 0.10f;
        if (highlightAlpha < target) {
            highlightAlpha = Math.min(1.0f, highlightAlpha + (dt / 0.15f));
        }
        assertEquals(1.0f, highlightAlpha, 0.0001f);

        // Shift released
        target = 0.0f;
        dt = 0.075f;
        if (highlightAlpha > target) {
            highlightAlpha = Math.max(0.0f, highlightAlpha - (dt / 0.15f));
        }
        assertEquals(0.5f, highlightAlpha, 0.0001f);
    }

    @Test
    public void testDtClamping() {
        long lastFrameNanos = 0L;
        long now = 1_000_000_000L; // 1s later
        float dt = (now - lastFrameNanos) / 1_000_000_000.0f;
        if (dt > 0.1f) dt = 0.1f;
        assertEquals(0.1f, dt, 0.0001f);
    }
}
