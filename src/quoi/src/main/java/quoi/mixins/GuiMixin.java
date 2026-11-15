package quoi.mixins;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.utils.skyblock.player.container.task.ContainerManager;

//? if <26.2
//@Mixin(net.minecraft.client.Minecraft.class)
//? if >=26.2
@Mixin(Gui.class)
public class GuiMixin {
    @Inject(
            method = "setScreen",
            at = @At("HEAD"),
            cancellable = true
    )
    private void quoi$onSetScreen(Screen screen, CallbackInfo ci) {
        if (ContainerManager.onSetScreen(screen)) ci.cancel();
    }
}
