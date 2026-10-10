# Ametrin Structures

An open source library mod for Minecraft structure mod developers.

- **Foam** - marker blocks for space that should stay air. Plain air in a template is treated as structure void. Foam can spread on its own to fill a region.
- **Simple structures** - a built-in structure type that covers most structure use-cases. Everything is data driven, so pack authors can modify structures.
- **Extended jigsaw** - vanilla's jigsaw structure with a builder and helpers for declaring pools with less boilerplate.
- **Even Spread placement** - spreads structures evenly with a minimum distance and no visible pattern. Has an optional structure tag based exclusion zone.
- **Scattered Grid placement** - one structure per grid cell, at a random spot in the cell. Has an optional structure tag based exclusion zone.
- **Fixtures** - blocks that turn into something else when the structure generates, like a loot chest, an entity or a spawner.
- **Spawner profiles** - data driven spawner settings that are applied when a spawner loads, so datapacks and mod updates can adjust existing spawners.

Using the library: [getting started](docs/getting-started.md), [extending it](docs/extending.md) and [debugging tools](docs/debugging.md).  
[Dungeons Enhanced](https://github.com/Ametrin-Studios/DungeonsEnhanced) is a source-available mod built on it.  
If you have questions, ask on the [Ametrin Studios discord](https://discord.gg/Ye6WxRV2Tt).

Disclaimer: This mod is inspired by [Structure Gel API](https://www.curseforge.com/minecraft/mc-mods/structure-gel-api).
