package com.customcosmetics.features.camera;

import net.minecraft.client.CameraType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SnaplookTest {
    @Test void missingOrInvalidPerspectiveUsesBackView() {
        assertEquals("back", Snaplook.normalize(null));
        assertEquals("back", Snaplook.normalize("invalid"));
        assertEquals(CameraType.THIRD_PERSON_BACK, Snaplook.perspective(null));
        assertEquals(CameraType.THIRD_PERSON_BACK, Snaplook.perspective(""));
    }

    @Test void explicitPerspectiveChoicesKeepTheirMeaning() {
        assertEquals(CameraType.FIRST_PERSON, Snaplook.perspective("first"));
        assertEquals(CameraType.THIRD_PERSON_BACK, Snaplook.perspective("back"));
        assertEquals(CameraType.THIRD_PERSON_FRONT, Snaplook.perspective("front"));
    }
}
