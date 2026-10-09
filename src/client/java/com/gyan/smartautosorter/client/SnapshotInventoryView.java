package com.gyan.smartautosorter.client;

import com.gyan.smartautosorter.core.InventoryView;
import com.gyan.smartautosorter.core.ItemStackLite;

import java.util.BitSet;

/**
 * A plain in-memory copy of the player's sortable slots (0-35), built
 * fresh each sort pass from PlayerInventoryView and handed to
 * SortEngine. SortEngine mutates this freely; afterwards we only care
 * about the SortResult.Move list it returns, which MoveApplier replays
 * against the real Inventory using real ItemStack references. The real
 * inventory is never mutated directly by the engine — see
 * PlayerInventoryView for why.
 */
final class SnapshotInventoryView implements InventoryView {
    private final ItemStackLite[] slots;
    private final BitSet locked;

    SnapshotInventoryView(PlayerInventoryView real, BitSet lockedSlots) {
        this.slots = new ItemStackLite[PlayerInventoryView.SORTABLE_SIZE];
        for (int i = 0; i < slots.length; i++) {
            slots[i] = real.get(i);
        }
        this.locked = lockedSlots;
    }

    @Override
    public int size() {
        return slots.length;
    }

    @Override
    public ItemStackLite get(int slot) {
        return slots[slot];
    }

    @Override
    public void set(int slot, ItemStackLite stack) {
        slots[slot] = stack;
    }

    @Override
    public boolean isLocked(int slot) {
        return locked.get(slot);
    }

    @Override
    public void setLocked(int slot, boolean value) {
        locked.set(slot, value);
    }
}
