// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import com.customcosmetics.client.ModErrors;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

public final class GameplayKeybinds {
    private GameplayKeybinds() {}

    public static Map<String, GameplayModule> normalize(Map<String, GameplayModule> bindings) {
        var valid = new LinkedHashMap<String, GameplayModule>();
        var modules = EnumSet.noneOf(GameplayModule.class);
        if (bindings != null) bindings.forEach((name, module) -> {
            if (name == null || module == null) return;
            try {
                var key = InputConstants.getKey(name);
                if (!InputConstants.UNKNOWN.equals(key) && !valid.containsKey(key.getName()) && modules.add(module))
                    valid.put(key.getName(), module);
            } catch (IllegalArgumentException invalid) { /* Stale input name. */ }
        });
        return Map.copyOf(valid);
    }

    public static InputConstants.Key key(GameplayModule module) {
        for (var entry : ConfigManager.gameplayKeybinds.entrySet()) {
            if (entry.getValue() == module) return InputConstants.getKey(entry.getKey());
        }
        return InputConstants.UNKNOWN;
    }

    /** Return the conflicting module so the recorder can keep the old binding. */
    public static GameplayModule bind(GameplayModule module, InputConstants.Key key) {
        var next = new LinkedHashMap<>(ConfigManager.gameplayKeybinds);
        String name = key.getName();
        var conflict = next.get(name);
        if (conflict != null && conflict != module) return conflict;
        next.values().removeIf(value -> value == module);
        if (!key.equals(InputConstants.UNKNOWN)) next.put(name, module);
        ConfigManager.gameplayKeybinds = Map.copyOf(next);
        ConfigManager.requestSave();
        return null;
    }

    public static boolean acceptsInput(long window, int action) {
        if (action != InputConstants.PRESS || ConfigManager.gameplayKeybinds.isEmpty()
                || !ConfigManager.gameplayPanelConfirmed || !ModErrors.active(ModErrors.Key.GAMEPLAY)) return false;
        var client = Minecraft.getInstance();
        return client.player != null && client.level != null && client.getWindow().handle() == window
            && ClientUiAccess.screen(client) == null;
    }

    public static void press(InputConstants.Key key) {
        var module = ConfigManager.gameplayKeybinds.get(key.getName());
        if (module != null) module.toggle();
    }
}
