// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.UiFontAccess;
import com.customcosmetics.config.*;
import com.customcosmetics.gui.CosmeticsScreen;
import com.customcosmetics.gui.PinkUi;
import com.customcosmetics.gui.UiFonts;
import com.customcosmetics.sync.ExperimentalAccess;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.google.gson.*;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.InactivityFpsLimit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.renderer.state.gui.GlyphRenderState;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.joml.Matrix3x2f;

/** Actual render-call intervals plus separately labelled GUI extraction CPU/allocation. Not Hypixel FPS. */
public final class UiPerformanceGameTest implements FabricClientGameTest {
    private static final com.sun.management.ThreadMXBean BEAN=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
    private static final int SECONDS=Integer.getInteger("customcosmetics.uiSampleSeconds",6);
    private static volatile Sample sample;private static long entered;
    public static void begin(){var s=sample;if(s!=null&&!s.done){entered=System.nanoTime();s.before=BEAN.getCurrentThreadAllocatedBytes();}}
    public static void end(){var s=sample;if(s!=null&&!s.done&&s.cpuCount<s.cpu.length){s.cpu[s.cpuCount]=System.nanoTime()-entered;s.bytes[s.cpuCount++]=BEAN.getCurrentThreadAllocatedBytes()-s.before;}}
    public static void frame(){var s=sample;if(s==null||s.done)return;long now=System.nanoTime();if(s.started==0){s.started=s.previous=now;return;}if(s.frames<s.intervals.length)s.intervals[s.frames++]=now-s.previous;s.previous=now;if(net.minecraft.client.Minecraft.getInstance().isWindowActive())s.focused++;if(now-s.started>=SECONDS*1_000_000_000L||s.frames==s.intervals.length)s.done=true;}
    private static final class Sample {final long[] intervals=new long[65536],cpu=new long[65536],bytes=new long[65536];int frames,cpuCount,focused;long before,started,previous;volatile boolean done;}
    private static double percentile(long[] a,double p){return a.length==0?0:a[Math.min(a.length-1,(int)Math.ceil(a.length*p)-1)]/1e6;}
    private static long gcMillis(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean->Math.max(0,bean.getCollectionTime())).sum();}
    private static long gcCount(){return ManagementFactory.getGarbageCollectorMXBeans().stream().mapToLong(bean->Math.max(0,bean.getCollectionCount())).sum();}
    @Override public void runTest(ClientGameTestContext context){
        var rows=new JsonArray();jdk.jfr.Recording recording=null;
        try{
            BEAN.setThreadAllocatedMemoryEnabled(true);
            context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.updateCheckEnabled=false;ConfigManager.uiBackgroundBlur=false;ConfigManager.uiAnimationsEnabled=true;ConfigManager.uiLanguage=System.getProperty("customcosmetics.uiLanguage","en_us");FeatureModules.all(false);try{ConfigManager.class.getField("uiFont").set(null,System.getProperty("customcosmetics.uiFont","vanilla"));}catch(ReflectiveOperationException oldVersion){};});
            if(Boolean.getBoolean("customcosmetics.profileUi")){recording=new jdk.jfr.Recording(jdk.jfr.Configuration.getConfiguration("profile"));recording.setMaxSize(64*1024*1024);recording.start();}
            try(var world=context.worldBuilder().setUseConsistentSettings(true).adjustSettings(s->s.setSeed("676767")).create()){
                world.getServer().runCommand("time set noon");context.getInput().resizeWindow(1600,1000);
                context.runOnClient(client->{client.options.guiScale().set(2);client.options.enableVsync().set(false);client.options.framerateLimit().set(260);client.options.inactivityFpsLimit().set(InactivityFpsLimit.MINIMIZED);client.options.pauseOnLostFocus=false;});
                context.waitTicks(40);context.getInput().setCursorPos(1595,995);
                String fontPath = System.getenv("CCOS_TEST_FONT");
                String fontWeights = System.getenv("CCOS_TEST_FONT_BOLD");
                List<String> phases = fontPath == null ? List.of("CLOSED","MAIN","ANIMATIONS","DAMAGE","THEME","CLOSED_AFTER")
                    : List.of("CLOSED_VANILLA", "FONTS_VANILLA", "FONTS_APPLY", "FONTS_LOCAL", "CLOSED_LOCAL", "CLOSED_RESTORED");
                if (fontPath != null) {
                    var access = ExperimentalAccess.class.getDeclaredField("status");
                    access.setAccessible(true);
                    context.runOnClient(client -> {
                        ConfigManager.checkExperimentalAccess = false;
                        ConfigManager.offlineMode = false;
                        ConfigManager.useFontInGame = true;
                        try { access.set(null, ExperimentalAccess.Status.GRANTED); }
                        catch (IllegalAccessException error) { throw new AssertionError(error); }
                    });
                    world.getServer().runCommand("gamemode creative @a");
                    world.getServer().runCommand("fill -4 84 -3 4 84 8 minecraft:stone");
                    world.getServer().runCommand("tp @a 0 85 0 0 0");
                    for (int i = -3; i <= 3; i++) {
                        world.getServer().runCommand("summon minecraft:text_display " + (i * 0.8) + " 86.6 5 "
                            + "{text:{text:'[522] ToryHi ♦ 5145 ❤ 山海'},billboard:'center',shadow:1b}");
                    }
                }
                for(String phase:phases){
                    context.runOnClient(client->{
                        if (phase.equals("CLOSED_RESTORED")) UiFonts.clear();
                        var page = phase.startsWith("FONTS_") ? "FONTS" : phase;
                        ClientUiAccess.setScreen(client,phase.startsWith("CLOSED")?null:new CosmeticsScreen(null,CosmeticsScreen.Page.valueOf(page)));
                    });
                    context.waitTicks(25);
                    long startMillis=System.currentTimeMillis(),gcMillis=gcMillis(),gcCount=gcCount();
                    var current=new Sample();
                    var fontApplied = new CompletableFuture<Boolean>();
                    context.runOnClient(client->{
                        sample=current;
                        if (phase.equals("FONTS_APPLY")) {
                            long start = System.nanoTime();
                            int sampling = fontWeights == null ? 2 : UiFonts.MAX_SAMPLING;
                            UiFonts.load(client, Path.of(fontPath), new UiFonts.Style(9, 0, sampling, 0, 1), success -> {
                                System.out.println("FONT_APPLY_LATENCY_MS " + (System.nanoTime() - start) / 1e6);
                                if (success && fontWeights != null) ConfigManager.uiFontPath = fontPath;
                                ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.FONTS));
                                fontApplied.complete(success);
                            });
                        }
                    });
                    context.waitFor(client->current.done,800);context.runOnClient(client->sample=null);
                    if (phase.equals("FONTS_APPLY")) {
                        context.waitFor(client -> fontApplied.isDone(), 300);
                        if (!fontApplied.join()) throw new AssertionError("Local font failed to load");
                        context.runOnClient(UiPerformanceGameTest::assertFontSampling);
                        if (fontWeights != null) assertFontWeights(context, fontWeights.equals("available"));
                    }
                    if (phase.equals("FONTS_LOCAL")) context.takeScreenshot("fonts_local");
                    if(current.frames<20)throw new AssertionError("Too few real rendered frames in "+phase);
                    if(phase.startsWith("CLOSED")&&current.cpuCount!=0)throw new AssertionError("Closed UI still extracted controls");
                    long[] intervals=Arrays.copyOf(current.intervals,current.frames),cpu=Arrays.copyOf(current.cpu,current.cpuCount);Arrays.sort(intervals);Arrays.sort(cpu);long total=0,slow=0,cpuTotal=0,allocated=0;for(long t:intervals)total+=t;for(long t:cpu)cpuTotal+=t;for(int i=0;i<current.cpuCount;i++)allocated+=current.bytes[i];int tail=Math.max(1,(int)Math.ceil(current.frames*.01));for(int i=current.frames-tail;i<current.frames;i++)slow+=intervals[i];
                    var row=new JsonObject();row.addProperty("page",phase);row.addProperty("sampleStartEpochMillis",startMillis);row.addProperty("sampleEndEpochMillis",System.currentTimeMillis());row.addProperty("gcMillis",gcMillis()-gcMillis);row.addProperty("gcCollections",gcCount()-gcCount);row.addProperty("actualFrames",current.frames);row.addProperty("focusedFrames",current.focused);row.addProperty("averageFps",current.frames*1e9/total);row.addProperty("onePercentLowFps",tail*1e9/slow);row.addProperty("meanFrameMs",total/1e6/current.frames);row.addProperty("p95FrameMs",percentile(intervals,.95));row.addProperty("p99FrameMs",percentile(intervals,.99));row.addProperty("maxFrameMs",percentile(intervals,1));row.addProperty("guiExtractions",current.cpuCount);row.addProperty("meanGuiCpuMs",current.cpuCount==0?0:cpuTotal/1e6/current.cpuCount);row.addProperty("p95GuiCpuMs",percentile(cpu,.95));row.addProperty("guiBytesPerFrame",current.cpuCount==0?0:allocated/(double)current.cpuCount);rows.add(row);
                }
                if (fontPath != null) {
                    String inlineBold = System.getenv("CCOS_TEST_INLINE_BOLD");
                    boolean chatFixture = System.getenv("CCOS_TEST_INLINE_CHAT") != null;
                    world.getServer().runCommand("kill @e[type=minecraft:text_display]");
                    world.getServer().runCommand("summon minecraft:text_display 0.5 86.6 5 "
                        + "{text:[{text:'[522] ',color:'dark_red'},{text:'ToryHi ',color:'red'},"
                        + "{text:'♦ ',color:'aqua'},{text:'5145 ❤ 山海',color:'white'}],billboard:'center',shadow:1b}");
                    var applied = new CompletableFuture<Boolean>();
                    var style = chatFixture ? new UiFonts.Style(9.5f, .3f, 5, 0, .6f)
                        : new UiFonts.Style(9, 0, inlineBold == null ? 2 : UiFonts.MAX_SAMPLING, 0, 1);
                    context.runOnClient(client -> UiFonts.load(client, Path.of(fontPath),
                        style, applied::complete));
                    context.waitFor(client -> applied.isDone(), 300);
                    if (!applied.join()) throw new AssertionError("Local font failed to reload");
                    context.waitTicks(5);
                    context.takeScreenshot("world_font");
                    if (inlineBold != null) {
                        world.getServer().runCommand("scoreboard objectives add cc_bold dummy {text:'SKYBLOCK',color:'yellow',bold:true}");
                        world.getServer().runCommand("scoreboard players set 'Purse' cc_bold 125");
                        world.getServer().runCommand("scoreboard objectives setdisplay sidebar cc_bold");
                        context.waitTicks(5);
                        context.takeScreenshot("inline_bold_sidebar");
                        context.runOnClient(client -> checkInlineBold(client, inlineBold));
                    }
                    if (chatFixture) {
                        context.runOnClient(UiPerformanceGameTest::addChatFixture);
                        context.waitTicks(5);
                        context.takeScreenshot("inline_bold_chat");
                    }
                    if (System.getenv("CCOS_TEST_SKYOCEAN_CHAT") != null) {
                        context.runOnClient(UiPerformanceGameTest::addSkyOceanChatFixture);
                        context.waitTicks(5);
                        context.takeScreenshot("skyocean_forge_chat");
                    }
                }
                context.runOnClient(client->ClientUiAccess.setScreen(client,null));
            }
            var root = new JsonObject();
            root.addProperty("scope", "isolated fixed world, actual frame intervals; not Hypixel gameplay");
            root.addProperty("sampleSeconds", SECONDS);
            String fontFile = System.getenv("CCOS_TEST_FONT");
            root.addProperty("font", fontFile == null ? System.getProperty("customcosmetics.uiFont", "vanilla") : Path.of(fontFile).getFileName().toString());
            root.addProperty("language", System.getProperty("customcosmetics.uiLanguage", "en_us"));
            root.addProperty("backend", backendName());
            root.add("scenes", rows);
            var output=FabricLoader.getInstance().getGameDir().resolve("ui-performance");Files.createDirectories(output);Files.writeString(output.resolve("frames.json"),new GsonBuilder().setPrettyPrinting().create().toJson(root));if(recording!=null){recording.stop();recording.dump(output.resolve("ui.jfr"));}
            System.out.println("UI_ACTUAL_FRAME_INTERVALS_EXTRACTION_AND_CLOSED_BASELINES_OK "+output);
        }catch(Exception error){throw new AssertionError(error);}finally{sample=null;if(recording!=null)recording.close();}
    }

    private static String backendName() throws ReflectiveOperationException {
        Object device = com.mojang.blaze3d.systems.RenderSystem.getDevice();
        try {
            Object info = device.getClass().getMethod("getDeviceInfo").invoke(device);
            return (String) info.getClass().getMethod("backendName").invoke(info);
        } catch (NoSuchMethodException legacyDevice) {
            return (String) device.getClass().getMethod("getBackendName").invoke(device);
        }
    }

    private static void assertFontWeights(ClientGameTestContext context, boolean expected) {
        context.runOnClient(client -> {
            if (UiFonts.boldAvailable() != expected || fontBoldToggle(client).active != expected
                    || ConfigManager.uiFontOversample != UiFonts.MAX_SAMPLING)
                throw new AssertionError("Unexpected bold control or sampling default");
        });
        if (!expected) return;
        String sample = "ABCDEFGHIJKLMNOPQRSTUVWXYZ abcdefghijklmnopqrstuvwxyz";
        int regular = context.computeOnClient(client -> client.font.width(sample));
        float inlineBold = context.computeOnClient(client ->
            client.font.getSplitter().stringWidth(Component.literal(sample).withStyle(Style.EMPTY.withBold(true))));
        context.runOnClient(client -> clickFontBold(client));
        context.waitFor(client -> UiFonts.boldActive() && Boolean.TRUE.equals(ConfigManager.uiFontBold), 150);
        context.runOnClient(client -> {
            if (client.font.width(sample) == regular) throw new AssertionError("Expected physical bold glyph metrics");
            if ("fixed".equals(System.getenv("CCOS_TEST_INLINE_BOLD"))
                    && Math.abs(client.font.getSplitter().stringWidth(sample) - inlineBold) > .001f)
                throw new AssertionError("Game-formatted bold did not use the physical bold face");
            assertFontSampling(client);
            if ("fixed".equals(System.getenv("CCOS_TEST_INLINE_BOLD"))) checkInlineBold(client, "fixed");
            UiFonts.clear();
            UiFonts.restore(client);
        });
        context.waitFor(client -> UiFonts.boldActive(), 150);
        context.runOnClient(client -> clickFontBold(client));
        context.waitFor(client -> !UiFonts.boldActive() && Boolean.FALSE.equals(ConfigManager.uiFontBold), 150);
        System.out.println("REAL_FONT_BOLD_SWITCH_RESTORE_AND_MAX_SAMPLING_OK");
    }

    private static PinkUi.Toggle fontBoldToggle(Minecraft client) {
        return ClientUiAccess.screen(client).children().stream().filter(PinkUi.Toggle.class::isInstance)
            .map(PinkUi.Toggle.class::cast).filter(toggle -> toggle.getMessage().getString().startsWith("Bold Font"))
            .findFirst().orElseThrow();
    }

    private static void clickFontBold(Minecraft client) {
        fontBoldToggle(client).onClick(new net.minecraft.client.input.MouseButtonEvent(0, 0,
            new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
    }

    private static void assertFontSampling(Minecraft client) {
        var fallback = ((UiFontAccess) (Object) client.font).customcosmetics$provider().glyphs(FontDescription.DEFAULT);
        var local = UiFonts.gameSource(FontDescription.DEFAULT, fallback);
        var glyph = local.getGlyph('W').createGlyph(0, 0, 0xFFFFFFFF, 0, Style.EMPTY, 0, 0);
        var worldAtlas = UiFonts.localAtlas(glyph.textureView());
        var guiSampler = new GlyphRenderState(new Matrix3x2f(), glyph, null).textureSetup().sampler0();
        if (worldAtlas == null || !worldAtlas.getSampler().getMinFilter().name().equals("LINEAR")
                || !guiSampler.getMinFilter().name().equals("LINEAR")) {
            throw new AssertionError("Local font atlas must use linear sampling in the GUI and world");
        }
        if (local.getGlyph(0xE000) != fallback.getGlyph(0xE000)) {
            throw new AssertionError("Local font replaced a private-use glyph");
        }
        System.out.println("LOCAL_FONT_GUI_WORLD_SAMPLING_AND_PRIVATE_USE_FALLBACK_OK");
    }

    private static void checkInlineBold(Minecraft client, String mode) {
        var plain = Component.literal("K");
        var bold = plain.copy().withStyle(Style.EMPTY.withBold(true));
        if (!mode.equals("baseline") && (!UiFonts.boldAvailable() || UiFonts.boldActive())) {
            float extra = client.font.getSplitter().stringWidth(bold) - client.font.getSplitter().stringWidth(plain);
            if (UiFonts.boldActive() ? extra != 0 : extra <= 0 || extra >= 1)
                throw new AssertionError("Bold text did not use the rasterized advance: " + extra);
        }
        int[] vertices = {0};
        var consumer = (VertexConsumer) java.lang.reflect.Proxy.newProxyInstance(VertexConsumer.class.getClassLoader(),
            new Class<?>[]{VertexConsumer.class}, (proxy, method, args) -> {
                if (method.getName().equals("addVertex")) vertices[0]++;
                return proxy;
            });
        client.font.prepareText(bold.getVisualOrderText(), 0, 0, 0xFFFFFFFF, true, false, 0)
            .visit(new Font.GlyphVisitor() {
                @Override public void acceptGlyph(TextRenderable.Styled glyph) {
                    if (UiFonts.localAtlas(glyph.textureView()) == null) throw new AssertionError("Expected a local glyph");
                    glyph.render(new org.joml.Matrix4f(), consumer, 0xF000F0, true);
                }
            });
        if (vertices[0] != (mode.equals("baseline") ? 16 : 8))
            throw new AssertionError("Unexpected bold foreground/shadow geometry: " + vertices[0]);
        System.out.println("INLINE_BOLD_VERTICES " + vertices[0]);
    }

    private static void addChatFixture(Minecraft client) {
        client.options.textBackgroundOpacity().set(0.0);
        var aqua = Style.EMPTY.withColor(0x55FFFF).withBold(true);
        var message = Component.literal("[Party] ").withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE)
            .append(Component.literal("[SkyOcean]").withStyle(aqua))
            .append(Component.literal(" Foraging").withStyle(net.minecraft.ChatFormatting.GRAY));
        ClientUiAccess.chat(client).addClientSystemMessage(message);
        ClientUiAccess.chat(client).addClientSystemMessage(
            Component.literal("[SkyOcean]").withStyle(aqua.withShadowColor(0))
                .append(Component.literal(" With no shadow")));
    }

    private static void addSkyOceanChatFixture(Minecraft client) {
        client.options.textBackgroundOpacity().set(0.0);
        var message = Component.literal("Forge Reminder").withColor(0xCDD6F4)
            .append(Component.literal(" | ").withColor(0x585B70))
            .append(Component.literal("7x ").withColor(0xAAAAAA))
            .append(Component.literal("Skeleton Key"))
            .append(Component.literal(" [").withColor(0xFFFF55))
            .append(Component.literal("Click to warp").withColor(0xFFAA00)
                .withStyle(net.minecraft.ChatFormatting.UNDERLINE))
            .append(Component.literal("]").withColor(0xFFFF55));
        try {
            var chatUtils = Class.forName("me.owdding.skyocean.utils.chat.ChatUtils");
            var instance = chatUtils.getField("INSTANCE").get(null);
            chatUtils.getMethod("chat", Component.class).invoke(instance, message);
            var prefix = (Component) chatUtils.getMethod("getPrefix").invoke(instance);
            int[] gradientGlyphs = {0, 0};
            client.font.prepareText(prefix.getVisualOrderText(), 0, 0, 0xFFFFFFFF, true, false, 0)
                .visit(new Font.GlyphVisitor() {
                    @Override public void acceptGlyph(TextRenderable.Styled glyph) {
                        var pipeline = new GlyphRenderState(new Matrix3x2f(), glyph, null).pipeline();
                        if (pipeline.getLocation().getNamespace().equals("meowdding-lib")
                                && UiFonts.localAtlas(glyph.textureView()) != null) {
                            gradientGlyphs[0]++;
                            if (pipeline.getShaderDefines().flags().contains("IS_GRAYSCALE")) gradientGlyphs[1]++;
                        }
                    }
                });
            if (gradientGlyphs[0] == 0 || "fixed".equals(System.getenv("CCOS_TEST_SKYOCEAN_CHAT"))
                    && gradientGlyphs[0] != gradientGlyphs[1]) {
                throw new AssertionError("Unexpected grayscale gradient glyph count: " + Arrays.toString(gradientGlyphs));
            }
            System.out.println("SKYOCEAN_PREFIX_GRAYSCALE " + Arrays.toString(gradientGlyphs));
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not send the SkyOcean fixture", error);
        }
    }
}
