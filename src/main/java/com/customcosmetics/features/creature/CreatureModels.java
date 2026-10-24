// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.creature;

import com.customcosmetics.client.CustomCosmeticsClient;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.FeatureModules;
import com.customcosmetics.sync.PresenceSync;
import java.util.EnumSet;
import java.util.Locale;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** A visual-only replacement: the real player continues to own movement, hitbox and interaction. */
public final class CreatureModels {
    public enum Choice {
        PLAYER("player", "Player"), ZOMBIE("zombie", "Zombie"), SKELETON("skeleton", "Skeleton"),
        CREEPER("creeper", "Creeper"), ENDERMAN("enderman", "Enderman"), BLAZE("blaze", "Blaze"),
        IRON_GOLEM("iron_golem", "Iron Golem"), VILLAGER("villager", "Villager"),
        COW("cow", "Cow"), PIG("pig", "Pig"), SHEEP("sheep", "Sheep"), CHICKEN("chicken", "Chicken");

        public final String id, label;
        Choice(String id, String label) { this.id = id; this.label = label; }

        public EntityType<?> type() {
            return this == PLAYER ? null : BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse("minecraft:" + id));
        }
    }

    private record Cached(Choice choice, LivingEntity entity) {}
    private static final WeakHashMap<AbstractClientPlayer, Cached> CACHE = new WeakHashMap<>();
    private static final EnumSet<Choice> FAILED = EnumSet.noneOf(Choice.class);
    private static final java.util.Map<String,Choice> CHOICES = choices();
    private static ClientLevel cachedLevel;

    private CreatureModels() {}

    private static java.util.Map<String,Choice> choices() {
        var choices = new java.util.HashMap<String,Choice>();
        for (Choice choice : Choice.values()) choices.put(choice.id, choice);
        return java.util.Map.copyOf(choices);
    }

    public static Choice parse(String value) {
        if (value == null) return Choice.PLAYER;
        Choice exact = CHOICES.get(value);
        return exact != null ? exact : CHOICES.getOrDefault(value.toLowerCase(Locale.ROOT), Choice.PLAYER);
    }

    public static Choice choiceFor(AbstractClientPlayer player) {
        if (!FeatureModules.enabled(FeatureModules.Feature.CREATURE_MODEL)) return Choice.PLAYER;
        Minecraft client = Minecraft.getInstance();
        if (player == client.player) return parse(ConfigManager.creatureModel);
        return parse(PresenceSync.getRemoteCreatureModel(player.getUUID()));
    }

    public static void select(Choice choice) {
        ConfigManager.creatureModel = choice.id;
        ConfigManager.requestSave();
    }

    public static void tick(Minecraft client) {
        if (client.level != cachedLevel) {
            CACHE.clear();
            cachedLevel = client.level;
        }
    }

    private static LivingEntity entity(AbstractClientPlayer player, Choice choice) {
        if (player.level() != cachedLevel) {
            CACHE.clear();
            cachedLevel = (ClientLevel) player.level();
        }
        Cached cached = CACHE.get(player);
        if (cached != null && cached.choice == choice && cached.entity.level() == player.level()) return cached.entity;
        if (FAILED.contains(choice)) return null;
        try {
            if (!(choice.type().create(player.level(), EntitySpawnReason.COMMAND) instanceof LivingEntity entity)) return null;
            // Render-state hooks expect an assigned ID, but the proxy is never added to the world.
            entity.setId(player.getId());
            CACHE.put(player, new Cached(choice, entity));
            return entity;
        } catch (RuntimeException failure) {
            FAILED.add(choice);
            CustomCosmeticsClient.LOGGER.warn("Unable to create visual {} model", choice.id, failure);
            return null;
        }
    }

    public static LivingEntity previewEntity(AbstractClientPlayer player) {
        Choice choice = choiceFor(player);
        return choice == Choice.PLAYER ? player : entity(player, choice);
    }

    public static EntityRenderState replace(AbstractClientPlayer player, AvatarRenderState original, float partialTick) {
        Choice choice = choiceFor(player);
        if (choice == Choice.PLAYER) return original;
        LivingEntity creature = entity(player, choice);
        if (creature == null) return original;
        creature.setPos(player.getX(), player.getY(), player.getZ());
        creature.setYRot(player.getYRot());
        creature.setXRot(player.getXRot());
        creature.setYBodyRot(player.yBodyRot);
        creature.setYHeadRot(player.getYHeadRot());
        EntityRenderState extracted = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(creature, partialTick);
        if (!(extracted instanceof LivingEntityRenderState replacement)) return original;

        replacement.x = original.x;
        replacement.y = original.y;
        replacement.z = original.z;
        replacement.ageInTicks = original.ageInTicks;
        replacement.distanceToCameraSq = original.distanceToCameraSq;
        replacement.isInvisible = original.isInvisible;
        replacement.isDiscrete = original.isDiscrete;
        replacement.displayFireAnimation = original.displayFireAnimation;
        replacement.lightCoords = original.lightCoords;
        replacement.outlineColor = original.outlineColor;
        replacement.passengerOffset = original.passengerOffset;
        replacement.nameTag = original.nameTag;
        replacement.scoreText = original.scoreText;
        replacement.nameTagAttachment = original.nameTagAttachment;
        replacement.bodyRot = original.bodyRot;
        replacement.yRot = original.yRot;
        replacement.xRot = original.xRot;
        replacement.deathTime = original.deathTime;
        replacement.walkAnimationPos = original.walkAnimationPos;
        replacement.walkAnimationSpeed = original.walkAnimationSpeed;
        replacement.isUpsideDown = original.isUpsideDown;
        replacement.isFullyFrozen = original.isFullyFrozen;
        replacement.isInWater = original.isInWater;
        replacement.isAutoSpinAttack = original.isAutoSpinAttack;
        replacement.hasRedOverlay = original.hasRedOverlay;
        replacement.isInvisibleToPlayer = original.isInvisibleToPlayer;
        replacement.pose = original.pose;
        return replacement;
    }
}
