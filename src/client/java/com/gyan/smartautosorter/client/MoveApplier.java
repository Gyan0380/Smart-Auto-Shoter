package com.gyan.smartautosorter.client;

import com.gyan.smartautosorter.SmartAutoSorterMod;
import com.gyan.smartautosorter.core.SortResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;

import java.util.List;

final class MoveApplier {
    private MoveApplier() {}

    static void apply(LocalPlayer player, List<SortResult.Move> moves) {
        for (SortResult.Move move : moves) {
            try {
                applyOne(player, move);
            } catch (Exception e) {
                SmartAutoSorterMod.LOGGER.warn("Skipped a sort move ({} -> {}) after an error",
                        move.fromSlot(), move.toSlot(), e);
            }
        }
    }

    private static void applyOne(LocalPlayer player, SortResult.Move move) {
        int from = toMenuSlot(move.fromSlot());
        int to = toMenuSlot(move.toSlot());
        if (!move.merged()) {
            click(player, from, 0);
            click(player, to, 0);
            return;
        }
        for (int i = 0; i < move.count(); i++) {
            click(player, from, 1);
            click(player, to, 1);
        }
        click(player, from, 0);
    }

    private static void click(LocalPlayer player, int slot, int button) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.gameMode == null) return;
        minecraft.gameMode.handleContainerInput(
                player.inventoryMenu.containerId, slot, button, ContainerInput.PICKUP, player);
    }

    /**
     * InventoryView indices are 0-8 for the hotbar and 9-35 for the main
     * inventory. InventoryMenu uses slots 36-44 for the hotbar, while main
     * inventory slots keep indices 9-35. Passing hotbar indices through
     * unchanged would click the crafting/armor slots instead of the hotbar.
     */
    private static int toMenuSlot(int sortableSlot) {
        if (sortableSlot < 0 || sortableSlot >= PlayerInventoryView.SORTABLE_SIZE) {
            throw new IllegalArgumentException("Invalid sortable inventory slot: " + sortableSlot);
        }
        return sortableSlot < 9 ? sortableSlot + 36 : sortableSlot;
    }
}
