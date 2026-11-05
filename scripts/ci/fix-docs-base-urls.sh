#!/usr/bin/env bash
# Post-generation repairs for the GitHub Pages build:
#
# 1. Copies the generated neomacros.d.ts into the public output: nitro
#    feeds .gitignore patterns into its asset ignore list, so the file
#    (ignored like every other generated artifact) is skipped by the
#    normal public/ copy.
# 2. Rewrites the absolute URLs the Docus build stack emits for subpath
#    hosting. The canonical link and the text/markdown alternate are built
#    from the bare origin plus the router path, dropping the /NeoMacros
#    base; og:image and the sitemap entries stack the base twice.
#    The hreflang tags and og:url are correct as generated.
#
# Run after `npm run generate`.
#
# Usage: bash scripts/ci/fix-docs-base-urls.sh [site-out-dir]
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
site="$repo_root/docs/site"
out="${1:-$site/.output/public}"

if [[ ! -d "$out" ]]; then
  echo "generated site not found; run: npm run generate (in docs/site)" >&2
  exit 1
fi

if [[ -f "$site/public/neomacros.d.ts" ]]; then
  cp "$site/public/neomacros.d.ts" "$out/neomacros.d.ts"
fi

origin='https://tuanzisama.github.io'
count=0
while IFS= read -r f; do
  before=$(wc -c < "$f")
  sed -i -E \
    -e "s|(rel=\"canonical\" href=\"$origin/)|\\1NeoMacros/|" \
    -e "s|(type=\"text/markdown\" href=\"$origin/)|\\1NeoMacros/|" \
    -e "s|$origin/NeoMacros/NeoMacros|$origin/NeoMacros|g" \
    "$f"
  after=$(wc -c < "$f")
  if [[ "$before" != "$after" ]]; then
    count=$((count + 1))
  fi
done < <(find "$out" -name '*.html' -o -name '*.xml' | grep -v node_modules)

echo "post-processed site output (URLs in $count page(s), d.ts copied)"
