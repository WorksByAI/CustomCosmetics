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
    private final String title;private final List<Option> options;private final Supplier<String> value;private final Consumer<String> choose;
    private String displayed;private boolean open;private int highlighted,first,menuTop,visibleRows;
    OptionDropdown(int x,int y,int w,String title,List<Option> options,Supplier<String> value,Consumer<String> choose){super(x,y,w,24,title,false,()->{});this.title=title;this.options=List.copyOf(options);this.value=value;this.choose=choose;refresh();}
    private void refresh(){String current=value.get();if(!Objects.equals(current,displayed)){displayed=current;var option=options.stream().filter(o->o.value.equals(current)).findFirst().orElse(options.getFirst());setMessage(Component.literal(title+": "+option.label+"  ▾"));}}
    @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){refresh();super.extractWidgetRenderState(g,mx,my,dt);}
    @Override public void onClick(MouseButtonEvent e,boolean twice){open=!open;highlighted=0;for(int i=0;i<options.size();i++)if(options.get(i).value.equals(value.get()))highlighted=i;first=0;}
    void closeMenu(){open=false;}
    void drawMenu(GuiGraphicsExtractor g,int mx,int my,int top,int bottom){
        if(!open||!visible)return;visibleRows=Math.max(1,Math.min(options.size(),(bottom-top-8)/22));first=Math.clamp(first,0,options.size()-visibleRows);int height=visibleRows*22+8;menuTop=Math.clamp(getBottom()+3,top,Math.max(top,bottom-height));
        g.nextStratum();outline(g,getX(),menuTop,width,height,4,PINK,PANEL);
        for(int row=0;row<visibleRows;row++){int i=first+row,y=menuTop+4+row*22;boolean hot=mx>=getX()&&mx<getRight()&&my>=y&&my<y+22;
            if(hot||i==highlighted)rounded(g,getX()+3,y,width-6,22,3,PINK_DARK);
            uiText(g,UiFonts.get(Minecraft.getInstance().font),options.get(i).label,getX()+9,y+6,options.get(i).value.equals(value.get())?PINK:TEXT,width-18);
        }
    }
    boolean menuClick(MouseButtonEvent e){if(!open)return false;if(e.button()==ClientInput.MOUSE_LEFT&&e.x()>=getX()&&e.x()<getRight()&&e.y()>=menuTop+4&&e.y()<menuTop+4+visibleRows*22){int index=first+(int)(e.y()-menuTop-4)/22;open=false;choose.accept(options.get(index).value);}else open=false;return true;}
    boolean menuScroll(double x,double y,double amount){if(!open)return false;if(x>=getX()&&x<getRight()&&y>=menuTop&&y<menuTop+visibleRows*22+8){first=Math.clamp(first-(int)Math.signum(amount),0,options.size()-Math.max(1,visibleRows));return true;}open=false;return false;}
    boolean menuKey(KeyEvent e){if(!open)return false;if(e.key()==ClientInput.ESCAPE){open=false;return true;}if(e.key()==ClientInput.UP||e.key()==ClientInput.DOWN){highlighted=Math.floorMod(highlighted+(e.key()==ClientInput.UP?-1:1),options.size());if(highlighted<first)first=highlighted;else if(highlighted>=first+visibleRows)first=highlighted-visibleRows+1;return true;}if(e.key()==ClientInput.ENTER||e.key()==ClientInput.SPACE){open=false;choose.accept(options.get(highlighted).value);}return true;}
    @Override public boolean keyPressed(KeyEvent e){if(menuKey(e))return true;if(e.key()==ClientInput.ENTER||e.key()==ClientInput.SPACE){open=true;return true;}return false;}
}
