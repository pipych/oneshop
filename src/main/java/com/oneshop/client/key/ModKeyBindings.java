package com.oneshop.client.key;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class ModKeyBindings {
    public static final String CATEGORY = "key.categories.oneshop";

    public static final KeyMapping OPEN_SHOP = new KeyMapping(
            "key.oneshop.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_P,
            CATEGORY
    );

    public static final KeyMapping OPEN_ADMIN_SHOP = new KeyMapping(
            "key.oneshop.admin_open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            CATEGORY
    );

    private ModKeyBindings() {}
}
