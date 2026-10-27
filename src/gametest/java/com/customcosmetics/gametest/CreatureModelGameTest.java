// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.FeatureModules;
import com.customcosmetics.features.creature.CreatureModels;
import com.customcosmetics.sync.PresenceSync;
import com.google.gson.JsonObject;
import java.util.UUID;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.EntityTypes;

public final class CreatureModelGameTest implements FabricClientGameTest {
    private static final UUID REMOTE = UUID.fromString("12345678-1234-4234-8234-123456789abc");
    private static final int REMOTE_ID = -12931;

    @Override public void runTest(ClientGameTestContext context) {
        float[] playerBounds = new float[2];
        context.runOnClient(client -> {
            ConfigManager.syncEnabled = false;
            ConfigManager.offlineMode = false;
            ConfigManager.creatureModel = "player";
            FeatureModules.set(FeatureModules.Feature.CREATURE_MODEL, true);
        });
        try (var world = context.worldBuilder().create()) {
            world.getServer().runCommand("gamemode creative @a");
            context.waitTicks(8);
            context.runOnClient(client -> {
                float width = client.player.getBbWidth(), height = client.player.getBbHeight();
                playerBounds[0] = width;
                playerBounds[1] = height;
                CreatureModels.select(CreatureModels.Choice.ZOMBIE);
                var dispatcher = client.getEntityRenderDispatcher();
                var state = dispatcher.extractEntity(client.player, 0);
                if (state instanceof AvatarRenderState || state.entityType != EntityTypes.ZOMBIE)
                    throw new AssertionError("Local player was not rendered as a zombie");
                if (client.player.getBbWidth() != width || client.player.getBbHeight() != height)
                    throw new AssertionError("Visual model changed the real player's hitbox");
                client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
                var remote = new RemotePlayer(client.level, new com.mojang.authlib.GameProfile(REMOTE, "Alice"));
                remote.setId(REMOTE_ID);
                remote.setPos(client.player.getX() + 3, client.player.getY(), client.player.getZ() + 4);
                client.level.addEntity(remote);
                receive("cow");
                if (dispatcher.extractEntity(remote, 0).entityType != EntityTypes.COW)
                    throw new AssertionError("Synced creature model was not rendered for another player");
                ConfigManager.offlineMode = true;
                if (!(dispatcher.extractEntity(remote, 0) instanceof AvatarRenderState))
                    throw new AssertionError("Offline mode retained the remote creature model");
                ConfigManager.offlineMode = false;
            });
            context.runOnClient(client -> {
                for (var field : EntityTypes.class.getFields()) {
                    if (!(field.getGenericType() instanceof java.lang.reflect.ParameterizedType parameterized)) continue;
                    var argument = parameterized.getActualTypeArguments()[0];
                    if (!(argument instanceof Class<?> type) || !net.minecraft.world.entity.LivingEntity.class.isAssignableFrom(type)) continue;
                    try {
                        var entityType = (net.minecraft.world.entity.EntityType<?>) field.get(null);
                        String id = net.minecraft.world.entity.EntityType.getKey(entityType).getPath();
                        if (!id.equals("player") && CreatureModels.parse(id).type() != entityType)
                            throw new AssertionError("Missing vanilla living model: " + id);
                    } catch (IllegalAccessException failure) { throw new AssertionError(failure); }
                }
            });
            for (var choice : CreatureModels.availableChoices()) {
                if (choice == CreatureModels.Choice.PLAYER) continue;
                context.runOnClient(client -> {
                    CreatureModels.select(choice);
                    if (client.getEntityRenderDispatcher().extractEntity(client.player, 0).entityType != choice.type())
                        throw new AssertionError("Creature model did not render: " + choice.id);
                    var preview = CreatureModels.previewEntity(client.player);
                    if (preview == client.player || preview != CreatureModels.previewEntity(client.player))
                        throw new AssertionError("Creature proxy was not cached: " + choice.id);
                    if (client.level.getEntity(preview.getId()) == preview)
                        throw new AssertionError("Visual creature was added to the real world: " + choice.id);
                    var remote = (RemotePlayer)client.level.getEntity(REMOTE_ID);
                    receive(choice.id);
                    if (client.getEntityRenderDispatcher().extractEntity(remote, 0).entityType != choice.type())
                        throw new AssertionError("Remote creature model did not render: " + choice.id);
                    if (client.player.getBbWidth() != playerBounds[0] || client.player.getBbHeight() != playerBounds[1])
                        throw new AssertionError("Creature changed the player's hitbox: " + choice.id);
                    System.out.println("CREATURE_MODEL_TYPE_OK " + choice.id);
                });
                context.waitTicks(2);
            }
            context.runOnClient(client -> com.customcosmetics.compatibility.ClientUiAccess.setScreen(client,
                new com.customcosmetics.gui.CosmeticsScreen(null, com.customcosmetics.gui.CosmeticsScreen.Page.SIZE)));
            for (var choice : CreatureModels.availableChoices()) {
                context.runOnClient(client -> CreatureModels.select(choice));
                context.waitTicks(2);
                if (choice == CreatureModels.Choice.ENDER_DRAGON || choice == CreatureModels.Choice.WARDEN)
                    context.takeScreenshot("creature-preview-" + choice.id);
            }
            context.runOnClient(client -> com.customcosmetics.compatibility.ClientUiAccess.setScreen(client, null));
            context.runOnClient(client -> CreatureModels.select(CreatureModels.Choice.ZOMBIE));
            context.waitTicks(5);
            context.takeScreenshot("creature-model-local-zombie");
            context.runOnClient(client -> {
                CreatureModels.select(CreatureModels.Choice.PLAYER);
                if (!(client.getEntityRenderDispatcher().extractEntity(client.player, 0) instanceof AvatarRenderState))
                    throw new AssertionError("Selecting Player did not restore the native renderer");
                CreatureModels.select(CreatureModels.Choice.ENDER_DRAGON);
                FeatureModules.set(FeatureModules.Feature.CREATURE_MODEL, false);
                if (!(client.getEntityRenderDispatcher().extractEntity(client.player, 0) instanceof AvatarRenderState)
                        || CreatureModels.previewEntity(client.player) != client.player)
                    throw new AssertionError("Disabled creature module retained a replacement");
                FeatureModules.set(FeatureModules.Feature.CREATURE_MODEL, true);
                client.level.removeEntity(REMOTE_ID, net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
                client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
                ConfigManager.creatureModel = "player";
            });
            System.out.println("CREATURE_MODEL_ALL_VANILLA_LOCAL_REMOTE_OFFLINE_CACHE_AND_HITBOX_OK");
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }

    private static void receive(String choice) {
        try {
            var user = new JsonObject(); user.addProperty("name", "Alice");
            var features = new JsonObject(); features.addProperty("creatureModel", choice); user.add("features", features);
            var users = new JsonObject(); users.add(REMOTE.toString(), user);
            var root = new JsonObject(); root.addProperty("authVersion", 2); root.add("users", users);
            var method = PresenceSync.class.getDeclaredMethod("applyPayload", String.class, boolean.class, boolean.class);
            method.setAccessible(true); method.invoke(null, root.toString(), false, true);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
}
