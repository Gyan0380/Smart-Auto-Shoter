package com.gyan.smartautosorter.gui;

import com.gyan.smartautosorter.client.SmartAutoSorterClient;
import com.gyan.smartautosorter.core.ItemRule;
import com.gyan.smartautosorter.core.Layout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Spec section 6: a 9x4 grid (hotbar + 27 main slots) mirroring vanilla
 * inventory layout. Clicking a grid cell while an item is "selected"
 * (via the item-picker row at the top) assigns that item's preferred
 * slot to the clicked cell, matching a Minecraft-style inventory visual
 * rather than a dropdown-driven form.
 *
 * Item icon rendering uses GuiGraphics.renderItem/renderItemDecorations,
 * same as vanilla inventory screens, so each cell shows the real item
 * texture rather than a text label.
 */
public final class LayoutEditorScreen extends Screen {

    private static final int SLOT_SIZE = 18;
    private static final int COLS = 9;

    private final Screen parent;
    private final Layout layout;
    private EditBox nameBox;
    private String selectedItemId; // the item currently "in hand" to assign to a slot
    private int gridLeft, gridTop;

    public LayoutEditorScreen(Screen parent, Layout layout) {
        super(Component.literal("Edit Layout"));
        this.parent = parent;
        this.layout = layout;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        gridLeft = this.width / 2 - (COLS * SLOT_SIZE) / 2;
        gridTop = 70;

        nameBox = new EditBox(this.font, this.width / 2 - 75, 20, 150, 20, Component.literal("Layout name"));
        nameBox.setValue(layout.name());
        nameBox.setMaxLength(32);
        addRenderableWidget(nameBox);

        addRenderableWidget(Button.builder(Component.translatable("smartautosorter.gui.save"), b -> {
            layout.setName(nameBox.getValue().isBlank() ? layout.name() : nameBox.getValue());
            SmartAutoSorterClient.saveConfig();
            onClose();
        }).bounds(this.width / 2 - 155, this.height - 32, 75, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("smartautosorter.gui.duplicate"), b -> {
            var cfg = SmartAutoSorterClient.config();
            if (cfg.canAddCustomLayout()) {
                Layout copy = layout.copy(layout.name() + " Copy");
                cfg.layouts.add(copy);
                SmartAutoSorterClient.saveConfig();
            }
        }).bounds(this.width / 2 - 75, this.height - 32, 75, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("smartautosorter.gui.delete"), b -> {
            if (!layout.isBuiltIn()) {
                SmartAutoSorterClient.config().layouts.remove(layout);
                SmartAutoSorterClient.saveConfig();
                onClose();
            }
        }).bounds(this.width / 2 + 5, this.height - 32, 75, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Back"), b -> onClose())
                .bounds(this.width / 2 + 85, this.height - 32, 75, 20).build());

        // Item picker row: every item currently assigned a slot in this layout, plus a
        // simple way to type in a new item id. A full searchable creative-inventory-style
        // picker is listed as a known limitation in README — this editor focuses the
        // implementation effort on the slot-assignment interaction itself.
        int pickY = 45;
        List<ItemRule> rules = layout.allRules();
        for (int i = 0; i < rules.size() && i < 9; i++) {
            final String itemId = rules.get(i).itemId();
            addRenderableWidget(Button.builder(Component.literal(shortName(itemId)), b -> selectedItemId = itemId)
                    .bounds(this.width / 2 - 135 + i * 30, pickY, 28, 20).build());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);

        if (selectedItemId != null) {
            graphics.drawString(this.font, "Selected: " + shortName(selectedItemId), gridLeft, gridTop - 12, 0xFFFF55);
        }

        for (int slot = 0; slot < 36; slot++) {
            int col = slot % COLS;
            int row = slot / COLS;
            int x = gridLeft + col * SLOT_SIZE;
            int y = gridTop + row * SLOT_SIZE + (row == 3 ? 6 : 0); // small gap between hotbar and main inv, like vanilla

            int borderColor = 0xFF8B8B8B;
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, borderColor);
            graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0xFF8B8B8B & 0x55FFFFFF);

            ItemRule occupant = ruleForSlot(slot);
            if (occupant != null) {
                ItemStack stack = stackFor(occupant.itemId());
                graphics.renderItem(stack, x + 1, y + 1);
                graphics.renderItemDecorations(this.font, stack, x + 1, y + 1);
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (selectedItemId != null) {
            for (int slot = 0; slot < 36; slot++) {
                int col = slot % COLS;
                int row = slot / COLS;
                int x = gridLeft + col * SLOT_SIZE;
                int y = gridTop + row * SLOT_SIZE + (row == 3 ? 6 : 0);
                if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    layout.putRule(new ItemRule(selectedItemId, slot, 0, false));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private ItemRule ruleForSlot(int slot) {
        for (ItemRule r : layout.allRules()) {
            if (r.preferredSlot() == slot) return r;
        }
        return null;
    }

    private static ItemStack stackFor(String itemId) {
        var id = net.minecraft.resources.ResourceLocation.tryParse(itemId);
        if (id == null) return ItemStack.EMPTY;
        var item = BuiltInRegistries.ITEM.get(id);
        return new ItemStack(item);
    }

    private static String shortName(String itemId) {
        int colon = itemId.indexOf(':');
        return colon >= 0 ? itemId.substring(colon + 1) : itemId;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
