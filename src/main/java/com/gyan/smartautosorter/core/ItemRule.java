package com.gyan.smartautosorter.core;

import java.util.Objects;

/** One entry in a Layout: "this item type prefers this slot, at this priority." Rule #1/#2/#9/#10. */
public final class ItemRule {
    private final String itemId;
    private final int preferredSlot;
    private final int priority;        // higher wins when two item types compete for the same preferred slot
    private final boolean lockOnPlace; // rule #7: once the sorter places it, lock the slot

    public ItemRule(String itemId, int preferredSlot, int priority, boolean lockOnPlace) {
        this.itemId = itemId;
        this.preferredSlot = preferredSlot;
        this.priority = priority;
        this.lockOnPlace = lockOnPlace;
    }

    public String itemId() {
        return itemId;
    }

    public int preferredSlot() {
        return preferredSlot;
    }

    public int priority() {
        return priority;
    }

    public boolean lockOnPlace() {
        return lockOnPlace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemRule itemRule)) return false;
        return preferredSlot == itemRule.preferredSlot && Objects.equals(itemId, itemRule.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, preferredSlot);
    }
}
