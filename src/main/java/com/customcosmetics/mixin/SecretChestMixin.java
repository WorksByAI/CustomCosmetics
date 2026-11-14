// SPDX-License-Identifier: GPL-3.0-only
// Adapted from Quoi's AutoCloseChest by Kyleen, jcnlk and contributors.
package com.customcosmetics.mixin;

import com.customcosmetics.features.gameplay.GameplayModule;
import com.customcosmetics.features.map.MapEditor;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenScreenPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class SecretChestMixin {
    @org.spongepowered.asm.mixin.Unique
    private static final Set<String> customcosmetics$secretTitles = Set.of("Chest", "Large Chest", "Trapped Chest");
    @org.spongepowered.asm.mixin.Unique
    private static final Set<MenuType<?>> customcosmetics$secretTypes = Set.of(MenuType.GENERIC_9x1, MenuType.GENERIC_9x2,
        MenuType.GENERIC_9x3, MenuType.GENERIC_9x4, MenuType.GENERIC_9x5, MenuType.GENERIC_9x6, MenuType.GENERIC_3x3);

    @Inject(method = "handleOpenScreen", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/network/protocol/game/ClientboundOpenScreenPacket;getType()Lnet/minecraft/world/inventory/MenuType;"),
        cancellable = true)
    private void customcosmetics$closeSecretChest(ClientboundOpenScreenPacket packet, CallbackInfo callback) {
        if (!GameplayModule.AUTO_CLOSE_CHEST.enabled() || !customcosmetics$secretTypes.contains(packet.getType())
                || !MapEditor.automaticDungeon(Minecraft.getInstance())
                || !customcosmetics$secretTitles.contains(packet.getTitle().getString().trim())) return;
        ((ClientPacketListener) (Object) this).send(new ServerboundContainerClosePacket(packet.getContainerId()));
        callback.cancel();
    }
}
