// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.update;
import java.io.IOException;
import java.util.*;
/** Launch credentials stay in process memory/stdin, never in an update plan, script or log. */
public final class RestartCommand {
    private RestartCommand(){}
    public static List<String> capture()throws IOException{
        var info=ProcessHandle.current().info();String executable=info.command().orElseThrow(()->new IOException("Launch command unavailable"));
        if(org.lwjgl.system.Platform.get()==org.lwjgl.system.Platform.WINDOWS){
            long function=org.lwjgl.system.windows.Kernel32.getLibrary().getFunctionAddress("GetCommandLineW");
            if(function==0)throw new IOException("Launch command unavailable");
            var result=new ArrayList<>(parseWindows(org.lwjgl.system.MemoryUtil.memUTF16(org.lwjgl.system.JNI.callP(function))));
            if(result.isEmpty())throw new IOException("Launch command unavailable");result.set(0,executable);return List.copyOf(result);
        }
        var result=new ArrayList<String>();result.add(executable);result.addAll(Arrays.asList(info.arguments().orElseThrow(()->new IOException("Launch arguments unavailable"))));return List.copyOf(result);
    }
    public static List<String> parseWindows(String command){
        var args=new ArrayList<String>();int i=0;
        while(i<command.length()){
            while(i<command.length()&&Character.isWhitespace(command.charAt(i)))i++;if(i==command.length())break;
            var arg=new StringBuilder();boolean quoted=false;
            while(i<command.length()){
                if(!quoted&&Character.isWhitespace(command.charAt(i)))break;
                int slashes=0;while(i<command.length()&&command.charAt(i)=='\\'){slashes++;i++;}
                if(i<command.length()&&command.charAt(i)=='"'){
                    arg.append("\\".repeat(slashes/2));
                    if(slashes%2==1)arg.append('"');else if(quoted&&i+1<command.length()&&command.charAt(i+1)=='"'){arg.append('"');i++;}else quoted=!quoted;i++;
                }else{arg.append("\\".repeat(slashes));if(i<command.length()&&(quoted||!Character.isWhitespace(command.charAt(i))))arg.append(command.charAt(i++));else break;}
            }
            args.add(arg.toString());
        }
        return List.copyOf(args);
    }
}
