// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

/** Local scoreboard revision; reading a sidebar never needs to parse unchanged rows again. */
public interface SidebarRevision {
    long customcosmetics$sidebarRevision();
}
