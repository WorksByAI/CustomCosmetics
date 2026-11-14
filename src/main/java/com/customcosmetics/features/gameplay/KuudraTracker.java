// SPDX-License-Identifier: BSD-3-Clause
// Adapted from Athen's Kuudra phase detection and Nebulune's Kuudra Peek, copyright Starred.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.PartyRenderPipeline;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.map.DungeonSidebarReader;
import com.customcosmetics.features.map.MapEditor;
import com.customcosmetics.features.party.PartyGeometry;
import com.customcosmetics.gui.UiText;
import com.customcosmetics.utils.MessageUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Giant;
import net.minecraft.world.entity.monster.cubemob.MagmaCube;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.regex.Pattern;

final class KuudraTracker {
    private static final Pattern TIER = Pattern.compile("Kuudra's Hollow \\(T([1-5])\\)");
    private static final Pattern STUNNED = Pattern.compile("^\\w+ destroyed one of Kuudra's pods!$");
    private static final Pattern EATEN = Pattern.compile("^\\w+ has been eaten by Kuudra[!.]$");
    private static final Pattern MANA_USED = Pattern.compile("^Used Extreme Focus! \\((\\d+) Mana\\)$");
    private static final Pattern DOWNTIME = Pattern.compile("^Party > (?:\\[[^]]*] )?\\w{1,16}(?: [ቾ⚒])?: ?!dt$");
    private static final Pattern SUPPLY_CHAT = Pattern.compile("(?:\\[[^]]*] )?(\\w+) recovered one of Elle's supplies! \\((\\d+)/(\\d+)\\)");
    private static final Pattern SUPPLY_PROGRESS = Pattern.compile("^\\[[| ]+]\\s*(\\d+)%$");
    private static final String SUPPLY_HEAD = "24bbfd9d84f42456cd02a4baa5cd054bced0ddb2d1c8321c83e5d667cd85575a";
    private static final String FUEL_HEAD = "fd72deb1ab0033b42b0a12efcf483f0fba26dc51ded3919ebadb70f9657a1f61";
    private static final java.util.Set<BlockPos> DROP_OFFS = java.util.Set.of(new BlockPos(-98, 78, -113), new BlockPos(-99, 78, -100),
        new BlockPos(-110, 78, -107), new BlockPos(-106, 78, -113), new BlockPos(-94, 78, -106), new BlockPos(-107, 78, -100));
    private static final String[] PHASES = {"Supply", "Build", "Fuel", "Stun", "DPS", "Kill"};
    private static int phase = -1, tier;
    private static long started;
    private static String peekSide = "";
    private static int requeueAt = -1, pearlAt = -1, pearlSlot = -1;
    private static boolean downtime, requeueHandled;
    private static MagmaCube rendTarget;
    private static float previousHealth;
    private static java.util.List<? extends Player> teammates = java.util.List.of();
    private record SupplyMarker(AABB box, int color, boolean beam) {}
    private static java.util.List<SupplyMarker> supplyMarkers = java.util.List.of();
    private static Component supplyProgress;
    private static int progressExpires;
    private static Component phaseTimer;

    private KuudraTracker() {}

    static void reset() {
        phase = -1;
        tier = 0;
        started = 0;
        peekSide = "";
        requeueAt = pearlAt = pearlSlot = -1;
        downtime = requeueHandled = false;
        rendTarget = null;
        teammates = java.util.List.of();
        supplyMarkers = java.util.List.of();
        supplyProgress = null;
        phaseTimer = null;
        KuudraStunHelper.reset();
    }

    private static boolean enabled() {
        return GameplayModule.KUUDRA_SPLITS.enabled() || GameplayModule.KUUDRA_PEEK.enabled()
            || GameplayModule.KUUDRA_AUTO_PEARL.enabled() || GameplayModule.KUUDRA_REND_DAMAGE.enabled()
            || GameplayModule.KUUDRA_SUPPLY_WAYPOINTS.enabled() || GameplayModule.KUUDRA_TITLES.enabled()
            || GameplayModule.KUUDRA_STUN_HELPER.enabled() || GameplayModule.KUUDRA_TIMERS.enabled();
    }

