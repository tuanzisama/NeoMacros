// Serves the generated TypeScript definitions for download. The file
// cannot live in public/: Nuxt's default ignore list excludes every
// *.d.ts from the static output copy, so a prerendered route is used
// instead. This code executes at build time only (the static output
// freezes the response), where cwd is the site project root.
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const dts = readFileSync(join(process.cwd(), 'server/assets/neomacros.d.ts'), 'utf-8')

export default defineEventHandler((event) => {
  setHeader(event, 'content-type', 'text/plain; charset=utf-8')
  return dts
})
