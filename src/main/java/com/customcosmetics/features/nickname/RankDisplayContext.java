// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;

/** Render-local policy. Closing a scope always restores its caller, including exceptional exits. */
public final class RankDisplayContext implements AutoCloseable {
    private static final ThreadLocal<RankText.Context> CURRENT=ThreadLocal.withInitial(()->RankText.Context.EXISTING);
    private final RankText.Context previous;
    private RankDisplayContext(RankText.Context context){previous=CURRENT.get();CURRENT.set(context);}
    public static RankText.Context current(){return CURRENT.get();}
    public static RankDisplayContext enter(RankText.Context context){return new RankDisplayContext(context);}
    @Override public void close(){CURRENT.set(previous);}
}
