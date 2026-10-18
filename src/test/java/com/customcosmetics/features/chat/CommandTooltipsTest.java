package com.customcosmetics.features.chat;
import com.customcosmetics.config.ConfigManager;
import net.minecraft.network.chat.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class CommandTooltipsTest {
    @Test void previewsTheRealCommandPreservingExistingTextAndClickBehavior(){
        var click=new ClickEvent.RunCommand("/party accept Alice");var text=Component.literal("Existing server tooltip");
        var original=Style.EMPTY.withClickEvent(click).withHoverEvent(new HoverEvent.ShowText(text));var result=CommandTooltips.enrich(original);
        assertSame(click,result.getClickEvent());assertEquals("Existing server tooltip",text.getString());
        assertEquals("Existing server tooltip\nClick to execute command: /party accept Alice",((HoverEvent.ShowText)result.getHoverEvent()).value().getString());
        assertSame(result,CommandTooltips.enrich(result));
        var plain=CommandTooltips.enrich(Style.EMPTY.withClickEvent(click));assertEquals(CommandTooltips.PREFIX+click.command(),((HoverEvent.ShowText)plain.getHoverEvent()).value().getString());
    }
    @Test void optOutOtherActionsAndNonTextTooltipsRemainUntouched(){
        assertNull(CommandTooltips.enrich(null));assertSame(Style.EMPTY,CommandTooltips.enrich(Style.EMPTY));
        for(var click:new ClickEvent[]{new ClickEvent.SuggestCommand("/help"),new ClickEvent.OpenUrl(java.net.URI.create("https://example.com")),new ClickEvent.CopyToClipboard("text")}){
            var style=Style.EMPTY.withClickEvent(click);assertSame(style,CommandTooltips.enrich(style));
        }
        boolean enabled=ConfigManager.commandTooltipsEnabled;try{ConfigManager.commandTooltipsEnabled=false;var style=Style.EMPTY.withClickEvent(new ClickEvent.RunCommand("/help"));assertSame(style,CommandTooltips.enrich(style));}finally{ConfigManager.commandTooltipsEnabled=enabled;}
    }
}
