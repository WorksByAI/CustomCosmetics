package com.customcosmetics.update;
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class UpdateInstallerProcessTest {
    @TempDir Path temporary;
    private static String java(){return Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();}
    private static void jar(Path path,String value)throws Exception{try(var zip=new ZipOutputStream(Files.newOutputStream(path))){zip.putNextEntry(new ZipEntry("version.txt"));zip.write(value.getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}
    @Test void standaloneHelperWaitsForTheJarOwnerThenReplacesAndRestartsWithoutWritingLaunchArguments()throws Exception{
        Path current=temporary.resolve("模组 old.jar"),candidate=temporary.resolve("new.jar"),backup=temporary.resolve("backup/old.jar.backup"),status=temporary.resolve("status"),marker=temporary.resolve("restart-marker");
        jar(current,"old");jar(candidate,"new");String old=UpdateInstaller.digest(current),next=UpdateInstaller.digest(candidate);
        String fixturePath=Path.of(UpdaterProcessFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        var owner=new ProcessBuilder(java(),"-cp",fixturePath,UpdaterProcessFixture.class.getName(),"hold",current.toString()).start();
        assertEquals("READY",new BufferedReader(new InputStreamReader(owner.getInputStream())).readLine());
        Path helperRoot=temporary.resolve("helper"),clazz=helperRoot.resolve("com/customcosmetics/update/UpdateInstaller.class");Files.createDirectories(clazz.getParent());
        try(var in=UpdateInstaller.class.getResourceAsStream("UpdateInstaller.class")){assertNotNull(in);Files.copy(in,clazz);}
        var helper=new ProcessBuilder(java(),"-cp",helperRoot.toString(),UpdateInstaller.class.getName()).start();
        String ephemeral=UUID.randomUUID().toString();
        try{
            try(var out=new DataOutputStream(helper.getOutputStream())){
                // Use the durable test class directory: Windows can retain a process CWD
                // handle briefly after exit, independently of the JAR replacement check.
                out.writeInt(0x43435550);out.writeLong(owner.pid());out.writeUTF(candidate.toString());out.writeUTF(current.toString());out.writeUTF(backup.toString());out.writeUTF(status.toString());out.writeUTF(old);out.writeUTF(next);out.writeUTF(fixturePath);
                var command=List.of(java(),"-Dprivate-fixture="+ephemeral,"-cp",fixturePath,UpdaterProcessFixture.class.getName(),"restart",marker.toString());out.writeInt(command.size());for(String arg:command)out.writeUTF(arg);
            }
            assertEquals("READY",new BufferedReader(new InputStreamReader(helper.getInputStream())).readLine());
            assertEquals(old,UpdateInstaller.digest(current));assertFalse(Files.exists(backup));
            owner.getOutputStream().close();assertTrue(owner.waitFor(10,TimeUnit.SECONDS));assertTrue(helper.waitFor(10,TimeUnit.SECONDS));assertEquals(0,helper.exitValue());
            assertEquals(next,UpdateInstaller.digest(current));assertEquals(old,UpdateInstaller.digest(backup));assertEquals("restarted",Files.readString(status));
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);while((!Files.exists(marker)||!Files.readString(marker).equals("restart-ok"))&&System.nanoTime()<until)Thread.sleep(10);assertEquals("restart-ok",Files.readString(marker));
            var restarted=ProcessHandle.of(Long.parseLong(Files.readString(Path.of(marker+".pid"))));if(restarted.isPresent())restarted.get().onExit().get(10,TimeUnit.SECONDS);
            try(var files=Files.walk(temporary)){for(var file:files.filter(Files::isRegularFile).toList())assertFalse(new String(Files.readAllBytes(file),StandardCharsets.ISO_8859_1).contains(ephemeral),"Launch argument persisted to disk");}
        }finally{owner.destroyForcibly();helper.destroyForcibly();}
    }
    @Test void windowsSharingViolationIsRetriedUntilTheOtherJarHandleCloses()throws Exception{
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        Path current=temporary.resolve("locked.jar"),candidate=temporary.resolve("new.jar"),backup=temporary.resolve("old.jar.backup");
        jar(current,"old");jar(candidate,"new");String old=UpdateInstaller.digest(current),next=UpdateInstaller.digest(candidate);
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()){
            java.util.concurrent.Future<?> replacement;
            try(var lock=java.nio.channels.FileChannel.open(current,StandardOpenOption.READ,com.sun.nio.file.ExtendedOpenOption.NOSHARE_DELETE)){
                replacement=executor.submit(()->{try{UpdateInstaller.installWithRetry(candidate,current,backup,old,next,java.time.Duration.ofSeconds(5));}catch(Exception e){throw new java.util.concurrent.CompletionException(e);}});
                Thread.sleep(400);assertFalse(replacement.isDone(),"A transient sharing violation must be retried");assertEquals(old,UpdateInstaller.digest(current));
            }
            replacement.get(5,TimeUnit.SECONDS);
        }
        assertEquals(next,UpdateInstaller.digest(current));assertEquals(old,UpdateInstaller.digest(backup));
    }
    @Test void anImmediatelyFailingRestartCannotBeReportedAsASuccessfulGameRestart()throws Exception{
        Path current=temporary.resolve("current.jar"),candidate=temporary.resolve("candidate.jar"),backup=temporary.resolve("backup.jar"),status=temporary.resolve("status");
        jar(current,"old");jar(candidate,"new");
        String mainPath=Path.of(UpdateInstaller.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        String fixturePath=Path.of(UpdaterProcessFixture.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        var helper=new ProcessBuilder(java(),"-cp",mainPath,UpdateInstaller.class.getName()).start();
        try{
            try(var out=new DataOutputStream(helper.getOutputStream())){
                out.writeInt(0x43435550);out.writeLong(Long.MAX_VALUE);out.writeUTF(candidate.toString());out.writeUTF(current.toString());out.writeUTF(backup.toString());out.writeUTF(status.toString());
                out.writeUTF(UpdateInstaller.digest(current));out.writeUTF(UpdateInstaller.digest(candidate));out.writeUTF(fixturePath);
                var command=List.of(java(),"-cp",fixturePath,UpdaterProcessFixture.class.getName(),"fail");out.writeInt(command.size());for(String arg:command)out.writeUTF(arg);
            }
            assertEquals("READY",new BufferedReader(new InputStreamReader(helper.getInputStream())).readLine());
            assertTrue(helper.waitFor(10,TimeUnit.SECONDS));assertEquals(UpdateInstaller.digest(candidate),UpdateInstaller.digest(current));
            assertEquals("installed_restart_exited_23",Files.readString(status));
        }finally{helper.destroyForcibly();}
    }
}
