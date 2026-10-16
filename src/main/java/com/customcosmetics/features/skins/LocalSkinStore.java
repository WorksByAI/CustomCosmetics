// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.skins;
import java.nio.file.*;
import java.io.IOException;
public final class LocalSkinStore {
    private final Path file;
    public LocalSkinStore(Path directory){file=directory.resolve("local.png");}
    public byte[] load()throws IOException{return Files.isRegularFile(file)?SkinImages.read(file):null;}
    public byte[] save(byte[] bytes)throws IOException{var png=SkinImages.normalize(bytes);Files.createDirectories(file.getParent());var temp=file.resolveSibling("local.png.tmp");Files.write(temp,png);try{Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException unsupported){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}return png;}
    public void clear()throws IOException{Files.deleteIfExists(file);}
}
