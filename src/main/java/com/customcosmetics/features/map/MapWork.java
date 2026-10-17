// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.map;

import java.util.concurrent.*;

/** Keep large map preparation away from both the game thread and realtime network workers. */
final class MapWork {
    static final Executor EXECUTOR=Executors.newSingleThreadExecutor(task->{var thread=new Thread(task,"CustomCosmetics-MapPrepare");thread.setDaemon(true);thread.setPriority(Thread.NORM_PRIORITY-1);return thread;});
    private MapWork(){}
}
