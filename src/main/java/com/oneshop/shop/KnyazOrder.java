package com.oneshop.shop;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Immutable data model representing a Knyaz resource or text order in OneShop.
 * Supports an unlimited number of resource items or text tasks per order, tracks resident respondents,
 * checklist readiness, order description, and Knyaz completion status.
 *
 * @param id          Unique order identifier
 * @param title       Display title of the order (optional custom title)
 * @param createdBy   Name of the Knyaz who published the order
 * @param items       List of resource items or text tasks required in this order
 * @param totalReward Total monetary reward ($PR) allocated for the order
 * @param paidReward  Monetary reward ($PR) previously recorded
 * @param createdAt   Creation timestamp string
 * @param respondents List of resident character names who responded to the order
 * @param description Optional detailed text task or description (e.g. building project)
 * @param completed   Whether the Knyaz has marked this order as completed
 */
public record KnyazOrder(
        String id,
        String title,
        String createdBy,
        List<KnyazOrderItem> items,
        long totalReward,
        long paidReward,
        String createdAt,
        List<String> respondents,
        String description,
        boolean completed
) {
    public static final Codec<KnyazOrder> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(KnyazOrder::id),
            Codec.STRING.optionalFieldOf("title", "").forGetter(KnyazOrder::title),
            Codec.STRING.optionalFieldOf("createdBy", "").forGetter(KnyazOrder::createdBy),
            KnyazOrderItem.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(KnyazOrder::items),
            Codec.LONG.fieldOf("totalReward").forGetter(KnyazOrder::totalReward),
            Codec.LONG.optionalFieldOf("paidReward", 0L).forGetter(KnyazOrder::paidReward),
            Codec.STRING.optionalFieldOf("createdAt", "").forGetter(KnyazOrder::createdAt),
            Codec.STRING.listOf().optionalFieldOf("respondents", List.of()).forGetter(KnyazOrder::respondents),
            Codec.STRING.optionalFieldOf("description", "").forGetter(KnyazOrder::description),
            Codec.BOOL.optionalFieldOf("completed", false).forGetter(KnyazOrder::completed)
    ).apply(instance, KnyazOrder::new));

    public KnyazOrder(
            String id,
            String title,
            String createdBy,
            List<KnyazOrderItem> items,
            long totalReward,
            long paidReward,
            String createdAt
    ) {
        this(id, title, createdBy, items, totalReward, paidReward, createdAt, List.of(), "", false);
    }

    public KnyazOrder(
            String id,
            String title,
            String createdBy,
            List<KnyazOrderItem> items,
            long totalReward,
            long paidReward,
            String createdAt,
            List<String> respondents
    ) {
        this(id, title, createdBy, items, totalReward, paidReward, createdAt, respondents, "", false);
    }

    public KnyazOrder {
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        title = title != null ? title.trim() : "";
        createdBy = createdBy != null ? createdBy.trim() : "";
        items = items != null ? List.copyOf(items) : List.of();
        totalReward = Math.max(0L, totalReward);
        paidReward = Math.max(0L, Math.min(totalReward, paidReward));
        createdAt = createdAt != null ? createdAt : "";
        description = description != null ? description.trim() : "";

        if (respondents != null) {
            List<String> clean = new ArrayList<>();
            for (String r : respondents) {
                if (r != null && !r.isBlank() && !clean.contains(r.trim())) {
                    clean.add(r.trim());
                }
            }
            respondents = List.copyOf(clean);
        } else {
            respondents = List.of();
        }
    }

    public int totalRequiredCount() {
        int sum = 0;
        for (KnyazOrderItem item : items) {
            sum += item.requiredCount();
        }
        return sum;
    }

    public int totalDeliveredCount() {
        int sum = 0;
        for (KnyazOrderItem item : items) {
            sum += item.deliveredCount();
        }
        return sum;
    }

    public int totalRemainingCount() {
        int sum = 0;
        for (KnyazOrderItem item : items) {
            sum += item.remainingNeeded();
        }
        return sum;
    }

    public int totalUnclaimedCount() {
        int sum = 0;
        for (KnyazOrderItem item : items) {
            sum += item.unclaimedDeliveredCount();
        }
        return sum;
    }

    public boolean isCompleted() {
        if (completed) return true;
        if (items.isEmpty()) return false;
        for (KnyazOrderItem item : items) {
            if (!item.isCompleted()) {
                return false;
            }
        }
        return true;
    }

    public boolean isFullyClaimed() {
        for (KnyazOrderItem item : items) {
            if (item.unclaimedDeliveredCount() > 0) {
                return false;
            }
        }
        return true;
    }

    public long remainingReward() {
        return Math.max(0L, totalReward - paidReward);
    }

    /**
     * Calculates the exact proportional reward (in integer $PR) to pay out when {@code newlyDelivered}
     * items are added to the current {@link #totalDeliveredCount()}.
     */
    public long calculatePayoutForDelivery(int newlyDelivered) {
        if (newlyDelivered <= 0 || totalReward <= 0) {
            return 0L;
        }
        int totalReq = totalRequiredCount();
        if (totalReq <= 0) {
            return 0L;
        }
        int newTotalDelivered = Math.min(totalReq, totalDeliveredCount() + newlyDelivered);
        if (newTotalDelivered >= totalReq) {
            return Math.max(0L, totalReward - paidReward);
        }
        long cumulativeEarned = (totalReward * (long) newTotalDelivered) / (long) totalReq;
        return Math.max(0L, cumulativeEarned - paidReward);
    }

    public ItemStack getPrimaryIconStack() {
        for (KnyazOrderItem item : items) {
            ItemStack stack = item.createDisplayStack();
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return new ItemStack(Items.WRITABLE_BOOK);
    }

    public String getDisplayTitle() {
        if (title != null && !title.isBlank()) {
            return title;
        }
        if (description != null && !description.isBlank()) {
            return description.length() > 30 ? description.substring(0, 30) + "..." : description;
        }
        if (!items.isEmpty()) {
            KnyazOrderItem first = items.get(0);
            if (first.isTextTask()) {
                String txt = first.taskText();
                String base = txt.length() > 30 ? txt.substring(0, 30) + "..." : txt;
                return items.size() > 1 ? base + " (+" + (items.size() - 1) + ")" : base;
            }
            ItemStack firstStack = first.createDisplayStack();
            if (!firstStack.isEmpty()) {
                return items.size() > 1
                        ? firstStack.getHoverName().getString() + " (+" + (items.size() - 1) + ")"
                        : firstStack.getHoverName().getString();
            }
        }
        return "Заказ #" + (id.length() > 6 ? id.substring(0, 6) : id);
    }

    public boolean matchesSearch(String lowerQuery) {
        if (lowerQuery == null || lowerQuery.isBlank()) {
            return true;
        }
        if (title != null && title.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (description != null && description.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        if (createdBy != null && createdBy.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
            return true;
        }
        for (String r : respondents) {
            if (r.toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                return true;
            }
        }
        for (KnyazOrderItem item : items) {
            if (item.isTextTask() && item.taskText().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                return true;
            }
            if (item.itemId().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                return true;
            }
            ItemStack stack = item.createDisplayStack();
            if (!stack.isEmpty() && stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                return true;
            }
        }
        return false;
    }

    public KnyazOrder withCompleted(boolean newCompleted) {
        List<KnyazOrderItem> updatedItems = new ArrayList<>(items.size());
        for (KnyazOrderItem item : items) {
            updatedItems.add(item.withCompleted(newCompleted));
        }
        return new KnyazOrder(id, title, createdBy, updatedItems, totalReward, paidReward, createdAt, respondents, description, newCompleted);
    }

    public KnyazOrder withItemCompleted(int index, boolean itemCompleted) {
        if (index < 0 || index >= items.size()) return this;
        List<KnyazOrderItem> updatedItems = new ArrayList<>(items);
        updatedItems.set(index, items.get(index).withCompleted(itemCompleted));
        boolean allDone = !updatedItems.isEmpty() && updatedItems.stream().allMatch(KnyazOrderItem::isCompleted);
        return new KnyazOrder(id, title, createdBy, updatedItems, totalReward, paidReward, createdAt, respondents, description, allDone);
    }

    public KnyazOrder withRespondent(String characterName) {
        if (characterName == null || characterName.isBlank()) {
            return this;
        }
        String trimmed = characterName.trim();
        List<String> updated = new ArrayList<>(this.respondents);
        if (!updated.contains(trimmed)) {
            updated.add(trimmed);
        }
        return new KnyazOrder(id, title, createdBy, items, totalReward, paidReward, createdAt, updated, description, completed);
    }

    public KnyazOrder withItemsAndPaidReward(List<KnyazOrderItem> newItems, long newPaidReward) {
        boolean allDone = !newItems.isEmpty() && newItems.stream().allMatch(KnyazOrderItem::isCompleted);
        return new KnyazOrder(id, title, createdBy, newItems, totalReward, newPaidReward, createdAt, respondents, description, allDone || completed);
    }

    public KnyazOrder withItems(List<KnyazOrderItem> newItems) {
        boolean allDone = !newItems.isEmpty() && newItems.stream().allMatch(KnyazOrderItem::isCompleted);
        return new KnyazOrder(id, title, createdBy, newItems, totalReward, paidReward, createdAt, respondents, description, allDone || completed);
    }
}
