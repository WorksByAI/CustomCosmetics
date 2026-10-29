// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import java.util.concurrent.atomic.AtomicInteger;

/** Test-only evidence that the full game's renderer reached the blocking transform. */
public final class RenderAssertions {
    public static volatile long nicknameTime = -1;
    public static final AtomicInteger nicknameGlyphs = new AtomicInteger();
    public static final AtomicInteger calls = new AtomicInteger();
    public static final AtomicInteger models = new AtomicInteger();
    public static final AtomicInteger remoteModels = new AtomicInteger();
    public static final java.util.Set<Integer> remoteSeen = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static final AtomicInteger itemModelOverrides = new AtomicInteger();
    public static volatile String customizedItemModel = "", untouchedItemModel = "";
    public static volatile String customizedHeadTexture = "";
    public static volatile boolean forceAnimatedArmorDye;
    public static volatile int animatedArmorColor=0xFF0554C9;
    public static final java.util.Set<Integer> observedArmorTints=java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static final java.util.Set<String> headContexts = java.util.concurrent.ConcurrentHashMap.newKeySet();
    public static volatile float swing, equip, blockingSwing, blockingEquip, blockingY;
    public static volatile float forcedBlockingSwing = Float.NaN;
    public static volatile String renderedBlockingStyle;
    public static volatile boolean movement;
    public static volatile boolean trackBlockingDepth;
    public static volatile float minBlockingDepth, maxBlockingDepth;
    public static final AtomicInteger depthCalls = new AtomicInteger();
    public static volatile boolean trackHandSleeves;
    public static final AtomicInteger firstPersonHandChecks = new AtomicInteger();
    public static final AtomicInteger firstPersonSleeveMismatch = new AtomicInteger();
    public static final AtomicInteger remoteSleeveMismatch = new AtomicInteger();
    private RenderAssertions() {}
}
