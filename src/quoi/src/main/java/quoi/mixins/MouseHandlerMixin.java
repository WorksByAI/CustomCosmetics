package quoi.mixins;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import quoi.api.events.MouseEvent;

@Mixin(MouseHandler.class)
public class MouseHandlerMixin {

    @Inject(
            method = "onScroll",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (outsideGame(window)) return;

        if (new MouseEvent.Scroll(horizontal, vertical).post()) ci.cancel();
    }

    @Inject(
            method = "onButton",
            at = @At("HEAD"),
            cancellable = true
    )
    private void onMouseClick(long window, MouseButtonInfo input, int action, CallbackInfo ci) {
        if (outsideGame(window) || !Minecraft.getInstance().mouseHandler.isMouseGrabbed()) return;
        if (new MouseEvent.Click(input.button(), action == 1).post()) ci.cancel();
    }

    @Inject(
            method = "onMove",
            at = @At("HEAD"),
            cancellable = true
    )
    //~ if >=26.3 'double my, CallbackInfo' -> 'double my, double dx, double dy, CallbackInfo'
    private void onMouseMove(long window, double mx, double my, CallbackInfo ci) {
        if (outsideGame(window)) return;
        if (new MouseEvent.Move(mx, my).post()) ci.cancel();
    }

    @Unique
    private boolean outsideGame(long window) {
        Minecraft mc = Minecraft.getInstance();
        return window != mc.getWindow().handle() || quoi.compat.GameAccess.INSTANCE.screen() != null;
    }

}
