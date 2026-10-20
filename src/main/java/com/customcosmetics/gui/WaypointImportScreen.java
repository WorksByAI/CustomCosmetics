// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.NativeFileDialogs;
import com.customcosmetics.features.waypoint.WaypointCodec;
import com.customcosmetics.features.waypoint.WaypointData.Group;
import com.customcosmetics.features.waypoint.Waypoints;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static com.customcosmetics.gui.PinkUi.*;

/** Imports Skyblocker's on-disk config without changing the source file. */
public final class WaypointImportScreen extends Screen {
    private final Screen parent;
    private Field path;
    private Action importButton;
    private List<Group> preview;
    private boolean reading;
    private String notice = "Choose Skyblocker's config/skyblocker/waypoints.json, then preview it.";
    private int x, y, w, h;

    public WaypointImportScreen(Screen parent) { super(Component.literal("Import Skyblocker Waypoints")); this.parent = parent; }

    @Override protected void init() {
        w = Math.min(640, width - 16); h = Math.min(230, height - 16); x = (width - w) / 2; y = (height - h) / 2;
        path = addRenderableWidget(new Field(font, x + 12, y + 44, w - 116, "Skyblocker waypoints.json"));
        path.setMaxLength(4096);
        Path defaultFile = FabricLoader.getInstance().getConfigDir().resolve("skyblocker/waypoints.json");
        path.setValue(defaultFile.toString());
        path.setResponder(value -> { preview = null; if (importButton != null) importButton.active = false; });
        addRenderableWidget(new Action(x + w - 96, y + 44, 84, 24, "Browse", false, this::browse));
        int third = (w - 36) / 3;
        addRenderableWidget(new Action(x + 12, y + h - 33, third, 22, "Preview", false, this::preview));
        importButton = addRenderableWidget(new Action(x + 18 + third, y + h - 33, third, 22, "Import", true, this::apply));
        importButton.active = false;
        addRenderableWidget(new Action(x + 24 + third * 2, y + h - 33, w - 36 - third * 2, 22, "Back", false, this::onClose));
    }

    private void browse() {
        boolean sdl = FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata()
                .getVersion().getFriendlyString().startsWith("26.3");
        long window = 0;
        if (sdl) try { window = (long) minecraft.getWindow().getClass().getMethod("handle").invoke(minecraft.getWindow()); }
        catch (ReflectiveOperationException failure) { notice = "Paste a JSON path or drop the file here."; return; }
        Path file = FabricLoader.getInstance().getConfigDir().resolve("skyblocker/waypoints.json");
        Path directory = Files.isDirectory(file.getParent()) ? file.getParent() : CapeFileScreen.defaultDirectory();
        NativeFileDialogs.open(new NativeFileDialogs.Request(false, directory, sdl, window, "waypoint"),
                minecraft::execute, result -> {
                    if (ClientUiAccess.screen(minecraft) != this) return;
                    if (result.path() != null) { path.setValue(result.path().toString()); preview(); }
                    else if (result.unavailable()) notice = "Paste a JSON path or drop the file here.";
                    else if (!result.error().isEmpty()) notice = result.error();
                });
    }

    private void preview() {
        if (reading) return;
        Path selected;
        try { selected = Path.of(path.getValue().strip().replaceAll("^\"|\"$", "")); }
        catch (RuntimeException invalid) { notice = "Invalid file path."; return; }
        reading = true; preview = null; importButton.active = false; notice = "Reading waypoint groups…";
        String scope = Waypoints.currentScope();
        CompletableFuture.supplyAsync(() -> {
            try {
                byte[] bytes;
                try (var input = Files.newInputStream(selected)) { bytes = input.readNBytes(WaypointCodec.MAX_BYTES + 1); }
                if (bytes.length > WaypointCodec.MAX_BYTES) throw new IllegalArgumentException("Waypoint file exceeds 2 MiB.");
                String json = StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes)).toString();
                return WaypointCodec.importConfig(json, scope);
            } catch (Exception failure) { throw new java.util.concurrent.CompletionException(failure); }
        }).whenComplete((groups, failure) -> minecraft.execute(() -> {
            if (ClientUiAccess.screen(minecraft) != this) return;
            reading = false;
            if (failure != null) {
                Throwable cause = failure.getCause() == null ? failure : failure.getCause();
                notice = "Could not read Skyblocker waypoints: " + cause.getMessage(); return;
            }
            preview = groups; importButton.active = !groups.isEmpty();
            int count = groups.stream().mapToInt(group -> group.points().size()).sum();
            notice = "Ready: " + groups.size() + " group(s), " + count + " waypoint(s). Existing groups stay intact.";
        }));
    }

    private void apply() {
        if (preview == null || reading) return;
        try {
            int count = Waypoints.importGroups(preview);
            preview = null; importButton.active = false;
            notice = "Imported " + count + " group(s). Islands remain separate.";
        } catch (RuntimeException failure) { notice = "Import failed: " + failure.getMessage(); }
    }

    @Override public void onFilesDrop(List<Path> paths) {
        if (!paths.isEmpty()) { path.setValue(paths.getFirst().toString()); preview(); }
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        outline(g, x, y, w, h, 10, BORDER, PANEL);
        text(g, font, getTitle().getString(), x + 12, y + 14, PINK, w - 24);
        int yy = y + 82;
        for (var line : font.split(Component.literal(notice), w - 24)) {
            if (yy > y + h - 44) break;
            g.text(font, line, x + 12, yy, TEXT, false); yy += 12;
        }
        super.extractRenderState(g, mx, my, dt);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }
}
