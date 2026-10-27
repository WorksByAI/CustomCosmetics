// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.accessories.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.function.*;
import static com.customcosmetics.gui.PinkUi.*;
public final class AccessoryAdjustmentScreen extends ThemedScreen {
    private final Screen parent;private final String slot;private int x,y,w,h,preview;
    public AccessoryAdjustmentScreen(Screen parent,String slot){super(Component.literal("Accessory Position"));this.parent=parent;this.slot=slot;}
    @Override protected void init(){
        w=Math.min(620,width-16);h=Math.min(380,height-16);x=(width-w)/2;y=(height-h)/2;preview=Math.clamp(h-172,32,160);
        var offset=ConfigManager.accessories.part(slot).offset();int cell=(w-36)/2,top=y+48+preview;
        axis(x+12,top,cell,"X · Right",offset.x(),v->update(o->o.x(v)));
        axis(x+24+cell,top,cell,"Y · Up",offset.y(),v->update(o->o.y(v)));
        axis(x+12,top+48,cell,"Z · Back",offset.z(),v->update(o->o.z(v)));
        addRenderableWidget(new PinkUi.Action(x+24+cell,top+52,cell,28,"Reset Position",false,()->{update(o->AccessoryOffset.ZERO);rebuildWidgets();}));
        addRenderableWidget(new PinkUi.Action(x+12,y+h-31,w-24,22,"Done",false,this::onClose));
    }
    private void axis(int x,int y,int width,String title,double value,DoubleConsumer setter){
        var number=new PinkUi.Field(font,x+width-57,y,57,title+" value",22);number.setValue(String.format(java.util.Locale.ROOT,"%.2f",value*16));number.setMaxLength(7);
        var slider=addRenderableWidget(new PinkUi.Slider(x,y,width-64,title+" (pixels)",value*16,-16,16,.25,v->{setter.accept(v/16);if(!number.isFocused())number.setValue(String.format(java.util.Locale.ROOT,"%.2f",v));}));
        number.setResponder(v->{try{double n=Double.parseDouble(v);boolean valid=Double.isFinite(n)&&n>=-16&&n<=16;number.invalid(!valid);if(valid)slider.setActual(n);}catch(NumberFormatException error){number.invalid(true);}});addRenderableWidget(number);
    }
    private void update(UnaryOperator<AccessoryOffset> change){var part=ConfigManager.accessories.part(slot);ConfigManager.accessories=ConfigManager.accessories.with(slot,part.offset(change.apply(part.offset())));ConfigManager.requestSave();CosmeticsScreen.childInteraction();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        outline(g,x,y,w,h,10,BORDER,PANEL);text(g,font,AccessoryCatalog.slot(slot).label()+" · Position",x+12,y+14,PINK,w-24);
        int half=(w-24)/2;CapePreview.drawAccessories(g,x+12,y+37,half,preview,true,WardrobeCamera.DEFAULT);CapePreview.drawAccessories(g,x+12+half,y+37,half,preview,false,WardrobeCamera.DEFAULT);
        text(g,font,"16 pixels = 1 block · Shared with your outfit.",x+12,y+h-48,MUTED,w-24);super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose(){ConfigManager.requestSave();ClientUiAccess.setScreen(minecraft,parent);}
    @Override public boolean isPauseScreen(){return false;}
}
