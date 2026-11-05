// Runs `nuxt generate` and the postbuild patch step unconditionally: the
// generate exit code cannot be trusted (Nitro's teardown can segfault
// after the output is complete), so postbuild.mjs performs the real
// completeness check. shell:true keeps this cross-platform.
import { spawnSync } from 'node:child_process'

const result = spawnSync('npx', ['nuxt', 'generate'], { stdio: 'inherit', shell: true })
if (result.error) {
  console.error(result.error)
}
