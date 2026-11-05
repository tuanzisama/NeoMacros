// Runs after `nuxt generate` (see the generate script in package.json).
//
// Nitro's teardown can segfault on Windows even after the output is
// complete, so the generate exit code cannot be trusted — this script
// validates the output instead and fails the build when it is
// incomplete.
//
// The section indexes (/en/api/ etc.) render the landing page in their
// prerendered form no matter which route variant won (a Docus/i18n
// routing quirk). They ship as the SPA shell instead: the client router
// renders the section overview from the prerendered payload on load.
import { copyFileSync, existsSync, mkdirSync } from 'node:fs'

const out = '.output/public'
const required = [
  '200.html',
  'en/index.html',
  'zh-cn/index.html',
  'en/api/chat/index.html',
  'zh-cn/api/chat/index.html',
  'neomacros.d.ts',
]
const missing = required.filter((f) => !existsSync(`${out}/${f}`))
if (missing.length > 0) {
  console.error(`incomplete site output, missing: ${missing.join(', ')}`)
  process.exit(1)
}

const sections = [
  'en/api', 'en/guide', 'en/examples',
  'zh-cn/api', 'zh-cn/guide', 'zh-cn/examples',
]
for (const dir of sections.map((s) => `${out}/${s}`)) {
  mkdirSync(dir, { recursive: true })
  copyFileSync(`${out}/200.html`, `${dir}/index.html`)
}
console.log('site output complete; section indexes replaced with the SPA shell')

