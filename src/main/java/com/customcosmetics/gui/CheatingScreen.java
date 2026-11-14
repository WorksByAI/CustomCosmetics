// SPDX-License-Identifier: AGPL-3.0-or-later
package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.features.gameplay.GameplayModule;
import com.customcosmetics.features.gameplay.GameplayKeybinds;
import com.customcosmetics.features.gameplay.AutoClicker;
import com.customcosmetics.features.gameplay.SafariSpecies;
import com.customcosmetics.features.gameplay.TrevorHelper;
import com.customcosmetics.features.gameplay.KuudraPerks;
import com.customcosmetics.features.gameplay.WorldScannerSettings;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import static com.customcosmetics.gui.PinkUi.*;

final class CheatingScreen extends ThemedScreen {
    private static final GameplayModule[] MODULES = Arrays.stream(GameplayModule.values())
        .sorted(Comparator.comparing(module -> module.label)).toArray(GameplayModule[]::new);
    private final Screen parent;
    private final ArrayList<Card> cards = new ArrayList<>();
    private GameplayModule.Category category = GameplayModule.Category.GENERAL;
    private GameplayModule editing;
    private StudioLayout layout;
    private String query = "";
    private PinkUi.Field search;
    private int scroll, cardsTop, cardsBottom;
    private int detailsTop, detailsY, detailsHeight;
    private int editingRow, revealRow, contentHeight;
    private String leapPreset = "", leapPresetNotice = "";
    private final ArrayList<Detail> details = new ArrayList<>();
    private final ArrayList<DetailLabel> detailLabels = new ArrayList<>();
    private boolean recording;
    private PinkUi.Action keyButton;
    private net.minecraft.client.KeyMapping recordedMapping;
    private SafariSpecies.Biome safariBiome = SafariSpecies.Biome.FOREST;
    private WorldScannerSettings.Target scannerTarget = WorldScannerSettings.Target.FAIRY_GROTTO;
    private KuudraPerks.Group kuudraPerkGroup = KuudraPerks.Group.BASIC;

    private record Card(GameplayModule module, PinkUi.Action toggle, PinkUi.Action settings, int y) {}
    private record Detail(net.minecraft.client.gui.components.AbstractWidget widget, int y) {}
    private record DetailLabel(String title, int y) {}

    static void open(Screen parent) {
        var client = Minecraft.getInstance();
        if (ConfigManager.gameplayPanelConfirmed) {
            ClientUiAccess.setScreen(client, new CheatingScreen(parent));
            return;
        }
        long deadline = System.nanoTime() + 3_000_000_000L;
        ClientUiAccess.setScreen(client, new Confirmation(parent, deadline));
    }

    private static final class Confirmation extends ThemedScreen {
        private final Screen parent;
        private final long deadline;
        private PinkUi.Action accept;
        private int x, y, w;

        Confirmation(Screen parent, long deadline) {
            super(Component.literal("Cheating"));
            this.parent = parent;
            this.deadline = deadline;
        }

        @Override protected void init() {
            w = Math.min(360, width - 16);
            x = (width - w) / 2;
            y = height / 2 - 52;
            int half = (w - 30) / 2;
            addRenderableWidget(new PinkUi.Action(x + 12, y + 70, half, 24, "Cancel", false, this::onClose));
            accept = addRenderableWidget(new PinkUi.Action(x + 18 + half, y + 70, half, 24, "I understand", true, () -> {
                if (System.nanoTime() - deadline < 0) return;
                ConfigManager.gameplayPanelConfirmed = true;
                ConfigManager.saveConfig();
                ClientUiAccess.setScreen(minecraft, new CheatingScreen(parent));
            }));
            updateAccept();
        }

        private void updateAccept() {
            long remaining = deadline - System.nanoTime();
            int seconds = remaining <= 0 ? 0 : (int) Math.ceil(remaining / 1_000_000_000.0);
            accept.active = seconds == 0;
            String label = UiText.tr("I understand") + (seconds == 0 ? "" : " (" + seconds + ")");
            if (!accept.getMessage().getString().equals(label)) accept.setMessage(Component.literal(label));
        }

        @Override public void tick() { if (!accept.active) updateAccept(); }

        @Override public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
            outline(graphics, x, y, w, 106, 5, BORDER, PANEL);
            UiFonts.drawTitle(graphics, UiText.tr("Cheating"), x + 14, y + 13, TEXT, w - 28);
            String message = UiText.tr("Using these features may get your account banned. The mod developers accept no responsibility for the consequences.");
            int lineY = y + 32;
            for (var line : font.split(Component.literal(message), w - 28)) {
                graphics.text(font, line, x + 14, lineY, MUTED, false);
                lineY += 10;
            }
            graphics.nextStratum();
            super.extractRenderState(graphics, mouseX, mouseY, delta);
        }

