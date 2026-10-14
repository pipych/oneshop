package com.oneshop.network;

import com.oneshop.OneShop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-to-Client packet instructing the client to open the shop screen.
 *
 * @param isAdmin true if opening in admin mode
 */
public record S2COpenShopScreenPayload(boolean isAdmin) implements CustomPacketPayload {
    public static final Type<S2COpenShopScreenPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "open_shop_screen"));

    public static final StreamCodec<ByteBuf, S2COpenShopScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, S2COpenShopScreenPayload::isAdmin,
            S2COpenShopScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
