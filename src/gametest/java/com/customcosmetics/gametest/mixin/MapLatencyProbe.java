package com.customcosmetics.gametest.mixin;

import com.customcosmetics.features.map.MapEditor;
import static com.customcosmetics.gametest.MapEditPerformanceGameTest.*;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

public final class MapLatencyProbe {
    @Mixin(Minecraft.class)
    public static class Frames {
        private long mapFrameStart;
        private long mapGcStart;
        @Inject(method="runTick",at=@At("HEAD")) private void start(CallbackInfo ci){mapFrameStart=active?System.nanoTime():0;if(active)mapGcStart=mapGcTime();}
        @Inject(method="runTick",at=@At("RETURN")) private void end(CallbackInfo ci){if(active&&mapFrameStart!=0){long elapsed=System.nanoTime()-mapFrameStart;maxFrame=Math.max(maxFrame,elapsed);if(elapsed>250_000_000L)System.out.println("MAP_FRAME_SPIKE_MS="+elapsed/1_000_000+" GC_MS="+(mapGcTime()-mapGcStart));}}
    }
    @Mixin(MapEditor.class)
    public static class Ticks {
        private static long mapTickStart;
        @Inject(method="tick",at=@At("HEAD")) private static void start(CallbackInfo ci){mapTickStart=active?System.nanoTime():0;}
        @Inject(method="tick",at=@At("RETURN")) private static void end(CallbackInfo ci){if(active&&mapTickStart!=0)maxTick=Math.max(maxTick,System.nanoTime()-mapTickStart);}
    }
}
