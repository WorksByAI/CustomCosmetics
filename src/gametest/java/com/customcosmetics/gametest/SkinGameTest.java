package com.customcosmetics.gametest;
import com.customcosmetics.features.skins.*;
import com.customcosmetics.config.ConfigManager;
import com.customcosmetics.gui.*;
import com.customcosmetics.compatibility.ClientUiAccess;
import net.fabricmc.fabric.api.client.gametest.v1.*;
import net.fabricmc.fabric.api.client.gametest.v1.context.*;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import net.minecraft.world.entity.player.PlayerModelType;
import java.util.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.nio.file.*;
import java.util.concurrent.atomic.*;
public final class SkinGameTest implements FabricClientGameTest {
    public static volatile Set<UUID> DIRECT=Set.of();private static final UUID PEER=UUID.fromString("62345678-1234-4234-8234-123456789abc");
    @Override public void runTest(ClientGameTestContext context){
        context.runOnClient(client->{ConfigManager.offlineMode=true;ConfigManager.syncEnabled=false;ConfigManager.swordBlockingSyncEnabled=false;ConfigManager.partyEspEnabled=false;ConfigManager.partyChatEnabled=false;ConfigManager.sharingChamsEnabled=false;ConfigManager.skinP2pSharing=false;ConfigManager.skinP2pReceiving=true;ConfigManager.localSkinSlim=false;try{LocalSkins.clear();}catch(java.io.IOException error){throw new AssertionError(error);}});
        try(var world=context.worldBuilder().create()){
            world.getServer().runCommand("fill -8 63 -8 8 64 8 minecraft:stone");world.getServer().runCommand("fill -8 65 -8 8 74 8 minecraft:air");world.getServer().runCommand("time set noon");world.getServer().runCommand("tp @a 0 65 5 180 0");context.waitTicks(20);
            var imported=new AtomicReference<java.util.concurrent.CompletableFuture<Void>>();var original=new AtomicReference<net.minecraft.world.entity.player.PlayerSkin>();
            context.runOnClient(client->{original.set(client.player.getSkin());var path=Path.of("skin-fixture.png");Files.write(path,png(0xffff33bb));imported.set(LocalSkins.importFile(path));});context.waitFor(client->imported.get().isDone());imported.get().join();context.waitTicks(3);
            context.runOnClient(client->{check(client.player.getSkin().body().texturePath().getNamespace().equals("customcosmetics"),"Local skin did not replace the player skin");check(client.player.getSkin().cape()==original.get().cape(),"Local skin replaced the cape");LocalSkins.slim(true);check(client.player.getSkin().model()==PlayerModelType.SLIM,"Slim skin model selection did not apply");ConfigManager.offlineMode=false;var player=new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(PEER,"SkinPeer"));player.setId(-29101);player.setPos(0,65,0);client.level.addEntity(player);DIRECT=Set.of(PEER);});context.waitTicks(2);
            context.runOnClient(client->LocalSkins.exchange().received(PEER,new SkinTransfer.Document(png(0xff11dddd),true)));context.waitFor(client->((RemotePlayer)client.level.getEntity(-29101)).getSkin().body().texturePath().getNamespace().equals("customcosmetics"));context.waitTicks(4);capture(context,"skins-direct-peer-texture");
            if (net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("iqaddons")) {
                var spawner = new net.minecraft.core.BlockPos(0, 68, 0);
                world.getServer().runCommand("setblock 0 68 0 minecraft:spawner{SpawnData:{entity:{id:\"minecraft:zombie\"}}}");
                context.waitFor(client -> client.level.getBlockEntity(spawner) instanceof net.minecraft.world.level.block.entity.SpawnerBlockEntity);
                context.runOnClient(client -> {
                    var block = (net.minecraft.world.level.block.entity.SpawnerBlockEntity) client.level.getBlockEntity(spawner);
                    check(block.getSpawner().getOrCreateDisplayEntity(client.level, spawner) != null,
                        "IQAddons prevented the spawner preview from loading");
                    System.out.println("IQADDONS_SPAWNER_PREVIEW_LOAD_OK");
                });
            }
            checkGlobalAndBlocks(context);
            context.runOnClient(SkinGameTest::checkPurseSidebar);
            context.runOnClient(SkinGameTest::checkInGameModeration);
            context.runOnClient(client->{var skin=((RemotePlayer)client.level.getEntity(-29101)).getSkin();check(skin.model()==PlayerModelType.SLIM,"Direct skin lost model metadata");DIRECT=Set.of();});context.waitTicks(2);
            context.runOnClient(client->{check(!((RemotePlayer)client.level.getEntity(-29101)).getSkin().body().texturePath().getNamespace().equals("customcosmetics"),"Remote skin stayed visible after P2P ended");check(client.player.getSkin().body().texturePath().getNamespace().equals("customcosmetics"),"Losing P2P removed the private local skin");ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SKINS));});context.waitTicks(4);capture(context,"skins-local-preview-settings");
            context.runOnClient(client->{ClientUiAccess.setScreen(client,new SkinImportScreen(null));});context.waitTicks(3);capture(context,"skins-import-screen");
            context.runOnClient(client->{LocalSkins.clear();ConfigManager.offlineMode=true;ClientUiAccess.setScreen(client,null);check(!client.player.getSkin().body().texturePath().getNamespace().equals("customcosmetics"),"Removing local skin did not restore original");});
            System.out.println("SKINS_LOCAL_IMPORT_SLIM_CAPE_PRESERVATION_DIRECT_RENDER_DISCONNECT_FALLBACK_GUI_OK");
        }catch(Exception error){throw new AssertionError(error);}finally{DIRECT=Set.of();}
        try { checkModerationLogin(context); } catch (Exception error) { throw new AssertionError(error); }
    }

    private static void checkInGameModeration(Minecraft client) throws ReflectiveOperationException {
        var events = net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.invoker();
        check(!events.allowSendCommandMessage("csbban"), "Reserved command was sent to the game server");
        var chat = ClientUiAccess.chat(client);
        var messages = chat.getClass().getDeclaredField("allMessages");
        messages.setAccessible(true);
        var last = (net.minecraft.client.multiplayer.chat.GuiMessage) ((List<?>) messages.get(chat)).getFirst();
        check(last.content().getString().equals("Unknown command. Type \"help\" for help. ('csbban')"),
            "The unknown command message revealed the mod: " + last.content().getString());

        var body = Component.literal("Fixture disconnect reason");
        var custom = new com.customcosmetics.sync.ModModeration.BanScreen(Component.literal("Fixture title"),
            body, Component.literal("Fixture button"));
        var pending = com.customcosmetics.sync.ModModeration.class.getDeclaredField("disconnectScreen");
        pending.setAccessible(true);
        pending.set(null, custom);
        var change = net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl.class.getDeclaredMethod(
            "createDisconnectScreen", net.minecraft.network.DisconnectionDetails.class);
        change.setAccessible(true);
        var screen = (net.minecraft.client.gui.screens.Screen) change.invoke(client.getConnection(),
            new net.minecraft.network.DisconnectionDetails(body));
        check(screen.getTitle().getString().equals("Fixture title") && pending.get(null) == null,
            "The in-game disconnect failed to render the configured screen");
        var kickMessage = com.customcosmetics.sync.ModModeration.class.getDeclaredMethod("kickMessage", String.class);
        kickMessage.setAccessible(true);
        var kick = (Component) kickMessage.invoke(null, "&cqwqqw &#102030HEX");
        var kickScreen = change.invoke(client.getConnection(), new net.minecraft.network.DisconnectionDetails(kick));
        var details = kickScreen.getClass().getDeclaredField("details");
        details.setAccessible(true);
        var reason = ((net.minecraft.network.DisconnectionDetails) details.get(kickScreen)).reason();
        check(reason.getString().equals("qwqqw HEX"), "Kick reason added a prefix or displayed its color codes");
        check(reason.getSiblings().getFirst().getStyle().getColor().getValue() == 0xFF5555, "Kick reason lost &c");
        check(reason.getSiblings().getLast().getStyle().getColor().getValue() == 0x102030, "Kick reason lost its hex color");
        check(((Component) kickMessage.invoke(null, "  ")).getString().equals("Disconnected"), "Blank kick reason lost its default");
        System.out.println("MODERATION_KICK_SCREEN_PRESERVES_COLORS_WITHOUT_PREFIX_OK");
        System.out.println("MODERATION_DISCONNECT_DESIGN_AND_UNKNOWN_COMMAND_OK");
    }

    private static void checkModerationLogin(ClientGameTestContext context) throws Exception {
        try (var gameServer = new ServerSocket()) {
            gameServer.bind(new InetSocketAddress("127.0.0.1", 0));
            gameServer.setSoTimeout(5000);
            checkModerationLogin(context, gameServer);
        }
    }

    private static void checkModerationLogin(ClientGameTestContext context, ServerSocket gameServer) throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var checked = new AtomicBoolean();
        var restrictedResponse = new CountDownLatch(1);
        var slowResponse = new CountDownLatch(1);
        var slowRequest = new CountDownLatch(1);
        String host = "127.0.0.1:" + gameServer.getLocalPort();
        server.createContext("/mod/moderation/check", exchange -> {
            try (exchange) {
                if (!("Bearer " + "a".repeat(64)).equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
                    exchange.sendResponseHeaders(401, -1);
                    return;
                }
                var request = com.google.gson.JsonParser.parseString(new String(exchange.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                String expected = java.util.HexFormat.of().formatHex(digest(host));
                if (!expected.equals(request.get("scope").getAsString())) {
                    exchange.sendResponseHeaders(400, -1);
                    return;
                }
                boolean restricted = !checked.get();
                if (!restricted) slowRequest.countDown();
                var response = restricted ? restrictedResponse : slowResponse;
                try {
                    if (!response.await(8, TimeUnit.SECONDS)) throw new java.io.IOException("Fixture did not release its reply");
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new java.io.IOException(error);
                }
                byte[] reply = (restricted ? """
                    {"administrator":false,"ban":{"id":"GAME1234","reason":"&cFixture restriction",
                    "permanent":false,"pending":false,"seconds":60,"remainingSeconds":59,"helpUrl":"https://example.org",
                    "screen":{"title":"Fixture title","body":"&cRemaining {time} | &f{reason} | {link} | #{id}","button":"Fixture return"}}}
                    """ : "{\"administrator\":false,\"ban\":null}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, reply.length);
                exchange.getResponseBody().write(reply);
                checked.set(true);
            }
        });
        server.start();
        try {
            context.runOnClient(client -> {
                ConfigManager.offlineMode = false;
                ConfigManager.syncServerIp = "127.0.0.1";
                ConfigManager.syncServerHttps = false;
                ConfigManager.syncServerPort = server.getAddress().getPort();
                var account = client.getUser();
                var session = new com.customcosmetics.sync.PresenceAuthClient.Session("a".repeat(64),
                    account.getProfileId(), account.getName(), System.currentTimeMillis() + 120000,
                    "http://127.0.0.1:" + ConfigManager.syncServerPort);
                var shared = com.customcosmetics.sync.PresenceSync.class.getDeclaredField("SESSIONS");
                shared.setAccessible(true);
                var cache = shared.get(null);
                var cached = cache.getClass().getDeclaredField("session");
                cached.setAccessible(true);
                cached.set(cache, session);
                var events = net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents.ALLOW_COMMAND.invoker();
                check(!events.allowSendCommandMessage("csbban Fixture cheating 30d"), "Hidden moderation command leaked to the server");
                check(events.allowSendCommandMessage("help"), "An unrelated command was intercepted");
                connectToModerationFixture(client, host);
                var connecting = ClientUiAccess.screen(client);
                check(connecting instanceof net.minecraft.client.gui.screens.ConnectScreen, "The original connecting screen was replaced");
                var field = connecting.getClass().getDeclaredField("status");
                field.setAccessible(true);
                var status = (Component) field.get(connecting);
                check(status.getString().equals(Component.translatable("connect.connecting").getString()),
                    "The login status appeared before moderation allowed the connection");
            });
            try (var connection = gameServer.accept()) {
                check(!checked.get(), "TCP did not start while the moderation request was pending");
                connection.setSoTimeout(50);
                try {
                    connection.getInputStream().read();
                    throw new AssertionError("Login or disconnect started before the restriction reply");
                } catch (SocketTimeoutException expected) {
                    restrictedResponse.countDown();
                }
                connection.setSoTimeout(3000);
                check(connection.getInputStream().read() == -1, "A restricted account sent a login packet");
            }
            context.waitFor(client -> ClientUiAccess.screen(client) instanceof net.minecraft.client.gui.screens.DisconnectedScreen);
            check(checked.get(), "Login restriction was not fetched from the authenticated moderation service");
            context.runOnClient(client -> {
                var screen = ClientUiAccess.screen(client);
                check(screen.getTitle().getString().equals("Fixture title"), "The remote screen title was ignored");
                var details = screen.getClass().getDeclaredField("details");
                details.setAccessible(true);
                var reason = ((net.minecraft.network.DisconnectionDetails) details.get(screen)).reason();
                check(reason.getString().equals("Remaining 59s | Fixture restriction | https://example.org | #GAME1234"),
                    "The remote body or its substitutions were ignored: " + reason.getString());
                var button = screen.getClass().getDeclaredField("buttonText");
                button.setAccessible(true);
                check(((Component) button.get(screen)).getString().equals("Fixture return"), "The remote button text was ignored");
            });
            context.runOnClient(client -> connectToModerationFixture(client, host));
            try (var connection = gameServer.accept()) {
                check(slowRequest.await(3, TimeUnit.SECONDS), "The second moderation check did not start");
                connection.setSoTimeout(2500);
                check(connection.getInputStream().read() > 0, "A slow moderation backend blocked the login handshake");
                check(slowResponse.getCount() == 1, "The handshake waited for the moderation reply");
            }
            System.out.println("MODERATION_LOGIN_GUARD_AND_HIDDEN_COMMANDS_OK");
            System.out.println("MODERATION_PRECHECK_PARALLEL_TCP_AND_BOUNDED_WAIT_OK");
        } finally {
            restrictedResponse.countDown();
            slowResponse.countDown();
            server.stop(0);
            context.runOnClient(client -> {
                ConfigManager.offlineMode = true;
                ClientUiAccess.setScreen(client, new net.minecraft.client.gui.screens.TitleScreen());
            });
        }
    }

    private static void connectToModerationFixture(Minecraft client, String host) {
        var parent = new net.minecraft.client.gui.screens.TitleScreen();
        var address = net.minecraft.client.multiplayer.resolver.ServerAddress.parseString(host);
        var destination = new net.minecraft.client.multiplayer.ServerData("Moderation fixture", host,
            net.minecraft.client.multiplayer.ServerData.Type.OTHER);
        net.minecraft.client.gui.screens.ConnectScreen.startConnecting(parent, client, address, destination, false, null);
    }

    private static byte[] digest(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException error) { throw new AssertionError(error); }
    }

    private static void checkGlobalAndBlocks(ClientGameTestContext context)throws Exception{
        var original=new AtomicReference<net.minecraft.world.entity.player.PlayerSkin>();var own=new AtomicReference<net.minecraft.resources.Identifier>();var wire=new AtomicReference<String>();
        UUID second=UUID.fromString("32345678-1234-4234-8234-123456789abc");
        context.runOnClient(client->{
            var peer=(RemotePlayer)client.level.getEntity(-29101);
            var another=new RemotePlayer(client.level,new com.mojang.authlib.GameProfile(second,"OtherPeer"));another.setId(-29102);client.level.addEntity(another);
            own.set(client.player.getSkin().body().texturePath());ConfigManager.skinP2pSharing=true;wire.set(LocalSkins.exchange().local().hash());
            var originalSelf = client.player.getSkin();
            var originalPeer = peer.getSkin().body().texturePath();
            ConfigManager.nickHiderSelf = false;
            var screen = new CosmeticsScreen(null, CosmeticsScreen.Page.NICK_HIDER);
            ClientUiAccess.setScreen(client, screen);
            var toggle = screen.children().stream().filter(PinkUi.Toggle.class::isInstance).map(PinkUi.Toggle.class::cast)
                .filter(widget -> widget.getMessage().getString().equals("Hide My Skin")).findFirst().orElseThrow();
            toggle.onClick(null, false);
            var hidden = client.player.getSkin();
            check(ConfigManager.nickHiderSelfSkin && !ConfigManager.nickHiderSelf, "Hide My Skin also toggled name masking");
            check(hidden.body().texturePath().getNamespace().equals("minecraft"), "The player kept their imported skin");
            check(hidden.cape() == originalSelf.cape(), "Hiding the skin also changed the cape");
            LocalSkins.selectSelfMask("steve");
            check(client.player.getSkin().model() == PlayerModelType.WIDE && client.player.getSkin().body().texturePath().getPath().endsWith("wide/steve.png"), "Steve selection did not apply");
            check(peer.getSkin().body().texturePath().equals(originalPeer), "Hiding my skin changed another player");
            check(LocalSkins.exchange().local().hash().equals(wire.get()), "The local mask changed the P2P skin document");
            toggle.onClick(null, false);
            check(!ConfigManager.nickHiderSelfSkin && client.player.getSkin().body().texturePath().equals(own.get()), "Disabling the mask did not restore the local skin");
            LocalSkins.selectSelfMask("auto");
            ClientUiAccess.setScreen(client, null);
            com.customcosmetics.sync.PlayerBlocks.set(com.customcosmetics.sync.PlayerBlocks.Kind.SKIN,List.of(new com.customcosmetics.config.PlayerBlocklist.Entry(null,"skinpeer")));
            check(!LocalSkins.exchange().receiving(PEER,"SkinPeer"),"Blocked peer can request skin downloads");
            check(!peer.getSkin().body().texturePath().getNamespace().equals("customcosmetics"),"Account-only block did not restore Mojang skin immediately");
            LocalSkins.otherMode("steve");check(another.getSkin().body().texturePath().getPath().endsWith("wide/steve.png"),"Global Steve did not affect a non-P2P player");
            check(!peer.getSkin().body().texturePath().getNamespace().equals("customcosmetics"),"Global mode overrode the Mojang-only block");
            com.customcosmetics.sync.PlayerBlocks.set(com.customcosmetics.sync.PlayerBlocks.Kind.SKIN,List.of());
            check(peer.getSkin().body().texturePath().equals(another.getSkin().body().texturePath()),"Global mode did not apply to all other players");
            LocalSkins.otherMode("alex");ConfigManager.offlineMode=true;
            check(peer.getSkin().body().texturePath().getPath().endsWith("slim/alex.png")&&peer.getSkin().model()==PlayerModelType.SLIM,"Global Alex does not work locally/offline");
            check(client.player.getSkin().body().texturePath().equals(own.get()),"Global mode changed the local player's own skin");
        });
        var maskFile = Path.of("self-mask-fixture.png");
        Files.write(maskFile, png(0xff8844aa));
        context.runOnClient(client -> {
            var screen = new SkinImportScreen(null, SkinImportScreen.Target.SELF_MASK);
            ClientUiAccess.setScreen(client, screen);
            screen.onFilesDrop(List.of(maskFile));
        });
        context.waitFor(client -> "custom".equals(ConfigManager.nickHiderSelfSkinMode) && ClientUiAccess.screen(client) == null);
        context.runOnClient(client -> {
            check(!ConfigManager.nickHiderSelfSkin && "alex".equals(ConfigManager.otherPlayerSkinMode), "Importing the mask changed another skin setting");
            ConfigManager.nickHiderSelfSkin = true;
            ConfigManager.nickHiderSelfSkinSlim = true;
            var custom = client.player.getSkin();
            check(custom.body().texturePath().getNamespace().equals("customcosmetics") && !own.get().equals(custom.body().texturePath()), "Custom mask did not replace the local texture");
            check(custom.model() == PlayerModelType.SLIM, "Custom mask did not use the chosen arm model");
            checkRenderedTab(client, custom.body().texturePath());
            check(LocalSkins.exchange().local().hash().equals(wire.get()), "The imported mask replaced the outgoing skin");
            ConfigManager.nickHiderSelfSkin = false;
            check(client.player.getSkin().body().texturePath().equals(own.get()), "Disabling the custom mask did not restore the local skin");
        });
        var imported=new AtomicReference<CompletableFuture<Void>>();
        context.runOnClient(client->{var file=Path.of("other-skin-fixture.png");Files.write(file,png(0xff5588ff));imported.set(LocalSkins.importOther(file));});
        context.waitFor(client->imported.get().isDone());imported.get().join();context.waitTicks(3);
        context.runOnClient(client->{
            var a=((RemotePlayer)client.level.getEntity(-29101)).getSkin();var b=((RemotePlayer)client.level.getEntity(-29102)).getSkin();
            check(a.body().texturePath().getNamespace().equals("customcosmetics")&&a.body().texturePath().equals(b.body().texturePath()),"Custom global skin differs between players");
            check(client.player.getSkin().body().texturePath().equals(own.get()),"Custom global skin leaked onto self");
            check(LocalSkins.exchange().local().hash().equals(wire.get()),"Local-only global skin leaked into outbound P2P document");
            check(!LocalSkins.exchange().receiving(second,"OtherPeer"),"Global override kept downloading invisible peer skins");
            ConfigManager.otherPlayerSkinSlim=true;check(LocalSkins.skin(second,b).model()==PlayerModelType.SLIM,"Global slim model preference was ignored");
            int before=ConfigManager.disabledModules;com.customcosmetics.config.FeatureModules.set(com.customcosmetics.config.FeatureModules.Feature.SKINS,false);
            check(!((RemotePlayer)client.level.getEntity(-29102)).getSkin().body().texturePath().getNamespace().equals("customcosmetics"),"Disabled skin module kept overriding players");ConfigManager.disabledModules=before;
            ClientUiAccess.setScreen(client,new CosmeticsScreen(null,CosmeticsScreen.Page.SKINS));
        });context.waitTicks(3);capture(context,"skins-global-local-only-settings");
        context.runOnClient(client->{ConfigManager.offlineMode=false;ConfigManager.skinP2pSharing=false;LocalSkins.otherMode("original");LocalSkins.exchange().received(PEER,new SkinTransfer.Document(png(0xff11dddd),true));});
        context.waitFor(client->((RemotePlayer)client.level.getEntity(-29101)).getSkin().body().texturePath().getNamespace().equals("customcosmetics"));
        System.out.println("SKIN_BLOCKING_GLOBAL_STEVE_ALEX_CUSTOM_SELF_EXCLUSION_NO_P2P_LEAK_AND_MODULE_GATE_OK");
    }
    private static Object hud(Minecraft client) throws ReflectiveOperationException {
        try {
            return client.gui.getClass().getField("hud").get(client.gui);
        } catch (NoSuchFieldException legacy) {
            return client.gui;
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkRenderedTab(Minecraft client, net.minecraft.resources.Identifier mask) throws ReflectiveOperationException {
        var connection = client.getConnection();
        var mode = connection.getClass().getDeclaredField("onlineMode");
        mode.setAccessible(true);
        var entries = connection.getClass().getDeclaredField("listedPlayers");
        entries.setAccessible(true);
        var listed = (Set<PlayerInfo>) entries.get(connection);
        var rosterEntry = new PlayerInfo(new com.mojang.authlib.GameProfile(PEER, "SyntheticTabRow"), false);
        rosterEntry.setTabListDisplayName(Component.literal("[314] [MVP+] " + client.getUser().getName() + " (Mage XX)"));
        var self = connection.getPlayerInfo(client.getUser().getProfileId());
        boolean online = mode.getBoolean(connection);
        boolean listedSelf = self != null && listed.remove(self);
        listed.add(rosterEntry);
        mode.setBoolean(connection, true);
        try {
            var maskView = client.getTextureManager().getTexture(mask).getTextureView();
            var hud = hud(client);
            var tab = (PlayerTabOverlay) hud.getClass().getMethod("getTabList").invoke(hud);
            var state = new GuiRenderState();
            var graphics = new GuiGraphicsExtractor(client, state, 0, 0);
            tab.extractRenderState(graphics, 440, client.level.getScoreboard(), null);
            var drawn = new AtomicInteger();
            state.forEachElement(element -> {
                if (element.textureSetup().texure0() == maskView) drawn.incrementAndGet();
            }, GuiRenderState.TraverseRange.ALL);
            check(drawn.get() == 2, "Tab did not draw the face and hat from the self mask for the synthetic roster entry");

            ConfigManager.nickHiderSelfSkin = false;
            var unmasked = new GuiRenderState();
            tab.extractRenderState(new GuiGraphicsExtractor(client, unmasked, 0, 0), 440, client.level.getScoreboard(), null);
            unmasked.forEachElement(element -> check(element.textureSetup().texure0() != maskView,
                "Tab continued to use the mask when the switch was turned off"), GuiRenderState.TraverseRange.ALL);
            ConfigManager.nickHiderSelfSkin = true;
            System.out.println("TAB_ROSTER_USES_MASK_TEXTURE_AND_RESTORES_ORIGINAL_OK");
        } finally {
            listed.remove(rosterEntry);
            if (listedSelf) listed.add(self);
            mode.setBoolean(connection, online);
        }
    }

    private static void checkPurseSidebar(Minecraft client) throws ReflectiveOperationException {
        var board = client.level.getScoreboard();
        var objective = board.addObjective("csb_test", ObjectiveCriteria.DUMMY, Component.literal("SKYBLOCK"),
            ObjectiveCriteria.RenderType.INTEGER, false, null);
        var previous = board.getDisplayObjective(DisplaySlot.SIDEBAR);
        board.setDisplayObjective(DisplaySlot.SIDEBAR, objective);
        try {
            var row = board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("purse"), objective);
            row.display(Component.literal("Purse: ").append(Component.literal("1,234.50")
                .withStyle(net.minecraft.ChatFormatting.GOLD)).append(" (+5)"));
            var styled = com.customcosmetics.features.hud.PurseDisplay.apply(row.display().copy(), "+1");
            check(styled.getSiblings().stream().anyMatch(part -> part.getString().equals("1,235.50")
                && part.getStyle().getColor().getValue() == 0xFFAA00), "Purse dropped the server's coin color");
            ConfigManager.purseDisplayEnabled = true;
            ConfigManager.purseDisplayOffset = "+100,000.25";
            check(renderedSidebar(client, objective).contains("Purse: 101,234.75 (+5)"), "Purse offset did not reach the sidebar");
            row.display(Component.literal("Purse: 1,275.50 (-2)"));
            check(renderedSidebar(client, objective).contains("Purse: 101,275.75 (-2)"), "Purse did not follow the live scoreboard update");
            ConfigManager.purseDisplayOffset = "-2,000.50";
            check(renderedSidebar(client, objective).contains("Purse: -725.00 (-2)"), "A negative offset did not apply to the current balance");
            check(row.display().getString().equals("Purse: 1,275.50 (-2)"), "Modified purse leaked into scoreboard data");
            ConfigManager.purseDisplayEnabled = false;
            check(renderedSidebar(client, objective).contains(row.display().getString()), "Disabling the override did not restore the actual balance");
            var team = board.addPlayerTeam("csb_purse_fixture");
            try {
                // Captured from Hypixel on 26.3. The score owner splits the real number.
                String owner = "§s";
                team.setPlayerPrefix(Component.literal("Purse: §612,451,"));
                team.setPlayerSuffix(Component.literal("§6456 §e(+5)"));
                board.addPlayerToTeam(owner, team);
                board.getOrCreatePlayerScore(ScoreHolder.forNameOnly(owner), objective);
                ConfigManager.purseDisplayEnabled = true;
                ConfigManager.purseDisplayOffset = "+5000000000";
                check(renderedSidebar(client, objective).contains("Purse: 5,012,451,456 (+5)"),
                    "The Hypixel team marker prevented the live Purse offset");
                var original = net.minecraft.world.scores.PlayerTeam.formatNameForTeam(team, Component.literal(owner));
                var adjusted = com.customcosmetics.features.hud.PurseDisplay.apply(original, ConfigManager.purseDisplayOffset);
                check(adjusted.getSiblings().stream().anyMatch(part -> part.getString().equals("5,012,451,456")
                    && part.getStyle().getColor().getValue() == 0xFFAA00), "The legacy gold coin color was lost");
                check(adjusted.getSiblings().stream().anyMatch(part -> part.getString().equals("(+5)")
                    && part.getStyle().getColor().getValue() == 0xFFFF55), "The legacy coin gain color was lost");
                team.setPlayerSuffix(Component.literal("§6457 §e(+1)"));
                check(renderedSidebar(client, objective).contains("Purse: 5,012,451,457 (+1)"),
                    "The displayed Purse did not follow a live team update");
                ConfigManager.purseDisplayEnabled = false;
                check(renderedSidebar(client, objective).contains("Purse: 12,451,457 (+1)"),
                    "The Hypixel Purse balance was modified in the scoreboard data");
            } finally {
                board.removePlayerTeam(team);
            }
            System.out.println("PURSE_SIDEBAR_FOLLOWS_LIVE_BALANCE_AND_PRESERVES_SERVER_DATA_OK");
        } finally {
            ConfigManager.purseDisplayEnabled = false;
            ConfigManager.purseDisplayOffset = "0";
            board.setDisplayObjective(DisplaySlot.SIDEBAR, previous);
            board.removeObjective(objective);
        }
    }

    private static List<String> renderedSidebar(Minecraft client, Objective objective) throws ReflectiveOperationException {
        var hud = hud(client);
        var display = hud.getClass().getDeclaredMethod("displayScoreboardSidebar", GuiGraphicsExtractor.class, Objective.class);
        display.setAccessible(true);
        var state = new GuiRenderState();
        display.invoke(hud, new GuiGraphicsExtractor(client, state, 0, 0), objective);
        var lines = new ArrayList<String>();
        state.forEachText(row -> {
            try {
                var field = row.getClass().getDeclaredField("text");
                field.setAccessible(true);
                var text = (FormattedCharSequence) field.get(row);
                var line = new StringBuilder();
                text.accept((index, style, codePoint) -> { line.appendCodePoint(codePoint); return true; });
                lines.add(line.toString());
            } catch (ReflectiveOperationException error) {
                throw new AssertionError(error);
            }
        });
        return lines;
    }


    private static byte[] png(int color){try{var image=new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB);for(int y=0;y<64;y++)for(int x=0;x<64;x++)image.setRGB(x,y,color);var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"PNG",bytes);return SkinImages.normalize(bytes.toByteArray());}catch(Exception error){throw new AssertionError(error);}}
    private static void check(boolean valid,String error){if(!valid)throw new AssertionError(error);}
    private static void capture(ClientGameTestContext context,String name){var done=new AtomicBoolean();context.runOnClient(client->{com.mojang.blaze3d.pipeline.RenderTarget target=null;for(Object owner:new Object[]{client.gameRenderer,client}){for(var method:owner.getClass().getMethods())if(method.getParameterCount()==0&&method.getReturnType()==com.mojang.blaze3d.pipeline.RenderTarget.class){try{target=(com.mojang.blaze3d.pipeline.RenderTarget)method.invoke(owner);break;}catch(Exception e){throw new AssertionError(e);}}if(target!=null)break;}net.minecraft.client.Screenshot.grab(client.gameDirectory,name+".png",target,1,message->done.set(true));});context.waitFor(client->done.get());}
}
