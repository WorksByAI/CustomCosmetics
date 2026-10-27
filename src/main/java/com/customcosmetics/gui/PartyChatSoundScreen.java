// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.party.PartyChatSound;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.*;
import static com.customcosmetics.gui.PinkUi.*;

/** Build the sound catalog once; only the current page has widgets. */
public final class PartyChatSoundScreen extends ThemedScreen {
    private final Screen parent;
    private List<String> sounds,filtered=List.of();
    private final List<PinkUi.Action> rows=new ArrayList<>();
    private String query="";private int offset,x,y,w,h;
    private PinkUi.Action previous,next;
    public PartyChatSoundScreen(Screen parent){super(Component.literal("Party Chat · Choose Sound"));this.parent=parent;}
    @Override protected void init(){
        w=Math.min(520,width-16);h=Math.min(330,height-16);x=(width-w)/2;y=(height-h)/2;rows.clear();
        if(sounds==null){
            var available=new TreeSet<String>();minecraft.getSoundManager().getAvailableSounds().forEach(id->available.add(id.toString()));
            var ordered=new LinkedHashSet<String>();PartyChatSound.PRESETS.keySet().stream().filter(available::contains).forEach(ordered::add);
            ordered.addAll(available);sounds=List.copyOf(ordered);
        }
        var search=addRenderableWidget(new PinkUi.Field(font,x+12,y+34,w-24,"Search Sounds",22));search.setMaxLength(128);search.setHint(Component.literal("Search sounds…"));search.setValue(query);search.setResponder(value->{query=value;offset=0;filter();});
        for(int i=0;i<Math.max(1,(h-132)/26);i++){
            int index=i;
            rows.add(addRenderableWidget(new PinkUi.Action(x+12,y+65+i*26,w-24,24,"",false,()->choose(offset+index))
                .selected(()->offset+index<filtered.size()&&filtered.get(offset+index).equals(ConfigManager.partyChatSound))));
        }
        int half=(w-30)/2;
        previous=addRenderableWidget(new PinkUi.Action(x+12,y+h-59,half,22,"Previous",false,()->{offset=Math.max(0,offset-rows.size());updateRows();}));
        next=addRenderableWidget(new PinkUi.Action(x+18+half,y+h-59,w-30-half,22,"Next",false,()->{offset+=rows.size();updateRows();}));
        addRenderableWidget(new PinkUi.Action(x+12,y+h-31,w-24,22,"Back",false,this::onClose));filter();
    }
    private void filter(){String needle=query.strip().toLowerCase(Locale.ROOT);filtered=sounds.stream().filter(id->(PartyChatSound.label(id)+" "+id).toLowerCase(Locale.ROOT).contains(needle)).toList();offset=0;updateRows();}
    private void updateRows(){
        offset=Math.clamp(offset,0,Math.max(0,filtered.size()-1));
        for(int i=0;i<rows.size();i++){int at=offset+i;var row=rows.get(i);row.visible=at<filtered.size();if(row.visible){String id=filtered.get(at);row.setMessage(Component.literal(PartyChatSound.label(id)));row.setTooltip(Tooltip.create(Component.literal(id)));}}
        previous.active=offset>0;next.active=offset+rows.size()<filtered.size();
    }
    private void choose(int index){if(index>=filtered.size())return;ConfigManager.partyChatSound=filtered.get(index);ConfigManager.requestSave();PartyChatSound.preview(minecraft);onClose();}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        g.fill(0,0,width,height,0x70000000);outline(g,x,y,w,h,12,BORDER,PANEL);text(g,font,getTitle().getString(),x+12,y+13,PINK,w-24);
        if(filtered.isEmpty())text(g,font,"No matching sounds",x+16,y+70,MUTED,w-32);
        super.extractRenderState(g,mx,my,dt);
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){ClientUiAccess.setScreen(minecraft,parent);}
}
