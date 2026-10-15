// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.nickname;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;
/** A render-only sequence must not substitute an alias again at another text entry point. */
public record ProcessedNicknameText(FormattedCharSequence delegate) implements NicknameText {
    @Override public boolean accept(FormattedCharSink sink){return delegate.accept(sink);}
}
