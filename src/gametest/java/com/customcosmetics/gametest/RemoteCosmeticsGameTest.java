// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.blocking.BlockHand;
import com.customcosmetics.features.blocking.SwordBlocking;
import com.customcosmetics.features.smolpeople.SmolPlayers;
import com.customcosmetics.sync.*;
import com.customcosmetics.utils.NickRenderUtils;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.nio.file.*;
import java.util.*;

final class RemoteCosmeticsGameTest {
    static void check(ClientGameTestContext context) throws ReflectiveOperationException {
        UUID alice = UUID.fromString("12345678-1234-4234-8234-123456789abc");
        UUID bob = UUID.fromString("22345678-1234-4234-8234-123456789abc");
        String publicData;
        try { publicData = Files.readString(Path.of(System.getProperty("customcosmetics.testProjectDir"), "build/audit/sync-integration-public.json")); }
        catch (java.io.IOException error) { throw new AssertionError("Run the authenticated two-client integration test first", error); }
        context.runOnClient(client -> {
            ConfigManager.syncServerIp = ""; ConfigManager.syncEnabled = true; ConfigManager.swordBlockingSyncEnabled = true;
            PresenceSync.onServerAddressChanged();
            var apply = PresenceSync.class.getDeclaredMethod("applyPayload", String.class, boolean.class);
            apply.setAccessible(true); apply.invoke(null, publicData, false);
            // Use the local skin type so all three players share exactly the same renderer/model.
            // Different wide/slim models can mask a leak into the first-person sleeve.
            var localSkin = client.player.getSkin();
            var a = new RemotePlayer(client.level, new com.mojang.authlib.GameProfile(alice, "Alice")) {
                @Override public net.minecraft.world.entity.player.PlayerSkin getSkin() { return localSkin; }
            };
            var b = new RemotePlayer(client.level, new com.mojang.authlib.GameProfile(bob, "Bob")) {
                @Override public net.minecraft.world.entity.player.PlayerSkin getSkin() { return localSkin; }
            };
            a.setId(-1001); b.setId(-1002);
            a.setPos(client.player.getX() - .8, client.player.getY(), client.player.getZ() + 3);
            b.setPos(client.player.getX() + .8, client.player.getY(), client.player.getZ() + 3);
            a.setYRot(180); b.setYRot(180);
            a.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BLAZE_ROD));
            b.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.IRON_SWORD));
            client.level.addEntity(a); client.level.addEntity(b);
            var poses = BlockingSync.class.getDeclaredField("remote"); poses.setAccessible(true);
            long expires = System.nanoTime() / 1_000_000 + 120000;
            poses.set(null, Map.of(alice, new BlockingTransport.Pose(BlockHand.MAIN, expires, "skyblock:HYPERION"),
                bob, new BlockingTransport.Pose(BlockHand.OFF, expires, "skyblock:ASPECT_OF_THE_VOID")));
            ConfigManager.swordBlockingListsApplyToOthers = false;
            ConfigManager.swordBlockingListMode = "whitelist";
            ConfigManager.swordBlockingWhitelist = new String[]{"skyblock:HYPERION"};
            if (!SmolPlayers.shouldApplySmolTo(a.getId()) || !SmolPlayers.shouldApplySmolTo(b.getId()))
                throw new AssertionError("Received small-player preferences were not applied to remote entities");
            String names = NickRenderUtils.handleFormattedText(net.minecraft.network.chat.Component.literal("Alice and Bob")).getString();
            if (!names.contains("Cherry") || !names.contains("Blossom")) throw new AssertionError("Received nicknames were not displayed: " + names);
            if (SwordBlocking.hand(a) != BlockHand.MAIN || SwordBlocking.hand(b) != BlockHand.OFF)
                throw new AssertionError("Received blocking hands were not applied to remote entities");
            RenderAssertions.remoteModels.set(0);
            RenderAssertions.remoteSleeveMismatch.set(0);
            client.player.setYRot(0); client.player.setXRot(0);
        });
        context.waitTicks(10); context.takeScreenshot("remote-nick-smol-blocking");
        if (RenderAssertions.remoteModels.get() < 2) throw new AssertionError("Remote small-player/blocking models were not rendered; seen=" + RenderAssertions.remoteSeen);
        checkFirstPersonSleeves(context);
        context.runOnClient(client -> {
            ConfigManager.swordBlockingListsApplyToOthers = true;
            var a = (RemotePlayer)client.level.getEntity(-1001); var b = (RemotePlayer)client.level.getEntity(-1002);
            if (SwordBlocking.hand(a) != BlockHand.MAIN || SwordBlocking.hand(b) != BlockHand.NONE)
                throw new AssertionError("Hyperion-only remote whitelist did not filter AOTV using the transmitted ID");
            if (!SmolPlayers.shouldApplySmolTo(a.getId()) || !SmolPlayers.shouldApplySmolTo(b.getId()))
                throw new AssertionError("Blocking filters affected remote small-player cosmetics");
        });
        context.waitTicks(5); context.takeScreenshot("remote-whitelist-hyperion-only");
        context.runOnClient(client -> {
            var a = (RemotePlayer)client.level.getEntity(-1001); var b = (RemotePlayer)client.level.getEntity(-1002);
            ConfigManager.swordBlockingWhitelist = new String[]{"skyblock:ASPECT_OF_THE_VOID"};
            if (SwordBlocking.hand(a) != BlockHand.NONE || SwordBlocking.hand(b) != BlockHand.OFF)
                throw new AssertionError("Changing the receiver whitelist did not immediately update both hands");
            ConfigManager.swordBlockingListMode = "blacklist";
            ConfigManager.swordBlockingBlacklist = new String[]{"skyblock:ASPECT_OF_THE_VOID"};
            if (SwordBlocking.hand(a) != BlockHand.MAIN || SwordBlocking.hand(b) != BlockHand.NONE)
                throw new AssertionError("Receiver blacklist failed");
            ConfigManager.swordBlockingListsApplyToOthers = false;
            if (SwordBlocking.hand(b) != BlockHand.OFF) throw new AssertionError("Turning filtering off did not restore remote blocking");
            ConfigManager.swordBlockingListsApplyToOthers = true; ConfigManager.swordBlockingListMode = "off";
            if (SwordBlocking.hand(b) != BlockHand.OFF) throw new AssertionError("Off list mode should not filter remote blocking");
            var unsynced = new RemotePlayer(client.level, new com.mojang.authlib.GameProfile(new UUID(4,4), "Unsynced"));
            unsynced.setId(-1003); client.level.addEntity(unsynced); ConfigManager.smolpeopleMode = true;
            if (SmolPlayers.shouldApplySmolTo(unsynced.getId())) throw new AssertionError("Unsynced player received a removed friend effect");
            if (!SmolPlayers.shouldApplySmolTo(client.player.getId())) throw new AssertionError("Local SmolPeople was removed with Friends");
            ConfigManager.smolpeopleMode = false; client.level.removeEntity(-1003, Entity.RemovalReason.DISCARDED);
            b.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            if (SwordBlocking.hand(b) != BlockHand.NONE) throw new AssertionError("Empty-hand remote pose should not render");
            var poses = BlockingSync.class.getDeclaredField("remote"); poses.setAccessible(true);
            poses.set(null, Map.of(alice, new BlockingTransport.Pose(BlockHand.MAIN, 0, "skyblock:HYPERION")));
            if (SwordBlocking.hand(a) != BlockHand.NONE) throw new AssertionError("Expired remote state was rendered");
        });
        context.runOnClient(client -> {
            client.level.removeEntity(-1001, Entity.RemovalReason.DISCARDED);
            client.level.removeEntity(-1002, Entity.RemovalReason.DISCARDED);
            var poses = BlockingSync.class.getDeclaredField("remote"); poses.setAccessible(true); poses.set(null, Map.of());
            ConfigManager.syncEnabled = false; ConfigManager.swordBlockingSyncEnabled = false;
            ConfigManager.swordBlockingListsApplyToOthers = false;
        });
        System.out.println("REMOTE_NICK_SMOL_BLOCKING_RENDER_OK REMOTE_BLOCKING_LISTS_OK FRIENDS_REMOVED_OK");
    }
    private static void checkFirstPersonSleeves(ClientGameTestContext context) {
        var originalHand = new java.util.concurrent.atomic.AtomicReference<net.minecraft.world.entity.HumanoidArm>();
        context.runOnClient(client -> {
            originalHand.set(client.options.mainHand().get());
            client.player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            client.player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
            RenderAssertions.firstPersonHandChecks.set(0);
            RenderAssertions.firstPersonSleeveMismatch.set(0);
            RenderAssertions.trackHandSleeves = true;
        });
        try {
            for (var hand : net.minecraft.world.entity.HumanoidArm.values()) {
                context.runOnClient(client -> client.options.mainHand().set(hand));
                context.waitTicks(8);
                context.takeScreenshot("remote-blocking-normal-empty-hand-" + hand.name().toLowerCase(java.util.Locale.ROOT));
            }
            if (RenderAssertions.firstPersonHandChecks.get() == 0)
                throw new AssertionError("The real first-person bare hand renderer was not checked");
            if (RenderAssertions.remoteSleeveMismatch.get() > 0 || RenderAssertions.firstPersonSleeveMismatch.get() > 0)
                throw new AssertionError("Remote blocking rotated child sleeves and leaked into the first-person hand: remote="
                    + RenderAssertions.remoteSleeveMismatch.get() + " firstPerson=" + RenderAssertions.firstPersonSleeveMismatch.get());
            System.out.println("REMOTE_BLOCKING_FIRST_PERSON_SLEEVES_OK checks=" + RenderAssertions.firstPersonHandChecks.get());
        } finally {
            context.runOnClient(client -> {
                RenderAssertions.trackHandSleeves = false;
                client.options.mainHand().set(originalHand.get());
            });
        }
    }
}
