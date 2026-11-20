// SPDX-License-Identifier: CC0-1.0
package com.customcosmetics.features.gameplay.dungeonapi;

import java.util.List;
import java.util.function.*;
import net.minecraft.client.gui.screens.Screen;

public interface DungeonMenu {
    List<Entry> modules();
    Screen hudEditor(Screen parent);
    void save();
    String snapshotConfig();
    void loadConfig(String json);
    void setToggleNotifier(BiConsumer<String, Boolean> notifier);
    void deviceGateChanged();

    enum Category { GENERAL, DUNGEON, RENDER, OTHER }

    record Entry(String name, String description, Category category, BooleanSupplier enabled, Runnable toggle,
                 Supplier<List<Control>> settings, Runnable reset) {}
    record Control(String name, String description, BooleanSupplier visible, Input input, List<Control> children) {}

    sealed interface Input permits Toggle, Number, Choice, Text, Color, Key, Button, Group {}
    record Toggle(BooleanSupplier value, Runnable toggle) implements Input {}
    record Number(DoubleSupplier value, double min, double max, double step, DoubleConsumer set) implements Input {}
    record Choice(List<String> options, IntSupplier index, IntConsumer set) implements Input {}
    record Text(Supplier<String> value, Consumer<String> set, int length, String placeholder) implements Input {}
    record Color(IntSupplier argb, IntConsumer set, boolean alpha, BooleanSupplier rainbow, Runnable toggleRainbow) implements Input {}
    record Key(Supplier<String> name, IntPredicate bind, Runnable clear) implements Input {}
    record Button(Runnable action) implements Input {}
    record Group() implements Input {}
}
