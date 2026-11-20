// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.CosmeticsConfigDir;
import com.customcosmetics.features.gameplay.dungeonapi.DeviceConfig;
import com.customcosmetics.utils.MessageUtils;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
import java.lang.reflect.Field;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import com.mojang.brigadier.arguments.StringArgumentType;

public final class CheatingConfigs {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> FIELDS = Set.of((
        "arrowDelayMs arrowLookOnly arrowRange arrowSneakPause autoClickerBlockDungeonBreaker autoClickerBreakBlocks autoClickerJitter autoClickerLeft " +
        "autoClickerLeftCps autoClickerLeftItems autoClickerRequireKey autoClickerReverseKey autoClickerRight autoClickerRightCps autoClickerRightItems autoClickerWhitelistOnly " +
        "autoI4Leap autoI4LeapPriority autoI4LeapToMelody autoI4Mask autoI4Precise autoI4Predict autoI4Rod autoI4RotationTime " +
        "autoI4SwapOrder autoLeapAfterBlood autoLeapByName autoLeapClasses autoLeapDelay autoLeapGate autoLeapMelody autoLeapNames " +
        "autoLeapPresets autoLeapSwapBack autoLeapTriggers bookCombineCloseAfterFinish bookCombineDisableAfterFinish bookCombineResultDelay carnivalShotDelay chocolateClick " +
        "chocolateDelay chocolateEggs chocolateMute chocolateStrays chocolateTower chocolateUpgrade chocolateUpgradeDelay corpseCube " +
        "corpseDepth corpseDistance corpseFill corpseHideClaimed corpseLineWidth corpseName corpseOutline corpseTracer " +
        "crystalDelay crystalInMenus crystalLookOnly crystalRange dojoControlAim dojoControlCenter dojoControlColor dojoControlHighlight " +
        "dojoControlPrediction dojoDisciplineBlock dojoDisciplineColor dojoDisciplineHighlight dojoDisciplineSwap dojoDisciplineTracer dojoForceBlock dojoForceColor " +
        "dojoForceHighlight dojoMasteryAmount dojoMasteryAuto dojoMasteryOffset dojoMasterySneakStops dojoMasteryTracer dojoSwiftnessAuto dojoSwiftnessCurve " +
        "dojoSwiftnessLead dojoSwiftnessRotation dojoSwiftnessStop dojoSwiftnessTurnDistance dojoTenacityColor dojoTenacityHighlight dojoTenacityLine doorAura " +
        "doorAuraRange doorInMenus doorRetryMs doorSwing enabledModulesBackground enabledModulesBackgroundColor enabledModulesChroma enabledModulesColor enabledModulesHidden enabledModulesHud " +
        "enabledModulesRightAlign enabledModulesScale enabledModulesShadow enabledModulesSort enabledModulesSpacing enabledModulesUseClickGuiFont enabledModulesX enabledModulesY experimentAutoClose " +
        "experimentGetMax experimentMaxDelay experimentMinDelay experimentSerums f7HudX f7HudY f7TimerTicks fastLeapAfterBlood " +
        "fastLeapClasses fastLeapMelody fastLeapNames fastLeapTarget fastLeapTriggers gameplayDeviceEnabled gameplayHudScales gameplayKeybinds gameplayModules " +
        "harpClickDelay hideAllPlayers hideNearbyPlayersRadius hidePlayersAtDevices hidePlayersBossOnly hidePlayersClickThrough hidePlayersDungeonOnly lapisCorpse " +
        "lapisCorpseColor mirrorverseJumpDelay mirrorverseLavaMaze mirrorverseLavaParkour mirrorversePunchDelay mirrorverseRedGreen mirrorverseRedGreenActionDelay mirrorverseRedGreenInteractDelay " +
        "mirrorverseShowMobs mirrorverseShowRecipes mirrorverseSneakDelay mirrorverseTinyDancer mirrorverseTubulator pestEspColor pestEspDepth pestEspThickness " +
        "pestEspTracer refillDungeonsOnly refillIntervalSeconds refillJerries refillLeaps refillPearls refillPercent refillSuperboom " +
        "refillTimed relicDelayMs relicPickup relicPlacement relicRange renderHideArm renderHideEffects renderHideEntityFire " +
        "renderHideFire renderHideFog renderHideGlow renderHideLava renderHideMobArmor renderHideOtherArmor renderHidePlayerArmor renderHideSelfArmor " +
        "safariAcrossBiomes safariDisabledBiomes safariDisabledSpecies safariFloorDropColor safariFloorDrops safariFloorDropsCurrentBiome safariSpeciesColors scannerJasperBackground " +
        "scannerOptions scannerShowJasperCount secretAuraBossRange secretAuraDelay secretAuraFov secretAuraInBoss secretAuraInContainer secretAuraRange " +
        "secretAuraSkullRange secretAuraSlot secretAuraSwap secretAuraSwapBack secretAuraSwing secretInteractDelay secretSwapBack secretSwapItem " +
        "secretSwapSlot simonAnnounce simonAuto simonDelayMs simonFirstColor simonHintOpacity simonRotation simonRotationTime " +
        "simonSecondColor simonSolver simonStartClicks simonStartDelay simonThirdColor spookCommitment spookDelay spookDungeon " +
        "spookMath spookSpeaking spookTitle starredMobColor terminalAuraFov terminalDelayMs terminalGroundOnly terminalLeapDelay " +
        "terminalLeapDelaySeconds terminalLookOnly terminalRange trajectoryBows trajectoryBox trajectoryBoxSize trajectoryColor trajectoryDepth " +
        "trajectoryLine trajectoryLineWidth trajectoryPearls trajectoryPlane trajectoryPlaneSize trajectoryTicks trevorAcceptDelay trevorAlertMessage " +
        "trevorAlertSound trevorAutoAccept trevorAutoCall trevorCallDelay trevorCallEarly trevorColors trevorCooldownAlert trevorCooldownTimer " +
        "trevorEsp trevorHudX trevorHudY trevorTracer tungstenCorpse tungstenCorpseColor umberCorpse umberCorpseColor " +
        "vanguardCorpse vanguardCorpseColor"
    ).split(" "));
    public static Path directory() { return CosmeticsConfigDir.getCosmeticsConfigDir().resolve("cheating-configs"); }
    public static Path path(String name) {
        if (name.endsWith(".json")) name = name.substring(0, name.length()-5);
        if (!name.matches("[A-Za-z0-9][A-Za-z0-9 _.-]{0,63}") || name.contains(".."))
            throw new IllegalArgumentException("Use a filename of 1–64 letters, numbers, spaces, dots, underscores or hyphens.");
        Path path = directory().resolve(name + ".json");
        if (Files.isSymbolicLink(path)) throw new IllegalArgumentException("Symbolic links are not supported.");
        return path;
    }
    public static List<String> list() {
        if (!Files.isDirectory(directory())) return List.of();
        try (var files = Files.list(directory())) {
            return files.filter(p -> Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS) && p.toString().endsWith(".json"))
                .map(p -> p.getFileName().toString().replaceFirst("\\.json$", "")).sorted(String.CASE_INSENSITIVE_ORDER).toList();
        } catch (java.io.IOException error) { throw new IllegalStateException("Cannot list configs", error); }
    }
    private static java.util.List<net.minecraft.client.KeyMapping> clickKeys() {
        return java.util.Arrays.asList(AutoClick.key(true),AutoClick.key(false),AutoClick.requiredKey());
    }
    public static JsonObject capture() {
        return consolidateDevices(captureCurrent());
    }

    private static JsonObject captureCurrent() {
        JsonObject root = new JsonObject(), local = new JsonObject();
        root.addProperty("format", 1);
        try { for (String name : FIELDS) {
            Field field = ConfigManager.class.getField(name);
            local.add(name, GSON.toJsonTree(field.get(null), field.getGenericType()));
        }} catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
        root.add("local", local);
        JsonObject keys=new JsonObject(); int i=0;
        for(var key:clickKeys()) {if(key!=null) keys.addProperty(Integer.toString(i),net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.getBoundKeyOf(key).getName());i++;}
        root.add("clickKeys",keys);
        var dungeon = QuoiDungeon.menu();
        if (dungeon != null) root.add("dungeon", JsonParser.parseString(dungeon.snapshotConfig()));
        return root;
    }

    private static JsonObject consolidateDevices(JsonObject profile) {
        JsonObject device = DeviceConfig.takeLocal(profile.getAsJsonObject("local"));
        if (profile.has("dungeon")) DeviceConfig.takeDungeon(profile.getAsJsonArray("dungeon")).entrySet()
            .forEach(entry -> device.add(entry.getKey(), entry.getValue()));
        DeviceConfig.inferMaster(device);
        profile.add("device", device);
        profile.addProperty("format", 2);
        return profile;
    }

    public static void apply(JsonObject root) {
        int format = root.has("format") ? root.get("format").getAsInt() : 0;
        if ((format != 1 && format != 2) || !root.has("local"))
            throw new IllegalArgumentException("Unsupported config format");
        root = root.deepCopy();
        if (format == 1) consolidateDevices(root);
        var device = root.getAsJsonObject("device");
        DeviceConfig.inferMaster(device);
        DeviceConfig.restoreLocal(root.getAsJsonObject("local"), device);
        JsonArray dungeonSettings = root.has("dungeon") ? root.getAsJsonArray("dungeon") : new JsonArray();
        DeviceConfig.restoreDungeon(dungeonSettings, device);
        if (!dungeonSettings.isEmpty()) root.add("dungeon", dungeonSettings);
        Map<Field,Object> values = new LinkedHashMap<>();
        try {
            for (var entry : root.getAsJsonObject("local").entrySet()) {
                if (!FIELDS.contains(entry.getKey())) continue;
                Field field = ConfigManager.class.getField(entry.getKey());
                Object value = GSON.fromJson(entry.getValue(), field.getGenericType());
                if (value instanceof Double d && !Double.isFinite(d) || value instanceof Float f && !Float.isFinite(f)) throw new IllegalArgumentException("Non-finite setting: " + entry.getKey());
                if (value == null) throw new IllegalArgumentException("Invalid setting: " + entry.getKey());
                values.put(field, value);
            }
            var dungeon = QuoiDungeon.menu();
            if (root.has("dungeon") && dungeon == null) throw new IllegalStateException("Open Cheating first to load dungeon modules.");
            java.util.Map<net.minecraft.client.KeyMapping,com.mojang.blaze3d.platform.InputConstants.Key> keys=new java.util.LinkedHashMap<>();
            if(root.has("clickKeys")) {var bindings=clickKeys();for(var entry:root.getAsJsonObject("clickKeys").entrySet()) {int i=Integer.parseInt(entry.getKey());if(i<0||i>=bindings.size())throw new IllegalArgumentException("Invalid key index");if(bindings.get(i)!=null) keys.put(bindings.get(i),com.mojang.blaze3d.platform.InputConstants.getKey(entry.getValue().getAsString()));}}
            JsonObject before = captureCurrent();
            try {
                if (dungeon != null && root.has("dungeon")) {
                    if (!root.get("dungeon").isJsonArray()) throw new IllegalArgumentException("Invalid dungeon settings");
                    dungeon.loadConfig(root.get("dungeon").toString());
                }
                for (var entry : values.entrySet()) entry.getKey().set(null, entry.getValue());
                ConfigManager.gameplayKeybinds = GameplayKeybinds.normalize(ConfigManager.gameplayKeybinds);
                keys.forEach(net.minecraft.client.KeyMapping::setKey);
                if(!keys.isEmpty()) net.minecraft.client.KeyMapping.resetMapping();
                if (ConfigManager.gameplayDeviceEnabled != before.getAsJsonObject("local").get("gameplayDeviceEnabled").getAsBoolean())
                    QuoiDungeon.deviceGateChanged();
                ConfigManager.requestSave();
            } catch (Exception failure) {
                for (Field field : values.keySet()) field.set(null, GSON.fromJson(before.getAsJsonObject("local").get(field.getName()), field.getGenericType()));
                if (dungeon != null && before.has("dungeon")) dungeon.loadConfig(before.get("dungeon").toString());
                throw failure;
            }
        } catch (ReflectiveOperationException error) { throw new IllegalArgumentException("Invalid config",error); }
    }
    public static void run(String action, String name) throws java.io.IOException {
        Path file = path(name);
        switch(action) {
            case "save" -> DeviceConfig.writeJson(file, capture());
            case "load" -> {
                if (Files.size(file) > 16_000_000) throw new IllegalArgumentException("Config exceeds 16 MB");
                JsonObject profile = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                apply(profile);
                if (profile.get("format").getAsInt() == 1) DeviceConfig.writeJson(file, consolidateDevices(profile));
                else if (DeviceConfig.inferMaster(profile.getAsJsonObject("device"))) DeviceConfig.writeJson(file, profile);
            }
            case "remove" -> { if (!Files.deleteIfExists(file)) throw new IllegalArgumentException("Config does not exist"); }
            default -> throw new IllegalArgumentException("Unknown config action");
        }
    }
    public static int execute(String action, String name) {
        try { run(action,name); MessageUtils.sendMessage("Config " + action + ": " + name); return 1; }
        catch(Exception error) { MessageUtils.sendMessage("Config: " + error.getMessage()); return 0; }
    }
    public static com.mojang.brigadier.builder.LiteralArgumentBuilder<net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource> command() {
        var root = ClientCommands.literal("config").executes(ctx -> {
            com.customcosmetics.client.CustomCosmeticsClient.requestScreen(() -> new com.customcosmetics.gui.CheatingConfigScreen(null)); return 1;
        });
        for (String action : List.of("save","load","remove")) root.then(ClientCommands.literal(action)
            .then(ClientCommands.argument("filename", StringArgumentType.greedyString())
                .suggests((ctx,builder) -> net.minecraft.commands.SharedSuggestionProvider.suggest(list(),builder))
                .executes(ctx -> execute(action,StringArgumentType.getString(ctx,"filename")))));
        return root;
    }
}
