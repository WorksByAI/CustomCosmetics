// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.map.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import static com.customcosmetics.gui.PinkUi.*;

/** Batch material replacement reuses the searchable palette, without touching the player's inventory. */
public final class MapReplaceScreen extends Screen {
    private final Screen parent;
    private String source="minecraft:quartz_block",target=MapEditor.material,error="";
    private MaterialReplacement.Scope scope=MaterialReplacement.Scope.ROOM;
    private PinkUi.Field from,to;private PinkUi.Action apply,cancel,scopeButton,chooseFrom,chooseTo,undo;
    private int x,y,w,h;
    public MapReplaceScreen(Screen parent){
        super(Component.literal("Map Editor · Replace Materials"));this.parent=parent;
        var client=Minecraft.getInstance();if(client.level!=null){var hit=MapEditor.target();if(hit!=null){var real=MapEditor.realState(hit.getBlockPos());if(!real.isAir())source=BuiltInRegistries.BLOCK.getKey(real.getBlock()).toString();}}
        if(MapEditor.currentRoom()==null&&MapEditor.selection()!=null)scope=MaterialReplacement.Scope.SELECTION;
    }
    @Override protected void init(){
        w=Math.min(560,width-16);h=Math.min(294,height-16);x=(width-w)/2;y=(height-h)/2;int col=(w-30)/2;
        from=addRenderableWidget(new PinkUi.Field(font,x+12,y+43,col,"Original Block",20));from.setMaxLength(128);from.setValue(source);from.setResponder(v->source=v);
        to=addRenderableWidget(new PinkUi.Field(font,x+18+col,y+43,col,"Replacement Material",20));to.setMaxLength(512);to.setValue(target);to.setResponder(v->target=v);
        chooseFrom=addRenderableWidget(new PinkUi.Action(x+12,y+68,col,20,"Choose Original Block",false,()->ClientUiAccess.setScreen(minecraft,new MapBlockScreen(this,"Choose Original Block",()->source,id->source=id))));
        chooseTo=addRenderableWidget(new PinkUi.Action(x+18+col,y+68,col,20,"Choose Replacement",false,()->ClientUiAccess.setScreen(minecraft,new MapBlockScreen(this,"Choose Replacement Material",()->target,id->target=id))));
        scopeButton=addRenderableWidget(new PinkUi.Action(x+12,y+96,w-24,22,scopeLabel(),false,()->{scope=scope==MaterialReplacement.Scope.ROOM?MaterialReplacement.Scope.SELECTION:MaterialReplacement.Scope.ROOM;scopeButton.setMessage(Component.literal(scopeLabel()));error="";}));
        apply=addRenderableWidget(new PinkUi.Action(x+12,y+h-58,col,22,"Replace",true,this::apply));
        undo=addRenderableWidget(new PinkUi.Action(x+18+col,y+h-58,col,22,"Undo Last Edit",false,()->{MapEditor.undo();error="Last edit undone.";}));
        cancel=addRenderableWidget(new PinkUi.Action(x+12,y+h-30,w-24,22,"Back",false,()->{if(MaterialReplacement.busy()){MaterialReplacement.cancel();error="";}else onClose();}));tick();
    }
    private String scopeLabel(){return "Scope: "+(scope==MaterialReplacement.Scope.ROOM?"Current Room":"Selected Region");}
    private void apply(){try{error="";MaterialReplacement.start(source.strip(),target.strip(),scope);}catch(RuntimeException problem){error=problem.getMessage();}tick();}
    @Override public void tick(){boolean busy=MaterialReplacement.busy();from.active=to.active=chooseFrom.active=chooseTo.active=scopeButton.active=undo.active=!busy;apply.active=!busy;cancel.setMessage(Component.literal(busy?"Cancel Replacement":"Back"));}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        outline(g,x,y,w,h,10,BORDER,PANEL);text(g,font,getTitle().getString(),x+12,y+13,PINK,w-24);
        text(g,font,"Original block type",x+12,y+30,MUTED,(w-30)/2);text(g,font,"Replacement material",x+18+(w-30)/2,y+30,MUTED,(w-30)/2);
        int yy=y+126;String context=scope==MaterialReplacement.Scope.ROOM?"Room: "+MapEditor.roomLabel():"Selection: /customcosmetics map pos1 and pos2";
        for(String line:new String[]{context,error.isEmpty()?MaterialReplacement.status():error,"Visual only · Loaded blocks only · Keeps original shape and collision"}){for(var row:font.split(Component.literal(line),w-24)){if(yy>=y+h-65)break;g.text(font,row,x+12,yy,MUTED,false);yy+=11;}yy+=4;}
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){MaterialReplacement.cancel();ClientUiAccess.setScreen(minecraft,parent);}
}
