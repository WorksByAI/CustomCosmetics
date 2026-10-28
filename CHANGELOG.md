# Changelog

## 2.0.0

- Redesign settings with themes, animations, search, Chinese and font options.
- Add startup updates, skin overrides and missing-accessory lists.
- Separate party/location settings and prefer P2P appearance sync with relay fallback.
- Improve previews, stability and UI/map performance; substantial gains are not guaranteed.

## 1.8.7

- Fix Minecraft 26.3 refresh-rate compatibility.
- Match 26.3 swing timing and preserve native first-person player effects with creature models.
- Keep automatic room restoration out of edit undo history.

## 1.8.6

- Add all vanilla creature models, including bosses, with synchronized choices.
- Fit large creature previews and make all models searchable.
- Fix sheep head jitter when looking around.
- Remove the Player Size footer credit.

## 1.8.6-dev.2

- Fix sheep head jitter by preserving player rotation interpolation in creature proxies.

## 1.8.6-dev.1

- Add all vanilla creature models, including bosses, with matching server sync support.
- Fit large creature previews and include new models in settings search.

## 1.8.5

- Improve performance where possible; substantial gains are not guaranteed for every user.
- This optimization effort consumed approximately US$1,100 worth of Codex credits.
- If performance does not meet your expectations, please open an issue or stop using this mod.

## 1.8.5-dev.1

- Reduce rendering allocations, redundant scans and disabled-module work.
- Move synchronization decoding and configuration writes off the game thread.
- Fix settings search navigation and blocking style keywords.

## 1.8.4

- Add support for changing rank colors.

## 1.8.3

- Add searchable head skins with online metadata, saved textures and item / helmet rendering.
- Cache previews, head profiles and model checks; skip untouched item tracking and load only visible icons.
- Rename the Gameplay navigation group to SkyBlock.

## 1.8.2

- Add synced creature models and custom worn armor models and colors.
- Accept shorthand model IDs and save item edits with Done; allow server-command shortcut overrides.
- Use Hypixel island scopes, live accessory metadata and Skyblocker-style waypoint controls.
- Bound waypoint rendering, skip duplicate imports, delay Clear All confirmation and fix text crashes.

## 1.8.1

- Add a SkyBlock accessory tooltip with collected, upgrade, upgradable, downgrade and missing states after a complete Accessory Bag scan.
- Import Skyblocker's `waypoints.json` directly while preserving island groups and existing waypoints.

## 1.8.0

- Add waypoint groups, ordered routes and Skyblocker V1 import/export.
- Add command aliases, argument replacements, keyboard/mouse shortcuts and `/ccos`.
- Redesign settings with grouped navigation, an overview and responsive layouts.
- Reduce idle render allocations, repeated parsing and sharing work; budget large map refreshes.

## 1.6.0

- Reduce text, player-model and map-processing overhead.
- Add independent module switches and display-paced nickname animation.

## 1.5.4.6

- Add animated rainbow colors for Damage Splash.

## 1.5.4.5

- Default Snaplook to third-person back view.
- Hide transient HTTP 429 notifications and report persistent sync failures.

## 1.5.4.4

- Add hold-to-use Snaplook with configurable perspective and keybind.

## 1.5.4.3

- Prepare dungeon room edits and fixed boss layouts from dungeon entry.
- Add a Settings toggle for command hover previews.

## 1.5.4.2

- Add configurable Party Chat notification sounds.
- Keep map edits active during M7 dragon phases.

## 1.5.4.1

- Fix fishing countdowns being treated as damage numbers.
- Support four-part hotfix versions in update checks.

## 1.5.4

- Map Editor: Add a Refresh button to reload preset files without restarting.
- Fix Mod Menu options screen compatibility.

## 1.5.3

- Performance optimizations for large map edits, room loading and presets.
- Map Editor: Add Kuudra support and Force Kuudra.
- Settings: Add an update check toggle, enabled by default.

## 1.5.2

- Map Editor: Store compressed presets and room layouts in separate folders; migrate legacy JSON with exact backups and save only changed rooms.
- Add a searchable preset manager with save, load, rename and recoverable deletion.
- Automatically align room presets from detected blocks, including directional materials; keep FME imports compatible.
- Default forced singleplayer editing without a floor sidebar to the shared F7/M7 Boss layout.
- Add a local-only CCMap reader with material/coordinate search and editable JSON round trips at https://cc.toryhi.com/map.
- Skins: Raise the PNG limit to 10 MiB and the shared P2P upload cap to 8 Mbps.
- Batch negotiated skin chunks, avoid premature retries and keep position traffic on a separate budget.

## 1.5.1

- Performance optimizations.

## 1.5.1-beta.1 (test build)

- Map Editor: Stop scanning ordinary material replacements every frame.
- Map Editor: Index nearby ghost blocks and loaded containers; cache material states and avoid redundant room snapshots.

## 1.5.0

- Equipments: Add seated Mini-Me pets, shoulder selection and synced position adjustments.
- Skins: Add local PNG imports and optional direct sharing.
- Map Editor: Add bulk material replacement, FME imports, room saves and Freecam.
- Map Editor: Remove edit limits and fix freezes with large, dense replacements.
- Map Editor: Improve dungeon detection, special block support and glass rendering.
- GUI: Improve block palette performance.

## 1.4.3-beta.9 (test build)

- Map Editor: Remove block-count limits for replacements, room saves and presets.
- Map Editor: Stream large room saves and batch restored block origins.
- GUI: Reduce block palette draw calls and batch cell backgrounds before item icons.

