// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.gameplay.CheatingConfigs;
import com.customcosmetics.features.gameplay.EnabledModulesHud;
import com.customcosmetics.features.gameplay.GameplayModule;
import com.customcosmetics.features.pack.TextColorOverrides;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import java.util.function.IntConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;

public final class CheatingConfigScreen extends ThemedScreen {
    private static final int MARGIN = 8;
    private static final int PADDING = 12;
    private static final int CONFIRMATION_HEIGHT = 28;
    private static final int MODULE_ROWS_TOP = 85;
    private static final int MODULE_ROW_HEIGHT = 22;
    private final Screen parent;
    private final boolean style;
    private final GameplayHudEditor hudEditor;
    private String name = "", notice = "";
    private Confirmation confirmation;
    private int page;
    private int panelX, panelY, panelWidth, panelHeight;
    private int movedX = -1, movedY;
    private double panelGrabX, panelGrabY;
    private boolean draggingPanel;
    private boolean choosingModules;
    private String moduleQuery = "";
    private int moduleScroll, moduleCount, moduleMaxScroll;
    private int scrollbarGrab = -1;
    private GameplayModule.Category moduleCategory;
    private List<String> availableModules = List.of();
    private final List<AbstractWidget> moduleWidgets = new ArrayList<>();
    private List<String> moduleRows = List.of();

    private record Confirmation(String action, String file) {}

    public CheatingConfigScreen(Screen parent) { this(parent,false); }
    public CheatingConfigScreen(Screen parent, boolean style) {
        super(Component.literal(style ? EnabledModulesHud.TITLE : "Config Manager"));
        this.parent = parent;
        this.style = style;
        hudEditor = style && parent instanceof GameplayHudEditor editor ? editor : null;
    }

    public boolean previewsModuleList() { return style; }
    public boolean previewsGameplayHud() { return hudEditor != null; }

    @Override protected void init() {
        var files = style ? List.<String>of() : CheatingConfigs.list();
        layoutPanel(files.size());
        int w = panelWidth - PADDING * 2, x = panelX + PADDING;
        if (choosingModules) {
            addModuleFilter(x, w);
            return;
        }
        if (style) {
            addStyleControls(x, w);
        } else {
            addConfigControls(x, w, files);
        }
        addFooter("Done");
    }

    private void layoutPanel(int fileCount) {
        panelWidth = Math.min(style ? 420 : 340, width - 2 * MARGIN);
        panelHeight = Math.min(style ? 244 : 177 + Math.min(5, fileCount) * 25, height - 2 * MARGIN);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        if (!style) return;
        int hudX = EnabledModulesHud.x(width), hudRight = hudX + EnabledModulesHud.width();
        int hudY = EnabledModulesHud.y(height), hudBottom = hudY + EnabledModulesHud.height();
        int left = hudX - 2 * MARGIN, right = width - hudRight - 2 * MARGIN;
        int side = Math.max(left, right);
        if (side >= Math.min(panelWidth, 264)) {
            panelWidth = Math.min(panelWidth, side);
            panelX = left >= right ? MARGIN : width - MARGIN - panelWidth;
        } else if (hudY >= panelHeight + 2 * MARGIN) {
            panelY = MARGIN;
        } else if (height - hudBottom >= panelHeight + 2 * MARGIN) {
            panelY = height - MARGIN - panelHeight;
        }
    }

    private int panelBottom() { return panelY + panelHeight; }
    private int confirmationY() { return panelBottom() - 35 - CONFIRMATION_HEIGHT; }
    @Override int widgetOffsetX() { return movedX < 0 ? 0 : Math.clamp(movedX, MARGIN, width - MARGIN - panelWidth) - panelX; }
    @Override int widgetOffsetY() { return movedX < 0 ? 0 : Math.clamp(movedY, MARGIN, height - MARGIN - panelHeight) - panelY; }

    private boolean insidePanel(double x, double y) {
        int left = panelX + widgetOffsetX(), top = panelY + widgetOffsetY();
        return StudioLayout.contains(x, y, left, top, left + panelWidth, top + panelHeight);
    }

