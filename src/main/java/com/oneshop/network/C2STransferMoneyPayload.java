package com.oneshop.network;

import com.oneshop.OneShop;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * Client-to-Server packet requesting money transfer ($PR) to another player.
 *
 * @param targetPlayerUuid UUID of the recipient player
 * @param amount           Amount of currency to transfer
 */
public record C2STransferMoneyPayload(UUID targetPlayerUuid, double amount) implements CustomPacketPayload {
    public static final Type<C2STransferMoneyPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "transfer_money"));

    public static final StreamCodec<ByteBuf, C2STransferMoneyPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, C2STransferMoneyPayload::targetPlayerUuid,
            ByteBufCodecs.DOUBLE, C2STransferMoneyPayload::amount,
            C2STransferMoneyPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
