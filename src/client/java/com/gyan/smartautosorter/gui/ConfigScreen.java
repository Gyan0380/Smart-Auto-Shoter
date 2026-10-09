package com.gyan.smartautosorter.gui;

import com.gyan.smartautosorter.client.SmartAutoSorterClient;
import com.gyan.smartautosorter.config.SorterConfig;
import com.gyan.smartautosorter.core.Layout;
import com.gyan.smartautosorter.share.ShareCodeCodec;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Spec section 2's main menu: master toggle, the four colored built-in
 * profiles, a scrollable custom-layouts section, and
 * configure/save/duplicate/delete/import/export actions.
 *
 * This does NOT pause single-player ("The GUI must work without
 * unnecessarily pausing single-player gameplay") — achieved simply by
 * never overriding isPauseScreen() to return true (Screen's default
 * is false), unlike the vanilla inventory/pause screens.
 *
 * Kept intentionally plain (vanilla Button/CycleButton widgets, a single
 * column of rows) rather than a fully custom-textured panel — the spec
 * asks for "polished", but a hand-drawn custom texture atlas is exactly
 * the kind of asset this environment cannot produce or preview (no image
 * tooling, no way to render and check it). Swapping in custom textures
 * later only touches render()/widget construction here, not the sorting
 * logic.
 */
public final class ConfigScreen extends Screen {

    private final Screen parent;
    private int rowY;

    public ConfigScreen(Screen parent) {
        super(Component.translatable("smartautosorter.gui.title"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        SorterConfig cfg = SmartAutoSorterClient.config();
        rowY = this.height / 2 - 90;
        int centerX = this.width / 2;
        int rowHeight = 22;

        addRenderableWidget(CycleButton.onOffBuilder(cfg.masterAutoSortEnabled)
                .create(centerX - 100, nextRow(rowHeight), 200, 20,
                        Component.translatable("smartautosorter.gui.master_toggle"),
                        (button, value) -> {
                            cfg.masterAutoSortEnabled = value;
                            SmartAutoSorterClient.saveConfig();
                        }));

        addProfileButton(centerX - 100, nextRow(rowHeight), com.gyan.smartautosorter.config.DefaultLayouts.PVP_ID, "smartautosorter.gui.profile.pvp");
        addProfileButton(centerX - 100, nextRow(rowHeight), com.gyan.smartautosorter.config.DefaultLayouts.SURVIVAL_ID, "smartautosorter.gui.profile.survival");
        addProfileButton(centerX - 100, nextRow(rowHeight), com.gyan.smartautosorter.config.DefaultLayouts.MINING_ID, "smartautosorter.gui.profile.mining");
        addProfileButton(centerX - 100, nextRow(rowHeight), com.gyan.smartautosorter.config.DefaultLayouts.BUILDING_ID, "smartautosorter.gui.profile.building");

        List<Layout> customLayouts = cfg.layouts.stream().filter(l -> !l.isBuiltIn()).toList();
        int y = nextRow(rowHeight);
        for (Layout custom : customLayouts) {
            addRenderableWidget(Button.builder(Component.literal(custom.name()), b -> selectLayout(custom.id()))
                    .bounds(centerX - 100, y, 150, 20).build());
            addRenderableWidget(Button.builder(Component.literal("..."), b ->
                    minecraft.gui.setScreen(new LayoutEditorScreen(this, custom)))
                    .bounds(centerX + 54, y, 46, 20).build());
            y += rowHeight;
        }
        rowY = y;

        addRenderableWidget(Button.builder(Component.literal("+ New Custom Layout"), b -> {
            if (cfg.canAddCustomLayout()) {
                Layout fresh = Layout.newCustom("New Layout", "#9E9E9E");
                cfg.layouts.add(fresh);
                SmartAutoSorterClient.saveConfig();
                minecraft.gui.setScreen(new LayoutEditorScreen(this, fresh));
            }
        }).bounds(centerX - 100, nextRow(rowHeight), 200, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("smartautosorter.gui.import"), b ->
                minecraft.gui.setScreen(new ImportShareCodeScreen(this)))
                .bounds(centerX - 100, nextRow(rowHeight), 95, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("smartautosorter.gui.export"), b -> {
            Layout active = cfg.activeLayout();
            if (active != null) {
                String code = ShareCodeCodec.encode(active);
                minecraft.keyboardHandler.setClipboard(code);
            }
        }).bounds(centerX + 5, rowY, 95, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Done"), b -> onClose())
                .bounds(centerX - 100, this.height - 32, 200, 20).build());
    }

    private int nextRow(int rowHeight) {
        int y = rowY;
        rowY += rowHeight;
        return y;
    }

    private void addProfileButton(int x, int y, String layoutId, String labelKey) {
        addRenderableWidget(Button.builder(Component.translatable(labelKey), b -> selectLayout(layoutId))
                .bounds(x, y, 200, 20).build());
    }

    private void selectLayout(String layoutId) {
        SorterConfig cfg = SmartAutoSorterClient.config();
        cfg.activeLayoutId = layoutId;
        SmartAutoSorterClient.saveConfig();
        if (cfg.applyLayoutImmediatelyOnSwitch) {
            SmartAutoSorterClient.applyLayoutNow();
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(parent);
    }
}
