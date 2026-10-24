// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

/** Immutable, client-rendered gradient; elapsed time never requires a remote update. */
public record NicknameGradient(String style, int start, int end, double speed, int shimmerColor, int shimmerHighlightColor, double shimmerSize, double shimmerInterval) {
    private static final class Blend {
        final float hue,saturation,value;
        final double deltaHue,deltaSaturation,deltaValue;
        final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap colors=new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap(128);
        Blend(float hue,float saturation,float value,double deltaHue,double deltaSaturation,double deltaValue){
            this.hue=hue;this.saturation=saturation;this.value=value;this.deltaHue=deltaHue;this.deltaSaturation=deltaSaturation;this.deltaValue=deltaValue;
            colors.defaultReturnValue(-1);
        }
    }
    /** Exact-input caches: no color quantization and no change to the animation clock. */
    private static final ThreadLocal<PaintContext> PAINT=ThreadLocal.withInitial(PaintContext::new);
    private static final class PaintContext {
        NicknameGradient gradient;
        Blend blend;
        long time;
        boolean initialized,shimmering;
        double sweep;
        void prepare(NicknameGradient next,long nextTime){
            boolean changed=gradient!=next&&!next.equals(gradient);
            if(changed)blend=NicknameGradient.blend(next.start,next.end);
            gradient=next;
            if(initialized&&!changed&&time==nextTime)return;
            initialized=true;time=nextTime;shimmering=false;
            if(!next.style.equals("shimmer")||next.speed==0)return;
            long moving=next.motionMillis(),elapsed=Math.floorMod(nextTime,next.periodMillis());
            if(elapsed>=moving)return;
            shimmering=true;
            sweep=-next.shimmerSize-0.065+elapsed/(double)moving*(1+2*next.shimmerSize+0.13);
        }
    }
    private static final ThreadLocal<it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<Blend>> BLENDS=
        ThreadLocal.withInitial(()->new it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap<>(256));
    private static Blend blend(int from,int to){
        var entries=BLENDS.get();
        long key=((long)from<<32)|(to&0xffffffffL);
        var found=entries.getAndMoveToFirst(key);if(found!=null)return found;
        float[] a=hsv(from),b=hsv(to);if(a[1]<.001)a[0]=b[0];if(b[1]<.001)b[0]=a[0];
        double delta=b[0]-a[0];if(delta>.5)delta-=1;if(delta<-.5)delta+=1;
        var result=new Blend(a[0],a[1],a[2],delta,b[1]-a[1],b[2]-a[2]);
        entries.putAndMoveToFirst(key,result);if(entries.size()>256)entries.removeLast();return result;
    }
    public static final int PRESET_START = 0x24CFDF;
    public static final int PRESET_END = 0xEA78C5;
    public static final int DEFAULT_SHIMMER = 0xFFCF64;
    public static final double DEFAULT_SIZE = 0.16;
    public static final double DEFAULT_INTERVAL = 2;
    public static final int DEFAULT_HIGHLIGHT = 0xFFF0BB;
    public NicknameGradient(String style, int start, int end, double speed) {
        this(style, start, end, speed, DEFAULT_SHIMMER, DEFAULT_HIGHLIGHT, DEFAULT_SIZE, DEFAULT_INTERVAL);
    }
    public NicknameGradient(String style, int start, int end, double speed, int shimmerColor, int shimmerHighlightColor) {
        this(style,start,end,speed,shimmerColor,shimmerHighlightColor,DEFAULT_SIZE,DEFAULT_INTERVAL);
    }
    public NicknameGradient(String style,int start,int end,double speed,int shimmerColor,int shimmerHighlightColor,double shimmerSize) {
        this(style,start,end,speed,shimmerColor,shimmerHighlightColor,shimmerSize,DEFAULT_INTERVAL);
    }
    public static final long PERIOD_MS = 3400;

    public NicknameGradient {
        style = normalizeStyle(style);
        shimmerSize = normalizeSize(shimmerSize);
        shimmerInterval = normalizeInterval(shimmerInterval);
        start &= 0xFFFFFF;
        end &= 0xFFFFFF;
        shimmerColor &= 0xFFFFFF; shimmerHighlightColor &= 0xFFFFFF;
        speed = Double.isFinite(speed) ? Math.clamp(speed, 0, 4) : 1;
    }

    public static double normalizeInterval(double seconds) { return Double.isFinite(seconds) ? Math.clamp(seconds,0,30) : DEFAULT_INTERVAL; }
    public long motionMillis() { return speed == 0 ? 0 : Math.max(1,Math.round((1+2*shimmerSize+0.13)/(3.5/3.4)*1000/speed)); }
    public long periodMillis() { return motionMillis()+Math.round(shimmerInterval*1000); }
    public static double normalizeSize(double size) { return Double.isFinite(size) ? Math.clamp(size, 0.02, 1) : DEFAULT_SIZE; }
    public static String normalizeStyle(String style) {
        return "smooth".equals(style) || "shimmer".equals(style) || "segmented".equals(style) ? style : "character";
    }

    public boolean smooth() { return !style.equals("character"); }

    public int color(double position, double height, long timeMs) {
        var paint=PAINT.get();paint.prepare(this,timeMs);
        double x = Math.clamp(Double.isFinite(position) ? position : 0, 0, 1);
        int base = hueBlend(paint.blend, x);
        if (!paint.shimmering) return base;
        // A short, diagonal colored sweep followed by a quiet gap, as in the reference.
        double distance = Math.abs(x + (Math.clamp(Double.isFinite(height) ? height : 0.5, 0, 1) - 0.5) * 0.13 - paint.sweep);
        double intensity = Math.max(0, 1 - distance / shimmerSize);
        intensity = intensity * intensity * (3 - 2 * intensity);
        int shine = rgbBlend(shimmerColor, shimmerHighlightColor, intensity);
        return rgbBlend(base, shine, intensity * 0.96);
    }

    private static int hueBlend(Blend blend, double t) {
        long key=Double.doubleToLongBits(t);
        int cached=blend.colors.get(key);if(cached!=-1)return cached;
        double hue = blend.hue + blend.deltaHue * t;
        hue -= Math.floor(hue);
        double s = blend.saturation + blend.deltaSaturation * t;
        double v = blend.value + blend.deltaValue * t;
        double h = hue * 6, f = h - Math.floor(h), p = v * (1 - s), q = v * (1 - f * s), r = v * (1 - (1 - f) * s);
        int color=switch ((int) h % 6) {
            case 0 -> rgb(v, r, p); case 1 -> rgb(q, v, p); case 2 -> rgb(p, v, r);
            case 3 -> rgb(p, q, v); case 4 -> rgb(r, p, v); default -> rgb(v, p, q);
        };
        if(blend.colors.size()>=512)blend.colors.clear();
        blend.colors.put(key,color);return color;
    }

    private static float[] hsv(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        float h = d == 0 ? 0 : max == r ? (g - b) / d : max == g ? 2 + (b - r) / d : 4 + (r - g) / d;
        h = (h / 6 + 1) % 1;
        return new float[]{h, max == 0 ? 0 : d / max, max};
    }

    private static int rgb(double r, double g, double b) {
        return ((int)Math.round(r * 255) << 16) | ((int)Math.round(g * 255) << 8) | (int)Math.round(b * 255);
    }

    private static int rgbBlend(int a, int b, double t) {
        int r = (int)Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int)Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int blue = (int)Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | blue;
    }
}
