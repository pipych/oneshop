package com.oneshop.network;

import com.oneshop.OneShop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server packet requesting deposit of physical Sprota coins from inventory into account.
 *
 * @param amount Number of coins to deposit
 */
public record C2SDepositCashPayload(int amount) implements CustomPacketPayload {
    public static final Type<C2SDepositCashPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "deposit_cash"));

    public static final StreamCodec<ByteBuf, C2SDepositCashPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, C2SDepositCashPayload::amount,
            C2SDepositCashPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
