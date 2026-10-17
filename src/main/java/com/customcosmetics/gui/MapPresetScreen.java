// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.features.map.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static com.customcosmetics.gui.PinkUi.*;

/** Named layouts in one searchable list. Room recognition supplies the default placement frame. */
public final class MapPresetScreen extends Screen {
    private final Screen parent;
    private String selected="",name=MapEditor.presetName,query="",status="",confirmation="";
    private List<MapEditor.PresetInfo> entries=List.of(),filtered=List.of();
    private final List<PinkUi.Action> rows=new ArrayList<>();
    private PinkUi.Field search,nameField;
    private PinkUi.Action load,rename,delete,rotationButton;
    private int x,y,w,h,left,right,detailWidth,offset,rotation=-1;
    private boolean advanced;
    public MapPresetScreen(Screen parent){super(Component.literal("Map Editor · Preset Manager"));this.parent=parent;}
    @Override protected void init(){
        rows.clear();w=Math.min(620,width-16);h=Math.min(326,height-16);x=(width-w)/2;y=(height-h)/2;
        left=Math.max(108,(w-36)*2/5);right=x+left+24;detailWidth=x+w-12-right;
        search=addRenderableWidget(new PinkUi.Field(font,x+12,y+36,left,"Search presets",20));search.setMaxLength(48);search.setHint(Component.literal("Search presets…"));search.setValue(query);search.setResponder(v->{query=v;offset=0;filter();});
        nameField=addRenderableWidget(new PinkUi.Field(font,right,y+51,detailWidth,"Preset name",20));nameField.setMaxLength(48);nameField.setValue(name);nameField.setResponder(v->{name=v;confirmation="";status="";});
        int half=(detailWidth-6)/2;
        addRenderableWidget(new PinkUi.Action(x+12,y+h-64,left,22,"New Preset",false,()->{selected="";nameField.setValue("My room");confirmation="";status="Name it, then click Save.";updateButtons();}));
        load=addRenderableWidget(new PinkUi.Action(right,y+h-116,half,22,"Load",true,this::load));
        addRenderableWidget(new PinkUi.Action(right+half+6,y+h-116,half,22,"Save",false,this::save));
        rename=addRenderableWidget(new PinkUi.Action(right,y+h-90,half,22,"Rename",false,()->run(()->{MapEditor.presetName=selected;MapEditor.renamePreset(name.strip());selected=MapEditor.presetName;reload();status="Preset renamed.";})));
        delete=addRenderableWidget(new PinkUi.Action(right+half+6,y+h-90,half,22,"Delete",false,this::delete));
        int advancedWidth=advanced?half:detailWidth;
        addRenderableWidget(new PinkUi.Action(right,y+h-64,advancedWidth,22,advanced?"Hide Advanced":"Advanced…",false,()->{advanced=!advanced;if(!advanced)rotation=-1;rebuildWidgets();}));
        if(advanced)rotationButton=addRenderableWidget(new PinkUi.Action(right+half+6,y+h-64,half,22,rotationLabel(),false,()->{rotation=rotation==3?-1:rotation+1;rotationButton.setMessage(Component.literal(rotationLabel()));}));
        addRenderableWidget(new PinkUi.Action(x+12,y+h-25,w-24,18,"Back",false,this::onClose));
        for(int i=0;i<Math.max(1,(h-134)/25);i++){
            int index=i;
            rows.add(addRenderableWidget(new PinkUi.Action(x+12,y+62+i*25,left,23,"",false,()->select(offset+index)).selected(()->offset+index<filtered.size()&&filtered.get(offset+index).name().equals(selected))));
        }
        reload();
    }
    private String rotationLabel(){return rotation<0?"Automatic":"Extra: "+rotation*90+"°";}
    private void reload(){entries=MapEditor.presetEntries();filter();}
    private void filter(){String needle=query.toLowerCase(Locale.ROOT);filtered=entries.stream().filter(p->(p.name()+" "+p.room()).toLowerCase(Locale.ROOT).contains(needle)).toList();offset=Math.clamp(offset,0,Math.max(0,filtered.size()-rows.size()));updateButtons();}
    private void updateButtons(){
        for(int i=0;i<rows.size();i++){var row=rows.get(i);int at=offset+i;row.visible=at<filtered.size();if(row.visible){var p=filtered.get(at);row.setMessage(Component.literal(p.name()+" · "+String.format(Locale.ROOT,"%,d",p.blocks())));}}
        boolean has=entries.stream().anyMatch(p->p.name().equals(selected));load.active=rename.active=delete.active=has;
        delete.setMessage(Component.literal(confirmation.equals("delete:"+selected)?"Confirm Delete":"Delete"));
    }
    private void select(int index){if(index>=filtered.size())return;selected=filtered.get(index).name();nameField.setValue(selected);MapEditor.presetName=selected;status="";confirmation="";rotation=-1;if(rotationButton!=null)rotationButton.setMessage(Component.literal(rotationLabel()));updateButtons();}
    private void load(){run(()->{MapEditor.presetName=selected;if(rotation<0)MapEditor.loadPresetAuto();else MapEditor.loadPreset(rotation);status="Loaded "+selected+". Undo restores the previous edits.";confirmation="";});}
    private void save(){
        String target=name.strip();boolean exists=entries.stream().anyMatch(p->p.name().equals(target));
        if(exists&&!confirmation.equals("save:"+target)){confirmation="save:"+target;status="Preset exists. Click Save again to overwrite.";return;}
        run(()->{MapEditor.presetName=target;MapEditor.savePreset();selected=target;confirmation="";reload();status="Saved "+selected+".";});
    }
    private void delete(){
        if(!confirmation.equals("delete:"+selected)){confirmation="delete:"+selected;status="Click Confirm Delete to remove this preset.";updateButtons();return;}
        run(()->{MapEditor.presetName=selected;MapEditor.deletePreset();selected="";confirmation="";reload();status="Deleted. A copy is kept in map-editor/backups/presets.";});
    }
    private void run(Runnable action){try{action.run();}catch(RuntimeException error){status=error.getMessage()==null?"Preset operation failed.":error.getMessage();}updateButtons();}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(mx>=x+12&&mx<right&&my>=y+62&&my<y+h-68){offset=Math.clamp(offset-(int)Math.signum(vertical)*3,0,Math.max(0,filtered.size()-rows.size()));updateButtons();return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        outline(g,x,y,w,h,10,BORDER,PANEL);text(g,font,getTitle().getString(),x+12,y+13,PINK,w-24);
        text(g,font,"Preset name",right,y+37,MUTED,detailWidth);
        var info=entries.stream().filter(p->p.name().equals(selected)).findFirst().orElse(null);
        text(g,font,info==null?"Select a preset or save a new one.":info.room(),right,y+81,TEXT,detailWidth);
        text(g,font,info==null?"Saves the current room's edits.":String.format(Locale.ROOT,"%,d blocks",info.blocks()),right,y+94,MUTED,detailWidth);
        if(h>=290){text(g,font,"Here: "+MapEditor.roomLabel(),right,y+117,MUTED,detailWidth);text(g,font,"Position and facing align automatically.",right,y+131,MUTED,detailWidth);}
        if(filtered.isEmpty())text(g,font,entries.isEmpty()?"No saved presets yet":"No matching presets",x+16,y+70,MUTED,left-8);
        text(g,font,status.isEmpty()?"Scroll the list to browse · "+entries.size()+" presets":status,x+12,y+h-38,status.isEmpty()?MUTED:PINK,w-24);
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){ClientUiAccess.setScreen(minecraft,parent);}
}
