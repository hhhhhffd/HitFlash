package dev.hitflash.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

final class HitFlashSettingsScreen extends Screen {
    private final Screen parent;
    private HitFlashConfig draft = HitFlashClient.settings();
    private int left, top, panelWidth;
    private boolean saveFailed;

    HitFlashSettingsScreen(Screen parent) {
        super(Component.literal("HitFlash"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(234, width - 20);
        left = (width - panelWidth) / 2;
        top = Math.max(31, (height - 123) / 2);
        int half = (panelWidth - 4) / 2;

        button(left, top, panelWidth, enabledLabel(), button -> {
            draft = new HitFlashConfig(!draft.enabled(), draft.rgb(), draft.durationMs(), draft.playersOnly());
            button.setMessage(enabledLabel());
        });
        button(left, top + 24, panelWidth, targetLabel(), button -> {
            draft = new HitFlashConfig(draft.enabled(), draft.rgb(), draft.durationMs(), !draft.playersOnly());
            button.setMessage(targetLabel());
        });
        addRenderableWidget(new DurationSlider(left, top + 48, panelWidth, draft.durationMs()));
        button(left, top + 72, panelWidth, t("color.open", draft.hex()), button ->
            minecraft.setScreen(new HitFlashColorScreen(this, draft.rgb(), rgb ->
                draft = new HitFlashConfig(draft.enabled(), rgb, draft.durationMs(), draft.playersOnly()))));
        button(left, top + 101, half, t("reset"), button -> {
            draft = HitFlashConfig.DEFAULT;
            saveFailed = false;
            rebuildWidgets();
        });
        button(left + half + 4, top + 101, panelWidth - half - 4, Component.translatable("gui.done"), button -> {
            if (HitFlashClient.applySettings(draft)) onClose();
            else saveFailed = true;
        });
    }

    private void button(int x, int y, int w, Component label, Button.OnPress action) {
        addRenderableWidget(Button.builder(label, action).bounds(x, y, w, 20).build());
    }

    private Component enabledLabel() {
        return t("enabled", Component.translatable(draft.enabled() ? "options.on" : "options.off"));
    }

    private Component targetLabel() {
        return t("targets", t(draft.playersOnly() ? "targets.players" : "targets.all"));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, getTitle(), width / 2, top - 26, 0xFFFFFFFF);
        g.drawCenteredString(font, t("hint"), width / 2, top - 14, 0xFFD6E3F3);
        swatch(g, left + panelWidth - 27, top + 77, 19, 10, draft.rgb());
        if (saveFailed) g.drawCenteredString(font, t("save_failed"), width / 2, top + 126, 0xFFFF7777);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    static void swatch(GuiGraphics g, int x, int y, int w, int h, int rgb) {
        g.renderOutline(x - 1, y - 1, w + 2, h + 2, 0xFFC4CBD8);
        g.fill(x, y, x + w, y + h, 0xFF000000 | rgb);
    }

    static Component t(String suffix, Object... args) {
        return Component.translatable("hitflash.menu." + suffix, args);
    }

    private final class DurationSlider extends AbstractSliderButton {
        private static final int RANGE = HitFlashConfig.MAX_MS - HitFlashConfig.MIN_MS;

        DurationSlider(int x, int y, int w, int current) {
            super(x, y, w, 20, Component.empty(), (current - HitFlashConfig.MIN_MS) / (double) RANGE);
            updateMessage();
        }

        private int milliseconds() {
            return HitFlashConfig.MIN_MS + (int) Math.round(value * RANGE);
        }

        @Override
        protected void updateMessage() {
            setMessage(t("duration", milliseconds()));
        }

        @Override
        protected void applyValue() {
            draft = new HitFlashConfig(draft.enabled(), draft.rgb(), milliseconds(), draft.playersOnly());
        }
    }
}
