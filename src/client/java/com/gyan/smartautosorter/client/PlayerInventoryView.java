package com.gyan.smartautosorter.client;

import com.gyan.smartautosorter.core.InventoryView;
import com.gyan.smartautosorter.core.ItemStackLite;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.BitSet;

/**
 * Wraps the local player's Inventory so SortEngine can operate on it
 * without ever importing a Minecraft class itself. Only covers the
 * hotbar + main inventory (slots 0-35) — armor and offhand are
 * deliberately excluded from automatic sorting (moving worn armor around
 * mid-sort is the kind of "unsafe inventory operation" rule #10 rules out).
 *
 * NOTE ON MAPPINGS: written against the Inventory/ItemStack class and
 * method names used by Mojang mappings for the last several Minecraft
 * releases (getItem/setItem/getMaxStackSize/isEmpty/getCount). Minecraft
 * 26.2 dropped obfuscation and standardized on Mojang mappings, so these
 * names should still be correct, but this was not checked against the
 * real 26.2 client jar (no network access here) — if a method got
 * renamed in 26.x, this is the file to fix first.
 */
public final class PlayerInventoryView implements InventoryView {

    public static final int SORTABLE_SIZE = 36; // hotbar (0-8) + main inventory (9-35)

    private final Inventory inventory;
    private final BitSet locked;

    public PlayerInventoryView(Inventory inventory, BitSet lockedSlots) {
        this.inventory = inventory;
        this.locked = lockedSlots;
    }

    @Override
    public int size() {
        return SORTABLE_SIZE;
    }

    @Override
    public ItemStackLite get(int slot) {
        ItemStack stack = inventory.getItem(slot);
        if (stack.isEmpty()) return ItemStackLite.EMPTY;
        String id = itemIdOf(stack);
        return new ItemStackLite(id, stack.getCount(), stack.getMaxStackSize());
    }

    /**
     * NOT used to actually mutate inventory state in production. SortEngine runs
     * against a detached ItemStackLite snapshot (see SnapshotInventoryView), and
     * SmartAutoSorterClient replays the resulting SortResult.Move list against the
     * real Inventory using vanilla's own container-click code path (see
     * MoveApplier), so real ItemStacks — and their NBT/data components — are never
     * reconstructed from a bare item id. This method exists only to satisfy the
     * InventoryView interface for code that genuinely does want a live view (none,
     * currently); it intentionally throws so it can't silently cause data loss.
     */
    @Override
    public void set(int slot, ItemStackLite lite) {
        throw new UnsupportedOperationException(
            "PlayerInventoryView is read-only by design — moves are applied via MoveApplier, "
                + "which uses real ItemStack references so NBT/data components survive a move. "
                + "See SmartAutoSorterClient.runSortPass().");
    }

    @Override
    public boolean isLocked(int slot) {
        return locked.get(slot);
    }

    @Override
    public void setLocked(int slot, boolean value) {
        locked.set(slot, value);
    }

    public static String itemIdOf(ItemStack stack) {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }
}
