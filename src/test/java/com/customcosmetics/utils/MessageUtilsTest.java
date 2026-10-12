package com.customcosmetics.utils;

import com.google.gson.JsonParser;
import com.customcosmetics.gui.PinkUi;
import com.customcosmetics.sync.SyncFailure;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MessageUtilsTest {
    @ParameterizedTest
    @CsvSource({
        "true, false, '[CustomCosmetics] Succeeded to sync remote data!'",
        "false, false, '[CustomCosmetics] Failed to sync remote data.'",
        "true, true, '[CustomCosmetics] Succeeded to sync remote data! [stop notifications]'",
        "false, true, '[CustomCosmetics] Failed to sync remote data. [stop notifications]'"
    })
    void syncResultsHaveCleanTextAndTheGuiPinkColor(boolean success, boolean notification, String expected) {
        Component result = MessageUtils.createSyncResult(success, notification);
        assertEquals(expected, result.getString());
        List<Integer> colors = new ArrayList<>();
        result.visit((style, text) -> {
            assertNull(style.getHoverEvent(), "Sync results must not contain lore");
            assertNotNull(style.getColor());
            colors.add(style.getColor().getValue());
            return Optional.empty();
        }, result.getStyle());
        assertFalse(colors.isEmpty());
        assertTrue(colors.stream().allMatch(color -> color == (PinkUi.PINK & 0xFFFFFF)));
        List<Component> clickable = new ArrayList<>();
        collectClickable(result, clickable);
        if (notification) {
            assertEquals(1, clickable.size());
            assertEquals("[stop notifications]", clickable.getFirst().getString());
            assertEquals(new ClickEvent.RunCommand("/customcosmetics notification off"),
                clickable.getFirst().getStyle().getClickEvent());
        } else {
            assertTrue(clickable.isEmpty());
        }
    }

    @Test
    void otherChatMessagesUseTheUpdatedDisplayName() {
        assertEquals("[CustomCosmetics] Example", MessageUtils.createMessageWithPrefix("Example").getString());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Sync notification disabled.", "Sync notification enabled.",
        "Sync notification is ON.", "Sync notification is OFF.", "Sync cooldown: 30s",
        "Syncing remote data...", "Remote data autosync enabled.", "Remote data autosync disabled."
    })
    void allCommandMessagesUsePinkForBothPrefixAndBody(String text) {
        Component message = MessageUtils.createMessageWithPrefix(text);
        assertEquals("[CustomCosmetics] " + text, message.getString());
        assertEntirelyPink(message);
    }

    @Test
    void recolorsNestedSpansWithoutChangingActionsOrTheOriginalMessage() {
        var action = new ClickEvent.RunCommand("/customcosmetics notification off");
        var child = Component.literal("[stop notifications]")
            .withStyle(style -> style.withColor(0xFF00FF).withUnderlined(true).withClickEvent(action));
        var original = Component.literal("Example ").withStyle(style -> style.withColor(0xFFFFFF).withBold(true))
            .append(Component.empty().withStyle(style -> style.withColor(0x00FF00)).append(child));
        Component message = MessageUtils.createMessageWithPrefix(original);
        assertEquals("[CustomCosmetics] Example [stop notifications]", message.getString());
        assertEntirelyPink(message);
        List<Component> clickable = new ArrayList<>();
        collectClickable(message, clickable);
        assertEquals(1, clickable.size());
        assertEquals(action, clickable.getFirst().getStyle().getClickEvent());
        assertTrue(clickable.getFirst().getStyle().isUnderlined());
        assertEquals(0xFFFFFF, original.getStyle().getColor().getValue());
        assertEquals(0xFF00FF, child.getStyle().getColor().getValue());
        assertTrue(original.getStyle().isBold());
        Component resent = MessageUtils.styleMessage(message);
        assertEquals(message.getString(), resent.getString());
        assertEntirelyPink(resent);
    }

    @ParameterizedTest
    @EnumSource(SyncFailure.Reason.class)
    void detailedFailuresKeepTheGuiPinkAndNotificationAction(SyncFailure.Reason reason) {
        var failure = SyncFailure.of(reason, SyncFailure.Stage.UPLOAD);
        var message = MessageUtils.createSyncResult(false, true, failure);
        assertEquals("[CustomCosmetics] Failed to sync remote data: " + failure.description() + ". [stop notifications]", message.getString());
        assertEntirelyPink(message);
        List<Component> clickable = new ArrayList<>();
        collectClickable(message, clickable);
        assertEquals(1, clickable.size());
        assertEquals(new ClickEvent.RunCommand("/customcosmetics notification off"), clickable.getFirst().getStyle().getClickEvent());
    }

    @Test
    void modMenuUsesTheNewDisplayNameAndExistingModId() throws Exception {
        try (var stream = MessageUtilsTest.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(stream);
            var metadata = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals("CustomCosmetics", metadata.get("name").getAsString());
            assertEquals("customcosmetics", metadata.get("id").getAsString());
            assertEquals(System.getProperty("customcosmetics.version"), metadata.get("version").getAsString());
        }
    }

    private static void collectClickable(Component component, List<Component> result) {
        if (component.getStyle().getClickEvent() != null) result.add(component);
        component.getSiblings().forEach(child -> collectClickable(child, result));
    }

    private static void assertEntirelyPink(Component message) {
        List<Integer> colors = new ArrayList<>();
        message.visit((style, text) -> {
            assertNotNull(style.getColor());
            colors.add(style.getColor().getValue());
            return Optional.empty();
        }, message.getStyle());
        assertFalse(colors.isEmpty());
        assertTrue(colors.stream().allMatch(color -> color == (PinkUi.PINK & 0xFFFFFF)));
    }
}
