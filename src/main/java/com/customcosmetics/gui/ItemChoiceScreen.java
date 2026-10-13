// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientUiAccess;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.input.MouseButtonEvent;
import static com.customcosmetics.gui.PinkUi.*;

/** A bounded, searchable list; only the visible rows submit item models. */
public final class ItemChoiceScreen extends Screen {
    public record Choice(String label, String id, ItemStack icon) {
        boolean matches(String search) { return label.toLowerCase(Locale.ROOT).contains(search) || id.toLowerCase(Locale.ROOT).contains(search); }
    }
    private final Screen parent;
    private final List<Choice> choices;
    private final Consumer<Choice> selected;
    private final List<Row> rows = new ArrayList<>();
    private final List<AbstractWidget> fixed = new ArrayList<>();
    private List<Choice> filtered;
    private String query = "";
    private int x,y,w,h,top,bottom,offset;
    private boolean draggingScrollbar;
    private int scrollbarGrabOffset;
    public ItemChoiceScreen(Screen parent, String title, List<Choice> choices, Consumer<Choice> selected) {
        super(Component.literal(title)); this.parent=parent; this.choices=List.copyOf(choices); this.selected=selected;
    }
    @Override protected void init() {
        fixed.clear(); rows.clear();
        w=Math.min(600,width-16);h=Math.min(436,height-16);x=(width-w)/2;y=(height-h)/2;
        var search=new PinkUi.Field(font,x+16,y+42,w-32,"Search");
        search.setMaxLength(128);search.setValue(query);search.setHint(Component.literal("Search name or ID"));
        search.setResponder(value -> { CosmeticsScreen.childInteraction();query=value;offset=0;refresh(); });
        fixed.add(addWidget(search));
        fixed.add(addWidget(new PinkUi.Action(x+16,y+h-36,w-32,26,"Back",false,this::onClose)));
        top=y+82;bottom=y+h-45;
        for(int i=0;i<Math.max(1,(bottom-top)/34);i++)rows.add(addWidget(new Row(x+16,top+i*34,w-42)));
        refresh();
    }
    private void refresh() {
        String search=query.strip().toLowerCase(Locale.ROOT);
        filtered=choices.stream().filter(value -> value.matches(search)).toList();
        offset=Math.clamp(offset,0,Math.max(0,filtered.size()-rows.size()));
        for(int i=0;i<rows.size();i++) {
            var row=rows.get(i);row.value=offset+i<filtered.size()?filtered.get(offset+i):null;row.visible=row.value!=null;
            if(row.value!=null)row.setMessage(Component.literal(row.value.label()+" · "+row.value.id()));
        }
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        CosmeticsScreen.childInteraction();
        if(mx>=x&&mx<x+w&&my>=top&&my<bottom){offset-=(int)Math.signum(vertical)*3;refresh();return true;}
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    private ScrollBarLayout scrollbar(){
        return ScrollBarLayout.of(top,bottom-top,rows.size(),filtered.size(),Math.max(0,filtered.size()-rows.size()));
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick) {
        if(event.button()==com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT&&filtered.size()>rows.size()
                &&event.x()>=x+w-31&&event.x()<x+w-11&&event.y()>=top&&event.y()<bottom) {
            var bar=scrollbar();int thumbTop=bar.thumbStart(offset);
            if(event.y()>=thumbTop&&event.y()<thumbTop+bar.thumbLength())scrollbarGrabOffset=(int)event.y()-thumbTop;
            else scrollbarGrabOffset=bar.thumbLength()/2;
            offset=bar.offsetAtPointer(event.y(),scrollbarGrabOffset);refresh();draggingScrollbar=true;
            CosmeticsScreen.childInteraction();return true;
        }
        return super.mouseClicked(event,doubleClick);
    }
    @Override public boolean mouseDragged(MouseButtonEvent event,double dx,double dy) {
        if(draggingScrollbar){offset=scrollbar().offsetAtPointer(event.y(),scrollbarGrabOffset);refresh();return true;}
        return super.mouseDragged(event,dx,dy);
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if(draggingScrollbar){draggingScrollbar=false;return true;}
        return super.mouseReleased(event);
    }
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta) {
        if(minecraft.level==null)super.extractBackground(g,mx,my,delta);else g.blurBeforeThisStratum();
        g.fill(0,0,width,height,0x4020242A);
    }
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta) {
        outline(g,x,y,w,h,11,BORDER,PANEL);
        text(g,font,getTitle().getString(),x+16,y+17,PINK,w-32);
        for(var widget:fixed)widget.extractRenderState(g,mx,my,delta);
        g.enableScissor(x+15,top,x+w-15,bottom);
        for(var row:rows)if(row.visible)row.extractRenderState(g,mx,my,delta);
        if(filtered.isEmpty())text(g,font,"No matching items",x+28,top+15,MUTED,w-56);
        if(filtered.size()>rows.size()) {
            var bar=scrollbar();
            rounded(g,x+w-21,top,3,bottom-top,1,BORDER);
            rounded(g,x+w-21,bar.thumbStart(offset),3,bar.thumbLength(),1,PINK);
        }
        g.disableScissor();
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){ClientUiAccess.setScreen(minecraft,parent);}
    private final class Row extends PinkUi.Action {
        private Choice value;
        Row(int x,int y,int width){super(x,y,width,30,"Item",false,()->{});}
        private void choose(){if(value!=null){CosmeticsScreen.childInteraction();selected.accept(value);onClose();}}
        @Override public void onClick(net.minecraft.client.input.MouseButtonEvent event,boolean doubled){choose();}
        @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event){
            if(event.key()==com.customcosmetics.compatibility.ClientInput.ENTER||event.key()==com.customcosmetics.compatibility.ClientInput.SPACE){choose();return true;}
            return super.keyPressed(event);
        }
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
            if(value==null)return;
            outline(g,getX(),getY(),getWidth(),getHeight(),5,isFocused()?PINK:BORDER,isHoveredOrFocused()?0x27FFFFFF:CARD);
            g.item(value.icon(),getX()+7,getY()+7);
            text(g,font,value.label(),getX()+32,getY()+5,TEXT,getWidth()-42);
            text(g,font,value.id(),getX()+32,getY()+17,MUTED,getWidth()-42);
        }
    }
}
