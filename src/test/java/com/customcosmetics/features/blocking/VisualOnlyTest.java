package com.customcosmetics.features.blocking;

import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Guard the user's critical constraint in compiled code, without launching Minecraft. */
class VisualOnlyTest {
    @Test void observingVanillaSwingsCannotCancelOrChangeTheirResult() throws Exception {
        for (String type : List.of("Legacy", "Modern")) {
            try (var in = getClass().getClassLoader().getResourceAsStream("com/customcosmetics/mixin/SwingAnimationMixin$" + type + ".class")) {
                assertNotNull(in);
                for (var method : ClassFile.of().parse(in.readAllBytes()).methods()) {
                    for (var element : method.code().map(c -> c.elementStream().toList()).orElse(List.of())) {
                        if (element instanceof InvokeInstruction call) {
                            assertFalse(Set.of("cancel", "setReturnValue").contains(call.name().stringValue()),
                                "A visual observer must preserve the original swing call");
                        }
                    }
                }
            }
        }
    }
    @Test void blockingCodeCannotSendGamePacketsOrMutateGameInputOrItemUse() throws Exception {
        for (String type : List.of("features/blocking/SwordBlocking", "features/blocking/SwordItems", "features/blocking/BlockingPose",
                "features/blocking/VisualBlockhit", "features/blocking/BlockhitClock", "features/blocking/BlockingStyle", "sync/BlockingSync", "sync/BlockingTransport",
                "compatibility/BlockingRenderAccess", "mixin/SwordBlockingMixin", "mixin/SwordBlockingMixin$FirstPerson",
                "compatibility/SwingAccess", "features/animations/SwingTimeline",
                "mixin/SwingAnimationMixin$Legacy", "mixin/SwingAnimationMixin$Modern",
                "mixin/SwordBlockingMixin$FirstPersonModern", "mixin/SwordBlockingMixin$ThirdPerson",
                "mixin/SwordBlockingMixin$ModernEquipment", "mixin/SwordBlockingMixin$ThirdPersonItem",
                "features/animations/ViewModelAnimations", "features/animations/SwingTimeline",
                "sync/UdpPresenceClient", "sync/UdpPacket")) {
            try (var in = getClass().getClassLoader().getResourceAsStream("com/customcosmetics/" + type + ".class")) {
                assertNotNull(in);
                var model = ClassFile.of().parse(in.readAllBytes());
                for (var method : model.methods()) {
                    for (var element : method.code().map(c -> c.elementStream().toList()).orElse(List.of())) {
                        if (element instanceof InvokeInstruction call) {
                            String owner = call.owner().name().stringValue(), name = call.name().stringValue();
                            assertFalse(owner.startsWith("net/minecraft/network/"), owner + " " + name);
                            assertTrue(!owner.startsWith("net/minecraft/client/multiplayer/") ||
                                owner.equals("net/minecraft/client/multiplayer/ClientLevel") && name.equals("getEntity"), owner + " " + name);
                            assertFalse(Set.of("startUsingItem", "stopUsingItem", "releaseUsingItem", "useItem", "useItemOn",
                                "attack", "startDestroyBlock", "continueDestroyBlock", "stopDestroyBlock",
                                "sendPacket", "getConnection", "setDown", "setKey", "setItemInHand").contains(name)
                                || name.equals("send") && !owner.equals("java/net/DatagramSocket")
                                    && !owner.equals("com/customcosmetics/sync/UdpPresenceClient"), owner + " " + name);
                            assertFalse(owner.startsWith("net/minecraft/") && name.equals("swing"), "A visual animation must not invoke the player's packet-producing swing");
                        }
                        if (element instanceof FieldInstruction field && field.opcode().name().startsWith("PUT")) {
                            String owner = field.owner().name().stringValue();
                            assertTrue(!owner.startsWith("net/minecraft/") ||
                                owner.equals("net/minecraft/client/model/geom/ModelPart") &&
                                Set.of("xRot", "yRot", "zRot").contains(field.name().stringValue()),
                                "Only renderer ModelPart rotations may be written");
                        }
                    }
                }
            }
        }
    }

    @Test void worldIdentityUsesOnlyOfficialApiEventsAndNeverSendsCommands() throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("customcosmetics.projectDir"));
        for (String file : List.of("PresenceWorld.java", "HypixelLocationBridge.java", "WorldScope.java")) {
            String source = java.nio.file.Files.readString(root.resolve("src/main/java/com/customcosmetics/features/animations/" + file));
            assertFalse(source.contains("locraw") || source.contains("sendCommand") || source.contains("sendPacket("));
        }
        String bridge = java.nio.file.Files.readString(root.resolve("src/main/java/com/customcosmetics/features/animations/HypixelLocationBridge.java"));
        assertTrue(bridge.contains("ClientboundHelloPacket") && bridge.contains("ClientboundLocationPacket") && bridge.contains("subscribeToEventPacket"));
    }

    @Test void runtimeReferencesCannotLoadAnotherClassInTheMixinPackage() throws Exception {
        try (var jar = new java.util.jar.JarFile(System.getProperty("customcosmetics.binaryJar"))) {
            for (var entry : jar.stream().filter(e -> e.getName().startsWith("com/customcosmetics/") && e.getName().endsWith(".class")).toList()) {
                String self = entry.getName().substring(0, entry.getName().length() - 6);
                try (var in = jar.getInputStream(entry)) {
                    for (var method : ClassFile.of().parse(in.readAllBytes()).methods()) {
                        for (var element : method.code().map(c -> c.elementStream().toList()).orElse(List.of())) {
                            String owner = null;
                            if (element instanceof InvokeInstruction call) {
                                owner = call.owner().name().stringValue();
                            } else if (element instanceof FieldInstruction field) {
                                owner = field.owner().name().stringValue();
                            }
                            if (owner != null) {
                                // Self references are remapped to the target; other Mixin-package owners are illegal.
                                assertTrue(!owner.startsWith("com/customcosmetics/mixin/") || owner.equals(self),
                                    "Illegal runtime helper in " + self + ": " + owner);
                            }
                        }
                    }
                }
            }
        }
    }
}
