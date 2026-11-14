// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Quoi AutoLeap, LeapSettings, Floor7, Stage and LeapManager by jcnlk and contributors.
package com.customcosmetics.features.gameplay;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.features.map.MapEditor;
import com.customcosmetics.features.pack.LegacyPackItems;
import com.customcosmetics.utils.MessageUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class AutoLeap {
    private static final Pattern DOOR = Pattern.compile("^(?:\\[\\w+] )?(\\w+) opened a (?:WITHER|Blood) door!$");
    private static final Pattern OBJECTIVE = Pattern.compile("^([A-Za-z0-9_]{1,16}) (?:activated|completed) a (terminal|lever|device)! \\((\\d)/(\\d)\\)$");
    private static final Pattern PARTY = Pattern.compile("^Party > (?:\\[[^]]+] )*([A-Za-z0-9_]{1,16}): (.+)$");
    private static final Pattern TEAMMATE = Pattern.compile("^\\[\\d+] (?:\\[[^]]+] )*([A-Za-z0-9_]{1,16}) .*?\\((\\w+)(?: [IVXLCDM]+)?\\)$");
    private static final Set<String> MELODY = Set.of("1/3", "2/3", "33%", "66%", "67%");
    private static final Set<String> RELICS = Set.of("GREEN_KING_RELIC", "PURPLE_KING_RELIC", "BLUE_KING_RELIC", "ORANGE_KING_RELIC", "RED_KING_RELIC");
    private static final AABB I4 = new AABB(62, 127, 34, 65, 130, 37);
    private static final AABB MIDDLE = new AABB(47, 64, 69, 62, 75, 84);
    private static final AABB[] CRYSTALS = {new AABB(78, 233, 46, 87, 243, 55), new AABB(60, 233, 46, 69, 243, 55)};
    private static final AABB YELLOW_PAD = new AABB(24, 170, 86, 41, 172, 103);
    private static final AABB PURPLE_PAD = new AABB(106, 170, 86, 125, 172, 103);
    private static final AABB BETWEEN_PADS = new AABB(41, 163, 86, 106, 172, 103);
    private static final DungeonClass[] CLASSES = DungeonClass.values();

    public enum DungeonClass {
        HEALER, MAGE, BERSERK, ARCHER, TANK;
        public static DungeonClass parse(String value) {
            for (var type : CLASSES) if (type.name().equalsIgnoreCase(value)) return type;
            return null;
        }
    }

    public record Preset(boolean byName, boolean swapBack, int clickDelay, boolean waitForGate, boolean melody,
                         boolean stopAfterBlood, Set<String> triggers, java.util.Map<String, String> names,
                         java.util.Map<String, String> classes, FastPreset fast) {
        public Preset {
            clickDelay = Math.clamp(clickDelay, 0, 10);
            triggers = triggers == null ? Set.of() : Set.copyOf(triggers);
            names = names == null ? java.util.Map.of() : java.util.Map.copyOf(names);
            classes = classes == null ? java.util.Map.of() : java.util.Map.copyOf(classes);
        }
    }

    public record FastPreset(String fallback, Set<String> triggers, java.util.Map<String, String> names,
                             java.util.Map<String, String> classes, boolean stopAfterBlood, boolean melody) {
        public FastPreset {
            fallback = fallback == null || !fallback.matches("[A-Za-z0-9_]{1,16}") ? "" : fallback;
            triggers = triggers == null ? Set.of() : Set.copyOf(triggers);
            names = names == null ? java.util.Map.of() : java.util.Map.copyOf(names);
            classes = classes == null ? java.util.Map.of() : java.util.Map.copyOf(classes);
        }
    }

    public static boolean preset(String input, boolean save) {
        String name = input.strip();
        if (name.isEmpty() || name.length() > 32) return false;
        var saved = ConfigManager.autoLeapPresets;
        String key = saved.keySet().stream().filter(existing -> existing.equalsIgnoreCase(name)).findFirst().orElse(name);
        if (save) {
            var copy = new java.util.LinkedHashMap<>(saved);
            copy.put(key, new Preset(ConfigManager.autoLeapByName, ConfigManager.autoLeapSwapBack, ConfigManager.autoLeapDelay,
                ConfigManager.autoLeapGate, ConfigManager.autoLeapMelody, ConfigManager.autoLeapAfterBlood,
                ConfigManager.autoLeapTriggers, ConfigManager.autoLeapNames, ConfigManager.autoLeapClasses,
                new FastPreset(ConfigManager.fastLeapTarget, ConfigManager.fastLeapTriggers, ConfigManager.fastLeapNames,
                    ConfigManager.fastLeapClasses, ConfigManager.fastLeapAfterBlood, ConfigManager.fastLeapMelody)));
            ConfigManager.autoLeapPresets = java.util.Map.copyOf(copy);
        } else {
            Preset value = saved.get(key);
            if (value == null) return false;
            ConfigManager.autoLeapByName = value.byName();
            ConfigManager.autoLeapSwapBack = value.swapBack();
            ConfigManager.autoLeapDelay = value.clickDelay();
            ConfigManager.autoLeapGate = value.waitForGate();
            ConfigManager.autoLeapMelody = value.melody();
            ConfigManager.autoLeapAfterBlood = value.stopAfterBlood();
            ConfigManager.autoLeapTriggers = value.triggers();
            ConfigManager.autoLeapNames = value.names();
            ConfigManager.autoLeapClasses = value.classes();
            if (value.fast() != null) {
                ConfigManager.fastLeapTarget = value.fast().fallback();
                ConfigManager.fastLeapTriggers = value.fast().triggers();
                ConfigManager.fastLeapNames = value.fast().names();
                ConfigManager.fastLeapClasses = value.fast().classes();
                ConfigManager.fastLeapAfterBlood = value.fast().stopAfterBlood();
                ConfigManager.fastLeapMelody = value.fast().melody();
            }
        }
        ConfigManager.requestSave();
        return true;
    }

    public enum Trigger {
        DOOR("door", "Door Opener Leap"), CRYSTAL("crystal", "Crystal Leap"), P1("p1", "P1 Leap"),
        FIRST_CRUSH("first_crush", "First Crush Leap"), SECOND_CRUSH("second_crush", "Second Crush Leap"),
        STORM_ENRAGE("storm_enrage", "Storm Enrage Leap"), STORM_DEATH("storm_death", "Storm Death Leap"),
        PREDEV("predev", "Predev Leap"), I4("i4", "I4 Leap"), P3("p3", "P3 Leap"),
        S1("s1", "S1 Leap"), S2("s2", "S2 Leap"), S3("s3", "S3 Leap"), S4("s4", "S4 Leap"),
        MIDDLE("middle", "Middle Leap"), P4("p4", "P4 Leap"), RELIC("relic", "Relic Leap");

        public final String id, label;
        Trigger(String id, String label) { this.id = id; this.label = label; }
        public boolean enabled() { return ConfigManager.autoLeapTriggers.contains(id); }
        public boolean hasToggle() { return this.ordinal() < S1.ordinal() || this.ordinal() > S4.ordinal(); }
        public boolean hasTarget() { return this != DOOR && this != P3; }
    }

    public enum FastTrigger {
        DOOR("Door Opener Leap"), CRYSTAL("Crystal Leap"), PREDEV("Predev Leap"), RELIC("Relic Leap"), P1("P1 Leap"),
        P4("P4 Leap"), YELLOW("Yellow Pad Leap"), PURPLE("Purple Pad Leap"), BETWEEN("Between PY Pad Leap"),
        STORM_DEATH("Storm Death Leap"), MIDDLE("Middle Leap"), I4("I4 Leap"), P3("P3 Leap"),
        S1("S1 Leap"), S2("S2 Leap"), S3("S3 Leap"), S4("S4 Leap");

        private static final FastTrigger[] SECTIONS = {S1, S2, S3, S4};
        public final String id = name().toLowerCase(Locale.ROOT), label;
        FastTrigger(String label) { this.label = label; }
        public boolean enabled() { return ConfigManager.fastLeapTriggers.contains(id); }
        public boolean hasToggle() { return ordinal() < S1.ordinal(); }
        public boolean hasTarget() { return this != DOOR && this != P3; }
    }

    private record Request(Pattern target, int expires, boolean manual) {}
    private Request pending, active;
    private int oldSlot = -1, leapSlot = -1, clickAt = -1, cooldown;
    private long lastCompletedLeap;
    private int phase, stage, crushes, crystals;
    private boolean gate, objectives, bloodOpen, p4Used, relicUsed;
    private String melody, doorOpener;

    void reset(Minecraft client) {
        finish(client);
        pending = null;
        phase = stage = crushes = crystals = cooldown = 0;
        gate = objectives = bloodOpen = p4Used = relicUsed = false;
        melody = null;
        doorOpener = null;
        lastCompletedLeap = 0;
    }

    void message(Minecraft client, String text) {
        if (!MapEditor.automaticDungeon(client) || client.player == null) return;
        if (text.equals("You cannot use this in a solo dungeon!") || text.equals("There are no other players to teleport to!")) {
            finish(client);
            pending = null;
            return;
        }
        if ((ConfigManager.autoLeapMelody || ConfigManager.fastLeapMelody) && text.startsWith("Party > ") && !text.endsWith(")")) {
            var party = PARTY.matcher(text);
            if (party.matches() && MELODY.stream().anyMatch(party.group(2)::contains)) melody = party.group(1);
        }
        if (text.equals("The BLOOD DOOR has been opened!")) bloodOpen = true;
        boolean automatic = GameplayModule.AUTO_LEAP.enabled();
        if ((GameplayModule.FAST_LEAP.enabled() || automatic && Trigger.DOOR.enabled()) && text.endsWith(" door!")) {
            var door = DOOR.matcher(text);
            if (door.matches()) {
                doorOpener = door.group(1);
                if (automatic && Trigger.DOOR.enabled() && (!ConfigManager.autoLeapAfterBlood || !bloodOpen)
                        && (phase == 0 || !inFloorSevenBoss(client))) request(client, doorOpener, false, false);
            }
        }
        if (!inFloorSevenBoss(client)) return;
        var pos = client.player.position();
        String playerName = client.player.getGameProfile().name();
        switch (text) {
            case "[BOSS] Maxor: WELL! WELL! WELL! LOOK WHO'S HERE!" -> phase = Math.max(phase, 1);
            case "[BOSS] Storm: Pathetic Maxor, just like expected." -> phase = Math.max(phase, 2);
            case "[BOSS] Goldor: Who dares trespass into my domain?" -> {
                if (phase < 3) { phase = 3; stage = 1; }
            }
            case "[BOSS] Necron: Finally, I heard so much about you. The Eye likes you very much.",
                 "[BOSS] Necron: You went further than any human before, congratulations." -> phase = Math.max(phase, 4);
            case "[BOSS] Storm: Oof", "[BOSS] Storm: Ouch, that hurt!" -> {
                if (phase == 2) {
                    if (++crushes == 1) trigger(client, Trigger.FIRST_CRUSH);
                    else if (crushes == 2) trigger(client, Trigger.SECOND_CRUSH);
                }
            }
            case "The Energy Laser is charging up!" -> {
                if (phase == 1 && ++crystals == 2 && pos.y > 210 && (!Trigger.CRYSTAL.enabled() || !onCrystal(pos))) trigger(client, Trigger.P1);
            }
            case "⚠ Storm is enraged! ⚠" -> { if (phase == 2) trigger(client, Trigger.STORM_ENRAGE); }
            case "[BOSS] Storm: I should have known that I stood no chance." -> { if (phase == 2) trigger(client, Trigger.STORM_DEATH); }
            case "[BOSS] Storm: I'd be happy to show you what that's like!" -> {
                if (phase >= 1 && phase <= 2 && phaseAt(pos.y) == 3) trigger(client, Trigger.PREDEV);
            }
            case "[BOSS] Necron: Goodbye." -> { if (phase == 4 && phaseAt(pos.y) == 4 && !MIDDLE.contains(pos)) trigger(client, Trigger.MIDDLE); }
            default -> {
                if (phase == 1 && onCrystal(pos) && text.equals(playerName + " picked up an Energy Crystal!")) trigger(client, Trigger.CRYSTAL);
            }
        }
        if (phase == 3 && stage >= 1 && stage <= 4) processStage(client, text, playerName, pos);
    }

    private void processStage(Minecraft client, String text, String player, Vec3 pos) {
        if (stage <= 2 && I4.contains(pos) && text.startsWith(player + " completed a device!")) trigger(client, Trigger.I4);
        if (text.equals("The Core entrance is opening!")) {
            if (stage == 4 && ConfigManager.autoLeapGate) leapFromSection(client, pos);
            stage = 5;
            return;
        }
        var match = OBJECTIVE.matcher(text);
        if (match.matches() && !objectives && match.group(3).equals(match.group(4)) && !match.group(4).equals("0")) {
            objectives = true;
            if (!ConfigManager.autoLeapGate) leapFromSection(client, pos);
        }
        if (text.equals("The gate has been destroyed!")) gate = true;
        if (stage < 4 && objectives && gate) {
            if (ConfigManager.autoLeapGate) leapFromSection(client, pos);
            stage++;
            gate = objectives = false;
        }
    }

    private void leapFromSection(Minecraft client, Vec3 pos) {
        int current = sectionAt(pos);
        if (Trigger.P3.enabled() && current > 0 && current <= stage && !I4.contains(pos))
            trigger(client, Trigger.values()[Trigger.S1.ordinal() + stage - 1]);
    }

    static int sectionAt(Vec3 pos) {
        if (phaseAt(pos.y) != 3) return 0;
        double x = pos.x, z = pos.z;
        if (x >= 89 && x <= 113 && z >= 30 && z <= 122) return 1;
        if (x >= 19 && x <= 111 && z >= 121 && z <= 145) return 2;
        if (x >= -6 && x <= 19 && z >= 51 && z <= 143) return 3;
        if (x >= -2 && x <= 90 && z >= 27 && z <= 51) return 4;
        if (x >= 41 && x <= 68 && z >= 59 && z <= 117) return 5;
        return 0;
    }

    private static int phaseAt(double y) { return y > 210 ? 1 : y > 155 ? 2 : y > 100 ? 3 : y > 45 ? 4 : 5; }

    private static boolean onCrystal(Vec3 pos) { return CRYSTALS[0].contains(pos) || CRYSTALS[1].contains(pos); }

    private static boolean inFloorSevenBoss(Minecraft client) {
        return client.player != null && GameplayFeatures.isFloorSeven() && client.player.getX() > -7 && client.player.getZ() > -7;
    }

    public void bossBar(Minecraft client, String name, float progress) {
        if (!inFloorSevenBoss(client) || !PresenceWorld.isSkyblock(client) || !MapEditor.automaticDungeon(client)) return;
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("wither king") && "M7".equals(MapEditor.floor())) phase = 5;
        if (phase == 4 && phaseAt(client.player.getY()) == 4 && !p4Used && Trigger.P4.enabled()
                && progress >= .001f && progress <= .051f && lower.contains("necron")) {
            p4Used = true;
            trigger(client, Trigger.P4);
        }
    }

    boolean fastLeap(Minecraft client) {
        boolean byName = ConfigManager.autoLeapByName;
        var pos = client.player.position();
        var target = fastTrigger(pos);
        if (target != null) {
            if (target == FastTrigger.DOOR) return request(client, doorOpener, false, true);
            if (target == FastTrigger.I4 && ConfigManager.fastLeapMelody && melody != null) return request(client, melody, false, true);
            var name = (byName ? ConfigManager.fastLeapNames : ConfigManager.fastLeapClasses).get(target.id);
            if (name != null && !name.isBlank()) return request(client, name, !byName, true);
        }
        return !ConfigManager.fastLeapTarget.isBlank() && request(client, ConfigManager.fastLeapTarget, false, true);
    }

    private FastTrigger fastTrigger(Vec3 pos) {
        if (phase == 0 || pos.x < -7 || pos.z < -7) return FastTrigger.DOOR.enabled() && doorOpener != null
            && (!ConfigManager.fastLeapAfterBlood || !bloodOpen) ? FastTrigger.DOOR : null;
        return switch (phase) {
            case 1 -> FastTrigger.CRYSTAL.enabled() && onCrystal(pos) ? FastTrigger.CRYSTAL
                : FastTrigger.PREDEV.enabled() && phaseAt(pos.y) == 3 ? FastTrigger.PREDEV
                : FastTrigger.P1.enabled() && phaseAt(pos.y) == 1 ? FastTrigger.P1 : null;
            case 2 -> {
                if (FastTrigger.PREDEV.enabled() && phaseAt(pos.y) == 3) yield FastTrigger.PREDEV;
                if (phaseAt(pos.y) != 2) yield null;
                if (YELLOW_PAD.contains(pos)) yield FastTrigger.YELLOW.enabled() ? FastTrigger.YELLOW : null;
                if (PURPLE_PAD.contains(pos)) yield FastTrigger.PURPLE.enabled() ? FastTrigger.PURPLE : null;
                if (BETWEEN_PADS.contains(pos)) yield FastTrigger.BETWEEN.enabled() ? FastTrigger.BETWEEN : null;
                yield FastTrigger.STORM_DEATH.enabled() ? FastTrigger.STORM_DEATH : null;
            }
            case 3 -> {
                if (FastTrigger.I4.enabled() && I4.contains(pos)) yield FastTrigger.I4;
                int section = FastTrigger.P3.enabled() ? sectionAt(pos) : 0;
                yield section >= 1 && section <= FastTrigger.SECTIONS.length ? FastTrigger.SECTIONS[section - 1] : null;
            }
            case 4 -> phaseAt(pos.y) != 4 ? null : MIDDLE.contains(pos)
                ? FastTrigger.P4.enabled() ? FastTrigger.P4 : null : FastTrigger.MIDDLE.enabled() ? FastTrigger.MIDDLE : null;
            case 5 -> phaseAt(pos.y) == 5 && FastTrigger.RELIC.enabled() ? FastTrigger.RELIC : null;
            default -> null;
        };
    }

    private boolean trigger(Minecraft client, Trigger trigger) {
        if (!GameplayModule.AUTO_LEAP.enabled() || trigger.hasToggle() && !trigger.enabled()) return false;
        if (trigger == Trigger.I4 && ConfigManager.autoLeapMelody && melody != null) return request(client, melody, false, false);
        boolean byName = ConfigManager.autoLeapByName;
        String target = (byName ? ConfigManager.autoLeapNames : ConfigManager.autoLeapClasses).get(trigger.id);
        return target != null && !target.isBlank() && request(client, target, !byName, false);
    }

    boolean request(Minecraft client, String target, boolean byClass, boolean manual) {
        if (client.getConnection() == null || client.player == null || cooldown > client.player.tickCount) return false;
        String self = client.player.getGameProfile().name();
        for (var entry : client.getConnection().getListedOnlinePlayers()) {
            var display = entry.getTabListDisplayName();
            if (display == null) continue;
            var match = TEAMMATE.matcher(ChatFormatting.stripFormatting(display.getString()));
            if (!match.matches()) continue;
            String name = match.group(1), role = match.group(2);
            if (name.equalsIgnoreCase(self) || "DEAD".equalsIgnoreCase(role)) continue;
            if ((byClass ? role : name).equalsIgnoreCase(target)) {
                var pattern = Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(name) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
                pending = new Request(pattern, client.player.tickCount + 60, manual);
                return true;
            }
        }
        MessageUtils.sendMessage("Leap target unavailable: " + target);
        return false;
    }

    void tick(Minecraft client) {
        boolean automatic = GameplayModule.AUTO_LEAP.enabled();
        if (!automatic) {
            if (pending != null && !pending.manual()) pending = null;
            if (active != null && !active.manual()) finish(client);
        }
        if (pending == null && active == null && !automatic && !GameplayModule.FAST_LEAP.enabled()) {
            if (phase != 0 || bloodOpen || melody != null || doorOpener != null) {
                long leapTime = lastCompletedLeap;
                reset(client);
                lastCompletedLeap = leapTime;
            }
            return;
        }
        if (!MapEditor.automaticDungeon(client) || client.gameMode == null || !client.player.isAlive()
                || client.player.isSpectator() || com.customcosmetics.features.map.Freecam.active()) {
            reset(client);
            return;
        }
        var inventory = client.player.getInventory();
        int now = client.player.tickCount;
        if (automatic && !relicUsed && phase == 5 && phaseAt(client.player.getY()) == 5
                && Trigger.RELIC.enabled() && RELICS.contains(LegacyPackItems.id(inventory.getItem(8)))) {
            relicUsed = trigger(client, Trigger.RELIC);
        }
        var screen = ClientUiAccess.screen(client);
        boolean leapMenu = screen instanceof AbstractContainerScreen<?> && "Spirit Leap".equals(screen.getTitle().getString());
        if (active != null) {
            if (now >= active.expires() || (clickAt >= 0 && !leapMenu)) { finish(client); return; }
            if (!leapMenu) return;
            if (clickAt < 0) clickAt = now + ConfigManager.autoLeapDelay;
            if (now < clickAt) return;
            var menu = ((AbstractContainerScreen<?>) screen).getMenu();
            for (var slot : menu.slots) {
                if (slot.container == inventory || slot.getItem().isEmpty()
                        || !active.target().matcher(slot.getItem().getHoverName().getString()).find()) continue;
                client.gameMode.handleContainerInput(menu.containerId, slot.index, 0, ContainerInput.PICKUP, client.player);
                noteLeap();
                cooldown = now + 48;
                finish(client);
                return;
            }
        } else if (pending != null) {
            if (now >= pending.expires() || now < cooldown) { pending = null; return; }
            if (!leapMenu && screen != null) return;
            if (!leapMenu) {
                int slot = findLeap(client);
                if (slot < 0) { pending = null; return; }
                oldSlot = inventory.getSelectedSlot();
                leapSlot = slot;
                inventory.setSelectedSlot(slot);
                client.gameMode.useItem(client.player, InteractionHand.MAIN_HAND);
            }
            active = new Request(pending.target(), now + 60, pending.manual());
            pending = null;
        }
    }

    boolean hasRequest() { return pending != null || active != null; }

    void noteLeap() { lastCompletedLeap = System.nanoTime(); }

    boolean recentlyLeaped(double seconds) {
        return lastCompletedLeap != 0 && System.nanoTime() - lastCompletedLeap < seconds * 1_000_000_000L;
    }

    private static int findLeap(Minecraft client) {
        var inventory = client.player.getInventory();
        int normal = -1;
        for (int i = 0; i < 9; i++) {
            String id = LegacyPackItems.id(inventory.getItem(i));
            if ("INFINITE_SPIRIT_LEAP".equals(id)) return i;
            if (normal < 0 && "SPIRIT_LEAP".equals(id)) normal = i;
        }
        return normal;
    }

    private void finish(Minecraft client) {
        if (ConfigManager.autoLeapSwapBack && oldSlot >= 0 && client.player != null
                && client.player.getInventory().getSelectedSlot() == leapSlot) client.player.getInventory().setSelectedSlot(oldSlot);
        oldSlot = leapSlot = clickAt = -1;
        active = null;
    }
}
