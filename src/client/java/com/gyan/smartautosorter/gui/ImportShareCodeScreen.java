package com.gyan.smartautosorter.gui;

import com.gyan.smartautosorter.client.SmartAutoSorterClient;
import com.gyan.smartautosorter.core.Layout;
import com.gyan.smartautosorter.share.ShareCodeCodec;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Spec section 7: paste/type a code, see a preview + validation result, confirm before saving it. */
public final class ImportShareCodeScreen extends Screen {

    private final Screen parent;
    private EditBox codeBox;
    private Layout previewedLayout;
    private Component statusMessage = Component.empty();

    public ImportShareCodeScreen(Screen parent) {
        super(Component.literal("Import Share Code"));
        this.parent = parent;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        codeBox = new EditBox(this.font, this.width / 2 - 150, 50, 300, 20, Component.literal("Share code"));
        codeBox.setMaxLength(20000);
        addRenderableWidget(codeBox);

        addRenderableWidget(Button.builder(Component.translatable("smartautosorter.gui.preview"), b -> preview())
                .bounds(this.width / 2 - 100, 80, 95, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Confirm Import"), b -> confirmImport())
                .bounds(this.width / 2 + 5, 80, 95, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(this.width / 2 - 50, this.height - 32, 100, 20).build());
    }

    private void preview() {
        ShareCodeCodec.DecodeResult result = ShareCodeCodec.decode(codeBox.getValue());
        if (result instanceof ShareCodeCodec.Success success) {
            previewedLayout = success.layout();
            statusMessage = Component.literal("Valid — \"" + previewedLayout.name() + "\", "
                    + previewedLayout.allRules().size() + " item rules");
        } else {
            previewedLayout = null;
            statusMessage = Component.translatable("smartautosorter.message.share_code_invalid");
        }
    }

    private void confirmImport() {
        if (previewedLayout == null) {
            preview();
        }
        if (previewedLayout == null) return;

        var cfg = SmartAutoSorterClient.config();
        if (!cfg.canAddCustomLayout()) {
            statusMessage = Component.literal("Custom layout limit reached (" + com.gyan.smartautosorter.config.SorterConfig.MAX_CUSTOM_LAYOUTS + ")");
            return;
        }
        cfg.layouts.add(previewedLayout);
        SmartAutoSorterClient.saveConfig();
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        graphics.drawCenteredString(this.font, Component.literal("Paste a share code (starts with SAS1-)"), this.width / 2, 35, 0xAAAAAA);
        graphics.drawCenteredString(this.font, statusMessage, this.width / 2, 110, 0xFFFF55);
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(parent);
    }
}
