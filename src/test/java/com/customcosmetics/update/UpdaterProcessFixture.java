package com.customcosmetics.update;
import java.nio.file.*;
import java.util.jar.JarFile;
/** Test-owned subprocess; never launches Minecraft or touches an installed mod. */
public final class UpdaterProcessFixture {
    public static void main(String[] args)throws Exception{
        if(args[0].equals("fail"))System.exit(23);
        if(args[0].equals("prism")){
            System.setProperty("org.prismlauncher.launch.mainclass",UpdaterProcessFixture.class.getName());
            System.setProperty("org.prismlauncher.launch.gameargs","restart\u001f"+args[1]);
            var command=RestartCommand.capture();
            if(command.contains("prism"))System.exit(24); // Replaying the bootstrap cannot recover consumed stdin.
            var child=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            child.getOutputStream().close();if(!child.waitFor(10,java.util.concurrent.TimeUnit.SECONDS)){child.destroyForcibly();System.exit(25);}System.exit(child.exitValue());
        }
        if(args[0].equals("restart")){Files.writeString(Path.of(args[1]+".pid"),Long.toString(ProcessHandle.current().pid()));Files.writeString(Path.of(args[1]),"restart-ok");Thread.sleep(4500);return;}
        try(var jar=new JarFile(args[1])){System.out.println("READY");System.out.flush();System.in.read();}
    }
}
