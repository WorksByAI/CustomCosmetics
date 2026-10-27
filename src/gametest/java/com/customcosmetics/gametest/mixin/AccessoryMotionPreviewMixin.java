// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;
import com.customcosmetics.gametest.AccessoriesGameTest;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Test only: animate render snapshots, never the live player or inputs. */
@Mixin(net.minecraft.client.model.player.PlayerModel.class)
public class AccessoryMotionPreviewMixin {
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("HEAD"))
    private void motion(AvatarRenderState state,CallbackInfo ci){
        int phase=AccessoriesGameTest.previewPose;if(phase==0)return;
        state.yRot=phase%2==0?55:-55;state.xRot=phase%2==0?25:-20;
        if(phase<5){state.walkAnimationSpeed=1;state.walkAnimationPos=phase*1.1f;}
        else try {
            float progress = (phase - 4) * .25f;
            try { state.getClass().getField("attackTime").setFloat(state, progress); }
            catch (NoSuchFieldException modern) {
                state.getClass().getField("swingAnimation").setFloat(state, progress);
                var field = state.getClass().getField("currentSwing");
                field.set(state, field.getType().getConstructor(net.minecraft.world.InteractionHand.class,
                    net.minecraft.world.item.component.SwingAnimation.class, int.class).newInstance(
                        net.minecraft.world.InteractionHand.MAIN_HAND,
                        net.minecraft.world.item.component.SwingAnimation.DEFAULT, 6));
            }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    @Inject(method="setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",at=@At("TAIL"))
    private void verify(AvatarRenderState state,CallbackInfo ci){int phase=AccessoriesGameTest.previewPose;if(phase!=0){var model=(net.minecraft.client.model.player.PlayerModel)(Object)this;if(Math.abs(model.head.yRot)<.2)throw new AssertionError("Head turn fixture was not rendered");if(phase<5&&Math.abs(model.rightLeg.xRot)+Math.abs(model.leftLeg.xRot)<.1)throw new AssertionError("Walking fixture stayed stationary");AccessoriesGameTest.motionPhases.add(phase);}}
}
