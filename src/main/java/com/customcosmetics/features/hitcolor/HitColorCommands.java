// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.hitcolor;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.gui.UiText;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;

import java.util.Locale;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static com.mojang.brigadier.arguments.StringArgumentType.getString;
import static com.mojang.brigadier.arguments.StringArgumentType.greedyString;
import static com.mojang.brigadier.arguments.StringArgumentType.word;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public final class HitColorCommands {
    private HitColorCommands() {}

    public static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        var root = literal(name).executes(c -> {
            c.getSource().sendFeedback(UiText.component("Hit Color: rgb | color | hex | opacity | chroma | body | armor | trims | items | cape | status | reset"));
            return 1;
        });
        root.then(literal("rgb").then(argument("red", integer(0, 255)).then(argument("green", integer(0, 255))
            .then(argument("blue", integer(0, 255)).executes(c -> setColor(c.getSource(),
                getInteger(c, "red") << 16 | getInteger(c, "green") << 8 | getInteger(c, "blue")))))));
        root.then(literal("color").then(argument("name", word()).suggests((c, b) -> {
                for (String preset : HitColors.PRESETS.keySet()) if (preset.startsWith(b.getRemainingLowerCase())) b.suggest(preset);
                return b.buildFuture();
            }).executes(c -> {
                var rgb = HitColors.PRESETS.get(getString(c, "name").toLowerCase(Locale.ROOT));
                if (rgb != null) return setColor(c.getSource(), rgb);
                c.getSource().sendError(UiText.component("Unknown Hit Color preset."));
                return 0;
            })))
            .then(literal("hex").then(argument("hex", greedyString()).executes(c -> {
                String hex = getString(c, "hex").strip().replaceFirst("^#", "");
                if (hex.matches("[0-9a-fA-F]{6}")) return setColor(c.getSource(), Integer.parseInt(hex, 16));
                c.getSource().sendError(UiText.component("Expected a six-digit hex color."));
                return 0;
            })))
            .then(literal("opacity").then(argument("percent", integer(0, 100)).executes(c -> {
                ConfigManager.hitOpacity = getInteger(c, "percent") / 100f;
                return save(c.getSource());
            })))
            .then(literal("chroma")
                .then(literal("on").executes(c -> { ConfigManager.hitColorChroma = true; return save(c.getSource()); }))
                .then(literal("off").executes(c -> { ConfigManager.hitColorChroma = false; return save(c.getSource()); }))
                .then(literal("speed").then(argument("speed", integer(1, 100)).executes(c -> {
                    ConfigManager.hitColorChromaSpeed = getInteger(c, "speed");
                    return save(c.getSource());
                }))))
            .then(literal("status").executes(c -> status(c.getSource())))
            .then(literal("reset").executes(c -> { HitColors.reset(); return status(c.getSource()); }));
        for (var layer : HitColors.Layer.values()) {
            String target = switch (layer) {
                case TRIM -> "trims";
                case ITEM -> "items";
                default -> layer.name().toLowerCase(Locale.ROOT);
            };
            root.then(literal(target)
                .then(literal("on").executes(c -> { layer.selected(true); return save(c.getSource()); }))
                .then(literal("off").executes(c -> { layer.selected(false); return save(c.getSource()); })));
        }
        return root;
    }

    private static int setColor(FabricClientCommandSource source, int rgb) {
        ConfigManager.hitColor = rgb;
        ConfigManager.hitColorChroma = false;
        return save(source);
    }

    private static int save(FabricClientCommandSource source) {
        ConfigManager.hitColorEnabled = true;
        ConfigManager.requestSave();
        return status(source);
    }

    private static int status(FabricClientCommandSource source) {
        source.sendFeedback(UiText.component("Hit Color: " + (ConfigManager.hitColorEnabled
            ? String.format(Locale.ROOT, "#%06X / %d%%", ConfigManager.hitColor, Math.round(100 * ConfigManager.hitOpacity)) : "Off")));
        return 1;
    }
}
