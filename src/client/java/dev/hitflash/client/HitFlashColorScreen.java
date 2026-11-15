package dev.hitflash.client;

import java.awt.Color;
import java.util.Locale;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import static dev.hitflash.client.HitFlashSettingsScreen.swatch;
import static dev.hitflash.client.HitFlashSettingsScreen.t;

final class HitFlashColorScreen extends Screen {
    private static final int[] PRESETS = {0xFFFFFF, 0xFF5264, 0xFFB75B, 0x5AE58F, 0x62C9FF, 0xCF7EFF};
    private static final int BAR_WIDTH = 17;

    private final Screen parent;
    private final IntConsumer accept;
    private final float[] hsv = new float[3];
    private int color, x, y, sizeX, sizeY, bar;
    private int drag;
    private boolean syncing;
    private EditBox hex;
    private Button apply;

    HitFlashColorScreen(Screen parent, int initialColor, IntConsumer accept) {
        super(t("color.title"));
        this.parent = parent;
        this.accept = accept;
        fromRgb(initialColor);
    }

    @Override
    protected void init() {
        int panel = Math.min(218, width - 20);
        sizeX = Math.max(2, panel - BAR_WIDTH - 8);
        sizeY = Math.clamp(height - 115, 24, 105);
        x = (width - panel) / 2;
        y = Math.max(23, (height - sizeY - 78) / 2);
        bar = x + sizeX + 8;
        drag = 0;

        hex = new EditBox(font, x + 27, y + sizeY + 8, 72, 20, t("color.hex"));
        hex.setMaxLength(7);
        hex.setValue(hex(color));
        hex.setResponder(input -> {
            if (syncing) return;
            try {
                fromRgb(HitFlashConfig.parseHex(input));
                apply.active = true;
                hex.setTextColor(0xFFFFFFFF);
            } catch (IllegalArgumentException badHex) {
                apply.active = false;
                hex.setTextColor(0xFFFF8585);
            }
        });
        addRenderableWidget(hex);

        int separation = panel / PRESETS.length;
        for (int n = 0; n < PRESETS.length; n++) {
            int value = PRESETS[n];
            Button button = Button.builder(Component.empty(), b -> {
                fromRgb(value);
                refreshHex();
            }).bounds(x + n * separation, y + sizeY + 32, separation - 2, 16).build();
            button.setTooltip(Tooltip.create(Component.literal(hex(value))));
            addRenderableWidget(button);
        }
        int half = (panel - 4) / 2;
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
            .bounds(x, y + sizeY + 53, half, 20).build());
        apply = addRenderableWidget(Button.builder(t("color.apply"), b -> {
            accept.accept(color);
            onClose();
        }).bounds(x + half + 4, y + sizeY + 53, panel - half - 4, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, getTitle(), width / 2, y - 16, 0xFFFFFFFF);
        g.renderOutline(x - 1, y - 1, sizeX + 2, sizeY + 2, 0xFFC4CBD8);
        g.renderOutline(bar - 1, y - 1, BAR_WIDTH + 2, sizeY + 2, 0xFFC4CBD8);
        for (int c = 0; c < sizeX; c++) {
            int bright = Color.HSBtoRGB(hsv[0], c / (float) (sizeX - 1), 1);
            g.fillGradient(x + c, y, x + c + 1, y + sizeY, bright, 0xFF000000);
        }
        for (int r = 0; r < sizeY; r++) {
            g.fill(bar, y + r, bar + BAR_WIDTH, y + r + 1, Color.HSBtoRGB(r / (float) (sizeY - 1), 1, 1));
        }
        int cx = x + Math.round(hsv[1] * (sizeX - 1));
        int cy = y + Math.round((1 - hsv[2]) * (sizeY - 1));
        g.renderOutline(cx - 4, cy - 4, 9, 9, 0xFF000000);
        g.renderOutline(cx - 3, cy - 3, 7, 7, 0xFFFFFFFF);

        int hy = y + Math.round(hsv[0] * (sizeY - 1));
        g.renderOutline(bar - 2, hy - 2, BAR_WIDTH + 4, 5, 0xFF000000);
        g.renderOutline(bar - 1, hy - 1, BAR_WIDTH + 2, 3, 0xFFFFFFFF);

        g.drawString(font, "HEX", x + 3, y + sizeY + 14, 0xFFE2E7EF, true);
        if (bar + BAR_WIDTH > x + 125) swatch(g, x + 108, y + sizeY + 10, bar + BAR_WIDTH - x - 108, 16, color);
        int separation = (bar + BAR_WIDTH - x) / PRESETS.length;
        // Leave vanilla button boundaries visible, including hover/focus feedback.
        for (int n = 0; n < PRESETS.length; n++) {
            int insetX = x + n * separation + 3;
            int insetY = y + sizeY + 35;
            g.fill(insetX, insetY, insetX + Math.max(1, separation - 8), insetY + 10,
                0xFF000000 | PRESETS[n]);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && event.y() >= y && event.y() < y + sizeY) {
            if (event.x() >= x && event.x() < x + sizeX) drag = 1;
            else if (event.x() >= bar && event.x() < bar + BAR_WIDTH) drag = 2;
            if (drag != 0) {
                setFocused(null);
                updatePointer(event.x(), event.y());
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (drag != 0 && event.button() == 0) {
            updatePointer(event.x(), event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (drag != 0 && event.button() == 0) {
            drag = 0;
            return true;
        }
        return super.mouseReleased(event);
    }

    private void updatePointer(double px, double py) {
        float relativeY = Math.clamp((float) ((py - y) / (sizeY - 1)), 0f, 1f);
        if (drag == 2) hsv[0] = relativeY;
        else {
            hsv[1] = Math.clamp((float) ((px - x) / (sizeX - 1)), 0f, 1f);
            hsv[2] = 1 - relativeY;
        }
        color = Color.HSBtoRGB(hsv[0], hsv[1], hsv[2]) & 0xFFFFFF;
        refreshHex();
    }

    private void fromRgb(int rgb) {
        color = rgb;
        Color.RGBtoHSB(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, hsv);
    }

    private void refreshHex() {
        syncing = true;
        try {
            hex.setValue(hex(color));
            apply.active = true;
            hex.setTextColor(0xFFFFFFFF);
        } finally {
            syncing = false;
        }
    }

    private static String hex(int rgb) {
        return String.format(Locale.ROOT, "#%06X", rgb);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
