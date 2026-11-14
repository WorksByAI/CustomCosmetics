// SPDX-License-Identifier: LGPL-3.0-only
package com.customcosmetics.features.motionblur.v263.mixin;

import net.minecraft.client.renderer.PostPass;
import net.minecraft.client.renderer.PostChain;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import java.util.List;

@Mixin(PostChain.class)
public interface PostChainAccessor {
    @Accessor List<PostPass> getPasses();
}