## 1.4.3-beta.8 (test build)

- Map Editor: Add one-click material replacement for the current room or a selected region.
- Map Editor: Choose source and replacement blocks from the searchable palette; undo a whole batch in one step.

## 1.4.3-beta.7 (test build)

- Map Editor: Allow chests, special blocks and fluids in the block palette.
- Map Editor: Render virtual containers and preserve real container contents.
- Map Editor: Fix blockgui, blocks and import screens closing immediately after chat commands.
- Map Editor: Remove Cheating mode; all edits preserve real collision and mining.
- Map Editor: Fix missing neighboring faces and section occlusion behind glass and hidden blocks.

## 1.4.3-beta.6 (test build)

- Map Editor: Add Force Dungeon and scan nearby room centers outside the legacy grid.
- Map Editor: Fix automatic Catacombs detection when legacy score row IDs interrupt the location name.
- Map Editor: Share F7/M7 boss edits and migrate legacy layouts with backups.
- Map Editor: Log raw sidebar diagnostics with the map status command.

## 1.4.3-beta.5 (test build)

- Map Editor: Add FME-style mouse controls and a searchable block palette with favorites.
- Map Editor: Automatically save room edits and restore them across room rotations.
- Map Editor: Add a compact editing HUD and per-room reset.
- Map Editor: Import legacy FME room, floor and favorite configurations with preview and backups.

## 1.4.3-beta.4

- Map Editor: Fix Catacombs detection through proxy addresses and colored sidebars.
- Map Editor: Fix Shift-right-click placement and detached camera block picking.
- Map Editor: Keep blocked editor clicks local and add `/customcosmetics map status` diagnostics.

## 1.4.3-beta.3

- Map Editor: Add local-only Freecam with configurable flight speed and key binding.
- Freecam: Keep the player stationary, edit from the camera, and restore normal controls on exit.

## 1.4.3-beta.2

- Map Editor: Add material-only Legit edits, visual air blocks and optional client-collision Cheating mode.
- Map Editor: Add region editing, undo and rotated room presets.
- Equipments: Add synchronized position adjustments for each part.
- Skins: Add local PNG imports and optional direct P2P sharing, with bounded transfers and disconnect fallback.

## 1.4.3-beta.1

- Equipments: Add Mini-Me, a seated companion wearing your skin.
- Pets: Choose the left or right shoulder.
- Sharing: Preserve accessory colors in chams.

## 1.4.2

- Sharing: Fix chams textures when players have glowing outlines.
- Sync: Improve direct connection stability during brief packet loss.

## 1.4.1

- Damage Splash: Add customizable damage numbers, inspired by Baity.
- GUI: Add settings search.
- Sharing: Add location tracers.
- Sharing: Reduce position sync bandwidth and adapt movement smoothing.
- Sharing: Add vanilla nametag style.
- Sharing: Add chams for teammates and shared players.

## 1.4.0

- GUI: Add compact layouts and reorganize settings.
- Player Size: Include SmolPeople.
- Sharing: Add public and selected-player location sharing.
- Sharing: Add a nearby nametag hide distance.
- Sync: Improve connection reliability.

## 1.3.1

- Add a mod icon.

- Sync: Add local debug reports.

- Sync: Prioritize direct connections and improve fallback recovery.

- Fix Sodium compatibility for gradient text.

- Party: Add a names-only ESP style.

- Add local nickname, accessory and cape blocklists with nickname search and commands.

## 1.3.0

- New Feature: Party ESP and IRC For Party Members.

- Party: Add customizable tracers.

- Sync: Improve latency in mainland China.

- 目前没有足够的玩家测试同步功能，如发现问题，请提交 Issue 或联系 QQ：1612998346。

## 1.2.1

- Add support for Modern UI.

## 1.2.0

- New Feature: Accessories.

- Accessories: Add Pixel and Detailed collections.

- Accessories: Fix model clipping.

- Cape: Add system file picker and preserve cropped image detail.

- Cape: Fix preview after restarting the client.

## 1.1.1

- Cape: Fix preview and slow uploads.

- Player Size: Add synced X/Y/Z scaling.

- NickTweaks: Add segmented gradients.

- Item Cosmetics: Fix missing model textures.

- GUI: Add draggable scrollbars and move Cape above Sync.

- Cape: Support high-resolution atlases up to 2 MiB.

## 1.1.0

Add Custom Capes with local cropping, preview and online review.

## 1.0.3

NickTweaks: Add Shimmer Size and Interval.

Animations: Add blocking position, rotation and preview.

Add Offline Mode.

## 1.0.2

NickTweaks: Add color presets and customizable shimmer colors.

## 1.0.1

new feature: nicktweaks new theme

new feature: item customize

## 1.0.0

Custom Cosmetics **1.0.0 正式版**发布。

- **1.7 BlockHit**：格挡动作，支持安装模组的玩家之间同步显示。
- **Animations**：支持手持物品大小、位置、旋转和挥动调整。
- **SmolPeople 与 NickTweaks**：可爱的小人模型与自定义昵称，支持玩家间同步。移植自 Baity。

支持 **Minecraft 26.1.x / 26.2 / 26.3**，需 Java 25、Fabric Loader 0.19.5+ 和对应版本的 Fabric API。

格挡共享仅在 **Hypixel** 中生效，需安装 **Hypixel Mod API**。

**注意：请关闭其他模组的 Animation 功能，否则可能出现兼容性问题。**
