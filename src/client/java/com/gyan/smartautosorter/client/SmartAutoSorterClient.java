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
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.resources.Identifier;

import java.util.BitSet;
import java.util.Collections;
import java.util.List;

@Environment(EnvType.CLIENT)
public final class SmartAutoSorterClient implements ClientModInitializer {
    private static final int SORT_COOLDOWN_TICKS = 4;
    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(SmartAutoSorterMod.MOD_ID, "controls"));

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
        toggleKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.smartautosorter.toggle", InputConstants.Type.KEYSYM, config.toggleKeyCode, KEY_CATEGORY));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.smartautosorter.menu", InputConstants.Type.KEYSYM, config.menuKeyCode, KEY_CATEGORY));
        ClientTickEvents.END_CLIENT_TICK.register(SmartAutoSorterClient::onClientTick);
        SmartAutoSorterMod.LOGGER.info("Smart Auto Sorter client ready");
    }

    public static SorterConfig config() { return config; }
    public static void saveConfig() { config.save(configPath); }

    private static void onClientTick(Minecraft client) {
        while (toggleKey.consumeClick()) {
            config.masterAutoSortEnabled = !config.masterAutoSortEnabled;
            saveConfig();
        }
        while (menuKey.consumeClick()) {
            if (client.gui.screen() == null) client.gui.setScreen(new ConfigScreen(null));
        }
        if (!config.masterAutoSortEnabled) return;
        LocalPlayer player = client.player;
        if (player == null) return;
        if (client.gui.screen() != null) return;
        if (player.containerMenu != player.inventoryMenu) return;
        if (cooldown > 0) { cooldown--; return; }
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
        if (result.isNoOp()) { lastMoves = Collections.emptyList(); return; }
        MoveApplier.apply(player, result.moves());
        lastMoves = result.moves();
        cooldown = SORT_COOLDOWN_TICKS;
    }

    public static void applyLayoutNow() {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            cooldown = 0;
            runSortPass(client.player);
        }
    }
}
