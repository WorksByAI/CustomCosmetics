package com.customcosmetics.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

public final class UtilitiesGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        new ShortcutsGameTest().runTest(context);
        new WaypointsGameTest().runTest(context);
        System.out.println("COMMAND_UTILITIES_CLIENT_REGRESSION_OK");
    }
}
