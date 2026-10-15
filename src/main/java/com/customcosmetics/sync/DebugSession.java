// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.sync;

import java.util.function.Consumer;
import java.util.function.Supplier;

/** Local, process-only diagnostics. Sampling never creates a network request. */
final class DebugSession {
    private boolean enabled;private long nextSample;
    boolean enabled(){return enabled;}
    void enabled(boolean value){enabled=value;nextSample=0;}
    void tick(long now,Supplier<String> report,Consumer<String> output){
        if(!enabled||now<nextSample)return;
        nextSample=now+5000;output.accept(report.get());
    }
}
