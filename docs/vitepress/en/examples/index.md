# Examples

Complete, runnable scripts demonstrating real NeoMacros patterns. Each one
is a single `.js` file: drop it into `config/neomacros/scripts`, run
`/neomacros reload`, and it works.

| Demo | Demonstrates |
|---|---|
| [Auto Greet](./auto-greet) | `events.on`, chat output, event payload fields |
| [Coords HUD](./coords-hud) | the `tick` event, `hud` overlays, `player.self`, `render` lines |
| [Block Guard](./block-guard) | keybinds via `input`, cancellable `events.onSync` handlers |
| [Damage Counter](./damage-counter) | `storage` persistence across reloads and restarts |

## How to read them

Every demo follows the same shape:

1. an optional `meta` export (name, lifecycle, permissions);
2. destructured `neo` facades at the top;
3. event subscriptions or loops that do the work.

Snippets in the [Guide](/en/guide/getting-started) show single calls; these
pages show the whole script plus a walkthrough of each part. Per-module
short examples also live at the bottom of every
[API reference](/en/api/) page.

## Trust note

None of these demos declare capabilities beyond the base surface, so they
load without a trust prompt. If you extend one with `fs`, `net` or
`command`, expect the [permission prompt](/en/guide/permissions-and-trust)
on the next load.
