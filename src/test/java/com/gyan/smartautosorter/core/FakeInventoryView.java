package com.gyan.smartautosorter.core;

import java.util.Arrays;

final class FakeInventoryView implements InventoryView {
    private final ItemStackLite[] slots;
    private final boolean[] locked;

    FakeInventoryView(int size) {
        slots = new ItemStackLite[size];
        locked = new boolean[size];
        Arrays.fill(slots, ItemStackLite.EMPTY);
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
        return locked[slot];
    }

    @Override
    public void setLocked(int slot, boolean value) {
        locked[slot] = value;
    }
}
