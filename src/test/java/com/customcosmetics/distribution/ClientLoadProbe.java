package com.customcosmetics.distribution;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.impl.launch.knot.Knot;

/** Separate-process Fabric/Mixin linkage check. Does not launch a world or initialize graphics. */
public final class ClientLoadProbe {
    public static void main(String[] args) throws Exception {
        var loader = new Knot(EnvType.CLIENT).init(new String[] {"--version", System.getProperty("fabric.gameVersion"),
            "--gameDir", System.getProperty("customcosmetics.probeDir"), "--accessToken", "0"});
        String firstPersonRenderer = System.getProperty("fabric.gameVersion").startsWith("26.3")
            ? "net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer" : "net.minecraft.client.renderer.ItemInHandRenderer";
        for (String type : new String[] {
            "com.customcosmetics.features.damage.DamageSplash", "com.customcosmetics.features.damage.DamageText", "com.customcosmetics.features.damage.DamageAnimation",
            "com.customcosmetics.gui.SettingsSearch", "net.minecraft.client.multiplayer.ClientPacketListener", "net.minecraft.client.renderer.entity.state.EntityRenderState",
            "com.customcosmetics.features.accessories.AccessoryLayer", "com.customcosmetics.features.accessories.AccessoryRenderState", "net.minecraft.client.renderer.entity.state.AvatarRenderState", "net.minecraft.client.renderer.entity.EntityRenderer",
            "com.customcosmetics.features.accessories.AccessoryMeshes", "com.customcosmetics.features.accessories.DetailedAccessoryMeshes", "com.customcosmetics.features.accessories.MiniMePet", "com.customcosmetics.compatibility.MiniMeRenderAccess", "com.customcosmetics.gui.WardrobeScreen", "net.minecraft.client.renderer.entity.LivingEntityRenderer",
            "net.minecraft.client.Minecraft", firstPersonRenderer, "net.minecraft.client.gui.Font",
            "net.minecraft.client.renderer.block.ModelBlockRenderer", "net.minecraft.client.renderer.chunk.VisGraph",
            "net.minecraft.client.renderer.block.BlockModelLighter",
            "net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher",
            "net.fabricmc.fabric.impl.client.indigo.renderer.render.AltModelBlockRendererImpl",
            "net.fabricmc.fabric.impl.client.indigo.renderer.aocalc.AoCalculator",
            "net.minecraft.client.gui.Font$PreparedTextBuilder", "net.minecraft.network.chat.Style",
            "com.customcosmetics.features.nickname.GradientGlyph", "com.customcosmetics.features.nickname.NicknameGlyphEffect",
            "net.minecraft.client.model.player.PlayerModel", "net.minecraft.client.gui.components.AbstractWidget",
            "net.minecraft.client.renderer.entity.player.AvatarRenderer",
            "com.customcosmetics.features.playersize.PlayerSize", "com.customcosmetics.features.playersize.PlayerSizeSettings",
            "net.minecraft.world.entity.LivingEntity",
            "com.customcosmetics.update.UpdateChecker", "com.customcosmetics.update.GitHubUpdateCheck",
            "com.customcosmetics.gui.RepositoryButton", "com.customcosmetics.gui.CosmeticsScreen",
            "com.customcosmetics.gui.BlockingPreview", "com.customcosmetics.gui.BlockingAdjustmentScreen",
            "com.customcosmetics.gui.CapeScreen", "com.customcosmetics.gui.CapeCropScreen", "com.customcosmetics.gui.CapePreview", "com.customcosmetics.gui.CapeFileScreen",
            "com.customcosmetics.features.cape.CapeCosmetics", "com.customcosmetics.sync.CapeSync", "net.minecraft.client.player.AbstractClientPlayer",
            "com.customcosmetics.features.blocking.BlockingAdjustment",
            "net.minecraft.client.renderer.entity.EntityRenderDispatcher", "net.minecraft.client.gui.render.pip.PictureInPictureRenderer",
            "com.customcosmetics.gui.BlockingItemsScreen", "com.customcosmetics.gui.BlockingModeDropdown", "com.customcosmetics.gui.BlockingStyleDropdown",
            "com.customcosmetics.features.blocking.SwordItems", "com.customcosmetics.features.blocking.SwordBlocking",
            "com.customcosmetics.features.blocking.VisualBlockhit",
            "com.customcosmetics.sync.BlockingSync", "com.customcosmetics.sync.WebSocketPresenceClient",
            "com.customcosmetics.sync.RealtimeSync", "com.customcosmetics.sync.RealtimeClient", "com.customcosmetics.sync.RealtimeRoute",
            "com.customcosmetics.sync.PartyPeerClient", "com.customcosmetics.sync.RealtimeConnections",
            "com.customcosmetics.config.PlayerBlocklist", "com.customcosmetics.sync.PlayerBlocks", "com.customcosmetics.sync.PlayerBlockCommands",
            "com.customcosmetics.sync.SyncDebug", "com.customcosmetics.sync.DebugSession", "com.customcosmetics.compatibility.SodiumGlyphWriter",
            "com.customcosmetics.features.party.PartyFeatures", "com.customcosmetics.features.party.PartyHighlight", "net.minecraft.client.renderer.LevelRenderer",
            "com.customcosmetics.features.party.PartyConnectionNotice", "com.customcosmetics.features.party.PartyNametagScale",
            "com.customcosmetics.features.party.PartyTracer", "com.customcosmetics.features.party.PartyPositionTrack",
            "com.customcosmetics.sync.SyncCommands", "com.customcosmetics.client.CustomCosmeticsClient",
            "com.customcosmetics.features.party.PartyGeometry", "com.customcosmetics.sync.PartyClient", "com.customcosmetics.sync.PartySync",
            "com.customcosmetics.compatibility.PartyRenderPipeline",
            "com.customcosmetics.config.RemoteServerConfig", "com.customcosmetics.config.NicknameLimit",
            "net.minecraft.world.item.ItemStack", "net.minecraft.client.renderer.item.ItemModelResolver", "net.minecraft.client.resources.model.ModelManager",
            "com.customcosmetics.features.items.ItemCosmetics", "com.customcosmetics.features.items.ItemNameText",
            "net.minecraft.client.renderer.entity.layers.ItemInHandLayer",
            "com.customcosmetics.features.animations.PresenceWorld", "com.customcosmetics.features.animations.ViewModelAnimations"
        }) {
            var loaded = Class.forName(type, false, loader);
            if(type.equals("net.minecraft.client.multiplayer.ClientPacketListener")&&java.util.Arrays.stream(loaded.getDeclaredMethods()).noneMatch(m->m.getName().contains("customcosmetics$damageMetadata")))throw new AssertionError("Damage metadata observer is missing");
            if(type.equals("net.minecraft.client.renderer.entity.state.EntityRenderState")&&!java.util.Arrays.stream(loaded.getInterfaces()).anyMatch(i->i.getName().endsWith("DamageRenderState")))throw new AssertionError("Damage name state is missing");
            if(type.endsWith("PartyRenderPipeline")){
                Object partyType=loaded.getMethod("type").invoke(null);
                if(partyType==null)throw new AssertionError("Party render pipeline is missing");
            }
            if(type.endsWith("AvatarRenderState")&&!java.util.Arrays.stream(loaded.getInterfaces()).anyMatch(i->i.getName().endsWith("AccessoryRenderState")))throw new AssertionError("Accessory state mixin missing");
            if(type.equals("net.minecraft.client.renderer.entity.EntityRenderer")&&java.util.Arrays.stream(loaded.getDeclaredMethods()).noneMatch(m->m.getName().contains("customcosmetics$extractAccessories")))throw new AssertionError("Accessory snapshot hook missing");
            if(type.equals("net.minecraft.client.renderer.entity.LivingEntityRenderer")&&java.util.Arrays.stream(loaded.getDeclaredMethods()).noneMatch(m->m.getName().contains("customcosmetics$expandCosmeticBounds")))throw new AssertionError("Accessory culling compatibility hook missing");
            if(type.equals("net.minecraft.client.renderer.entity.LivingEntityRenderer")&&java.util.Arrays.stream(loaded.getDeclaredMethods()).noneMatch(m->m.getName().contains("customcosmetics$captureAppearance")))throw new AssertionError("Original-appearance chams hook missing");
            if(type.equals("net.minecraft.client.renderer.LevelRenderer")&&java.util.Arrays.stream(loaded.getDeclaredMethods()).noneMatch(m->m.getName().contains("customcosmetics$finishChams")))throw new AssertionError("Chams composite hook missing");
            loaded.getDeclaredMethods();
            if(type.equals("net.minecraft.client.renderer.entity.player.AvatarRenderer") && java.util.Arrays.stream(loaded.getDeclaredMethods())
                    .noneMatch(method->method.getName().contains("customcosmetics$additionalScale")))
                throw new AssertionError("Player Size render hook was not applied");
            if (type.equals("net.minecraft.client.gui.Font$PreparedTextBuilder") && java.util.Arrays.stream(loaded.getDeclaredMethods())
                    .noneMatch(method -> method.getName().contains("customcosmetics$gradientGlyph")))
                throw new AssertionError("Smooth nickname glyph rendering was not applied");
            if (type.equals("net.minecraft.network.chat.Style") && java.util.Arrays.stream(loaded.getDeclaredMethods())
                    .noneMatch(method -> method.getName().equals("customcosmetics$gradient")))
                throw new AssertionError("Render-only nickname styles were not applied");
            if (type.equals("net.minecraft.world.entity.LivingEntity")) for (String hook : new String[]{"observeSwing", "advanceSwing"})
                if (java.util.Arrays.stream(loaded.getDeclaredMethods()).noneMatch(method -> method.getName().contains("customcosmetics$" + hook)))
                    throw new AssertionError("Visual swing observer was not applied: " + hook);
            if (type.equals("net.minecraft.world.item.ItemStack") && java.util.Arrays.stream(loaded.getDeclaredMethods()).noneMatch(method -> method.getName().contains("customcosmetics$itemName")))
                throw new AssertionError("Local item name rendering was not applied");
            if (type.equals("net.minecraft.client.renderer.item.ItemModelResolver") && java.util.Arrays.stream(loaded.getDeclaredMethods()).noneMatch(method -> method.getName().contains("customcosmetics$itemModel")))
                throw new AssertionError("Local item model rendering was not applied");
            if (type.equals(firstPersonRenderer) ||
                    type.equals("net.minecraft.client.model.player.PlayerModel")) {
                if (java.util.Arrays.stream(loaded.getDeclaredMethods())
                        .noneMatch(method -> method.getName().contains("customcosmetics$blockPose")))
                    throw new AssertionError("Blocking Mixin not applied to " + type);
            }
        }
        checkGameReferences(loader,"com/customcosmetics/features/playersize/PlayerSize");
        checkGameReferences(loader,"com/customcosmetics/features/damage/DamageSplash");
        checkGameReferences(loader,"com/customcosmetics/features/party/SharingNametag");
        checkGameReferences(loader,"com/customcosmetics/features/party/SharingChams");
        checkGameReferences(loader,"com/customcosmetics/compatibility/ChamsRenderAccess");
        checkGameReferences(loader,"com/customcosmetics/gui/CosmeticsScreen");
        checkGameReferences(loader,"com/customcosmetics/features/map/MapEditor");
        checkGameReferences(loader,"com/customcosmetics/features/map/MaterialReplacement");
        checkGameReferences(loader,"com/customcosmetics/features/map/MaterialReplacement$Job");
        checkGameReferences(loader,"com/customcosmetics/gui/MapReplaceScreen");
        checkGameReferences(loader,"com/customcosmetics/features/map/GhostBlocks");
        checkGameReferences(loader,"com/customcosmetics/features/map/DungeonSidebar");
        for(String type:new String[]{"features/map/MapEditor$1","features/map/DungeonSidebarReader","features/map/DungeonRooms","features/map/RoomCores","features/map/RoomLayouts","features/map/FmeImport","features/map/LegacyMaterials","gui/FmeImportScreen","features/map/MapEditorUi","gui/MapBlockScreen","gui/MapBlockScreen$Cell"})checkGameReferences(loader,"com/customcosmetics/"+type);
        checkGameReferences(loader,"com/customcosmetics/features/map/Freecam");
        checkGameReferences(loader,"com/customcosmetics/compatibility/SidebarAccess");
        checkGameReferences(loader,"com/customcosmetics/features/party/PartyHighlight");
        checkGameReferences(loader,"com/customcosmetics/features/party/PartyGeometry");
        checkGameReferences(loader,"com/customcosmetics/features/party/PartyTracer");
        checkGameReferences(loader,"com/customcosmetics/features/party/PartyPositionTrack");
        checkGameReferences(loader,"com/customcosmetics/features/party/PartyFeatures");
        checkGameReferences(loader,"com/customcosmetics/features/party/PartyConnectionNotice");
        if(!System.getProperty("fabric.gameVersion").startsWith("26.3")){
            var target=Class.forName("net.minecraft.client.renderer.rendertype.OutputTarget",false,loader);
            if(java.util.Arrays.stream(target.getDeclaredMethods()).noneMatch(m->m.getName().contains("customcosmetics$appearanceTarget")))throw new AssertionError("Chams material target hook missing");
        }
        checkGameReferences(loader,"com/customcosmetics/sync/SyncCommands");
        checkGameReferences(loader,"com/customcosmetics/sync/PartySync");
        checkGameReferences(loader,"com/customcosmetics/features/accessories/AccessoryLayer");
        checkGameReferences(loader,"com/customcosmetics/features/accessories/AccessoryMeshes");
        checkGameReferences(loader,"com/customcosmetics/features/accessories/DetailedAccessoryMeshes");
        checkGameReferences(loader,"com/customcosmetics/features/accessories/AccessoryModelFit");
        checkGameReferences(loader,"com/customcosmetics/features/accessories/GarmentMeshes");
        checkGameReferences(loader,"com/customcosmetics/features/accessories/AccessoryModelBake");
        checkGameReferences(loader,"com/customcosmetics/gui/WardrobeScreen");
        Class.forName("com.customcosmetics.compatibility.AccessoryRenderAccess",true,loader).getMethod("validate",Class.class)
            .invoke(null,Class.forName("net.minecraft.client.renderer.OrderedSubmitNodeCollector",false,loader));
        Class.forName("com.customcosmetics.compatibility.NativeFileDialogs",true,loader).getMethod("validate",boolean.class)
            .invoke(null,System.getProperty("fabric.gameVersion").startsWith("26.3"));
        checkGameReferences(loader,"com/customcosmetics/gui/CapeScreen");
        checkGameReferences(loader,"com/customcosmetics/gui/CapePreview");
        Class.forName("com.customcosmetics.compatibility.CapePreviewSwing",true,loader).getMethod("validate",Class.class)
            .invoke(null,Class.forName("net.minecraft.client.renderer.entity.state.AvatarRenderState",false,loader));
        checkTransformedBlockingCalls(loader, firstPersonRenderer);
        checkHandheldDisplayBasis(loader);
        for (String type : new String[] {"features/blocking/SwordBlocking", "features/blocking/SwordItems", "features/blocking/BlockingPose",
            "features/blocking/VisualBlockhit", "features/blocking/BlockingItems",
            "gui/BlockingItemsScreen", "gui/BlockingItemsScreen$WeaponChoice", "gui/BlockingModeDropdown", "gui/BlockingStyleDropdown",
            "sync/BlockingSync", "gui/RepositoryButton", "mixin/SwordBlockingMixin", "mixin/SwordBlockingMixin$FirstPerson",
            "mixin/SwordBlockingMixin$FirstPersonModern", "compatibility/BlockingRenderAccess",
            "mixin/SwordBlockingMixin$ThirdPerson", "mixin/SwordBlockingMixin$ThirdPersonItem",
            "features/animations/PresenceWorld", "features/animations/ViewModelAnimations",
            "update/UpdateChecker", "update/GitHubUpdateCheck", "features/items/ItemCosmetics", "features/items/ItemNameText",
            "gui/ItemAppearanceScreen", "gui/ItemChoiceScreen", "gui/ItemChoiceScreen$Row",
            "mixin/ItemAppearanceMixin$Names", "mixin/ItemAppearanceMixin$Models", "utils/NickRenderUtils",
            "features/nickname/GradientGlyph", "features/nickname/GradientGlyph$GradientVertices",
            "features/nickname/NicknameText", "features/nickname/ProcessedNicknameText",
            "compatibility/ModernUiNicknameText", "compatibility/ModernUiNicknameText$Palette", "compatibility/ModernUiNicknameText$Buffers",
            "mixin/ModernUiNicknameMixin$GuiText", "mixin/ModernUiNicknameMixin$PreparedText", "mixin/ModernUiNicknameMixin$GlyphRun",
            "mixin/ModernUiNicknameMixin$Effects", "mixin/ModernUiNicknameMixin$Layout", "mixin/ModernUiNicknameMixin$WorldText", "mixin/ModernUiNicknameMixin$Width",
            "features/nickname/NicknameGlyphEffect", "mixin/NicknameGradientMixin", "mixin/NicknameStyleMixin"}) checkGameReferences(loader, "com/customcosmetics/" + type);
        Class.forName("com.customcosmetics.compatibility.RepositoryLinks", true, loader);
        Class.forName("com.customcosmetics.compatibility.SwingAccess", true, loader)
            .getMethod("validate", Class.class).invoke(null, Class.forName("net.minecraft.client.player.LocalPlayer", false, loader));
        Class.forName("com.customcosmetics.compatibility.SwingAccess", true, loader)
            .getMethod("validateItem", Class.class).invoke(null, Class.forName("net.minecraft.world.item.ItemStack", false, loader));
        if (System.getProperty("fabric.gameVersion").startsWith("26.3")) {
            Class.forName("net.minecraft.client.player.FirstPersonHandsAndItems", false, loader).getDeclaredMethods();
            checkGameReferences(loader, "com/customcosmetics/mixin/SwordBlockingMixin$ModernEquipment");
            var playerState = Class.forName("net.minecraft.client.renderer.state.level.PlayerRenderState", false, loader);
            if (!playerState.getField("avatarRenderState").getType().getName().endsWith("AvatarRenderState"))
                throw new AssertionError("Invalid avatar snapshot field");
            var hands = Class.forName("net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState", false, loader);
            for (String field : new String[] {"mainHandRenderState", "offHandRenderState"})
                if (!hands.getField(field).getType().getName().endsWith("ItemStackRenderState"))
                    throw new AssertionError("Invalid item snapshot field " + field);
        }
        System.out.println("CLIENT_LOAD_PROBE_OK " + System.getProperty("fabric.gameVersion"));
    }

