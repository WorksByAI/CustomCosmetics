// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.gameplay.EnabledModulesHud;
import com.customcosmetics.features.gameplay.GameplayFeatures;
import com.customcosmetics.features.gameplay.GameplayModule;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class GameplayHudEditor extends ThemedScreen {
    private final Screen parent;
    private int selected = -1;
    private double grabX, grabY;

    GameplayHudEditor(Screen parent) {
        super(Component.literal("HUD Editor"));
        this.parent = parent;
    }

    @Override protected void init() {
        addRenderableWidget(new PinkUi.Action(width / 2 - 128, 8, 160, 22, "Module List Style", false, () -> ClientUiAccess.setScreen(minecraft, new CheatingConfigScreen(this, true))));
        addRenderableWidget(new PinkUi.Action(width / 2 + 38, 8, 90, 22, "5 px Grid", false, () -> {
            ConfigManager.hudLayoutGrid = !ConfigManager.hudLayoutGrid;
            ConfigManager.requestSave();
        }).selected(() -> ConfigManager.hudLayoutGrid));
        addRenderableWidget(new PinkUi.Action(width / 2 - 80, height - 32, 76, 22, "Reset", false, () -> {
            ConfigManager.f7HudX = .02f;
            ConfigManager.f7HudY = .08f;
            ConfigManager.trevorHudX = .02f;
            ConfigManager.trevorHudY = .26f;
            ConfigManager.enabledModulesX = .8f; ConfigManager.enabledModulesY = .05f; ConfigManager.enabledModulesScale = 1;
            ConfigManager.gameplayHudScales = new float[0];
            ConfigManager.requestSave();
        }));
        addRenderableWidget(new PinkUi.Action(width / 2 + 4, height - 32, 76, 22, "Done", true, this::onClose));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        drawPreview(graphics, x, y);
        super.extractRenderState(graphics, x, y, delta);
    }

    void drawPreview(GuiGraphicsExtractor graphics, int x, int y) {
        if (ConfigManager.hudLayoutGrid) {
            HudGrid.draw(graphics);
            graphics.nextStratum();
        }
        GameplayFeatures.drawHud(graphics, true);
        int hovered = GameplayFeatures.hudEnabled(selected) ? selected : hudAt(x, y);
        if (hovered >= 0) drawHoverLabel(graphics, hovered);
    }

    @Override public void extractBackground(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        if (minecraft.level == null) super.extractBackground(graphics, x, y, delta);
    }

    private void drawHoverLabel(GuiGraphicsExtractor graphics, int hovered) {
        String name = UiText.tr(switch (hovered) {
            case 0 -> GameplayModule.F7_TIMER.label;
            case 1 -> GameplayModule.TREVOR_HELPER.label;
            default -> EnabledModulesHud.TITLE;
        });
        float scale = hovered == 2 ? EnabledModulesHud.scale() : GameplayFeatures.hudScale(hovered);
        String label = String.format(Locale.ROOT, "%s  %.1f×", name, scale);
        int left = hudX(hovered), top = hudY(hovered), previewWidth = hudWidth(hovered), previewHeight = hudHeight(hovered);
        int screenWidth = graphics.guiWidth(), screenHeight = graphics.guiHeight();
        int labelWidth = Math.min(screenWidth - 4, font.width(label) + 12);
        int labelHeight = font.lineHeight + 7;
        int labelX = Math.clamp(left + (previewWidth - labelWidth) / 2, 2, Math.max(2, screenWidth - labelWidth - 2));
        int labelY = top - labelHeight - 5;
        if (labelY < 2) labelY = Math.min(screenHeight - labelHeight - 2, top + previewHeight + 5);
        PinkUi.outline(graphics, left - 2, top - 2, previewWidth + 4, previewHeight + 4, 0, PinkUi.PINK, 0);
        PinkUi.outline(graphics, labelX, labelY, labelWidth, labelHeight, 4, PinkUi.BORDER, PinkUi.PANEL);
        PinkUi.text(graphics, font, label, labelX + 6, labelY + 4, PinkUi.TEXT, labelWidth - 12);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick) || beginHudDrag(event);
    }

    boolean beginHudDrag(MouseButtonEvent event) {
        if (event.button() != ClientInput.MOUSE_LEFT) return false;
        selected = hudAt(event.x(), event.y());
        if (selected < 0) return false;
        grabX = event.x() - hudX(selected);
        grabY = event.y() - hudY(selected);
        return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        return scaleHudAt(x, y, vertical) || super.mouseScrolled(x, y, horizontal, vertical);
    }

    boolean scaleHudAt(double x, double y, double vertical) {
        if (vertical == 0) return false;
        int id = GameplayFeatures.hudEnabled(selected) ? selected : hudAt(x, y);
        if (id == 2) {
            ConfigManager.enabledModulesScale = Math.clamp(Math.round((EnabledModulesHud.scale() + Math.signum(vertical) * .1) * 10) / 10f, .2f, 5f);
            ConfigManager.requestSave();
            return true;
        }
        if (id < 0) return false;
        GameplayFeatures.scaleHud(id, vertical);
        return true;
    }

    private int hudAt(double x, double y) {
        for (int id = 2; id >= 0; id--) {
            if (!GameplayFeatures.hudEnabled(id)) continue;
            int left = hudX(id), top = hudY(id);
            if (x >= left - 2 && x < left + hudWidth(id) + 2 && y >= top - 2 && y < top + hudHeight(id) + 2) return id;
        }
        return -1;
    }

    private int viewportWidth() { return minecraft.getWindow().getGuiScaledWidth(); }
    private int viewportHeight() { return minecraft.getWindow().getGuiScaledHeight(); }
    private int hudX(int id) { return id == 2 ? EnabledModulesHud.x(viewportWidth()) : GameplayFeatures.hudX(id,viewportWidth()); }
    private int hudY(int id) { return id == 2 ? EnabledModulesHud.y(viewportHeight()) : GameplayFeatures.hudY(id,viewportHeight()); }
    private int hudWidth(int id) { if (id == 2) return EnabledModulesHud.width(); return (int) Math.ceil(previewFont().width(GameplayFeatures.hudPreview(id)) * GameplayFeatures.hudScale(id)); }
    private int hudHeight(int id) { if (id == 2) return EnabledModulesHud.height(); return (int) Math.ceil(previewFont().lineHeight * GameplayFeatures.hudScale(id)); }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return dragHud(event) || super.mouseDragged(event, dx, dy);
    }

    boolean dragHud(MouseButtonEvent event) {
        if (!GameplayFeatures.hudEnabled(selected) || event.button() != ClientInput.MOUSE_LEFT) return false;
        int width = viewportWidth(), height = viewportHeight();
        double left = event.x() - grabX, top = event.y() - grabY;
        float x = (float) Math.clamp(left / width, 0, .99);
        float y = (float) Math.clamp(top / height, 0, .95);
        if (ConfigManager.hudLayoutGrid) {
            int reservedWidth = selected == 2 ? hudWidth(selected) : 100;
            int reservedHeight = selected == 2 ? hudHeight(selected) : 22;
            x = HudGrid.fraction(left, width, Math.max(0, width - reservedWidth));
            y = HudGrid.fraction(top, height, Math.max(0, height - reservedHeight));
        }
        if (selected == 2) { ConfigManager.enabledModulesX = x; ConfigManager.enabledModulesY = y; } else if (selected == 0) {
            ConfigManager.f7HudX = x;
            ConfigManager.f7HudY = y;
        } else {
            ConfigManager.trevorHudX = x;
            ConfigManager.trevorHudY = y;
        }
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return endHudDrag(event) || super.mouseReleased(event);
    }

    boolean endHudDrag(MouseButtonEvent event) {
        if (event.button() != ClientInput.MOUSE_LEFT || selected < 0) return false;
        finishHudDrag();
        return true;
    }

    void finishHudDrag() {
        if (selected < 0) return;
        selected = -1;
        ConfigManager.requestSave();
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        ConfigManager.requestSave();
        ClientUiAccess.setScreen(minecraft, parent);
    }
}
