// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.RemotePlayer;

/** Unspawned client-only reference: Minecraft's own swing state machine with Noamm's duration hook. */
public final class NoammSwingReferencePlayer extends RemotePlayer {
    public int referenceDuration = 6;
    public NoammSwingReferencePlayer(ClientLevel level) {
        super(level, new GameProfile(java.util.UUID.randomUUID(), "NoammReference"));
    }
    public void step() { oAttackAnim = attackAnim; updateSwingTime(); }
}
