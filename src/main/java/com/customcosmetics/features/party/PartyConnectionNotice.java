// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.features.party;

import com.customcosmetics.utils.MessageUtils;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** A roster refresh or a transport reconnect is not another party join. */
public final class PartyConnectionNotice {
    public static final String SETTINGS_COMMAND = "/customcosmetics party";
    private boolean inParty;
    private boolean announced;
    private boolean failed;

    public void membership(boolean present) {
        if (!present) {
            inParty = false;
            announced = false;
            failed = false;
        } else {
            inParty = true;
        }
    }

    public boolean connected(boolean ready) {
        return connected(ready,true);
    }

    public boolean connected(boolean ready,boolean enabled) {
        if (!inParty || !ready || announced) return false;
        announced = true;
        return enabled;
    }

    public boolean failed(boolean enabled) {
        if(!inParty||announced||failed)return false;
        failed=true;return enabled;
    }

    public static MutableComponent message() {
        return message(true);
    }

    public static MutableComponent message(boolean connected) {
        return MessageUtils.createMessageWithPrefix(Component.literal(connected?"Connected to the Party sharing server. ":"Could not connect to the Party sharing server. ")
            .append(Component.literal("[Settings]").withStyle(style -> style.withUnderlined(true))))
            .withStyle(style -> style.withClickEvent(new ClickEvent.RunCommand(SETTINGS_COMMAND)));
    }
}
