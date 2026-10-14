package com.oneshop.item;

import com.oneshop.OneShop;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, OneShop.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> ONESHOP_TAB =
            CREATIVE_MODE_TABS.register("oneshop_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.oneshop"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.SPROTA.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.SPROTA.get());
                    }).build());
}
