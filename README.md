# Ametrin Structures

An open source library mod for Minecraft structure mod developers.

- **Foam** - marker blocks that define blocks which should stay air (air blocks get treated as structure void). They can spread on their own to fill a region.
- **Simple structures** - a built-in structure type and piece type covering a large range of structure use-cases.  Everything is data driven so pack authors can modify structures.
- **Extended jigsaw** - jigsaw structure with a fluent builder and pool bootstrap helpers that reduce boilerplate.
- **Even Spread placement** - spreads structures evenly with a minimum distance without any visible pattern and with a structure-tag exclusion zone.
- **Scattered Grid placement** - scattered grid-shaped placement with a structure-tag exclusion zone.
- **Fixtures** - a block that gets replaced with whatever it specifies when a structure is generated.
- **Spawner profiles** - data driven spawner settings that resolve at load time so spawners can be adjusted from data packs

Using the library: [getting started](docs/getting-started.md), [extending it](docs/extending.md) and [debugging helpers](docs/debugging.md).  
[Dungeons Enhanced](https://github.com/Ametrin-Studios/DungeonsEnhanced) is a source-available mod using it.  
If you have further questions ask on the [Ametrin Studios discord](https://discord.gg/Ye6WxRV2Tt).  

Disclaimer: This mod is inspired by [Structure Gel API](https://www.curseforge.com/minecraft/mc-mods/structure-gel-api)