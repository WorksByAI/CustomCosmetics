// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.pack;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.ExperimentalAccess;
import com.customcosmetics.utils.MessageUtils;
import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class PackDisablerCommands {
    private static final int CLIPBOARD_LIMIT = 1_048_576;
    private static KeyMapping whitelistKey;
    private static KeyMapping debugKey;

    private PackDisablerCommands() {}

    public static void init() {
        if (!HypixelPackDisabler.available()) return;
        var category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("customcosmetics", "pack_disabler"));
        whitelistKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.customcosmetics.pack_whitelist", InputConstants.KEY_P, category));
        debugKey = KeyMappingHelper.registerKeyMapping(
            new KeyMapping("key.customcosmetics.pack_debug", InputConstants.UNKNOWN.getValue(), category));
    }

    public static boolean handleKey(KeyEvent event, ItemStack stack) {
        if (!HypixelPackDisabler.usingFallback()) return false;
        var client = Minecraft.getInstance();
        if (debugKey.matches(event) && client.getConnection() != null) {
            var ops = RegistryOps.create(JsonOps.INSTANCE, client.getConnection().registryAccess());
            DataComponentPatch.CODEC.encodeStart(ops, stack.getComponentsPatch()).result().ifPresentOrElse(data -> {
                client.keyboardHandler.setClipboard(data.toString());
                message("Copied item data to the clipboard.");
            }, () -> message("Could not copy item data."));
            return true;
        }
        if (!whitelistKey.matches(event)) return false;
        String id = LegacyPackItems.id(stack);
        if (id.isEmpty()) return false;
        whitelist(id);
        return true;
    }

    public static LiteralArgumentBuilder<FabricClientCommandSource> command(String name) {
        return ClientCommands.literal(name)
            .executes(authorized(context -> help()))
            .then(ClientCommands.literal("help").executes(authorized(context -> help())))
            .then(ClientCommands.literal("reload").executes(authorized(context -> reload())))
            .then(ClientCommands.literal("textcolors").executes(authorized(context -> {
                ConfigManager.hypixelPackTextColors = !ConfigManager.hypixelPackTextColors;
                ConfigManager.requestSave();
                HypixelPackDisabler.refresh(Minecraft.getInstance());
                return message("Hypixel text colors: " + (ConfigManager.hypixelPackTextColors ? "on" : "off"));
            })))
            .then(ClientCommands.literal("whitelist")
                .executes(authorized(context -> whitelist(heldId())))
                .then(ClientCommands.argument("id", StringArgumentType.greedyString())
                    .suggests((context, builder) -> {
                        if (!ExperimentalAccess.allowed()) return builder.buildFuture();
                        String prefix = builder.getRemainingLowerCase();
                        LegacyPackItems.ids().stream().filter(id -> id.toLowerCase(Locale.ROOT).startsWith(prefix))
                            .sorted().forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .executes(authorized(context -> whitelist(normalizeId(StringArgumentType.getString(context, "id").strip()))))))
            .then(replacements())
            .then(glints());
    }

    private static Command<FabricClientCommandSource> authorized(Command<FabricClientCommandSource> command) {
        // Permissions are fetched after joining. Keep the command tree available for later grants.
        return context -> ExperimentalAccess.allowed() ? command.run(context) : message("Pack Disabler requires experimental access.");
    }

    private static int help() {
        return message("""
            Hover a SkyBlock item and press %s to toggle its Hypixel model.
            /ccos pack reload | textcolors | whitelist [id]
            /ccos pack replace set <vanilla item> | remove | list | export | import
            /ccos pack glint set <on|off> | remove | list
            Replace and glint apply to the held item's SkyBlock ID.
            """.formatted(whitelistKey.getTranslatedKeyMessage().getString()).stripTrailing());
    }

    public static int reload() {
        HypixelPackDisabler.checkDownloadAndReload(Minecraft.getInstance());
        return 1;
    }

    private static int whitelist(String id) {
        if (id.isEmpty()) return noHeldItem();
        if (!validId(id)) return message("Invalid SkyBlock item ID.");
        var ids = new HashSet<>(ConfigManager.hypixelPackWhitelist);
        boolean added = ids.add(id);
        if (!added) ids.remove(id);
        ConfigManager.hypixelPackWhitelist = java.util.Set.copyOf(ids);
        ConfigManager.requestSave();
        return message(id + (added ? " uses its Hypixel model." : " uses its legacy model."));
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> replacements() {
        return ClientCommands.literal("replace")
            .then(ClientCommands.literal("set")
                .then(ClientCommands.argument("item", StringArgumentType.greedyString())
                    .suggests((context, builder) -> {
                        if (!ExperimentalAccess.allowed()) return builder.buildFuture();
                        String prefix = builder.getRemainingLowerCase();
                        BuiltInRegistries.ITEM.keySet().stream().filter(id -> "minecraft".equals(id.getNamespace()))
                            .map(id -> prefix.contains(":") ? id.toString() : id.getPath())
                            .filter(id -> id.startsWith(prefix)).sorted().forEach(builder::suggest);
                        return builder.buildFuture();
                    })
                    .executes(authorized(context -> setReplacement(StringArgumentType.getString(context, "item").strip())))))
            .then(ClientCommands.literal("remove").executes(authorized(context -> remove(true))))
            .then(ClientCommands.literal("list").executes(authorized(context -> list(ConfigManager.hypixelPackReplacements))))
            .then(ClientCommands.literal("export").executes(authorized(context -> exportReplacements())))
            .then(ClientCommands.literal("import").executes(authorized(context -> importReplacements())));
    }

    private static int setReplacement(String value) {
        String id = heldId();
        if (id.isEmpty()) return noHeldItem();
        value = value.toLowerCase(Locale.ROOT);
        if (LegacyPackItems.vanillaModel(value) == null) return message("Unknown vanilla item: " + value);
        ConfigManager.hypixelPackReplacements = update(ConfigManager.hypixelPackReplacements, id, Identifier.parse(value).toString());
        ConfigManager.requestSave();
        return message(id + " uses " + value + ".");
    }

    private static LiteralArgumentBuilder<FabricClientCommandSource> glints() {
        return ClientCommands.literal("glint")
            .then(ClientCommands.literal("set")
                .then(ClientCommands.literal("on").executes(authorized(context -> setGlint(true))))
                .then(ClientCommands.literal("off").executes(authorized(context -> setGlint(false)))))
            .then(ClientCommands.literal("remove").executes(authorized(context -> remove(false))))
            .then(ClientCommands.literal("list").executes(authorized(context -> list(ConfigManager.hypixelPackGlints))));
    }

    private static int setGlint(boolean value) {
        String id = heldId();
        if (id.isEmpty()) return noHeldItem();
        ConfigManager.hypixelPackGlints = update(ConfigManager.hypixelPackGlints, id, value);
        ConfigManager.requestSave();
        return message(id + " enchantment glint: " + (value ? "on" : "off"));
    }

    private static int remove(boolean replacement) {
        String id = heldId();
        if (id.isEmpty()) return noHeldItem();
        if (replacement) ConfigManager.hypixelPackReplacements = update(ConfigManager.hypixelPackReplacements, id, null);
        ConfigManager.hypixelPackGlints = update(ConfigManager.hypixelPackGlints, id, null);
        ConfigManager.requestSave();
        return message("Reset " + (replacement ? "visual replacement" : "enchantment glint") + " for " + id + ".");
    }

    private static <T> Map<String, T> update(Map<String, T> previous, String id, T value) {
        var next = new HashMap<>(previous);
        if (value == null) next.remove(id);
        else next.put(id, value);
        return Map.copyOf(next);
    }

    private static int list(Map<String, ?> settings) {
        if (settings.isEmpty()) return message("No overrides set.");
        settings.entrySet().stream().sorted(Map.Entry.comparingByKey())
            .forEach(entry -> message(entry.getKey() + " → " + entry.getValue()));
        return 1;
    }

    private static int exportReplacements() {
        try (var bytes = new ByteArrayOutputStream()) {
            try (var gzip = new GZIPOutputStream(bytes)) {
                gzip.write(new Gson().toJson(ConfigManager.hypixelPackReplacements).getBytes(StandardCharsets.UTF_8));
            }
            Minecraft.getInstance().keyboardHandler.setClipboard(Base64.getEncoder().encodeToString(bytes.toByteArray()));
            return message("Copied " + ConfigManager.hypixelPackReplacements.size() + " replacements to the clipboard.");
        } catch (IOException error) {
            return message("Could not export replacements.");
        }
    }

    private static int importReplacements() {
        try {
            String data = Minecraft.getInstance().keyboardHandler.getClipboard().strip();
            if (data.length() > CLIPBOARD_LIMIT) throw new IOException("Replacement data is too large");
            if (!data.startsWith("{")) {
                try (var gzip = new GZIPInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(data)))) {
                    byte[] json = gzip.readNBytes(CLIPBOARD_LIMIT + 1);
                    if (json.length > CLIPBOARD_LIMIT) throw new IOException("Replacement data is too large");
                    data = new String(json, StandardCharsets.UTF_8);
                }
            }
            var replacements = new HashMap<String, String>();
            for (var entry : JsonParser.parseString(data).getAsJsonObject().entrySet()) {
                String id = normalizeId(entry.getKey());
                String item = entry.getValue().getAsString().toLowerCase(Locale.ROOT);
                if (!validId(id) || LegacyPackItems.vanillaModel(item) == null) {
                    throw new IOException("Invalid replacement");
                }
                replacements.put(id, Identifier.parse(item).toString());
            }
            var glints = new HashMap<>(ConfigManager.hypixelPackGlints);
            glints.keySet().retainAll(replacements.keySet());
            ConfigManager.hypixelPackReplacements = Map.copyOf(replacements);
            ConfigManager.hypixelPackGlints = Map.copyOf(glints);
            ConfigManager.requestSave();
            return message("Imported " + replacements.size() + " replacements.");
        } catch (RuntimeException | IOException error) {
            return message("Invalid replacement data in the clipboard.");
        }
    }

    private static String normalizeId(String id) {
        return id.equalsIgnoreCase("quiver_arrow") ? "quiver_arrow" : id.replace(':', '-').toUpperCase(Locale.ROOT);
    }

    private static boolean validId(String id) {
        return id.equals("quiver_arrow") || id.matches("[A-Z0-9_;.-]{1,120}");
    }

    private static String heldId() {
        var player = Minecraft.getInstance().player;
        return player == null ? "" : LegacyPackItems.id(player.getMainHandItem());
    }

    private static int noHeldItem() {
        return message("Hold a SkyBlock item first.");
    }

    private static int message(String value) {
        MessageUtils.sendMessage(value);
        return 1;
    }
}
