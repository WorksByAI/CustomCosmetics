package com.customcosmetics.compatibility;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class ClientUiAccessTest {
    @Test
    void resolvesMinecraftChatMethodsWithoutStartingAGame() {
        assertDoesNotThrow(() -> Class.forName(ClientUiAccess.class.getName(), true,
            ClientUiAccess.class.getClassLoader()));
    }
}
