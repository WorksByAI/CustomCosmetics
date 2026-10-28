// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.update;
import java.io.*;
import java.nio.file.*;
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
            System.out.println("READY");System.out.flush();
            var parent=ProcessHandle.of(pid);if(parent.isPresent())parent.get().onExit().get(5,TimeUnit.MINUTES);
            boolean installed=false;
            try{install(candidate,target,backup,expectedOld,expectedNew);installed=true;Files.writeString(status,"installed");}
            catch(Exception failure){Files.writeString(status,"installation_failed_original_preserved");}
            if(!command.isEmpty()){
                try{new ProcessBuilder(command).directory(directory.toFile()).redirectInput(ProcessBuilder.Redirect.INHERIT).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();Files.writeString(status,installed?"restarted":"original_restarted_after_install_failure");}
                catch(IOException failure){Files.writeString(status,"installed_restart_with_launcher");}
            }
        }catch(Exception failure){System.err.println("Update helper could not finish. The original mod backup is retained.");}
    }
    public static String digest(Path path)throws IOException{
        try{var hash=MessageDigest.getInstance("SHA-256");try(var in=Files.newInputStream(path)){byte[] bytes=new byte[65536];for(int n;(n=in.read(bytes))!=-1;)hash.update(bytes,0,n);}return HexFormat.of().formatHex(hash.digest());}
        catch(NoSuchAlgorithmException impossible){throw new AssertionError(impossible);}
    }
    public static void install(Path candidate,Path target,Path backup,String oldHash,String newHash)throws IOException{
        if(!digest(candidate).equals(newHash)||!digest(target).equals(oldHash))throw new IOException("Changed artifact");
        Files.createDirectories(backup.getParent());Files.copy(target,backup,StandardCopyOption.REPLACE_EXISTING);
        Path temporary=target.resolveSibling(target.getFileName()+".cc-update");
        try{
            Files.copy(candidate,temporary,StandardCopyOption.REPLACE_EXISTING);if(!digest(temporary).equals(newHash))throw new IOException("Copy verification failed");
            try{Files.move(temporary,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unavailable){Files.move(temporary,target,StandardCopyOption.REPLACE_EXISTING);}
        }catch(IOException failure){
            boolean original;try{original=digest(target).equals(oldHash);}catch(IOException missing){original=false;}
            if(!original)try{Files.copy(backup,target,StandardCopyOption.REPLACE_EXISTING);}catch(IOException rollback){failure.addSuppressed(rollback);}
            throw failure;
        }finally{Files.deleteIfExists(temporary);}
    }
}
