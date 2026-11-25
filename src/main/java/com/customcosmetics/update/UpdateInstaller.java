// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.update;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.security.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
/** Standalone JDK-only helper, copied out of the installed JAR before it is replaced. */
public final class UpdateInstaller {
    private UpdateInstaller(){}
    public static void main(String[] ignored){
        try(var in=new DataInputStream(System.in)){
            if(in.readInt()!=0x43435550)throw new IOException("Invalid plan");long pid=in.readLong();
            Path candidate=Path.of(in.readUTF()),target=Path.of(in.readUTF()),backup=Path.of(in.readUTF()),status=Path.of(in.readUTF());
            String expectedOld=in.readUTF(),expectedNew=in.readUTF();Path directory=Path.of(in.readUTF());int count=in.readInt();
            if(count<0||count>4096)throw new IOException("Invalid command");var command=new ArrayList<String>();for(int i=0;i<count;i++)command.add(in.readUTF());
            if(!digest(candidate).equals(expectedNew)||!digest(target).equals(expectedOld))throw new IOException("Changed artifact");
            writeStatus(status,"waiting_for_exit");System.out.println("READY");System.out.flush();
            // A manual launcher restart may happen much later. Only parent exit or
            // explicit cancellation may end this wait; never silently expire the plan.
            var parent=ProcessHandle.of(pid);if(parent.isPresent())parent.get().onExit().get();
            boolean installed=false;
            try{installWithRetry(candidate,target,backup,expectedOld,expectedNew,Duration.ofSeconds(30));installed=true;writeStatus(status,"installed");}
            catch(Exception failure){
                boolean preserved;try{preserved=digest(target).equals(expectedOld);}catch(IOException unavailable){preserved=false;}
                writeStatus(status,preserved?"installation_failed_original_preserved":"installation_failed_check_backup");
            }
            if(!command.isEmpty()){
                try{
                    var restarted=new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
                    restarted.getOutputStream().close();
                    if(restarted.waitFor(3,TimeUnit.SECONDS))writeStatus(status,(installed?"installed":"original")+"_restart_exited_"+restarted.exitValue());
                    else writeStatus(status,installed?"restarted":"original_restarted_after_install_failure");
                }
                catch(IOException failure){writeStatus(status,installed?"installed_restart_with_launcher":"installation_failed_restart_with_launcher");}
            }
        }catch(Exception failure){System.err.println("Update helper could not finish. The original mod backup is retained.");}
    }
    private static void writeStatus(Path path,String value){try{Files.writeString(path,value);}catch(IOException unavailable){/* Status persistence must not abort an otherwise valid install/restart. */}}
    public static String digest(Path path)throws IOException{
        try{var hash=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] bytes=new byte[65536];for(int n;(n=in.read(bytes))!=-1;)hash.update(bytes,0,n);}return HexFormat.of().formatHex(hash.digest());}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    static void installWithRetry(Path candidate,Path target,Path backup,String oldHash,String newHash,Duration timeout)throws IOException,InterruptedException{
        long deadline=System.nanoTime()+timeout.toNanos();
        while(true){
            try{install(candidate,target,backup,oldHash,newHash);return;}
            catch(FileSystemException busy){if(System.nanoTime()>=deadline)throw busy;Thread.sleep(200);}
        }
    }
    public static void install(Path candidate,Path target,Path backup,String oldHash,String newHash)throws IOException{
        if(!digest(candidate).equals(newHash)||!digest(target).equals(oldHash))throw new IOException("Changed artifact");
        Files.createDirectories(backup.getParent());Files.copy(target,backup,StandardCopyOption.REPLACE_EXISTING);
        if(!digest(backup).equals(oldHash)||!digest(target).equals(oldHash))throw new IOException("Installation changed during backup");
        Path temporary=target.resolveSibling(target.getFileName()+".cc-update");
        try{
            Files.copy(candidate,temporary,StandardCopyOption.REPLACE_EXISTING);if(!digest(temporary).equals(newHash))throw new IOException("Copy verification failed");
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException|FileAlreadyExistsException unavailable){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException failure){
            boolean original;try{original=digest(target).equals(oldHash);}catch(IOException missing){original=false;}
            if(!original)try{Files.copy(backup,target,StandardCopyOption.REPLACE_EXISTING);}catch(IOException rollback){failure.addSuppressed(rollback);}
            throw failure;
        }finally{try{Files.deleteIfExists(temporary);}catch(IOException cleanup){/* A leftover temporary must not turn a verified replacement into a reported failure. */}}
    }
}
