// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v263.commands;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.customcosmetics.features.bobby.v263.FakeChunkManager;
import com.customcosmetics.features.bobby.v263.Worlds;
import com.customcosmetics.features.bobby.v263.ext.ClientChunkCacheExt;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;

public class MergeWorldsCommand implements Command<FabricClientCommandSource> {
    @Override
    public int run(CommandContext<FabricClientCommandSource> context) {
        FabricClientCommandSource source = context.getSource();
        ClientLevel world = source.getLevel();

        FakeChunkManager bobbyChunkManager = ((ClientChunkCacheExt) world.getChunkSource()).bobby_getFakeChunkManager();
        if (bobbyChunkManager == null) {
            source.sendError(Component.translatable("bobby.upgrade.not_enabled"));
            return 0;
        }

        Worlds worlds = bobbyChunkManager.getWorlds();
        if (worlds == null) {
            source.sendError(Component.translatable("bobby.dynamic_multi_world.not_enabled"));
            return 0;
        }

        int sourceId = getInteger(context, "source");
        int targetId = getInteger(context, "target");

        worlds.userRequestedMerge(source, sourceId, targetId);

        return 0;
    }
}
