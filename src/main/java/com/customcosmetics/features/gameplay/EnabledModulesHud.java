// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.gui.CheatingConfigScreen;
import com.customcosmetics.gui.GameplayHudEditor;
import com.customcosmetics.gui.UiFonts;
import com.customcosmetics.gui.UiMotion;
import com.customcosmetics.features.nickname.AnimationClock;
import com.customcosmetics.features.gameplay.dungeonapi.DeviceConfig.Part;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.*;

public final class EnabledModulesHud {
    public static final String TITLE = "Module List HUD";
    private static final String OLD_AUTO_CLICK_LABEL = "Auto Click";
    private static List<String> lines = List.of(TITLE);
    private static final ArrayList<AnimatedRow> liveRows = new ArrayList<>();
    private static List<String> liveTargets;

    private static final class AnimatedRow {
        final String label;
        final UiMotion motion;
        boolean wanted;
        float progress;

        AnimatedRow(String label, boolean initial) {
            this.label = label;
            motion = new UiMotion(initial ? 1 : 0, UiMotion.Curve.SPRING);
        }
    }

    public enum Sort {
        ALPHABETICAL("Sort: A–Z"), LONGEST_FIRST("Sort: Longest First"), SHORTEST_FIRST("Sort: Shortest First");

        public final String label;
        Sort(String label) { this.label = label; }

        public Sort next() {
            return switch (this) {
                case ALPHABETICAL -> LONGEST_FIRST;
                case LONGEST_FIRST -> SHORTEST_FIRST;
                case SHORTEST_FIRST -> ALPHABETICAL;
            };
        }
    }

    public static Sort sort() {
        return switch (ConfigManager.enabledModulesSort) {
            case 1 -> Sort.LONGEST_FIRST;
            case 2 -> Sort.SHORTEST_FIRST;
            default -> Sort.ALPHABETICAL;
        };
    }

    public static boolean enabled() {
        return ConfigManager.enabledModulesHud && ConfigManager.gameplayPanelConfirmed;
    }

    public static boolean shown(String module) {
        var hidden = ConfigManager.enabledModulesHidden;
        return !hidden.contains(module)
            && (!GameplayModule.AUTO_CLICKER.label.equals(module) || !hidden.contains(OLD_AUTO_CLICK_LABEL));
    }

    public static void toggleShown(String module) {
        var hidden = new HashSet<>(ConfigManager.enabledModulesHidden);
        boolean wasHidden = hidden.remove(module);
        if (GameplayModule.AUTO_CLICKER.label.equals(module)) wasHidden |= hidden.remove(OLD_AUTO_CLICK_LABEL);
        if (!wasHidden) hidden.add(module);
        ConfigManager.enabledModulesHidden = Set.copyOf(hidden);
        ConfigManager.requestSave();
    }

    public static List<String> availableModules(GameplayModule.Category category) {
        return List.copyOf(moduleNames(false, category));
    }

    private static TreeSet<String> moduleNames(boolean enabledOnly, GameplayModule.Category category) {
        var names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        for (var module : GameplayModule.values()) {
            if ((category == null || module.category == category)
                    && module.localControl() && module != GameplayModule.AUTO_I4
                    && (!enabledOnly || module.enabled() && shown(module.label))) names.add(module.label);
        }
        var dungeon = QuoiDungeon.menu();
        if (dungeon != null) for (var module : dungeon.modules()) {
            if ((category == null || module.category().name().equals(category.name()))
                    && Part.forDungeonName(module.name()) == null
                    && module.enabled() != null && (!enabledOnly || module.enabled().getAsBoolean() && shown(module.name()))) names.add(module.name());
        }
        return names;
    }
    public static float scale() { float s = ConfigManager.enabledModulesScale; return Float.isFinite(s) ? Math.clamp(s,.2f,5f) : 1; }
    private static int spacing() { return Math.clamp(ConfigManager.enabledModulesSpacing,0,12); }
    private static Font font() {
        var gameFont = Minecraft.getInstance().font;
        return ConfigManager.enabledModulesUseClickGuiFont ? UiFonts.get(gameFont) : gameFont;
    }
    private static int rawWidth(Font font) { return lines.stream().mapToInt(font::width).max().orElse(0) + 8; }
    private static int rowHeight(Font font) { return UiFonts.lineHeight(font) + spacing(); }
    public static int width() { return (int) Math.ceil(rawWidth(font()) * scale()); }
    public static int height() { return (int) Math.ceil(lines.size() * rowHeight(font()) * scale()); }
    public static int x(int width) { return position(width, width(), ConfigManager.enabledModulesX); }
    public static int y(int height) { return position(height, height(), ConfigManager.enabledModulesY); }

    private static int position(int viewport, int size, float fraction) {
        return Math.clamp(Math.round(viewport * fraction), 0, Math.max(0, viewport - size));
    }

