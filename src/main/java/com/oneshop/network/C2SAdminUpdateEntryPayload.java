package com.oneshop.network;

import com.oneshop.OneShop;
import com.oneshop.shop.ShopEntry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server packet sent by OP players to add, update, or delete a shop entry.
 *
 * @param categoryId ID of the target category
 * @param entry      The shop entry to add/update/delete
 * @param delete     true to delete the entry, false to add/update
 */
public record C2SAdminUpdateEntryPayload(
        String categoryId,
        ShopEntry entry,
        boolean delete
) implements CustomPacketPayload {
    public static final Type<C2SAdminUpdateEntryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "admin_update_entry"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SAdminUpdateEntryPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2SAdminUpdateEntryPayload::categoryId,
            ByteBufCodecs.fromCodecWithRegistries(ShopEntry.CODEC), C2SAdminUpdateEntryPayload::entry,
            ByteBufCodecs.BOOL, C2SAdminUpdateEntryPayload::delete,
            C2SAdminUpdateEntryPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
