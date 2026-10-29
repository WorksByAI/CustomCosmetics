// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.update;

/** Distribution flavor is independent of the semantic mod version and file name. */
public final class ClientFlavor {
    public static final String FONT="assets/customcosmetics/font/lxgwwenkai-medium.ttf";
    public static final boolean BUNDLED_FONT=ClientFlavor.class.getClassLoader().getResource(FONT)!=null;
    private ClientFlavor(){}
}
