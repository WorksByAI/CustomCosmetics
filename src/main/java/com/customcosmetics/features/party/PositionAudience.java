// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import com.customcosmetics.config.PlayerBlocklist;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

/** Outbound permission and inbound opt-in are independent; neither is a party roster. */
public record PositionAudience(String mode, Set<UUID> members, Set<String> names, boolean receive) {
    public static final PositionAudience PARTY = new PositionAudience("party", Set.of(), Set.of(), false);
    public static final int MAX_TARGETS = 256;
    public PositionAudience {
        mode = Set.of("party", "public", "selected").contains(mode == null ? "" : mode) ? mode : "selected";
        members = members == null ? Set.of() : Set.copyOf(members);
        names = names == null ? Set.of() : names.stream().filter(Objects::nonNull).filter(n -> n.matches("[A-Za-z0-9_]{3,16}"))
            .map(n -> n.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (members.size() + names.size() > MAX_TARGETS) throw new IllegalArgumentException("Too many position sharing recipients");
    }
    public static String mode(String value) { return "public".equals(value) ? "public" : "selected"; }
    public static List<PlayerBlocklist.Entry> targets(Collection<PlayerBlocklist.Entry> entries) {
        return PlayerBlocklist.normalize(entries).stream().filter(e -> e.account().matches("[A-Za-z0-9_]{3,16}")).limit(MAX_TARGETS).toList();
    }
    public static PositionAudience settings(boolean enabled, String mode, Collection<PlayerBlocklist.Entry> targets, boolean receive) {
        var ids = new HashSet<UUID>(); var names = new HashSet<String>();
        if (enabled && !"public".equals(mode)) for (var entry : targets(targets)) {
            if (entry.uuid() != null) ids.add(entry.uuid()); else names.add(entry.account());
        }
        return new PositionAudience(enabled ? mode(mode) : "party", ids, names, receive);
    }
    public boolean publishes(PartyRoster roster, boolean partyConsent) {
        return switch (mode) { case "public" -> true; case "selected" -> !members.isEmpty() || !names.isEmpty(); default -> partyConsent && !roster.empty(); };
    }
    public boolean sendsTo(UUID uuid, String name, PartyRoster roster, boolean partyConsent) {
        return switch (mode) { case "public" -> true; case "selected" -> members.contains(uuid) || name != null && names.contains(name.toLowerCase(Locale.ROOT)); default -> partyConsent && roster.contains(uuid, name); };
    }
    public boolean accepts(UUID uuid, String name, PartyRoster roster, boolean partyConsent) { return receive || partyConsent && roster.contains(uuid, name); }
    public JsonObject json() {
        var value = new JsonObject(); value.addProperty("mode", mode); value.addProperty("receive", receive);
        var ids = new JsonArray(); members.stream().sorted().forEach(id -> ids.add(id.toString())); value.add("members", ids);
        var accounts = new JsonArray(); names.stream().sorted().forEach(accounts::add); value.add("names", accounts); return value;
    }
}