    private MouseButtonEvent panelEvent(MouseButtonEvent event) {
        int dx = widgetOffsetX(), dy = widgetOffsetY();
        return dx == 0 && dy == 0 ? event
            : new MouseButtonEvent(event.x() - dx, event.y() - dy, event.buttonInfo());
    }

    private void addFooter(String label) {
        addRenderableWidget(new PinkUi.Action(panelX + (panelWidth - 140) / 2, panelBottom() - 30, 140, 22,
            label, true, this::onClose));
    }

    private void addConfigControls(int x, int width, List<String> files) {
        int y = panelY + 30;
        var input = addRenderableWidget(new PinkUi.Field(font, x, y, width, "Config filename", 22));
        input.setMaxLength(69);
        input.setValue(name);
        input.setHint(UiText.component("Config filename"));
        input.setResponder(this::selectFile);
        y += 28;
        int third = width / 3;
        addRenderableWidget(new PinkUi.Action(x, y, third - 4, 22, "Save", false, () -> act("save", name))
            .selected(() -> awaiting("save")));
        addRenderableWidget(new PinkUi.Action(x + third, y, third - 4, 22, "Load", false, () -> act("load", name))
            .selected(() -> awaiting("load")));
        addRenderableWidget(new PinkUi.Action(x + 2 * third, y, width - 2 * third, 22, "Remove", false, () -> act("remove", name))
            .selected(() -> awaiting("remove")));
        y += 30;
        int pagerY = confirmationY() - 26;
        int count = Math.max(1, (pagerY - y) / 25);
        int pages = Math.max(1, (files.size() + count - 1) / count);
        page = Math.clamp(page, 0, pages - 1);
        for (int i = page * count; i < Math.min(files.size(), (page + 1) * count); i++) {
            String file = files.get(i);
            addRenderableWidget(new PinkUi.Action(x, y, width, 22, file, false, () -> {
                selectFile(file);
                rebuildWidgets();
            }));
            y += 25;
        }
        if (pages > 1) {
            int half = width / 2;
            addRenderableWidget(new PinkUi.Action(x, pagerY, half - 3, 22, "Previous", false, () -> {
                page--;
                rebuildWidgets();
            })).active = page > 0;
            addRenderableWidget(new PinkUi.Action(x + half + 3, pagerY, width - half - 3, 22, "Next", false, () -> {
                page++;
                rebuildWidgets();
            })).active = page + 1 < pages;
        }
    }

