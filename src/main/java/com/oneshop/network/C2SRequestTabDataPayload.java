package com.oneshop.network;

import com.oneshop.OneShop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server packet requesting fresh tab overlay data (metrics, balances, character names).
 */
public record C2SRequestTabDataPayload() implements CustomPacketPayload {
    public static final Type<C2SRequestTabDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "request_tab_data"));

    public static final StreamCodec<ByteBuf, C2SRequestTabDataPayload> STREAM_CODEC =
            StreamCodec.unit(new C2SRequestTabDataPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
