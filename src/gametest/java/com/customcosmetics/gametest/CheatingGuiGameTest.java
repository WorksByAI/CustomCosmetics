package com.customcosmetics.gametest;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.features.gameplay.CheatingConfigs;
import com.customcosmetics.features.gameplay.EnabledModulesHud;
import com.customcosmetics.features.gameplay.GameplayModule;
import com.customcosmetics.features.gameplay.GameplayFeatures;
import com.customcosmetics.features.nickname.SkyBlockLevel;
import com.customcosmetics.gui.PinkUi;
import com.customcosmetics.gui.UiFonts;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import com.customcosmetics.features.gameplay.QuoiDungeon;
import com.customcosmetics.features.gameplay.dungeonapi.DungeonMenu;
import com.customcosmetics.features.gameplay.dungeonapi.DeviceConfig;
import com.mojang.blaze3d.platform.InputConstants;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.customcosmetics.gui.CheatingConfigScreen;
import com.customcosmetics.gui.CheatingScreen;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.compatibility.ClientUiAccess;
import net.minecraft.client.gui.components.AbstractWidget;
public final class CheatingGuiGameTest implements FabricClientGameTest {
 public void runTest(ClientGameTestContext context) {
  context.runOnClient(client -> {
   ConfigManager.offlineMode = true;
   ConfigManager.syncEnabled = false;
  });
  try(var world=context.worldBuilder().create()) {
   context.getInput().resizeWindow(640, 480);
   context.waitTicks(2);
   context.runOnClient(client->{
    ClientUiAccess.setScreen(client,new CheatingConfigScreen(null));
    long buttons=ClientUiAccess.screen(client).children().stream().filter(x->x instanceof AbstractWidget w && java.util.Set.of("Save","Load","Remove").contains(w.getMessage().getString())).count();
    if(buttons!=3) throw new AssertionError("Missing config controls");
    ClientUiAccess.setScreen(client,new CheatingConfigScreen(null,true));
    for(var child:ClientUiAccess.screen(client).children()) if(child instanceof AbstractWidget w && (w.getY()<0 || w.getBottom()>client.getWindow().getGuiScaledHeight())) throw new AssertionError("Clipped HUD style control: "+w.getMessage());
    var ctor=Class.forName("com.customcosmetics.gui.GameplayHudEditor").getDeclaredConstructor(net.minecraft.client.gui.screens.Screen.class);ctor.setAccessible(true);
    ClientUiAccess.setScreen(client,(net.minecraft.client.gui.screens.Screen)ctor.newInstance((Object)null));
    var editor = ClientUiAccess.screen(client);
    var window = client.getWindow();
    int x = GameplayFeatures.hudX(1, window.getGuiScaledWidth()) + 3;
    int y = GameplayFeatures.hudY(1, window.getGuiScaledHeight()) + 3;
    ConfigManager.gameplayPanelConfirmed = true;
    ConfigManager.enabledModulesHud = false;
    ConfigManager.gameplayModules = java.util.Set.of();
    ConfigManager.gameplayHudScales = new float[0];
    if (editor.mouseScrolled(x, y, 0, 1)) throw new AssertionError("Disabled HUD accepted scroll");
    ConfigManager.gameplayModules = java.util.Set.of(GameplayModule.TREVOR_HELPER.name());
    if (!editor.mouseScrolled(x, y, 0, 1) || GameplayFeatures.hudScale(1) != 1.1f) {
     throw new AssertionError("Enabled HUD was not resized");
    }
   });
   context.waitTicks(3);
   var mouse = new java.util.concurrent.atomic.AtomicReference<int[]>();
   context.runOnClient(client -> {
    var window = client.getWindow();
    int x = com.customcosmetics.features.gameplay.GameplayFeatures.hudX(1, window.getGuiScaledWidth()) + 3;
    int y = com.customcosmetics.features.gameplay.GameplayFeatures.hudY(1, window.getGuiScaledHeight()) + 3;
    mouse.set(new int[] { x * window.getScreenWidth() / window.getGuiScaledWidth(),
        y * window.getScreenHeight() / window.getGuiScaledHeight() });
   });
   context.getInput().setCursorPos(mouse.get()[0], mouse.get()[1]);
   context.waitTicks(3);
   context.takeScreenshot("hud-editor-hover-name");
   context.getInput().resizeWindow(1440, 900);
   context.waitTicks(2);
   checkConfigConfirmation(context);
   context.runOnClient(client -> ClientUiAccess.setScreen(client, new CheatingConfigScreen(null)));
   context.waitTicks(2);
   context.takeScreenshot("config-manager-compact-panel");
   context.runOnClient(client -> {
    ConfigManager.gameplayPanelConfirmed = true;
    ConfigManager.gameplayModules = java.util.Set.of(GameplayModule.AUTO_CLICKER.name(), GameplayModule.AUTO_GFS.name());
    ConfigManager.enabledModulesHud = true;
    ConfigManager.enabledModulesSort = 0;
    ConfigManager.enabledModulesHidden = java.util.Set.of();
    ConfigManager.enabledModulesBackgroundColor = 0x88000000;
    ClientUiAccess.setScreen(client, new CheatingConfigScreen(null, true));
    var screen = ClientUiAccess.screen(client);
    click(widget(screen, "Sort: A–Z"));
    click(widget(screen, "Sort: Longest First"));
    widget(screen, "Sort: Shortest First");
    var scale = (PinkUi.Slider) widget(screen, "Scale:");
    scale.setActual(1.8);
    if (!scale.getMessage().getString().equals("Scale: 1.8×")) throw new AssertionError("Scale has no numerical value");
    var spacing = (PinkUi.Slider) widget(screen, "Line Spacing:");
    spacing.setActual(4);
    if (!spacing.getMessage().getString().equals("Line Spacing: 4 px")) throw new AssertionError("Spacing has no pixel value");
    if (!widget(screen, "Background Opacity:").getMessage().getString().equals("Background Opacity: 53%")) {
     throw new AssertionError("Opacity display differs from actual alpha");
    }
    var color = (PinkUi.Field) widget(screen, "Text Color HEX");
    color.setValue("#12AB56");
    color.setValue("#12AB");
    if (ConfigManager.enabledModulesColor != 0xFF12AB56) throw new AssertionError("Text HEX color/input validation failed");
    color.setValue("#12AB56");
    ((PinkUi.Field) widget(screen, "Background Color HEX")).setValue("44CC66");
    if (ConfigManager.enabledModulesBackgroundColor != 0x8844CC66) throw new AssertionError("HEX color edit changed background alpha");
    var picker = screen.children().stream().filter(PinkUi.ColorPicker.class::isInstance)
        .map(PinkUi.ColorPicker.class::cast).findFirst().orElseThrow();
    if (picker.rgb() != 0x12AB56) throw new AssertionError("HEX color did not update the picker");
    click(picker);
    if (!color.getValue().equals(String.format(java.util.Locale.ROOT, "#%06X", picker.rgb()))) {
     throw new AssertionError("Picker did not update the HEX field");
    }
   });
   context.waitTicks(3);
   context.takeScreenshot("module-list-style-values");
   context.runOnClient(client -> {
    ConfigManager.enabledModulesChroma = false;
    click(widget(ClientUiAccess.screen(client), "Chroma"));
    if (!ConfigManager.enabledModulesChroma) throw new AssertionError("Chroma switch did not update the settings");
    var profile = CheatingConfigs.capture();
    ConfigManager.enabledModulesChroma = false;
    CheatingConfigs.apply(profile);
    if (!ConfigManager.enabledModulesChroma) throw new AssertionError("Profile did not restore Chroma");
   });
   context.waitTicks(2);
   context.takeScreenshot("module-list-chroma");
   context.runOnClient(client -> {
    var screen = ClientUiAccess.screen(client);
    click(widget(screen, "Chroma"));
    click(widget(screen, "Visible Modules"));
    widget(screen, "All");
    widget(screen, "Dungeon");
    widget(screen, "Other");
    click(widget(screen, "Render"));
    var search = (PinkUi.Field) widget(screen, "Search modules");
    search.setValue("auto click");
    if (screen.children().stream().anyMatch(child -> child instanceof PinkUi.Toggle toggle
        && toggle.getMessage().getString().startsWith("Auto Clicker"))) {
     throw new AssertionError("A general module appeared in the Render filter");
    }
    click(widget(screen, "General"));
    ConfigManager.enabledModulesHidden = java.util.Set.of("Auto Click");
    var legacyProfile = CheatingConfigs.capture();
    ConfigManager.enabledModulesHidden = java.util.Set.of();
    CheatingConfigs.apply(legacyProfile);
    if (EnabledModulesHud.shown("Auto Clicker")) throw new AssertionError("Legacy profile lost Auto Click visibility");
    click(widget(screen, "Auto Clicker"));
    if (!EnabledModulesHud.shown("Auto Clicker") || ConfigManager.enabledModulesHidden.contains("Auto Click")) {
     throw new AssertionError("Visibility toggle did not clear the old name");
    }
    click(widget(screen, "Auto Clicker"));
    if (EnabledModulesHud.shown("Auto Clicker") || !GameplayModule.AUTO_CLICKER.enabled()) {
     throw new AssertionError("Visibility toggle changed the wrong state");
    }
    var profile = CheatingConfigs.capture();
    ConfigManager.enabledModulesHidden = java.util.Set.of();
    CheatingConfigs.apply(profile);
    if (EnabledModulesHud.shown("Auto Clicker")) throw new AssertionError("Profile did not restore HUD visibility");
   });
   context.waitTicks(3);
   context.takeScreenshot("module-list-visible-modules");
   context.runOnClient(client -> {
    var screen = ClientUiAccess.screen(client);
    click(widget(screen, "Back"));
    widget(screen, "Visible Modules");
    ClientUiAccess.setScreen(client, null);
   });
   System.out.println("CHEATING_CONFIG_SCREENS_AND_HUD_OK");
   checkHudFont(context);
   checkFloatingHudPanel(context);
   checkAnimatedModuleList(context);
   context.runOnClient(client -> {
    ConfigManager.uiAnimationsEnabled = true;
    var dispatcher = ClientCommands.getActiveDispatcher();
    try {
     if (dispatcher == null || dispatcher.execute("csb cheating",
         (FabricClientCommandSource) client.getConnection().getSuggestionsProvider()) != 1) {
      throw new AssertionError("Could not open the Cheating screen through the command");
     }
    } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
     throw new AssertionError("Could not execute /csb cheating", error);
    }
   });
   context.waitFor(client -> ClientUiAccess.screen(client) instanceof CheatingScreen, 60);
   context.takeScreenshot("cheating-command-opening");
   context.waitTicks(8);
   context.takeScreenshot("cheating-command-settled");
   checkDeviceGroup(context);
   context.runOnClient(client -> {
    ConfigManager.gameplayPanelConfirmed = true;
    var menu = QuoiDungeon.menu();
    var modules = menu.modules();
    var slotBinds = entry(modules, "Slot Binds", DungeonMenu.Category.GENERAL);
    var loadouts = entry(modules, "Loadout Keybinds", DungeonMenu.Category.GENERAL);
    var autoClose = (DungeonMenu.Toggle) control(loadouts, "Auto Close After Use").input();
    if (autoClose.value().getAsBoolean()) throw new AssertionError("Loadout auto-close should be opt-in");
    var hotbar = control(loadouts, "General keybinds").children().stream()
        .filter(option -> option.name().equals("Use hotbar binds")).findFirst().orElseThrow();
    if (!((DungeonMenu.Toggle) hotbar.input()).value().getAsBoolean()) throw new AssertionError("Loadout hotbar binds default to on");
    entry(modules, "Pet Keybinds", DungeonMenu.Category.GENERAL);
    entry(modules, "Slayer Nametags", DungeonMenu.Category.RENDER);
    var markerMode = (DungeonMenu.Choice) control(slotBinds, "Show markers").input();
    if (!markerMode.options().equals(java.util.List.of("Always", "Hovered", "Never"))) {
     throw new AssertionError("Missing slot marker mode: " + markerMode.options());
    }
    var triggers = entry(modules, "Custom Triggers", DungeonMenu.Category.OTHER);
    var triggerManager = Class.forName("quoi.api.customtriggers.TriggerManager");
    var instance = triggerManager.getField("INSTANCE").get(null);
    var actions = triggerManager.getMethod("getActionEntries").invoke(instance).toString();
    for (var id : java.util.List.of("swap_item", "use_item", "module")) {
     if (!actions.contains("(" + id + ",")) throw new AssertionError("Missing packaged trigger action: " + id);
    }
    ClientUiAccess.setScreen(client, new CheatingConfigScreen(null));
    ((DungeonMenu.Button) control(triggers, "Open editor").input()).action().run();
   });
   context.waitTicks(3);
   context.runOnClient(client -> {
    var editor = ClientUiAccess.screen(client);
    if (!editor.getTitle().getString().equals("Custom Triggers")) throw new AssertionError("Trigger editor did not open");
    editor.onClose();
    if (!(ClientUiAccess.screen(client) instanceof CheatingConfigScreen)) throw new AssertionError("Trigger editor lost its parent screen");
   });
   System.out.println("CHEATING_UPSTREAM_MODULES_AND_TRIGGER_EDITOR_OK");
   context.getInput().resizeWindow(1280, 720);
   context.waitTicks(3);
   context.runOnClient(client -> {
    ConfigManager.resetSettings("uiThemeMode", "uiGlassTheme");
    ConfigManager.enabledModulesHud = false;
    ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.THEME));
   });
   context.waitTicks(12);
   context.takeScreenshot("clickgui-glass-shared-pane");
   context.runOnClient(client -> {
    var constructor = CheatingScreen.class.getDeclaredConstructor(Screen.class);
    constructor.setAccessible(true);
    ClientUiAccess.setScreen(client, constructor.newInstance(ClientUiAccess.screen(client)));
   });
   context.waitTicks(12);
   context.takeScreenshot("cheating-glass-shared-pane");
   checkLevelPresets(context);
  } catch(Exception e){throw new AssertionError(e);}
 }

 private static void checkConfigConfirmation(ClientGameTestContext context) throws java.io.IOException {
  int original = ConfigManager.experimentMinDelay;
  String file = "confirmation-fixture";
  context.runOnClient(client -> {
   ConfigManager.experimentMinDelay = 170;
   CheatingConfigs.run("save", file);
   ConfigManager.experimentMinDelay = 270;
   ClientUiAccess.setScreen(client, new CheatingConfigScreen(null));
   var screen = ClientUiAccess.screen(client);
   ((PinkUi.Field) widget(screen, "Config filename")).setValue(file);
   click(widget(screen, "Load"));
   if (ConfigManager.experimentMinDelay != 270) throw new AssertionError("Load skipped confirmation");
  });
  context.waitTicks(3);
  context.takeScreenshot("config-load-confirmation");
  context.runOnClient(client -> {
   try {
    click(widget(ClientUiAccess.screen(client), "Load"));
    if (ConfigManager.experimentMinDelay != 170) throw new AssertionError("Second click did not load the profile");
   } finally {
    ConfigManager.experimentMinDelay = original;
    CheatingConfigs.run("remove", file);
   }
  });
 }

 private static void checkDeviceGroup(ClientGameTestContext context) throws Exception {
  context.runOnClient(client -> {
   var menu = QuoiDungeon.menu();
   var before = CheatingConfigs.capture();
   var profile = new JsonObject();
   profile.addProperty("format", 1);
   profile.add("local", JsonParser.parseString("""
       {"autoI4RotationTime":113,"gameplayModules":["AUTO_I4"],
        "gameplayKeybinds":{"key.keyboard.g":"AUTO_I4"}}
       """));
   var oldDungeon = JsonParser.parseString(menu.snapshotConfig()).getAsJsonArray();
   for (var row : oldDungeon) {
    var module = row.getAsJsonObject();
    String name = module.get("name").getAsString();
    if (java.util.Set.of("Simon Says", "Lights Device", "Arrow Align").contains(name)) {
     module.addProperty("enabled", !name.equals("Lights Device"));
    }
   }
   profile.add("dungeon", oldDungeon);
   var file = CheatingConfigs.path("device-fixture");
   try {
    DeviceConfig.writeJson(file, profile);
    CheatingConfigs.run("load", "device-fixture");
    var migrated = JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
    var devices = migrated.getAsJsonObject("device");
    if (migrated.get("format").getAsInt() != 2 || !devices.get("enabled").getAsBoolean()
        || !devices.getAsJsonObject("Simon Says").get("enabled").getAsBoolean()
        || devices.getAsJsonObject("Light Device").get("enabled").getAsBoolean()
        || !devices.getAsJsonObject("Arrow Align").get("enabled").getAsBoolean()
        || !GameplayModule.AUTO_I4.enabled() || ConfigManager.autoI4RotationTime != 113
        || ConfigManager.gameplayKeybinds.get("key.keyboard.g") != GameplayModule.AUTO_I4) {
     throw new AssertionError("Device migration lost settings: " + devices);
    }
    CheatingConfigs.run("load", "device-fixture");
    if (!entry(menu.modules(), "Simon Says", DungeonMenu.Category.DUNGEON).enabled().getAsBoolean()) {
     throw new AssertionError("Reloading the consolidated file lost Simon Says");
    }
    var screen = ClientUiAccess.screen(client);
    click(widget(screen, "Dungeon"));
    ((PinkUi.Field) widget(screen, "Search modules")).setValue("Arrow Align");
    var simon = entry(menu.modules(), "Simon Says", DungeonMenu.Category.DUNGEON);
    var managerType = Class.forName("quoi.module.ModuleManager");
    var manager = managerType.getField("INSTANCE").get(null);
    var moduleByName = managerType.getMethod("getModuleByName", String.class);
    var nativeSimon = moduleByName.invoke(manager, "Simon Says");
    var nativeLight = moduleByName.invoke(manager, "Lights Device");
    var nativeArrow = moduleByName.invoke(manager, "Arrow Align");
    var running = nativeSimon.getClass().getMethod("getRunning");
    var lightRunning = nativeLight.getClass().getMethod("getRunning");
    var arrowRunning = nativeArrow.getClass().getMethod("getRunning");
    if (!(boolean) running.invoke(nativeSimon) || !(boolean) arrowRunning.invoke(nativeArrow)
        || (boolean) lightRunning.invoke(nativeLight)) {
     throw new AssertionError("The migrated Device did not restore the selected children");
    }
    click(widget(screen, "Device"));
    if (GameplayModule.DEVICE.enabled() || GameplayModule.AUTO_I4.enabled() || (boolean) running.invoke(nativeSimon)
        || (boolean) arrowRunning.invoke(nativeArrow) || !simon.enabled().getAsBoolean() || !GameplayModule.AUTO_I4.configured()) {
     throw new AssertionError("Turning off the parent either left children running or cleared child settings");
    }
    click(widget(screen, "⚙"));
    var labels = java.util.List.of("Simon Says", "Light Device", "Arrow Align", "Auto I4");
    var headings = screen.children().stream().filter(child -> child.getClass() == PinkUi.Action.class)
        .map(child -> ((PinkUi.Action) child).getMessage().getString()).filter(labels::contains).toList();
    if (!headings.equals(labels)) throw new AssertionError("Unexpected Device order: " + headings);
    click(widget(screen, "Device Keybind"));
    var masterKey = InputConstants.getKey("key.keyboard.j");
    var i4Key = InputConstants.getKey("key.keyboard.g");
    screen.keyPressed(new net.minecraft.client.input.KeyEvent(masterKey.getValue(), 0, 0));
    click(widget(screen, "Auto I4"));
    click(widget(screen, "Auto I4 Keybind"));
    screen.keyPressed(new net.minecraft.client.input.KeyEvent(i4Key.getValue(), 0, 0));
    var dungeonKeyCodes = java.util.stream.Stream.of("key.keyboard.k", "key.keyboard.l", "key.keyboard.p")
        .map(InputConstants::getKey).mapToInt(InputConstants.Key::getValue).toArray();
    for (int i = 0; i < dungeonKeyCodes.length; i++) {
     click(widget(screen, labels.get(i)));
     click(widget(screen, "Key bind"));
     screen.keyPressed(new net.minecraft.client.input.KeyEvent(dungeonKeyCodes[i], 0, 0));
    }
    var hudEntries = EnabledModulesHud.availableModules(GameplayModule.Category.DUNGEON);
    if (!hudEntries.contains("Device") || hudEntries.stream().anyMatch(labels::contains) || hudEntries.contains("Lights Device"))
     throw new AssertionError("Device children appear separately in the HUD: " + hudEntries);
    click(widget(screen, "Device"));
    if (!GameplayModule.DEVICE.enabled() || !GameplayModule.AUTO_I4.enabled()
        || !(boolean) running.invoke(nativeSimon) || !(boolean) arrowRunning.invoke(nativeArrow)
        || (boolean) lightRunning.invoke(nativeLight)) {
     throw new AssertionError("Turning on Device did not restore exactly the selected children");
    }
    var names = EnabledModulesHud.class.getDeclaredMethod("moduleNames", boolean.class, GameplayModule.Category.class);
    names.setAccessible(true);
    var shown = (java.util.Set<?>) names.invoke(null, true, GameplayModule.Category.DUNGEON);
    if (!shown.contains("Device") || shown.stream().anyMatch(labels::contains) || shown.contains("Lights Device"))
     throw new AssertionError("Enabled HUD entries did not collapse into Device: " + shown);
    if (ConfigManager.gameplayKeybinds.get("key.keyboard.j") != GameplayModule.DEVICE
        || ConfigManager.gameplayKeybinds.get("key.keyboard.g") != GameplayModule.AUTO_I4) {
     throw new AssertionError("The master and Auto I4 recordings overwrote each other");
    }
    ClientUiAccess.setScreen(client, null);
    com.customcosmetics.features.gameplay.GameplayKeybinds.press(masterKey);
    var handleNativeKey = managerType.getDeclaredMethod("invokeKeybind", int.class, boolean.class);
    handleNativeKey.setAccessible(true);
    for (int code : dungeonKeyCodes) handleNativeKey.invoke(manager, code, true);
    com.customcosmetics.features.gameplay.GameplayKeybinds.press(i4Key);
    if (GameplayModule.DEVICE.enabled() || GameplayModule.AUTO_I4.configured() || simon.enabled().getAsBoolean()
        || !entry(menu.modules(), "Lights Device", DungeonMenu.Category.DUNGEON).enabled().getAsBoolean()
        || entry(menu.modules(), "Arrow Align", DungeonMenu.Category.DUNGEON).enabled().getAsBoolean()
        || (boolean) lightRunning.invoke(nativeLight)) {
     throw new AssertionError("A child shortcut failed to toggle its own setting with Device off");
    }
    ClientUiAccess.setScreen(client, screen);
   } finally {
    CheatingConfigs.apply(before);
    java.nio.file.Files.deleteIfExists(file);
   }
  });
  context.waitTicks(4);
  context.takeScreenshot("dungeon-device");
  System.out.println("CSB_DEVICE_MASTER_GATE_SEPARATE_BINDINGS_MIGRATION_AND_HUD_OK");
 }

 private static void checkAnimatedModuleList(ClientGameTestContext context) throws ReflectiveOperationException {
  var rows = EnabledModulesHud.class.getDeclaredField("liveRows");
  rows.setAccessible(true);
  context.runOnClient(client -> {
   ConfigManager.enabledModulesHud = true;
   ConfigManager.uiAnimationsEnabled = true;
   ConfigManager.enabledModulesHidden = java.util.Set.of();
   ConfigManager.gameplayModules = java.util.Set.of(GameplayModule.TREVOR_HELPER.name());
   ClientUiAccess.setScreen(client, null);
  });
  context.waitTicks(5);
  context.runOnClient(client -> {
   if (((java.util.List<?>) rows.get(null)).isEmpty()) throw new AssertionError("Live HUD did not show the enabled module");
   ConfigManager.gameplayModules = java.util.Set.of();
  });
  context.waitTicks(8);
  context.runOnClient(client -> {
   if (!((java.util.List<?>) rows.get(null)).isEmpty()) throw new AssertionError("Removed module stayed in the list");
   ConfigManager.gameplayModules = java.util.Set.of(GameplayModule.TREVOR_HELPER.name());
  });
  context.waitTicks(5);
  context.runOnClient(client -> {
   if (((java.util.List<?>) rows.get(null)).isEmpty()) throw new AssertionError("The module did not reappear");
   ConfigManager.gameplayModules = java.util.Set.of();
  });
  System.out.println("CSB_MODULE_LIST_LIVE_TRANSITIONS_OK");
 }

 private static void checkLevelPresets(ClientGameTestContext context) {
  var colors = java.util.List.of(0xFF66AA, 0xFFCC55, 0x55DDEE, 0xBB88FF, -1);
  context.runOnClient(client -> {
   ConfigManager.skyBlockLevelEnabled = false;
   ConfigManager.skyBlockLevelValue = 1314;
   ConfigManager.skyBlockLevelColorMode = "digits";
   ConfigManager.skyBlockLevelDigitColors = colors;
   ConfigManager.skyBlockLevelColorPresets = java.util.Map.of();
   ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.SKYBLOCK_LEVEL));
   var screen = ClientUiAccess.screen(client);
   click(widget(screen, "Presets"));
   ((PinkUi.Field) widget(screen, "Preset Name")).setValue("Sunset");
   click(widget(screen, "Save / Update"));
   if (!ConfigManager.skyBlockLevelColorPresets.containsKey("Sunset")) throw new AssertionError("Could not save color preset");
   ConfigManager.skyBlockLevelValue = 523;
   ConfigManager.skyBlockLevelColorMode = "auto";
   ConfigManager.skyBlockLevelDigitColors = java.util.List.of();
   click(widget(screen, "Apply: Sunset"));
   if (ConfigManager.skyBlockLevelEnabled || ConfigManager.skyBlockLevelValue != 523
       || !ConfigManager.skyBlockLevelDigitColors.equals(colors)
       || !SkyBlockLevel.configured().colors().equals(colors.subList(0, 3))) {
    throw new AssertionError("Applying a color preset changed the level or lost colors");
   }
  });
  context.waitTicks(12);
  context.takeScreenshot("skyblock-level-color-presets");
  context.runOnClient(client -> {
   click(widget(ClientUiAccess.screen(client), "Delete: Sunset"));
   if (!ConfigManager.skyBlockLevelColorPresets.isEmpty()) throw new AssertionError("Could not delete color preset");
  });
  System.out.println("SKYBLOCK_LEVEL_COLOR_PRESET_UI_OK");
 }

 private static void checkHudFont(ClientGameTestContext context) {
  var path = UiFonts.systemFonts().stream()
      .filter(font -> font.toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ttf"))
      .findFirst().orElseThrow(() -> new AssertionError("A system TrueType font is needed for the HUD fixture"));
  var loaded = new java.util.concurrent.CompletableFuture<Boolean>();
  context.runOnClient(client -> {
   ConfigManager.useFontInGame = false;
   ConfigManager.enabledModulesUseClickGuiFont = true;
   ConfigManager.enabledModulesHidden = java.util.Set.of();
   ConfigManager.gameplayModules = java.util.Set.of(GameplayModule.AUTO_CLICKER.name());
   ConfigManager.enabledModulesScale = 1;
   ConfigManager.enabledModulesHud = true;
   UiFonts.load(client, path, new UiFonts.Style(12, 2, 3, 0, 1), loaded::complete);
  });
  context.waitFor(client -> loaded.isDone(), 100);
  context.runOnClient(client -> {
   if (!loaded.getNow(false)) throw new AssertionError("Could not load test font " + path);
   ClientUiAccess.setScreen(client, new CheatingConfigScreen(null, true));
  });
  context.waitTicks(3);
  context.takeScreenshot("module-list-clickgui-font-setting");
  context.runOnClient(client -> {
   var hudFont = UiFonts.get(client.font);
   int customWidth = hudFont.width("Auto Clicker") + 8;
   int vanillaWidth = client.font.width("Auto Clicker") + 8;
   if (customWidth == vanillaWidth || EnabledModulesHud.width() != customWidth
       || EnabledModulesHud.height() != 12 + ConfigManager.enabledModulesSpacing) {
    throw new AssertionError("HUD metrics did not follow the selected font while the global font was disabled");
   }
   var screen = ClientUiAccess.screen(client);
   var profile = CheatingConfigs.capture();
   click(widget(screen, "Use ClickGUI Font"));
   if (ConfigManager.enabledModulesUseClickGuiFont || EnabledModulesHud.width() != vanillaWidth) {
    throw new AssertionError("Font toggle did not restore game font metrics");
   }
   CheatingConfigs.apply(profile);
   if (!ConfigManager.enabledModulesUseClickGuiFont || EnabledModulesHud.width() != customWidth) {
    throw new AssertionError("Config profile did not restore the HUD font selection");
   }
   screen.onClose();
  });
  context.waitTicks(3);
  context.takeScreenshot("module-list-clickgui-font-live");
  context.runOnClient(client -> UiFonts.clear());
  System.out.println("MODULE_LIST_CLICKGUI_FONT_SCOPE_OK");
 }

 private static AbstractWidget widget(Screen screen, String prefix) {
  return screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
      .filter(widget -> widget.getMessage().getString().startsWith(prefix)).findFirst().orElseThrow();
 }

 private static void checkFloatingHudPanel(ClientGameTestContext context) throws ReflectiveOperationException {
  context.runOnClient(client -> {
   ConfigManager.gameplayPanelConfirmed = true;
   ConfigManager.gameplayModules = java.util.Set.of(GameplayModule.TREVOR_HELPER.name());
   ConfigManager.enabledModulesHud = true;
   ConfigManager.enabledModulesScale = 1;
   ConfigManager.enabledModulesX = .85f;
   ConfigManager.trevorHudX = .02f;
   ConfigManager.trevorHudY = .32f;
   ConfigManager.hudLayoutGrid = false;
   ConfigManager.uiGlassTheme = true;
   ConfigManager.uiGlassOpacity = 60;
   var constructor = Class.forName("com.customcosmetics.gui.GameplayHudEditor").getDeclaredConstructor(Screen.class);
   constructor.setAccessible(true);
   ClientUiAccess.setScreen(client, (Screen) constructor.newInstance((Object) null));
   click(widget(ClientUiAccess.screen(client), "5 px Grid"));
   click(widget(ClientUiAccess.screen(client), "Module List Style"));
  });
  context.waitTicks(3);
  context.runOnClient(client -> {
   var screen = ClientUiAccess.screen(client);
   int x = panelInt(screen, "panelX"), y = panelInt(screen, "panelY");
   int screenWidth = client.getWindow().getGuiScaledWidth(), screenHeight = client.getWindow().getGuiScaledHeight();
   int destX = screenWidth - panelInt(screen, "panelWidth") - 8;
   if (x == destX) throw new AssertionError("Test panel has no horizontal travel");
   var title = mouseAt(x + 18, y + 14);
   var movedTitle = mouseAt(destX + 18, y + 14);
   if (!screen.mouseClicked(title, false) || !screen.mouseDragged(movedTitle, destX - x, 0)
       || !screen.mouseReleased(movedTitle) || panelInt(screen, "movedX") != destX) {
    throw new AssertionError("Floating style panel did not move by its title");
   }
   int hudX = GameplayFeatures.hudX(1, screenWidth) + 3, hudY = GameplayFeatures.hudY(1, screenHeight) + 3;
   float oldY = ConfigManager.trevorHudY;
   var hudStart = mouseAt(hudX, hudY);
   var hudEnd = mouseAt(hudX, hudY + 11);
   if (!screen.mouseClicked(hudStart, false) || !screen.mouseDragged(hudEnd, 0, 11)
       || !screen.mouseReleased(hudEnd) || ConfigManager.trevorHudY <= oldY || panelInt(screen, "movedX") != destX) {
    throw new AssertionError("HUD element drag did not stay independent of the style panel");
   }
   if (GameplayFeatures.hudX(1, screenWidth) % 5 != 0 || GameplayFeatures.hudY(1, screenHeight) % 5 != 0) {
    throw new AssertionError("HUD position did not snap to the 5 px grid");
   }
   float oldScale = GameplayFeatures.hudScale(1);
   if (!screen.mouseScrolled(hudEnd.x(), hudEnd.y(), 0, 1) || GameplayFeatures.hudScale(1) <= oldScale) {
    throw new AssertionError("HUD element did not resize outside the style panel");
   }
  });
  context.waitTicks(3);
  context.takeScreenshot("floating-style-panel-with-huds");
  context.runOnClient(client -> {
   var screen = ClientUiAccess.screen(client);
   int dx = panelInt(screen, "movedX") - panelInt(screen, "panelX");
   var button = widget(screen, "Visible Modules");
   var click = mouseAt(button.getX() + dx + 10, button.getY() + 6);
   if (!screen.mouseClicked(click, false)) throw new AssertionError("Moved widget did not receive its click");
   screen.mouseReleased(click);
   var first = firstModuleToggle(screen);
   dx = panelInt(screen, "movedX") - panelInt(screen, "panelX");
   int dy = panelInt(screen, "movedY") - panelInt(screen, "panelY");
   if (!screen.mouseScrolled(first.getX() + dx + 5, first.getY() + dy + 5, 0, -1)
       || first.getMessage().getString().equals(firstModuleToggle(screen).getMessage().getString())) {
    throw new AssertionError("Visible Modules did not advance on wheel scroll");
   }
   if (screen.children().stream().anyMatch(child -> child instanceof AbstractWidget w
       && java.util.Set.of("Previous", "Next").contains(w.getMessage().getString()))) {
    throw new AssertionError("Visible Modules still presents pagination buttons");
   }
  });
  context.waitTicks(3);
  context.takeScreenshot("visible-modules-wheel-scroll");
  System.out.println("CSB_FLOATING_HUD_EDITOR_AND_WHEEL_SCROLL_OK");
 }

 private static PinkUi.Toggle firstModuleToggle(Screen screen) {
  return screen.children().stream().filter(PinkUi.Toggle.class::isInstance).map(PinkUi.Toggle.class::cast).findFirst().orElseThrow();
 }

 private static int panelInt(Screen screen, String field) throws ReflectiveOperationException {
  var declared = CheatingConfigScreen.class.getDeclaredField(field);
  declared.setAccessible(true);
  return declared.getInt(screen);
 }

 private static MouseButtonEvent mouseAt(double x, double y) {
  return new MouseButtonEvent(x, y, new MouseButtonInfo(ClientInput.MOUSE_LEFT, 0));
 }

 private static void click(AbstractWidget widget) {
  widget.onClick(new MouseButtonEvent(widget.getX()+4, widget.getY()+4, new MouseButtonInfo(ClientInput.MOUSE_LEFT, 0)), false);
 }

 private static DungeonMenu.Entry entry(java.util.List<DungeonMenu.Entry> modules, String name, DungeonMenu.Category category) {
  var entry = modules.stream().filter(module -> module.name().equals(name)).findFirst().orElseThrow();
  if (entry.category() != category) throw new AssertionError("Wrong category for " + name);
  return entry;
 }

 private static DungeonMenu.Control control(DungeonMenu.Entry module, String name) {
  return module.settings().get().stream().filter(control -> control.name().equals(name)).findFirst().orElseThrow();
 }
}