    private void addStyleControls(int x, int width) {
        int columnWidth = (width - 12) / 2;
        int y = panelY + 30;
        int buttonHeight = Math.clamp((panelHeight - 143) / 6, 12, 22);
        int row = buttonHeight + 3;
        addRenderableWidget(new PinkUi.Action(x, y, columnWidth, buttonHeight,
            "Module List: " + (ConfigManager.enabledModulesHud ? "Enabled" : "Disabled"), false, () -> {
                ConfigManager.enabledModulesHud = !ConfigManager.enabledModulesHud;
                changed();
            }));
        y += row;
        var sort = EnabledModulesHud.sort();
        addRenderableWidget(new PinkUi.Action(x, y, columnWidth, buttonHeight, sort.label, false, () -> {
                ConfigManager.enabledModulesSort = sort.next().ordinal();
                changed();
            }));
        y += row;
        addRenderableWidget(new PinkUi.Action(x, y, columnWidth, buttonHeight,
            "Align: " + (ConfigManager.enabledModulesRightAlign ? "Right" : "Left"), false, () -> {
                ConfigManager.enabledModulesRightAlign = !ConfigManager.enabledModulesRightAlign;
                changed();
            }));
        y += row;
        int fontRowX = x, fontRowY = y;
        String fontLabel = "Use ClickGUI Font";
        String translated = UiText.tr(fontLabel);
        float labelScale = Math.clamp((columnWidth - 40) / (float) Math.max(1, font.width(translated)), 0.1f, 1f);
        addRenderableOnly((graphics, mx, my, delta) -> {
            var pose = graphics.pose();
            pose.pushMatrix();
            pose.translate(fontRowX, fontRowY + (18 - font.lineHeight * labelScale) / 2f);
            pose.scale(labelScale, labelScale);
            graphics.text(font, translated, 0, 0, PinkUi.TEXT, false);
            pose.popMatrix();
        });
        var fontToggle = addRenderableWidget(new PinkUi.Toggle(x, y, fontLabel,
            () -> ConfigManager.enabledModulesUseClickGuiFont, () -> {
                ConfigManager.enabledModulesUseClickGuiFont = !ConfigManager.enabledModulesUseClickGuiFont;
                ConfigManager.requestSave();
            }).rowWidth(columnWidth));
        fontToggle.setTooltip(Tooltip.create(UiText.component("Use the font selected in QOL → Fonts. Turn off to use the game font.")));
        y += Math.max(20, row);
        addRenderableWidget(new PinkUi.Slider(x, y, columnWidth, "Scale", ConfigManager.enabledModulesScale, .2, 5, .1, value -> {
            ConfigManager.enabledModulesScale = (float) value;
            ConfigManager.requestSave();
        }).showValue(value -> String.format(Locale.ROOT, "%.1f×", value)));
        y += 30;
        addRenderableWidget(new PinkUi.Slider(x, y, columnWidth, "Line Spacing", ConfigManager.enabledModulesSpacing, 0, 12, 1, value -> {
            ConfigManager.enabledModulesSpacing = (int) value;
            ConfigManager.requestSave();
        }).showValue(value -> (int) value + " px"));
        y += 30;
        int shadowWidth = (columnWidth - 6) * 2 / 5;
        addRenderableWidget(new PinkUi.Action(x, y, shadowWidth, buttonHeight, "Shadow", false, () -> {
            ConfigManager.enabledModulesShadow = !ConfigManager.enabledModulesShadow;
            ConfigManager.requestSave();
        }).selected(() -> ConfigManager.enabledModulesShadow));
        addRenderableWidget(new PinkUi.Action(x + shadowWidth + 6, y, columnWidth - shadowWidth - 6, buttonHeight,
            "Background", false, () -> {
                ConfigManager.enabledModulesBackground = !ConfigManager.enabledModulesBackground;
                ConfigManager.requestSave();
            }).selected(() -> ConfigManager.enabledModulesBackground));
        addRenderableWidget(new PinkUi.Action(x, y + row, columnWidth, buttonHeight,
            "Visible Modules", false, () -> {
                choosingModules = true;
                availableModules = EnabledModulesHud.availableModules(moduleCategory);
                moduleScroll = 0;
                rebuildWidgets();
            }));

        x += columnWidth + 12;
        y = panelY + 30;
        int colorOptionsX = x, colorOptionsY = y;
        addRenderableOnly((graphics, mx, my, delta) -> PinkUi.uiText(graphics, font, "Chroma",
            colorOptionsX, colorOptionsY + 5, PinkUi.TEXT, columnWidth - 41));
        addRenderableWidget(new PinkUi.Toggle(x, y, "Chroma", () -> ConfigManager.enabledModulesChroma, () -> {
            ConfigManager.enabledModulesChroma = !ConfigManager.enabledModulesChroma;
            ConfigManager.requestSave();
        }).rowWidth(columnWidth));
        y += 22;
        int fieldWidth = Math.max(font.width("#FFFFFF"), font.width("#RRGGBB")) + 19;
        int labelHeight = font.width(UiText.tr("Background Color")) + fieldWidth + 6 > columnWidth ? font.lineHeight + 3 : 0;
        int paletteHeight = Math.clamp((panelHeight - 184) / 2 - labelHeight, 12, 55);
        addColorControl(x, y, columnWidth, paletteHeight, fieldWidth, labelHeight, "Text Color", ConfigManager.enabledModulesColor, rgb -> {
            ConfigManager.enabledModulesColor = 0xFF000000 | rgb;
            ConfigManager.requestSave();
        });
        y += 31 + paletteHeight + labelHeight;
        addColorControl(x, y, columnWidth, paletteHeight, fieldWidth, labelHeight, "Background Color", ConfigManager.enabledModulesBackgroundColor, rgb -> {
            ConfigManager.enabledModulesBackgroundColor = (ConfigManager.enabledModulesBackgroundColor & 0xFF000000) | rgb;
            ConfigManager.requestSave();
        });
        y += 32 + paletteHeight + labelHeight;
        addRenderableWidget(new PinkUi.Slider(x, y, columnWidth, "Background Opacity",
            ConfigManager.enabledModulesBackgroundColor >>> 24, 0, 255, 1, value -> {
                ConfigManager.enabledModulesBackgroundColor = (int) value << 24 | (ConfigManager.enabledModulesBackgroundColor & 0xFFFFFF);
                ConfigManager.requestSave();
            }).showValue(value -> Math.round(value * 100 / 255) + "%"));
    }

