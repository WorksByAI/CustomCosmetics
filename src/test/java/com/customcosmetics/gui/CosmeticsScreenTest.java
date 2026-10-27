package com.customcosmetics.gui;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeElement;
import java.lang.classfile.MethodModel;
import java.lang.classfile.instruction.ConstantInstruction;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Inspect the compiled screen without starting Minecraft or creating a graphics context. */
class CosmeticsScreenTest {
    @Test void damageAndSearchAreLocalAndNeverMutateGameplayEntities()throws Exception{
        for(String type:List.of("features/damage/DamageSplash","features/damage/DamageText","features/damage/DamageAnimation","gui/SettingsSearch","gui/SettingsSearch$Index","gui/SettingsCatalog")){
            var calls=read("com/customcosmetics/"+type).methods().stream().flatMap(method->instructions(method).stream()).filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast).toList();
            assertTrue(calls.stream().noneMatch(call->call.owner().name().stringValue().startsWith("java/net/")||call.owner().name().stringValue().startsWith("com/customcosmetics/sync/")));
            assertTrue(calls.stream().noneMatch(call->Set.of("remove","discard","setCustomName","setHealth","setPos","send","sendPacket","sendImmediately").contains(call.name().stringValue())&&call.owner().name().stringValue().startsWith("net/minecraft/")));
        }
        var metadata=read("com/customcosmetics/mixin/DamageSplashMixin$Metadata");
        assertTrue(metadata.methods().stream().flatMap(method->instructions(method).stream()).filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast).noneMatch(call->call.name().stringValue().equals("cancel")));
        var screen=read("com/customcosmetics/gui/CosmeticsScreen");
        assertTrue(pageReferences(code(screen,"buildNavigation")).contains("DAMAGE"));
        for(String name:List.of("damageSplash","damageColor","searchResults"))assertTrue(code(screen,name).stream().filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast).noneMatch(call->call.owner().name().stringValue().startsWith("java/net/")||call.owner().name().stringValue().startsWith("com/customcosmetics/sync/")));
        for (String type : List.of("gui/SettingsCatalog", "gui/SettingsSearch", "gui/SettingsSearch$Index"))
            assertTrue(read("com/customcosmetics/" + type).methods().stream().flatMap(method -> instructions(method).stream())
                .filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast).noneMatch(call ->
                    call.owner().name().stringValue().endsWith("Screen") || call.owner().name().stringValue().contains("ConfigManager")
                        || call.name().stringValue().equals("store")),
                "Searching must not instantiate screens, read feature stores or alter configuration");
    }
    @Test void itemCustomizationHasOneVisibleNameAndOdinFooterIsRemoved()throws Exception{
        for(String type:List.of("com/customcosmetics/gui/CosmeticsScreen","com/customcosmetics/gui/ItemAppearanceScreen")){
            var strings=read(type).methods().stream().flatMap(method->instructions(method).stream()).filter(ConstantInstruction.class::isInstance)
                .map(ConstantInstruction.class::cast).map(ConstantInstruction::constantValue).toList();
            assertTrue(strings.contains("Item Customization"));assertFalse(strings.contains("Item Cosmetics"));
            assertFalse(strings.contains("Based on Odin / odtheking."));
        }
    }
    @Test void accessoryControlsStayLocalAndLayerCannotDrawFirstPersonOrSendGameplayPackets() throws Exception {
        var screen=read("com/customcosmetics/gui/CosmeticsScreen");assertTrue(pageReferences(code(screen,"init")).contains("ACCESSORIES"));
        for(var method:screen.methods())if(method.methodName().stringValue().toLowerCase().contains("accessor"))
            assertTrue(instructions(method).stream().filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast).noneMatch(call->call.owner().name().stringValue().startsWith("com/customcosmetics/sync/")||call.owner().name().stringValue().startsWith("java/net/")));
        var layer=read("com/customcosmetics/features/accessories/AccessoryLayer");assertEquals("net/minecraft/client/renderer/entity/layers/RenderLayer",layer.superclass().orElseThrow().name().stringValue());
        var wardrobe=read("com/customcosmetics/gui/WardrobeScreen");assertTrue(wardrobe.methods().stream().flatMap(method->instructions(method).stream()).filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast).noneMatch(call->call.owner().name().stringValue().startsWith("java/net/")||call.owner().name().stringValue().startsWith("com/customcosmetics/sync/")));
        assertTrue(layer.methods().stream().flatMap(method->instructions(method).stream()).filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast).noneMatch(call->Set.of("send","sendPacket","sendImmediately","setBoundingBox","refreshDimensions").contains(call.name().stringValue())));
    }
    private static final String SCREEN = "com/customcosmetics/gui/CosmeticsScreen";
    @Test void p2pControlsAndLiveProtocolCountsBelongOnlyToSyncDev() throws Exception {
        var screen=read(SCREEN);
        var sync=code(screen,"sync");
        var strings=sync.stream().filter(ConstantInstruction.class::isInstance).map(ConstantInstruction.class::cast).map(ConstantInstruction::constantValue).toList();
        String hint="Leave these settings unchanged unless you use a custom network configuration.";
        assertTrue(strings.containsAll(List.of("Advanced connections","Disable P2P Connections",hint)));
        assertFalse(strings.contains("If you don't know what this button does, leave it off."));
        assertTrue(strings.indexOf("Advanced connections")<strings.indexOf(hint));
        assertTrue(strings.indexOf(hint)<strings.indexOf("Disable P2P Connections"));
        assertTrue(strings.indexOf("Proxy Auth")<strings.indexOf("Disable P2P Connections"));
        assertTrue(strings.indexOf("Disable P2P Connections")<strings.indexOf("P2P UDP Port"));
        assertTrue(sync.stream().filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast).anyMatch(call->call.name().stringValue().equals("connectionStatus")));
        var party=code(screen,"party");
        assertTrue(party.stream().filter(ConstantInstruction.class::isInstance).map(ConstantInstruction.class::cast).noneMatch(value->value.constantValue().equals("P2P Connections")||value.constantValue().equals("Disable P2P Connections")));
        assertTrue(party.stream().filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast).noneMatch(call->Set.of("status","connectionStatus","membershipStatus").contains(call.name().stringValue())));
    }
    @Test void sharingPageSeparatesPartyAndLocationSettings()throws Exception{
        var strings=code(read(SCREEN),"party").stream().filter(ConstantInstruction.class::isInstance).map(ConstantInstruction.class::cast).map(ConstantInstruction::constantValue).toList();
        assertTrue(strings.containsAll(List.of("Party visuals","Party chat","Markers & names","Location sharing","Share My Position")));
        assertTrue(strings.indexOf("Party visuals")<strings.indexOf("Party ESP"));
        assertTrue(strings.indexOf("Party chat")<strings.indexOf("IRC For Party Members"));
        assertTrue(strings.indexOf("Markers & names")<strings.indexOf("Max Display Distance (blocks)"));
        assertTrue(strings.indexOf("Location sharing")<strings.indexOf("Share My Position"));
        var titles=code(read(SCREEN),"pageTitle").stream().filter(ConstantInstruction.class::isInstance).map(ConstantInstruction.class::cast).map(ConstantInstruction::constantValue).toList();
        assertTrue(titles.containsAll(List.of("Sharing","Equipments (Beta)")));assertFalse(titles.contains("Party"));assertFalse(titles.contains("Accessories"));
    }
    @Test void playerSizeEditsStayLocalAndNeverChangeGameplayDimensions() throws Exception {
        var screen=read(SCREEN);
        assertTrue(pageReferences(code(screen,"init")).contains("SIZE"));
        for(var method:screen.methods()) {
            if(!method.methodName().stringValue().contains("playerSize"))continue;
            assertTrue(instructions(method).stream().filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast)
                .noneMatch(call->call.owner().name().stringValue().startsWith("com/customcosmetics/sync/")));
        }
        var renderer=read("com/customcosmetics/features/playersize/PlayerSize");
        assertTrue(renderer.methods().stream().flatMap(method->instructions(method).stream()).filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast)
            .noneMatch(call->Set.of("setDimensions","refreshDimensions","setBoundingBox","send","sendPacket","sendImmediately","setPos").contains(call.name().stringValue())));
    }
    @Test void editingNicknameSegmentsNeverStartsAnUploadOrDownload() throws Exception {
        var editor=read("com/customcosmetics/gui/NicknameSegmentsScreen");
        var calls=editor.methods().stream().flatMap(method->instructions(method).stream()).filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast).toList();
        assertTrue(calls.stream().noneMatch(call->{String owner=call.owner().name().stringValue();return owner.startsWith("java/net/")||owner.startsWith("com/customcosmetics/sync/");}));
        assertTrue(calls.stream().anyMatch(call->call.owner().name().stringValue().equals("com/customcosmetics/config/ConfigManager")&&call.name().stringValue().equals("requestSave")));
    }
    @Test void sidebarHeaderAndPreviewUseRuntimeThemeAndNavigationIsGrouped() throws Exception {
        var screen = read(SCREEN);
        var render = code(screen, "extractRenderState").stream().filter(ConstantInstruction.class::isInstance)
            .map(ConstantInstruction.class::cast).map(ConstantInstruction::constantValue).toList();
        assertTrue(render.contains("CustomCosmetics"));
        assertFalse(render.contains("CUSTOM"));
        assertTrue(code(screen,"extractRenderState").stream().filter(FieldInstruction.class::isInstance)
            .map(FieldInstruction.class::cast).anyMatch(field -> field.owner().name().stringValue().equals("com/customcosmetics/gui/PinkUi") && field.name().stringValue().equals("TEXT")));
        var navigation = code(screen, "buildNavigation").stream().filter(ConstantInstruction.class::isInstance)
            .map(ConstantInstruction.class::cast).map(ConstantInstruction::constantValue).toList();
        assertTrue(navigation.containsAll(List.of("Overview", "APPEARANCE", "SKYBLOCK", "CONNECTIONS")));
        assertTrue(navigation.indexOf("APPEARANCE") < navigation.indexOf("NickTweaks"));
        assertTrue(navigation.indexOf("SKYBLOCK") < navigation.indexOf("Animations"));
        assertTrue(navigation.indexOf("CONNECTIONS") < navigation.indexOf("Sharing"));
        assertTrue(code(screen,"playerPreview").stream().filter(FieldInstruction.class::isInstance)
            .map(FieldInstruction.class::cast).anyMatch(field -> field.owner().name().stringValue().equals("com/customcosmetics/gui/PinkUi") && field.name().stringValue().equals("PINK")));
    }

    @Test
    void syncPageKeepsSyncControlsAndHasNoConnectionEditor() throws Exception {
        var screen = read(SCREEN);
        var strings = code(screen, "sync").stream()
            .filter(ConstantInstruction.class::isInstance).map(ConstantInstruction.class::cast)
            .map(ConstantInstruction::constantValue).filter(String.class::isInstance)
            .map(String.class::cast).collect(Collectors.toSet());
        assertTrue(strings.containsAll(Set.of("Automatic Sync", "Refresh Interval (seconds)", "Notifications", "Sync Now")));
        assertTrue(strings.containsAll(Set.of("Advanced connections","Leave these settings unchanged unless you use a custom network configuration.","Proxy Host","Proxy Port","Proxy Auth","Fallback Direct")));
        assertTrue(strings.stream().noneMatch(Set.of("Server Address", "Server Port", "Use HTTPS", "Save Address")::contains));
        // All GUI actions must leave the saved connection fields alone, including Sync Now.
        assertTrue(screen.methods().stream().flatMap(method -> instructions(method).stream())
            .filter(FieldInstruction.class::isInstance).map(FieldInstruction.class::cast)
            .noneMatch(field -> field.owner().name().stringValue().equals("com/customcosmetics/config/ConfigManager")
                && Set.of("syncServerIp", "syncServerPort", "syncServerHttps").contains(field.name().stringValue())));
    }
    @Test void nicknameOptionsUseCapitalizedLabelsAndBothFeaturesHaveCredit() throws Exception {
        var screen = read(SCREEN);
        for (String method : List.of("nickname","colors","chroma")) {
            var labels = code(screen,method).stream().filter(ConstantInstruction.class::isInstance).map(ConstantInstruction.class::cast)
                .map(ConstantInstruction::constantValue).filter(String.class::isInstance).map(String.class::cast).toList();
            for (String lowercase : List.of("nick changer","bold self nick","custom nick color","gradient editor","lightness","size","speed"))
                assertFalse(labels.contains(lowercase),lowercase);
        }
        assertTrue(screen.methods().stream().flatMap(method -> instructions(method).stream()).filter(ConstantInstruction.class::isInstance).map(ConstantInstruction.class::cast)
            .anyMatch(value -> "Thanks to Baity / raueyhs.".equals(value.constantValue())));
    }

    @Test
    void friendsHasNoPageControlConfigurationOrDistributedClasses() throws Exception {
        var screen = read(SCREEN);
        assertFalse(pageReferences(code(screen, "init")).contains("FRIENDS"));
        assertTrue(pageReferences(code(screen, "init")).containsAll(Set.of("SIZE", "NICK")));
        assertTrue(screen.methods().stream().anyMatch(method->pageReferences(instructions(method)).contains("SYNC")));
        assertFalse(pageReferences(code(screen,"init")).contains("SMOL"));
        assertTrue(code(screen, "smol").stream().filter(ConstantInstruction.class::isInstance)
            .map(ConstantInstruction.class::cast).noneMatch(value -> Set.of("Friends", "Manage Friends").contains(value.constantValue())));
        assertTrue(screen.methods().stream().noneMatch(method -> method.methodName().stringValue().equals("friends")));
        assertTrue(java.util.Arrays.stream(com.customcosmetics.config.ConfigManager.class.getFields())
            .noneMatch(field -> field.getName().toLowerCase().contains("friend")));
        try (var jar = new java.util.jar.JarFile(System.getProperty("customcosmetics.binaryJar"))) {
            assertTrue(jar.stream().noneMatch(entry -> entry.getName().contains("SmolFriend")));
            assertNotNull(jar.getEntry("com/customcosmetics/features/smolpeople/SmolPlayers.class"));
        }
    }

    @Test
    void syncResultsCannotDisplayATitle() throws Exception {
        for (String type : List.of("sync/PresenceSync", "utils/MessageUtils", "compatibility/ClientUiAccess")) {
            var compiled = read("com/customcosmetics/" + type);
            assertTrue(compiled.methods().stream().flatMap(method -> instructions(method).stream())
                .filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast)
                .noneMatch(call -> Set.of("showTitle", "setTitle", "setSubtitle", "setTimes").contains(call.name().stringValue())));
        }
    }

    @Test
    void animationsAndRepositoryLinkRemainInTheSidebar() throws Exception {
        var screen = read(SCREEN);
        assertTrue(pageReferences(code(screen, "buildNavigation")).contains("ANIMATIONS"));
        assertTrue(code(screen, "init").stream().filter(InvokeInstruction.class::isInstance)
            .map(InvokeInstruction.class::cast).anyMatch(call -> call.owner().name().stringValue()
                .equals("com/customcosmetics/gui/RepositoryButton") && call.name().stringValue().equals("<init>")));
        var strings = code(screen, "animations").stream().filter(ConstantInstruction.class::isInstance)
            .map(ConstantInstruction.class::cast).map(ConstantInstruction::constantValue).toList();
        assertFalse(strings.contains("Sync Range"));
        assertTrue(code(screen, "animations").stream().filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast)
            .anyMatch(call -> call.owner().name().stringValue().equals("com/customcosmetics/gui/BlockingStyleDropdown")));
        assertTrue(strings.containsAll(List.of("Sword Blocking", "Apply Lists to Other Players", "Share Blocking", "Item Scale",
            "Position X", "Position Y", "Position Z", "Rotation X", "Rotation Y", "Rotation Z",
            "Swing X", "Swing Y", "Swing Z", "Disable Hand Movement", "Disable Equip Animation",
            "Disable Swing Animation", "Terminator Only", "Swing Speed", "Ignore Haste")));
        var button = read("com/customcosmetics/gui/RepositoryButton");
        assertTrue(code(button, "<clinit>").stream().filter(ConstantInstruction.class::isInstance)
            .map(ConstantInstruction.class::cast).anyMatch(value ->
                "https://github.com/WorksByAI/CustomCosmetics".equals(value.constantValue())));
        assertTrue(button.methods().stream().flatMap(method -> instructions(method).stream())
            .filter(InvokeInstruction.class::isInstance).map(InvokeInstruction.class::cast)
            .anyMatch(call -> call.name().stringValue().equals("openUri")));
    }

    private static Set<String> pageReferences(List<CodeElement> instructions) {
        return instructions.stream().filter(FieldInstruction.class::isInstance).map(FieldInstruction.class::cast)
            .filter(field -> field.owner().name().stringValue().equals(SCREEN + "$Page"))
            .map(field -> field.name().stringValue()).collect(Collectors.toSet());
    }

    private static List<CodeElement> code(ClassModel model, String method) {
        return instructions(model.methods().stream().filter(value -> value.methodName().stringValue().equals(method))
            .findFirst().orElseThrow());
    }

    private static List<CodeElement> instructions(MethodModel method) {
        return method.code().map(code -> code.elementStream().toList()).orElse(List.of());
    }

    private static ClassModel read(String name) throws IOException {
        try (var input = CosmeticsScreenTest.class.getClassLoader().getResourceAsStream(name + ".class")) {
            assertNotNull(input);
            return ClassFile.of().parse(input.readAllBytes());
        }
    }
}
