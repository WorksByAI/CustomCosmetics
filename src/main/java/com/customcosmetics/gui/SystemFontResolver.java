// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
/** Bounded, read-only font discovery. Never enumerates system fonts or runs during rendering. */
public final class SystemFontResolver {
    private SystemFontResolver(){}
    public static Optional<Path> find(String os,String windows,String local,String home,Predicate<Path> readable){
        var dirs=new ArrayList<Path>();
        if(os!=null&&os.toLowerCase(Locale.ROOT).startsWith("windows")){
            if(windows!=null&&!windows.isBlank())dirs.add(Path.of(windows,"Fonts"));
            if(local!=null&&!local.isBlank())dirs.add(Path.of(local,"Microsoft","Windows","Fonts"));
        }else if(home!=null&&!home.isBlank()){
            dirs.add(Path.of(home,".local","share","fonts"));dirs.add(Path.of(home,".fonts"));
        }
        for(Path dir:dirs)for(String file:List.of("msyhbd.ttc","msyh.ttc","msyh.ttf","MicrosoftYaHei.ttf")){
            Path candidate=dir.resolve(file);if(readable.test(candidate))return Optional.of(candidate);
        }
        return Optional.empty();
    }
    public static Optional<Path> installed(){return find(System.getProperty("os.name"),System.getenv("WINDIR"),System.getenv("LOCALAPPDATA"),System.getProperty("user.home"),p->Files.isRegularFile(p)&&Files.isReadable(p));}
}
