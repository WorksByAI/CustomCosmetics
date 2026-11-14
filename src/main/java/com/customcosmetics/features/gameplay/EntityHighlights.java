// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Nebulune TrevorHelper, PestESP and SafariESP, Copyright (c) 2025 Starred.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.items.HeadTexture;
import com.customcosmetics.features.party.PartyGeometry;
import com.customcosmetics.features.party.PartyTracer;
import com.customcosmetics.features.waypoint.Waypoints;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.fish.TropicalFish;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.item.Items;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;

final class EntityHighlights {
    private static final Set<String> ANIMALS = Set.of("cow", "pig", "sheep", "chicken", "rabbit", "horse");
    private static final Map<String, String> textureHashes = new HashMap<>();
    private static Textures textures;
    private static final Int2IntOpenHashMap pendingPests = new Int2IntOpenHashMap();
    private static final Set<ArmorStand> pests = new HashSet<>();
    private static ClientLevel pestLevel;
    private static boolean pestTracking;
    private static List<BlockPos> safariDrops = List.of();
    private static final Map<String, SafariSpecies> SAFARI_IDENTITIES = safariIdentities();
    private static List<Marker> markers = List.of();
    private record Textures(Set<String> pests, Map<String, String> safariByHash) {}
    private record Marker(Entity entity, AABB bounds, int color, GameplayModule module) {}

    private EntityHighlights() {}

    static void reset() {
        resetPests();
        markers = List.of();
        textureHashes.clear();
        safariDrops = List.of();
    }

    private static void resetPests() {
        pendingPests.clear();
        pests.clear();
        pestLevel = null;
        pestTracking = false;
    }

    static void pestEntityUpdated(int id) {
        if (!GameplayModule.PEST_ESP.enabled() || !pestTracking) return;
        var client = Minecraft.getInstance();
        if (client.level != pestLevel || client.player == null || !(client.level.getEntity(id) instanceof ArmorStand)) return;
        pendingPests.put(id, client.player.tickCount + 2);
    }

    static void tickPests(Minecraft client) {
        if (!GameplayModule.PEST_ESP.enabled() || !"garden".equals(Waypoints.currentIslandMode())) {
            if (pestTracking) resetPests();
            return;
        }
        if (!pestTracking || pestLevel != client.level) {
            resetPests();
            pestTracking = true;
            pestLevel = client.level;
            if (textures == null) textures = loadTextures();
            for (var entity : client.level.entitiesForRendering()) {
                if (entity instanceof ArmorStand stand) updatePest(stand);
            }
        }
        if (client.player.tickCount % 20 == 0) pests.removeIf(stand -> !stand.isAlive());
        var updates = pendingPests.int2IntEntrySet().fastIterator();
        while (updates.hasNext()) {
            var update = updates.next();
            if (client.player.tickCount < update.getIntValue()) continue;
            if (client.level.getEntity(update.getIntKey()) instanceof ArmorStand stand) updatePest(stand);
            updates.remove();
        }
    }

    private static void updatePest(ArmorStand stand) {
        if (stand.isAlive() && textures.pests().contains(texture(stand.getItemBySlot(EquipmentSlot.HEAD)))) pests.add(stand);
        else pests.remove(stand);
    }

    static void tick(Minecraft client) {
        if (!GameplayModule.SAFARI_ESP.enabled() && TrevorHelper.animal() == null) {
            markers = List.of();
            safariDrops = List.of();
            return;
        }
        String island = Waypoints.currentIslandMode();
        boolean safari = "safari".equals(island) && GameplayModule.SAFARI_ESP.enabled();
        var trevor = "farming_1".equals(island) ? TrevorHelper.animal() : null;
        if (!safari && trevor == null) {
            markers = List.of();
            safariDrops = List.of();
            return;
        }
        if (safari && textures == null) textures = loadTextures();
        var next = new ArrayList<Marker>();
        var range = client.player.getBoundingBox().inflate(72);
        var observer = client.player.position();
        var playerBiome = safari ? SafariSpecies.Biome.at(observer.x, observer.z) : null;
        if (!safari || !ConfigManager.safariFloorDrops) safariDrops = List.of();
        else if (client.player.tickCount % 10 == 0) refreshFloorDrops(client, playerBiome);
        for (var entity : client.level.getEntities(client.player, range, Entity::isAlive)) {
            if (safari) {
                var biome = SafariSpecies.Biome.at(entity.getX(), entity.getZ());
                if (biome == null || ConfigManager.safariDisabledBiomes.contains(biome.key())
                        || (playerBiome != biome && !ConfigManager.safariAcrossBiomes.contains(biome.key()))) continue;
                var species = identify(entity);
                if (species == null || species.biome != biome || ConfigManager.safariDisabledSpecies.contains(species.key())) continue;
                int color = ConfigManager.safariSpeciesColors.getOrDefault(species.key(), species.color);
                next.add(new Marker(entity, critterBox(entity), 0xFF000000 | color, GameplayModule.SAFARI_ESP));
            } else if (trevor != null && entity instanceof LivingEntity living) {
                String animal = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
                if (!ANIMALS.contains(animal)) continue;
                float hp = living.getMaxHealth();
                if (animal.equals("horse")) hp /= 2;
                if (TrevorHelper.matches(trevor, hp))
                    next.add(new Marker(entity, entity.getBoundingBox(), 0xFF000000 | trevor.color(), GameplayModule.TREVOR_HELPER));
            }
        }
        markers = next;
    }

