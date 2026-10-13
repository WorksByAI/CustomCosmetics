// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.cape;
import java.io.IOException;
import java.nio.file.*;

/** This file is never read by an automatic upload. Explicit review submission takes a copy. */
public final class LocalCapeStore {
    private final Path path;
    private byte[] png;
    private String hash;
    public LocalCapeStore(Path directory)throws IOException{
        Files.createDirectories(directory);path=directory.resolve("cape-local.png");
        if(Files.exists(path)){try{png=CapeImages.atlasPng(CapeImages.read(path));}catch(IOException ignored){png=null;}}
        hash=png==null?null:CapeImages.hash(png);
    }
    public void save(byte[] bytes)throws IOException{
        var normalized=CapeImages.atlasPng(CapeImages.decode(bytes));
        var temp=path.resolveSibling("cape-local.png.tmp");Files.write(temp,normalized);
        try{Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
        catch(AtomicMoveNotSupportedException e){Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING);}
        png=normalized;hash=CapeImages.hash(png);
    }
    public byte[] copy(){return png==null?null:png.clone();}
    public String hash(){return hash;}
    public void clear()throws IOException{Files.deleteIfExists(path);png=null;hash=null;}
}
