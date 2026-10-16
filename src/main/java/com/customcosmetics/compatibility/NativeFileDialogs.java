// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;

import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.*;

/** Uses the native libraries supplied by Minecraft; selecting files never performs network I/O. */
public final class NativeFileDialogs {
    public record Request(boolean atlas,Path directory,boolean sdl,long window,String purpose) {
        public Request(boolean atlas,Path directory,boolean sdl,long window){this(atlas,directory,sdl,window,"");}
        public String title(){return "fme".equals(purpose)?"Import FME Config":"skin".equals(purpose)?"Choose Local Skin":atlas?"Choose Cape + Elytra":"Choose Image to Crop";}
        public String description(){return "fme".equals(purpose)?"FME JSON Config":"skin".equals(purpose)?"Skin PNG":atlas?"Cape PNG (*.png)":"Image files";}
        public String[] extensions(){return "fme".equals(purpose)?new String[]{"json"}:atlas?new String[]{"png"}:new String[]{"png","jpg","jpeg","bmp","gif"};}
    }
    public record Result(Path path,String error,boolean unavailable) {
        public static Result selected(String filename){if(filename==null||filename.isEmpty())return cancelled();try{return new Result(Path.of(filename).toAbsolutePath().normalize(),"",false);}catch(RuntimeException invalid){return new Result(null,"Invalid image path.",false);}}
        public static Result cancelled(){return new Result(null,"",false);}
        public static Result unavailableResult(){return new Result(null,"System file picker is unavailable.",true);}
    }
    @FunctionalInterface public interface Backend {void open(Request request,Consumer<Result> callback)throws Exception;}
    /** One dialog at a time; ignore duplicate callbacks and always return results on the UI queue. */
    public static final class Coordinator {
        private final Backend backend;private final AtomicBoolean busy=new AtomicBoolean();
        public Coordinator(Backend backend){this.backend=backend;}
        public boolean busy(){return busy.get();}
        public boolean open(Request request,Executor ui,Consumer<Result> callback){
            if(!busy.compareAndSet(false,true))return false;var completed=new AtomicBoolean();
            Consumer<Result> finish=result->{if(completed.compareAndSet(false,true)){busy.set(false);ui.execute(()->callback.accept(result));}};
            try{backend.open(request,finish);}catch(Exception|LinkageError unavailable){finish.accept(Result.unavailableResult());}return true;
        }
    }
    private static final ExecutorService TINY_WORKER=Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"CustomCosmetics file picker");thread.setDaemon(true);return thread;});
    private static final Coordinator COORDINATOR=new Coordinator(NativeFileDialogs::openBackend);
    private static final AtomicReference<SdlRequest> SDL_PENDING=new AtomicReference<>();
    // One permanent callback avoids freeing a native trampoline while it is still executing.
    private static Object sdlCallback;
    private NativeFileDialogs(){}
    public static boolean busy(){return COORDINATOR.busy();}
    public static boolean open(Request request,Executor ui,Consumer<Result> callback){return COORDINATOR.open(request,ui,callback);}
    private static void openBackend(Request request,Consumer<Result> callback)throws Exception{
        if(request.sdl)openSdl(request,callback);else TINY_WORKER.execute(()->{try{callback.accept(openTiny(request));}catch(Exception|LinkageError unavailable){callback.accept(Result.unavailableResult());}});
    }
    private static Result openTiny(Request request)throws ReflectiveOperationException{
        Class<?> tiny=Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
        try(var stack=MemoryStack.stackPush()){
            // Query the raw pointer API: the query returns pointer-sized 0/1, never a string.
            long graphic=(long)tiny.getMethod("ntinyfd_openFileDialog",long.class,long.class,int.class,long.class,long.class,int.class)
                .invoke(null,MemoryUtil.memAddress(stack.UTF8("tinyfd_query")),0L,0,0L,0L,0);
            if(graphic!=1)return Result.unavailableResult();
            String[] extensions=request.extensions();var filters=stack.mallocPointer(extensions.length*2);
            for(String extension:extensions){filters.put(stack.UTF8("*."+extension));filters.put(stack.UTF8("*."+extension.toUpperCase(java.util.Locale.ROOT)));}filters.flip();
            String selected=(String)tiny.getMethod("tinyfd_openFileDialog",CharSequence.class,CharSequence.class,PointerBuffer.class,CharSequence.class,boolean.class)
                .invoke(null,request.title(),request.directory.toString()+java.io.File.separator,filters,request.description(),false);
            return Result.selected(selected);
        }
    }
    private record SdlRequest(Object filters,ByteBuffer name,ByteBuffer patterns,Consumer<Result> callback,AtomicBoolean released){
        void close(){if(released.compareAndSet(false,true)){((NativeResource)filters).free();MemoryUtil.memFree(name);MemoryUtil.memFree(patterns);}}
    }
    private static synchronized Object sdlCallback(Class<?> callbackInterface)throws ReflectiveOperationException{
        if(sdlCallback!=null)return sdlCallback;
        Object handler=Proxy.newProxyInstance(callbackInterface.getClassLoader(),new Class<?>[]{callbackInterface},(proxy,method,args)->{
            if(method.getName().equals("invoke")){receiveSdl((long)args[1]);return null;}
            if(method.isDefault())return InvocationHandler.invokeDefault(proxy,method,args);
            return switch(method.getName()){case "hashCode"->System.identityHashCode(proxy);case "equals"->proxy==args[0];case "toString"->"CustomCosmetics file callback";default->throw new UnsupportedOperationException(method.getName());};
        });
        sdlCallback=Class.forName("org.lwjgl.sdl.SDL_DialogFileCallback").getMethod("create",callbackInterface).invoke(null,handler);return sdlCallback;
    }
    private static void openSdl(Request request,Consumer<Result> callback)throws ReflectiveOperationException{
        Class<?> filterClass=Class.forName("org.lwjgl.sdl.SDL_DialogFileFilter"),callbackInterface=Class.forName("org.lwjgl.sdl.SDL_DialogFileCallbackI"),bufferClass=Class.forName("org.lwjgl.sdl.SDL_DialogFileFilter$Buffer");
        Object nativeCallback=sdlCallback(callbackInterface),filters=filterClass.getMethod("malloc",int.class).invoke(null,1);
        ByteBuffer name=MemoryUtil.memUTF8(request.description()),patterns=MemoryUtil.memUTF8(String.join(";",request.extensions()));
        var pending=new SdlRequest(filters,name,patterns,callback,new AtomicBoolean());
        try{
            Object filter=bufferClass.getMethod("get",int.class).invoke(filters,0);filterClass.getMethod("name",ByteBuffer.class).invoke(filter,name);filterClass.getMethod("pattern",ByteBuffer.class).invoke(filter,patterns);
            if(!SDL_PENDING.compareAndSet(null,pending))throw new IllegalStateException("A file dialog is already open");
            Class.forName("org.lwjgl.sdl.SDLDialog").getMethod("SDL_ShowOpenFileDialog",callbackInterface,long.class,long.class,bufferClass,CharSequence.class,boolean.class)
                .invoke(null,nativeCallback,0L,request.window,filters,request.directory.toString(),false);
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){if(SDL_PENDING.compareAndSet(pending,null))pending.close();else if(SDL_PENDING.get()!=pending)pending.close();throw error;}
    }
    private static void receiveSdl(long fileList){
        var pending=SDL_PENDING.getAndSet(null);if(pending==null)return;Result result;
        try{if(fileList==0)result=Result.unavailableResult();else{long file=MemoryUtil.memGetAddress(fileList);result=file==0?Result.cancelled():Result.selected(MemoryUtil.memUTF8(file));}}
        catch(RuntimeException invalid){result=new Result(null,"Could not read the selected file.",false);}
        finally{pending.close();}
        pending.callback.accept(result);
    }
    /** Linkage probe only: this does not open a dialog or initialize a game window. */
    public static void validate(boolean sdl)throws ReflectiveOperationException{
        if(sdl){
            Class<?> callback=Class.forName("org.lwjgl.sdl.SDL_DialogFileCallbackI",false,NativeFileDialogs.class.getClassLoader()),buffer=Class.forName("org.lwjgl.sdl.SDL_DialogFileFilter$Buffer",false,NativeFileDialogs.class.getClassLoader());
            Class.forName("org.lwjgl.sdl.SDLDialog",false,NativeFileDialogs.class.getClassLoader()).getMethod("SDL_ShowOpenFileDialog",callback,long.class,long.class,buffer,CharSequence.class,boolean.class);
            buffer.getMethod("get",int.class);Class.forName("org.lwjgl.sdl.SDL_DialogFileCallback",false,NativeFileDialogs.class.getClassLoader()).getMethod("create",callback);
        }else {
            var tiny=Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs",false,NativeFileDialogs.class.getClassLoader());
            tiny.getMethod("tinyfd_openFileDialog",CharSequence.class,CharSequence.class,PointerBuffer.class,CharSequence.class,boolean.class);
            tiny.getMethod("ntinyfd_openFileDialog",long.class,long.class,int.class,long.class,long.class,int.class);
        }
    }
}
