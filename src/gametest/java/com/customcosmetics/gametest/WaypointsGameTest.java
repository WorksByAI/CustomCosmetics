// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.waypoint.*;
import com.customcosmetics.gui.PinkUi;
import com.customcosmetics.gui.WaypointScreen;
import com.customcosmetics.gui.WaypointImportScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.*;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import static com.customcosmetics.features.waypoint.WaypointData.*;

/** Real input, command dispatch, framebuffer pixels and persistence in the isolated client instance. */
public final class WaypointsGameTest implements FabricClientGameTest {
    private static volatile boolean occlusionWall;
    private static volatile float fixtureDepthFar;
    private static double occlusionWallDistance = 200;
    private static RenderType occlusionType;
    @Override public void runTest(ClientGameTestContext context) {
        var clipboardBefore = new AtomicReference<String>();
        try {
            Path project = Path.of(Objects.requireNonNull(System.getProperty("customcosmetics.testProjectDir"))).toAbsolutePath().normalize();
            Path file = FabricLoader.getInstance().getConfigDir().resolve("customcosmetics/waypoints.json").toAbsolutePath().normalize();
            check(file.startsWith(project.resolve("build")), "Waypoint fixture would write outside the isolated build directory");
            Path skyblockerFile = FabricLoader.getInstance().getConfigDir().resolve("skyblocker/waypoints.json").toAbsolutePath().normalize();
            check(skyblockerFile.startsWith(project.resolve("build")), "Skyblocker fixture would write outside the isolated build directory");
            Files.createDirectories(skyblockerFile.getParent());
            Files.writeString(skyblockerFile, """
                [{"name":"Hub","island":"hub","waypoints":[{"pos":[1,70,2],"name":"",
                    "colorComponents":[1,0,0],"shouldRender":true}]},
                 {"name":"Mining","island":"mining_3","ordered":true,"waypoints":[
                    {"pos":[3,100,4],"name":"Ore","colorComponents":[0,1,0],"shouldRender":false}]}]
                """);
            context.runOnClient(client -> {
                clipboardBefore.set(client.keyboardHandler.getClipboard());
                ConfigManager.offlineMode = true; ConfigManager.syncEnabled = false; ConfigManager.disabledModules = -1;
                Waypoints.replace(Snapshot.empty()); client.options.guiScale().set(2);
                registerOcclusionWall();
            });
            AtomicReference<Group> saved = new AtomicReference<>();
            try (var world = context.worldBuilder().create()) {
                world.getServer().runCommand("gamemode creative @a");
                world.getServer().runCommand("time set noon");
                world.getServer().runCommand("weather clear");
                world.getServer().runCommand("fill -10 -61 -10 10 -61 20 stone");
                world.getServer().runCommand("tp @a 0.5 -60 0.5 0 0");
                context.getInput().resizeWindow(1280, 720); context.waitTicks(25);
                command(context, "customcosmetics waypoint"); assertManager(context);
                context.runOnClient(client -> ClientUiAccess.setScreen(client, null));
                command(context, "ccos waypoint"); assertManager(context);
                click(context, "New Group", PinkUi.Action.class);
                type(context, "Name", "Waypoint Fixture Group");
                click(context, "Bind to Current", PinkUi.Action.class);
                click(context, "Save", PinkUi.Action.class);
                context.runOnClient(client -> {
                    check(Waypoints.snapshot().groups().size() == 1, "GUI group was not created");
                    check(first().scope().equals(Waypoints.currentScope()), "New group was not scoped to its world");
                });
                click(context, "Add Waypoint", PinkUi.Action.class);
                type(context, "Name", "Current Position");
                click(context, "Use Current Position", PinkUi.Action.class);
                click(context, "Save", PinkUi.Action.class);
                context.runOnClient(client -> {
                    var pos = client.player.blockPosition(); var point = first().points().getFirst();
                    check(point.x() == pos.getX() && point.y() == pos.getY() && point.z() == pos.getZ(), "Current-position fixture differs from player");
                });
                click(context, "Current Position", PinkUi.Action.class);
                type(context, "Name", "Magenta Marker");
                type(context, "X", "0"); type(context, "Y", "-60"); type(context, "Z", "8");
                type(context, "Color", "FF22FF");
                click(context, "Save", PinkUi.Action.class);
                context.runOnClient(client -> {
                    Point point = first().points().getFirst();
                    check(point.name().equals("Magenta Marker") && point.x() == 0 && point.y() == -60 &&
                            point.z() == 8 && point.color() == 0xff22ff, "GUI coordinates / color / name were not saved");
                });
                click(context, "On", PinkUi.Action.class);
                context.runOnClient(client -> check(!first().points().getFirst().enabled(), "Point toggle did not disable"));
                click(context, "Off", PinkUi.Action.class);
                context.runOnClient(client -> check(first().points().getFirst().enabled(), "Point toggle did not re-enable"));
                context.takeScreenshot("waypoint-manager-group-point-color");
                click(context, "Export Group", PinkUi.Action.class);
                context.runOnClient(client -> {
                    Group copy = WaypointCodec.importGroups(minecraftClipboard(client), Waypoints.currentScope()).getFirst();
                    check(copy.points().equals(first().points()) && copy.scope().equals(first().scope()), "GUI share round trip changed data");
                });
                click(context, "Import Clipboard", PinkUi.Action.class);
                context.runOnClient(client -> check(Waypoints.snapshot().groups().size() == 2, "GUI import did not append a group"));
                // The imported copy is identical; remove it before counting render markers.
                context.runOnClient(client -> Waypoints.removeGroup(Waypoints.snapshot().groups().get(1).id()));
                click(context, "Import Skyblocker waypoints.json", PinkUi.Action.class);
                context.runOnClient(client -> check(ClientUiAccess.screen(client) instanceof WaypointImportScreen,
                        "Skyblocker file import screen did not open"));
                click(context, "Preview", PinkUi.Action.class);
                context.waitFor(client -> ClientUiAccess.screen(client) instanceof WaypointImportScreen &&
                        ClientUiAccess.screen(client).children().stream().filter(AbstractWidget.class::isInstance)
                            .map(AbstractWidget.class::cast).anyMatch(widget -> widget.active && widget.getMessage().getString().equals("Import")));
                context.takeScreenshot("skyblocker-file-import-preview");
                click(context, "Import", PinkUi.Action.class);
                context.runOnClient(client -> {
                    var imported = Waypoints.snapshot().groups();
                    check(imported.size() == 3, "Skyblocker file import did not append both groups");
                    check(imported.get(1).scope().equals("hypixel:hub") && imported.get(2).scope().equals("hypixel:mining_3"),
                            "Imported Skyblocker islands were not separated");
                    check(imported.get(1).points().getFirst().name().startsWith("Waypoint ("), "Unnamed Skyblocker point was lost");
                    check(!imported.get(2).points().getFirst().enabled(), "Disabled Skyblocker point became enabled");
                });
                click(context, "Back", PinkUi.Action.class);
                context.runOnClient(client -> {
                    for (var imported : List.copyOf(Waypoints.snapshot().groups()).subList(1, 3)) Waypoints.removeGroup(imported.id());
                });
                command(context, "ccos waypoint");
                context.getInput().resizeWindow(640, 480); context.waitTicks(4);
                assertControlsInside(context); context.takeScreenshot("waypoint-manager-compact");
                click(context, "Magenta Marker", PinkUi.Action.class);
                assertControlsInside(context); context.takeScreenshot("waypoint-editor-compact");
                click(context, "Cancel", PinkUi.Action.class);
                context.getInput().resizeWindow(1280, 720); context.waitTicks(4);
                context.runOnClient(client -> {
                    hideHud(client, true);
                    ClientUiAccess.setScreen(client, null);
                    client.player.setYRot(0); client.player.setXRot(0);
                });
                context.waitTicks(15);
                checkVisible(context, 1);
                int withMarker = magentaPixels(context);
                context.takeScreenshot("waypoint-world-magenta-label-and-diamond");
                check(withMarker > 25, "Actual waypoint marker / label pixels missing: " + withMarker);
                context.runOnClient(client -> Waypoints.replace(new Snapshot(false, Waypoints.snapshot().groups())));
                context.waitTicks(6); checkVisible(context, 0);
                int withoutMarker = magentaPixels(context);
                check(withoutMarker + 20 < withMarker, "Disabled waypoints still render: " + withoutMarker + " / " + withMarker);
                context.runOnClient(client -> Waypoints.replace(new Snapshot(true, Waypoints.snapshot().groups())));
                context.waitTicks(10); checkVisible(context, 1);
                AtomicReference<Group> lastGroup = new AtomicReference<>();
                context.runOnClient(client -> { lastGroup.set(first()); Waypoints.removeGroup(first().id()); });
                context.waitTicks(6); checkVisible(context, 0);
                check(magentaPixels(context) + 20 < withMarker, "Deleting the last group left a stale world marker");
                context.runOnClient(client -> Waypoints.put(lastGroup.get()));
                context.waitTicks(10); checkVisible(context, 1);
                // Keep both distances inside this test client's actual far plane, beyond the marker projection.
                // First prove the far point is visible with no wall, ruling out frustum clipping as a false pass.
                context.runOnClient(client -> {
                    double projectedRange = Math.max(8, Math.min(128, fixtureDepthFar * .5));
                    int target = (int) Math.min(500, fixtureDepthFar * .8);
                    check(target > projectedRange + 8, "Fixture frustum is too short for a far-depth regression");
                    occlusionWallDistance = (projectedRange + target) / 2;
                    Group group = first(); occlusionWall = false;
                    Waypoints.put(new Group(group.id(), group.name(), group.scope(), true, false, false,
                            List.of(new Point("Far Occlusion Fixture", 0, -60, target, 0xff22ff, true))));
                    System.out.println("WAYPOINT_OCCLUSION_FIXTURE far=" + fixtureDepthFar + " target=" + target + " wall=" + occlusionWallDistance);
                });
                context.waitTicks(10);
                int noWall = magentaPixels(context);
                context.takeScreenshot("waypoint-far-real-depth-without-wall");
                check(noWall > 25, "Far marker fixture is clipped even without a wall: " + noWall);
                context.runOnClient(client -> occlusionWall = true);
                context.waitTicks(10);
                int depthTested = magentaPixels(context);
                context.takeScreenshot("waypoint-far-marker-occluded-at-real-depth");
                check(depthTested + 20 < noWall, "Non-through-wall marker was projected in front of its occluder");
                context.runOnClient(client -> {
                    Group group = first();
                    Waypoints.put(new Group(group.id(), group.name(), group.scope(), true, false, true, group.points()));
                });
                context.waitTicks(10);
                int throughWall = magentaPixels(context);
                context.takeScreenshot("waypoint-far-marker-through-wall");
                check(throughWall > 25, "Through-wall marker did not render over the opaque occluder: " + throughWall);
                context.runOnClient(client -> { occlusionWall = false; Waypoints.put(lastGroup.get()); });
                context.waitTicks(10);
                context.runOnClient(client -> {
                    Group group = first();
                    Waypoints.put(new Group(group.id(), group.name(), group.scope() + "-other-world", true, false, true, group.points()));
                });
                context.waitTicks(10); checkVisible(context, 0);
                context.runOnClient(client -> {
                    Group group = first();
                    Waypoints.put(new Group(group.id(), group.name(), Waypoints.currentScope(), true, true, true,
                            List.of(group.points().getFirst(), new Point("Route Next", 5, -60, 8, 0x22ff88, true),
                                    new Point("Route Third", 10, -60, 8, 0x2288ff, true))));
                });
                context.waitTicks(10);
                command(context, "ccos waypoint ordered next");
                assertRoute(context, 1);
                command(context, "customcosmetics waypoint ordered previous");
                assertRoute(context, 0);
                command(context, "ccos waypoint ordered next");
                command(context, "customcosmetics waypoint ordered reset");
                assertRoute(context, 0);
                context.takeScreenshot("waypoint-world-ordered-route");
                context.runOnClient(client -> hideHud(client, false));
                command(context, "ccos waypoint");
                click(context, "2. Route Next", PinkUi.Action.class);
                click(context, "Delete", PinkUi.Action.class);
                click(context, "Confirm Delete", PinkUi.Action.class);
                context.runOnClient(client -> {
                    check(first().points().size() == 2 && first().points().get(1).name().equals("Route Third"), "Point deletion changed wrong route entry");
                    saved.set(first());
                    ClientUiAccess.setScreen(client, null);
                });
                context.waitFor(client -> Waypoints.status().isEmpty());
                Snapshot disk = new WaypointStore(file).load();
                check(disk.equals(Waypoints.snapshot()), "Asynchronous saved waypoint file differs from in-memory snapshot");
            }
            // A second real save has the same dimension but must never inherit the first world's markers.
            try (var world = context.worldBuilder().create()) {
                context.waitTicks(30);
                context.runOnClient(client -> check(!Waypoints.currentScope().equals(saved.get().scope()), "Independent saves share the same waypoint scope"));
                checkVisible(context, 0);
                command(context, "ccos waypoint");
                click(context, "Edit Group", PinkUi.Action.class);
                click(context, "Delete", PinkUi.Action.class);
                click(context, "Confirm Delete", PinkUi.Action.class);
                context.runOnClient(client -> { check(Waypoints.snapshot().groups().isEmpty(), "Group delete failed"); ClientUiAccess.setScreen(client, null); });
                context.waitFor(client -> Waypoints.status().isEmpty());
                check(new WaypointStore(file).load().groups().isEmpty(), "Deleted group remained on disk");
            }
            System.out.println("WAYPOINTS_COMMAND_ALIASES_GUI_EDIT_IMPORT_EXPORT_RENDER_PIXELS_ORDERED_DISABLE_CROSS_WORLD_AND_PERSISTENCE_OK");
        } catch (Exception failure) { throw new AssertionError(failure); }
        finally {
            occlusionWall = false;
            if (clipboardBefore.get() != null)
                context.runOnClient(client -> client.keyboardHandler.setClipboard(clipboardBefore.get()));
        }
    }
    private static void registerOcclusionWall() throws ReflectiveOperationException {
        if (occlusionType == null) {
            // Debug quads are translucent and do NOT write depth. Build a true solid occluder for this fixture.
            var snippetField = RenderPipelines.class.getDeclaredField("DEBUG_FILLED_SNIPPET"); snippetField.setAccessible(true);
            Object snippet = snippetField.get(null);
            Object snippets = java.lang.reflect.Array.newInstance(snippetField.getType(), 1);
            java.lang.reflect.Array.set(snippets, 0, snippet);
            Object base = RenderType.class.getMethod("pipeline").invoke(RenderTypes.debugQuads());
            Object builder = base.getClass().getMethod("builder", snippets.getClass()).invoke(null, snippets);
            builder.getClass().getMethod("withLocation", String.class).invoke(builder, "customcosmetics/test/waypoint_occluder");
            Class<?> color = Class.forName("com.mojang.blaze3d.pipeline.ColorTargetState");
            builder.getClass().getMethod("withColorTargetState", color).invoke(builder, color.getField("DEFAULT").get(null));
            Class<?> depth = Class.forName("com.mojang.blaze3d.pipeline.DepthStencilState");
            builder.getClass().getMethod("withDepthStencilState", depth).invoke(builder, depth.getField("DEFAULT").get(null));
            Object pipeline = builder.getClass().getMethod("build").invoke(builder);
            Object setupBuilder = RenderSetup.class.getMethod("builder", base.getClass()).invoke(null, pipeline);
            RenderSetup setup = (RenderSetup) setupBuilder.getClass().getMethod("createRenderSetup").invoke(setupBuilder);
            var create = RenderType.class.getDeclaredMethod("create", String.class, RenderSetup.class); create.setAccessible(true);
            occlusionType = (RenderType) create.invoke(null, "customcosmetics_waypoint_test_occluder", setup);
        }
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            fixtureDepthFar = context.levelState().cameraRenderState.depthFar;
            if (!occlusionWall) return;
            var camera = context.levelState().cameraRenderState;
            var pose = new PoseStack(); pose.translate(-camera.pos.x, -camera.pos.y, occlusionWallDistance - camera.pos.z);
            context.submitNodeCollector().order(-100).submitCustomGeometry(pose, occlusionType, (matrix, vertices) -> {
                int color = 0xff334455;
                vertices.addVertex(matrix.pose(), -100, -160, 0).setColor(color);
                vertices.addVertex(matrix.pose(), 100, -160, 0).setColor(color);
                vertices.addVertex(matrix.pose(), 100, 40, 0).setColor(color);
                vertices.addVertex(matrix.pose(), -100, 40, 0).setColor(color);
                vertices.addVertex(matrix.pose(), -100, 40, 0).setColor(color);
                vertices.addVertex(matrix.pose(), 100, 40, 0).setColor(color);
                vertices.addVertex(matrix.pose(), 100, -160, 0).setColor(color);
                vertices.addVertex(matrix.pose(), -100, -160, 0).setColor(color);
            });
        });
    }
    private static Group first() { return Waypoints.snapshot().groups().getFirst(); }
    private static String minecraftClipboard(net.minecraft.client.Minecraft client) { return client.keyboardHandler.getClipboard(); }
    private static void hideHud(net.minecraft.client.Minecraft client, boolean hidden) throws ReflectiveOperationException {
        try { client.options.getClass().getField("hideGui").setBoolean(client.options, hidden); }
        catch (NoSuchFieldException newer) {
            Object hud = client.gui.getClass().getField("hud").get(client.gui);
            if ((Boolean) hud.getClass().getMethod("isHidden").invoke(hud) != hidden) hud.getClass().getMethod("toggle").invoke(hud);
        }
    }
    private static void command(ClientGameTestContext context, String command) throws Exception {
        context.runOnClient(client -> {
            var dispatcher = ClientCommands.getActiveDispatcher();
            check(dispatcher != null, "Client command dispatcher missing");
            int result = dispatcher.execute(command, (FabricClientCommandSource) client.getConnection().getSuggestionsProvider());
            check(result == 1, "Command failed: " + command);
        });
        context.waitTicks(3);
    }
    private static void assertManager(ClientGameTestContext context) {
        context.runOnClient(client -> check(ClientUiAccess.screen(client) instanceof WaypointScreen, "Waypoint command did not open manager"));
    }
    private static void checkVisible(ClientGameTestContext context, int expected) throws Exception {
        context.runOnClient(client -> {
            var field = Waypoints.class.getDeclaredField("visibleCount"); field.setAccessible(true);
            check(field.getInt(null) == expected, "Visible waypoint count differs: expected " + expected + ", got " + field.getInt(null));
        });
    }
    private static void assertRoute(ClientGameTestContext context, int expected) throws Exception {
        context.runOnClient(client -> {
            var field = Waypoints.class.getDeclaredField("ROUTES"); field.setAccessible(true);
            var routes = (Map<?, ?>) field.get(null);
            check(((WaypointRoute) routes.get(first().id())).current() == expected, "Ordered command did not change route position");
        });
    }
    private static void assertControlsInside(ClientGameTestContext context) {
        context.runOnClient(client -> {
            var screen = ClientUiAccess.screen(client);
            for (var child : screen.children()) if (child instanceof AbstractWidget widget && widget.visible)
                check(widget.getX() >= 0 && widget.getY() >= 0 && widget.getRight() <= screen.width && widget.getBottom() <= screen.height,
                        "Waypoint control outside compact viewport: " + widget.getMessage().getString());
        });
    }
    private static AbstractWidget widget(net.minecraft.client.Minecraft client, String prefix, Class<?> type) {
        return ClientUiAccess.screen(client).children().stream().filter(type::isInstance).map(AbstractWidget.class::cast)
                .filter(value -> value.visible && value.active && value.getMessage().getString().startsWith(prefix))
                .findFirst().orElseThrow(() -> new AssertionError("Missing waypoint control: " + prefix));
    }
    private static void click(ClientGameTestContext context, String prefix, Class<?> type) {
        var position = new AtomicReference<double[]>();
        context.runOnClient(client -> {
            var widget = widget(client, prefix, type);
            double scale = client.getWindow().getGuiScale();
            position.set(new double[]{(widget.getX() + widget.getWidth() / 2.0) * scale, (widget.getY() + widget.getHeight() / 2.0) * scale});
        });
        context.getInput().setCursorPos(position.get()[0], position.get()[1]);
        context.getInput().pressMouse(ClientInput.MOUSE_LEFT); context.waitTicks(2);
    }
    private static void type(ClientGameTestContext context, String prefix, String text) {
        click(context, prefix, PinkUi.Field.class);
        context.runOnClient(client -> {
            var field = (PinkUi.Field) widget(client, prefix, PinkUi.Field.class);
            check(field.isFocused(), "Waypoint field did not focus: " + prefix); field.setValue("");
        });
        context.getInput().typeChars(text); context.waitTicks(2);
    }
    private static int magentaPixels(ClientGameTestContext context) throws Exception {
        var result = new CompletableFuture<Integer>();
        context.runOnClient(client -> {
            Object target;
            try { target = client.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(client.gameRenderer); }
            catch (NoSuchMethodException old) { target = client.getClass().getMethod("getMainRenderTarget").invoke(client); }
            Screenshot.takeScreenshot((com.mojang.blaze3d.pipeline.RenderTarget) target, image -> {
                try (image) {
                    int count = 0;
                    for (int y = 50; y < image.getHeight() - 80; y++) for (int x = 50; x < image.getWidth() - 50; x++) {
                        int c = image.getPixel(x, y), r = c >>> 16 & 255, g = c >>> 8 & 255, b = c & 255;
                        if (r > 180 && b > 180 && g < 110) count++;
                    }
                    result.complete(count);
                } catch (Throwable failure) { result.completeExceptionally(failure); }
            });
        });
        context.waitFor(client -> result.isDone());
        return result.join();
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
