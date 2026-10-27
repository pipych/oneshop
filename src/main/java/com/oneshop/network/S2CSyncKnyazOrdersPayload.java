package com.oneshop.network;

import com.oneshop.OneShop;
import com.oneshop.shop.KnyazOrder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server-to-Client packet syncing Knyaz orders, whether the receiving player is the Knyaz,
 * whether they possess/wear the Monarch Crown, the current Knyaz name, and the OneManager Treasury balance.
 */
public record S2CSyncKnyazOrdersPayload(
        List<KnyazOrder> orders,
        boolean isKnyaz,
        boolean hasCrown,
        String knyazName,
        long treasuryBalance
) implements CustomPacketPayload {
    public static final Type<S2CSyncKnyazOrdersPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "sync_knyaz_orders"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncKnyazOrdersPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodecWithRegistries(KnyazOrder.CODEC).apply(ByteBufCodecs.list()),
            S2CSyncKnyazOrdersPayload::orders,
            ByteBufCodecs.BOOL,
            S2CSyncKnyazOrdersPayload::isKnyaz,
            ByteBufCodecs.BOOL,
            S2CSyncKnyazOrdersPayload::hasCrown,
            ByteBufCodecs.STRING_UTF8,
            S2CSyncKnyazOrdersPayload::knyazName,
            ByteBufCodecs.VAR_LONG,
            S2CSyncKnyazOrdersPayload::treasuryBalance,
            S2CSyncKnyazOrdersPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
