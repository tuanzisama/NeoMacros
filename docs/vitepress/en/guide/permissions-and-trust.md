# Permissions & Trust

NeoMacros scripts run in a sandbox. Reading world state or printing to chat
is always allowed, but anything reaching outside the base surface — files,
network, commands — must be declared by the script and granted explicitly by
you, the player.

## Declaring capabilities

A script declares what it needs in its `neomacro.yml` manifest:

```yaml
# macros/event/backup-notes/neomacro.yml
name: backup-notes
version: 1.0.0
author: you                    # name, version and author are required
lifecycle: world               # always | world (default) | manual
permissions: ["fs"]            # capabilities beyond the base surface
```

| Permission | Unlocks |
|---|---|
| `fs` | every [`neo.fs`](/en/api/fs) member (readText, writeText, list, mkdir, delete, exists) |
| `net` | every [`neo.net`](/en/api/net) member (get, request) |
| `command` | [`neo.command`](/en/api/command) register and execute |

Scripts with no `permissions` list (or an empty one) get the base surface
and never trigger a prompt. Unknown capability names are rejected by the
trust gate, so typos fail closed.

## The trust prompt

On first load of a script with declared capabilities, a chat prompt appears
listing what was requested. Until you decide, the script runs under a
**denying view** — the guarded calls simply fail. Click the prompt line (it
runs the trust command for you), or type it yourself:

```text
/neomacros trust backup-notes
```

Either way the decision persists for this content hash.

```text
/neomacros trust backup-notes
```

## Trust is keyed to content

Decisions are keyed to the **SHA-256 content hash** of the script file. This
has two consequences:

- a trusted script can never silently grow new capabilities — editing the
  file changes its hash, and the prompt appears again on the next load;
- trust decisions survive restarts (stored in `config.json`).

Re-approving after an edit is the normal workflow; the prompt is the review
step.

## Imports stay in the script folder

Scripts can split code into files with ES module imports
(`import { fmt } from './lib/util.js'`). Every imported file must live
inside the script's own folder: `..` escapes, absolute paths, and `file:`
URIs pointing elsewhere are rejected at load time. This boundary is part of
the sandbox itself — it is not a permission, and no manifest declaration
widens it. The `@neomacros/*` modules are provided by the mod and are not
affected.

## What does not need trust

- [`events`](/en/api/events), [`clock`](/en/api/clock), [`chat`](/en/api/chat)
- Reading world and player state via [`world`](/en/api/world),
  [`player`](/en/api/player), [`entity`](/en/api/entity)
- HUD and world overlays via [`hud`](/en/api/hud) and
  [`render`](/en/api/render)
- Keybinds via [`input`](/en/api/input)
- [`storage`](/en/api/storage) — the script's own key-value store

If a script only uses these, omit the `permissions` list and it loads
without any prompt.
