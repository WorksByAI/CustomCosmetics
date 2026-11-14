// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Quoi's GreatSpook and AutoCarnival.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.map.Freecam;
import com.customcosmetics.features.map.MapEditor;
import com.customcosmetics.features.pack.LegacyPackItems;
import com.customcosmetics.features.waypoint.Waypoints;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.regex.Pattern;

public final class SeasonalGames {
    private static final String MATH = "QUICK MATHS! Solve: ";
    private static final String SIGN = "[FEAR] Commitment Phobia: Please sign this paper!\n";
    private static final Pattern SPEAK = Pattern.compile("\\[FEAR] Public Speaking Demon: (?:Speak|Say something interesting) (\\w{1,16})!");
    private static final String[] ANSWERS = {"The Great Spook is here!", "Time to face another Primal Fear.", "Spooky monsters are back in SkyBlock."};
    private static final Identifier CARNIVAL_LAMP = Identifier.withDefaultNamespace("redstone_lamp");
    private static final ArrayDeque<Answer> answers = new ArrayDeque<>();
    private record Answer(String command, int tick) {}
    private static int answerIndex;
    private static Vec3 lamp;
    private static long nextShot;
    private SeasonalGames() {}

    static void reset() { answers.clear(); lamp = null; nextShot = 0; }

    static void message(Minecraft client, Component message, String text) {
        if (!GameplayModule.GREAT_SPOOK.enabled() || client.player == null || client.getConnection() == null) return;
        if (ConfigManager.spookTitle && text.equals("FEAR. A Primal Fear has been summoned!")
                && (ConfigManager.spookDungeon || !MapEditor.automaticDungeon(client))) {
            ClientUiAccess.title(client, Component.literal("Primal Fear spawned!").withStyle(ChatFormatting.LIGHT_PURPLE), Component.empty());
        }
        String command = null;
        if (ConfigManager.spookMath && text.startsWith(MATH)) {
            Integer result = evalMath(text.substring(MATH.length()));
            if (result != null) command = "ac " + result;
        } else if (ConfigManager.spookSpeaking && text.startsWith("[FEAR] Public Speaking Demon:")) {
            var matcher = SPEAK.matcher(text);
            if (matcher.matches() && matcher.group(1).equalsIgnoreCase(client.player.getGameProfile().name())) {
                command = "ac " + ANSWERS[answerIndex];
                answerIndex = (answerIndex + 1) % ANSWERS.length;
            }
        } else if (ConfigManager.spookCommitment) {
            String line = text.strip();
            if (line.startsWith(SIGN)) line = line.substring(SIGN.length());
            if (line.startsWith("Click HERE to sign the ") && line.endsWith(".")) {
                command = message.visit((style, part) -> part.contains("HERE") && style.getClickEvent() instanceof ClickEvent.RunCommand click
                    ? Optional.of(click.command()) : Optional.<String>empty(), Style.EMPTY).orElse(null);
                if (command != null) command = command.startsWith("/") && command.length() > 1 ? command.substring(1) : null;
            }
        }
        if (command == null) return;
        if (ConfigManager.spookDelay == 0) client.getConnection().sendCommand(command);
        else if (answers.size() < 8) answers.add(new Answer(command, client.player.tickCount + ConfigManager.spookDelay));
    }

    static Integer evalMath(String expression) {
        if (expression.length() > 128) return null;
        try {
            var parser = new Maths(expression.replace('x', '*').replace('\u00d7', '*').replace('\u00f7', '/'));
            int value = parser.sum();
            parser.space();
            return parser.index == parser.text.length() ? value : null;
        } catch (IllegalArgumentException | ArithmeticException invalid) { return null; }
    }

