// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v261.ext;

import com.customcosmetics.features.bobby.v261.FakeChunkManager;
import com.customcosmetics.features.bobby.v261.VisibleChunksTracker;

public interface ClientChunkCacheExt {
    FakeChunkManager bobby_getFakeChunkManager();
    VisibleChunksTracker bobby_getRealChunksTracker();
    void bobby_onFakeChunkAdded(int x, int z);
    void bobby_onFakeChunkRemoved(int x, int z, boolean willBeReplaced);
}
