// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v263.mixin;

import com.mojang.serialization.Codec;
import net.minecraft.client.OptionInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(OptionInstance.class)
public interface OptionInstanceAccessor<T> {
    @Accessor
    @Mutable
    void setCodec(Codec<T> value);
}
