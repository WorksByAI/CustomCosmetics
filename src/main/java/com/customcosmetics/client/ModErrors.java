// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.client;

import com.customcosmetics.gui.UiText;
import net.minecraft.client.Minecraft;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Bounded, session-only isolation at recoverable mod-owned boundaries. Never catches VM failures. */
public final class ModErrors {
    public enum Key {
        STARTUP("Feature Startup"),GUI_OPEN("Interface"),UI_ACTION("Interface Actions"),PREVIEW("Player Preview"),
        CREATURE("Creature Model"),WORLD("World Detection"),CONFIG("Configuration"),SIZE("SmolPeople"),MAP("Map Editor"),
        FREECAM("Freecam"),SNAPLOOK("Snaplook"),DAMAGE("Damage Splash"),CAPE("Cape"),ITEMS("Item Customization"),
        SYNC("Remote Sync"),PARTY("Sharing"),UPDATES("Check for Updates"),BLOCKING("Sword Blocking"),
        REALTIME("Real-time Connection"),SKINS("Skins"),DEBUG("Debug"),ANIMATIONS("Animations"),BLOCKHIT("Blockhit"),
        APPEARANCE("Peer Appearance"),ACCESSORIES("Accessory Helper"),WAYPOINTS("Waypoints"),SHORTCUTS("Shortcuts"),COMMANDS("Commands");
        public final String label;Key(String label){this.label=label;}
    }
    private record Notice(Key key,String error){}
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("CustomCosmetics/Errors");
    private static final AtomicLong failed=new AtomicLong();
    private static final ConcurrentLinkedQueue<Notice> pending=new ConcurrentLinkedQueue<>();
    private static long nextNotice;
    private static boolean notificationsFailed;
    private ModErrors(){}
    public static boolean active(Key key){return (failed.get()&(1L<<key.ordinal()))==0;}
    public static void report(Key key,Throwable error){
        if(error instanceof VirtualMachineError fatal)throw fatal;if(error instanceof ThreadDeath fatal)throw fatal;
        long bit=1L<<key.ordinal();if((failed.getAndUpdate(value->value|bit)&bit)!=0)return;
        LOGGER.error("{} failed; the affected operation is paused for this session",key.label,error);
        pending.add(new Notice(key,error.getClass().getSimpleName()));
    }
    public static boolean attempt(Key key,Runnable action){try{action.run();return true;}catch(RuntimeException|LinkageError|AssertionError error){report(key,error);return false;}}
    public static void run(Key key,Runnable action){if(active(key))attempt(key,action);}
    public static void tick(Key key,Minecraft client,Consumer<Minecraft> action){if(!active(key))return;try{action.accept(client);}catch(RuntimeException|LinkageError|AssertionError error){report(key,error);}}
    public static void cleanup(Key key,Runnable action){try{action.run();}catch(RuntimeException|LinkageError|AssertionError error){report(key,error);}}
    /** A bad button/field is disabled individually; other UI actions remain available. */
    public static boolean action(Runnable action){return attempt(Key.UI_ACTION,action);}
    public static <T> boolean action(Consumer<T> action,T value){try{action.accept(value);return true;}catch(RuntimeException|LinkageError|AssertionError error){report(Key.UI_ACTION,error);return false;}}
    static String message(String details){return UiText.tr("An operation was paused after an error: ")+details+". "+UiText.tr("Please contact the mod author and include latest.log.");}
    public static void flush(Minecraft client){
        if(pending.isEmpty()||notificationsFailed||client.player==null)return;
        long now=System.nanoTime();if(now<nextNotice)return;nextNotice=now+5_000_000_000L;
        var details=new StringJoiner(", ");for(int i=0;i<3;i++){var notice=pending.poll();if(notice==null)break;details.add(UiText.tr(notice.key.label)+" ("+notice.error+")");}
        try{com.customcosmetics.compatibility.ClientUiAccess.chat(client).addClientSystemMessage(com.customcosmetics.utils.MessageUtils.createMessageWithPrefix(message(details.toString())));}
        catch(RuntimeException|LinkageError|AssertionError failure){notificationsFailed=true;LOGGER.error("Unable to display the mod error notice; see preceding errors",failure);}
    }
    static void resetForTests(){failed.set(0);pending.clear();nextNotice=0;notificationsFailed=false;}
    static int pendingCount(){return pending.size();}
}
