// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.shortcuts;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable, event-driven command rewriting. No polling or scheduled command execution. */
public final class ShortcutRules {
    public enum Kind { ALIAS, ARGUMENT, KEY }
    public record Entry(String id, Kind kind, String trigger, String command, boolean enabled) {}
    public static final int MAX_ENTRIES = 256, MAX_COMMAND = 256;
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$(\\*|[1-9][0-9]?)");
    private static final Set<String> OWN_COMMANDS = Set.of("customcosmetics", "ccos");
    private final Map<String, Entry> aliases, arguments;

    public ShortcutRules(List<Entry> entries) {
        Map<String, Entry> a = new HashMap<>(), p = new HashMap<>();
        for (Entry entry : entries) if (entry.enabled()) {
            if (entry.kind() == Kind.ALIAS) a.put(entry.trigger(), entry);
            if (entry.kind() == Kind.ARGUMENT) p.put(entry.trigger(), entry);
        }
        aliases = Map.copyOf(a); arguments = Map.copyOf(p);
    }

    public boolean empty() { return aliases.isEmpty() && arguments.isEmpty(); }
    public boolean hasAlias(String command) { return aliases.containsKey(first(command)) || arguments.containsKey("/" + first(command)); }
    public static String withoutSlash(String command) {
        String result = command.strip();
        return result.startsWith("/") ? result.substring(1) : result;
    }
    public static String first(String command) {
        int end = command.indexOf(' ');
        return end < 0 ? command : command.substring(0, end);
    }
    public static List<String> keys(String trigger) {
        return Arrays.stream(trigger.split(" \\+ ", -1)).map(String::strip).sorted().toList();
    }

