// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.blocking.BlockingAdjustment;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;
import static com.customcosmetics.gui.PinkUi.*;

/** Independent first-person blocking editor, with scrollable controls and a live native preview. */
public final class BlockingAdjustmentScreen extends ThemedScreen {
    private final Screen parent;
    private int x,y,w,h,top,bottom,scroll;
    private boolean animate;
    private final List<Placement> controls=new ArrayList<>();
    private record Placement(AbstractWidget widget,int y){}
    public BlockingAdjustmentScreen(Screen parent){super(Component.literal("Adjust Blocking"));this.parent=parent;}
    @Override protected void init(){
        controls.clear();w=Math.min(650,width-16);h=Math.min(460,height-16);x=(width-w)/2;y=(height-h)/2;
        int previewHeight=Math.clamp(h/2,60,190);top=y+48+previewHeight+10;bottom=y+h-44;
        int columns=w>=360?2:1,cell=(w-32-(columns-1)*8)/columns;
        DoubleSupplier[] values={()->ConfigManager.blockingX,()->ConfigManager.blockingY,()->ConfigManager.blockingZ,
            ()->ConfigManager.blockingRotationX,()->ConfigManager.blockingRotationY,()->ConfigManager.blockingRotationZ};
        DoubleConsumer[] setters={v->ConfigManager.blockingX=v,v->ConfigManager.blockingY=v,v->ConfigManager.blockingZ=v,
            v->ConfigManager.blockingRotationX=v,v->ConfigManager.blockingRotationY=v,v->ConfigManager.blockingRotationZ=v};
        String[] names={"Position X","Position Y","Position Z","Rotation X","Rotation Y","Rotation Z"};
        for(int i=0;i<6;i++){
            final int index=i;int bx=x+16+(i%columns)*(cell+8),by=top+(i/columns)*53;double limit=i<3?2:180;
            int inputWidth=48,sliderWidth=Math.max(32,cell-inputWidth-6);
            var number=new PinkUi.Field(font,bx+sliderWidth+6,by+17,inputWidth,names[i]+" value");number.setMaxLength(8);
            var slider=new PinkUi.Slider(bx,by+17,sliderWidth,names[i],values[i].getAsDouble(),-limit,limit,i<3?.01:1,v->{
                setters[index].accept(v);ConfigManager.requestSave();CosmeticsScreen.childInteraction();
                if(!number.isFocused())number.setValue(String.format(Locale.ROOT,"%.2f",v));
            });
            number.setValue(String.format(Locale.ROOT,"%.2f",values[i].getAsDouble()));number.setResponder(text->{
                if(!number.isFocused())return;
                try{double value=Double.parseDouble(text);boolean valid=Double.isFinite(value)&&Math.abs(value)<=limit;number.invalid(!valid);if(valid)slider.setActual(value);}
                catch(NumberFormatException invalid){number.invalid(true);}
            });
            controls.add(new Placement(addWidget(slider),slider.getY()));controls.add(new Placement(addWidget(number),number.getY()));
        }
        int part=(w-44)/3;
        addRenderableWidget(new PinkUi.Action(x+16,y+h-33,part,24,"Reset",false,()->{BlockingAdjustment.reset();ConfigManager.requestSave();rebuildWidgets();}));
        addRenderableWidget(new PinkUi.Action(x+22+part,y+h-33,part,24,animate?"Pause Preview":"Play BlockHit",false,()->{animate=!animate;rebuildWidgets();}));
        addRenderableWidget(new PinkUi.Action(x+28+part*2,y+h-33,part,24,"Done",false,this::onClose));
        scroll=Math.clamp(scroll,0,maxScroll());position();
    }
    private int maxScroll(){int rows=w>=360?3:6;return Math.max(0,rows*53-(bottom-top));}
    private void position(){for(var placement:controls){var control=placement.widget();control.setY(placement.y()-scroll);control.visible=control.getBottom()>top&&control.getY()<bottom;}}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){
        if(mx>=x&&mx<x+w&&my>=top&&my<bottom){scroll=Math.clamp(scroll-(int)(vertical*27),0,maxScroll());position();CosmeticsScreen.childInteraction();return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor graphics,int mx,int my,float dt){
        outline(graphics,x,y,w,h,10,BORDER,PANEL);uiText(graphics,font,"Adjust Blocking",x+16,y+16,PINK,w-32);
        int previewHeight=top-y-58;outline(graphics,x+16,y+38,w-32,previewHeight,6,BORDER,0x80303943);
        uiText(graphics,font,"Preview",x+24,y+46,PINK,w-48);
        BlockingPreview.draw(graphics,x+18,y+58,w-36,Math.max(1,previewHeight-23),animate?(System.currentTimeMillis()%800)/800f:0);
        position();graphics.enableScissor(x+10,top,x+w-10,bottom);
        for(var placement:controls)if(placement.widget().visible)placement.widget().extractRenderState(graphics,mx,my,dt);
        graphics.disableScissor();
        for(var child:children())if(child instanceof PinkUi.Action action)action.extractRenderState(graphics,mx,my,dt);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){ConfigManager.saveConfig();}
    @Override public void onClose(){ClientUiAccess.setScreen(minecraft,parent);}
}