    private void addColorControl(int x, int y, int width, int paletteHeight, int fieldWidth, int labelHeight,
                                 String label, int color, IntConsumer setColor) {
        addRenderableOnly((graphics, mx, my, delta) -> PinkUi.uiText(graphics, font, label, x,
            y + (labelHeight == 0 ? 6 : 0), PinkUi.TEXT, labelHeight == 0 ? width - fieldWidth - 6 : width));
        var field = addRenderableWidget(new PinkUi.Field(font, x + width - fieldWidth, y + labelHeight,
            fieldWidth, label + " HEX", 20));
        field.setMaxLength(7);
        field.setHint(Component.literal("#RRGGBB"));
        int rgb = color & 0xFFFFFF;
        field.setValue(TextColorOverrides.hex(rgb));
        var picker = addRenderableWidget(new PinkUi.ColorPicker(x, y + 24 + labelHeight, width, paletteHeight, rgb,
            value -> field.setValue(TextColorOverrides.hex(value))));
        field.setResponder(text -> {
            Integer value = text.matches("#?[0-9a-fA-F]{6}") ? TextColorOverrides.parse(text) : null;
            field.invalid(value == null);
            if (value != null) {
                if (picker.rgb() != value) picker.setColor(value);
                setColor.accept(value);
            }
        });
    }

    private void addModuleFilter(int x, int width) {
        moduleWidgets.clear();
        var categories = GameplayModule.Category.values();
        int tabWidth = width / (categories.length + 1);
        addCategory(x, tabWidth - 3, "All", null);
        for (int i = 0; i < categories.length; i++) {
            var category = categories[i];
            addCategory(x + (i + 1) * tabWidth, tabWidth - 3, category.label, category);
        }
        var field = addRenderableWidget(new PinkUi.Field(font, x, panelY + 56, width, "Search modules", 22));
        field.setHint(UiText.component("Search modules"));
        field.setMaxLength(80);
        field.setValue(moduleQuery);
        field.setResponder(value -> {
            moduleQuery = value;
            moduleScroll = 0;
            refreshModuleRows();
        });
        refreshModuleRows();
        addFooter("Back");
    }

    private void addCategory(int x, int width, String label, GameplayModule.Category category) {
        addRenderableWidget(new PinkUi.Action(x, panelY + 30, width, 20, label, false, () -> {
            moduleCategory = category;
            availableModules = EnabledModulesHud.availableModules(category);
            moduleScroll = 0;
            refreshModuleRows();
        }).selected(() -> moduleCategory == category));
    }

    private int moduleTop() { return panelY + MODULE_ROWS_TOP; }
    private int moduleBottom() { return panelBottom() - 37; }
    private int moduleScrollbarX() { return panelX + panelWidth - PADDING - 2; }

    private ScrollBarLayout moduleScrollbar() {
        return ScrollBarLayout.of(moduleTop(), moduleBottom() - moduleTop(),
            moduleRows.size(), moduleCount, moduleMaxScroll);
    }

    private void scrollModules(int next) {
        int offset = Math.clamp(next, 0, moduleMaxScroll);
        if (moduleScroll == offset) return;
        moduleScroll = offset;
        refreshModuleRows();
    }

