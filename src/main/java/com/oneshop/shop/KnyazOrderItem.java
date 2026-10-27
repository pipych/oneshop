package com.oneshop.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Immutable data model representing a single resource requirement or text task in a Knyaz Order.
 *
 * @param itemId        Registry ID of the required item (e.g., "minecraft:iron_ingot"), or empty if this is a text task
 * @param requiredCount Total number of items or steps required (defaults to 1 for text tasks)
 * @param deliveredCount Number of items already delivered or 1 if text task is completed
 * @param claimedCount   Number of delivered items already claimed from the order by the Knyaz
 * @param taskText      Custom task text (e.g. "Построить мост через реку"), or empty if this is a resource item
 */
public record KnyazOrderItem(
        String itemId,
        int requiredCount,
        int deliveredCount,
        int claimedCount,
        String taskText
) {
    public static final Codec<KnyazOrderItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("itemId", "").forGetter(KnyazOrderItem::itemId),
            Codec.INT.optionalFieldOf("requiredCount", 1).forGetter(KnyazOrderItem::requiredCount),
            Codec.INT.optionalFieldOf("deliveredCount", 0).forGetter(KnyazOrderItem::deliveredCount),
            Codec.INT.optionalFieldOf("claimedCount", 0).forGetter(KnyazOrderItem::claimedCount),
            Codec.STRING.optionalFieldOf("taskText", "").forGetter(KnyazOrderItem::taskText)
    ).apply(instance, KnyazOrderItem::new));

    public KnyazOrderItem {
        itemId = itemId != null ? itemId.trim() : "";
        taskText = taskText != null ? taskText.trim() : "";
        requiredCount = Math.max(1, requiredCount);
        deliveredCount = Math.max(0, Math.min(requiredCount, deliveredCount));
        claimedCount = Math.max(0, Math.min(deliveredCount, claimedCount));
    }

    public KnyazOrderItem(String itemId, int requiredCount, int deliveredCount, int claimedCount) {
        this(itemId, requiredCount, deliveredCount, claimedCount, "");
    }

    public KnyazOrderItem(String itemId, int requiredCount) {
        this(itemId, requiredCount, 0, 0, "");
    }

    public static KnyazOrderItem textTask(String taskText, boolean completed) {
        return new KnyazOrderItem("", 1, completed ? 1 : 0, 0, taskText);
    }

    public boolean isTextTask() {
        return !taskText.isBlank() && itemId.isBlank();
    }

    public KnyazOrderItem withCompleted(boolean completed) {
        return new KnyazOrderItem(itemId, requiredCount, completed ? requiredCount : 0, claimedCount, taskText);
    }

    public KnyazOrderItem withTaskText(String newTaskText) {
        return new KnyazOrderItem(itemId, requiredCount, deliveredCount, claimedCount, newTaskText);
    }

    public int remainingNeeded() {
        return Math.max(0, requiredCount - deliveredCount);
    }

    public int unclaimedDeliveredCount() {
        return Math.max(0, deliveredCount - claimedCount);
    }

    public boolean isCompleted() {
        return deliveredCount >= requiredCount;
    }

    public Item resolveItem() {
        if (itemId == null || itemId.isBlank()) {
            return Items.AIR;
        }
        try {
            ResourceLocation loc = ResourceLocation.tryParse(itemId);
            if (loc != null) {
                Item item = BuiltInRegistries.ITEM.get(loc);
                if (item != null) {
                    return item;
                }
            }
        } catch (Throwable ignored) {}
        return Items.AIR;
    }

    public ItemStack createDisplayStack() {
        if (isTextTask()) {
            return new ItemStack(Items.WRITABLE_BOOK, 1);
        }
        Item item = resolveItem();
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, 1);
    }

    public boolean matchesItem(ItemStack stack) {
        if (isTextTask() || stack == null || stack.isEmpty()) {
            return false;
        }
        Item target = resolveItem();
        if (target == Items.AIR) {
            return false;
        }
        return stack.is(target);
    }

    public KnyazOrderItem withDeliveredCount(int newDeliveredCount) {
        return new KnyazOrderItem(itemId, requiredCount, newDeliveredCount, claimedCount, taskText);
    }

    public KnyazOrderItem withAddedDelivery(int added) {
        if (added <= 0) return this;
        return new KnyazOrderItem(itemId, requiredCount, Math.min(requiredCount, deliveredCount + added), claimedCount, taskText);
    }

    public KnyazOrderItem withClaimedCount(int newClaimedCount) {
        return new KnyazOrderItem(itemId, requiredCount, deliveredCount, newClaimedCount, taskText);
    }

    public KnyazOrderItem withRequiredCount(int newRequiredCount) {
        return new KnyazOrderItem(itemId, Math.max(1, newRequiredCount), deliveredCount, claimedCount, taskText);
    }
}
