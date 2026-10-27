package com.oneshop.network;

import com.oneshop.OneShop;
import com.oneshop.shop.KnyazOrder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server packet sent by the Knyaz (or admin) to publish/update or delete a Knyaz order.
 */
public record C2SKnyazManageOrderPayload(
        KnyazOrder order,
        boolean delete
) implements CustomPacketPayload {
    public static final Type<C2SKnyazManageOrderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "knyaz_manage_order"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SKnyazManageOrderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodecWithRegistries(KnyazOrder.CODEC),
            C2SKnyazManageOrderPayload::order,
            ByteBufCodecs.BOOL,
            C2SKnyazManageOrderPayload::delete,
            C2SKnyazManageOrderPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
