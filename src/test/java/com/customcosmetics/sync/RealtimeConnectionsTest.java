// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.customcosmetics.sync.RealtimeConnections.Path.*;

class RealtimeConnectionsTest {
    private final UUID a=UUID.randomUUID(),b=UUID.randomUUID(),c=UUID.randomUUID();
    @Test void standbyConnectionsAndZeroCountsAreHidden(){
        var routes=new RealtimeConnections();
        assertEquals("Connecting",routes.status(false,Set.of(a),0));
        assertEquals("Connected",routes.status(true,Set.of(),0));
        assertEquals("Connected\nP2P: 1",routes.status(true,Set.of(a),0));
    }
    @Test void mixedRoutesCountPlayersOnceAndSelectedDirectHidesWarmRelays(){
        var routes=new RealtimeConnections();
        routes.received(a,"position",UDP,100);routes.received(a,"blocking",TCP,100);
        routes.received(b,"position",UDP,100);routes.received(b,"blocking",UDP,100);
        routes.received(c,"position",TCP,100);
        assertEquals("Connected\nP2P: 1\nUDP: 1\nTCP: 1",routes.status(true,Set.of(a),0));
    }
    @Test void pathChangesExpiryAndStoppedBlockingRemoveObsoleteCounts(){
        var routes=new RealtimeConnections();routes.received(a,"position",UDP,100);
        routes.received(a,"position",P2P,100);
        assertEquals("Connected\nP2P: 1",routes.status(true,Set.of(a),0));
        assertEquals("Connected",routes.status(true,Set.of(),0));
        routes.received(a,"position",TCP,100);
        assertEquals("Connected\nTCP: 1",routes.status(true,Set.of(),0));
        assertEquals("Connected",routes.status(true,Set.of(),100));
        routes.received(a,"blocking",UDP,200);routes.remove(a,"blocking");
        assertEquals("Connected",routes.status(true,Set.of(),100));
    }
    @Test void leavingInterestAndWorldCannotKeepOldConnectionCounts(){
        var routes=new RealtimeConnections();routes.received(a,"blocking",UDP,100);
        routes.received(b,"position",TCP,100);routes.retainBlocking(Set.of());
        assertEquals("Connected\nTCP: 1",routes.status(true,Set.of(),0));
        routes.clear();assertEquals("Connected",routes.status(true,Set.of(),0));
    }
}
