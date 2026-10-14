package com.oneshop.network;

import com.oneshop.OneShop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server packet requesting the opening of the shop screen.
 *
 * @param isAdmin true if requesting the admin view of the shop
 */
public record C2SOpenShopPayload(boolean isAdmin) implements CustomPacketPayload {
    public static final Type<C2SOpenShopPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "open_shop"));

    public static final StreamCodec<ByteBuf, C2SOpenShopPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, C2SOpenShopPayload::isAdmin,
            C2SOpenShopPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
