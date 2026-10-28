// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.accessoryhelper;

import com.customcosmetics.gui.*;
import com.customcosmetics.features.items.HeadSkins;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import java.util.*;
import static com.customcosmetics.gui.PinkUi.*;

/** Screen-owned local overlay. No slot mutations, container clicks or server packets. */
public final class MissingAccessoriesPanel {
    private MissingAccessories.Filter filter=MissingAccessories.Filter.MISSING;
    private boolean allTiers,collapsed,active;
    private AccessoryExclusions.Snapshot exclusions;
    private AccessoryCatalog catalog;
    private AccessoryBagIndex.View view;
    private Map<String,AccessoryItemCatalog.Item> metadata=Map.of();
    private List<AccessoryCatalog.Entry> entries=List.of();
    private final Map<String,ItemStack> icons=new HashMap<>();
    private boolean dirty=true;
    private int x,y,w,h,columns,page,pages=1,footer;
    private String pageText="1/1",title="",filterText="";
    private List<net.minecraft.util.FormattedCharSequence> message=List.of();
    private int language=-1;
    private int lastWidth,lastColumns;

    public void render(Screen screen,GuiGraphicsExtractor g,int bagX,int bagY,int bagWidth,int mx,int my){
        var next=AccessoryTooltipHelper.bagCatalog(screen);active=next!=null;if(!active)return;
        int left=bagX-8,right=screen.width-bagX-bagWidth-8,space=Math.max(left,right);
        if(space<34){active=false;return;}
        w=Math.min(132,space);columns=Math.clamp((w-12)/22,1,5);h=18+4*22+66;
        if(w!=lastWidth||columns!=lastColumns){dirty=true;lastWidth=w;lastColumns=columns;}
        x=left>=w?bagX-6-w:bagX+bagWidth+6;y=Math.clamp(bagY+12,4,Math.max(4,screen.height-h-4));
        AccessoryExclusions.request();var nextExclusions=AccessoryExclusions.snapshot();
        if(nextExclusions!=exclusions){exclusions=nextExclusions;dirty=true;}
        var nextView=AccessoryTooltipHelper.bagView();var nextMetadata=AccessoryItems.items();
        if(next!=catalog){catalog=next;AccessoryItems.request(next);dirty=true;}
        if(nextView!=view||nextMetadata!=metadata||language!=UiText.revision()){view=nextView;metadata=nextMetadata;language=UiText.revision();dirty=true;}
        UiTheme.refresh();var font=UiFonts.get(Minecraft.getInstance().font);
        if(dirty){
            var sorted=new ArrayList<>(exclusions.ready()?MissingAccessories.list(catalog,view,filter,allTiers,exclusions.ignored()):List.<AccessoryCatalog.Entry>of());
            sorted.sort(Comparator.comparing(e->name(e.id()),String.CASE_INSENSITIVE_ORDER));entries=List.copyOf(sorted);
            pages=Math.max(1,(entries.size()+columns*4-1)/(columns*4));page=Math.min(page,pages-1);pageText=(page+1)+"/"+pages;
            title=UiText.tr("Missing")+(exclusions.ready()?" ("+entries.size()+")":"");
            filterText=UiText.tr("Filter")+": "+UiText.tr(switch(filter){case MISSING->"Missing";case UPGRADES->"Upgrades";case ALL->"All";});
            String status=catalog.size()==0||!exclusions.ready()?"Loading accessory metadata…":!view.complete()?"Scan every Accessory Bag page":entries.isEmpty()?"No missing accessories":"";
            message=status.isEmpty()?List.of():font.split(UiText.guiComponent(status),Math.max(16,w-12));icons.clear();dirty=false;
        }
        g.nextStratum();
        if(collapsed){outline(g,x+w-22,y,22,20,4,BORDER,PANEL);text(g,font,">",x+w-15,y+6,TEXT,14);return;}
        outline(g,x,y,w,h,5,BORDER,PANEL);text(g,font,title,x+6,y+5,TEXT,w-28);text(g,font,"<",x+w-16,y+5,MUTED,12);
        for(int i=0;i<message.size()&&i<5;i++)g.text(font,message.get(i),x+6,y+28+i*11,MUTED,false);
        int first=page*columns*4;
        for(int i=0;message.isEmpty()&&i<columns*4&&first+i<entries.size();i++){
            var entry=entries.get(first+i);int cx=x+6+i%columns*22,cy=y+20+i/columns*22;
            boolean hot=mx>=cx&&mx<cx+20&&my>=cy&&my<cy+20;
            outline(g,cx,cy,20,20,3,hot?PINK:BORDER,hot?HOVER:CARD);
            var icon=icons.computeIfAbsent(entry.id(),this::icon);g.item(icon,cx+2,cy+2);
            if(hot)g.setTooltipForNextFrame(Minecraft.getInstance().font,icon,mx,my);
        }
        footer=y+18+4*22;
        text(g,font,"<",x+8,footer+5,page>0?TEXT:DIM,10);text(g,font,pageText,x+(w-font.width(pageText))/2,footer+5,MUTED,w-38);text(g,font,">",x+w-16,footer+5,page+1<pages?TEXT:DIM,10);
        outline(g,x+4,footer+21,w-8,20,3,BORDER,CARD);text(g,font,filterText,x+8,footer+27,TEXT,w-16);
        outline(g,x+4,footer+43,w-8,19,3,BORDER,CARD);uiText(g,font,allTiers?"All Tiers: On":"All Tiers: Off",x+8,footer+48,TEXT,w-16);
    }
    public boolean click(MouseButtonEvent event){
        if(!active||event.button()!=com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT||event.x()<x+(collapsed?w-22:0)||event.x()>=x+w||event.y()<y||event.y()>=y+(collapsed?20:h))return false;
        if(event.y()<y+18&&event.x()>=x+w-24){collapsed=!collapsed;return true;}
        if(collapsed)return true;
        if(event.y()>=footer&&event.y()<footer+20){
            if(event.x()<x+25)page=Math.max(0,page-1);else if(event.x()>x+w-25)page=Math.min(pages-1,page+1);
            pageText=(page+1)+"/"+pages;
            icons.clear();
        }else if(event.y()>=footer+21&&event.y()<footer+41){filter=MissingAccessories.Filter.values()[(filter.ordinal()+1)%3];page=0;dirty=true;}
        else if(event.y()>=footer+43){allTiers=!allTiers;page=0;dirty=true;}
        return true;
    }
    private String name(String id){var item=metadata.get(id);return item==null?id:item.name();}
    private ItemStack icon(String id){
        var data=metadata.get(id);ItemStack stack;
        if(data!=null&&!data.texture().isEmpty()){stack=new ItemStack(Items.PLAYER_HEAD);stack.set(DataComponents.PROFILE,HeadSkins.profile(data.texture()));}
        else{
            String material=data==null?"paper":data.material().toLowerCase(Locale.ROOT);
            material=switch(material){case "skull_item"->"player_head";case "iron_barding"->"iron_horse_armor";case "gold_barding"->"golden_horse_armor";case "watch"->"clock";case "redstone_comparator"->"comparator";case "diode"->"repeater";case "fireball"->"fire_charge";case "leash"->"lead";case "exp_bottle"->"experience_bottle";case "sulphur"->"gunpowder";case "carrot_stick"->"carrot_on_a_stick";case "ink_sack"->DYES[Math.clamp(data.damage(),0,15)]+"_dye";default->material;};
            var key=Identifier.tryParse("minecraft:"+material);var item=key==null?Items.PAPER:BuiltInRegistries.ITEM.getOptional(key).orElse(Items.PAPER);stack=new ItemStack(item==Items.AIR?Items.PAPER:item);
        }
        int color=data==null?0xFFFFFF:switch(data.rarity()){case "UNCOMMON"->0x55FF55;case "RARE"->0x5555FF;case "EPIC"->0xAA00AA;case "LEGENDARY"->0xFFAA00;case "MYTHIC"->0xFF55FF;case "SPECIAL","VERY_SPECIAL"->0xFF5555;default->0xFFFFFF;};
        stack.set(DataComponents.CUSTOM_NAME,Component.literal(name(id)).withColor(color));
        var tag=new net.minecraft.nbt.CompoundTag();tag.putString("id",id);stack.set(DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));return stack;
    }
    private static final String[] DYES={"black","red","green","brown","blue","purple","cyan","light_gray","gray","pink","lime","yellow","light_blue","magenta","orange","white"};
}
