// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.mixin;

import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.gameplay.AutoClick;
import com.customcosmetics.features.gameplay.GameplayFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

public final class GameplayMixin {
    @Mixin(LocalPlayer.class)
    public static class PlayerPicking {
        @org.spongepowered.asm.mixin.injection.ModifyArg(method = "pick", index = 4, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/entity/projectile/ProjectileUtil;getEntityHitResult(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;D)Lnet/minecraft/world/phys/EntityHitResult;"))
        private static java.util.function.Predicate<Entity> csb$pickVisiblePlayers(java.util.function.Predicate<Entity> original) {
            if (!ConfigManager.hidePlayersClickThrough || !com.customcosmetics.features.gameplay.GameplayModule.HIDE_PLAYERS.enabled()) return original;
            return entity -> original.test(entity) && !GameplayFeatures.hidePlayer(entity);
        }
    }

    @Mixin(net.minecraft.client.multiplayer.MultiPlayerGameMode.class)
    public static class AttackEntity {
        @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
        private void csb$attunement(net.minecraft.world.entity.player.Player player, Entity target, CallbackInfo ci) {
            if (GameplayFeatures.dojoBlocksAttack(target)) ci.cancel();
        }

        @Inject(method = "useItemOn", at = @At("HEAD"))
        private void csb$simonStart(LocalPlayer player, net.minecraft.world.InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit,
                                   CallbackInfoReturnable<net.minecraft.world.InteractionResult> ci) {
            if (hand == net.minecraft.world.InteractionHand.MAIN_HAND)
                com.customcosmetics.features.gameplay.FloorSevenDevices.manualSimonStart(Minecraft.getInstance(), hit.getBlockPos());
        }
    }

    @Mixin(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen.class)
    public static class ContainerTools {
        @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
        private void csb$experimentClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick,
                                        CallbackInfoReturnable<Boolean> callback) {
            if (com.customcosmetics.features.gameplay.AutoExperiments.blocksManualClick(Minecraft.getInstance(),
                (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) (Object) this)) callback.setReturnValue(true);
        }
        @Inject(method = "extractRenderState", at = @At("TAIL"))
        private void csb$mirrorverseRecipes(net.minecraft.client.gui.GuiGraphicsExtractor graphics, int mouseX,
                                           int mouseY, float delta, CallbackInfo callback) {
            GameplayFeatures.gameplayRecipes(graphics, (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) (Object) this);
        }

    }

    @Mixin(net.minecraft.client.multiplayer.ClientPacketListener.class)
    public static class GameplayPackets {
        @Inject(method = {"handleContainerSetSlot", "handleContainerContent"}, at = @At("TAIL"))
        private void customcosmetics$experimentBoard(CallbackInfo callback) {
            com.customcosmetics.features.gameplay.AutoExperiments.updated(Minecraft.getInstance());
        }

        @Inject(method = "handleParticleEvent", at = @At("TAIL"))
        private void customcosmetics$gameplayParticles(net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket packet, CallbackInfo callback) {
            GameplayFeatures.gameplayParticles(packet);
        }

        @Inject(method = "setSubtitleText", at = @At("TAIL"))
        private void customcosmetics$gameplaySubtitle(net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket packet, CallbackInfo callback) {
            GameplayFeatures.gameplaySubtitle(packet.text());
        }

        @Inject(method = "handleSoundEvent", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/multiplayer/ClientLevel;playSeededSound(Lnet/minecraft/world/entity/Entity;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V"),
            cancellable = true)
        private void customcosmetics$gameplaySound(net.minecraft.network.protocol.game.ClientboundSoundPacket packet, CallbackInfo callback) {
            if (GameplayFeatures.gameplaySound(packet)) callback.cancel();
        }

        @Inject(method = "handleSetEntityData", at = @At("TAIL"))
        private void customcosmetics$pestMetadata(net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket packet, CallbackInfo callback) {
            GameplayFeatures.pestEntityUpdated(packet.id());
        }

        @Inject(method = "handleSetEquipment", at = @At("RETURN"))
        private void customcosmetics$equipment(net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket packet, CallbackInfo callback) {
            GameplayFeatures.gameplayEquipment(packet.getEntity());
        }

    }

    @Mixin(net.minecraft.client.gui.components.BossHealthOverlay.class)
    public static class BossBars {
        @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
        private java.util.Map<java.util.UUID, net.minecraft.client.gui.components.LerpingBossEvent> events;

        @Inject(method = "update", at = @At("TAIL"))
        private void update(net.minecraft.network.protocol.game.ClientboundBossEventPacket packet, CallbackInfo callback) {
            GameplayFeatures.bossBar(packet, events);
        }
    }

    @Mixin(net.minecraft.client.multiplayer.ClientLevel.class)
    public static class DeviceBlocks {
        @Inject(method = "setServerVerifiedBlockState", at = @At("HEAD"))
        private void beforeUpdate(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                                  int flags, CallbackInfo callback) {
            com.customcosmetics.features.gameplay.SecretAura.blockChanging(pos, state);
            GameplayFeatures.otherBlockChanging(pos, state);
        }

        @Inject(method = "setServerVerifiedBlockState", at = @At("TAIL"))
        private void observe(net.minecraft.core.BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
                             int flags, CallbackInfo callback) {
            com.customcosmetics.features.gameplay.FloorSevenDevices.blockChanged(pos, state);
        }
    }

    @Mixin(value = Minecraft.class, priority = 900)
    public abstract static class Attack implements GameplayFeatures.ClickActions {
        @org.spongepowered.asm.mixin.gen.Invoker("startAttack")
        @Override public abstract boolean csb$attack();

        @org.spongepowered.asm.mixin.gen.Invoker("startUseItem")
        @Override public abstract void csb$use();

        @ModifyVariable(method = "continueAttack", at = @At("HEAD"), argsOnly = true)
        private boolean csb$includeAutoClickMining(boolean manual) {
            return manual || AutoClick.continuesMining((Minecraft) (Object) this);
        }

        @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
        private void startLeap(CallbackInfoReturnable<Boolean> callback) {
            if (GameplayFeatures.startLeap((Minecraft) (Object) this)) callback.setReturnValue(false);
        }

    }

    @Mixin(EntityRenderDispatcher.class)
    public static class HidePlayers {
        @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDD)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
        private void hideLegacy(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> callback) {
            if (GameplayFeatures.hidePlayer(entity)) callback.setReturnValue(false);
        }

        @Inject(method = "shouldRender(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/culling/Frustum;DDDF)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
        private void hideModern(Entity entity, Frustum frustum, double x, double y, double z, float partial, CallbackInfoReturnable<Boolean> callback) {
            if (GameplayFeatures.hidePlayer(entity)) callback.setReturnValue(false);
        }
    }

}
