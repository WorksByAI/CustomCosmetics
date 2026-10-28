// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.compatibility.*;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.update.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.concurrent.*;
import static com.customcosmetics.gui.PinkUi.*;
/** Startup-only offer. Downloads and helper processes require player opt-in. */
public final class UpdateScreen extends ThemedScreen {
    private final Screen parent;private final String installed,latest;private final boolean automatic;
    private boolean began,busy,arming,manual;private volatile boolean abandoned;private String status="";private int x,y,w,h;
    public UpdateScreen(Screen parent,String installed,String latest,boolean automatic){super(Component.literal("Update Available"));this.parent=parent;this.installed=installed;this.latest=latest;this.automatic=automatic;}
    @Override protected void init(){
        w=Math.min(470,width-24);h=Math.min(228,height-24);x=(width-w)/2;y=(height-h)/2;
        var now=addRenderableWidget(new PinkUi.Action(x+12,y+h-92,w-24,24,manual?"Quit Minecraft":"Update Now",true,()->{if(manual)minecraft.stop();else begin();}));now.active=!busy;
        var next=addRenderableWidget(new PinkUi.Action(x+12,y+h-62,w-24,24,"Update after next launch",false,()->{ConfigManager.updateOnNextLaunch=true;ConfigManager.saveConfig();ClientUiAccess.setScreen(minecraft,parent);}));next.active=!busy&&!manual;
        var later=addRenderableWidget(new PinkUi.Action(x+12,y+h-32,w-24,24,"Update Later",false,this::onClose));later.active=!arming;
        if(automatic&&!began)begin();
    }
    private void begin(){
        if(busy)return;began=true;busy=true;status="Downloading verified update...";rebuildWidgets();
        AutomaticUpdates.download(installed).whenComplete((prepared,error)->minecraft.execute(()->{
            if(abandoned||ClientUiAccess.screen(minecraft)!=this)return;
            if(error!=null){busy=false;status="Update failed. Your installed mod is unchanged.";rebuildWidgets();return;}
            arming=true;status="Preparing automatic restart...";rebuildWidgets();
            CompletableFuture.supplyAsync(()->{try{return AutomaticUpdates.arm(prepared);}catch(Exception failure){throw new CompletionException(failure);}}).whenComplete((restart,failure)->minecraft.execute(()->{
                arming=busy=false;
                if(failure!=null){status="Update could not be installed. Your mod is unchanged.";rebuildWidgets();return;}
                ConfigManager.updateOnNextLaunch=false;ConfigManager.saveConfig();
                if(restart)minecraft.stop();else{manual=true;status="Update ready. Quit and restart using your launcher.";rebuildWidgets();}
            }));
        }));
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float dt){
        if(parent instanceof net.minecraft.client.gui.screens.TitleScreen){parent.extractBackground(g,-1,-1,dt);parent.extractRenderState(g,-1,-1,dt);g.fill(0,0,width,height,0x88000000);}
        else super.extractBackground(g,mx,my,dt);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        outline(g,x,y,w,h,8,BORDER,PANEL);uiText(g,font,"Update Available",x+12,y+14,PINK,w-24);
        text(g,previewFont(),"v"+installed+" → v"+latest,x+12,y+39,TEXT,w-24);
        uiText(g,font,status.isEmpty()?"Minecraft will restart after the update is installed.":status,x+12,y+66,MUTED,w-24);super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose(){if(arming)return;abandoned=true;if(manual)AutomaticUpdates.cancelArmed();ConfigManager.updateOnNextLaunch=false;ConfigManager.saveConfig();ClientUiAccess.setScreen(minecraft,parent);}
    @Override public boolean isPauseScreen(){return false;}
}
