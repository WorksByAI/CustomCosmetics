/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.config;

import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CosmeticsConfigDir {
    private static final Logger LOGGER = LoggerFactory.getLogger("CustomCosmetics/ConfigDir");
    private static Path cosmeticsConfigDir = null;
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        initialized = true;

        Path configDirectory = FabricLoader.getInstance().getConfigDir().resolve("customcosmetics");
        try {
            Files.createDirectories(configDirectory);
            cosmeticsConfigDir = configDirectory;
        } catch (IOException e) {
            LOGGER.warn("[ConfigDir] Failed to initialize config directory: {}", e.toString());
            cosmeticsConfigDir = configDirectory;
        }
    }

    public static Path getCosmeticsConfigDir() {
        if (cosmeticsConfigDir == null) {
            init();
        }
        return cosmeticsConfigDir;
    }

    public static Path getConfigDir() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
