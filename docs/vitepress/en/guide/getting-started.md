# Getting Started

NeoMacros runs ECMAScript (JavaScript) scripts inside your Minecraft client.
Scripts are plain `.js` ES modules placed in the `config/neomacros/scripts`
folder.

## Install

1. Install the [Fabric Loader](https://fabricmc.net/use/) for your Minecraft
   version (26.1+ supported).
2. Drop `neomacros-<mc>-fabric-<version>.jar` and the matching
   [Fabric API](https://modrinth.com/mod/fabric-api) into your `mods` folder.
3. Launch the game once; NeoMacros creates `config/neomacros/scripts`.

## Your first script

Create `config/neomacros/scripts/hello.js`:

```js
export function onEvent(ev) { }

const { events, chat } = neo;

chat.log("Hello from NeoMacros!");

events.on("player:join", (payload) => {
    chat.log(`Welcome, ${payload.name}!`);
});
```

Scripts with the default lifecycle load when the game starts. Run
`/neomacros list` to see what is loaded and `/neomacros reload` to pick up
file changes.

## Subscribing to events

`events.on` registers a listener and returns a subscription; call
`.deactivate()` to unsubscribe. Use `events.waitFor(name)` to await one
occurrence as a promise:

```js
const { events, clock } = neo;

async function watchDamage() {
    while (true) {
        const ev = await events.waitFor("player:damage");
        chat.actionbar(`Ouch! -${ev.amount.toFixed(1)} HP`);
        await clock.ticks(20);
    }
}
watchDamage();
```

## Trust and permissions

On first load, a script declaring capabilities beyond the safe default set
prompts in chat: click the line or run `/neomacros trust <script>` to grant
them for this content hash.

## Drawing on screen and in the world

```js
const { hud, render } = neo;

hud.addText("coords", "x/z display", 8, 8, 0xFFFFFFFF);

render.drawLine("path",
    { x: 0, y: 64, z: 0 }, { x: 10, y: 64, z: 10 }, 0xFF00FF00);
```

## Next steps

- Browse the [API reference](/en/api/) for every facade and event payload.
- The in-game `/neomacros errors` screen lists recent script failures.
