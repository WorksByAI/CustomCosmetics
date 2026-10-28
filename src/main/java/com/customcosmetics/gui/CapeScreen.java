// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.features.cape.*;
import com.customcosmetics.sync.*;
import com.customcosmetics.config.*;
import com.customcosmetics.compatibility.ClientUiAccess;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import static com.customcosmetics.gui.PinkUi.*;

public final class CapeScreen extends ThemedScreen {
    private final Screen parent;private int x,y,w,h;private String notice="";private PinkUi.Action submit,cropButton,atlasButton,removeButton;private boolean choosing;private long selection;
    public CapeScreen(Screen parent){super(Component.literal("Cape"));this.parent=parent;}
    @Override protected void init(){
        w=Math.min(620,width-16);h=Math.min(410,height-16);x=(width-w)/2;y=(height-h)/2;
        addRenderableWidget(new PinkUi.Action(x+w-164,y+10,150,22,"Blocked Capes",false,()->ClientUiAccess.setScreen(minecraft,new CosmeticsScreen(this,CosmeticsScreen.Page.BLOCKED_CAPES))));
        int buttonY=y+h-119,cell=(w-38)/2;
        cropButton=addRenderableWidget(new PinkUi.Action(x+14,buttonY,cell,24,"Choose Image & Crop",false,()->choose(false)));
        cropButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(UiText.component("Choose a regular image, crop it into a cape, then submit it for review.")));
        atlasButton=addRenderableWidget(new PinkUi.Action(x+24+cell,buttonY,cell,24,"Choose Vanilla-Format Cape Image",false,()->choose(true)));
        atlasButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(UiText.component("Choose a PNG in Minecraft's cape texture format; no cropping needed.")));
        removeButton=addRenderableWidget(new PinkUi.Action(x+14,buttonY+31,cell,24,"Remove Local Cape",false,()->{try{CapeCosmetics.clearLocal();notice="Local cape removed.";}catch(Exception error){notice=error.getMessage();}}));
        submit=addRenderableWidget(new PinkUi.Action(x+24+cell,buttonY+31,cell,24,"Submit for Review",true,()->{notice="";CapeSync.submit();}));
        boolean administrator=RemoteAccess.online()&&CapeAdministrators.includes(minecraft.getUser().getProfileId());int part=administrator?(w-48)/3:cell;
        var enabled=addRenderableWidget(new PinkUi.Action(x+14,y+h-29,part,22,"Show Custom Capes",false,()->{ConfigManager.capeEnabled=!ConfigManager.capeEnabled;ConfigManager.requestSave();}));enabled.selected(()->ConfigManager.capeEnabled);
        int next=x+24+part;
        if(administrator){addRenderableWidget(new PinkUi.Action(next,y+h-29,part,22,"Review Queue",false,()->com.customcosmetics.compatibility.RepositoryLinks.openUri(java.net.URI.create(SyncEndpoint.baseUrl()+"/admin"))));next+=part+10;}
        addRenderableWidget(new PinkUi.Action(next,y+h-29,part,22,"Done",false,this::onClose));
    }
    private void choose(boolean atlas){
        if(choosing)return;CosmeticsScreen.childInteraction();choosing=true;notice="Choose a file in the system window...";long request=++selection;
        boolean sdl=net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft").orElseThrow().getMetadata().getVersion().getFriendlyString().startsWith("26.3");long window=0;
        if(sdl)try{window=(long)minecraft.getWindow().getClass().getMethod("handle").invoke(minecraft.getWindow());}catch(ReflectiveOperationException unavailable){choosing=false;ClientUiAccess.setScreen(minecraft,new CapeFileScreen(this,atlas));return;}
        boolean started=com.customcosmetics.compatibility.NativeFileDialogs.open(new com.customcosmetics.compatibility.NativeFileDialogs.Request(atlas,CapeFileScreen.defaultDirectory(),sdl,window),minecraft::execute,result->{
            if(request!=selection||ClientUiAccess.screen(minecraft)!=this)return;
            if(result.unavailable()){choosing=false;ClientUiAccess.setScreen(minecraft,new CapeFileScreen(this,atlas));return;}
            if(result.path()==null){choosing=false;notice=result.error();return;}
            notice="Loading local image...";
            java.util.concurrent.CompletableFuture.supplyAsync(()->{try{return CapeImageImport.read(result.path(),atlas);}catch(Exception failure){throw new java.util.concurrent.CompletionException(failure);}})
                .whenComplete((image,error)->minecraft.execute(()->{if(request!=selection||ClientUiAccess.screen(minecraft)!=this)return;choosing=false;
                    if(error!=null){var cause=error instanceof java.util.concurrent.CompletionException?error.getCause():error;notice=cause.getMessage()==null?"Could not open image.":cause.getMessage();return;}
                    try{if(atlas){CapeCosmetics.saveLocal(image.atlas());notice="Local cape loaded.";}else ClientUiAccess.setScreen(minecraft,new CapeCropScreen(this,image.source()));}catch(Exception failure){notice=failure.getMessage();}
                }));
        });if(!started){choosing=false;notice="A system file picker is already open.";}
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        outline(g,x,y,w,h,10,BORDER,PANEL);uiText(g,font,"Cape",x+14,y+15,PINK,w-28);
        int previewHeight=Math.max(30,h-172);outline(g,x+14,y+37,w-28,previewHeight,6,BORDER,CARD);
        var texture=CapeCosmetics.preview();
        uiText(g,font,"Preview · "+(CapeCosmetics.hasLocal()?"Local Cape":texture!=null?"Approved Cape":"Current Cape"),x+24,y+47,PINK,w-48);
        if(texture!=null){int side=Math.min(90,previewHeight-33),ph=Math.max(1,side);g.blit(texture,x+30,y+69,x+30+Math.max(1,ph*10/16),y+69+ph,1f/64,11f/64,1f/32,17f/32);}
        if(minecraft.player!=null)CapePreview.draw(g,x+w/4,y+58,w*3/4-20,Math.max(1,previewHeight-25));
        else uiText(g,font,"Enter a world for the player preview.",x+24,y+75,MUTED,w-48);
        uiText(g,font,RemoteAccess.online()?"Online: approved capes are shared. Local edits stay private.":"Offline: local cape only. Nothing is uploaded.",x+14,y+h-130,MUTED,w-28);
        submit.active=!choosing&&CapeSync.canSubmit();removeButton.active=!choosing;
        cropButton.active=atlasButton.active=!choosing&&!com.customcosmetics.compatibility.NativeFileDialogs.busy();
        String state=UiText.tr(CapeSync.status());long cooldown=CapeSync.cooldownSeconds();if(cooldown>0)state+=UiText.tr(" · Next submission in ")+(cooldown/3600)+"h "+(cooldown%3600/60)+"m";
        text(g,font,state,x+14,y+h-52,MUTED,w-28);
        if(!notice.isBlank())uiText(g,font,notice,x+14,y+h-66,PINK,w-28);
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose(){CosmeticsScreen.childInteraction();ClientUiAccess.setScreen(minecraft,parent);}
    @Override public void removed(){selection++;choosing=false;super.removed();}
    @Override public boolean isPauseScreen(){return false;}
}
