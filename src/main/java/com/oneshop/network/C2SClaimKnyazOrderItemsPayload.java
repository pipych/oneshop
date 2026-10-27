package com.oneshop.network;

import com.oneshop.OneShop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server packet sent by the Knyaz to claim delivered resources from a Knyaz order into their inventory.
 */
public record C2SClaimKnyazOrderItemsPayload(String orderId) implements CustomPacketPayload {
    public static final Type<C2SClaimKnyazOrderItemsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "claim_knyaz_order_items"));

    public static final StreamCodec<ByteBuf, C2SClaimKnyazOrderItemsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            C2SClaimKnyazOrderItemsPayload::orderId,
            C2SClaimKnyazOrderItemsPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