    static boolean highTierRun() { return phase >= 0 && tier >= 3; }

    static boolean allowMessage(Minecraft client, Component message) {
        if (!GameplayModule.KUUDRA_SUPPLY_WAYPOINTS.enabled() || !ConfigManager.kuudraSupplyMessages || phase != 0) return true;
        String value = message.getString();
        if (!value.contains("recovered one of Elle's supplies!") || !MapEditor.automaticKuudra(client)) return true;
        var match = SUPPLY_CHAT.matcher(value);
        if (!match.find()) return true;
        String time = String.format(Locale.ROOT, "%.1fs", (System.nanoTime() - started) / 1_000_000_000.0);
        String line = ConfigManager.kuudraSupplyMessage.replace("#user", match.group(1)).replace("#time", time)
            .replace("#cur", match.group(2)).replace("#max", match.group(3));
        ClientUiAccess.chat(client).addClientSystemMessage(Component.literal(line));
        return false;
    }

    static void message(Minecraft client, String text) {
        boolean trackPhase = enabled();
        boolean manaAlert = GameplayModule.MANA_USED_ALERT.enabled() && text.startsWith("Used Extreme Focus!");
        boolean requeue = GameplayModule.KUUDRA_REQUEUE.enabled();
        if ((!trackPhase && !manaAlert && !requeue) || !MapEditor.automaticKuudra(client)) return;
        if (manaAlert) announceManaUsed(client, text);
        if (GameplayModule.KUUDRA_TITLES.enabled()) supplyTitle(client, text);
        if (GameplayModule.KUUDRA_STUN_HELPER.enabled() && highTierRun()) {
            if (text.equals("You purchased Human Cannonball!")) KuudraStunHelper.purchase(client);
            else if (text.endsWith("destroyed one of Kuudra's pods!") && STUNNED.matcher(text).matches()) KuudraStunHelper.reset();
        }
        if (requeue && client.player != null) {
            if (text.startsWith("Party > ") && DOWNTIME.matcher(text.strip()).matches()) { downtime = true; requeueAt = -1; }
            else if (text.strip().startsWith("KUUDRA DOWN!") && !requeueHandled) {
                requeueHandled = true;
                if (!downtime) requeueAt = client.player.tickCount + ConfigManager.kuudraRequeueDelay;
                downtime = false;
            }
        }
        if (!trackPhase) return;
        int next = switch (text) {
            case "[NPC] Elle: Okay adventurers, I will go and fish up Kuudra!" -> 0;
            case "[NPC] Elle: OMG! Great work collecting my supplies!" -> 1;
            case "[NPC] Elle: Phew! The Ballista is finally ready! It should be strong enough to tank Kuudra's blows now!" -> 2;
            case "[NPC] Elle: POW! SURELY THAT'S IT! I don't think he has any more in him!" -> 5;
            default -> -1;
        };
        if (next < 0 && phase >= 0) {
            String line = text.strip();
            if (line.startsWith("KUUDRA DOWN!") || line.startsWith("DEFEAT")) next = PHASES.length;
            else if (phase < 3 && EATEN.matcher(line).matches() && !line.startsWith("Elle ")) next = 3;
            else if (phase < 4 && STUNNED.matcher(line).matches()) next = 4;
        }
        if (next < 0 || next == phase || next > 0 && next <= phase) return;
        long now = System.nanoTime();
        if (next > 0 && phase >= 0 && GameplayModule.KUUDRA_SPLITS.enabled()) {
            var split = UiText.tr(PHASES[phase]) + " · " + String.format(Locale.ROOT, "%.1fs", (now - started) / 1_000_000_000.0);
            ClientUiAccess.chat(client).addClientSystemMessage(MessageUtils.createMessageWithPrefix(split));
        }
        if (next == PHASES.length) {
            phase = -1;
            tier = 0;
            rendTarget = null;
            KuudraStunHelper.reset();
        }
        else {
            phase = next;
            started = now;
        }
    }

