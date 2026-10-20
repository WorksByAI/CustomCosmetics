// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.shortcuts;

import java.util.*;

/** Input-event state machine. Candidates are indexed by the changed key, not scanned per frame. */
public final class ShortcutKeyState<K> {
    public record Binding<K>(String id, List<K> keys, String command) {}
    private final Map<K, List<Binding<K>>> byKey;
    private final Set<K> held = new HashSet<>();
    private final Set<String> fired = new HashSet<>();
    public ShortcutKeyState(List<Binding<K>> bindings) {
        Map<K, List<Binding<K>>> index = new HashMap<>();
        for (Binding<K> binding : bindings) for (K key : binding.keys())
            index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(binding);
        byKey = Map.copyOf(index);
    }
    public boolean empty() { return byKey.isEmpty(); }
    public void clear() { held.clear(); fired.clear(); }
    public String event(K key, boolean pressed, boolean repeat, boolean allowed) {
        if (empty()) return null;
        if (!allowed) { clear(); return null; }
        List<Binding<K>> candidates = byKey.get(key);
        if (!pressed) {
            held.remove(key);
            if (candidates != null) for (Binding<K> binding : candidates) fired.remove(binding.id());
            return null;
        }
        if (repeat || candidates == null || !held.add(key)) return null;
        Binding<K> chosen = null;
        for (Binding<K> candidate : candidates)
            if (!fired.contains(candidate.id()) && held.containsAll(candidate.keys())
                && (chosen == null || candidate.keys().size() > chosen.keys().size())) chosen = candidate;
        if (chosen == null) return null;
        fired.add(chosen.id());
        return chosen.command();
    }
}
