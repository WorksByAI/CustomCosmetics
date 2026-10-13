package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.sync.PresenceSync;
import com.customcosmetics.utils.NickRenderUtils;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import static com.customcosmetics.gui.PinkUi.*;

/** Independent pink cosmetics studio, with scrollable settings and keyboard focus. */
public final class CosmeticsScreen extends Screen {
    public enum Page { MAIN, SMOL, NICK, COLOR, CHROMA, PRESETS, ANIMATIONS, ITEMS, SYNC }
    private static final ScreenSession SESSION = new ScreenSession();
    private final Screen parent;
    private Page page;
    private final List<AbstractWidget> navigation = new ArrayList<>();
    private final List<Placement> controls = new ArrayList<>();
    private final List<Consumer<GuiGraphicsExtractor>> art = new ArrayList<>();
    private final List<Consumer<GuiGraphicsExtractor>> pinnedArt = new ArrayList<>();
    private int panelX, panelY, panelWidth, panelHeight, sideWidth;
    private int contentX, contentWidth, viewTop, viewBottom, rowY, contentBottom;
    private double scroll;
    private int selectedEndpoint;
    private int mouseX, mouseY;
    private String notice = "";
    private long noticeUntil;
    private PinkUi.Field hexField;
    private PinkUi.Field refreshIntervalField;
    private PinkUi.Action syncNowButton;
    private BlockingModeDropdown blockingMode;
    private BlockingStyleDropdown blockingStyle;
    private PinkUi.ColorPicker picker;
    private boolean changingColorText;
    private boolean editingShimmerColors;
    private String presetName = "";
    private boolean pinnedNickname, draggingScroll;
    private record Placement(AbstractWidget widget, int y) {}

    public CosmeticsScreen(Screen parent, Page page) {
        super(Component.literal("CustomCosmetics"));
        this.parent = parent;
        if (page == Page.MAIN) {
            var remembered = SESSION.open(System.currentTimeMillis());
            this.page = Page.valueOf(remembered.page()); scroll = remembered.scroll();
        } else { this.page = page; SESSION.touch(System.currentTimeMillis()); }
    }