    public static void validate(List<Entry> entries, Set<String> reservedCommands) {
        if (entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Use at most 256 shortcuts.");
        Set<String> ids = new HashSet<>(), triggers = new HashSet<>(), commandTriggers = new HashSet<>();
        for (Entry entry : entries) {
            if (entry == null || entry.id() == null || entry.id().isBlank() || !ids.add(entry.id()))
                throw new IllegalArgumentException("Each shortcut needs a unique ID.");
            if (entry.kind() == null || entry.trigger() == null || entry.command() == null)
                throw new IllegalArgumentException("Incomplete shortcut.");
            String trigger = entry.trigger();
            if (trigger.isBlank() || trigger.length() > 180 || !trigger.equals(trigger.strip()) || hasControl(trigger))
                throw new IllegalArgumentException("Enter a valid shortcut trigger.");
            if (entry.command().isBlank() || entry.command().length() > MAX_COMMAND || hasControl(entry.command()))
                throw new IllegalArgumentException("Enter one command, up to 256 characters.");
            String signature = entry.kind() + ":" + trigger;
            if (entry.kind() == Kind.ALIAS) {
                if (!trigger.matches("[A-Za-z0-9_:-]+"))
                    throw new IllegalArgumentException("Alias names use letters, numbers, _, : or - without /.");
                if (OWN_COMMANDS.contains(trigger))
                    throw new IllegalArgumentException("/" + trigger + " is reserved for CustomCosmetics shortcut management.");
                if (!commandTriggers.add(trigger)) throw new IllegalArgumentException("That command shortcut is already used.");
            } else if (entry.kind() == Kind.ARGUMENT) {
                if (trigger.chars().anyMatch(Character::isWhitespace))
                    throw new IllegalArgumentException("An argument shortcut replaces one complete token.");
                if (PLACEHOLDER.matcher(entry.command()).find())
                    throw new IllegalArgumentException("Argument replacements use literal text, without $ placeholders.");
                if (trigger.startsWith("/") && OWN_COMMANDS.contains(trigger.substring(1)))
                    throw new IllegalArgumentException(trigger + " is reserved for CustomCosmetics shortcut management.");
                if (trigger.startsWith("/") && !commandTriggers.add(trigger.substring(1)))
                    throw new IllegalArgumentException("That command shortcut is already used.");
            } else {
                List<String> keys = keys(trigger);
                if (keys.isEmpty() || keys.size() > 4 || new HashSet<>(keys).size() != keys.size()
                    || keys.stream().anyMatch(k -> !k.matches("(?:key\\.(?:keyboard|mouse)\\.[A-Za-z0-9_.-]+|scancode\\.[0-9]+)")))
                    throw new IllegalArgumentException("Record one to four different keyboard or mouse buttons.");
                signature = entry.kind() + ":" + String.join(" + ", keys);
                if (PLACEHOLDER.matcher(entry.command()).find())
                    throw new IllegalArgumentException("Key shortcuts need a complete command without $ placeholders.");
            }
            if (!triggers.add(signature)) throw new IllegalArgumentException("That shortcut trigger is already used.");
            if (entry.kind() != Kind.ARGUMENT) {
                String command = withoutSlash(entry.command());
                if (command.isBlank() || !first(command).matches("[A-Za-z0-9_:-]+"))
                    throw new IllegalArgumentException("The command name must be fixed, without placeholders.");
            }
        }
        // Detect alias cycles even among disabled entries so enabling one cannot introduce recursion.
        Map<String, Entry> allAliases = new HashMap<>();
        for (Entry entry : entries) if (entry.kind() == Kind.ALIAS) allAliases.put(entry.trigger(), entry);
        for (String start : allAliases.keySet()) {
            Set<String> seen = new HashSet<>();
            String at = start;
            while (allAliases.containsKey(at)) {
                if (!seen.add(at)) throw new IllegalArgumentException("Recursive command aliases are not allowed.");
                at = first(withoutSlash(allAliases.get(at).command()));
            }
        }
    }

    public String expand(String input) {
        if (empty()) return input;
        String command = input;
        Set<String> visited = null;
        for (int depth = 0; depth <= MAX_ENTRIES; depth++) {
            Entry alias = aliases.get(first(command));
            if (alias == null) break;
            if (visited == null) visited = new HashSet<>();
            if (!visited.add(alias.trigger())) throw new IllegalArgumentException("Recursive command alias blocked.");
            String tail = command.length() == alias.trigger().length() ? "" : command.substring(alias.trigger().length()).strip();
            command = substitute(withoutSlash(alias.command()), tail);
        }
        // Like Skyblocker's argument shortcuts: exact tokens, one non-recursive pass.
        // The first token includes its slash, so /v can map to /visit and @home to a name.
        if (!arguments.isEmpty()) {
            String[] tokens = command.split(" ", -1);
            boolean changed = false;
            for (int i = 0; i < tokens.length; i++) {
                Entry replacement = arguments.get(i == 0 ? "/" + tokens[i] : tokens[i]);
                if (replacement != null) {
                    tokens[i] = i == 0 ? withoutSlash(replacement.command()) : replacement.command();
                    changed = true;
                }
            }
            if (changed) command = String.join(" ", tokens);
        }
        if (command.isBlank() || command.length() > MAX_COMMAND || hasControl(command))
            throw new IllegalArgumentException("Expanded command must contain 1–256 characters.");
        return command;
    }

    static String substitute(String template, String tail) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        if (!matcher.find()) return tail.isEmpty() ? template : template + " " + tail;
        String[] args = tail.isEmpty() ? new String[0] : tail.split("\\s+");
        StringBuilder result = new StringBuilder();
        do {
            String token = matcher.group(1);
            String replacement;
            if (token.equals("*")) replacement = tail;
            else {
                int index = Integer.parseInt(token) - 1;
                if (index >= args.length) throw new IllegalArgumentException("Missing argument $" + token + ".");
                replacement = args[index];
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        } while (matcher.find());
        matcher.appendTail(result);
        return result.toString().strip();
    }
    private static boolean hasControl(String value) { return value.chars().anyMatch(c -> Character.isISOControl(c) || c == '\u00a7'); }
}
