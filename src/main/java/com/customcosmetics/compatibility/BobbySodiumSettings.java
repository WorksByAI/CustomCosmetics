// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import com.customcosmetics.features.motionblur.IntegratedClientPorts;
import net.caffeinemc.mods.sodium.api.config.ConfigEntryPoint;
import net.caffeinemc.mods.sodium.api.config.structure.ConfigBuilder;
import net.fabricmc.loader.api.FabricLoader;

public final class BobbySodiumSettings implements ConfigEntryPoint {
    @Override public void registerConfigLate(ConfigBuilder builder) {
        if (FabricLoader.getInstance().isModLoaded("bobby")) return;
        try {
            String name = IntegratedClientPorts.typeName("bobby", "SodiumConfigEntrypoint");
            ((ConfigEntryPoint) Class.forName(name).getConstructor().newInstance()).registerConfigLate(builder);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not register Bobby's render distance option", error);
        }
    }
}
