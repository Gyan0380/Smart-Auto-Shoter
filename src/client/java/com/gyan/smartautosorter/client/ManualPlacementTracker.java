package com.gyan.smartautosorter.client;

import com.gyan.smartautosorter.core.ItemStackLite;
import com.gyan.smartautosorter.core.SortResult;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Heuristic: a slot is "manually placed" if its contents changed since
 * the last sort pass, and that change was not one of the moves our own
 * MoveApplier just made. Reset to "not manual" once the slot empties out
 * (the item was consumed, dropped, or crafted away — there's nothing
 * left to preserve a placement for).
 *
 * This is a best-effort signal, not a perfect one — e.g. a server-side
 * effect that silently swaps two slot contents in the same tick as a
 * coincidental player action could be misread. That's an acceptable
 * trade-off: the worst case is a manually-placed item gets treated as
 * sortable slightly early, which rule #4 (never overwrite a different
 * item) still protects against causing any damage.
 */
final class ManualPlacementTracker {

    private ItemStackLite[] lastSnapshot = new ItemStackLite[PlayerInventoryView.SORTABLE_SIZE];
    private final Set<Integer> manualSlots = new HashSet<>();

    ManualPlacementTracker() {
        java.util.Arrays.fill(lastSnapshot, ItemStackLite.EMPTY);
    }

    /** Call once per tick, BEFORE running the sort pass, with the current real-inventory contents. */
    void observe(ItemStackLite[] currentSnapshot, List<SortResult.Move> movesAppliedLastTick) {
        Set<Integer> justSorted = new HashSet<>();
        for (SortResult.Move m : movesAppliedLastTick) {
            justSorted.add(m.toSlot());
            justSorted.add(m.fromSlot());
        }

        for (int slot = 0; slot < currentSnapshot.length; slot++) {
            ItemStackLite before = lastSnapshot[slot];
            ItemStackLite after = currentSnapshot[slot];

            if (after.isEmpty()) {
                manualSlots.remove(slot);
                continue;
            }
            if (justSorted.contains(slot)) {
                continue; // our own move — not a manual placement signal either way
            }
            if (!after.equals(before)) {
                manualSlots.add(slot);
            }
        }

        lastSnapshot = currentSnapshot.clone();
    }

    Set<Integer> manualSlots() {
        return manualSlots;
    }

    void clearSlot(int slot) {
        manualSlots.remove(slot);
    }
}
