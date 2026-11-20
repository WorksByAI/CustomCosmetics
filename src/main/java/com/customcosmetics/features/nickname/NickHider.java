// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.items.ItemNameText;
import com.customcosmetics.features.map.DungeonSidebarReader;
import com.customcosmetics.features.party.PartyFeatures;
import com.customcosmetics.features.party.PartyRoster;
import com.customcosmetics.features.text.TextReplacements;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Per-connection display aliases. Original profiles and all network data stay intact. */
public final class NickHider {
    public static final int PREFIX_LIMIT = 24, SELF_ALIAS_LIMIT = 30, SELF_ALIAS_INPUT_LIMIT = 256;
    private static final int SCAN_TICKS = 5, CHAT_REFRESH_TICKS = 10, HISTORY_LIMIT = 8_192;
    private static final String ACCOUNT = "[A-Za-z0-9_]{1,16}";
    private static final Pattern VALID_ACCOUNT = Pattern.compile(ACCOUNT);
    private static final Pattern TAB_MEMBER = Pattern.compile("^(?:\\[[^]]+]\\s+)*(" + ACCOUNT
        + ")\\s+.*?\\((Archer|Mage|Berserk(?:er)?|Healer|Tank|DEAD)(?:\\s+(?:[IVXLCDM]+|\\d+))?\\)\\s*$",
        Pattern.CASE_INSENSITIVE);
    private static final Pattern SIDEBAR_MEMBER = Pattern.compile("^[^A-Za-z0-9_\\[]*\\[(A|M|B|H|T|Archer|Mage|Berserk(?:er)?|Healer|Tank)]\\s+"
        + "(?:\\[[^]]+]\\s+)*(" + ACCOUNT + ")(?=\\s|$).*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PARTY_CHAT = Pattern.compile("^(?:(?:Party|Guild|Officer|Co-op) > |(?:From|To) )"
        + "(?:\\[[^]]+]\\s+)*(" + ACCOUNT + "):");

    private static final LinkedHashMap<String, Integer> numbers = new LinkedHashMap<>();
    private static Map<String, String> dungeonMembers = Map.of();
    private static Set<String> ownNames = Set.of();
    private static PartyRoster party = PartyRoster.EMPTY;
    private static Object connection, world;
    private static int number, scanDelay, chatRefreshDelay;
    private static boolean enabled, inDungeon;

    record Member(String name, String role) {}
    record Aliases(Map<String, String> names, Set<String> styled) {}

