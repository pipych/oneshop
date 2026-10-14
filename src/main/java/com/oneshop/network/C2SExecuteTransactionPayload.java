package com.oneshop.network;

import com.oneshop.OneShop;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client-to-Server packet requesting a purchase or sale of a shop entry.
 *
 * @param categoryId ID of the category containing the entry
 * @param entryId    ID of the shop entry to buy or sell
 * @param quantity   Amount of units to buy or sell
 * @param isBuy      true for buy, false for sell
 */
public record C2SExecuteTransactionPayload(
        String categoryId,
        String entryId,
        int quantity,
        boolean isBuy
) implements CustomPacketPayload {
    public static final Type<C2SExecuteTransactionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "execute_transaction"));

    public static final StreamCodec<ByteBuf, C2SExecuteTransactionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, C2SExecuteTransactionPayload::categoryId,
            ByteBufCodecs.STRING_UTF8, C2SExecuteTransactionPayload::entryId,
            ByteBufCodecs.VAR_INT, C2SExecuteTransactionPayload::quantity,
            ByteBufCodecs.BOOL, C2SExecuteTransactionPayload::isBuy,
            C2SExecuteTransactionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