    private static void supplyTitle(Minecraft client, String text) {
        boolean pickup = text.equals("You retrieved some of Elle's supplies from the Lava!")
            || text.equals("You retrieved a Ballista Fuel Cell from the Lava!");
        boolean drop = text.equals("You moved and the Chest slipped out of your hands!");
        if (!pickup && !drop) return;
        supplyProgress = null;
        if (pickup && ConfigManager.kuudraPickupAlert) ClientUiAccess.title(client,
            Component.literal(ConfigManager.kuudraPickupText).withStyle(net.minecraft.ChatFormatting.GREEN), Component.empty());
        if (drop && ConfigManager.kuudraDropAlert) ClientUiAccess.title(client,
            Component.literal(ConfigManager.kuudraDropText).withStyle(net.minecraft.ChatFormatting.RED), Component.empty());
    }

    static boolean replaceSupplyTitle(Minecraft client, Component title) {
        if (!GameplayModule.KUUDRA_TITLES.enabled() || !ConfigManager.kuudraSupplyHud || (phase != 0 && phase != 2)
                || client.player == null) return false;
        var progress = SUPPLY_PROGRESS.matcher(net.minecraft.ChatFormatting.stripFormatting(title.getString()));
        if (!progress.matches()) return false;
        supplyProgress = supplyLabel(Integer.parseInt(progress.group(1)));
        progressExpires = client.player.tickCount + 30;
        return true;
    }

    static Component supplyHud() {
        return GameplayModule.KUUDRA_TITLES.enabled() && ConfigManager.kuudraSupplyHud && (phase == 0 || phase == 2) ? supplyProgress : null;
    }

    static Component supplyLabel(int progress) {
        int total = ConfigManager.kuudraTitleBars;
        int filled = Math.clamp(progress, 0, 100) * total / 100;
        return Component.literal(ConfigManager.kuudraTitleFormat.replace("#perc", Integer.toString(progress))
            .replace("#bars", ConfigManager.kuudraTitleFilled.repeat(filled))
            .replace("#total", ConfigManager.kuudraTitleEmpty.repeat(total - filled)));
    }

    // Adapted from Athen's ManaUsedAlert, with the party announcement sent as a command.
    private static void announceManaUsed(Minecraft client, String text) {
        if (client.player == null || client.level == null || client.getConnection() == null) return;
        var message = MANA_USED.matcher(text);
        if (!message.matches()) return;
        int nearby = 0;
        for (var player : client.level.players()) {
            if (player != client.player && player.getUUID().version() == 4 && client.player.distanceToSqr(player) <= 25) nearby++;
        }
        if (nearby == 0 && ConfigManager.kuudraManaIgnoreZero) return;
        String alert = message.group(1) + " mana used on " + nearby + " players!";
        client.getConnection().sendCommand("pc " + alert);
        MessageUtils.sendMessage(alert);
    }

    static boolean useSupply(Minecraft client) {
        if (!GameplayModule.KUUDRA_AUTO_PEARL.enabled() || phase != 0 || pearlAt >= 0 || client.player == null
                || ClientUiAccess.screen(client) != null || !"Elle's Supplies".equals(client.player.getMainHandItem().getHoverName().getString())) return false;
        var inventory = client.player.getInventory();
        for (int i = 0; i < 9; i++) {
            if (inventory.getItem(i).is(Items.ENDER_PEARL)) {
                inventory.setSelectedSlot(i);
                pearlSlot = i;
                pearlAt = client.player.tickCount + ConfigManager.kuudraPearlDelay;
                return true;
            }
        }
        return false;
    }

    static void tickActions(Minecraft client) {
        KuudraStunHelper.tick(client);
        if (supplyProgress != null && client.player.tickCount >= progressExpires) supplyProgress = null;
        if (requeueAt >= 0 && client.player.tickCount >= requeueAt) {
            requeueAt = -1;
            if (GameplayModule.KUUDRA_REQUEUE.enabled() && MapEditor.automaticKuudra(client)) client.getConnection().sendCommand("instancerequeue");
        }
        if (pearlAt >= 0 && client.player.tickCount >= pearlAt) {
            pearlAt = -1;
            var inventory = client.player.getInventory();
            if (GameplayModule.KUUDRA_AUTO_PEARL.enabled() && phase == 0 && client.gameMode != null
                    && ClientUiAccess.screen(client) == null && inventory.getSelectedSlot() == pearlSlot
                    && inventory.getItem(pearlSlot).is(Items.ENDER_PEARL)) client.gameMode.useItem(client.player, net.minecraft.world.InteractionHand.MAIN_HAND);
        }
    }

