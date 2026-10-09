package com.gyan.smartautosorter.gui;

import com.gyan.smartautosorter.client.SmartAutoSorterClient;
import com.gyan.smartautosorter.core.ItemRule;
import com.gyan.smartautosorter.core.Layout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.List;

public final class LayoutEditorScreen extends Screen {
    private static final int SLOT_SIZE = 18;
    private static final int COLS = 9;
    private final Screen parent;
    private final Layout layout;
    private EditBox nameBox;
    private String selectedCategory = "BLOCKS";
    private int gridLeft, gridTop;

    public LayoutEditorScreen(Screen parent, Layout layout) {
        super(Component.literal("Edit Layout"));
        this.parent = parent;
        this.layout = layout;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    protected void init() {
        gridLeft = this.width / 2 - (COLS * SLOT_SIZE) / 2;
        gridTop = 100;
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

        addRenderableWidget(Button.builder(Component.literal("Category: " + selectedCategory + "  (click to change)"), b -> {
            String[] categories = {"BLOCKS", "TOOLS", "WEAPONS", "FOOD", "OTHER"};
            int current = java.util.Arrays.asList(categories).indexOf(selectedCategory);
            selectedCategory = categories[(current + 1) % categories.length];
            b.setMessage(Component.literal("Category: " + selectedCategory + "  (click to change)"));
        }).bounds(this.width / 2 - 100, 48, 200, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear slot assignment"), b -> {
            // Clear all category assignments from the slot currently under the mouse is not possible via a button;
            // right-clicking a slot below clears that slot.
        }).bounds(this.width / 2 - 100, 72, 200, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        graphics.text(this.font, "Category " + selectedCategory + ": click slots to assign; right-click to clear",
                gridLeft - 20, gridTop - 12, 0xFFFFFF55, true);
        for (int slot = 0; slot < 36; slot++) {
            int col = slot % COLS;
            int row = slot / COLS;
            int x = gridLeft + col * SLOT_SIZE;
            int y = gridTop + row * SLOT_SIZE + (row == 3 ? 6 : 0);
            String assigned = categoryForSlot(slot);
            int tint = assigned == null ? 0xFF555555 : categoryColor(assigned);
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, tint);
            graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x558B8B8B);
            graphics.text(this.font, Integer.toString(slot + 1), x + 1, y + 1, 0xFFFFFFFF, true);
            if (assigned != null) graphics.text(this.font, assigned.substring(0, 1), x + 6, y + 7, 0xFFFFFFFF, true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        for (int slot = 0; slot < 36; slot++) {
                int col = slot % COLS;
                int row = slot / COLS;
                int x = gridLeft + col * SLOT_SIZE;
                int y = gridTop + row * SLOT_SIZE + (row == 3 ? 6 : 0);
                if (mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE) {
                    if (event.button() == 1) layout.unassignCategorySlot(slot);
                    else layout.assignCategorySlot(selectedCategory, slot);
                    SmartAutoSorterClient.saveConfig();
                    return true;
                }
            }
        return super.mouseClicked(event, doubleClick);
    }

    private String categoryForSlot(int slot) {
        for (var entry : layout.categorySlots().entrySet()) if (entry.getValue().contains(slot)) return entry.getKey();
        ItemRule rule = ruleForSlot(slot);
        return rule == null ? null : Layout.categoryForItem(rule.itemId());
    }

    private ItemRule ruleForSlot(int slot) {
        for (ItemRule r : layout.allRules()) if (r.preferredSlot() == slot) return r;
        return null;
    }

    private static ItemStack stackFor(String itemId) {
        Identifier id;
        try { id = Identifier.parse(itemId); }
        catch (IllegalArgumentException ex) { return ItemStack.EMPTY; }
        var item = BuiltInRegistries.ITEM.get(id);
        return item.map(ItemStack::new).orElse(ItemStack.EMPTY);
    }

    private static int categoryColor(String category) {
        return switch (category.toUpperCase(java.util.Locale.ROOT)) {
            case "BLOCKS" -> 0xFF4B9A61;
            case "TOOLS" -> 0xFF4E91D9;
            case "WEAPONS" -> 0xFFE05A5A;
            case "FOOD" -> 0xFFE5B24C;
            case "OTHER" -> 0xFF8B70B5;
            default -> 0xFF555555;
        };
    }

    private static String shortName(String itemId) {
        int colon = itemId.indexOf(':');
        return colon >= 0 ? itemId.substring(colon + 1) : itemId;
    }

    @Override
    public void onClose() { this.minecraft.gui.setScreen(parent); }
}
