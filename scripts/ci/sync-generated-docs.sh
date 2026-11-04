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

# The API trees are fully generated (only the hand-written index.md is
# tracked), so stale pages from removed generators are wiped first.
find "$site/en/api" -mindepth 1 -maxdepth 1 ! -name 'index.md' -exec rm -rf {} +
find "$site/zh/api" -mindepth 1 -maxdepth 1 ! -name 'index.md' -exec rm -rf {} +

# Append the types/java module supplement (non-facade modules that
# TSDoclet cannot scan: types cells, java module re-exports).
supplement="$site/api-examples/types-supplement.d.ts"
if [[ -f "$supplement" ]]; then
  cat "$supplement" >> "$gen_ts/types/neomacros.d.ts"
fi

# Appends the locale's example snippet for one module, when one exists.
append_examples() {
  local dest="$1" fragment="$2"
  if [[ -f "$fragment" ]]; then
    { printf '\n## Examples\n\n'; cat "$fragment"; } >> "$dest"
  fi
}

# English pages keep their name; Chinese pages drop the .zh.md suffix so
# each locale tree maps module name to route identically. Subdirectories
# (classes/, events/) are copied preserving their relative path.
while IFS= read -r f; do
  rel="${f#"$gen_md"/api/}"
  case "$f" in
    *.zh.md) ;;
    *)
      mkdir -p "$site/en/api/$(dirname "$rel")"
      cp "$f" "$site/en/api/$rel"
      append_examples "$site/en/api/$rel" "$examples/en/$(basename "$f")"
      ;;
  esac
done < <(find "$gen_md/api" -name '*.md' -type f | sort)
while IFS= read -r f; do
  rel="${f#"$gen_md"/api/}"
  name="$(basename "$f" .zh.md).md"
  target="$site/zh/api/$(dirname "$rel")/$name"
  mkdir -p "$(dirname "$target")"
  cp "$f" "$target"
  append_examples "$target" "$examples/zh/$name"
done < <(find "$gen_md/api" -name '*.zh.md' -type f | sort)

cp "$gen_ts/types/neomacros.d.ts" "$site/public/neomacros.d.ts"

echo "synced $(find "$site/en/api" -name '*.md' ! -name 'index.md' | wc -l) module page(s) into the site"