    private static final class Maths {
        final String text;
        int index;
        Maths(String text) { this.text = text; }
        void space() { while (index < text.length() && Character.isWhitespace(text.charAt(index))) index++; }
        boolean take(char token) { space(); if (index == text.length() || text.charAt(index) != token) return false; index++; return true; }
        int sum() {
            int value = product();
            while (true) {
                if (take('+')) value = Math.addExact(value, product());
                else if (take('-')) value = Math.subtractExact(value, product());
                else return value;
            }
        }
        int product() {
            int value = atom();
            while (true) {
                if (take('*')) value = Math.multiplyExact(value, atom());
                else if (take('/')) {
                    int divisor = atom();
                    if (divisor == 0 || value % divisor != 0 || value == Integer.MIN_VALUE && divisor == -1) throw new ArithmeticException();
                    value /= divisor;
                } else return value;
            }
        }
        int atom() {
            if (take('(')) { int value = sum(); if (!take(')')) throw new IllegalArgumentException(); return value; }
            if (take('-')) return Math.negateExact(atom());
            if (take('+')) return atom();
            int start = index;
            while (index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9') index++;
            return Integer.parseInt(text.substring(start, index));
        }
    }

    static void tick(Minecraft client) {
        if (!GameplayModule.GREAT_SPOOK.enabled()) answers.clear();
        else if (client.getConnection() != null)
            while (!answers.isEmpty() && answers.peek().tick() <= client.player.tickCount) client.getConnection().sendCommand(answers.remove().command());
        if (!carnival(client)) { lamp = null; return; }
        long now = System.nanoTime();
        if (now < nextShot || ClientUiAccess.screen(client) != null || Freecam.active() || !client.player.isAlive() || client.gameMode == null) return;
        Vec3 target = lamp;
        if (target == null) {
            Zombie selected = null;
            int best = 4;
            double distance = Double.POSITIVE_INFINITY;
            for (var zombie : client.level.getEntitiesOfClass(Zombie.class, client.player.getBoundingBox().inflate(50))) {
                if (!zombie.isAlive()) continue;
                var helmet = zombie.getItemBySlot(EquipmentSlot.HEAD);
                int rank = helmet.is(Items.DIAMOND_HELMET) ? 0 : helmet.is(Items.GOLDEN_HELMET) ? 1
                    : helmet.is(Items.IRON_HELMET) ? 2 : helmet.is(Items.LEATHER_HELMET) ? 3 : 4;
                double d = zombie.distanceToSqr(client.player);
                if (rank < best || rank == best && rank < 4 && d < distance) { best = rank; distance = d; selected = zombie; }
            }
            if (selected != null) {
                var velocity = selected.getDeltaMovement();
                target = selected.getEyePosition().add(velocity.x * 8, 0, velocity.z * 8);
            }
        }
        if (target == null) return;
        client.player.lookAt(net.minecraft.commands.arguments.EntityAnchorArgument.Anchor.EYES, target);
        client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
        nextShot = now + ConfigManager.carnivalShotDelay * 1_000_000L;
    }

    private static boolean carnival(Minecraft client) {
        return GameplayModule.AUTO_CARNIVAL.enabled() && client.player != null && client.level != null
            && "hub".equals(Waypoints.currentIslandMode()) && "CARNIVAL_DART_TUBE".equals(LegacyPackItems.id(client.player.getMainHandItem()));
    }

    static void blockChanging(Minecraft client, BlockPos pos, BlockState next) {
        if (!GameplayModule.AUTO_CARNIVAL.enabled() || !next.hasProperty(BlockStateProperties.LIT)
                || !CARNIVAL_LAMP.equals(BuiltInRegistries.BLOCK.getKey(next.getBlock()))
                || !carnival(client) || !PresenceWorld.isHypixel(client) || pos.distToCenterSqr(client.player.position()) > 2500) return;
        var old = client.level.getBlockState(pos);
        if (!old.is(next.getBlock())) return;
        boolean wasLit = old.getValue(BlockStateProperties.LIT), lit = next.getValue(BlockStateProperties.LIT);
        if (!wasLit && lit) lamp = Vec3.atCenterOf(pos);
        else if (wasLit && !lit && lamp != null && BlockPos.containing(lamp).equals(pos)) lamp = null;
    }
}
