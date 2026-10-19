package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.config.*;
import com.customcosmetics.features.map.*;
import com.customcosmetics.features.nickname.*;
import com.customcosmetics.features.smolpeople.SmolPlayers;
import com.customcosmetics.gui.*;
import com.customcosmetics.utils.NickRenderUtils;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.network.chat.*;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.*;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class ClientPerformanceGameTest implements FabricClientGameTest {
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.partyEspEnabled=false;ConfigManager.partyChatEnabled=false;FeatureModules.all(true);});
        try(var world=context.worldBuilder().create()){
            context.waitTicks(20);
            context.runOnClient(client->{
                var board=client.level.getScoreboard();
                var objective=board.addObjective("performance",ObjectiveCriteria.DUMMY,Component.literal("SKYBLOCK"),ObjectiveCriteria.RenderType.INTEGER,false,null);
                var team=board.addPlayerTeam("perf_location");team.setPlayerPrefix(Component.literal("⏣ The Catacombs (M7)"));board.addPlayerToTeam("§u",team);
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("§u"),objective).set(1);board.setDisplayObjective(DisplaySlot.SIDEBAR,objective);
                var first=DungeonSidebarReader.read(client);check(first.detected()&&first.floor().equals("M7"),"M7 sidebar cache changed detection");
                check(first==DungeonSidebarReader.read(client),"Unchanged sidebar was reparsed");
                team.setPlayerPrefix(Component.literal("⏣ Hub"));var outside=DungeonSidebarReader.read(client);check(!outside.detected(),"Team changes did not invalidate the sidebar immediately");
                check(DungeonSidebarReader.read(client,true).raw().size()>0,"Diagnostics lost raw sidebar details");
                var paint=new NicknameGradient("shimmer",0x24CFDF,0xEA78C5,1);
                var a=NicknameGlyphEffect.attach(Style.EMPTY,new NicknameGlyphEffect(paint,0,.5));
                var b=NicknameGlyphEffect.attach(Style.EMPTY,new NicknameGlyphEffect(paint,.5,1));
                check(!a.equals(b)&&a.hashCode()!=b.hashCode(),"Optimized style hooks merged different glyph spans");
                check(NicknameGlyphEffect.from(a.applyTo(Style.EMPTY))!=null,"Style inheritance lost the gradient");
                try{
                    Method sources=SmolPlayers.class.getDeclaredMethod("collectSmolMirrorSources",net.minecraft.client.Minecraft.class);sources.setAccessible(true);
                    ConfigManager.smolpeopleMode=true;Object once=sources.invoke(null,client);for(int i=0;i<1000;i++)check(once==sources.invoke(null,client),"Mirror sources were rebuilt within one tick");
                    ConfigManager.smolpeopleMode=false;check(once!=sources.invoke(null,client),"Local small-model changes did not invalidate sources");
                }catch(ReflectiveOperationException error){throw new AssertionError(error);}
                FeatureModules.all(false);NickRenderUtils.invalidateLocalTargetsCache();
                FormattedCharSequence poison=sink->{throw new AssertionError("Disabled nickname module traversed glyphs");};
                check(NickRenderUtils.handleCharSequence(poison)==poison&&NickRenderUtils.measureGradient(poison,client.font)==poison,"Disabled text path was not inert");
                check(!SmolPlayers.shouldApplySmolTo(client.player.getId()),"Disabled small model is still active");
                for(int i=0;i<1000;i++){MapEditor.tick(client);com.customcosmetics.features.damage.DamageSplash.tick(client);}
                check(!com.customcosmetics.config.FeatureModules.remoteConsumers(),"Disabled modules kept online consumers alive");
                ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.MODULES));
            });
            context.waitTicks(3);context.takeScreenshot("module-manager-disabled");
            click(context,"Enable All Modules");
            context.runOnClient(client->{check(ConfigManager.disabledModules==0,"Enable All did not restore modules");});
            click(context,"Disable All Modules");
            context.runOnClient(client->{check(ConfigManager.disabledModules==FeatureModules.ALL,"Disable All did not disable modules");FeatureModules.all(true);ClientUiAccess.setScreen(client,null);});
            System.out.println("CLIENT_PERF_MODULE_GATES_SIDEBAR_REVISION_SMOL_TICK_CACHE_AND_STYLE_INHERITANCE_OK");
        }
    }
    private static void click(ClientGameTestContext context,String label){
        var point=new AtomicReference<double[]>();context.runOnClient(client->{var button=ClientUiAccess.screen(client).children().stream().filter(child->child instanceof PinkUi.Action action&&action.getMessage().getString().equals(label)).map(PinkUi.Action.class::cast).findFirst().orElseThrow();double scale=client.getWindow().getGuiScale();point.set(new double[]{(button.getX()+button.getWidth()/2d)*scale,(button.getY()+button.getHeight()/2d)*scale});});
        context.getInput().setCursorPos(point.get()[0],point.get()[1]);context.getInput().pressMouse(ClientInput.MOUSE_LEFT);context.waitTicks(2);
    }
    private static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
