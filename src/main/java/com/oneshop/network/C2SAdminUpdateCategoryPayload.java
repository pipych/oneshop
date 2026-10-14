package com.oneshop.network;

import com.oneshop.OneShop;
import com.oneshop.shop.ShopCategory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server packet sent by OP players to add, update, or delete a shop category.
 *
 * @param category The category to add/update/delete
 * @param delete   true to delete the category, false to add/update
 */
public record C2SAdminUpdateCategoryPayload(
        ShopCategory category,
        boolean delete
) implements CustomPacketPayload {
    public static final Type<C2SAdminUpdateCategoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "admin_update_category"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SAdminUpdateCategoryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodecWithRegistries(ShopCategory.CODEC), C2SAdminUpdateCategoryPayload::category,
            ByteBufCodecs.BOOL, C2SAdminUpdateCategoryPayload::delete,
            C2SAdminUpdateCategoryPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
