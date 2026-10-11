/*
 * Contains code adapted from Baity: https://github.com/raueyhs/Baity
 * Copyright (C) 2025 raueyhs
 * Modifications Copyright (C) 2026 CustomCosmetics contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 * Modified for CustomCosmetics; notices updated 2026-10-11. See LICENSE and NOTICE.
 */
package com.customcosmetics.gui.module;

import java.util.function.BooleanSupplier;

/** A live view of the setting, shared by the ported rendering and sync code. */
public record Module(BooleanSupplier enabled) {
    public boolean isEnabled() { return enabled.getAsBoolean(); }
}
