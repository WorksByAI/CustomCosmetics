// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest.mixin;
import com.customcosmetics.compatibility.NativeFileDialogs;
import com.customcosmetics.gametest.CapeGameTest;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Only the human's OS file choice is substituted; production UI and import logic still run. */
@Mixin(NativeFileDialogs.class)
public class CapeFileDialogProbeMixin {
    @Inject(method="openBackend",at=@At("HEAD"),cancellable=true)
    private static void selection(NativeFileDialogs.Request request,Consumer<NativeFileDialogs.Result> callback,CallbackInfo ci)throws Exception{
        NativeFileDialogs.validate(request.sdl());
        if(!request.sdl()){
            String version=(String)Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs").getMethod("tinyfd_getGlobalChar",CharSequence.class).invoke(null,"tinyfd_version");if(version==null||version.isEmpty())throw new AssertionError("TinyFD native library did not load");
        }
        if(CapeGameTest.deferFileSelection){CapeGameTest.delayedFileSelection=callback;CapeGameTest.deferFileSelection=false;}
        else{var selected=CapeGameTest.fileSelections.poll();if(selected==null)throw new AssertionError("Missing OS file selection fixture");callback.accept(selected);}
        ci.cancel();
    }
}
