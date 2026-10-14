package com.oneshop.attachment;

import com.oneshop.OneShop;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, OneShop.MODID);

    public static final Supplier<AttachmentType<PlayerAccountData>> PLAYER_ACCOUNT =
            ATTACHMENT_TYPES.register("player_account", () -> AttachmentType.builder(() -> PlayerAccountData.DEFAULT)
                    .serialize(PlayerAccountData.CODEC)
                    .copyOnDeath()
                    .build());
}
