// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.features.items.ArmorItemIdentity;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ArmorIdentityMixin {
    @Inject(method="handleSetEquipment",at=@At("HEAD"))
    private void customcosmetics$beforeSlimArmor(ClientboundSetEquipmentPacket packet,CallbackInfo ci){ArmorItemIdentity.equipment(packet.getEntity());}
    @Inject(method="handleSetEquipment",at=@At("RETURN"))
    private void customcosmetics$afterSlimArmor(ClientboundSetEquipmentPacket packet,CallbackInfo ci){ArmorItemIdentity.equipment(packet.getEntity());}
    @Inject(method={"handleContainerContent","handleContainerSetSlot","handleSetPlayerInventory"},at=@At("RETURN"))
    private void customcosmetics$inventoryArmor(CallbackInfo ci){ArmorItemIdentity.tick();}
}
