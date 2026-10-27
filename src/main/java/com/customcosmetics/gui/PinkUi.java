package com.customcosmetics.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.customcosmetics.compatibility.ClientInput;

import java.awt.Color;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

/** Code-drawn controls: no textures or rendering dependencies on another mod. */
public final class PinkUi {
    // Legacy public names retained for every existing dialog; palette changes update all of them.
    public static int PANEL=0xFF1A1E23,SIDEBAR=0xFF14181C,CARD=0xFF22272D,BORDER=0xFF333A42;
    public static int PINK=0xFF6DB29A,PINK_DARK=0x246DB29A,TEXT=0xFFE8ECF0,MUTED=0xFFA2ACB6,DIM=0xFF798592,INK=0xFF101619,HOVER=0xFF303840;
    private static final int[][] CORNERS=corners();
    private static int[][] corners(){var result=new int[17][];for(int r=0;r<result.length;r++){result[r]=new int[r];for(int row=0;row<r;row++){double dy=r-row-.5;result[r][row]=(int)Math.ceil(r-Math.sqrt(r*r-dy*dy));}}return result;}

    private PinkUi() {}

    public static void rounded(GuiGraphicsExtractor g, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        radius = Math.min(radius, Math.min(width, height) / 2);
        g.fill(x, y + radius, x + width, y + height - radius, color);
        for (int i = 0; i < radius; i++) {
            int inset = roundedInset(radius,i);
            g.fill(x + inset, y + i, x + width - inset, y + i + 1, color);
            g.fill(x + inset, y + height - i - 1, x + width - inset, y + height - i, color);
        }
    }

    public static void outline(GuiGraphicsExtractor g, int x, int y, int w, int h, int r, int border, int fill) {
        if (w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        // Draw the border as a ring so translucent fills do not reveal a solid border underneath.
        rounded(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
        int curvedRows = Math.max(1, r);
        for (int row = 0; row < Math.min(h, curvedRows * 2); row++) {
            int actualRow = row < curvedRows ? row : h - curvedRows * 2 + row;
            int edge = Math.min(actualRow, h - 1 - actualRow);
            int outer = roundedInset(r, edge);
            if (actualRow == 0 || actualRow == h - 1) {
                g.fill(x + outer, y + actualRow, x + w - outer, y + actualRow + 1, border);
            } else {
                int inner = 1 + roundedInset(Math.max(0, r - 1), edge - 1);
                g.fill(x + outer, y + actualRow, x + inner, y + actualRow + 1, border);
                g.fill(x + w - inner, y + actualRow, x + w - outer, y + actualRow + 1, border);
            }
        }
        if (h > curvedRows * 2) {
            g.fill(x, y + curvedRows, x + 1, y + h - curvedRows, border);
            g.fill(x + w - 1, y + curvedRows, x + w, y + h - curvedRows, border);
        }
    }

    private static int roundedInset(int radius, int row) {
        if (row >= radius || radius == 0) return 0;
        if(radius<CORNERS.length)return CORNERS[radius][Math.max(0,row)];
        double dy = radius - row - 0.5;
        return (int) Math.ceil(radius - Math.sqrt(radius * radius - dy * dy));
    }

    public static int mix(int from, int to, double t) {
        t = Math.clamp(t, 0, 1);
        int r = (int) (((from >> 16) & 255) * (1 - t) + ((to >> 16) & 255) * t);
        int green = (int) (((from >> 8) & 255) * (1 - t) + ((to >> 8) & 255) * t);
        int b = (int) ((from & 255) * (1 - t) + (to & 255) * t);
        return 0xFF000000 | r << 16 | green << 8 | b;
    }

    public static void gradient(GuiGraphicsExtractor g, int x, int y, int w, int h, int start, int end) {
        for (int i = 0; i < w; i++) g.fill(x + i, y, x + i + 1, y + h, mix(start, end, i / (double) Math.max(1, w - 1)));
    }

    public static void text(GuiGraphicsExtractor g, Font font, String text, int x, int y, int color, int maxWidth) {
        if (maxWidth <= 0) return;
        String result = font.width(text) > maxWidth ? font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("…"))) + "…" : text;
        g.text(font, result, x, y, color, false);
    }

