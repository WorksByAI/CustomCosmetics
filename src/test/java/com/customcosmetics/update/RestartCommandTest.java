package com.customcosmetics.update;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RestartCommandTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void quotedPathsUnicodeEmptyArgumentsAndEscapedQuotesRoundTrip(){
        assertEquals(List.of("C:\\Program Files\\Java\\javaw.exe","-cp","C:\\MC Mods\\libs","Main","--gameDir","D:\\我的 游戏","","say \"hello\""),
            RestartCommand.parseWindows("\"C:\\Program Files\\Java\\javaw.exe\" -cp \"C:\\MC Mods\\libs\" Main --gameDir \"D:\\我的 游戏\" \"\" \"say \\\"hello\\\"\""));
    }
    @Test void actualJvmLaunchCanBeCapturedWithoutPersistingIt()throws Exception{
        var command=RestartCommand.capture();assertFalse(command.isEmpty());assertTrue(command.getFirst().toLowerCase(Locale.ROOT).contains("java"));
    }
    @Test void temporaryJavaArgumentFilesAreExpandedBeforeTheirLauncherDeletesThem()throws Exception{
        var file=directory.resolve("launch args.txt");
        java.nio.file.Files.writeString(file,"# launch options\n-cp \"C:\\\\MC Mods\\\\libraries\"\nMain --gameDir 'D:/我的 游戏' \"\" \"say \\\"hello\\\"\"\n");
        var command=RestartCommand.expandArgumentFiles(List.of("java","@"+file));java.nio.file.Files.delete(file);
        assertEquals(List.of("java","-cp","C:\\MC Mods\\libraries","Main","--gameDir","D:/我的 游戏","","say \"hello\""),command);
        assertEquals(List.of("java","--disable-@files","@missing"),RestartCommand.expandArgumentFiles(List.of("java","--disable-@files","@missing")));
        assertEquals(List.of("joinedtext"),RestartCommand.parseArgumentFile("\"joined\\\n    text\""));
        assertEquals(List.of("partial"),RestartCommand.parseArgumentFile("\"partial"));
        assertEquals(List.of("first","second"),RestartCommand.parseArgumentFile("\"first\nsecond"));
        assertEquals(List.of("java","Main","@missing"),RestartCommand.expandArgumentFiles(List.of("java","Main","@missing")));
        assertEquals(List.of("java","@@literal"),RestartCommand.expandArgumentFiles(List.of("java","@@literal")));
    }
    @Test void prismRestartUsesTheRealMainAndGameArgumentsInsteadOfReplayingItsStdinBootstrap()throws Exception{
        String executable=java.nio.file.Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        var marker=directory.resolve("重启 marker");
        String classpath=java.nio.file.Path.of(UpdaterProcessFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI())
            +java.io.File.pathSeparator+java.nio.file.Path.of(RestartCommand.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var process=new ProcessBuilder(executable,"-cp",classpath,UpdaterProcessFixture.class.getName(),"prism",marker.toString())
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try{
            process.getOutputStream().close();assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue());
            assertEquals("restart-ok",java.nio.file.Files.readString(marker));
        }finally{process.destroyForcibly();}
    }
    @Test void longWindowsClasspathsUseAPersistentClasspathFileWithoutWritingGameCredentials()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        String executable=java.nio.file.Path.of(System.getProperty("java.home"),"bin","java.exe").toString();
        String fixture=java.nio.file.Path.of(UpdaterProcessFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        String classpath=String.join(java.io.File.pathSeparator,Collections.nCopies(500,fixture));
        while(classpath.length()<35000)classpath+=java.io.File.pathSeparator+classpath;
        String token=UUID.randomUUID().toString();var marker=directory.resolve("long-classpath-marker");
        var command=RestartCommand.prepare(List.of(executable,"-cp",classpath,UpdaterProcessFixture.class.getName(),"restart",marker.toString(),"--accessToken",token),directory);
        assertTrue(command.get(1).startsWith("@"));
        var file=java.nio.file.Path.of(command.get(1).substring(1));assertFalse(java.nio.file.Files.readString(file).contains(token));
        var process=new ProcessBuilder(command).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        try{process.getOutputStream().close();assertTrue(process.waitFor(15,java.util.concurrent.TimeUnit.SECONDS));assertEquals(0,process.exitValue());assertEquals("restart-ok",java.nio.file.Files.readString(marker));}
        finally{process.destroyForcibly();}
    }
}
