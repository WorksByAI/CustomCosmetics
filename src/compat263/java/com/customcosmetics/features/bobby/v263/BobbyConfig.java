// SPDX-License-Identifier: LGPL-3.0-or-later
package com.customcosmetics.features.bobby.v263;


public class BobbyConfig {
    public static final BobbyConfig DEFAULT = new BobbyConfig();

    private boolean enabled = true;
    private boolean dynamicMultiWorld = true;
    private boolean noBlockEntities = true;
    private boolean taintFakeChunks = false;
    private int unloadDelaySecs = 60;
    private int deleteUnusedRegionsAfterDays = -1;
    private int maxRenderDistance = 32;
    private int viewDistanceOverwrite = 0;

    public BobbyConfig() {}

    public BobbyConfig(
            boolean enabled,
            boolean dynamicMultiWorld,
            boolean noBlockEntities,
            boolean taintFakeChunks,
            int unloadDelaySecs,
            int deleteUnusedRegionsAfterDays,
            int maxRenderDistance,
            int viewDistanceOverwrite
    ) {
        this.enabled = enabled;
        this.dynamicMultiWorld = dynamicMultiWorld;
        this.noBlockEntities = noBlockEntities;
        this.taintFakeChunks = taintFakeChunks;
        this.unloadDelaySecs = unloadDelaySecs;
        this.deleteUnusedRegionsAfterDays = deleteUnusedRegionsAfterDays;
        this.maxRenderDistance = maxRenderDistance;
        this.viewDistanceOverwrite = viewDistanceOverwrite;
    }

    public boolean isNoBlockEntities() {
        return noBlockEntities;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDynamicMultiWorld() {
        return dynamicMultiWorld;
    }

    public boolean isTaintFakeChunks() {
        return taintFakeChunks;
    }

    public int getUnloadDelaySecs() {
        return unloadDelaySecs;
    }

    public int getDeleteUnusedRegionsAfterDays() {
        return deleteUnusedRegionsAfterDays;
    }

    public int getMaxRenderDistance() {
        return maxRenderDistance;
    }

    public int getViewDistanceOverwrite() {
        return viewDistanceOverwrite;
    }
}
