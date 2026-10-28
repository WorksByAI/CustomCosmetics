// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.features.cape.*;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import java.awt.image.BufferedImage;
import static com.customcosmetics.gui.PinkUi.*;

/** Fixed cape aspect ratio; drag to move, scroll to zoom. Saving only changes the local PNG. */
public final class CapeCropScreen extends ThemedScreen {
    private final Screen parent;private final BufferedImage source;private CapeImages.Crop crop;
    private Identifier texture;private int x,y,w,h;private double scale,bx,by;private boolean dragging;private String notice="Drag to move · Scroll to resize";
    public CapeCropScreen(Screen parent,BufferedImage source){super(Component.literal("Crop Cape"));this.parent=parent;this.source=source;crop=CapeImages.Crop.centered(source.getWidth(),source.getHeight());}
    @Override protected void init(){
        w=Math.min(650,width-16);h=Math.min(440,height-16);x=(width-w)/2;y=(height-h)/2;
        scale=Math.min((w-32.0)/source.getWidth(),Math.max(20,h-112.0)/source.getHeight());bx=x+(w-source.getWidth()*scale)/2;by=y+45+(h-112-source.getHeight()*scale)/2;
        if(texture==null)try{
            texture=Identifier.fromNamespaceAndPath("customcosmetics","cape_crop");var pixels=new NativeImage(source.getWidth(),source.getHeight(),false);
            for(int iy=0;iy<source.getHeight();iy++)for(int ix=0;ix<source.getWidth();ix++)pixels.setPixel(ix,iy,source.getRGB(ix,iy));
            minecraft.getTextureManager().register(texture,new DynamicTexture(()->"CustomCosmetics crop",pixels));
        }catch(Exception failure){notice="Could not preview image.";}
        int cell=(w-48)/3;
        addRenderableWidget(new PinkUi.Action(x+14,y+h-32,cell,22,"Reset Crop",false,()->crop=CapeImages.Crop.centered(source.getWidth(),source.getHeight())));
        addRenderableWidget(new PinkUi.Action(x+24+cell,y+h-32,cell,22,"Save Local Cape",true,()->{try{CapeCosmetics.saveLocal(CapeImages.crop(source,crop));onClose();}catch(Exception failure){notice=failure.getMessage();}}));
        addRenderableWidget(new PinkUi.Action(x+34+cell*2,y+h-32,cell,22,"Cancel",false,this::onClose));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        outline(g,x,y,w,h,10,BORDER,PANEL);uiText(g,font,"Crop Cape",x+14,y+15,PINK,w-28);
        if(texture!=null)g.blit(texture,(int)bx,(int)by,(int)(bx+source.getWidth()*scale),(int)(by+source.getHeight()*scale),0,1,0,1);
        int left=(int)(bx+crop.x()*scale),top=(int)(by+crop.y()*scale),right=(int)(bx+(crop.x()+crop.width())*scale),bottom=(int)(by+(crop.y()+crop.height())*scale);
        g.fill((int)bx,(int)by,(int)(bx+source.getWidth()*scale),top,0x90000000);g.fill((int)bx,bottom,(int)(bx+source.getWidth()*scale),(int)(by+source.getHeight()*scale),0x90000000);
        g.fill((int)bx,top,left,bottom,0x90000000);g.fill(right,top,(int)(bx+source.getWidth()*scale),bottom,0x90000000);
        g.fill(left,top,right,top+1,PINK);g.fill(left,bottom-1,right,bottom,PINK);g.fill(left,top,left+1,bottom,PINK);g.fill(right-1,top,right,bottom,PINK);
        int quality=CapeImages.cropScale(crop);
        uiText(g,font,"Cape face: up to "+(10*quality)+" x "+(16*quality)+" · Max 2 MiB",x+14,y+h-72,DIM,w-28);
        uiText(g,font,notice,x+14,y+h-55,MUTED,w-28);super.extractRenderState(g,mx,my,dt);
    }
    private boolean inside(double mx,double my){return mx>=bx&&my>=by&&mx<bx+source.getWidth()*scale&&my<by+source.getHeight()*scale;}
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean twice){if(event.button()==0&&inside(event.x(),event.y())){dragging=true;return true;}return super.mouseClicked(event,twice);}
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy){if(dragging){crop=crop.move(dx/scale,dy/scale,source.getWidth(),source.getHeight());return true;}return super.mouseDragged(event,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent event){dragging=false;return super.mouseReleased(event);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(inside(mx,my)){crop=crop.zoom(Math.pow(1.1,-vertical),source.getWidth(),source.getHeight());return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    @Override public void onClose(){CosmeticsScreen.childInteraction();ClientUiAccess.setScreen(minecraft,parent);}
    @Override public void removed(){if(texture!=null){minecraft.getTextureManager().release(texture);texture=null;}}
    @Override public boolean isPauseScreen(){return false;}
}
