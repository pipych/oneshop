package com.oneshop.network;

import com.oneshop.OneShop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server packet sent when a resident clicks "Заказ подготовлен" (Order Prepared)
 * to deliver matching items from their inventory and receive reward from the OneManager Treasury.
 */
public record C2SFulfillKnyazOrderPayload(String orderId) implements CustomPacketPayload {
    public static final Type<C2SFulfillKnyazOrderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "fulfill_knyaz_order"));

    public static final StreamCodec<ByteBuf, C2SFulfillKnyazOrderPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            C2SFulfillKnyazOrderPayload::orderId,
            C2SFulfillKnyazOrderPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
