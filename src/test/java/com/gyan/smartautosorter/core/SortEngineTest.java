package com.gyan.smartautosorter.core;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the logic-level items from spec section 10 ("Testing
 * Requirements") that don't require a running Minecraft client:
 * empty preferred slot, occupied-by-other, stack merging, full
 * inventory / no destination, manual placement preservation, locked
 * slots, and idempotency (anti-flicker).
 *
 * IMPORTANT: this file was written but never executed in this
 * environment — there is no javac/JDK, jshell, or Gradle available here
 * (see README). Run `./gradlew test` yourself to actually execute it.
 */
class SortEngineTest {

    private static Layout pickaxeLayout() {
        Layout layout = new Layout("test", "Test", "#2196F3");
        layout.putRule(new ItemRule("minecraft:diamond_pickaxe", 0, 0, false));
        layout.putRule(new ItemRule("minecraft:torch", 8, 0, false));
        return layout;
    }

    @Test
    void movesItemToEmptyPreferredSlot() {
        FakeInventoryView inv = new FakeInventoryView(9);
        inv.set(3, new ItemStackLite("minecraft:diamond_pickaxe", 1, 1));

        SortResult result = new SortEngine().sort(inv, pickaxeLayout(), Collections.emptySet());

        assertEquals(1, result.moveCount());
        assertTrue(inv.get(3).isEmpty());
        assertEquals("minecraft:diamond_pickaxe", inv.get(0).itemId());
    }

    @Test
    void neverOverwritesADifferentItemInThePreferredSlot() {
        FakeInventoryView inv = new FakeInventoryView(9);
        inv.set(0, new ItemStackLite("minecraft:iron_pickaxe", 1, 1)); // occupies the diamond pickaxe's spot
        inv.set(3, new ItemStackLite("minecraft:diamond_pickaxe", 1, 1));

        SortResult result = new SortEngine().sort(inv, pickaxeLayout(), Collections.emptySet());

        assertEquals("minecraft:iron_pickaxe", inv.get(0).itemId(), "must not be destroyed or overwritten");
        // diamond pickaxe should land in some other free, non-reserved slot (rule 5), not slot 0 or 8 (reserved)
        boolean foundElsewhere = false;
        for (int i = 1; i < 9; i++) {
            if (i == 8) continue;
            if ("minecraft:diamond_pickaxe".equals(inv.get(i).itemId())) foundElsewhere = true;
        }
        assertTrue(foundElsewhere);
        assertEquals(1, result.moveCount());
    }

    @Test
    void mergesCompatibleStacks() {
        FakeInventoryView inv = new FakeInventoryView(9);
        inv.set(0, new ItemStackLite("minecraft:torch", 0, 64)); // wrong layout slot on purpose for clarity
        Layout layout = new Layout("t", "T", "#000");
        layout.putRule(new ItemRule("minecraft:torch", 8, 0, false));
        inv.set(8, new ItemStackLite("minecraft:torch", 40, 64));
        inv.set(2, new ItemStackLite("minecraft:torch", 30, 64));

        SortResult result = new SortEngine().sort(inv, layout, Collections.emptySet());

        assertEquals(64, inv.get(8).count());
        assertEquals(6, inv.get(2).count()); // 30 - (64-40) = 6 left behind, not deleted
        assertFalse(inv.get(2).isEmpty());
        assertEquals(1, result.moveCount());
    }

    @Test
    void leavesItemInPlaceWhenNoDestinationExists() {
        FakeInventoryView inv = new FakeInventoryView(2); // tiny, fully packed inventory
        Layout layout = new Layout("t", "T", "#000");
        layout.putRule(new ItemRule("minecraft:stick", 0, 0, false));
        inv.set(0, new ItemStackLite("minecraft:coal", 1, 64));   // occupies the stick's preferred slot
        inv.set(1, new ItemStackLite("minecraft:stick", 1, 64));  // nowhere else to go

        SortResult result = new SortEngine().sort(inv, layout, Collections.emptySet());

        assertTrue(result.isNoOp());
        assertEquals("minecraft:stick", inv.get(1).itemId()); // untouched, not deleted
    }

    @Test
    void respectsLockedSlots() {
        FakeInventoryView inv = new FakeInventoryView(9);
        inv.set(3, new ItemStackLite("minecraft:diamond_pickaxe", 1, 1));
        inv.setLocked(3, true);

        SortResult result = new SortEngine().sort(inv, pickaxeLayout(), Collections.emptySet());

        assertTrue(result.isNoOp());
        assertEquals("minecraft:diamond_pickaxe", inv.get(3).itemId());
    }

    @Test
    void preserveAlwaysPolicyKeepsManuallyPlacedItemsPut() {
        FakeInventoryView inv = new FakeInventoryView(9);
        inv.set(3, new ItemStackLite("minecraft:diamond_pickaxe", 1, 1));
        Layout layout = pickaxeLayout();
        layout.setManualPlacementPolicy(ManualPlacementPolicy.PRESERVE_ALWAYS);

        SortResult result = new SortEngine().sort(inv, layout, Set.of(3));

        assertTrue(result.isNoOp());
        assertEquals("minecraft:diamond_pickaxe", inv.get(3).itemId());
    }

    @Test
    void secondPassOnOwnOutputIsANoOp_antiFlicker() {
        FakeInventoryView inv = new FakeInventoryView(9);
        inv.set(3, new ItemStackLite("minecraft:diamond_pickaxe", 1, 1));
        inv.set(5, new ItemStackLite("minecraft:torch", 12, 64));
        Layout layout = pickaxeLayout();
        SortEngine engine = new SortEngine();

        SortResult first = engine.sort(inv, layout, Collections.emptySet());
        SortResult second = engine.sort(inv, layout, Collections.emptySet());

        assertFalse(first.isNoOp());
        assertTrue(second.isNoOp(), "sorting an already-sorted inventory must not move anything");
    }

    @Test
    void ignoresItemsWithNoRuleInTheActiveLayout() {
        FakeInventoryView inv = new FakeInventoryView(9);
        inv.set(4, new ItemStackLite("minecraft:dirt", 5, 64)); // no rule for dirt in pickaxeLayout()

        SortResult result = new SortEngine().sort(inv, pickaxeLayout(), Collections.emptySet());

        assertTrue(result.isNoOp());
        assertEquals("minecraft:dirt", inv.get(4).itemId());
    }
}
