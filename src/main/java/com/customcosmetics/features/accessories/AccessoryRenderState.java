// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessories;

/** Immutable per-player snapshot. Preview suppression never affects the world or another entity. */
public interface AccessoryRenderState {
    AccessoriesSettings customcosmetics$accessories();
    void customcosmetics$accessories(AccessoriesSettings value);
    boolean customcosmetics$hideAccessories();
    void customcosmetics$hideAccessories(boolean value);
}
