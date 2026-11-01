#!/usr/bin/env bash
# Copies generated API documentation into the VitePress site tree:
# English pages to en/api/, Chinese pages to zh/api/, and the TypeScript
# definitions to public/ so the site serves them for download.
#
# Hand-written per-module example snippets (docs/vitepress/api-examples/
# <locale>/<module>.md) are appended under an "## Examples" heading; pages
# without a matching snippet pass through unchanged.
#
# Usage: bash scripts/ci/sync-generated-docs.sh [gen-md-dir] [gen-ts-dir]
# Defaults match the docs-builder generateApiDocs task outputs.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
gen_md="${1:-$repo_root/docs-builder/build/gen/md}"
gen_ts="${2:-$repo_root/docs-builder/build/gen/ts}"
site="$repo_root/docs/vitepress"
examples="$site/api-examples"

if [[ ! -d "$gen_md/api" || ! -f "$gen_ts/types/neomacros.d.ts" ]]; then
  echo "generated docs not found; run: ./gradlew -p docs-builder generateApiDocs" >&2
  exit 1
fi

mkdir -p "$site/en/api" "$site/zh/api" "$site/public"

# Appends the locale's example snippet for one module, when one exists.
append_examples() {
  local dest="$1" fragment="$2"
  if [[ -f "$fragment" ]]; then
    { printf '\n## Examples\n\n'; cat "$fragment"; } >> "$dest"
  fi
}

# English pages keep their name; Chinese pages drop the .zh.md suffix so
# each locale tree maps module name to route identically.
for f in "$gen_md"/api/*.md; do
  case "$f" in
    *.zh.md) ;;
    *)
      cp "$f" "$site/en/api/"
      append_examples "$site/en/api/$(basename "$f")" "$examples/en/$(basename "$f")"
      ;;
  esac
done
for f in "$gen_md"/api/*.zh.md; do
  name="$(basename "$f" .zh.md).md"
  cp "$f" "$site/zh/api/$name"
  append_examples "$site/zh/api/$name" "$examples/zh/$name"
done

cp "$gen_ts/types/neomacros.d.ts" "$site/public/neomacros.d.ts"

echo "synced $(find "$site/en/api" -name '*.md' ! -name 'index.md' | wc -l) module page(s) into the site"
