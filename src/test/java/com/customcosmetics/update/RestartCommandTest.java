package com.customcosmetics.update;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RestartCommandTest {
    @Test void quotedPathsUnicodeEmptyArgumentsAndEscapedQuotesRoundTrip(){
        assertEquals(List.of("C:\\Program Files\\Java\\javaw.exe","-cp","C:\\MC Mods\\libs","Main","--gameDir","D:\\我的 游戏","","say \"hello\""),
            RestartCommand.parseWindows("\"C:\\Program Files\\Java\\javaw.exe\" -cp \"C:\\MC Mods\\libs\" Main --gameDir \"D:\\我的 游戏\" \"\" \"say \\\"hello\\\"\""));
    }
    @Test void actualJvmLaunchCanBeCapturedWithoutPersistingIt()throws Exception{
        var command=RestartCommand.capture();assertFalse(command.isEmpty());assertTrue(command.getFirst().toLowerCase(Locale.ROOT).contains("java"));
    }
}