    private static Map<String, SafariSpecies> safariIdentities() {
        var identities = new HashMap<String, SafariSpecies>();
        for (var species : SafariSpecies.values()) for (String id : species.identities) identities.put(id, species);
        return Map.copyOf(identities);
    }

    private static void refreshFloorDrops(Minecraft client, SafariSpecies.Biome playerBiome) {
        var counts = new HashMap<BlockPos, Integer>();
        for (var entity : client.level.entitiesForRendering()) {
            if (!(entity instanceof Display.ItemDisplay display) || display.itemRenderState() == null
                    || !display.itemRenderState().itemStack().is(Items.STRING)) continue;
            if (ConfigManager.safariFloorDropsCurrentBiome
                    && SafariSpecies.Biome.at(entity.getX(), entity.getZ()) != playerBiome) continue;
            counts.merge(entity.blockPosition(), 1, Integer::sum);
        }
        safariDrops = counts.entrySet().stream().filter(e -> e.getValue() >= 3).map(Map.Entry::getKey).toList();
    }

    private static SafariSpecies identify(Entity entity) {
        if (entity instanceof Parrot parrot)
            return SAFARI_IDENTITIES.get("parrot:" + parrot.getVariant().name());
        if (entity instanceof TropicalFish fish)
            return SAFARI_IDENTITIES.get("fish:" + fish.getPattern().name() + ":" + fish.getBaseColor().name() + ":" + fish.getPatternColor().name());
        if (entity instanceof Shulker shulker)
            return shulker.getColor() == null ? null : SAFARI_IDENTITIES.get("shulker:" + shulker.getColor().name());
        Block block = displayBlock(entity);
        if (block instanceof ShulkerBoxBlock shulker && shulker.getColor() != null)
            return SAFARI_IDENTITIES.get("shulker:" + shulker.getColor().name());
        if (entity instanceof ArmorStand stand) return textured("head:", texture(stand.getItemBySlot(EquipmentSlot.HEAD)));
        if (entity instanceof Player player) return textured("player:", texture(player.getGameProfile()));
        if (entity instanceof Display.ItemDisplay display && display.itemRenderState() != null)
            return textured("display:", texture(display.itemRenderState().itemStack()));

        String id = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).getPath();
        if (id.equals("silverfish") && (entity.isInvisible() || !entity.getPassengers().isEmpty())) return null;
        return SAFARI_IDENTITIES.get(id);
    }

    private static SafariSpecies textured(String kind, String hash) {
        String texture = textures.safariByHash().get(hash);
        return texture == null ? null : SAFARI_IDENTITIES.get(kind + texture);
    }

    private static Block displayBlock(Entity entity) {
        if (entity instanceof Display.BlockDisplay display && display.blockRenderState() != null)
            return display.blockRenderState().blockState().getBlock();
        if (entity instanceof Display.ItemDisplay display && display.itemRenderState() != null)
            return Block.byItem(display.itemRenderState().itemStack().getItem());
        return null;
    }

    private static AABB critterBox(Entity entity) {
        Vec3 position = entity.position();
        if (entity instanceof ArmorStand) return AABB.ofSize(position.add(0, 1.65, 0), .6, .6, .6);
        if (entity instanceof Display) {
            if (displayBlock(entity) instanceof ShulkerBoxBlock) return AABB.ofSize(position.add(0, .5, 0), 1, 1, 1);
            return AABB.ofSize(position.add(0, -.15, 0), .6, .6, .6);
        }
        return entity.getBoundingBox();
    }

    private static String texture(ItemStack stack) {
        var profile = stack.get(DataComponents.PROFILE);
        return profile == null ? "" : texture(profile.partialProfile());
    }

    private static String texture(GameProfile profile) {
        for (var property : profile.properties().get("textures")) {
            if (textureHashes.size() >= 1024) textureHashes.clear();
            return textureHashes.computeIfAbsent(property.value(), value -> {
                try { return HeadTexture.normalize(value); }
                catch (IllegalArgumentException invalid) { return ""; }
            });
        }
        return "";
    }

    private static Textures loadTextures() {
        try (var input = EntityHighlights.class.getResourceAsStream("/assets/customcosmetics/gameplay/critter-textures.json")) {
            if (input == null) throw new IllegalStateException("Missing critter textures");
            var json = JsonParser.parseReader(new InputStreamReader(input, StandardCharsets.UTF_8)).getAsJsonObject();
            var pest = new HashSet<String>();
            for (var hash : json.getAsJsonArray("pests")) pest.add(hash.getAsString());
            var safari = new HashMap<String, String>();
            json.getAsJsonObject("safari").entrySet().forEach(entry -> safari.put(entry.getValue().getAsString(), entry.getKey()));
            return new Textures(Set.copyOf(pest), Map.copyOf(safari));
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }

    static void render(LevelRenderContext context) {
        boolean pest = !pests.isEmpty() && GameplayModule.PEST_ESP.enabled();
        if (!pest && markers.isEmpty() && safariDrops.isEmpty()) return;
        var camera = context.levelState().cameraRenderState;
        var position = camera.pos;
        var pose = new PoseStack();
        boolean pestTracer = pest && ConfigManager.pestEspTracer;
        boolean trevorTracer = ConfigManager.trevorTracer && TrevorHelper.animal() != null;
        Vec3 forward = null, right = null;
        if (pestTracer || trevorTracer) {
            var f = new org.joml.Vector3f(0, 0, -1).rotate(camera.orientation);
            var r = new org.joml.Vector3f(1, 0, 0).rotate(camera.orientation);
            forward = new Vec3(f.x, f.y, f.z);
            right = new Vec3(r.x, r.y, r.z);
        }
        if (GameplayModule.SAFARI_ESP.enabled() && ConfigManager.safariFloorDrops) {
            int color = 0xFF000000 | ConfigManager.safariFloorDropColor;
            for (var pos : safariDrops) {
                double x = pos.getX() - position.x, y = pos.getY() + 1 - position.y, z = pos.getZ() - position.z;
                drawBox(context, pose, new AABB(x, y, z, x + 1, y + 1, z + 1), color, .016, false);
            }
        }
        var client = Minecraft.getInstance();
        float partial = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        if (pest) {
            int color = 0xFF000000 | ConfigManager.pestEspColor;
            for (var stand : pests) {
                if (!stand.isAlive()) continue;
                var pos = stand.getPosition(partial).subtract(position);
                var box = AABB.unitCubeFromLowerCorner(pos.add(-.5, 1, -.5));
                drawBox(context, pose, box, color, .008 * ConfigManager.pestEspThickness, ConfigManager.pestEspDepth);
                if (pestTracer) drawTracer(context, pose, forward, right, box.getCenter(), color,
                    ConfigManager.pestEspThickness, ConfigManager.pestEspDepth);
            }
        }
        for (var marker : markers) {
            if (marker.entity().isRemoved() || !marker.module().enabled()) continue;
            boolean trevor = marker.module() == GameplayModule.TREVOR_HELPER;
            if (trevor && TrevorHelper.animal() == null) continue;
            var offset = trevor ? marker.entity().getPosition(partial).subtract(marker.entity().position()) : Vec3.ZERO;
            var box = marker.bounds().move(offset.subtract(position));
            drawBox(context, pose, box, marker.color(), .023, false);
            if (trevor && trevorTracer) drawTracer(context, pose, forward, right, box.getCenter(), marker.color(), 2, false);
        }
    }

    private static void drawBox(LevelRenderContext context, PoseStack pose, AABB box, int color, double thickness, boolean depth) {
        var pipeline = depth ? net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads() : PartyRenderPipeline.type();
        context.submitNodeCollector().submitCustomGeometry(pose, pipeline, (matrices, output) ->
            PartyGeometry.outline(output, matrices.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, thickness, color));
    }

    private static void drawTracer(LevelRenderContext context, PoseStack pose, Vec3 forward, Vec3 right, Vec3 point,
                                   int color, int thickness, boolean depth) {
        var ribbon = PartyTracer.ribbon(forward, right, point, Math.max(8, context.levelState().cameraRenderState.depthFar * .5), thickness);
        if (ribbon.isEmpty()) return;
        var pipeline = depth ? net.minecraft.client.renderer.rendertype.RenderTypes.debugQuads() : PartyRenderPipeline.type();
        context.submitNodeCollector().submitCustomGeometry(pose, pipeline, (matrices, output) -> {
            for (var vertex : ribbon) output.addVertex(matrices.pose(), (float) vertex.x, (float) vertex.y, (float) vertex.z).setColor(color);
        });
    }
}
