// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v262;

import com.customcosmetics.features.gameplay.BobbyIntegration;

import com.customcosmetics.features.bobby.v262.commands.CreateWorldCommand;
import com.customcosmetics.features.bobby.v262.commands.MergeWorldsCommand;
import com.customcosmetics.features.bobby.v262.commands.UpgradeCommand;
import com.customcosmetics.features.bobby.v262.commands.WorldsCommand;
import com.customcosmetics.features.bobby.v262.ext.ClientChunkCacheExt;
import com.customcosmetics.features.bobby.v262.mixin.OptionInstanceAccessor;
import com.customcosmetics.features.bobby.v262.mixin.OptionInstanceIntRangeAccessor;
import com.customcosmetics.features.bobby.v262.util.FlawlessFrames;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

public class Bobby implements ClientModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    public static final String MOD_ID = "bobby";
    { instance = this; }
    private static Bobby instance;
    public static Bobby getInstance() {
        return instance;
    }

    private static final Minecraft client = Minecraft.getInstance();

    private BobbyConfig config = BobbyConfig.DEFAULT;
    private TaintChunksConfigHandler taintHandler;
    private MaxRenderDistanceConfigHandler distanceHandler;

    @Override
    public void onInitializeClient() {
        config = readConfig(BobbyIntegration.read());

        ClientCommandRegistrationCallback.EVENT.register(((dispatcher, registryAccess) ->
                dispatcher.register(literal("bobby")
                        .then(literal("worlds").executes(new WorldsCommand(false))
                                .then(literal("full").executes(new WorldsCommand(true)))
                                .then(literal("merge")
                                        .then(argument("source", integer())
                                                .then(argument("target", integer())
                                                        .executes(new MergeWorldsCommand()))))
                                .then(literal("create").executes(new CreateWorldCommand()))
                        )
                        .then(literal("upgrade").executes(new UpgradeCommand())))));

        FlawlessFrames.onClientInitialization();

        taintHandler = new TaintChunksConfigHandler();
        distanceHandler = new MaxRenderDistanceConfigHandler();
        BobbyIntegration.onSave(values -> {
            config = readConfig(values);
            taintHandler.update(config);
            distanceHandler.update(config);
        });

        Util.ioPool().execute(this::cleanupOldWorlds);
    }

    public BobbyConfig getConfig() {
        return config;
    }

    public boolean isEnabled() {
        BobbyConfig config = getConfig();
        return config.isEnabled()
                // For singleplayer, disable ourselves unless the view-distance overwrite is active.
                && (client.getSingleplayerServer() == null || config.getViewDistanceOverwrite() != 0);
    }

    private static BobbyConfig readConfig(int[] options) {
        return new BobbyConfig(options[0] != 0, options[1] != 0, options[2] != 0, options[3] != 0,
            options[4], options[5], options[6], options[7]);
    }

    private void cleanupOldWorlds() {
        int deleteUnusedRegionsAfterDays = Bobby.getInstance().getConfig().getDeleteUnusedRegionsAfterDays();
        if (deleteUnusedRegionsAfterDays < 0) {
            return;
        }

        Path basePath = client.gameDirectory.toPath().resolve(".bobby");

        List<Path> toBeDeleted;
        try (Stream<Path> stream = Files.walk(basePath, 4)) {
            toBeDeleted = stream
                    .filter(it -> basePath.relativize(it).getNameCount() == 4)
                    .flatMap(directory -> {
                        try {
                            List<Path> toDelete = new ArrayList<>();
                            if (Files.exists(Worlds.metaFile(directory))) {
                                List<Path> worlds;
                                try (Stream<Path> fStream = Files.list(directory)) {
                                    worlds = fStream.filter(Files::isDirectory).collect(Collectors.toList());
                                }
                                boolean stillHasWorlds = false;
                                for (Path world : worlds) {
                                    if (LastAccessFile.isEverythingOlderThan(world, deleteUnusedRegionsAfterDays)) {
                                        try (Stream<Path> fStream = Files.list(world)) {
                                            fStream.forEach(toDelete::add);
                                        }
                                    } else {
                                        stillHasWorlds = true;
                                    }
                                }
                                if (!stillHasWorlds && LastAccessFile.isEverythingOlderThan(directory, deleteUnusedRegionsAfterDays)) {
                                    try (Stream<Path> fStream = Files.list(directory)) {
                                        fStream.forEach(toDelete::add);
                                    }
                                }
                            } else {
                                // Multi-world: Remove world directory files if the worlds.meta file is missing
                                try (Stream<Path> fStream = Files.walk(directory, 2)) {
                                    fStream.filter(it -> it.relativize(directory).getNameCount() == 2).forEach(toDelete::add);
                                }

                                if (LastAccessFile.isEverythingOlderThan(directory, deleteUnusedRegionsAfterDays)) {
                                    try (Stream<Path> fStream = Files.list(directory)) {
                                        fStream.filter(it -> !Files.isDirectory(it)).forEach(toDelete::add);
                                    }
                                }
                            }
                            return toDelete.stream();
                        } catch (IOException e) {
                            LOGGER.error("Failed to read last used file in " + directory + ":", e);
                        }
                        return Stream.empty();
                    })
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.error("Failed to index bobby cache for cleanup:", e);
            return;
        }

        for (Path path : toBeDeleted) {
            if (!Files.exists(path)) continue;
            try {
                Files.delete(path);
                deleteParentsIfEmpty(path);
            } catch (IOException e) {
                LOGGER.error("Failed to delete " + path + ":", e);
            }
        }
    }

    private static void deleteParentsIfEmpty(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent == null) {
            return;
        }
        try (Stream<Path> stream = Files.list(parent)) {
            if (stream.findAny().isPresent()) {
                return;
            }
        }
        Files.delete(parent);
        deleteParentsIfEmpty(parent);
    }

    private class TaintChunksConfigHandler {
        private boolean wasEnabled = getConfig().isTaintFakeChunks();

        public void update(BobbyConfig config) {
            client.execute(() -> setEnabled(config.isTaintFakeChunks()));
        }

        private void setEnabled(boolean enabled) {
            if (wasEnabled == enabled) {
                return;
            }
            wasEnabled = enabled;

            ClientLevel world = client.level;
            if (world == null) {
                return;
            }

            FakeChunkManager bobbyChunkManager = ((ClientChunkCacheExt) world.getChunkSource()).bobby_getFakeChunkManager();
            if (bobbyChunkManager == null) {
                return;
            }

            for (LevelChunk fakeChunk : bobbyChunkManager.getFakeChunks()) {
                ((FakeChunk) fakeChunk).setTainted(enabled);
            }
        }
    }

    private class MaxRenderDistanceConfigHandler {
        private int oldMaxRenderDistance = 0;

        {
            update(getConfig(), true);
        }

        public void update(BobbyConfig config) {
            update(config, false);
        }

        public void update(BobbyConfig config, boolean increaseOnly) {
            client.execute(() -> setMaxRenderDistance(config.getMaxRenderDistance(), increaseOnly));
        }

        @SuppressWarnings({"ConstantConditions", "unchecked"})
        private void setMaxRenderDistance(int newMaxRenderDistance, boolean increaseOnly) {
            if (oldMaxRenderDistance == newMaxRenderDistance) {
                return;
            }
            oldMaxRenderDistance = newMaxRenderDistance;

            OptionInstance<Integer> viewDistance = client.options.renderDistance();
            if (viewDistance.values() instanceof OptionInstance.IntRange callbacks) {
                OptionInstanceIntRangeAccessor callbacksAcc = (OptionInstanceIntRangeAccessor)(Object) callbacks;
                if (increaseOnly) {
                    callbacksAcc.setMaxInclusive(Math.max(callbacks.maxInclusive(), newMaxRenderDistance));
                } else {
                    callbacksAcc.setMaxInclusive(newMaxRenderDistance);
                }
                OptionInstanceAccessor<Integer> optionAccessor = (OptionInstanceAccessor<Integer>)(Object) viewDistance;
                optionAccessor.setCodec(callbacks.codec());
            }
        }
    }
}
