package com.oneshop.client;

/**
 * Client-side cache holding the local player's current bank balance for UI price tags and HUD display.
 */
public final class ClientBankCache {
    private static double balance = 0.0;

    private ClientBankCache() {}

    public static double getBalance() {
        return balance;
    }

    public static void setBalance(double newBalance) {
        balance = newBalance;
    }
}
