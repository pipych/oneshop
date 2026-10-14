package com.oneshop.network;

import com.oneshop.OneShop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SyncBalancePayload(double balance) implements CustomPacketPayload {
    public static final Type<SyncBalancePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "sync_balance"));

    public static final StreamCodec<ByteBuf, SyncBalancePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            SyncBalancePayload::balance,
            SyncBalancePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
