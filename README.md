# Ametrin Structures

An open source library mod for Minecraft structure mod developers.

**The library is in beta.** Any update can contain breaking changes, so check the [changelog](changelog.md) before updating.

- **Foam** - marker blocks for space that should stay air. Plain air in a template is treated as structure void. Foam can spread on its own to fill a region.
- **Modular structures** - a built-in structure type that combines templates, jigsaw layouts and piece sources of your own. Everything is data driven, so pack authors can modify structures.
- **Jigsaw layouts** - modular structures can grow vanilla style jigsaw layouts. Pool elements can be limited in how often, how deep and where they appear and helpers declare pools with less boilerplate.
- **Even Spread placement** - spreads structures evenly with a minimum distance and no visible pattern. Has an optional structure tag based exclusion zone.
- **Scattered Grid placement** - one structure per grid cell, at a random spot in the cell. Has an optional structure tag based exclusion zone.
- **Fixtures** - blocks that turn into something else when the structure generates, like a loot chest, an entity or a spawner.
- **Spawner profiles** - data driven spawner settings that are applied when a spawner loads, so datapacks and mod updates can adjust existing spawners.
- **Debugging commands** - see where a structure generates and why not elsewhere, how its layouts assemble, what its chests hold, and which templates, pools or loot tables are missing.

## Documentation

- [Getting started](docs/getting-started.md): from an empty mod to a structure in the world
- [Structures](docs/structures.md): structure sets, placements and modular structures
- [Jigsaw layouts](docs/jigsaw.md): jigsaw layouts and template pools
- [Fixtures](docs/fixtures.md): fixtures, presets, conditions and spawner profiles
- [Extending](docs/extending.md): your own filters, piece sources, structures, fixtures and foam
- [Debugging](docs/debugging.md): the debugging commands

[Dungeons Enhanced](https://github.com/Ametrin-Studios/DungeonsEnhanced) is a source-available mod built on this library, if you want a real example. If you have questions, ask on the [Ametrin Studios discord](https://discord.gg/Ye6WxRV2Tt). Want to help? See [contributing](CONTRIBUTING.md).

Disclaimer: This mod is inspired by [Structure Gel API](https://www.curseforge.com/minecraft/mc-mods/structure-gel-api).

## License

The code is licensed under [MIT](LICENSE). The license doesn't cover the names "Ametrin Structures" and "Ametrin Studios" or the logo (`src/main/resources/icon.png`). Copies, forks and ports have to be released under a different name and logo, unless agreed otherwise with us.
