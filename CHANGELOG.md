# Changelog

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
