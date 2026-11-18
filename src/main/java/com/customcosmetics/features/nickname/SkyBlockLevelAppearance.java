// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.PlayerBlocklist;
import com.customcosmetics.features.animations.PresenceWorld;
import com.customcosmetics.sync.PresenceSync;
import com.customcosmetics.sync.RemoteAccess;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class SkyBlockLevelAppearance {
    private static final String SPACE = "[ \\p{Zs}]";
    private static final String ACCOUNT = "(?<name>[A-Za-z0-9_]{1,16})(?![A-Za-z0-9_])";
    private static final String TAG = "\\[[^]\\r\\n]{1,20}]" + SPACE + "*";
    private static final String LEVEL = "(?<level>[0-9]{1,5})";
    private static final String BRACKETED_LEVEL = "\\[" + LEVEL + "(?:" + SPACE + "*[♲☀Ⓑ])?]" + SPACE + "+";
    private static final String CHAT_EMBLEM = "(?:[\\p{L}\\p{M}\\p{P}\\p{S}\\p{Co}&&[^\\p{ASCII}]]{1,5}" + SPACE + "+)?";
    private static final Pattern NAMETAG = Pattern.compile("^" + SPACE + "*" + BRACKETED_LEVEL + "(?:" + TAG + ")?" + ACCOUNT);
    private static final Pattern COMPACT = Pattern.compile("^" + SPACE + "*" + LEVEL + SPACE + "+(?:" + TAG + ")?" + ACCOUNT);
    private static final Pattern CHAT = Pattern.compile("^(?:\\[\\d{1,2}:\\d\\d(?::\\d\\d)?]" + SPACE + "*)?"
        + "(?:(?:Party|Guild|Officer|Co-op) > )?" + BRACKETED_LEVEL + CHAT_EMBLEM + "(?:" + TAG + "){0,2}"
        + ACCOUNT + "(?:" + SPACE + "+[^A-Za-z0-9_:\\s]{1,5})?" + SPACE + "*:");
    private static final Pattern NAMED_LEVEL = Pattern.compile("(?i)^[ \\p{Zs}]*(?:Your )?SkyBlock Level[ \\p{Zs}]*:[ \\p{Zs}]*\\[?"
        + LEVEL + "(?![0-9])");
    private static final Set<String> PERSONAL_MENUS = Set.of("SkyBlock Menu", "SkyBlock Leveling", "Your SkyBlock Profile", "Stats & Equipment");
    private record Glyph(int value, Style style) {}
    private static Map<String, SkyBlockLevel> known = Map.of();
    private static Object snapshot, blocked, connection;
    private static boolean online, inSkyBlock;
    private static String self = "";

    private SkyBlockLevelAppearance() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(SkyBlockLevelAppearance::refresh);
        ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            var own = SkyBlockLevel.local();
            var client = Minecraft.getInstance();
            if (own == null || !inSkyBlock || client.player == null
                    || !(ClientUiAccess.screen(client) instanceof AbstractContainerScreen<?> screen)) return;
            String title = screen.getTitle().getString();
            if (!PERSONAL_MENUS.contains(title) && !(client.player.getGameProfile().name() + "'s Profile").equalsIgnoreCase(title)) return;
            for (int i = 0; i < lines.size(); i++) {
                var line = lines.get(i);
                var rewritten = replace(line, NAMED_LEVEL, own);
                if (rewritten != line) lines.set(i, rewritten);
            }
        });
    }

    private static SkyBlockLevel previousLocal;
    private static void refresh(Minecraft client) {
        var local = SkyBlockLevel.local();
        boolean nextOnline = RemoteAccess.online();
        var nextSnapshot = PresenceSync.appearanceSnapshot();
        var nextBlocked = ConfigManager.nickTweaksBlockedPlayers;
        var nextConnection = client.getConnection();
        boolean nextSkyBlock = client.player != null && PresenceWorld.isHypixel(client) && PresenceWorld.isSkyblock(client);
        if (nextSkyBlock == inSkyBlock && snapshot == nextSnapshot && blocked == nextBlocked && connection == nextConnection
                && online == nextOnline && Objects.equals(previousLocal, local)) return;
        var next = new HashMap<String, SkyBlockLevel>();
        self = nextSkyBlock ? client.player.getGameProfile().name().toLowerCase(Locale.ROOT) : "";
        if (nextSkyBlock && nextOnline) {
            for (var remote : PresenceSync.remoteLevels()) {
                if (!PlayerBlocklist.contains(nextBlocked, remote.uuid(), remote.name())
                        && !remote.uuid().equals(client.player.getUUID())) {
                    next.put(remote.name().toLowerCase(Locale.ROOT), remote.level());
                }
            }
        }
        next.remove(self);
        if (local != null && nextSkyBlock) next.put(self, local);
        boolean reflow = !known.equals(next);
        known = Map.copyOf(next);
        snapshot = nextSnapshot;
        blocked = nextBlocked;
        connection = nextConnection;
        online = nextOnline;
        inSkyBlock = nextSkyBlock;
        previousLocal = local;
        if (reflow) ClientUiAccess.chat(client).rescaleChat();
    }

    public static Component tab(Component name, String profileName) {
        if (known.isEmpty()) return name;
        var styled = replacePlayer(name, NAMETAG);
        if (styled != name) return styled;
        var compact = COMPACT.matcher(plain(name));
        if (compact.find() && compact.group("name").equalsIgnoreCase(profileName)) {
            var display = known.get(profileName.toLowerCase(Locale.ROOT));
            if (display != null) return replaceDigits(name, compact.start("level"), compact.end("level"), display);
        }
        return describeOwnLevel(name);
    }

    public static Component nametag(Component name) {
        return replacePlayer(name, NAMETAG);
    }

    public static Component chat(Component message) {
        return replacePlayer(message, CHAT);
    }

    public static Component describeOwnLevel(Component text) {
        var own = known.get(self);
        return own == null ? text : replace(text, NAMED_LEVEL, own);
    }

    private static Component replacePlayer(Component text, Pattern pattern) {
        if (known.isEmpty()) return text;
        var match = pattern.matcher(plain(text));
        if (!match.find()) return text;
        var display = known.get(match.group("name").toLowerCase(Locale.ROOT));
        return display == null ? text : replaceDigits(text, match.start("level"), match.end("level"), display);
    }

    private static Component replace(Component text, Pattern pattern, SkyBlockLevel display) {
        var match = pattern.matcher(plain(text));
        return match.find() ? replaceDigits(text, match.start("level"), match.end("level"), display) : text;
    }

    static Component replaceDigits(Component text, int from, int to, SkyBlockLevel display) {
        var glyphs = new ArrayList<Glyph>();
        RankText.sequence(text).accept((index, style, cp) -> { glyphs.add(new Glyph(cp, style)); return true; });
        String plain = plain(text);
        from = plain.codePointCount(0, from);
        to = plain.codePointCount(0, to);
        if (to > glyphs.size()) return text;
        String digits = Integer.toString(display.value());
        var output = new ArrayList<>(glyphs.subList(0, from));
        for (int index = 0; index < digits.length(); index++) {
            var style = glyphs.get(Math.min(from + index, to - 1)).style().withColor(display.colors().get(index));
            output.add(new Glyph(digits.charAt(index), style));
        }
        output.addAll(glyphs.subList(to, glyphs.size()));
        FormattedCharSequence result = sink -> {
            for (int i = 0; i < output.size(); i++) {
                var glyph = output.get(i);
                if (!sink.accept(i, glyph.style(), glyph.value())) return false;
            }
            return true;
        };
        return RankText.component(result);
    }

    private static String plain(Component text) {
        return ChatFormatting.stripFormatting(text.getString());
    }

    public static Component preview(SkyBlockLevel display) {
        var text = Component.literal("[").withColor(0x555555);
        String digits = Integer.toString(display.value());
        for (int i = 0; i < digits.length(); i++) {
            text.append(Component.literal(digits.substring(i, i + 1)).withColor(display.colors().get(i)));
        }
        return text.append("]");
    }
}
