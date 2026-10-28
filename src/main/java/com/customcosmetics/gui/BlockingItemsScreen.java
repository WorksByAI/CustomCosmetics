// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.blocking.BlockingItems;
import com.customcosmetics.features.blocking.BlockingRules;
import com.customcosmetics.features.blocking.WeaponCatalog.Entry;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import java.util.*;
import static com.customcosmetics.gui.PinkUi.*;

/** Searchable local selections, with independent blacklists and whitelists. */
public final class BlockingItemsScreen extends ThemedScreen {
    private final Screen parent;
    private final List<Entry> catalog = new ArrayList<>(BlockingItems.catalog());
    private final Set<String> blacklist = new TreeSet<>(List.of(BlockingRules.sanitize(ConfigManager.swordBlockingBlacklist)));
    private final Set<String> whitelist = new TreeSet<>(List.of(BlockingRules.sanitize(ConfigManager.swordBlockingWhitelist)));
    private final List<WeaponChoice> rows = new ArrayList<>();
    private final List<AbstractWidget> fixed = new ArrayList<>();
    private boolean white = BlockingRules.Mode.parse(ConfigManager.swordBlockingListMode) == BlockingRules.Mode.WHITELIST;
    private boolean selectedOnly;
    private String query = "";
    private int source, offset, x, y, w, h, listTop, listBottom;
    private List<Entry> filtered = List.of();
    private PinkUi.Field search;

    public BlockingItemsScreen(Screen parent) { super(Component.literal("Blocking Lists")); this.parent = parent; }
    private Set<String> selected() { return white ? whitelist : blacklist; }
    @Override protected void init() {
        fixed.clear(); rows.clear();
        w = Math.min(600, width - 16); h = Math.min(436, height - 16);
        x = (width - w) / 2; y = (height - h) / 2;
        int half = (w - 40) / 2;
        button(x + 16, y + 42, half, "Blacklist", () -> switchList(false)).selected(() -> !white);
        button(x + 24 + half, y + 42, half, "Whitelist", () -> switchList(true)).selected(() -> white);
        search = new PinkUi.Field(font, x + 16, y + 76, w - 32, "Search weapons");
        search.setMaxLength(128); search.setValue(query);
        search.setHint(UiText.component("Search name or item ID"));
        search.setResponder(value -> { CosmeticsScreen.childInteraction(); query = value; offset = 0; refreshRows(); });
        fixed.add(addWidget(search));
        int part = (w - 50) / 4;
        String[] sources = {"All", "Minecraft", "SkyBlock"};
        for (int i = 0; i < sources.length; i++) {
            int index = i;
            button(x + 16 + i * (part + 6), y + 114, part, sources[i], () -> {
                source = index; offset = 0; refreshRows();
            }).selected(() -> source == index);
        }
        button(x + 16 + 3 * (part + 6), y + 114, part, "Selected", () -> {
            selectedOnly = !selectedOnly; offset = 0; refreshRows();
        }).selected(() -> selectedOnly);
        boolean compact = h < 280;
        listTop = y + 145; listBottom = y + h - (compact ? 40 : 80);
        int count = Math.max(1, (listBottom - listTop) / 36);
        for (int i = 0; i < count; i++) rows.add(addWidget(new WeaponChoice(x + 16, listTop + i * 36, w - 42)));
        if (!compact) {
            button(x + 24 + half, y + h - 69, half, "Clear Results", () -> {
                for (Entry entry : filtered) selected().remove(entry.id());
                saveSelections(); refreshRows();
            });
        }
        button(x + 16, y + h - 35, half, "Add Held Item", this::addHeldItem).active = minecraft.player != null;
        button(x + 24 + half, y + h - 35, half, "Done", this::onClose);
        refreshRows();
    }
    private PinkUi.Action button(int bx, int by, int bw, String label, Runnable action) {
        var button = addWidget(new PinkUi.Action(bx, by, bw, 26, label, false, action));
        fixed.add(button); return button;
    }
    private void switchList(boolean value) { white = value; offset = 0; refreshRows(); }
    private void refreshRows() {
        filtered = catalog.stream().filter(entry -> entry.matches(query))
            .filter(entry -> source == 0 || (source == 2) == entry.skyblock())
            .filter(entry -> !selectedOnly || selected().contains(entry.id())).toList();
        offset = Math.clamp(offset, 0, Math.max(0, filtered.size() - rows.size()));
        for (int i = 0; i < rows.size(); i++) {
            WeaponChoice row = rows.get(i);
            row.entry = offset + i < filtered.size() ? filtered.get(offset + i) : null;
            row.visible = row.entry != null;
            if (row.entry != null) row.setMessage(Component.literal(row.entry.name() + ": " + row.entry.id()
                + (selected().contains(row.entry.id()) ? ", selected" : ", unselected")));
        }
    }
    private void saveSelections() {
        ConfigManager.swordBlockingBlacklist = blacklist.toArray(String[]::new);
        ConfigManager.swordBlockingWhitelist = whitelist.toArray(String[]::new);
        ConfigManager.requestSave();
    }
    private void addHeldItem() {
        if (minecraft.player == null) return;
        var stack = minecraft.player.getMainHandItem();
        String id = BlockingItems.identity(stack);
        if (id.isEmpty()) return;
        if (catalog.stream().noneMatch(entry -> entry.id().equals(id)))
            catalog.add(new Entry(id, stack.getHoverName().getString(), "Custom"));
        selected().add(id); saveSelections();
        source = 0; selectedOnly = false; search.setValue(id);
        offset = 0; refreshRows();
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        CosmeticsScreen.childInteraction();
        if (mx >= x + 16 && mx < x + w - 16 && my >= listTop && my < listBottom) {
            offset = Math.clamp(offset - (int) Math.signum(vertical) * 3, 0, Math.max(0, filtered.size() - rows.size()));
            refreshRows(); return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        CosmeticsScreen.childInteraction();
        if (event.key() == ClientInput.PAGE_UP || event.key() == ClientInput.PAGE_DOWN) {
            offset += (event.key() == ClientInput.PAGE_UP ? -1 : 1) * rows.size(); refreshRows(); return true;
        }
        return super.keyPressed(event);
    }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        CosmeticsScreen.childInteraction(); return super.mouseClicked(event,doubleClick);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        outline(g, x, y, w, h, 11, BORDER, PANEL);
        uiText(g, font, "Blocking Lists · " + selected().size() + " selected", x + 16, y + 17, PINK, w - 32);
        for (AbstractWidget widget : fixed) widget.extractRenderState(g, mx, my, dt);
        g.enableScissor(x + 15, listTop, x + w - 15, listBottom);
        for (WeaponChoice row : rows) if (row.visible) row.extractRenderState(g, mx, my, dt);
        if (filtered.isEmpty()) uiText(g, font, "No matching weapons", x + 28, listTop + 16, MUTED, w - 56);
        if (filtered.size() > rows.size()) {
            int track = rows.size() * 36;
            int thumb = Math.max(14, track * rows.size() / filtered.size());
            rounded(g, x + w - 21, listTop, 3, track, 1, BORDER);
            rounded(g, x + w - 21, listTop + offset * (track - thumb) / (filtered.size() - rows.size()), 3, thumb, 1, PINK);
        }
        g.disableScissor();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { saveSelections(); ConfigManager.saveConfig(); }
    @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }

    private final class WeaponChoice extends PinkUi.Action {
        private Entry entry;
        WeaponChoice(int bx, int by, int bw) { super(bx, by, bw, 32, "Weapon", false, () -> {}); }
        @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) { toggle(); }
        @Override public boolean keyPressed(KeyEvent event) {
            if (event.key() == ClientInput.ENTER || event.key() == ClientInput.NUMPAD_ENTER || event.key() == ClientInput.SPACE) {
                toggle(); return true;
            }
            return false;
        }
        private void toggle() {
            if (entry == null) return;
            if (!BlockingItemsScreen.this.selected().remove(entry.id())) BlockingItemsScreen.this.selected().add(entry.id());
            saveSelections(); refreshRows();
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            if (entry == null) return;
            boolean effect=beginHoverTransform(g,mx,my);
            try {
            boolean checked = BlockingItemsScreen.this.selected().contains(entry.id());
            outline(g, getX(), getY(), getWidth(), getHeight(), 5, isFocused() ? PINK : BORDER,
                isHoveredOrFocused() ? 0x27FFFFFF : CARD);
            outline(g, getX() + 9, getY() + 9, 14, 14, 3, checked ? PINK : BORDER, checked ? PINK_DARK : SIDEBAR);
            if (checked) uiText(g, font, "✓", getX() + 12, getY() + 12, PINK, 10);
            text(g, font, entry.name(), getX() + 32, getY() + 5, checked ? PINK : TEXT, getWidth() - 42);
            text(g, font, entry.id(), getX() + 32, getY() + 18, MUTED, getWidth() - 42);
            } finally {if(effect)g.pose().popMatrix();}
        }
    }
}
