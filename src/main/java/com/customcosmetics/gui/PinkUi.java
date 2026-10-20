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
    public static final int PANEL = 0xBD252830;
    public static final int SIDEBAR = 0x5820242C;
    public static final int CARD = 0x13FFFFFF;
    public static final int BORDER = 0x28FFFFFF;
    public static final int PINK = 0xFFFFA8CA;
    public static final int PINK_DARK = 0x32FFA8CA;
    public static final int TEXT = 0xFFF5F5F7;
    public static final int MUTED = 0xFFC2C4CC;
    public static final int DIM = 0xFF9599A6;
    public static final int INK = 0xFF352630;

    private PinkUi() {}

    public static void rounded(GuiGraphicsExtractor g, int x, int y, int width, int height, int radius, int color) {
        if (width <= 0 || height <= 0) return;
        radius = Math.min(radius, Math.min(width, height) / 2);
        g.fill(x, y + radius, x + width, y + height - radius, color);
        for (int i = 0; i < radius; i++) {
            double dy = radius - i - 0.5;
            int inset = (int) Math.ceil(radius - Math.sqrt(radius * radius - dy * dy));
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

        public Action(int x, int y, int w, int h, String text, boolean primary, Runnable action) {
            super(x, y, w, h, Component.literal(text));
            this.action = action;
            this.primary = primary;
        }

        public Action selected(BooleanSupplier selected) { this.selected = selected; return this; }
        public Action leftAligned() { leftAligned = true; return this; }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            boolean hot = isHoveredOrFocused() && active;
            boolean chosen = selected.getAsBoolean();
            int bg = primary ? (hot ? 0xFFFFB6D0 : PINK) : chosen ? PINK_DARK : hot ? 0x27FFFFFF : CARD;
            outline(g, getX(), getY(), width, height, 6, chosen || isFocused() ? PINK : BORDER, active ? bg : PANEL);
            Font font = Minecraft.getInstance().font;
            if (cachedMessage != getMessage() || cachedWidth != width) {
                cachedMessage = getMessage(); cachedWidth = width;
                String original = getMessage().getString();
                int available = Math.max(0, width - (leftAligned ? 22 : 12));
                cachedLabel = font.width(original) <= available ? original
                    : font.plainSubstrByWidth(original, Math.max(0, available - font.width("…"))) + "…";
                cachedLabelWidth = font.width(cachedLabel);
            }
            if (chosen && leftAligned) rounded(g, getX() + 3, getY() + 6, 2, height - 12, 1, PINK);
            g.text(font, cachedLabel, getX() + (leftAligned ? 11 : (width - cachedLabelWidth) / 2), getY() + (height - 9) / 2,
                !active ? DIM : primary ? INK : chosen ? PINK : TEXT, false);
        }

        @Override public void onClick(MouseButtonEvent event, boolean doubleClick) { action.run(); }
        @Override public boolean keyPressed(KeyEvent event) {
            if (active && (event.key() == ClientInput.ENTER || event.key() == ClientInput.SPACE || event.key() == ClientInput.NUMPAD_ENTER)) {
                playButtonClickSound(Minecraft.getInstance().getSoundManager());
                action.run();
                return true;
            }
            return false;
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }
    }

    public static final class Toggle extends Action {
        private final BooleanSupplier enabled;
        private float position;
        private long lastFrame;
        private final Component onMessage, offMessage;
        private Boolean lastEnabled;

        public Toggle(int x, int y, String name, BooleanSupplier enabled, Runnable change) {
            super(x, y, 34, 18, name, false, change);
            this.enabled = enabled;
            position = enabled.getAsBoolean() ? 1 : 0;
            onMessage = Component.literal(name + ": On");
            offMessage = Component.literal(name + ": Off");
        }

        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            long now = System.nanoTime();
            double elapsed = lastFrame == 0 ? 1 : Math.min(1, (now - lastFrame) / 90_000_000.0);
            lastFrame = now;
            boolean value = enabled.getAsBoolean();
            position += ((value ? 1 : 0) - position) * (float) elapsed;
            if (lastEnabled == null || lastEnabled != value) {
                lastEnabled = value;
                setMessage(value ? onMessage : offMessage);
            }
            outline(g, getX(), getY(), width, height, 9, isFocused() ? TEXT : BORDER, mix(0xFF555962, PINK, position));
            rounded(g, getX() + 3 + Math.round(position * 16), getY() + 3, 12, 12, 6, TEXT);
        }
    }

    public static final class Slider extends AbstractSliderButton {
        private final String label;
        private final double min, max, step;
        private final DoubleConsumer setter;
        private int labelSpace;

        public Slider(int x, int y, int w, String label, double value, double min, double max, double step, DoubleConsumer setter) {
            super(x, y, w, 30, Component.literal(label), Math.clamp((value - min) / (max - min), 0, 1));
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
            int progress = (int) Math.round(value * (width - 8));
            rounded(g, getX(), trackY, progress + 4, 3, 1, PINK);
            rounded(g, getX() + progress, trackY - 3, 8, 9, 4, isHoveredOrFocused() ? TEXT : PINK);
        }
    }

    public static final class Field extends EditBox {
        private boolean invalid;
        private boolean secret;
        private final int paddingY, paddingX, boxHeight;

        public Field(Font font, int x, int y, int width, String name) {
            this(font,x,y,width,name,30);
        }
        public Field(Font font, int x, int y, int width, String name, int height) {
            super(font, x + (height == 30 ? 9 : 6), y + (height - 12) / 2, width - (height == 30 ? 18 : 12), 12, Component.literal(name));
            paddingY = (height - 12) / 2; paddingX = height == 30 ? 9 : 6; boxHeight = height;
            setBordered(false);
            setTextShadow(false);
            setTextColor(TEXT);
        }

        public void invalid(boolean invalid) { this.invalid = invalid; setTextColor(invalid ? 0xFFFF7F9B : TEXT); }
        public int insetY() { return paddingY; }
        public Field secret(boolean value) { secret = value; return this; }

        @Override public void extractWidgetRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
            outline(g, getX() - paddingX, getY() - paddingY, width + paddingX * 2, boxHeight, 5,
                invalid ? 0xFFFF7F9B : isFocused() ? PINK : BORDER, SIDEBAR);
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
            // A small grid keeps the custom picker inexpensive in Minecraft's GUI render queue.
            for (int x = 0; x < mapWidth; x += 3) {
                int color = Color.HSBtoRGB(x / (float) Math.max(1, mapWidth - 1), 1, 1);
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
