// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeFileDialogsTest {
    private static final NativeFileDialogs.Request REQUEST=new NativeFileDialogs.Request(false,Path.of("."),false,0);
    @Test void onlyOneDialogOpensAndResultsWaitForTheUiQueue(){
        var pending=new AtomicReference<Consumer<NativeFileDialogs.Result>>();var opens=new AtomicInteger();var queue=new ArrayList<Runnable>();var results=new ArrayList<NativeFileDialogs.Result>();
        var dialogs=new NativeFileDialogs.Coordinator((request,callback)->{opens.incrementAndGet();pending.set(callback);});
        assertTrue(dialogs.open(REQUEST,queue::add,results::add));assertTrue(dialogs.busy());assertFalse(dialogs.open(REQUEST,queue::add,results::add));assertEquals(1,opens.get());
        pending.get().accept(NativeFileDialogs.Result.selected("暮冬披风.png"));pending.get().accept(NativeFileDialogs.Result.cancelled());assertFalse(dialogs.busy());assertTrue(results.isEmpty());assertEquals(1,queue.size());queue.getFirst().run();assertEquals("暮冬披风.png",results.getFirst().path().getFileName().toString());
    }
    @Test void cancelDoesNotSelectAFileOrBecomeAnUnavailablePicker(){var result=NativeFileDialogs.Result.selected(null);assertNull(result.path());assertEquals("",result.error());assertFalse(result.unavailable());assertEquals(result,NativeFileDialogs.Result.selected(""));}
    @Test void nativeLinkageFailureAllowsTheLocalFallbackWithoutLeavingBusyState(){
        var results=new ArrayList<NativeFileDialogs.Result>();var dialogs=new NativeFileDialogs.Coordinator((request,callback)->{throw new UnsatisfiedLinkError("fixture");});
        assertTrue(dialogs.open(REQUEST,Runnable::run,results::add));assertFalse(dialogs.busy());assertTrue(results.getFirst().unavailable());assertNull(results.getFirst().path());assertTrue(dialogs.open(REQUEST,Runnable::run,results::add));assertEquals(2,results.size());
    }
    @Test void imagesAndAtlasesUseDifferentFiltersAndDisableMultipleSelectionByContract(){assertArrayEquals(new String[]{"png","jpg","jpeg","bmp","gif"},REQUEST.extensions());assertArrayEquals(new String[]{"png"},new NativeFileDialogs.Request(true,Path.of("."),false,0).extensions());assertEquals("Choose Image to Crop",REQUEST.title());}
    @Test void nonexistentOrMalformedPathsStayLocalAndAreNotInterpretedAsUrls(){assertNull(NativeFileDialogs.Result.selected("bad\u0000image.png").path());assertEquals(Path.of("folder with spaces/../披风.png").toAbsolutePath().normalize(),NativeFileDialogs.Result.selected("folder with spaces/../披风.png").path());}
}