    static void tick(Minecraft client) {
        if (GameplayModule.KUUDRA_TIMERS.enabled()) {
            double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
            if (phase == 0 && ConfigManager.kuudraSpawnTimer && elapsed < 8.9) phaseTimer = timerLabel(false, 8.9 - elapsed);
            else if (phase == 1 && ConfigManager.kuudraBuildTimer && elapsed < 5.1) phaseTimer = timerLabel(true, 5.1 - elapsed);
            else phaseTimer = null;
        } else phaseTimer = null;
        teammates = GameplayModule.KUUDRA_TEAMMATES.enabled() && MapEditor.automaticKuudra(client)
            ? client.level.players().stream().filter(other -> other != client.player && other.getUUID().version() == 4).toList()
            : java.util.List.of();
        if (GameplayModule.KUUDRA_SUPPLY_WAYPOINTS.enabled() && (phase == 0 || phase == 2)) updateSupplyMarkers(client);
        else supplyMarkers = java.util.List.of();
        boolean peek = GameplayModule.KUUDRA_PEEK.enabled() && (!ConfigManager.kuudraPeekCheckHeight || client.player.getY() < 25);
        boolean rend = GameplayModule.KUUDRA_REND_DAMAGE.enabled();
        boolean stun = GameplayModule.KUUDRA_STUN_HELPER.enabled();
        if (tier == 0 && phase >= 0 && (stun || phase == 5 && (peek || rend))) {
            for (String line : DungeonSidebarReader.read(client).rows()) {
                var match = TIER.matcher(line);
                if (match.find()) {
                    tier = Integer.parseInt(match.group(1));
                    break;
                }
            }
        }
        if (phase != 5 || (!peek && !rend)) return;
        if (tier != 5) return;
        for (var entity : client.level.getEntitiesOfClass(MagmaCube.class, client.player.getBoundingBox().inflate(110))) {
            if (entity.getSize() != 30 || entity.getMaxHealth() != 100_000) continue;
            float current = entity.getHealth();
            if (rend) {
                float difference = previousHealth - current;
                if (entity == rendTarget && current <= 25_000 && difference > 1666 && client.player.getY() <= 30) {
                    double seconds = (System.nanoTime() - started) / 1_000_000_000.0;
                    MessageUtils.sendMessage(String.format(Locale.ROOT, "Detected %.2fM damage at %.1fs!", difference * .0096, seconds));
                }
                rendTarget = entity;
                previousHealth = current;
            }
            if (!peek || ConfigManager.kuudraPeekFirstOnly && (current < 24_500 || current > 25_000)) return;
            double x = entity.getX(), z = entity.getZ();
            String side = x < -128 ? "Right" : x > -72 ? "Left" : z > -84 ? "Front" : z < -132 ? "Back" : "";
            if (!side.equals(peekSide)) {
                peekSide = side;
                if (!side.isEmpty()) {
                    MessageUtils.sendMessage(UiText.tr("Kuudra peeked from ") + UiText.tr(side) + "!");
                    ClientUiAccess.title(client, UiText.component(side + "!").withStyle(net.minecraft.ChatFormatting.RED), Component.empty());
                }
            }
            break;
        }
    }

    static Component phaseTimer(boolean build) {
        return GameplayModule.KUUDRA_TIMERS.enabled() && phase == (build ? 1 : 0) ? phaseTimer : null;
    }

    static Component timerLabel(boolean build, double time) {
        String format = build ? ConfigManager.kuudraBuildFormat : ConfigManager.kuudraSpawnFormat;
        return Component.literal(format.replace("#time", String.format(Locale.ROOT, "%.1fs", time)));
    }

