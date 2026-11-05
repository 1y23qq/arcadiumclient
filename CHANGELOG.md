## 0.7.2

- Fixed GPU player rendering stopping after world changes and resource reloads.
- Reused HUD buffers across changing text sizes and removed unnecessary name-formatting work.
- Switched Argentum, Argentum Extras and Cera downloads to Modrinth.

## 0.7.1

- Added automatic server reconnection with a 3-second countdown on all game versions. Back stops retrying.
- The launcher window can now be dragged during updates.

## 0.7.0

- Improved name tag and HUD text rendering performance.
- Added batching for forced vanilla fonts and cached name masking/text measurements.
- Added performance reports with optimization usage and fallback state.

## 0.6.7

- Set the default minimum player, name tag and particle counts for beta GPU optimizations to 1.

## 0.6.6

- Simplified Optimizations (Beta) into Hypixel, Players and Display, with diagnostics on a separate page.
- Added compact name tag preparation and optimized hybrid motion blur history.
- Reduced temporary allocations in model, particle and cosmetic rendering.

## 0.6.5

- Fixed Session login for Microsoft refresh tokens.
- Improved keyless Bedwars stats loading and added customizable stats, formats and proxy addresses.
- Improved lobby rendering with particle, hologram, name tag and scoreboard optimizations.
- Added separate world and entity color settings.
- Resource pack search now keeps active packs visible.

New experimental rendering options are off by default.

## 0.6.4

- Added Optimizations (Beta) with GPU player, armor and name tag rendering options.
- Added retained name tag scenes and HUD batches to reduce repeated rendering work.
- Added adjustable world render resolution while keeping HUD and menus sharp.

Experimental options are off by default. GPU player and name tag modes require OpenGL 4.3.

## 0.6.3

- All Custom F3 settings are now available inside the client module settings page.

## 0.6.2

- Added cross-version resource pack support.
- Search all video settings from the main page.
- Added Custom F3 with configurable modules and hardware text.
- Fixed third-person 3D skins and reduced their rendering overhead.

## 0.6.1

- Faster keyless Bedwars stats loading when providers are slow or stalled.
- Reduced recovery delays after network errors and improved request cancellation.

## 0.6.0

- Added Disable Emoji in Scoreboard, enabled by default.
- Fixed Minecraft settings and resource pack selections failing to persist in some configurations.
- Preserve legacy smooth lighting settings when sharing options with newer Minecraft versions.

## 0.5.9

- Fixed emoji spacing in chat and input fields on Minecraft 1.8.9.
- Respect outgoing chat limits while typing and pasting.
- Fixed overlapping sweep and extra critical particles on Minecraft 1.21.11.

## 0.5.8

- Added a height limit of 100 on compmc.net for Minecraft 1.8.9.
- Added Tab Background colors and optional separate color and opacity for your own name.
- Show Flying in the Toggle Sprint HUD while flying.
- Keep inventory and shop keyboard shortcuts working with IME enabled.
- Faster keyless Bedwars Overlay stats loading.

## 0.5.7

- Make waypoint shortcuts editable from the client Keybinds menu.

## 0.5.6

- Faster Minecraft 1.8.9 startup.

## 0.5.5

- Improved Minecraft 1.7.10 performance with Hodgepodge, CoreTweaks and ArchaicFix.
- Reduced redundant startup work and Mod validation overhead.

## 0.5.2

- Fixed freezing when joining servers by double-clicking a server entry on 1.8.9.
- Added Reload Resources to the bottom-left of the 1.8.9 pause menu.

## 0.5.1

- Added Rod Thickness and Password Hider for 1.8.9.
- Improved Toggle Sprint and Toggle Sneak controls.
- Fixed background FPS limiting, server switching and fresh 1.8.9 setups.
- Added the first-launch Nvidium recommendation for 1.21.11.

## 0.5.0

- Added Minecraft 1.21.11 and 26.2 support with updated bundled mods.
- Added optional Nvidium setup for compatible NVIDIA GPUs on 26.2.
- Added resource pack version filtering and fixed missing pack icons.
- Matched Arcadium badges across versions.
- Fixed screen closing, input recovery and menu navigation.

## 0.4.5

- Added custom texture pack creation and BLC-style LevelHead.
- Restored chat emoji with Twemoji and grouped chat settings.
- Added saved local skins for Cracked accounts and refresh-token login.
- Improved Bedwars Overlay caching and keyless lookups.
- Improved nametag background alignment.
- Moved official repositories to 1y23qq. Install this release manually when upgrading from 0.4.4 or earlier.

## 0.4.4

- Fixed 1.7.10 Zoom, menu keybinds, scoreboard text and fullscreen focus handling.
- Improved Perspective input and Ping reliability.
- Fixed the connecting screen to show the actual server.
- Added Height Limit HUD spacing and an Unfocused FPS toggle.

## 0.4.3

- Added title background settings with 15 panorama previews, blur and gradient controls.
- Set Lakeside Morning as the new default background.
- Added an optional resource pack panorama setting.

## 0.4.2

- Rebuilt Minecraft 1.7.10 with native Forge mod loading and Angelica rendering.
- Preserved shared settings and player data while separating version-specific loaders and mods.
- Shared worlds, game settings, server lists and other player data between Minecraft versions.
- Added backups before opening newer worlds in an older Minecraft version.

- Fixed Compact Chat counters and nametag alignment.
- Improved Height Limit HUD formatting and colors.
- Added Unfocused FPS and Health HUD.

## 0.4.1

- Fixed Windows update recovery.
- Shared mod and HUD settings between Minecraft 1.7.10 and 1.8.9.

## 0.4.0

- Added Minecraft 1.7.10 support.
- Added item scale options to Badlion Animations.
- Added resource pack drag and drop and larger action buttons.
- Added line limits for Compact Chat.
- Added third-person crosshair options.
- Fixed HUD text alignment.

## 0.3.9

- Set Simple GUI as the default for new users.
- Removed the Client module from the mod menu.
- Renamed Arcadium Players to Arcadium Badge.
- Matched the title screen background to Arcadium Options.

## 0.3.8

- Added Simple GUI mode.
- Added Minemen Club height limits.
- Added cape switching in Skin Changer.
- Fixed resource pack search.
- Fixed oversized hitboxes.

## 0.3.7

- reworked stuff
