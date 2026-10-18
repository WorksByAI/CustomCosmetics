package com.customcosmetics.sync;

import org.junit.jupiter.api.Test;
import static com.customcosmetics.sync.StartupSyncNotice.Operation.*;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitNoticeTest {
    private final RateLimitNotice notice = new RateLimitNotice();
    private final SyncFailure limited = SyncFailure.fromHttp(SyncFailure.Stage.DOWNLOAD, 429, "");

    @Test void occasionalLimitsStaySilentAndPersistentLimitsWarnOnce() {
        assertFalse(notice.complete(READ, false, limited));
        assertFalse(notice.complete(READ, false, limited));
        assertTrue(notice.complete(READ, false, limited));
        for (int i = 0; i < 20; i++) assertFalse(notice.complete(READ, false, limited));
    }

    @Test void successfulOtherOperationDoesNotResetFailedFetches() {
        assertFalse(notice.complete(READ, false, limited));
        assertFalse(notice.complete(WRITE, false, limited));
        assertFalse(notice.complete(WRITE, true, null));
        assertFalse(notice.complete(READ, false, limited));
        assertTrue(notice.complete(READ, false, limited));
        assertFalse(notice.complete(WRITE, false, limited));
    }

    @Test void recoveryAndEndpointResetStartAFreshFailureStreak() {
        for (int cycle = 0; cycle < 3; cycle++) {
            assertFalse(notice.complete(READ, false, limited));
            assertFalse(notice.complete(READ, false, limited));
            assertTrue(notice.complete(READ, false, limited));
            if (cycle == 0) notice.complete(READ, true, null);
            else notice.reset();
        }
    }

    @Test void nonRateLimitFailuresAreNotTreatedAsRateLimits() {
        var denied = SyncFailure.fromHttp(SyncFailure.Stage.DOWNLOAD, 403, "");
        assertFalse(denied.rateLimited());
        assertTrue(limited.rateLimited());
        assertTrue(SyncFailure.fromHttp(SyncFailure.Stage.DOWNLOAD, 400, "rate_limited").rateLimited());
        notice.complete(READ, false, limited);
        notice.complete(READ, false, limited);
        assertFalse(notice.complete(READ, false, denied));
        assertFalse(notice.complete(READ, false, limited));
    }

    @Test void deferredStartupCanRecoverInsteadOfReportingTheFirst429AsFailure() {
        var startup = new StartupSyncNotice();
        startup.enterWorld();
        var read = startup.beginAutomatic(READ);
        startup.complete(startup.beginAutomatic(WRITE), true, 1_000);
        assertFalse(notice.complete(READ, false, limited));
        startup.defer(read);
        assertNull(startup.poll(60_000));
        var retry = startup.beginAutomatic(READ);
        assertNotNull(retry);
        notice.complete(READ, true, null);
        startup.complete(retry, true, 61_000);
        assertEquals(new StartupSyncNotice.Result(true), startup.poll(64_000));
    }
}
