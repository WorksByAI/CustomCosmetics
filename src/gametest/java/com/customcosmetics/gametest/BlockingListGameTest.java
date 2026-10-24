// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gametest;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.blocking.BlockHand;
import com.customcosmetics.features.blocking.SwordBlocking;
import com.customcosmetics.gui.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

final class BlockingListGameTest {
    static void check(ClientGameTestContext context, TestSingleplayerContext world) {
        context.runOnClient(client -> {
            ConfigManager.swordBlockingListMode = "off";
            ConfigManager.swordBlockingBlacklist = new String[]{"skyblock:ASPECT_OF_THE_VOID"};
            ConfigManager.swordBlockingWhitelist = new String[0];
            ClientUiAccess.setScreen(client, new CosmeticsScreen(null, CosmeticsScreen.Page.ANIMATIONS));
        });
        context.waitTicks(3);
        activate(context, "Blocking List");
        context.takeScreenshot("blocking-list-dropdown");
        context.getInput().pressKey(ClientInput.DOWN);
        context.getInput().pressKey(ClientInput.ENTER);
        context.runOnClient(client -> { if (!ConfigManager.swordBlockingListMode.equals("blacklist")) throw new AssertionError("Dropdown selection failed"); });
        activate(context, "Edit Blocking Lists");
        context.waitForScreen(BlockingItemsScreen.class);
        context.runOnClient(client -> {
            var messages = ClientUiAccess.screen(client).children().stream()
                .filter(child -> child instanceof AbstractWidget).map(child -> (AbstractWidget) child)
                .filter(widget -> widget.visible).map(widget -> widget.getMessage().getString()).toList();
            if (messages.stream().anyMatch(message -> message.contains("%%") || message.contains("§")))
                throw new AssertionError("Weapon list displays raw formatting tokens");
        });
        context.takeScreenshot("blocking-list-readable-names");
        search(context, "AXE_PREVIEW");
        context.runOnClient(client -> {
            if (ClientUiAccess.screen(client).children().stream().filter(child -> child instanceof PinkUi.Action)
                    .map(child -> (AbstractWidget) child).anyMatch(widget -> widget.visible && widget.getMessage().getString().contains("skyblock:AXE_PREVIEW")))
                throw new AssertionError("Preview menu item is listed as a weapon");
        });
        context.takeScreenshot("blocking-list-no-preview-items");
        search(context, "ASPECT_OF_THE_VOID");
        activate(context, "Aspect of the Void:");
        context.runOnClient(client -> { if (Arrays.asList(ConfigManager.swordBlockingBlacklist).contains("skyblock:ASPECT_OF_THE_VOID")) throw new AssertionError("Cannot uncheck weapon"); });
        activate(context, "Aspect of the Void:");
        context.takeScreenshot("blocking-list-blacklist");
        activate(context, "Whitelist");
        search(context, "TERMINATOR");
        activate(context, "Terminator:");
        context.runOnClient(client -> {
            if (!Arrays.asList(ConfigManager.swordBlockingBlacklist).contains("skyblock:ASPECT_OF_THE_VOID")
                || !Arrays.asList(ConfigManager.swordBlockingWhitelist).contains("skyblock:TERMINATOR"))
                throw new AssertionError("List selections were not independent");
        });
        context.takeScreenshot("blocking-list-whitelist");
        activate(context, "Done");
        context.waitForScreen(CosmeticsScreen.class);
        activate(context, "Blocking List");
        context.getInput().pressKey(ClientInput.DOWN);
        context.getInput().pressKey(ClientInput.ENTER);
        context.runOnClient(client -> {
            if (!ConfigManager.swordBlockingListMode.equals("whitelist")) throw new AssertionError("Cannot switch to whitelist");
            ClientUiAccess.setScreen(client, null);
        });
        checkHand(context, world, "minecraft:bow[minecraft:custom_data={id:\"TERMINATOR\"}]", BlockHand.MAIN, "whitelisted-terminator");
        checkHand(context, world, "minecraft:diamond_sword", BlockHand.NONE, "whitelist-unselected-sword");
        context.runOnClient(client -> ConfigManager.swordBlockingListMode = "blacklist");
        checkHand(context, world, "minecraft:diamond_shovel[minecraft:custom_data={id:\"ASPECT_OF_THE_VOID\"}]", BlockHand.NONE, "blacklisted-aotv");
        checkHand(context, world, "minecraft:diamond_sword", BlockHand.MAIN, "blacklist-ordinary-sword");
        context.runOnClient(client -> ConfigManager.swordBlockingListMode = "off");
        context.runOnClient(client -> {
            var sword=client.player.getMainHandItem();var copy=sword.copy();
            String vanilla=com.customcosmetics.features.blocking.BlockingItems.identity(copy);
            var tag=new net.minecraft.nbt.CompoundTag();tag.putString("id","TERMINATOR");
            copy.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
            String skyblock=com.customcosmetics.features.blocking.BlockingItems.identity(copy);
            if(vanilla.equals(skyblock)||!skyblock.equals("skyblock:TERMINATOR"))throw new AssertionError("Blocking identity cache retained old item components");
            tag.putString("id","invalid!");copy.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));
            if(!com.customcosmetics.features.blocking.BlockingItems.identity(copy).isEmpty())throw new AssertionError("Invalid SkyBlock identity fell back to vanilla material");
            ConfigManager.swordBlockingEnabled=false;
            if(SwordBlocking.localHand()!=BlockHand.NONE)throw new AssertionError("Disabled sword blocking retained a sampled hand");
            ConfigManager.swordBlockingEnabled=true;
        });
        System.out.println("BLOCKING_LIST_GUI_AND_RENDER_OK");
    }
    private static void activate(ClientGameTestContext context, String prefix) {
        var scroll = new AtomicReference<double[]>();
        for (int attempt = 0; attempt < 20; attempt++) {
            context.runOnClient(client -> {
                scroll.set(null);
                var screen = ClientUiAccess.screen(client);
                var widget = screen.children().stream().filter(AbstractWidget.class::isInstance)
                    .map(AbstractWidget.class::cast).filter(child -> child.getMessage().getString().startsWith(prefix))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing control: " + prefix + "; controls: "
                        + screen.children().stream().filter(AbstractWidget.class::isInstance).map(AbstractWidget.class::cast)
                            .map(child -> child.getMessage().getString()).toList()));
                if (screen instanceof CosmeticsScreen) {
                    int top = studioInt(screen, "viewTop"), bottom = studioInt(screen, "viewBottom");
                    double steps = widget.getY() < top ? Math.ceil((top - widget.getY()) / 48.0)
                        : widget.getBottom() > bottom ? -Math.ceil((widget.getBottom() - bottom) / 48.0) : 0;
                    if (steps != 0) {
                        double scale = client.getWindow().getGuiScale();
                        scroll.set(new double[]{(widget.getX() + widget.getWidth() / 2.0) * scale, (top + bottom) / 2.0 * scale, steps});
                        return;
                    }
                }
                if (!widget.visible || !widget.active) throw new AssertionError("Control is not interactable: " + prefix);
                screen.setFocused(widget);
            });
            var target = scroll.get();
            if (target != null) {
                context.getInput().setCursorPos(target[0], target[1]);
                context.getInput().scroll((int) target[2]); context.waitTicks(2);
                continue;
            }
            context.getInput().pressKey(ClientInput.ENTER); context.waitTicks(2); return;
        }
        throw new AssertionError("Could not scroll control into the visible viewport: " + prefix);
    }
    private static int studioInt(Object screen, String name) {
        try {
            var field = CosmeticsScreen.class.getDeclaredField(name); field.setAccessible(true);
            return field.getInt(screen);
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void search(ClientGameTestContext context, String query) {
        context.runOnClient(client -> {
            var screen = ClientUiAccess.screen(client);
            var field = screen.children().stream().filter(child -> child instanceof PinkUi.Field)
                .map(child -> (PinkUi.Field) child).findFirst().orElseThrow();
            field.setValue(""); screen.setFocused(field);
        });
        context.getInput().typeChars(query); context.waitTicks(2);
    }
    private static void checkHand(ClientGameTestContext context, TestSingleplayerContext world, String item, BlockHand expected, String label) {
        context.getInput().releaseKey(options -> options.keyUse);
        world.getServer().runCommand("item replace entity @a weapon.mainhand with " + item);
        world.getServer().runCommand("item replace entity @a weapon.offhand with minecraft:air");
        context.waitTicks(10); RenderAssertions.calls.set(0);
        context.getInput().holdKey(options -> options.keyUse); context.waitTicks(5);
        context.runOnClient(client -> { if (SwordBlocking.localHand() != expected) throw new AssertionError(label + ": wrong visual hand"); });
        context.takeScreenshot("blocking-list-" + label);
        if (expected == BlockHand.MAIN && RenderAssertions.calls.get() == 0) throw new AssertionError("Selected non-sword did not render blocking");
        if (expected == BlockHand.NONE && RenderAssertions.calls.get() != 0) throw new AssertionError("Unselected item rendered blocking");
        context.getInput().releaseKey(options -> options.keyUse); context.waitTicks(5);
    }
}
