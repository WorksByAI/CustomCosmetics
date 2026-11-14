// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.gameplay.GameplayFeatures;
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
        addRenderableWidget(new PinkUi.Action(width / 2 - 80, height - 32, 76, 22, "Reset", false, () -> {
            ConfigManager.f7HudX = .02f;
            ConfigManager.f7HudY = .08f;
            ConfigManager.freshHudX = .02f;
            ConfigManager.freshHudY = .14f;
            ConfigManager.cocoonHudX = .02f;
            ConfigManager.cocoonHudY = .20f;
            ConfigManager.kuudraSupplyHudX = .42f;
            ConfigManager.kuudraSupplyHudY = .4f;
            ConfigManager.kuudraSpawnHudX = .42f;
            ConfigManager.kuudraSpawnHudY = .34f;
            ConfigManager.kuudraBuildHudX = .42f;
            ConfigManager.kuudraBuildHudY = .29f;
            ConfigManager.trevorHudX = .02f;
            ConfigManager.trevorHudY = .26f;
            ConfigManager.gameplayHudScales = new float[0];
            ConfigManager.requestSave();
        }));
        addRenderableWidget(new PinkUi.Action(width / 2 + 4, height - 32, 76, 22, "Done", true, this::onClose));
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        var hovered = selected >= 0 ? selected : hudAt(x, y);
        if (hovered >= 0) {
            int left = GameplayFeatures.hudX(hovered, width), top = GameplayFeatures.hudY(hovered, height);
            graphics.fill(left - 2, top - 2, left + hudWidth(hovered) + 2, top + hudHeight(hovered) + 2, 0x663D3942);
        }
        GameplayFeatures.drawHud(graphics, true);
        super.extractRenderState(graphics, x, y, delta);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() != ClientInput.MOUSE_LEFT) return false;
        selected = hudAt(event.x(), event.y());
        if (selected < 0) return false;
        grabX = event.x() - GameplayFeatures.hudX(selected, width);
        grabY = event.y() - GameplayFeatures.hudY(selected, height);
        return true;
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        int id = selected >= 0 ? selected : hudAt(x, y);
        if (vertical == 0 || id < 0) return super.mouseScrolled(x, y, horizontal, vertical);
        GameplayFeatures.scaleHud(id, vertical);
        return true;
    }

    private int hudAt(double x, double y) {
        for (int id = GameplayFeatures.hudCount() - 1; id >= 0; id--) {
            int left = GameplayFeatures.hudX(id, width), top = GameplayFeatures.hudY(id, height);
            if (x >= left - 2 && x < left + hudWidth(id) + 2 && y >= top - 2 && y < top + hudHeight(id) + 2) return id;
        }
        return -1;
    }

    private int hudWidth(int id) { return (int) Math.ceil(previewFont().width(GameplayFeatures.hudPreview(id)) * GameplayFeatures.hudScale(id)); }
    private int hudHeight(int id) { return (int) Math.ceil(previewFont().lineHeight * GameplayFeatures.hudScale(id)); }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (selected < 0) return super.mouseDragged(event, dx, dy);
        float x = (float) Math.clamp((event.x() - grabX) / width, 0, .99);
        float y = (float) Math.clamp((event.y() - grabY) / height, 0, .95);
        if (selected == 0) {
            ConfigManager.f7HudX = x;
            ConfigManager.f7HudY = y;
        } else if (selected == 1) {
            ConfigManager.freshHudX = x;
            ConfigManager.freshHudY = y;
        } else if (selected == 2) {
            ConfigManager.cocoonHudX = x;
            ConfigManager.cocoonHudY = y;
        } else if (selected == 3) {
            ConfigManager.kuudraSupplyHudX = x;
            ConfigManager.kuudraSupplyHudY = y;
        } else if (selected == 4) {
            ConfigManager.kuudraSpawnHudX = x;
            ConfigManager.kuudraSpawnHudY = y;
        } else if (selected == 5) {
            ConfigManager.kuudraBuildHudX = x;
            ConfigManager.kuudraBuildHudY = y;
        } else {
            ConfigManager.trevorHudX = x;
            ConfigManager.trevorHudY = y;
        }
        return true;
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (selected < 0) return super.mouseReleased(event);
        selected = -1;
        ConfigManager.requestSave();
        return true;
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        ConfigManager.requestSave();
        ClientUiAccess.setScreen(minecraft, parent);
    }
}
