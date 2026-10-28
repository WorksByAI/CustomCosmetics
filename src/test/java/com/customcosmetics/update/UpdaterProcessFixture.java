package com.customcosmetics.update;
import java.nio.file.*;
import java.util.jar.JarFile;
/** Test-owned subprocess; never launches Minecraft or touches an installed mod. */
public final class UpdaterProcessFixture {
    public static void main(String[] args)throws Exception{
        if(args[0].equals("restart")){Files.writeString(Path.of(args[1]+".pid"),Long.toString(ProcessHandle.current().pid()));Files.writeString(Path.of(args[1]),"restart-ok");return;}
        try(var jar=new JarFile(args[1])){System.out.println("READY");System.out.flush();System.in.read();}
    }
}
