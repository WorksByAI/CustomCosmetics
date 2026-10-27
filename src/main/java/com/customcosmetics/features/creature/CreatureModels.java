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
import net.minecraft.client.renderer.entity.state.EnderDragonRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;

/** A visual-only replacement: the real player continues to own movement, hitbox and interaction. */
public final class CreatureModels {
    // Render-thread scope: 26.3 also extracts the local avatar for first-person effects.
    // That state must stay an avatar; world/GUI extraction still uses the selected creature.
    public static boolean extractingPlayerEffects;
    public enum Choice {
        PLAYER("player", "Player"),
        ALLAY("allay", "Allay"),
        ARMADILLO("armadillo", "Armadillo"),
        ARMOR_STAND("armor_stand", "Armor Stand"),
        AXOLOTL("axolotl", "Axolotl"),
        BAT("bat", "Bat"),
        BEE("bee", "Bee"),
        BLAZE("blaze", "Blaze"),
        BOGGED("bogged", "Bogged"),
        BREEZE("breeze", "Breeze"),
        CAMEL("camel", "Camel"),
        CAMEL_HUSK("camel_husk", "Camel Husk"),
        CAT("cat", "Cat"),
        CAVE_SPIDER("cave_spider", "Cave Spider"),
        CHICKEN("chicken", "Chicken"),
        COD("cod", "Cod"),
        COPPER_GOLEM("copper_golem", "Copper Golem"),
        COW("cow", "Cow"),
        CREAKING("creaking", "Creaking"),
        CREEPER("creeper", "Creeper"),
        DOLPHIN("dolphin", "Dolphin"),
        DONKEY("donkey", "Donkey"),
        DROWNED("drowned", "Drowned"),
        ELDER_GUARDIAN("elder_guardian", "Elder Guardian"),
        ENDER_DRAGON("ender_dragon", "Ender Dragon"),
        ENDERMAN("enderman", "Enderman"),
        ENDERMITE("endermite", "Endermite"),
        EVOKER("evoker", "Evoker"),
        FOX("fox", "Fox"),
        FROG("frog", "Frog"),
        GHAST("ghast", "Ghast"),
        GIANT("giant", "Giant"),
        GLOW_SQUID("glow_squid", "Glow Squid"),
        GOAT("goat", "Goat"),
        GUARDIAN("guardian", "Guardian"),
        HAPPY_GHAST("happy_ghast", "Happy Ghast"),
        HOGLIN("hoglin", "Hoglin"),
        HORSE("horse", "Horse"),
        HUSK("husk", "Husk"),
        ILLUSIONER("illusioner", "Illusioner"),
        IRON_GOLEM("iron_golem", "Iron Golem"),
        LLAMA("llama", "Llama"),
        MAGMA_CUBE("magma_cube", "Magma Cube"),
        MANNEQUIN("mannequin", "Mannequin"),
        MOOSHROOM("mooshroom", "Mooshroom"),
        MULE("mule", "Mule"),
        NAUTILUS("nautilus", "Nautilus"),
        OCELOT("ocelot", "Ocelot"),
        PANDA("panda", "Panda"),
        PARCHED("parched", "Parched"),
        PARROT("parrot", "Parrot"),
        PHANTOM("phantom", "Phantom"),
        PIG("pig", "Pig"),
        PIGLIN("piglin", "Piglin"),
        PIGLIN_BRUTE("piglin_brute", "Piglin Brute"),
        PILLAGER("pillager", "Pillager"),
        POLAR_BEAR("polar_bear", "Polar Bear"),
        PUFFERFISH("pufferfish", "Pufferfish"),
        RABBIT("rabbit", "Rabbit"),
        RAVAGER("ravager", "Ravager"),
        SALMON("salmon", "Salmon"),
        SHEEP("sheep", "Sheep"),
        SHULKER("shulker", "Shulker"),
        SILVERFISH("silverfish", "Silverfish"),
        SKELETON("skeleton", "Skeleton"),
        SKELETON_HORSE("skeleton_horse", "Skeleton Horse"),
        SLIME("slime", "Slime"),
        SNIFFER("sniffer", "Sniffer"),
        SNOW_GOLEM("snow_golem", "Snow Golem"),
        SPIDER("spider", "Spider"),
        SQUID("squid", "Squid"),
        STRAY("stray", "Stray"),
        STRIDER("strider", "Strider"),
        SULFUR_CUBE("sulfur_cube", "Sulfur Cube"),
        TADPOLE("tadpole", "Tadpole"),
        TRADER_LLAMA("trader_llama", "Trader Llama"),
        TROPICAL_FISH("tropical_fish", "Tropical Fish"),
        TURTLE("turtle", "Turtle"),
        VEX("vex", "Vex"),
        VILLAGER("villager", "Villager"),
        VINDICATOR("vindicator", "Vindicator"),
        WANDERING_TRADER("wandering_trader", "Wandering Trader"),
        WARDEN("warden", "Warden"),
        WITCH("witch", "Witch"),
        WITHER("wither", "Wither"),
        WITHER_SKELETON("wither_skeleton", "Wither Skeleton"),
        WOLF("wolf", "Wolf"),
        ZOGLIN("zoglin", "Zoglin"),
        ZOMBIE("zombie", "Zombie"),
        ZOMBIE_HORSE("zombie_horse", "Zombie Horse"),
        ZOMBIE_NAUTILUS("zombie_nautilus", "Zombie Nautilus"),
        ZOMBIE_VILLAGER("zombie_villager", "Zombie Villager"),
        ZOMBIFIED_PIGLIN("zombified_piglin", "Zombified Piglin");

