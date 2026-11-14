// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v261.ext;

import net.minecraft.client.multiplayer.ClientPacketListener;

public interface ClientPacketListenerExt {
    void bobby_queueUnloadFakeLightDataTask(Runnable runnable);

    static ClientPacketListenerExt get(ClientPacketListener handler) {
        return (ClientPacketListenerExt) handler;
    }
}