    public static void draw(GuiGraphicsExtractor g, boolean preview) {
        var client = Minecraft.getInstance();
        if (!enabled() || !preview && client.player == null) {
            if (!preview) clearLiveRows();
            return;
        }
        if (!preview) {
            var screen = ClientUiAccess.screen(client);
            if (screen instanceof GameplayHudEditor || screen instanceof CheatingConfigScreen editor && editor.previewsModuleList()) return;
        }
        var enabled = moduleNames(true, null);
        if (preview && enabled.isEmpty()) enabled.add(com.customcosmetics.gui.UiText.tr(TITLE));
        lines = new ArrayList<>(enabled);
        Font font = font();
        Sort sort = sort();
        if (sort != Sort.ALPHABETICAL) {
            Comparator<String> byWidth = Comparator.comparingInt(font::width);
            if (sort == Sort.LONGEST_FIRST) byWidth = byWidth.reversed();
            lines.sort(byWidth.thenComparing(String::compareToIgnoreCase));
        }
        float hue = ConfigManager.enabledModulesChroma ? AnimationClock.millis() % 6000L / 6000f : -1;
        if (!preview && ConfigManager.uiAnimationsEnabled) drawAnimated(g, font, hue);
        else {
            if (!preview) clearLiveRows();
            drawStatic(g, font, hue);
        }
    }

    private static void clearLiveRows() {
        if (liveTargets == null) return;
        liveRows.clear();
        liveTargets = null;
    }

    private static void updateLiveRows() {
        if (lines.equals(liveTargets)) return;
        var previous = List.copyOf(liveRows);
        var oldByLabel = new HashMap<String, AnimatedRow>();
        for (var row : previous) {
            row.wanted = false;
            oldByLabel.put(row.label, row);
        }
        liveRows.clear();
        boolean initial = liveTargets == null;
        for (String label : lines) {
            var row = oldByLabel.get(label);
            if (row == null) row = new AnimatedRow(label, initial);
            row.wanted = true;
            liveRows.add(row);
        }
        for (int index = 0; index < previous.size(); index++) {
            var row = previous.get(index);
            if (!row.wanted) liveRows.add(Math.min(index, liveRows.size()), row);
        }
        liveTargets = List.copyOf(lines);
    }

    private static void drawStatic(GuiGraphicsExtractor g, Font font, float hue) {
        if (lines.isEmpty()) return;
        var pose = g.pose(); pose.pushMatrix();
        try {
            pose.translate(x(g.guiWidth()),y(g.guiHeight())); pose.scale(scale(),scale());
            int width = rawWidth(font), step = rowHeight(font);
            for (int index = 0; index < lines.size(); index++) {
                drawRow(g, font, lines.get(index), width, step, index * step, 1, hue, index);
            }
        } finally { pose.popMatrix(); }
    }

    private static void drawAnimated(GuiGraphicsExtractor g, Font font, float hue) {
        updateLiveRows();
        int width = 8, step = rowHeight(font);
        float height = 0;
        for (var iterator = liveRows.iterator(); iterator.hasNext();) {
            var row = iterator.next();
            row.progress = row.motion.value(row.wanted ? 1 : 0, true);
            if (!row.wanted && row.progress <= 0) {
                iterator.remove();
                continue;
            }
            width = Math.max(width, font.width(row.label) + 8);
            height += Math.clamp(row.progress, 0, 1) * step;
        }
        if (height == 0) return;
        float scale = scale();
        var pose = g.pose();
        pose.pushMatrix();
        try {
            pose.translate(position(g.guiWidth(), (int) Math.ceil(width * scale), ConfigManager.enabledModulesX),
                position(g.guiHeight(), (int) Math.ceil(height * scale), ConfigManager.enabledModulesY));
            pose.scale(scale, scale);
            float y = 0;
            for (int index = 0; index < liveRows.size(); index++) {
                var row = liveRows.get(index);
                drawRow(g, font, row.label, width, step, y, row.progress, hue, index);
                y += Math.clamp(row.progress, 0, 1) * step;
            }
        } finally { pose.popMatrix(); }
    }

    private static void drawRow(GuiGraphicsExtractor g, Font font, String line, int width, int step,
                                float y, float progress, float hue, int index) {
        float visible = Math.clamp(progress, 0, 1);
        if (visible < .03f) return;
        boolean right = ConfigManager.enabledModulesRightAlign;
        float shift = (1 - progress) * (right ? 12 : -12);
        int rowY = Math.round(y);
        boolean translated = shift != 0 || y != rowY;
        var pose = g.pose();
        if (translated) {
            pose.pushMatrix();
            pose.translate(shift, y - rowY);
        }
        try {
            if (ConfigManager.enabledModulesBackground) {
                g.fill(0, rowY, width, rowY + step, fade(ConfigManager.enabledModulesBackgroundColor, visible));
            }
            int color = hue < 0 ? ConfigManager.enabledModulesColor : Color.HSBtoRGB((hue + index * .075f) % 1, .72f, 1);
            g.text(font, line, right ? width - font.width(line) - 4 : 4, rowY + 1,
                fade(color, visible), ConfigManager.enabledModulesShadow);
        } finally {
            if (translated) pose.popMatrix();
        }
    }

    private static int fade(int argb, float visible) {
        return (argb & 0xFFFFFF) | Math.round((argb >>> 24) * visible) << 24;
    }
}
