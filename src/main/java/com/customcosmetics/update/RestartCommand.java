// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.update;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.*;
/** Launch credentials stay in process memory/stdin, never in an update plan, script or log. */
public final class RestartCommand {
    private RestartCommand(){}
    public static List<String> capture()throws IOException{
        var info=ProcessHandle.current().info();String executable=info.command().orElseThrow(()->new IOException("Launch command unavailable"));
        String prismMain=System.getProperty("org.prismlauncher.launch.mainclass");
        if(prismMain!=null)return direct(executable,ManagementFactory.getRuntimeMXBean().getInputArguments(),
            System.getProperty("java.class.path"),prismMain,
            Arrays.asList(System.getProperty("org.prismlauncher.launch.gameargs","").split("\u001f",-1)));
        List<String> result;
        if(org.lwjgl.system.Platform.get()==org.lwjgl.system.Platform.WINDOWS){
            long function=org.lwjgl.system.windows.Kernel32.getLibrary().getFunctionAddress("GetCommandLineW");
            if(function==0)throw new IOException("Launch command unavailable");
            result=new ArrayList<>(parseWindows(org.lwjgl.system.MemoryUtil.memUTF16(org.lwjgl.system.JNI.callP(function))));
            if(result.isEmpty())throw new IOException("Launch command unavailable");result.set(0,executable);
        }else{
            result=new ArrayList<>();result.add(executable);result.addAll(Arrays.asList(info.arguments().orElseThrow(()->new IOException("Launch arguments unavailable"))));
        }
        if(result.stream().anyMatch(arg->arg.startsWith("@"))){
            String main=System.getProperty("sun.java.command","").split(" ",2)[0];
            if(main.equals("net.fabricmc.loader.impl.launch.knot.KnotClient"))return direct(executable,
                ManagementFactory.getRuntimeMXBean().getInputArguments(),System.getProperty("java.class.path"),main,
                Arrays.asList(net.fabricmc.loader.api.FabricLoader.getInstance().getLaunchArguments(false)));
            result=expandArgumentFiles(result);
        }
        if(result.stream().anyMatch(arg->arg.equals("org.prismlauncher.EntryPoint")||arg.equals("org.multimc.EntryPoint")))
            throw new IOException("Launcher requires a new launch through its interface");
        return List.copyOf(result);
    }
    /** Prism's bootstrap consumes stdin once. Restart its published real entry point instead. */
    static List<String> direct(String executable,List<String> vmArgs,String classpath,String main,List<String> gameArgs)throws IOException{
        if(classpath==null||classpath.isBlank()||main==null||!main.matches("[A-Za-z_$][A-Za-z0-9_.$]*")
            ||main.equals("org.prismlauncher.EntryPoint")||main.equals("org.multimc.EntryPoint"))throw new IOException("Launch command unavailable");
        var result=new ArrayList<String>();result.add(executable);result.addAll(vmArgs);
        for(String key:List.of("java.library.path","org.lwjgl.librarypath","org.lwjgl.system.SharedLibraryExtractPath",
            "jna.boot.library.path","minecraft.launcher.brand","minecraft.launcher.version")){
            String value=System.getProperty(key);if(value!=null)result.add("-D"+key+"="+value);
        }
        result.add("-cp");result.add(classpath);result.add(main);result.addAll(gameArgs);return List.copyOf(result);
    }
    static List<String> prepare(List<String> command,java.nio.file.Path directory)throws IOException{
        try{return checked(command);}catch(IOException tooLong){
            // Only the classpath goes on disk. Game arguments and authentication data
            // stay in the helper's stdin even when CreateProcess needs a short command.
            var compact=new ArrayList<>(command);
            for(int i=1;i<compact.size()-1;i++){
                if(Set.of("-cp","-classpath","--class-path").contains(compact.get(i))){
                    String classpath=compact.get(i+1);
                    String escaped=classpath.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
                    var file=directory.resolve("restart-classpath.args").toAbsolutePath();
                    java.nio.file.Files.writeString(file,"-cp\n\""+escaped+"\"\n",java.nio.charset.Charset.defaultCharset());
                    compact.set(i,"@"+file);compact.remove(i+1);return checked(compact);
                }
            }
            throw tooLong;
        }
    }
    private static List<String> checked(List<String> command)throws IOException{
        // CreateProcess has a 32K command-line limit. Never discover it after exit.
        if(System.getProperty("os.name","").startsWith("Windows")&&command.stream()
            .mapToLong(arg->arg.length()+3L+arg.chars().filter(c->c=='\\'||c=='"').count()).sum()>32700)
            throw new IOException("Launch command requires the launcher");
        for(String arg:command){
            long bytes=arg.chars().mapToLong(c->c>=1&&c<=127?1:c<=2047?2:3).sum();
            if(bytes>65535)throw new IOException("Launch argument requires a classpath file or the launcher");
        }
        return List.copyOf(command);
    }
    /** Expand temporary Java launcher files in memory before the launcher removes them. */
    static List<String> expandArgumentFiles(List<String> command)throws IOException{
        var expanded=new ArrayList<String>();boolean launcher=true,disabled=false;int expecting=0;
        var valueOptions=Set.of("-cp","-classpath","--class-path","-p","--module-path","--upgrade-module-path",
            "--add-modules","--limit-modules","--add-exports","--add-opens","--add-reads","--patch-module","--enable-native-access","--source");
        for(String arg:command){
            if(expanded.isEmpty()){expanded.add(arg);continue;}
            List<String> tokens=List.of(arg);boolean fromFile=false;
            if(launcher&&!disabled&&arg.startsWith("@")&&!arg.startsWith("@@")&&arg.length()>1){
                try(var in=java.nio.file.Files.newInputStream(java.nio.file.Path.of(arg.substring(1)))){
                    byte[] bytes=in.readNBytes(1_048_577);if(bytes.length>1_048_576)throw new IOException("Launcher argument file too large");
                    tokens=parseArgumentFile(new String(bytes,java.nio.charset.Charset.defaultCharset()));fromFile=true;
                }
            }
            for(String token:tokens){
                // Java does not recursively expand @ tokens inside an argument file.
                expanded.add(fromFile&&launcher&&!disabled&&token.startsWith("@")?"@"+token:token);
                if(!launcher||disabled)continue;
                if(expecting==1){expecting=0;continue;}
                if(expecting==2){launcher=false;continue;}
                if(token.equals("--disable-@files"))disabled=true;
                else if(Set.of("-jar","-m","--module").contains(token))expecting=2;
                else if(valueOptions.contains(token))expecting=1;
                else if(!token.startsWith("-")||token.startsWith("--module="))launcher=false;
            }
        }
        return expanded;
    }
    static List<String> parseArgumentFile(String source)throws IOException{
        var result=new ArrayList<String>();var token=new StringBuilder();char quote=0;boolean present=false;
        for(int i=0;i<source.length();i++){
            char c=source.charAt(i);
            if(c=='\n'||c=='\r')quote=0;
            if(quote==0&&c=='#'){while(i<source.length()&&source.charAt(i)!='\n'&&source.charAt(i)!='\r')i++;c='\n';}
            if(quote==0&&" \t\n\r\f".indexOf(c)>=0){
                if(present){result.add(token.toString());token.setLength(0);present=false;}continue;
            }
            present=true;
            if(c=='\''||c=='"'){
                if(quote==0){quote=c;continue;}if(quote==c){quote=0;continue;}
            }
            if(quote!=0&&c=='\\'&&i+1<source.length()){
                c=source.charAt(++i);
                if(c=='\n'||c=='\r'){
                    while(i+1<source.length()&&" \t\n\r\f".indexOf(source.charAt(i+1))>=0)i++;
                    continue;
                }
                c=switch(c){case 'n'->'\n';case 'r'->'\r';case 't'->'\t';case 'f'->'\f';default->c;};
            }
            token.append(c);
        }
        if(present)result.add(token.toString());return result;
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
