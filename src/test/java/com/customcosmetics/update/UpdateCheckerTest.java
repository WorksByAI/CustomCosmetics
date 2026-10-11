package com.customcosmetics.update;

import com.customcosmetics.gui.PinkUi;
import java.lang.classfile.ClassFile;
import java.lang.classfile.instruction.InvokeInstruction;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    @Test void noticeUsesPinkAndAUserClickedFixedProjectReleaseLink() {
        var message = UpdateChecker.message("1.0.0", "1.0.1");
        assertEquals("[CustomCosmetics] Update available: 1.0.1 (installed: 1.0.0). [View Release]", message.getString());
        var links = new java.util.ArrayList<ClickEvent>();
        message.visit((style,text) -> {
            assertEquals(PinkUi.PINK & 0xFFFFFF, style.getColor().getValue()); assertNull(style.getHoverEvent());
            if (style.getClickEvent() != null) { assertTrue(style.isUnderlined()); links.add(style.getClickEvent()); }
            return Optional.empty();
        },message.getStyle());
        assertEquals(List.of(new ClickEvent.OpenUrl(GitHubUpdateCheck.RELEASE_PAGE)),links);
    }
    @Test void updateCodeCannotSendGamePacketsCommandsOrAutomaticallyOpenALink() throws Exception {
        for (String type : List.of("UpdateChecker", "GitHubUpdateCheck")) {
            try (var in = getClass().getClassLoader().getResourceAsStream("com/customcosmetics/update/" + type + ".class")) {
                assertNotNull(in);
                for (var method : ClassFile.of().parse(in.readAllBytes()).methods()) {
                    for (var element : method.code().map(c -> c.elementStream().toList()).orElse(List.of())) {
                        if (element instanceof InvokeInstruction call) {
                            String owner = call.owner().name().stringValue();
                            assertFalse(owner.startsWith("net/minecraft/network/protocol/") || owner.startsWith("net/minecraft/client/multiplayer/"));
                            assertFalse(Set.of("sendChat","sendCommand","sendPacket","getConnection","openUri","sendUserText").contains(call.name().stringValue()));
                        }
                    }
                }
            }
        }
    }
}
