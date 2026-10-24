// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright (C) 2026 CustomCosmetics contributors
package com.customcosmetics.features.blocking;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.BlockingSync;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;

/** Read-only input sampling and render queries. Never changes game input, use state or game packets. */
public final class SwordBlocking {
    private static volatile BlockHand localHand = BlockHand.NONE;
    private static String localItemId = "";
    private SwordBlocking() {}

    public static void tick(Minecraft client) {
        sample(client);
        BlockingSync.tick(localHand, localItemId, client.level);
    }
    private static void sample(Minecraft client) {
        if(!ConfigManager.swordBlockingEnabled||!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ANIMATIONS)){clearLocal();return;}
        BlockHand previous = localHand;
        String previousId = localItemId;
        var player = client.player;
        boolean world = client.level != null && player != null;
        // Item identities and list rules cannot affect an inactive guard.
        if(!world||ClientUiAccess.screen(client)!=null||!client.isWindowActive()||!player.isAlive()
                ||player.isSpectator()||!client.options.keyUse.isDown()){clearLocal();return;}
        boolean otherUse = world && player.isUsingItem() && !BlockingItems.allowed(player.getUseItem())
            && !(player.getUseItem().getItem() instanceof ShieldItem);
        localHand = BlockHand.choose((ConfigManager.swordBlockingEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ANIMATIONS)), world, ClientUiAccess.screen(client) != null,
            client.isWindowActive(), world && player.isAlive(), world && player.isSpectator(),
            client.options.keyUse.isDown(), otherUse, world && BlockingItems.allowed(player.getMainHandItem()),
            world && BlockingItems.allowed(player.getOffhandItem()));
        localItemId = localHand == BlockHand.NONE ? "" : BlockingItems.identity(
            localHand == BlockHand.MAIN ? player.getMainHandItem() : player.getOffhandItem());
        if (previous != localHand || !previousId.equals(localItemId)) BlockingSync.localHandChanged(localHand, localItemId);
    }
    private static void clearLocal(){
        if(localHand!=BlockHand.NONE||!localItemId.isEmpty()){localHand=BlockHand.NONE;localItemId="";BlockingSync.localHandChanged(localHand,localItemId);}
    }

    public static boolean isSword(ItemStack stack) { return SwordItems.isSword(stack); }
    public static BlockHand localHand() { sample(Minecraft.getInstance()); return (ConfigManager.swordBlockingEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ANIMATIONS)) ? localHand : BlockHand.NONE; }

    public static BlockHand hand(Player player) {
        if (!(ConfigManager.swordBlockingEnabled && com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.ANIMATIONS)) || player == null || !player.isAlive() || player.isSpectator()) return BlockHand.NONE;
        if (player == Minecraft.getInstance().player) sample(Minecraft.getInstance());
        boolean local = player == Minecraft.getInstance().player;
        var pose = !local && ConfigManager.swordBlockingSyncEnabled && ConfigManager.syncEnabled
            ? BlockingSync.pose(player.getUUID()) : null;
        BlockHand hand = local ? localHand : pose != null ? pose.hand() : BlockHand.NONE;
        ItemStack held = hand == BlockHand.MAIN ? player.getMainHandItem() : player.getOffhandItem();
        if (hand == BlockHand.NONE || held.isEmpty()) return BlockHand.NONE;
        if (!local) return BlockingItems.allowedRemote(pose.itemId()) ? hand : BlockHand.NONE;
        return BlockingItems.allowed(held) ? hand : BlockHand.NONE;
    }
}
