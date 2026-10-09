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
    private String selectedItemId;
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

        int pickY = 45;
        List<ItemRule> rules = layout.allRules();
        for (int i = 0; i < rules.size() && i < 9; i++) {
            final String itemId = rules.get(i).itemId();
            addRenderableWidget(Button.builder(Component.literal(shortName(itemId)), b -> selectedItemId = itemId)
                    .bounds(this.width / 2 - 135 + i * 30, pickY, 28, 20).build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        if (selectedItemId != null) {
            graphics.text(this.font, "Selected: " + shortName(selectedItemId), gridLeft, gridTop - 12, 0xFFFFFF55, true);
        }
        for (int slot = 0; slot < 36; slot++) {
            int col = slot % COLS;
            int row = slot / COLS;
            int x = gridLeft + col * SLOT_SIZE;
            int y = gridTop + row * SLOT_SIZE + (row == 3 ? 6 : 0);
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, 0xFF8B8B8B);
            graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x558B8B8B);
            ItemRule occupant = ruleForSlot(slot);
            if (occupant != null) graphics.fakeItem(stackFor(occupant.itemId()), x + 1, y + 1);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
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
        return super.mouseClicked(event, doubleClick);
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

    private static String shortName(String itemId) {
        int colon = itemId.indexOf(':');
        return colon >= 0 ? itemId.substring(colon + 1) : itemId;
    }

    @Override
    public void onClose() { this.minecraft.gui.setScreen(parent); }
}
