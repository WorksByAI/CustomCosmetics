package com.customcosmetics.gametest.mixin;
import com.customcosmetics.features.map.MapEditor;
import com.customcosmetics.gametest.FreecamGameTest;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(MapEditor.class)
public class FmeInputTrace {
    @Inject(method="click",at=@At("HEAD"))
    private static void trace(int button,CallbackInfoReturnable<Boolean> cir){if(FreecamGameTest.observe)System.out.println("FME_INPUT button="+button+" "+MapEditor.status());}
}
