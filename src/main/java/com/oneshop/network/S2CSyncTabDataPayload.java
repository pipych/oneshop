package com.oneshop.network;

import com.oneshop.OneShop;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-to-Client packet syncing player tab metrics (state brands, tax debt, tax rate, and player character names).
 */
public record S2CSyncTabDataPayload(
        int stateBrands,
        long taxDebt,
        int taxRate,
        Map<UUID, String> characterNames,
        @Nullable UUID knyazUuid,
        String knyazName
) implements CustomPacketPayload {
    public static final Type<S2CSyncTabDataPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(OneShop.MODID, "sync_tab_data"));

    public S2CSyncTabDataPayload {
        characterNames = characterNames != null ? Map.copyOf(characterNames) : Map.of();
        knyazName = knyazName != null ? knyazName : "";
    }

    public S2CSyncTabDataPayload(int stateBrands, long taxDebt, int taxRate, Map<UUID, String> characterNames) {
        this(stateBrands, taxDebt, taxRate, characterNames, null, "");
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CSyncTabDataPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public S2CSyncTabDataPayload decode(RegistryFriendlyByteBuf buf) {
                    int stateBrands = buf.readVarInt();
                    long taxDebt = buf.readVarLong();
                    int taxRate = buf.readVarInt();
                    int size = Math.min(1024, Math.max(0, buf.readVarInt()));
                    Map<UUID, String> map = new HashMap<>(size);
                    for (int i = 0; i < size; i++) {
                        UUID uuid = buf.readUUID();
                        String name = buf.readUtf(256);
                        map.put(uuid, name);
                    }
                    UUID knyazUuid = buf.readBoolean() ? buf.readUUID() : null;
                    String knyazName = buf.readUtf(256);
                    return new S2CSyncTabDataPayload(stateBrands, taxDebt, taxRate, map, knyazUuid, knyazName);
                }

                @Override
                public void encode(RegistryFriendlyByteBuf buf, S2CSyncTabDataPayload payload) {
                    buf.writeVarInt(payload.stateBrands());
                    buf.writeVarLong(payload.taxDebt());
                    buf.writeVarInt(payload.taxRate());
                    Map<UUID, String> names = payload.characterNames();
                    buf.writeVarInt(names.size());
                    for (Map.Entry<UUID, String> entry : names.entrySet()) {
                        buf.writeUUID(entry.getKey());
                        buf.writeUtf(entry.getValue() != null ? entry.getValue() : "", 256);
                    }
                    boolean hasKnyaz = payload.knyazUuid() != null;
                    buf.writeBoolean(hasKnyaz);
                    if (hasKnyaz) {
                        buf.writeUUID(payload.knyazUuid());
                    }
                    buf.writeUtf(payload.knyazName() != null ? payload.knyazName() : "", 256);
                }
            };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
