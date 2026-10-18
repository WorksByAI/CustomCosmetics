package com.customcosmetics.gametest.mixin;
import com.customcosmetics.features.chat.CommandTooltips;
import com.customcosmetics.gametest.EagerMapGameTest;
import net.minecraft.network.chat.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(CommandTooltips.class)
public class CommandTooltipProbe {
    @Inject(method="enrich",at=@At("RETURN"))
    private static void observed(Style style,CallbackInfoReturnable<Style> cir){if(style!=null&&style.getClickEvent() instanceof ClickEvent.RunCommand run&&run.command().equals("/party accept NativeMate"))EagerMapGameTest.hovered=cir.getReturnValue();}
}
