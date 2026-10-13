// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.compatibility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CapePreviewSwingTest {
    public static class Older {public float attackTime=.8f;public float unaffected=.4f;}
    public static class Modern {public float swingAnimation=.6f;public float unaffected=.3f;}
    @Test void bothVersionLayoutsClearOnlyTheirOwnRenderSwing(){
        var older=new Older();var modern=new Modern();CapePreviewSwing.clear(older);CapePreviewSwing.clear(modern);
        assertEquals(0,older.attackTime);assertEquals(0,modern.swingAnimation);assertEquals(.4f,older.unaffected);assertEquals(.3f,modern.unaffected);
        older.attackTime=.2f;CapePreviewSwing.clear(older);assertEquals(0,older.attackTime);
    }
    @Test void unsupportedSnapshotsFailValidationInsteadOfChangingAnUnrelatedObject(){assertThrows(IllegalStateException.class,()->CapePreviewSwing.validate(Object.class));}
}
