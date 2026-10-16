package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.map.MapEditor;
import com.customcosmetics.gui.MapBlockScreen;
import jdk.jfr.Recording;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.components.EditBox;
import java.nio.file.Path;
import java.time.Duration;

/** Runs the full, unfiltered palette through the real GUI renderer, not a mocked item list. */
public final class MapPaletteGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create(); var recording = new Recording()) {
            world.getServer().runCommand("gamemode creative @a");
            context.waitTicks(25);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10)).withStackTrace();
            recording.start();
            context.runOnClient(client -> ClientUiAccess.setScreen(client, new MapBlockScreen(null)));
            context.waitTicks(60);
            context.runOnClient(client -> {
                try { System.out.println("MAP_PALETTE_FPS=" + client.getClass().getMethod("getFps").invoke(client)); }
                catch (ReflectiveOperationException ignored) {}
            });
            recording.stop();
            recording.dump(Path.of("map-palette-profile.jfr"));
            context.takeScreenshot("map-palette-full");
            context.runOnClient(client -> client.options.guiScale().set(1));
            context.getInput().resizeWindow(1280,800);
            context.waitTicks(5);
            try (var largeGrid = new Recording()) {
                largeGrid.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(10)).withStackTrace();largeGrid.start();
                context.waitTicks(60);
                context.runOnClient(client -> {
                    long visible = ClientUiAccess.screen(client).children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible && w.getClass().getSimpleName().equals("Cell")).count();
                    if (visible < 171) throw new AssertionError("Stress palette did not fill the large grid: " + visible);
                    try { System.out.println("MAP_PALETTE_LARGE_GRID_CELLS=" + visible + "; FPS=" + client.getClass().getMethod("getFps").invoke(client)); }
                    catch (ReflectiveOperationException ignored) {}
                });
                largeGrid.stop();largeGrid.dump(Path.of("map-palette-large-grid-profile.jfr"));
            }
            context.takeScreenshot("map-palette-large-grid");
            context.runOnClient(client -> {
                var screen = ClientUiAccess.screen(client);
                var search = (EditBox) screen.children().stream().filter(EditBox.class::isInstance).findFirst().orElseThrow();
                search.setValue("chest");
                long matches=screen.children().stream().filter(c -> c instanceof net.minecraft.client.gui.components.AbstractWidget w && w.visible && w.getClass().getSimpleName().equals("Cell")).count();
                if(matches==0 || matches>=171) throw new AssertionError("Search did not filter the palette: " + matches);
            });
            context.waitTicks(5);
            context.takeScreenshot("map-palette-search");
            context.runOnClient(client -> {
                if (!(ClientUiAccess.screen(client) instanceof MapBlockScreen)) throw new AssertionError("Palette closed while filtering");
                ClientUiAccess.setScreen(client, null);
                MapEditor.enabled = false;
            });
        } catch (java.io.IOException e) { throw new AssertionError(e); }
        System.out.println("MAP_PALETTE_FULL_RENDER_SEARCH_OK");
    }
}
