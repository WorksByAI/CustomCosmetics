// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import com.customcosmetics.compatibility.ClientInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;
import static com.customcosmetics.gui.PinkUi.*;
/** Scrollable selection popup; labels are cached by Action and idle frames do not allocate components. */
final class OptionDropdown extends PinkUi.Action {
    record Option(String value,String label){}
    private final String title;private List<Option> options;private final Supplier<String> value;private final Consumer<String> choose;
    private String displayed;private boolean open;private int highlighted,first,menuTop,visibleRows;
    OptionDropdown(int x,int y,int w,String title,List<Option> options,Supplier<String> value,Consumer<String> choose){super(x,y,w,24,title,false,()->{});this.title=title;this.options=List.copyOf(options);this.value=value;this.choose=choose;refresh();}
    OptionDropdown(int x,int y,int w,String title,Consumer<String> choose){
        this(x,y,w,title,List.of(),null,choose);
        setMessage(Component.literal(title+"  ▾"));
        active=false;
    }
    void setOptions(List<Option> options){
        this.options=List.copyOf(options);
        active=!options.isEmpty();
        closeMenu();
    }
    private void refresh(){if(value==null)return;String current=value.get();if(!Objects.equals(current,displayed)){displayed=current;var option=options.stream().filter(o->o.value.equals(current)).findFirst().orElse(options.getFirst());setMessage(Component.literal(title+": "+option.label+"  ▾"));}}
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){refresh();super.extractWidgetRenderState(g,mx,my,dt);}
    @Override public void onClick(MouseButtonEvent e,boolean twice){
        if(options.isEmpty())return;
        open=!open;highlighted=first=visibleRows=0;
        if(value!=null)for(int i=0;i<options.size();i++)if(options.get(i).value.equals(value.get()))highlighted=i;
    }
    private void apply(String value){if(!com.customcosmetics.client.ModErrors.action(choose,value))active=false;}
    void closeMenu(){open=false;visibleRows=0;}
    void drawMenu(GuiGraphicsExtractor g,int mx,int my,int top,int bottom){
        if(!open||!visible)return;
        int limit=(bottom-top-8)/22;
        if(value==null){
            int below=(bottom-getBottom()-11)/22,above=(getY()-top-11)/22;
            limit=Math.min(6,below>0?below:above);
        }
        visibleRows=Math.max(1,Math.min(options.size(),limit));
        first=Math.clamp(first,0,options.size()-visibleRows);
        int height=visibleRows*22+8;
        int anchor=value==null&&getBottom()+3+height>bottom?getY()-height-3:getBottom()+3;
        menuTop=Math.clamp(anchor,top,Math.max(top,bottom-height));
        g.nextStratum();outline(g,getX(),menuTop,width,height,4,PINK,PANEL);
        String current=value==null?null:value.get();
        var font=UiFonts.get(Minecraft.getInstance().font);
        for(int row=0;row<visibleRows;row++){int i=first+row,y=menuTop+4+row*22;boolean hot=mx>=getX()&&mx<getRight()&&my>=y&&my<y+22;
            if(hot||i==highlighted)rounded(g,getX()+3,y,width-6,22,3,PINK_DARK);
            var option=options.get(i);
            text(g,font,value==null?option.label:UiText.tr(option.label),getX()+9,y+6,option.value.equals(current)?PINK:TEXT,width-18);
        }
        if(value==null&&options.size()>visibleRows){
            int track=height-8,thumb=Math.max(6,track*visibleRows/options.size());
            int offset=(track-thumb)*first/(options.size()-visibleRows);
            g.fill(getRight()-5,menuTop+4+offset,getRight()-3,menuTop+4+offset+thumb,PINK);
        }
    }
    boolean menuClick(MouseButtonEvent e){
        if(!open)return false;
        if(inMenu(e.x(),e.y())){
            double offset=e.y()-menuTop-4;
            boolean onScrollRail=value==null&&options.size()>visibleRows&&e.x()>=getRight()-7;
            if(e.button()==ClientInput.MOUSE_LEFT&&!onScrollRail&&offset>=0&&offset<visibleRows*22){
                int index=first+(int)offset/22;
                closeMenu();apply(options.get(index).value);
            }
            return true;
        }
        closeMenu();
        return value!=null||isMouseOver(e.x(),e.y());
    }
    private boolean inMenu(double x,double y){
        return visible&&visibleRows>0&&x>=getX()&&x<getRight()&&y>=menuTop&&y<menuTop+visibleRows*22+8;
    }
    boolean menuScroll(double x,double y,double amount){if(!open)return false;if(inMenu(x,y)){first=Math.clamp(first-(int)Math.signum(amount),0,options.size()-visibleRows);return true;}closeMenu();return false;}
    boolean menuKey(KeyEvent e){if(!open)return false;if(e.key()==ClientInput.ESCAPE){closeMenu();return true;}if(e.key()==ClientInput.UP||e.key()==ClientInput.DOWN){highlighted=Math.floorMod(highlighted+(e.key()==ClientInput.UP?-1:1),options.size());if(highlighted<first)first=highlighted;else if(highlighted>=first+Math.max(1,visibleRows))first=highlighted-Math.max(1,visibleRows)+1;return true;}if(e.key()==ClientInput.ENTER||e.key()==ClientInput.SPACE){closeMenu();apply(options.get(highlighted).value);}return true;}
    @Override public boolean keyPressed(KeyEvent e){if(menuKey(e))return true;if(active&&!options.isEmpty()&&(e.key()==ClientInput.ENTER||e.key()==ClientInput.SPACE)){onClick(null,false);return true;}return false;}
}
