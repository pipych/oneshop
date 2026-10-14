package com.oneshop.network;

import com.oneshop.OneShop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server packet requesting withdrawal of cash from account into physical Sprota coins.
 *
 * @param amount Number of coins to withdraw
 */
public record C2SWithdrawCashPayload(int amount) implements CustomPacketPayload {
    public static final Type<C2SWithdrawCashPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "withdraw_cash"));

    public static final StreamCodec<ByteBuf, C2SWithdrawCashPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, C2SWithdrawCashPayload::amount,
            C2SWithdrawCashPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
