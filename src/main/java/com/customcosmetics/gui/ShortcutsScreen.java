// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.shortcuts.*;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.io.IOException;
import java.util.*;
import static com.customcosmetics.gui.PinkUi.*;

/** Command-only manager: aliases, exact argument replacements and recorded key chords. */
public final class ShortcutsScreen extends ThemedScreen {
    private final Screen parent;
    private final List<PinkUi.Action> rows = new ArrayList<>();
    private final LinkedHashSet<String> captured = new LinkedHashSet<>();
    private ShortcutRules.Kind kind = ShortcutRules.Kind.ALIAS;
    private List<ShortcutRules.Entry> filtered = List.of();
    private String id = "", trigger = "", command = "", status = "";
    private boolean editing, entryEnabled = true, recording, confirmDelete;
    private int x, y, w, h, offset;
    private PinkUi.Action captureButton;

    public ShortcutsScreen(Screen parent) { super(Component.literal("Shortcuts")); this.parent = parent; status = Shortcuts.status(); }
    @Override protected void init() {
        w = Math.min(620, width - 16); h = Math.min(374, height - 16);
        x = (width - w) / 2; y = (height - h) / 2; rows.clear(); recording = false;
        if (editing) editor(); else list();
    }
    private PinkUi.Action button(int bx, int by, int bw, String label, boolean primary, Runnable action) {
        return addRenderableWidget(new PinkUi.Action(bx, by, bw, 21, label, primary, action));
    }
    private void list() {
        button(x + w - 104, y + 8, 92, Shortcuts.data().enabled() ? "Enabled" : "Disabled", false, () -> {
            run(() -> Shortcuts.save(new ShortcutStore.Data(!Shortcuts.data().enabled(), Shortcuts.data().entries())));
            rebuildWidgets();
        });
        int third = (w - 32) / 3;
        for (int i = 0; i < ShortcutRules.Kind.values().length; i++) {
            var tab = ShortcutRules.Kind.values()[i];
            button(x + 12 + i * (third + 4), y + 36, third, label(tab), false, () -> {
                kind = tab; offset = 0; status = ""; rebuildWidgets();
            }).selected(() -> kind == tab);
        }
        int count = Math.max(1, (h - 132) / 27);
        for (int i = 0; i < count; i++) {
            int row = i;
            rows.add(button(x + 12, y + 66 + i * 27, w - 24, "", false, () -> edit(filtered.get(offset + row))));
        }
        button(x + 12, y + h - 59, third, "New", true, () -> edit(null)).active = Shortcuts.writable();
        button(x + 16 + third, y + h - 59, third, "Reload", false, () -> {
            Shortcuts.reload(); status = Shortcuts.status().isEmpty() ? "Reloaded." : Shortcuts.status(); refresh();
        });
        button(x + 20 + third * 2, y + h - 59, third, "Done", false, this::onClose);
        refresh();
    }
    private void refresh() {
        filtered = Shortcuts.data().entries().stream().filter(e -> e.kind() == kind).toList();
        offset = Math.clamp(offset, 0, Math.max(0, filtered.size() - rows.size()));
        for (int i = 0; i < rows.size(); i++) {
            var row = rows.get(i); row.visible = offset + i < filtered.size();
            if (row.visible) {
                var entry = filtered.get(offset + i);
                row.setMessage(Component.literal((entry.enabled() ? "" : "[Off] ") + displayTrigger(entry) + "  →  " + entry.command()));
            }
        }
    }
    private void edit(ShortcutRules.Entry entry) {
        editing = true; confirmDelete = false; captured.clear(); status = "";
        id = entry == null ? "" : entry.id(); trigger = entry == null ? "" : entry.trigger();
        command = entry == null ? "" : entry.command(); entryEnabled = entry == null || entry.enabled();
        rebuildWidgets();
    }
    private void editor() {
        int full = w - 24, half = (full - 6) / 2;
        if (kind == ShortcutRules.Kind.KEY) {
            captureButton = button(x + 12, y + 64, full, keyLabel(), false, () -> {
                recording = true; captured.clear(); status = "Hold up to 4 keys/buttons, then release. Esc cancels.";
                captureButton.setMessage(UiText.component("Recording…"));
                setFocused(captureButton);
            });
        } else {
            var field = addRenderableWidget(new PinkUi.Field(font, x + 12, y + 64, full, "Shortcut", 20));
            field.setMaxLength(180); field.setValue(trigger);
            field.setHint(Component.literal(kind == ShortcutRules.Kind.ALIAS ? "Example: home" : "Example: @friend or /v"));
            field.setResponder(value -> trigger = value);
        }
        var target = addRenderableWidget(new PinkUi.Field(font, x + 12, y + 105, full, "Command or replacement", 20));
        target.setMaxLength(256); target.setValue(command);
        target.setHint(Component.literal(kind == ShortcutRules.Kind.ARGUMENT ? "Exact replacement text" : "/warp home"));
        target.setResponder(value -> command = value);
        button(x + 12, y + h - 59, half, entryEnabled ? "Shortcut: On" : "Shortcut: Off", false, () -> {
            entryEnabled = !entryEnabled; rebuildWidgets();
        });
        if (!id.isEmpty()) button(x + 18 + half, y + h - 59, full - half - 6,
            confirmDelete ? "Confirm Delete" : "Delete", false, () -> {
                if (!confirmDelete) { confirmDelete = true; rebuildWidgets(); return; }
                if (run(() -> Shortcuts.save(new ShortcutStore.Data(Shortcuts.data().enabled(),
                    Shortcuts.data().entries().stream().filter(e -> !e.id().equals(id)).toList())))) backToList();
            });
        button(x + 12, y + h - 30, (full - 6) / 2, "Save", true, this::save).active = Shortcuts.writable();
        button(x + 18 + half, y + h - 30, full - half - 6, "Cancel", false, this::backToList);
    }
    private void save() {
        if (recording) { status = "Release the recorded keys first."; return; }
        String normalized = trigger.strip();
        if (kind == ShortcutRules.Kind.ALIAS) normalized = ShortcutRules.withoutSlash(normalized);
        String savedId = id.isEmpty() ? UUID.randomUUID().toString() : id;
        var entry = new ShortcutRules.Entry(savedId, kind, normalized, command.strip(), entryEnabled);
        List<ShortcutRules.Entry> next = new ArrayList<>(Shortcuts.data().entries());
        next.removeIf(e -> e.id().equals(savedId)); next.add(entry);
        if (run(() -> Shortcuts.save(new ShortcutStore.Data(Shortcuts.data().enabled(), next)))) {
            backToList(); status = "Saved.";
        }
    }
    private void backToList() { editing = false; recording = false; confirmDelete = false; status = ""; rebuildWidgets(); }
    private boolean run(IoAction action) {
        try { action.run(); return true; }
        catch (IOException | IllegalArgumentException error) { status = error.getMessage(); return false; }
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private static String label(ShortcutRules.Kind value) {
        return switch (value) { case ALIAS -> "Commands"; case ARGUMENT -> "Arguments"; case KEY -> "Key Bindings"; };
    }
    private static String displayTrigger(ShortcutRules.Entry entry) {
        if (entry.kind() == ShortcutRules.Kind.ALIAS) return "/" + entry.trigger();
        if (entry.kind() != ShortcutRules.Kind.KEY) return entry.trigger();
        try { return String.join(" + ", ShortcutRules.keys(entry.trigger()).stream().map(key -> InputConstants.getKey(key).getDisplayName().getString()).toList()); }
        catch (RuntimeException ignored) { return entry.trigger(); }
    }
    private String keyLabel() {
        return trigger.isEmpty() ? "Record key / combination…" : displayTrigger(new ShortcutRules.Entry("", kind, trigger, "", true));
    }
    private void capture(InputConstants.Key key) {
        if (!key.equals(InputConstants.UNKNOWN) && captured.size() < 4) captured.add(key.getName());
        captureButton.setMessage(UiText.component("Recording: " + String.join(" + ", captured.stream()
            .map(name -> InputConstants.getKey(name).getDisplayName().getString()).toList())));
    }
    private boolean finishCapture() {
        if (!recording) return false;
        if (!captured.isEmpty()) trigger = String.join(" + ", captured.stream().sorted().toList());
        recording = false; status = ""; captureButton.setMessage(Component.literal(keyLabel())); return true;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (recording) {
            if (event.key() == ClientInput.ESCAPE) { captured.clear(); finishCapture(); }
            else capture(InputConstants.getKey(event));
            return true;
        }
        if (editing && event.key() == ClientInput.ESCAPE) { backToList(); return true; }
        return super.keyPressed(event);
    }
    @Override public boolean keyReleased(KeyEvent event) {
        return recording && !captured.isEmpty() ? finishCapture() : super.keyReleased(event);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (recording) { capture(InputConstants.Type.MOUSE.getOrCreate(event.button())); return true; }
        return super.mouseClicked(event, doubleClick);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        // The release of the click that starts recording must not end an empty recording.
        return recording && !captured.isEmpty() ? finishCapture() : super.mouseReleased(event);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (!editing && mx >= x && mx < x + w && my >= y + 62 && my < y + h - 64) {
            offset -= (int) Math.signum(vertical) * 3; refresh(); return true;
        }
        return super.mouseScrolled(mx, my, horizontal, vertical);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        outline(g, x, y, w, h, 10, BORDER, PANEL);
        text(g, font, editing ? label(kind) + " · " + (id.isEmpty() ? "New shortcut" : "Edit shortcut") : "Shortcuts", x + 12, y + 15, PINK, editing ? w - 24 : w - 128);
        if (editing) {
            text(g, font, kind == ShortcutRules.Kind.KEY ? "Press once to send one command" : kind == ShortcutRules.Kind.ALIAS ? "Command alias" : "Exact token replacement", x + 12, y + 37, MUTED, w - 24);
            text(g, font, kind == ShortcutRules.Kind.KEY ? "Keyboard / mouse combination" : "Trigger", x + 12, y + 52, TEXT, w - 24);
            text(g, font, kind == ShortcutRules.Kind.ARGUMENT ? "Replacement" : "Command", x + 12, y + 93, TEXT, w - 24);
            String help = switch (kind) {
                case ALIAS -> "Arguments append automatically; $1, $2 and $* insert arguments.";
                case ARGUMENT -> "Whole tokens only, once: /visit @friend → /visit PlayerName";
                case KEY -> "Bindings work only in-game. Holding a key never repeats.";
            };
            text(g, font, help, x + 12, y + 136, MUTED, w - 24);
            uiText(g, font, status, x + 12, y + 150, PINK, w - 24);
        } else {
            if (filtered.isEmpty()) uiText(g, font, "No shortcuts yet. Choose New to add one.", x + 16, y + 76, MUTED, w - 32);
            text(g, font, status.isEmpty() ? filtered.size() + " shortcuts · Click to edit · Scroll to browse" : status, x + 12, y + h - 27, status.isEmpty() ? MUTED : PINK, w - 24);
        }
        super.extractRenderState(g, mx, my, dt);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }
}