    private static void checkHandheldDisplayBasis(ClassLoader loader) throws Exception {
        try (var input = loader.getResourceAsStream("assets/minecraft/models/item/handheld.json")) {
            if (input == null) throw new AssertionError("Missing native handheld model");
            var model = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(input, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            var display = model.getAsJsonObject("display");
            for (int side : new int[] {1, -1}) {
                var hand = display.getAsJsonObject(side > 0 ? "firstperson_righthand" : "firstperson_lefthand");
                float[][] expected = {{0, -90f * side, 25f * side}, {1.13f, 3.2f, 1.13f}, {.68f, .68f, .68f}};
                String[] keys = {"rotation", "translation", "scale"};
                for (int group = 0; group < keys.length; group++) {
                    var values = hand.getAsJsonArray(keys[group]);
                    for (int axis = 0; axis < 3; axis++)
                        if (Math.abs(values.get(axis).getAsFloat() - expected[group][axis]) > .00001f)
                            throw new AssertionError("Native handheld display changed: " + keys[group]);
                }
            }
        }
        System.out.println("HANDHELD_BASIS_OK " + System.getProperty("fabric.gameVersion"));
    }

    /** Resolve the calls that actually remain after Mixin injection, then run the pose helper. */
    private static void checkTransformedBlockingCalls(ClassLoader loader, String renderer) throws Exception {
        var output = java.nio.file.Path.of(".mixin.out", "class", renderer.replace('.', '/') + ".class");
        if (!java.nio.file.Files.isRegularFile(output)) throw new AssertionError("Missing transformed renderer " + output);
        var model = java.lang.classfile.ClassFile.of().parse(java.nio.file.Files.readAllBytes(output));
        int poses = 0;
        for (var method : model.methods()) {
            if (!method.methodName().stringValue().contains("customcosmetics$blockPose")) continue;
            for (var instruction : method.code().orElseThrow().elementStream().toList()) {
                if (instruction instanceof java.lang.classfile.instruction.InvokeInstruction call &&
                        call.owner().name().stringValue().startsWith("com/customcosmetics/")) {
                    // A helper left in the configured Mixin package fails here under the real Fabric loader.
                    var owner = Class.forName(call.owner().name().stringValue().replace('/', '.'), false, loader);
                    var signature = java.lang.invoke.MethodType.fromMethodDescriptorString(call.type().stringValue(), loader);
                    if (!hasMethod(owner, call.name().stringValue(), signature))
                        throw new AssertionError("Invalid injected helper " + call);
                    if (call.owner().name().stringValue().equals("com/customcosmetics/features/blocking/BlockingPose")
                            && call.name().stringValue().equals("apply")) {
                        var transform = owner.getDeclaredMethod(call.name().stringValue(), signature.parameterArray());
                        var setting = Class.forName("com.customcosmetics.config.ConfigManager", true, loader).getField("swordBlockingStyle");
                        var original = setting.get(null);
                        try {
                            for (String style : new String[]{"default", "jigsaw"}) {
                                setting.set(null, style);
                                for (int side : new int[]{-1, 1}) for (float phase : new float[]{0, .25f, .8f, 1}) {
                                    var stack = signature.parameterType(0).getDeclaredConstructor().newInstance();
                                    transform.invoke(null, stack, side, phase, 0f);
                                }
                            }
                        } finally { setting.set(null, original); }
                        poses++;
                    }
                }
            }
        }
        if (poses == 0) throw new AssertionError("No blocking pose helper was executed");
        System.out.println("TRANSFORMED_BLOCKING_POSE_OK " + System.getProperty("fabric.gameVersion"));
    }

    private static void checkGameReferences(ClassLoader loader, String type) throws Exception {
        try (var stream = loader.getResourceAsStream(type + ".class")) {
            if (stream == null) throw new AssertionError("Missing probe input " + type);
            var model = java.lang.classfile.ClassFile.of().parse(stream.readAllBytes());
            for (var method : model.methods()) {
                for (var instruction : method.code().map(code -> code.elementStream().toList()).orElse(java.util.List.of())) {
                    if (instruction instanceof java.lang.classfile.instruction.InvokeInstruction call &&
                        (call.owner().name().stringValue().startsWith("net/minecraft/") ||
                         call.owner().name().stringValue().startsWith("com/mojang/blaze3d/"))) {
                        String name = call.name().stringValue();
                        Class<?> owner = Class.forName(call.owner().name().stringValue().replace('/', '.'), false, loader);
                        var signature = java.lang.invoke.MethodType.fromMethodDescriptorString(call.type().stringValue(), loader);
                        if (name.equals("<init>")) owner.getDeclaredConstructor(signature.parameterArray());
                        else if (!hasMethod(owner, name, signature)) throw new AssertionError("Missing game method " + call);
                    }
                    if (instruction instanceof java.lang.classfile.instruction.FieldInstruction field &&
                        field.owner().name().stringValue().startsWith("net/minecraft/")) {
                        Class<?> owner = Class.forName(field.owner().name().stringValue().replace('/', '.'), false, loader);
                        if (!hasField(owner, field.name().stringValue(), field.type().stringValue()))
                            throw new AssertionError("Missing game field " + field);
                    }
                }
            }
        }
    }

    private static boolean hasMethod(Class<?> type, String name, java.lang.invoke.MethodType signature) {
        if (type == null) return false;
        // invokeinterface may resolve a public Object method even though reflection omits it.
        if (type.isInterface() && java.util.Arrays.stream(Object.class.getMethods()).anyMatch(method ->
                !java.lang.reflect.Modifier.isStatic(method.getModifiers()) && method.getName().equals(name)
                && method.getReturnType().equals(signature.returnType())
                && java.util.Arrays.equals(method.getParameterTypes(), signature.parameterArray()))) return true;
        for (var method : type.getDeclaredMethods())
            if (method.getName().equals(name) && method.getReturnType().equals(signature.returnType()) &&
                java.util.Arrays.equals(method.getParameterTypes(), signature.parameterArray())) return true;
        if (hasMethod(type.getSuperclass(), name, signature)) return true;
        return java.util.Arrays.stream(type.getInterfaces()).anyMatch(iface -> hasMethod(iface, name, signature));
    }

    private static boolean hasField(Class<?> type, String name, String descriptor) {
        if (type == null) return false;
        for (var field : type.getDeclaredFields())
            if (field.getName().equals(name) && field.getType().descriptorString().equals(descriptor)) return true;
        return hasField(type.getSuperclass(), name, descriptor) || java.util.Arrays.stream(type.getInterfaces())
            .anyMatch(iface -> hasField(iface, name, descriptor));
    }
}
