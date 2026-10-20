package com.customcosmetics.gui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ScreenSessionTest {
    @Test void firstOpenStartsAtTheOverview() { assertEquals(new ScreenSession.Position("MAIN",0),new ScreenSession().open(100)); }
    @Test void reopeningBeforeOneMinuteKeepsThePageAndScroll() {
        var session = new ScreenSession(); session.open(0); session.interact("ANIMATIONS",120,1000); session.remember("ANIMATIONS",144);
        assertEquals(new ScreenSession.Position("ANIMATIONS",144),session.open(60_999));
    }
    @Test void oneMinuteWithoutInteractionResetsEvenIfTheGuiWasLeftOpen() {
        var session = new ScreenSession(); session.open(0); session.interact("SYNC",300,1000); session.remember("SYNC",300);
        assertEquals(new ScreenSession.Position("MAIN",0),session.open(61_000));
    }
    @Test void interactionExtendsTheTimerWhileClosingDoesNot() {
        var session = new ScreenSession(); session.open(0); session.interact("NICK",25,30_000); session.remember("NICK",25);
        assertEquals("NICK",session.open(89_999).page()); session.remember("NICK",25);
        assertEquals("MAIN",session.open(149_999).page());
    }
}
