// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.config;

import com.customcosmetics.sync.*;
import com.customcosmetics.utils.NickRenderUtils;

public final class CosmeticsMode {
    private CosmeticsMode() {}
    public static void setOffline(boolean offline) {
        RemoteAccess.offline(offline);
        PresenceSync.onModeChanged(); BlockingSync.disconnect();
        PartySync.disconnect();
        NickRenderUtils.invalidateLocalTargetsCache(); ConfigManager.requestSave();
    }
}
