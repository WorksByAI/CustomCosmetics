// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.config.ConfigManager;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.List;

public final class ExperimentBoardGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context) {
        try (var world = context.worldBuilder().create()) {
            context.runOnClient(client -> {
                var solver = Class.forName("com.customcosmetics.features.gameplay.AutoExperiments");
                var reset = solver.getDeclaredMethod("reset");
                var read = solver.getDeclaredMethod("readBoard");
                var screen = solver.getDeclaredField("screen");
                var order = solver.getDeclaredField("order");
                var playing = solver.getDeclaredField("playing");
                var index = solver.getDeclaredField("index");
                for (var field : List.of(screen, order, playing, index)) field.setAccessible(true);
                reset.setAccessible(true);
                read.setAccessible(true);
                boolean close = ConfigManager.experimentAutoClose;
                ConfigManager.experimentAutoClose = false;
                try {
                    reset.invoke(null);
                    var menu = ChestMenu.sixRows(1, client.player.getInventory());
                    screen.set(null, new ContainerScreen(menu, client.player.getInventory(),
                        Component.literal("Ultrasequencer (Metaphysical)")));
                    menu.slots.get(49).set(new ItemStack(Items.GLOWSTONE));
                    menu.slots.get(42).set(number(1));
                    read.invoke(null);
                    assertOrder(order.get(null), 42);
                    menu.slots.get(49).set(new ItemStack(Items.CLOCK));
                    read.invoke(null);
                    playing.set(null, true);
                    index.set(null, 1);
                    menu.slots.get(42).set(new ItemStack(Items.STONE));
                    menu.slots.get(49).set(new ItemStack(Items.GLOWSTONE));
                    read.invoke(null);

                    // The second round arrives in packets and its tiles then disappear in packets.
                    menu.slots.get(19).set(number(1));
                    read.invoke(null);
                    menu.slots.get(14).set(number(2));
                    read.invoke(null);
                    assertOrder(order.get(null), 19, 14);
                    menu.slots.get(14).set(new ItemStack(Items.STONE));
                    read.invoke(null);
                    assertOrder(order.get(null), 19, 14);
                    menu.slots.get(19).set(new ItemStack(Items.STONE));
                    read.invoke(null);
                    menu.slots.get(49).set(new ItemStack(Items.CLOCK));
                    read.invoke(null);
                    assertOrder(order.get(null), 19, 14);
                    if (!playing.getBoolean(null) || index.getInt(null) != 0)
                        throw new AssertionError("Playback was not reset for round two");

                    index.set(null, 2);
                    menu.slots.get(49).set(new ItemStack(Items.GLOWSTONE));
                    read.invoke(null);
                    assertOrder(order.get(null));
                    menu.slots.get(11).set(number(1));
                    menu.slots.get(44).set(number(3));
                    read.invoke(null);
                    assertOrder(order.get(null));
                    menu.slots.get(9).set(number(2));
                    read.invoke(null);
                    assertOrder(order.get(null), 11, 9, 44);
                    System.out.println("ULTRASEQUENCER_PACKET_REVEAL_HIDE_AND_NEXT_ROUND_OK");
                } finally {
                    reset.invoke(null);
                    ConfigManager.experimentAutoClose = close;
                }
            });
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static ItemStack number(int number) {
        var stack = new ItemStack(Items.PAPER, number);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(Integer.toString(number)));
        return stack;
    }

    private static void assertOrder(Object actual, Integer... expected) {
        if (!List.of(expected).equals(actual))
            throw new AssertionError("Expected " + List.of(expected) + ", got " + actual);
    }
}
