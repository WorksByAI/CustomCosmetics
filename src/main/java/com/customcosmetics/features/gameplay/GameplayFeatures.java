// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.map.MapEditor;
import com.customcosmetics.features.pack.LegacyPackItems;
import com.customcosmetics.features.party.PartyGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GameplayFeatures {
    private static final String[] BOSS = {"Maxor", "Storm", "Goldor", "Necron", "Wither King"};
    private static final net.minecraft.resources.Identifier RED_TERRACOTTA = net.minecraft.resources.Identifier.withDefaultNamespace("red_terracotta");
    private static Object level, harpScreen;
    private static boolean wasInSkyblock;
    private static Component hudTimer = Component.empty();
    private static long phaseStarted, lastLeap, lastHarp;
    private static int phase = -1, harpNotes;
    private static long freshStarted;
    private static Component freshText = Component.empty();
    private static long cocoonUntil;
    private static Component cocoonText = Component.empty();
    private static int nextDoorTick;
    private static Object joinConnection;
    private static int joinTicks;
    private static boolean joinFinished;

    public interface ClickActions {
        boolean csb$attack();
        void csb$use();
    }
    private static List<net.minecraft.world.entity.LivingEntity> starred = List.of();
    private static final AutoLeap AUTO_LEAP = new AutoLeap();

    public static void bossBar(net.minecraft.network.protocol.game.ClientboundBossEventPacket packet,
            java.util.Map<java.util.UUID, net.minecraft.client.gui.components.LerpingBossEvent> events) {
        if (!GameplayModule.AUTO_LEAP.enabled() && !GameplayModule.FAST_LEAP.enabled()) return;
        var client = Minecraft.getInstance();
        packet.dispatch(new net.minecraft.network.protocol.game.ClientboundBossEventPacket.Handler() {
            @Override public void add(java.util.UUID id, Component name, float progress,
                    net.minecraft.world.BossEvent.BossBarColor color, net.minecraft.world.BossEvent.BossBarOverlay overlay,
                    boolean darken, boolean music, boolean fog) {
                AUTO_LEAP.bossBar(client, name.getString(), progress);
            }

            @Override public void updateProgress(java.util.UUID id, float progress) {
                var bar = events.get(id);
                if (bar != null) AUTO_LEAP.bossBar(client, bar.getName().getString(), progress);
            }

            @Override public void updateName(java.util.UUID id, Component name) {
                var bar = events.get(id);
                if (bar != null) AUTO_LEAP.bossBar(client, name.getString(), bar.getProgress());
            }
        });
    }

    public static boolean requestLeap(Minecraft client, String target) {
        return ConfigManager.gameplayPanelConfirmed && PresenceWorld.isHypixel(client) && PresenceWorld.isSkyblock(client)
            && MapEditor.automaticDungeon(client) && AUTO_LEAP.request(client, target, AutoLeap.DungeonClass.parse(target) != null, true);
    }

    public static boolean useKuudraSupply(Minecraft client) {
        return KuudraStunHelper.blockUse(client, KuudraTracker.highTierRun()) || KuudraTracker.useSupply(client);
    }

    public static boolean replaceKuudraSupplyTitle(Component title) {
        return KuudraTracker.replaceSupplyTitle(Minecraft.getInstance(), title);
    }

    private GameplayFeatures() {}

    public static void pestEntityUpdated(int id) { EntityHighlights.pestEntityUpdated(id); }

    public static void otherBlockChanging(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        var client = Minecraft.getInstance();
        SeasonalGames.blockChanging(client, pos, state);
        DojoGames.blockChanging(client, pos, state);
    }

    public static void gameplayParticles(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packet) {
        DojoGames.particles(packet);
    }

    public static boolean dojoBlocksAttack(Entity target) { return DojoGames.attack(Minecraft.getInstance(), target); }

    public static boolean gameplaySound(net.minecraft.network.protocol.game.ClientboundSoundPacket packet) {
        Mirrorverse.sound(Minecraft.getInstance(), packet);
        return ConfigManager.chocolateMute && GameplayModule.CHOCOLATE_FACTORY.enabled()
            && ChocolateFactory.silence(Minecraft.getInstance(), packet.getSound().value().location().toString());
    }

    public static void gameplaySubtitle(Component text) {
        if (GameplayModule.MIRRORVERSE.enabled()) Mirrorverse.subtitle(Minecraft.getInstance(), text.getString());
    }

    public static void gameplayRecipes(GuiGraphicsExtractor graphics, AbstractContainerScreen<?> screen) {
        Mirrorverse.drawRecipes(graphics, screen);
    }

    public static void gameplayEquipment(int id) {
        pestEntityUpdated(id);
        SecretAura.equipmentUpdated(id);
    }

    static boolean terminalDelayedByLeap() {
        return ConfigManager.terminalLeapDelay && AUTO_LEAP.recentlyLeaped(ConfigManager.terminalLeapDelaySeconds);
    }

    public static void init() {
        AutoClicker.init();
        KuudraStunHelper.init();
        KuudraPerks.init();
        CrystalHollowsScanner.init();
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || KuudraTracker.allowMessage(Minecraft.getInstance(), message));
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || SlayerMinibossAlerts.allowMessage(Minecraft.getInstance(), message));
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || !ConfigManager.gameplayPanelConfirmed || ConfigManager.gameplayModules.isEmpty()) return;
            var client = Minecraft.getInstance();
            if (!PresenceWorld.isHypixel(client) || !PresenceWorld.isSkyblock(client)) return;
            String text = message.getString();
            SeasonalGames.message(client, message, text);
            ChocolateFactory.message(client, text);
            Mirrorverse.message(text);
            DojoGames.message(client, text);
            if (ConfigManager.terminalLeapDelay && text.startsWith("You have teleported to ")) AUTO_LEAP.noteLeap();
            InventoryAutomation.message(text);
            SlayerSplits.message(client, text);
            if (GameplayModule.COCOON_ALERT.enabled() && text.strip().equals("YOU COCOONED YOUR SLAYER BOSS")) cocoon(client);
            TrevorHelper.message(message, text);
            CorpseHighlight.message(client, text);
            KuudraTracker.message(client, text);
            if (GameplayModule.AUTO_LEAP.enabled() || GameplayModule.FAST_LEAP.enabled()) AUTO_LEAP.message(client, text);
            if (GameplayModule.FRESH_TOOLS.enabled()
                && text.equals("Your Fresh Tools Perk bonus doubles your building speed for the next 10 seconds!")
                && MapEditor.automaticKuudra(client)) {
                freshStarted = System.nanoTime();
            }
            if (!GameplayModule.F7_TIMER.enabled() || !MapEditor.automaticDungeon(client) || !isFloorSeven()) return;
            for (int i = BOSS.length - 1; i > phase; i--) {
                if (text.startsWith("[BOSS] " + BOSS[i] + ":")) {
                    phase = i;
                    phaseStarted = System.nanoTime();
                    break;
                }
            }
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(Trajectories::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(CorpseHighlight::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(EntityHighlights::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(KuudraTracker::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(KuudraStunHelper::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(SlayerBeaconHighlight::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(SlayerSplits::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(CrystalHollowsScanner::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(FloorSevenDevices::renderSimon);
        LevelRenderEvents.COLLECT_SUBMITS.register(ChocolateFactory::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(Mirrorverse::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(DojoGames::render);
        LevelRenderEvents.COLLECT_SUBMITS.register(context -> {
            if (!GameplayModule.STARRED_MOBS.enabled() || starred.isEmpty()) return;
            var camera = context.levelState().cameraRenderState;
            var pose = new PoseStack();
            int color = 0xFF000000 | ConfigManager.starredMobColor;
            for (var stand : starred) {
                if (stand.isRemoved()) continue;
                var box = stand.getBoundingBox().inflate(.05).move(-camera.pos.x, -camera.pos.y, -camera.pos.z);
                context.submitNodeCollector().submitCustomGeometry(pose, PartyRenderPipeline.type(), (matrix, vertices) ->
                    PartyGeometry.outline(vertices, matrix.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, .022, color));
            }
        });
    }

    public static void tick(Minecraft client) {
        if (level != client.level) {
            level = client.level;
            phase = -1;
            hudTimer = Component.empty();
            starred = List.of();
            harpScreen = null;
            freshStarted = 0;
            cocoonUntil = 0;
            AutoClicker.reset(client);
            nextDoorTick = 0;
            InventoryAutomation.worldChanged();
            AutoExperiments.reset();
            FloorSevenDevices.reset();
            SecretTriggerbot.reset();
            AUTO_LEAP.reset(client);
            SlayerSplits.reset();
            SlayerMinibossAlerts.reset();
            SlayerBeaconHighlight.reset();
            KuudraTracker.reset();
            EntityHighlights.reset();
            TrevorHelper.reset();
            DaggerSwap.reset();
            SeasonalGames.reset();
            ChocolateFactory.reset();
            Mirrorverse.reset();
            DojoGames.reset();
            SecretAura.reset(client);
            CorpseHighlight.reset();
            CrystalHollowsScanner.reset();
            wasInSkyblock = false;
        }
        if (joinConnection != client.getConnection()) {
            joinConnection = client.getConnection();
            joinTicks = 0;
            joinFinished = false;
        }
        if (client.player == null || client.level == null || !ConfigManager.gameplayPanelConfirmed) {
            AutoClicker.reset(client);
            return;
        }
        if (ConfigManager.gameplayModules.isEmpty() && !AUTO_LEAP.hasRequest()) {
            AutoClicker.reset(client);
            if (wasInSkyblock) {
                AUTO_LEAP.reset(client);
                SlayerBeaconHighlight.reset();
                SlayerSplits.reset();
                SecretTriggerbot.reset();
                CrystalHollowsScanner.reset();
                KuudraTracker.reset();
                EntityHighlights.reset();
                TrevorHelper.reset();
                DaggerSwap.reset();
                SeasonalGames.reset();
                ChocolateFactory.reset();
                Mirrorverse.reset();
                DojoGames.reset();
                SecretAura.reset(client);
                InventoryAutomation.resetRefills();
                AutoExperiments.reset();
                wasInSkyblock = false;
            }
            starred = List.of();
            harpScreen = null;
            return;
        }
        Trajectories.tick(client);
        boolean onHypixel = PresenceWorld.isHypixel(client);
        boolean inSkyblock = onHypixel && PresenceWorld.isSkyblock(client);
        AutoClicker.tick(client, inSkyblock);
        if (GameplayModule.AUTO_JOIN_SKYBLOCK.enabled() && onHypixel && !joinFinished) {
            if (inSkyblock) joinFinished = true;
            else if (++joinTicks >= 20 && PresenceWorld.hasOfficialLocation(client)) {
                joinFinished = true;
                client.getConnection().sendCommand("skyblock");
            }
        }
        if (!inSkyblock) {
            if (wasInSkyblock) {
                InventoryAutomation.worldChanged();
                AutoExperiments.reset();
                FloorSevenDevices.reset();
                SecretTriggerbot.reset();
                AUTO_LEAP.reset(client);
                KuudraTracker.reset();
                CrystalHollowsScanner.reset();
                CorpseHighlight.reset();
                SlayerSplits.reset();
                TrevorHelper.reset();
                DaggerSwap.reset();
                SeasonalGames.reset();
                ChocolateFactory.reset();
                Mirrorverse.reset();
                DojoGames.reset();
                SecretAura.reset(client);
                EntityHighlights.reset();
            }
            wasInSkyblock = false;
            starred = List.of();
            return;
        }
        wasInSkyblock = true;
        CrystalHollowsScanner.tick(client);
        if (GameplayModule.AUTO_SPRINT.enabled() && ClientUiAccess.screen(client) == null
            && !client.player.isInWater() && !client.player.isUnderWater() && !client.player.isShiftKeyDown()
            && client.player.getFoodData().getFoodLevel() > 6 && !client.player.horizontalCollision
            && !client.player.isUsingItem() && !com.customcosmetics.features.map.Freecam.active()
            && client.options.keyUp.isDown()) client.player.setSprinting(true);

        boolean refill = GameplayModule.AUTO_GFS.enabled();
        InventoryAutomation.refillState(refill);
        if (refill || GameplayModule.AUTO_BOOK_COMBINE.enabled()) InventoryAutomation.tick(client);
        AutoExperiments.tick(client);

        if (GameplayModule.AUTO_HARP.enabled()) harp(client);
        else harpScreen = null;
        AUTO_LEAP.tick(client);
        KuudraTracker.tickActions(client);
        FloorSevenDevices.tick(client);
        SecretTriggerbot.tick(client);
        SecretAura.tick(client);
        TrevorHelper.tick(client);
        DaggerSwap.tick(client);
        SeasonalGames.tick(client);
        ChocolateFactory.tick(client);
        Mirrorverse.tick(client);
        DojoGames.tick(client);
        EntityHighlights.tickPests(client);
        if (GameplayModule.AUTO_DOOR.enabled()) openDoor(client);
        if (client.player.tickCount % 5 != 0) return;
        SlayerSplits.tick(client);
        SlayerMinibossAlerts.tick(client);
        SlayerBeaconHighlight.tick(client);
        KuudraTracker.tick(client);
        EntityHighlights.tick(client);
        CorpseHighlight.tick(client);

        if (freshStarted != 0) {
            double remaining = 10 - (System.nanoTime() - freshStarted) / 1_000_000_000.0;
            if (remaining <= 0) freshStarted = 0;
            else freshText = Component.literal(String.format(Locale.ROOT, "Fresh Tools %.1fs", remaining));
        }
        if (cocoonUntil != 0) {
            double remaining = (cocoonUntil - System.nanoTime()) / 1_000_000_000.0;
            if (remaining <= 0 || !GameplayModule.COCOON_ALERT.enabled() || !ConfigManager.cocoonTimer) cocoonUntil = 0;
            else cocoonText = cocoonLabel(remaining);
        }
        if (phase >= 0 && GameplayModule.F7_TIMER.enabled()) {
            double time = (System.nanoTime() - phaseStarted) / 1_000_000_000.0;
            hudTimer = Component.literal(BOSS[phase] + "  " + (ConfigManager.f7TimerTicks ? (int) (time * 20) + "t"
                : String.format(Locale.ROOT, "%.1fs", time)));
        }
        if (!GameplayModule.STARRED_MOBS.enabled() || !MapEditor.automaticDungeon(client)) {
            starred = List.of();
            return;
        }
        var next = new java.util.LinkedHashSet<net.minecraft.world.entity.LivingEntity>();
        for (var stand : client.level.getEntitiesOfClass(ArmorStand.class, client.player.getBoundingBox().inflate(48))) {
            var name = stand.getCustomName();
            if (name == null || name.getString().indexOf('✯') < 0) continue;
            int offset = name.getString().toLowerCase(Locale.ROOT).contains("withermancer") ? 3 : 1;
            Entity linked = client.level.getEntity(stand.getId() - offset);
            if (linked instanceof net.minecraft.world.entity.LivingEntity mob && !(mob instanceof ArmorStand)
                && mob != client.player && mob.distanceToSqr(stand) < 16) {
                next.add(mob);
            } else {
                for (var candidate : client.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
                        stand.getBoundingBox().move(0, -1, 0).inflate(.4), entity -> entity != client.player && !(entity instanceof ArmorStand))) {
                    next.add(candidate);
                    break;
                }
            }
        }
        starred = List.copyOf(next);
    }

    static boolean isFloorSeven() {
        String floor = MapEditor.floor();
        return floor.equals("F7") || floor.equals("M7");
    }

    // Quoi's door grid and crosshair checks, applied to loaded world blocks.
    private static void openDoor(Minecraft client) {
        if (client.gameMode == null || client.player.tickCount < nextDoorTick || !client.player.isAlive()
            || client.player.isSpectator() || !ConfigManager.doorInMenus && ClientUiAccess.screen(client) != null
            || com.customcosmetics.features.map.Freecam.active() || !MapEditor.automaticDungeon(client)) return;
        var hit = ConfigManager.doorAura ? closestDoor(client)
            : client.hitResult instanceof BlockHitResult lookedAt ? lookedAt : null;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;
        var pos = hit.getBlockPos();
        if (pos.getY() < 69 || pos.getY() > 73) return;
        int col = Math.round((pos.getX() + 185) / 16f), row = Math.round((pos.getZ() + 185) / 16f);
        if (col < 0 || col > 10 || row < 0 || row > 10 || ((col ^ row) & 1) == 0) return;
        int x = -185 + col * 16, z = -185 + row * 16;
        if (Math.abs(pos.getX() - x) > 2 || Math.abs(pos.getZ() - z) > 2) return;
        Block target = client.level.getBlockState(pos).getBlock();
        var center = new BlockPos(x, 69, z);
        if (!lockedDoorBlock(target) || !client.level.getBlockState(center).is(target)) return;
        double reach = ConfigManager.doorAura ? ConfigManager.doorAuraRange : client.player.blockInteractionRange();
        if (client.player.getEyePosition().distanceToSqr(hit.getLocation()) > reach * reach) return;
        nextDoorTick = client.player.tickCount + Math.ceilDiv(ConfigManager.doorRetryMs, 50);
        client.gameMode.useItemOn(client.player, InteractionHand.MAIN_HAND, hit);
        if (ConfigManager.doorSwing) com.customcosmetics.compatibility.SwingAccess.swingMainHand(client.player);
    }

    private static BlockHitResult closestDoor(Minecraft client) {
        var eye = client.player.getEyePosition();
        double closest = ConfigManager.doorAuraRange * ConfigManager.doorAuraRange;
        int col = (int) Math.round((eye.x + 185) / 16), row = (int) Math.round((eye.z + 185) / 16);
        BlockHitResult found = null;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = col + dx, z = row + dz;
                if (x < 0 || x > 10 || z < 0 || z > 10 || ((x ^ z) & 1) == 0) continue;
                var center = new BlockPos(-185 + x * 16, 69, -185 + z * 16);
                var target = Vec3.atCenterOf(center);
                if (eye.distanceToSqr(target) > closest || !client.level.hasChunkAt(center)
                        || !lockedDoorBlock(client.level.getBlockState(center).getBlock())) continue;
                var hit = client.level.clip(new ClipContext(eye, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, client.player));
                var pos = hit.getBlockPos();
                if (hit.getType() != HitResult.Type.BLOCK || pos.getY() < 69 || pos.getY() > 73
                        || Math.abs(pos.getX() - center.getX()) > 2 || Math.abs(pos.getZ() - center.getZ()) > 2
                        || !client.level.getBlockState(pos).is(client.level.getBlockState(center).getBlock())) continue;
                closest = eye.distanceToSqr(hit.getLocation());
                found = hit;
            }
        }
        return found;
    }

    private static boolean lockedDoorBlock(Block block) {
        return block == Blocks.COAL_BLOCK || RED_TERRACOTTA.equals(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block));
    }

    // Adapted from the Nebulune AutoHarp slot-state algorithm (BSD-3-Clause).
    private static void harp(Minecraft client) {
        if (!(ClientUiAccess.screen(client) instanceof AbstractContainerScreen<?> screen)
            || !screen.getTitle().getString().startsWith("Harp - ") || screen.getMenu().getType() != MenuType.GENERIC_9x6) {
            harpScreen = null;
            return;
        }
        if (screen != harpScreen) {
            harpScreen = screen;
            harpNotes = 0;
            lastHarp = Long.MIN_VALUE / 2;
        }
        int notes = 0, slot = -1;
        for (int i = 37; i <= 43; i++) {
            if (!screen.getMenu().slots.get(i).getItem().is(Items.QUARTZ_BLOCK)) continue;
            notes |= 1 << (i - 37);
            if (slot < 0) slot = i;
        }
        if (notes == harpNotes) return;
        if (slot < 0) { harpNotes = notes; return; }
        long tick = client.player.tickCount;
        if (client.gameMode == null || tick - lastHarp < ConfigManager.harpClickDelay) return;
        harpNotes = notes;
        lastHarp = tick;
        client.gameMode.handleContainerInput(screen.getMenu().containerId, slot, 0, ContainerInput.CLONE, client.player);
    }

    public static boolean startLeap(Minecraft client) {
        if (!GameplayModule.FAST_LEAP.enabled() || client.player == null || client.gameMode == null
            || ClientUiAccess.screen(client) != null || !MapEditor.automaticDungeon(client)
            || !PresenceWorld.isHypixel(client) || !PresenceWorld.isSkyblock(client)) return false;
        String item = LegacyPackItems.id(client.player.getMainHandItem());
        if (!"SPIRIT_LEAP".equals(item) && !"INFINITE_SPIRIT_LEAP".equals(item)) return false;
        long now = System.nanoTime();
        if (now - lastLeap < 250_000_000L) return true;
        if (!AUTO_LEAP.fastLeap(client)) return false;
        lastLeap = now;
        return true;
    }

    public static boolean hidePlayer(Entity entity) {
        if (!GameplayModule.HIDE_PLAYERS.enabled() || !(entity instanceof Player) || entity.getUUID().version() == 2) return false;
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null || entity == player || !PresenceWorld.isSkyblock(client)) return false;
        if (ConfigManager.hidePlayersDungeonOnly && !MapEditor.automaticDungeon(client)) return false;
        if (ConfigManager.hidePlayersBossOnly && !(MapEditor.automaticDungeon(client) && player.getX() > -7 && player.getZ() > -7)) return false;
        if (ConfigManager.hidePlayersAtDevices && !(isFloorSeven() && (player.distanceToSqr(108.63, 120, 94) <= 3.24
                || player.distanceToSqr(63.5, 127, 35.5) <= 3.24))) return false;
        double radius = ConfigManager.hideNearbyPlayersRadius;
        return ConfigManager.hideAllPlayers || player.distanceToSqr(entity) <= radius * radius;
    }

    public static int hudX(int id, int width) {
        float position = switch (id) { case 0 -> ConfigManager.f7HudX; case 1 -> ConfigManager.freshHudX;
            case 2 -> ConfigManager.cocoonHudX; case 3 -> ConfigManager.kuudraSupplyHudX;
            case 4 -> ConfigManager.kuudraSpawnHudX; case 5 -> ConfigManager.kuudraBuildHudX;
            default -> ConfigManager.trevorHudX; };
        return Math.min(Math.max(0, width - 100), Math.round(width * position));
    }

    public static int hudY(int id, int height) {
        float position = switch (id) { case 0 -> ConfigManager.f7HudY; case 1 -> ConfigManager.freshHudY;
            case 2 -> ConfigManager.cocoonHudY; case 3 -> ConfigManager.kuudraSupplyHudY;
            case 4 -> ConfigManager.kuudraSpawnHudY; case 5 -> ConfigManager.kuudraBuildHudY;
            default -> ConfigManager.trevorHudY; };
        return Math.min(Math.max(0, height - 22), Math.round(height * position));
    }

    public static void drawHud(GuiGraphicsExtractor graphics, boolean preview) {
        boolean timer = GameplayModule.F7_TIMER.enabled() && phase >= 0;
        boolean fresh = GameplayModule.FRESH_TOOLS.enabled() && freshStarted != 0;
        boolean cocoon = GameplayModule.COCOON_ALERT.enabled() && ConfigManager.cocoonTimer && cocoonUntil != 0;
        Component supply = preview ? HUD_PREVIEWS[3] : KuudraTracker.supplyHud();
        Component spawn = preview ? HUD_PREVIEWS[4] : KuudraTracker.phaseTimer(false);
        Component build = preview ? HUD_PREVIEWS[5] : KuudraTracker.phaseTimer(true);
        Component trevor = preview ? HUD_PREVIEWS[6] : TrevorHelper.hud();
        if (!preview && !timer && !fresh && !cocoon && supply == null && spawn == null && build == null && trevor == null) return;
        var client = Minecraft.getInstance();
        if (!preview && (client.player == null || !PresenceWorld.isSkyblock(client))) return;
        if (!preview && ClientUiAccess.screen(client) instanceof com.customcosmetics.gui.GameplayHudEditor) return;
        if (preview || timer) draw(graphics, preview ? HUD_PREVIEWS[0] : hudTimer, 0);
        if (preview || fresh) draw(graphics, preview ? HUD_PREVIEWS[1] : freshText, 1);
        if (preview || cocoon) draw(graphics, preview ? HUD_PREVIEWS[2] : cocoonText, 2);
        if (supply != null) draw(graphics, supply, 3);
        if (spawn != null) draw(graphics, spawn, 4);
        if (build != null) draw(graphics, build, 5);
        if (trevor != null) draw(graphics, trevor, 6);
    }

    // Athen's Cocoon Alert, including its six-second timer and separate sound and title controls.
    private static void cocoon(Minecraft client) {
        if (ConfigManager.cocoonTimer) {
            cocoonUntil = System.nanoTime() + 6_000_000_000L;
            cocoonText = cocoonLabel(6);
        }
        if (ConfigManager.cocoonAlert) ClientUiAccess.title(client,
            Component.literal(ConfigManager.cocoonAlertText).withStyle(net.minecraft.ChatFormatting.RED), Component.empty());
        var sound = net.minecraft.resources.Identifier.tryParse(ConfigManager.cocoonAlertSound);
        if (!ConfigManager.cocoonAlertSound.isBlank() && sound != null) client.getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvent.createVariableRangeEvent(sound), 1, 1));
    }

    private static Component cocoonLabel(double seconds) {
        return Component.literal("Cocoon: ").append(Component.literal(String.format(Locale.ROOT, "%.1fs", seconds))
            .withStyle(net.minecraft.ChatFormatting.RED));
    }

    private static final Component[] HUD_PREVIEWS = {Component.literal("Storm  12.5s"), Component.literal("Fresh Tools  6.5s"),
        cocoonLabel(4.6), KuudraTracker.supplyLabel(67), KuudraTracker.timerLabel(false, 4.5),
        KuudraTracker.timerLabel(true, 4.5), TrevorHelper.cooldownLabel(124)};

    public static int hudCount() { return HUD_PREVIEWS.length; }
    public static Component hudPreview(int id) { return HUD_PREVIEWS[id]; }

    public static float hudScale(int id) {
        var scales = ConfigManager.gameplayHudScales;
        float scale = id < scales.length ? scales[id] : 0;
        return scale > 0 && Float.isFinite(scale) ? Math.clamp(scale, .2f, 5f) : 1f;
    }

    public static void scaleHud(int id, double scroll) {
        float scale = Math.clamp(Math.round((hudScale(id) + Math.signum(scroll) * .1) * 10) / 10f, .2f, 5f);
        if (scale == hudScale(id)) return;
        var scales = java.util.Arrays.copyOf(ConfigManager.gameplayHudScales, hudCount());
        scales[id] = scale;
        ConfigManager.gameplayHudScales = scales;
        ConfigManager.requestSave();
    }

    private static void draw(GuiGraphicsExtractor graphics, Component text, int id) {
        var font = Minecraft.getInstance().font;
        int x = hudX(id, graphics.guiWidth()), y = hudY(id, graphics.guiHeight());
        float scale = hudScale(id);
        if (scale == 1f) {
            graphics.text(font, text, x, y, 0xFFF3E9F0, true);
            return;
        }
        var pose = graphics.pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            pose.scale(scale, scale);
            graphics.text(font, text, 0, 0, 0xFFF3E9F0, true);
        } finally { pose.popMatrix(); }
    }
}
