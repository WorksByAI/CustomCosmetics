/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.gui.module;

import com.customcosmetics.config.ConfigManager;
import java.util.Map;

public final class ModuleManager {
    private static final Map<String, Module> MODULES = Map.of(
        "SmolPeople", new Module(() -> ConfigManager.smolpeopleMode),
        "NickTweaks", new Module(() -> ConfigManager.nickTweaksEnabled)
    );

    private ModuleManager() {}

    public static Module getModuleByName(String name) { return MODULES.get(name); }
}
