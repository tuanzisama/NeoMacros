# Getting Started

NeoMacros runs ECMAScript (JavaScript) scripts inside your Minecraft client.
Scripts are plain `.js` ES modules placed in the `config/neomacros/scripts`
folder, powered by GraalJS — promises, `async`/`await` and a real event loop
per script.

## Requirements

- Minecraft 26.1+ with [Fabric Loader](https://fabricmc.net/use/)
- [Fabric API](https://modrinth.com/mod/fabric-api) matching your game version
- NeoMacros itself: download the latest
  [`neomacros-<mc>-fabric-<version>.jar`](https://github.com/tuanzisama/NeoMacros/releases)

## Install

1. Install Fabric Loader for your Minecraft version.
2. Drop the NeoMacros jar and the matching Fabric API jar into your `mods`
   folder.
3. Launch the game once; NeoMacros creates `config/neomacros/scripts`.

## Folder layout

Everything a script touches lives under the game directory:

```
.minecraft/
└── config/
    └── neomacros/
        ├── options.json   # mod settings
        └── macros/        # your scripts, one folder per script
            ├── keypress/  # key-bound scripts
            ├── event/     # event-driven scripts
            └── services/  # long-running scripts
```

Each script folder contains a `neomacro.yml` (or `.json` / `.json5`)
manifest and its entry file - see
[Your First Script](./first-script).

Scripts keep their own persistent data in an isolated store (see
[`storage`](/en/api/storage)), so files stay readable and swappable.

## In-game commands

All management goes through the `/neomacros` client command:

| Command | Purpose |
|---|---|
| `/neomacros list` | show loaded scripts |
| `/neomacros reload` | rescan the scripts folder and reload changes |
| `/neomacros trust <script>` | grant capabilities to a script |
| `/neomacros errors` | open the screen listing recent script failures |

## Next steps

- Write [your first script](./first-script).
- Learn the [event model and async patterns](./events-and-async).
- Browse the [API reference](/en/api/) for every facade and event payload.
