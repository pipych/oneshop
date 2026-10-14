package com.oneshop.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record PlayerAccountData(double balance) {
    public static final Codec<PlayerAccountData> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.DOUBLE.fieldOf("balance").forGetter(PlayerAccountData::balance)
            ).apply(instance, PlayerAccountData::new)
    );

    public static final PlayerAccountData DEFAULT = new PlayerAccountData(0.0);

    public PlayerAccountData withBalance(double newBalance) {
        return new PlayerAccountData(newBalance);
    }

    public PlayerAccountData add(double amount) {
        return new PlayerAccountData(this.balance + amount);
    }

    public PlayerAccountData subtract(double amount) {
        return new PlayerAccountData(this.balance - amount);
    }

    public boolean canAfford(double amount) {
        return this.balance >= amount;
    }
}
