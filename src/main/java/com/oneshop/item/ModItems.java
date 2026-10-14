package com.oneshop.item;

import com.oneshop.OneShop;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(OneShop.MODID);

    public static final DeferredItem<SprotaItem> SPROTA = ITEMS.registerItem(
            "sprota",
            SprotaItem::new,
            new Item.Properties().stacksTo(64)
    );
}
