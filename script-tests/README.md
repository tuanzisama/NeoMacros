English | [简体中文](README.zh-CN.md)

# script-tests

End-to-end cases that run inside a real game client. Each entry is a script
folder (manifest + entry) following the standard macros layout:

- `suite/` — the E2E-G runner (`e2e-suite`, lifecycle `always`); install below
  `config/neomacros/macros/services/`.
- `cases/` — standalone cases; event-driven ones go below
  `config/neomacros/macros/event/`.

The suite prints one result line per case (`[NeoMacros-E2E] <caseId>
PASS|FAIL <detail>`) plus a final `SUITE-DONE` marker, and persists the full
result map via storage — the `verifyGameE2E` protocol reads it after the
client exits. With `-Dneomacros.e2e.quitOnDone=true` the mod shuts the client
down once the suite finishes.

Case contract: use only the public script API, run harmlessly under one-time
state, and always leave a `detail` on failure. Cases needing human judgement
print their observations for manual review.
