// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.gameplay;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.client.KeyMapping;

/** Releases only keys pressed by this feature. */
final class GameplayInput {
    private final Set<KeyMapping> held = new HashSet<>();

    void set(KeyMapping key, boolean down) {
        if (down) {
            if (held.contains(key) || !key.isDown()) {
                held.add(key);
                key.setDown(true);
            }
        } else if (held.remove(key)) key.setDown(false);
    }

    void release() {
        for (var key : held) key.setDown(false);
        held.clear();
    }
}
