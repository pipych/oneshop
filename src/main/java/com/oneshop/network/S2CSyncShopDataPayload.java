package com.oneshop.network;

import com.oneshop.OneShop;
import com.oneshop.shop.ShopCategory;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server-to-Client packet syncing the entire shop registry to the client.
 */
public record S2CSyncShopDataPayload(List<ShopCategory> categories) implements CustomPacketPayload {
    public static final Type<S2CSyncShopDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "sync_shop_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncShopDataPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.fromCodecWithRegistries(ShopCategory.CODEC).apply(ByteBufCodecs.list()),
            S2CSyncShopDataPayload::categories,
            S2CSyncShopDataPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
