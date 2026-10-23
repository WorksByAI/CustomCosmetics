// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.items;

/** The backing patch identity changes after a captured copy-on-write snapshot is mutated. */
public interface ItemComponentIdentity {
    Object customcosmetics$componentIdentity();
}
