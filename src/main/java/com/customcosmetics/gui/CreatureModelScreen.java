// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.creature.CreatureModels;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.*;
import static com.customcosmetics.gui.PinkUi.*;
/** Only the current page has widgets; no entity previews or background work per choice. */
public final class CreatureModelScreen extends ThemedScreen {
    private final Screen parent;private String query;private int page,x,y,w,h,pages;private PinkUi.Field search;
    public CreatureModelScreen(Screen parent){this(parent,"");}
    public CreatureModelScreen(Screen parent,String query){super(Component.literal("Creature Model"));this.parent=parent;this.query=query;}
    @Override protected void init(){
        w=Math.min(640,width-24);h=Math.min(330,height-24);x=(width-w)/2;y=(height-h)/2;
        search=addRenderableWidget(new PinkUi.Field(font,x+12,y+36,w-24,"Search Creatures",24));search.setMaxLength(128);search.setHint(UiText.component("Search Creatures"));search.setValue(query);
        search.setResponder(value->{query=value;page=0;rebuildWidgets();setFocused(search);search.moveCursorToEnd(false);});
        String needle=query.strip().toLowerCase(Locale.ROOT);
        var choices=CreatureModels.availableChoices().stream().filter(c->needle.isEmpty()||c.label.toLowerCase(Locale.ROOT).contains(needle)||UiText.zh(c.label).contains(needle)||c.id.contains(needle)).toList();
        int columns=w<380?2:4,rows=Math.max(1,(h-110)/30),perPage=columns*rows,cell=(w-24-(columns-1)*6)/columns;
        pages=Math.max(1,(choices.size()+perPage-1)/perPage);page=Math.clamp(page,0,pages-1);
        for(int i=0;i<perPage&&page*perPage+i<choices.size();i++){
            var choice=choices.get(page*perPage+i);
            var button=new PinkUi.Action(x+12+(i%columns)*(cell+6),y+70+(i/columns)*30,cell,25,choice.label,false,()->{CreatureModels.select(choice);CosmeticsScreen.childInteraction();onClose();}).selected(()->ConfigManager.creatureModel.equals(choice.id));
            button.setTooltip(Tooltip.create(UiText.guiComponent(choice.label)));addRenderableWidget(button);
        }
        addRenderableWidget(new PinkUi.Action(x+12,y+h-30,32,22,"‹",false,()->{page--;rebuildWidgets();})).active=page>0;
        addRenderableWidget(new PinkUi.Action(x+108,y+h-30,32,22,"›",false,()->{page++;rebuildWidgets();})).active=page+1<pages;
        addRenderableWidget(new PinkUi.Action(x+w-90,y+h-30,78,22,"Done",false,this::onClose));
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        outline(g,x,y,w,h,10,BORDER,PANEL);uiText(g,font,"Creature Model",x+12,y+13,TEXT,w-24);text(g,font,(page+1)+" / "+pages,x+53,y+h-23,MUTED,50);super.extractRenderState(g,mx,my,dt);
    }
    @Override public void onClose(){ClientUiAccess.setScreen(minecraft,parent);}
    @Override public boolean isPauseScreen(){return false;}
}
