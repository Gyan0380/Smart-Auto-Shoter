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
    private EditBox itemIdBox;
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

        itemIdBox = new EditBox(this.font, this.width / 2 - 150, 45, 235, 20,
                Component.literal("Item ID, e.g. minecraft:stone"));
        itemIdBox.setHint(Component.literal("minecraft:stone"));
        itemIdBox.setMaxLength(128);
        addRenderableWidget(itemIdBox);
        addRenderableWidget(Button.builder(Component.literal("Select item"), b -> {
            String raw = itemIdBox.getValue().trim();
            try {
                Identifier parsed = Identifier.parse(raw);
                if (BuiltInRegistries.ITEM.containsKey(parsed)) {
                    selectedItemId = parsed.toString();
                } else {
                    selectedItemId = null;
                }
            } catch (IllegalArgumentException ex) {
                selectedItemId = null;
            }
        }).bounds(this.width / 2 + 90, 45, 70, 20).build());

        List<ItemRule> rules = layout.allRules();
        int pickY = 70;
        for (int i = 0; i < rules.size() && i < 9; i++) {
            final String itemId = rules.get(i).itemId();
            addRenderableWidget(Button.builder(Component.literal(shortName(itemId)), b -> {
                selectedItemId = itemId;
                itemIdBox.setValue(itemId);
            }).bounds(this.width / 2 - 135 + i * 30, pickY, 28, 20).build());
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, this.title, this.width / 2, 8, 0xFFFFFFFF);
        if (selectedItemId != null) {
            graphics.text(this.font, "Selected: " + shortName(selectedItemId) + " — click a slot to assign",
                    gridLeft, gridTop - 12, 0xFFFFFF55, true);
        } else {
            graphics.text(this.font, "Enter an item ID above, select it, then click a slot",
                    gridLeft, gridTop - 12, 0xFFFFFFFF, true);
        }
        for (int slot = 0; slot < 36; slot++) {
            int col = slot % COLS;
            int row = slot / COLS;
            int x = gridLeft + col * SLOT_SIZE;
            int y = gridTop + row * SLOT_SIZE + (row == 3 ? 6 : 0);
            ItemRule occupant = ruleForSlot(slot);
            int tint = occupant == null ? 0xFF8B8B8B : categoryColor(occupant.itemId());
            graphics.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, tint);
            graphics.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, 0x558B8B8B);
            if (occupant != null) graphics.fakeItem(stackFor(occupant.itemId()), x + 1, y + 1);
            graphics.text(this.font, Integer.toString(slot + 1), x + 1, y + 1, 0xFFFFFFFF, true);
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

    private static int categoryColor(String itemId) {
        String id = itemId.toLowerCase(java.util.Locale.ROOT);
        if (id.contains("sword") || id.contains("bow") || id.contains("shield") || id.contains("trident")) return 0xFFE05A5A;
        if (id.contains("pickaxe") || id.contains("axe") || id.contains("shovel") || id.contains("hoe")) return 0xFF4E91D9;
        if (id.contains("bread") || id.contains("beef") || id.contains("porkchop") || id.contains("apple")
                || id.contains("carrot") || id.contains("potato") || id.contains("stew") || id.contains("fish")) return 0xFFE5B24C;
        if (id.contains("stone") || id.contains("dirt") || id.contains("planks") || id.contains("brick")
                || id.contains("glass") || id.contains("cobblestone") || id.contains("sand")) return 0xFF4B9A61;
        return 0xFF8B70B5;
    }

    private static String shortName(String itemId) {
        int colon = itemId.indexOf(':');
        return colon >= 0 ? itemId.substring(colon + 1) : itemId;
    }

    @Override
    public void onClose() { this.minecraft.gui.setScreen(parent); }
}
