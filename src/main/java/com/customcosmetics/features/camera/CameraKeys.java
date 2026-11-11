// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.camera;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public final class CameraKeys {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("customcosmetics", "camera"));

    private CameraKeys() {}
}