    // Athen SupplyWaypoints and AbstractSupply, with cached shape data for the render callback.
    private static void updateSupplyMarkers(Minecraft client) {
        var markers = new java.util.ArrayList<SupplyMarker>();
        var search = client.player.getBoundingBox().inflate(115);
        if (phase == 0 && ConfigManager.kuudraSupplyDropOff) {
            var received = new java.util.HashSet<BlockPos>();
            for (var stand : client.level.getEntitiesOfClass(ArmorStand.class, search)) {
                if (stand.hasCustomName() && stand.getCustomName().getString().equals("✓ SUPPLIES RECEIVED ✓")) received.add(stand.blockPosition());
            }
            for (var pos : DROP_OFFS) if (!received.contains(pos)) markers.add(new SupplyMarker(new AABB(pos), ConfigManager.kuudraDropOffColor, false));
        }
        if (phase == 0 ? ConfigManager.kuudraSupplyPickup : ConfigManager.kuudraFuel) {
            for (var giant : client.level.getEntitiesOfClass(Giant.class, search)) {
                var head = giant.getMainHandItem().get(DataComponents.PROFILE);
                if (head == null) continue;
                boolean valid = false;
                for (var property : head.partialProfile().properties().get("textures")) {
                    try {
                        String hash = com.customcosmetics.features.items.HeadTexture.normalize(property.value());
                        valid = SUPPLY_HEAD.equals(hash) || FUEL_HEAD.equals(hash);
                    } catch (IllegalArgumentException ignored) { }
                    if (valid) break;
                }
                if (!valid) continue;
                double angle = Math.toRadians(giant.getYRot() + 130), x = giant.getX() + 3.7 * Math.cos(angle), z = giant.getZ() + 3.7 * Math.sin(angle);
                var bounds = new AABB(x - 1, 74.5, z, x + 2, 76.5, z + 3);
                var proximity = bounds.inflate(4, 5, 4);
                var pos = new BlockPos((int) bounds.getCenter().x, 75, (int) bounds.getCenter().z);
                int color = phase == 0 ? ConfigManager.kuudraPickupColor : ConfigManager.kuudraFuelColor;
                if (ConfigManager.kuudraSupplyProximity && client.level.players().stream().anyMatch(player -> proximity.contains(player.position())))
                    color = ConfigManager.kuudraNearbyColor;
                markers.add(new SupplyMarker(new AABB(pos), color, true));
            }
        }
        supplyMarkers = markers;
    }

    static void render(net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext context) {
        boolean team = !teammates.isEmpty() && GameplayModule.KUUDRA_TEAMMATES.enabled();
        boolean supplies = !supplyMarkers.isEmpty() && GameplayModule.KUUDRA_SUPPLY_WAYPOINTS.enabled();
        if (!team && !supplies) return;
        var camera = context.levelState().cameraRenderState.pos;
        var pose = new com.mojang.blaze3d.vertex.PoseStack();
        int color = 0xFF000000 | ConfigManager.kuudraTeammateColor;
        double width = .01 * ConfigManager.kuudraTeammateWidth;
        if (team) for (var player : teammates) {
            if (player.isRemoved()) continue;
            var box = player.getBoundingBox().move(-camera.x, -camera.y, -camera.z);
            context.submitNodeCollector().submitCustomGeometry(pose, PartyRenderPipeline.type(), (matrices, output) ->
                PartyGeometry.outline(output, matrices.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, width, color));
        }
        if (supplies) for (var marker : supplyMarkers) {
            var box = marker.box().move(-camera.x, -camera.y, -camera.z);
            int rgb = marker.color();
            context.submitNodeCollector().submitCustomGeometry(pose, PartyRenderPipeline.type(), (matrices, output) -> {
                if (marker.beam()) {
                    PartyGeometry.outline(output, matrices.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, .025, 0xFF000000 | rgb);
                    double x = box.minX + .5, z = box.minZ + .5;
                    PartyGeometry.box(output, matrices.pose(), x - .085, box.maxY, z - .085, x + .085, box.maxY + 180, z + .085, 0x55000000 | rgb);
                } else PartyGeometry.box(output, matrices.pose(), box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, 0x55000000 | rgb);
            });
        }
    }
}
