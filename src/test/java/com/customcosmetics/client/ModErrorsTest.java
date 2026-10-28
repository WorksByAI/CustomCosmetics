package com.customcosmetics.client;
import com.customcosmetics.gui.UiText;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
class ModErrorsTest {
    @AfterEach void reset(){ModErrors.resetForTests();UiText.configure("auto","en_us");}
    @Test void failingCallbacksAreIsolatedAndOnlyQueuedOnce(){
        var count=new int[1];Runnable bad=()->{count[0]++;throw new IllegalArgumentException("intentional test failure");};
        ModErrors.run(ModErrors.Key.CONFIG,bad);ModErrors.run(ModErrors.Key.CONFIG,bad);
        assertEquals(1,count[0]);assertEquals(1,ModErrors.pendingCount());assertFalse(ModErrors.active(ModErrors.Key.CONFIG));
        ModErrors.run(ModErrors.Key.ITEMS,()->count[0]++);assertEquals(2,count[0]);
        assertFalse(ModErrors.action(bad));assertTrue(ModErrors.action(()->count[0]++));
    }
    @Test void virtualMachineFailuresRemainFatalAndBothLanguagesHaveActionableNotices(){
        assertThrows(OutOfMemoryError.class,()->ModErrors.run(ModErrors.Key.ITEMS,()->{throw new OutOfMemoryError("simulated, no allocation");}));
        assertTrue(ModErrors.active(ModErrors.Key.ITEMS));
        UiText.configure("zh_cn","en_us");assertTrue(ModErrors.message("test").contains("请联系模组作者"));
        UiText.configure("en_us","en_us");assertTrue(ModErrors.message("test").contains("Please contact the mod author"));
    }
}
