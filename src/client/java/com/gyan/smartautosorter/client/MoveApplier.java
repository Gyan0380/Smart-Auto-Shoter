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

    private static int toMenuSlot(int sortableSlot) {
        return sortableSlot;
    }
}