    public static class Action extends AbstractWidget {
        private final Runnable action;
        private final boolean primary;
        private BooleanSupplier selected = () -> false;
        private boolean leftAligned;
        private String cachedLabel;
        private int cachedWidth = -1, cachedLabelWidth;
        private Component cachedMessage;
        private final UiMotion hover=new UiMotion(0),selection=new UiMotion(0),press=new UiMotion(0);

        public Action(int x, int y, int w, int h, String text, boolean primary, Runnable action) {
            super(x, y, w, h, Component.literal(text));
            UiTheme.refresh();
            this.action = action;
            this.primary = primary;
        }

        public Action selected(BooleanSupplier selected) { this.selected = selected; return this; }
        public Action leftAligned() { leftAligned = true; return this; }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            boolean hot = isHoveredOrFocused() && active;
            boolean chosen = selected.getAsBoolean();
            boolean animate=com.customcosmetics.config.ConfigManager.uiAnimationsEnabled;
            float warmth=hover.value(hot?1:0,animate),picked=selection.value(chosen?1:0,animate),pressed=press.value(0,animate);
            int base=primary?PINK:leftAligned?SIDEBAR:CARD;
            int bg=mix(base,primary?mix(PINK,TEXT,.15):HOVER,warmth*.8+pressed*.2);
            if(!primary&&picked>0)bg=mix(bg,mix(CARD,PINK,.17),picked);
            int border=primary?PINK:isFocused()?PINK:leftAligned?bg:BORDER;
            outline(g, getX(), getY(), width, height, 4, border, active ? bg : SIDEBAR);
            Font font = Minecraft.getInstance().font;
            if (cachedMessage != getMessage() || cachedWidth != width) {
                cachedMessage = getMessage(); cachedWidth = width;
                String original = getMessage().getString();
                int available = Math.max(0, width - (leftAligned ? 22 : 12));
                cachedLabel = font.width(original) <= available ? original
                    : font.plainSubstrByWidth(original, Math.max(0, available - font.width("…"))) + "…";
                cachedLabelWidth = font.width(cachedLabel);
            }
            if (picked>.01f && leftAligned) rounded(g, getX()+3,getY()+6,2,Math.max(1,Math.round((height-12)*picked)),1,PINK);
            g.text(font, cachedLabel, getX() + (leftAligned ? 11 : (width - cachedLabelWidth) / 2), getY() + (height - 9) / 2,
                !active ? DIM : primary ? INK : TEXT, false);
        }

        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { press.snap(1);action.run(); }
        @Override public boolean keyPressed(KeyEvent event) {
            if (active && (event.key() == ClientInput.ENTER || event.key() == ClientInput.SPACE || event.key() == ClientInput.NUMPAD_ENTER)) {
                playButtonClickSound(Minecraft.getInstance().getSoundManager());
                press.snap(1);action.run();
                return true;
            }
            return false;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    public static final class Toggle extends Action {
        private final BooleanSupplier enabled;
        private final UiMotion position;
        private final Component onMessage, offMessage;
        private Boolean lastEnabled;
        public Toggle rowWidth(int pixels){setWidth(Math.max(34,pixels));return this;}

        public Toggle(int x, int y, String name, BooleanSupplier enabled, Runnable change) {
            super(x, y, 34, 18, name, false, change);
            this.enabled = enabled;
            position = new UiMotion(enabled.getAsBoolean()?1:0);
            onMessage = Component.literal(name + ": On");
            offMessage = Component.literal(name + ": Off");
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            boolean value = enabled.getAsBoolean();
            float at=position.value(value?1:0,com.customcosmetics.config.ConfigManager.uiAnimationsEnabled);
            if (lastEnabled == null || lastEnabled != value) {
                lastEnabled = value;
                setMessage(value ? onMessage : offMessage);
            }
            int switchX=getX()+width-34;
            outline(g,switchX,getY(),34,height,9,isFocused()?TEXT:BORDER,mix(HOVER,PINK,at));
            rounded(g,switchX+3+Math.round(at*16),getY()+3,12,12,6,at>.65?INK:TEXT);
        }
    }

    public static final class Slider extends AbstractSliderButton {
        private final String label;
        private final double min, max, step;
        private final DoubleConsumer setter;
        private int labelSpace;
        private final UiMotion knob;

        public Slider(int x, int y, int w, String label, double value, double min, double max, double step, DoubleConsumer setter) {
            super(x, y, w, 30, Component.literal(label), Math.clamp((value - min) / (max - min), 0, 1));
            UiTheme.refresh();knob=new UiMotion((float)this.value);
            this.label = label;
            this.min = min;
            this.max = max;
            this.step = step;
            this.setter = setter;
            updateMessage();
        }

        public double actual() { return Math.clamp(Math.round((min + value * (max - min)) / step) * step, min, max); }
        public Slider labelSpace(int pixels) { labelSpace = pixels; return this; }
        public void setActual(double actual) { setValue(Math.clamp((actual - min) / (max - min), 0, 1)); }
        @Override protected void updateMessage() {
            setMessage(Component.literal(String.format(Locale.ROOT, "%s: %.2f", label, actual())));
        }
        @Override protected void applyValue() { setter.accept(actual()); }
        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            Font font = Minecraft.getInstance().font;
            text(g, font, label, getX(), getY(), TEXT, width - labelSpace);
            int trackY = getY() + 21;
            rounded(g, getX(), trackY, width, 3, 1, BORDER);
            int progress = Math.round(knob.value((float)value,com.customcosmetics.config.ConfigManager.uiAnimationsEnabled)*(width-8));
            rounded(g, getX(), trackY, progress + 4, 3, 1, PINK);
            rounded(g, getX() + progress, trackY - 3, 8, 9, 4, isHoveredOrFocused() ? TEXT : PINK);
        }
    }

