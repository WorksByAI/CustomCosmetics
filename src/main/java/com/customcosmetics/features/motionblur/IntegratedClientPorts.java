package com.customcosmetics.features.motionblur;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public final class IntegratedClientPorts {
    private IntegratedClientPorts() {}

    public static void launch(String feature, String className) {
        try {
            String name = typeName(feature, className);
            ((ClientModInitializer) Class.forName(name).getConstructor().newInstance()).onInitializeClient();
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Could not initialize " + feature, error);
        }
    }

    public static String typeName(String feature, String className) {
        var game = FabricLoader.getInstance().getModContainer("minecraft")
            .orElseThrow().getMetadata().getVersion().getFriendlyString();
        return "com.customcosmetics.features." + feature + ".v26" + game.charAt(3) + "." + className;
    }
}
