English | [简体中文](README.zh-CN.md)

# script-tests

End-to-end cases that run inside a real game client. `suite.js` is the runner: it loads each case, prints one result line per case (`[NeoMacros-E2E] <caseId> PASS|FAIL <detail>`) plus a final `SUITE-DONE` marker, and persists the full result map via storage — the `verifyGameE2E` Gradle task reads it after the client exits.

`cases/` is mirrored into [e2e/fixtures/scripts](../e2e/fixtures/scripts/) (the same files, hash-checked in CI).

Case contract: use only the public script API, run harmlessly under one-time state, and always leave a `detail` on failure. Cases needing human judgement print their observations for manual review.
