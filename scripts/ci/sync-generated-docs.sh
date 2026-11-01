#!/usr/bin/env bash
# Copies generated API documentation into the VitePress site tree:
# English pages to en/api/, Chinese pages to zh/api/, and the TypeScript
# definitions to public/ so the site serves them for download.
#
# Usage: bash scripts/ci/sync-generated-docs.sh [gen-md-dir] [gen-ts-dir]
# Defaults match the docs-builder generateApiDocs task outputs.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
gen_md="${1:-$repo_root/docs-builder/build/gen/md}"
gen_ts="${2:-$repo_root/docs-builder/build/gen/ts}"
site="$repo_root/docs/vitepress"

if [[ ! -d "$gen_md/api" || ! -f "$gen_ts/types/neomacros.d.ts" ]]; then
  echo "generated docs not found; run: ./gradlew -p docs-builder generateApiDocs" >&2
  exit 1
fi

mkdir -p "$site/en/api" "$site/zh/api" "$site/public"

# English pages keep their name; Chinese pages drop the .zh.md suffix so
# each locale tree maps module name to route identically.
for f in "$gen_md"/api/*.md; do
  case "$f" in
    *.zh.md) ;;
    *) cp "$f" "$site/en/api/" ;;
  esac
done
for f in "$gen_md"/api/*.zh.md; do
  cp "$f" "$site/zh/api/$(basename "$f" .zh.md).md"
done

cp "$gen_ts/types/neomacros.d.ts" "$site/public/neomacros.d.ts"

echo "synced $(find "$site/en/api" -name '*.md' ! -name 'index.md' | wc -l) module page(s) into the site"