        @Override public boolean isPauseScreen() { return false; }
        @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }
    }

    private CheatingScreen(Screen parent) {
        super(Component.literal("Cheating"));
        this.parent = parent;
    }

    @Override protected void init() {
        layout = StudioLayout.of(width, height);
        cards.clear();
        details.clear();
        detailLabels.clear();
        addRenderableWidget(new PinkUi.Action(layout.x() + layout.width() - 32, layout.y() + 12,
            20, 20, "×", false, this::onClose));
        int row = layout.y() + 44;
        int categoryStep = Math.clamp((layout.height() - 86) / GameplayModule.Category.values().length, 12, 26);
        for (var option : GameplayModule.Category.values()) {
            var button = addRenderableWidget(new PinkUi.Action(layout.x() + 9, row, layout.sidebarWidth() - 18, categoryStep - 2,
                option.label, false, () -> {
                    category = option;
                    query = "";
                    editing = null;
                    scroll = 0;
                    rebuildWidgets();
                }).leftAligned().selected(() -> option == category));
            button.active = option != GameplayModule.Category.KUUDRA;
            row += categoryStep;
        }
        addRenderableWidget(new PinkUi.Action(layout.x() + 9, layout.y() + layout.height() - 35,
            layout.sidebarWidth() - 18, 24, "HUD Editor", false, () -> {
                ClientUiAccess.setScreen(minecraft, new GameplayHudEditor(this));
            }));
        search = new PinkUi.Field(font, layout.contentX(), layout.y() + 42, layout.contentWidth(), "Search modules…", 23);
        search.setMaxLength(64);
        search.setHint(UiText.component("Search modules…"));
        search.setValue(query);
        search.setResponder(value -> {
            query = value;
            editing = null;
            recording = false;
            recordedMapping = null;
            scroll = 0;
            rebuildWidgets();
            setFocused(search);
            search.setFocused(true);
            search.moveCursorToEnd(false);
        });
        addRenderableWidget(search);
        cardsTop = layout.y() + 73;
        cardsBottom = layout.contentBottom();
        String filter = query.strip().toLowerCase(Locale.ROOT);
        int gap = 6;
        int column = (layout.contentWidth() - 2 * gap) / 3;
        int settingsWidth = Math.min(24, Math.max(8, column / 3));
        int count = 0;
        editingRow = -1;
        detailsHeight = 0;
        for (var module : MODULES) {
            if (filter.isEmpty() ? module.category != category
                : !module.label.toLowerCase(Locale.ROOT).contains(filter) && !UiText.zh(module.label).contains(filter)) continue;
            int x = layout.contentX() + count % 3 * (column + gap);
            int y = cardsTop + count / 3 * 39;
            var toggle = new PinkUi.Action(x, y, column - settingsWidth, 34, module.label, false, module::toggle)
                .selected(module::enabled);
            toggle.setTooltip(net.minecraft.client.gui.components.Tooltip.create(UiText.component(module.label)));
            var settings = new PinkUi.Action(x + column - settingsWidth, y, settingsWidth, 34, editing == module ? "⌃" : "⚙", false,
                () -> showSettings(module)).selected(() -> editing == module);
            addWidget(toggle);
            addWidget(settings);
            cards.add(new Card(module, toggle, settings, y));
            if (module == editing) editingRow = y;
            count++;
        }
        if (editingRow != -1) addDetails();
        contentHeight = (count + 2) / 3 * 39 + detailsHeight;
        if (revealRow > cardsTop) scroll = Math.max(scroll, revealRow - cardsTop);
        revealRow = 0;
        positionCards();
    }

    private void showSettings(GameplayModule module) {
        recording = false;
        recordedMapping = null;
        editing = editing == module ? null : module;
        if (editing != null) {
            for (var card : cards) if (card.module() == module && card.toggle().getBottom() + 70 > cardsBottom) {
                revealRow = card.y();
                break;
            }
        }
        rebuildWidgets();
    }

    private void addDetails() {
        detailsTop = editingRow + 39;
        detailsY = detailsTop + 7;
        int x = layout.contentX() + 7, y = detailsY, w = detailWidth();
        int clearWidth = Math.min(68, w / 3);
        keyButton = new PinkUi.Action(x, y, w - clearWidth - 6, 20,
            keyLabel(), false, () -> {
                recording = true;
                recordedMapping = null;
                keyButton.setMessage(UiText.component("Press a key or mouse button…"));
            }).leftAligned();
        addDetail(keyButton);
        addDetail(new PinkUi.Action(x + w - clearWidth, y, clearWidth, 20, "Clear", false,
            () -> finishRecording(InputConstants.UNKNOWN)));
        y = detailsY;
        switch (editing) {
            case FAST_LEAP -> fastLeapSettings();
            case AUTO_CLICKER -> autoClickerSettings();
            case AUTO_EXPERIMENTS -> {
                slider("Min Click Delay (ms)", ConfigManager.experimentMinDelay, 100, 1000, 1,
                    v -> ConfigManager.experimentMinDelay = (int) v);
                slider("Max Click Delay (ms)", ConfigManager.experimentMaxDelay, 100, 1000, 1,
                    v -> ConfigManager.experimentMaxDelay = (int) v);
                option("Auto Close", () -> ConfigManager.experimentAutoClose, v -> ConfigManager.experimentAutoClose = v);
                slider("Serums Applied", ConfigManager.experimentSerums, 0, 3, 1, v -> ConfigManager.experimentSerums = (int) v);
                option("Get Max", () -> ConfigManager.experimentGetMax, v -> ConfigManager.experimentGetMax = v);
                if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("skyhanni")) {
                    detailLabels.add(new DetailLabel(UiText.tr("Turn off SkyHanni’s experiment solver when using Auto Experiments."), detailsY));
                    detailsY += 17;
                }
            }
            case AUTO_LEAP -> autoLeapSettings();
            case TREVOR_HELPER -> trevorSettings();
            case DOJO -> dojoSettings();
            case CHOCOLATE_FACTORY -> {
                option("Click Factory", () -> ConfigManager.chocolateClick, v -> ConfigManager.chocolateClick = v);
                option("Auto Time Tower", () -> ConfigManager.chocolateTower, v -> ConfigManager.chocolateTower = v);
                option("Auto Upgrade", () -> ConfigManager.chocolateUpgrade, v -> ConfigManager.chocolateUpgrade = v);
                slider("Delay (ms)", ConfigManager.chocolateDelay, 50, 1500, 5, v -> ConfigManager.chocolateDelay = (int) v);
                slider("Upgrade Delay (ms)", ConfigManager.chocolateUpgradeDelay, 300, 2000, 100, v -> ConfigManager.chocolateUpgradeDelay = (int) v);
                option("Claim Strays", () -> ConfigManager.chocolateStrays, v -> ConfigManager.chocolateStrays = v);
                option("Mute Factory Sound", () -> ConfigManager.chocolateMute, v -> ConfigManager.chocolateMute = v);
                option("Egg ESP", () -> ConfigManager.chocolateEggs, v -> ConfigManager.chocolateEggs = v);
            }
            case AUTO_CARNIVAL -> slider("Click Delay (ms)", ConfigManager.carnivalShotDelay, 50, 500, 50, v -> ConfigManager.carnivalShotDelay = (int) v);
            case MIRRORVERSE -> {
                option("Lava Maze", () -> ConfigManager.mirrorverseLavaMaze, v -> ConfigManager.mirrorverseLavaMaze = v);
                option("Lava Parkour", () -> ConfigManager.mirrorverseLavaParkour, v -> ConfigManager.mirrorverseLavaParkour = v);
                option("Tubulator", () -> ConfigManager.mirrorverseTubulator, v -> ConfigManager.mirrorverseTubulator = v);
                option("Show Mirrored Mobs", () -> ConfigManager.mirrorverseShowMobs, v -> ConfigManager.mirrorverseShowMobs = v);
                option("Show Crafting Recipes", () -> ConfigManager.mirrorverseShowRecipes, v -> ConfigManager.mirrorverseShowRecipes = v);
                option("Red / Green", () -> ConfigManager.mirrorverseRedGreen, v -> ConfigManager.mirrorverseRedGreen = v);
                slider("Action Delay (ticks)", ConfigManager.mirrorverseRedGreenActionDelay, 2, 5, 1, v -> ConfigManager.mirrorverseRedGreenActionDelay = (int) v);
                slider("Interaction Delay (ticks)", ConfigManager.mirrorverseRedGreenInteractDelay, 0, 5, 1, v -> ConfigManager.mirrorverseRedGreenInteractDelay = (int) v);
                option("Tiny Dancer", () -> ConfigManager.mirrorverseTinyDancer, v -> ConfigManager.mirrorverseTinyDancer = v);
                slider("Sneak Delay (ticks)", ConfigManager.mirrorverseSneakDelay, 0, 20, 1, v -> ConfigManager.mirrorverseSneakDelay = (int) v);
                slider("Jump Delay (ticks)", ConfigManager.mirrorverseJumpDelay, 0, 20, 1, v -> ConfigManager.mirrorverseJumpDelay = (int) v);
                slider("Punch Delay (ticks)", ConfigManager.mirrorversePunchDelay, 0, 20, 1, v -> ConfigManager.mirrorversePunchDelay = (int) v);
            }
            case GREAT_SPOOK -> {
                option("Auto Math", () -> ConfigManager.spookMath, v -> ConfigManager.spookMath = v);
                option("Auto Public Speaking", () -> ConfigManager.spookSpeaking, v -> ConfigManager.spookSpeaking = v);
                option("Auto Commitment Phobia", () -> ConfigManager.spookCommitment, v -> ConfigManager.spookCommitment = v);
                slider("Answer Delay (ticks)", ConfigManager.spookDelay, 0, 10, 1, v -> ConfigManager.spookDelay = (int) v);
                option("Spawn Title", () -> ConfigManager.spookTitle, v -> ConfigManager.spookTitle = v);
                option("Show in Dungeons", () -> ConfigManager.spookDungeon, v -> ConfigManager.spookDungeon = v);
            }
            case SECRET_AURA -> secretAuraSettings();
            case SECRET_TRIGGERBOT -> {
                option("Item Swapping", () -> ConfigManager.secretSwapItem, v -> ConfigManager.secretSwapItem = v);
                slider("Swap Slot", ConfigManager.secretSwapSlot, 1, 9, 1, v -> ConfigManager.secretSwapSlot = (int) v);
                option("Swap Back", () -> ConfigManager.secretSwapBack, v -> ConfigManager.secretSwapBack = v);
                slider("Interact Delay (ticks)", ConfigManager.secretInteractDelay, 0, 5, 1, v -> ConfigManager.secretInteractDelay = (int) v);
            }
            case AUTO_BOOK_COMBINE -> {
                slider("Anvil Result Delay (ticks)", ConfigManager.bookCombineResultDelay, 2, 40, 1, v -> ConfigManager.bookCombineResultDelay = (int) v);
                option("Disable After Combining", () -> ConfigManager.bookCombineDisableAfterFinish, v -> ConfigManager.bookCombineDisableAfterFinish = v);
                option("Close Anvil After Combining", () -> ConfigManager.bookCombineCloseAfterFinish, v -> ConfigManager.bookCombineCloseAfterFinish = v);
            }
            case KUUDRA_PEEK -> {
                option("Check Y Level", () -> ConfigManager.kuudraPeekCheckHeight, v -> ConfigManager.kuudraPeekCheckHeight = v);
                option("Only Show First Peek", () -> ConfigManager.kuudraPeekFirstOnly, v -> ConfigManager.kuudraPeekFirstOnly = v);
            }
            case MANA_USED_ALERT -> option("Ignore if 0 Players", () -> ConfigManager.kuudraManaIgnoreZero,
                v -> ConfigManager.kuudraManaIgnoreZero = v);
            case KUUDRA_REQUEUE -> slider("Requeue Delay (ticks)", ConfigManager.kuudraRequeueDelay, 0, 100, 1,
                v -> ConfigManager.kuudraRequeueDelay = (int) v);
            case KUUDRA_AUTO_PEARL -> slider("Pearl Click Delay (ticks)", ConfigManager.kuudraPearlDelay, 1, 2, 1,
                v -> ConfigManager.kuudraPearlDelay = (int) v);
            case KUUDRA_TEAMMATES -> {
                slider("Line Width", ConfigManager.kuudraTeammateWidth, 1, 10, 1, v -> ConfigManager.kuudraTeammateWidth = (int) v);
                color("Highlight Color", ConfigManager.kuudraTeammateColor, v -> ConfigManager.kuudraTeammateColor = v);
            }
            case KUUDRA_SUPPLY_WAYPOINTS -> {
                option("Drop Off", () -> ConfigManager.kuudraSupplyDropOff, v -> ConfigManager.kuudraSupplyDropOff = v);
                color("Drop Off Color", ConfigManager.kuudraDropOffColor, v -> ConfigManager.kuudraDropOffColor = v);
                option("Pick Up", () -> ConfigManager.kuudraSupplyPickup, v -> ConfigManager.kuudraSupplyPickup = v);
                color("Pick Up Color", ConfigManager.kuudraPickupColor, v -> ConfigManager.kuudraPickupColor = v);
                option("Fuel", () -> ConfigManager.kuudraFuel, v -> ConfigManager.kuudraFuel = v);
                color("Fuel Color", ConfigManager.kuudraFuelColor, v -> ConfigManager.kuudraFuelColor = v);
                option("Detect Player Proximity", () -> ConfigManager.kuudraSupplyProximity, v -> ConfigManager.kuudraSupplyProximity = v);
                color("Nearby Color", ConfigManager.kuudraNearbyColor, v -> ConfigManager.kuudraNearbyColor = v);
                option("Custom Supply Messages", () -> ConfigManager.kuudraSupplyMessages, v -> ConfigManager.kuudraSupplyMessages = v);
                textSetting("Supply Text Style", ConfigManager.kuudraSupplyMessage, v -> ConfigManager.kuudraSupplyMessage = v);
            }
            case KUUDRA_TITLES -> {
                option("Supply Progress HUD", () -> ConfigManager.kuudraSupplyHud, v -> ConfigManager.kuudraSupplyHud = v);
                textSetting("Supply Progress Format", ConfigManager.kuudraTitleFormat, v -> ConfigManager.kuudraTitleFormat = v);
                textSetting("Filled Bar Character", ConfigManager.kuudraTitleFilled, v -> ConfigManager.kuudraTitleFilled = v);
                textSetting("Empty Bar Character", ConfigManager.kuudraTitleEmpty, v -> ConfigManager.kuudraTitleEmpty = v);
                slider("Number of Bars", ConfigManager.kuudraTitleBars, 5, 30, 1, v -> ConfigManager.kuudraTitleBars = (int) v);
                option("Drop Alert", () -> ConfigManager.kuudraDropAlert, v -> ConfigManager.kuudraDropAlert = v);
                textSetting("Drop Alert Message", ConfigManager.kuudraDropText, v -> ConfigManager.kuudraDropText = v);
                option("Pickup Alert", () -> ConfigManager.kuudraPickupAlert, v -> ConfigManager.kuudraPickupAlert = v);
                textSetting("Pickup Alert Message", ConfigManager.kuudraPickupText, v -> ConfigManager.kuudraPickupText = v);
                addDetail(new PinkUi.Action(x, detailsY, detailWidth(), 23, "HUD Editor", false,
                    () -> ClientUiAccess.setScreen(minecraft, new GameplayHudEditor(this))));
            }
            case KUUDRA_TIMERS -> {
                option("Supply Spawn Timer", () -> ConfigManager.kuudraSpawnTimer, v -> ConfigManager.kuudraSpawnTimer = v);
                textSetting("Supply Timer Format", ConfigManager.kuudraSpawnFormat, v -> ConfigManager.kuudraSpawnFormat = v);
                option("Build Start Timer", () -> ConfigManager.kuudraBuildTimer, v -> ConfigManager.kuudraBuildTimer = v);
                textSetting("Build Timer Format", ConfigManager.kuudraBuildFormat, v -> ConfigManager.kuudraBuildFormat = v);
                addDetail(new PinkUi.Action(x, detailsY, detailWidth(), 23, "HUD Editor", false,
                    () -> ClientUiAccess.setScreen(minecraft, new GameplayHudEditor(this))));
            }
            case KUUDRA_STUN_HELPER -> {
                option("Highlight Pods", () -> ConfigManager.kuudraHighlightPods, v -> ConfigManager.kuudraHighlightPods = v);
                option("Highlight Exact Block", () -> ConfigManager.kuudraHighlightExactPod, v -> ConfigManager.kuudraHighlightExactPod = v);
                cycle("Exact Pod", java.util.List.of("Left", "Middle", "Right"), () -> ConfigManager.kuudraPod, v -> ConfigManager.kuudraPod = v);
                color("Pod Color", ConfigManager.kuudraPodColor, v -> ConfigManager.kuudraPodColor = v);
                option("Depth Check", () -> ConfigManager.kuudraPodDepth, v -> ConfigManager.kuudraPodDepth = v);
                option("Block Pickaxe Ability", () -> ConfigManager.kuudraBlockPickobulus, v -> ConfigManager.kuudraBlockPickobulus = v);
                cycle("Block When", java.util.List.of("Outside Belly", "Wrong Aim Inside Belly", "Both"),
                    () -> ConfigManager.kuudraBlockWhen, v -> ConfigManager.kuudraBlockWhen = v);
                clickKey("Block Override Key", com.customcosmetics.features.gameplay.KuudraStunHelper.overrideKey());
                option("Auto Close Perk Menu", () -> ConfigManager.kuudraStunCloseMenu, v -> ConfigManager.kuudraStunCloseMenu = v);
                slider("Close Delay (ticks)", ConfigManager.kuudraStunCloseDelay, 0, 5, 1, v -> ConfigManager.kuudraStunCloseDelay = (int) v);
            }
            case KUUDRA_BLOCK_PERKS -> {
                option("Hide Blocked Perks", () -> ConfigManager.kuudraHideBlockedPerks, value -> ConfigManager.kuudraHideBlockedPerks = value);
                clickKey("Override Key", KuudraPerks.overrideKey());
                var groups = KuudraPerks.Group.values();
                cycle("Blocked Perks", java.util.Arrays.stream(groups).map(group -> group.title).toList(),
                    () -> kuudraPerkGroup.ordinal(), value -> kuudraPerkGroup = groups[value]);
                for (String perk : kuudraPerkGroup.perks) option(perk, () -> ConfigManager.kuudraBlockedPerks.contains(perk),
                    value -> ConfigManager.kuudraBlockedPerks = membership(ConfigManager.kuudraBlockedPerks, perk, value));
            }
            case COCOON_ALERT -> {
                option("Show Alert", () -> ConfigManager.cocoonAlert, v -> ConfigManager.cocoonAlert = v);
                textSetting("Cocoon Alert Message", ConfigManager.cocoonAlertText, v -> ConfigManager.cocoonAlertText = v);
                textSetting("Cocoon Sound ID", ConfigManager.cocoonAlertSound, v -> ConfigManager.cocoonAlertSound = v);
                option("Cocoon Timer", () -> ConfigManager.cocoonTimer, v -> ConfigManager.cocoonTimer = v);
                addDetail(new PinkUi.Action(x, detailsY, detailWidth(), 23, "HUD Editor", false,
                    () -> ClientUiAccess.setScreen(minecraft, new GameplayHudEditor(this))));
            }
            case SLAYER_MINIBOSS_ALERT -> {
                option("Detect From Chat", () -> ConfigManager.minibossChatDetection, v -> ConfigManager.minibossChatDetection = v);
                option("Send Message", () -> ConfigManager.minibossChatAlert, v -> ConfigManager.minibossChatAlert = v);
                option("Keep Original Chat Message", () -> ConfigManager.minibossVanillaMessage, v -> ConfigManager.minibossVanillaMessage = v);
                option("Show Title", () -> ConfigManager.minibossTitle, v -> ConfigManager.minibossTitle = v);
                slider("Maximum Distance", ConfigManager.minibossDistance, 1, 15, 1, v -> ConfigManager.minibossDistance = (int) v);
                textSetting("Alert Text", ConfigManager.minibossAlertText, v -> ConfigManager.minibossAlertText = v);
                textSetting("Special Text", ConfigManager.minibossSpecialText, v -> ConfigManager.minibossSpecialText = v);
            }
            case BEACON_ESP -> {
                option("Show Outline", () -> ConfigManager.beaconOutline, v -> ConfigManager.beaconOutline = v);
                option("Show Fill", () -> ConfigManager.beaconFill, v -> ConfigManager.beaconFill = v);
                option("Depth Check", () -> ConfigManager.beaconDepth, v -> ConfigManager.beaconDepth = v);
                option("Show Tracer", () -> ConfigManager.beaconTracer, v -> ConfigManager.beaconTracer = v);
                color("Outline Color", ConfigManager.beaconColor, v -> ConfigManager.beaconColor = v);
                color("Fill Color", ConfigManager.beaconFillColor, v -> ConfigManager.beaconFillColor = v);
                color("Tracer Color", ConfigManager.beaconTracerColor, v -> ConfigManager.beaconTracerColor = v);
                slider("Line Width", ConfigManager.beaconLineWidth, 1, 10, 1, v -> ConfigManager.beaconLineWidth = (int) v);
                slider("Tracer Width", ConfigManager.beaconTracerWidth, 1, 10, 1, v -> ConfigManager.beaconTracerWidth = (int) v);
            }
            case TRAJECTORIES -> trajectorySettings();
            case AUTO_GFS -> {
                option("Ender Pearls", () -> ConfigManager.refillPearls, v -> ConfigManager.refillPearls = v);
                option("Superboom TNT", () -> ConfigManager.refillSuperboom, v -> ConfigManager.refillSuperboom = v);
                option("Inflatable Jerries", () -> ConfigManager.refillJerries, v -> ConfigManager.refillJerries = v);
                option("Spirit Leaps", () -> ConfigManager.refillLeaps, v -> ConfigManager.refillLeaps = v);
                cycle("Refill Mode", java.util.List.of("Amount", "Time"), () -> ConfigManager.refillTimed ? 1 : 0,
                    v -> ConfigManager.refillTimed = v == 1);
                if (ConfigManager.refillTimed) slider("Refill Interval (seconds)", ConfigManager.refillIntervalSeconds, 1, 60, 1,
                    v -> ConfigManager.refillIntervalSeconds = (int) v);
                else slider("Refill Threshold (%)", ConfigManager.refillPercent, 5, 95, 5, v -> ConfigManager.refillPercent = (int) v);
                option("Dungeon Only", () -> ConfigManager.refillDungeonsOnly, v -> ConfigManager.refillDungeonsOnly = v);
            }
            case CRYSTAL_PICKUP, OPEN_TERMINALS, RELICS, ARROW_ALIGN, SIMON_SAYS -> deviceSettings();
            case AUTO_DOOR -> {
                option("Open Nearby Doors", () -> ConfigManager.doorAura, v -> ConfigManager.doorAura = v);
                slider("Nearby Door Range", ConfigManager.doorAuraRange, 2, 6, .1, v -> ConfigManager.doorAuraRange = v);
                option("Swing Hand", () -> ConfigManager.doorSwing, v -> ConfigManager.doorSwing = v);
                option("Run in Menus", () -> ConfigManager.doorInMenus, v -> ConfigManager.doorInMenus = v);
                slider("Retry Delay (ms)", ConfigManager.doorRetryMs, 100, 2000, 50, v -> ConfigManager.doorRetryMs = (int) v);
            }
            case SLAYER_SPLITS -> {
                option("Spawn Time", () -> ConfigManager.slayerSpawnTime, v -> ConfigManager.slayerSpawnTime = v);
                option("Kill Time", () -> ConfigManager.slayerKillTime, v -> ConfigManager.slayerKillTime = v);
            }
            case SLAYER_BOSS_HIGHLIGHT -> {
                option("Outline", () -> ConfigManager.slayerBossOutline, v -> ConfigManager.slayerBossOutline = v);
                option("Fill", () -> ConfigManager.slayerBossFill, v -> ConfigManager.slayerBossFill = v);
                option("Tracer", () -> ConfigManager.slayerBossTracer, v -> ConfigManager.slayerBossTracer = v);
                color("Highlight Color", ConfigManager.slayerBossColor, v -> ConfigManager.slayerBossColor = v);
                slider("Line Width", ConfigManager.slayerBossWidth, 1, 10, 1, v -> ConfigManager.slayerBossWidth = (int) v);
            }
            case DAGGER_SWAP -> {
                slider("Delay (ticks)", ConfigManager.daggerSwapDelay, 0, 10, 1, value -> ConfigManager.daggerSwapDelay = (int) value);
                slider("Delay Variance (ticks)", ConfigManager.daggerSwapVariance, 0, 10, 1, value -> ConfigManager.daggerSwapVariance = (int) value);
            }
            case CORPSE_ESP -> {
                option("Show Outline", () -> ConfigManager.corpseOutline, v -> ConfigManager.corpseOutline = v);
                option("Show Fill", () -> ConfigManager.corpseFill, v -> ConfigManager.corpseFill = v);
                option("Use Block Cube", () -> ConfigManager.corpseCube, v -> ConfigManager.corpseCube = v);
                slider("Line Width", ConfigManager.corpseLineWidth, 1, 10, 1, v -> ConfigManager.corpseLineWidth = v);
                option("Depth Check", () -> ConfigManager.corpseDepth, v -> ConfigManager.corpseDepth = v);
                option("Show Tracer", () -> ConfigManager.corpseTracer, v -> ConfigManager.corpseTracer = v);
                option("Show Name", () -> ConfigManager.corpseName, v -> ConfigManager.corpseName = v);
                option("Show Distance", () -> ConfigManager.corpseDistance, v -> ConfigManager.corpseDistance = v);
                option("Hide Claimed", () -> ConfigManager.corpseHideClaimed, v -> ConfigManager.corpseHideClaimed = v);
                option("Lapis", () -> ConfigManager.lapisCorpse, v -> ConfigManager.lapisCorpse = v);
                option("Tungsten", () -> ConfigManager.tungstenCorpse, v -> ConfigManager.tungstenCorpse = v);
                option("Umber", () -> ConfigManager.umberCorpse, v -> ConfigManager.umberCorpse = v);
                option("Vanguard", () -> ConfigManager.vanguardCorpse, v -> ConfigManager.vanguardCorpse = v);
                color("Lapis Color", ConfigManager.lapisCorpseColor, v -> ConfigManager.lapisCorpseColor = v);
                color("Tungsten Color", ConfigManager.tungstenCorpseColor, v -> ConfigManager.tungstenCorpseColor = v);
                color("Umber Color", ConfigManager.umberCorpseColor, v -> ConfigManager.umberCorpseColor = v);
                color("Vanguard Color", ConfigManager.vanguardCorpseColor, v -> ConfigManager.vanguardCorpseColor = v);
            }
            case PEST_ESP -> {
                option("Show Tracer", () -> ConfigManager.pestEspTracer, v -> ConfigManager.pestEspTracer = v);
                option("Depth Check", () -> ConfigManager.pestEspDepth, v -> ConfigManager.pestEspDepth = v);
                slider("Line Width", ConfigManager.pestEspThickness, 1, 10, 1, v -> ConfigManager.pestEspThickness = (int) v);
                color("ESP Color", ConfigManager.pestEspColor, v -> ConfigManager.pestEspColor = v);
            }
            case SAFARI_ESP -> safariSettings();
            case RENDER_OPTIMISER -> renderOptimiserSettings();
            case WORLD_SCANNER -> scannerSettings();
            case FRESH_TOOLS -> addDetail(new PinkUi.Action(x, y, w, 23,
                "HUD Editor", false, () -> ClientUiAccess.setScreen(minecraft, new GameplayHudEditor(this))));
            case AUTO_HARP -> slider("Harp Click Delay", ConfigManager.harpClickDelay, 0, 5, 1, v -> ConfigManager.harpClickDelay = (int) v);
            case HIDE_PLAYERS -> {
                option("Hide All Players", () -> ConfigManager.hideAllPlayers, v -> ConfigManager.hideAllPlayers = v);
                option("Dungeon Only", () -> ConfigManager.hidePlayersDungeonOnly, v -> ConfigManager.hidePlayersDungeonOnly = v);
                option("Boss Only", () -> ConfigManager.hidePlayersBossOnly, v -> ConfigManager.hidePlayersBossOnly = v);
                option("Only at Devices", () -> ConfigManager.hidePlayersAtDevices, v -> ConfigManager.hidePlayersAtDevices = v);
                option("Click Through", () -> ConfigManager.hidePlayersClickThrough, v -> ConfigManager.hidePlayersClickThrough = v);
                slider("Hide Radius", ConfigManager.hideNearbyPlayersRadius, 0, 32, .5, v -> ConfigManager.hideNearbyPlayersRadius = v);
            }
            case F7_TIMER -> option("Show Timer in Ticks", () -> ConfigManager.f7TimerTicks, v -> ConfigManager.f7TimerTicks = v);
            case STARRED_MOBS -> color("Highlight Color", ConfigManager.starredMobColor, v -> ConfigManager.starredMobColor = v);
            default -> {}
        }
        detailsHeight = detailsY - detailsTop + 7;
    }

    private void dojoSettings() {
        option("Force: Highlight Negative Mobs", () -> ConfigManager.dojoForceHighlight, v -> ConfigManager.dojoForceHighlight = v);
        color("Force Color", ConfigManager.dojoForceColor, v -> ConfigManager.dojoForceColor = v);
        option("Force: Block Negative Hits", () -> ConfigManager.dojoForceBlock, v -> ConfigManager.dojoForceBlock = v);
        option("Discipline: Highlight Correct Mobs", () -> ConfigManager.dojoDisciplineHighlight, v -> ConfigManager.dojoDisciplineHighlight = v);
        color("Discipline Color", ConfigManager.dojoDisciplineColor, v -> ConfigManager.dojoDisciplineColor = v);
        option("Discipline: Tracer", () -> ConfigManager.dojoDisciplineTracer, v -> ConfigManager.dojoDisciplineTracer = v);
        option("Discipline: Block Wrong Hits", () -> ConfigManager.dojoDisciplineBlock, v -> ConfigManager.dojoDisciplineBlock = v);
        option("Discipline: Auto Swap", () -> ConfigManager.dojoDisciplineSwap, v -> ConfigManager.dojoDisciplineSwap = v);
        option("Control: Highlight Target", () -> ConfigManager.dojoControlHighlight, v -> ConfigManager.dojoControlHighlight = v);
        color("Control Color", ConfigManager.dojoControlColor, v -> ConfigManager.dojoControlColor = v);
        option("Control: Auto Aim", () -> ConfigManager.dojoControlAim, v -> ConfigManager.dojoControlAim = v);
        option("Control: Keep Centered", () -> ConfigManager.dojoControlCenter, v -> ConfigManager.dojoControlCenter = v);
        slider("Control: Prediction (ticks)", ConfigManager.dojoControlPrediction, 1, 10, 1, v -> ConfigManager.dojoControlPrediction = (int) v);
        option("Mastery: Target Tracers", () -> ConfigManager.dojoMasteryTracer, v -> ConfigManager.dojoMasteryTracer = v);
        slider("Mastery: Tracer Count", ConfigManager.dojoMasteryAmount, 1, 4, 1, v -> ConfigManager.dojoMasteryAmount = (int) v);
        option("Mastery: Auto Shoot", () -> ConfigManager.dojoMasteryAuto, v -> ConfigManager.dojoMasteryAuto = v);
        option("Mastery: Sneak to Pause", () -> ConfigManager.dojoMasterySneakStops, v -> ConfigManager.dojoMasterySneakStops = v);
        slider("Mastery: Release Offset (ms)", ConfigManager.dojoMasteryOffset, 0, 1000, 50, v -> ConfigManager.dojoMasteryOffset = (int) v);
        option("Swiftness: Auto Movement", () -> ConfigManager.dojoSwiftnessAuto, v -> ConfigManager.dojoSwiftnessAuto = v);
        option("Swiftness: Full Stop", () -> ConfigManager.dojoSwiftnessStop, v -> ConfigManager.dojoSwiftnessStop = v);
        slider("Swiftness: Landing Lead", ConfigManager.dojoSwiftnessLead, 0, .5, .05, v -> ConfigManager.dojoSwiftnessLead = v);
        slider("Swiftness: Turn Threshold", ConfigManager.dojoSwiftnessTurnDistance, 4, 9, .5, v -> ConfigManager.dojoSwiftnessTurnDistance = v);
        slider("Swiftness: Turn Time (ms)", ConfigManager.dojoSwiftnessRotation, 0, 300, 50, v -> ConfigManager.dojoSwiftnessRotation = (int) v);
        cycle("Swiftness: Rotation Curve", com.customcosmetics.features.gameplay.DojoGames.SWIFTNESS_CURVES,
            () -> ConfigManager.dojoSwiftnessCurve, v -> ConfigManager.dojoSwiftnessCurve = v);
        option("Tenacity: Highlight Trajectories", () -> ConfigManager.dojoTenacityHighlight, v -> ConfigManager.dojoTenacityHighlight = v);
        option("Tenacity: Trajectory Lines", () -> ConfigManager.dojoTenacityLine, v -> ConfigManager.dojoTenacityLine = v);
        color("Tenacity Color", ConfigManager.dojoTenacityColor, v -> ConfigManager.dojoTenacityColor = v);
    }

    private void textSetting(String label, String value, java.util.function.Consumer<String> setter) {
        var field = new PinkUi.Field(font, layout.contentX() + 7, detailsY, detailWidth(), label, 23);
        field.setMaxLength(150);
        field.setHint(UiText.component(label));
        field.setValue(value == null ? "" : value);
        field.setResponder(input -> { setter.accept(input); ConfigManager.requestSave(); });
        addDetail(field);
    }

    private void trevorSettings() {
        option("Animal ESP", () -> ConfigManager.trevorEsp, v -> ConfigManager.trevorEsp = v);
        option("Show Tracer", () -> ConfigManager.trevorTracer, v -> ConfigManager.trevorTracer = v);
        option("Auto Call", () -> ConfigManager.trevorAutoCall, v -> ConfigManager.trevorAutoCall = v);
        slider("Call Delay (ticks)", ConfigManager.trevorCallDelay, 0, 5, 1, v -> ConfigManager.trevorCallDelay = (int) v);
        slider("Call Early (s)", ConfigManager.trevorCallEarly, 0, 5, .1, v -> ConfigManager.trevorCallEarly = v);
        option("Auto Accept", () -> ConfigManager.trevorAutoAccept, v -> ConfigManager.trevorAutoAccept = v);
        slider("Accept Delay (ticks)", ConfigManager.trevorAcceptDelay, 0, 5, 1, v -> ConfigManager.trevorAcceptDelay = (int) v);
        option("Cooldown End Alert", () -> ConfigManager.trevorCooldownAlert, v -> ConfigManager.trevorCooldownAlert = v);
        textSetting("Alert Message", ConfigManager.trevorAlertMessage, v -> ConfigManager.trevorAlertMessage = v);
        textSetting("Alert Sound ID", ConfigManager.trevorAlertSound, v -> ConfigManager.trevorAlertSound = v);
        option("Cooldown Timer", () -> ConfigManager.trevorCooldownTimer, v -> ConfigManager.trevorCooldownTimer = v);
        addDetail(new PinkUi.Action(layout.contentX() + 7, detailsY, detailWidth(), 23, "HUD Editor", false,
            () -> ClientUiAccess.setScreen(minecraft, new GameplayHudEditor(this))));
        for (var rarity : TrevorHelper.Rarity.values()) color(rarity.label, rarity.color(), value -> {
            var colors = new java.util.HashMap<>(ConfigManager.trevorColors);
            colors.put(rarity.name(), value);
            ConfigManager.trevorColors = java.util.Map.copyOf(colors);
        });
    }

    private void secretAuraSettings() {
        slider("Chest and Lever Range", ConfigManager.secretAuraRange, 2.1, 6.5, .1, v -> ConfigManager.secretAuraRange = v);
        slider("Skull Range", ConfigManager.secretAuraSkullRange, 2.1, 4.7, .1, v -> ConfigManager.secretAuraSkullRange = v);
        slider("Boss Lever Range", ConfigManager.secretAuraBossRange, 2.1, 6.5, .1, v -> ConfigManager.secretAuraBossRange = v);
        slider("Click Delay (ms)", ConfigManager.secretAuraDelay, 100, 4000, 50, v -> ConfigManager.secretAuraDelay = (int) v);
        slider("Aura FOV", ConfigManager.secretAuraFov, 10, 360, 1, v -> ConfigManager.secretAuraFov = (int) v);
        cycle("Swap On", java.util.List.of("None", "Skulls", "All"), () -> ConfigManager.secretAuraSwap, v -> ConfigManager.secretAuraSwap = v);
        if (ConfigManager.secretAuraSwap != 0) {
            option("Swap Back", () -> ConfigManager.secretAuraSwapBack, v -> ConfigManager.secretAuraSwapBack = v);
            slider("Swap Slot", ConfigManager.secretAuraSlot, 1, 9, 1, v -> ConfigManager.secretAuraSlot = (int) v);
        }
        option("Swing Hand", () -> ConfigManager.secretAuraSwing, v -> ConfigManager.secretAuraSwing = v);
        option("In Boss", () -> ConfigManager.secretAuraInBoss, v -> ConfigManager.secretAuraInBoss = v);
        option("In Container", () -> ConfigManager.secretAuraInContainer, v -> ConfigManager.secretAuraInContainer = v);
    }

    private void renderOptimiserSettings() {
        option("Hide Player Arm", () -> ConfigManager.renderHideArm, v -> ConfigManager.renderHideArm = v);
        option("Hide Effects in UI", () -> ConfigManager.renderHideEffects, v -> ConfigManager.renderHideEffects = v);
        option("Hide Player Armor", () -> ConfigManager.renderHidePlayerArmor, v -> ConfigManager.renderHidePlayerArmor = v);
        option("Hide Own Armor", () -> ConfigManager.renderHideSelfArmor, v -> ConfigManager.renderHideSelfArmor = v);
        option("Hide Other Players' Armor", () -> ConfigManager.renderHideOtherArmor, v -> ConfigManager.renderHideOtherArmor = v);
        option("Hide Mob Armor", () -> ConfigManager.renderHideMobArmor, v -> ConfigManager.renderHideMobArmor = v);
        option("Hide Glowing Effect", () -> ConfigManager.renderHideGlow, v -> ConfigManager.renderHideGlow = v);
        option("Hide Fog", () -> ConfigManager.renderHideFog, v -> ConfigManager.renderHideFog = v);
        option("Hide Lava Fog", () -> ConfigManager.renderHideLava, v -> ConfigManager.renderHideLava = v);
        option("Hide Fire Overlay", () -> ConfigManager.renderHideFire, v -> ConfigManager.renderHideFire = v);
        option("Hide Fire on Entities", () -> ConfigManager.renderHideEntityFire, v -> ConfigManager.renderHideEntityFire = v);
    }

    private void scannerSettings() {
        var targets = WorldScannerSettings.Target.values();
        cycle("Structure", java.util.Arrays.stream(targets).map(target -> target.label).toList(),
            () -> scannerTarget.ordinal(), value -> scannerTarget = targets[value]);
        var target = scannerTarget;
        option("Enabled", () -> target.options().enabled(), value -> target.set(target.options().enabled(value)));
        cycle("Highlight Style", java.util.List.of("Outline", "Filled", "Both"), () -> target.options().style(),
            value -> target.set(target.options().style(value)));
        option("Show Tracer", () -> target.options().tracer(), value -> target.set(target.options().tracer(value)));
        option("Show Name", () -> target.options().name(), value -> target.set(target.options().name(value)));
        slider("Name Scale", target.options().scale(), 0, 1, .05, value -> target.set(target.options().scale(value)));
        slider("Name Background Opacity", target.options().background(), 0, 1, .05,
            value -> target.set(target.options().background(value)));
        option("Send Coordinates in Chat", () -> target.options().chat(), value -> target.set(target.options().chat(value)));
        if (target == WorldScannerSettings.Target.FAIRY_GROTTO) {
            option("Show Number of Blocks", () -> ConfigManager.scannerShowJasperCount, value -> ConfigManager.scannerShowJasperCount = value);
            slider("Count Background Opacity", ConfigManager.scannerJasperBackground, 0, 1, .05,
                value -> ConfigManager.scannerJasperBackground = value);
        }
        color("ESP Color", target.options().color(), value -> target.set(target.options().color(value)));
    }

    private void cycle(String label, java.util.List<String> choices, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter) {
        var widget = new PinkUi.Action(layout.contentX() + 7, detailsY, detailWidth(), 23,
            UiText.tr(label) + ": " + UiText.tr(choices.get(getter.getAsInt())), false, () -> {
                setter.accept((getter.getAsInt() + 1) % choices.size());
                ConfigManager.requestSave();
                rebuildWidgets();
            }).leftAligned().rawLabel();
        addDetail(widget);
    }

    private String keyLabel() {
        var key = GameplayKeybinds.key(editing);
        return UiText.tr("Keybind") + ": " + (key.equals(InputConstants.UNKNOWN)
            ? UiText.tr("Unassigned") : key.getDisplayName().getString());
    }

    private void autoClickerSettings() {
        detailLabels.add(new DetailLabel(UiText.tr("Enable the module and a click type, then hold its key in SkyBlock."), detailsY));
        detailsY += 17;
        option("Enable Left Click", () -> ConfigManager.autoClickerLeft, v -> ConfigManager.autoClickerLeft = v);
        clickKey("Left Click Keybind", AutoClicker.key(true));
        slider("Left Minimum CPS", ConfigManager.autoClickerLeftMin, 1, 20, 1, value -> {
            ConfigManager.autoClickerLeftMin = (int) value;
        });
        slider("Left Maximum CPS", ConfigManager.autoClickerLeftMax, 1, 20, 1, value -> {
            ConfigManager.autoClickerLeftMax = (int) value;
        });
        option("Enable Right Click", () -> ConfigManager.autoClickerRight, v -> ConfigManager.autoClickerRight = v);
        clickKey("Right Click Keybind", AutoClicker.key(false));
        slider("Right Minimum CPS", ConfigManager.autoClickerRightMin, 1, 20, 1, value -> {
            ConfigManager.autoClickerRightMin = (int) value;
        });
        slider("Right Maximum CPS", ConfigManager.autoClickerRightMax, 1, 20, 1, value -> {
            ConfigManager.autoClickerRightMax = (int) value;
        });
        option("Allow Breaking Blocks", () -> ConfigManager.autoClickerBreakBlocks, v -> ConfigManager.autoClickerBreakBlocks = v);
        option("Stop on Item Swap", () -> ConfigManager.autoClickerStopOnSwap, v -> ConfigManager.autoClickerStopOnSwap = v);
        option("Stop While Sneaking", () -> !ConfigManager.autoClickerWhileSneaking, v -> ConfigManager.autoClickerWhileSneaking = !v);
        option("Block Dungeon Breaker", () -> ConfigManager.autoClickerBlockDungeonBreaker, v -> ConfigManager.autoClickerBlockDungeonBreaker = v);
        var whitelist = option("Whitelist Only", () -> ConfigManager.autoClickerWhitelistOnly, v -> ConfigManager.autoClickerWhitelistOnly = v);
        whitelist.setTooltip(net.minecraft.client.gui.components.Tooltip.create(UiText.guiComponent(
            "Use /csb autoclicker add, remove or clear, followed by left or right.")));
    }

    private void safariSettings() {
        option("Floor Drops", () -> ConfigManager.safariFloorDrops, v -> ConfigManager.safariFloorDrops = v);
        option("Floor Drops in Current Biome", () -> ConfigManager.safariFloorDropsCurrentBiome, v -> ConfigManager.safariFloorDropsCurrentBiome = v);
        color("Floor Drop Color", ConfigManager.safariFloorDropColor, v -> ConfigManager.safariFloorDropColor = v);

        var biomes = SafariSpecies.Biome.values();
        int width = detailWidth() / biomes.length;
        int top = detailsY;
        for (var biome : biomes) {
            int x = layout.contentX() + biome.ordinal() * width;
            addDetail(new PinkUi.Action(x, top, width - 3, 22, biome.label, false, () -> {
                safariBiome = biome;
                rebuildWidgets();
            }).selected(() -> safariBiome == biome));
        }
        var selected = safariBiome;
        option("Show This Biome", () -> !ConfigManager.safariDisabledBiomes.contains(selected.key()),
            v -> ConfigManager.safariDisabledBiomes = membership(ConfigManager.safariDisabledBiomes, selected.key(), !v));
        option("Only While in This Biome", () -> !ConfigManager.safariAcrossBiomes.contains(selected.key()),
            v -> ConfigManager.safariAcrossBiomes = membership(ConfigManager.safariAcrossBiomes, selected.key(), !v));
        for (var species : SafariSpecies.values()) {
            if (species.biome != selected) continue;
            String key = species.key();
            option(species.label, () -> !ConfigManager.safariDisabledSpecies.contains(key),
                v -> ConfigManager.safariDisabledSpecies = membership(ConfigManager.safariDisabledSpecies, key, !v));
            color(species.label, ConfigManager.safariSpeciesColors.getOrDefault(key, species.color), value -> {
                var colors = new java.util.HashMap<>(ConfigManager.safariSpeciesColors);
                colors.put(key, value);
                ConfigManager.safariSpeciesColors = java.util.Map.copyOf(colors);
            });
        }
    }

    private void fastLeapSettings() {
        option("Target by Player Name", () -> ConfigManager.autoLeapByName, v -> {
            ConfigManager.autoLeapByName = v;
            rebuildWidgets();
        });
        option("Swap Back", () -> ConfigManager.autoLeapSwapBack, v -> ConfigManager.autoLeapSwapBack = v);
        slider("Leap Click Delay (ticks)", ConfigManager.autoLeapDelay, 0, 10, 1, v -> ConfigManager.autoLeapDelay = (int) v);
        var fallback = new PinkUi.Field(font, layout.contentX() + 7, detailsY, detailWidth(), "Leap Target", 23);
        fallback.setMaxLength(16);
        fallback.setHint(UiText.component("Leap Target"));
        fallback.setValue(ConfigManager.fastLeapTarget);
        fallback.setResponder(value -> {
            boolean valid = value.isEmpty() || value.matches("[A-Za-z0-9_]{1,16}");
            fallback.invalid(!valid);
            if (valid) {
                ConfigManager.fastLeapTarget = value;
                ConfigManager.requestSave();
            }
        });
        fallback.setTooltip(net.minecraft.client.gui.components.Tooltip.create(UiText.component("Used when no enabled phase or room rule supplies a target.")));
        addDetail(fallback);
        for (var trigger : com.customcosmetics.features.gameplay.AutoLeap.FastTrigger.values()) {
            if (trigger.hasToggle()) option(trigger.label, trigger::enabled, v -> ConfigManager.fastLeapTriggers =
                membership(ConfigManager.fastLeapTriggers, trigger.id, v));
            switch (trigger) {
                case DOOR -> option("Disable After Blood Open", () -> ConfigManager.fastLeapAfterBlood, v -> ConfigManager.fastLeapAfterBlood = v);
                case I4 -> option("Leap Melody", () -> ConfigManager.fastLeapMelody, v -> ConfigManager.fastLeapMelody = v);
                default -> {}
            }
            if (trigger.hasTarget()) leapTargetField(trigger.id, trigger.label, true);
        }
    }

    private void autoLeapSettings() {
        var preset = new PinkUi.Field(font, layout.contentX() + 7, detailsY, detailWidth(), "Preset Name", 23);
        preset.setMaxLength(32);
        preset.setHint(UiText.component("Preset Name"));
        preset.setValue(leapPreset);
        preset.setResponder(value -> leapPreset = value);
        addDetail(preset);
        int buttonY = detailsY, buttonWidth = (detailWidth() - 4) / 2;
        addDetail(new PinkUi.Action(layout.contentX(), buttonY, buttonWidth, 22, "Save Preset", false, () -> leapPreset(true)));
        addDetail(new PinkUi.Action(layout.contentX() + buttonWidth + 4, buttonY, buttonWidth, 22, "Load Preset", false, () -> leapPreset(false)));
        if (!leapPresetNotice.isEmpty()) {
            detailLabels.add(new DetailLabel(UiText.tr(leapPresetNotice), detailsY));
            detailsY += 13;
        }
        option("Target by Player Name", () -> ConfigManager.autoLeapByName, v -> {
            ConfigManager.autoLeapByName = v;
            rebuildWidgets();
        });
        option("Swap Back", () -> ConfigManager.autoLeapSwapBack, v -> ConfigManager.autoLeapSwapBack = v);
        slider("Leap Click Delay (ticks)", ConfigManager.autoLeapDelay, 0, 10, 1, v -> ConfigManager.autoLeapDelay = (int) v);
        for (var trigger : com.customcosmetics.features.gameplay.AutoLeap.Trigger.values()) {
            if (trigger.hasToggle()) option(trigger.label, trigger::enabled, v -> ConfigManager.autoLeapTriggers =
                membership(ConfigManager.autoLeapTriggers, trigger.id, v));
            switch (trigger) {
                case DOOR -> option("Disable After Blood Open", () -> ConfigManager.autoLeapAfterBlood, v -> ConfigManager.autoLeapAfterBlood = v);
                case I4 -> option("Leap Melody", () -> ConfigManager.autoLeapMelody, v -> ConfigManager.autoLeapMelody = v);
                case P3 -> option("Only When Gate Blown", () -> ConfigManager.autoLeapGate, v -> ConfigManager.autoLeapGate = v);
                default -> {}
            }
            if (trigger.hasTarget()) leapTargetField(trigger.id, trigger.label, false);
        }
    }

    private void leapTargetField(String id, String title, boolean fast) {
        boolean byName = ConfigManager.autoLeapByName;
        String label = title + " · " + (byName ? "Player Name" : "Dungeon Class");
        var targets = fast ? (byName ? ConfigManager.fastLeapNames : ConfigManager.fastLeapClasses)
            : (byName ? ConfigManager.autoLeapNames : ConfigManager.autoLeapClasses);
        if (!byName) {
            var choices = java.util.List.of("Unknown", "Healer", "Mage", "Berserk", "Archer", "Tank");
            cycle(title, choices, () -> {
                String selected = targets.getOrDefault(id, "Unknown");
                for (int i = 0; i < choices.size(); i++) if (choices.get(i).equalsIgnoreCase(selected)) return i;
                return 0;
            }, index -> setLeapTarget(id, index == 0 ? "" : choices.get(index), fast, false));
            return;
        }
        var field = new PinkUi.Field(font, layout.contentX() + 7, detailsY, detailWidth(), label, 23);
        field.setMaxLength(16);
        field.setHint(UiText.component(label));
        field.setValue(targets.getOrDefault(id, ""));
        field.setResponder(value -> {
            boolean valid = value.isEmpty() || value.matches("[A-Za-z0-9_]{1,16}");
            field.invalid(!valid);
            if (valid) setLeapTarget(id, value, fast, true);
        });
        addDetail(field);
    }

    private static void setLeapTarget(String id, String value, boolean fast, boolean byName) {
        var targets = new java.util.HashMap<>(fast ? (byName ? ConfigManager.fastLeapNames : ConfigManager.fastLeapClasses)
            : (byName ? ConfigManager.autoLeapNames : ConfigManager.autoLeapClasses));
        if (value.isEmpty()) targets.remove(id);
        else targets.put(id, value);
        var updated = java.util.Map.copyOf(targets);
        if (fast) {
            if (byName) ConfigManager.fastLeapNames = updated;
            else ConfigManager.fastLeapClasses = updated;
        } else {
            if (byName) ConfigManager.autoLeapNames = updated;
            else ConfigManager.autoLeapClasses = updated;
        }
        ConfigManager.requestSave();
    }

    private void leapPreset(boolean save) {
        boolean success = com.customcosmetics.features.gameplay.AutoLeap.preset(leapPreset, save);
        leapPresetNotice = success ? (save ? "Preset saved." : "Preset loaded.")
            : leapPreset.isBlank() ? "Enter a preset name." : "Preset not found.";
        rebuildWidgets();
    }

    private static java.util.Set<String> membership(java.util.Set<String> current, String key, boolean add) {
        var next = new java.util.HashSet<>(current);
        if (add) next.add(key);
        else next.remove(key);
        return java.util.Set.copyOf(next);
    }

    private void clickKey(String title, net.minecraft.client.KeyMapping mapping) {
        if (mapping == null) return;
        var button = new PinkUi.Action[1];
        button[0] = new PinkUi.Action(layout.contentX() + 7, detailsY, detailWidth(), 20,
            UiText.tr(title) + ": " + mapping.getTranslatedKeyMessage().getString(), false, () -> {
                recording = true;
                recordedMapping = mapping;
                button[0].setMessage(UiText.component("Press a key or mouse button…"));
            }).leftAligned().rawLabel();
        addDetail(button[0]);
    }

    private void trajectorySettings() {
        option("Bows", () -> ConfigManager.trajectoryBows, v -> ConfigManager.trajectoryBows = v);
        option("Ender Pearls", () -> ConfigManager.trajectoryPearls, v -> ConfigManager.trajectoryPearls = v);
        option("Show Path", () -> ConfigManager.trajectoryLine, v -> ConfigManager.trajectoryLine = v);
        option("Show Impact Box", () -> ConfigManager.trajectoryBox, v -> ConfigManager.trajectoryBox = v);
        option("Show Impact Plane", () -> ConfigManager.trajectoryPlane, v -> ConfigManager.trajectoryPlane = v);
        option("Depth Check", () -> ConfigManager.trajectoryDepth, v -> ConfigManager.trajectoryDepth = v);
        slider("Simulation Ticks", ConfigManager.trajectoryTicks, 1, 120, 1, v -> ConfigManager.trajectoryTicks = (int) v);
        slider("Path Width", ConfigManager.trajectoryLineWidth, .1, 5, .1, v -> ConfigManager.trajectoryLineWidth = v);
        slider("Impact Box Size", ConfigManager.trajectoryBoxSize, .5, 3, .1, v -> ConfigManager.trajectoryBoxSize = v);
        slider("Impact Plane Size", ConfigManager.trajectoryPlaneSize, .1, 5, .1, v -> ConfigManager.trajectoryPlaneSize = v);
        color("Path Color", ConfigManager.trajectoryColor, v -> ConfigManager.trajectoryColor = v);
    }

    private void deviceSettings() {
        switch (editing) {
            case CRYSTAL_PICKUP -> {
                option("Look at Target", () -> ConfigManager.crystalLookOnly, v -> ConfigManager.crystalLookOnly = v);
                option("Run in Menus", () -> ConfigManager.crystalInMenus, v -> ConfigManager.crystalInMenus = v);
                slider("Interaction Range", ConfigManager.crystalRange, 1, 5, .1, v -> ConfigManager.crystalRange = v);
                slider("Delay (ticks)", ConfigManager.crystalDelay, 1, 20, 1, v -> ConfigManager.crystalDelay = (int) v);
            }
            case OPEN_TERMINALS -> {
                cycle("Mode", java.util.List.of("Aura", "Triggerbot"), () -> ConfigManager.terminalLookOnly ? 1 : 0,
                    v -> ConfigManager.terminalLookOnly = v == 1);
                option("Ground Only", () -> ConfigManager.terminalGroundOnly, v -> ConfigManager.terminalGroundOnly = v);
                slider("Interaction Range", ConfigManager.terminalRange, 0, 4, .1, v -> ConfigManager.terminalRange = v);
                slider("Delay (ms)", ConfigManager.terminalDelayMs, 0, 2000, 50, v -> ConfigManager.terminalDelayMs = (int) v);
                if (!ConfigManager.terminalLookOnly) slider("Aura FOV", ConfigManager.terminalAuraFov, 10, 360, 1, v -> ConfigManager.terminalAuraFov = (int) v);
                option("Delay After Leap", () -> ConfigManager.terminalLeapDelay, v -> ConfigManager.terminalLeapDelay = v);
                slider("Leap Delay (s)", ConfigManager.terminalLeapDelaySeconds, .1, 5, .1, v -> ConfigManager.terminalLeapDelaySeconds = v);
            }
            case RELICS -> {
                option("Pick Up Relics", () -> ConfigManager.relicPickup, v -> ConfigManager.relicPickup = v);
                option("Place Relics", () -> ConfigManager.relicPlacement, v -> ConfigManager.relicPlacement = v);
                slider("Interaction Range", ConfigManager.relicRange, 1, 4.5, .1, v -> ConfigManager.relicRange = v);
                slider("Delay (ms)", ConfigManager.relicDelayMs, 50, 1000, 50, v -> ConfigManager.relicDelayMs = (int) v);
            }
            case ARROW_ALIGN -> {
                option("Look at Target", () -> ConfigManager.arrowLookOnly, v -> ConfigManager.arrowLookOnly = v);
                option("Pause While Sneaking", () -> ConfigManager.arrowSneakPause, v -> ConfigManager.arrowSneakPause = v);
                slider("Interaction Range", ConfigManager.arrowRange, 2.1, 6.5, .1, v -> ConfigManager.arrowRange = v);
                slider("Delay (ms)", ConfigManager.arrowDelayMs, 70, 500, 10, v -> ConfigManager.arrowDelayMs = (int) v);
            }
            case SIMON_SAYS -> {
                option("Solver", () -> ConfigManager.simonSolver, v -> ConfigManager.simonSolver = v);
                option("Auto", () -> ConfigManager.simonAuto, v -> ConfigManager.simonAuto = v);
                option("Announce Time", () -> ConfigManager.simonAnnounce, v -> ConfigManager.simonAnnounce = v);
                slider("Delay (ms)", ConfigManager.simonDelayMs, 50, 500, 10, v -> ConfigManager.simonDelayMs = (int) v);
                slider("Start Clicks", ConfigManager.simonStartClicks, 1, 10, 1, v -> ConfigManager.simonStartClicks = (int) v);
                slider("Start Delay (ms)", ConfigManager.simonStartDelay, 50, 1250, 10, v -> ConfigManager.simonStartDelay = (int) v);
                cycle("Rotation Mode", java.util.List.of("None", "Natural", "Curve"), () -> ConfigManager.simonRotation,
                    v -> ConfigManager.simonRotation = v);
                if (ConfigManager.simonRotation != 0) slider("Rotation Time (ms)", ConfigManager.simonRotationTime, 50, 500, 10,
                    v -> ConfigManager.simonRotationTime = (int) v);
                slider("Hint Opacity", ConfigManager.simonHintOpacity, 0, 1, .05, v -> ConfigManager.simonHintOpacity = v);
                color("First Button Color", ConfigManager.simonFirstColor, v -> ConfigManager.simonFirstColor = v);
                color("Second Button Color", ConfigManager.simonSecondColor, v -> ConfigManager.simonSecondColor = v);
                color("Remaining Buttons Color", ConfigManager.simonThirdColor, v -> ConfigManager.simonThirdColor = v);
            }
            default -> {}
        }
    }

    private PinkUi.Action option(String title, java.util.function.BooleanSupplier value, java.util.function.Consumer<Boolean> setter) {
        var button = new PinkUi.Action(layout.contentX() + 7, detailsY, detailWidth(), 23, title, false, () -> {
            setter.accept(!value.getAsBoolean());
            ConfigManager.requestSave();
        }).leftAligned().selected(value);
        addDetail(button);
        return button;
    }

    private void slider(String title, double value, double min, double max, double step, java.util.function.DoubleConsumer setter) {
        int x = layout.contentX() + 7, width = detailWidth();
        var number = new PinkUi.Field(font, x + width - 48, detailsY, 48, title, 19);
        number.setMaxLength(8);
        number.setValue(String.format(Locale.ROOT, "%.2f", value));
        var slider = new PinkUi.Slider(x, detailsY, width, title, value, min, max, step, v -> {
            setter.accept(v);
            ConfigManager.requestSave();
            if (!number.isFocused()) number.setValue(String.format(Locale.ROOT, "%.2f", v));
        }).labelSpace(54);
        number.setResponder(input -> {
            if (!number.isFocused()) return;
            try {
                double parsed = Double.parseDouble(input);
                boolean valid = Double.isFinite(parsed) && parsed >= min && parsed <= max;
                number.invalid(!valid);
                if (valid) slider.setActual(parsed);
            } catch (NumberFormatException failure) { number.invalid(true); }
        });
        int y = detailsY;
        addDetail(slider);
        details.add(new Detail(addWidget(number), y));
    }

    private void color(String title, int rgb, java.util.function.IntConsumer setter) {
        int x = layout.contentX() + 7, width = detailWidth();
        detailLabels.add(new DetailLabel(UiText.tr(title), detailsY));
        detailsY += 13;
        var field = new PinkUi.Field(font, x, detailsY, width, title, 20);
        field.setMaxLength(7);
        field.setValue(String.format(Locale.ROOT, "#%06X", rgb));
        var picker = new PinkUi.ColorPicker(x, detailsY + 25, Math.min(width, 280), 55, rgb, value -> {
            setter.accept(value);
            ConfigManager.requestSave();
            field.setValue(String.format(Locale.ROOT, "#%06X", value));
        });
        field.setResponder(input -> {
            boolean valid = input.matches("#?[0-9A-Fa-f]{6}");
            field.invalid(!valid);
            if (valid) {
                int value = Integer.parseInt(input.replace("#", ""), 16);
                setter.accept(value);
                picker.setColor(value);
                ConfigManager.requestSave();
            }
        });
        var tooltip = net.minecraft.client.gui.components.Tooltip.create(UiText.component(title));
        field.setTooltip(tooltip);
        picker.setTooltip(tooltip);
        addDetail(field);
        addDetail(picker);
    }

    private int detailWidth() { return Math.max(1, layout.contentWidth() - 16); }
    private void addDetail(net.minecraft.client.gui.components.AbstractWidget widget) {
        details.add(new Detail(addWidget(widget), widget.getY()));
        detailsY = Math.max(detailsY, widget.getBottom() + 5);
    }
    private void finishRecording(InputConstants.Key key) {
        recording = false;
        if (recordedMapping != null) {
            if (key != null) {
                recordedMapping.setKey(key);
                net.minecraft.client.KeyMapping.resetMapping();
                minecraft.options.save();
            }
            recordedMapping = null;
            rebuildWidgets();
            return;
        }
        var conflict = key == null ? null : GameplayKeybinds.bind(editing, key);
        keyButton.setMessage(Component.literal(conflict == null ? keyLabel()
            : UiText.tr("Already assigned to: ") + UiText.tr(conflict.label)));
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (!recording) {
            if (event.key() == ClientInput.ESCAPE && editing != null) {
                showSettings(editing);
                return true;
            }
            return super.keyPressed(event);
        }
        if (event.key() == ClientInput.ESCAPE) finishRecording(null);
        else {
            var key = InputConstants.getKey(event);
            if (!key.equals(InputConstants.UNKNOWN)) finishRecording(key);
        }
        return true;
    }

    private int scrollLimit() { return Math.max(0, contentHeight - Math.max(0, cardsBottom - cardsTop)); }

    private void positionCards() {
        scroll = Math.clamp(scroll, 0, scrollLimit());
        for (var card : cards) {
            int y = card.y() - scroll + (card.y() > editingRow ? detailsHeight : 0);
            card.toggle().setY(y);
            card.settings().setY(y);
            card.toggle().visible = card.settings().visible = y + 34 > cardsTop && y < cardsBottom;
        }
        for (var detail : details) {
            var widget = detail.widget();
            widget.setY(detail.y() - scroll);
            widget.visible = widget.getBottom() > cardsTop && widget.getY() < cardsBottom;
        }
    }

    @Override public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (recording) {
            finishRecording(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            return true;
        }
        if (StudioLayout.contains(event.x(), event.y(), layout.contentX(), cardsTop,
                layout.contentX() + layout.contentWidth(), cardsBottom)) {
            if (event.button() == ClientInput.MOUSE_RIGHT) {
                for (var card : cards) {
                    if (card.toggle().isMouseOver(event.x(), event.y()) || card.settings().isMouseOver(event.x(), event.y())) {
                        showSettings(card.module());
                        return true;
                    }
                }
            }
            return super.mouseClicked(event, doubleClick);
        }
        for (var card : cards) card.toggle().visible = card.settings().visible = false;
        for (var detail : details) detail.widget().visible = false;
        try { return super.mouseClicked(event, doubleClick); }
        finally { positionCards(); }
    }

    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (vertical != 0 && StudioLayout.contains(x, y, layout.contentX(), cardsTop,
                layout.contentX() + layout.contentWidth() + 3, cardsBottom)) {
            scroll -= (int) Math.round(vertical * 39);
            positionCards();
            return true;
        }
        return super.mouseScrolled(x, y, horizontal, vertical);
    }

    @Override public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        outline(graphics, layout.x(), layout.y(), layout.width(), layout.height(), 5, BORDER, PANEL);
        graphics.fill(layout.x() + layout.sidebarWidth(), layout.y() + 1, layout.x() + layout.sidebarWidth() + 1,
            layout.y() + layout.height() - 1, BORDER);
        UiFonts.drawTitle(graphics, UiText.tr("Cheating"), layout.x() + 12, layout.y() + 19, TEXT, layout.sidebarWidth() - 20);
        UiFonts.drawTitle(graphics, UiText.tr(category.label), layout.contentX(), layout.y() + 19, TEXT, layout.contentWidth() - 24);
        graphics.nextStratum();
        super.extractRenderState(graphics, x, y, delta);
        if (cards.isEmpty()) uiText(graphics, font, "No modules found", layout.contentX() + 5, cardsTop + 8, DIM, layout.contentWidth());
        if (cardsBottom <= cardsTop) return;
        int left = layout.contentX(), right = left + layout.contentWidth();
        graphics.enableScissor(left, cardsTop, right, cardsBottom);
        int hoverY = y >= cardsTop && y < cardsBottom ? y : Integer.MIN_VALUE;
        int top = detailsTop - scroll;
        if (detailsHeight > 0 && top < cardsBottom && top + detailsHeight > cardsTop) {
            outline(graphics, left, top, layout.contentWidth(), detailsHeight - 4, 4, BORDER, PANEL);
            graphics.nextStratum();
            for (var label : detailLabels) {
                int labelY = label.y() - scroll;
                if (labelY + 11 > cardsTop && labelY < cardsBottom)
                    text(graphics, UiFonts.get(font), label.title(), left + 7, labelY, TEXT, detailWidth());
            }
            for (var detail : details) if (detail.widget().visible) detail.widget().extractRenderState(graphics, x, hoverY, delta);
        }
        for (var card : cards) if (card.toggle().visible) {
            graphics.nextStratum();
            card.toggle().extractRenderState(graphics, x, hoverY, delta);
            card.settings().extractRenderState(graphics, x, hoverY, delta);
        }
        graphics.disableScissor();
        if (scrollLimit() > 0) {
            int height = cardsBottom - cardsTop, thumb = Math.max(12, height * height / contentHeight);
            int thumbTop = cardsTop + scroll * (height - thumb) / scrollLimit();
            graphics.fill(right, cardsTop, right + 2, cardsBottom, BORDER);
            graphics.fill(right, thumbTop, right + 2, thumbTop + thumb, PINK);
        }
    }

    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { ClientUiAccess.setScreen(minecraft, parent); }
}
