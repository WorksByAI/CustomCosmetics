// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.*;
import com.customcosmetics.features.pack.HypixelPackDisabler;
import com.customcosmetics.features.pack.LegacyPackItems;
import com.customcosmetics.gui.*;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
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
        var packProbe = Identifier.withDefaultNamespace("pack-priority-probe.txt");
        var dependency = Identifier.fromNamespaceAndPath("hypixel_skyblock", "fallback-probe.txt");
        var localShader = context.computeOnClient(client -> java.util.stream.Stream.of(
                "shaders/core/text.vsh", "shaders/core/rendertype_text.vsh")
            .map(Identifier::withDefaultNamespace).filter(id -> client.getResourceManager().getResource(id).isPresent())
            .findFirst().orElseThrow());
        var extraShader = Identifier.withDefaultNamespace("shaders/include/modify_vanilla_color.glsl");
        byte[] textShader = context.computeOnClient(client -> {
            try (var stream = client.getResourceManager().getResource(localShader).orElseThrow().open()) {
                return stream.readAllBytes();
            }
        });
        var officialPack = testPack(java.util.Map.of(
            "assets/minecraft/" + packProbe.getPath(), new byte[]{2},
            "assets/minecraft/" + localShader.getPath(), textShader,
            "assets/minecraft/" + extraShader.getPath(), new byte[]{' '},
            "assets/hypixel_skyblock/" + dependency.getPath(), new byte[]{1}));
        var otherPack = testPack(java.util.Map.of("assets/minecraft/" + packProbe.getPath(), new byte[]{3}));
        context.runOnClient(client -> {
            ClientUiAccess.setScreen(client, null);
            ConfigManager.hideHypixelSkyBlockPack = false;
            HypixelPackDisabler.remember(official,
                java.net.URI.create("https://assets.hypixel.net/SkyBlock.zip").toURL(), "");
            HypixelPackDisabler.remember(other,
                java.net.URI.create("https://example.org/SkyBlock.zip").toURL(), "");
            var downloads = client.getDownloadedPackSource();
            downloads.allowServerPacks();
            downloads.pushLocalPack(official, officialPack);
            downloads.pushLocalPack(other, otherPack);
        });
        context.waitFor(client -> client.getResourceManager().getResource(dependency).isPresent()
            && client.getResourceManager().getResourceStack(packProbe).size() == 2, 240);
        context.runOnClient(client -> {
            ConfigManager.hideHypixelSkyBlockPack = true;
            HypixelPackDisabler.refresh(client);
            checkDevonianReloadGuard(client);
        });
        context.waitFor(client -> packIsFallback(client, dependency) && LegacyPackItems.ids().contains("MAGMA_URCHIN"), 240);
        context.runOnClient(client -> {
            var resources = client.getResourceManager();
            var selectedPack = resources.getResource(packProbe).orElseThrow().sourcePackId();
            if (!selectedPack.endsWith(other.toString())) throw new AssertionError("Non-Hypixel pack lost priority");
            var shaders = resources.listResources("shaders", id -> true);
            if (shaders.containsKey(extraShader) || shaders.get(localShader).sourcePackId().endsWith(official.toString())) {
                throw new AssertionError("Hypixel text shaders are still selected");
            }
            checkLegacyItems();
            ConfigManager.hypixelPackTextColors = true;
            HypixelPackDisabler.refresh(client);
        });
        context.waitFor(client -> {
            var shaders = client.getResourceManager().listResources("shaders", id -> true);
            return shaders.containsKey(extraShader) && shaders.get(localShader).sourcePackId().endsWith(official.toString());
        }, 240);
        context.runOnClient(client -> {
            ConfigManager.hypixelPackTextColors = false;
            ConfigManager.hideHypixelSkyBlockPack = false;
            HypixelPackDisabler.refresh(client);
        });
        context.waitFor(client -> !packIsFallback(client, dependency)
            && client.getResourceManager().getResource(dependency).isPresent(), 240);
        context.runOnClient(client -> {
            client.getDownloadedPackSource().popPack(official);
            client.getDownloadedPackSource().popPack(other);
            HypixelPackDisabler.disconnect();
        });
        context.waitFor(client -> client.getResourceManager().getResource(dependency).isEmpty(), 240);
        java.nio.file.Files.deleteIfExists(officialPack);
        java.nio.file.Files.deleteIfExists(otherPack);
        System.out.println("SERVER_PACK_FALLBACK_PRIORITY_SHADERS_HEADS_GLINT_AND_DYNAMIC_MODELS_OK");
        String officialFixture = System.getenv("CCOS_TEST_HYPIXEL_PACK");
        if (officialFixture != null) checkCachedHypixelPack(context, Path.of(officialFixture));
    }

    private static boolean packIsFallback(net.minecraft.client.Minecraft client, Identifier probe) {
        var manager = client.getResourceManager();
        return manager.getResource(probe).map(resource -> manager.listPacks().findFirst()
            .map(pack -> pack.packId().equals(resource.sourcePackId())).orElse(false)).orElse(false);
    }

    private static ItemStack skyBlockItem(String id, String path, java.util.function.Consumer<CompoundTag> extra) {
        var stack = new ItemStack(Items.PAPER);
        var tag = new CompoundTag();
        tag.putString("id", id);
        extra.accept(tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        stack.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath("hypixel_skyblock", path));
        return stack;
    }

    private static void checkLegacyItems() {
        var magma = skyBlockItem("MAGMA_URCHIN", "item/power_stones/magma_urchin", tag -> {});
        var original = magma.get(DataComponents.ITEM_MODEL);
        var head = Identifier.withDefaultNamespace("player_head");
        if (!head.equals(LegacyPackItems.model(magma, original)) || LegacyPackItems.headProfile(magma, null) == null) {
            throw new AssertionError("Magma Urchin did not restore its legacy head and texture");
        }
        ConfigManager.hypixelPackWhitelist = java.util.Set.of("MAGMA_URCHIN");
        if (!original.equals(LegacyPackItems.model(magma, original))) throw new AssertionError("Whitelist did not preserve official model");
        ConfigManager.hypixelPackReplacements = java.util.Map.of("MAGMA_URCHIN", "minecraft:diamond");
        if (!Identifier.withDefaultNamespace("diamond").equals(LegacyPackItems.model(magma, original))) {
            throw new AssertionError("Replacement did not override whitelist");
        }
        ConfigManager.hypixelPackGlints = java.util.Map.of("MAGMA_URCHIN", true);
        if (!magma.hasFoil() || magma.has(DataComponents.ENCHANTMENT_GLINT_OVERRIDE)) throw new AssertionError("Glint on changed item data or failed");
        magma.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        ConfigManager.hypixelPackGlints = java.util.Map.of("MAGMA_URCHIN", false);
        if (magma.hasFoil() || !Boolean.TRUE.equals(magma.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) {
            throw new AssertionError("Glint off changed item data or failed");
        }
        ConfigManager.hypixelPackWhitelist = java.util.Set.of();
        ConfigManager.hypixelPackReplacements = java.util.Map.of();
        ConfigManager.hypixelPackGlints = java.util.Map.of();
        assertLegacyModel(skyBlockItem("CARNIVAL_SHOVEL", "item/carnival_shovel_diamond", tag -> {}), "diamond_shovel");
        assertLegacyModel(skyBlockItem("FIREDUST_DAGGER", "item/firedust_dagger", tag -> tag.putInt("td_attune_mode", 1)), "golden_sword");
        assertLegacyModel(skyBlockItem("FUNGI_CUTTER", "item/fungi_cutter", tag -> tag.putString("fungi_cutter_mode", "RED")), "red_mushroom");
        var katana = skyBlockItem("VOIDEDGE_KATANA", "item/voidedge_katana", tag -> {});
        var cooldowns = net.minecraft.client.Minecraft.getInstance().player.getCooldowns();
        cooldowns.addCooldown(katana, 5);
        assertLegacyModel(katana, "golden_sword");
        assertLegacyModel(skyBlockItem("OTHER", "item/quiver_arrow", tag -> tag.putBoolean("quiver_arrow", true)), "arrow");
        assertLegacyModel(skyBlockItem("UNKNOWN_NEW_ID", "item/new", tag -> {}), "hypixel_skyblock:item/new");
        if (HypixelPackDisabler.tooltipStyle(Identifier.fromNamespaceAndPath("hypixel_skyblock", "test")) != null
            || HypixelPackDisabler.tooltipStyle(Identifier.withDefaultNamespace("test")) == null) {
            throw new AssertionError("Tooltip styles were not filtered by namespace");
        }
    }

    private static void assertLegacyModel(ItemStack stack, String expected) {
        var result = LegacyPackItems.model(stack, stack.get(DataComponents.ITEM_MODEL));
        if (!Identifier.parse(expected).equals(result)) throw new AssertionError("Wrong legacy model for " + LegacyPackItems.id(stack) + ": " + result);
    }

    private static void checkCachedHypixelPack(ClientGameTestContext context, Path path) throws Exception {
        var official = java.util.UUID.randomUUID();
        var texture = Identifier.fromNamespaceAndPath("hypixel_skyblock", "textures/item/power_stones/magma_urchin.png");
        var hash = java.security.MessageDigest.getInstance("SHA-1");
        try (var input = new java.security.DigestInputStream(java.nio.file.Files.newInputStream(path), hash)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        String digest = java.util.HexFormat.of().formatHex(hash.digest());
        var cache = context.computeOnClient(client -> client.gameDirectory.toPath().resolve("downloads").resolve(official.toString()).resolve(digest));
        java.nio.file.Files.createDirectories(cache.getParent());
        java.nio.file.Files.copy(path, cache);
        context.runOnClient(client -> {
            ConfigManager.hideHypixelSkyBlockPack = true;
            HypixelPackDisabler.remember(official, java.net.URI.create("https://resourcepacks.hypixel.net/SkyBlock/test.zip").toURL(), digest);
            client.getDownloadedPackSource().pushLocalPack(official, cache);
        });
        var model = Identifier.fromNamespaceAndPath("hypixel_skyblock", "item/power_stones/magma_urchin");
        context.waitFor(client -> packIsFallback(client, texture)
            && com.customcosmetics.features.items.ItemCosmetics.hasModel(model), 240);
        context.runOnClient(client -> {
            var magma = skyBlockItem("MAGMA_URCHIN", "item/power_stones/magma_urchin", tag -> {});
            var state = new net.minecraft.client.renderer.item.ItemStackRenderState();
            client.getItemModelResolver().updateForTopItem(state, magma, ItemDisplayContext.GUI, client.level, client.player, 0);
            if (state.isEmpty()) throw new AssertionError("Magma Urchin did not submit a model with the real pack");
            ConfigManager.hypixelPackWhitelist = java.util.Set.of("MAGMA_URCHIN");
            client.getItemModelResolver().updateForTopItem(state, magma, ItemDisplayContext.GUI, client.level, client.player, 0);
            var sprite = state.pickParticleMaterial(net.minecraft.util.RandomSource.create(0)).sprite();
            if (!sprite.contents().name().getPath().endsWith("/magma_urchin")) {
                throw new AssertionError("Official Magma Urchin texture was not available to the whitelist: " + sprite.contents().name());
            }
            ConfigManager.hypixelPackWhitelist = java.util.Set.of();
        });
        var bakedModels = context.computeOnClient(client -> com.customcosmetics.features.items.ItemCosmetics.catalogIdentity());
        context.runOnClient(HypixelPackDisabler::checkDownloadAndReload);
        context.waitFor(client -> bakedModels != com.customcosmetics.features.items.ItemCosmetics.catalogIdentity()
            && packIsFallback(client, texture), 240);
        checkHypixelTextAndTooltip(context);
        context.runOnClient(client -> {
            ConfigManager.hideHypixelSkyBlockPack = false;
            client.getDownloadedPackSource().popPack(official);
            HypixelPackDisabler.disconnect();
        });
        context.waitFor(client -> client.getResourceManager().getResource(texture).isEmpty(), 240);
        System.out.println("CACHED_HYPIXEL_PACK_MAGMA_URCHIN_LEGACY_AND_OFFICIAL_MODELS_OK");
    }

    private static void checkHypixelTextAndTooltip(ClientGameTestContext context) throws Exception {
        var colors = new int[]{0xFFAA00, 0x5555FF, 0x55FF55};
        var tooltipStyle = Identifier.fromNamespaceAndPath("hypixel_skyblock", "epic");
        int tooltipX = 80, tooltipY = 260, tooltipWidth = 160, tooltipHeight = 40;
        context.runOnClient(client -> ClientUiAccess.setScreen(client, new net.minecraft.client.gui.screens.Screen(
            net.minecraft.network.chat.Component.literal("Hypixel text colors")) {
            @Override public boolean isPauseScreen() { return false; }
            @Override public void extractRenderState(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int x, int y, float delta) {
                graphics.fill(0, 0, width, height, 0xFF101010);
                graphics.pose().pushMatrix();
                graphics.pose().scale(4, 4);
                for (int i = 0; i < colors.length; i++) {
                    graphics.text(font, "MMMMMMMM", 10, 12 + i * 15, 0xFF000000 | colors[i], false);
                }
                graphics.pose().popMatrix();
                net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil.extractTooltipBackground(
                    graphics, tooltipX, tooltipY, tooltipWidth, tooltipHeight, tooltipStyle);
                graphics.text(font, "Magma Urchin", tooltipX + 4, tooltipY + 4, 0xFFAA00AA, false);
            }
        }));
        int[][] actual = new int[3][];
        int[][] tooltipPixels = new int[3][];
        for (int step = 0; step < actual.length; step++) {
            boolean enabled = step == 1;
            if (step != 0) {
                var models = context.computeOnClient(client -> com.customcosmetics.features.items.ItemCosmetics.catalogIdentity());
                context.runOnClient(client -> {
                    ConfigManager.hypixelPackTextColors = enabled;
                    ConfigManager.hypixelPackTooltipBackground = enabled;
                    HypixelPackDisabler.refresh(client);
                });
                context.waitFor(client -> models != com.customcosmetics.features.items.ItemCosmetics.catalogIdentity(), 240);
            }
            context.waitFor(Release201GameTest::overlayClosed, 240);
            context.waitTicks(2);
            var screenshot = context.takeScreenshot("hypixel-pack-visuals-" + step);
            var image = javax.imageio.ImageIO.read(screenshot.toFile());
            int guiScale = context.computeOnClient(client -> (int) client.getWindow().getGuiScale());
            int scale = guiScale * 4;
            int regionWidth = tooltipWidth * guiScale;
            tooltipPixels[step] = image.getRGB(tooltipX * guiScale, (tooltipY + 15) * guiScale, regionWidth,
                (tooltipHeight - 15) * guiScale, null, 0, regionWidth);
            actual[step] = new int[colors.length];
            for (int row = 0; row < colors.length; row++) {
                var counts = new java.util.HashMap<Integer, Integer>();
                for (int y = (12 + row * 15) * scale; y < (22 + row * 15) * scale; y++) {
                    for (int x = 10 * scale; x < 60 * scale; x++) {
                        int rgb = image.getRGB(x, y) & 0xFFFFFF;
                        if (rgb != 0x101010) counts.merge(rgb, 1, Integer::sum);
                    }
                }
                actual[step][row] = counts.entrySet().stream().max(java.util.Map.Entry.comparingByValue())
                    .orElseThrow(() -> new AssertionError("The text sample did not render")).getKey();
            }
        }
        context.runOnClient(client -> ClientUiAccess.setScreen(client, null));
        System.out.println("HYPIXEL_TEXT_COLORS_PIXELS " + java.util.Arrays.deepToString(actual));
        if (!java.util.Arrays.equals(actual[0], colors)
            || !java.util.Arrays.equals(actual[1], new int[]{0xFF9000, 0x459BFF, 0x55FF55})
            || !java.util.Arrays.equals(actual[2], colors)) {
            throw new AssertionError("The Hypixel text color toggle did not change the rendered palette");
        }
        if (java.util.Arrays.equals(tooltipPixels[0], tooltipPixels[1])
            || !java.util.Arrays.equals(tooltipPixels[0], tooltipPixels[2])
            || java.util.Arrays.stream(tooltipPixels[1]).anyMatch(rgb -> (rgb & 0xFFFFFF) == 0xF800F8)) {
            throw new AssertionError("The Hypixel tooltip background did not switch cleanly");
        }
        System.out.println("HYPIXEL_TOOLTIP_BACKGROUND_PIXELS_OK");
    }

    private static boolean overlayClosed(net.minecraft.client.Minecraft client) {
        try {
            try { return client.gui.getClass().getMethod("overlay").invoke(client.gui) == null; }
            catch (NoSuchMethodException legacy) { return client.getClass().getMethod("getOverlay").invoke(client) == null; }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
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

    private static Path testPack(java.util.Map<String, byte[]> resources) throws java.io.IOException {
        var format = net.minecraft.SharedConstants.getCurrentVersion().packVersion(net.minecraft.server.packs.PackType.CLIENT_RESOURCES);
        var file = java.nio.file.Files.createTempFile("ccos-pack-probe", ".zip");
        try (var zip = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(file))) {
            zip.putNextEntry(new java.util.zip.ZipEntry("pack.mcmeta"));
            var range = "[" + format.major() + "," + format.minor() + "]";
            zip.write(("{\"pack\":{\"description\":\"Test pack\",\"min_format\":" + range + ",\"max_format\":" + range + "}}")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            for (var resource : resources.entrySet()) {
                zip.putNextEntry(new java.util.zip.ZipEntry(resource.getKey()));
                zip.write(resource.getValue());
                zip.closeEntry();
            }
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
