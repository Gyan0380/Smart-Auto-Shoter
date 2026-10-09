package com.gyan.smartautosorter.core;

/**
 * Abstraction over "some inventory of N slots" so the sorting engine
 * (SortEngine) never touches Minecraft classes directly. In production,
 * PlayerInventoryView / ContainerInventoryView wrap the real
 * net.minecraft.world.entity.player.Inventory / Container Minecraft
 * objects. In tests, FakeInventoryView is used instead.
 *
 * This split is what makes it possible to unit-test the sorting rules
 * (rule set #4 in the spec) without a running Minecraft client — which
 * matters a lot here, because this environment has no way to launch
 * Minecraft or run a Fabric dev client to test against the real game.
 */
public interface InventoryView {

    int size();

    /** Null/empty-stack semantics: implementations should never return null; use ItemStackLite.EMPTY. */
    ItemStackLite get(int slot);

    void set(int slot, ItemStackLite stack);

    /**
     * True if the player (or an earlier sort pass) deliberately placed an
     * item in this slot and it should not be moved by automatic sorting
     * unless the manual-placement policy says otherwise. Rule #7/#8.
     */
    boolean isLocked(int slot);

    void setLocked(int slot, boolean locked);
}