    private void refreshModuleRows() {
        if (moduleWidgets.contains(getFocused())) setFocused(null);
        moduleWidgets.forEach(this::removeWidget);
        moduleWidgets.clear();
        String query = moduleQuery.strip().toLowerCase(Locale.ROOT);
        var matches = availableModules.stream().filter(name -> name.toLowerCase(Locale.ROOT).contains(query)
            || UiText.tr(name).toLowerCase(Locale.ROOT).contains(query)).toList();
        int rowsVisible = Math.max(1, (moduleBottom() - moduleTop()) / MODULE_ROW_HEIGHT);
        moduleCount = matches.size();
        moduleMaxScroll = Math.max(0, moduleCount - rowsVisible);
        moduleScroll = Math.clamp(moduleScroll, 0, moduleMaxScroll);
        moduleRows = matches.subList(moduleScroll, Math.min(moduleScroll + rowsVisible, moduleCount));
        int x = panelX + PADDING, y = moduleTop();
        int width = panelWidth - PADDING * 2 - (moduleMaxScroll > 0 ? 8 : 0);
        for (String module : moduleRows) {
            moduleWidgets.add(addRenderableWidget(new PinkUi.Toggle(x, y, module, () -> EnabledModulesHud.shown(module),
                () -> EnabledModulesHud.toggleShown(module)).rowWidth(width)));
            y += MODULE_ROW_HEIGHT;
        }
    }

    private void changed() {ConfigManager.requestSave();rebuildWidgets();}
    private void selectFile(String file) {
        name = file;
        confirmation = null;
        notice = "";
    }

    private boolean awaiting(String action) {
        return confirmation != null && confirmation.action().equals(action);
    }

    private void act(String action, String file) {
        try {
            var path = CheatingConfigs.path(file);
            var requested = new Confirmation(action, file);
            if ((!action.equals("save") || java.nio.file.Files.exists(path)) && !requested.equals(confirmation)) {
                confirmation = requested;
                return;
            }
            confirmation = null;
            CheatingConfigs.run(action, file);
            notice = "Config " + action + ": " + file;
            rebuildWidgets();
        } catch (Exception error) {
            confirmation = null;
            notice = error.getMessage();
        }
    }

    private void drawConfirmation(GuiGraphicsExtractor g) {
        int x = panelX + PADDING, y = confirmationY(), w = panelWidth - 2 * PADDING;
        int ink = 0xFF442706, edge = 0xFFC67C1B;
        PinkUi.outline(g, x, y, w, CONFIRMATION_HEIGHT, 5, edge, 0xFFFFE3A8);
        PinkUi.rounded(g, x + 5, y + 5, 3, CONFIRMATION_HEIGHT - 10, 1, edge);
        var message = UiText.component(switch (confirmation.action()) {
            case "load" -> "Click Load again to confirm";
            case "remove" -> "Click Remove again to confirm";
            default -> "Click Save again to overwrite";
        }).withStyle(ChatFormatting.BOLD);
        g.text(font, Language.getInstance().getVisualOrder(font.substrByWidth(message, w - 20)), x + 13, y + 4, ink, false);
        PinkUi.text(g, font, confirmation.file(), x + 13, y + 16, ink, w - 20);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int x, int y, float delta) {
        if (minecraft.level == null) super.extractBackground(g, x, y, delta);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta) {
        if (hudEditor != null) {
            boolean overPanel = insidePanel(x, y);
            hudEditor.drawPreview(g, overPanel ? Integer.MIN_VALUE : x, overPanel ? Integer.MIN_VALUE : y);
        } else if (style) EnabledModulesHud.draw(g, true);
        g.nextStratum();
        int dx = widgetOffsetX(), dy = widgetOffsetY();
        g.pose().pushMatrix();
        g.pose().translate(dx, dy);
        try { drawPanel(g, x - dx, y - dy, delta); }
        finally { g.pose().popMatrix(); }
    }

