// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.*;
import com.customcosmetics.features.map.MapEditor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;
import static com.customcosmetics.gui.PinkUi.*;

/** Inventory-style local palette: left selects, right favorites. No inventory or creative packets. */
public final class MapBlockScreen extends ThemedScreen {
    private record BlockChoice(String id,String label,ItemStack icon){}
    private final Screen parent;
    private final java.util.function.Supplier<String> selected;
    private final java.util.function.Consumer<String> chooseBlock;
    private final List<BlockChoice> catalog=new ArrayList<>(),filtered=new ArrayList<>();
    private final List<Cell> cells=new ArrayList<>();private final List<AbstractWidget> fixed=new ArrayList<>();
    private String query="",selectedId="";private boolean favoritesOnly;private int x,y,w,h,top,bottom,columns,rows,offset;
    public MapBlockScreen(Screen parent){this(parent,"Map Editor · Blocks",()->MapEditor.material,id->{MapEditor.material=id;MapEditor.save();});}
    public MapBlockScreen(Screen parent,String title,java.util.function.Supplier<String> selected,java.util.function.Consumer<String> chooseBlock){super(Component.literal(title));this.parent=parent;this.selected=selected;this.chooseBlock=chooseBlock;}
    @Override protected void init(){
        if(catalog.isEmpty())for(var block:BuiltInRegistries.BLOCK){String id=BuiltInRegistries.BLOCK.getKey(block).toString();if(MapEditor.selectable(id))catalog.add(new BlockChoice(id,block.getName().getString(),MapEditor.icon(block.defaultBlockState())));}
        catalog.sort(Comparator.comparing(BlockChoice::id));fixed.clear();cells.clear();w=Math.min(540,width-16);h=Math.min(400,height-16);x=(width-w)/2;y=(height-h)/2;
        var search=new PinkUi.Field(font,x+12,y+34,w-24,"Search blocks");search.setMaxLength(128);search.setValue(query);search.setHint(Component.literal("Search block name or ID"));search.setResponder(value->{query=value;offset=0;refresh();});fixed.add(addWidget(search));
        fixed.add(addWidget(new PinkUi.Action(x+12,y+64,(w-30)/2,22,"All Blocks",false,()->{favoritesOnly=false;offset=0;refresh();}).selected(()->!favoritesOnly)));
        fixed.add(addWidget(new PinkUi.Action(x+18+(w-30)/2,y+64,(w-30)/2,22,"Favorites",false,()->{favoritesOnly=true;offset=0;refresh();}).selected(()->favoritesOnly)));
        fixed.add(addWidget(new PinkUi.Action(x+12,y+h-30,w-24,22,"Back",false,this::onClose)));
        top=y+96;bottom=y+h-49;columns=Math.max(1,(w-30)/26);rows=Math.max(1,(bottom-top)/26);
        for(int row=0;row<rows;row++)for(int col=0;col<columns;col++)cells.add(addWidget(new Cell(x+12+col*26,top+row*26)));
        refresh();
    }
    private void refresh(){
        String q=query.strip().toLowerCase(Locale.ROOT);filtered.clear();for(var choice:catalog)if((!favoritesOnly||MapEditor.favorites.contains(choice.id))&&(choice.id.contains(q)||choice.label.toLowerCase(Locale.ROOT).contains(q)))filtered.add(choice);
        int max=Math.max(0,(filtered.size()+columns-1)/columns-rows);offset=Math.clamp(offset,0,max);
        for(int i=0;i<cells.size();i++){int index=offset*columns+i;var cell=cells.get(i);cell.value=index<filtered.size()?filtered.get(index):null;cell.visible=cell.value!=null;if(cell.visible)cell.setMessage(Component.literal(cell.value.label));}
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubled){
        if(event.button()==ClientInput.MOUSE_RIGHT){for(var cell:cells)if(cell.visible&&cell.isMouseOver(event.x(),event.y())){MapEditor.favorite(cell.value.id);refresh();return true;}}
        return super.mouseClicked(event,doubled);
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){if(mx>=x&&mx<x+w&&my>=top&&my<bottom){offset-=(int)Math.signum(vertical)*2;refresh();return true;}return super.mouseScrolled(mx,my,horizontal,vertical);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
        outline(g,x,y,w,h,10,BORDER,PANEL);text(g,font,getTitle().getString(),x+12,y+14,PINK,w-24);for(var widget:fixed)widget.extractRenderState(g,mx,my,dt);
        // Keep the dense grid in two passes. Interleaving thousands of border rectangles and
        // item models makes vanilla's GUI overlap search dominate every frame.
        selectedId=selected.get();for(var cell:cells)if(cell.visible)cell.extractRenderState(g,mx,my,dt);
        for(var cell:cells)if(cell.visible)cell.extractIcon(g);
        String hint="Left: select · Right: favorite · "+filtered.size()+" blocks";for(var cell:cells)if(cell.visible&&cell.isMouseOver(mx,my))hint=cell.value.label+" · "+cell.value.id;
        text(g,font,hint,x+12,y+h-44,MUTED,w-24);if(filtered.isEmpty())text(g,font,"No matching blocks",x+15,top+12,MUTED,w-30);
        int total=(filtered.size()+columns-1)/columns;if(total>rows){var bar=ScrollBarLayout.of(top,rows*26,rows,total,total-rows);rounded(g,x+w-12,top,3,rows*26,1,BORDER);rounded(g,x+w-12,bar.thumbStart(offset),3,bar.thumbLength(),1,PINK);}
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){ClientUiAccess.setScreen(minecraft,parent);}
    private final class Cell extends PinkUi.Action {
        private BlockChoice value;
        Cell(int x,int y){super(x,y,24,24,"Block",false,()->{});}
        private void choose(){if(value==null)return;chooseBlock.accept(value.id);onClose();}
        @Override public void onClick(MouseButtonEvent e,boolean doubled){choose();}
        @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent e){if(e.key()==ClientInput.ENTER||e.key()==ClientInput.SPACE){choose();return true;}return super.keyPressed(e);}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
            if(value==null)return;int border=value.id.equals(selectedId)?0xFF7BE6A2:MapEditor.favorites.contains(value.id)?0xFF6DE6EE:isFocused()?PINK:BORDER;
            int left=getX(),top=getY();g.fill(left+1,top+1,left+23,top+23,isHoveredOrFocused()?0x27FFFFFF:CARD);
            g.fill(left,top,left+24,top+1,border);g.fill(left,top+23,left+24,top+24,border);
            g.fill(left,top+1,left+1,top+23,border);g.fill(left+23,top+1,left+24,top+23,border);
        }
        private void extractIcon(GuiGraphicsExtractor g){g.item(value.icon,getX()+4,getY()+4);}
    }
}