        public final String id, label;
        Choice(String id, String label) { this.id = id; this.label = label; }

        public EntityType<?> type() {
            return this == PLAYER ? null : BuiltInRegistries.ENTITY_TYPE.getOptional(Identifier.parse("minecraft:" + id)).orElse(null);
        }
    }

    private record Cached(Choice choice, LivingEntity entity) {}
    private static final WeakHashMap<AbstractClientPlayer, Cached> CACHE = new WeakHashMap<>();
    private static final EnumSet<Choice> FAILED = EnumSet.noneOf(Choice.class);
    private static final java.util.Map<String,Choice> CHOICES = choices();
    private static ClientLevel cachedLevel;
    private static java.util.List<Choice> availableChoices;

    private CreatureModels() {}

    /** Resolve once, outside rendering; older Minecraft versions omit types they do not have. */
    public static java.util.List<Choice> availableChoices() {
        if (availableChoices == null) availableChoices = java.util.Arrays.stream(Choice.values())
            .filter(choice -> choice == Choice.PLAYER || choice.type() != null).toList();
        return availableChoices;
    }

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
            FAILED.clear();
            cachedLevel = client.level;
        }
    }

    private static LivingEntity entity(AbstractClientPlayer player, Choice choice) {
        if (player.level() != cachedLevel) {
            CACHE.clear();
            FAILED.clear();
            cachedLevel = (ClientLevel) player.level();
        }
        if (FAILED.contains(choice)) return null;
        Cached cached = CACHE.get(player);
        if (cached != null && cached.choice == choice && cached.entity.level() == player.level()) return cached.entity;
        try {
            EntityType<?> type = choice.type();
            if (type == null || !(type.create(player.level(), EntitySpawnReason.COMMAND) instanceof LivingEntity entity)) {
                FAILED.add(choice);
                return null;
            }
            // Render-state hooks expect an assigned ID, but the proxy is never added to the world.
            entity.setId(player.getId());
            if (entity instanceof EnderDragon dragon) {
                // A proxy is not ticked by the world. Seed the dragon's segmented body orientation.
                for (int i = 0; i < 64; i++) dragon.flightHistory.record(player.getY(), player.getYRot());
            }
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
        LivingEntity creature = choice == Choice.PLAYER ? null : entity(player, choice);
        return creature == null ? player : creature;
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
        // Species-specific animation (notably the sheep's head) interpolates these
        // directly, even when the common living render angles are replaced below.
        creature.xRotO = player.xRotO;
        creature.yRotO = player.yRotO;
        creature.yBodyRotO = player.yBodyRotO;
        creature.yHeadRotO = player.yHeadRotO;
        if (creature instanceof EnderDragon dragon && creature.tickCount != player.tickCount)
            dragon.flightHistory.record(player.getY(), player.getYRot());
        creature.tickCount = player.tickCount;
        EntityRenderState replacement;
        try {
            replacement = Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(creature, partialTick);
        } catch (RuntimeException failure) {
            FAILED.add(choice);
            CACHE.remove(player);
            CustomCosmeticsClient.LOGGER.warn("Unable to render visual {} model; using player model", choice.id, failure);
            return original;
        }

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
        if (replacement instanceof LivingEntityRenderState living) {
            living.bodyRot = original.bodyRot;
            living.yRot = original.yRot;
            living.xRot = original.xRot;
            living.deathTime = original.deathTime;
            living.walkAnimationPos = original.walkAnimationPos;
            living.walkAnimationSpeed = original.walkAnimationSpeed;
            living.isUpsideDown = original.isUpsideDown;
            living.isFullyFrozen = original.isFullyFrozen;
            living.isInWater = original.isInWater;
            living.isAutoSpinAttack = original.isAutoSpinAttack;
            living.hasRedOverlay = original.hasRedOverlay;
            living.isInvisibleToPlayer = original.isInvisibleToPlayer;
            living.pose = original.pose;
        } else if (replacement instanceof EnderDragonRenderState dragon) {
            dragon.flapTime = original.ageInTicks * 0.1f;
            dragon.deathTime = original.deathTime;
            dragon.hasRedOverlay = original.hasRedOverlay;
        }
        return replacement;
    }
}
