#!/usr/bin/env bash
# Copies generated API documentation into the Docus site content tree:
# English pages to content/en/api/, Chinese pages to content/zh-cn/api/,
# and the TypeScript definitions to server/assets/ (a prerendered server
# route serves them for download; a *.d.ts cannot ship from public/).
#
# Hand-written per-module example snippets (docs/site/api-examples/
# <locale>/<module>.md) are appended under an "## Examples" heading; pages
# without a matching snippet pass through unchanged.
#
# The API trees are fully generated (only the hand-written index.md is
# tracked), so stale pages from removed generators are wiped first, and the
# sidebar section titles (.navigation.yml) for the API subtree are written
# here as well. Event pages are grouped into per-domain subfolders derived
# from the slug's first dash segment (e.g. chat-send -> events/chat/) so the
# auto-generated sidebar folds them by domain. Relative ".html" links in the
# generated Markdown (MDDoclet emits VitePress-style URLs) are stripped and
# lowercased because Nuxt Content serves clean, lowercased routes.
#
# Usage: bash scripts/ci/sync-generated-docs.sh [gen-md-dir] [gen-ts-dir]
# Defaults match the docs-builder generateApiDocs task outputs.
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
gen_md="${1:-$repo_root/docs-builder/build/gen/md}"
gen_ts="${2:-$repo_root/docs-builder/build/gen/ts}"
site="$repo_root/docs/site"
examples="$site/api-examples"

if [[ ! -d "$gen_md/api" || ! -f "$gen_ts/types/neomacros.d.ts" ]]; then
  echo "generated docs not found; run: ./gradlew -p docs-builder generateApiDocs" >&2
  exit 1
fi

mkdir -p "$site/content/en/api" "$site/content/zh-cn/api" "$site/server/assets"

# The API trees are fully generated (only the hand-written index.md is
# tracked), so stale pages from removed generators are wiped first.
find "$site/content/en/api" -mindepth 1 -maxdepth 1 ! -name 'index.md' -exec rm -rf {} +
find "$site/content/zh-cn/api" -mindepth 1 -maxdepth 1 ! -name 'index.md' -exec rm -rf {} +

# Sidebar titles for the generated Classes/Events subtrees. The api/ and
# examples/ folders must NOT get a .navigation.yml: Nuxt Content inserts
# those files as collection rows, and their path would collide with the
# folder's index.md (two rows, one path) — the overview page would then
# nondeterministically render as an empty navigation stub.
write_navigation() {
  local loc="$1" classes_title events_title
  if [[ "$loc" == zh-cn ]]; then
    classes_title='类' events_title='事件'
  else
    classes_title='Classes' events_title='Events'
  fi
  mkdir -p "$site/content/$loc/api/20.classes" "$site/content/$loc/api/30.events"
  printf 'title: %s\n' "$classes_title" > "$site/content/$loc/api/20.classes/.navigation.yml"
  printf 'title: %s\n' "$events_title"  > "$site/content/$loc/api/30.events/.navigation.yml"
}

# Appends the locale's example snippet for one module, when one exists.
append_examples() {
  local dest="$1" fragment="$2"
  if [[ -f "$fragment" ]]; then
    { printf '\n## Examples\n\n'; cat "$fragment"; } >> "$dest"
  fi
}

# MDDoclet emits VitePress-style relative links (Pos3D.html); Nuxt Content
# routes are clean and lowercased, so the suffix is stripped and relative
# targets are lowercased (class pages are synced to lowercased file names).
# Links with a scheme (http://...) are left untouched.
strip_html_links() {
  sed -i -E -e 's/\]\(([^):]*)\.html\)/](\1)/g' \
            -e 's/\]\(([^):]+)\)/](\L\1\E)/g' "$1"
}

# Facade module order for the sidebar, mirroring the old VitePress config:
# modules first, then Classes, then Events. Numeric prefixes order the
# auto-generated sidebar and are stripped from the routes.
api_modules_order="events thread storage chat hud command render world player entity fs net java input client reflect"

module_prefix() {
  local name="$1" i=1 m
  for m in $api_modules_order; do
    if [[ "$m" == "$name" ]]; then
      # Zero-padded so the lexicographic file sort matches numeric order.
      printf '%02d.' "$i"
      return
    fi
    i=$((i + 1))
  done
  printf ''
}

# Target path relative to the locale's api/ dir. Class pages are lowercased
# to match the served routes; event slugs gain a per-domain subfolder from
# their first dash segment (e.g. chat-send -> events/chat/).
target_for() {
  local rel="$1" dir name
  dir="$(dirname "$rel")"
  if [[ "$dir" == events || "$dir" == events/* ]]; then
    local slug
    slug="$(basename "$rel" .md)"
    printf '30.events/%s/%s.md' "${slug%%-*}" "$slug"
  elif [[ "$dir" == classes || "$dir" == classes/* ]]; then
    printf '20.classes/%s.md' "$(basename "$rel" .md | tr '[:upper:]' '[:lower:]')"
  else
    name="$(basename "$rel" .md)"
    printf '%s%s.md' "$(module_prefix "$name")" "$name"
  fi
}

write_navigation en
write_navigation zh-cn

# English pages keep their name; Chinese pages drop the .zh.md suffix so
# each locale tree maps module name to route identically.
while IFS= read -r f; do
  rel="${f#"$gen_md"/api/}"
  case "$f" in
    *.zh.md) ;;
    *)
      dest="$site/content/en/api/$(target_for "$rel")"
      mkdir -p "$(dirname "$dest")"
      cp "$f" "$dest"
      strip_html_links "$dest"
      append_examples "$dest" "$examples/en/$(basename "$f")"
      ;;
  esac
done < <(find "$gen_md/api" -name '*.md' -type f | sort)
while IFS= read -r f; do
  rel="${f#"$gen_md"/api/}"
  name="$(basename "$f" .zh.md).md"
  dest="$site/content/zh-cn/api/$(target_for "$(dirname "$rel")/$name")"
  mkdir -p "$(dirname "$dest")"
  cp "$f" "$dest"
  strip_html_links "$dest"
  append_examples "$dest" "$examples/zh/$name"
done < <(find "$gen_md/api" -name '*.zh.md' -type f | sort)

# Append the types/java module supplement (non-facade modules that
# TSDoclet cannot scan: types cells, java module re-exports).
supplement="$examples/types-supplement.d.ts"
if [[ -f "$supplement" ]]; then
  cat "$supplement" >> "$gen_ts/types/neomacros.d.ts"
fi

cp "$gen_ts/types/neomacros.d.ts" "$site/server/assets/neomacros.d.ts"

echo "synced $(find "$site/content/en/api" -name '*.md' ! -name 'index.md' | wc -l) module page(s) into the site"