    @Override protected void init() {
        navigation.clear(); controls.clear(); art.clear(); pinnedArt.clear();
        hexField = null; picker = null;
        refreshIntervalField = null; syncNowButton = null;
        blockingMode = null;
        blockingStyle = null;
        panelWidth = Math.min(740, width - 16);
        panelHeight = Math.min(436, height - 16);
        panelX = (width - panelWidth) / 2;
        panelY = (height - panelHeight) / 2;
        sideWidth = panelWidth < 480 ? 96 : 122;
        contentX = panelX + sideWidth + 18;
        contentWidth = panelWidth - sideWidth - 36;
        viewTop = panelY + 52;
        viewBottom = panelY + panelHeight - 18;
        rowY = viewTop;

        Page[] pages = {Page.SMOL, Page.NICK, Page.ANIMATIONS, Page.ITEMS, Page.SYNC};
        String[] names = {"SmolPeople", "NickTweaks", "Animations", "Item Cosmetics", "Sync"};
        int navTop = panelHeight < 220 ? 51 : 64;
        int navGap = Math.clamp((panelHeight - navTop - 52) / (pages.length+1), 14, 31);
        for (int i = 0; i < pages.length; i++) {
            Page target = pages[i];
            PinkUi.Action action = new PinkUi.Action(panelX + 10, panelY + navTop + i * navGap,
                sideWidth - 20, navGap - 4, names[i], false, () -> open(target));
            action.selected(() -> target == page
                || target == Page.NICK && (page == Page.COLOR || page == Page.CHROMA || page == Page.PRESETS));
            navigation.add(addWidget(action));
        }
        navigation.add(addWidget(new PinkUi.Action(panelX+10,panelY+navTop+pages.length*navGap,sideWidth-20,navGap-4,"Cape",false,
            ()->com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,new CapeScreen(this)))));
        navigation.add(addWidget(new RepositoryButton(panelX + 12, panelY + panelHeight - 42)));
        var mode = new PinkUi.Action(panelX + 47, panelY + panelHeight - 42, sideWidth - 55, 30,
            ConfigManager.offlineMode ? "Offline" : "Online", false, () -> {
                com.customcosmetics.config.CosmeticsMode.setOffline(!ConfigManager.offlineMode); rebuildWidgets();
            });
        mode.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Offline: local cosmetics only. No remote connections.")));
        navigation.add(addWidget(mode));
        navigation.add(addWidget(new PinkUi.Action(panelX + panelWidth - 32, panelY + 15, 20, 20, "×", false, this::onClose)));
        pinnedNickname = panelHeight >= 360 && (page == Page.NICK || page == Page.COLOR || page == Page.CHROMA || page == Page.PRESETS);
        if (pinnedNickname) {
            previewCard(); tabs();
            pinnedArt.addAll(art);
            art.clear();
            for (Placement placement : controls) navigation.add(placement.widget());
            controls.clear();
            viewTop = rowY;
        }
        switch (page) {
            case MAIN -> smol();
            case SMOL -> smol();
            case NICK -> nickname();
            case COLOR -> colors();
            case CHROMA -> chroma();
            case PRESETS -> presets();
            case ANIMATIONS -> animations();
            case ITEMS -> itemCosmetics();
            case SYNC -> sync();
        }
        if (page == Page.SMOL || page == Page.NICK || page == Page.COLOR || page == Page.CHROMA || page == Page.PRESETS) credit();
        contentBottom = rowY + 5;
        scroll = Math.clamp(scroll, 0, maxScroll());
        positionControls();
    }

    private String pageTitle() {
        return switch (page) {
            case MAIN, SMOL -> "SmolPeople";
            case NICK, COLOR, CHROMA, PRESETS -> "NickTweaks";
            case ANIMATIONS -> "Animations";
            case ITEMS -> "Item Cosmetics";
            case SYNC -> "Sync";
        };
    }

    private void smol() {
        int y = card(184);
        label("Preview", contentX + 12, y + 12, PINK, contentWidth - 24);
        art.add(g -> {
            if (minecraft.player == null) {
                text(g, font, "Player preview unavailable", contentX + 12, y + 59, MUTED, contentWidth - 24);
                text(g, font, "Requires an active world", contentX + 12, y + 77, DIM, contentWidth - 24);
                return;
            }
            Component name = NickRenderUtils.previewNickname(minecraft.player.getName().getString());
            int nameWidth = font.width(name);
            float nameScale = PreviewLayout.nameScale(nameWidth, contentWidth - 32);
            g.pose().pushMatrix();
            g.pose().translate(contentX + (contentWidth - nameWidth * nameScale) / 2f, y + 31);
            g.pose().scale(nameScale, nameScale);
            g.text(font, name, 0, 0, TEXT, false);
            g.pose().popMatrix();
            // Use vanilla's inventory renderer, including skin, equipped items and SmolPeople mixins.
            // Entity bounds are screen coordinates; draw outside the scrolling pose.
            int top = y - (int) Math.round(scroll);
            g.pose().pushMatrix();
            g.pose().translate(0, (float) Math.round(scroll));
            InventoryScreen.extractEntityInInventoryFollowsMouse(g,
                contentX + 4, top + 51, contentX + contentWidth - 4, top + 178,
                52, 0.0625f, mouseX, mouseY, minecraft.player);
            g.pose().popMatrix();
        });
        toggleRow("Enabled", "", () -> ConfigManager.smolpeopleMode, v -> ConfigManager.smolpeopleMode = v);
        sliderRow("Limb Swing Speed", "", () -> ConfigManager.smolLimbSwingSpeed,
            0.5, 5, 0.1, 2.5, v -> ConfigManager.smolLimbSwingSpeed = v);
    }

    private void itemCosmetics() {
        toggleRow("Enabled", "Custom item names and models. Only visible on your client.",
            () -> com.customcosmetics.features.items.ItemCosmetics.store().enabled(),
            value -> com.customcosmetics.features.items.ItemCosmetics.store().enabled(value));
        fullButton("Customize Items", () -> com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,
            new ItemAppearanceScreen(this)), false);
        label("Select a specific SkyBlock item from your inventory.", contentX, rowY, MUTED, contentWidth);rowY += 22;
        label("Models include vanilla and your enabled resource packs.", contentX, rowY, MUTED, contentWidth);rowY += 22;
    }

    private void animations() {
        toggleRow("Sword Blocking", "Hold Use with a sword. Visual effect only.",
            () -> ConfigManager.swordBlockingEnabled, v -> ConfigManager.swordBlockingEnabled = v);
        fullButton("Adjust Blocking", () -> com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,
            new BlockingAdjustmentScreen(this)), false);
        blockingStyle = add(new BlockingStyleDropdown(contentX, rowY, contentWidth));
        rowY += 38;
        blockingMode = add(new BlockingModeDropdown(contentX, rowY, contentWidth));
        rowY += 38;
        fullButton("Edit Blocking Lists", () -> com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,
            new BlockingItemsScreen(this)), false);
        toggleRow("Apply Lists to Other Players", "Filter their blocking with your blacklist or whitelist.",
            () -> ConfigManager.swordBlockingListsApplyToOthers, v -> ConfigManager.swordBlockingListsApplyToOthers = v);
        toggleRow("Share Blocking", "Show other players and share your pose. Requires Automatic Sync ENABLED.",
            () -> ConfigManager.swordBlockingSyncEnabled, v -> ConfigManager.swordBlockingSyncEnabled = v);
        toggleRow("View Model", "", () -> ConfigManager.animationsEnabled, v -> ConfigManager.animationsEnabled = v);
        var numbers = new ArrayList<NumericSetting>(List.of(
            new NumericSetting("Item Scale", () -> ConfigManager.animationItemScale, 0.1, 2.5, 0.05, 1, v -> ConfigManager.animationItemScale = v),
            new NumericSetting("Position X", () -> ConfigManager.animationX, -2, 2, 0.01, 0, v -> ConfigManager.animationX = v),
            new NumericSetting("Position Y", () -> ConfigManager.animationY, -2, 2, 0.01, 0, v -> ConfigManager.animationY = v),
            new NumericSetting("Position Z", () -> ConfigManager.animationZ, -2, 2, 0.01, 0, v -> ConfigManager.animationZ = v),
            new NumericSetting("Rotation X", () -> ConfigManager.animationRotationX, -50, 50, 1, 0, v -> ConfigManager.animationRotationX = v),
            new NumericSetting("Rotation Y", () -> ConfigManager.animationRotationY, -50, 50, 1, 0, v -> ConfigManager.animationRotationY = v),
            new NumericSetting("Rotation Z", () -> ConfigManager.animationRotationZ, -50, 50, 1, 0, v -> ConfigManager.animationRotationZ = v),
            new NumericSetting("Swing X", () -> ConfigManager.animationSwingX, 0, 2, 0.01, 1, v -> ConfigManager.animationSwingX = v),
            new NumericSetting("Swing Y", () -> ConfigManager.animationSwingY, 0, 2, 0.01, 1, v -> ConfigManager.animationSwingY = v),
            new NumericSetting("Swing Z", () -> ConfigManager.animationSwingZ, 0, 2, 0.01, 1, v -> ConfigManager.animationSwingZ = v)));
        if (!ConfigManager.animationDisableSwing || ConfigManager.animationTerminatorOnly)
            numbers.add(new NumericSetting("Swing Speed", () -> ConfigManager.animationSwingSpeed, -2, 1, 0.05, 0, v -> ConfigManager.animationSwingSpeed = v));
        int columns = contentWidth >= 300 ? 2 : 1, cellWidth = (contentWidth - (columns - 1) * 6) / columns;
        for (int i = 0; i < numbers.size(); i++) {
            sliderCell(numbers.get(i), contentX + (i % columns) * (cellWidth + 6), rowY, cellWidth);
            if (i % columns == columns - 1 || i == numbers.size() - 1) rowY += 64;
        }
        toggleRow("Disable Hand Movement", "", () -> ConfigManager.animationDisableHandMove, v -> ConfigManager.animationDisableHandMove = v);
        toggleRow("Disable Equip Animation", "", () -> ConfigManager.animationDisableEquip, v -> ConfigManager.animationDisableEquip = v);
        toggleRow("Disable Swing Animation", "", () -> ConfigManager.animationDisableSwing, v -> { ConfigManager.animationDisableSwing = v; rebuildWidgets(); });
        if (ConfigManager.animationDisableSwing)
            toggleRow("Terminator Only", "", () -> ConfigManager.animationTerminatorOnly, v -> { ConfigManager.animationTerminatorOnly = v; rebuildWidgets(); });
        if (!ConfigManager.animationDisableSwing || ConfigManager.animationTerminatorOnly) {
            toggleRow("Ignore Haste", "Use normal swing timing instead of potion timing.", () -> ConfigManager.animationIgnoreHaste, v -> ConfigManager.animationIgnoreHaste = v);
        }
        add(new PinkUi.Action(contentX,rowY,132,26,"Reset View Model",false,() -> { ConfigManager.resetAnimations(); changed(); rebuildWidgets(); }));
        rowY += 32;
    }

    private void tabs() {
        String[] labels = {"General", "Gradient", "Chroma", "Presets"};
        Page[] pages = {Page.NICK, Page.COLOR, Page.CHROMA, Page.PRESETS};
        int w = (contentWidth - 12) / 4;
        for (int i = 0; i < pages.length; i++) {
            Page target = pages[i];
            add(new PinkUi.Action(contentX + i * (w + 4), rowY, w, 25, labels[i], false, () -> open(target)).selected(() -> page == target));
        }
        rowY += 35;
    }

    private void nickname() {
        if (!pinnedNickname) { previewCard(); tabs(); }
        toggleRow("Enabled", "", () -> ConfigManager.nickTweaksEnabled, v -> ConfigManager.nickTweaksEnabled = v);
        int y = card(82);
        label("Nick Changer", contentX + 12, y + 11, TEXT, contentWidth - 24);
        ConfigManager.nickTweaksNickChanger = com.customcosmetics.config.NicknameLimit.limit(ConfigManager.nickTweaksNickChanger);
        var nickname = input(contentX + 12, y + 28, contentWidth - 24, "Nick Changer", ConfigManager.nickTweaksNickChanger,
            com.customcosmetics.config.NicknameLimit.MAX_LENGTH, value -> {});
        nickname.setHint(Component.literal("Account name (40 characters max)"));
        nickname.setResponder(value -> {
            String limited = com.customcosmetics.config.NicknameLimit.limit(value);
            if (!value.equals(limited)) { nickname.setValue(limited); return; }
            activity(); ConfigManager.nickTweaksNickChanger = limited; changed();
        });
        label("Color codes: &0–&f, &r", contentX + 12, y + 65, MUTED, contentWidth - 24);
        toggleRow("Bold Self Nick", "", () -> ConfigManager.nickTweaksBoldSelf, v -> ConfigManager.nickTweaksBoldSelf = v);
    }

    private void colors() {
        if (!pinnedNickname) { previewCard(); tabs(); }
        toggleRow("Custom Nick Color", "Chroma takes priority.", () -> ConfigManager.nickTweaksCustomNickColorEnabled, v -> ConfigManager.nickTweaksCustomNickColorEnabled = v);
        String mode = switch (com.customcosmetics.features.nickname.NicknameGradient.normalizeStyle(ConfigManager.nickTweaksGradientStyle)) {
            case "smooth" -> "Smooth"; case "shimmer" -> "Shimmer"; default -> "Per Character";
        };
        fullButton("Gradient Style: " + mode, () -> {
            ConfigManager.nickTweaksGradientStyle = switch (ConfigManager.nickTweaksGradientStyle) {
                case "character" -> "smooth"; case "smooth" -> "shimmer"; default -> "character";
            };
            changed(); rebuildWidgets();
        }, false);
        fullButton("Shimmer Preset", () -> {
            ConfigManager.nickTweaksEnabled = true;
            ConfigManager.nickTweaksCustomNickColorEnabled = true;
            ConfigManager.nickTweaksChromaEnabled = false;
            ConfigManager.nickTweaksGradientStyle = "shimmer";
            ConfigManager.nickTweaksShimmerSpeed = 1; ConfigManager.nickTweaksShimmerSize = 0.16; ConfigManager.nickTweaksShimmerInterval = 2;
            ConfigManager.nickTweaksGradientStartColor = com.customcosmetics.features.nickname.NicknameGradient.PRESET_START;
            ConfigManager.nickTweaksGradientEndColor = com.customcosmetics.features.nickname.NicknameGradient.PRESET_END;
            ConfigManager.nickTweaksShimmerColor = 0xFFCF64; ConfigManager.nickTweaksShimmerHighlightColor = 0xFFF0BB;
            editingShimmerColors = false;
            changed(); rebuildWidgets();
        }, false);
        if (ConfigManager.nickTweaksGradientStyle.equals("shimmer")) {
            sliderRow("Shimmer Size", "", () -> ConfigManager.nickTweaksShimmerSize, 0.02, 1, 0.01, 0.16, v -> ConfigManager.nickTweaksShimmerSize = v);
            sliderRow("Shimmer Interval", "Seconds between sweeps.", () -> ConfigManager.nickTweaksShimmerInterval, 0, 30, 0.1, 2, v -> ConfigManager.nickTweaksShimmerInterval = v);
            sliderRow("Shimmer Speed", "", () -> ConfigManager.nickTweaksShimmerSpeed, 0, 4, 0.1, 1, v -> ConfigManager.nickTweaksShimmerSpeed = v);
        }
        if (!ConfigManager.nickTweaksGradientStyle.equals("shimmer")) editingShimmerColors = false;
        if (ConfigManager.nickTweaksGradientStyle.equals("shimmer"))
            fullButton("Editing: " + (editingShimmerColors ? "Shimmer Colors" : "Gradient Colors"), () -> {
                editingShimmerColors = !editingShimmerColors; selectedEndpoint = 0; rebuildWidgets();
            }, false);
        int y = card(267);
        label(editingShimmerColors ? "Shimmer Editor" : "Gradient Editor", contentX + 12, y + 12, TEXT, contentWidth - 24);
        int half = (contentWidth - 30) / 2;
        add(new PinkUi.Action(contentX + 12, y + 29, half, 25, editingShimmerColors ? "Sweep" : "Start", false, () -> selectEndpoint(0)).selected(() -> selectedEndpoint == 0));
        add(new PinkUi.Action(contentX + 18 + half, y + 29, half, 25, editingShimmerColors ? "Highlight" : "End", false, () -> selectEndpoint(1)).selected(() -> selectedEndpoint == 1));
        art.add(g -> {
            gradient(g, contentX + 12, y + 59, contentWidth - 24, 10, color(0), color(1));
            text(g, font, hex(color(0)), contentX + 12, y + 75, MUTED, half);
            text(g, font, hex(color(1)), contentX + contentWidth - 64, y + 75, MUTED, 62);
        });
        picker = add(new PinkUi.ColorPicker(contentX + 12, y + 96, contentWidth - 26, 88, selectedColor(), color -> {
            setSelectedColor(color);
            if (hexField != null) {
                changingColorText = true;
                hexField.setValue(hex(color));
                hexField.invalid(false);
                changingColorText = false;
            }
        }));
        label("Hue / Saturation", contentX + 12, y + 190, DIM, contentWidth - 70);
        label("Value", contentX + contentWidth - 38, y + 190, DIM, 30);
        int fieldWidth = Math.min(112, contentWidth / 2 - 10);
        hexField = input(contentX + 12, y + 208, fieldWidth, "HEX color", hex(selectedColor()), 7, value -> {
            if (changingColorText) return;
            boolean valid = value.matches("#?[0-9a-fA-F]{6}");
            hexField.invalid(!valid);
            if (valid) {
                int color = Integer.parseInt(value.replace("#", ""), 16);
                setSelectedColor(color); picker.setColor(color);
            }
        });
        int bx = contentX + fieldWidth + 18;
        add(new PinkUi.Action(bx, y + 208, contentX + contentWidth - bx - 12, 30, "Copy", false, () -> {
            setSelectedColor(color(1 - selectedEndpoint));
            rebuildWidgets();
        }));
        label("#RRGGBB", contentX + 12, y + 246, MUTED, contentWidth - 24);
        int w = (contentWidth - 6) / 2;
        add(new PinkUi.Action(contentX, rowY, w, 28, "Swap", false, () -> {
            int first = color(0); color(0, color(1)); color(1, first);
            changed(); rebuildWidgets();
        }));
        add(new PinkUi.Action(contentX + w + 6, rowY, w, 28, "Reset", false, () -> {
            color(0, editingShimmerColors ? 0xFFCF64 : 0xFFFFFF);
            color(1, editingShimmerColors ? 0xFFF0BB : 0xFFFFFF);
            changed(); rebuildWidgets();
        }));
        rowY += 38;
    }

    private void presets() {
        if (!pinnedNickname) { previewCard(); tabs(); }
        int y = card(80);
        label("Save Current Colors", contentX + 12, y + 11, PINK, contentWidth - 24);
        input(contentX + 12, y + 30, contentWidth - 24, "Preset Name", presetName, 40, value -> presetName = value);
        fullButton("Save / Update", () -> {
            try {
                com.customcosmetics.features.nickname.NicknamePresets.store().put(presetName,
                    com.customcosmetics.features.nickname.NicknameColors.capture());
                toast("Preset saved."); rebuildWidgets();
            } catch (java.io.IOException | IllegalArgumentException error) { toast(error.getMessage()); }
        }, true);
        var entries = com.customcosmetics.features.nickname.NicknamePresets.store().entries();
        if (entries.isEmpty()) { label("No saved presets", contentX + 12, rowY, MUTED, contentWidth - 24); rowY += 25; }
        for (var entry : entries.entrySet()) {
            int top = card(92), w = (contentWidth - 30) / 2;
            label(entry.getKey(), contentX + 12, top + 11, TEXT, contentWidth - 24);
            var colors = entry.getValue();
            label(colors.chromaEnabled() ? "Chroma" : switch (colors.style()) {
                case "shimmer" -> "Shimmer"; case "smooth" -> "Smooth"; default -> "Per Character";
            }, contentX + 12, top + 27, MUTED, contentWidth - 24);
            art.add(g -> gradient(g, contentX + 12, top + 43, contentWidth - 24, 5, colors.start(), colors.end()));
            add(new PinkUi.Action(contentX + 12, top + 57, w, 25, "Apply: " + entry.getKey(), false, () -> {
                colors.apply(); presetName = entry.getKey(); changed(); toast("Preset applied."); rebuildWidgets();
            }));
            add(new PinkUi.Action(contentX + 18 + w, top + 57, w, 25, "Delete: " + entry.getKey(), false, () -> {
                try { com.customcosmetics.features.nickname.NicknamePresets.store().remove(entry.getKey()); toast("Preset deleted."); rebuildWidgets(); }
                catch (java.io.IOException error) { toast(error.getMessage()); }
            }));
        }
    }

    private void chroma() {
        if (!pinnedNickname) { previewCard(); tabs(); }
        toggleRow("Chroma", "Overrides custom nick color.", () -> ConfigManager.nickTweaksChromaEnabled, v -> ConfigManager.nickTweaksChromaEnabled = v);
        sliderRow("Lightness", "", () -> ConfigManager.nickTweaksChromaLightness, 0.2, 1, 0.05, 0.8, v -> ConfigManager.nickTweaksChromaLightness = v);
        sliderRow("Chroma", "", () -> ConfigManager.nickTweaksChromaChroma, 0, 0.4, 0.01, 0.2, v -> ConfigManager.nickTweaksChromaChroma = v);
        sliderRow("Size", "", () -> ConfigManager.nickTweaksChromaSize, 0.5, 10, 0.1, 3.1, v -> ConfigManager.nickTweaksChromaSize = v);
        sliderRow("Speed", "", () -> ConfigManager.nickTweaksChromaSpeed, 0.1, 8, 0.1, 1, v -> ConfigManager.nickTweaksChromaSpeed = v);
    }

    private void sync() {
        toggleRow("Automatic Sync", "", () -> ConfigManager.syncEnabled, v -> ConfigManager.syncEnabled = v);
        int intervalY = card(82);
        label("Refresh Interval (seconds)", contentX + 12, intervalY + 11, TEXT, contentWidth - 24);
        refreshIntervalField = input(contentX + 12, intervalY + 28, contentWidth - 24, "Refresh Interval",
            Integer.toString(ConfigManager.getSyncRefreshIntervalSeconds()), 8, value -> {
                try {
                    int seconds = Integer.parseInt(value.trim());
                    boolean valid = seconds >= 30 && seconds <= 7200;
                    refreshIntervalField.invalid(!valid);
                    if (valid) { ConfigManager.syncRefreshIntervalSeconds = seconds; ConfigManager.requestSave(); }
                } catch (NumberFormatException e) { refreshIntervalField.invalid(true); }
            });
        label("30–7200 seconds (2 hours)", contentX + 12, intervalY + 65, MUTED, contentWidth - 24);
        toggleRow("Notifications", "Show sync results in chat.", () -> ConfigManager.syncNotifications, v -> ConfigManager.syncNotifications = v);
        syncNowButton = fullButton("Sync Now", () -> {
            if (minecraft.player == null) { toast("No active world"); return; }
            if (PresenceSync.syncOnce()) toast("Sync requested");
            updateSyncNowButton();
        }, true);
        updateSyncNowButton();
        rowY += 24;
        label("DEV",contentX,rowY,PINK,contentWidth); rowY += 20;
        var lines = font.split(Component.literal("If you don't know what these settings do, leave them blank."),contentWidth);
        for (var line : lines) { int y = rowY; art.add(g -> g.text(font,line,contentX,y,MUTED,false)); rowY += 12; }
        rowY += 8;
        proxyText("Proxy Host",ConfigManager.syncProxyHost,value -> ConfigManager.syncProxyHost = value);
        int y = card(64);
        label("Proxy Port",contentX + 12,y + 10,TEXT,contentWidth - 24);
        var port = input(contentX + 12,y + 28,contentWidth - 24,"Proxy Port",ConfigManager.syncProxyPort == 0 ? "" : Integer.toString(ConfigManager.syncProxyPort),5,value -> {});
        port.setResponder(value -> {
            activity();
            try { int parsed = value.isBlank() ? 0 : Integer.parseInt(value); boolean valid = parsed >= 0 && parsed <= 65535;
                port.invalid(!valid); if (valid) { ConfigManager.syncProxyPort = parsed; proxyChanged(); }
            } catch (NumberFormatException error) { port.invalid(true); }
        });
        var auth = proxyText("Proxy Auth",ConfigManager.syncProxyAuth,value -> ConfigManager.syncProxyAuth = value);
        auth.secret(true);
        toggleRow("Fallback Direct","Retry without the proxy if it fails.",() -> ConfigManager.syncProxyFallbackDirect,
            value -> { ConfigManager.syncProxyFallbackDirect = value; proxyChanged(); });
        add(new PinkUi.Action(contentX,rowY,contentWidth,28,"Proxy Source: " + ConfigManager.syncProxySource,false,() -> {
            var sources = List.of("none","manual","auto");
            ConfigManager.syncProxySource = sources.get((sources.indexOf(ConfigManager.syncProxySource) + 1) % sources.size());
            proxyChanged(); rebuildWidgets();
        })); rowY += 36;
    }

    private PinkUi.Field proxyText(String title,String value,Consumer<String> setter) {
        int y = card(64); label(title,contentX + 12,y + 10,TEXT,contentWidth - 24);
        return input(contentX + 12,y + 28,contentWidth - 24,title,value,256,text -> { setter.accept(text.strip()); proxyChanged(); });
    }
    private void proxyChanged() { changed(); com.customcosmetics.sync.PresenceProxyResolver.resetSession(); }
    private void credit() {
        int y = rowY + 4;
        art.add(g -> { g.pose().pushMatrix(); g.pose().translate(contentX,y); g.pose().scale(.8f,.8f);
            text(g,font,"Thanks to Baity / raueyhs.",0,0,DIM,(int)(contentWidth/.8f)); g.pose().popMatrix(); });
        rowY += 22;
    }
    private record NumericSetting(String title,DoubleSupplier getter,double min,double max,double step,double defaultValue,DoubleConsumer setter) {}
    private void sliderCell(NumericSetting setting,int x,int y,int width) {
        art.add(g -> outline(g,x,y,width,58,6,BORDER,CARD));
        PinkUi.Field[] number = new PinkUi.Field[1];
        number[0] = add(new PinkUi.Field(font,x + width - 56,y + 4,48,setting.title() + " value",22));
        number[0].setMaxLength(7); number[0].setValue(String.format(Locale.ROOT,"%.2f",setting.getter().getAsDouble()));
        var slider = add(new PinkUi.Slider(x + 8,y + 7,width - 16,setting.title(),setting.getter().getAsDouble(),setting.min(),setting.max(),setting.step(),value -> {
            setting.setter().accept(value); changed();
            if (number[0] != null && !number[0].isFocused()) number[0].setValue(String.format(Locale.ROOT,"%.2f",value));
        }).labelSpace(54));
        number[0].setResponder(value -> {
            activity(); if (!number[0].isFocused()) return;
            try { double parsed = Double.parseDouble(value); boolean valid = Double.isFinite(parsed) && parsed >= setting.min() && parsed <= setting.max();
                number[0].invalid(!valid); if (valid) slider.setActual(parsed);
            } catch (NumberFormatException error) { number[0].invalid(true); }
        });
        String hint = setting.title().equals("Swing Speed") ? "0 = Normal" : String.format(Locale.ROOT,"%.2f – %.2f",setting.min(),setting.max());
        label(hint,x + 8,y + 42,DIM,width - 60);
        add(new PinkUi.Action(x + width - 48,y + 36,40,18,"Reset",false,() -> { setting.setter().accept(setting.defaultValue()); changed(); rebuildWidgets(); }));
    }

    private void updateSyncNowButton() {
        if (syncNowButton == null) return;
        int remaining = PresenceSync.manualSyncCooldownSeconds();
        syncNowButton.active = !ConfigManager.offlineMode && remaining == 0;
        String label = remaining > 0 ? "Sync Now (" + remaining + "s)" : "Sync Now";
        if (!label.equals(syncNowButton.getMessage().getString())) syncNowButton.setMessage(Component.literal(label));
    }

    private void previewCard() {
        int y = card(68);
        label("Preview", contentX + 12, y + 12, PINK, contentWidth - 24);
        art.add(g -> {
            String playerName = minecraft.player == null ? "YourName" : minecraft.player.getName().getString();
            Component name = NickRenderUtils.previewNickname(playerName);
            float scale = Math.min(1.55f, (contentWidth - 32) / (float) Math.max(1, font.width(name)));
            g.pose().pushMatrix();
            g.pose().translate(contentX + 16, y + 35);
            g.pose().scale(scale, scale);
            g.text(font, name, 0, 0, TEXT, false);
            g.pose().popMatrix();
        });
    }

    private int card(int height) {
        int y = rowY;
        art.add(g -> outline(g, contentX, y, contentWidth, height, 8, BORDER, CARD));
        rowY += height + 10;
        return y;
    }
    private void label(String label, int x, int y, int color, int width) { art.add(g -> text(g, font, label, x, y, color, width)); }
    private void toggleRow(String title, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
        var lines = description.isEmpty() ? List.<net.minecraft.util.FormattedCharSequence>of() : font.split(Component.literal(description),contentWidth - 24);
        int y = card(description.isEmpty() ? 38 : 34 + lines.size() * 12);
        label(title, contentX + 12, y + (description.isEmpty() ? 15 : 11), TEXT, contentWidth - 68);
        for (int i = 0; i < lines.size(); i++) { var line = lines.get(i); int ty = y + 31 + i * 12; art.add(g -> g.text(font,line,contentX + 12,ty,MUTED,false)); }
        add(new PinkUi.Toggle(contentX + contentWidth - 46, y + (description.isEmpty() ? 10 : 8), title, getter, () -> { setter.accept(!getter.getAsBoolean()); changed(); }));
    }

    private void sliderRow(String title, String description, DoubleSupplier getter, double min, double max, double step, double defaultValue, DoubleConsumer setter) {
        int y = card(72);
        PinkUi.Field[] number = new PinkUi.Field[1];
        PinkUi.Slider slider = add(new PinkUi.Slider(contentX + 12, y + 12, contentWidth - 96, title, getter.getAsDouble(), min, max, step, value -> {
            setter.accept(value); changed();
            if (number[0] != null && !number[0].isFocused()) number[0].setValue(String.format(Locale.ROOT, "%.2f", value));
        }));
        number[0] = input(contentX + contentWidth - 72, y + 10, 60, title + " value", String.format(Locale.ROOT, "%.2f", getter.getAsDouble()), 7, value -> {
            if (number[0] == null || !number[0].isFocused()) return;
            try {
                double parsed = Double.parseDouble(value);
                boolean valid = Double.isFinite(parsed) && parsed >= min && parsed <= max;
                number[0].invalid(!valid);
                if (valid) slider.setActual(parsed);
            } catch (NumberFormatException e) { number[0].invalid(true); }
        });
        
        label(String.format(Locale.ROOT, "%.2f — %.2f", min, max), contentX + 12, y + 54, DIM, contentWidth - 90);
        add(new PinkUi.Action(contentX + contentWidth - 64, y + 44, 52, 22, "Reset", false, () -> {
            setter.accept(defaultValue); changed(); rebuildWidgets();
        }));
    }

    private PinkUi.Field input(int x, int y, int width, String name, String value, int maximum, Consumer<String> setter) {
        PinkUi.Field field = new PinkUi.Field(font, x, y, width, name);
        field.setMaxLength(maximum); field.setValue(value); field.setResponder(text -> { activity(); setter.accept(text); });
        return add(field);
    }
    private PinkUi.Action fullButton(String text, Runnable callback, boolean primary) {
        PinkUi.Action button = add(new PinkUi.Action(contentX, rowY, contentWidth, 30, text, primary, callback));
        rowY += 40;
        return button;
    }
    private <T extends AbstractWidget> T add(T widget) { controls.add(new Placement(widget, widget.getY())); return addWidget(widget); }
    private void open(Page page) { this.page = page == Page.MAIN ? Page.SMOL : page; scroll = 0; activity(); setFocused(null); rebuildWidgets(); }
    private void selectEndpoint(int index) { selectedEndpoint = index; rebuildWidgets(); }
    private int color(int endpoint) {
        if (editingShimmerColors) return endpoint == 0 ? ConfigManager.nickTweaksShimmerColor : ConfigManager.nickTweaksShimmerHighlightColor;
        return endpoint == 0 ? ConfigManager.nickTweaksGradientStartColor : ConfigManager.nickTweaksGradientEndColor;
    }
    private void color(int endpoint, int color) {
        if (editingShimmerColors) {
            if (endpoint == 0) ConfigManager.nickTweaksShimmerColor = color; else ConfigManager.nickTweaksShimmerHighlightColor = color;
        } else {
            if (endpoint == 0) ConfigManager.nickTweaksGradientStartColor = color; else ConfigManager.nickTweaksGradientEndColor = color;
        }
    }
    private int selectedColor() { return color(selectedEndpoint); }
    private void setSelectedColor(int color) { color(selectedEndpoint, color); changed(); }
    private static String hex(int color) { return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF); }
    private void activity() { SESSION.interact(page.name(),scroll,System.currentTimeMillis()); }
    static void childInteraction() { SESSION.touch(System.currentTimeMillis()); }
    private void changed() { activity(); ConfigManager.requestSave(); NickRenderUtils.invalidateLocalTargetsCache(); }
    private void toast(String text) { notice = text; noticeUntil = System.currentTimeMillis() + 3500; }
    private double maxScroll() { return Math.max(0, contentBottom - viewBottom); }
    private void positionControls() {
        for (Placement p : controls) {
            AbstractWidget w = p.widget();
            w.setY(p.y() - (int) Math.round(scroll));
            int inset = w instanceof PinkUi.Field field ? field.insetY() : 0;
            w.visible = w.getBottom() + inset > viewTop && w.getY() - inset < viewBottom;
        }
    }

    @Override public void tick() { updateSyncNowButton(); }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        activity();
        if (blockingMode != null) blockingMode.closeMenu();
        if (blockingStyle != null) blockingStyle.closeMenu();
        if (mx < contentX || mx > contentX + contentWidth + 10 || my < viewTop || my > viewBottom) return super.mouseScrolled(mx, my, horizontal, vertical);
        scroll = Math.clamp(scroll - vertical * 27, 0, maxScroll()); positionControls();
        if (getFocused() instanceof AbstractWidget widget && !widget.visible) setFocused(null);
        return true;
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (blockingStyle != null && blockingStyle.menuClick(event)) { activity(); return true; }
        if (blockingMode != null && blockingMode.menuClick(event)) { activity(); return true; }
        boolean closing = event.button() == ClientInput.MOUSE_LEFT && event.x() >= panelX + panelWidth - 32
            && event.x() < panelX + panelWidth - 12 && event.y() >= panelY + 15 && event.y() < panelY + 35;
        if (!closing) activity();
        positionControls();
        if (event.button() == com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT && maxScroll() > 0 && event.x() >= contentX + contentWidth + 2
                && event.x() <= contentX + contentWidth + 12 && event.y() >= viewTop && event.y() <= viewBottom) {
            draggingScroll = true;
            scrollToMouse(event.y());
            return true;
        }
        if (event.y() < viewTop || event.y() >= viewBottom) {
            for (Placement placement : controls) placement.widget().visible = false;
        }
        try { return super.mouseClicked(event, doubleClick); }
        finally { positionControls(); }
    }

    private void scrollToMouse(double y) {
        int track = viewBottom - viewTop;
        int thumb = Math.max(20, (int) (track * track / (double) (track + maxScroll())));
        scroll = Math.clamp((y - viewTop - thumb / 2.0) / Math.max(1, track - thumb), 0, 1) * maxScroll();
        positionControls();
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        activity();
        if (draggingScroll) { scrollToMouse(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScroll) { draggingScroll = false; return true; }
        return super.mouseReleased(event);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (blockingStyle != null && blockingStyle.menuKey(event)) { activity(); return true; }
        if (blockingMode != null && blockingMode.menuKey(event)) { activity(); return true; }
        if (event.key() != ClientInput.ESCAPE) activity();
        boolean handled = super.keyPressed(event);
        if (getFocused() instanceof AbstractWidget focused) {
            for (Placement placement : controls) {
                if (placement.widget() != focused) continue;
                int inset = focused instanceof PinkUi.Field field ? field.insetY() : 0;
                if (focused.getY() - inset < viewTop) scroll -= viewTop - focused.getY() + inset;
                else if (focused.getBottom() + inset > viewBottom) scroll += focused.getBottom() + inset - viewBottom;
                scroll = Math.clamp(scroll, 0, maxScroll());
                positionControls();
                break;
            }
        }
        return handled;
    }
    @Override public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float dt) {
        if (minecraft.level == null) super.extractBackground(g, mx, my, dt);
        else g.blurBeforeThisStratum();
        g.fill(0, 0, width, height, 0x4020242A);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float dt) {
        mouseX = mx; mouseY = my;
        NickRenderUtils.beginClickGuiRenderScope();
        try {
            rounded(g, panelX - 5, panelY + 5, panelWidth + 10, panelHeight + 4, 14, 0x60000000);
            outline(g, panelX, panelY, panelWidth, panelHeight, 11, BORDER, PANEL);
            rounded(g, panelX + 1, panelY + 1, sideWidth, panelHeight - 2, 10, SIDEBAR);
            g.fill(panelX + sideWidth, panelY + 1, panelX + sideWidth + 1, panelY + panelHeight - 1, BORDER);
            text(g, font, "Custom", panelX + 16, panelY + 22, PINK, sideWidth - 30);
            int cosmeticsX = Math.max(panelX + 16, panelX + sideWidth - 14 - font.width("Cosmetics"));
            text(g, font, "Cosmetics", cosmeticsX, panelY + 35, PINK, panelX + sideWidth - 14 - cosmeticsX);
            text(g, font, pageTitle(), contentX, panelY + 23, TEXT, contentWidth - 24);
            for (Consumer<GuiGraphicsExtractor> draw : pinnedArt) draw.accept(g);
            for (AbstractWidget widget : navigation) widget.extractRenderState(g, mx, my, dt);

            positionControls();
            g.enableScissor(contentX - 1, viewTop, contentX + contentWidth + 1, viewBottom);
            g.pose().pushMatrix();
            g.pose().translate(0, -(float) Math.round(scroll));
            for (Consumer<GuiGraphicsExtractor> draw : art) draw.accept(g);
            g.pose().popMatrix();
            for (Placement p : controls) if (p.widget().visible) p.widget().extractRenderState(g, mx, my, dt);
            g.disableScissor();
            if (maxScroll() > 0) {
                int track = viewBottom - viewTop;
                int thumb = Math.max(20, (int) (track * track / (double) (track + maxScroll())));
                int y = viewTop + (int) ((track - thumb) * scroll / maxScroll());
                rounded(g, contentX + contentWidth + 6, viewTop, 3, track, 1, BORDER);
                rounded(g, contentX + contentWidth + 6, y, 3, thumb, 1, PINK);
            }
            if (System.currentTimeMillis() < noticeUntil) {
                int tw = Math.min(contentWidth, font.width(notice) + 24);
                int x = contentX + (contentWidth - tw) / 2;
                outline(g, x, viewBottom - 34, tw, 28, 7, PINK_DARK, 0xEF292B32);
                text(g, font, notice, x + 12, viewBottom - 24, PINK, tw - 24);
            }
            if (blockingMode != null) blockingMode.drawMenu(g, mx, my, viewTop, viewBottom);
            if (blockingStyle != null) blockingStyle.drawMenu(g, mx, my, viewTop, viewBottom);
        } finally { NickRenderUtils.endClickGuiRenderScope(); }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() { SESSION.remember(page.name(),scroll); ConfigManager.saveConfig(); PresenceSync.onClickGuiClosed(); }
    @Override public void onClose() { com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft, parent); }
}
