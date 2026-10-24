package com.customcosmetics.features.nickname;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

/** The same workload is compiled against the released 1.8.4 class to produce the golden digest. */
final class GradientGoldenWorkload {
    static String fingerprint() throws Exception {
        var digest=MessageDigest.getInstance("SHA-256");var random=new Random(0xCC0184);
        var styles=new String[]{"character","smooth","segmented","shimmer",null};
        double[] speeds={0,.25,1,4,Double.NaN,Double.POSITIVE_INFINITY,.000001};
        long[] times={0,704,1350,3000,-1,Long.MAX_VALUE,Long.MIN_VALUE,704};
        double[] coordinates={-.5,-0.0,0,.1,.5,.9,1,1.5,Double.NaN,Double.POSITIVE_INFINITY};
        var palettes=new NicknameGradient[300];
        for(int i=0;i<palettes.length;i++)palettes[i]=new NicknameGradient(styles[i%styles.length],random.nextInt(),random.nextInt(),speeds[i%speeds.length],random.nextInt(),random.nextInt(),i%2==0?.16:.8,i%3==0?0:2);
        for(int round=0;round<3;round++)for(int i=0;i<palettes.length;i++){
            var original=palettes[i];
            var paint=round==1?new NicknameGradient(original.style(),original.start(),original.end(),original.speed(),original.shimmerColor(),original.shimmerHighlightColor(),original.shimmerSize(),original.shimmerInterval()):original;
            for(long time:times)for(int j=0;j<40;j++){
                double x=j<coordinates.length?coordinates[j]:random.nextDouble();double y=coordinates[j%coordinates.length];
                // Multiple calls at the same clock value exercise the prepared-paint path.
                update(digest,paint.color(x,y,time));update(digest,paint.color(x,y,time));
            }
            // More exact positions than the per-palette bound exercise eviction without quantizing colors.
            for(int j=0;j<520;j++)update(digest,paint.color(j/519.0,.5,704));
        }
        return HexFormat.of().formatHex(digest.digest());
    }
    private static void update(MessageDigest digest,int color){digest.update((byte)(color>>>24));digest.update((byte)(color>>>16));digest.update((byte)(color>>>8));digest.update((byte)color);}
    public static void main(String[] args)throws Exception{System.out.println(fingerprint());}
}
