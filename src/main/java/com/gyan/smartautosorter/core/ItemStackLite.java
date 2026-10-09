package com.gyan.smartautosorter.core;

import java.util.Objects;

/**
 * Minimal, Minecraft-independent stand-in for net.minecraft.world.item.ItemStack.
 * Production code (PlayerInventoryView etc.) converts real ItemStacks to/from
 * this on each sort pass. Keeping SortEngine's input/output in terms of this
 * type — rather than the real ItemStack — is what lets core/ compile and run
 * as plain Java with zero Minecraft/Fabric classpath, which is the only kind
 * of code this environment can actually compile and execute to verify logic
 * (see README "What was actually tested").
 */
public final class ItemStackLite {

    public static final ItemStackLite EMPTY = new ItemStackLite("minecraft:air", 0, Integer.MAX_VALUE);

    private final String itemId;
    private final int count;
    private final int maxStackSize;

    public ItemStackLite(String itemId, int count, int maxStackSize) {
        this.itemId = itemId;
        this.count = count;
        this.maxStackSize = maxStackSize;
    }

    public String itemId() {
        return itemId;
    }

    public int count() {
        return count;
    }

    public int maxStackSize() {
        return maxStackSize;
    }

    public boolean isEmpty() {
        return count <= 0 || "minecraft:air".equals(itemId);
    }

    public boolean sameItem(ItemStackLite other) {
        return !isEmpty() && !other.isEmpty() && Objects.equals(itemId, other.itemId);
    }

    public int remainingCapacity() {
        return Math.max(0, maxStackSize - count);
    }

    public ItemStackLite withCount(int newCount) {
        return new ItemStackLite(itemId, newCount, maxStackSize);
    }

    @Override
    public String toString() {
        return isEmpty() ? "EMPTY" : count + "x" + itemId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ItemStackLite that)) return false;
        return count == that.count && Objects.equals(itemId, that.itemId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemId, count);
    }
}
