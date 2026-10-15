// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import com.customcosmetics.gui.PinkUi;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SyncDebugTest {
    @Test void debugDefaultsOffAndSamplesOnlyEveryFiveSeconds(){
        var session=new DebugSession();var calls=new AtomicInteger();var output=new ArrayList<String>();
        for(int i=0;i<10000;i++)session.tick(i,()->{calls.incrementAndGet();return "sample";},output::add);
        assertEquals(0,calls.get());session.enabled(true);
        session.tick(10000,()->{calls.incrementAndGet();return "sample";},output::add);
        session.tick(14999,()->{calls.incrementAndGet();return "too soon";},output::add);
        session.tick(15000,()->{calls.incrementAndGet();return "next";},output::add);
        session.enabled(false);session.tick(30000,()->{calls.incrementAndGet();return "disabled";},output::add);
        assertEquals(2,calls.get());assertEquals(List.of("sample","next"),output);
    }
    @Test void localStatusHasAPinkCopyActionWithTheCompleteReport(){
        var lines=List.of("Version: test","P2P: direct=0","Peer Alice: No authenticated probe reply");
        var message=SyncDebug.message(true,lines);var clicks=new ArrayList<ClickEvent>();
        message.visit((style,text)->{assertEquals(PinkUi.PINK&0xFFFFFF,style.getColor().getValue());if(style.getClickEvent()!=null)clicks.add(style.getClickEvent());return Optional.empty();},message.getStyle());
        assertEquals(List.of(new ClickEvent.CopyToClipboard(String.join("\n",lines))),clicks);
        assertTrue(message.getString().contains("Peer Alice: No authenticated probe reply"));
        assertTrue(message.getString().contains("logs/latest.log"));
    }
    @Test void samplingAndReportsCannotInitiateConnectionsOrSendGameCommands()throws Exception{
        for(String name:List.of("SyncDebug","DebugSession"))try(var input=getClass().getResourceAsStream("/com/customcosmetics/sync/"+name+".class")){
            var compiled=ClassFile.of().parse(input.readAllBytes());
            for(var method:compiled.methods())for(var instruction:method.code().map(code->code.elementStream().toList()).orElse(List.of()))if(instruction instanceof InvokeInstruction call){
                var owner=call.owner().name().stringValue();var called=call.name().stringValue();
                assertFalse(owner.startsWith("java/net/")||owner.startsWith("net/minecraft/client/multiplayer/"));
                assertFalse(Set.of("sendPacket","sendChat","sendCommand","openConnection","authenticatedSession","syncOnce").contains(called));
            }
        }
    }
}
