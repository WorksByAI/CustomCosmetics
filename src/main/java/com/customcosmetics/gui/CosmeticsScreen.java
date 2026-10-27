package com.customcosmetics.gui;

import com.customcosmetics.compatibility.ClientInput;
import com.customcosmetics.compatibility.ClientUiAccess;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.config.PlayerBlocklist;
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
    public enum Page { MAIN, SIZE, NICK, COLOR, CHROMA, PRESETS, RANK, BLOCKED_NICKS, BLOCKED_ACCESSORIES, BLOCKED_CAPES, POSITION_RECIPIENTS, ANIMATIONS, DAMAGE, ITEMS, ACCESSORIES, MAP, PARTY, SKINS, SYNC, MODULES, SEARCH }
    private static final ScreenSession SESSION = new ScreenSession();
    private final Screen parent;
    private Page page;
    private final List<AbstractWidget> navigation = new ArrayList<>();
    private final List<Placement> sidebarControls = new ArrayList<>();
    private final List<Consumer<GuiGraphicsExtractor>> sidebarArt = new ArrayList<>();
    private final List<Placement> controls = new ArrayList<>();
    private final List<Consumer<GuiGraphicsExtractor>> art = new ArrayList<>();
    private final List<Consumer<GuiGraphicsExtractor>> pinnedArt = new ArrayList<>();
    private int panelX, panelY, panelWidth, panelHeight, sideWidth;
    private int contentX, contentWidth, viewTop, viewBottom, rowY, contentBottom;
    private int playerPreviewHeight;
    private int navTop, navBottom, navContentBottom, navRowY, navScroll, scrollGrabOffset;
    private boolean draggingNavigation;
    private String connectionSummary = "";
    private List<String> connectionLines = List.of();
    private String connectionRosterTitle = "", connectionRosterSource = "";
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
    private String blockedPlayerName = "";
    private String blockedPlayerSearch = "";
    private boolean pinnedNickname, pinnedSize, pinnedAccessories, draggingScroll;
    private final List<SettingsSearch.Entry> searchOptions=new ArrayList<>();
    private String searchQuery="";
    private PinkUi.Field searchField;
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
        navigation.clear(); sidebarControls.clear(); sidebarArt.clear(); controls.clear(); art.clear(); pinnedArt.clear();
        searchOptions.clear();
        hexField = null; picker = null;
        refreshIntervalField = null; syncNowButton = null;
        blockingMode = null;
        blockingStyle = null;
        var layout = StudioLayout.of(width, height);
        panelWidth = layout.width(); panelHeight = layout.height();
        panelX = layout.x(); panelY = layout.y(); sideWidth = layout.sidebarWidth();
        contentX = layout.contentX(); contentWidth = layout.contentWidth();
        viewTop = layout.contentTop(); viewBottom = layout.contentBottom();
        navTop = layout.navigationTop(); navBottom = layout.navigationBottom();
        rowY = viewTop;
        buildNavigation();
        navigation.add(addWidget(new RepositoryButton(panelX + 12, panelY + panelHeight - 42)));
        navigation.add(addWidget(new PinkUi.Action(panelX+47,panelY+panelHeight-42,sideWidth-55,30,"Settings",false,()->open(Page.SYNC)).selected(()->page==Page.SYNC)));
        navigation.add(addWidget(new PinkUi.Action(panelX + panelWidth - 32, panelY + 14, 20, 20, "×", false, this::onClose)));
        searchField=new PinkUi.Field(font,contentX,panelY+54,contentWidth,"Search Settings",24);
        searchField.setMaxLength(128);searchField.setHint(Component.literal("Search settings..."));searchField.setValue(searchQuery);
        searchField.setResponder(value->{
            searchQuery=value;scroll=0;activity();
            page=Page.SEARCH;rebuildWidgets();setFocused(searchField);searchField.moveCursorToEnd(false);
        });
        navigation.add(addWidget(searchField));
        pinnedNickname = panelHeight >= 390 && (page == Page.NICK || page == Page.COLOR || page == Page.CHROMA || page == Page.RANK || page == Page.PRESETS);
        pinnedSize = panelHeight >= 430 && page == Page.SIZE;
        pinnedAccessories = panelHeight >= 410 && page == Page.ACCESSORIES;
        if(pinnedAccessories){accessoryPreview();pinnedArt.addAll(art);art.clear();viewTop=rowY;}
        if (pinnedSize) {
            playerPreview();
            pinnedArt.addAll(art);
            art.clear();
            viewTop = rowY;
        }
        if (pinnedNickname) {
            previewCard(); tabs();
            pinnedArt.addAll(art);
            art.clear();
            for (Placement placement : controls) navigation.add(placement.widget());
            controls.clear();
            viewTop = rowY;
        }
        switch (page) {
            case MAIN -> overview();
            case SIZE -> playerSize();
            case NICK -> nickname();
            case RANK -> rank();
            case COLOR -> colors();
            case CHROMA -> chroma();
            case PRESETS -> presets();
            case BLOCKED_NICKS, BLOCKED_ACCESSORIES, BLOCKED_CAPES -> blockedPlayers();
            case ANIMATIONS -> animations();
            case DAMAGE -> damageSplash();
            case ITEMS -> itemCosmetics();
            case MAP -> mapEditor();
            case SKINS -> skins();
            case ACCESSORIES -> accessories();
            case PARTY -> party();
            case POSITION_RECIPIENTS -> positionRecipients();
            case SYNC -> sync();
            case MODULES -> modules();
            case SEARCH -> searchResults();
        }
        if (page == Page.NICK || page == Page.COLOR || page == Page.CHROMA || page == Page.RANK || page == Page.PRESETS || page == Page.DAMAGE) credit();
        contentBottom = rowY + 5;
        scroll = Math.clamp(scroll, 0, maxScroll());
        positionControls();
    }

    private String pageTitle() {
        return switch (page) {
            case MAIN -> "Overview";
            case SIZE -> "Player Size";
            case NICK, COLOR, CHROMA, PRESETS, RANK, BLOCKED_NICKS -> "NickTweaks";
            case ANIMATIONS -> "Animations";
            case DAMAGE -> "Damage Splash";
            case ITEMS -> "Item Customization";
            case MAP -> "Map Editor";
            case SKINS -> "Skins";
            case ACCESSORIES, BLOCKED_ACCESSORIES -> "Equipments (Beta)";
            case PARTY, POSITION_RECIPIENTS -> "Sharing";
            case SYNC -> "Settings";
            case MODULES -> "Module Manager";
            case BLOCKED_CAPES -> "Cape";
            case SEARCH -> "Search";
        };
    }

    private String pageDescription() {
        return switch (page) {
            case MAIN -> "Your look, your settings, all in one place.";
            case SIZE -> "Proportions and the SmolPeople model.";
            case NICK, COLOR, CHROMA, PRESETS, RANK, BLOCKED_NICKS -> "Names, colors and animated gradients.";
            case ANIMATIONS -> "Hand poses, item placement and swing timing.";
            case DAMAGE -> "Style your local damage numbers.";
            case ITEMS -> "Custom names and models for your items.";
            case MAP -> "Visual layouts, room presets and editing tools.";
            case SKINS -> "Local imports and direct P2P sharing.";
            case ACCESSORIES, BLOCKED_ACCESSORIES -> "Build an outfit and adjust its placement.";
            case PARTY, POSITION_RECIPIENTS -> "Party chat, positions and teammate visuals.";
            case SYNC -> "Preferences, connections and advanced settings.";
            case MODULES -> "Pause whole features while keeping your settings.";
            case BLOCKED_CAPES -> "Choose whose capes you see.";
            case SEARCH -> "Find a setting and jump straight to it.";
        };
    }

    private void buildNavigation() {
        navRowY = navTop;
        navItem("Overview", Page.MAIN);
        navSection("APPEARANCE");
        navItem("NickTweaks", Page.NICK); navItem("Player Size", Page.SIZE);
        navItem("Skins", Page.SKINS); navItem("Equipments (Beta)", Page.ACCESSORIES);
        navAction("Cape", () -> ClientUiAccess.setScreen(minecraft, new CapeScreen(this)), () -> page == Page.BLOCKED_CAPES);
        navSection("SKYBLOCK");
        navItem("Animations", Page.ANIMATIONS); navItem("Damage Splash", Page.DAMAGE);
        navItem("Item Customization", Page.ITEMS); navItem("Map Editor", Page.MAP);
        navSection("CONNECTIONS");
        navItem("Sharing", Page.PARTY);
        navContentBottom = navRowY;
        navScroll = Math.clamp(navScroll, 0, maxNavScroll());
        positionNavigation();
    }

    private void navSection(String title) {
        int y = navRowY + 7;
        sidebarArt.add(g -> text(g, font, title, panelX + 15, y, DIM, sideWidth - 26));
        navRowY += 21;
    }
    private void navItem(String name, Page target) {
        navAction(name, () -> open(target), () -> target == page
            || target == Page.ACCESSORIES && page == Page.BLOCKED_ACCESSORIES
            || target == Page.PARTY && page == Page.POSITION_RECIPIENTS
            || target == Page.NICK && (page == Page.COLOR || page == Page.CHROMA || page == Page.RANK || page == Page.PRESETS || page == Page.BLOCKED_NICKS));
    }
    private void navAction(String name, Runnable callback, BooleanSupplier selected) {
        var widget = new PinkUi.Action(panelX + 8, navRowY, sideWidth - 18, 23, name, false, callback).leftAligned().selected(selected);
        widget.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(name)));
        sidebarControls.add(new Placement(addWidget(widget), navRowY)); navRowY += 26;
    }
    private int maxNavScroll() { return Math.max(0, navContentBottom - navBottom); }
    private void positionNavigation() {
        for (var placement : sidebarControls) {
            var widget = placement.widget(); widget.setY(placement.y() - navScroll);
            widget.visible = widget.getBottom() > navTop && widget.getY() < navBottom;
        }
    }
    private void overview() {
        int total = com.customcosmetics.config.FeatureModules.Feature.values().length;
        int enabled = total - Integer.bitCount(ConfigManager.disabledModules & com.customcosmetics.config.FeatureModules.ALL);
        section("Make it yours", enabled + " of " + total + " modules enabled. Choose a section or search for a setting.");
        int columns = contentWidth >= 400 ? 2 : 1, gap = 10, cellWidth = (contentWidth - gap * (columns - 1)) / columns;
        String[] titles = {"Your appearance", "In-game visuals", "Play together", "Module Manager"};
        String[] descriptions = {"Names, proportions, skins, capes and equipment.", "Animations, damage numbers, items and visual maps.",
            "Party chat, shared positions and teammate markers.", "Turn off unused features and keep your saved preferences."};
        Page[] targets = {Page.NICK, Page.ANIMATIONS, Page.PARTY, Page.MODULES};
        for (int i = 0; i < titles.length; i++) {
            int x = contentX + i % columns * (cellWidth + gap), y = rowY;
            var lines = font.split(Component.literal(descriptions[i]), Math.max(1, cellWidth - 24));
            int height = 90;
            art.add(g -> outline(g, x, y, cellWidth, height, 8, BORDER, CARD));
            label(titles[i], x + 12, y + 12, PINK, cellWidth - 24);
            for (int j = 0; j < Math.min(3, lines.size()); j++) { var line = lines.get(j); int ly = y + 28 + j * 11; art.add(g -> g.text(font, line, x + 12, ly, MUTED, false)); }
            Page target = targets[i];
            add(new PinkUi.Action(x + 10, y + height - 28, cellWidth - 20, 21, "Open " + titles[i], false, () -> open(target)).leftAligned());
            if (i % columns == columns - 1 || i == titles.length - 1) rowY += height + gap;
        }
        section("Command tools", "Additional tools open from chat:");
        paragraph("/ccos waypoint", PINK);
        paragraph("/ccos shortcuts", PINK);
        paragraph("The /customcosmetics command is also available.", DIM);
    }

    private void section(String title, String description) {
        rowY += 7; label(title, contentX, rowY, PINK, contentWidth); rowY += 17;
        if (!description.isBlank()) paragraph(description, MUTED);
        rowY += 3;
    }
    private void paragraph(String text, int color) {
        for (var line : font.split(Component.literal(text), Math.max(1, contentWidth))) {
            int y = rowY; art.add(g -> g.text(font, line, contentX, y, color, false)); rowY += 12;
        }
        rowY += 5;
    }
    private void indexOption(String title,String description,int y){searchOptions.add(new SettingsSearch.Entry(pageTitle(),title,description,page.name(),Math.max(0,y-viewTop)));}
    private void searchResults(){
        var results=SettingsCatalog.find(searchQuery);
        label(searchQuery.isBlank()?"Type a setting name to search.":results.size()+" results",contentX,rowY+4,MUTED,contentWidth);rowY+=24;
        for(var result:results){
            int y=rowY;
            var action=fullButton(result.title(),()->{
                if(result.page().equals("CAPE")){com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,new CapeScreen(this));return;}
                if (result.title().equals("Shimmer Colors")) editingShimmerColors = true;
                else if (result.title().equals("Gradient Colors")) editingShimmerColors = false;
                open(Page.valueOf(result.page()));
                var anchor = searchOptions.stream().filter(option -> SettingsSearch.sameSetting(option.title(), result.title())).findFirst();
                if (anchor.isPresent()) scroll = Math.clamp(anchor.get().offset(), 0, maxScroll());
                else if (result.description().startsWith("Requires ")) toast(result.description());
                positionControls();
            },false);
            action.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(result.section()+" · "+result.title())));
            label(result.section(),contentX+8,y+29,DIM,contentWidth-16);rowY+=14;
        }
    }

    private void damageSplash() {
        int top=card(100);
        label("Preview",contentX+10,top+9,PINK,contentWidth-20);
        label("Critical",contentX+12,top+29,MUTED,contentWidth/2-24);
        label("Normal",contentX+contentWidth/2+12,top+29,MUTED,contentWidth/2-24);
        art.add(g->{
            double duration=com.customcosmetics.features.damage.DamageAnimation.bounded(ConfigManager.damageSplashDuration,1.2,.3,3);
            double progress="default".equals(ConfigManager.damageSplashStyle)?0:(System.nanoTime()/1_000_000_000.0%duration)/duration;
            for(int i=0;i<2;i++){
                boolean critical=i==0;
                var hit=com.customcosmetics.features.damage.DamageText.parse(critical?"✧1,234,567✧":"32,451");
                var sample=com.customcosmetics.features.damage.DamageSplash.styled(hit,0xFFFFFF);
                var frame=com.customcosmetics.features.damage.DamageAnimation.frame(ConfigManager.damageSplashStyle,progress,critical,.4,.2,0);
                if(frame.alpha()<=.01f)continue;
                float size=(float)Math.min(ConfigManager.damageSplashSize*frame.scale(),(contentWidth/2.0-24)/Math.max(1,font.width(sample)));
                g.pose().pushMatrix();
                try{
                    g.pose().translate((float)(contentX+contentWidth*(i==0?.25:.75)+frame.x()*35),(float)(top+66-frame.y()*35));
                    g.pose().scale(size,size);
                    g.text(font,sample,-font.width(sample)/2,0,((int)(frame.alpha()*255)<<24)|0xFFFFFF,ConfigManager.damageSplashShadow);
                }finally{g.pose().popMatrix();}
            }
        });
        toggleRow("Damage Splash","Local damage numbers in Hypixel SkyBlock.",()->ConfigManager.damageSplashEnabled,v->ConfigManager.damageSplashEnabled=v);
        fullButton("Animation: "+capitalize(ConfigManager.damageSplashStyle),()->{
            var styles=com.customcosmetics.features.damage.DamageAnimation.STYLES;
            ConfigManager.damageSplashStyle=styles.get((styles.indexOf(ConfigManager.damageSplashStyle)+1)%styles.size());changed();rebuildWidgets();
        },false);
        sliderGrid(List.of(
            new NumericSetting("Size",()->ConfigManager.damageSplashSize,.25,3,.05,1,v->ConfigManager.damageSplashSize=v),
            new NumericSetting("Duration (seconds)",()->ConfigManager.damageSplashDuration,.3,3,.1,1.2,v->ConfigManager.damageSplashDuration=v)));
        fullButton("Color Mode: "+capitalize(com.customcosmetics.features.damage.DamageColors.normalize(ConfigManager.damageSplashColorMode)),()->{
            ConfigManager.damageSplashColorMode="rainbow".equals(com.customcosmetics.features.damage.DamageColors.normalize(ConfigManager.damageSplashColorMode))?"gradient":"rainbow";
            changed();rebuildWidgets();
        },false);
        if("rainbow".equals(com.customcosmetics.features.damage.DamageColors.normalize(ConfigManager.damageSplashColorMode)))
            sliderGrid(List.of(new NumericSetting("Rainbow Speed",()->ConfigManager.damageSplashRainbowSpeed,0,4,.1,1,v->ConfigManager.damageSplashRainbowSpeed=v)));
        else {damageColor("Gradient Start",true);damageColor("Gradient End",false);}
        var symbol=fieldRow("Critical Symbol","Critical Symbol",ConfigManager.damageSplashSymbol,2,"One character; leave empty to hide.",90,value->{});
        symbol.setResponder(value->{
            String limited=com.customcosmetics.features.damage.DamageText.symbol(value);
            if(!value.equals(limited)){symbol.setValue(limited);return;}
            ConfigManager.damageSplashSymbol=limited;changed();
        });
        toggleRow("Bold","",()->ConfigManager.damageSplashBold,v->ConfigManager.damageSplashBold=v);
        toggleRow("Style Normal Damage","Apply the selected colors and bold style to non-critical damage too.",()->ConfigManager.damageSplashStyleNormal,v->ConfigManager.damageSplashStyleNormal=v);
        toggleRow("Compact Numbers","Example: 1,234,567 becomes 1.2M.",()->ConfigManager.damageSplashCompact,v->ConfigManager.damageSplashCompact=v);
        fullButton("Separator: "+capitalize(ConfigManager.damageSplashSeparator),()->{
            var choices=List.of("none","comma","space","underscore");
            ConfigManager.damageSplashSeparator=choices.get((choices.indexOf(ConfigManager.damageSplashSeparator)+1)%choices.size());changed();rebuildWidgets();
        },false);
        toggleRow("Text Shadow","",()->ConfigManager.damageSplashShadow,v->ConfigManager.damageSplashShadow=v);
    }
    private static String capitalize(String value){return value.substring(0,1).toUpperCase(Locale.ROOT)+value.substring(1);}
    private void damageColor(String title,boolean start){
        var field=fieldRow(title,title+" Color",hex(start?ConfigManager.damageSplashStartColor:ConfigManager.damageSplashEndColor),7,"#RRGGBB",110,value->{});
        field.setResponder(value->{
            activity();String color=value.replaceFirst("^#","");boolean valid=color.matches("[0-9a-fA-F]{6}");field.invalid(!valid);
            if(valid){int rgb=Integer.parseInt(color,16);if(start)ConfigManager.damageSplashStartColor=rgb;else ConfigManager.damageSplashEndColor=rgb;changed();}
        });
    }

    private void party(){
        section("Party visuals", "Control how teammates appear in your world.");
        toggleRow("Party ESP","Highlight your teammates and share your position.",()->ConfigManager.partyEspEnabled,v->ConfigManager.partyEspEnabled=v);
        toggleRow("Chams","See loaded shared players through walls with their original skin, armor, cape and equipment.",()->ConfigManager.sharingChamsEnabled,v->ConfigManager.sharingChamsEnabled=v);
        section("Party chat", "Messages and notification sounds for connected teammates.");
        toggleRow("IRC For Party Members","Send messages to your connected party members.",()->ConfigManager.partyChatEnabled,v->ConfigManager.partyChatEnabled=v);
        toggleRow("Chat Notification Sound","Play a sound for other party members' IRC messages.",()->ConfigManager.partyChatSoundEnabled,v->ConfigManager.partyChatSoundEnabled=v);
        fullButton("Chat Sound: "+com.customcosmetics.features.party.PartyChatSound.label(ConfigManager.partyChatSound),()->ClientUiAccess.setScreen(minecraft,new PartyChatSoundScreen(this)),false,"Choose a notification sound, including sounds from your resource pack.");
        sliderGrid(List.of(
            new NumericSetting("Chat Sound Volume (%)",()->ConfigManager.partyChatSoundVolume*100,0,100,5,50,v->ConfigManager.partyChatSoundVolume=v/100),
            new NumericSetting("Chat Sound Pitch",()->ConfigManager.partyChatSoundPitch,.5,2,.05,1,v->ConfigManager.partyChatSoundPitch=v)
        ));
        fullButton("Preview Chat Sound",()->{if(!com.customcosmetics.features.party.PartyChatSound.preview(minecraft))com.customcosmetics.features.party.PartyFeatures.notice("The selected sound is not available in your current resource pack.");},false,"Try the selected sound, volume and pitch.");
        toggleRow("Connection Notifications","Show connection results after joining a party.",()->ConfigManager.partyConnectionNotifications,v->ConfigManager.partyConnectionNotifications=v);
        var prefixField=fieldRow("Chat Prefix","Chat Prefix",ConfigManager.partyChatPrefix,32,"Empty: all chat to CC Party. Commands are unchanged.",140,value->{});
        prefixField.setResponder(value->{
            activity();boolean valid=value.equals(com.customcosmetics.features.party.PartyChatRules.prefix(value));prefixField.invalid(!valid);
            if(valid){ConfigManager.partyChatPrefix=value;changed();}
        });
        section("Markers & names", "Colors, distance limits and labels.");
        var distanceField=fieldRow("Max Display Distance (blocks)","Max Display Distance",String.format(Locale.ROOT,"%.0f",ConfigManager.partyMaxDisplayDistance),8,"0 = unlimited",100,value->{});
        distanceField.setResponder(value->{
            activity();boolean valid=value.matches("[0-9]{1,8}")&&Double.parseDouble(value)<=com.customcosmetics.features.party.PartyDisplayRange.MAX;distanceField.invalid(!valid);
            if(valid){ConfigManager.partyMaxDisplayDistance=Double.parseDouble(value);changed();}
        });
        fullButton("ESP Style: "+switch(ConfigManager.partyEspStyle){case "none"->"None";case "fill"->"Fill";case "both"->"Fill + Outline";default->"Outline";},()->{
            ConfigManager.partyEspStyle=switch(ConfigManager.partyEspStyle){case "outline"->"fill";case "fill"->"both";case "both"->"none";default->"outline";};changed();rebuildWidgets();
        },false);
        var field=fieldRow("ESP Color","ESP Color",hex(ConfigManager.partyEspColor),7,"",110,value->{});
        field.setResponder(value->{activity();boolean valid=value.matches("#?[0-9a-fA-F]{6}");field.invalid(!valid);if(valid){ConfigManager.partyEspColor=Integer.parseInt(value.replace("#",""),16);changed();}});
        sliderGrid(List.of(
            new NumericSetting("Fill Opacity",()->ConfigManager.partyEspOpacity,.05,.75,.05,.2,v->ConfigManager.partyEspOpacity=v),
            new NumericSetting("Outline Width",()->ConfigManager.partyEspLineWidth,.5,4,.25,1.5,v->ConfigManager.partyEspLineWidth=v)));
        toggleRow("Tracers","Draw lines from the crosshair to your teammates.",()->ConfigManager.partyTracersEnabled,v->ConfigManager.partyTracersEnabled=v);
        var tracerField=fieldRow("Tracer Color","Tracer Color",hex(ConfigManager.partyTracerColor),7,"",110,value->{});
        tracerField.setResponder(value->{activity();boolean valid=value.matches("#?[0-9a-fA-F]{6}");tracerField.invalid(!valid);if(valid){ConfigManager.partyTracerColor=Integer.parseInt(value.replace("#",""),16);changed();}});
        toggleRow("Show Names","Show teammate names and distances.",()->ConfigManager.partyShowNames,v->ConfigManager.partyShowNames=v);
        fullButton("Nametag Style: "+capitalize(ConfigManager.partyNametagStyle),()->{
            ConfigManager.partyNametagStyle="vanilla".equals(ConfigManager.partyNametagStyle)?"simple":"vanilla";changed();rebuildWidgets();
        },false,"Simple or Vanilla: applies to party and location sharing. Vanilla preserves the original name, level, colors and icons. Local data only; unavailable names fall back to the account ID.");
        var namesDistance=fieldRow("Hide Names Within (blocks)","Hide Names Within",String.format(Locale.ROOT,"%.0f",ConfigManager.partyNametagHideDistance),8,"0 = disabled",100,value->{});
        namesDistance.setResponder(value->{
            activity();boolean valid=value.matches("[0-9]{1,8}")&&Double.parseDouble(value)<=com.customcosmetics.features.party.PartyDisplayRange.MAX;namesDistance.invalid(!valid);
            if(valid){ConfigManager.partyNametagHideDistance=Double.parseDouble(value);changed();}
        });
        fullButton("Nametag Scaling: "+("distance".equals(ConfigManager.partyNametagMode)?"Distance":"Fixed Screen Size"),()->{
            ConfigManager.partyNametagMode="distance".equals(ConfigManager.partyNametagMode)?"fixed":"distance";changed();rebuildWidgets();
        },false);
        sliderRow("Nametag Size","",()->ConfigManager.partyNametagSize,.5,3,.1,1,v->ConfigManager.partyNametagSize=v);
        toggleRow("Distant Markers","Requires Hypixel Mod API and both players sharing positions.",()->ConfigManager.partyFarMarkers,v->ConfigManager.partyFarMarkers=v);
        label("Without Mod API: /p list for nearby ESP and chat.",contentX,rowY+4,DIM,contentWidth);rowY+=24;
        section("Location sharing", "Choose who can see your position.");
        toggleRow("Share My Position","Hypixel only. Replaces the Party audience while enabled.",()->ConfigManager.positionSharingEnabled,v->ConfigManager.positionSharingEnabled=v);
        fullButton("Audience: "+("public".equals(ConfigManager.positionSharingMode)?"Public · Same World":"Selected Players"),()->{
            ConfigManager.positionSharingMode="public".equals(ConfigManager.positionSharingMode)?"selected":"public";changed();rebuildWidgets();
        },false);
        fullButton("Selected Players: "+ConfigManager.positionSharingTargets.size(),()->open(Page.POSITION_RECIPIENTS),false);
        toggleRow("Show Shared Positions","Show names and distances shared by non-party players.",()->ConfigManager.positionSharingReceive,v->ConfigManager.positionSharingReceive=v);
        toggleRow("Location Tracers","Draw lines to players whose shared positions you receive.",()->ConfigManager.locationTracersEnabled,v->ConfigManager.locationTracersEnabled=v);
        var locationColor=fieldRow("Location Tracer Color","Location Tracer Color",hex(ConfigManager.locationTracerColor),7,"#RRGGBB",110,value->{});
        locationColor.setResponder(value->{
            activity();String color=value.replaceFirst("^#","");boolean valid=color.matches("[0-9a-fA-F]{6}");locationColor.invalid(!valid);
            if(valid){ConfigManager.locationTracerColor=Integer.parseInt(color,16);changed();}
        });
    }

    private void positionRecipients(){
        fullButton("Back to Sharing",()->open(Page.PARTY),false);
        for(var line:font.split(Component.literal("Only selected players receive your position in Selected Players mode."),contentWidth)){
            int y=rowY;art.add(g->g.text(font,line,contentX,y,MUTED,false));rowY+=12;
        }rowY+=16;
        var search=fieldRow("Search Nickname or Account","Search Nickname or Account",blockedPlayerSearch,64,"",220,value->{});
        search.setResponder(value->{activity();blockedPlayerSearch=value;rebuildWidgets();
            var replacement=controls.stream().map(Placement::widget).filter(widget->widget instanceof PinkUi.Field&&widget.getMessage().getString().equals("Search Nickname or Account")).findFirst().orElseThrow();
            setFocused(replacement);((PinkUi.Field)replacement).moveCursorToEnd(false);
        });
        String query=blockedPlayerSearch.strip().toLowerCase(Locale.ROOT);
        var matches=PresenceSync.syncedPlayers().stream().filter(p->!p.uuid().equals(minecraft.getUser().getProfileId()))
            .filter(p->p.name().toLowerCase(Locale.ROOT).contains(query)||p.nickname().toLowerCase(Locale.ROOT).contains(query))
            .sorted(java.util.Comparator.comparing(PresenceSync.SyncedPlayer::name,String.CASE_INSENSITIVE_ORDER)).toList();
        for(var player:matches){
            int row=card(50);label(player.nickname(),contentX+12,row+10,TEXT,contentWidth-116);label(player.name(),contentX+12,row+29,DIM,contentWidth-116);
            boolean selected=PlayerBlocklist.contains(ConfigManager.positionSharingTargets,player.uuid(),player.name());
            var action=add(new PinkUi.Action(contentX+contentWidth-92,row+12,80,26,selected?"Selected":"Select",false,()->selectPositionRecipient(new PlayerBlocklist.Entry(player.uuid(),player.name()))));
            action.active=!selected&&ConfigManager.positionSharingTargets.size()<com.customcosmetics.features.party.PositionAudience.MAX_TARGETS;
        }
        label("Selected Players",contentX,rowY+4,PINK,contentWidth);rowY+=24;
        if(ConfigManager.positionSharingTargets.isEmpty()){
            for(var line:font.split(Component.literal("No selected players. Your position will not be shared in this mode."),contentWidth-24)){
                int y=rowY+10;art.add(g->g.text(font,line,contentX+12,y,DIM,false));rowY+=12;
            }rowY+=20;
        }
        for(var entry:ConfigManager.positionSharingTargets){int row=card(38);label(entry.account(),contentX+12,row+14,TEXT,contentWidth-116);
            add(new PinkUi.Action(contentX+contentWidth-92,row+6,80,26,"Remove",false,()->{
                ConfigManager.positionSharingTargets=ConfigManager.positionSharingTargets.stream().filter(e->!e.equals(entry)).toList();changed();rebuildWidgets();
            }));
        }
        int y=card(68);label("Account Name",contentX+12,y+10,TEXT,contentWidth-24);
        var field=input(contentX+12,y+28,contentWidth-126,"Recipient Account Name",blockedPlayerName,16,value->{});
        var select=add(new PinkUi.Action(contentX+contentWidth-102,y+28,90,26,"Select",true,()->{
            String name=blockedPlayerName.strip();if(!name.matches("[A-Za-z0-9_]{3,16}"))return;
            var known=PresenceSync.syncedPlayers().stream().filter(p->p.name().equalsIgnoreCase(name)).findFirst();
            blockedPlayerName="";selectPositionRecipient(new PlayerBlocklist.Entry(known.map(PresenceSync.SyncedPlayer::uuid).orElse(null),name));
        }));select.active=blockedPlayerName.strip().matches("[A-Za-z0-9_]{3,16}")&&ConfigManager.positionSharingTargets.size()<com.customcosmetics.features.party.PositionAudience.MAX_TARGETS;
        field.setResponder(value->{activity();blockedPlayerName=value;boolean valid=value.strip().matches("[A-Za-z0-9_]{3,16}");field.invalid(!value.isEmpty()&&!valid);select.active=valid&&ConfigManager.positionSharingTargets.size()<com.customcosmetics.features.party.PositionAudience.MAX_TARGETS;});
    }

    private void selectPositionRecipient(PlayerBlocklist.Entry entry){
        var entries=new ArrayList<>(ConfigManager.positionSharingTargets);entries.removeIf(e->e.account().equalsIgnoreCase(entry.account()));entries.add(entry);
        ConfigManager.positionSharingTargets=com.customcosmetics.features.party.PositionAudience.targets(entries);changed();rebuildWidgets();
    }
    private void smol() {
        toggleRow("SmolPeople", "Use the small player model alongside your size settings.", () -> ConfigManager.smolpeopleMode, v -> ConfigManager.smolpeopleMode = v);
        sliderRow("Limb Swing Speed", "", () -> ConfigManager.smolLimbSwingSpeed,
            0.5, 5, 0.1, 2.5, v -> ConfigManager.smolLimbSwingSpeed = v);
    }

    private void playerSize(){
        if (!pinnedSize) playerPreview();
        toggleRow("Enabled","Share your proportions with other players.",()->ConfigManager.playerSizeEnabled,v->ConfigManager.playerSizeEnabled=v);
        sliderGrid(List.of(
            new NumericSetting("Size X",()->ConfigManager.playerSizeX,.1,3,.05,1,v->ConfigManager.playerSizeX=v),
            new NumericSetting("Size Y",()->ConfigManager.playerSizeY,.1,3,.05,1,v->ConfigManager.playerSizeY=v),
            new NumericSetting("Size Z",()->ConfigManager.playerSizeZ,.1,3,.05,1,v->ConfigManager.playerSizeZ=v)));
        fullButton("Reset Player Size",()->{ConfigManager.playerSizeX=ConfigManager.playerSizeY=ConfigManager.playerSizeZ=1;changed();rebuildWidgets();},false);
        rowY+=6;smol();
        section("Creature Model", "Choose a visual model for yourself. Mod users can see your choice after sync.");
        for (var choice : com.customcosmetics.features.creature.CreatureModels.availableChoices()) {
            var model = choice;
            fullButton((ConfigManager.creatureModel.equals(choice.id) ? "✓ " : "") + choice.label,
                () -> {com.customcosmetics.features.creature.CreatureModels.select(model);changed();rebuildWidgets();},false);
        }
    }

    private void playerPreview() {
        int height = PreviewLayout.playerCardHeight(viewBottom - viewTop, pinnedSize);
        playerPreviewHeight = height;
        int y = card(height);
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
            // The inventory preview uses the same selected creature as world rendering.
            // Entity bounds are screen coordinates; draw outside the scrolling pose.
            int previewScroll = pinnedSize ? 0 : (int) Math.round(scroll);
            int top = y - previewScroll;
            var preview = com.customcosmetics.features.creature.CreatureModels.previewEntity(minecraft.player);
            int modelScale = preview == minecraft.player ? PreviewLayout.playerModelScale(height)
                : PreviewLayout.creatureModelScale(height, contentWidth - 8, preview.getBbWidth(), preview.getBbHeight());
            g.pose().pushMatrix();
            g.pose().translate(0, (float) previewScroll);
            InventoryScreen.extractEntityInInventoryFollowsMouse(g,
                contentX + 4, top + 51, contentX + contentWidth - 4, top + height - 6,
                modelScale, 0.0625f, mouseX, mouseY, preview);
            g.pose().popMatrix();
        });
    }

    private void accessories() {
        if(!pinnedAccessories)accessoryPreview();
        accessoryControls();
    }
    private void accessoryPreview(){
        int y=card(166);label("Preview",contentX+12,y+10,PINK,contentWidth-24);
        art.add(g->{int offset=pinnedAccessories?0:(int)Math.round(scroll);g.pose().pushMatrix();g.pose().translate(0,offset);
            int half=(contentWidth-24)/2;
            CapePreview.drawAccessories(g,contentX+12,y+26-offset,half,130,true);
            CapePreview.drawAccessories(g,contentX+12+half,y+26-offset,half,130,false);g.pose().popMatrix();});
    }
    private void accessoryControls(){
        fullButton("Blocked Equipments",()->open(Page.BLOCKED_ACCESSORIES),false);
        fullButton("Open Wardrobe",()->com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,new WardrobeScreen(this)),false);
        label("Choose styles for each part, then adjust colors and size.",contentX,rowY,MUTED,contentWidth);rowY+=16;
        label("Online outfits follow your normal sync interval.",contentX,rowY,MUTED,contentWidth);rowY+=16;
        fullButton("Clear Outfit",()->{ConfigManager.accessories=com.customcosmetics.features.accessories.AccessoriesSettings.NONE;changed();rebuildWidgets();},false);
    }

    private void itemCosmetics() {
        section("SkyBlock accessories", "See collected tiers when hovering accessories.");
        toggleRow("Accessory Helper Tooltip", "Open every Accessory Bag page once per profile to record ownership.",
            () -> ConfigManager.accessoryHelperTooltipEnabled, value -> ConfigManager.accessoryHelperTooltipEnabled = value);
        fullButton("Clear This Profile's Bag Scan", () -> {
            boolean cleared = com.customcosmetics.features.accessoryhelper.AccessoryTooltipHelper.clearCurrent();
            toast(cleared ? "Accessory Bag scan cleared. Reopen every page to refresh." : "No profile scan to clear.");
            rebuildWidgets();
        }, false);
        String accessoryNotice = com.customcosmetics.features.accessoryhelper.AccessoryTooltipHelper.notice();
        if (!accessoryNotice.isEmpty()) { label(accessoryNotice, contentX, rowY, MUTED, contentWidth); rowY += 16; }
        section("Item appearance", "Customize names and models on your client.");
        toggleRow("Enabled", "Custom item names and models. Only visible on your client.",
            () -> com.customcosmetics.features.items.ItemCosmetics.store().enabled(),
            value -> com.customcosmetics.features.items.ItemCosmetics.store().enabled(value));
        fullButton("Customize Items", () -> com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,
            new ItemAppearanceScreen(this)), false);
        label("Select a specific SkyBlock item from your inventory.", contentX, rowY, MUTED, contentWidth);rowY += 16;
        label("Models include vanilla and your enabled resource packs.", contentX, rowY, MUTED, contentWidth);rowY += 16;
    }

    private void animations() {
        section("Sword blocking", "Choose a pose and decide when to use it.");
        toggleRow("Sword Blocking", "Hold Use with a sword. Visual effect only.",
            () -> ConfigManager.swordBlockingEnabled, v -> ConfigManager.swordBlockingEnabled = v);
        fullButton("Adjust Blocking", () -> com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,
            new BlockingAdjustmentScreen(this)), false);
        indexOption("Blocking Style","Default Jigsaw",rowY);
        blockingStyle = add(new BlockingStyleDropdown(contentX, rowY, contentWidth));
        rowY += 38;
        indexOption("Blocking List Mode","Off Blacklist Whitelist",rowY);
        blockingMode = add(new BlockingModeDropdown(contentX, rowY, contentWidth));
        rowY += 38;
        fullButton("Edit Blocking Lists", () -> com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,
            new BlockingItemsScreen(this)), false);
        toggleRow("Apply Lists to Other Players", "Filter their blocking with your blacklist or whitelist.",
            () -> ConfigManager.swordBlockingListsApplyToOthers, v -> ConfigManager.swordBlockingListsApplyToOthers = v);
        toggleRow("Share Blocking", "Show other players and share your pose. Requires Automatic Sync ENABLED.",
            () -> ConfigManager.swordBlockingSyncEnabled, v -> ConfigManager.swordBlockingSyncEnabled = v);
        section("View model", "Position, scale and rotation of your held item.");
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
        sliderGrid(numbers);
        section("Movement & swing", "");
        toggleRow("Disable Hand Movement", "", () -> ConfigManager.animationDisableHandMove, v -> ConfigManager.animationDisableHandMove = v);
        toggleRow("Disable Equip Animation", "", () -> ConfigManager.animationDisableEquip, v -> ConfigManager.animationDisableEquip = v);
        toggleRow("Disable Swing Animation", "", () -> ConfigManager.animationDisableSwing, v -> { ConfigManager.animationDisableSwing = v; rebuildWidgets(); });
        if (ConfigManager.animationDisableSwing)
            toggleRow("Terminator Only", "", () -> ConfigManager.animationTerminatorOnly, v -> { ConfigManager.animationTerminatorOnly = v; rebuildWidgets(); });
        if (!ConfigManager.animationDisableSwing || ConfigManager.animationTerminatorOnly) {
            toggleRow("Ignore Haste", "Use normal swing timing instead of potion timing.", () -> ConfigManager.animationIgnoreHaste, v -> ConfigManager.animationIgnoreHaste = v);
        }
        indexOption("Reset View Model", "Reset held item placement", rowY);
        add(new PinkUi.Action(contentX,rowY,Math.min(132, contentWidth),26,"Reset View Model",false,() -> { ConfigManager.resetAnimations(); changed(); rebuildWidgets(); }));
        rowY += 32;
    }

    private void tabs() {
        String[] labels = {"General", "Gradient", "Chroma", "Presets", "Rank"};
        Page[] pages = {Page.NICK, Page.COLOR, Page.CHROMA, Page.PRESETS, Page.RANK};
        int columns = contentWidth < 240 ? 2 : contentWidth < 400 ? 3 : 5;
        int w = (contentWidth - (columns - 1) * 4) / columns;
        for (int i = 0; i < pages.length; i++) {
            Page target = pages[i];
            add(new PinkUi.Action(contentX + (i % columns) * (w + 4), rowY + (i / columns) * 29, w, 25, labels[i], false, () -> open(target)).selected(() -> page == target));
        }
        rowY += ((pages.length + columns - 1) / columns) * 29 + 6;
    }

    private void rank() {
        if (!pinnedNickname) { if(panelHeight>=300)previewCard(); tabs(); }
        toggleRow("Custom Rank", "Independent of nickname colors and renaming.", () -> ConfigManager.nickTweaksRankEnabled, value -> ConfigManager.nickTweaksRankEnabled=value);
        fullButton("Rank Type: "+new com.customcosmetics.features.nickname.RankSettings(true,ConfigManager.nickTweaksRankPlusColor,ConfigManager.nickTweaksRankType,ConfigManager.nickTweaksRankTagColor).label(),()->{
            ConfigManager.nickTweaksRankType=ConfigManager.nickTweaksRankType.equals("mvp_plus_plus")?"mvp_plus":"mvp_plus_plus";changed();rebuildWidgets();
        },false);
        if(ConfigManager.nickTweaksRankType.equals("mvp_plus_plus"))fullButton("MVP++ Style: "+(ConfigManager.nickTweaksRankTagColor.equals("aqua")?"Aqua":"Gold"),()->{
            ConfigManager.nickTweaksRankTagColor=ConfigManager.nickTweaksRankTagColor.equals("aqua")?"gold":"aqua";changed();rebuildWidgets();
        },false);
        label("Plus Color",contentX,rowY+5,PINK,contentWidth);rowY+=25;
        var colors=com.customcosmetics.features.nickname.RankSettings.PlusColor.values();
        int columns=contentWidth<300?2:3,w=(contentWidth-(columns-1)*6)/columns;
        for(int i=0;i<colors.length;i++){
            var color=colors[i];
            add(new PinkUi.Action(contentX+(i%columns)*(w+6),rowY+(i/columns)*29,w,25,color.label,false,()->{
                ConfigManager.nickTweaksRankPlusColor=color.id();changed();rebuildWidgets();
            }).selected(()->color.id().equals(ConfigManager.nickTweaksRankPlusColor)));
        }
        rowY+=((colors.length+columns-1)/columns)*29+8;
        var prefix=new com.customcosmetics.features.nickname.RankSettings(true,ConfigManager.nickTweaksRankPlusColor,ConfigManager.nickTweaksRankType,ConfigManager.nickTweaksRankTagColor).prefix();
        label(prefix+" "+(minecraft.player==null?"Player":minecraft.getUser().getName()),contentX,rowY+5,TEXT,contentWidth);rowY+=28;
        label("Both + signs use the selected color in [MVP++].",contentX,rowY+4,MUTED,contentWidth);rowY+=22;
        label("Visible to mod users. Admin rank overrides take priority.",contentX,rowY+4,MUTED,contentWidth);rowY+=25;
    }

    private void nickname() {
        if (!pinnedNickname) { previewCard(); tabs(); }
        toggleRow("Enabled", "", () -> ConfigManager.nickTweaksEnabled, v -> ConfigManager.nickTweaksEnabled = v);
        ConfigManager.nickTweaksNickChanger = com.customcosmetics.config.NicknameLimit.limit(ConfigManager.nickTweaksNickChanger);
        var nickname = fieldRow("Nick Changer","Nick Changer",ConfigManager.nickTweaksNickChanger,
            com.customcosmetics.config.NicknameLimit.MAX_LENGTH,"Color codes: &0–&f, &r",220,value -> {});
        nickname.setHint(Component.literal("Account name (40 characters max)"));
        nickname.setResponder(value -> {
            String limited = com.customcosmetics.config.NicknameLimit.limit(value);
            if (!value.equals(limited)) { nickname.setValue(limited); return; }
            activity(); ConfigManager.nickTweaksNickChanger = limited; changed();
        });
        toggleRow("Bold Self Nick", "", () -> ConfigManager.nickTweaksBoldSelf, v -> ConfigManager.nickTweaksBoldSelf = v);
        fullButton("Blocked Nicknames",()->open(Page.BLOCKED_NICKS),false);
    }

    private com.customcosmetics.sync.PlayerBlocks.Kind blockKind(){return switch(page){case BLOCKED_ACCESSORIES->com.customcosmetics.sync.PlayerBlocks.Kind.ACCESSORIES;case BLOCKED_CAPES->com.customcosmetics.sync.PlayerBlocks.Kind.CAPE;default->com.customcosmetics.sync.PlayerBlocks.Kind.NICK;};}
    private List<PlayerBlocklist.Entry> blockList(){return com.customcosmetics.sync.PlayerBlocks.entries(blockKind());}
    private void blockList(List<PlayerBlocklist.Entry> entries){com.customcosmetics.sync.PlayerBlocks.set(blockKind(),entries);}
    private void blockedPlayers(){
        boolean accessories=page==Page.BLOCKED_ACCESSORIES,capes=page==Page.BLOCKED_CAPES;
        fullButton(capes?"Back to Cape":accessories?"Back to Equipments":"Back to NickTweaks",()->{if(capes)onClose();else open(accessories?Page.ACCESSORIES:Page.NICK);},false);
        label(capes?"Blocked Capes":accessories?"Blocked Equipments":"Blocked Nicknames",contentX,rowY+4,PINK,contentWidth);rowY+=22;
        for(var line:font.split(Component.literal(capes?"Hide these players' custom capes. Local only.":accessories?"Hide these players' accessories. Local only.":"Show these players' original account names. Local only."),contentWidth)){
            int y=rowY;art.add(g->g.text(font,line,contentX,y,MUTED,false));rowY+=12;
        }rowY+=10;
        rowY+=8;label("Synced Players",contentX,rowY+4,PINK,contentWidth);rowY+=24;
        var search=fieldRow("Search Nickname or Account","Search Nickname or Account",blockedPlayerSearch,64,"",220,value->{});
        search.setResponder(value->{activity();blockedPlayerSearch=value;rebuildWidgets();
            var replacement=controls.stream().map(Placement::widget).filter(widget->widget instanceof PinkUi.Field&&widget.getMessage().getString().equals("Search Nickname or Account")).findFirst().orElseThrow();
            setFocused(replacement);((PinkUi.Field)replacement).moveCursorToEnd(false);
        });
        String query=blockedPlayerSearch.strip().toLowerCase(Locale.ROOT);
        var matches=PresenceSync.syncedPlayers().stream().filter(player->!player.uuid().equals(minecraft.getUser().getProfileId()))
            .filter(player->player.name().toLowerCase(Locale.ROOT).contains(query)||player.nickname().toLowerCase(Locale.ROOT).contains(query))
            .sorted(java.util.Comparator.comparing(PresenceSync.SyncedPlayer::name,String.CASE_INSENSITIVE_ORDER)).toList();
        if(matches.isEmpty()){label("No matching synced players.",contentX+12,rowY+10,DIM,contentWidth-24);rowY+=30;}
        for(var player:matches){
            String alias=player.nickname();if(alias.isBlank())alias=player.name();
            int row=card(50);label(alias,contentX+12,row+10,TEXT,contentWidth-116);label(player.name(),contentX+12,row+29,DIM,contentWidth-116);
            boolean blocked=PlayerBlocklist.contains(blockList(),player.uuid(),player.name());
            var action=add(new PinkUi.Action(contentX+contentWidth-92,row+12,80,26,blocked?"Blocked":"Block",false,()->{
                var entries=new ArrayList<>(blockList());entries.add(new PlayerBlocklist.Entry(player.uuid(),player.name()));
                blockList(entries);changed();rebuildWidgets();
            }));action.active=!blocked;
        }
        rowY+=8;label("Blocked Players",contentX,rowY+4,PINK,contentWidth);rowY+=24;
        if(blockList().isEmpty()){label("No blocked players.",contentX+12,rowY+10,DIM,contentWidth-24);rowY+=30;}
        for(var entry:blockList()){
            int row=card(38);label(entry.account(),contentX+12,row+14,TEXT,contentWidth-116);
            add(new PinkUi.Action(contentX+contentWidth-92,row+6,80,26,"Unblock",false,()->{
                blockList(blockList().stream().filter(value->!value.equals(entry)).toList());changed();rebuildWidgets();
            }));
        }
        int y=card(68);label("Account Name",contentX+12,y+10,TEXT,contentWidth-24);
        var field=input(contentX+12,y+28,contentWidth-126,"Blocked Account Name",blockedPlayerName,16,value->{});
        field.setHint(Component.literal("Minecraft username"));
        var block=add(new PinkUi.Action(contentX+contentWidth-102,y+28,90,26,"Block",true,()->{
            String name=blockedPlayerName.strip();
            if(!com.customcosmetics.config.PlayerBlocklist.valid(name))return;
            var result=com.customcosmetics.sync.PlayerBlocks.edit(true,blockKind(),name,PresenceSync.syncedPlayers());toast(result.message());
            blockedPlayerName="";changed();rebuildWidgets();
        }));
        block.active=com.customcosmetics.config.PlayerBlocklist.valid(blockedPlayerName.strip());
        field.setResponder(value->{activity();blockedPlayerName=value;
            boolean valid=com.customcosmetics.config.PlayerBlocklist.valid(value.strip());field.invalid(!value.isEmpty()&&!valid);block.active=valid;
        });
    }

    private void colors() {
        if (!pinnedNickname) { previewCard(); tabs(); }
        toggleRow("Custom Nick Color", "Chroma takes priority.", () -> ConfigManager.nickTweaksCustomNickColorEnabled, v -> ConfigManager.nickTweaksCustomNickColorEnabled = v);
        String mode = switch (com.customcosmetics.features.nickname.NicknameGradient.normalizeStyle(ConfigManager.nickTweaksGradientStyle)) {
            case "smooth" -> "Smooth"; case "shimmer" -> "Shimmer"; case "segmented" -> "Segmented"; default -> "Per Character";
        };
        fullButton("Gradient Style: " + mode, () -> {
            ConfigManager.nickTweaksGradientStyle = switch (ConfigManager.nickTweaksGradientStyle) {
                case "character" -> "smooth"; case "smooth" -> "shimmer"; case "shimmer" -> "segmented"; default -> "character";
            };
            changed(); rebuildWidgets();
        }, false);
        if(ConfigManager.nickTweaksGradientStyle.equals("segmented"))
            fullButton("Edit Gradient Segments",()->com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,new NicknameSegmentsScreen(this)),false);
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
            sliderGrid(List.of(
                new NumericSetting("Shimmer Size", () -> ConfigManager.nickTweaksShimmerSize, 0.02, 1, 0.01, 0.16, v -> ConfigManager.nickTweaksShimmerSize = v),
                new NumericSetting("Shimmer Interval", () -> ConfigManager.nickTweaksShimmerInterval, 0, 30, 0.1, 2, v -> ConfigManager.nickTweaksShimmerInterval = v,"Seconds between sweeps."),
                new NumericSetting("Shimmer Speed", () -> ConfigManager.nickTweaksShimmerSpeed, 0, 4, 0.1, 1, v -> ConfigManager.nickTweaksShimmerSpeed = v)));
        }
        if (!ConfigManager.nickTweaksGradientStyle.equals("shimmer")) editingShimmerColors = false;
        if (ConfigManager.nickTweaksGradientStyle.equals("shimmer"))
            fullButton("Editing: " + (editingShimmerColors ? "Shimmer Colors" : "Gradient Colors"), () -> {
                editingShimmerColors = !editingShimmerColors; selectedEndpoint = 0; rebuildWidgets();
            }, false);
        indexOption(editingShimmerColors?"Shimmer Colors":"Gradient Colors","Start end color picker HEX",rowY);
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
        fieldRow("Save Current Colors","Preset Name",presetName,40,"",220,value -> presetName = value);
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
                case "shimmer" -> "Shimmer"; case "smooth" -> "Smooth"; case "segmented" -> "Segmented"; default -> "Per Character";
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
        sliderGrid(List.of(
            new NumericSetting("Lightness", () -> ConfigManager.nickTweaksChromaLightness, 0.2, 1, 0.05, 0.8, v -> ConfigManager.nickTweaksChromaLightness = v),
            new NumericSetting("Chroma", () -> ConfigManager.nickTweaksChromaChroma, 0, 0.4, 0.01, 0.2, v -> ConfigManager.nickTweaksChromaChroma = v),
            new NumericSetting("Size", () -> ConfigManager.nickTweaksChromaSize, 0.5, 10, 0.1, 3.1, v -> ConfigManager.nickTweaksChromaSize = v),
            new NumericSetting("Speed", () -> ConfigManager.nickTweaksChromaSpeed, 0.1, 8, 0.1, 1, v -> ConfigManager.nickTweaksChromaSpeed = v)));
    }

    private void modules(){
        paragraph("Disabled modules skip their main work. Detailed preferences are kept for the next time you enable them.", MUTED);
        fullButton("Enable All Modules",()->{int before=ConfigManager.disabledModules;com.customcosmetics.config.FeatureModules.all(true);moduleChanged(before);},false);
        fullButton("Disable All Modules",()->{int before=ConfigManager.disabledModules;com.customcosmetics.config.FeatureModules.all(false);moduleChanged(before);},false);
        for(var feature:com.customcosmetics.config.FeatureModules.Feature.values())
            toggleRow(feature.label,"Master switch; keeps your detailed settings.",()->com.customcosmetics.config.FeatureModules.enabled(feature),value->{int before=ConfigManager.disabledModules;com.customcosmetics.config.FeatureModules.set(feature,value);moduleChanged(before);});
    }
    private void moduleChanged(int before){
        changed();int difference=before^ConfigManager.disabledModules;
        if((difference&com.customcosmetics.config.FeatureModules.Feature.MAP.bit())!=0)com.customcosmetics.features.map.MapEditor.refresh();
        if(!com.customcosmetics.config.FeatureModules.remoteConsumers()){
            PresenceSync.onModeChanged();com.customcosmetics.sync.RealtimeSync.disconnect();com.customcosmetics.sync.PartySync.disconnect();com.customcosmetics.sync.BlockingSync.disconnect();
        }
        if(!com.customcosmetics.config.FeatureModules.enabled(com.customcosmetics.config.FeatureModules.Feature.SKINS))com.customcosmetics.features.skins.LocalSkins.close();
        rebuildWidgets();
    }
    private void sync() {
        section("General", "Preferences and feature availability.");
        fullButton("Module Manager",()->open(Page.MODULES),false,"Enable or disable whole features without losing their settings.");
        toggleRow("Check for Updates","Check GitHub for new releases and show a chat notification. Enabled by default.",()->ConfigManager.updateCheckEnabled,v->ConfigManager.updateCheckEnabled=v);
        toggleRow("Command Tooltips","Show the actual command when hovering clickable text. Preserves existing text, item and entity tooltips.",()->ConfigManager.commandTooltipsEnabled,v->ConfigManager.commandTooltipsEnabled=v);
        toggleRow("Snaplook","Hold your assigned key to switch perspective; release to restore your previous view.",()->ConfigManager.snaplookEnabled,v->{ConfigManager.snaplookEnabled=v;if(!v)com.customcosmetics.features.camera.Snaplook.stop(minecraft);});
        fullButton("Snaplook View: "+switch(com.customcosmetics.features.camera.Snaplook.normalize(ConfigManager.snaplookPerspective)){case "first"->"First Person";case "back"->"Third Person Back";default->"Third Person Front (Rear View)";},()->{
            ConfigManager.snaplookPerspective=switch(com.customcosmetics.features.camera.Snaplook.normalize(ConfigManager.snaplookPerspective)){case "front"->"back";case "back"->"first";default->"front";};changed();rebuildWidgets();
        },false);
        fullButton("Snaplook Keybind…",()->ClientUiAccess.setScreen(minecraft,new net.minecraft.client.gui.screens.options.controls.KeyBindsScreen(this,minecraft.options)),false,"Assign Snaplook under CustomCosmetics: Camera. Unbound by default.");
        section("Connections", "Choose how your cosmetics and teammates stay in sync.");
        fullButton("Mode: "+(ConfigManager.offlineMode?"Offline":"Online"),()->{
            com.customcosmetics.config.CosmeticsMode.setOffline(!ConfigManager.offlineMode);activity();rebuildWidgets();
        },false).setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal("Offline: local cosmetics only. No remote connections.")));
        toggleRow("Automatic Sync", "", () -> ConfigManager.syncEnabled, v -> ConfigManager.syncEnabled = v);
        refreshIntervalField = fieldRow("Refresh Interval (seconds)","Refresh Interval",
            Integer.toString(ConfigManager.getSyncRefreshIntervalSeconds()),8,"30–7200 seconds (2 hours)",100,value -> {
                try {
                    int seconds = Integer.parseInt(value.trim());
                    boolean valid = seconds >= 30 && seconds <= 7200;
                    refreshIntervalField.invalid(!valid);
                    if (valid) { ConfigManager.syncRefreshIntervalSeconds = seconds; ConfigManager.requestSave(); }
                } catch (NumberFormatException e) { refreshIntervalField.invalid(true); }
            });
        toggleRow("Notifications", "Show sync results in chat.", () -> ConfigManager.syncNotifications, v -> ConfigManager.syncNotifications = v);
        syncNowButton = fullButton("Sync Now", () -> {
            if (minecraft.player == null) { toast("No active world"); return; }
            if (PresenceSync.syncOnce()) toast("Sync requested");
            updateSyncNowButton();
        }, true);
        updateSyncNowButton();
        section("Advanced connections", "Leave these settings unchanged unless you use a custom network configuration.");
        connectionStatus();
        proxyText("Proxy Host",ConfigManager.syncProxyHost,value -> ConfigManager.syncProxyHost = value);
        var port = fieldRow("Proxy Port","Proxy Port",ConfigManager.syncProxyPort == 0 ? "" : Integer.toString(ConfigManager.syncProxyPort),5,"",100,value -> {});
        port.setResponder(value -> {
            activity();
            try { int parsed = value.isBlank() ? 0 : Integer.parseInt(value); boolean valid = parsed >= 0 && parsed <= 65535;
                port.invalid(!valid); if (valid) { ConfigManager.syncProxyPort = parsed; proxyChanged(); }
            } catch (NumberFormatException error) { port.invalid(true); }
        });
        var auth = proxyText("Proxy Auth",ConfigManager.syncProxyAuth,value -> ConfigManager.syncProxyAuth = value);
        auth.secret(true);
        toggleRow("Disable P2P Connections","",()->!ConfigManager.partyP2pEnabled,v->ConfigManager.partyP2pEnabled=!v);
        var p2pPort=fieldRow("P2P UDP Port","P2P UDP Port",Integer.toString(ConfigManager.getSyncP2pPort()),5,"0 = automatic. Applied when this menu closes.",100,value->{});
        p2pPort.setResponder(value->{
            activity();
            try{int parsed=value.isBlank()?0:Integer.parseInt(value);boolean valid=parsed>=0&&parsed<=65535;
                p2pPort.invalid(!valid);if(valid){ConfigManager.syncP2pPort=parsed;changed();}
            }catch(NumberFormatException error){p2pPort.invalid(true);}
        });
        toggleRow("Fallback Direct","Retry without the proxy if it fails.",() -> ConfigManager.syncProxyFallbackDirect,
            value -> { ConfigManager.syncProxyFallbackDirect = value; proxyChanged(); });
        indexOption("Proxy Source", "Advanced connection proxy source none manual auto", rowY);
        add(new PinkUi.Action(contentX,rowY,contentWidth,28,"Proxy Source: " + ConfigManager.syncProxySource,false,() -> {
            var sources = List.of("none","manual","auto");
            ConfigManager.syncProxySource = sources.get((sources.indexOf(ConfigManager.syncProxySource) + 1) % sources.size());
            proxyChanged(); rebuildWidgets();
        })); rowY += 36;
    }

    private PinkUi.Action fullButton(String title,Runnable action){return fullButton(title,action,false);}
    private void skins(){
        fullButton("Import Local Skin",()->com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,new SkinImportScreen(this)));
        if(minecraft.player!=null&&com.customcosmetics.features.skins.LocalSkins.hasLocal()){int height=panelHeight<300?76:142;int y=card(height),half=contentWidth/2;art.add(g->{CapePreview.drawAccessories(g,contentX,y+4,half,height-8,true,WardrobeCamera.DEFAULT);CapePreview.drawAccessories(g,contentX+half,y+4,half,height-8,false,WardrobeCamera.DEFAULT);});}
        toggleRow("Show My Local Skin","Your local import works without a server connection.",()->ConfigManager.localSkinEnabled,v->ConfigManager.localSkinEnabled=v);
        fullButton("Remove Local Skin",()->{try{com.customcosmetics.features.skins.LocalSkins.clear();}catch(java.io.IOException error){com.customcosmetics.features.cape.CapeCosmetics.notice(error.getMessage());}});
        fullButton("Skin Model: "+(ConfigManager.localSkinSlim?"Slim (Alex)":"Wide (Steve)"),()->{com.customcosmetics.features.skins.LocalSkins.slim(!ConfigManager.localSkinSlim);rebuildWidgets();});
        toggleRow("Share Skin via P2P","Only established Sharing direct connections. No relay or server upload.",()->ConfigManager.skinP2pSharing,v->ConfigManager.skinP2pSharing=v);
        toggleRow("Receive P2P Skins","Restore the original skin when the direct connection ends.",()->ConfigManager.skinP2pReceiving,v->ConfigManager.skinP2pReceiving=v);
        label("Established P2P peers: "+com.customcosmetics.features.skins.LocalSkins.directCount(),contentX,rowY+5,MUTED,contentWidth);rowY+=24;
        for(var line:font.split(Component.literal("PNG up to 10 MiB. Model pixels are preserved. Skin transfers share an 8 Mbps upload limit, with missing-chunk retries."),contentWidth-16)){int y=rowY;art.add(g->g.text(font,line,contentX+8,y,MUTED,false));rowY+=12;}
    }
    private void mapEditor(){
        toggleRow("Freecam","WASD / Jump / Sneak: fly. Sprint: faster. Bind a key in Controls.",com.customcosmetics.features.map.Freecam::active,v->com.customcosmetics.features.map.MapCommands.run(()->com.customcosmetics.features.map.Freecam.setEnabled(v)));
        sliderGrid(List.of(new NumericSetting("Freecam Speed (blocks/s)",()->com.customcosmetics.features.map.Freecam.speed,1,40,1,12,v->{com.customcosmetics.features.map.Freecam.speed=v;com.customcosmetics.features.map.MapEditor.save();})));
        toggleRow("Map Editor","Local edits. Other players keep the original map.",()->com.customcosmetics.features.map.MapEditor.enabled,v->{com.customcosmetics.features.map.MapEditor.enabled=v;com.customcosmetics.features.map.MapEditor.refresh();});
        String explanation="Visual only: keeps real collision and mining. Air additions have no collision.";
        for(var line:font.split(Component.literal(explanation),contentWidth-16)){int y=rowY;art.add(g->g.text(font,line,contentX+8,y,MUTED,false));rowY+=12;}rowY+=6;
        toggleRow("Dungeon Only","Edit only inside the Catacombs, Kuudra or local single-player worlds.",()->com.customcosmetics.features.map.MapEditor.dungeonOnly,v->{com.customcosmetics.features.map.MapEditor.dungeonOnly=v;com.customcosmetics.features.map.MapEditor.refresh();});
        toggleRow("Force Dungeon","Assume you are in a dungeon. Singleplayer without a floor uses the shared F7/M7 Boss layout; multiplayer still detects rooms from blocks.",()->com.customcosmetics.features.map.MapEditor.forceDungeon,com.customcosmetics.features.map.MapEditor::forceDungeon);
        toggleRow("Force Kuudra","Edit a local Kuudra map or override detection. Uses a separate Kuudra layout at the original world coordinates.",()->com.customcosmetics.features.map.MapEditor.forceKuudra,com.customcosmetics.features.map.MapEditor::forceKuudra);
        toggleRow("Edit Mode","Right: place. Left: hide/delete. Middle: pick. Changes save by room automatically.",()->com.customcosmetics.features.map.MapEditor.editing,v->{com.customcosmetics.features.map.MapEditor.editing=v;if(v)com.customcosmetics.features.map.MapEditor.enabled=true;com.customcosmetics.features.map.MapEditor.refresh();});
        fullButton("Import FME Config",()->com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,new FmeImportScreen(this)));
        fullButton("Choose Block / Favorites",com.customcosmetics.features.map.MapEditorUi::open);
        fullButton("Replace Materials",()->com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,new MapReplaceScreen(this)));
        toggleRow("Editor HUD","Room, selected block and controls while editing.",()->com.customcosmetics.features.map.MapEditor.showHud,v->{com.customcosmetics.features.map.MapEditor.showHud=v;com.customcosmetics.features.map.MapEditor.save();});
        label("Room: "+com.customcosmetics.features.map.MapEditor.roomLabel(),contentX,rowY+5,MUTED,contentWidth);rowY+=24;
        label("Room library: "+com.customcosmetics.features.map.MapEditor.ROOMS.count()+" saved layouts",contentX,rowY+5,MUTED,contentWidth);rowY+=24;
        fullButton("Reset This Room",()->com.customcosmetics.features.map.MapCommands.run(com.customcosmetics.features.map.MapEditor::clearRoomInteractive));
        fieldRow("Block Material","Block Material",com.customcosmetics.features.map.MapEditor.material,128,"Example: minecraft:gold_block. Air hides a real block visually in Legit mode.",220,v->{if(com.customcosmetics.features.map.MapEditor.state(v)!=null)com.customcosmetics.features.map.MapEditor.material=v;});
        fullButton("Undo",()->com.customcosmetics.features.map.MapEditor.undoInteractive());
        fullButton("Clear Current Edits",()->com.customcosmetics.features.map.MapEditor.clearInteractive());
        toggleRow("Automatically Restore Rooms","Automatically applies saved rooms and aligns them from blocks.",()->com.customcosmetics.features.map.MapEditor.autoRooms,v->{com.customcosmetics.features.map.MapEditor.autoRooms=v;com.customcosmetics.features.map.MapEditor.save();});
        fullButton("Preset Manager",()->com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft,new MapPresetScreen(this)));
        label("Region: /customcosmetics map pos1, pos2, fill",contentX,rowY+5,MUTED,contentWidth);rowY+=24;
    }

    private void connectionStatus(){
        updateConnectionStatus();
        int y=card(108);
        art.add(g->{
            if (y - scroll >= viewBottom || y + 108 - scroll <= viewTop) return;
            text(g,font,connectionRosterTitle,contentX+12,y+12,PINK,contentWidth-24);
            text(g,font,connectionRosterSource,contentX+12,y+28,MUTED,contentWidth-24);
            for(int i=0;i<connectionLines.size();i++)text(g,font,connectionLines.get(i),contentX+12,y+48+i*12,MUTED,contentWidth-24);
        });
    }
    private void updateConnectionStatus() {
        var roster = com.customcosmetics.features.party.PartyFeatures.roster();
        connectionRosterTitle = roster.title(); connectionRosterSource = roster.sourceLabel();
        String status = com.customcosmetics.sync.RealtimeSync.connectionStatus();
        if (!status.equals(connectionSummary)) { connectionSummary = status; connectionLines = status.lines().toList(); }
    }

    private PinkUi.Field proxyText(String title,String value,Consumer<String> setter) {
        return fieldRow(title,title,value,256,"",220,text -> { setter.accept(text.strip()); proxyChanged(); });
    }
    private void proxyChanged() { changed(); com.customcosmetics.sync.PresenceProxyResolver.resetSession(); }
    private void credit() {
        int y = rowY + 4;
        art.add(g -> { g.pose().pushMatrix(); g.pose().translate(contentX,y); g.pose().scale(.8f,.8f);
            text(g,font,"Thanks to Baity / raueyhs.",0,0,DIM,(int)(contentWidth/.8f)); g.pose().popMatrix(); });
        rowY += 22;
    }
    private record NumericSetting(String title,DoubleSupplier getter,double min,double max,double step,double defaultValue,DoubleConsumer setter,String description) {
        NumericSetting(String title,DoubleSupplier getter,double min,double max,double step,double defaultValue,DoubleConsumer setter){this(title,getter,min,max,step,defaultValue,setter,"");}
    }
    private void sliderGrid(List<NumericSetting> settings){
        int columns=contentWidth>=400?2:1,cellWidth=(contentWidth-(columns-1)*10)/columns;
        for(int i=0;i<settings.size();i++){
            sliderCell(settings.get(i),contentX+(i%columns)*(cellWidth+10),rowY,cellWidth);
            if(i%columns==columns-1||i==settings.size()-1)rowY+=60;
        }
    }
    private void sliderCell(NumericSetting setting,int x,int y,int width) {
        indexOption(setting.title(),setting.description(),y);
        art.add(g -> outline(g,x,y,width,50,6,BORDER,CARD));
        PinkUi.Field[] number = new PinkUi.Field[1];
        number[0] = add(new PinkUi.Field(font,x + width - 56,y + 4,48,setting.title() + " value",22));
        number[0].setMaxLength(7); number[0].setValue(String.format(Locale.ROOT,"%.2f",setting.getter().getAsDouble()));
        var slider = add(new PinkUi.Slider(x + 8,y + 5,width - 16,setting.title(),setting.getter().getAsDouble(),setting.min(),setting.max(),setting.step(),value -> {
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
        label(hint,x + 8,y + 38,DIM,width - 60);
        if(!setting.description().isEmpty())slider.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(setting.description())));
        add(new PinkUi.Action(x + width - 48,y + 33,40,16,"Reset",false,() -> { setting.setter().accept(setting.defaultValue()); changed(); rebuildWidgets(); }));
    }

    private void updateSyncNowButton() {
        if (syncNowButton == null) return;
        int remaining = PresenceSync.manualSyncCooldownSeconds();
        syncNowButton.active = !ConfigManager.offlineMode && remaining == 0;
        String label = remaining > 0 ? "Sync Now (" + remaining + "s)" : "Sync Now";
        if (!label.equals(syncNowButton.getMessage().getString())) syncNowButton.setMessage(Component.literal(label));
    }

    private void previewCard() {
        var rankPreview=page==Page.RANK?com.customcosmetics.features.nickname.RankText.component(
            com.customcosmetics.features.nickname.RankText.sequence(Component.literal(new com.customcosmetics.features.nickname.RankSettings(true,ConfigManager.nickTweaksRankPlusColor,ConfigManager.nickTweaksRankType,ConfigManager.nickTweaksRankTagColor).prefix()+" "))):null;
        int y = card(58);
        label("Preview", contentX + 12, y + 12, PINK, contentWidth - 24);
        art.add(g -> {
            String playerName = minecraft.player == null ? "YourName" : minecraft.player.getName().getString();
            Component name = NickRenderUtils.previewNickname(playerName);
            if(rankPreview!=null)name=rankPreview.copy().append(name);
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
        rowY += height + 8;
        return y;
    }
    private void label(String label, int x, int y, int color, int width) {
        if (width <= 0) return;
        String fitted = font.width(label) <= width ? label
            : font.plainSubstrByWidth(label, Math.max(0, width - font.width("…"))) + "…";
        art.add(g -> g.text(font, fitted, x, y, color, false));
    }
    private void toggleRow(String title, String description, BooleanSupplier getter, Consumer<Boolean> setter) {
        indexOption(title,description,rowY);
        var titleLines = font.split(Component.literal(title), Math.max(1, contentWidth - 66));
        var lines = description.isEmpty() ? List.<net.minecraft.util.FormattedCharSequence>of() : font.split(Component.literal(description),Math.max(1, contentWidth - 24));
        int titleHeight = Math.max(18, titleLines.size() * 11);
        int y = card(Math.max(36, 18 + titleHeight + (lines.isEmpty() ? 0 : 4 + lines.size() * 11)));
        for (int i = 0; i < titleLines.size(); i++) { var line = titleLines.get(i); int ty = y + 11 + i * 11; art.add(g -> g.text(font, line, contentX + 12, ty, TEXT, false)); }
        for (int i = 0; i < lines.size(); i++) { var line = lines.get(i); int ty = y + 12 + titleHeight + i * 11; art.add(g -> g.text(font,line,contentX + 12,ty,MUTED,false)); }
        add(new PinkUi.Toggle(contentX + contentWidth - 46, y + 9, title, getter, () -> { setter.accept(!getter.getAsBoolean()); changed(); }));
    }

    private void sliderRow(String title, String description, DoubleSupplier getter, double min, double max, double step, double defaultValue, DoubleConsumer setter) {
        sliderGrid(List.of(new NumericSetting(title,getter,min,max,step,defaultValue,setter,description)));
    }

    private PinkUi.Field fieldRow(String title,String name,String value,int maximum,String hint,int preferredWidth,Consumer<String> setter){
        indexOption(title,name+" "+hint,rowY);
        boolean inline=contentWidth>=400;
        int fieldWidth=inline?Math.min(preferredWidth,contentWidth/2):contentWidth-20;
        int textWidth=inline?contentWidth-fieldWidth-30:contentWidth-20;
        var titles=font.split(Component.literal(title), Math.max(1, textWidth));
        var lines=hint.isEmpty()?List.<net.minecraft.util.FormattedCharSequence>of():font.split(Component.literal(hint),Math.max(1,textWidth));
        int titleHeight = titles.size() * 11;
        int fieldTop = inline ? 9 : 10 + titleHeight + 4;
        int hintTop = inline ? 10 + titleHeight + 4 : fieldTop + 28;
        int height=Math.max(inline ? 40 : fieldTop + 30, hintTop + lines.size() * 11 + 9);
        int y=card(height);
        for(int i=0;i<titles.size();i++){var line=titles.get(i);int lineY=y+10+i*11;art.add(g->g.text(font,line,contentX+10,lineY,TEXT,false));}
        for(int i=0;i<lines.size();i++){var line=lines.get(i);int lineY=y+hintTop+i*11;art.add(g->g.text(font,line,contentX+10,lineY,MUTED,false));}
        return input(inline?contentX+contentWidth-fieldWidth-10:contentX+10,y+(inline?(height-22)/2:fieldTop),fieldWidth,name,value,maximum,setter);
    }
    private PinkUi.Field input(int x, int y, int width, String name, String value, int maximum, Consumer<String> setter) {
        PinkUi.Field field = new PinkUi.Field(font, x, y, width, name,22);
        field.setMaxLength(maximum); field.setValue(value); field.setResponder(text -> { activity(); setter.accept(text); });
        return add(field);
    }
    private PinkUi.Action fullButton(String text, Runnable callback, boolean primary) {
        return fullButton(text,callback,primary,"");
    }
    private PinkUi.Action fullButton(String text,Runnable callback,boolean primary,String description){
        indexOption(text,description,rowY);
        PinkUi.Action button = add(new PinkUi.Action(contentX, rowY, contentWidth, 28, text, primary, callback).leftAligned());
        button.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(description.isEmpty() ? text : description)));
        rowY += 36;
        return button;
    }
    private <T extends AbstractWidget> T add(T widget) { controls.add(new Placement(widget, widget.getY())); return addWidget(widget); }
    private void open(Page page) { this.page = page; searchQuery="";scroll = 0; activity(); setFocused(null); rebuildWidgets(); }
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

    @Override public void tick() { updateSyncNowButton(); if (page == Page.SYNC) updateConnectionStatus(); }

    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        activity();
        if (blockingMode != null) blockingMode.closeMenu();
        if (blockingStyle != null) blockingStyle.closeMenu();
        if (StudioLayout.contains(mx, my, panelX + 1, navTop, panelX + sideWidth, navBottom)) {
            navScroll = Math.clamp((int) Math.round(navScroll - vertical * 36), 0, maxNavScroll());
            positionNavigation(); return true;
        }
        if (mx < contentX || mx > contentX + contentWidth + 10 || my < viewTop || my > viewBottom) return super.mouseScrolled(mx, my, horizontal, vertical);
        scroll = Math.clamp(scroll - vertical * 48, 0, maxScroll()); positionControls();
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
        positionNavigation();
        if (event.button() == ClientInput.MOUSE_LEFT && maxNavScroll() > 0
            && StudioLayout.contains(event.x(), event.y(), panelX + sideWidth - 8, navTop, panelX + sideWidth, navBottom)) {
            var bar = navigationScrollbar();
            int thumb = bar.thumbStart(navScroll);
            scrollGrabOffset = event.y() >= thumb && event.y() < thumb + bar.thumbLength()
                ? (int) event.y() - thumb : bar.thumbLength() / 2;
            draggingNavigation = true; navScroll = bar.offsetAtPointer(event.y(), scrollGrabOffset);
            positionNavigation(); return true;
        }
        if (event.button() == com.customcosmetics.compatibility.ClientInput.MOUSE_LEFT && maxScroll() > 0 && event.x() >= contentX + contentWidth + 2
                && event.x() <= contentX + contentWidth + 12 && event.y() >= viewTop && event.y() <= viewBottom) {
            var bar = contentScrollbar();
            int thumb = bar.thumbStart((int) Math.round(scroll));
            scrollGrabOffset = event.y() >= thumb && event.y() < thumb + bar.thumbLength()
                ? (int) event.y() - thumb : bar.thumbLength() / 2;
            draggingScroll = true;
            scrollToMouse(event.y());
            return true;
        }
        if (event.y() < viewTop || event.y() >= viewBottom) {
            for (Placement placement : controls) placement.widget().visible = false;
        }
        if (event.y() < navTop || event.y() >= navBottom) {
            for (Placement placement : sidebarControls) placement.widget().visible = false;
        }
        try { return super.mouseClicked(event, doubleClick); }
        finally { positionControls(); positionNavigation(); }
    }

    private ScrollBarLayout contentScrollbar() {
        int visible = Math.max(0, viewBottom - viewTop);
        return ScrollBarLayout.of(viewTop, visible, visible, visible + (int) Math.ceil(maxScroll()), (int) Math.ceil(maxScroll()));
    }
    private ScrollBarLayout navigationScrollbar() {
        int visible = Math.max(0, navBottom - navTop);
        return ScrollBarLayout.of(navTop, visible, visible, visible + maxNavScroll(), maxNavScroll());
    }
    private void scrollToMouse(double y) {
        scroll = contentScrollbar().offsetAtPointer(y, scrollGrabOffset);
        positionControls();
    }

    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        activity();
        if (draggingNavigation) {
            navScroll = navigationScrollbar().offsetAtPointer(event.y(), scrollGrabOffset); positionNavigation(); return true;
        }
        if (draggingScroll) { scrollToMouse(event.y()); return true; }
        return super.mouseDragged(event, dx, dy);
    }

    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (draggingScroll || draggingNavigation) { draggingScroll = false; draggingNavigation = false; return true; }
        return super.mouseReleased(event);
    }

    @Override public boolean keyPressed(KeyEvent event) {
        if (blockingStyle != null && blockingStyle.menuKey(event)) { activity(); return true; }
        if (blockingMode != null && blockingMode.menuKey(event)) { activity(); return true; }
        if (event.key() != ClientInput.ESCAPE) activity();
        boolean handled = super.keyPressed(event);
        if (getFocused() instanceof AbstractWidget focused) {
            for (Placement placement : sidebarControls) {
                if (placement.widget() != focused) continue;
                if (focused.getY() < navTop) navScroll -= navTop - focused.getY();
                else if (focused.getBottom() > navBottom) navScroll += focused.getBottom() - navBottom;
                navScroll = Math.clamp(navScroll, 0, maxNavScroll()); positionNavigation(); break;
            }
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
            text(g, font, "CUSTOM", panelX + 15, panelY + 19, PINK, sideWidth - 26);
            text(g, font, "COSMETICS", panelX + 15, panelY + 33, TEXT, sideWidth - 26);
            g.pose().pushMatrix(); g.pose().translate(contentX, panelY + 18); g.pose().scale(1.2f, 1.2f);
            text(g, font, pageTitle(), 0, 0, TEXT, (int) ((contentWidth - 24) / 1.2f));
            g.pose().popMatrix();
            text(g, font, pageDescription(), contentX, panelY + 35, MUTED, contentWidth);
            g.fill(contentX, panelY + 83, contentX + contentWidth, panelY + 84, BORDER);
            positionNavigation();
            g.enableScissor(panelX + 1, navTop, panelX + sideWidth - 6, navBottom);
            g.pose().pushMatrix(); g.pose().translate(0, -navScroll);
            for (var draw : sidebarArt) draw.accept(g);
            g.pose().popMatrix();
            for (var placement : sidebarControls) if (placement.widget().visible)
                placement.widget().extractRenderState(g, mx, my >= navTop && my < navBottom ? my : Integer.MIN_VALUE, dt);
            g.disableScissor();
            if (maxNavScroll() > 0) {
                var bar = navigationScrollbar();
                rounded(g, panelX + sideWidth - 5, navTop, 2, navBottom - navTop, 1, BORDER);
                rounded(g, panelX + sideWidth - 5, bar.thumbStart(navScroll), 2, bar.thumbLength(), 1, PINK);
            }
            g.fill(panelX + 12, panelY + panelHeight - 50, panelX + sideWidth - 12, panelY + panelHeight - 49, BORDER);
            for (Consumer<GuiGraphicsExtractor> draw : pinnedArt) draw.accept(g);
            for (AbstractWidget widget : navigation) widget.extractRenderState(g, mx, my, dt);

            positionControls();
            g.enableScissor(contentX - 1, viewTop, contentX + contentWidth + 1, viewBottom);
            g.pose().pushMatrix();
            g.pose().translate(0, -(float) Math.round(scroll));
            for (Consumer<GuiGraphicsExtractor> draw : art) draw.accept(g);
            g.pose().popMatrix();
            for (Placement p : controls) if (p.widget().visible)
                p.widget().extractRenderState(g, mx, my >= viewTop && my < viewBottom ? my : Integer.MIN_VALUE, dt);
            g.disableScissor();
            if (maxScroll() > 0) {
                var bar = contentScrollbar();
                rounded(g, contentX + contentWidth + 6, viewTop, 3, viewBottom - viewTop, 1, BORDER);
                rounded(g, contentX + contentWidth + 6, bar.thumbStart((int) Math.round(scroll)), 3, bar.thumbLength(), 1, PINK);
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
    @Override public void removed() { SESSION.remember(page.name(),scroll); ConfigManager.saveConfig(); com.customcosmetics.sync.RealtimeSync.onSettingsClosed(); PresenceSync.onClickGuiClosed(); }
    @Override public void onClose() { com.customcosmetics.compatibility.ClientUiAccess.setScreen(minecraft, parent); }
}
