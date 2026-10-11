package com.customcosmetics.sync;

import org.junit.jupiter.api.Test;
import static com.customcosmetics.sync.StartupSyncNotice.Operation.*;
import static org.junit.jupiter.api.Assertions.*;

class ManualSyncResultTest {
    @Test
    void successfulUploadDoesNotHideFailedDownload() {
        var result = new ManualSyncResult();
        var failure = SyncFailure.of(SyncFailure.Reason.TIMEOUT, SyncFailure.Stage.DOWNLOAD);
        result.begin(1);
        assertNull(result.complete(1, WRITE, true, null));
        assertTrue(result.needs(1, READ));
        assertFalse(result.needs(1, WRITE));
        assertEquals(new ManualSyncResult.Result(false, failure), result.complete(1, READ, false, failure));
        assertNull(result.complete(1, READ, true, null));
    }

    @Test
    void successfulDownloadDoesNotHideOfflineUploadFailure() {
        var result = new ManualSyncResult();
        var failure = SyncFailure.of(SyncFailure.Reason.INVALID_MINECRAFT_SESSION, SyncFailure.Stage.MINECRAFT_AUTH);
        result.begin(1);
        assertNull(result.complete(1, READ, true, null));
        assertEquals(new ManualSyncResult.Result(false, failure), result.complete(1, WRITE, false, failure));
    }

    @Test
    void reportsSuccessOnlyAfterBothOperationsComplete() {
        var result = new ManualSyncResult();
        result.begin(1);
        assertNull(result.complete(1, READ, true, null));
        assertEquals(new ManualSyncResult.Result(true, null), result.complete(1, WRITE, true, null));
        assertFalse(result.needs(1, READ));
    }

    @Test
    void cancelledAndOldEndpointRequestsCannotCompleteANewRound() {
        var result = new ManualSyncResult();
        result.begin(1);
        result.cancel();
        assertNull(result.complete(1, READ, false, null));
        result.begin(2);
        assertNull(result.complete(1, WRITE, true, null));
        assertTrue(result.needs(2, WRITE));
        assertNull(result.complete(2, WRITE, true, null));
        assertEquals(new ManualSyncResult.Result(true, null), result.complete(2, READ, true, null));
    }
}