    private void drawPanel(GuiGraphicsExtractor g, int x, int y, float delta) {
        ClickGuiChrome.drawPanel(g, panelX, panelY, panelWidth, panelHeight, 8);
        Component title = choosingModules ? UiText.component("Visible Modules") : getTitle();
        g.text(font, title, panelX + (panelWidth - font.width(title)) / 2, panelY + 12, PinkUi.TEXT, false);
        if (choosingModules) {
            int rowWidth = panelWidth - PADDING * 2;
            int left = panelX + PADDING, top = moduleTop();
            for (int i = 0; i < moduleRows.size(); i++) {
                PinkUi.uiText(g, font, moduleRows.get(i), left, top + MODULE_ROW_HEIGHT * i + 5, PinkUi.TEXT, rowWidth - 50);
            }
            if (moduleRows.isEmpty()) PinkUi.uiText(g, font, "No matching modules", left, top + 5, PinkUi.MUTED, rowWidth);
            if (moduleMaxScroll > 0) {
                var bar = moduleScrollbar();
                int right = moduleScrollbarX();
                PinkUi.rounded(g, right, top, 3, moduleBottom() - top, 1, PinkUi.BORDER);
                PinkUi.rounded(g, right, bar.thumbStart(moduleScroll), 3, bar.thumbLength(), 1, PinkUi.PINK);
            }
        }
        if (confirmation != null) drawConfirmation(g);
        else if (notice != null && !notice.isEmpty()) PinkUi.text(g, font, notice, panelX + PADDING, panelBottom() - 43,
            PinkUi.TEXT, panelWidth - PADDING * 2);
        g.nextStratum();
        super.extractRenderState(g,x,y,delta);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean twice) {
        if (!insidePanel(event.x(), event.y())) {
            return hudEditor != null && hudEditor.beginHudDrag(event) || super.mouseClicked(panelEvent(event), twice);
        }
        var local = panelEvent(event);
        if (style && event.button() == ClientInput.MOUSE_LEFT) {
            if (local.y() < panelY + 27) {
                panelGrabX = local.x() - panelX;
                panelGrabY = local.y() - panelY;
                draggingPanel = true;
                return true;
            }
            if (choosingModules && moduleMaxScroll > 0 && local.x() >= moduleScrollbarX() - 3
                    && local.y() >= moduleTop() && local.y() < moduleBottom()) {
                var bar = moduleScrollbar();
                int offset = (int) local.y() - bar.thumbStart(moduleScroll);
                scrollbarGrab = offset >= 0 && offset < bar.thumbLength() ? offset : bar.thumbLength() / 2;
                scrollModules(bar.offsetAtPointer(local.y(), scrollbarGrab));
                return true;
            }
        }
        return super.mouseClicked(local, twice);
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingPanel && event.button() == ClientInput.MOUSE_LEFT) {
            movedX = Math.clamp((int) Math.round(event.x() - panelGrabX), MARGIN, width - panelWidth - MARGIN);
            movedY = Math.clamp((int) Math.round(event.y() - panelGrabY), MARGIN, height - panelHeight - MARGIN);
            return true;
        }
        if (scrollbarGrab >= 0 && event.button() == ClientInput.MOUSE_LEFT) {
            scrollModules(moduleScrollbar().offsetAtPointer(event.y() - widgetOffsetY(), scrollbarGrab));
            return true;
        }
        return hudEditor != null && hudEditor.dragHud(event) || super.mouseDragged(panelEvent(event), dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == ClientInput.MOUSE_LEFT && (draggingPanel || scrollbarGrab >= 0)) {
            draggingPanel = false;
            scrollbarGrab = -1;
            return true;
        }
        return hudEditor != null && hudEditor.endHudDrag(event) || super.mouseReleased(panelEvent(event));
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (insidePanel(x, y)) {
            if (choosingModules && vertical != 0 && y - widgetOffsetY() >= moduleTop()
                    && y - widgetOffsetY() < moduleBottom()) {
                int rows = Math.max(1, (int) Math.round(Math.abs(vertical) * 3));
                scrollModules(moduleScroll + (vertical < 0 ? rows : -rows));
                return true;
            }
        } else if (hudEditor != null && hudEditor.scaleHudAt(x, y, vertical)) {
            return true;
        }
        return super.mouseScrolled(x - widgetOffsetX(), y - widgetOffsetY(), horizontal, vertical);
    }
    @Override public void onClose() {
        if (choosingModules) {
            choosingModules = false;
            rebuildWidgets();
            return;
        }
        ConfigManager.requestSave();
        ClientUiAccess.setScreen(minecraft, parent);
    }
    @Override public void removed() {
        if (hudEditor != null) hudEditor.finishHudDrag();
        draggingPanel = false;
        scrollbarGrab = -1;
        super.removed();
    }
    @Override public boolean isPauseScreen(){return false;}
}
