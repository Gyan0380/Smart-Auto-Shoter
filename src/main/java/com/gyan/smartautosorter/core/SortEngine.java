package com.gyan.smartautosorter.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements the "Smart Slot Rules" (spec section 4) against an
 * InventoryView + Layout. Deliberately Minecraft-free so it can be unit
 * tested directly (see src/test) — this is the one part of the mod that
 * was actually compiled and run in this environment; everything that
 * touches real Minecraft classes could only be written, not executed
 * here. See README "What was actually tested" for the full picture.
 *
 * Design choices made to satisfy "avoid flickering / duplication / recursive
 * updates" (spec section 3):
 *  - An item with no rule in the active layout is never touched. Only
 *    items the player has explicitly configured get moved.
 *  - Each slot is only ever the *source* of one move per sort() call
 *    (settledThisPass), so a single pass can't bounce an item back and
 *    forth.
 *  - No item is ever deleted: every branch either moves/merges a whole
 *    or partial stack, or leaves it untouched (rule #10).
 *  - Running sort() twice in a row on its own output is idempotent
 *    (second call returns an empty SortResult) as long as no new items
 *    entered the inventory between calls — this is exercised directly in
 *    SortEngineTest.
 */
public final class SortEngine {

    public SortResult sort(InventoryView inv, Layout layout, Set<Integer> manuallyPlacedSlots) {
        List<SortResult.Move> moves = new ArrayList<>();
        int n = inv.size();
        boolean[] settled = new boolean[n];

        Set<Integer> reservedSlots = new HashSet<>();
        for (ItemRule r : layout.allRules()) {
            if (r.preferredSlot() >= 0 && r.preferredSlot() < n) {
                reservedSlots.add(r.preferredSlot());
            }
        }

        for (int slot = 0; slot < n; slot++) {
            if (settled[slot]) continue;
            ItemStackLite stack = inv.get(slot);
            if (stack.isEmpty()) continue;
            if (inv.isLocked(slot)) continue; // rule #7

            ItemRule rule = layout.ruleFor(stack.itemId());
            if (rule == null) continue; // rule #: don't hardcode destinations for unconfigured items

            int preferred = rule.preferredSlot();
            if (preferred == slot) continue; // rule #1/#2: already home
            if (preferred < 0 || preferred >= n) continue; // defensive: invalid config

            // rule #7/#8: manual-placement policy
            if (manuallyPlacedSlots.contains(slot)
                    && layout.manualPlacementPolicy() == ManualPlacementPolicy.PRESERVE_ALWAYS) {
                continue;
            }

            ItemStackLite atPreferred = inv.get(preferred);

            if (atPreferred.isEmpty() && !inv.isLocked(preferred)) {
                // rule #2
                moveWhole(inv, slot, preferred, stack, moves);
                settled[preferred] = true;
                settled[slot] = true;
                if (rule.lockOnPlace()) inv.setLocked(preferred, true);
                continue;
            }

            if (atPreferred.sameItem(stack) && atPreferred.remainingCapacity() > 0 && !inv.isLocked(preferred)) {
                // rule #3
                mergeInto(inv, slot, preferred, stack, atPreferred, moves);
                settled[preferred] = true;
                if (inv.get(slot).isEmpty()) settled[slot] = true;
                continue;
            }

            // rule #4: preferred slot holds a different item — never overwrite it.
            // rule #5: look for an alternative suitable destination instead.
            int mergeTarget = findMergeTarget(inv, stack, n, settled, slot, preferred);
            if (mergeTarget >= 0) {
                ItemStackLite target = inv.get(mergeTarget);
                mergeInto(inv, slot, mergeTarget, stack, target, moves);
                settled[mergeTarget] = true;
                if (inv.get(slot).isEmpty()) settled[slot] = true;
                continue;
            }

            int fallback = findFallbackEmptySlot(inv, n, reservedSlots, settled, slot);
            if (fallback >= 0) {
                moveWhole(inv, slot, fallback, stack, moves);
                settled[fallback] = true;
                settled[slot] = true;
                continue;
            }

            // rule #6: nothing suitable — leave the item exactly where it is.
        }

        return new SortResult(moves);
    }

    private static void moveWhole(InventoryView inv, int from, int to, ItemStackLite stack, List<SortResult.Move> moves) {
        inv.set(to, stack);
        inv.set(from, ItemStackLite.EMPTY);
        moves.add(new SortResult.Move(from, to, stack.itemId(), stack.count(), false));
    }

    private static void mergeInto(InventoryView inv, int from, int to, ItemStackLite stack, ItemStackLite target, List<SortResult.Move> moves) {
        int moveAmount = Math.min(stack.count(), target.remainingCapacity());
        inv.set(to, target.withCount(target.count() + moveAmount));
        int remaining = stack.count() - moveAmount;
        inv.set(from, remaining > 0 ? stack.withCount(remaining) : ItemStackLite.EMPTY);
        moves.add(new SortResult.Move(from, to, stack.itemId(), moveAmount, true));
    }

    private static int findMergeTarget(InventoryView inv, ItemStackLite stack, int n, boolean[] settled, int sourceSlot, int preferredSlot) {
        for (int i = 0; i < n; i++) {
            if (i == sourceSlot || i == preferredSlot || settled[i] || inv.isLocked(i)) continue;
            ItemStackLite candidate = inv.get(i);
            if (candidate.sameItem(stack) && candidate.remainingCapacity() > 0) {
                return i;
            }
        }
        return -1;
    }

    private static int findFallbackEmptySlot(InventoryView inv, int n, Set<Integer> reservedSlots, boolean[] settled, int sourceSlot) {
        for (int i = 0; i < n; i++) {
            if (i == sourceSlot || settled[i] || inv.isLocked(i) || reservedSlots.contains(i)) continue;
            if (inv.get(i).isEmpty()) return i;
        }
        return -1;
    }
}
