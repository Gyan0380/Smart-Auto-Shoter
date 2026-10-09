package com.gyan.smartautosorter.client;

import com.gyan.smartautosorter.SmartAutoSorterMod;
import com.gyan.smartautosorter.config.SorterConfig;
import com.gyan.smartautosorter.core.ItemStackLite;
import com.gyan.smartautosorter.core.Layout;
import com.gyan.smartautosorter.core.SortEngine;
import com.gyan.smartautosorter.core.SortResult;
import com.gyan.smartautosorter.gui.ConfigScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.util.InputConstants;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * Wiring for everything spec section 3 describes as "detect inventory
 * changes": rather than mixin-ing into every possible source of an
 * inventory change (pickup, chest transfer, crafting result, server
 * push), this polls the player's sortable slots once per client tick and
 * diffs against the previous tick. That single mechanism naturally
 * covers all of those cases — anything that changed a slot gets picked
 * up the same way — without needing a separate mixin per code path, and
 * without risking recursive/duplicate triggers from several mixins
 * firing for the same underlying change.
 *
 * A short cooldown after each applied sort pass (SORT_COOLDOWN_TICKS)
 * exists purely to let the server's reconciliation for the previous
 * pass's clicks land before we read the inventory again — this is what
 * "avoid ... excessive network requests" / "avoid sending repeated or
 * excessive inventory packets" (section 8) means in practice here.
 */
@Environment(EnvType.CLIENT)
public final class SmartAutoSorterClient implements ClientModInitializer {

    private static final int SORT_COOLDOWN_TICKS = 4;

    public static KeyMapping toggleKey;
    public static KeyMapping menuKey;

    private static SorterConfig config;
    private static java.nio.file.Path configPath;
    private static final BitSet lockedSlots = new BitSet(PlayerInventoryView.SORTABLE_SIZE);
    private static final ManualPlacementTracker manualTracker = new ManualPlacementTracker();
    private static final SortEngine engine = new SortEngine();

    private static int cooldown = 0;
    private static List<SortResult.Move> lastMoves = Collections.emptyList();

    @Override
    public void onInitializeClient() {
        configPath = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getConfigDir().resolve(SmartAutoSorterMod.MOD_ID).resolve("config.json");
        config = SorterConfig.load(configPath);

        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.smartautosorter.toggle", InputConstants.Type.KEYSYM,
                config.toggleKeyCode, "key.category.smartautosorter"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.smartautosorter.menu", InputConstants.Type.KEYSYM,
                config.menuKeyCode, "key.category.smartautosorter"));

        ClientTickEvents.END_CLIENT_TICK.register(SmartAutoSorterClient::onClientTick);

        SmartAutoSorterMod.LOGGER.info("Smart Auto Sorter client ready");
    }

    public static SorterConfig config() {
        return config;
    }

    public static void saveConfig() {
        config.save(configPath);
    }

    private static void onClientTick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            config.masterAutoSortEnabled = !config.masterAutoSortEnabled;
            saveConfig();
        }
        while (menuKey.consumeClick()) {
            if (client.screen == null) {
                client.setScreen(new ConfigScreen(null));
            }
        }

        if (!config.masterAutoSortEnabled) return;
        LocalPlayer player = client.player;
        if (player == null) return;

        // Don't sort while the player (or this mod's own GUI) has a container screen
        // open and mid-interaction — fighting the player's cursor item is exactly the
        // kind of "flickering"/unsafe operation the spec warns against.
        if (client.screen != null) return;
        if (player.containerMenu != player.inventoryMenu) return;

        if (cooldown > 0) {
            cooldown--;
            return;
        }

        runSortPass(player);
    }

    private static void runSortPass(LocalPlayer player) {
        Layout layout = config.activeLayout();
        if (layout == null) return;

        PlayerInventoryView real = new PlayerInventoryView(player.getInventory(), lockedSlots);

        ItemStackLite[] currentSnapshot = new ItemStackLite[PlayerInventoryView.SORTABLE_SIZE];
        for (int i = 0; i < currentSnapshot.length; i++) currentSnapshot[i] = real.get(i);

        manualTracker.observe(currentSnapshot, lastMoves);

        SnapshotInventoryView working = new SnapshotInventoryView(real, lockedSlots);
        SortResult result = engine.sort(working, layout, manualTracker.manualSlots());

        if (result.isNoOp()) {
            lastMoves = Collections.emptyList();
            return;
        }

        MoveApplier.apply(player, result.moves());
        lastMoves = result.moves();
        cooldown = SORT_COOLDOWN_TICKS;
    }

    /** Called by the Layout switch UI when "apply to existing inventory" is chosen (spec section 5). */
    public static void applyLayoutNow() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            cooldown = 0;
            runSortPass(client.player);
        }
    }
}
