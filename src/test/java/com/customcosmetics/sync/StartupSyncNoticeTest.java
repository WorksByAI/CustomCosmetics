package com.customcosmetics.sync;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static com.customcosmetics.sync.StartupSyncNotice.Operation.*;
import static org.junit.jupiter.api.Assertions.*;

class StartupSyncNoticeTest {
    private final StartupSyncNotice notice = new StartupSyncNotice();

    @BeforeEach
    void enterWorld() { notice.enterWorld(); }

    @ParameterizedTest
    @EnumSource(StartupSyncNotice.Operation.class)
    void firstReadOrWriteFailureIsDeliveredOnlyOnce(StartupSyncNotice.Operation operation) {
        var ticket = notice.beginAutomatic(operation);
        notice.complete(ticket, false, 1_000);
        assertNull(notice.poll(3_999));
        assertEquals(new StartupSyncNotice.Result(false), notice.poll(4_000));
        assertNull(notice.poll(100_000));
        assertNull(notice.beginAutomatic(READ));
        assertNull(notice.beginAutomatic(WRITE));
    }

    @Test
    void firstSyncNeedsBothDownloadAndUploadToSucceed() {
        var read = notice.beginAutomatic(READ);
        var write = notice.beginAutomatic(WRITE);
        notice.complete(read, true, 1_000);
        assertNull(notice.poll(100_000));
        notice.complete(write, true, 101_000);
        assertNull(notice.poll(103_999));
        assertEquals(new StartupSyncNotice.Result(true), notice.poll(104_000));
        notice.complete(write, false, 105_000);
        assertNull(notice.poll(110_000));
    }

    @Test
    void uploadFailureStillCountsAfterSuccessfulDownload() {
        var read = notice.beginAutomatic(READ);
        var write = notice.beginAutomatic(WRITE);
        notice.complete(read, true, 1_000);
        notice.complete(write, false, 2_000);
        assertEquals(new StartupSyncNotice.Result(false), notice.poll(5_000));
    }

    @Test
    void laterCompletionCannotOverwriteTheFirstFailure() {
        var read = notice.beginAutomatic(READ);
        var write = notice.beginAutomatic(WRITE);
        notice.complete(read, false, 1_000);
        notice.complete(write, true, 2_000);
        assertEquals(new StartupSyncNotice.Result(false), notice.poll(4_000));
    }

    @Test
    void manualRequestsWithoutAnAutomaticTicketDoNotTriggerStartupWarnings() {
        notice.complete(null, false, 1_000);
        assertNull(notice.poll(10_000));
        var read = notice.beginAutomatic(READ);
        var write = notice.beginAutomatic(WRITE);
        notice.complete(read, true, 11_000);
        notice.complete(write, true, 11_000);
        assertEquals(new StartupSyncNotice.Result(true), notice.poll(14_000));
    }

    @Test
    void reconnectIgnoresOldRequestsAndCanShowOneNewFailure() {
        var oldRead = notice.beginAutomatic(READ);
        notice.complete(oldRead, false, 1_000);
        notice.leaveWorld();
        assertNull(notice.poll(10_000));
        notice.enterWorld();
        notice.complete(oldRead, false, 11_000);
        assertNull(notice.poll(14_000));
        var newRead = notice.beginAutomatic(READ);
        notice.complete(newRead, false, 15_000);
        assertEquals(new StartupSyncNotice.Result(false), notice.poll(18_000));
        assertNull(notice.poll(30_000));
    }

    @Test
    void addressChangeRejectsStaleCallbacksBeforeStartupFinishes() {
        var oldRead = notice.beginAutomatic(READ);
        var oldWrite = notice.beginAutomatic(WRITE);
        notice.addressChanged();
        notice.complete(oldRead, false, 1_000);
        notice.complete(oldWrite, false, 1_000);
        assertNull(notice.poll(10_000));
        notice.complete(notice.beginAutomatic(READ), true, 11_000);
        notice.complete(notice.beginAutomatic(WRITE), true, 11_000);
        assertEquals(new StartupSyncNotice.Result(true), notice.poll(14_000));
    }

    @Test
    void addressChangesCannotRepeatAnAlreadyDeliveredWarning() {
        notice.complete(notice.beginAutomatic(READ), false, 1_000);
        assertEquals(new StartupSyncNotice.Result(false), notice.poll(4_000));
        notice.addressChanged();
        assertNull(notice.beginAutomatic(READ));
        assertNull(notice.poll(10_000));
    }

    @Test
    void firstSuccessfulRoundCannotBeReplacedByLaterFailures() {
        notice.complete(notice.beginAutomatic(READ), true, 1_000);
        notice.complete(notice.beginAutomatic(WRITE), true, 1_000);
        assertNull(notice.beginAutomatic(READ));
        notice.complete(null, false, 2_000);
        assertEquals(new StartupSyncNotice.Result(true), notice.poll(4_000));
    }

    @Test
    void keepsTheSpecificFirstFailureUntilItIsShown() {
        var failure = SyncFailure.of(SyncFailure.Reason.INVALID_MINECRAFT_SESSION, SyncFailure.Stage.MINECRAFT_AUTH);
        notice.complete(notice.beginAutomatic(WRITE), false, failure, 1_000);
        assertEquals(new StartupSyncNotice.Result(false, failure), notice.poll(4_000));
        assertNull(notice.poll(5_000));
    }
}