    public static final class Field extends EditBox {
        private boolean invalid;
        private boolean secret;
        private final int paddingY, paddingX, boxHeight;
        private final UiMotion focus=new UiMotion(0);

        public Field(Font font, int x, int y, int width, String name) {
            this(font,x,y,width,name,30);
        }
        public Field(Font font, int x, int y, int width, String name, int height) {
            super(font, x + (height == 30 ? 9 : 6), y + (height - 12) / 2, width - (height == 30 ? 18 : 12), 12, Component.literal(name));
            UiTheme.refresh();
            paddingY = (height - 12) / 2; paddingX = height == 30 ? 9 : 6; boxHeight = height;
            setBordered(false);
            setTextShadow(false);
            setTextColor(TEXT);
        }

        public void invalid(boolean invalid) { this.invalid = invalid; setTextColor(invalid ? 0xFFFF7F9B : TEXT); }
        public int insetY() { return paddingY; }
        public Field secret(boolean value) { secret = value; return this; }

        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            float focused=focus.value(isFocused()?1:0,com.customcosmetics.config.ConfigManager.uiAnimationsEnabled);
            outline(g,getX()-paddingX,getY()-paddingY,width+paddingX*2,boxHeight,4,
                invalid?0xFFDA7771:mix(BORDER,PINK,focused),SIDEBAR);
            if (!secret) super.extractWidgetRenderState(g, mx, my, dt);
            else {
                Font font = Minecraft.getInstance().font;
                int visible = Math.max(1,width / Math.max(1,font.width("*"))), cursor = getCursorPosition();
                int start = Math.max(0,cursor - visible + 1);
                String masked = "*".repeat(Math.min(visible,Math.max(0,getValue().length() - start)));
                if (!getHighlighted().isEmpty()) g.fill(getX(),getY(),getX() + font.width(masked),getY() + 10,PINK_DARK);
                g.text(font,masked,getX(),getY(),invalid ? 0xFFFF7F9B : TEXT,false);
                if (isFocused() && System.currentTimeMillis() / 500 % 2 == 0) {
                    int caret = getX() + font.width("*".repeat(Math.max(0,cursor - start)));
                    g.fill(caret,getY() - 1,caret + 1,getY() + 10,TEXT);
                }
            }
        }
    }

    /** Hue/saturation plane and value strip, matching the original gradient editor's controls. */
    public static final class ColorPicker extends AbstractWidget {
        private float hue, saturation, brightness;
        private final IntConsumer setter;
        private boolean draggingValue;
        private int cachedMapWidth=-1;
        private int[] hueColumns=new int[0];

        public ColorPicker(int x, int y, int width, int height, int rgb, IntConsumer setter) {
            super(x, y, width, height, Component.literal("Color: hue, saturation and value"));
            this.setter = setter;
            setColor(rgb);
        }

        public void setColor(int rgb) {
            float[] hsv = Color.RGBtoHSB((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, null);
            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2];
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            int mapWidth = width - 24;
            // Hue columns depend only on geometry; cache conversions until the picker resizes.
            if(cachedMapWidth!=mapWidth){
                cachedMapWidth=mapWidth;hueColumns=new int[Math.max(0,(mapWidth+2)/3)];
                for(int i=0;i<hueColumns.length;i++)hueColumns[i]=Color.HSBtoRGB(i*3/(float)Math.max(1,mapWidth-1),1,1);
            }
            for (int x = 0; x < mapWidth; x += 3) {
                int color = hueColumns[x/3];
                g.fillGradient(getX() + x, getY(), getX() + Math.min(mapWidth, x + 3), getY() + height, color, 0xFFFFFFFF);
            }
            int selected = Color.HSBtoRGB(hue, saturation, 1);
            g.fillGradient(getX() + width - 14, getY(), getX() + width, getY() + height, selected, 0xFF000000);
            int cx = getX() + Math.round(hue * (mapWidth - 1));
            int cy = getY() + Math.round((1 - saturation) * (height - 1));
            outline(g, cx - 4, cy - 4, 9, 9, 4, TEXT, 0xFF18111B);
            rounded(g, cx - 2, cy - 2, 5, 5, 2, Color.HSBtoRGB(hue, saturation, brightness));
            int by = getY() + Math.round((1 - brightness) * (height - 1));
            outline(g, getX() + width - 17, by - 2, 20, 5, 2, TEXT, INK);
            if (isFocused()) {
                g.fill(getX() - 2, getY() - 2, getX() + width + 2, getY() - 1, PINK);
                g.fill(getX() - 2, getY() + height + 1, getX() + width + 2, getY() + height + 2, PINK);
            }
        }

        private void update(double mx, double my) {
            if (draggingValue) brightness = 1 - (float) Math.clamp((my - getY()) / (height - 1), 0, 1);
            else {
                hue = (float) Math.clamp((mx - getX()) / (width - 25), 0, 1);
                saturation = 1 - (float) Math.clamp((my - getY()) / (height - 1), 0, 1);
            }
            setter.accept(Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF);
        }
        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) {
            draggingValue = event.x() >= getX() + width - 20;
            update(event.x(), event.y());
        }
        @Override protected void onDrag(MouseButtonEvent event, double dx, double dy) { update(event.x(), event.y()); }
        @Override public boolean keyPressed(KeyEvent event) {
            int key = event.key();
            if (key == ClientInput.LEFT) hue = Math.max(0, hue - 0.01f);
            else if (key == ClientInput.RIGHT) hue = Math.min(1, hue + 0.01f);
            else if (key == ClientInput.UP) saturation = Math.min(1, saturation + 0.01f);
            else if (key == ClientInput.DOWN) saturation = Math.max(0, saturation - 0.01f);
            else if (key == ClientInput.PAGE_UP) brightness = Math.min(1, brightness + 0.02f);
            else if (key == ClientInput.PAGE_DOWN) brightness = Math.max(0, brightness - 0.02f);
            else return false;
            setter.accept(Color.HSBtoRGB(hue, saturation, brightness) & 0xFFFFFF);
            return true;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }
}