    private NickHider() {}

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> reset());
        ClientReceiveMessageEvents.CHAT.register((message, signed, sender, parameters, timestamp) -> {
            if (ConfigManager.nickHiderPlayers && sender != null) observeSpeaker(Minecraft.getInstance(), sender.name());
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (overlay || !ConfigManager.nickHiderPlayers) return;
            var match = PARTY_CHAT.matcher(ChatFormatting.stripFormatting(message.getString()));
            if (match.find()) observeSpeaker(Minecraft.getInstance(), match.group(1));
        });
    }

    public static String prefix(String value) {
        String clean = cleanLabel(value, PREFIX_LIMIT);
        return clean.isEmpty() ? "Player" : clean;
    }

    public static String selfAlias(String value) {
        if (value == null) return "";
        String clean = cleanLabel(value.replace('§', '&'), SELF_ALIAS_INPUT_LIMIT);
        String limited = ItemNameText.limitVisible(clean, SELF_ALIAS_LIMIT).strip();
        return ItemNameText.parse(limited, Style.EMPTY).getString().isBlank() ? "" : limited;
    }

    private static String cleanLabel(String value, int limit) {
        if (value == null) return "";
        var result = new StringBuilder();
        value.strip().codePoints().filter(cp -> cp != '§' && !Character.isISOControl(cp)
            && Character.getType(cp) != Character.FORMAT).limit(limit).forEach(result::appendCodePoint);
        return result.toString().strip();
    }

    private static boolean configured() {
        return ConfigManager.nickHiderPlayers || ConfigManager.nickHiderDungeon || ConfigManager.nickHiderSelf;
    }

    public static void tick(Minecraft client) {
        if (!configured()) {
            if (enabled) changed(client);
            return;
        }
        if (client.level == null || client.player == null || client.getConnection() == null) return;
        var transport = client.getConnection().getConnection();
        if (connection != transport || world != client.level || scanDelay-- <= 0) scan(client, false);
        if (chatRefreshDelay > 0 && --chatRefreshDelay == 0) TextReplacements.refreshChat(client);
    }

    public static void changed(Minecraft client) {
        boolean refresh;
        if (configured() && client.level != null && client.player != null && client.getConnection() != null) {
            refresh = scan(client, true) || chatRefreshDelay > 0;
        } else {
            refresh = reset();
        }
        chatRefreshDelay = 0;
        if (refresh && client.player != null) TextReplacements.refreshChat(client);
    }

    private static boolean scan(Minecraft client, boolean force) {
        var listener = client.getConnection();
        var transport = listener.getConnection();
        boolean dirty = !enabled;
        if (connection != transport) {
            reset();
            connection = transport;
            dirty = true;
        }
        if (world != client.level) {
            world = client.level;
            dungeonMembers = Map.of();
            party = PartyRoster.EMPTY;
            inDungeon = false;
            dirty = true;
        }
        enabled = true;
        scanDelay = SCAN_TICKS - 1;

        String self = client.player.getGameProfile().name();
        var sidebar = ConfigManager.nickHiderDungeon ? DungeonSidebarReader.read(client) : null;
        boolean dungeon = sidebar != null && sidebar.detected();
        var own = inDungeon && dungeon ? new HashSet<>(ownNames) : new HashSet<String>();
        own.add(key(self));
        own.add(key(client.getUser().getName()));
        if (dungeon) {
            var roster = PartyFeatures.roster();
            Map<String, String> members = inDungeon && party.equals(roster) ? new HashMap<>(dungeonMembers) : new HashMap<>();
            for (var name : roster.names()) members.putIfAbsent(name, "");
            for (var id : roster.members()) {
                var info = listener.getPlayerInfo(id);
                if (info != null) members.putIfAbsent(key(info.getProfile().name()), "");
            }
            for (String line : sidebar.rows()) {
                Member member = sidebarMember(line);
                addMember(members, member == null ? tabMember(line) : member);
            }
            var worldId = client.player.getUUID();
            var accountId = client.getUser().getProfileId();
            for (var info : listener.getListedOnlinePlayers()) {
                var display = info.getTabListDisplayName();
                Member member = display == null ? null : tabMember(display.getString());
                addMember(members, member);
                var profile = info.getProfile();
                if (worldId.equals(profile.id()) || accountId.equals(profile.id())) {
                    own.add(key(profile.name()));
                    if (member != null) own.add(member.name());
                }
            }
            party = roster;
            if (!dungeonMembers.equals(members)) {
                dungeonMembers = Map.copyOf(members);
                dirty = true;
            }
        } else if (inDungeon) {
            dungeonMembers = Map.of();
            party = PartyRoster.EMPTY;
            dirty = true;
        }
        inDungeon = dungeon;
        if (!ownNames.equals(own)) {
            ownNames = Set.copyOf(own);
            dirty = true;
        }
        if (ConfigManager.nickHiderSelf) dirty |= remember(self);
        if (ConfigManager.nickHiderPlayers) {
            for (var player : listener.getOnlinePlayers()) dirty |= rememberOther(player.getProfile().name());
            for (var player : client.level.players()) dirty |= rememberOther(player.getGameProfile().name());
        }
        if (dungeon) for (String name : dungeonMembers.keySet()) dirty |= rememberOther(name);
        return (dirty || force) && publish(client);
    }

    private static void observeSpeaker(Minecraft client, String name) {
        if (client.player == null || client.getConnection() == null) return;
        if (connection != client.getConnection().getConnection() || world != client.level) scan(client, false);
        if (rememberOther(name)) publish(client);
    }

    private static boolean publish(Minecraft client) {
        String profile = key(client.player.getGameProfile().name());
        var aliases = buildAliases(profile, ownNames, numbers, dungeonMembers);
        if (!TextReplacements.setPlayerAliases(aliases.names(), aliases.styled())) return false;
        if (chatRefreshDelay <= 0) chatRefreshDelay = CHAT_REFRESH_TICKS;
        return true;
    }

    static Aliases buildAliases(String profile, Set<String> own, Map<String, Integer> numbers, Map<String, String> classes) {
        String prefix = ConfigManager.nickHiderPrefix + "-";
        var names = new HashMap<String, String>();
        for (var entry : numbers.entrySet()) {
            String name = entry.getKey();
            if (own.contains(name)) continue;
            String role = classes.getOrDefault(name, "");
            if (!role.isEmpty()) names.put(name, role);
            else if (ConfigManager.nickHiderPlayers) names.put(name, prefix + entry.getValue());
        }
        if (!ConfigManager.nickHiderSelf) return new Aliases(names, Set.of());

        String alias = ConfigManager.nickHiderSelfAlias;
        boolean custom = !alias.isEmpty();
        if (!custom) {
            alias = classes.getOrDefault(profile, "");
            if (alias.isEmpty()) alias = own.stream().map(name -> classes.getOrDefault(name, ""))
                .filter(role -> !role.isEmpty()).findFirst().orElse("");
            if (alias.isEmpty()) {
                Integer number = numbers.get(profile);
                if (number == null) return new Aliases(names, Set.of());
                alias = prefix + number;
            }
        }
        for (String name : own) names.put(name, alias);
        return new Aliases(names, custom ? own : Set.of());
    }

    private static boolean remember(String name) {
        if (!VALID_ACCOUNT.matcher(name).matches()) return false;
        String canonical = key(name);
        if (numbers.containsKey(canonical)) return false;
        numbers.put(canonical, ++number);
        if (numbers.size() > HISTORY_LIMIT) numbers.pollFirstEntry();
        return true;
    }

    private static boolean rememberOther(String name) {
        return !ownNames.contains(key(name)) && remember(name);
    }

    private static boolean reset() {
        enabled = inDungeon = false;
        connection = world = null;
        number = scanDelay = chatRefreshDelay = 0;
        numbers.clear();
        ownNames = Set.of();
        dungeonMembers = Map.of();
        party = PartyRoster.EMPTY;
        return TextReplacements.setPlayerAliases(Map.of(), Set.of());
    }

    static Member tabMember(String line) {
        var match = TAB_MEMBER.matcher(cleanLine(line));
        return match.matches() ? new Member(key(match.group(1)), role(match.group(2))) : null;
    }

    static Member sidebarMember(String line) {
        var match = SIDEBAR_MEMBER.matcher(cleanLine(line));
        return match.matches() ? new Member(key(match.group(2)), role(match.group(1))) : null;
    }

    private static void addMember(Map<String, String> members, Member member) {
        if (member == null) return;
        if (member.role().isEmpty()) members.putIfAbsent(member.name(), "");
        else members.put(member.name(), member.role());
    }

    private static String cleanLine(String line) {
        return ChatFormatting.stripFormatting(line).strip();
    }

    private static String key(String name) { return name.toLowerCase(Locale.ROOT); }

    private static String role(String raw) {
        return switch (key(raw)) {
            case "a", "archer" -> "Archer";
            case "m", "mage" -> "Mage";
            case "b", "berserk", "berserker" -> "Berserk";
            case "h", "healer" -> "Healer";
            case "t", "tank" -> "Tank";
            default -> "";
        };
    }
}
