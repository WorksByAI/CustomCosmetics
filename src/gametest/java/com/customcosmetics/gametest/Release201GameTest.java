// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.*;
import com.customcosmetics.gui.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

/** Exercise real rendered depth changes, native drop submission and inventory blur. */
public final class Release201GameTest implements FabricClientGameTest {
    public static volatile boolean trackDrops;
    public static volatile float dropRatio;
    public static volatile int dropDraws;
    public static volatile boolean trackInventoryBlur;
    public static volatile int inventoryBlurPasses;
    public void runTest(ClientGameTestContext context){
        context.runOnClient(c->{ConfigManager.syncEnabled=false;ConfigManager.offlineMode=true;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.uiAnimationsEnabled=false;ConfigManager.uiThemeMode="dark";ConfigManager.smolpeopleMode=false;ConfigManager.playerSizeEnabled=true;ConfigManager.playerSizeX=ConfigManager.playerSizeY=ConfigManager.playerSizeZ=1;ConfigManager.creatureModel="player";FeatureModules.all(true);FeatureModules.set(FeatureModules.Feature.ITEMS,false);});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("gamemode survival @a");world.getServer().runCommand("time set noon");world.getServer().runCommand("weather clear");
            for(String piece:new String[]{"chest.chestplate","legs.leggings","feet.boots"}){var p=piece.split("\\.");world.getServer().runCommand("item replace entity @a armor."+p[0]+" with minecraft:leather_"+p[1]+"[minecraft:dyed_color=16711680]");}
            context.waitTicks(8);context.getInput().resizeWindow(1600,1000);
            var panel=new AtomicReference<PreviewLayout.PlayerPanel>();var physical=new AtomicReference<Float>();
            context.runOnClient(c->{c.options.guiScale().set(2);physical.set(c.player.getBbWidth());ClientUiAccess.setScreen(c,new CosmeticsScreen(null,CosmeticsScreen.Page.SIZE));});context.waitTicks(3);context.takeScreenshot("201-portrait-before-drag");
            context.runOnClient(c->{var screen=(CosmeticsScreen)ClientUiAccess.screen(c);panel.set((PreviewLayout.PlayerPanel)read(screen,"playerPanel"));var p=panel.get();var event=mouse(p.previewX()+p.previewWidth()/2d,p.previewY()+p.previewHeight()/2d);if(!screen.mouseClicked(event,false)||!screen.mouseDragged(event,(90-WardrobeCamera.DEFAULT.yaw())/.7,-WardrobeCamera.DEFAULT.pitch()/.4))throw new AssertionError("Portrait does not accept drag: panel="+p+" top="+read(screen,"viewTop")+" bottom="+read(screen,"viewBottom")+" scroll="+read(screen,"scroll")+" entrance="+read(screen,"entranceScale")+" event="+event);screen.mouseReleased(event);});
            setDepth(context,.35);context.waitTicks(3);Path thin=context.takeScreenshot("201-player-depth-thin");
            setDepth(context,2.5);context.waitTicks(3);Path deep=context.takeScreenshot("201-player-depth-deep");
            int thinWidth=redWidth(thin,panel.get(),2),deepWidth=redWidth(deep,panel.get(),2);
            if(thinWidth<5||deepWidth<thinWidth*1.5)throw new AssertionError("Depth change is not visible: thin="+thinWidth+" deep="+deepWidth);
            context.runOnClient(c->{if(c.player.getBbWidth()!=physical.get())throw new AssertionError("Preview altered collision bounds");if(UiFonts.get(c.font)!=c.font)throw new AssertionError("Font flavor did not fall back correctly");ClientUiAccess.setScreen(c,null);ConfigManager.playerSizeEnabled=false;c.player.setYRot(0);c.player.setXRot(0);var item=new ItemEntity(c.level,c.player.getX(),c.player.getY()+1,c.player.getZ()+3,new ItemStack(Items.DIAMOND,32));item.setId(-20101);item.setNoGravity(true);c.level.addEntity(item);trackDrops=true;ConfigManager.droppedItems2dEnabled=false;dropDraws=0;});
            context.waitTicks(5);assertDrop(false);
            context.runOnClient(c->{ConfigManager.droppedItems2dEnabled=true;dropDraws=0;});context.waitTicks(5);assertDrop(true);context.takeScreenshot("201-flat-dropped-items");
            context.runOnClient(c->{FeatureModules.set(FeatureModules.Feature.DROP_2D,false);dropDraws=0;});context.waitTicks(4);assertDrop(false);
            context.runOnClient(c->{trackDrops=false;ClientUiAccess.setScreen(c,new CosmeticsScreen(null,CosmeticsScreen.Page.DROP_2D));});context.waitTicks(2);context.takeScreenshot("201-2d-drop-settings");
            System.out.println("RELEASE_201_NATIVE_DEPTH_AND_DROP_OK thin="+thinWidth+" deep="+deepWidth);
            context.runOnClient(c->{ClientUiAccess.setScreen(c,null);c.level.removeEntity(-20101,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);});
            checkInventoryBlur(context, world.getServer()::runCommand);
        }catch(Exception error){throw new AssertionError(error);}finally{trackDrops=false;}
    }
    private static void checkInventoryBlur(ClientGameTestContext context, java.util.function.Consumer<String> command) throws Exception {
        var access = com.customcosmetics.sync.ExperimentalAccess.class.getDeclaredField("status");
        access.setAccessible(true);
        var previous = access.get(null);
        try {
            context.runOnClient(client -> {
                ConfigManager.checkExperimentalAccess = false;
                client.options.menuBackgroundBlurriness().set(5);
                ClientUiAccess.setScreen(client, new net.minecraft.client.gui.screens.inventory.InventoryScreen(client.player));
                trackInventoryBlur = true;
                inventoryBlurPasses = 0;
                ConfigManager.inventoryBlurEnabled = true;
            });
            context.waitTicks(3);
            if (inventoryBlurPasses != 0) throw new AssertionError("Unauthorized inventory blur rendered");
            context.runOnClient(client -> {
                ConfigManager.offlineMode = false;
                try { access.set(null, com.customcosmetics.sync.ExperimentalAccess.Status.GRANTED); }
                catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            context.waitTicks(3);
            context.takeScreenshot("experimental-inventory-blur");
            if (inventoryBlurPasses == 0) context.runOnClient(client -> {
                var screen = ClientUiAccess.screen(client);
                throw new AssertionError("Inventory did not request native blur: screen=" + screen + ", policy="
                    + UiBlurPolicy.inventory(screen) + ", strength=" + client.options.getMenuBackgroundBlurriness());
            });
            context.runOnClient(client -> {
                ConfigManager.inventoryBlurEnabled = false;
                inventoryBlurPasses = 0;
            });
            context.waitTicks(3);
            if (inventoryBlurPasses != 0) throw new AssertionError("Disabled inventory blur rendered");
            System.out.println("EXPERIMENTAL_INVENTORY_BLUR_AUTHORIZED_ONLY_OK");
            context.runOnClient(client -> ConfigManager.inventoryDimPercent = 0);
            context.waitTicks(3);
            int undimmed = backgroundLight(context.takeScreenshot("inventory-dim-zero"));
            context.runOnClient(client -> ConfigManager.inventoryDimPercent = 50);
            context.waitTicks(3);
            int medium = backgroundLight(context.takeScreenshot("inventory-dim-half"));
            context.runOnClient(client -> ConfigManager.inventoryDimPercent = 100);
            context.waitTicks(3);
            int dark = backgroundLight(context.takeScreenshot("inventory-dim-full"));
            if (!(undimmed > medium && medium > dark && dark < 3))
                throw new AssertionError("Inventory overlay did not render the requested darkness: " + undimmed + "/" + medium + "/" + dark);
            System.out.println("INVENTORY_DIM_PIXEL_LEVELS_OK " + undimmed + "/" + medium + "/" + dark);
            checkPackToggle(context);
            checkGlobalFont(context);
            context.runOnClient(client -> {
                ClientUiAccess.setScreen(client, null);
                ConfigManager.inventoryDimPercent = 80;
                var layer = com.customcosmetics.features.sky.skybox.impl.SkyLayer.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,
                    com.google.gson.JsonParser.parseString("""
                    {"properties":"customcosmetics:test_sky","texture":"minecraft:textures/block/magenta_wool.png","rotate":false,"blend":"replace"}
                    """)).getOrThrow();
                com.customcosmetics.features.sky.Skyboxify.replace(java.util.List.of(
                    new com.customcosmetics.features.sky.skybox.impl.Skybox(client.level.dimension(), java.util.List.of(layer))));
                ConfigManager.customSkyEnabled = true;
                client.player.setXRot(-45);
            });
            context.waitTicks(6);
            context.takeScreenshot("experimental-custom-sky");
            if (!com.customcosmetics.features.sky.Skyboxify.enabled()) throw new AssertionError("Custom sky renderer fell back to vanilla");
            System.out.println("EXPERIMENTAL_CUSTOM_SKY_RENDERER_OK");
            command.accept("gamemode creative @a");
            long start = System.nanoTime();
            command.accept("execute as @a at @s run fill ~-2 ~ ~-2 ~2 ~3 ~2 minecraft:water");
            context.waitFor(client -> com.customcosmetics.features.sky.Skyboxify.camera().getFluidInCamera()
                == net.minecraft.world.level.material.FogType.WATER, 80);
            long submerged = System.nanoTime();
            command.accept("execute as @a at @s run fill ~-2 ~-1 ~-2 ~2 ~3 ~2 minecraft:air replace minecraft:water");
            context.waitFor(client -> com.customcosmetics.features.sky.Skyboxify.camera().getFluidInCamera()
                == net.minecraft.world.level.material.FogType.NONE, 80);
            if (!com.customcosmetics.features.sky.Skyboxify.enabled()) throw new AssertionError("Custom sky fell back after crossing the water surface");
            System.out.printf("EXPERIMENTAL_SKY_WATER_TRANSITIONS_OK enter=%dms exit=%dms%n",
                (submerged - start) / 1_000_000, (System.nanoTime() - submerged) / 1_000_000);
        } finally {
            context.runOnClient(client -> {
                trackInventoryBlur = false;
                ConfigManager.inventoryBlurEnabled = false;
                ConfigManager.inventoryDimPercent = 80;
                ConfigManager.offlineMode = true;
                ConfigManager.customSkyEnabled = false;
                com.customcosmetics.features.sky.Skyboxify.replace(java.util.List.of());
                try { access.set(null, previous); }
                catch (ReflectiveOperationException error) { throw new AssertionError(error); }
                ClientUiAccess.setScreen(client, null);
            });
        }
    }
    private static void checkPackToggle(ClientGameTestContext context) throws Exception {
        var official = java.util.UUID.randomUUID();
        var other = java.util.UUID.randomUUID();
        var officialResource = net.minecraft.resources.Identifier.fromNamespaceAndPath("customcosmetics", "official-probe.txt");
        var otherResource = net.minecraft.resources.Identifier.fromNamespaceAndPath("customcosmetics", "other-probe.txt");
        var officialPack = testPack(officialResource.getPath());
        var otherPack = testPack(otherResource.getPath());
        context.runOnClient(client -> {
            ClientUiAccess.setScreen(client, null);
            ConfigManager.hideHypixelSkyBlockPack = false;
            com.customcosmetics.features.pack.HypixelPackDisabler.remember(official,
                java.net.URI.create("https://assets.hypixel.net/SkyBlock.zip").toURL());
            com.customcosmetics.features.pack.HypixelPackDisabler.remember(other,
                java.net.URI.create("https://example.org/SkyBlock.zip").toURL());
            var downloads = client.getDownloadedPackSource();
            downloads.allowServerPacks();
            downloads.pushLocalPack(official, officialPack);
            downloads.pushLocalPack(other, otherPack);
        });
        context.waitFor(client -> client.getResourceManager().getResource(officialResource).isPresent()
            && client.getResourceManager().getResource(otherResource).isPresent(), 240);
        context.runOnClient(client -> {
            ConfigManager.hideHypixelSkyBlockPack = true;
            com.customcosmetics.features.pack.HypixelPackDisabler.refresh(client);
            checkDevonianReloadGuard(client);
        });
        context.waitFor(client -> client.getResourceManager().getResource(officialResource).isEmpty()
            && client.getResourceManager().getResource(otherResource).isPresent(), 240);
        context.runOnClient(client -> {
            var stack = new ItemStack(Items.DIAMOND_SWORD);
            var model = net.minecraft.resources.Identifier.fromNamespaceAndPath("hypixel_skyblock", "fixture");
            var fallback = com.customcosmetics.features.pack.HypixelPackDisabler.fallbackModel(stack, model);
            if (!net.minecraft.resources.Identifier.withDefaultNamespace("diamond_sword").equals(fallback))
                throw new AssertionError("Official item model did not fall back to the underlying item");
            ConfigManager.hideHypixelSkyBlockPack = false;
            com.customcosmetics.features.pack.HypixelPackDisabler.refresh(client);
        });
        context.waitFor(client -> client.getResourceManager().getResource(officialResource).isPresent(), 240);
        context.runOnClient(client -> {
            client.getDownloadedPackSource().popPack(official);
            client.getDownloadedPackSource().popPack(other);
            com.customcosmetics.features.pack.HypixelPackDisabler.disconnect();
        });
        context.waitFor(client -> client.getResourceManager().getResource(officialResource).isEmpty(), 240);
        java.nio.file.Files.deleteIfExists(officialPack);
        java.nio.file.Files.deleteIfExists(otherPack);
        System.out.println("SERVER_PACK_TOGGLE_HIDE_RESTORE_AND_PRESERVE_OTHER_PACK_OK");
    }

    private static void checkDevonianReloadGuard(net.minecraft.client.Minecraft client) throws ReflectiveOperationException {
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("devonian")) return;
        var type = Class.forName("com.github.synnerz.devonian.features.misc.hiders.DisableWorldLoadingScreen");
        var tracker = type.getDeclaredField("levelLoadingScreen");
        tracker.setAccessible(true);
        var gui = client.gui;
        var overlay = gui.getClass().getMethod("overlay");
        Object before = overlay.invoke(gui);
        if (!(before instanceof net.minecraft.client.gui.screens.LoadingOverlay))
            throw new AssertionError("The fixture needs a running resource reload");
        Object saved = tracker.get(null);
        try {
            Class<?> accessor = tracker.getType();
            tracker.set(null, java.lang.reflect.Proxy.newProxyInstance(accessor.getClassLoader(),
                new Class<?>[]{accessor}, (proxy, method, arguments) -> null));
            type.getMethod("onPlayerLoaded").invoke(type.getField("INSTANCE").get(null));
            if (overlay.invoke(gui) != before) throw new AssertionError("Devonian interrupted the resource reload");
        } finally {
            tracker.set(null, saved);
        }
        System.out.println("DEVONIAN_RELOAD_OVERLAY_PRESERVED_OK");
    }

    private static void checkGlobalFont(ClientGameTestContext context) throws Exception {
        var fonts = UiFonts.systemFonts();
        if (fonts.isEmpty()) throw new AssertionError("Install a local TrueType font for the game font fixture");
        Path path = fonts.stream().filter(font -> font.getFileName().toString().equalsIgnoreCase("arial.ttf"))
            .findFirst().orElse(fonts.getFirst());
        var loaded = new java.util.concurrent.CompletableFuture<Boolean>();
        String sample = "CustomCosmetics WWW 0123456789";
        int originalWidth = context.computeOnClient(client -> client.font.width(sample));
        context.runOnClient(client -> {
            ConfigManager.useFontInGame = true;
            UiFonts.load(client, path, new UiFonts.Style(12, 2, 3, .5f, .5f), loaded::complete);
        });
        context.waitFor(client -> loaded.isDone(), 100);
        context.runOnClient(client -> {
            if (!loaded.join() || client.font.width(sample) == originalWidth)
                throw new AssertionError("Local font did not change vanilla font measurements");
            UiFonts.clear();
            if (client.font.width(sample) != originalWidth)
                throw new AssertionError("Vanilla font measurements were not restored");
        });
        System.out.println("NATIVE_GAME_FONT_LOAD_AND_RESTORE_OK");
    }

    private static Path testPack(String resource) throws java.io.IOException {
        var format = net.minecraft.SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
        var file = java.nio.file.Files.createTempFile("ccos-pack-probe", ".zip");
        try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(file))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("pack.mcmeta"));
            var range = "[" + format.major() + "," + format.minor() + "]";
            zip.write(("{\"pack\":{\"description\":\"Test pack\",\"min_format\":" + range + ",\"max_format\":" + range + "}}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new java.util.zip.ZipEntry("assets/customcosmetics/" + resource));
            zip.write(1);
            zip.closeEntry();
        }
        return file;
    }
    private static int backgroundLight(Path path) throws java.io.IOException {
        var image = javax.imageio.ImageIO.read(path.toFile());
        int pixel = image.getRGB(image.getWidth() / 12, image.getHeight() / 8);
        return ((pixel >> 16 & 255) + (pixel >> 8 & 255) + (pixel & 255)) / 3;
    }
    private static void assertDrop(boolean flat){if(dropDraws==0||!Float.isFinite(dropRatio)||(flat?dropRatio>.011f:dropRatio<.9f))throw new AssertionError("Drop submission differs, flat="+flat+" ratio="+dropRatio+" draws="+dropDraws);}
    private static void setDepth(ClientGameTestContext context,double value){context.runOnClient(c->{var screen=(CosmeticsScreen)ClientUiAccess.screen(c);var slider=screen.children().stream().filter(PinkUi.Slider.class::isInstance).map(PinkUi.Slider.class::cast).filter(s->s.getMessage().getString().startsWith("Z")).findFirst().orElseThrow();slider.setActual(value);if(Math.abs(ConfigManager.playerSizeZ-value)>.001)throw new AssertionError("Depth slider failed");});}
    private static int redWidth(Path file,PreviewLayout.PlayerPanel p,int scale)throws Exception{
        var image=javax.imageio.ImageIO.read(file.toFile());int min=image.getWidth(),max=-1;
        for(int y=(p.previewY()+55)*scale;y<(p.previewY()+p.previewHeight()-5)*scale;y++)for(int x=(p.previewX()+5)*scale;x<(p.previewX()+p.previewWidth()-5)*scale;x++){
            int rgb=image.getRGB(x,y),r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;if(r>65&&g<r*.5&&b<r*.5){min=Math.min(min,x);max=Math.max(max,x);}
        }return max<min?0:max-min+1;
    }
    private static Object read(Object object,String name){try{var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(Exception e){throw new AssertionError(e);}}
    private static MouseButtonEvent mouse(double x,double y){try{var button=Class.forName("net.minecraft.client.input.MouseButtonInfo");return(MouseButtonEvent)MouseButtonEvent.class.getConstructor(double.class,double.class,button).newInstance(x,y,button.getConstructor(int.class,int.class).newInstance(com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT,0));}catch(Exception e){throw new AssertionError(e);}}
}
