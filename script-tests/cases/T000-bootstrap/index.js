/**
 * E2E-G case: T000-bootstrap (mode: auto)
 *
 * Manual procedure until the script runtime and the in-game test runner are
 * available (they replace this comment with automated assertions):
 *
 *   1. `./gradlew runFabricClient` and wait for the main menu;
 *   2. run/logs/latest.log must contain: (neomacros) NeoMacros bootstrap;
 *   3. type /neomacros in chat; the reply must be "NeoMacros <version> is loaded."
 *
 * __test bridge contract (once available):
 *   __test.report('T000-bootstrap', ok, detail)
 * Assertions: /neomacros is registered (reply text matches) and the bootstrap
 * marker appeared in the log.
 */
