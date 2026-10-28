// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import net.minecraft.client.gui.Font;
/** Runtime bridge lives outside the mixin package. */
public interface UiFontAccess { Font.Provider customcosmetics$provider(); }
