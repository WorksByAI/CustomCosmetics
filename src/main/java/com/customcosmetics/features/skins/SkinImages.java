// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.skins;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
/** Validates before decoding and keeps source resolution. No skin website or account token is used. */
public final class SkinImages {
    public static final int MAX_BYTES=2*1024*1024,MAX_SIDE=1024;
    private SkinImages(){}
    public static byte[] read(Path path)throws IOException{if(!Files.isRegularFile(path)||Files.size(path)>MAX_BYTES)throw new IOException("Choose a PNG skin up to 2 MiB.");return normalize(Files.readAllBytes(path));}
    public static byte[] normalize(byte[] bytes)throws IOException{
        if(bytes.length<24||bytes.length>MAX_BYTES||bytes[0]!=(byte)137||bytes[1]!='P'||bytes[2]!='N'||bytes[3]!='G')throw new IOException("Use a PNG skin up to 2 MiB.");
        BufferedImage source;
        try(var stream=new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))){var readers=ImageIO.getImageReaders(stream);if(!readers.hasNext())throw new IOException("Invalid PNG skin.");var reader=readers.next();try{
            reader.setInput(stream,true,true);int w=reader.getWidth(0),h=reader.getHeight(0);boolean legacy=w==64&&h==32;
            if(!legacy&&(w<64||w>MAX_SIDE||(w&(w-1))!=0||h!=w))throw new IOException("Use 64x64-format skins (64, 128, 256, 512 or 1024 square), or legacy 64x32.");source=reader.read(0);
        }finally{reader.dispose();}}
        int side=source.getWidth();var image=new BufferedImage(side,side,BufferedImage.TYPE_INT_ARGB);image.setRGB(0,0,side,source.getHeight(),source.getRGB(0,0,side,source.getHeight(),null,0,side),0,side);
        if(source.getHeight()==32){mirrorLimb(image,source,0,16);mirrorLimb(image,source,40,32);boolean opaque=true;for(int y=0;y<16;y++)for(int x=32;x<64;x++)if((image.getRGB(x,y)>>>24)<128)opaque=false;if(opaque)for(int y=0;y<16;y++)for(int x=32;x<64;x++)image.setRGB(x,y,image.getRGB(x,y)&0xffffff);}
        // Vanilla base layers are opaque; outer layers retain transparency.
        int scale=side/64;opaque(image,0,0,32,16,scale);opaque(image,0,16,64,32,scale);opaque(image,16,48,48,64,scale);
        var output=new ByteArrayOutputStream();ImageIO.write(image,"PNG",output);byte[] result=output.toByteArray();if(result.length>MAX_BYTES)throw new IOException("Normalized skin exceeds 2 MiB.");return result;
    }
    private static void opaque(BufferedImage image,int x1,int y1,int x2,int y2,int scale){for(int y=y1*scale;y<y2*scale;y++)for(int x=x1*scale;x<x2*scale;x++)image.setRGB(x,y,image.getRGB(x,y)|0xff000000);}
    private static void mirrorLimb(BufferedImage image,BufferedImage source,int sourceU,int destinationU){
        mirror(image,source,sourceU+4,16,destinationU+4,48,4,4);mirror(image,source,sourceU+8,16,destinationU+8,48,4,4);
        int[] faces={8,4,0,12};for(int face=0;face<4;face++)mirror(image,source,sourceU+faces[face],20,destinationU+face*4,52,4,12);
    }
    private static void mirror(BufferedImage image,BufferedImage source,int sx,int sy,int dx,int dy,int width,int height){for(int y=0;y<height;y++)for(int x=0;x<width;x++)image.setRGB(dx+x,dy+y,source.getRGB(sx+width-1-x,sy+y));}
}
