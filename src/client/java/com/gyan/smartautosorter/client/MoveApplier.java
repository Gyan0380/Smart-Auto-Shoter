package com.gyan.smartautosorter.client;

import com.gyan.smartautosorter.SmartAutoSorterMod;
import com.gyan.smartautosorter.core.SortResult;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;

import java.util.List;

/**
 * Turns SortEngine's abstract Move list into real inventory actions.
 *
 * Deliberately reuses the exact same client -> server path vanilla uses
 * when a player drags an item in their inventory screen:
 * `player.inventoryMenu.clicked(slot, button, clickType, player)`. That
 * method already:
 *  - sends the resulting ServerboundContainerClickPacket to the server,
 *  - applies the client-side prediction locally,
 *  - gets corrected automatically if the server disagrees (standard
 *    vanilla reconciliation — satisfies "handle rejected or delayed
 *    server responses gracefully" without this mod reimplementing it).
 *
 * container id 0 is always the player's own inventory menu, whether or
 * not any screen is currently open, so this works for "sort my hotbar
 * after picking something off the ground" just as well as while a chest
 * is open.
 *
 * Partial-stack merges (SortResult.Move#merged() with count less than
 * the full source stack) are approximated with repeated single-item
 * right-click pulls, mirroring how a player would manually top off a
 * stack. This is simple and safe but slower than a hypothetical
 * single-packet "move N items" operation, which vanilla's click protocol
 * doesn't actually expose. For a full-stack move we do a single
 * pickup/place pair instead.
 */
final class MoveApplier {

    private static final int PLAYER_INVENTORY_CONTAINER_ID = 0;

    private MoveApplier() {}

    static void apply(LocalPlayer player, List<SortResult.Move> moves) {
        for (SortResult.Move move : moves) {
            try {
                applyOne(player, move);
            } catch (Exception e) {
                // A single move failing (e.g. the server already changed this slot
                // between our snapshot and now) must never crash the client or corrupt
                // other slots — skip it, the next tick's pass will re-evaluate from
                // the real, current state.
                SmartAutoSorterMod.LOGGER.warn("Skipped a sort move ({} -> {}) after an error", move.fromSlot(), move.toSlot(), e);
            }
        }
    }

    private static void applyOne(LocalPlayer player, SortResult.Move move) {
        int from = toMenuSlot(move.fromSlot());
        int to = toMenuSlot(move.toSlot());

        if (!move.merged()) {
            // full stack swap/placement: pick up source, place at destination (and, if the
            // destination wasn't empty, this naturally becomes a vanilla swap instead of
            // overwriting it — the server enforces this, matching rule #4).
            player.inventoryMenu.clicked(from, 0, ClickType.PICKUP, player);
            player.inventoryMenu.clicked(to, 0, ClickType.PICKUP, player);
            return;
        }

        // partial merge: pull `count` single items from the source stack into the
        // destination stack via repeated right-clicks (button 1 = secondary click).
        for (int i = 0; i < move.count(); i++) {
            player.inventoryMenu.clicked(from, 1, ClickType.PICKUP, player);
            player.inventoryMenu.clicked(to, 1, ClickType.PICKUP, player);
        }
        // whatever is left on the cursor (shouldn't be anything for a correctly
        // computed partial merge, but never leave items floating on the cursor) goes
        // back to the source slot.
        player.inventoryMenu.clicked(from, 0, ClickType.PICKUP, player);
    }

    /**
     * SortEngine/PlayerInventoryView index 0-35 as hotbar(0-8)+main(9-35), matching
     * vanilla Inventory's own indexing. The player's inventoryMenu (AbstractContainerMenu)
     * historically uses the SAME 0-35 numbering for its own slot list for these slots
     * (armor/offhand/crafting occupy higher indices) — verify this against 26.2's actual
     * Inventory/InventoryMenu slot layout before relying on it; it has been stable across
     * many Minecraft versions but was not re-checked here (no network/decompiled jar access).
     */
    private static int toMenuSlot(int sortableSlot) {
        return sortableSlot;
    }
}
